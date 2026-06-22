package dev.frost.miniverse.minigame.impl.deathshuffle.death;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.TimedRespawnPolicy;
import dev.frost.miniverse.minigame.impl.deathshuffle.DeathShuffleMinigame;
import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class DeathShuffleDeathLifecycleConfig implements DeathLifecycleConfig, ImmediateRespawnNotifier {
    private final DeathShuffleMinigame minigame;

    public DeathShuffleDeathLifecycleConfig(DeathShuffleMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new DeathShuffleDeathPolicy();
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        return new FreeFlySpectatorPolicy(MinigameManager.getInstance().getSpectatorService());
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        int delayTicks = this.minigame.getSettings() != null ? this.minigame.getSettings().respawnDelaySeconds() * 20 : 100;
        return new TimedRespawnPolicy(this.minigame.getDeathLifecycleManager(), delayTicks);
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return new DeathShuffleRespawnStrategy(this.minigame);
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return new DeathShuffleDeathCallbacks(this.minigame);
    }

    @Override
    @Nullable
    public String resolveTeamId(UUID playerId) {
        return null;
    }

    @Override
    public Text getDeathTitle(ServerPlayerEntity victim, DamageSource source) {
        return Text.literal("You died!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text getDeathSubtitle(ServerPlayerEntity victim, DamageSource source, int ticksRemaining) {
        int seconds = (int) Math.ceil(ticksRemaining / 20.0);
        return Text.literal("Respawning in " + seconds + "s...").formatted(Formatting.GRAY);
    }

    @Override
    @Nullable
    public String resolveMatchIdentifier() {
        return null;
    }
}
