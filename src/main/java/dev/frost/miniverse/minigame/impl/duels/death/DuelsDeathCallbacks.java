package dev.frost.miniverse.minigame.impl.duels.death;

import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.duels.DuelMatchManager;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Hooks the Death Lifecycle Framework events into the Duels round win-condition logic.
 * The core responsibility is forwarding the "player is dead" signal to DuelMatchManager
 * so it can evaluate the round win condition.
 */
public class DuelsDeathCallbacks implements DeathLifecycleCallbacks {
    private final DuelMatchManager matchManager;

    public DuelsDeathCallbacks(DuelMatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public void onDeathProcessed(ServerPlayerEntity victim, DeathContext context) {
        // Show elimination title to the dead player
        String killerName = context.killer() != null ? context.killer().getName().getString() : null;
        Text titleText = Text.literal("ELIMINATED").formatted(Formatting.RED, Formatting.BOLD);
        Text subtitleText = killerName != null
            ? Text.literal("Killed by ").formatted(Formatting.GRAY)
                .append(Text.literal(killerName).formatted(Formatting.DARK_RED, Formatting.BOLD))
            : Text.literal("Eliminated!").formatted(Formatting.GRAY);

        victim.networkHandler.sendPacket(new TitleS2CPacket(titleText));
        victim.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleText));

        // Forward to match manager — this is what triggers the round win-condition check.
        // DuelMatchManager.handleDeath already gates on ACTIVE state, so this is safe.
        this.matchManager.handleDeath(victim);
    }
}
