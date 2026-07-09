package dev.frost.miniverse.minigame.impl.bedwars.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
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
import dev.frost.miniverse.minigame.impl.bedwars.BedTeamState;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMapConfig;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsMinigame;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsSettings;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;

public final class BedwarsDeathLifecycleConfig implements DeathLifecycleConfig, ImmediateRespawnNotifier {
    private final BedwarsMinigame minigame;
    private final Map<String, BedTeamState> bedTeamStates;
    private final BedwarsSettings settings;
    private final SpectatorService spectatorService;
    private final BedwarsDeathCallbacks callbacks;
    private final BedwarsRespawnStrategy respawnStrategy;

    public BedwarsDeathLifecycleConfig(BedwarsMinigame minigame, Map<String, BedTeamState> bedTeamStates, BedwarsSettings settings, BedwarsMapConfig mapConfig, SpectatorService spectatorService, Set<UUID> permanentlyEliminated) {
        this.minigame = minigame;
        this.bedTeamStates = bedTeamStates;
        this.settings = settings;
        this.spectatorService = spectatorService;
        this.callbacks = new BedwarsDeathCallbacks(minigame, this, bedTeamStates, permanentlyEliminated);
        this.respawnStrategy = new BedwarsRespawnStrategy(minigame, mapConfig);
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
        return new BedwarsConditionalSpectatorPolicy(bedTeamStates, spectatorService, minigame.getContext().roster());
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy() {
        return createPostDeathPolicy(null);
    }

    @Override
    public PostDeathPolicy createPostDeathPolicy(DeathContext ctx) {
        boolean bedAlive = ctx != null
            && bedTeamStates.containsKey(ctx.victimTeamId())
            && bedTeamStates.get(ctx.victimTeamId()).isBedAlive();
        return bedAlive
            ? new TimedRespawnPolicy(minigame.getDeathLifecycleManager(), (int) (settings.respawnDelaySeconds() * 20L))
            : new SpectateForeverPolicy();
    }

    @Override
    public RespawnStrategy getRespawnStrategy() {
        return respawnStrategy;
    }

    @Override
    public net.minecraft.world.GameMode resolveRespawnGameMode() {
        return net.minecraft.world.GameMode.SURVIVAL;
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
    public net.minecraft.text.Text getDeathTitle(net.minecraft.server.network.ServerPlayerEntity victim, net.minecraft.entity.damage.DamageSource source) {
        return net.minecraft.text.Text.literal("YOU DIED!").formatted(net.minecraft.util.Formatting.RED);
    }

    @Override
    public net.minecraft.text.Text getDeathSubtitle(net.minecraft.server.network.ServerPlayerEntity victim, net.minecraft.entity.damage.DamageSource source, int ticksRemaining) {
        int seconds = (int) Math.ceil(ticksRemaining / 20.0);
        return net.minecraft.text.Text.literal("Respawning in " + seconds + "s...").formatted(net.minecraft.util.Formatting.YELLOW);
    }
}
