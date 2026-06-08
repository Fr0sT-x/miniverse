package dev.frost.miniverse.minigame.core.kit;

import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class KitRegistry {
    private static final Map<Identifier, Kit> REGISTRY = new ConcurrentHashMap<>();

    public static void register(Kit kit) {
        REGISTRY.put(kit.getId(), kit);
    }

    public static Optional<Kit> get(Identifier id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    public static Collection<Kit> getAll() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    public static void clear() {
        REGISTRY.clear();
    }

    public static void loadCustomKits(net.minecraft.server.MinecraftServer server) {
        try {
            java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("miniverse/custom_kits");
            if (java.nio.file.Files.exists(configDir)) {
                try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(configDir)) {
                    stream.filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                        try {
                            String jsonStr = java.nio.file.Files.readString(path);
                            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                            Kit kit = Kit.fromJson(json, server.getRegistryManager());
                            register(kit);
                        } catch (Exception e) {
                            System.err.println("Failed to load custom kit from " + path + ": " + e.getMessage());
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
