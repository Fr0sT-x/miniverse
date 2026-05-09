package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.ScoreboardController;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamDescriptor;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manhunt minigame implementation.
 * In this game, Speedrunners try to reach the End while Hunters try to stop them.
 * If a Speedrunner dies, the Hunters win. Hunters can respawn upon death.
 */
public class ManhuntMinigame implements Minigame {
    private static final String NAME = "Manhunt";

    private GameState state;

    // Role assignments for each player
    private final Map<UUID, ManhuntRole> playerRoles = new ConcurrentHashMap<>();

    // Track dead speedrunners who are now spectators
    private final List<UUID> deadSpeedrunners = new CopyOnWriteArrayList<>();

    // Track currently alive speedrunners
    private final List<UUID> aliveSpeedrunners = new CopyOnWriteArrayList<>();

    // Track which speedrunner each hunter is currently following
    private final Map<UUID, Integer> hunterTrackingIndexes = new ConcurrentHashMap<>();

    private static final String MANHUNT_COMPASS_TAG = "Manhunt_tracker";
    private static final String SCOREBOARD_OBJECTIVE = "manhunt_display";
    private static final ScoreboardController SCOREBOARD = new ScoreboardController(SCOREBOARD_OBJECTIVE, Text.literal("Manhunt"));
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("manhunt");

    private final Map<UUID, RunnerTrackingData> runnerTracking = new ConcurrentHashMap<>();
    private final Set<UUID> huntersNotifiedMissingNether = ConcurrentHashMap.newKeySet();
    private final Set<UUID> huntersNotifiedEndPortalHint = ConcurrentHashMap.newKeySet();
    private final ManhuntSpeedrunnerRespawnSystem speedrunnerRespawns = new ManhuntSpeedrunnerRespawnSystem(this);
    private final Map<UUID, Integer> speedrunnerDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hunterDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingHunterRespawns = new ConcurrentHashMap<>();
    private final Set<UUID> eliminatedHunters = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> compassCooldownUntilTicks = new ConcurrentHashMap<>();
    private ManhuntSettings settings;
    private int leadTicksRemaining;
    private boolean huntStarted;
    private int tickCounter;
    private long gameTicks;
    private MinecraftServer server;

    /**
     * Creates a new ManhuntMinigame instance.
     */
    public ManhuntMinigame() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.applySettings(ManhuntSettings.defaults());
        this.vanillaTeams.setFriendlyFireAllowed(true);
        this.vanillaTeams.setTeammateCollisionAllowed(false);
    }

    public void setVanillaFriendlyFireAllowed(boolean allowed) {
        this.vanillaTeams.setFriendlyFireAllowed(allowed);
        this.syncVanillaTeams();
    }

    public void setVanillaTeammateCollisionAllowed(boolean allowed) {
        this.vanillaTeams.setTeammateCollisionAllowed(allowed);
        this.syncVanillaTeams();
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.playerRoles.clear();
        this.deadSpeedrunners.clear();
        this.aliveSpeedrunners.clear();
        this.hunterTrackingIndexes.clear();
        this.huntersNotifiedMissingNether.clear();
        this.huntersNotifiedEndPortalHint.clear();
        this.speedrunnerDeaths.clear();
        this.hunterDeaths.clear();
        this.pendingHunterRespawns.clear();
        this.eliminatedHunters.clear();
        this.compassCooldownUntilTicks.clear();
        this.speedrunnerRespawns.reset();
        this.gameTicks = 0L;
    }

    @Override
    public void startGame() {
        this.state = GameState.STARTING;
        MinigameManager.getInstance().setCurrentState(GameState.STARTING);

        // Initialize tracking
        this.aliveSpeedrunners.clear();
        this.aliveSpeedrunners.addAll(this.getSpeedrunnerUuids());
        this.deadSpeedrunners.clear();
        this.hunterTrackingIndexes.clear();
        this.runnerTracking.clear();
        this.huntersNotifiedMissingNether.clear();
        this.huntersNotifiedEndPortalHint.clear();
        this.speedrunnerDeaths.clear();
        this.hunterDeaths.clear();
        this.pendingHunterRespawns.clear();
        this.eliminatedHunters.clear();
        this.compassCooldownUntilTicks.clear();
        this.speedrunnerRespawns.reset();
        this.leadTicksRemaining = this.settings.hunterReleaseDelaySeconds() * 20;
        this.huntStarted = false;
        this.tickCounter = 0;
        this.gameTicks = 0L;

        // Apply head-start effects to hunters
        for (ServerPlayerEntity hunter : this.getHunters()) {
            this.applyLeadEffects(hunter);
        }

        // Broadcast game start
        this.broadcastMessage(
            Text.literal("✓ Manhunt game started!").formatted(Formatting.GREEN)
        );
        this.broadcastMessage(
            Text.literal("Hunters release in " + this.settings.hunterReleaseDelaySeconds() + "s.").formatted(Formatting.YELLOW)
        );

        if (this.leadTicksRemaining <= 0) {
            this.beginHunt();
        }
        this.updateScoreboard();
    }

    @Override
    public void stopGame() {
        this.state = GameState.ENDING;

        // Clean up
        this.playerRoles.clear();
        this.deadSpeedrunners.clear();
        this.aliveSpeedrunners.clear();
        this.hunterTrackingIndexes.clear();
        this.runnerTracking.clear();
        this.huntersNotifiedMissingNether.clear();
        this.huntersNotifiedEndPortalHint.clear();
        this.speedrunnerDeaths.clear();
        this.hunterDeaths.clear();
        this.pendingHunterRespawns.clear();
        this.eliminatedHunters.clear();
        this.compassCooldownUntilTicks.clear();
        this.speedrunnerRespawns.reset();
        this.clearVanillaTeams();

        // Remove all participants from the minigame
        MinigameManager.getInstance().clearParticipants();
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (!this.playerRoles.containsKey(player.getUuid())) {
            return; // Player is not part of this minigame
        }

        ManhuntRole role = this.playerRoles.get(player.getUuid());

        if (role == ManhuntRole.SPEEDRUNNER) {
            // Speedrunner died - move to spectator mode and end game
            this.handleSpeedrunnerDeath(player);
        } else if (role == ManhuntRole.HUNTER) {
            // Hunter died - they will respawn naturally
            this.handleHunterDeath(player);
        }
    }

    /**
     * Handles a Speedrunner's death.
     * Moves them to spectator mode and ends the game with Hunter victory.
     *
     * @param speedrunner the speedrunner who died
     */
    private void handleSpeedrunnerDeath(ServerPlayerEntity speedrunner) {
        if (this.state != GameState.IN_PROGRESS) {
            return;
        }

        int speedrunnerCount = this.getSpeedrunners().size();
        UUID speedrunnerUuid = speedrunner.getUuid();
        this.aliveSpeedrunners.remove(speedrunnerUuid);
        if (!this.deadSpeedrunners.contains(speedrunnerUuid)) {
            this.deadSpeedrunners.add(speedrunnerUuid);
        }
        int deaths = this.speedrunnerDeaths.merge(speedrunnerUuid, 1, Integer::sum);

        if (!this.hasLivesRemaining(this.settings.runnerLives(), deaths)) {
            speedrunner.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            this.broadcastMessage(
                Text.literal(speedrunner.getName().getString() + " (Speedrunner) is out of lives!").formatted(Formatting.RED)
            );
            if (this.aliveSpeedrunners.isEmpty()) {
                this.endGameWithHunterVictory();
            }
            return;
        }

        this.broadcastMessage(
            Text.literal(speedrunner.getName().getString() + " (Speedrunner) died and is waiting to respawn.").formatted(Formatting.RED)
        );

        if (this.aliveSpeedrunners.isEmpty() && speedrunnerCount <= 1) {
            this.speedrunnerRespawns.beginRespawn(speedrunner, this.gameTicks);
            this.updateHunterCompasses();
        } else if (this.aliveSpeedrunners.isEmpty()) {
            this.endGameWithHunterVictory();
        } else {
            this.speedrunnerRespawns.beginRespawn(speedrunner, this.gameTicks);
            this.speedrunnerRespawns.retargetWaitingRunners();
            this.updateHunterCompasses();
        }
    }

    /**
     * Handles a Hunter's death.
     * Currently, hunters simply respawn through normal Minecraft mechanics.
     * This method is here for extensibility (e.g., respawn delays, messages).
     *
     * @param hunter the hunter who died
     */
    private void handleHunterDeath(ServerPlayerEntity hunter) {
        UUID hunterUuid = hunter.getUuid();
        int deaths = this.hunterDeaths.merge(hunterUuid, 1, Integer::sum);
        if (!this.hasLivesRemaining(this.settings.hunterLives(), deaths)) {
            this.eliminatedHunters.add(hunterUuid);
            this.pendingHunterRespawns.remove(hunterUuid);
            this.broadcastMessage(
                Text.literal(hunter.getName().getString() + " (Hunter) is out of lives!").formatted(Formatting.RED)
            );
            if (this.getActiveHunters().isEmpty()) {
                this.endGameWithRunnerVictory(Text.literal("All hunters have been eliminated."));
            }
            return;
        }

        if (this.settings.hunterRespawnDelaySeconds() > 0) {
            this.pendingHunterRespawns.put(hunterUuid, this.gameTicks + (long) this.settings.hunterRespawnDelaySeconds() * 20L);
        }

        this.broadcastMessage(
            Text.literal(hunter.getName().getString() + " (Hunter) died but will respawn!").formatted(Formatting.YELLOW)
        );
    }

    /**
     * Ends the game with a Hunter victory.
     */
    private void endGameWithHunterVictory() {
        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);

        this.broadcastMessage(
            Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD)
        );
        this.broadcastMessage(
            Text.literal("🏆 HUNTERS WIN! 🏆").formatted(Formatting.GOLD)
        );
        this.broadcastMessage(
            Text.literal("All Speedrunners have been eliminated!").formatted(Formatting.GOLD)
        );
        this.broadcastMessage(
            Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD)
        );

        this.showGameOverTitle(Text.literal("Hunters Win"));
        this.clearScoreboard();
    }

    private void endGameWithRunnerVictory(Text reason) {
        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);

        this.broadcastMessage(
            Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD)
        );
        this.broadcastMessage(
            Text.literal("🏆 RUNNERS WIN! 🏆").formatted(Formatting.GOLD)
        );
        this.broadcastMessage(reason.copy().formatted(Formatting.GOLD));
        this.broadcastMessage(
            Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD)
        );

        this.showGameOverTitle(Text.literal("Runners Win"));
        this.clearScoreboard();
    }

    public void handleDragonDeath() {
        if (this.state != GameState.IN_PROGRESS) {
            return;
        }

        this.endGameWithRunnerVictory(Text.literal("The Ender Dragon has been defeated!"));
    }

    public void onServerTick(MinecraftServer server) {
        this.server = server;
        if (this.state == GameState.ENDING) {
            return;
        }

        this.gameTicks++;
        if (this.state == GameState.IN_PROGRESS) {
            this.speedrunnerRespawns.tick(this.gameTicks);
            this.tickHunterRespawns();
        }

        this.tickCounter++;
        if (this.tickCounter >= 20) {
            this.tickCounter = 0;
            this.updateRunnerTracking();
            this.pulseRunnerGlow();
            this.updateScoreboard();

            if (this.state == GameState.STARTING) {
                this.leadTicksRemaining = Math.max(0, this.leadTicksRemaining - 20);
                if (this.leadTicksRemaining <= 0) {
                    this.beginHunt();
                }
            } else if (this.state == GameState.IN_PROGRESS) {
                this.updateHunterCompasses();
                this.cleanupDroppedCompasses();
            }
        }
    }

    private void beginHunt() {
        if (this.huntStarted) {
            return;
        }
        this.huntStarted = true;
        this.state = GameState.IN_PROGRESS;
        MinigameManager.getInstance().setCurrentState(GameState.IN_PROGRESS);

        for (ServerPlayerEntity hunter : this.getActiveHunters()) {
            this.clearLeadEffects(hunter);
            if (this.settings.huntersCompassEnabled()) {
                this.grantHunterCompass(hunter, true);
            }
        }

        this.broadcastMessage(Text.literal("Hunters released!").formatted(Formatting.GREEN));
    }

    /**
     * Updates all hunter compasses to point to the nearest alive speedrunner.
     * Hunters in the Overworld, Nether, and End can all track speedrunners.
     */
    public void updateHunterCompasses() {
        if (this.state != GameState.IN_PROGRESS) {
            return;
        }

        this.pruneDisconnectedSpeedrunners();
        if (this.aliveSpeedrunners.isEmpty()) {
            this.endGameWithHunterVictory();
            return;
        }

        if (this.getActiveHunters().isEmpty()) {
            this.endGameWithRunnerVictory(Text.literal("All hunters have left the game."));
            return;
        }

        if (!this.settings.huntersCompassEnabled()) {
            return;
        }

        for (ServerPlayerEntity hunter : this.getActiveHunters()) {
            if (hunter.isDisconnected()) {
                continue;
            }

            this.syncHunterTracking(hunter, false);
        }
    }

    /**
     * Cycles the tracked speedrunner for a hunter to the next alive speedrunner.
     *
     * @param hunter the hunter who used the compass
     * @return the newly selected speedrunner, or null if none exist
     */
    @Nullable
    public ServerPlayerEntity cycleHunterTrackingTarget(ServerPlayerEntity hunter) {
        if (!this.settings.huntersCompassEnabled()) {
            hunter.sendMessage(Text.literal("Hunter compasses are disabled for this match."), true);
            return null;
        }

        long cooldownUntil = this.compassCooldownUntilTicks.getOrDefault(hunter.getUuid(), 0L);
        if (cooldownUntil > this.gameTicks) {
            long seconds = Math.max(1L, (cooldownUntil - this.gameTicks + 19L) / 20L);
            hunter.sendMessage(Text.literal("Compass cooldown: " + seconds + "s").formatted(Formatting.YELLOW), true);
            return this.getTrackedSpeedrunner(hunter);
        }

        if (this.aliveSpeedrunners.isEmpty()) {
            return null;
        }

        if (this.settings.compassCooldownSeconds() > 0) {
            this.compassCooldownUntilTicks.put(hunter.getUuid(), this.gameTicks + (long) this.settings.compassCooldownSeconds() * 20L);
        }
        int nextIndex = Math.floorMod(this.hunterTrackingIndexes.getOrDefault(hunter.getUuid(), -1) + 1, this.aliveSpeedrunners.size());
        this.hunterTrackingIndexes.put(hunter.getUuid(), nextIndex);
        this.syncHunterTracking(hunter, true);
        return this.getPlayerByUuid(this.aliveSpeedrunners.get(nextIndex));
    }

    /**
     * Returns the currently tracked speedrunner for a hunter.
     *
     * @param hunter the hunter
     * @return the tracked speedrunner, or null if none are alive
     */
    @Nullable
    public ServerPlayerEntity getTrackedSpeedrunner(ServerPlayerEntity hunter) {
        if (this.aliveSpeedrunners.isEmpty()) {
            return null;
        }

        int index = this.hunterTrackingIndexes.getOrDefault(hunter.getUuid(), 0);
        int normalizedIndex = Math.floorMod(index, this.aliveSpeedrunners.size());
        this.hunterTrackingIndexes.put(hunter.getUuid(), normalizedIndex);
        return this.getPlayerByUuid(this.aliveSpeedrunners.get(normalizedIndex));
    }

    /**
     * Synchronizes a hunter's tracking target and sends the current target name to the player.
     *
     * @param hunter the hunter to update
     */
    public void syncHunterTracking(ServerPlayerEntity hunter, boolean announce) {
        ServerPlayerEntity target = this.getTrackedSpeedrunner(hunter);
        if (target == null) {
            if (announce) {
                hunter.sendMessage(Text.literal("No speedrunners are currently alive."), true);
            }
            return;
        }

        this.setCompassTarget(hunter, target);
        if (announce) {
            hunter.sendMessage(
                Text.literal("Tracking: " + target.getName().getString() + " | Right-click compass to switch target.")
                    .formatted(Formatting.AQUA),
                true
            );
        }
    }

    private void setCompassTarget(ServerPlayerEntity hunter, ServerPlayerEntity target) {
        if (hunter.isDisconnected() || target.isDisconnected()) {
            return;
        }

        Optional<GlobalPos> trackingTarget = this.resolveTrackingTarget(hunter, target);
        if (trackingTarget.isEmpty()) {
            this.setCompassMad(hunter);
            return;
        }

        this.updateCompassStacks(hunter, trackingTarget.get(), false);
    }

    private Optional<GlobalPos> resolveTrackingTarget(ServerPlayerEntity hunter, ServerPlayerEntity target) {
        RunnerTrackingData data = this.runnerTracking.computeIfAbsent(target.getUuid(), uuid -> new RunnerTrackingData());
        RegistryKey<World> hunterDim = hunter.getEntityWorld().getRegistryKey();

        if (hunterDim.equals(World.NETHER)) {
            if (!this.settings.netherTrackingEnabled()) {
                return Optional.empty();
            }
            if (data.lastNether == null) {
                this.notifyMissingNether(hunter, target);
                return Optional.empty();
            }
            this.huntersNotifiedMissingNether.remove(hunter.getUuid());
            return Optional.of(GlobalPos.create(World.NETHER, data.lastNether));
        }

        if (hunterDim.equals(World.END)) {
            BlockPos endPos = data.lastEnd != null ? data.lastEnd : target.getBlockPos();
            return Optional.of(GlobalPos.create(World.END, endPos));
        }

        BlockPos overworldPos = data.lastOverworld != null ? data.lastOverworld : target.getBlockPos();
        if (target.getEntityWorld().getRegistryKey().equals(World.END) && data.endEntryOverworld != null) {
            overworldPos = data.endEntryOverworld;
            this.notifyEndPortalHint(hunter, target);
        }
        return Optional.of(GlobalPos.create(World.OVERWORLD, overworldPos));
    }

    private void notifyMissingNether(ServerPlayerEntity hunter, ServerPlayerEntity target) {
        if (this.huntersNotifiedMissingNether.add(hunter.getUuid())) {
            hunter.sendMessage(
                Text.literal("Looks like " + target.getName().getString() + " hasn't been in the Nether yet...")
                    .formatted(Formatting.GOLD),
                true
            );
        }
    }

    private void notifyEndPortalHint(ServerPlayerEntity hunter, ServerPlayerEntity target) {
        if (this.huntersNotifiedEndPortalHint.add(hunter.getUuid())) {
            hunter.sendMessage(
                Text.literal(target.getName().getString() + " is in the End. Compass points to their last Overworld location.")
                    .formatted(Formatting.LIGHT_PURPLE),
                true
            );
        }
    }

    private void updateCompassStacks(ServerPlayerEntity hunter, GlobalPos target, boolean tracked) {
        LodestoneTrackerComponent tracker = new LodestoneTrackerComponent(Optional.of(target), tracked);

        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            ItemStack stack = hunter.getInventory().getStack(slot);
            if (!this.isManhuntCompass(stack)) {
                continue;
            }

            stack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
        }
    }

    private void setCompassMad(ServerPlayerEntity hunter) {
        LodestoneTrackerComponent tracker = new LodestoneTrackerComponent(Optional.empty(), true);
        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            ItemStack stack = hunter.getInventory().getStack(slot);
            if (!this.isManhuntCompass(stack)) {
                continue;
            }

            stack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
        }
    }

    private void grantHunterCompass(ServerPlayerEntity hunter, boolean announce) {
        if (!this.hunterHasCompass(hunter)) {
            ItemStack stack = Items.COMPASS.getDefaultStack();
            this.markManhuntCompass(stack);
            hunter.getInventory().insertStack(stack);
        }
        this.hunterTrackingIndexes.put(hunter.getUuid(), 0);
        if (this.state == GameState.IN_PROGRESS) {
            this.syncHunterTracking(hunter, announce);
        }
    }

    private boolean hunterHasCompass(ServerPlayerEntity hunter) {
        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            if (this.isManhuntCompass(hunter.getInventory().getStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private boolean isManhuntCompass(ItemStack stack) {
        if (!stack.isOf(Items.COMPASS)) {
            return false;
        }

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        NbtCompound nbt = customData.copyNbt();
        return nbt.getBoolean(MANHUNT_COMPASS_TAG, false);
    }

    private void markManhuntCompass(ItemStack stack) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putBoolean(MANHUNT_COMPASS_TAG, true));
    }

    /**
     * Assigns a role to a player.
     *
     * @param player the player
     * @param role the role to assign
     */
    public void setPlayerRole(ServerPlayerEntity player, ManhuntRole role) {
        // Keep role changes safe if commands are used multiple times before start.
        UUID playerUuid = player.getUuid();
        this.aliveSpeedrunners.remove(playerUuid);
        this.deadSpeedrunners.remove(playerUuid);
        this.hunterTrackingIndexes.remove(player.getUuid());
        this.playerRoles.put(playerUuid, role);
        this.eliminatedHunters.remove(playerUuid);
        this.speedrunnerRespawns.removePlayer(player);

        if (this.state == GameState.IN_PROGRESS) {
            if (role == ManhuntRole.SPEEDRUNNER) {
                this.aliveSpeedrunners.add(playerUuid);
            } else if (role == ManhuntRole.HUNTER && this.settings.huntersCompassEnabled()) {
                this.grantHunterCompass(player, true);
            }
        }
        this.syncVanillaTeams();
    }

    /**
     * Gets the role of a player.
     *
     * @param player the player
     * @return the player's role, or null if not assigned
     */
    @Nullable
    public ManhuntRole getPlayerRole(ServerPlayerEntity player) {
        return this.playerRoles.get(player.getUuid());
    }

    /**
     * Gets all Speedrunners.
     *
     * @return a list of all speedrunners
     */
    public List<ServerPlayerEntity> getSpeedrunners() {
        return this.getSpeedrunnerUuids().stream()
            .map(this::getPlayerByUuid)
            .filter(Objects::nonNull)
            .toList();
    }

    private List<UUID> getSpeedrunnerUuids() {
        return this.playerRoles.entrySet().stream()
            .filter(entry -> entry.getValue() == ManhuntRole.SPEEDRUNNER)
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Gets all Hunters.
     *
     * @return a list of all hunters
     */
    public List<ServerPlayerEntity> getHunters() {
        return this.playerRoles.entrySet().stream()
            .filter(entry -> entry.getValue() == ManhuntRole.HUNTER)
            .map(entry -> this.getPlayerByUuid(entry.getKey()))
            .filter(Objects::nonNull)
            .toList();
    }

    public List<ServerPlayerEntity> getActiveHunters() {
        return this.playerRoles.entrySet().stream()
            .filter(entry -> entry.getValue() == ManhuntRole.HUNTER)
            .filter(entry -> !this.eliminatedHunters.contains(entry.getKey()))
            .map(entry -> this.getPlayerByUuid(entry.getKey()))
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Gets all alive Speedrunners.
     *
     * @return a list of all alive speedrunners
     */
    public List<ServerPlayerEntity> getAliveSpeedrunners() {
        return this.aliveSpeedrunners.stream()
            .map(this::getPlayerByUuid)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Gets all dead Speedrunners (now in spectator mode).
     *
     * @return a list of all dead speedrunners
     */
    public List<ServerPlayerEntity> getDeadSpeedrunners() {
        return this.deadSpeedrunners.stream()
            .map(this::getPlayerByUuid)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Broadcasts a message to all participants.
     *
     * @param message the message to broadcast
     */
    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(MinigameManager.getInstance().getParticipants(), message);
    }

    private void pruneDisconnectedSpeedrunners() {
        for (UUID speedrunnerUuid : new ArrayList<>(this.aliveSpeedrunners)) {
            ServerPlayerEntity speedrunner = this.getPlayerByUuid(speedrunnerUuid);
            if (speedrunner == null || speedrunner.isDisconnected()) {
                this.aliveSpeedrunners.remove(speedrunnerUuid);
            }
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    @Override
    public void setState(GameState state) {
        this.state = state;
    }

    /**
     * Checks if the game is currently in progress.
     *
     * @return true if the game is in progress, false otherwise
     */
    public boolean isGameActive() {
        return this.state == GameState.IN_PROGRESS;
    }

    /**
     * Returns true when the session has enough players assigned to start.
     * Every participant must have a role, and at least one speedrunner and one hunter are required.
     *
     * @return true if the match can start
     */
    public boolean canStartMatch() {
        return this.getUnassignedParticipants().isEmpty()
            && !this.getSpeedrunners().isEmpty()
            && !this.getHunters().isEmpty();
    }

    /**
     * Returns the participants that still need a role assigned.
     *
     * @return participants missing a role
     */
    public List<ServerPlayerEntity> getUnassignedParticipants() {
        List<ServerPlayerEntity> unassigned = new ArrayList<>();
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            if (!this.playerRoles.containsKey(participant.getUuid())) {
                unassigned.add(participant);
            }
        }
        return unassigned;
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        UUID playerUuid = oldPlayer.getUuid();
        ManhuntRole role = this.playerRoles.get(playerUuid);
        if (role == null) {
            return;
        }

        this.speedrunnerRespawns.handlePlayerRespawn(newPlayer);

        if (role == ManhuntRole.HUNTER && this.state == GameState.IN_PROGRESS) {
            if (this.eliminatedHunters.contains(playerUuid)) {
                newPlayer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            } else if (this.pendingHunterRespawns.containsKey(playerUuid)) {
                newPlayer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                newPlayer.sendMessage(Text.literal("Waiting to respawn as Hunter...").formatted(Formatting.YELLOW), true);
            } else if (this.settings.huntersCompassEnabled()) {
                this.grantHunterCompass(newPlayer, false);
            }
        } else if (role == ManhuntRole.SPEEDRUNNER && this.deadSpeedrunners.contains(playerUuid)) {
            newPlayer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            this.speedrunnerRespawns.handlePlayerRespawn(newPlayer);
        }
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        ManhuntRole role = this.playerRoles.remove(playerUuid);
        if (role == null) {
            return;
        }

        this.aliveSpeedrunners.remove(playerUuid);
        this.deadSpeedrunners.remove(playerUuid);
        this.hunterTrackingIndexes.remove(player.getUuid());
        this.runnerTracking.remove(player.getUuid());
        this.huntersNotifiedMissingNether.remove(player.getUuid());
        this.huntersNotifiedEndPortalHint.remove(player.getUuid());
        this.speedrunnerRespawns.removePlayer(player);
        this.pendingHunterRespawns.remove(playerUuid);
        this.eliminatedHunters.remove(playerUuid);
        this.compassCooldownUntilTicks.remove(playerUuid);

        if (role == ManhuntRole.SPEEDRUNNER && this.state == GameState.IN_PROGRESS && this.aliveSpeedrunners.isEmpty()) {
            this.broadcastMessage(
                Text.literal(player.getName().getString() + " (Speedrunner) left the game.").formatted(Formatting.RED)
            );
            this.endGameWithHunterVictory();
        }
        this.syncVanillaTeams();
    }

    public void setSpeedrunnerRespawnDelaySeconds(int seconds) {
        this.applySettings(this.settings.withSpeedrunnerRespawnDelaySeconds(seconds));
    }

    public int getSpeedrunnerRespawnDelaySeconds() {
        return this.speedrunnerRespawns.getRespawnDelaySeconds();
    }

    public boolean shouldCancelDamage(ServerPlayerEntity player) {
        return this.speedrunnerRespawns.isProtected(player, this.gameTicks);
    }

    public ManhuntSettings getSettings() {
        return this.settings;
    }

    public void applySettings(ManhuntSettings settings) {
        this.settings = settings == null ? ManhuntSettings.defaults() : settings;
        this.speedrunnerRespawns.setRespawnDelaySeconds(this.settings.speedrunnerRespawnDelaySeconds());
    }

    ServerPlayerEntity getPlayerByUuid(UUID uuid) {
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            if (participant.getUuid().equals(uuid)) {
                return participant;
            }
        }
        return this.server == null ? null : this.server.getPlayerManager().getPlayer(uuid);
    }

    ServerPlayerEntity getAliveSpeedrunnerByUuid(UUID uuid) {
        if (this.aliveSpeedrunners.contains(uuid)) {
            return this.getPlayerByUuid(uuid);
        }
        return null;
    }

    void completeSpeedrunnerRespawn(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        this.deadSpeedrunners.remove(playerUuid);
        if (!this.aliveSpeedrunners.contains(playerUuid)) {
            this.aliveSpeedrunners.add(playerUuid);
        }
        this.updateHunterCompasses();
        this.updateScoreboard();
    }

    void endGameBecauseNoAliveSpeedrunners() {
        if (this.state == GameState.IN_PROGRESS) {
            this.endGameWithHunterVictory();
        }
    }

    void broadcastManhuntMessage(Text message) {
        this.broadcastMessage(message);
    }

    private void updateRunnerTracking() {
        for (UUID speedrunnerUuid : this.aliveSpeedrunners) {
            ServerPlayerEntity speedrunner = this.getPlayerByUuid(speedrunnerUuid);
            if (speedrunner == null) {
                continue;
            }
            if (speedrunner.isDisconnected()) {
                continue;
            }

            RunnerTrackingData data = this.runnerTracking.computeIfAbsent(speedrunner.getUuid(), uuid -> new RunnerTrackingData());
            RegistryKey<World> dimension = speedrunner.getEntityWorld().getRegistryKey();
            BlockPos position = speedrunner.getBlockPos();

            if (dimension.equals(World.OVERWORLD)) {
                data.lastOverworld = position;
            } else if (dimension.equals(World.NETHER)) {
                if (this.settings.netherTrackingEnabled()) {
                    data.lastNether = position;
                }
            } else if (dimension.equals(World.END)) {
                data.lastEnd = position;
                if (data.endEntryOverworld == null && data.lastOverworld != null) {
                    data.endEntryOverworld = data.lastOverworld;
                }
            }
        }
    }

    private void tickHunterRespawns() {
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(this.pendingHunterRespawns.entrySet())) {
            if (entry.getValue() > this.gameTicks) {
                continue;
            }

            ServerPlayerEntity hunter = this.getPlayerByUuid(entry.getKey());
            if (hunter == null || hunter.isDisconnected()) {
                continue;
            }

            this.pendingHunterRespawns.remove(entry.getKey());
            hunter.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            hunter.setHealth(hunter.getMaxHealth());
            hunter.getHungerManager().setFoodLevel(20);
            hunter.getHungerManager().setSaturationLevel(20.0F);
            if (this.settings.huntersCompassEnabled()) {
                this.grantHunterCompass(hunter, false);
            }
            hunter.sendMessage(Text.literal("You are back in the hunt.").formatted(Formatting.GREEN), true);
        }
    }

    private void pulseRunnerGlow() {
        if (this.settings.runnerGlowPulseMinutes() <= 0 || this.state != GameState.IN_PROGRESS) {
            return;
        }

        int intervalTicks = this.settings.runnerGlowPulseMinutes() * 60 * 20;
        if (intervalTicks <= 0 || this.gameTicks % intervalTicks != 0) {
            return;
        }

        for (ServerPlayerEntity speedrunner : this.getAliveSpeedrunners()) {
            speedrunner.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 10 * 20, 0, true, false, true));
        }
        this.broadcastMessage(Text.literal("Speedrunners are glowing briefly.").formatted(Formatting.GOLD));
    }

    private void cleanupDroppedCompasses() {
        if (this.server == null) {
            return;
        }

        for (ServerWorld world : this.server.getWorlds()) {
            List<? extends ItemEntity> items = world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class),
                entity -> this.isManhuntCompass(entity.getStack()));
            for (ItemEntity item : items) {
                item.discard();
            }
        }
    }

    private void updateScoreboard() {
        if (this.server == null) {
            return;
        }
        this.syncVanillaTeams();

        SCOREBOARD.setScore(this.server, "Speedrunners", this.getSpeedrunners().size());
        SCOREBOARD.setScore(this.server, "Alive Runners", this.aliveSpeedrunners.size());
        SCOREBOARD.setScore(this.server, "Hunters", this.getActiveHunters().size());
        SCOREBOARD.setScore(this.server, "Runner Lives", this.settings.runnerLives());
        SCOREBOARD.setScore(this.server, "Hunter Lives", this.settings.hunterLives());

        if (this.state == GameState.STARTING) {
            int secondsLeft = Math.max(0, this.leadTicksRemaining / 20);
            SCOREBOARD.setScore(this.server, "Lead", secondsLeft);
        } else {
            SCOREBOARD.setScore(this.server, "Lead", 0);
        }
    }

    private void clearScoreboard() {
        if (this.server == null) {
            return;
        }

        SCOREBOARD.clear(this.server);
        this.clearVanillaTeams();
    }

    private void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }

        List<VanillaTeamDescriptor> descriptors = new ArrayList<>();
        VanillaTeamOptions runnerOptions = VanillaTeamOptions.defaults()
            .withColor(Formatting.GREEN)
            .withPrefix(Text.literal("[RUNNER] ").formatted(Formatting.GREEN))
            .withFriendlyFireAllowed(true)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
        VanillaTeamOptions hunterOptions = VanillaTeamOptions.defaults()
            .withColor(Formatting.RED)
            .withPrefix(Text.literal("[HUNTER] ").formatted(Formatting.RED))
            .withFriendlyFireAllowed(true)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
        VanillaTeamOptions deadOptions = VanillaTeamOptions.defaults()
            .withColor(Formatting.DARK_GRAY)
            .withPrefix(Text.literal("[DEAD] ").formatted(Formatting.DARK_GRAY))
            .withFriendlyFireAllowed(false)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);

        descriptors.add(new VanillaTeamDescriptor("runners", Text.literal("Speedrunners"), this.getAliveSpeedrunners(), runnerOptions));
        descriptors.add(new VanillaTeamDescriptor("hunters", Text.literal("Hunters"), this.getActiveHunters(), hunterOptions));
        List<ServerPlayerEntity> dead = this.deadSpeedrunners.stream()
            .map(this::getPlayerByUuid)
            .filter(Objects::nonNull)
            .toList();
        descriptors.add(new VanillaTeamDescriptor("dead", Text.literal("Dead"), dead, deadOptions));
        this.vanillaTeams.sync(this.server, descriptors);
    }

    private void clearVanillaTeams() {
        if (this.server != null) {
            this.vanillaTeams.clear(this.server);
        }
    }

    private void showGameOverTitle(Text title) {
        GameMessenger.showGameOverTitle(MinigameManager.getInstance().getParticipants(), title);
    }

    private void applyLeadEffects(ServerPlayerEntity hunter) {
        int leadTicks = this.settings.hunterReleaseDelaySeconds() * 20;
        if (leadTicks <= 0) {
            return;
        }
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, leadTicks, 3, true, false, false));
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, leadTicks, 1, true, false, false));
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, leadTicks, 3, true, false, false));
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, leadTicks, 3, true, false, false));
    }

    private void clearLeadEffects(ServerPlayerEntity hunter) {
        hunter.removeStatusEffect(StatusEffects.SLOWNESS);
        hunter.removeStatusEffect(StatusEffects.BLINDNESS);
        hunter.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        hunter.removeStatusEffect(StatusEffects.WEAKNESS);
    }

    public enum ManhuntRole {
        SPEEDRUNNER("Speedrunner"),
        HUNTER("Hunter");

        private final String displayName;

        ManhuntRole(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return this.displayName;
        }
    }

    private static final class RunnerTrackingData {
        private BlockPos lastOverworld;
        private BlockPos lastNether;
        private BlockPos lastEnd;
        private BlockPos endEntryOverworld;
    }

    private boolean hasLivesRemaining(int lives, int deaths) {
        return lives == ManhuntSettings.UNLIMITED_LIVES || deaths < lives;
    }
}
