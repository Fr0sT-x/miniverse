package dev.frost.miniverse.minigame.impl.bridge;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

public final class BridgeStatsEvents {
    private BridgeStatsEvents() {
    }

    public static final Event<GoalScored> GOAL_SCORED = EventFactory.createArrayBacked(GoalScored.class,
        listeners -> (player, teamId, points) -> {
            for (GoalScored listener : listeners) {
                listener.onGoalScored(player, teamId, points);
            }
        });

    public static final Event<MatchWon> MATCH_WON = EventFactory.createArrayBacked(MatchWon.class,
        listeners -> (winningTeamId, scoreRed, scoreBlue) -> {
            for (MatchWon listener : listeners) {
                listener.onMatchWon(winningTeamId, scoreRed, scoreBlue);
            }
        });

    public static final Event<MatchDraw> MATCH_DRAW = EventFactory.createArrayBacked(MatchDraw.class,
        listeners -> (score) -> {
            for (MatchDraw listener : listeners) {
                listener.onMatchDraw(score);
            }
        });

    @FunctionalInterface
    public interface GoalScored {
        void onGoalScored(ServerPlayerEntity player, String teamId, int points);
    }

    @FunctionalInterface
    public interface MatchWon {
        void onMatchWon(String winningTeamId, int scoreRed, int scoreBlue);
    }

    @FunctionalInterface
    public interface MatchDraw {
        void onMatchDraw(int score);
    }
}
