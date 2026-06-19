package dev.frost.miniverse.minigame.impl.murdermystery.death;

import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.death.DeathContext;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks;
import dev.frost.miniverse.minigame.impl.murdermystery.MurderMysteryMinigame;
import dev.frost.miniverse.minigame.impl.murdermystery.role.DetectiveRole;
import dev.frost.miniverse.minigame.impl.murdermystery.role.SpectatorRole;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collections;

public class MurderMysteryDeathCallbacks implements DeathLifecycleCallbacks {
    private final MurderMysteryMinigame minigame;

    public MurderMysteryDeathCallbacks(MurderMysteryMinigame minigame) {
        this.minigame = minigame;
    }

    @Override
    public void onDeathProcessed(ServerPlayerEntity victim, DeathContext context) {
        // --- Kill title shown to the victim ---
        String killerName = context.killer() != null ? context.killer().getName().getString() : null;
        Text titleText = Text.literal("YOU WERE ELIMINATED").formatted(Formatting.RED, Formatting.BOLD);
        Text subtitleText = killerName != null
            ? Text.literal("Killed by ").formatted(Formatting.GRAY)
                .append(Text.literal(killerName).formatted(Formatting.DARK_RED, Formatting.BOLD))
            : Text.literal("Eliminated!").formatted(Formatting.GRAY);

        victim.networkHandler.sendPacket(new TitleS2CPacket(titleText));
        victim.networkHandler.sendPacket(new SubtitleS2CPacket(subtitleText));

        // --- Announce elimination in chat to all participants ---
        Text announcement = killerName != null
            ? Text.literal("☠ ").formatted(Formatting.RED)
                .append(Text.literal(context.victimName()).formatted(Formatting.WHITE))
                .append(Text.literal(" was eliminated by ").formatted(Formatting.GRAY))
                .append(Text.literal(killerName).formatted(Formatting.DARK_RED))
                .append(Text.literal("!").formatted(Formatting.GRAY))
            : Text.literal("☠ ").formatted(Formatting.RED)
                .append(Text.literal(context.victimName()).formatted(Formatting.WHITE))
                .append(Text.literal(" was eliminated!").formatted(Formatting.GRAY));
        GameMessenger.broadcast(minigame.getContext().liveParticipants(), announcement);

        // --- Detective weapon drop ---
        if (minigame.getRoleManager().hasRole(victim, DetectiveRole.class)) {
            minigame.getWeaponManager().dropDetectiveWeapon(victim.getServerWorld(), victim.getPos());
            GameMessenger.broadcast(minigame.getContext().liveParticipants(), Text.literal("Detective eliminated! The Detective's Bow has dropped.").formatted(Formatting.RED));
        }

        minigame.getCorpseManager().spawnCorpse(victim);
        minigame.getRoleManager().assignRole(victim, new SpectatorRole());
        minigame.getVisibilityManager().sync(minigame.getContext().nullableServer());
        minigame.updateScoreboardTick();

        minigame.checkWinConditions();
    }
}
