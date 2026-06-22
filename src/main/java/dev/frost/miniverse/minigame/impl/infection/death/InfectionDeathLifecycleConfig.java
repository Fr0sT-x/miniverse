package dev.frost.miniverse.minigame.impl.infection.death;

import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.FreeFlySpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.TimedRespawnPolicy;
import dev.frost.miniverse.minigame.impl.infection.InfectionMinigame;
import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public class InfectionDeathLifecycleConfig implements DeathLifecycleConfig, ImmediateRespawnNotifier {
    private final InfectionMinigame minigame;

    public InfectionDeathLifecycleConfig(InfectionMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new InfectionDeathPolicy(this.minigame);
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        return new FreeFlySpectatorPolicy(MinigameManager.getInstance().getSpectatorService());
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        return new TimedRespawnPolicy(this.minigame.getDeathLifecycleManager(), 60);
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return new InfectionRespawnStrategy(this.minigame);
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return new InfectionDeathCallbacks(this.minigame);
    }

    @Override
    @Nullable
    public String resolveTeamId(UUID playerId) {
        return null;
    }

    @Override
    public Text getDeathTitle(ServerPlayerEntity victim, DamageSource source) {
        if (this.minigame.isSurvivor(victim)) {
            return Text.literal("INFECTED!").formatted(Formatting.RED, Formatting.BOLD);
        }
        return Text.literal("You died!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text getDeathSubtitle(ServerPlayerEntity victim, DamageSource source, int ticksRemaining) {
        int seconds = (int) Math.ceil(ticksRemaining / 20.0);
        if (this.minigame.isSurvivor(victim)) {
            return Text.literal("Respawning as infected in " + seconds + "s...");
        }
        return Text.literal("Respawning in " + seconds + "s...");
    }

    @Override
    @Nullable
    public String resolveMatchIdentifier() {
        return null;
    }
}
