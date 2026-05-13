package dev.frost.miniverse.command;

import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

final class PendingMinigameCommand {
    private PendingMinigameCommand() {
    }

    static <T extends Minigame> @Nullable T getOrCreate(
        ServerCommandSource source,
        Class<T> type,
        Supplier<T> factory
    ) {
        MinigameManager manager = MinigameManager.getInstance();
        Minigame active = manager.getActiveMinigame();

        if (active == null) {
            T minigame = factory.get();
            manager.setActiveMinigame(minigame);
            return minigame;
        }

        if (type.isInstance(active)) {
            return type.cast(active);
        }

        source.sendError(Text.literal("Another minigame is currently active."));
        return null;
    }
}
