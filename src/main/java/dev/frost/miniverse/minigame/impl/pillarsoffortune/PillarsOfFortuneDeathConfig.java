package dev.frost.miniverse.minigame.impl.pillarsoffortune;

import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.SpectateForeverPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.death.NoTargetPolicy;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.spectator.SpectatorSession;
import net.minecraft.world.GameMode;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public class PillarsOfFortuneDeathConfig implements DeathLifecycleConfig {
    private final PillarsOfFortuneMinigame minigame;

    public PillarsOfFortuneDeathConfig(PillarsOfFortuneMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new DeathPolicy() {
            @Override
            public void execute(ServerPlayerEntity player, DeathContext context) {
                minigame.onPlayerEliminated(player);
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
                        dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies.unrestricted(),
                        dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders.roster(),
                        dev.frost.miniverse.minigame.core.spectator.SpectatorMode.STANDARD,
                        null,
                        null,
                        net.minecraft.text.Text.literal("You died! Now spectating.").formatted(net.minecraft.util.Formatting.GRAY)
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
                ServerWorld world = minigame.getContext().nullableServer().getOverworld();
                return new RespawnLocation(world, new Vec3d(0, 100, 0), 0, 0);
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
