package dev.frost.miniverse.minigame.core.lifecycle;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.MinigameSessionStore;
import dev.frost.miniverse.session.SessionPermissions;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.minecraft.server.command.CommandManager.literal;

public final class MatchLifecycleCommands {
    private MatchLifecycleCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("miniverse_cancel_return")
            .executes(context -> {
                ServerPlayerEntity player = context.getSource().getPlayer();
                if (!SessionPermissions.checkCanManageSessions(player, "cancel match return teleport")) {
                    return 0;
                }
                boolean cancelled = MatchLifecycleController.getInstance().cancelReturn(player);
                if (!cancelled) {
                    context.getSource().sendFeedback(() -> Text.literal("No active return countdown.").formatted(Formatting.YELLOW), false);
                    return 0;
                }
                return 1;
            }));

        dispatcher.register(literal("miniverse_game")
            .then(literal("pause")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (!SessionPermissions.checkCanManageSessions(player, "pause the active match")) {
                        return 0;
                    }
                    boolean paused = MinigameManager.getInstance().pauseActiveGame();
                    if (!paused) {
                        context.getSource().sendFeedback(() -> Text.literal("No active match can be paused.").formatted(Formatting.YELLOW), false);
                        return 0;
                    }
                    SessionRuntimeConfig.getSessionId().ifPresent(SessionRegistry::markPauseRequested);
                    context.getSource().sendFeedback(() -> Text.literal("Paused active match and saved runtime state.").formatted(Formatting.YELLOW), true);
                    return 1;
                }))
            .then(literal("resume")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (!SessionPermissions.checkCanManageSessions(player, "resume the active match")) {
                        return 0;
                    }
                    boolean resumed = MinigameManager.getInstance().resumeActiveGame();
                    if (!resumed) {
                        context.getSource().sendFeedback(() -> Text.literal("No paused match can be resumed.").formatted(Formatting.YELLOW), false);
                        return 0;
                    }
                    SessionRuntimeConfig.getSessionId().ifPresent(SessionRegistry::clearPauseRequested);
                    context.getSource().sendFeedback(() -> Text.literal("Resumed active match.").formatted(Formatting.GREEN), true);
                    return 1;
                }))
            .then(literal("save")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (!SessionPermissions.checkCanManageSessions(player, "save the active match")) {
                        return 0;
                    }
                    boolean saved = MinigameSessionStore.saveActiveRuntime();
                    if (!saved) {
                        context.getSource().sendFeedback(() -> Text.literal("Active match does not support runtime persistence.").formatted(Formatting.YELLOW), false);
                        return 0;
                    }
                    context.getSource().sendFeedback(() -> Text.literal("Saved match state to " + MinigameSessionStore.savePath() + ".").formatted(Formatting.GREEN), false);
                    return 1;
                }))
            .then(literal("load")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (!SessionPermissions.checkCanManageSessions(player, "load the active match")) {
                        return 0;
                    }
                    MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
                    boolean loaded = MinigameSessionStore.loadInto(runtime);
                    if (!loaded) {
                        context.getSource().sendFeedback(() -> Text.literal("No compatible saved match state was found.").formatted(Formatting.YELLOW), false);
                        return 0;
                    }
                    context.getSource().sendFeedback(() -> Text.literal("Loaded match state from " + MinigameSessionStore.savePath() + ".").formatted(Formatting.GREEN), true);
                    return 1;
                })));
    }
}
