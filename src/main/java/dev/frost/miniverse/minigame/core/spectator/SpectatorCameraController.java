package dev.frost.miniverse.minigame.core.spectator;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

public final class SpectatorCameraController {
    public void ensureSpectatorMode(ServerPlayerEntity spectator) {
        if (spectator.getGameMode() != GameMode.SPECTATOR) {
            spectator.changeGameMode(GameMode.SPECTATOR);
        }
    }

    public void attach(ServerPlayerEntity spectator, Entity target) {
        this.ensureSpectatorMode(spectator);
        if (target != null) {
            spectator.setCameraEntity(target);
        }
    }

    public void detach(ServerPlayerEntity spectator) {
        spectator.setCameraEntity(spectator);
    }

    public boolean isAttachedTo(ServerPlayerEntity spectator, Entity target) {
        Entity cameraEntity = spectator.getCameraEntity();
        return cameraEntity != null && target != null && cameraEntity.getUuid().equals(target.getUuid());
    }

    @Nullable
    public Entity currentCameraTarget(ServerPlayerEntity spectator) {
        Entity cameraEntity = spectator.getCameraEntity();
        if (cameraEntity == null || cameraEntity.getUuid().equals(spectator.getUuid())) {
            return null;
        }
        return cameraEntity;
    }
}

