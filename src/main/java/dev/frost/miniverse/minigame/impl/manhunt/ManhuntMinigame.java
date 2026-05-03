package dev.frost.miniverse.minigame.impl.manhunt;

import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
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
    private final Map<ServerPlayerEntity, ManhuntRole> playerRoles = new ConcurrentHashMap<>();

    // Track dead speedrunners who are now spectators
    private final List<ServerPlayerEntity> deadSpeedrunners = new CopyOnWriteArrayList<>();

    // Track currently alive speedrunners
    private final List<ServerPlayerEntity> aliveSpeedrunners = new CopyOnWriteArrayList<>();

    // Track which speedrunner each hunter is currently following
    private final Map<UUID, Integer> hunterTrackingIndexes = new ConcurrentHashMap<>();

    private static final String MANHUNT_COMPASS_TAG = "Manhunt_tracker";
    private static final String SCOREBOARD_OBJECTIVE = "manhunt_display";
    private static final int LEAD_SECONDS = 10;
    private static final int LEAD_TICKS = LEAD_SECONDS * 20;

    private final Map<UUID, RunnerTrackingData> runnerTracking = new ConcurrentHashMap<>();
    private final Set<UUID> huntersNotifiedMissingNether = ConcurrentHashMap.newKeySet();
    private final ManhuntSpeedrunnerRespawnSystem speedrunnerRespawns = new ManhuntSpeedrunnerRespawnSystem(this);
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
        this.speedrunnerRespawns.setRespawnDelaySeconds(
            Integer.getInteger("miniverse.manhunt.respawnDelaySeconds", ManhuntSpeedrunnerRespawnSystem.DEFAULT_RESPAWN_DELAY_SECONDS)
        );
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.playerRoles.clear();
        this.deadSpeedrunners.clear();
        this.aliveSpeedrunners.clear();
        this.hunterTrackingIndexes.clear();
        this.speedrunnerRespawns.reset();
        this.gameTicks = 0L;
    }

    @Override
    public void startGame() {
        this.state = GameState.STARTING;
        MinigameManager.getInstance().setCurrentState(GameState.STARTING);

        // Initialize tracking
        this.aliveSpeedrunners.clear();
        this.aliveSpeedrunners.addAll(this.getSpeedrunners());
        this.deadSpeedrunners.clear();
        this.hunterTrackingIndexes.clear();
        this.runnerTracking.clear();
        this.huntersNotifiedMissingNether.clear();
        this.speedrunnerRespawns.reset();
        this.leadTicksRemaining = LEAD_TICKS;
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
            Text.literal("Hunters have a " + LEAD_SECONDS + "s head start.").formatted(Formatting.YELLOW)
        );

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
        this.speedrunnerRespawns.reset();

        // Remove all participants from the minigame
        MinigameManager.getInstance().clearParticipants();
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (!this.playerRoles.containsKey(player)) {
            return; // Player is not part of this minigame
        }

        ManhuntRole role = this.playerRoles.get(player);

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
        this.aliveSpeedrunners.remove(speedrunner);
        if (!this.deadSpeedrunners.contains(speedrunner)) {
            this.deadSpeedrunners.add(speedrunner);
        }

        if (!this.speedrunnerRespawns.shouldHandleDeath(speedrunnerCount)) {
            speedrunner.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            this.broadcastMessage(
                Text.literal(speedrunner.getName().getString() + " (Speedrunner) died!").formatted(Formatting.RED)
            );
            this.endGameWithHunterVictory();
            return;
        }

        this.broadcastMessage(
            Text.literal(speedrunner.getName().getString() + " (Speedrunner) died and is waiting to respawn.").formatted(Formatting.RED)
        );

        if (this.aliveSpeedrunners.isEmpty()) {
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
        // Hunters can die and respawn freely in Manhunt
        // This method can be extended to add custom death messages or respawn mechanics
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
        }

        this.tickCounter++;
        if (this.tickCounter >= 20) {
            this.tickCounter = 0;
            this.updateRunnerTracking();
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

        for (ServerPlayerEntity hunter : this.getHunters()) {
            this.clearLeadEffects(hunter);
            this.grantHunterCompass(hunter, true);
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

        if (this.getHunters().isEmpty()) {
            this.endGameWithRunnerVictory(Text.literal("All hunters have left the game."));
            return;
        }

        for (ServerPlayerEntity hunter : this.getHunters()) {
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
        if (this.aliveSpeedrunners.isEmpty()) {
            return null;
        }

        int nextIndex = Math.floorMod(this.hunterTrackingIndexes.getOrDefault(hunter.getUuid(), -1) + 1, this.aliveSpeedrunners.size());
        this.hunterTrackingIndexes.put(hunter.getUuid(), nextIndex);
        this.syncHunterTracking(hunter, true);
        return this.aliveSpeedrunners.get(nextIndex);
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
        return this.aliveSpeedrunners.get(normalizedIndex);
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
        this.aliveSpeedrunners.remove(player);
        this.deadSpeedrunners.remove(player);
        this.hunterTrackingIndexes.remove(player.getUuid());
        this.playerRoles.put(player, role);
        this.speedrunnerRespawns.removePlayer(player);

        if (this.state == GameState.IN_PROGRESS) {
            if (role == ManhuntRole.SPEEDRUNNER) {
                this.aliveSpeedrunners.add(player);
            } else if (role == ManhuntRole.HUNTER) {
                this.grantHunterCompass(player, true);
            }
        }
    }

    /**
     * Gets the role of a player.
     *
     * @param player the player
     * @return the player's role, or null if not assigned
     */
    @Nullable
    public ManhuntRole getPlayerRole(ServerPlayerEntity player) {
        return this.playerRoles.get(player);
    }

    /**
     * Gets all Speedrunners.
     *
     * @return a list of all speedrunners
     */
    public List<ServerPlayerEntity> getSpeedrunners() {
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
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Gets all alive Speedrunners.
     *
     * @return a list of all alive speedrunners
     */
    public List<ServerPlayerEntity> getAliveSpeedrunners() {
        return new ArrayList<>(this.aliveSpeedrunners);
    }

    /**
     * Gets all dead Speedrunners (now in spectator mode).
     *
     * @return a list of all dead speedrunners
     */
    public List<ServerPlayerEntity> getDeadSpeedrunners() {
        return new ArrayList<>(this.deadSpeedrunners);
    }

    /**
     * Broadcasts a message to all participants.
     *
     * @param message the message to broadcast
     */
    private void broadcastMessage(Text message) {
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            participant.sendMessage(message);
        }
    }

    private void pruneDisconnectedSpeedrunners() {
        for (ServerPlayerEntity speedrunner : new ArrayList<>(this.aliveSpeedrunners)) {
            if (speedrunner.isDisconnected()) {
                this.aliveSpeedrunners.remove(speedrunner);
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
            if (!this.playerRoles.containsKey(participant)) {
                unassigned.add(participant);
            }
        }
        return unassigned;
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        ManhuntRole role = this.playerRoles.remove(oldPlayer);
        if (role == null) {
            return;
        }

        this.playerRoles.put(newPlayer, role);

        if (this.aliveSpeedrunners.remove(oldPlayer)) {
            this.aliveSpeedrunners.add(newPlayer);
        }

        if (this.deadSpeedrunners.remove(oldPlayer)) {
            this.deadSpeedrunners.add(newPlayer);
        }

        this.speedrunnerRespawns.handlePlayerRespawn(newPlayer);

        if (role == ManhuntRole.HUNTER && this.state == GameState.IN_PROGRESS) {
            this.grantHunterCompass(newPlayer, false);
        } else if (role == ManhuntRole.SPEEDRUNNER && this.deadSpeedrunners.contains(newPlayer)) {
            newPlayer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            this.speedrunnerRespawns.handlePlayerRespawn(newPlayer);
        }
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        ManhuntRole role = this.playerRoles.remove(player);
        if (role == null) {
            return;
        }

        this.aliveSpeedrunners.remove(player);
        this.deadSpeedrunners.remove(player);
        this.hunterTrackingIndexes.remove(player.getUuid());
        this.runnerTracking.remove(player.getUuid());
        this.huntersNotifiedMissingNether.remove(player.getUuid());
        this.speedrunnerRespawns.removePlayer(player);

        if (role == ManhuntRole.SPEEDRUNNER && this.state == GameState.IN_PROGRESS && this.aliveSpeedrunners.isEmpty()) {
            this.broadcastMessage(
                Text.literal(player.getName().getString() + " (Speedrunner) left the game.").formatted(Formatting.RED)
            );
            this.endGameWithHunterVictory();
        }
    }

    public void setSpeedrunnerRespawnDelaySeconds(int seconds) {
        this.speedrunnerRespawns.setRespawnDelaySeconds(seconds);
    }

    public int getSpeedrunnerRespawnDelaySeconds() {
        return this.speedrunnerRespawns.getRespawnDelaySeconds();
    }

    public boolean shouldCancelDamage(ServerPlayerEntity player) {
        return this.speedrunnerRespawns.isProtected(player, this.gameTicks);
    }

    ServerPlayerEntity getParticipantByUuid(UUID uuid) {
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            if (participant.getUuid().equals(uuid)) {
                return participant;
            }
        }
        return null;
    }

    ServerPlayerEntity getAliveSpeedrunnerByUuid(UUID uuid) {
        for (ServerPlayerEntity speedrunner : this.aliveSpeedrunners) {
            if (speedrunner.getUuid().equals(uuid)) {
                return speedrunner;
            }
        }
        return null;
    }

    void completeSpeedrunnerRespawn(ServerPlayerEntity player) {
        this.deadSpeedrunners.remove(player);
        if (!this.aliveSpeedrunners.contains(player)) {
            this.aliveSpeedrunners.add(player);
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
        for (ServerPlayerEntity speedrunner : this.aliveSpeedrunners) {
            if (speedrunner.isDisconnected()) {
                continue;
            }

            RunnerTrackingData data = this.runnerTracking.computeIfAbsent(speedrunner.getUuid(), uuid -> new RunnerTrackingData());
            RegistryKey<World> dimension = speedrunner.getEntityWorld().getRegistryKey();
            BlockPos position = speedrunner.getBlockPos();

            if (dimension.equals(World.OVERWORLD)) {
                data.lastOverworld = position;
            } else if (dimension.equals(World.NETHER)) {
                data.lastNether = position;
            } else if (dimension.equals(World.END)) {
                data.lastEnd = position;
            }
        }
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

        Scoreboard scoreboard = this.server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectives().stream()
            .filter(obj -> SCOREBOARD_OBJECTIVE.equals(obj.getName()))
            .findFirst()
            .orElseGet(() -> scoreboard.addObjective(
                SCOREBOARD_OBJECTIVE,
                ScoreboardCriterion.DUMMY,
                Text.literal("Manhunt"),
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                null
            ));

        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);

        this.setScore(scoreboard, objective, "Speedrunners", this.getSpeedrunners().size());
        this.setScore(scoreboard, objective, "Hunters", this.getHunters().size());
        this.setScore(scoreboard, objective, "State", this.state == null ? 0 : this.state.ordinal());

        if (this.state == GameState.STARTING) {
            int secondsLeft = Math.max(0, this.leadTicksRemaining / 20);
            this.setScore(scoreboard, objective, "Lead", secondsLeft);
        } else {
            this.setScore(scoreboard, objective, "Lead", 0);
        }
    }

    private void setScore(Scoreboard scoreboard, ScoreboardObjective objective, String label, int value) {
        ScoreHolder holder = ScoreHolder.fromName(label);
        ScoreAccess score = scoreboard.getOrCreateScore(holder, objective);
        score.setScore(value);
    }

    private void clearScoreboard() {
        if (this.server == null) {
            return;
        }

        Scoreboard scoreboard = this.server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectives().stream()
            .filter(obj -> SCOREBOARD_OBJECTIVE.equals(obj.getName()))
            .findFirst()
            .orElse(null);

        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }

    private void showGameOverTitle(Text title) {
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            participant.networkHandler.sendPacket(new TitleS2CPacket(title.copy().formatted(Formatting.GOLD)));
        }
    }

    private void applyLeadEffects(ServerPlayerEntity hunter) {
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, LEAD_TICKS, 3, true, false, false));
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, LEAD_TICKS, 1, true, false, false));
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, LEAD_TICKS, 3, true, false, false));
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, LEAD_TICKS, 3, true, false, false));
    }

    private void clearLeadEffects(ServerPlayerEntity hunter) {
        hunter.removeStatusEffect(StatusEffects.SLOWNESS);
        hunter.removeStatusEffect(StatusEffects.BLINDNESS);
        hunter.removeStatusEffect(StatusEffects.MINING_FATIGUE);
        hunter.removeStatusEffect(StatusEffects.WEAKNESS);
    }

    private static final class RunnerTrackingData {
        private BlockPos lastOverworld;
        private BlockPos lastNether;
        private BlockPos lastEnd;
    }
}
