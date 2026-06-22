package dev.frost.miniverse.minigame.core.death.policy.impl;

import dev.frost.miniverse.minigame.core.death.CancellationReason;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;

public class TimedRespawnPolicy implements PostDeathPolicy {
    private final DeathLifecycleManager manager;
    private final int delayTicks;
    
    private UUID victimId;
    private int ticksRemaining;
    private boolean active;
    private int lastSeconds = -1;

    public TimedRespawnPolicy(DeathLifecycleManager manager, int delayTicks) {
        this.manager = manager;
        this.delayTicks = delayTicks;
    }

    @Override
    public void start(ServerPlayerEntity victim, DeathContext context) {
        this.victimId = victim.getUuid();
        this.ticksRemaining = this.delayTicks;
        this.active = true;
        this.lastSeconds = -1;
        
        if (this.ticksRemaining <= 0) {
            this.active = false;
            this.manager.executeRespawn(victim);
        }
    }

    @Override
    public void cancel(CancellationReason reason) {
        this.active = false;
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(this.victimId);
        // Defensive guard: handleDisconnect should cancel this policy before this state is reached
        if (player == null || player.isDisconnected()) {
            this.active = false;
            return;
        }

        this.ticksRemaining--;

        if (this.manager.getConfig() instanceof ImmediateRespawnNotifier notifier) {
            dev.frost.miniverse.minigame.core.death.DeathContext context = this.manager.getContext(this.victimId);
            net.minecraft.entity.damage.DamageSource source = context != null ? context.damageSource() : null;
            
            int currentSeconds = (int) Math.ceil(this.ticksRemaining / 20.0);
            
            if (this.lastSeconds == -1) {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, this.delayTicks + 20, 20));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(notifier.getDeathSubtitle(player, source, this.ticksRemaining)));
                player.networkHandler.sendPacket(new TitleS2CPacket(notifier.getDeathTitle(player, source)));
                this.lastSeconds = currentSeconds;
            } else if (currentSeconds != this.lastSeconds) {
                player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(0, this.delayTicks + 20, 20));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(notifier.getDeathSubtitle(player, source, this.ticksRemaining)));
                player.networkHandler.sendPacket(new TitleS2CPacket(notifier.getDeathTitle(player, source)));
                this.lastSeconds = currentSeconds;
            }
        }
        
        if (this.ticksRemaining <= 0) {
            this.active = false;
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(0, 20, 10));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(net.minecraft.text.Text.empty()));
            player.networkHandler.sendPacket(new TitleS2CPacket(net.minecraft.text.Text.literal("Respawned!").formatted(net.minecraft.util.Formatting.GREEN)));
            this.manager.executeRespawn(player);
        }
    }
}
