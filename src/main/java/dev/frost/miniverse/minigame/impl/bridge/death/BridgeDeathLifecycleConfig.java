package dev.frost.miniverse.minigame.impl.bridge.death;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.TimedRespawnPolicy;
import dev.frost.miniverse.minigame.impl.bridge.BridgeMinigame;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BridgeDeathLifecycleConfig implements DeathLifecycleConfig, ImmediateRespawnNotifier {
    private final BridgeMinigame minigame;

    public BridgeDeathLifecycleConfig(BridgeMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new BridgeDeathPolicy();
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        return new FreeFlySpectatorPolicy(MinigameManager.getInstance().getSpectatorService());
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        int delayTicks = this.minigame.getSettings() != null ? this.minigame.getSettings().respawnDelaySeconds() * 20 : 60;
        return new TimedRespawnPolicy(this.minigame.getDeathLifecycleManager(), delayTicks);
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return new BridgeRespawnStrategy(this.minigame);
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return new BridgeDeathCallbacks(this.minigame);
    }

    @Override
    @Nullable
    public String resolveTeamId(UUID playerId) {
        return null;
    }

    @Override
    public Text getDeathTitle(ServerPlayerEntity victim, DamageSource source) {
        return Text.literal("You died!").formatted(Formatting.RED);
    }

    @Override
    public Text getDeathSubtitle(ServerPlayerEntity victim, DamageSource source, int ticksRemaining) {
        int seconds = (int) Math.ceil(ticksRemaining / 20.0);
        return Text.literal("Respawning in ").formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(seconds)).formatted(Formatting.GREEN))
            .append(Text.literal(" seconds...").formatted(Formatting.GRAY));
    }

    @Override
    @Nullable
    public String resolveMatchIdentifier() {
        return null;
    }
}
