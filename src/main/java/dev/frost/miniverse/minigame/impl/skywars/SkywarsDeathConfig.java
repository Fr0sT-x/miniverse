package dev.frost.miniverse.minigame.impl.skywars;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.SpectateForeverPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.UUID;

public class SkywarsDeathConfig implements DeathLifecycleConfig {
    private final SkywarsMinigame minigame;

    public SkywarsDeathConfig(SkywarsMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new DeathPolicy() {
            @Override
            public void execute(ServerPlayerEntity player, DeathContext context) {
                minigame.onPlayerEliminated(player, false);
            }

            @Override
            public boolean interceptsRespawn() {
                return true;
            }
        };
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        return new DeathSpectatorPolicy() {
            @Override
            public void apply(ServerPlayerEntity player, DeathContext context) {
                SpectatorService.getInstance().startSpectating(
                    player,
                    SpectatorPolicies.unrestricted(),
                    SpectatorTargetProviders.roster(),
                    SpectatorMode.STANDARD,
                    null,
                    null,
                    Text.literal("You were eliminated! Now spectating.").formatted(Formatting.GRAY)
                );
            }

            @Override
            public boolean requiresFixedCamera() {
                return false;
            }

            @Override
            public NoTargetPolicy noTargetPolicy() {
                return NoTargetPolicy.FREE_FLY;
            }
        };
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        return new SpectateForeverPolicy();
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return new RespawnStrategy() {
            @Override
            public RespawnLocation resolve(DeathContext context, SpectatorSession spectatorSession) {
                ServerWorld world = minigame.getContext().nullableServer() != null
                    ? minigame.getContext().nullableServer().getOverworld()
                    : null;
                Vec3d pos = minigame.getSpectatorSpawnPos();
                return new RespawnLocation(world, pos, 0, 0);
            }
        };
    }

    @Override
    public GameMode resolveRespawnGameMode() {
        return GameMode.SPECTATOR;
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return new DeathLifecycleCallbacks() {
            @Override
            public void onDeathProcessed(ServerPlayerEntity player, DeathContext context) {
            }
        };
    }

    @Override
    public String resolveTeamId(UUID playerId) {
        return null;
    }

    @Override
    public String resolveMatchIdentifier() {
        return minigame.getName();
    }
}
