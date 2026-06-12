package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.arena.ArenaManager;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.kit.Kit;
import dev.frost.miniverse.minigame.core.kit.KitRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;

public class DuelsMinigame extends AbstractMinigame {

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

    public void applySettings(Properties properties) {
        this.duelTypeId = properties.getProperty("duels.duelType", "").trim();
        this.kitId = properties.getProperty("duels.kitId", "").trim();
        this.duelType = DuelTypeRegistry.get(this.duelTypeId).orElse(null);
        String mapConfig = properties.getProperty("duels.mapConfig", "{}");
        try {
            this.metadata = DuelsMapConfig.metadataFromEditorConfig(com.google.gson.JsonParser.parseString(mapConfig).getAsJsonObject());
        } catch (Exception ignored) {
            this.metadata = new DuelsMetadata(List.of(), List.of());
        }
    }

    public DuelMatchManager getMatchManager() {
        return matchManager;
    }

    @Override
    public void initialize() {
        setState(GameState.WAITING_FOR_PLAYERS);
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        return GlobalMatchRules.defaults();
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

        MatchCreationResult result = this.matchManager.createMatch(this.kit().orElse(null), this.matchRules(), this.team1, this.team2);
        if (!result.success()) {
            for (ServerPlayerEntity player : this.players()) {
                player.sendMessage(net.minecraft.text.Text.literal(result.userFacingError()));
            }
            setState(GameState.ENDING);
            return;
        }

        this.activeMatch = result.match().orElse(null);
        if (this.activeMatch == null) {
            setState(GameState.ENDING);
            return;
        }
        this.activeMatch.start();
        setState(GameState.PLAYING);
    }

    @Override
    protected void onMatchEnd() {
        this.restoreGamerules();
        setState(GameState.ENDING);
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
        }
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (matchManager != null) {
            matchManager.handleDeath(player);
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayerEntity player) {
            this.onPlayerDeath(player);
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.matchManager != null) {
            this.matchManager.handleDisconnect(player);
        }
    }

    @Override
    protected void onGameTick(net.minecraft.server.MinecraftServer server) {
        if (this.matchManager != null) {
            this.matchManager.tick();
        }
        if (this.duelType != null && !this.duelType.allowHunger()) {
            for (ServerPlayerEntity player : this.players()) {
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0F);
            }
        }
    }

    public void onPlayerJoin(ServerPlayerEntity player, Properties properties) {
        this.ensureArenaManager((ServerWorld) player.getEntityWorld());
        String team = properties.getProperty("player." + player.getUuid() + ".team", "").trim().toLowerCase(java.util.Locale.ROOT);
        if ("team_1".equals(team)) {
            this.addUnique(this.team1, player);
        } else if ("team_2".equals(team)) {
            this.addUnique(this.team2, player);
        }
    }

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
            1
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
        // it must be resolved later or during attachContext/reconnect. We will save them for potential future use.
    }
}
