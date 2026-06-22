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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;

public class DeathLifecycleManager {

    public interface PlayerLookup {
        @Nullable ServerPlayerEntity find(UUID playerId);
    }

    private final DeathLifecycleConfig config;
    private final SpectatorService spectatorService;
    private final Map<UUID, PlayerDeathStateMachine> stateMachines = new ConcurrentHashMap<>();
    private final Map<UUID, DeathContext> contexts = new ConcurrentHashMap<>();
    private final Map<UUID, PostDeathPolicy> activePostDeathPolicies = new ConcurrentHashMap<>();

    @Nullable
    public DeathContext getContext(UUID playerId) {
        return this.contexts.get(playerId);
    }

    public DeathLifecycleManager(DeathLifecycleConfig config, SpectatorService spectatorService) {
        this.config = config;
        this.spectatorService = spectatorService;
    }

    public DeathLifecycleConfig getConfig() {
        return this.config;
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
        
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(5.0f);
        player.extinguish();
        player.clearStatusEffects();
        player.fallDistance = 0.0f;
        
        if (location != null && location.world() != null) {
            player.teleport(location.world(), location.pos().x, location.pos().y, location.pos().z, java.util.Set.of(), location.yaw(), location.pitch());
        }
        this.spectatorService.stopSpectating(player, dev.frost.miniverse.minigame.core.spectator.SpectatorStopReason.RESPAWN);

        if (callbacks != null) {
            callbacks.onRespawnComplete(player, context);
        }

        if (transitionState(player, context, DeathState.ALIVE)) {
            this.activePostDeathPolicies.remove(playerId);
            this.stateMachines.remove(playerId);
            this.contexts.remove(playerId);
        }
    }

    public void handleVanillaRespawn(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PlayerDeathStateMachine stateMachine = this.stateMachines.get(playerId);
        if (stateMachine != null && stateMachine.getCurrentState() == DeathState.DEATH_PROCESSING) {
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

    public JsonObject saveRuntimeState() {
        JsonObject root = new JsonObject();
        
        JsonArray stateMachinesArray = new JsonArray();
        for (Map.Entry<UUID, PlayerDeathStateMachine> entry : this.stateMachines.entrySet()) {
            JsonObject smObj = new JsonObject();
            smObj.addProperty("playerId", entry.getKey().toString());
            smObj.addProperty("state", entry.getValue().getCurrentState().name());
            stateMachinesArray.add(smObj);
        }
        root.add("stateMachines", stateMachinesArray);

        JsonArray contextsArray = new JsonArray();
        for (Map.Entry<UUID, DeathContext> entry : this.contexts.entrySet()) {
            DeathContext ctx = entry.getValue();
            JsonObject ctxObj = new JsonObject();
            ctxObj.addProperty("victimId", ctx.victimId().toString());
            ctxObj.addProperty("victimName", ctx.victimName());
            if (ctx.killer() != null) {
                ctxObj.addProperty("killerId", ctx.killer().getUuidAsString());
            }
            // DamageSource is too complex to fully serialize correctly in a general way, 
            // so we skip it or recreate a generic one on load.
            ctxObj.addProperty("dimension", ctx.dimension().getValue().toString());
            ctxObj.addProperty("locX", ctx.location().x);
            ctxObj.addProperty("locY", ctx.location().y);
            ctxObj.addProperty("locZ", ctx.location().z);
            ctxObj.addProperty("yawAtDeath", ctx.yawAtDeath());
            ctxObj.addProperty("pitchAtDeath", ctx.pitchAtDeath());
            ctxObj.addProperty("timestamp", ctx.timestamp());
            if (ctx.victimTeamId() != null) ctxObj.addProperty("victimTeamId", ctx.victimTeamId());
            if (ctx.matchIdentifier() != null) ctxObj.addProperty("matchIdentifier", ctx.matchIdentifier());
            if (ctx.spectatorTargetAtDeath() != null) ctxObj.addProperty("spectatorTargetAtDeath", ctx.spectatorTargetAtDeath().toString());
            contextsArray.add(ctxObj);
        }
        root.add("contexts", contextsArray);

        JsonArray policiesArray = new JsonArray();
        for (Map.Entry<UUID, PostDeathPolicy> entry : this.activePostDeathPolicies.entrySet()) {
            JsonObject polObj = new JsonObject();
            polObj.addProperty("playerId", entry.getKey().toString());
            polObj.add("state", entry.getValue().saveRuntimeState());
            policiesArray.add(polObj);
        }
        root.add("activePolicies", policiesArray);

        return root;
    }

    public void loadRuntimeState(JsonObject root, net.minecraft.server.MinecraftServer server) {
        if (root == null) return;

        this.stateMachines.clear();
        this.contexts.clear();
        this.activePostDeathPolicies.clear();

        if (root.has("stateMachines")) {
            for (JsonElement el : root.getAsJsonArray("stateMachines")) {
                JsonObject smObj = el.getAsJsonObject();
                UUID playerId = UUID.fromString(smObj.get("playerId").getAsString());
                DeathState state = DeathState.valueOf(smObj.get("state").getAsString());
                PlayerDeathStateMachine sm = new PlayerDeathStateMachine();
                // Fast forward state
                if (state == DeathState.SPECTATING) {
                    sm.transitionTo(DeathState.DEATH_PROCESSING);
                    sm.transitionTo(DeathState.SPECTATING);
                } else if (state == DeathState.RESPAWNING) {
                    sm.transitionTo(DeathState.DEATH_PROCESSING);
                    sm.transitionTo(DeathState.SPECTATING);
                    sm.transitionTo(DeathState.RESPAWNING);
                }
                this.stateMachines.put(playerId, sm);
            }
        }

        if (root.has("contexts")) {
            for (JsonElement el : root.getAsJsonArray("contexts")) {
                JsonObject ctxObj = el.getAsJsonObject();
                UUID victimId = UUID.fromString(ctxObj.get("victimId").getAsString());
                String victimName = ctxObj.get("victimName").getAsString();
                Entity killer = null;
                if (ctxObj.has("killerId")) {
                    killer = server.getOverworld().getEntity(UUID.fromString(ctxObj.get("killerId").getAsString()));
                }
                RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, Identifier.tryParse(ctxObj.get("dimension").getAsString()));
                Vec3d loc = new Vec3d(ctxObj.get("locX").getAsDouble(), ctxObj.get("locY").getAsDouble(), ctxObj.get("locZ").getAsDouble());
                float yaw = ctxObj.get("yawAtDeath").getAsFloat();
                float pitch = ctxObj.get("pitchAtDeath").getAsFloat();
                long timestamp = ctxObj.get("timestamp").getAsLong();
                String teamId = ctxObj.has("victimTeamId") ? ctxObj.get("victimTeamId").getAsString() : null;
                String matchId = ctxObj.has("matchIdentifier") ? ctxObj.get("matchIdentifier").getAsString() : null;
                UUID specTarget = ctxObj.has("spectatorTargetAtDeath") ? UUID.fromString(ctxObj.get("spectatorTargetAtDeath").getAsString()) : null;

                // Create a generic damage source since we can't perfectly serialize the original
                net.minecraft.entity.damage.DamageSource source = server.getOverworld().getDamageSources().generic();

                DeathContext ctx = new DeathContext(victimId, victimName, killer, source, dimension, loc, yaw, pitch, timestamp, teamId, matchId, specTarget);
                this.contexts.put(victimId, ctx);
            }
        }

        if (root.has("activePolicies")) {
            for (JsonElement el : root.getAsJsonArray("activePolicies")) {
                JsonObject polObj = el.getAsJsonObject();
                UUID playerId = UUID.fromString(polObj.get("playerId").getAsString());
                PostDeathPolicy policy = this.config.createPostDeathPolicy();
                if (polObj.has("state")) {
                    policy.loadRuntimeState(polObj.getAsJsonObject("state"));
                }
                this.activePostDeathPolicies.put(playerId, policy);
            }
        }
    }
}
