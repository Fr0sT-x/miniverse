package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.arena.ArenaManager;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

public class DuelsMinigame implements Minigame {

    private final ServerWorld world;
    private final DuelsMetadata metadata;
    private ArenaManager arenaManager;
    private DuelMatchManager matchManager;
    private GameState state = GameState.WAITING;

    public DuelsMinigame(ServerWorld world, DuelsMetadata metadata) {
        this.world = world;
        this.metadata = metadata;
    }

    public DuelMatchManager getMatchManager() {
        return matchManager;
    }

    @Override
    public void initialize() {
        this.arenaManager = new ArenaManager(world);
        
        // Register arenas from metadata
        metadata.arenas().forEach(arenaManager::registerArena);
        
        this.matchManager = new DuelMatchManager(arenaManager);

        setupEventInterceptors();
        
        setState(GameState.WAITING);
    }

    private void setupEventInterceptors() {
        // Enforce block breaking rules
        PlayerBlockBreakEvents.BEFORE.register((w, player, pos, state, blockEntity) -> {
            if (w == this.world && player instanceof ServerPlayerEntity spe) {
                return matchManager.getMatchForPlayer(spe).map(match -> {
                    return match.getContext().getMatchRules().allowBlockBreaking();
                }).orElse(false); // If not in a match, deny breaking.
            }
            return true;
        });

        // Enforce block placement rules
        UseBlockCallback.EVENT.register((player, w, hand, hitResult) -> {
            if (w == this.world && player instanceof ServerPlayerEntity spe) {
                return matchManager.getMatchForPlayer(spe).map(match -> {
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
    public void startGame() {
        setState(GameState.PLAYING);
        // The matchmaking logic will externally call matchManager.createMatch(...)
    }

    @Override
    public void stopGame() {
        setState(GameState.ENDING);
        // Reset all arenas
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (matchManager != null) {
            matchManager.handleDeath(player);
        }
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

    // Minigame-level build/break (we override these to false, using our custom interceptors instead)
    @Override
    public boolean canBuild() {
        return false;
    }

    @Override
    public boolean canBreakBlocks() {
        return false;
    }
}
