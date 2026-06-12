package dev.frost.miniverse.minigame.core.scoreboard;

import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Optional;
import java.util.Set;

public class ScoreboardLine {
    private final ScoreboardTemplate template;
    private final String scoreHolderName;
    private final int score;
    private Text text;

    ScoreboardLine(ScoreboardTemplate template, String scoreHolderName, int score, Text text) {
        this.template = template;
        this.scoreHolderName = scoreHolderName;
        this.score = score;
        this.text = text;
    }

    public void setText(Text text) {
        this.text = text;
    }

    public void update(ServerPlayerEntity player) {
        if (this.template.isViewing(player)) {
            player.networkHandler.sendPacket(this.createUpdatePacket());
        }
    }

    public void updateAll() {
        ScoreboardScoreUpdateS2CPacket packet = this.createUpdatePacket();
        for (ServerPlayerEntity player : this.template.getViewers()) {
            player.networkHandler.sendPacket(packet);
        }
    }

    ScoreboardScoreUpdateS2CPacket createUpdatePacket() {
        return new ScoreboardScoreUpdateS2CPacket(
            this.scoreHolderName,
            this.template.getObjectiveName(),
            this.score,
            Optional.of(this.text),
            Optional.of(BlankNumberFormat.INSTANCE)
        );
    }
}
