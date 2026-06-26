package dev.frost.miniverse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.speedrun.SpeedrunMinigame;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class SpeedrunCommands {
    private SpeedrunCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("speedrun")
                .then(literal("join")
                    .executes(SpeedrunCommands::joinSelf))
                .then(literal("add")
                    .then(argument("player", EntityArgumentType.player())
                        .executes(SpeedrunCommands::addByName)))
                .then(literal("start").executes(SpeedrunCommands::start))
                .then(literal("stop").executes(SpeedrunCommands::stop))
                .then(literal("info").executes(SpeedrunCommands::info))
        );
    }

    private static int joinSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();

        SpeedrunMinigame speedrun = getOrCreatePendingSpeedrun(source);
        if (speedrun == null) {
            return 0;
        }

        if (dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance().isSpectating(player.getUuid())) {
            dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance().stopSpectating(
                player, dev.frost.miniverse.minigame.core.spectator.SpectatorStopReason.MANUAL
            );
        }
        MinigameManager.getInstance().addParticipant(player);
        if (speedrun.getState() == GameState.RUNNING) {
            player.sendMessage(Text.literal("Joined Speedrun in progress."), false);
        } else {
            source.sendFeedback(() -> Text.literal("You joined Speedrun."), false);
        }

        return 1;
    }

    private static int addByName(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        SpeedrunMinigame speedrun = getOrCreatePendingSpeedrun(source);
        if (speedrun == null) {
            return 0;
        }

        if (dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance().isSpectating(target.getUuid())) {
            dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance().stopSpectating(
                target, dev.frost.miniverse.minigame.core.spectator.SpectatorStopReason.MANUAL
            );
        }
        MinigameManager.getInstance().addParticipant(target);
        if (speedrun.getState() == GameState.RUNNING) {
            target.sendMessage(Text.literal("Joined Speedrun in progress."), false);
        }
        source.sendFeedback(() -> Text.literal("Added " + target.getName().getString() + " to Speedrun."), true);

        return 1;
    }

    private static int start(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        SpeedrunMinigame speedrun = getExistingSpeedrun();
        MinigameManager manager = MinigameManager.getInstance();

        if (speedrun == null) {
            source.sendError(Text.literal("No pending Speedrun session. Use /speedrun join first."));
            return 0;
        }

        if (speedrun.getState() == GameState.RUNNING) {
            source.sendError(Text.literal("Speedrun is already running."));
            return 0;
        }

        if (!speedrun.canStartRun()) {
            source.sendError(Text.literal("You need at least one participant before starting."));
            return 0;
        }

        manager.setCurrentState(GameState.STARTING);
        speedrun.startGame();

        source.sendFeedback(
            () -> Text.literal("Speedrun started for " + manager.getParticipantCount() + " participants."),
            true
        );
        return 1;
    }

    private static int stop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinigameManager manager = MinigameManager.getInstance();

        if (!(manager.getActiveMinigame() instanceof SpeedrunMinigame speedrun)) {
            source.sendError(Text.literal("No active Speedrun session to stop."));
            return 0;
        }

        speedrun.stopGame();
        manager.reset();
        source.sendFeedback(() -> Text.literal("Stopped the active Speedrun session."), true);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        SpeedrunMinigame speedrun = getExistingSpeedrun();

        if (speedrun == null) {
            source.sendFeedback(() -> Text.literal("No pending or active Speedrun session."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("Speedrun info:"), false);
        source.sendFeedback(() -> Text.literal("- State: " + speedrun.getState().name()), false);
        source.sendFeedback(() -> Text.literal("- Participants: " + MinigameManager.getInstance().getParticipantCount()), false);
        source.sendFeedback(() -> Text.literal("- Time: " + (speedrun.getState() == GameState.RUNNING ? "In Progress" : "Not Started")), false);
        return 1;
    }

    private static @Nullable SpeedrunMinigame getOrCreatePendingSpeedrun(ServerCommandSource source) {
        return PendingMinigameCommand.getOrCreate(source, SpeedrunMinigame.class, SpeedrunMinigame::new);
    }

    private static @Nullable SpeedrunMinigame getExistingSpeedrun() {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        return active instanceof SpeedrunMinigame speedrun ? speedrun : null;
    }
}
