package dev.frost.miniverse.minigame.core.scoreboard;

import dev.frost.miniverse.minigame.core.FrameworkModule;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.function.Function;

public class ScoreboardTemplate implements FrameworkModule {
    private String objectiveName;
    private final Text title;
    private final List<ScoreboardLine> lines = new ArrayList<>();
    private final Set<ServerPlayerEntity> viewers = Collections.newSetFromMap(new WeakHashMap<>());
    
    // Minecraft limits scores to a large range, we start from 99 down.
    private int nextScore = 99;
    private int lineCounter = 0;

    public ScoreboardTemplate(String gameId, Text title) {
        String baseName = "mv_" + gameId.replace("-", "");
        this.objectiveName = baseName.substring(0, Math.min(baseName.length(), 16));
        this.title = title;
    }

    public ScoreboardTemplate(Text title) {
        this(UUID.randomUUID().toString(), title);
    }

    void setObjectiveName(String objectiveName) {
        this.objectiveName = objectiveName;
    }

    public String getObjectiveName() {
        return this.objectiveName;
    }

    public boolean isViewing(ServerPlayerEntity player) {
        return this.viewers.contains(player);
    }

    public Set<ServerPlayerEntity> getViewers() {
        return this.viewers;
    }

    public ScoreboardLine addLine(Text text) {
        String holderName = "line_" + (this.lineCounter++);
        ScoreboardLine line = new ScoreboardLine(this, holderName, this.nextScore--, text);
        this.lines.add(line);
        return line;
    }

    public void addBlankLine() {
        this.addLine(Text.literal(""));
    }

    public void show(Iterable<ServerPlayerEntity> players) {
        Scoreboard dummyBoard = new Scoreboard();
        ScoreboardObjective obj = new ScoreboardObjective(
            dummyBoard,
            this.objectiveName,
            ScoreboardCriterion.DUMMY,
            this.title,
            ScoreboardCriterion.RenderType.INTEGER,
            false,
            BlankNumberFormat.INSTANCE
        );

        ScoreboardObjectiveUpdateS2CPacket createPacket = new ScoreboardObjectiveUpdateS2CPacket(obj, 0);
        ScoreboardDisplayS2CPacket displayPacket = new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, obj);

        for (ServerPlayerEntity player : players) {
            if (this.viewers.add(player)) {
                player.networkHandler.sendPacket(createPacket);
                player.networkHandler.sendPacket(displayPacket);
                for (ScoreboardLine line : this.lines) {
                    player.networkHandler.sendPacket(line.createUpdatePacket());
                }
            }
        }
    }

    public void show(ServerPlayerEntity player) {
        this.show(List.of(player));
    }

    public void hide(Iterable<ServerPlayerEntity> players) {
        Scoreboard dummyBoard = new Scoreboard();
        ScoreboardObjective obj = new ScoreboardObjective(
            dummyBoard,
            this.objectiveName,
            ScoreboardCriterion.DUMMY,
            this.title,
            ScoreboardCriterion.RenderType.INTEGER,
            false,
            BlankNumberFormat.INSTANCE
        );
        ScoreboardObjectiveUpdateS2CPacket removePacket = new ScoreboardObjectiveUpdateS2CPacket(obj, 1);

        for (ServerPlayerEntity player : players) {
            if (this.viewers.remove(player)) {
                player.networkHandler.sendPacket(removePacket);
            }
        }
    }

    public void remove(ServerPlayerEntity player) {
        this.hide(List.of(player));
    }

    public void rebuild(Function<ServerPlayerEntity, ScoreboardTemplate> factory) {
        List<ServerPlayerEntity> currentViewers = new ArrayList<>(this.viewers);
        for (ServerPlayerEntity player : currentViewers) {
            this.remove(player);
            ScoreboardTemplate newTemplate = factory.apply(player);
            newTemplate.setObjectiveName(this.objectiveName);
            newTemplate.show(player);
            // Re-add to viewers so this base template still tracks them for cleanup
            this.viewers.add(player);
        }
    }

    public void clearLines() {
        this.lines.clear();
        this.lineCounter = 0;
        this.nextScore = 99;
    }

    public void resendStructure() {
        List<ServerPlayerEntity> currentViewers = new ArrayList<>(this.viewers);
        this.hide(currentViewers);
        this.show(currentViewers);
    }

    @Override
    public void cleanup(MinecraftServer server) {
        // Hide from all active viewers
        this.hide(new ArrayList<>(this.viewers));
    }
}
