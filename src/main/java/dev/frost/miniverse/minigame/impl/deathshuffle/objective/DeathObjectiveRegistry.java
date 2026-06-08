package dev.frost.miniverse.minigame.impl.deathshuffle.objective;

import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

public final class DeathObjectiveRegistry {
    private DeathObjectiveRegistry() {
    }

    public static void register() {
        DynamicRegistries.registerSynced(DeathObjective.REGISTRY_KEY, DeathObjective.CODEC);
        dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager.initialize();
        
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            java.nio.file.Path datapacksDir = server.getSavePath(net.minecraft.util.WorldSavePath.DATAPACKS);
            java.nio.file.Path objectiveDir = datapacksDir.resolve("miniverse_objectives/data/miniverse/death_objective");
            try {
                java.nio.file.Files.createDirectories(objectiveDir);
                java.nio.file.Path mcmeta = datapacksDir.resolve("miniverse_objectives/pack.mcmeta");
                if (!java.nio.file.Files.exists(mcmeta)) {
                    java.nio.file.Files.writeString(mcmeta, "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"Custom Miniverse Death Objectives\"\n  }\n}");
                }
            } catch (java.io.IOException e) {
                // Ignore if unable to create directory
            }
        });
    }
}
