package dev.frost.miniverse.session;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class LaunchProgressBroadcaster {

    public static void broadcastLaunchProgress(MinecraftServer server, GameSession session, String stage, String detail, int progress, boolean done) {
        String title = session.getGameType().getDisplayName() + " " + session.getSessionId();
        server.execute(() -> {
            Set<UUID> recipients = new LinkedHashSet<>();
            for (SessionGroup group : session.snapshotGroups()) {
                recipients.addAll(group.getPlayerUuids());
            }
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (SessionPermissions.canManageSessions(online)) {
                    recipients.add(online.getUuid());
                }
            }
            sendLaunchProgress(server, recipients, session.getSessionId(), title, stage, detail, progress, done);
        });
    }

    public static void broadcastLaunchProgress(MinecraftServer server, String sessionId, String title, String stage, String detail, int progress, boolean done) {
        server.execute(() -> {
            Set<UUID> recipients = new LinkedHashSet<>();
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
                if (SessionPermissions.canManageSessions(online)) {
                    recipients.add(online.getUuid());
                }
            }
            sendLaunchProgress(server, recipients, sessionId, title, stage, detail, progress, done);
        });
    }

    private static void sendLaunchProgress(MinecraftServer server, Set<UUID> recipients, String sessionId, String title, String stage, String detail, int progress, boolean done) {
        NetworkConstants.LaunchProgressPayload payload = new NetworkConstants.LaunchProgressPayload(sessionId, title, stage, detail == null ? "" : detail, Math.clamp(progress, 0, 100), done);
        Text message = Text.literal(title + ": " + stage).formatted(done ? Formatting.GREEN : Formatting.YELLOW);
        for (UUID recipient : recipients) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(recipient);
            if (target == null) {
                continue;
            }
            ServerPlayNetworking.send(target, payload);
            if (progress <= 10 || done) {
                target.sendMessage(message, false);
            }
        }
    }
}
