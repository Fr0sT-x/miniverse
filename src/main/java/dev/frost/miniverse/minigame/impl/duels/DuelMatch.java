package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.arena.ArenaState;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class DuelMatch {
    private final DuelMatchContext context;
    private DuelMatchState state;
    private int countdownSeconds = 3;
    private long lastTickTime;

    public DuelMatch(DuelMatchContext context) {
        this.context = context;
        this.state = DuelMatchState.RESERVED;
        this.lastTickTime = System.currentTimeMillis();
        
        context.getArena().setActiveMatchId(context.getMatchId());
        context.getArena().setState(ArenaState.RESERVED);
    }

    public DuelMatchContext getContext() {
        return context;
    }

    public DuelMatchState getState() {
        return state;
    }

    public void start() {
        // Teleport players
        Vec3d p1Spawn = context.getArena().getSpawn("player1");
        Vec3d p2Spawn = context.getArena().getSpawn("player2");

        if (p1Spawn != null && context.getPlayers().size() > 0) {
            ServerPlayerEntity p1 = context.getPlayers().get(0);
            p1.teleport(context.getArena().getWorld(), p1Spawn.x, p1Spawn.y, p1Spawn.z, p1.getYaw(), p1.getPitch());
            context.getKit().apply(p1);
            p1.heal(p1.getMaxHealth());
        }

        if (p2Spawn != null && context.getPlayers().size() > 1) {
            ServerPlayerEntity p2 = context.getPlayers().get(1);
            p2.teleport(context.getArena().getWorld(), p2Spawn.x, p2Spawn.y, p2Spawn.z, p2.getYaw(), p2.getPitch());
            context.getKit().apply(p2);
            p2.heal(p2.getMaxHealth());
        }

        this.state = DuelMatchState.TELEPORTED;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (state == DuelMatchState.TELEPORTED) {
            this.state = DuelMatchState.COUNTDOWN;
            this.lastTickTime = now;
            this.countdownSeconds = 3;
        } else if (state == DuelMatchState.COUNTDOWN) {
            if (now - lastTickTime >= 1000) {
                lastTickTime = now;
                if (countdownSeconds > 0) {
                    broadcastTitle(Text.literal(String.valueOf(countdownSeconds)).formatted(Formatting.YELLOW));
                    countdownSeconds--;
                } else {
                    broadcastTitle(Text.literal("FIGHT!").formatted(Formatting.RED));
                    this.state = DuelMatchState.ACTIVE;
                    context.getArena().setState(ArenaState.RUNNING);
                }
            }
        }
    }

    public void endMatch(ServerPlayerEntity winner, ServerPlayerEntity loser) {
        if (this.state == DuelMatchState.ENDING) return;
        this.state = DuelMatchState.ENDING;

        if (winner != null) {
            broadcastMessage(winner.getDisplayName().copy().append(" has won the duel!"));
        } else {
            broadcastMessage(Text.literal("The duel ended in a draw."));
        }

        // Clean up and reset arena
        context.getArena().startReset();
    }

    private void broadcastTitle(Text text) {
        TitleS2CPacket packet = new TitleS2CPacket(text);
        for (ServerPlayerEntity player : context.getPlayers()) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private void broadcastMessage(Text text) {
        for (ServerPlayerEntity player : context.getPlayers()) {
            player.sendMessage(text);
        }
    }
}
