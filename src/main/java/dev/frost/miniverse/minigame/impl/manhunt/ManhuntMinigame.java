package dev.frost.miniverse.minigame.impl.manhunt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.PauseAwareMinigame;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.ItemUseAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.item.ProtectedItemRule;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTypes;
import dev.frost.miniverse.minigame.core.item.TrackingItemNameFormatter;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.persistence.SessionData;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamDescriptor;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamMembership;
import dev.frost.miniverse.team.TeamRole;
import dev.frost.miniverse.team.TeamSnapshot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import dev.frost.miniverse.minigame.core.tracker.PlayerTracker;

/**
 * Manhunt minigame implementation.
 * In this game, Speedrunners try to reach the End while Hunters try to stop them.
 * If a Speedrunner dies, the Hunters win. Hunters can respawn upon death.
 */
public class ManhuntMinigame extends dev.frost.miniverse.minigame.core.AbstractMinigame implements TeamManagerProvider {
    private static final String NAME = "Manhunt";

    private GameState state;

    private final TeamManager teams = new TeamManager();

    // Track dead speedrunners who are now spectators
    private final List<UUID> deadSpeedrunners = new CopyOnWriteArrayList<>();

    // Track currently alive speedrunners
    private final List<UUID> aliveSpeedrunners = new CopyOnWriteArrayList<>();

    // Track which speedrunner each hunter is currently following
    private final Map<UUID, Integer> hunterTrackingIndexes = new ConcurrentHashMap<>();

    private static final String TRACKER_TYPE = ProtectedItemTypes.TRACKER_COMPASS;

    private final PlayerTracker playerTracker = new PlayerTracker();
    private final Set<String> huntersNotifiedMissingNether = ConcurrentHashMap.newKeySet();
    private final Set<String> huntersNotifiedEndPortalHint = ConcurrentHashMap.newKeySet();
    private final ManhuntSpeedrunnerRespawnSystem speedrunnerRespawns = new ManhuntSpeedrunnerRespawnSystem(this);
    private final Map<UUID, Integer> speedrunnerDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hunterDeaths = new ConcurrentHashMap<>();
    private final Map<UUID, Long> pendingHunterRespawns = new ConcurrentHashMap<>();
    private final Set<UUID> eliminatedHunters = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> compassCooldownUntilTicks = new ConcurrentHashMap<>();
    private final Set<UUID> pendingLateJoinTeleports = new HashSet<>();
    private int graceSecondsRemaining;
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
    }

    public void setVanillaFriendlyFireAllowed(boolean allowed) {
        if (this.getVanillaTeams() != null) {
            this.getVanillaTeams().setFriendlyFireAllowed(allowed);
            this.syncVanillaTeams();
        }
    }

    public void setVanillaTeammateCollisionAllowed(boolean allowed) {
        if (this.getVanillaTeams() != null) {
            this.getVanillaTeams().setTeammateCollisionAllowed(allowed);
            this.syncVanillaTeams();
        }
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.teams.clear();
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
        this.pendingLateJoinTeleports.clear();
        this.speedrunnerRespawns.reset();
        this.gameTicks = 0L;
    }

    @Override
    protected dev.frost.miniverse.minigame.core.rules.GlobalMatchRules configureGameRules() {
        return dev.frost.miniverse.minigame.core.rules.GlobalMatchRules.defaults().withImmediateRespawn(true);
    }

    @Override
    protected void onMatchStart() {
        this.state = GameState.RUNNING;
        this.setRuntimeState(GameState.RUNNING);
        this.registerProtectedItems();

        // Initialize tracking
        this.aliveSpeedrunners.clear();
        this.aliveSpeedrunners.addAll(this.getSpeedrunnerUuids());
        this.deadSpeedrunners.clear();
        this.hunterTrackingIndexes.clear();
        this.playerTracker.clear();
        this.huntersNotifiedMissingNether.clear();
        this.huntersNotifiedEndPortalHint.clear();
        this.speedrunnerDeaths.clear();
        this.hunterDeaths.clear();
        this.pendingHunterRespawns.clear();
        this.eliminatedHunters.clear();
        this.compassCooldownUntilTicks.clear();
        this.pendingLateJoinTeleports.clear();
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

        if (this.leadTicksRemaining > 0) {
            this.sendLeadActionBar(this.leadSecondsRemaining());
        }

        if (this.leadTicksRemaining <= 0) {
            this.beginHunt();
        }
    }

    @Override
    protected void onMatchEnd() {
        this.state = GameState.ENDING;

        // Clean up
        for (ServerPlayerEntity hunter : this.getHunters()) {
            this.clearLeadEffects(hunter);
        }
        this.teams.clear();
        this.deadSpeedrunners.clear();
        this.aliveSpeedrunners.clear();
        this.hunterTrackingIndexes.clear();
        this.playerTracker.clear();
        this.huntersNotifiedMissingNether.clear();
        this.huntersNotifiedEndPortalHint.clear();
        this.speedrunnerDeaths.clear();
        this.hunterDeaths.clear();
        this.pendingHunterRespawns.clear();
        this.eliminatedHunters.clear();
        this.compassCooldownUntilTicks.clear();
        this.pendingLateJoinTeleports.clear();
        this.speedrunnerRespawns.reset();
        ProtectedItemService.getInstance().removeRule(TRACKER_TYPE);
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        ManhuntRole role = this.roleFor(player.getUuid());
        if (role == null) {
            return; // Player is not part of this minigame
        }

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
        if (!this.state.isActive()) {
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
            this.speedrunnerRespawns.beginEliminatedSpectate(speedrunner);
            this.broadcastMessage(
                Text.literal(speedrunner.getName().getString() + " (Speedrunner) is out of lives!").formatted(Formatting.RED)
            );
            if (this.aliveSpeedrunners.isEmpty()) {
                this.endGameWithHunterVictory();
            }
            this.syncVanillaTeams();
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
        this.syncVanillaTeams();
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
    }

    /**
     * Ends the game with a Hunter victory.
     */
    private void endGameWithHunterVictory() {
        this.state = GameState.ENDING;
        this.setRuntimeState(GameState.ENDING);

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

        this.startStandardEndSequence(this.getActiveHunters(), Text.literal("Hunters"));
        this.clearVanillaTeams();
    }

    private void endGameWithRunnerVictory(Text reason) {
        this.state = GameState.ENDING;
        this.setRuntimeState(GameState.ENDING);

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

        this.startStandardEndSequence(this.getSpeedrunners(), Text.literal("Runners"));
        this.clearVanillaTeams();
    }

    private void startStandardEndSequence(Collection<ServerPlayerEntity> winners, Text winnerLabel) {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            MatchLifecycleController.getInstance().endMatch(
                runtime,
                MatchEndResult.winners(winners, winnerLabel),
                MatchLifecycleOptions.defaults(NAME)
            );
        }
    }

    public void handleDragonDeath() {
        if (!this.state.isActive()) {
            return;
        }

        this.endGameWithRunnerVictory(Text.literal("The Ender Dragon has been defeated!"));
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;
        if (this.state.isTerminal()) {
            return;
        }

        this.gameTicks++;
        if (this.state == GameState.RUNNING) {
            this.speedrunnerRespawns.tick(this.gameTicks);
            this.tickHunterRespawns();
        }

        this.tickCounter++;
        if (this.tickCounter >= 20) {
            this.tickCounter = 0;
            this.updateRunnerTracking();
            this.pulseRunnerGlow();

            if (this.state == GameState.RUNNING && !this.huntStarted) {
                this.sendLeadActionBar(this.leadSecondsRemaining());
                this.leadTicksRemaining = Math.max(0, this.leadTicksRemaining - 20);
                if (this.leadTicksRemaining <= 0) {
                    this.beginHunt();
                }
            } else if (this.state == GameState.RUNNING && this.huntStarted) {
                this.updateHunterCompasses();
            }
        }
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand) {
        if (!this.isGameActive()) {
            return ActionResult.PASS;
        }

        if (this.isParticipant(player) && this.getPlayerRole(player) == ManhuntRole.SPEEDRUNNER
            && this.deadSpeedrunners.contains(player.getUuid()) && player.isSpectator()) {
            if (this.speedrunnerRespawns.cycleSpectatorTarget(player, true)) {
                ServerPlayerEntity target = this.speedrunnerRespawns.getSpectatorTarget(player);
                if (target != null) {
                    player.sendMessage(
                        Text.literal("Spectating: " + target.getName().getString() + " (right-click to switch)")
                            .formatted(Formatting.AQUA),
                        true
                    );
                }
            } else {
                player.sendMessage(Text.literal("No alive speedrunners to spectate.").formatted(Formatting.YELLOW), true);
            }
            return ActionResult.SUCCESS;
        }

        if (!player.getStackInHand(hand).isOf(Items.COMPASS)) {
            return ActionResult.PASS;
        }

        if (!this.isParticipant(player) || this.getPlayerRole(player) != ManhuntRole.HUNTER) {
            return ActionResult.PASS;
        }

        ServerPlayerEntity target = this.cycleHunterTrackingTarget(player);
        if (target == null) {
            player.sendMessage(Text.literal("No speedrunners are alive to track."), true);
            return ActionResult.SUCCESS;
        }

        player.sendMessage(
            Text.literal("Tracking now: " + target.getName().getString() + " (right-click again to switch)"),
            true
        );
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (!this.isParticipant(player)) {
            return true;
        }

        if (this.shouldCancelDamage(player)) {
            return false;
        }

        return true;
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof EnderDragonEntity) {
            this.handleDragonDeath();
            return;
        }

        if (entity instanceof ServerPlayerEntity player && this.isParticipant(player)) {
            this.onPlayerDeath(player);
        }
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (this.isParticipant(oldPlayer)) {
            this.replaceParticipant(oldPlayer, newPlayer);
            this.handlePlayerRespawn(oldPlayer, newPlayer);
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.isParticipant(player)) {
            this.handlePlayerLeave(player);
        }
    }

    private void beginHunt() {
        if (this.huntStarted) {
            return;
        }
        this.huntStarted = true;

        for (ServerPlayerEntity hunter : this.getActiveHunters()) {
            this.clearLeadEffects(hunter);
            if (this.settings.huntersCompassEnabled()) {
                this.grantHunterCompass(hunter, true);
            }
        }

        this.sendActionBarToHunters(Text.literal("GO!").formatted(Formatting.GREEN));
        this.sendActionBarToSpeedrunners(Text.literal("Hunters are released!").formatted(Formatting.RED));
        this.broadcastMessage(Text.literal("Hunters released!").formatted(Formatting.GREEN));
    }

    /**
     * Updates all hunter compasses to point to the nearest alive speedrunner.
     * Hunters in the Overworld, Nether, and End can all track speedrunners.
     */
    public void updateHunterCompasses() {
        if (this.state != GameState.RUNNING || !this.huntStarted) {
            return;
        }

        this.pruneDisconnectedSpeedrunners();
        if (this.aliveSpeedrunners.isEmpty() && !this.speedrunnerRespawns.hasAnyPendingRespawns()) {
            if (!MatchLifecycleController.getInstance().isDisconnectGraceActive()) {
                this.endGameWithHunterVictory();
            }
            return;
        }

        if (this.getActiveHunters().isEmpty() && !this.hasHunterInReconnectGrace()) {
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
        int initialIndex = this.hunterTrackingIndexes.getOrDefault(hunter.getUuid(), -1);
        int nextIndex = initialIndex;
        ServerPlayerEntity target = null;
        for (int i = 0; i < this.aliveSpeedrunners.size(); i++) {
            nextIndex = Math.floorMod(nextIndex + 1, this.aliveSpeedrunners.size());
            target = this.getPlayerByUuid(this.aliveSpeedrunners.get(nextIndex));
            if (target != null && !target.isDisconnected()) {
                break;
            }
        }
        if (target == null || target.isDisconnected()) {
            return null;
        }
        this.hunterTrackingIndexes.put(hunter.getUuid(), nextIndex);
        this.syncHunterTracking(hunter, true);
        return target;
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
            this.setCompassMad(hunter, target);
            return;
        }

        this.updateCompassStacks(hunter, trackingTarget.get(), false, target);
    }

    private Optional<GlobalPos> resolveTrackingTarget(ServerPlayerEntity hunter, ServerPlayerEntity target) {
        PlayerTracker.TrackingData data = this.playerTracker.getData(target.getUuid());
        RegistryKey<World> hunterDim = hunter.getEntityWorld().getRegistryKey();

        if (hunterDim.equals(World.NETHER)) {
            if (!this.settings.netherTrackingEnabled()) {
                return Optional.empty();
            }
            if (data.lastNether == null) {
                this.notifyMissingNether(hunter, target);
                return Optional.empty();
            }
            this.huntersNotifiedMissingNether.remove(hunter.getUuid() + "-" + target.getUuid());
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
        if (this.huntersNotifiedMissingNether.add(hunter.getUuid() + "-" + target.getUuid())) {
            hunter.sendMessage(
                Text.literal("Looks like " + target.getName().getString() + " hasn't been in the Nether yet...")
                    .formatted(Formatting.GOLD),
                true
            );
        }
    }

    private void notifyEndPortalHint(ServerPlayerEntity hunter, ServerPlayerEntity target) {
        if (this.huntersNotifiedEndPortalHint.add(hunter.getUuid() + "-" + target.getUuid())) {
            hunter.sendMessage(
                Text.literal(target.getName().getString() + " is in the End. Compass points to their last Overworld location.")
                    .formatted(Formatting.LIGHT_PURPLE),
                true
            );
        }
    }

    private void updateCompassStacks(ServerPlayerEntity hunter, GlobalPos target, boolean tracked, ServerPlayerEntity trackedPlayer) {
        this.playerTracker.updateTrackerStacks(hunter, trackedPlayer, Optional.of(target), ManhuntMinigame::isTrackerCompass, true);
    }

    private void setCompassMad(ServerPlayerEntity hunter, ServerPlayerEntity trackedPlayer) {
        this.playerTracker.updateTrackerStacks(hunter, trackedPlayer, Optional.empty(), ManhuntMinigame::isTrackerCompass, true);
        hunter.sendMessage(Text.literal("Cannot track " + trackedPlayer.getName().getString() + " in " + trackedPlayer.getWorld().getRegistryKey().getValue().getPath()).formatted(Formatting.RED), true);
    }

    private void grantHunterCompass(ServerPlayerEntity hunter, boolean announce) {
        if (!this.hunterHasCompass(hunter)) {
            ItemStack stack = Items.COMPASS.getDefaultStack();
            tagTrackerCompass(stack);
            this.insertTrackerCompass(hunter, stack);
        }
        this.hunterTrackingIndexes.put(hunter.getUuid(), 0);
        if (this.state == GameState.RUNNING) {
            this.syncHunterTracking(hunter, announce);
        }
    }

    private boolean hunterHasCompass(ServerPlayerEntity hunter) {
        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            if (isTrackerCompass(hunter.getInventory().getStack(slot))) {
                return true;
            }
        }
        return false;
    }

    static boolean isTrackerCompass(ItemStack stack) {
        if (!stack.isOf(Items.COMPASS)) {
            return false;
        }
        return ProtectedItemTags.hasType(stack, TRACKER_TYPE);
    }

    static void tagTrackerCompass(ItemStack stack) {
        ProtectedItemTags.mark(stack, TRACKER_TYPE);
    }

    private void registerProtectedItems() {
        ProtectedItemService service = ProtectedItemService.getInstance();
        service.clearRules();
        service.registerRule(ProtectedItemRule.builder(TRACKER_TYPE)
            .preventDrop()
            .preventExternalStorage()
            .preventDeletion()
            .preventDeathLoss()
            .preventDuplication()
            .autoRestore()
            .allowRearrange(true)
            .allowOffhandSwap(true)
            .maxStacks(1)
            .cleanupWorldDrops()
            .canHold(player -> this.settings.huntersCompassEnabled() && this.getPlayerRole(player) == ManhuntRole.HUNTER)
            .shouldHave(player -> this.settings.huntersCompassEnabled() && this.getPlayerRole(player) == ManhuntRole.HUNTER)
            .restoreAction(player -> this.grantHunterCompass(player, false))
            .build());
    }

    private void insertTrackerCompass(ServerPlayerEntity hunter, ItemStack stack) {
        if (hunter.getInventory().insertStack(stack)) {
            return;
        }

        int emptySlot = hunter.getInventory().getEmptySlot();
        if (emptySlot >= 0) {
            hunter.getInventory().setStack(emptySlot, stack);
            hunter.getInventory().markDirty();
            return;
        }

        int fallbackSlot = Math.max(0, hunter.getInventory().selectedSlot);
        ItemStack displaced = hunter.getInventory().getStack(fallbackSlot);
        hunter.getInventory().setStack(fallbackSlot, stack);
        if (!displaced.isEmpty()) {
            hunter.getInventory().insertStack(displaced);
            if (!displaced.isEmpty()) {
                hunter.dropItem(displaced, false);
            }
        }
        hunter.getInventory().markDirty();
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
        TeamRole teamRole = role == ManhuntRole.SPEEDRUNNER ? TeamRole.RUNNER : TeamRole.HUNTER;
        String teamId = role == ManhuntRole.SPEEDRUNNER ? "speedrunners" : "hunters";
        String teamLabel = role == ManhuntRole.SPEEDRUNNER ? "Speedrunners" : "Hunters";
        this.teams.assign(player, teamId, teamLabel, teamRole);
        this.eliminatedHunters.remove(playerUuid);
        this.speedrunnerRespawns.removePlayer(player);

        if (this.state == GameState.RUNNING) {
            if (role == ManhuntRole.SPEEDRUNNER) {
                this.aliveSpeedrunners.add(playerUuid);
            } else if (role == ManhuntRole.HUNTER && this.settings.huntersCompassEnabled()) {
                this.grantHunterCompass(player, true);
            }
            if (role == ManhuntRole.HUNTER && !this.huntStarted && this.leadTicksRemaining > 0) {
                this.applyLeadEffects(player);
            }
        }
        this.syncVanillaTeams();
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        if (!this.isParticipant(player) && this.context != null) {
            this.context.roster().add(player);
        }

        ManhuntRole resolvedRole = parseLateJoinRole(role, teamId);
        if (resolvedRole == null) {
            player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            player.sendMessage(Text.literal("Joined Manhunt as an unassigned spectator. Ask an admin to assign Runner or Hunter.").formatted(Formatting.YELLOW), false);
            return;
        }

        this.registerProtectedItems();
        this.setPlayerRole(player, resolvedRole);
        this.speedrunnerDeaths.putIfAbsent(player.getUuid(), 0);
        this.hunterDeaths.putIfAbsent(player.getUuid(), 0);
        this.pendingHunterRespawns.remove(player.getUuid());
        this.eliminatedHunters.remove(player.getUuid());
        this.compassCooldownUntilTicks.remove(player.getUuid());

        if (this.state == GameState.RUNNING) {
            if (resolvedRole == ManhuntRole.SPEEDRUNNER && this.settings.midGameJoinTeleportEnabled()) {
                player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                this.pendingLateJoinTeleports.add(player.getUuid());

                Map<UUID, String> activeRunners = new HashMap<>();
                for (UUID uuid : this.aliveSpeedrunners) {
                    ServerPlayerEntity runner = player.server.getPlayerManager().getPlayer(uuid);
                    if (runner != null && runner.isAlive()) {
                        activeRunners.put(runner.getUuid(), runner.getName().getString());
                    }
                }

                if (activeRunners.isEmpty()) {
                    this.executeLateJoinTeleport(player, null);
                } else {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                        player,
                        new dev.frost.miniverse.common.NetworkConstants.ManhuntLateJoinPayload(activeRunners)
                    );
                }
            } else {
                player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0F);

                if (resolvedRole == ManhuntRole.SPEEDRUNNER) {
                    if (!this.aliveSpeedrunners.contains(player.getUuid())) {
                        this.aliveSpeedrunners.add(player.getUuid());
                    }
                    this.deadSpeedrunners.remove(player.getUuid());
                    this.speedrunnerRespawns.removePlayer(player);
                    this.updateRunnerTracking();
                    for (ServerPlayerEntity hunter : this.getActiveHunters()) {
                        this.syncHunterTracking(hunter, true);
                    }
                } else {
                    this.hunterTrackingIndexes.put(player.getUuid(), 0);
                    if (this.settings.huntersCompassEnabled()) {
                        this.grantHunterCompass(player, true);
                    }
                    if (!this.huntStarted && this.leadTicksRemaining > 0) {
                        this.applyLeadEffects(player);
                    }
                }
                
                if (resolvedRole == ManhuntRole.SPEEDRUNNER) {
                    this.spawnPlayerAtWorldSpawn(player);
                } else if (resolvedRole == ManhuntRole.HUNTER) {
                    ServerPlayerEntity activeHunter = this.getActiveHunters().stream()
                        .filter(h -> h != player && !h.isDisconnected())
                        .findFirst().orElse(null);
                    if (activeHunter != null) {
                        player.teleport(activeHunter.getServerWorld(), activeHunter.getX(), activeHunter.getY(), activeHunter.getZ(), activeHunter.getYaw(), activeHunter.getPitch());
                    } else {
                        this.spawnPlayerAtWorldSpawn(player);
                    }
                }
            }
        } else {
            if (resolvedRole == ManhuntRole.HUNTER && this.settings.huntersCompassEnabled()) {
                this.grantHunterCompass(player, true);
            }
            if (!this.huntStarted && this.leadTicksRemaining > 0) {
                this.applyLeadEffects(player);
            }
        }

        this.syncVanillaTeams();
        player.sendMessage(Text.literal("Joined Manhunt in progress as " + resolvedRole.getDisplayName() + ".").formatted(Formatting.GREEN), false);
    }

    /**
     * Gets the role of a player.
     *
     * @param player the player
     * @return the player's role, or null if not assigned
     */
    @Nullable
    public ManhuntRole getPlayerRole(ServerPlayerEntity player) {
        return this.roleFor(player.getUuid());
    }

    @Nullable
    private ManhuntRole roleFor(UUID playerUuid) {
        if (!this.teams.contains(playerUuid)) {
            return null;
        }
        return switch (this.teams.role(playerUuid)) {
            case RUNNER -> ManhuntRole.SPEEDRUNNER;
            case HUNTER -> ManhuntRole.HUNTER;
            default -> null;
        };
    }

    @Nullable
    private static ManhuntRole parseLateJoinRole(String role, String teamId) {
        String value = role == null || role.isBlank() ? teamId : role;
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "speedrunner", "runner", "speedrunners", "runners" -> ManhuntRole.SPEEDRUNNER;
            case "hunter", "hunters" -> ManhuntRole.HUNTER;
            default -> null;
        };
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
        return this.teams.playerUuidsWithRole(TeamRole.RUNNER);
    }

    /**
     * Gets all Hunters.
     *
     * @return a list of all hunters
     */
    public List<ServerPlayerEntity> getHunters() {
        return this.teams.playerUuidsWithRole(TeamRole.HUNTER).stream()
            .map(this::getPlayerByUuid)
            .filter(Objects::nonNull)
            .toList();
    }

    public List<ServerPlayerEntity> getActiveHunters() {
        return this.teams.playerUuidsWithRole(TeamRole.HUNTER).stream()
            .filter(uuid -> !this.eliminatedHunters.contains(uuid))
            .map(this::getPlayerByUuid)
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
        GameMessenger.broadcast(this.getParticipants(), message);
    }

    private List<ServerPlayerEntity> getParticipants() {
        return this.context != null ? this.context.liveParticipants() : List.of();
    }

    private boolean isParticipant(ServerPlayerEntity player) {
        return this.context != null && this.context.roster().contains(player);
    }

    private void removeParticipant(ServerPlayerEntity player) {
        if (this.context != null) this.context.roster().remove(player);
    }

    public void handleLateJoinTeleport(ServerPlayerEntity player, String targetName) {
        if (this.pendingLateJoinTeleports.remove(player.getUuid())) {
            ServerPlayerEntity target = player.server.getPlayerManager().getPlayer(targetName);
            this.executeLateJoinTeleport(player, target);
        }
    }

    private void executeLateJoinTeleport(ServerPlayerEntity player, ServerPlayerEntity target) {
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0F);

        if (!this.aliveSpeedrunners.contains(player.getUuid())) {
            this.aliveSpeedrunners.add(player.getUuid());
        }
        this.deadSpeedrunners.remove(player.getUuid());
        this.speedrunnerRespawns.removePlayer(player);
        this.updateRunnerTracking();
        for (ServerPlayerEntity hunter : this.getActiveHunters()) {
            this.syncHunterTracking(hunter, true);
        }

        if (target != null && target.isAlive()) {
            player.teleport((ServerWorld) target.getWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
            player.sendMessage(Text.literal("Teleported to " + target.getName().getString() + ".").formatted(Formatting.GREEN), false);
        } else {
            this.spawnPlayerAtWorldSpawn(player);
            player.sendMessage(Text.literal("Target was unavailable, spawned at world spawn.").formatted(Formatting.YELLOW), false);
        }
    }

    private void spawnPlayerAtWorldSpawn(ServerPlayerEntity player) {
        if (player.server != null) {
            net.minecraft.server.world.ServerWorld world = player.server.getOverworld();
            net.minecraft.util.math.BlockPos spawnPos = world.getSpawnPos();
            player.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
        }
    }

    private void replaceParticipant(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (this.context != null) this.context.roster().add(newPlayer);
    }

    private void setRuntimeState(GameState state) {
        if (this.context != null) {
            this.context.setState(state);
        }
    }

    private void pruneDisconnectedSpeedrunners() {
        for (UUID speedrunnerUuid : new ArrayList<>(this.aliveSpeedrunners)) {
            if (this.roleFor(speedrunnerUuid) != ManhuntRole.SPEEDRUNNER) {
                this.aliveSpeedrunners.remove(speedrunnerUuid);
            }
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public TeamManager teamManager() {
        return this.teams;
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
        return this.state == GameState.RUNNING && this.huntStarted;
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
        for (ServerPlayerEntity participant : this.getParticipants()) {
            if (!this.teams.contains(participant.getUuid())) {
                unassigned.add(participant);
            }
        }
        return unassigned;
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        UUID playerUuid = oldPlayer.getUuid();
        ManhuntRole role = this.roleFor(playerUuid);
        if (role == null) {
            return;
        }

        this.speedrunnerRespawns.handlePlayerRespawn(newPlayer);

        if (role == ManhuntRole.HUNTER && this.state == GameState.RUNNING) {
            if (this.eliminatedHunters.contains(playerUuid)) {
                newPlayer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
            } else if (this.pendingHunterRespawns.containsKey(playerUuid)) {
                newPlayer.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                newPlayer.sendMessage(Text.literal("Waiting to respawn as Hunter...").formatted(Formatting.YELLOW), true);
            } else if (this.settings.huntersCompassEnabled()) {
                this.grantHunterCompass(newPlayer, false);
            }
        }
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        ManhuntRole role = this.roleFor(playerUuid);
        if (role == null) {
            return;
        }

        if (role == ManhuntRole.HUNTER) {
            this.clearLeadEffects(player);
        }
        this.compassCooldownUntilTicks.remove(player.getUuid());
        this.hunterTrackingIndexes.remove(player.getUuid());

        if (role == ManhuntRole.SPEEDRUNNER && this.state.isActive() && this.getAliveSpeedrunnerCount() <= 0) {
            if (!MatchLifecycleController.getInstance().isDisconnectGraceActiveFor(player.getUuid())) {
                this.broadcastMessage(
                    Text.literal(player.getName().getString() + " (Speedrunner) left the game.").formatted(Formatting.RED)
                );
                this.endGameWithHunterVictory();
            }
        }
        this.syncVanillaTeams();
    }

    void handleDisconnectGraceExpired(List<UUID> pendingPlayers) {
        if (!this.state.isActive()) {
            return;
        }
        if (this.getAliveSpeedrunnerCount() > 0) {
            return;
        }
        this.broadcastMessage(Text.literal("Disconnect grace expired. Hunters win.").formatted(Formatting.RED));
        this.endGameWithHunterVictory();
    }

    int getAliveSpeedrunnerCount() {
        int count = 0;
        for (UUID speedrunnerUuid : this.aliveSpeedrunners) {
            ServerPlayerEntity player = this.getPlayerByUuid(speedrunnerUuid);
            if ((player == null || player.isDisconnected()) && MatchLifecycleController.getInstance().isDisconnectGraceActiveFor(speedrunnerUuid)) {
                count++;
                continue;
            }
            if (player != null && !player.isDisconnected()) {
                count++;
            }
        }
        return count;
    }

    private boolean hasHunterInReconnectGrace() {
        for (UUID hunterUuid : this.teams.playerUuidsWithRole(TeamRole.HUNTER)) {
            if (!this.eliminatedHunters.contains(hunterUuid) && MatchLifecycleController.getInstance().isDisconnectGraceActiveFor(hunterUuid)) {
                return true;
            }
        }
        return false;
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

    @Override
    public void onPause(GameState previousState) {
        this.broadcastMessage(Text.literal("Manhunt paused.").formatted(Formatting.YELLOW));
    }

    @Override
    public void onResume(GameState resumedState) {
        this.broadcastMessage(Text.literal("Manhunt resumed.").formatted(Formatting.GREEN));
        if (resumedState == GameState.RUNNING && this.huntStarted && this.settings.huntersCompassEnabled()) {
            this.updateHunterCompasses();
        }
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject root = new JsonObject();
        root.addProperty("state", this.state.name());
        root.addProperty("leadTicksRemaining", this.leadTicksRemaining);
        root.addProperty("huntStarted", this.huntStarted);
        root.addProperty("tickCounter", this.tickCounter);
        root.addProperty("gameTicks", this.gameTicks);

        root.add("teams", this.writeTeams());
        root.add("aliveSpeedrunners", writeUuidArray(this.aliveSpeedrunners));
        root.add("deadSpeedrunners", writeUuidArray(this.deadSpeedrunners));
        root.add("eliminatedHunters", writeUuidArray(this.eliminatedHunters));
        root.add("hunterTrackingIndexes", writeUuidIntMap(this.hunterTrackingIndexes));
        root.add("speedrunnerDeaths", writeUuidIntMap(this.speedrunnerDeaths));
        root.add("hunterDeaths", writeUuidIntMap(this.hunterDeaths));
        root.add("pendingHunterRespawns", writeUuidLongMap(this.pendingHunterRespawns));
        root.add("compassCooldownUntilTicks", writeUuidLongMap(this.compassCooldownUntilTicks));
        root.add("runnerTracking", this.playerTracker.writeTrackingData());
        root.add("speedrunnerRespawns", this.speedrunnerRespawns.saveRuntimeState());
        return root;
    }

    @Override
    public SessionData saveSessionData(MinigameRuntime runtime) {
        return new ManhuntSessionData(
            SessionRuntimeConfig.getSessionId().orElseGet(() -> UUID.randomUUID().toString()),
            runtime.state(),
            this.gameTicks,
            this.context != null ? this.context.participantIds() : java.util.Set.of(),
            Instant.now(),
            this.settings,
            this.saveRuntimeState()
        );
    }

    @Override
    public void loadRuntimeState(JsonObject root) {
        if (root == null) {
            return;
        }

        this.teams.clear();
        this.deadSpeedrunners.clear();
        this.aliveSpeedrunners.clear();
        this.hunterTrackingIndexes.clear();
        this.playerTracker.clear();
        this.huntersNotifiedMissingNether.clear();
        this.huntersNotifiedEndPortalHint.clear();
        this.speedrunnerDeaths.clear();
        this.hunterDeaths.clear();
        this.pendingHunterRespawns.clear();
        this.eliminatedHunters.clear();
        this.compassCooldownUntilTicks.clear();

        this.state = parseState(stringValue(root, "state", GameState.WAITING_FOR_PLAYERS.name()));
        this.setRuntimeState(this.state);
        this.leadTicksRemaining = intValue(root, "leadTicksRemaining", 0);
        this.huntStarted = booleanValue(root, "huntStarted", false);
        this.tickCounter = intValue(root, "tickCounter", 0);
        this.gameTicks = longValue(root, "gameTicks", 0L);

        this.readTeams(root);
        this.aliveSpeedrunners.addAll(readUuidArray(root, "aliveSpeedrunners"));
        this.deadSpeedrunners.addAll(readUuidArray(root, "deadSpeedrunners"));
        this.eliminatedHunters.addAll(readUuidArray(root, "eliminatedHunters"));
        this.hunterTrackingIndexes.putAll(readUuidIntMap(root, "hunterTrackingIndexes"));
        this.speedrunnerDeaths.putAll(readUuidIntMap(root, "speedrunnerDeaths"));
        this.hunterDeaths.putAll(readUuidIntMap(root, "hunterDeaths"));
        this.pendingHunterRespawns.putAll(readUuidLongMap(root, "pendingHunterRespawns"));
        this.compassCooldownUntilTicks.putAll(readUuidLongMap(root, "compassCooldownUntilTicks"));
        if (root.has("runnerTracking") && root.get("runnerTracking").isJsonArray()) {
            this.playerTracker.readTrackingData(root.getAsJsonArray("runnerTracking"));
        }
        if (root.has("speedrunnerRespawns") && root.get("speedrunnerRespawns").isJsonObject()) {
            this.speedrunnerRespawns.loadRuntimeState(root.getAsJsonObject("speedrunnerRespawns"));
        }
        this.repairActiveRunnerRoster();
        this.registerProtectedItems();
        this.syncVanillaTeams();
    }

    @Override
    public void loadSessionData(SessionData data) {
        if (data instanceof ManhuntSessionData manhuntData) {
            this.applySettings(manhuntData.settings());
            this.loadRuntimeState(this.runtimeForRestore(manhuntData));
            for (UUID participantId : manhuntData.participantIds()) {
                if (this.context != null) {
                    this.context.roster().add(participantId);
                }
            }
            return;
        }
        super.loadSessionData(data);
    }

    private JsonObject runtimeForRestore(ManhuntSessionData data) {
        JsonObject runtime = data.runtime().deepCopy();
        GameState runtimeState = parseState(stringValue(runtime, "state", data.gameState().name()));
        boolean hasRuntimeProgress = data.gameTicks() > 0L
            || booleanValue(runtime, "huntStarted", false)
            || hasElements(runtime, "aliveSpeedrunners")
            || hasElements(runtime, "deadSpeedrunners")
            || hasElements(runtime, "speedrunnerDeaths")
            || hasElements(runtime, "hunterDeaths");

        if (hasRuntimeProgress && !runtimeState.isActive() && runtimeState != GameState.PAUSED && !runtimeState.isTerminal()) {
            runtime.addProperty("state", GameState.RUNNING.name());
        } else if ((data.gameState().isActive() || data.gameState() == GameState.PAUSED) && !runtimeState.isActive() && runtimeState != GameState.PAUSED) {
            runtime.addProperty("state", data.gameState().name());
        }
        return runtime;
    }

    private static boolean hasElements(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonArray() && !root.getAsJsonArray(key).isEmpty();
    }

    private void repairActiveRunnerRoster() {
        if (!this.state.isActive()) {
            return;
        }
        if (!this.aliveSpeedrunners.isEmpty() || !this.deadSpeedrunners.isEmpty()) {
            return;
        }
        this.aliveSpeedrunners.addAll(this.getSpeedrunnerUuids());
    }

    @Override
    protected ServerPlayerEntity getPlayerByUuid(UUID uuid) {
        for (ServerPlayerEntity participant : this.getParticipants()) {
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
        this.syncVanillaTeams();
    }

    void endGameBecauseNoAliveSpeedrunners() {
        if (this.state == GameState.RUNNING) {
            this.endGameWithHunterVictory();
        }
    }

    void broadcastManhuntMessage(Text message) {
        this.broadcastMessage(message);
    }

    private void updateRunnerTracking() {
        List<ServerPlayerEntity> targets = this.aliveSpeedrunners.stream()
            .map(this::getPlayerByUuid)
            .filter(java.util.Objects::nonNull)
            .toList();
        this.playerTracker.updatePositions(targets, this.settings.netherTrackingEnabled());
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
        if (this.settings.runnerGlowPulseMinutes() <= 0 || this.state != GameState.RUNNING) {
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


    private void sendLeadActionBar(int secondsRemaining) {
        if (secondsRemaining <= 0) {
            return;
        }
        Text hunterText = Text.literal("Releasing in " + secondsRemaining + "s").formatted(Formatting.GREEN);
        Text runnerText = Text.literal("Hunters releasing in " + secondsRemaining + "s").formatted(Formatting.RED);
        this.sendActionBarToHunters(hunterText);
        this.sendActionBarToSpeedrunners(runnerText);
    }

    private int leadSecondsRemaining() {
        return Math.max(1, (this.leadTicksRemaining + 19) / 20);
    }

    private void sendActionBarToHunters(Text message) {
        for (ServerPlayerEntity hunter : this.getActiveHunters()) {
            hunter.sendMessage(message, true);
        }
    }

    private void sendActionBarToSpeedrunners(Text message) {
        for (ServerPlayerEntity speedrunner : this.getAliveSpeedrunners()) {
            speedrunner.sendMessage(message, true);
        }
    }

    @Override
    protected void syncVanillaTeams() {
        if (this.server == null || this.getVanillaTeams() == null) {
            return;
        }

        VanillaTeamOptions aliveOptionsTemplate = dev.frost.miniverse.minigame.core.util.VanillaTeamSync.aliveOptionsTemplate();
        VanillaTeamOptions deadOptions = dev.frost.miniverse.minigame.core.util.VanillaTeamSync.deadOptions();

        List<VanillaTeamDescriptor> descriptors = new ArrayList<>();
        for (ServerPlayerEntity player : this.getAliveSpeedrunners()) {
            String teamId = "player_" + player.getUuid();
            VanillaTeamOptions options = aliveOptionsTemplate.withColor(this.getVanillaTeams().colorFor(teamId));
            descriptors.add(new VanillaTeamDescriptor(teamId, player.getDisplayName(), List.of(player), options));
        }
        for (ServerPlayerEntity player : this.getActiveHunters()) {
            String teamId = "player_" + player.getUuid();
            VanillaTeamOptions options = aliveOptionsTemplate.withColor(this.getVanillaTeams().colorFor(teamId));
            descriptors.add(new VanillaTeamDescriptor(teamId, player.getDisplayName(), List.of(player), options));
        }

        List<ServerPlayerEntity> deadPlayers = this.getDeadSpeedrunners();
        if (!deadPlayers.isEmpty()) {
            descriptors.add(new VanillaTeamDescriptor("dead", Text.literal("Dead"), deadPlayers, deadOptions));
        }

        this.getVanillaTeams().sync(this.server, descriptors);
    }


    private void applyLeadEffects(ServerPlayerEntity hunter) {
        int leadTicks = this.settings.hunterReleaseDelaySeconds() * 20;
        if (leadTicks <= 0) {
            return;
        }
        hunter.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, leadTicks, 0, true, false, false));
        FreezeService.getInstance().freeze(hunter, FreezeReason.MANHUNT_LEAD);
        hunter.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
    }

    private void clearLeadEffects(ServerPlayerEntity hunter) {
        hunter.removeStatusEffect(StatusEffects.BLINDNESS);
        FreezeService.getInstance().unfreeze(hunter, FreezeReason.MANHUNT_LEAD);
        hunter.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
    }

    private void showSpeedrunnerDeathTitle(ServerPlayerEntity player) {
        int seconds = this.speedrunnerRespawns.getRespawnDelaySeconds();
        Text subtitle = seconds <= 0
            ? Text.literal("You will respawn now.").formatted(Formatting.YELLOW)
            : Text.literal("You will respawn in " + this.formatMinutes(seconds) + " minutes").formatted(Formatting.YELLOW);
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("YOU DIED").formatted(Formatting.RED, Formatting.BOLD)));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
    }

    private void showLastSpeedrunnerDeathTitle(ServerPlayerEntity player) {
        Text title = Text.literal("YOU LOST").formatted(Formatting.RED, Formatting.BOLD);
        Text subtitle = Text.literal("There is no alive speedrunner teammate!").formatted(Formatting.YELLOW);
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
    }

    private int formatMinutes(int totalSeconds) {
        return Math.max(1, (totalSeconds + 59) / 60);
    }

    private JsonArray writeTeams() {
        JsonArray teams = new JsonArray();
        for (TeamSnapshot snapshot : this.teams.snapshots()) {
            JsonObject team = new JsonObject();
            team.addProperty("id", snapshot.id());
            team.addProperty("label", snapshot.label());
            JsonArray members = new JsonArray();
            for (TeamMembership membership : snapshot.members()) {
                JsonObject member = new JsonObject();
                member.addProperty("uuid", membership.playerUuid().toString());
                member.addProperty("name", membership.playerName());
                member.addProperty("role", membership.role().name());
                members.add(member);
            }
            team.add("members", members);
            teams.add(team);
        }
        return teams;
    }

    private void readTeams(JsonObject root) {
        if (!root.has("teams") || !root.get("teams").isJsonArray()) {
            return;
        }
        for (var teamElement : root.getAsJsonArray("teams")) {
            if (!teamElement.isJsonObject()) {
                continue;
            }
            JsonObject team = teamElement.getAsJsonObject();
            String teamId = stringValue(team, "id", "");
            String label = stringValue(team, "label", teamId);
            if (teamId.isBlank() || !team.has("members") || !team.get("members").isJsonArray()) {
                continue;
            }
            for (var memberElement : team.getAsJsonArray("members")) {
                if (!memberElement.isJsonObject()) {
                    continue;
                }
                JsonObject member = memberElement.getAsJsonObject();
                UUID uuid = uuidValue(member, "uuid");
                if (uuid == null) {
                    continue;
                }
                TeamRole role = parseTeamRole(stringValue(member, "role", TeamRole.MEMBER.name()));
                String name = stringValue(member, "name", uuid.toString());
                this.teams.assign(uuid, name, teamId, label, role);
                if (this.context != null) {
                    this.context.roster().add(uuid);
                }
            }
        }
    }



    private static JsonArray writeUuidArray(Collection<UUID> uuids) {
        JsonArray array = new JsonArray();
        for (UUID uuid : uuids) {
            array.add(uuid.toString());
        }
        return array;
    }

    private static List<UUID> readUuidArray(JsonObject object, String key) {
        List<UUID> uuids = new ArrayList<>();
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return uuids;
        }
        for (var element : object.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            try {
                uuids.add(UUID.fromString(element.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return uuids;
    }

    private static JsonObject writeUuidIntMap(Map<UUID, Integer> map) {
        JsonObject object = new JsonObject();
        for (Map.Entry<UUID, Integer> entry : map.entrySet()) {
            object.addProperty(entry.getKey().toString(), entry.getValue());
        }
        return object;
    }

    private static Map<UUID, Integer> readUuidIntMap(JsonObject root, String key) {
        Map<UUID, Integer> map = new LinkedHashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return map;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
            try {
                map.put(UUID.fromString(entry.getKey()), entry.getValue().getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
        return map;
    }

    private static JsonObject writeUuidLongMap(Map<UUID, Long> map) {
        JsonObject object = new JsonObject();
        for (Map.Entry<UUID, Long> entry : map.entrySet()) {
            object.addProperty(entry.getKey().toString(), entry.getValue());
        }
        return object;
    }

    private static Map<UUID, Long> readUuidLongMap(JsonObject root, String key) {
        Map<UUID, Long> map = new LinkedHashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return map;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
            try {
                map.put(UUID.fromString(entry.getKey()), entry.getValue().getAsLong());
            } catch (RuntimeException ignored) {
            }
        }
        return map;
    }

    private static void putBlockPos(JsonObject object, String key, @Nullable BlockPos pos) {
        if (pos == null) {
            return;
        }
        JsonObject value = new JsonObject();
        value.addProperty("x", pos.getX());
        value.addProperty("y", pos.getY());
        value.addProperty("z", pos.getZ());
        object.add(key, value);
    }

    @Nullable
    private static BlockPos readBlockPos(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        JsonObject value = object.getAsJsonObject(key);
        return new BlockPos(intValue(value, "x", 0), intValue(value, "y", 0), intValue(value, "z", 0));
    }

    private static GameState parseState(String value) {
        try {
            return GameState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return GameState.WAITING_FOR_PLAYERS;
        }
    }

    private static TeamRole parseTeamRole(String value) {
        try {
            return TeamRole.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return TeamRole.MEMBER;
        }
    }

    @Nullable
    private static UUID uuidValue(JsonObject object, String key) {
        String value = stringValue(object, key, "");
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
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



    private boolean hasLivesRemaining(int lives, int deaths) {
        return lives == ManhuntSettings.UNLIMITED_LIVES || deaths < lives;
    }

    private void dropSpeedrunnerLoot(ServerPlayerEntity player) {
        if (player == null || player.getEntityWorld() == null) {
            return;
        }
        if (player.getEntityWorld() instanceof ServerWorld serverWorld) {
            int xpToDrop = player.totalExperience;
            if (xpToDrop > 0) {
                ExperienceOrbEntity.spawn(serverWorld, new Vec3d(player.getX(), player.getY(), player.getZ()), xpToDrop);
                player.totalExperience = 0;
                player.experienceLevel = 0;
                player.experienceProgress = 0.0F;
            }
        }
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack drop = stack.copy();
            player.getInventory().setStack(slot, ItemStack.EMPTY);
            ItemEntity item = new ItemEntity(player.getEntityWorld(), player.getX(), player.getY(), player.getZ(), drop);
            player.getEntityWorld().spawnEntity(item);
        }
        player.getInventory().markDirty();
    }
}
