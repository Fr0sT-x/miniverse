package dev.frost.miniverse.minigame.impl.deathswap.death;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.SpectateForeverPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.TimedRespawnPolicy;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapMinigame;
import dev.frost.miniverse.minigame.impl.deathswap.RespawnMode;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DeathSwapDeathLifecycleConfig implements DeathLifecycleConfig, ImmediateRespawnNotifier {
    private final DeathSwapMinigame minigame;

    public DeathSwapDeathLifecycleConfig(DeathSwapMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new DeathSwapDeathPolicy(this.minigame);
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        return new FreeFlySpectatorPolicy(MinigameManager.getInstance().getSpectatorService());
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        if (this.minigame.getSettings() != null && this.minigame.getSettings().respawnMode() == RespawnMode.ELIMINATION) {
            return new SpectateForeverPolicy();
        }
        int delayTicks = this.minigame.getSettings() != null ? this.minigame.getSettings().respawnDelaySeconds() * 20 : 100;
        return new TimedRespawnPolicy(this.minigame.getDeathLifecycleManager(), delayTicks);
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return new DeathSwapRespawnStrategy(this.minigame);
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return new DeathSwapDeathCallbacks(this.minigame);
    }

    @Override
    @Nullable
    public String resolveTeamId(UUID playerId) {
        return null;
    }

    @Override
    public Text getDeathTitle(ServerPlayerEntity victim, DamageSource source) {
        String killerName = source != null && source.getAttacker() != null ? source.getAttacker().getName().getString() : "Unknown";
        return Text.literal("You died on " + killerName + "'s swap!").formatted(net.minecraft.util.Formatting.RED);
    }

    @Override
    public Text getDeathSubtitle(ServerPlayerEntity victim, DamageSource source, int ticksRemaining) {
        if (this.minigame.getSettings() != null && this.minigame.getSettings().respawnMode() == RespawnMode.ELIMINATION) {
            return Text.literal("Spectating").formatted(net.minecraft.util.Formatting.GRAY);
        }
        int seconds = (int) Math.ceil(ticksRemaining / 20.0);
        return Text.literal("Respawning in ").formatted(net.minecraft.util.Formatting.GRAY)
            .append(Text.literal(String.valueOf(seconds)).formatted(net.minecraft.util.Formatting.GREEN))
            .append(Text.literal(" seconds...").formatted(net.minecraft.util.Formatting.GRAY));
    }

    @Override
    @Nullable
    public String resolveMatchIdentifier() {
        return null;
    }
}
