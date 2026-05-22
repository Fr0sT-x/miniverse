package dev.frost.miniverse.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

@Plugin(
    id = "miniverse-proxy",
    name = "Miniverse Proxy",
    version = "1.0.0",
    authors = {"Frost"}
)
public final class MiniverseVelocityPlugin {
    private static final int PROTOCOL_VERSION = 1;
    private static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from("miniverse:velocity");

    private final ProxyServer proxy;
    private final Logger logger;
    private final Object serverRegistrationLock = new Object();

    @Inject
    public MiniverseVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        this.proxy.getChannelRegistrar().register(CHANNEL);
        this.logger.info("Miniverse Velocity bridge listening on {}", CHANNEL.getId());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }

        try {
            ProxyCommand command = ProxyCommand.read(event.getData());
            switch (command.action()) {
                case "register" -> this.registerServer(command);
                case "connect" -> this.connectPlayer(command);
                case "register_connect" -> {
                    this.registerServer(command);
                    this.connectPlayer(command);
                }
                case "unregister" -> this.unregisterServer(command.serverName());
                default -> this.logger.warn("Ignoring unknown Miniverse proxy action '{}'.", command.action());
            }
        } catch (IOException | IllegalArgumentException e) {
            this.logger.warn("Invalid Miniverse proxy message.", e);
        }
    }

    private void registerServer(ProxyCommand command) {
        if (command.serverName().isBlank() || command.host().isBlank() || command.port() <= 0) {
            throw new IllegalArgumentException("register requires serverName, host, and port");
        }

        ServerInfo info = new ServerInfo(command.serverName(), new InetSocketAddress(command.host(), command.port()));
        synchronized (this.serverRegistrationLock) {
            Optional<RegisteredServer> existing = this.proxy.getServer(command.serverName());
            if (existing.isPresent()) {
                ServerInfo existingInfo = existing.get().getServerInfo();
                if (existingInfo.getAddress().equals(info.getAddress())) {
                    return;
                }

                this.proxy.unregisterServer(existingInfo);
            }

            this.proxy.registerServer(info);
        }
        this.logger.info("Registered Miniverse backend {} -> {}:{}.", command.serverName(), command.host(), command.port());
    }

    private void unregisterServer(String serverName) {
        if (serverName.isBlank()) {
            return;
        }

        synchronized (this.serverRegistrationLock) {
            Optional<RegisteredServer> server = this.proxy.getServer(serverName);
            if (server.isPresent()) {
                this.proxy.unregisterServer(server.get().getServerInfo());
                this.logger.info("Unregistered Miniverse backend {}.", serverName);
            }
        }
    }

    private void connectPlayer(ProxyCommand command) {
        if (command.serverName().isBlank() || command.playerUuid() == null) {
            throw new IllegalArgumentException("connect requires serverName and playerUuid");
        }

        Optional<Player> player = this.proxy.getPlayer(command.playerUuid());
        Optional<RegisteredServer> server = this.proxy.getServer(command.serverName());
        if (player.isEmpty()) {
            this.logger.warn("Cannot move missing player {} to {}.", command.playerUuid(), command.serverName());
            return;
        }
        if (server.isEmpty()) {
            this.logger.warn("Cannot move {} to missing server {}.", player.get().getUsername(), command.serverName());
            return;
        }

        player.get().createConnectionRequest(server.get()).fireAndForget();
        this.logger.info("Moving {} to Miniverse backend {}.", player.get().getUsername(), command.serverName());
    }

    private record ProxyCommand(String action, String serverName, String host, int port, UUID playerUuid) {
        private static ProxyCommand read(byte[] data) throws IOException {
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(data))) {
                int version = input.readInt();
                if (version != PROTOCOL_VERSION) {
                    throw new IllegalArgumentException("unsupported protocol version " + version);
                }

                String action = input.readUTF();
                String serverName = input.readUTF();
                String host = input.readUTF();
                int port = input.readInt();
                String player = input.readUTF();
                UUID playerUuid = player.isBlank() ? null : UUID.fromString(player);
                return new ProxyCommand(action, serverName, host, port, playerUuid);
            }
        }
    }
}
