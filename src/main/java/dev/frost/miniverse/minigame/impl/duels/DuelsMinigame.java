package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.arena.ArenaManager;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.impl.SpectateForeverPolicy;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.kit.Kit;
import dev.frost.miniverse.minigame.core.kit.KitRegistry;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.impl.duels.death.DuelsDeathCallbacks;
import dev.frost.miniverse.minigame.impl.duels.death.DuelsDeathPolicy;
import dev.frost.miniverse.minigame.impl.duels.death.DuelsRespawnStrategy;
import dev.frost.miniverse.minigame.impl.duels.death.DuelsSpectatorPolicy;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;

public class DuelsMinigame extends AbstractMinigame implements DeathAwareMinigame, SpawnPointAware {

    private ServerWorld world;
    private DuelsMetadata metadata = new DuelsMetadata(List.of(), List.of());
    private ArenaManager arenaManager;
    private DuelMatchManager matchManager;
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private String duelTypeId = "";
    private String kitId = "";
    private DuelType duelType;
    private Boolean previousNaturalRegen;
    private final List<ServerPlayerEntity> team1 = new ArrayList<>();
    private final List<ServerPlayerEntity> team2 = new ArrayList<>();
    private DuelMatch activeMatch;
    private int totalRounds = 1;

    /** Death framework — initialized in onMatchStart once we have a matchManager. */
    private DeathLifecycleManager deathLifecycleManager;

    // -------------------------------------------------------------------------
    // DeathAwareMinigame
    // -------------------------------------------------------------------------

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    public void applySettings(Properties properties) {
        this.duelTypeId = properties.getProperty("duelType", properties.getProperty("duels.duelType", "")).trim();
        this.kitId = properties.getProperty("kitId", properties.getProperty("duels.kitId", "")).trim();
        this.duelType = DuelTypeRegistry.get(this.duelTypeId).orElse(null);
        String mapConfig = properties.getProperty("mapConfig", properties.getProperty("duels.mapConfig", "{}"));
        try {
            this.metadata = DuelsMapConfig.metadataFromEditorConfig(com.google.gson.JsonParser.parseString(mapConfig).getAsJsonObject());
        } catch (Exception ignored) {
            this.metadata = new DuelsMetadata(List.of(), List.of());
        }
        // Parse and validate totalRounds — must be a positive odd integer.
        // If an even number is provided (e.g. 4), it is clamped down to the nearest odd (3).
        try {
            int parsed = Integer.parseInt(properties.getProperty("rounds", properties.getProperty("duels.rounds", "1")).trim());
            parsed = Math.max(1, parsed);
            if (parsed % 2 == 0) parsed--; // ensure odd
            this.totalRounds = parsed;
        } catch (NumberFormatException ignored) {
            this.totalRounds = 1;
        }
    }

    public DuelMatchManager getMatchManager() {
        return matchManager;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void initialize() {
        setState(GameState.WAITING_FOR_PLAYERS);
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        // doImmediateRespawn=false required: framework calls changeGameMode(SPECTATOR) on fatal damage; 
        // client must not auto-respawn before the framework transition completes. See DECISIONS.md D04.
        return GlobalMatchRules.defaults(true, false);
    }

    @Override
    protected boolean isTeamBased() {
        return true;
    }

    private void setupEventInterceptors(ServerWorld world) {
        // Enforce block breaking rules
        PlayerBlockBreakEvents.BEFORE.register((w, player, pos, state, blockEntity) -> {
            if (w == this.world && player instanceof ServerPlayerEntity spe) {
                return matchManager != null && matchManager.getMatchForPlayer(spe)
                    .map(match -> match.getContext().getMatchRules().allowBlockBreaking())
                    .orElse(false);
            }
            return true;
        });

        // Enforce block placement rules
        UseBlockCallback.EVENT.register((player, w, hand, hitResult) -> {
            if (w == this.world && player instanceof ServerPlayerEntity spe) {
                return matchManager == null ? ActionResult.FAIL : matchManager.getMatchForPlayer(spe).map(match -> {
                    if (!match.getContext().getMatchRules().allowBlockPlacement()) {
                        return ActionResult.FAIL;
                    }
                    return ActionResult.PASS;
                }).orElse(ActionResult.FAIL);
            }
            return ActionResult.PASS;
        });
    }

    @Override
    protected void onMatchStart() {
        if (!this.canStartMatch()) {
            setState(GameState.ENDING);
            return;
        }

        // Initialise the Death Lifecycle Framework now that matchManager is ready
        this.initDeathFramework();

        if (this.activeMatch == null) {
            MatchCreationResult result = this.matchManager.createMatch(this.kit().orElse(null), this.matchRules(), this.team1, this.team2);
            if (!result.success()) {
                for (ServerPlayerEntity player : this.players()) {
                    player.sendMessage(net.minecraft.text.Text.literal(result.userFacingError()));
                }
                setState(GameState.ENDING);
                return;
            }
            this.activeMatch = result.match().orElse(null);
        }

        if (this.activeMatch == null) {
            setState(GameState.ENDING);
            return;
        }
        
        this.activeMatch.start();
        setState(GameState.RUNNING);
    }

    @Override
    protected void onMatchEnd() {
        this.restoreGamerules();
        setState(GameState.ENDING);
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
        }
        // Let the framework clean up any in-flight spectator states
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleMatchEnding(this::getPlayerByUuid);
        }
    }

    // -------------------------------------------------------------------------
    // Death events — routed through the Death Lifecycle Framework
    // -------------------------------------------------------------------------

    /**
     * Called by the framework event router when a player entity dies.
     * We delegate to the DeathLifecycleManager instead of directly poking DuelMatchManager.
     * DuelsDeathCallbacks.onDeathProcessed will call matchManager.handleDeath once
     * the framework has finished its own processing.
     */
    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayerEntity player && this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleFatalDamage(player, source);
        }
    }

    /**
     * Legacy hook — kept as a no-op since onEntityDeath now handles player deaths.
     * The framework routes through handleFatalDamage which supersedes this.
     */
    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        // Handled by onEntityDeath → DeathLifecycleManager.handleFatalDamage
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        // Clean up any active spectator/death state in the framework
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleDisconnect(player);
        }
        // Still notify the match manager for the round win-condition check
        // (gated internally on ACTIVE state, so safe to call regardless)
        if (this.matchManager != null) {
            this.matchManager.handleDisconnect(player);
        }
    }

    // -------------------------------------------------------------------------
    // Game tick
    // -------------------------------------------------------------------------

    @Override
    protected void onGameTick(net.minecraft.server.MinecraftServer server) {
        if ((this.getState() == GameState.STARTING || this.getState() == GameState.FROZEN) && this.activeMatch == null && this.canStartMatch()) {
            if (this.world != null) {
                this.ensureArenaManager(this.world);
            }
            MatchCreationResult result = this.matchManager.createMatch(this.kit().orElse(null), this.matchRules(), this.team1, this.team2);
            if (result.success()) {
                this.activeMatch = result.match().orElse(null);
                if (this.activeMatch != null) {
                    net.minecraft.util.math.Vec3d p1Spawn = this.activeMatch.getContext().getArena().getSpawn("player1");
                    net.minecraft.util.math.Vec3d p2Spawn = this.activeMatch.getContext().getArena().getSpawn("player2");
                    ServerWorld w = this.activeMatch.getContext().getArena().getWorld();
                    for (ServerPlayerEntity p : this.team1) {
                        if (p1Spawn != null) p.teleport(w, p1Spawn.x, p1Spawn.y, p1Spawn.z, java.util.Set.of(), p.getYaw(), p.getPitch());
                    }
                    for (ServerPlayerEntity p : this.team2) {
                        if (p2Spawn != null) p.teleport(w, p2Spawn.x, p2Spawn.y, p2Spawn.z, java.util.Set.of(), p.getYaw(), p.getPitch());
                    }
                }
            }
        }

        if (this.matchManager != null) {
            this.matchManager.tick();
        }
        // Tick any active post-death policies (e.g. timed respawn countdowns)
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.tick(server);
        }
        if (this.duelType != null && !this.duelType.allowHunger()) {
            for (ServerPlayerEntity player : this.players()) {
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0F);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Player join / spawn
    // -------------------------------------------------------------------------

    public void onPlayerJoin(ServerPlayerEntity player, Properties properties) {
        this.ensureArenaManager((ServerWorld) player.getEntityWorld());
        String team = properties.getProperty("player." + player.getUuid() + ".team", "").trim().toLowerCase(java.util.Locale.ROOT);
        if ("team_1".equals(team)) {
            this.addUnique(this.team1, player);
        } else if ("team_2".equals(team)) {
            this.addUnique(this.team2, player);
        }
    }

    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        // Duels allocates arenas *after* the freeze screen finishes (in onMatchStart).
        // To avoid players floating at world spawn during the freeze screen, we teleport
        // them to the first arena's spectator spawn just to get them into the map.
        // They will be teleported to their actual combat arena in DuelMatch.start().
        if (this.metadata != null && !this.metadata.arenas().isEmpty()) {
            dev.frost.miniverse.minigame.arena.ArenaRegion firstArena = this.metadata.arenas().getFirst();
            net.minecraft.util.math.Vec3d spawn = firstArena.spawns().get("spectator");
            if (spawn == null) {
                spawn = firstArena.spawns().get("player1");
            }
            if (spawn != null && player.getEntityWorld() instanceof ServerWorld sw) {
                player.teleport(sw, spawn.x, spawn.y, spawn.z, java.util.Set.of(), player.getYaw(), player.getPitch());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public boolean canStartMatch() {
        return this.matchManager != null
            && !this.metadata.arenas().isEmpty()
            && this.duelType != null
            && this.kit().isPresent()
            && !this.team1.isEmpty()
            && !this.team2.isEmpty();
    }

    private void ensureArenaManager(ServerWorld world) {
        if (this.arenaManager != null) {
            return;
        }
        this.world = world;
        this.arenaManager = new ArenaManager(world);
        this.metadata.arenas().forEach(this.arenaManager::registerArena);
        this.matchManager = new DuelMatchManager(this.arenaManager);
        this.setupEventInterceptors(world);
        this.applyGamerules();
    }

    /**
     * Wires the Death Lifecycle Framework to the Duels match manager.
     * Must be called after matchManager is initialised (i.e. inside onMatchStart).
     */
    private void initDeathFramework() {
        DuelMatchManager capturedManager = this.matchManager;
        DuelsMinigame self = this;

        DeathLifecycleConfig config = new DeathLifecycleConfig() {
            @Override
            public dev.frost.miniverse.minigame.core.death.policy.DeathPolicy getDeathPolicy() {
                return new DuelsDeathPolicy();
            }

            @Override
            public dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy getSpectatorPolicy() {
                return new DuelsSpectatorPolicy(SpectatorService.getInstance());
            }

            @Override
            public dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy createPostDeathPolicy() {
                // Players spectate until the round ends (when startRound() brings them back).
                // SpectateForeverPolicy does exactly this — no timer, no auto-respawn.
                return new SpectateForeverPolicy();
            }

            @Override
            public dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy getRespawnStrategy() {
                return new DuelsRespawnStrategy(self);
            }

            @Override
            public GameMode resolveRespawnGameMode() {
                return GameMode.SPECTATOR;
            }

            @Override
            public dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks getCallbacks() {
                return new DuelsDeathCallbacks(capturedManager);
            }

            @Override
            public String resolveTeamId(java.util.UUID playerId) {
                // Identify which team a player belongs to for context metadata
                for (ServerPlayerEntity p : team1) {
                    if (p.getUuid().equals(playerId)) return "team_1";
                }
                for (ServerPlayerEntity p : team2) {
                    if (p.getUuid().equals(playerId)) return "team_2";
                }
                return null;
            }

            @Override
            public String resolveMatchIdentifier() {
                return "duels";
            }
        };

        this.deathLifecycleManager = new DeathLifecycleManager(config, SpectatorService.getInstance());
    }

    private MatchRules matchRules() {
        boolean allowPlacement = this.duelType != null && this.duelType.allowBuilding();
        boolean allowBreaking = this.duelType != null && this.duelType.allowBreaking();
        return new MatchRules(
            allowPlacement,
            allowBreaking,
            false,
            false,
            false,
            List.of("duel_type:" + this.duelTypeId),
            1,
            this.totalRounds
        );
    }

    private Optional<Kit> kit() {
        Identifier id = Identifier.tryParse(this.kitId);
        return id == null ? Optional.empty() : KitRegistry.get(id);
    }

    private void applyGamerules() {
        if (this.world == null || this.duelType == null) {
            return;
        }
        GameRules.BooleanRule naturalRegen = this.world.getGameRules().get(GameRules.NATURAL_REGENERATION);
        this.previousNaturalRegen = naturalRegen.get();
        naturalRegen.set(this.duelType.naturalRegen(), this.world.getServer());
    }

    private void restoreGamerules() {
        if (this.world == null || this.previousNaturalRegen == null) {
            return;
        }
        this.world.getGameRules().get(GameRules.NATURAL_REGENERATION).set(this.previousNaturalRegen, this.world.getServer());
        this.previousNaturalRegen = null;
    }

    private List<ServerPlayerEntity> players() {
        List<ServerPlayerEntity> players = new ArrayList<>();
        players.addAll(this.team1);
        players.addAll(this.team2);
        return players;
    }

    private void addUnique(List<ServerPlayerEntity> players, ServerPlayerEntity player) {
        players.removeIf(existing -> existing.getUuid().equals(player.getUuid()));
        players.add(player);
    }

    // -------------------------------------------------------------------------
    // Minigame boilerplate
    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        return "Duels";
    }

    @Override
    public GameState getState() {
        return state;
    }

    @Override
    public void setState(GameState state) {
        this.state = state;
    }

    @Override
    public boolean canBuild() {
        return this.duelType != null && this.duelType.allowBuilding();
    }

    @Override
    public boolean canBreakBlocks() {
        return this.duelType != null && this.duelType.allowBreaking();
    }

    @Override
    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject stateObj = new com.google.gson.JsonObject();
        stateObj.addProperty("state", this.state.name());
        stateObj.addProperty("duelTypeId", this.duelTypeId);
        stateObj.addProperty("kitId", this.kitId);

        com.google.gson.JsonArray t1 = new com.google.gson.JsonArray();
        for (ServerPlayerEntity p : this.team1) {
            t1.add(p.getUuidAsString());
        }
        stateObj.add("team1", t1);

        com.google.gson.JsonArray t2 = new com.google.gson.JsonArray();
        for (ServerPlayerEntity p : this.team2) {
            t2.add(p.getUuidAsString());
        }
        stateObj.add("team2", t2);

        return stateObj;
    }

    @Override
    public void loadRuntimeState(com.google.gson.JsonObject stateObj) {
        if (stateObj == null) return;
        if (stateObj.has("state")) {
            try {
                this.state = GameState.valueOf(stateObj.get("state").getAsString());
            } catch (Exception ignored) {}
        }
        if (stateObj.has("duelTypeId")) {
            this.duelTypeId = stateObj.get("duelTypeId").getAsString();
            this.duelType = DuelTypeRegistry.get(this.duelTypeId).orElse(null);
        }
        if (stateObj.has("kitId")) {
            this.kitId = stateObj.get("kitId").getAsString();
        }

        // Cannot restore team1 and team2 directly since ServerPlayerEntity isn't available at load time,
        // it must be resolved later or during attachContext/reconnect.
    }

    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        net.minecraft.server.MinecraftServer server = this.context != null ? this.context.nullableServer() : null;
        // Block progression only when an entire team is offline — if only some members
        // are missing, the round continues normally (they can rejoin mid-round).
        if (this.matchManager != null && server != null) {
            if (this.matchManager.isTeam1FullyAbsent(server)) {
                return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(
                    true, null,
                    net.minecraft.text.Text.literal("Waiting for the other team to reconnect...").formatted(net.minecraft.util.Formatting.RED));
            }
            if (this.matchManager.isTeam2FullyAbsent(server)) {
                return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(
                    true, null,
                    net.minecraft.text.Text.literal("Waiting for the other team to reconnect...").formatted(net.minecraft.util.Formatting.RED));
            }
        } else {
            // Fallback before matchManager is initialised: require at least 2 online
            int onlineCount = roster.onlinePlayers(server).size();
            if (onlineCount < 2) {
                return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(
                    true, null,
                    net.minecraft.text.Text.literal("Waiting for your opponent to reconnect...").formatted(net.minecraft.util.Formatting.RED));
            }
        }
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }

    /** Exposed for DuelsRespawnStrategy. */
    @Override
    public ServerPlayerEntity getPlayerByUuid(java.util.UUID uuid) {
        return super.getPlayerByUuid(uuid);
    }

    /** Exposed for DuelsDeathCallbacks and DuelsRespawnStrategy. */
    public dev.frost.miniverse.minigame.core.MinigameContext getContext() {
        return context;
    }
}
