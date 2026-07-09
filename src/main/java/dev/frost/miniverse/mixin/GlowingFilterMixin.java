package dev.frost.miniverse.mixin;

import dev.frost.miniverse.minigame.core.visibility.TeamGlowVisibility;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

@Mixin(net.minecraft.server.network.ServerCommonNetworkHandler.class)
public abstract class GlowingFilterMixin {
    @ModifyVariable(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> miniverse$filterGlowing(Packet<?> packet) {
        if (packet instanceof EntityTrackerUpdateS2CPacket metadataPacket && (Object) this instanceof ServerPlayNetworkHandler playHandler) {
            Entity target = playHandler.player.getWorld().getEntityById(metadataPacket.id());

            if (target instanceof ServerPlayerEntity targetPlayer
                    && targetPlayer != playHandler.player
                    && !TeamGlowVisibility.canViewerSeeGlowing(playHandler.player, targetPlayer)) {
                List<DataTracker.SerializedEntry<?>> entries = metadataPacket.trackedValues();
                if (entries != null) {
                    boolean needsClone = false;
                    for (DataTracker.SerializedEntry<?> entry : entries) {
                        if (entry.id() == TeamGlowVisibility.ENTITY_FLAGS_TRACKED_DATA_ID
                                && entry.value() instanceof Byte b
                                && (b & TeamGlowVisibility.GLOWING_FLAG) != 0) {
                            needsClone = true;
                            break;
                        }
                    }
                    
                    if (needsClone) {
                        List<DataTracker.SerializedEntry<?>> newEntries = new ArrayList<>();
                        for (DataTracker.SerializedEntry<?> entry : entries) {
                            if (entry.id() == TeamGlowVisibility.ENTITY_FLAGS_TRACKED_DATA_ID
                                    && entry.value() instanceof Byte b
                                    && (b & TeamGlowVisibility.GLOWING_FLAG) != 0) {
                                byte newFlags = (byte) (b & ~TeamGlowVisibility.GLOWING_FLAG);
                                @SuppressWarnings("unchecked")
                                DataTracker.SerializedEntry<Byte> cloned = new DataTracker.SerializedEntry<>(
                                    entry.id(),
                                    (net.minecraft.entity.data.TrackedDataHandler<Byte>) entry.handler(),
                                    newFlags
                                );
                                newEntries.add(cloned);
                            } else {
                                newEntries.add(entry);
                            }
                        }
                        return new EntityTrackerUpdateS2CPacket(metadataPacket.id(), newEntries);
                    }
                }
            }
        }
        return packet;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void miniverse$cancelGlowingEffect(Packet<?> packet, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (packet instanceof net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket effectPacket && (Object) this instanceof ServerPlayNetworkHandler playHandler) {
            if (effectPacket.getEffectId().equals(net.minecraft.entity.effect.StatusEffects.GLOWING)) {
                Entity target = playHandler.player.getWorld().getEntityById(effectPacket.getEntityId());
                if (target instanceof ServerPlayerEntity targetPlayer 
                        && targetPlayer != playHandler.player 
                        && !TeamGlowVisibility.canViewerSeeGlowing(playHandler.player, targetPlayer)) {
                    ci.cancel();
                }
            }
        }
    }
}
