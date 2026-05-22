package dev.frost.miniverse.network;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TransitionTransferCoordinator {
    private static final int FALLBACK_TRANSFER_TICKS = 80;
    private static final Map<String, PendingTransfer> PENDING_TRANSFERS = new ConcurrentHashMap<>();
    private static boolean registered;

    private TransitionTransferCoordinator() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.TRANSITION_READY_ID, (payload, context) ->
            completeTransfer(context.player(), payload.token())
        );
        ServerTickEvents.END_SERVER_TICK.register(TransitionTransferCoordinator::tick);
        registered = true;
    }

    public static void transfer(ServerPlayerEntity player, String host, int port) {
        transfer(player, host, port, "Transferring");
    }

    public static void transfer(ServerPlayerEntity player, String host, int port, String context) {
        transfer(player, host, port, context, () -> {
        });
    }

    public static void transfer(ServerPlayerEntity player, String host, int port, String context, Runnable afterTransferPacketSent) {
        if (VelocityProxyBridge.isEnabled()) {
            transferToVelocityLobby(player, context, afterTransferPacketSent);
            return;
        }

        String transferHost = ClientConnectionHosts.resolveForTransfer(player, host);
        if (transferHost.isBlank() || port <= 0) {
            return;
        }

        if (!ServerPlayNetworking.canSend(player, NetworkConstants.TRANSITION_START_ID)) {
            sendTransferPacket(player, transferHost, port);
            afterTransferPacketSent.run();
            return;
        }

        String token = UUID.randomUUID().toString();
        PENDING_TRANSFERS.put(token, new PendingTransfer(
            player.getUuid(),
            transferHost,
            port,
            "",
            -1,
            player.getEntityWorld().getServer().getTicks() + FALLBACK_TRANSFER_TICKS,
            afterTransferPacketSent
        ));
        ServerPlayNetworking.send(player, new NetworkConstants.TransitionStartPayload(token, normalizeContext(context)));
    }

    public static void transferToVelocityBackend(ServerPlayerEntity player, String serverName, int localPort, String context, Runnable afterTransferPacketSent) {
        if (serverName == null || serverName.isBlank() || localPort <= 0) {
            return;
        }

        queueTransfer(player, new PendingTransfer(
            player.getUuid(),
            "",
            -1,
            serverName,
            localPort,
            player.getEntityWorld().getServer().getTicks() + FALLBACK_TRANSFER_TICKS,
            afterTransferPacketSent
        ), context);
    }

    public static void transferToVelocityServer(ServerPlayerEntity player, String serverName, String context, Runnable afterTransferPacketSent) {
        if (serverName == null || serverName.isBlank()) {
            return;
        }

        queueTransfer(player, new PendingTransfer(
            player.getUuid(),
            "",
            -1,
            serverName,
            -1,
            player.getEntityWorld().getServer().getTicks() + FALLBACK_TRANSFER_TICKS,
            afterTransferPacketSent
        ), context);
    }

    private static void transferToVelocityLobby(ServerPlayerEntity player, String context, Runnable afterTransferPacketSent) {
        transferToVelocityServer(player, dev.frost.miniverse.session.VelocityProxyConfig.getInstance().lobbyServerName(), context, afterTransferPacketSent);
    }

    private static void queueTransfer(ServerPlayerEntity player, PendingTransfer transfer, String context) {
        if (!ServerPlayNetworking.canSend(player, NetworkConstants.TRANSITION_START_ID)) {
            sendTransfer(player, transfer);
            transfer.afterTransferPacketSent().run();
            return;
        }

        String token = UUID.randomUUID().toString();
        PENDING_TRANSFERS.put(token, transfer);
        ServerPlayNetworking.send(player, new NetworkConstants.TransitionStartPayload(token, normalizeContext(context)));
    }

    private static void completeTransfer(ServerPlayerEntity player, String token) {
        PendingTransfer transfer = PENDING_TRANSFERS.remove(token);
        if (transfer == null || !transfer.playerUuid().equals(player.getUuid())) {
            return;
        }

        sendTransfer(player, transfer);
        transfer.afterTransferPacketSent().run();
    }

    private static void tick(MinecraftServer server) {
        int ticks = server.getTicks();
        Iterator<Map.Entry<String, PendingTransfer>> iterator = PENDING_TRANSFERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PendingTransfer> entry = iterator.next();
            PendingTransfer transfer = entry.getValue();
            if (ticks < transfer.fallbackTick()) {
                continue;
            }

            PENDING_TRANSFERS.remove(entry.getKey(), transfer);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(transfer.playerUuid());
            if (player != null && !player.isDisconnected()) {
                sendTransfer(player, transfer);
            }
            transfer.afterTransferPacketSent().run();
        }
    }

    private static void sendTransfer(ServerPlayerEntity player, PendingTransfer transfer) {
        if (transfer.velocityServerName() != null && !transfer.velocityServerName().isBlank()) {
            if (transfer.velocityLocalPort() > 0) {
                VelocityProxyBridge.registerAndConnect(player, transfer.velocityServerName(), transfer.velocityLocalPort());
            } else {
                VelocityProxyBridge.connect(player, transfer.velocityServerName());
            }
            return;
        }

        sendTransferPacket(player, transfer.host(), transfer.port());
    }

    private static void sendTransferPacket(ServerPlayerEntity player, String host, int port) {
        player.networkHandler.sendPacket(new ServerTransferS2CPacket(host, port));
    }

    private static String normalizeContext(String context) {
        if (context == null || context.isBlank()) {
            return "Transferring";
        }
        return context.length() > 48 ? context.substring(0, 48) : context;
    }

    private record PendingTransfer(UUID playerUuid, String host, int port, String velocityServerName, int velocityLocalPort, int fallbackTick, Runnable afterTransferPacketSent) {
    }
}
