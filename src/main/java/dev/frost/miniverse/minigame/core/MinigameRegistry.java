package dev.frost.miniverse.minigame.core;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MinigameRegistry {
    private static final Map<String, MinigameDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static boolean eventsRegistered;

    private MinigameRegistry() {
    }

    public static synchronized void register(MinigameDefinition definition) {
        String id = normalize(definition.id());
        if (DEFINITIONS.containsKey(id)) {
            throw new IllegalStateException("Duplicate minigame id: " + id);
        }
        DEFINITIONS.put(id, definition);
    }

    public static Optional<MinigameDefinition> get(String id) {
        return Optional.ofNullable(DEFINITIONS.get(normalize(id)));
    }

    public static List<MinigameDefinition> getDefinitions() {
        return Collections.unmodifiableList(new ArrayList<>(DEFINITIONS.values()));
    }

    public static List<String> getIds() {
        return DEFINITIONS.keySet().stream().toList();
    }

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        for (MinigameDefinition definition : DEFINITIONS.values()) {
            definition.registerCommands(dispatcher);
        }
    }

    public static synchronized void registerEvents() {
        if (eventsRegistered) {
            return;
        }

        for (MinigameDefinition definition : DEFINITIONS.values()) {
            definition.registerEvents();
        }
        eventsRegistered = true;
    }

    private static String normalize(@Nullable String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
