package dev.frost.miniverse.network.handlers;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.LateJoinPolicy;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameRegistry;
import dev.frost.miniverse.session.PendingSessionJoinManager;
import dev.frost.miniverse.session.PlayerTransferService;
import dev.frost.miniverse.session.SessionConfigJson;
import dev.frost.miniverse.session.SessionListSerializer;
import dev.frost.miniverse.session.SessionManager;
import dev.frost.miniverse.session.SessionPermissions;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public class SessionPlayerNetworkHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.ASSIGN_MID_GAME_PLAYER_ID, (payload, context) -> handleAssignMidGame(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CLEANUP_PLAYER_ID, (payload, context) -> handleCleanupPlayer(context.server(), context.player(), payload));
    }

    private static void handleAssignMidGame(MinecraftServer server, ServerPlayerEntity admin, NetworkConstants.AssignMidGamePlayerPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(admin, "assign late players")) {
            return;
        }

        UUID playerId;
        try {
            playerId = UUID.fromString(payload.playerUuid());
        } catch (IllegalArgumentException e) {
            admin.sendMessage(Text.literal("Invalid player id: " + payload.playerUuid()).formatted(Formatting.RED), false);
            return;
        }

        if (SessionRuntimeConfig.isSessionServer()) {
            String currentSessionId = SessionRuntimeConfig.getSessionId().orElse(payload.sessionId());
            if (!currentSessionId.equals(payload.sessionId())) {
                admin.sendMessage(Text.literal("Cannot assign players to a different backend session from here.").formatted(Formatting.RED), false);
                return;
            }
            if (!validateMidGameRoleForSession(payload.sessionId(), payload.role(), admin)) {
                SessionListSerializer.sendSessionList(server, admin);
                return;
            }
            SessionRegistry.addMidGameAssignmentRequest(
                payload.sessionId(),
                playerId,
                payload.teamLabel(),
                payload.role(),
                admin.getName().getString()
            );
            admin.sendMessage(Text.literal("Assignment requested. The main server will transfer the player into this session.").formatted(Formatting.YELLOW), false);
            SessionListSerializer.sendSessionList(server, admin);
            return;
        }

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerId);
        if (target == null) {
            admin.sendMessage(Text.literal("That player is no longer online.").formatted(Formatting.YELLOW), false);
            PendingSessionJoinManager.getInstance().remove(playerId);
            SessionListSerializer.sendSessionList(server, admin);
            return;
        }

        SessionManager manager = SessionManager.getInstance();
        try {
            if (!validateMidGameRoleForSession(payload.sessionId(), payload.role(), admin)) {
                SessionListSerializer.sendSessionList(server, admin);
                return;
            }
            manager.assignPlayerMidGameAsync(payload.sessionId(), target, payload.teamLabel(), payload.role(), server)
                .whenComplete((group, error) -> server.execute(() -> {
                    if (error != null) {
                        admin.sendMessage(Text.literal(error.getCause() == null ? error.getMessage() : error.getCause().getMessage()).formatted(Formatting.RED), false);
                        SessionListSerializer.sendSessionList(server, admin);
                        return;
                    }

                    PendingSessionJoinManager.getInstance().remove(playerId);
                    new PlayerTransferService().transferPlayer(target, group);
                    admin.sendMessage(Text.literal("Assigned " + target.getName().getString() + " to " + payload.sessionId() + "."), false);
                    SessionListSerializer.sendSessionList(server, admin);
                }));
        } catch (RuntimeException e) {
            admin.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
            SessionListSerializer.sendSessionList(server, admin);
            return;
        }
    }

    private static void handleCleanupPlayer(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.CleanupPlayerPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "run session cleanup")) {
            return;
        }

        String sessionId = payload.sessionId();
        SessionManager.getInstance().getSession(sessionId).ifPresent(session -> {
            for (dev.frost.miniverse.session.SessionGroup group : session.snapshotGroups()) {
                for (UUID playerUuid : group.getPlayerUuids()) {
                    ServerPlayerEntity target = server.getPlayerManager().getPlayer(playerUuid);
                    if (target != null) {
                        target.getInventory().clear();
                        target.currentScreenHandler.sendContentUpdates();
                        target.getHungerManager().setFoodLevel(20);
                        target.getHungerManager().setSaturationLevel(20.0F);
                        target.sendMessage(Text.literal("Your inventory has been cleaned and hunger restored."), false);
                    }
                }
            }
        });
    }

    private static LateJoinPolicy getLateJoinPolicy(String sessionId) {
        String gameId;
        if (SessionRuntimeConfig.isSessionServer()) {
            gameId = SessionRuntimeConfig.getSessionJson()
                .map(json -> SessionConfigJson.string(json, "gameId", SessionConfigJson.string(json, "game", "")))
                .orElse("");
        } else {
            gameId = SessionManager.getInstance().getSession(sessionId)
                .map(session -> session.getGameType().getCommandName())
                .orElse("");
        }

        return MinigameRegistry.get(gameId)
            .map(MinigameDefinition::lateJoinPolicy)
            .orElseGet(dev.frost.miniverse.minigame.core.DefaultLateJoinPolicy::new);
    }

    private static boolean validateMidGameRoleForSession(String sessionId, String role, ServerPlayerEntity admin) {
        LateJoinPolicy policy = getLateJoinPolicy(sessionId);
        boolean valid = policy.validateRole(role);
        if (!valid) {
            admin.sendMessage(Text.literal("Invalid role selected for this game type. Please choose a valid role.").formatted(Formatting.RED), false);
        }
        return valid;
    }
}
