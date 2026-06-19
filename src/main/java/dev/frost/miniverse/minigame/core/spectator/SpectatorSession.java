package dev.frost.miniverse.minigame.core.spectator;

import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class SpectatorSession {
    private final UUID spectatorId;
    private final SpectatorPolicy policy;
    private final SpectatorTargetProvider targetProvider;
    private final SpectatorMode mode;
    private final GameMode returnMode;
    private final long startTick;
    @Nullable
    private UUID targetId;
    @Nullable
    private final NoTargetPolicy noTargetPolicy;
    private List<UUID> allowedTargetIds = List.of();
    private long lastValidatedTick;

    public SpectatorSession(UUID spectatorId,
                            SpectatorPolicy policy,
                            SpectatorTargetProvider targetProvider,
                            SpectatorMode mode,
                            GameMode returnMode,
                            long startTick,
                            @Nullable UUID targetId,
                            @Nullable NoTargetPolicy noTargetPolicy) {
        this.spectatorId = spectatorId;
        this.policy = policy;
        this.targetProvider = targetProvider;
        this.mode = mode;
        this.returnMode = returnMode;
        this.startTick = startTick;
        this.targetId = targetId;
        this.noTargetPolicy = noTargetPolicy;
    }

    public UUID spectatorId() {
        return this.spectatorId;
    }

    public SpectatorPolicy policy() {
        return this.policy;
    }

    public SpectatorTargetProvider targetProvider() {
        return this.targetProvider;
    }

    public SpectatorMode mode() {
        return this.mode;
    }

    public GameMode returnMode() {
        return this.returnMode;
    }

    public long startTick() {
        return this.startTick;
    }

    @Nullable
    public UUID targetId() {
        return this.targetId;
    }

    @Nullable
    public NoTargetPolicy noTargetPolicy() {
        return this.noTargetPolicy;
    }

    public void setTargetId(@Nullable UUID targetId) {
        this.targetId = targetId;
    }

    public List<UUID> allowedTargetIds() {
        return this.allowedTargetIds;
    }

    public void setAllowedTargetIds(List<UUID> allowedTargetIds) {
        this.allowedTargetIds = allowedTargetIds == null ? List.of() : List.copyOf(allowedTargetIds);
    }

    public long lastValidatedTick() {
        return this.lastValidatedTick;
    }

    public void markValidated(long tick) {
        this.lastValidatedTick = tick;
    }
}

