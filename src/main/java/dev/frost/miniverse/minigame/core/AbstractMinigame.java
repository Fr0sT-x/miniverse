package dev.frost.miniverse.minigame.core;

import com.google.gson.JsonObject;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.minigame.core.corpse.CorpseManager;
import dev.frost.miniverse.minigame.core.event.*;
import dev.frost.miniverse.minigame.core.role.RoleManager;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Base class for all minigames enforcing the core framework features.
 */
public abstract class AbstractMinigame implements Minigame, RuntimeContextAware, ServerTickAware,
    ItemUseAware, PlayerDamageAware, EntityDeathAware, PlayerJoinAware, PlayerLeaveAware,
    PlayerRegionAware, PlayerRespawnAware, PersistentMinigame, PauseAwareMinigame, DynamicParticipantMinigame {

    protected MinigameRuntime runtime;
    protected MinigameContext context;
    private final Set<FrameworkModule> activeModules = new LinkedHashSet<>();
    private VanillaTeamAdapter vanillaTeamAdapter;

    @Override
    public final void attachContext(MinigameContext context) {
        this.context = context;
        if (MinigameManager.getInstance().getRuntime() != null && MinigameManager.getInstance().getRuntime().context() == context) {
            this.runtime = MinigameManager.getInstance().getRuntime();
        }
    }

    // --- Mandatory Lifecycle Hooks ---

    @Override
    public final void startGame() {
        if (this.isTeamBased()) {
            this.vanillaTeamAdapter = new VanillaTeamAdapter(this.getName().toLowerCase().replace(" ", "_"));
            this.syncVanillaTeams();
        }

        if (this.isScoreboardEnabled()) {
            this.initScoreboard();
        }

        this.onMatchStart();
    }

    @Override
    public final void stopGame() {
        this.onMatchEnd();

        if (this.context.nullableServer() != null) {
            for (FrameworkModule module : this.activeModules) {
                module.cleanup(this.context.nullableServer());
            }
        }
        this.activeModules.clear();

        if (this.vanillaTeamAdapter != null) {
            this.clearVanillaTeams();
        }

        this.clearParticipants();
    }

    protected abstract void onMatchStart();

    protected void onMatchEnd() {
    }

    protected boolean isTeamBased() {
        return true;
    }

    private final java.util.Map<net.minecraft.world.GameRules.Key<net.minecraft.world.GameRules.BooleanRule>, Boolean> queuedBooleanRules = new java.util.HashMap<>();

    protected void applyVanillaGameRule(net.minecraft.world.GameRules.Key<net.minecraft.world.GameRules.BooleanRule> key, boolean value) {
        if (this.context != null && this.context.nullableServer() != null) {
            for (net.minecraft.server.world.ServerWorld world : this.context.nullableServer().getWorlds()) {
                world.getGameRules().get(key).set(value, this.context.nullableServer());
            }
        } else {
            this.queuedBooleanRules.put(key, value);
        }
    }

    protected boolean isScoreboardEnabled() {
        return true;
    }

    protected void initScoreboard() {
        // Typically minigames set up their scoreboard lines in onMatchStart,
        // but this ensures the scoreboard is prepared.
    }

    private final java.util.Map<ServerPlayerEntity, Integer> pendingScoreboardPlayers = new java.util.WeakHashMap<>();

    // --- Ticking and Pause logic ---

    @Override
    public final void onServerTick(MinecraftServer server) {
        if (!this.pendingScoreboardPlayers.isEmpty()) {
            java.util.Iterator<java.util.Map.Entry<ServerPlayerEntity, Integer>> iterator = this.pendingScoreboardPlayers.entrySet().iterator();
            while (iterator.hasNext()) {
                java.util.Map.Entry<ServerPlayerEntity, Integer> entry = iterator.next();
                int ticks = entry.getValue() - 1;
                if (ticks <= 0) {
                    ServerPlayerEntity player = entry.getKey();
                    for (FrameworkModule module : this.activeModules) {
                        if (module instanceof dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate scoreboard) {
                            scoreboard.remove(player);
                            scoreboard.show(player);
                        }
                    }
                    iterator.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }
        
        if (!this.queuedBooleanRules.isEmpty()) {
            for (java.util.Map.Entry<net.minecraft.world.GameRules.Key<net.minecraft.world.GameRules.BooleanRule>, Boolean> entry : this.queuedBooleanRules.entrySet()) {
                for (net.minecraft.server.world.ServerWorld world : server.getWorlds()) {
                    world.getGameRules().get(entry.getKey()).set(entry.getValue(), server);
                }
            }
            this.queuedBooleanRules.clear();
        }
        
        if (this.context != null && this.getState() == GameState.PAUSED) {
            return;
        }
        if (this.context != null && this.checkProgression(this.context.roster()).blocked()) {
            return;
        }
        // tick mandatory modules if needed here
        this.onGameTick(server);
    }

    protected void onGameTick(MinecraftServer server) {
    }

    @Override
    public void onPause(GameState previousState) {
    }

    @Override
    public void onResume(GameState resumedState) {
    }

    // --- Optional Modules (Lazy Initialization) ---

    @SuppressWarnings("unchecked")
    protected <T> T getOrRegisterModule(Class<T> moduleClass, Supplier<T> factory) {
        for (FrameworkModule module : this.activeModules) {
            if (moduleClass.isInstance(module)) {
                return (T) module;
            }
        }
        T newModule = factory.get();
        if (newModule instanceof FrameworkModule frameworkModule) {
            this.activeModules.add(frameworkModule);
        } else if (newModule == SpectatorService.getInstance()) {
            // Special case for SpectatorService as it's a singleton and doesn't implement FrameworkModule directly
            this.activeModules.add(server -> SpectatorService.getInstance().clearAll(true));
        }
        return newModule;
    }

    protected SpectatorService spectator() {
        return this.getOrRegisterModule(SpectatorService.class, SpectatorService::getInstance);
    }

    protected RoleManager roles() {
        return this.getOrRegisterModule(RoleManager.class, RoleManager::new);
    }

    protected CorpseManager corpses() {
        return this.getOrRegisterModule(CorpseManager.class, CorpseManager::new);
    }

    // --- Reconnect & Late-Join ---

    public final void onPlayerReconnect(ServerPlayerEntity player) {
        if (this.isTeamBased() && this.vanillaTeamAdapter != null) {
            this.syncVanillaTeams();
        }
        this.pendingScoreboardPlayers.put(player, 60);
        this.onPlayerReconnectGame(player);
    }

    protected void onPlayerReconnectGame(ServerPlayerEntity player) {
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
    }

    @Override
    public void removeParticipantMidGame(ServerPlayerEntity player) {
    }

    @Override
    public void assignTeamMidGame(ServerPlayerEntity player, String teamId, String role) {
    }

    // --- Persistence (No-op Defaults) ---

    @Override
    public JsonObject saveRuntimeState() {
        return new JsonObject();
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
    }

    // --- Event "Aware" Default Empty Implementations ---

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand) {
        return ActionResult.PASS;
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        return true;
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
    }

    @Override
    public final void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        if (this.context != null && this.context.roster().contains(player)) {
            this.onPlayerReconnect(player);
        } else {
            this.onPlayerJoinGame(player, server);
        }
    }

    protected void onPlayerJoinGame(ServerPlayerEntity player, MinecraftServer server) {
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
    }

    @Override
    public void onPlayerEnterRegion(ServerPlayerEntity player, MapMarker region) {
    }

    @Override
    public void onPlayerExitRegion(ServerPlayerEntity player, MapMarker region) {
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
    }

    // --- Utility Methods ---

    protected void syncVanillaTeams() {
        if (this.vanillaTeamAdapter != null && this.context != null && this.context.nullableServer() != null) {
            if (this instanceof dev.frost.miniverse.team.TeamManagerProvider provider) {
                this.vanillaTeamAdapter.syncSnapshots(this.context.nullableServer(), provider.teamManager().snapshots(), null);
            }
        }
    }

    protected void clearVanillaTeams() {
        if (this.vanillaTeamAdapter != null && this.context != null && this.context.nullableServer() != null) {
            this.vanillaTeamAdapter.clear(this.context.nullableServer());
        }
    }

    protected void clearParticipants() {
        if (this.context != null && this.context.roster() != null) {
            this.context.roster().clear();
        }
    }

    @Nullable
    protected VanillaTeamAdapter getVanillaTeams() {
        return this.vanillaTeamAdapter;
    }

    @Nullable
    protected ServerPlayerEntity getPlayerByUuid(java.util.UUID uuid) {
        if (this.context != null && this.context.nullableServer() != null) {
            return this.context.nullableServer().getPlayerManager().getPlayer(uuid);
        }
        return null;
    }
}
