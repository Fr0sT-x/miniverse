 package dev.frost.miniverse.minigame.impl.resourcesprint;

import dev.frost.miniverse.minigame.core.GameMessenger;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Centralized event messaging helper for Resource Sprint that applies severity levels.
 */
public final class ResourceSprintEventMessenger {
    public enum Severity { MAJOR, MILESTONE, REGULAR }

    private final Supplier<Collection<ServerPlayerEntity>> participants;

    public ResourceSprintEventMessenger(Supplier<Collection<ServerPlayerEntity>> participants) {
        this.participants = participants;
    }

    public void send(Severity severity, Text title, Text subtitle) {
        Collection<ServerPlayerEntity> participants = this.participants.get();
        switch (severity) {
            case MAJOR -> {
                GameMessenger.showGameTitle(participants, title, subtitle);
                GameMessenger.broadcast(participants, title.copy().formatted(Formatting.GOLD));
            }
            case MILESTONE -> {
                GameMessenger.broadcast(participants, title.copy().formatted(Formatting.YELLOW));
            }
            default -> {
                GameMessenger.broadcast(participants, subtitle);
            }
        }
    }
}


