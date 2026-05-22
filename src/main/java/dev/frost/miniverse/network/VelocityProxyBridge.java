package dev.frost.miniverse.network;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionGroup;
import dev.frost.miniverse.session.VelocityProxyConfig;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public final class VelocityProxyBridge {
    private static final int PROTOCOL_VERSION = 1;

    private VelocityProxyBridge() {
    }

    public static boolean isEnabled() {
        return VelocityProxyConfig.getInstance().velocityEnabled();
    }

    public static String serverName(SessionGroup group) {
        return VelocityProxyConfig.getInstance().serverName(group.getSessionId(), group.getGroupLabel());
    }

    public static String serverName(String sessionId, String groupLabel) {
        return VelocityProxyConfig.getInstance().serverName(sessionId, groupLabel);
    }

    public static void registerAndConnect(ServerPlayerEntity player, String serverName, int localPort) {
        String host = VelocityProxyConfig.getInstance().backendHost();
        send(player, "register_connect", serverName, host, localPort, player.getUuid());
    }

    public static void connectToLobby(ServerPlayerEntity player) {
        send(player, "connect", VelocityProxyConfig.getInstance().lobbyServerName(), "", -1, player.getUuid());
    }

    public static void connect(ServerPlayerEntity player, String serverName) {
        send(player, "connect", serverName, "", -1, player.getUuid());
    }

    private static void send(ServerPlayerEntity player, String action, String serverName, String host, int port, UUID playerUuid) {
        try {
            byte[] data = encode(action, serverName, host, port, playerUuid);
            player.networkHandler.sendPacket(new CustomPayloadS2CPacket(new NetworkConstants.VelocityProxyPayload(data)));
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to encode Velocity proxy command {} for {}.", action, player.getName().getString(), e);
        }
    }

    private static byte[] encode(String action, String serverName, String host, int port, UUID playerUuid) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(PROTOCOL_VERSION);
            output.writeUTF(action == null ? "" : action);
            output.writeUTF(serverName == null ? "" : serverName);
            output.writeUTF(host == null ? "" : host);
            output.writeInt(port);
            output.writeUTF(playerUuid == null ? "" : playerUuid.toString());
        }
        return bytes.toByteArray();
    }
}
