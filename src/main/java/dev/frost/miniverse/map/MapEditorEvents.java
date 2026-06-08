package dev.frost.miniverse.map;

import dev.frost.miniverse.session.BackendLaunchMode;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import com.google.gson.JsonObject;

import java.util.Optional;

public final class MapEditorEvents {
    private static int emptyTicks = 0;
    private static boolean booted = false;

    private MapEditorEvents() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (SessionRuntimeConfig.getLaunchMode() != BackendLaunchMode.MAP_EDITOR) {
                return;
            }
            booted = true;
            emptyTicks = 0;
            
            // Teleport to editorSpawn
            JsonObject editorConfig = SessionRuntimeConfig.getSessionJson()
                .filter(json -> json.has("mapEditor") && json.get("mapEditor").isJsonObject())
                .map(json -> json.getAsJsonObject("mapEditor"))
                .orElse(null);
                
            if (editorConfig != null && editorConfig.has("mapId")) {
                String mapId = editorConfig.get("mapId").getAsString();
                Optional<MapDescriptor> map = MapStore.find(mapId);
                if (map.isPresent()) {
                    MapPosition spawn = map.get().metadata().editorSpawn();
                    handler.player.teleport(
                        server.getOverworld(),
                        spawn.x(), spawn.y(), spawn.z(),
                        spawn.yaw(), spawn.pitch()
                    );
                    handler.player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                    handler.player.getAbilities().flying = true;
                    handler.player.sendAbilitiesUpdate();
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (SessionRuntimeConfig.getLaunchMode() != BackendLaunchMode.MAP_EDITOR) {
                return;
            }
            if (!booted) {
                return;
            }
            if (server.getPlayerManager().getCurrentPlayerCount() == 0) {
                emptyTicks++;
                if (emptyTicks >= 100) { // 5 seconds empty
                    server.stop(false);
                }
            } else {
                emptyTicks = 0;
            }
            dev.frost.miniverse.map.editor.MapEditorVisualization.render(server, dev.frost.miniverse.map.editor.MapEditorNetwork.currentMapId());
        });
    }
}
