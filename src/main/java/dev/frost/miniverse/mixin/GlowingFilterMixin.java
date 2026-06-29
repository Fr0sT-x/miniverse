package dev.frost.miniverse.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
            
            if (target != null && target != playHandler.player && !playHandler.player.isTeammate(target)) {
                List<DataTracker.SerializedEntry<?>> entries = metadataPacket.trackedValues();
                if (entries != null) {
                    boolean needsClone = false;
                    for (DataTracker.SerializedEntry<?> entry : entries) {
                        if (entry.value() instanceof Byte b && (b & 0x40) != 0) { // 0x40 is GLOWING flag
                            needsClone = true;
                            break;
                        }
                    }
                    
                    if (needsClone) {
                        List<DataTracker.SerializedEntry<?>> newEntries = new ArrayList<>();
                        for (DataTracker.SerializedEntry<?> entry : entries) {
                            if (entry.value() instanceof Byte b && (b & 0x40) != 0) {
                                byte newFlags = (byte) (b & ~0x40);
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
}
