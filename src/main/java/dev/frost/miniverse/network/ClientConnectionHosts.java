package dev.frost.miniverse.network;

import dev.frost.miniverse.Miniverse;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientConnectionHosts {
    private static final int MAX_HOST_LENGTH = 255;
    private static final Map<UUID, String> HOSTS = new ConcurrentHashMap<>();
    private static boolean registered;

    private ClientConnectionHosts() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HOSTS.remove(handler.player.getUuid()));
        registered = true;
    }

    public static void remember(ServerPlayerEntity player, String host) {
        String normalized = normalizeHost(host);
        if (normalized.isBlank()) {
            return;
        }

        HOSTS.put(player.getUuid(), normalized);
        Miniverse.LOGGER.debug("Remembered client connection host {} for {}", normalized, player.getName().getString());
    }

    public static Optional<String> get(ServerPlayerEntity player) {
        return Optional.ofNullable(HOSTS.get(player.getUuid()));
    }

    public static String resolveForTransfer(ServerPlayerEntity player, String configuredHost) {
        String normalized = normalizeHost(configuredHost);
        if (!isLoopbackOrWildcard(normalized)) {
            return normalized;
        }

        Optional<String> clientHost = get(player).filter(host -> !isLoopbackOrWildcard(host));
        if (clientHost.isPresent()) {
            Miniverse.LOGGER.info(
                "Using client-reported connection host {} for transfer of {} instead of configured host {}.",
                clientHost.get(),
                player.getName().getString(),
                normalized
            );
            return clientHost.get();
        }

        return normalized;
    }

    public static boolean isLoopbackOrWildcard(String host) {
        String normalized = normalizeHost(host).toLowerCase();
        return normalized.isBlank()
            || normalized.equals("localhost")
            || normalized.equals("127.0.0.1")
            || normalized.equals("0.0.0.0")
            || normalized.equals("::1")
            || normalized.equals("[::1]")
            || normalized.equals("0:0:0:0:0:0:0:1");
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }

        String value = host.trim();
        if (value.length() > MAX_HOST_LENGTH) {
            value = value.substring(0, MAX_HOST_LENGTH);
        }
        return value;
    }
}
