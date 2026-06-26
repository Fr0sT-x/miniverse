package dev.frost.miniverse.minigame.core.spectator;

import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpectatorService {
    private static final SpectatorService INSTANCE = new SpectatorService();
    private static final int VALIDATION_INTERVAL_TICKS = 10;

    private final Map<UUID, SpectatorSession> sessions = new ConcurrentHashMap<>();
    private final SpectatorCameraController cameraController = new SpectatorCameraController();
    private final SpectatorEvents events = new SpectatorEvents();
    private long tickCounter;

    private SpectatorService() {
    }

    public static SpectatorService getInstance() {
        return INSTANCE;
    }

    public SpectatorEvents events() {
        return this.events;
    }

    public boolean isSpectating(UUID spectatorId) {
        return spectatorId != null && this.sessions.containsKey(spectatorId);
    }

    @Nullable
    public SpectatorSession session(UUID spectatorId) {
        return spectatorId == null ? null : this.sessions.get(spectatorId);
    }

    public SpectatorSession startSpectating(ServerPlayerEntity spectator,
                                            @Nullable SpectatorPolicy policy,
                                            @Nullable SpectatorTargetProvider targetProvider,
                                            @Nullable SpectatorMode mode,
                                            @Nullable UUID preferredTargetId,
                                            @Nullable GameMode returnMode,
                                            @Nullable Text startMessage) {
        return startSpectating(spectator, policy, targetProvider, mode, preferredTargetId, returnMode, startMessage, null);
    }

    public SpectatorSession startSpectating(ServerPlayerEntity spectator,
                                            @Nullable SpectatorPolicy policy,
                                            @Nullable SpectatorTargetProvider targetProvider,
                                            @Nullable SpectatorMode mode,
                                            @Nullable UUID preferredTargetId,
                                            @Nullable GameMode returnMode,
                                            @Nullable Text startMessage,
                                            @Nullable NoTargetPolicy noTargetPolicy) {
        if (spectator == null) {
            throw new IllegalArgumentException("spectator is required");
        }
        SpectatorPolicy resolvedPolicy = policy == null ? SpectatorPolicies.unrestricted() : policy;
        SpectatorTargetProvider resolvedProvider = targetProvider == null ? SpectatorTargetProviders.none() : targetProvider;
        SpectatorMode resolvedMode = mode == null ? SpectatorMode.STANDARD : mode;
        GameMode resolvedReturnMode = returnMode == null ? defaultReturnMode(spectator) : returnMode;
        SpectatorSession session = new SpectatorSession(
            spectator.getUuid(),
            resolvedPolicy,
            resolvedProvider,
            resolvedMode,
            resolvedReturnMode,
            this.tickCounter,
            preferredTargetId,
            noTargetPolicy
        );
        this.sessions.put(spectator.getUuid(), session);
        this.applySession(spectator, session, true);
        if (startMessage != null) {
            spectator.sendMessage(startMessage, false);
        }
        this.events.notifyStart(session);
        return session;
    }

    public boolean ensureSpectating(ServerPlayerEntity spectator) {
        SpectatorSession session = this.sessions.get(spectator.getUuid());
        if (session == null) {
            return false;
        }
        this.applySession(spectator, session, true);
        return true;
    }

    public void stopSpectating(ServerPlayerEntity spectator, SpectatorStopReason reason) {
        if (spectator == null) {
            return;
        }
        this.stopSpectating(spectator.getUuid(), reason, true, spectator.getEntityWorld().getServer());
    }

    public void clearAll() {
        this.clearAll(true);
    }

    public void clearAll(boolean restoreGameMode) {
        MinecraftServer server = resolveServer();
        for (UUID spectatorId : List.copyOf(this.sessions.keySet())) {
            this.stopSpectating(spectatorId, SpectatorStopReason.MATCH_END, restoreGameMode, server);
        }
        this.cameraController.clearPending();
        this.tickCounter = 0L;
    }

    public void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        // Process sync requests before anything else.
        this.cameraController.tickSyncRequests(server);

        if (this.sessions.isEmpty()) {
            return;
        }
        this.tickCounter++;
        MinigameContext minigameContext = MinigameManager.getInstance().getContext();
        for (SpectatorSession session : this.sessions.values()) {
            if (!shouldValidate(session)) {
                continue;
            }
            ServerPlayerEntity spectator = server.getPlayerManager().getPlayer(session.spectatorId());
            if (spectator == null || spectator.isDisconnected()) {
                continue;
            }
            SpectatorContext context = new SpectatorContext(minigameContext, server, session.spectatorId(), spectator);
            this.validateSession(context, session, false);
        }
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        SpectatorSession session = this.sessions.get(player.getUuid());
        if (session != null) {
            this.applySession(player, session, true);
        }
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        this.invalidateTarget(player.getUuid());
        this.stopSpectating(player.getUuid(), SpectatorStopReason.DISCONNECT, false, player.getServer());
    }

    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (newPlayer == null) {
            return;
        }
        SpectatorSession session = this.sessions.get(newPlayer.getUuid());
        if (session != null) {
            this.applySession(newPlayer, session, true);
        }
    }

    public void onEntityDeath(LivingEntity entity) {
        if (entity == null) {
            return;
        }
        this.invalidateTarget(entity.getUuid());
    }

    public boolean setTarget(ServerPlayerEntity spectator, UUID targetId) {
        if (spectator == null || targetId == null) {
            return false;
        }
        SpectatorSession session = this.sessions.get(spectator.getUuid());
        if (session == null) {
            return false;
        }
        SpectatorRestrictions restrictions = session.policy().restrictions();
        if (!restrictions.allowTargetSwitching()) {
            return false;
        }
        session.setTargetId(targetId);
        this.applySession(spectator, session, true);
        return true;
    }

    public boolean cycleTarget(ServerPlayerEntity spectator, boolean forward) {
        if (spectator == null) {
            return false;
        }
        SpectatorSession session = this.sessions.get(spectator.getUuid());
        if (session == null || !session.policy().restrictions().allowTargetSwitching()) {
            return false;
        }
        this.applySession(spectator, session, false);
        List<UUID> allowed = session.allowedTargetIds();
        if (allowed.isEmpty()) {
            return false;
        }
        UUID current = session.targetId();
        int index = allowed.indexOf(current);
        if (index < 0) {
            index = forward ? -1 : 0;
        }
        int nextIndex = forward ? index + 1 : index - 1;
        if (nextIndex < 0) {
            nextIndex = allowed.size() - 1;
        } else if (nextIndex >= allowed.size()) {
            nextIndex = 0;
        }
        UUID nextTargetId = allowed.get(nextIndex);
        UUID previous = session.targetId();
        session.setTargetId(nextTargetId);
        Entity target = SpectatorUtils.findEntity(spectator.getEntityWorld().getServer(), nextTargetId);
        if (target != null) {
            this.cameraController.attachImmediate(spectator, target);
        }
        if (!nextTargetId.equals(previous)) {
            this.events.notifyTargetChanged(session, previous, nextTargetId);
        }
        return true;
    }

    private void stopSpectating(UUID spectatorId, SpectatorStopReason reason, boolean restoreGameMode, @Nullable MinecraftServer server) {
        SpectatorSession session = this.sessions.remove(spectatorId);
        if (session == null) {
            return;
        }
        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(spectatorId);
            if (player != null && !player.isDisconnected()) {
                dev.frost.miniverse.minigame.core.freeze.FreezeService.getInstance().unfreeze(player, dev.frost.miniverse.minigame.core.freeze.FreezeReason.SPECTATOR_NO_TARGET);
                this.cameraController.detach(player);
                if (restoreGameMode) {
                    player.changeGameMode(session.returnMode());
                }
            }
        }
        this.events.notifyStop(session, reason);
    }

    private boolean shouldValidate(SpectatorSession session) {
        SpectatorRestrictions restrictions = session.policy().restrictions();
        if (restrictions.lockCamera()) {
            return true;
        }
        return this.tickCounter - session.lastValidatedTick() >= VALIDATION_INTERVAL_TICKS;
    }

    private void applySession(ServerPlayerEntity spectator, SpectatorSession session, boolean force) {
        MinecraftServer server = spectator.getEntityWorld().getServer();
        if (server == null) {
            return;
        }
        MinigameContext minigameContext = MinigameManager.getInstance().getContext();
        SpectatorContext context = new SpectatorContext(minigameContext, server, session.spectatorId(), spectator);
        this.validateSession(context, session, force);
    }

    private void validateSession(SpectatorContext context, SpectatorSession session, boolean force) {
        ServerPlayerEntity spectator = context.spectator();
        if (spectator == null || spectator.isDisconnected()) {
            return;
        }
        this.cameraController.ensureSpectatorMode(spectator);
        SpectatorRestrictions restrictions = session.policy().restrictions();
        List<Entity> allowedTargets = resolveAllowedTargets(context, session);
        if (allowedTargets.isEmpty() && session.noTargetPolicy() == NoTargetPolicy.SPECTATE_ANY_PLAYER) {
            allowedTargets = SpectatorTargetProviders.onlinePlayers().findTargets(context);
        }
        session.setAllowedTargetIds(allowedTargets.stream().map(Entity::getUuid).toList());

        Entity currentCamera = this.cameraController.currentCameraTarget(spectator);
        boolean cameraAllowed = isAllowedTarget(currentCamera, allowedTargets);
        if (restrictions.lockCamera() || !restrictions.allowFreecam()) {
            Entity selectedTarget = selectTarget(context, session, allowedTargets, cameraAllowed ? currentCamera : null);
            UUID previous = session.targetId();
            if (selectedTarget != null) {
                session.setTargetId(selectedTarget.getUuid());
                if (force || !this.cameraController.isAttachedTo(spectator, selectedTarget)) {
                    if (force) {
                        this.cameraController.attach(spectator, selectedTarget);
                    } else {
                        this.cameraController.attachImmediate(spectator, selectedTarget);
                    }
                    FreezeService.getInstance().unfreeze(spectator, FreezeReason.SPECTATOR_NO_TARGET);
                } else if (restrictions.lockCamera() && spectator.interactionManager.getGameMode() == GameMode.SPECTATOR) {
                    if (this.tickCounter % 40 == 0) {
                        this.cameraController.forceSyncPacket(spectator, selectedTarget);
                    }
                }
            } else {
                session.setTargetId(null);
                if (session.noTargetPolicy() == NoTargetPolicy.REQUEST_ELIMINATION) {
                    this.events.notifyNoTargetElimination(session);
                    return;
                } else if (session.noTargetPolicy() == NoTargetPolicy.STATIONARY_FREE_FLY || session.noTargetPolicy() == NoTargetPolicy.FREEZE) {
                    this.cameraController.detach(spectator);
                    FreezeService.getInstance().freeze(spectator, FreezeReason.SPECTATOR_NO_TARGET);
                    return;
                } else if (restrictions.lockCamera() || !restrictions.allowFreecam()) {
                    // Enforce freeze if freecam is disabled and no valid targets exist
                    this.cameraController.detach(spectator);
                    FreezeService.getInstance().freeze(spectator, FreezeReason.SPECTATOR_NO_TARGET);
                    return;
                }
                if (force || !restrictions.allowFreecam()) {
                    this.cameraController.detach(spectator);
                }
            }
            if (!equalsTarget(previous, session.targetId())) {
                this.events.notifyTargetChanged(session, previous, session.targetId());
            }
            session.markValidated(this.tickCounter);
            return;
        }

        if (!restrictions.allowTargetSwitching()) {
            Entity selectedTarget = selectTarget(context, session, allowedTargets, cameraAllowed ? currentCamera : null);
            UUID previous = session.targetId();
            if (selectedTarget != null) {
                session.setTargetId(selectedTarget.getUuid());
                if (force || !this.cameraController.isAttachedTo(spectator, selectedTarget)) {
                    if (force) {
                        this.cameraController.attach(spectator, selectedTarget);
                    } else {
                        this.cameraController.attachImmediate(spectator, selectedTarget);
                    }
                }
            } else {
                session.setTargetId(null);
                if (!cameraAllowed) {
                    this.cameraController.detach(spectator);
                }
            }
            if (!equalsTarget(previous, session.targetId())) {
                this.events.notifyTargetChanged(session, previous, session.targetId());
            }
            session.markValidated(this.tickCounter);
            return;
        }

        if (currentCamera != null && !cameraAllowed) {
            UUID previous = session.targetId();
            session.setTargetId(null);
            this.cameraController.detach(spectator);
            if (!equalsTarget(previous, session.targetId())) {
                this.events.notifyTargetChanged(session, previous, session.targetId());
            }
            session.markValidated(this.tickCounter);
            return;
        }

        if (currentCamera != null) {
            UUID previous = session.targetId();
            session.setTargetId(currentCamera.getUuid());
            if (!equalsTarget(previous, session.targetId())) {
                this.events.notifyTargetChanged(session, previous, session.targetId());
            }
        }
        session.markValidated(this.tickCounter);
    }

    private List<Entity> resolveAllowedTargets(SpectatorContext context, SpectatorSession session) {
        List<Entity> candidates = session.targetProvider().findTargets(context);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        SpectatorRestrictions restrictions = session.policy().restrictions();
        List<Entity> allowed = new ArrayList<>();
        for (Entity candidate : candidates) {
            if (candidate == null || candidate.isRemoved()) {
                continue;
            }
            if (candidate.getUuid().equals(context.spectatorId())) {
                continue;
            }
            if (candidate instanceof LivingEntity living && !living.isAlive()) {
                continue;
            }
            if (candidate instanceof ServerPlayerEntity player) {
                if (player.isDisconnected()) {
                    continue;
                }
                if (!restrictions.allowSpectatorTargets() && player.isSpectator()) {
                    continue;
                }
            }
            if (!restrictions.allowCrossDimension() && context.spectator() != null
                && candidate.getEntityWorld().getRegistryKey() != context.spectator().getEntityWorld().getRegistryKey()) {
                continue;
            }
            if (!session.policy().isTargetAllowed(context, candidate)) {
                continue;
            }
            allowed.add(candidate);
        }
        return allowed;
    }

    @Nullable
    private Entity selectTarget(SpectatorContext context,
                                SpectatorSession session,
                                List<Entity> allowedTargets,
                                @Nullable Entity currentCamera) {
        UUID preferred = session.targetId();
        if (preferred != null) {
            for (Entity target : allowedTargets) {
                if (preferred.equals(target.getUuid())) {
                    return target;
                }
            }
        }
        if (currentCamera != null) {
            for (Entity target : allowedTargets) {
                if (currentCamera.getUuid().equals(target.getUuid())) {
                    return target;
                }
            }
        }
        return allowedTargets.isEmpty() ? null : allowedTargets.get(0);
    }

    private void invalidateTarget(UUID targetId) {
        if (targetId == null) {
            return;
        }
        for (SpectatorSession session : this.sessions.values()) {
            if (targetId.equals(session.targetId())) {
                session.setTargetId(null);
                session.markValidated(0L);
            }
        }
    }

    private boolean equalsTarget(@Nullable UUID left, @Nullable UUID right) {
        return left != null ? left.equals(right) : right == null;
    }

    private boolean isAllowedTarget(@Nullable Entity target, List<Entity> allowedTargets) {
        if (target == null || allowedTargets == null || allowedTargets.isEmpty()) {
            return false;
        }
        UUID targetId = target.getUuid();
        for (Entity allowed : allowedTargets) {
            if (allowed != null && targetId.equals(allowed.getUuid())) {
                return true;
            }
        }
        return false;
    }

    private GameMode defaultReturnMode(ServerPlayerEntity player) {
        GameMode current = player.interactionManager.getGameMode();
        if (current == GameMode.SPECTATOR || current == GameMode.DEFAULT) {
            return GameMode.SURVIVAL;
        }
        return current;
    }

    @Nullable
    private MinecraftServer resolveServer() {
        MinigameContext context = MinigameManager.getInstance().getContext();
        if (context != null) {
            return context.nullableServer();
        }
        return null;
    }
}

