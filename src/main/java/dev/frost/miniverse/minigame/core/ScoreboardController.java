package dev.frost.miniverse.minigame.core;

import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public final class ScoreboardController {
    private final String objectiveName;
    private final Text displayName;

    public ScoreboardController(String objectiveName, Text displayName) {
        this.objectiveName = objectiveName;
        this.displayName = displayName;
    }

    @Nullable
    public ScoreboardObjective getOrCreate(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectives().stream()
            .filter(existing -> this.objectiveName.equals(existing.getName()))
            .findFirst()
            .orElseGet(() -> scoreboard.addObjective(
                this.objectiveName,
                ScoreboardCriterion.DUMMY,
                this.displayName,
                ScoreboardCriterion.RenderType.INTEGER,
                false,
                null
            ));

        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective);
        return objective;
    }

    public void setScore(MinecraftServer server, String label, int value) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = this.getOrCreate(server);
        if (objective == null) {
            return;
        }

        ScoreHolder holder = ScoreHolder.fromName(label);
        ScoreAccess score = scoreboard.getOrCreateScore(holder, objective);
        score.setScore(value);
    }

    public void clear(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectives().stream()
            .filter(existing -> this.objectiveName.equals(existing.getName()))
            .findFirst()
            .orElse(null);

        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }
}
