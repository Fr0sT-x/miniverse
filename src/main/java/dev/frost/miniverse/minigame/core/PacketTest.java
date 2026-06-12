package dev.frost.miniverse.minigame.core;

import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreResetS2CPacket;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.text.Text;

import java.util.Optional;

public class PacketTest {
    public static void test() {
        Scoreboard sb = new Scoreboard();
        ScoreboardObjective obj = new ScoreboardObjective(sb, "test", ScoreboardCriterion.DUMMY, Text.literal("Test"), ScoreboardCriterion.RenderType.INTEGER, false, BlankNumberFormat.INSTANCE);
        
        ScoreboardObjectiveUpdateS2CPacket p1 = new ScoreboardObjectiveUpdateS2CPacket(obj, 0); // 0 = create
        ScoreboardDisplayS2CPacket p2 = new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, obj);
        
        ScoreboardScoreUpdateS2CPacket p3 = new ScoreboardScoreUpdateS2CPacket("player", "test", 10, Optional.of(Text.literal("Display Name")), Optional.of(BlankNumberFormat.INSTANCE));
        
        ScoreboardScoreResetS2CPacket p4 = new ScoreboardScoreResetS2CPacket("player", "test");
    }
}
