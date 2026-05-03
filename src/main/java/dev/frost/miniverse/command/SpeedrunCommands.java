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
                .then(literal("runner")
                    .then(argument("player", EntityArgumentType.player())
                        .executes(SpeedrunCommands::setRunner)))
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

        MinigameManager manager = MinigameManager.getInstance();
        manager.addParticipant(player);

        if (speedrun.getRunner() == null) {
            speedrun.setRunner(player);
            source.sendFeedback(() -> Text.literal("You joined Speedrun as the runner."), false);
        } else {
            if (speedrun.getState() == GameState.IN_PROGRESS) {
                player.changeGameMode(GameMode.SPECTATOR);
            }
            source.sendFeedback(() -> Text.literal("You joined Speedrun as a spectator."), false);
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

        MinigameManager manager = MinigameManager.getInstance();
        manager.addParticipant(target);

        if (speedrun.getRunner() == null) {
            speedrun.setRunner(target);
            source.sendFeedback(() -> Text.literal("Added " + target.getName().getString() + " as the runner."), true);
        } else {
            if (speedrun.getState() == GameState.IN_PROGRESS) {
                target.changeGameMode(GameMode.SPECTATOR);
            }
            source.sendFeedback(() -> Text.literal("Added " + target.getName().getString() + " as a spectator."), true);
        }

        return 1;
    }

    private static int setRunner(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        SpeedrunMinigame speedrun = getOrCreatePendingSpeedrun(source);
        if (speedrun == null) {
            return 0;
        }

        MinigameManager.getInstance().addParticipant(target);
        speedrun.setRunner(target);
        source.sendFeedback(() -> Text.literal(target.getName().getString() + " is now the runner."), true);
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

        if (speedrun.getState() == GameState.IN_PROGRESS) {
            source.sendError(Text.literal("Speedrun is already running."));
            return 0;
        }

        if (!speedrun.canStartRun()) {
            source.sendError(Text.literal("You need at least one participant and one runner before starting."));
            return 0;
        }

        for (ServerPlayerEntity participant : manager.getParticipants()) {
            if (speedrun.getRunner() != null && participant.getUuid().equals(speedrun.getRunner().getUuid())) {
                participant.getInventory().clear();
                participant.changeGameMode(GameMode.SURVIVAL);
                participant.getHungerManager().setFoodLevel(20);
                participant.getHungerManager().setSaturationLevel(20.0F);
            } else {
                participant.changeGameMode(GameMode.SPECTATOR);
            }
        }

        manager.setCurrentState(GameState.STARTING);
        speedrun.startGame();

        ServerPlayerEntity runner = speedrun.getRunner();
        source.sendFeedback(
            () -> Text.literal("Speedrun started for runner " + (runner == null ? "unknown" : runner.getName().getString()) + "."),
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

        ServerPlayerEntity runner = speedrun.getRunner();
        source.sendFeedback(() -> Text.literal("Speedrun info:"), false);
        source.sendFeedback(() -> Text.literal("- State: " + speedrun.getState().name()), false);
        source.sendFeedback(() -> Text.literal("- Participants: " + MinigameManager.getInstance().getParticipantCount()), false);
        source.sendFeedback(() -> Text.literal("- Runner: " + (runner == null ? "none" : runner.getName().getString())), false);
        source.sendFeedback(() -> Text.literal("- Time: " + speedrun.getFormattedTime()), false);
        return 1;
    }

    private static @Nullable SpeedrunMinigame getOrCreatePendingSpeedrun(ServerCommandSource source) {
        MinigameManager manager = MinigameManager.getInstance();
        Minigame active = manager.getActiveMinigame();

        if (active == null) {
            SpeedrunMinigame speedrun = new SpeedrunMinigame();
            manager.setActiveMinigame(speedrun);
            return speedrun;
        }

        if (active instanceof SpeedrunMinigame speedrun) {
            return speedrun;
        }

        source.sendError(Text.literal("Another minigame is currently active."));
        return null;
    }

    private static @Nullable SpeedrunMinigame getExistingSpeedrun() {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        return active instanceof SpeedrunMinigame speedrun ? speedrun : null;
    }
}



