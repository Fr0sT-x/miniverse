package dev.frost.miniverse.minigame.core.visibility;

import net.minecraft.server.network.ServerPlayerEntity;

public interface GlowVisibilityAware {
    boolean canViewerSeeGlowing(ServerPlayerEntity viewer, ServerPlayerEntity target);
}
