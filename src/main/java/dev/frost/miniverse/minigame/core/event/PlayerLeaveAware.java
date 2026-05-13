package dev.frost.miniverse.minigame.core.event;

import net.minecraft.server.network.ServerPlayerEntity;

public interface PlayerLeaveAware {
    void onPlayerLeave(ServerPlayerEntity player);
}
