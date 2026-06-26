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
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import java.util.UUID;

public class DuelsMinigame extends AbstractMinigame implements DeathAwareMinigame, SpawnPointAware, TeamManagerProvider {

    private ServerWorld world;
    private DuelsMetadata metadata = new DuelsMetadata(List.of(), List.of());
    private ArenaManager arenaManager;
    private DuelMatchManager matchManager;
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private String duelTypeId = "";
    private String kitId = "";
    private DuelType duelType;
    private Boolean previousNaturalRegen;
    private final TeamManager teamManager = new TeamManager();
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("duels");
    private final List<UUID> loadedTeam1 = new ArrayList<>();
    private final List<UUID> loadedTeam2 = new ArrayList<>();
    private DuelMatch activeMatch;
    private int totalRounds = 1;
    private net.minecraft.server.MinecraftServer server;

    /** Death framework — initialized in onMatchStart once we have a matchManager. */
    private DeathLifecycleManager deathLifecycleManager;

    private com.google.gson.JsonObject pendingArenaState;
    private com.google.gson.JsonObject pendingMatchManagerState;

    // -------------------------------------------------------------------------
    // DeathAwareMinigame
    // -------------------------------------------------------------------------

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }

    @Override
    public TeamManager teamManager() {
        return this.teamManager;
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
        this.applyVanillaGameRule(net.minecraft.world.GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_IMMEDIATE_RESPAWN, true);
        setState(GameState.WAITING_FOR_PLAYERS);
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
            MatchCreationResult result = this.matchManager.createMatch(this.kit().orElse(null), this.matchRules(), getTeamPlayers("team_1"), getTeamPlayers("team_2"));
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
        this.clearVanillaTeams();
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
        this.server = server;
        if ((this.getState() == GameState.STARTING || this.getState() == GameState.FROZEN) && this.activeMatch == null && this.canStartMatch()) {
            if (this.world != null) {
                this.ensureArenaManager(this.world);
            }
            MatchCreationResult result = this.matchManager.createMatch(this.kit().orElse(null), this.matchRules(), getTeamPlayers("team_1"), getTeamPlayers("team_2"));
            if (result.success()) {
                this.activeMatch = result.match().orElse(null);
                if (this.activeMatch != null) {
                    net.minecraft.util.math.Vec3d p1Spawn = this.activeMatch.getContext().getArena().getSpawn("player1");
                    net.minecraft.util.math.Vec3d p2Spawn = this.activeMatch.getContext().getArena().getSpawn("player2");
                    ServerWorld w = this.activeMatch.getContext().getArena().getWorld();
                    for (ServerPlayerEntity p : getTeamPlayers("team_1")) {
                        if (p1Spawn != null) p.teleport(w, p1Spawn.x, p1Spawn.y, p1Spawn.z, java.util.Set.of(), p.getYaw(), p.getPitch());
                    }
                    for (ServerPlayerEntity p : getTeamPlayers("team_2")) {
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
        if ("team_1".equals(team) || this.loadedTeam1.contains(player.getUuid())) {
            this.teamManager.assign(player, "team_1", "Team 1", dev.frost.miniverse.team.TeamRole.MEMBER);
            this.loadedTeam1.remove(player.getUuid());
        } else if ("team_2".equals(team) || this.loadedTeam2.contains(player.getUuid())) {
            this.teamManager.assign(player, "team_2", "Team 2", dev.frost.miniverse.team.TeamRole.MEMBER);
            this.loadedTeam2.remove(player.getUuid());
        }
        if (this.matchManager != null) {
            this.matchManager.updatePlayerReference(player);
        }
        this.syncVanillaTeams();
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
            && !getTeamPlayers("team_1").isEmpty()
            && !getTeamPlayers("team_2").isEmpty();
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
        
        if (this.pendingArenaState != null) {
            this.arenaManager.loadRuntimeState(this.pendingArenaState);
            this.pendingArenaState = null;
        }
        if (this.pendingMatchManagerState != null && world.getServer() != null) {
            this.matchManager.loadRuntimeState(this.pendingMatchManagerState, world.getServer());
            this.pendingMatchManagerState = null;
            
            // Restore activeMatch reference if we have any active matches
            if (this.activeMatch == null && !getTeamPlayers("team_1").isEmpty()) {
                this.activeMatch = this.matchManager.getMatchForPlayerUuid(getTeamPlayers("team_1").get(0).getUuid()).orElse(null);
            }
        }
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
                dev.frost.miniverse.team.TeamSnapshot s1 = getTeamSnapshot("team_1");
                if (s1 != null && s1.members().stream().anyMatch(m -> m.playerUuid().equals(playerId))) return "team_1";
                dev.frost.miniverse.team.TeamSnapshot s2 = getTeamSnapshot("team_2");
                if (s2 != null && s2.members().stream().anyMatch(m -> m.playerUuid().equals(playerId))) return "team_2";
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

    private dev.frost.miniverse.team.TeamSnapshot getTeamSnapshot(String teamId) {
        return this.teamManager.snapshots().stream().filter(s -> s.id().equals(teamId)).findFirst().orElse(null);
    }

    private List<ServerPlayerEntity> getTeamPlayers(String teamId) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        dev.frost.miniverse.team.TeamSnapshot snapshot = getTeamSnapshot(teamId);
        if (snapshot != null) {
            for (dev.frost.miniverse.team.TeamMembership m : snapshot.members()) {
                ServerPlayerEntity p = this.getPlayerByUuid(m.playerUuid());
                if (p != null) players.add(p);
            }
        }
        return players;
    }

    private List<ServerPlayerEntity> players() {
        List<ServerPlayerEntity> players = new ArrayList<>();
        players.addAll(getTeamPlayers("team_1"));
        players.addAll(getTeamPlayers("team_2"));
        return players;
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
        dev.frost.miniverse.team.TeamSnapshot s1 = getTeamSnapshot("team_1");
        if (s1 != null) {
            for (dev.frost.miniverse.team.TeamMembership m : s1.members()) {
                t1.add(m.playerUuid().toString());
            }
        }
        stateObj.add("team1", t1);

        com.google.gson.JsonArray t2 = new com.google.gson.JsonArray();
        dev.frost.miniverse.team.TeamSnapshot s2 = getTeamSnapshot("team_2");
        if (s2 != null) {
            for (dev.frost.miniverse.team.TeamMembership m : s2.members()) {
                t2.add(m.playerUuid().toString());
            }
        }
        stateObj.add("team2", t2);

        if (this.arenaManager != null) {
            stateObj.add("arenaManager", this.arenaManager.saveRuntimeState());
        }
        if (this.matchManager != null) {
            stateObj.add("matchManager", this.matchManager.saveRuntimeState());
        }

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
        if (stateObj.has("arenaManager")) {
            this.pendingArenaState = stateObj.getAsJsonObject("arenaManager");
        }
        if (stateObj.has("matchManager")) {
            this.pendingMatchManagerState = stateObj.getAsJsonObject("matchManager");
        }

        if (stateObj.has("team1")) {
            for (com.google.gson.JsonElement el : stateObj.getAsJsonArray("team1")) {
                this.loadedTeam1.add(UUID.fromString(el.getAsString()));
            }
        }
        if (stateObj.has("team2")) {
            for (com.google.gson.JsonElement el : stateObj.getAsJsonArray("team2")) {
                this.loadedTeam2.add(UUID.fromString(el.getAsString()));
            }
        }
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

    @Override
    protected void syncVanillaTeams() {
        net.minecraft.server.MinecraftServer srv = this.server != null ? this.server : (this.context != null ? this.context.nullableServer() : null);
        if (srv == null) {
            return;
        }
        this.vanillaTeams.syncSnapshots(srv, this.teamManager.snapshots(), snapshot -> {
            net.minecraft.util.Formatting color = "team_1".equals(snapshot.id()) ? net.minecraft.util.Formatting.RED : net.minecraft.util.Formatting.BLUE;
            return dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(net.minecraft.text.Text.literal("[" + snapshot.label() + "] ").formatted(color))
                .withFriendlyFireAllowed(false)
                .withCollisionRule(net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER);
        });
    }

    protected void clearVanillaTeams() {
        net.minecraft.server.MinecraftServer srv = this.server != null ? this.server : (this.context != null ? this.context.nullableServer() : null);
        if (srv != null) {
            this.vanillaTeams.clear(srv);
        }
    }
}
