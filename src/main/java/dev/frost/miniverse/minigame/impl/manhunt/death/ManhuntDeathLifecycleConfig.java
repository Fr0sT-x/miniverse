package dev.frost.miniverse.minigame.impl.manhunt.death;

import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame.ManhuntRole;

public class ManhuntDeathLifecycleConfig implements DeathLifecycleConfig, ImmediateRespawnNotifier {
    private final ManhuntMinigame minigame;

    public ManhuntDeathLifecycleConfig(ManhuntMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new ManhuntDeathPolicy();
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        return new ManhuntSpectatorPolicy(this.minigame);
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        return new ManhuntPostDeathPolicy(this.minigame);
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return new ManhuntRespawnStrategy(this.minigame);
    }

    @Override
    public DeathLifecycleCallbacks getCallbacks() {
        return new ManhuntDeathCallbacks(this.minigame);
    }

    @Override
    @Nullable
    public String resolveTeamId(UUID playerId) {
        ManhuntRole role = this.minigame.getPlayerRole(this.minigame.getPlayerByUuid(playerId));
        if (role == ManhuntRole.SPEEDRUNNER) return "speedrunners";
        if (role == ManhuntRole.HUNTER) return "hunters";
        return null;
    }

    @Override
    @Nullable
    public String resolveMatchIdentifier() {
        return "manhunt";
    }

    @Override
    public Text getDeathTitle(ServerPlayerEntity victim, DamageSource source) {
        return Text.literal("You Died!").formatted(Formatting.RED);
    }

    @Override
    public Text getDeathSubtitle(ServerPlayerEntity victim, DamageSource source, int ticksRemaining) {
        int seconds = (int) Math.ceil(ticksRemaining / 20.0);
        return Text.literal("Respawning in " + seconds + "s").formatted(Formatting.YELLOW);
    }
}
