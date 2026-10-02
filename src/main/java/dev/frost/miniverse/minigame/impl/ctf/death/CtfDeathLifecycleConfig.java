package dev.frost.miniverse.minigame.impl.ctf.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.DeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy;
import dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy;
import dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy;
import dev.frost.miniverse.minigame.core.death.policy.impl.SpectateForeverPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.TimedRespawnPolicy;
import dev.frost.miniverse.minigame.core.death.policy.impl.VanillaDeathPolicy;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMapConfig;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMinigame;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagSettings;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagInstance;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagState;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public final class CtfDeathLifecycleConfig implements DeathLifecycleConfig, ImmediateRespawnNotifier {
    private final CaptureTheFlagMinigame minigame;
    private final CaptureTheFlagSettings settings;
    private final SpectatorService spectatorService;
    private final CtfDeathCallbacks callbacks;
    private final CtfRespawnStrategy respawnStrategy;

    public CtfDeathLifecycleConfig(CaptureTheFlagMinigame minigame, CaptureTheFlagSettings settings,
                                  CaptureTheFlagMapConfig mapConfig, SpectatorService spectatorService,
                                  Set<UUID> permanentlyEliminated) {
        this.minigame = minigame;
        this.settings = settings;
        this.spectatorService = spectatorService;
        this.callbacks = new CtfDeathCallbacks(minigame, permanentlyEliminated);
        this.respawnStrategy = new CtfRespawnStrategy(minigame, mapConfig);
    }

    @Override
    public DeathPolicy getDeathPolicy() {
        return new VanillaDeathPolicy() {
            @Override
            public boolean interceptsRespawn() {
                return true;
            }
        };
    }

    @Override
    public DeathSpectatorPolicy getSpectatorPolicy() {
        return new CtfConditionalSpectatorPolicy(minigame, spectatorService);
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        return createPostDeathPolicy(null);
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy(DeathContext ctx) {
        String teamId = ctx != null ? ctx.victimTeamId() : null;
        CtfFlagInstance flag = teamId != null ? this.minigame.getFlagManager().getFlag(teamId) : null;
        boolean canRespawn = !this.settings.eliminationMode()
            || (flag != null && flag.state() != CtfFlagState.CAPTURED);

        return canRespawn
            ? new TimedRespawnPolicy(minigame.getDeathLifecycleManager(), settings.respawnDelaySeconds() * 20)
            : new SpectateForeverPolicy();
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return respawnStrategy;
    }

    @Override
    public GameMode resolveRespawnGameMode() {
        return GameMode.SURVIVAL;
    }

    @Override
    public @Nullable DeathLifecycleCallbacks getCallbacks() {
        return callbacks;
    }

    @Override
    public @Nullable String resolveTeamId(UUID playerId) {
        return minigame.teamManager().teamId(playerId);
    }

    @Override
    public @Nullable String resolveMatchIdentifier() {
        return minigame.getName();
    }

    @Override
    public Text getDeathTitle(ServerPlayerEntity victim, net.minecraft.entity.damage.DamageSource source) {
        return Text.literal("YOU DIED!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text getDeathSubtitle(ServerPlayerEntity victim, net.minecraft.entity.damage.DamageSource source, int ticksRemaining) {
        int seconds = (int) Math.ceil(ticksRemaining / 20.0);
        return Text.literal("Respawning in " + seconds + "s...").formatted(Formatting.YELLOW);
    }
}
