package dev.frost.miniverse.minigame.impl.bountyhunt.death;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.TimedRespawnPolicy;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntMinigame;
import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class BountyHuntDeathLifecycleConfig implements DeathLifecycleConfig, ImmediateRespawnNotifier {
    private final BountyHuntMinigame minigame;

    public BountyHuntDeathLifecycleConfig(BountyHuntMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new BountyHuntDeathPolicy();
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
        return new BountyHuntRespawnStrategy(this.minigame);
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return new BountyHuntDeathCallbacks(this.minigame);
    }

    @Override
    @Nullable
    public String resolveTeamId(UUID playerId) {
        return null; // Not using explicit team resolution for respawn logic right now
    }

    @Override
    public Text getDeathTitle(ServerPlayerEntity victim, DamageSource source) {
        return this.minigame.getDeathTitle(victim, source);
    }

    @Override
    public Text getDeathSubtitle(ServerPlayerEntity victim, DamageSource source, int ticksRemaining) {
        return this.minigame.getDeathSubtitle(victim, source, ticksRemaining);
    }

    @Override
    @Nullable
    public String resolveMatchIdentifier() {
        return null;
    }
}
