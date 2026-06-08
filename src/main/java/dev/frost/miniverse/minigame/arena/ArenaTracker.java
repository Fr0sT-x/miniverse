package dev.frost.miniverse.minigame.arena;

import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ArenaTracker {
    private static final Map<ServerWorld, ArenaManager> managers = new ConcurrentHashMap<>();

    public static void register(ServerWorld world, ArenaManager manager) {
        managers.put(world, manager);
    }

    public static void unregister(ServerWorld world) {
        managers.remove(world);
    }

    public static Optional<ArenaManager> getManager(ServerWorld world) {
        return Optional.ofNullable(managers.get(world));
    }
}
