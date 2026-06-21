package dev.frost.miniverse.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntMinigame.ManhuntRole;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntSettings;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class ManhuntCommands {
    private ManhuntCommands() {
    }

    private static final List<String> ROLE_NAMES = List.of("speedrunner", "hunter");
    private static final SuggestionProvider<ServerCommandSource> ROLE_SUGGESTIONS = (context, builder) ->
        CommandSource.suggestMatching(ROLE_NAMES, builder);

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("manhunt")
                .then(literal("add")
                    .then(argument("role", StringArgumentType.word())
                        .suggests(ROLE_SUGGESTIONS)
                        .then(argument("player", EntityArgumentType.player())
                            .executes(ManhuntCommands::addByName))))
                .then(literal("join")
                    .then(argument("role", StringArgumentType.word())
                        .suggests(ROLE_SUGGESTIONS)
                        .executes(ManhuntCommands::joinSelf)))
                .then(literal("start").executes(ManhuntCommands::start))
                .then(literal("stop").executes(ManhuntCommands::stop))
                .then(literal("respawnDelay")
                    .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                        .executes(ManhuntCommands::setRespawnDelay)))
                .then(literal("releaseDelay")
                    .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                        .executes(ManhuntCommands::setReleaseDelay)))
                .then(literal("compass")
                    .then(argument("enabled", BoolArgumentType.bool())
                        .executes(ManhuntCommands::setCompassEnabled)))
                .then(literal("netherTracking")
                    .then(argument("enabled", BoolArgumentType.bool())
                        .executes(ManhuntCommands::setNetherTracking)))
                .then(literal("compassCooldown")
                    .then(argument("seconds", IntegerArgumentType.integer(0, 300))
                        .executes(ManhuntCommands::setCompassCooldown)))
                .then(literal("runnerGlowPulse")
                    .then(argument("minutes", IntegerArgumentType.integer(0, 120))
                        .executes(ManhuntCommands::setRunnerGlowPulse)))
                .then(literal("runnerLives")
                    .then(argument("lives", IntegerArgumentType.integer(-1, 100))
                        .executes(ManhuntCommands::setRunnerLives)))
                .then(literal("hunterLives")
                    .then(argument("lives", IntegerArgumentType.integer(-1, 100))
                        .executes(ManhuntCommands::setHunterLives)))
                .then(literal("hunterRespawnDelay")
                    .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                        .executes(ManhuntCommands::setHunterRespawnDelay)))
                .then(literal("_latejoin_tp")
                    .then(argument("target", StringArgumentType.word())
                        .executes(ManhuntCommands::executeLateJoinTp)))
                .then(literal("info").executes(ManhuntCommands::info))
        );
    }

    private static int addByName(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        String roleName = StringArgumentType.getString(context, "role");
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");

        ManhuntRole role = parseRole(roleName);
        if (role == null) {
            source.sendError(Text.literal("Unknown role '" + roleName + "'. Use speedrunner or hunter."));
            return 0;
        }

        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        MinigameManager manager = MinigameManager.getInstance();
        manager.addParticipant(target);
        manhunt.setPlayerRole(target, role);

        source.sendFeedback(
            () -> Text.literal("Added " + target.getName().getString() + " as " + role.getDisplayName() + "."),
            true
        );
        return 1;
    }

    private static int joinSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        String roleName = StringArgumentType.getString(context, "role");

        ManhuntRole role = parseRole(roleName);
        if (role == null) {
            source.sendError(Text.literal("Unknown role '" + roleName + "'. Use speedrunner or hunter."));
            return 0;
        }

        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        MinigameManager manager = MinigameManager.getInstance();
        manager.addParticipant(player);
        manhunt.setPlayerRole(player, role);

        source.sendFeedback(() -> Text.literal("You joined Manhunt as " + role.getDisplayName() + "."), false);
        return 1;
    }

    private static int start(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getExistingManhunt();
        MinigameManager manager = MinigameManager.getInstance();

        if (manhunt == null) {
            source.sendError(Text.literal("No pending Manhunt session. Use /manhunt add <role> <player> first."));
            return 0;
        }

        if (manhunt.getState() == GameState.RUNNING) {
            source.sendError(Text.literal("Manhunt is already running."));
            return 0;
        }

        if (!manhunt.canStartMatch()) {
            List<ServerPlayerEntity> unassigned = manhunt.getUnassignedParticipants();
            if (!unassigned.isEmpty()) {
                source.sendError(Text.literal("Every participant must have a role before starting: " + formatPlayers(unassigned)));
            } else if (manhunt.getSpeedrunners().isEmpty() || manhunt.getHunters().isEmpty()) {
                source.sendError(Text.literal("You need at least one speedrunner and one hunter before starting."));
            } else {
                source.sendError(Text.literal("Manhunt is not ready to start."));
            }
            return 0;
        }

        for (ServerPlayerEntity participant : manager.getParticipants()) {
            participant.getInventory().clear();
            participant.changeGameMode(GameMode.SURVIVAL);
            participant.getHungerManager().setFoodLevel(20);
            participant.getHungerManager().setSaturationLevel(20.0F);
        }

        manager.setCurrentState(GameState.STARTING);
        manhunt.startGame();

        source.sendFeedback(
            () -> Text.literal("Manhunt started with " + manhunt.getSpeedrunners().size() + " speedrunner(s) and " + manhunt.getHunters().size() + " hunter(s)."),
            true
        );
        return 1;
    }

    private static int setRespawnDelay(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        manhunt.setSpeedrunnerRespawnDelaySeconds(seconds);
        source.sendFeedback(() -> Text.literal("Set Manhunt speedrunner respawn delay to " + seconds + " second(s)."), true);
        return 1;
    }

    private static int setReleaseDelay(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        ManhuntSettings current = manhunt.getSettings();
        manhunt.applySettings(new ManhuntSettings(
            seconds,
            current.speedrunnerRespawnDelaySeconds(),
            current.huntersCompassEnabled(),
            current.netherTrackingEnabled(),
            current.compassCooldownSeconds(),
            current.runnerGlowPulseMinutes(),
            current.runnerLives(),
            current.hunterLives(),
            current.hunterRespawnDelaySeconds(),
            current.midGameJoinTeleportEnabled(),
            current.disconnectGraceSeconds()
        ));
        source.sendFeedback(() -> Text.literal("Set Manhunt hunter release delay to " + seconds + " second(s)."), true);
        return 1;
    }

    private static int setCompassEnabled(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        ManhuntSettings current = manhunt.getSettings();
        manhunt.applySettings(new ManhuntSettings(
            current.hunterReleaseDelaySeconds(),
            current.speedrunnerRespawnDelaySeconds(),
            enabled,
            current.netherTrackingEnabled(),
            current.compassCooldownSeconds(),
            current.runnerGlowPulseMinutes(),
            current.runnerLives(),
            current.hunterLives(),
            current.hunterRespawnDelaySeconds(),
            current.midGameJoinTeleportEnabled(),
            current.disconnectGraceSeconds()
        ));
        source.sendFeedback(() -> Text.literal("Set Manhunt hunter compass to " + enabled + "."), true);
        return 1;
    }

    private static int setNetherTracking(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        ManhuntSettings current = manhunt.getSettings();
        manhunt.applySettings(new ManhuntSettings(
            current.hunterReleaseDelaySeconds(),
            current.speedrunnerRespawnDelaySeconds(),
            current.huntersCompassEnabled(),
            enabled,
            current.compassCooldownSeconds(),
            current.runnerGlowPulseMinutes(),
            current.runnerLives(),
            current.hunterLives(),
            current.hunterRespawnDelaySeconds(),
            current.midGameJoinTeleportEnabled(),
            current.disconnectGraceSeconds()
        ));
        source.sendFeedback(() -> Text.literal("Set Manhunt Nether tracking to " + enabled + "."), true);
        return 1;
    }

    private static int setCompassCooldown(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        ManhuntSettings current = manhunt.getSettings();
        manhunt.applySettings(new ManhuntSettings(
            current.hunterReleaseDelaySeconds(),
            current.speedrunnerRespawnDelaySeconds(),
            current.huntersCompassEnabled(),
            current.netherTrackingEnabled(),
            seconds,
            current.runnerGlowPulseMinutes(),
            current.runnerLives(),
            current.hunterLives(),
            current.hunterRespawnDelaySeconds(),
            current.midGameJoinTeleportEnabled(),
            current.disconnectGraceSeconds()
        ));
        source.sendFeedback(() -> Text.literal("Set Manhunt compass cooldown to " + seconds + " second(s)."), true);
        return 1;
    }

    private static int setRunnerGlowPulse(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        int minutes = IntegerArgumentType.getInteger(context, "minutes");
        ManhuntSettings current = manhunt.getSettings();
        manhunt.applySettings(new ManhuntSettings(
            current.hunterReleaseDelaySeconds(),
            current.speedrunnerRespawnDelaySeconds(),
            current.huntersCompassEnabled(),
            current.netherTrackingEnabled(),
            current.compassCooldownSeconds(),
            minutes,
            current.runnerLives(),
            current.hunterLives(),
            current.hunterRespawnDelaySeconds(),
            current.midGameJoinTeleportEnabled(),
            current.disconnectGraceSeconds()
        ));
        source.sendFeedback(() -> Text.literal("Set Manhunt runner glow pulse to every " + minutes + " minute(s). 0 disables it."), true);
        return 1;
    }

    private static int setRunnerLives(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        int lives = IntegerArgumentType.getInteger(context, "lives");
        ManhuntSettings current = manhunt.getSettings();
        manhunt.applySettings(new ManhuntSettings(
            current.hunterReleaseDelaySeconds(),
            current.speedrunnerRespawnDelaySeconds(),
            current.huntersCompassEnabled(),
            current.netherTrackingEnabled(),
            current.compassCooldownSeconds(),
            current.runnerGlowPulseMinutes(),
            lives,
            current.hunterLives(),
            current.hunterRespawnDelaySeconds(),
            current.midGameJoinTeleportEnabled(),
            current.disconnectGraceSeconds()
        ));
        source.sendFeedback(() -> Text.literal("Set Manhunt runner lives to " + formatLives(lives) + "."), true);
        return 1;
    }

    private static int setHunterLives(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        int lives = IntegerArgumentType.getInteger(context, "lives");
        ManhuntSettings current = manhunt.getSettings();
        manhunt.applySettings(new ManhuntSettings(
            current.hunterReleaseDelaySeconds(),
            current.speedrunnerRespawnDelaySeconds(),
            current.huntersCompassEnabled(),
            current.netherTrackingEnabled(),
            current.compassCooldownSeconds(),
            current.runnerGlowPulseMinutes(),
            current.runnerLives(),
            lives,
            current.hunterRespawnDelaySeconds(),
            current.midGameJoinTeleportEnabled(),
            current.disconnectGraceSeconds()
        ));
        source.sendFeedback(() -> Text.literal("Set Manhunt hunter lives to " + formatLives(lives) + "."), true);
        return 1;
    }

    private static int setHunterRespawnDelay(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getOrCreatePendingManhunt(source);
        if (manhunt == null) {
            return 0;
        }

        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        ManhuntSettings current = manhunt.getSettings();
        manhunt.applySettings(new ManhuntSettings(
            current.hunterReleaseDelaySeconds(),
            current.speedrunnerRespawnDelaySeconds(),
            current.huntersCompassEnabled(),
            current.netherTrackingEnabled(),
            current.compassCooldownSeconds(),
            current.runnerGlowPulseMinutes(),
            current.runnerLives(),
            current.hunterLives(),
            seconds,
            current.midGameJoinTeleportEnabled(),
            current.disconnectGraceSeconds()
        ));
        source.sendFeedback(() -> Text.literal("Set Manhunt hunter respawn delay to " + seconds + " second(s)."), true);
        return 1;
    }

    private static int executeLateJoinTp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!source.isExecutedByPlayer()) {
            return 0;
        }
        
        MinigameManager manager = MinigameManager.getInstance();
        if (manager.getActiveMinigame() instanceof ManhuntMinigame manhunt) {
            ServerPlayerEntity player = source.getPlayer();
            String targetName = StringArgumentType.getString(context, "target");
            manhunt.handleLateJoinTeleport(player, targetName);
            return 1;
        }
        return 0;
    }

    private static int stop(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        MinigameManager manager = MinigameManager.getInstance();

        if (manager.getActiveMinigame() == null) {
            source.sendError(Text.literal("No active Manhunt session to stop."));
            return 0;
        }

        Minigame active = manager.getActiveMinigame();
        if (active != null) {
            active.stopGame();
        }
        manager.reset();

        source.sendFeedback(() -> Text.literal("Stopped the active Manhunt session."), true);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ManhuntMinigame manhunt = getExistingManhunt();
        MinigameManager manager = MinigameManager.getInstance();

        if (manhunt == null) {
            source.sendFeedback(() -> Text.literal("No pending or active Manhunt session."), false);
            return 1;
        }

        GameState state = manager.getCurrentState();
        source.sendFeedback(() -> Text.literal("Manhunt info:"), false);
        source.sendFeedback(() -> Text.literal("- State: " + (state == null ? "NONE" : state.name())), false);
        source.sendFeedback(() -> Text.literal("- Participants: " + manager.getParticipantCount()), false);
        source.sendFeedback(() -> Text.literal("- Speedrunners: " + manhunt.getSpeedrunners().size()), false);
        source.sendFeedback(() -> Text.literal("- Hunters: " + manhunt.getHunters().size()), false);
        ManhuntSettings settings = manhunt.getSettings();
        source.sendFeedback(() -> Text.literal("- Hunter release delay: " + settings.hunterReleaseDelaySeconds() + "s"), false);
        source.sendFeedback(() -> Text.literal("- Speedrunner respawn delay: " + settings.speedrunnerRespawnDelaySeconds() + "s"), false);
        source.sendFeedback(() -> Text.literal("- Hunter respawn delay: " + settings.hunterRespawnDelaySeconds() + "s"), false);
        source.sendFeedback(() -> Text.literal("- Compass: " + settings.huntersCompassEnabled() + " cooldown=" + settings.compassCooldownSeconds() + "s"), false);
        source.sendFeedback(() -> Text.literal("- Nether tracking: " + settings.netherTrackingEnabled()), false);
        source.sendFeedback(() -> Text.literal("- Runner glow pulse: " + settings.runnerGlowPulseMinutes() + "m"), false);
        source.sendFeedback(() -> Text.literal("- Runner lives: " + formatLives(settings.runnerLives())), false);
        source.sendFeedback(() -> Text.literal("- Hunter lives: " + formatLives(settings.hunterLives())), false);
        source.sendFeedback(() -> Text.literal("- Unassigned: " + formatPlayers(manhunt.getUnassignedParticipants())), false);

        return 1;
    }

    private static @org.jetbrains.annotations.Nullable ManhuntMinigame getOrCreatePendingManhunt(ServerCommandSource source) {
        return PendingMinigameCommand.getOrCreate(source, ManhuntMinigame.class, ManhuntMinigame::new);
    }

    private static @org.jetbrains.annotations.Nullable ManhuntMinigame getExistingManhunt() {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        return active instanceof ManhuntMinigame manhunt ? manhunt : null;
    }

    private static @org.jetbrains.annotations.Nullable ManhuntRole parseRole(String rawRole) {
        return switch (rawRole.toLowerCase()) {
            case "speedrunner", "runner" -> ManhuntRole.SPEEDRUNNER;
            case "hunter", "hunters" -> ManhuntRole.HUNTER;
            default -> null;
        };
    }

    private static String formatPlayers(List<ServerPlayerEntity> players) {
        if (players.isEmpty()) {
            return "none";
        }

        return players.stream()
            .map(player -> player.getName().getString())
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    }

    private static String formatLives(int lives) {
        return lives < 0 ? "unlimited" : Integer.toString(lives);
    }
}

