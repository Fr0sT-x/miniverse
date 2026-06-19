package dev.frost.miniverse.minigame.core.death;

import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.state.PlayerDeathStateMachine;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathLifecycleManager {

    public interface PlayerLookup {
        @Nullable ServerPlayerEntity find(UUID playerId);
    }

    private final DeathLifecycleConfig config;
    private final SpectatorService spectatorService;
    private final Map<UUID, PlayerDeathStateMachine> stateMachines = new ConcurrentHashMap<>();
    private final Map<UUID, DeathContext> contexts = new ConcurrentHashMap<>();
    private final Map<UUID, PostDeathPolicy> activePostDeathPolicies = new ConcurrentHashMap<>();

    public DeathLifecycleManager(DeathLifecycleConfig config, SpectatorService spectatorService) {
        this.config = config;
        this.spectatorService = spectatorService;
    }

    private boolean transitionState(ServerPlayerEntity player, DeathContext context, DeathState targetState) {
        UUID playerId = player.getUuid();
        PlayerDeathStateMachine stateMachine = this.stateMachines.get(playerId);
        if (stateMachine == null) return false;

        DeathState previousState = stateMachine.getCurrentState();
        if (stateMachine.transitionTo(targetState)) {
            DeathLifecycleCallbacks callbacks = this.config.getCallbacks();
            if (callbacks != null) {
                callbacks.onDeathStateChanged(player, context, previousState, targetState);
            }
            return true;
        }
        return false;
    }

    public void handleFatalDamage(ServerPlayerEntity victim, DamageSource source) {
        UUID victimId = victim.getUuid();

        this.stateMachines.computeIfAbsent(victimId, id -> new PlayerDeathStateMachine());

        SpectatorSession snapshotSession = this.spectatorService.isSpectating(victimId) ? this.spectatorService.session(victimId) : null;
        UUID spectatorTargetAtDeath = snapshotSession != null ? snapshotSession.targetId() : null;

        DeathContext context = new DeathContext(
            victimId,
            victim.getName().getString(),
            source.getAttacker(),
            source,
            victim.getWorld().getRegistryKey(),
            victim.getPos(),
            victim.getYaw(),
            victim.getPitch(),
            System.currentTimeMillis(),
            this.config.resolveTeamId(victimId),
            this.config.resolveMatchIdentifier(),
            spectatorTargetAtDeath
        );
        this.contexts.put(victimId, context);

        if (!transitionState(victim, context, DeathState.DEATH_PROCESSING)) {
            return;
        }

        DeathLifecycleCallbacks callbacks = this.config.getCallbacks();
        if (callbacks != null) {
            callbacks.onDeath(victim, context);
        }

        this.config.getDeathPolicy().execute(victim, context);

        if (callbacks != null) {
            callbacks.onDeathProcessed(victim, context);
        }

        if (this.config.getDeathPolicy().interceptsRespawn()) {
            victim.changeGameMode(GameMode.SPECTATOR);

            // Do NOT send PlayerRespawnS2CPacket here — that triggers a full client-side
            // terrain reload ("Loading terrain" screen for ~30s). A simple game mode change
            // + teleport is sufficient; the client handles SPECTATOR mode without respawning.

            SpectatorSession liveSession = this.spectatorService.isSpectating(victimId) ? this.spectatorService.session(victimId) : null;
            RespawnStrategy.RespawnLocation location = this.config.getRespawnStrategy().resolve(context, liveSession);
            if (location != null && location.world() != null) {
                victim.teleport(location.world(), location.pos().x, location.pos().y, location.pos().z, java.util.Set.of(), location.yaw(), location.pitch());
            }

            if (transitionState(victim, context, DeathState.SPECTATING)) {
                if (callbacks != null) {
                    callbacks.onSpectatorEnter(victim, context);
                }
                this.config.getSpectatorPolicy().apply(victim, context);

                PostDeathPolicy postDeathPolicy = this.config.createPostDeathPolicy();
                this.activePostDeathPolicies.put(victimId, postDeathPolicy);
                postDeathPolicy.start(victim, context);
            }
        }
    }

    public void executeRespawn(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        DeathContext context = this.contexts.get(playerId);
        if (context == null) return;

        DeathLifecycleCallbacks callbacks = this.config.getCallbacks();
        if (callbacks != null) {
            callbacks.onSpectatorExit(player, context);
        }

        if (!transitionState(player, context, DeathState.RESPAWNING)) {
            return;
        }

        if (callbacks != null) {
            callbacks.onRespawnBegin(player, context);
        }

        SpectatorSession liveSession = this.spectatorService.isSpectating(playerId) ? this.spectatorService.session(playerId) : null;
        RespawnStrategy.RespawnLocation location = this.config.getRespawnStrategy().resolve(context, liveSession);

        player.changeGameMode(this.config.resolveRespawnGameMode());
        player.teleport(location.world(), location.pos().x, location.pos().y, location.pos().z, java.util.Set.of(), location.yaw(), location.pitch());

        if (callbacks != null) {
            callbacks.onRespawnComplete(player, context);
        }

        if (transitionState(player, context, DeathState.ALIVE)) {
            this.activePostDeathPolicies.remove(playerId);
            this.stateMachines.remove(playerId);
            this.contexts.remove(playerId);
        }
    }

    public void handleDisconnect(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerDeathStateMachine stateMachine = this.stateMachines.get(playerId);
        if (stateMachine != null) {
            DeathState previousState = stateMachine.getCurrentState();
            if (stateMachine.disconnect()) {
                DeathContext context = this.contexts.get(playerId);
                DeathLifecycleCallbacks callbacks = this.config.getCallbacks();

                if (callbacks != null && context != null) {
                    callbacks.onDeathStateChanged(player, context, previousState, DeathState.DISCONNECTED);
                    callbacks.onDeathFlowCancelled(player, context, CancellationReason.DISCONNECT);
                }

                PostDeathPolicy activePolicy = this.activePostDeathPolicies.remove(playerId);
                if (activePolicy != null) {
                    activePolicy.cancel(CancellationReason.DISCONNECT);
                }

                this.stateMachines.remove(playerId);
                this.contexts.remove(playerId);
            }
        }
    }

    public void handleMatchEnding(PlayerLookup lookup) {
        for (Map.Entry<UUID, PlayerDeathStateMachine> entry : this.stateMachines.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerDeathStateMachine stateMachine = entry.getValue();

            DeathState previousState = stateMachine.getCurrentState();
            if (previousState != DeathState.ALIVE && previousState != DeathState.DISCONNECTED) {
                DeathContext context = this.contexts.get(playerId);
                ServerPlayerEntity player = lookup.find(playerId);

                if (player != null && context != null) {
                    DeathLifecycleCallbacks callbacks = this.config.getCallbacks();
                    if (callbacks != null) {
                        callbacks.onDeathFlowCancelled(player, context, CancellationReason.MATCH_ENDING);
                    }
                }

                PostDeathPolicy activePolicy = this.activePostDeathPolicies.remove(playerId);
                if (activePolicy != null) {
                    activePolicy.cancel(CancellationReason.MATCH_ENDING);
                }
            }
        }

        this.stateMachines.clear();
        this.contexts.clear();
        this.activePostDeathPolicies.clear();
    }

    public void tick(net.minecraft.server.MinecraftServer server) {
        for (PostDeathPolicy policy : this.activePostDeathPolicies.values()) {
            policy.tick(server);
        }
    }
}
