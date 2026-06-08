package dev.frost.miniverse.minigame.impl.deathshuffle.objective;

import dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator.ConditionObjectiveGenerator;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator.DamageTypeObjectiveGenerator;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator.DeathObjectiveGenerator;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator.EntityObjectiveGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeathObjectiveManager {
    private static final Map<Identifier, DeathObjective> cachedBuiltInObjectives = new LinkedHashMap<>();
    private static final Map<Identifier, DeathObjective> cachedDynamicObjectives = new LinkedHashMap<>();
    private static final List<DeathObjectiveGenerator> GENERATORS = List.of(
        new EntityObjectiveGenerator(),
        new DamageTypeObjectiveGenerator(),
        new ConditionObjectiveGenerator()
    );

    private DeathObjectiveManager() {
    }

    public static void initialize() {
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            if (success) {
                reload();
            }
        });
        reload();
    }

    public static void reload() {
        cachedBuiltInObjectives.clear();
        for (DeathObjectiveGenerator generator : GENERATORS) {
            Map<Identifier, DeathObjective> generated = generator.generate();
            if (generated != null) {
                cachedBuiltInObjectives.putAll(generated);
            }
        }
    }

    @Nullable
    public static DeathObjective get(MinecraftServer server, Identifier id) {
        if (id.getNamespace().equals("miniverse") && id.getPath().startsWith("dynamic/")) {
            DeathObjective obj = dev.frost.miniverse.minigame.impl.deathshuffle.objective.generator.DynamicObjectiveResolver.resolve(id);
            if (obj != null) {
                cachedDynamicObjectives.put(id, obj);
            }
            return obj;
        }
        if (server != null) {
            Registry<DeathObjective> registry = server.getRegistryManager().get(DeathObjective.REGISTRY_KEY);
            if (registry != null) {
                DeathObjective obj = registry.get(id);
                if (obj != null) {
                    return obj;
                }
            }
        }

        if (cachedBuiltInObjectives.isEmpty()) {
            reload();
        }
        return cachedBuiltInObjectives.get(id);
    }

    public static Map<Identifier, DeathObjective> getAll(@Nullable MinecraftServer server) {
        Map<Identifier, DeathObjective> all = new LinkedHashMap<>();

        if (cachedBuiltInObjectives.isEmpty()) {
            reload();
        }
        all.putAll(cachedBuiltInObjectives);
        all.putAll(cachedDynamicObjectives);

        if (server != null) {
            Registry<DeathObjective> registry = server.getRegistryManager().get(DeathObjective.REGISTRY_KEY);
            if (registry != null) {
                for (Identifier id : registry.getIds()) {
                    DeathObjective obj = registry.get(id);
                    if (obj != null) {
                        all.put(id, obj);
                    }
                }
            }
        }
        
        return all;
    }

    @Nullable
    public static Identifier getId(@Nullable MinecraftServer server, DeathObjective objective) {
        for (Map.Entry<Identifier, DeathObjective> entry : getAll(server).entrySet()) {
            if (entry.getValue().equals(objective)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
