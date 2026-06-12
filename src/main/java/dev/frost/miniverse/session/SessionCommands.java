package dev.frost.miniverse.session;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameRegistry;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class SessionCommands {
    private static final SuggestionProvider<ServerCommandSource> GAME_SUGGESTIONS = (context, builder) -> CommandSource.suggestMatching(MinigameRegistry.getIds(), builder);
    private static final SuggestionProvider<ServerCommandSource> SESSION_ID_SUGGESTIONS = (context, builder) -> {
        SessionManager.getInstance().getSessions().stream()
            .map(GameSession::getSessionId)
            .forEach(builder::suggest);
        return builder.buildFuture();
    };

    private SessionCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("session")
                .then(literal("create")
                    .then(argument("game", StringArgumentType.word())
                        .suggests(GAME_SUGGESTIONS)
                        .executes(SessionCommands::createSession)))
                .then(literal("add")
                    .then(argument("session", StringArgumentType.word())
                        .suggests(SESSION_ID_SUGGESTIONS)
                        .then(argument("player", EntityArgumentType.player())
                            .executes(SessionCommands::addPlayer))))
                .then(literal("launch")
                    .then(argument("session", StringArgumentType.word())
                        .suggests(SESSION_ID_SUGGESTIONS)
                        .executes(SessionCommands::launchSession)))
                .then(literal("stop")
                    .then(argument("session", StringArgumentType.word())
                        .suggests(SESSION_ID_SUGGESTIONS)
                        .executes(SessionCommands::stopSession)))
                .then(literal("info")
                    .then(argument("session", StringArgumentType.word())
                        .suggests(SESSION_ID_SUGGESTIONS)
                        .executes(SessionCommands::info)))
                .then(literal("list").executes(SessionCommands::listSessions))
        );
    }

    private static int createSession(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String rawGame = StringArgumentType.getString(context, "game");
        MinigameDefinition definition = MinigameRegistry.get(rawGame).orElse(null);

        if (definition == null) {
            source.sendError(Text.literal("Unknown game type '" + rawGame + "'. Use one of: " + String.join(", ", MinigameRegistry.getIds()) + "."));
            return 0;
        }

        SessionGameDescriptor gameType = SessionGameDescriptor.fromDefinition(definition);
        GameSession session = SessionManager.getInstance().createSession(gameType);
        source.sendFeedback(
            () -> Text.literal("Created " + gameType.getDisplayName() + " session " + session.getSessionId() + " with shared seed " + session.getSeedPlan().sharedSeed() + "."),
            false
        );
        return 1;
    }

    private static int addPlayer(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        String sessionId = StringArgumentType.getString(context, "session");
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");

        GameSession session = SessionManager.getInstance().getSession(sessionId).orElse(null);
        if (session == null) {
            source.sendError(Text.literal("Unknown session '" + sessionId + "'."));
            return 0;
        }

        SessionGroup assignment = SessionManager.getInstance().assignPlayer(sessionId, player);
        source.sendFeedback(
            () -> Text.literal("Assigned " + player.getName().getString() + " to session " + sessionId + "."),
            true
        );

        if (session.getState() == SessionState.RUNNING || session.getState() == SessionState.LAUNCHING) {
            String launchNotice = assignment.getConnectionAddress() == null
                ? "Instance is launching in the background for " + player.getName().getString() + "."
                : "Instance is running at " + assignment.getConnectionAddress() + ".";
            source.sendFeedback(() -> Text.literal(launchNotice), false);
        }
        return 1;
    }

    private static int launchSession(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String sessionId = StringArgumentType.getString(context, "session");
        SessionManager manager = SessionManager.getInstance();
        GameSession session = manager.getSession(sessionId).orElse(null);

        if (session == null) {
            source.sendError(Text.literal("Unknown session '" + sessionId + "'."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Launching session " + sessionId + " in the background..."), true);
        manager.launchSession(sessionId, source.getServer()).whenComplete((launchedSession, error) -> source.getServer().execute(() -> {
            if (error != null) {
                source.sendError(Text.literal("Failed to launch session " + sessionId + ": " + error.getMessage()));
                Miniverse.LOGGER.error("Failed to launch session {}", sessionId, error);
                return;
            }

            new dev.frost.miniverse.session.PlayerTransferService().transferAssignedPlayers(source.getServer(), launchedSession);

            StringBuilder message = new StringBuilder("Session ")
                .append(launchedSession.getSessionId())
                .append(" launched for ")
                .append(launchedSession.getAssignments().size())
                .append(" player(s): ");

            boolean first = true;
            for (SessionGroup assignment : launchedSession.getAssignments()) {
                if (!first) {
                    message.append(" | ");
                }
                first = false;
                message.append(assignment.getDisplayName())
                    .append(" -> ")
                    .append(assignment.getConnectionAddress());
            }

            source.sendFeedback(() -> Text.literal(message.toString()), true);
        }));
        return 1;
    }

    private static int stopSession(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String sessionId = StringArgumentType.getString(context, "session");
        SessionManager manager = SessionManager.getInstance();

        if (manager.getSession(sessionId).isEmpty()) {
            source.sendError(Text.literal("Unknown session '" + sessionId + "'."));
            return 0;
        }

        SessionRegistry.markStopRequested(sessionId);
        manager.stopSession(sessionId);
        source.sendFeedback(() -> Text.literal("Stopped session " + sessionId + "."), true);
        return 1;
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String sessionId = StringArgumentType.getString(context, "session");
        GameSession session = SessionManager.getInstance().getSession(sessionId).orElse(null);

        if (session == null) {
            source.sendError(Text.literal("Unknown session '" + sessionId + "'."));
            return 0;
        }

        source.sendFeedback(() -> Text.literal("Session " + session.getSessionId() + ":"), false);
        source.sendFeedback(() -> Text.literal("- Game: " + session.getGameType().getDisplayName()), false);
        source.sendFeedback(() -> Text.literal("- State: " + session.getState()), false);
        source.sendFeedback(() -> Text.literal("- Seed: " + session.getSeedPlan().sharedSeed()), false);
        source.sendFeedback(() -> Text.literal("- Players: " + session.getAssignments().size()), false);

        for (SessionGroup assignment : session.getAssignments()) {
            source.sendFeedback(
                () -> Text.literal("  * " + assignment.getDisplayName() + " => " + assignment.getState() + (assignment.getConnectionAddress() == null ? "" : " @ " + assignment.getConnectionAddress())),
                false
            );
        }

        return 1;
    }

    private static int listSessions(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        List<GameSession> sessions = SessionManager.getInstance().getSessions();

        if (sessions.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No sessions have been created yet."), false);
            return 1;
        }

        source.sendFeedback(() -> Text.literal("Sessions:"), false);
        for (GameSession session : sessions) {
            source.sendFeedback(
                () -> Text.literal("- " + session.getSessionId() + " [" + session.getGameType().getDisplayName() + "] " + session.getState() + " groups=" + session.getAssignments().size()),
                false
            );
        }
        return 1;
    }

}