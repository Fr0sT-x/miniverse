package dev.frost.miniverse.session;

import dev.frost.miniverse.network.TransitionTransferCoordinator;
import dev.frost.miniverse.network.VelocityProxyBridge;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public class PlayerTransferService {

    public void transferAssignedPlayers(MinecraftServer server, GameSession session) {
        for (SessionGroup group : session.snapshotGroups()) {
            if (group.getState() != SessionState.RUNNING || group.getPort() == null) {
                continue;
            }

            for (UUID playerUuid : group.getPlayerUuids()) {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
                if (player != null) {
                    this.transferPlayer(player, group);
                }
            }
        }
    }

    public void transferPlayer(ServerPlayerEntity player, SessionGroup group) {
        if (group.getState() != SessionState.RUNNING || group.getPort() == null) {
            return;
        }

        player.sendMessage(Text.literal("Transferring you to your session..."), false);
        if (VelocityProxyBridge.isEnabled()) {
            TransitionTransferCoordinator.transferToVelocityBackend(
                player,
                VelocityProxyBridge.serverName(group),
                group.getPort(),
                "Joining " + group.getGameType().getDisplayName(),
                () -> {
                }
            );
            return;
        }

        TransitionTransferCoordinator.transfer(
            player,
            SessionServerConfig.getInstance().advertisedHost(),
            SessionLauncherConfig.getInstance().publicPortForLocalPort(group.getPort()),
            "Joining " + group.getGameType().getDisplayName()
        );
    }

    public void transferPlayer(ServerPlayerEntity player, String host, int port) {
        this.transferPlayer(player, host, port, () -> {
        });
    }

    public void transferPlayer(ServerPlayerEntity player, String host, int port, Runnable afterTransferPacketSent) {
        if (host == null || host.isBlank() || port <= 0) {
            return;
        }

        player.sendMessage(Text.literal("Returning you to the main server..."), false);
        TransitionTransferCoordinator.transfer(player, host, port, "Returning to Lobby", afterTransferPacketSent);
    }

    public void transferToMapEditor(ServerPlayerEntity player, String mapId, int port) {
        String context = "Editing map " + mapId;
        if (VelocityProxyBridge.isEnabled()) {
            TransitionTransferCoordinator.transferToVelocityBackend(
                player,
                VelocityProxyBridge.serverName(mapId, "map-editor-" + port),
                port,
                context,
                () -> {
                }
            );
        } else {
            TransitionTransferCoordinator.transfer(
                player,
                SessionServerConfig.getInstance().advertisedHost(),
                SessionLauncherConfig.getInstance().publicPortForLocalPort(port),
                context
            );
        }
    }

    public void transferToInspectionServer(ServerPlayerEntity player, String sessionId, int port) {
        String context = "Inspecting " + sessionId;
        if (VelocityProxyBridge.isEnabled()) {
            TransitionTransferCoordinator.transferToVelocityBackend(
                player,
                VelocityProxyBridge.serverName(sessionId, "inspection-" + port),
                port,
                context,
                () -> {
                }
            );
        } else {
            TransitionTransferCoordinator.transfer(
                player,
                SessionServerConfig.getInstance().advertisedHost(),
                SessionLauncherConfig.getInstance().publicPortForLocalPort(port),
                context
            );
        }
    }
}
