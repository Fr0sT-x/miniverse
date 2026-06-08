package dev.frost.miniverse.minigame.core.role;

import net.minecraft.server.network.ServerPlayerEntity;

public interface Role {
    String getId();
    String getDisplayName();
    String getDescription();
    
    default void onAssign(ServerPlayerEntity player) {}
    default void onRemove(ServerPlayerEntity player) {}
    
    default boolean isSpectator() {
        return false;
    }
}
