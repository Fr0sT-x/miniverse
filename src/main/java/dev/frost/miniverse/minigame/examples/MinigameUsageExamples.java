package dev.frost.miniverse.minigame.examples;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame.ManhuntRole;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Example usage of the Minigame Framework.
 * This class demonstrates how to use the core API and implementations.
 *
 * NOT part of the production code - provided for reference only.
 */
public class MinigameUsageExamples {

    /**
     * Example 1: Starting a Manhunt game with players
     */
    public static void example1_StartManhuntGame(ServerPlayerEntity speedrunner, ServerPlayerEntity hunter1, ServerPlayerEntity hunter2) {
        // Get the singleton manager
        MinigameManager manager = MinigameManager.getInstance();

        // Create a new Manhunt minigame
        ManhuntMinigame manhunt = new ManhuntMinigame();

        // Set it as the active minigame
        manager.setActiveMinigame(manhunt);

        // Add players as participants
        manager.addParticipant(speedrunner);
        manager.addParticipant(hunter1);
        manager.addParticipant(hunter2);

        // Assign roles
        manhunt.setPlayerRole(speedrunner, ManhuntRole.SPEEDRUNNER);
        manhunt.setPlayerRole(hunter1, ManhuntRole.HUNTER);
        manhunt.setPlayerRole(hunter2, ManhuntRole.HUNTER);

        // Start the game
        manhunt.startGame();
        manager.setCurrentState(GameState.STARTING);
        manager.setCurrentState(GameState.RUNNING);
    }

    /**
     * Example 2: Handling a player death event
     */
    public static void example2_PlayerDeathEvent(ServerPlayerEntity deadPlayer) {
        MinigameManager manager = MinigameManager.getInstance();

        // Check if a minigame is active
        if (manager.isMinigameActive()) {
            // Notify the minigame about the death
            manager.getActiveMinigame().onPlayerDeath(deadPlayer);

            // Check if the game ended
            if (manager.getCurrentState() == GameState.ENDING) {
                System.out.println("Game has ended!");
            }
        }
    }

    /**
     * Example 3: Periodic compass updates (called from server tick)
     */
    public static void example3_TickUpdate() {
        MinigameManager manager = MinigameManager.getInstance();

        if (manager.isMinigameActive() && manager.getActiveMinigame() instanceof ManhuntMinigame) {
            ManhuntMinigame manhunt = (ManhuntMinigame) manager.getActiveMinigame();

            // Update compasses every 20 ticks (1 second)
            if (manhunt.isGameActive()) {
                manhunt.updateHunterCompasses();
            }
        }
    }

    /**
     * Example 4: Ending a game
     */
    public static void example4_EndGame() {
        MinigameManager manager = MinigameManager.getInstance();

        if (manager.isMinigameActive()) {
            manager.getActiveMinigame().stopGame();
            manager.reset();
        }
    }

    /**
     * Example 5: Checking game state and participants
     */
    public static void example5_CheckGameStatus() {
        MinigameManager manager = MinigameManager.getInstance();

        if (manager.isMinigameActive()) {
            GameState state = manager.getCurrentState();
            int playerCount = manager.getParticipantCount();

            System.out.println("Current game: " + manager.getActiveMinigame().getName());
            System.out.println("State: " + state);
            System.out.println("Players: " + playerCount);

            // List all participants
            for (ServerPlayerEntity player : manager.getParticipants()) {
                System.out.println("  - " + player.getName().getString());
            }
        } else {
            System.out.println("No minigame is currently active");
        }
    }

    /**
     * Example 6: Checking if a specific player is in a minigame
     */
    public static void example6_IsPlayerParticipant(ServerPlayerEntity player) {
        MinigameManager manager = MinigameManager.getInstance();

        if (manager.isParticipant(player)) {
            System.out.println(player.getName().getString() + " is currently in a minigame");
        } else {
            System.out.println(player.getName().getString() + " is not in any minigame");
        }
    }

    /**
     * Example 7: Getting all players of a specific role in Manhunt
     */
    public static void example7_GetPlayersByRole() {
        MinigameManager manager = MinigameManager.getInstance();

        if (manager.isMinigameActive() && manager.getActiveMinigame() instanceof ManhuntMinigame) {
            ManhuntMinigame manhunt = (ManhuntMinigame) manager.getActiveMinigame();

            System.out.println("Speedrunners:");
            for (ServerPlayerEntity speedrunner : manhunt.getSpeedrunners()) {
                System.out.println("  - " + speedrunner.getName().getString());
            }

            System.out.println("Hunters:");
            for (ServerPlayerEntity hunter : manhunt.getHunters()) {
                System.out.println("  - " + hunter.getName().getString());
            }

            System.out.println("Alive Speedrunners:");
            for (ServerPlayerEntity alive : manhunt.getAliveSpeedrunners()) {
                System.out.println("  - " + alive.getName().getString());
            }

            System.out.println("Dead Speedrunners (Spectators):");
            for (ServerPlayerEntity dead : manhunt.getDeadSpeedrunners()) {
                System.out.println("  - " + dead.getName().getString());
            }
        }
    }

    /**
     * Example 8: Integrating with event listeners (Pseudo-code for mixins)
     *
     * This would typically be implemented in a mixin:
     *
     * @Mixin(ServerPlayerEntity.class)
     * public class ServerPlayerEntityMixin {
     *     @Inject(method = "onDeath", ...)
     *     private void onPlayerDeath(DamageSource source, CallbackInfo ci) {
     *         ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
     *         MinigameManager manager = MinigameManager.getInstance();
     *
     *         if (manager.isMinigameActive()) {
     *             manager.getActiveMinigame().onPlayerDeath(player);
     *         }
     *     }
     * }
     */
    public static void example8_MixinIntegration() {
        // This is pseudo-code showing how mixins would integrate with the framework
        // See MINIGAME_FRAMEWORK_GUIDE.md for details
    }
}

