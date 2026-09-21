package dev.frost.miniverse.minigame.impl.dropper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
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
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRegionAware;
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
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

public class DropperMinigame extends AbstractMinigame implements
    PlayerDamageAware,
    PlayerRegionAware,
    SpawnPointAware,
    PlayerLeaveAware,
    DeathAwareMinigame,
    PersistentMinigame {

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private DropperSettings settings = DropperSettings.defaults();
    private DropperMapConfig mapConfig = new DropperMapConfig(List.of(), List.of());
    private List<DropperMapConfig.DropperLevel> matchLevels = new ArrayList<>();

    private final Map<UUID, Integer> playerLevelIndex = new HashMap<>();
    private final Map<UUID, Integer> playerFails = new HashMap<>();
    private final List<UUID> placements = new ArrayList<>();
    private final Map<UUID, Long> completedTimes = new HashMap<>();

    private long matchStartTime = 0;
    private int finalCountdownRemaining = -1;
    private int tickCounter = 0;
    private boolean matchFinished = false;

    private ScoreboardTemplate scoreboard;
    private ScoreboardLine timeLine;
    private ScoreboardLine levelsLine;
    private ScoreboardLine countdownLine;
    private final List<ScoreboardLine> playerLines = new ArrayList<>();

    private DeathLifecycleManager deathLifecycleManager;

    public DropperMinigame() {
    }

    public void applySettings(DropperSettings settings, DropperMapConfig mapConfig) {
        this.settings = settings != null ? settings : DropperSettings.defaults();
        this.mapConfig = mapConfig != null ? mapConfig : new DropperMapConfig(List.of(), List.of());
        this.buildMatchLevels();
    }

    @Override
    public String getName() {
        return DropperDefinition.DISPLAY_NAME;
    }

    public String getGameId() {
        return DropperDefinition.ID;
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    @Override
    public void setState(GameState state) {
        GameState oldState = this.getState();
        this.state = state == null ? GameState.WAITING_FOR_PLAYERS : state;

        if (oldState != GameState.FROZEN && this.getState() == GameState.FROZEN) {
            if (this.matchLevels.isEmpty()) {
                this.buildMatchLevels();
            }
            for (ServerPlayerEntity player : this.players()) {
                this.playerLevelIndex.putIfAbsent(player.getUuid(), 0);
                this.playerFails.putIfAbsent(player.getUuid(), 0);
                this.teleportToSpawn(player);
                player.changeGameMode(GameMode.ADVENTURE);
            }
        }
    }

    private void updateGameState(GameState state) {
        this.setState(state);
        if (this.context != null) {
            this.context.setState(state);
        }
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

    public List<ServerPlayerEntity> players() {
        return this.context != null ? this.context.liveParticipants() : List.of();
    }

    public ServerWorld getWorld() {
        if (this.context != null && this.context.nullableServer() != null) {
            return this.context.nullableServer().getOverworld();
        }
        return null;
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(GameRules.DO_IMMEDIATE_RESPAWN, false);
        this.applyVanillaGameRule(GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(GameRules.FALL_DAMAGE, true);
        this.applyVanillaGameRule(GameRules.DO_MOB_SPAWNING, false);
        this.applyVanillaGameRule(GameRules.DO_DAYLIGHT_CYCLE, false);
    }

    public MapValidationResult startValidation() {
        MapValidationResult.Builder builder = MapValidationResult.builder();
        if (this.mapConfig.levels().isEmpty()) {
            builder.error("Map has no valid Dropper Levels configured.");
        }
        if (this.players().isEmpty()) {
            builder.error("No players in match.");
        }
        return builder.build();
    }

    public boolean canStartMatch() {
        return !this.mapConfig.levels().isEmpty() && !this.players().isEmpty();
    }

    @Override
    protected void onMatchStart() {
        if (!this.canStartMatch()) {
            this.updateGameState(GameState.ENDING);
            return;
        }

        this.matchStartTime = System.currentTimeMillis();
        this.matchFinished = false;
        this.finalCountdownRemaining = -1;
        this.placements.clear();
        this.completedTimes.clear();
        this.playerLevelIndex.clear();
        this.playerFails.clear();

        this.buildMatchLevels();

        for (ServerPlayerEntity player : this.players()) {
            player.changeGameMode(GameMode.ADVENTURE);
            this.playerLevelIndex.put(player.getUuid(), 0);
            this.playerFails.put(player.getUuid(), 0);
            this.teleportToLevel(player, 0);
        }

        this.initScoreboard();
        this.initDeathFramework();
        this.updateGameState(GameState.RUNNING);
        this.updateScoreboard();

        GameMessenger.broadcast(this.players(), Text.literal("§6§l[DROPPER] §aGame started! Reach the bottom without hitting obstacles!"));
    }

    private void buildMatchLevels() {
        List<DropperMapConfig.DropperLevel> candidates = new ArrayList<>(this.mapConfig.levels());
        
        if (!this.settings.selectedLevelIds().isBlank()) {
            Set<String> selectedIds = new HashSet<>(Arrays.asList(this.settings.selectedLevelIds().split(",")));
            candidates.removeIf(l -> !selectedIds.contains(l.id()));
        }

        if (candidates.isEmpty()) {
            candidates.addAll(this.mapConfig.levels());
        }

        String mode = this.settings.selectionMode();
        if ("SHUFFLE".equalsIgnoreCase(mode)) {
            Collections.shuffle(candidates);
        } else if ("RANDOM_N".equalsIgnoreCase(mode)) {
            Collections.shuffle(candidates);
            // levelsToPlay <= 0 means "all" — no truncation
            if (this.settings.levelsToPlay() > 0) {
                int count = Math.min(this.settings.levelsToPlay(), candidates.size());
                candidates = new ArrayList<>(candidates.subList(0, Math.max(1, count)));
            }
        } else {
            // ORDER — levelsToPlay <= 0 means "all" — no truncation
            int count = this.settings.levelsToPlay();
            if (count > 0 && count < candidates.size()) {
                candidates = new ArrayList<>(candidates.subList(0, count));
            }
        }

        this.matchLevels = List.copyOf(candidates);
    }

    @Override
    protected void initScoreboard() {
        if (this.matchLevels.isEmpty()) {
            this.buildMatchLevels();
        }
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate("dropper", Text.literal("THE DROPPER").formatted(Formatting.GOLD, Formatting.BOLD)));
        }
        this.scoreboard.clearLines();
        this.scoreboard.addBlankLine();
        this.timeLine = this.scoreboard.addLine(Text.literal("Time: §e0:00"));
        this.levelsLine = this.scoreboard.addLine(Text.literal("Total Levels: §e" + this.matchLevels.size()));
        this.countdownLine = this.scoreboard.addLine(Text.literal(""));
        this.scoreboard.addBlankLine();

        this.playerLines.clear();
        for (int i = 0; i < 10; i++) {
            this.playerLines.add(this.scoreboard.addLine(Text.literal("")));
        }

        this.scoreboard.show(this.players());
        this.scoreboard.resendStructure();
    }

    private void initDeathFramework() {
        this.deathLifecycleManager = new DeathLifecycleManager(new DeathLifecycleConfig() {
            @Override
            public DeathPolicy getDeathPolicy() {
                return new DeathPolicy() {
                    @Override
                    public void execute(ServerPlayerEntity player, DeathContext context) {
                        handlePlayerFail(player);
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
                        handlePlayerFail(player);
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
                    int currentIdx = playerLevelIndex.getOrDefault(ctx.victimId(), 0);
                    MapPosition spawn = getSpawnForLevel(currentIdx);
                    ServerWorld w = getWorld();
                    return new RespawnStrategy.RespawnLocation(
                        w, new Vec3d(spawn.x(), spawn.y(), spawn.z()), spawn.yaw(), spawn.pitch()
                    );
                };
            }

            @Override
            public @Nullable String resolveTeamId(UUID playerId) {
                return null;
            }

            @Override
            public @Nullable String resolveMatchIdentifier() {
                return "dropper";
            }
        }, SpectatorService.getInstance());
    }

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.getState() != GameState.RUNNING) {
            return false;
        }

        if (this.placements.contains(player.getUuid())) {
            return false;
        }

        int currentIdx = this.playerLevelIndex.getOrDefault(player.getUuid(), 0);
        if (currentIdx < this.matchLevels.size()) {
            DropperMapConfig.DropperLevel currentLevel = this.matchLevels.get(currentIdx);
            if (this.isInsideGoal(player, currentLevel)) {
                this.handleLevelComplete(player, currentIdx);
                return false;
            }
        }

        // Intercept fall damage, void, kinetic/wall crash, fire/lava, and fatal damage
        if (source.isOf(DamageTypes.FALL) ||
            source.isOf(DamageTypes.OUT_OF_WORLD) ||
            source.isOf(DamageTypes.FLY_INTO_WALL) ||
            source.isOf(DamageTypes.IN_WALL) ||
            source.isOf(DamageTypes.LAVA) ||
            source.isOf(DamageTypes.DROWN) ||
            amount >= player.getHealth()) {
            
            this.handlePlayerFail(player);
            return false;
        }

        return false;
    }

    private boolean isInsideGoal(ServerPlayerEntity player, DropperMapConfig.DropperLevel level) {
        if (level == null || level.goalRegions() == null || level.goalRegions().isEmpty()) {
            return false;
        }
        for (dev.frost.miniverse.map.editor.RegionPart part : level.goalRegions()) {
            double minX = Math.min(part.min().x(), part.max().x());
            double minY = Math.min(part.min().y(), part.max().y());
            double minZ = Math.min(part.min().z(), part.max().z());
            double maxX = Math.max(part.min().x(), part.max().x()) + 1.0;
            double maxY = Math.max(part.min().y(), part.max().y()) + 1.0;
            double maxZ = Math.max(part.min().z(), part.max().z()) + 1.0;

            net.minecraft.util.math.Box goalBox = new net.minecraft.util.math.Box(
                minX, minY, minZ, maxX, maxY, maxZ
            ).expand(0.2);
            if (goalBox.intersects(player.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    private synchronized void handlePlayerFail(ServerPlayerEntity player) {
        if (this.getState() != GameState.RUNNING || this.placements.contains(player.getUuid())) {
            return;
        }

        int fails = this.playerFails.merge(player.getUuid(), 1, Integer::sum);
        int currentIdx = this.playerLevelIndex.getOrDefault(player.getUuid(), 0);

        player.networkHandler.sendPacket(new OverlayMessageS2CPacket(
            Text.literal("§c§lOUCH! §7Respawning... §8(Fails: §e" + fails + "§8)")
        ));

        if (this.settings.allowSkip() && fails == this.settings.skipFailsThreshold()) {
            player.sendMessage(Text.literal("§6§l[DROPPER] §eStuck? Type §b/dropper skip §eto advance to the next level!").formatted(Formatting.YELLOW));
        }

        this.teleportToLevel(player, currentIdx);

        // Play impactful drop / obstacle hit sound at respawn location
        player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_BIG_FALL, SoundCategory.PLAYERS, 1.0f, 0.85f);
        player.playSoundToPlayer(SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.4f, 1.5f);

        this.updateScoreboard();
    }

    @Override
    public void onPlayerEnterRegion(ServerPlayerEntity player, MapMarker region) {
        if (this.getState() != GameState.RUNNING || this.placements.contains(player.getUuid())) {
            return;
        }

        int currentIdx = this.playerLevelIndex.getOrDefault(player.getUuid(), 0);
        if (currentIdx < this.matchLevels.size()) {
            DropperMapConfig.DropperLevel currentLevel = this.matchLevels.get(currentIdx);
            if (region != null && region.id().equals(currentLevel.goalMarkerId())) {
                this.handleLevelComplete(player, currentIdx);
            }
        }
    }

    private synchronized void handleLevelComplete(ServerPlayerEntity player, int completedIdx) {
        int nextIdx = completedIdx + 1;
        this.playerLevelIndex.put(player.getUuid(), nextIdx);

        if (nextIdx >= this.matchLevels.size()) {
            // Player completed the final level!
            this.handlePlayerVictory(player);
        } else {
            // Moving to next level
            player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9f, 1.3f);
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§a§lLEVEL COMPLETE!")));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§7Advancing to Level " + (nextIdx + 1) + "/" + this.matchLevels.size())));

            this.teleportToLevel(player, nextIdx);
            this.updateScoreboard();
        }
    }

    private synchronized void handlePlayerVictory(ServerPlayerEntity player) {
        this.placements.add(player.getUuid());
        long elapsedMs = System.currentTimeMillis() - this.matchStartTime;
        this.completedTimes.put(player.getUuid(), elapsedMs);

        int rank = this.placements.size();
        int fails = this.playerFails.getOrDefault(player.getUuid(), 0);
        String timeStr = formatDuration(elapsedMs);

        player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§a§lCOURSE COMPLETE!")));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§e#" + rank + " Place §7• " + timeStr + " (" + fails + " fails)")));

        GameMessenger.broadcast(this.players(), Text.literal(
            "§6§l[DROPPER] §e" + player.getName().getString() + " §afinished in §e#" + rank + " Place §7(" + timeStr + ", " + fails + " fails)!"
        ));

        // Turn finisher into spectator mode with free-fly and player-teleport hotbar
        SpectatorService.getInstance().startSpectating(
            player,
            SpectatorPolicies.unrestricted(),
            SpectatorTargetProviders.roster(),
            SpectatorMode.STANDARD,
            null,
            GameMode.ADVENTURE,
            Text.literal("§aYou completed the Dropper! Spectating remaining runners...").formatted(Formatting.GREEN)
        );

        // Check if all players have completed the course
        long remainingRunners = this.players().stream()
            .filter(p -> !this.placements.contains(p.getUuid()))
            .count();

        if (remainingRunners == 0) {
            GameMessenger.broadcast(this.players(), Text.literal("§6§l[DROPPER] §aAll players have completed the course!"));
            this.finishMatch();
        } else if (rank == 1) {
            // First finisher starts the final countdown
            this.finalCountdownRemaining = this.settings.finalCountdownSeconds();
            GameMessenger.broadcast(this.players(), Text.literal(
                "§6§l[DROPPER] §e1st Place reached! §c" + this.finalCountdownRemaining + "s §eremaining for other players to finish!"
            ));
        }
    }

    public int handleCommandSkip(ServerPlayerEntity player) {
        if (this.getState() != GameState.RUNNING) {
            player.sendMessage(Text.literal("§cThe match is not active.").formatted(Formatting.RED));
            return 0;
        }
        if (this.placements.contains(player.getUuid())) {
            player.sendMessage(Text.literal("§cYou have already finished the course.").formatted(Formatting.RED));
            return 0;
        }
        if (!this.settings.allowSkip()) {
            player.sendMessage(Text.literal("§cLevel skipping is disabled for this match.").formatted(Formatting.RED));
            return 0;
        }

        int fails = this.playerFails.getOrDefault(player.getUuid(), 0);
        if (fails < this.settings.skipFailsThreshold()) {
            player.sendMessage(Text.literal("§cYou need at least " + this.settings.skipFailsThreshold() + " fails to skip this level! (Current: " + fails + ")").formatted(Formatting.RED));
            return 0;
        }

        int currentIdx = this.playerLevelIndex.getOrDefault(player.getUuid(), 0);
        GameMessenger.broadcast(this.players(), Text.literal("§7" + player.getName().getString() + " skipped Level " + (currentIdx + 1) + " after " + fails + " fails."));
        this.handleLevelComplete(player, currentIdx);
        return 1;
    }

    private void teleportToLevel(ServerPlayerEntity player, int levelIndex) {
        MapPosition spawn = this.getSpawnForLevel(levelIndex);
        ServerWorld w = player.getServerWorld();
        if (w == null) {
            w = this.getWorld();
        }
        if (w == null) return;
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        player.fallDistance = 0.0f;
        player.setHealth(20.0f);
        player.getHungerManager().setFoodLevel(20);
        player.clearStatusEffects();
        player.resetPortalCooldown();
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 4, false, false, false));
        player.teleport(w, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private MapPosition getSpawnForLevel(int levelIndex) {
        if (this.matchLevels.isEmpty()) {
            this.buildMatchLevels();
        }
        if (levelIndex >= 0 && levelIndex < this.matchLevels.size()) {
            return this.matchLevels.get(levelIndex).spawn();
        }
        if (!this.mapConfig.levels().isEmpty()) {
            int idx = Math.min(Math.max(0, levelIndex), this.mapConfig.levels().size() - 1);
            return this.mapConfig.levels().get(idx).spawn();
        }
        if (!this.mapConfig.lobbySpawns().isEmpty()) {
            return this.mapConfig.lobbySpawns().get(0);
        }
        return MapPosition.of(0, 100, 0);
    }

    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        if (this.matchLevels.isEmpty()) {
            this.buildMatchLevels();
        }
        int currentIdx = this.playerLevelIndex.getOrDefault(player.getUuid(), 0);
        this.teleportToLevel(player, currentIdx);
    }

    public void handlePlayerJoin(ServerPlayerEntity player) {
        if (this.matchLevels.isEmpty()) {
            this.buildMatchLevels();
        }
        this.playerLevelIndex.putIfAbsent(player.getUuid(), 0);
        this.playerFails.putIfAbsent(player.getUuid(), 0);
        this.teleportToSpawn(player);
        if (this.scoreboard != null) {
            this.scoreboard.show(List.of(player));
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleDisconnect(player);
        }

        if (this.getState() == GameState.RUNNING && !this.matchFinished) {
            long remaining = this.players().stream()
                .filter(p -> !p.getUuid().equals(player.getUuid()) && !this.placements.contains(p.getUuid()))
                .count();
            if (remaining == 0 && !this.placements.isEmpty()) {
                this.finishMatch();
            }
        }
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        if (this.getState() != GameState.RUNNING || this.matchFinished) {
            return;
        }

        this.tickCounter++;

        // Reset portal cooldown to completely prevent vanilla nether dimension teleportation
        for (ServerPlayerEntity p : this.context.liveParticipants()) {
            p.resetPortalCooldown();
        }

        // Active runner goal check safeguard (e.g. landing in portal or water without damage)
        for (ServerPlayerEntity p : this.context.liveParticipants()) {
            if (this.placements.contains(p.getUuid())) continue;
            int curLvl = this.playerLevelIndex.getOrDefault(p.getUuid(), 0);
            if (curLvl < this.matchLevels.size()) {
                DropperMapConfig.DropperLevel lvl = this.matchLevels.get(curLvl);
                if (this.isInsideGoal(p, lvl)) {
                    this.handleLevelComplete(p, curLvl);
                }
            }
        }

        // Update scoreboard every 10 ticks (0.5s)
        if (this.tickCounter % 10 == 0) {
            this.updateScoreboard();
        }

        // Ticking countdown & match time limit every 20 ticks (1s)
        if (this.tickCounter % 20 == 0) {
            long elapsedSeconds = (System.currentTimeMillis() - this.matchStartTime) / 1000;
            if (this.settings.timeLimitSeconds() > 0 && elapsedSeconds >= this.settings.timeLimitSeconds()) {
                GameMessenger.broadcast(this.players(), Text.literal("§c§l[DROPPER] §cTime limit reached!"));
                this.finishMatch();
                return;
            }

            if (this.finalCountdownRemaining > 0) {
                this.finalCountdownRemaining--;
                if (this.finalCountdownRemaining == 30 || this.finalCountdownRemaining == 15 || this.finalCountdownRemaining <= 5) {
                    GameMessenger.broadcast(this.players(), Text.literal("§c§l[DROPPER] §e" + this.finalCountdownRemaining + "s §eremaining!"));
                }
                if (this.finalCountdownRemaining == 0) {
                    GameMessenger.broadcast(this.players(), Text.literal("§c§l[DROPPER] §cTime's up!"));
                    this.finishMatch();
                }
            }
        }
    }

    private void updateScoreboard() {
        if (this.scoreboard == null) {
            return;
        }

        long elapsedMs = System.currentTimeMillis() - this.matchStartTime;
        this.timeLine.setText(Text.literal("Time: §e" + formatDuration(elapsedMs)));
        this.timeLine.updateAll();
        this.levelsLine.setText(Text.literal("Total Levels: §e" + this.matchLevels.size()));
        this.levelsLine.updateAll();

        if (this.finalCountdownRemaining >= 0) {
            this.countdownLine.setText(Text.literal("Closing in: §c" + this.finalCountdownRemaining + "s"));
        } else {
            this.countdownLine.setText(Text.literal(""));
        }
        this.countdownLine.updateAll();

        List<ServerPlayerEntity> sorted = new ArrayList<>(this.players());
        sorted.sort((p1, p2) -> {
            boolean f1 = placements.contains(p1.getUuid());
            boolean f2 = placements.contains(p2.getUuid());
            if (f1 && f2) {
                return Integer.compare(placements.indexOf(p1.getUuid()), placements.indexOf(p2.getUuid()));
            }
            if (f1) return -1;
            if (f2) return 1;

            int lvl1 = playerLevelIndex.getOrDefault(p1.getUuid(), 0);
            int lvl2 = playerLevelIndex.getOrDefault(p2.getUuid(), 0);
            if (lvl1 != lvl2) {
                return Integer.compare(lvl2, lvl1);
            }
            int fails1 = playerFails.getOrDefault(p1.getUuid(), 0);
            int fails2 = playerFails.getOrDefault(p2.getUuid(), 0);
            return Integer.compare(fails1, fails2);
        });

        int totalSlots = this.playerLines.size();
        for (int i = 0; i < totalSlots; i++) {
            ScoreboardLine line = this.playerLines.get(i);
            if (i < sorted.size()) {
                ServerPlayerEntity p = sorted.get(i);
                boolean finished = this.placements.contains(p.getUuid());
                int currentLevelNumber = this.playerLevelIndex.getOrDefault(p.getUuid(), 0) + 1;
                String prefix = finished ? "§a#" + (this.placements.indexOf(p.getUuid()) + 1) + " " : "§f";
                String progress = finished ? "§aCompleted" : "§eLevel " + currentLevelNumber + "/" + this.matchLevels.size();
                line.setText(Text.literal(prefix + p.getName().getString() + ": " + progress));
            } else {
                line.setText(Text.literal(""));
            }
            line.updateAll();
        }
    }

    private synchronized void finishMatch() {
        if (this.matchFinished) {
            return;
        }
        this.matchFinished = true;

        ServerPlayerEntity winner = null;
        if (!this.placements.isEmpty() && this.context != null && this.context.nullableServer() != null) {
            UUID winnerId = this.placements.get(0);
            winner = this.context.nullableServer().getPlayerManager().getPlayer(winnerId);
        }

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

    @Override
    protected void onMatchEnd() {
        if (this.deathLifecycleManager != null && this.context != null && this.context.nullableServer() != null) {
            this.deathLifecycleManager.handleMatchEnding(this.context.nullableServer().getPlayerManager()::getPlayer);
        }
        SpectatorService.getInstance().clearAll(true);
        this.updateGameState(GameState.STOPPED);
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long remSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remSeconds);
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject state = new JsonObject();
        state.addProperty("matchStartTime", this.matchStartTime);
        state.addProperty("finalCountdownRemaining", this.finalCountdownRemaining);
        JsonArray ranks = new JsonArray();
        for (UUID u : this.placements) {
            ranks.add(u.toString());
        }
        state.add("placements", ranks);
        return state;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        if (state == null) return;
        if (state.has("matchStartTime")) {
            this.matchStartTime = state.get("matchStartTime").getAsLong();
        }
        if (state.has("finalCountdownRemaining")) {
            this.finalCountdownRemaining = state.get("finalCountdownRemaining").getAsInt();
        }
    }
}
