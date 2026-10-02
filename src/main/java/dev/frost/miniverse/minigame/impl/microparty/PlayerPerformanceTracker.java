package dev.frost.miniverse.minigame.impl.microfrenzy;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerPerformanceTracker {
    private final int startingLives;
    private final Map<UUID, Integer> lives = new HashMap<>();
    private final Map<UUID, Integer> passes = new HashMap<>();
    private final Map<UUID, Integer> fails = new HashMap<>();
    private final Map<UUID, Integer> points = new HashMap<>();

    // Per-round transient tracking state
    private final Map<UUID, Boolean> currentRoundPassed = new HashMap<>();
    private final Map<UUID, Integer> sneakCounts = new HashMap<>();
    private final Map<UUID, Integer> jumpCounts = new HashMap<>();
    private final Map<UUID, Float> initialYaw = new HashMap<>();
    private final Map<UUID, Float> cumulativeYawDelta = new HashMap<>();
    private final Map<UUID, Vec3d> initialPosition = new HashMap<>();
    private final Map<UUID, Boolean> movedViolation = new HashMap<>();

    public PlayerPerformanceTracker(int startingLives) {
        this.startingLives = Math.max(1, startingLives);
    }

    public void initPlayer(UUID playerId) {
        lives.put(playerId, startingLives);
        passes.put(playerId, 0);
        fails.put(playerId, 0);
        points.put(playerId, 0);
        resetRoundState(playerId);
    }

    public void removePlayer(UUID playerId) {
        lives.remove(playerId);
        passes.remove(playerId);
        fails.remove(playerId);
        points.remove(playerId);
        clearTransient(playerId);
    }

    public void resetRoundState(UUID playerId) {
        currentRoundPassed.put(playerId, false);
        sneakCounts.put(playerId, 0);
        jumpCounts.put(playerId, 0);
        initialYaw.remove(playerId);
        cumulativeYawDelta.put(playerId, 0.0f);
        initialPosition.remove(playerId);
        movedViolation.put(playerId, false);
    }

    public void resetAllRoundStates() {
        for (UUID playerId : lives.keySet()) {
            resetRoundState(playerId);
        }
    }

    private void clearTransient(UUID playerId) {
        currentRoundPassed.remove(playerId);
        sneakCounts.remove(playerId);
        jumpCounts.remove(playerId);
        initialYaw.remove(playerId);
        cumulativeYawDelta.remove(playerId);
        initialPosition.remove(playerId);
        movedViolation.remove(playerId);
    }

    public int getLives(UUID playerId) {
        return lives.getOrDefault(playerId, 0);
    }

    public boolean isAlive(UUID playerId) {
        return getLives(playerId) > 0;
    }

    public int deductLife(UUID playerId) {
        int current = getLives(playerId);
        int updated = Math.max(0, current - 1);
        lives.put(playerId, updated);
        fails.put(playerId, fails.getOrDefault(playerId, 0) + 1);
        return updated;
    }

    public void recordPass(UUID playerId, int pointReward) {
        passes.put(playerId, passes.getOrDefault(playerId, 0) + 1);
        points.put(playerId, points.getOrDefault(playerId, 0) + pointReward);
        currentRoundPassed.put(playerId, true);
    }

    public boolean hasPassedCurrentRound(UUID playerId) {
        return Boolean.TRUE.equals(currentRoundPassed.get(playerId));
    }

    public void setPassedCurrentRound(UUID playerId, boolean passed) {
        currentRoundPassed.put(playerId, passed);
    }

    public int getPasses(UUID playerId) {
        return passes.getOrDefault(playerId, 0);
    }

    public int getFails(UUID playerId) {
        return fails.getOrDefault(playerId, 0);
    }

    public int getPoints(UUID playerId) {
        return points.getOrDefault(playerId, 0);
    }

    // --- Transient Movement / Action Tracking ---

    public void recordInitialPosition(ServerPlayerEntity player) {
        initialPosition.put(player.getUuid(), player.getPos());
        initialYaw.put(player.getUuid(), player.getYaw());
        cumulativeYawDelta.put(player.getUuid(), 0.0f);
        movedViolation.put(player.getUuid(), false);
    }

    public void checkMovementViolation(ServerPlayerEntity player, double threshold) {
        UUID id = player.getUuid();
        if (Boolean.TRUE.equals(movedViolation.get(id))) {
            return;
        }
        Vec3d init = initialPosition.get(id);
        if (init != null) {
            double distSq = player.getPos().squaredDistanceTo(init);
            if (distSq > threshold * threshold) {
                movedViolation.put(id, true);
            }
        }
    }

    public boolean hasMovedViolated(UUID playerId) {
        return Boolean.TRUE.equals(movedViolation.get(playerId));
    }

    public void trackYawRotation(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        Float last = initialYaw.get(id);
        float current = player.getYaw();
        if (last != null) {
            float delta = Math.abs(current - last);
            if (delta > 180.0f) {
                delta = 360.0f - delta;
            }
            float cum = cumulativeYawDelta.getOrDefault(id, 0.0f) + delta;
            cumulativeYawDelta.put(id, cum);
        }
        initialYaw.put(id, current);
    }

    public float getCumulativeYawDelta(UUID playerId) {
        return cumulativeYawDelta.getOrDefault(playerId, 0.0f);
    }

    public void incrementSneak(UUID playerId) {
        sneakCounts.put(playerId, sneakCounts.getOrDefault(playerId, 0) + 1);
    }

    public int getSneakCount(UUID playerId) {
        return sneakCounts.getOrDefault(playerId, 0);
    }

    public void incrementJump(UUID playerId) {
        jumpCounts.put(playerId, jumpCounts.getOrDefault(playerId, 0) + 1);
    }

    public int getJumpCount(UUID playerId) {
        return jumpCounts.getOrDefault(playerId, 0);
    }
}
