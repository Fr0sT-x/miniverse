package dev.frost.miniverse.minigame.impl.microparty;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.map.editor.RegionPart;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.SessionRoster;
import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.event.EntityDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRegionAware;
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRuleRegistry;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MicroPartyMinigame extends AbstractMinigame implements
    SpawnPointAware,
    PlayerLeaveAware,
    PlayerDamageAware,
    EntityDamageAware,
    PlayerRegionAware,
    DeathAwareMinigame,
    dev.frost.miniverse.chat.ChatInterceptAware,
    dev.frost.miniverse.minigame.core.event.ItemUseOnBlockAware,
    PersistentMinigame {

    public enum Phase {
        INTERMISSION,
        SPEED_UP,
        ANNOUNCEMENT,
        ACTIVE,
        RESOLVING
    }

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private MicroPartySettings settings = MicroPartySettings.defaults();
    private MicroPartyMapConfig mapConfig = new MicroPartyMapConfig(List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());

    private PlayerPerformanceTracker tracker = new PlayerPerformanceTracker(3);
    private final List<UUID> eliminatedPlayers = new ArrayList<>();
    private final List<UUID> placements = new ArrayList<>();

    private int currentRound = 0;
    private Phase currentPhase = Phase.INTERMISSION;
    private int phaseTicksRemaining = 0;
    private MicroRule activeRule = null;
    private String lastRuleId = "";
    private final List<String> recentRuleHistory = new ArrayList<>();
    private final Random random = new Random();

    private long matchStartTime = 0;
    private boolean matchFinished = false;
    private int tickCounter = 0;

    // Scoreboard
    private ScoreboardTemplate scoreboard;
    private ScoreboardLine roundLine;
    private ScoreboardLine speedLine;
    private ScoreboardLine taskLine;
    private ScoreboardLine timerLine;
    private final List<ScoreboardLine> playerLines = new ArrayList<>();

    // Death Lifecycle
    private DeathLifecycleManager deathLifecycleManager;

    // Temporary Arena Blocks
    private final TemporaryBlockManager blockManager = new TemporaryBlockManager();

    // Spawn Slot Allocation
    private final Map<UUID, Integer> assignedSpawnSlots = new ConcurrentHashMap<>();
    private final Set<Integer> occupiedSpawnSlots = ConcurrentHashMap.newKeySet();

    public MicroPartyMinigame() {
    }

    public TemporaryBlockManager getBlockManager() {
        return this.blockManager;
    }

    public void applySettings(MicroPartySettings settings, MicroPartyMapConfig mapConfig) {
        this.settings = settings != null ? settings : MicroPartySettings.defaults();
        this.mapConfig = mapConfig != null ? mapConfig : new MicroPartyMapConfig(List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of());
        this.tracker = new PlayerPerformanceTracker(this.settings.startingLives());
    }

    @Override
    public String getName() {
        return MicroPartyDefinition.DISPLAY_NAME;
    }

    public String getGameId() {
        return MicroPartyDefinition.ID;
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    @Override
    public void setState(GameState state) {
        this.state = state == null ? GameState.WAITING_FOR_PLAYERS : state;
    }

    public int getCurrentRound() {
        return this.currentRound;
    }

    public int getMaxRounds() {
        return this.settings.maxRounds();
    }

    public PlayerPerformanceTracker getTracker() {
        return this.tracker;
    }

    public MicroPartyMapConfig getMapConfig() {
        return this.mapConfig;
    }

    public List<ServerPlayerEntity> players() {
        return this.context != null ? this.context.liveParticipants() : List.of();
    }

    public List<ServerPlayerEntity> getLivingPlayers() {
        return this.players().stream()
            .filter(p -> !eliminatedPlayers.contains(p.getUuid()) && tracker.isAlive(p.getUuid()))
            .toList();
    }

    public boolean isEliminated(UUID uuid) {
        return this.eliminatedPlayers.contains(uuid);
    }

    public ServerWorld getWorld() {
        if (this.context != null && this.context.nullableServer() != null) {
            return this.context.nullableServer().getOverworld();
        }
        return null;
    }

    @Override
    public boolean canBuild() {
        return false;
    }

    @Override
    public boolean canBreakBlocks() {
        return false;
    }

    @Override
    public MatchProgressionValidator.ProgressionState checkProgression(SessionRoster roster) {
        return MatchProgressionValidator.ProgressionState.valid();
    }

    @Override
    public void initialize() {
        // Critical invariant: interceptsRespawn = true requires doImmediateRespawn = false (I01)
        this.applyVanillaGameRule(GameRules.DO_IMMEDIATE_RESPAWN, false);
        this.applyVanillaGameRule(GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(GameRules.FALL_DAMAGE, false);
        this.applyVanillaGameRule(GameRules.DO_MOB_SPAWNING, false);
        this.applyVanillaGameRule(GameRules.DO_DAYLIGHT_CYCLE, false);
    }

    public MapValidationResult startValidation() {
        MapValidationResult.Builder builder = MapValidationResult.builder();
        if (this.mapConfig.arenaBounds().isEmpty()) {
            builder.error("Map is missing Arena Bounds region.");
        }
        if (this.mapConfig.playerSpawns().size() < 2) {
            builder.error("Map must have at least 2 Player Spawns.");
        }
        if (this.players().isEmpty()) {
            builder.error("No players present in match.");
        }
        return builder.build();
    }

    public boolean canStartMatch() {
        return !this.mapConfig.arenaBounds().isEmpty() && !this.mapConfig.playerSpawns().isEmpty() && !this.players().isEmpty();
    }

    public void handlePlayerJoin(ServerPlayerEntity player) {
        this.tracker.initPlayer(player.getUuid());
        if (this.getState() == GameState.RUNNING) {
            this.eliminatedPlayers.add(player.getUuid());
            this.sendToSpectator(player);
        } else {
            this.teleportToAssignedSpawn(player);
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        this.tracker.removePlayer(id);
        this.eliminatedPlayers.remove(id);
        Integer slot = this.assignedSpawnSlots.remove(id);
        if (slot != null && slot >= 0) {
            this.occupiedSpawnSlots.remove(slot);
        }
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleDisconnect(player);
        }
        if (this.getState() == GameState.RUNNING && !matchFinished) {
            checkMatchEndCondition();
        }
    }

    @Override
    protected void onMatchStart() {
        if (!this.canStartMatch()) {
            this.setState(GameState.ENDING);
            return;
        }

        this.matchStartTime = System.currentTimeMillis();
        this.matchFinished = false;
        this.currentRound = 0;
        this.eliminatedPlayers.clear();
        this.placements.clear();
        this.activeRule = null;
        this.lastRuleId = "";
        this.recentRuleHistory.clear();

        this.tracker = new PlayerPerformanceTracker(this.settings.startingLives());
        for (ServerPlayerEntity p : this.players()) {
            p.changeGameMode(GameMode.ADVENTURE);
            this.tracker.initPlayer(p.getUuid());
            this.teleportToAssignedSpawn(p);
        }

        this.initScoreboard();
        this.initDeathFramework();
        this.setState(GameState.RUNNING);
        this.updateScoreboard();

        GameMessenger.broadcast(this.players(), Text.literal("§6§l[Micro Party] §aGame started! Prepare for micro-challenges!"));

        // Begin the first intermission
        startIntermission();
    }

    @Override
    protected void onMatchEnd() {
        if (this.deathLifecycleManager != null && this.context != null && this.context.nullableServer() != null) {
            this.deathLifecycleManager.handleMatchEnding(this.context.nullableServer().getPlayerManager()::getPlayer);
        }
        if (this.activeRule != null && this.context != null && this.context.nullableServer() != null) {
            this.activeRule.onEnd(this, this.context.nullableServer());
            this.activeRule = null;
        }
        this.blockManager.restoreAll(this.getWorld());
        SpectatorService.getInstance().clearAll(true);
        for (ServerPlayerEntity p : this.players()) {
            if (!this.eliminatedPlayers.contains(p.getUuid())) {
                p.changeGameMode(GameMode.ADVENTURE);
            }
            FreezeService.getInstance().unfreeze(p, FreezeReason.ROUND_RESET);
            p.getInventory().clear();
            p.clearStatusEffects();
        }
        this.setState(GameState.STOPPED);
    }

    private void initDeathFramework() {
        this.deathLifecycleManager = new DeathLifecycleManager(new DeathLifecycleConfig() {
            @Override
            public DeathPolicy getDeathPolicy() {
                return new DeathPolicy() {
                    @Override
                    public void execute(ServerPlayerEntity player, DeathContext context) {
                        handlePlayerHazardFail(player);
                    }

                    @Override
                    public boolean interceptsRespawn() {
                        return true;
                    }
                };
            }

            @Override
            public DeathSpectatorPolicy getSpectatorPolicy() {
                return new DeathSpectatorPolicy() {
                    @Override
                    public void apply(ServerPlayerEntity player, DeathContext context) {
                    }

                    @Override
                    public boolean requiresFixedCamera() {
                        return false;
                    }

                    @Override
                    public NoTargetPolicy noTargetPolicy() {
                        return NoTargetPolicy.FREE_FLY;
                    }
                };
            }

            @Override
            public PostDeathPolicy createPostDeathPolicy() {
                return new PostDeathPolicy() {
                    @Override
                    public void start(ServerPlayerEntity player, DeathContext context) {
                        handlePlayerHazardFail(player);
                    }

                    @Override
                    public void cancel(dev.frost.miniverse.minigame.core.death.CancellationReason reason) {}

                    @Override
                    public void tick(MinecraftServer server) {}
                };
            }

            @Override
            public RespawnStrategy getRespawnStrategy() {
                return (ctx, session) -> {
                    Integer slot = assignedSpawnSlots.get(ctx.victimId());
                    MapPosition spawn = (slot != null && slot >= 0 && slot < mapConfig.playerSpawns().size())
                        ? mapConfig.playerSpawns().get(slot)
                        : getLobbySpawn();
                    ServerWorld w = getWorld();
                    return new RespawnStrategy.RespawnLocation(
                        w, new Vec3d(spawn.x() + 0.5, spawn.y(), spawn.z() + 0.5), spawn.yaw(), spawn.pitch()
                    );
                };
            }

            @Override
            public @Nullable String resolveTeamId(UUID playerId) {
                return null;
            }

            @Override
            public @Nullable String resolveMatchIdentifier() {
                return MicroPartyDefinition.ID;
            }
        }, SpectatorService.getInstance());
    }

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }

    // --- MicroRule Engine Ticking Loop ---

    @Override
    protected void onGameTick(MinecraftServer server) {
        if (this.getState() != GameState.RUNNING || this.matchFinished) {
            return;
        }

        this.tickCounter++;

        // Update scoreboard every 10 ticks (0.5s)
        if (this.tickCounter % 10 == 0) {
            this.updateScoreboard();
        }

        // Ticking the active rule if in ACTIVE phase
        if (this.currentPhase == Phase.ACTIVE && this.activeRule != null) {
            this.activeRule.onTick(this, server, this.phaseTicksRemaining);

            // Bounds check: if player fell out of arena bounds, catch them!
            for (ServerPlayerEntity p : getLivingPlayers()) {
                if (!isInsideArenaBounds(p.getPos())) {
                    handlePlayerOutOfBounds(p);
                }
            }
        } else if (this.currentPhase == Phase.SPEED_UP) {
            if (this.phaseTicksRemaining % 20 == 0) {
                int secs = (this.phaseTicksRemaining + 19) / 20;
                for (ServerPlayerEntity p : this.players()) {
                    p.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("§c⚡ Next micro-game in §e" + secs + "s§c...").formatted(Formatting.GOLD)));
                    p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.8f, 1.0f + (5 - secs) * 0.15f);
                }
            }
        }

        // Progress phase ticks
        if (this.phaseTicksRemaining > 0) {
            this.phaseTicksRemaining--;
        } else {
            // State transition
            advancePhase(server);
        }
    }

    private void advancePhase(MinecraftServer server) {
        switch (this.currentPhase) {
            case INTERMISSION -> {
                int nextRound = this.currentRound + 1;
                boolean isSpeedUp = this.settings.speedScaling() && (nextRound % 5 == 1) && (nextRound > 1);
                if (isSpeedUp) {
                    startSpeedUpPause(server);
                } else {
                    startAnnouncement(server);
                }
            }
            case SPEED_UP -> startAnnouncement(server);
            case ANNOUNCEMENT -> startActiveRule(server);
            case ACTIVE -> startResolving(server);
            case RESOLVING -> {
                if (checkMatchEndCondition()) {
                    return;
                }
                startIntermission();
            }
        }
    }

    private void startSpeedUpPause(MinecraftServer server) {
        this.currentPhase = Phase.SPEED_UP;
        this.phaseTicksRemaining = 100; // 5 seconds (5 * 20 ticks)

        for (ServerPlayerEntity p : this.players()) {
            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("⚡ SPEED UP! ⚡").formatted(Formatting.RED, Formatting.BOLD)));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Tempo is accelerating!").formatted(Formatting.YELLOW)));
            p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.4f);
        }
        GameMessenger.broadcast(this.players(), Text.literal("§c§l⚡ SPEED UP! ⚡ §eNext micro-games will be faster! Starting in 5 seconds..."));
        updateScoreboard();
    }

    private void startIntermission() {
        this.currentPhase = Phase.INTERMISSION;
        this.phaseTicksRemaining = Math.max(20, this.settings.intermissionSeconds() * 20);

        if (this.activeRule != null && this.context != null && this.context.nullableServer() != null) {
            this.activeRule.onEnd(this, this.context.nullableServer());
            this.activeRule = null;
        }
        this.blockManager.restoreAll(this.getWorld());

        // Clean player state - DO NOT reset player positions!
        for (ServerPlayerEntity p : getLivingPlayers()) {
            p.getInventory().clear();
            p.clearStatusEffects();
            FreezeService.getInstance().unfreeze(p, FreezeReason.ROUND_RESET);
        }
        updateScoreboard();
    }

    private void startAnnouncement(MinecraftServer server) {
        this.currentRound++;
        if (this.currentRound > this.settings.maxRounds()) {
            finishMatch();
            return;
        }

        this.currentPhase = Phase.ANNOUNCEMENT;
        this.phaseTicksRemaining = 25; // 1.25 seconds announcement pause

        // Unfreeze living players
        for (ServerPlayerEntity p : getLivingPlayers()) {
            FreezeService.getInstance().unfreeze(p, FreezeReason.ROUND_RESET);
        }

        // Pick next rule from applicable pool filtered by enabled pool in settings
        List<MicroRule> pool = MicroRuleRegistry.getApplicableRules(this.mapConfig);
        if (this.settings.enabledRules() != null && !this.settings.enabledRules().isEmpty()) {
            pool = pool.stream()
                .filter(r -> this.settings.isRuleEnabled(r.id()))
                .toList();
        }
        if (pool.isEmpty()) {
            GameMessenger.broadcast(this.players(), Text.literal("§cNo enabled micro-rules for this match!"));
            finishMatch();
            return;
        }

        this.activeRule = selectNextRule(pool);

        // Reset tracker round transient state
        this.tracker.resetAllRoundStates();

        // Prepare rule state (randomizing questions, phrases, targets, etc.) before title/instruction query
        this.activeRule.onPrepare(this, server);

        // Always announce the actual rule's title and instruction
        for (ServerPlayerEntity p : this.players()) {
            p.networkHandler.sendPacket(new TitleS2CPacket(this.activeRule.title(this)));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(this.activeRule.instruction(this)));
            p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 1.0f, getPitchForSpeed());
        }

        updateScoreboard();
    }

    private void startActiveRule(MinecraftServer server) {
        this.currentPhase = Phase.ACTIVE;
        this.phaseTicksRemaining = this.activeRule.getDurationTicks(this);

        // Apply speed potion effect if speed tiered (Tier 3 & 4)
        if (getSpeedFactor() <= 0.65f) {
            for (ServerPlayerEntity p : getLivingPlayers()) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, this.phaseTicksRemaining + 10, 1, false, false, false));
            }
        }

        // Call rule start hook
        this.activeRule.onStart(this, server);
        updateScoreboard();
    }

    private void startResolving(MinecraftServer server) {
        this.currentPhase = Phase.RESOLVING;
        this.phaseTicksRemaining = 25; // 1.25 seconds resolution view

        for (ServerPlayerEntity p : getLivingPlayers()) {
            if (p.interactionManager.getGameMode() == GameMode.SURVIVAL) {
                p.changeGameMode(GameMode.ADVENTURE);
            }
            boolean passed = this.activeRule != null && this.activeRule.hasPassed(p, this);
            if (passed) {
                this.tracker.recordPass(p.getUuid(), 100);
                p.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("§a§l✔ PASSED!").formatted(Formatting.GREEN)));
                p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
                ServerWorld w = p.getServerWorld();
                w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
            } else {
                p.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("§c§l❌ FAILED!").formatted(Formatting.RED)));
                p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 0.9f, 1.0f);
                p.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.4f, 1.6f);
                ServerWorld w = p.getServerWorld();
                w.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.3, 0.3, 0.05);

                if ("SURVIVAL".equalsIgnoreCase(this.settings.gameMode())) {
                    int remaining = this.tracker.deductLife(p.getUuid());
                    if (remaining <= 0) {
                        handlePlayerEliminated(p);
                    }
                }
            }
        }

        if (this.activeRule != null) {
            this.activeRule.onEnd(this, server);
        }
        this.blockManager.restoreAll(this.getWorld());

        updateScoreboard();
    }

    private void handlePlayerEliminated(ServerPlayerEntity player) {
        this.eliminatedPlayers.add(player.getUuid());
        this.placements.add(0, player.getUuid()); // Last eliminated gets highest placement rank

        GameMessenger.broadcast(this.players(), Text.literal("§c💀 " + player.getName().getString() + " §7has run out of lives and was eliminated!"));
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§c§lELIMINATED!")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§7You have run out of lives!")));
        sendToSpectator(player);
    }

    private void handlePlayerOutOfBounds(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("§c§lOUT OF BOUNDS!")));
        player.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.PLAYERS, 0.8f, 1.0f);
        this.tracker.setPassedCurrentRound(player.getUuid(), false);
        teleportToAssignedSpawn(player);
    }

    private void handlePlayerHazardFail(ServerPlayerEntity player) {
        player.setHealth(20.0f);
        this.tracker.setPassedCurrentRound(player.getUuid(), false);
        teleportToAssignedSpawn(player);
    }

    private void sendToSpectator(ServerPlayerEntity player) {
        SpectatorService.getInstance().startSpectating(
            player,
            SpectatorPolicies.unrestricted(),
            SpectatorTargetProviders.roster(),
            SpectatorMode.STANDARD,
            null,
            GameMode.ADVENTURE,
            Text.literal("§cEliminated! Spectating remaining players...").formatted(Formatting.RED)
        );
        teleportToLobby(player);
    }

    private boolean checkMatchEndCondition() {
        if ("SURVIVAL".equalsIgnoreCase(this.settings.gameMode())) {
            List<ServerPlayerEntity> living = getLivingPlayers();
            if (living.size() <= 1 && this.players().size() > 1) {
                finishMatch();
                return true;
            } else if (living.isEmpty()) {
                finishMatch();
                return true;
            }
        }
        return false;
    }

    private synchronized void finishMatch() {
        if (this.matchFinished) {
            return;
        }
        this.matchFinished = true;
        this.setState(GameState.ENDING);

        if (this.activeRule != null) {
            this.activeRule.onEnd(this, this.context != null ? this.context.nullableServer() : null);
            this.activeRule = null;
        }
        this.blockManager.restoreAll(this.getWorld());

        List<ServerPlayerEntity> living = getLivingPlayers();
        ServerPlayerEntity winner = living.isEmpty() ? null : living.get(0);

        if (winner != null) {
            GameMessenger.broadcast(this.players(), Text.literal(
                "§6§l[Micro Party] §e👑 " + winner.getName().getString() + " §awins the Micro Party! §e(" + tracker.getPasses(winner.getUuid()) + " passes)"
            ));
            for (ServerPlayerEntity p : this.players()) {
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§e§lVICTORY!")));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§6" + winner.getName().getString() + " §7is the champion!")));
                p.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        } else {
            GameMessenger.broadcast(this.players(), Text.literal("§6§l[Micro Party] §eGame Over! Thanks for playing!"));
        }

        updateScoreboard();

        MatchEndResult result;
        if (winner != null) {
            result = MatchEndResult.winner(winner);
        } else {
            result = new MatchEndResult(Set.of(), Text.literal("Nobody"));
        }

        MinigameRuntime runtime = this.runtime != null ? this.runtime : MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                runtime,
                result,
                MatchLifecycleOptions.defaults(this.getName())
                    .withReturnSeconds(10)
                    .withEndTitles(
                        Text.literal("VICTORY").formatted(Formatting.GOLD, Formatting.BOLD),
                        Text.literal("GAME OVER").formatted(Formatting.DARK_RED, Formatting.BOLD)
                    )
            );
        }
    }

    public MicroRule getActiveRule() {
        return this.activeRule;
    }

    public float getSpeedFactor() {
        if (!this.settings.speedScaling()) {
            return 1.0f;
        }
        if (this.currentRound <= 5) return 1.0f;
        if (this.currentRound <= 10) return 0.75f;
        if (this.currentRound <= 15) return 0.56f;
        return 0.38f; // Party speed! (3.0s for base 8s rules)
    }

    public float getPitchForSpeed() {
        float factor = getSpeedFactor();
        if (factor >= 1.0f) return 1.0f;
        if (factor >= 0.75f) return 1.15f;
        if (factor >= 0.56f) return 1.30f;
        return 1.50f;
    }

    // --- Damage & Events ---

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.getState() != GameState.RUNNING || this.eliminatedPlayers.contains(player.getUuid())) {
            return false;
        }

        // Delegate to active rule if present
        if (this.activeRule != null) {
            if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
                this.activeRule.onPlayerAttack(attacker, player, this);
            }
            return this.activeRule.onPlayerDamage(player, source, amount, this);
        }

        // Intercept lethal, fall, void damage
        if (source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.OUT_OF_WORLD) || amount >= player.getHealth()) {
            handlePlayerOutOfBounds(player);
            return false;
        }

        return false;
    }

    @Override
    public boolean allowEntityDamage(net.minecraft.entity.LivingEntity entity, DamageSource source, float amount) {
        if (this.getState() != GameState.RUNNING) {
            return false;
        }
        if (this.activeRule != null) {
            return this.activeRule.onEntityDamage(entity, source, amount, this);
        }
        return true;
    }

    @Override
    public void onPlayerEnterRegion(ServerPlayerEntity player, MapMarker region) {
    }

    @Override
    public void onPlayerExitRegion(ServerPlayerEntity player, MapMarker region) {
        if (region != null && MicroPartyDefinition.ARENA_BOUNDS.equals(region.definitionKey())) {
            if (this.getState() == GameState.RUNNING && !this.eliminatedPlayers.contains(player.getUuid())) {
                if (!isInsideArenaBounds(player.getPos())) {
                    handlePlayerOutOfBounds(player);
                }
            }
        }
    }

    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        if (this.getState() == GameState.RUNNING && this.eliminatedPlayers.contains(player.getUuid())) {
            this.teleportToLobby(player);
        } else {
            this.teleportToAssignedSpawn(player);
        }
    }

    @Override
    public dev.frost.miniverse.chat.ChatInterceptResult onChatMessage(ServerPlayerEntity sender, String message) {
        if (this.getState() == GameState.RUNNING && (this.currentPhase == Phase.ACTIVE || this.currentPhase == Phase.ANNOUNCEMENT) && this.activeRule != null) {
            boolean consumed = this.activeRule.onChatMessage(sender, message, this);
            if (consumed) {
                return dev.frost.miniverse.chat.ChatInterceptResult.CONSUME_SILENT;
            }
        }
        return dev.frost.miniverse.chat.ChatInterceptResult.PASS;
    }

    @Override
    public net.minecraft.util.ActionResult onUseBlock(ServerPlayerEntity player, net.minecraft.world.World world, net.minecraft.util.Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (this.getState() == GameState.RUNNING && this.currentPhase == Phase.ACTIVE && this.activeRule != null) {
            return this.activeRule.onUseBlock(player, world, hand, hitResult, this);
        }
        return net.minecraft.util.ActionResult.PASS;
    }

    private boolean isInsideArenaBounds(Vec3d pos) {
        List<RegionPart> bounds = this.mapConfig.arenaBounds();
        if (bounds.isEmpty()) {
            return true;
        }
        for (RegionPart r : bounds) {
            double minX = Math.min(r.min().x(), r.max().x()) - 1.5;
            double maxX = Math.max(r.min().x(), r.max().x()) + 2.5;
            double minY = Math.min(r.min().y(), r.max().y()) - 4.0;
            double maxY = 320.0; // Open sky ceiling — jumping, MLG launches, mortar strikes do not trigger out of bounds
            double minZ = Math.min(r.min().z(), r.max().z()) - 1.5;
            double maxZ = Math.max(r.min().z(), r.max().z()) + 2.5;
            if (pos.x >= minX && pos.x <= maxX && pos.y >= minY && pos.y <= maxY && pos.z >= minZ && pos.z <= maxZ) {
                return true;
            }
        }
        return false;
    }

    // --- Teleportation Helpers ---

    private MapPosition getOrAssignSpawnPosition(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        Integer slot = this.assignedSpawnSlots.get(uuid);
        if (slot != null) {
            if (slot >= 0 && slot < this.mapConfig.playerSpawns().size()) {
                return this.mapConfig.playerSpawns().get(slot);
            }
            return getLobbySpawn();
        }

        // Find first free slot among playerSpawns
        List<MapPosition> spawns = this.mapConfig.playerSpawns();
        int chosenSlot = -1;
        for (int i = 0; i < spawns.size(); i++) {
            if (this.occupiedSpawnSlots.add(i)) {
                chosenSlot = i;
                break;
            }
        }

        this.assignedSpawnSlots.put(uuid, chosenSlot);
        if (chosenSlot >= 0 && chosenSlot < spawns.size()) {
            return spawns.get(chosenSlot);
        }
        return getLobbySpawn();
    }

    private void teleportToAssignedSpawn(ServerPlayerEntity player) {
        MapPosition spawn = getOrAssignSpawnPosition(player);
        ServerWorld w = getWorld();
        if (w == null) return;
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
        player.setHealth(20.0f);
        player.getHungerManager().setFoodLevel(20);
        player.clearStatusEffects();
        player.teleport(w, spawn.x() + 0.5, spawn.y(), spawn.z() + 0.5, Set.of(), spawn.yaw(), spawn.pitch());
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private void teleportToLobby(ServerPlayerEntity player) {
        MapPosition lobby = getLobbySpawn();
        ServerWorld w = getWorld();
        if (w == null) return;
        player.teleport(w, lobby.x() + 0.5, lobby.y(), lobby.z() + 0.5, Set.of(), lobby.yaw(), lobby.pitch());
    }

    private MapPosition getStageSpawn(int index) {
        List<MapPosition> spawns = this.mapConfig.playerSpawns();
        if (spawns.isEmpty()) {
            return this.mapConfig.arenaCenter() != null ? this.mapConfig.arenaCenter() : MapPosition.of(0, 100, 0);
        }
        return spawns.get(Math.abs(index) % spawns.size());
    }

    private MapPosition getLobbySpawn() {
        List<MapPosition> spawns = this.mapConfig.lobbySpawns();
        if (!spawns.isEmpty()) {
            return spawns.get(0);
        }
        return getStageSpawn(0);
    }

    // --- Scoreboard Integration (F12) ---

    @Override
    protected void initScoreboard() {
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate("microparty", Text.literal("MICRO PARTY").formatted(Formatting.GOLD, Formatting.BOLD)));
        }
        this.scoreboard.clearLines();
        this.scoreboard.addBlankLine();
        this.roundLine = this.scoreboard.addLine(Text.literal("Round: §e0/" + this.settings.maxRounds()));
        this.speedLine = this.scoreboard.addLine(Text.literal("Speed: §a1.0x"));
        this.taskLine = this.scoreboard.addLine(Text.literal("Task: §7Waiting..."));
        this.timerLine = this.scoreboard.addLine(Text.literal(""));
        this.scoreboard.addBlankLine();

        this.playerLines.clear();
        for (int i = 0; i < 8; i++) {
            this.playerLines.add(this.scoreboard.addLine(Text.literal("")));
        }

        this.scoreboard.show(this.players());
        this.scoreboard.resendStructure();
    }

    private void updateScoreboard() {
        if (this.scoreboard == null) {
            return;
        }

        this.roundLine.setText(Text.literal("Round: §e" + this.currentRound + "§7/§e" + this.settings.maxRounds()));
        this.roundLine.updateAll();

        float speed = (1.0f / getSpeedFactor());
        this.speedLine.setText(Text.literal(String.format(Locale.ROOT, "Speed: §c%.1fx", speed)));
        this.speedLine.updateAll();

        if (this.currentPhase == Phase.SPEED_UP) {
            this.taskLine.setText(Text.literal("Task: §c⚡ SPEED UP!"));
        } else if (this.activeRule != null) {
            this.taskLine.setText(Text.literal("Task: §b" + this.activeRule.id().toUpperCase(Locale.ROOT)));
        } else {
            this.taskLine.setText(Text.literal("Task: §7Intermission"));
        }
        this.taskLine.updateAll();

        int seconds = (this.phaseTicksRemaining + 19) / 20;
        this.timerLine.setText(Text.literal("Timer: §e" + seconds + "s"));
        this.timerLine.updateAll();

        List<ServerPlayerEntity> participants = new ArrayList<>(this.players());
        for (int i = 0; i < this.playerLines.size(); i++) {
            ScoreboardLine line = this.playerLines.get(i);
            if (i < participants.size()) {
                ServerPlayerEntity p = participants.get(i);
                int lives = this.tracker.getLives(p.getUuid());
                String hearts = buildHearts(lives, this.settings.startingLives());
                line.setText(Text.literal("§f" + p.getName().getString() + ": " + hearts));
            } else {
                line.setText(Text.literal(""));
            }
            line.updateAll();
        }
    }

    private String buildHearts(int current, int max) {
        if (current <= 0) {
            return "§8[💀 ELIMINATED]";
        }
        StringBuilder sb = new StringBuilder("§c[");
        for (int i = 0; i < max; i++) {
            if (i < current) {
                sb.append("♥");
            } else {
                sb.append("§7♡§c");
            }
        }
        sb.append("§c]");
        return sb.toString();
    }

    // --- Rule Recency & Selection ---

    public MicroRule selectNextRule(List<MicroRule> pool) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }

        // 1. Strictly exclude rules that appeared in the last 3 rounds.
        // If pool is very small (e.g. pool <= 3), adaptively clamp exclusion count so candidate list is not empty.
        int strictExcludeCount = Math.min(3, Math.max(0, pool.size() - 1));

        Set<String> strictlyExcludedIds = new HashSet<>();
        int historySize = this.recentRuleHistory.size();
        for (int i = 0; i < strictExcludeCount && i < historySize; i++) {
            strictlyExcludedIds.add(this.recentRuleHistory.get(historySize - 1 - i));
        }

        List<MicroRule> candidates = new ArrayList<>(pool);
        candidates.removeIf(r -> strictlyExcludedIds.contains(r.id()));
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(pool);
        }

        // 2. Weighted selection: any rule after the 3rd round (e.g. 4-6 rounds ago)
        // has a lower priority / lower chance compared to rules not played recently.
        int totalWeight = 0;
        int[] weights = new int[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            MicroRule rule = candidates.get(i);
            int weight = getRuleSelectionWeight(rule.id());
            weights[i] = weight;
            totalWeight += weight;
        }

        MicroRule chosen = candidates.get(candidates.size() - 1);
        if (totalWeight > 0) {
            int roll = this.random.nextInt(totalWeight);
            int cumulative = 0;
            for (int i = 0; i < candidates.size(); i++) {
                cumulative += weights[i];
                if (roll < cumulative) {
                    chosen = candidates.get(i);
                    break;
                }
            }
        }

        this.recentRuleHistory.add(chosen.id());
        if (this.recentRuleHistory.size() > 20) {
            this.recentRuleHistory.remove(0);
        }
        this.lastRuleId = chosen.id();
        return chosen;
    }

    public int getRuleSelectionWeight(String ruleId) {
        int historySize = this.recentRuleHistory.size();
        int roundsAgo = -1;
        for (int i = historySize - 1; i >= 0; i--) {
            if (this.recentRuleHistory.get(i).equals(ruleId)) {
                roundsAgo = historySize - i;
                break;
            }
        }

        if (roundsAgo == -1 || roundsAgo >= 7) {
            return 10; // Fresh rule or played long ago: full chance
        } else if (roundsAgo == 4) {
            return 2;  // Just became eligible after 3 rounds: lower priority / lower chance
        } else if (roundsAgo == 5) {
            return 4;
        } else if (roundsAgo == 6) {
            return 7;
        } else {
            return 1;  // Fallback if small pool permitted roundsAgo <= 3
        }
    }

    public List<String> getRecentRuleHistory() {
        return new ArrayList<>(this.recentRuleHistory);
    }

    // --- Persistence (F06 / F20) ---

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject state = new JsonObject();
        state.addProperty("currentRound", this.currentRound);
        state.addProperty("lastRuleId", this.lastRuleId);
        JsonArray historyArr = new JsonArray();
        for (String id : this.recentRuleHistory) {
            historyArr.add(id);
        }
        state.add("recentRuleHistory", historyArr);
        state.addProperty("matchFinished", this.matchFinished);
        return state;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        if (state == null) return;
        if (state.has("currentRound")) {
            this.currentRound = state.get("currentRound").getAsInt();
        }
        if (state.has("lastRuleId")) {
            this.lastRuleId = state.get("lastRuleId").getAsString();
        }
        if (state.has("recentRuleHistory")) {
            this.recentRuleHistory.clear();
            for (JsonElement el : state.getAsJsonArray("recentRuleHistory")) {
                this.recentRuleHistory.add(el.getAsString());
            }
        } else if (!this.lastRuleId.isBlank()) {
            this.recentRuleHistory.clear();
            this.recentRuleHistory.add(this.lastRuleId);
        }
        if (state.has("matchFinished")) {
            this.matchFinished = state.get("matchFinished").getAsBoolean();
        }
    }
}
