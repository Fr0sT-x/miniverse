package dev.frost.miniverse.minigame.core.event;

import dev.frost.miniverse.map.editor.MapMarker;
import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerRegionAware {
    default void onPlayerEnterRegion(ServerPlayerEntity player, MapMarker region) {
    }

    default void onPlayerExitRegion(ServerPlayerEntity player, MapMarker region) {
    }
}
