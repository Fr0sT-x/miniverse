package dev.frost.miniverse.minigame.core.spectator;

import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectatorCameraController {
    private static class SyncRequest {
        final UUID targetId;
        int ticksActive;

        SyncRequest(UUID targetId) {
            this.targetId = targetId;
            this.ticksActive = 0;
        }
    }

    private final Map<UUID, SyncRequest> syncRequests = new ConcurrentHashMap<>();

    public void ensureSpectatorMode(ServerPlayerEntity spectator) {
        if (spectator.interactionManager.getGameMode() != GameMode.SPECTATOR) {
            spectator.changeGameMode(GameMode.SPECTATOR);
        }
    }

    public void attach(ServerPlayerEntity spectator, Entity target) {
        this.ensureSpectatorMode(spectator);
        if (target != null) {
            spectator.setCameraEntity(target);
            this.syncRequests.put(spectator.getUuid(), new SyncRequest(target.getUuid()));
        }
    }

    public void attachImmediate(ServerPlayerEntity spectator, Entity target) {
        // attachImmediate now behaves identically to attach, ensuring a robust sync is always performed
        this.attach(spectator, target);
    }

    public void detach(ServerPlayerEntity spectator) {
        this.syncRequests.remove(spectator.getUuid());
        spectator.setCameraEntity(spectator);
    }

    public void forceSyncPacket(ServerPlayerEntity spectator, Entity target) {
        if (spectator != null && target != null && !spectator.isDisconnected()) {
            if (spectator.interactionManager.getGameMode() == GameMode.SPECTATOR) {
                spectator.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket(target));
            }
        }
    }

    public void tickSyncRequests(MinecraftServer server) {
        if (this.syncRequests.isEmpty() || server == null) {
            return;
        }
        var iterator = this.syncRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SyncRequest> entry = iterator.next();
            ServerPlayerEntity spectator = server.getPlayerManager().getPlayer(entry.getKey());
            if (spectator == null || spectator.isDisconnected()) {
                iterator.remove();
                continue;
            }

            SyncRequest request = entry.getValue();
            request.ticksActive++;

            // Burst sync to aggressively handle chunk loading latencies and game mode transition races.
            // Pings the client at tick 1, 5, 10, 20, 30, 40, 60, and 80.
            if (request.ticksActive == 1 || request.ticksActive == 5 || request.ticksActive == 10 ||
                request.ticksActive == 20 || request.ticksActive == 30 || request.ticksActive == 40 ||
                request.ticksActive == 60 || request.ticksActive == 80) {
                
                Entity target = SpectatorUtils.findEntity(server, request.targetId);
                if (target != null) {
                    this.forceSyncPacket(spectator, target);
                }
            }

            if (request.ticksActive >= 80) {
                iterator.remove();
            }
        }
    }

    public boolean isAttachedTo(ServerPlayerEntity spectator, Entity target) {
        if (target == null) {
            return false;
        }
        // Check active sync requests first
        SyncRequest sync = this.syncRequests.get(spectator.getUuid());
        if (sync != null && sync.targetId.equals(target.getUuid())) {
            return true;
        }
        Entity cameraEntity = spectator.getCameraEntity();
        return cameraEntity != null && cameraEntity.getUuid().equals(target.getUuid());
    }

    @Nullable
    public Entity currentCameraTarget(ServerPlayerEntity spectator) {
        Entity cameraEntity = spectator.getCameraEntity();
        if (cameraEntity == null || cameraEntity.getUuid().equals(spectator.getUuid())) {
            return null;
        }
        return cameraEntity;
    }

    public void clearPending() {
        this.syncRequests.clear();
    }

    public boolean hasPending(UUID spectatorId) {
        return this.syncRequests.containsKey(spectatorId);
    }
}

