package dev.frost.miniverse.session;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PendingSessionJoinManager {
    private static final PendingSessionJoinManager INSTANCE = new PendingSessionJoinManager();
    private final Map<UUID, PendingJoiner> pendingJoiners = new LinkedHashMap<>();

    private PendingSessionJoinManager() {
    }

    public static PendingSessionJoinManager getInstance() {
        return INSTANCE;
    }

    public synchronized void recordIfNeeded(ServerPlayerEntity player, MinecraftServer server) {
        if (player == null || server == null) {
            return;
        }
        SessionManager manager = SessionManager.getInstance();
        if (manager.getSessionForPlayer(player.getUuid()).isPresent()) {
            this.pendingJoiners.remove(player.getUuid());
            return;
        }
        List<String> runningSessionIds = manager.getSessions().stream()
            .filter(session -> session.getState() == SessionState.RUNNING)
            .map(GameSession::getSessionId)
            .toList();
        if (runningSessionIds.isEmpty()) {
            return;
        }

        long joinedAtMillis = System.currentTimeMillis();
        boolean added = this.pendingJoiners.put(player.getUuid(), new PendingJoiner(player.getUuid(), player.getName().getString(), joinedAtMillis)) == null;
        if (added) {
            SessionRegistry.recordPendingJoinNotice(runningSessionIds, player.getUuid(), player.getName().getString(), joinedAtMillis);
            Text message = Text.literal(player.getName().getString() + " joined unassigned. Use Miniverse session admin to add them to a live session.")
                .formatted(Formatting.YELLOW);
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (SessionPermissions.canManageSessions(online)) {
                    online.sendMessage(message, false);
                }
            }
        }
    }

    public synchronized void remove(UUID playerId) {
        this.pendingJoiners.remove(playerId);
        SessionRegistry.removePendingJoinNotice(playerId);
    }

    public synchronized List<PendingJoiner> list(MinecraftServer server) {
        if (server != null) {
            List<UUID> offline = this.pendingJoiners.keySet().stream()
                .filter(uuid -> server.getPlayerManager().getPlayer(uuid) == null)
                .toList();
            for (UUID uuid : offline) {
                this.remove(uuid);
            }
        }
        return List.copyOf(new ArrayList<>(this.pendingJoiners.values()));
    }

    public record PendingJoiner(UUID playerId, String playerName, long joinedAtMillis) {
    }
}
