package dev.frost.miniverse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntMinigame;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntSettings;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;


import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BountyHuntCommands {
    private BountyHuntCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("bountyhunt")
                .then(literal("add")
                    .then(argument("player", EntityArgumentType.player())
                        .executes(BountyHuntCommands::addPlayer)))
                .then(literal("join")
                    .executes(BountyHuntCommands::joinSelf))
                .then(literal("start").executes(BountyHuntCommands::start))
                .then(literal("stop").executes(BountyHuntCommands::stop))
                .then(literal("grace")
                    .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                        .executes(BountyHuntCommands::setGrace)))
                .then(literal("scoreToWin")
                    .then(argument("score", IntegerArgumentType.integer(1, 99))
                        .executes(BountyHuntCommands::setScoreToWin)))
                .then(literal("targetSwap")
                    .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                        .executes(BountyHuntCommands::setTargetSwap)))
                .then(literal("tracker")
                    .then(argument("enabled", BoolArgumentType.bool())
                        .executes(BountyHuntCommands::setTrackerEnabled)))
                .then(literal("netherTracking")
                    .then(argument("enabled", BoolArgumentType.bool())
                        .executes(BountyHuntCommands::setNetherTracking)))
                .then(literal("compassCooldown")
                    .then(argument("seconds", IntegerArgumentType.integer(0, 300))
                        .executes(BountyHuntCommands::setCompassCooldown)))
                .then(literal("trackerItem")
                    .then(argument("item", StringArgumentType.word())
                        .executes(BountyHuntCommands::setTrackerItem)))
                .then(literal("highValueTarget")
                    .then(argument("enabled", BoolArgumentType.bool())
                        .executes(BountyHuntCommands::setHighValueTarget)))
                .then(literal("revengeAssignment")
                    .then(argument("enabled", BoolArgumentType.bool())
                        .executes(BountyHuntCommands::setRevengeAssignment)))
                .then(literal("info").executes(BountyHuntCommands::info))
        );
    }

    private static int addPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) {
            return 0;
        }

        MinigameManager.getInstance().addParticipant(target);
        context.getSource().sendFeedback(() -> Text.literal("Added " + target.getName().getString() + " to Bounty Hunt."), true);
        return 1;
    }

    private static int joinSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) {
            return 0;
        }

        MinigameManager.getInstance().addParticipant(player);
        context.getSource().sendFeedback(() -> Text.literal("You joined Bounty Hunt."), false);
        return 1;
    }

    private static int start(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getExistingBountyHunt();
        if (bountyHunt == null) {
            context.getSource().sendError(Text.literal("No pending Bounty Hunt session. Add players first."));
            return 0;
        }

        if (bountyHunt.getState() == GameState.RUNNING) {
            context.getSource().sendError(Text.literal("Bounty Hunt is already running."));
            return 0;
        }

        if (!bountyHunt.canStartMatch()) {
            context.getSource().sendError(Text.literal("Need at least two participants to start."));
            return 0;
        }

        MinigameManager.getInstance().setCurrentState(GameState.STARTING);
        bountyHunt.startGame();
        context.getSource().sendFeedback(() -> Text.literal("Bounty Hunt started."), true);
        return 1;
    }

    private static int stop(CommandContext<ServerCommandSource> context) {
        MinigameManager manager = MinigameManager.getInstance();
        Minigame active = manager.getActiveMinigame();
        if (!(active instanceof BountyHuntMinigame bountyHunt)) {
            context.getSource().sendError(Text.literal("No active Bounty Hunt session to stop."));
            return 0;
        }

        bountyHunt.stopGame();
        manager.reset();
        context.getSource().sendFeedback(() -> Text.literal("Stopped the active Bounty Hunt session."), true);
        return 1;
    }

    private static int setGrace(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        bountyHunt.applySettings(bountyHunt.getSettings().withGracePeriodSeconds(seconds));
        context.getSource().sendFeedback(() -> Text.literal("Set grace period to " + seconds + "s."), true);
        return 1;
    }

    private static int setScoreToWin(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        int score = IntegerArgumentType.getInteger(context, "score");
        bountyHunt.applySettings(bountyHunt.getSettings().withScoreToWin(score));
        context.getSource().sendFeedback(() -> Text.literal("Set score to win to " + score + "."), true);
        return 1;
    }

    private static int setTargetSwap(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        bountyHunt.applySettings(bountyHunt.getSettings().withTargetSwapIntervalSeconds(seconds));
        context.getSource().sendFeedback(() -> Text.literal("Set target swap interval to " + seconds + "s."), true);
        return 1;
    }

    private static int setTrackerEnabled(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        bountyHunt.applySettings(bountyHunt.getSettings().withTrackerEnabled(enabled));
        context.getSource().sendFeedback(() -> Text.literal("Set tracker enabled to " + enabled + "."), true);
        return 1;
    }

    private static int setNetherTracking(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        bountyHunt.applySettings(bountyHunt.getSettings().withNetherTrackingEnabled(enabled));
        context.getSource().sendFeedback(() -> Text.literal("Set nether tracking to " + enabled + "."), true);
        return 1;
    }

    private static int setCompassCooldown(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        bountyHunt.applySettings(bountyHunt.getSettings().withCompassCooldownSeconds(seconds));
        context.getSource().sendFeedback(() -> Text.literal("Set tracker cooldown to " + seconds + "s."), true);
        return 1;
    }

    private static int setTrackerItem(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        String item = StringArgumentType.getString(context, "item");
        bountyHunt.applySettings(bountyHunt.getSettings().withTrackerItemId(item));
        context.getSource().sendFeedback(() -> Text.literal("Set tracker item to " + item + "."), true);
        return 1;
    }

    private static int setHighValueTarget(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        bountyHunt.applySettings(bountyHunt.getSettings().withHighValueTargetEnabled(enabled));
        context.getSource().sendFeedback(() -> Text.literal("Set high value target mode to " + enabled + "."), true);
        return 1;
    }

    private static int setRevengeAssignment(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getOrCreatePending(context.getSource());
        if (bountyHunt == null) return 0;
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        bountyHunt.applySettings(bountyHunt.getSettings().withRevengeAssignmentEnabled(enabled));
        context.getSource().sendFeedback(() -> Text.literal("Set revenge assignments to " + enabled + "."), true);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        BountyHuntMinigame bountyHunt = getExistingBountyHunt();
        MinigameManager manager = MinigameManager.getInstance();
        if (bountyHunt == null) {
            context.getSource().sendFeedback(() -> Text.literal("No pending or active Bounty Hunt session."), false);
            return 1;
        }

        BountyHuntSettings settings = bountyHunt.getSettings();
        context.getSource().sendFeedback(() -> Text.literal("Bounty Hunt info:"), false);
        GameState state = manager.getCurrentState();
        context.getSource().sendFeedback(() -> Text.literal("- State: " + (state == null ? "NONE" : state.name())), false);
        context.getSource().sendFeedback(() -> Text.literal("- Participants: " + manager.getParticipantCount()), false);
        context.getSource().sendFeedback(() -> Text.literal("- Grace: " + settings.gracePeriodSeconds() + "s"), false);
        context.getSource().sendFeedback(() -> Text.literal("- Score to win: " + settings.scoreToWin()), false);
        context.getSource().sendFeedback(() -> Text.literal("- Target swap: " + settings.targetSwapIntervalSeconds() + "s"), false);
        context.getSource().sendFeedback(() -> Text.literal("- Tracker: " + settings.trackerEnabled() + " cooldown=" + settings.compassCooldownSeconds() + "s"), false);
        context.getSource().sendFeedback(() -> Text.literal("- Nether tracking: " + settings.netherTrackingEnabled()), false);
        context.getSource().sendFeedback(() -> Text.literal("- Tracker item: " + settings.trackerItemId()), false);
        context.getSource().sendFeedback(() -> Text.literal("- High Value Target: " + settings.highValueTargetEnabled()), false);
        context.getSource().sendFeedback(() -> Text.literal("- Revenge Assignments: " + settings.revengeAssignmentEnabled()), false);

        return 1;
    }

    private static BountyHuntMinigame getOrCreatePending(ServerCommandSource source) {
        BountyHuntMinigame minigame = PendingMinigameCommand.getOrCreate(source, BountyHuntMinigame.class, BountyHuntMinigame::new);
        if (minigame == null) {
            source.sendError(Text.literal("Could not create or find a Bounty Hunt session."));
        }
        return minigame;
    }

    private static BountyHuntMinigame getExistingBountyHunt() {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        return active instanceof BountyHuntMinigame bountyHunt ? bountyHunt : null;
    }
}


