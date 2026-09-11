package dev.frost.miniverse.minigame.impl.pillarsoffortune;

import com.mojang.brigadier.CommandDispatcher;
import dev.frost.miniverse.map.MapGamemodeRegistry;
import dev.frost.miniverse.map.MapGamemodeType;
import dev.frost.miniverse.map.editor.MapEditorExtensionRegistry;
import dev.frost.miniverse.minigame.core.MinigameDefinition;
import dev.frost.miniverse.minigame.core.MinigameMetadata;
import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Map;
import java.util.Properties;

public final class PillarsOfFortuneDefinition implements MinigameDefinition {
    public static final String ID = "pillarsoffortune";
    public static final String DISPLAY_NAME = "Pillars of Fortune";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public SessionTopology topology() {
        return SessionTopology.SHARED_WORLD;
    }

    @Override
    public MinigameMetadata metadata() {
        return MinigameMetadata.custom(
            this.id(),
            this.displayName(),
            "Survive on your pillar as long as possible while gathering random loot drops.",
            "🏛",
            this.topology()
        );
    }

    @Override
    public void writeSessionProperties(NbtCompound settingsNbt, Properties properties) {
        String mapId = settingsNbt.contains("mapId") ? settingsNbt.getString("mapId").trim() : "";
        if (!mapId.isBlank()) {
            properties.setProperty("pillarsoffortune.mapId", mapId);
            dev.frost.miniverse.map.MapStore.readGamemodeConfig(mapId, ID)
                .ifPresent(config -> properties.setProperty("pillarsoffortune.mapConfig", config.toString()));
        }
        PillarsOfFortuneSettings.fromNbt(settingsNbt).writeTo(properties);
    }

    @Override
    public void writeLaunchProperties(NbtCompound settingsNbt, Map<String, String> properties) {
        if (settingsNbt.contains("mapId")) {
            properties.put("miniverse.pillarsoffortune.mapId", settingsNbt.getString("mapId"));
        }
    }

    @Override
    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
    }

    @Override
    public void registerEvents() {
        MapGamemodeRegistry.register(new MapGamemodeType(ID, DISPLAY_NAME, (map, config) -> dev.frost.miniverse.map.MapValidationResult.ok()));
        MapEditorExtensionRegistry.register(PillarsOfFortuneMapEditorExtension.EXTENSION);
        PillarsOfFortuneSessionBootstrap.register();
    }
}
