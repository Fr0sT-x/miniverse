package dev.frost.miniverse.minigame.core.visibility;

import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public final class TeamGlowVisibility {
    public static final int ENTITY_FLAGS_TRACKED_DATA_ID = 0;
    public static final byte GLOWING_FLAG = 0x40;

    private TeamGlowVisibility() {
    }

    public static boolean canViewerSeeGlowing(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (!(active instanceof TeamManagerProvider provider)) {
            return true;
        }

        String viewerTeam = provider.teamManager().teamId(viewer.getUuid());
        String targetTeam = provider.teamManager().teamId(target.getUuid());
        if (viewerTeam == null || targetTeam == null) {
            return false;
        }
        return viewerTeam.equals(targetTeam);
    }

    public static void resyncFor(ServerPlayerEntity viewer) {
        if (viewer == null || viewer.isDisconnected()) {
            return;
        }
        MinecraftServer server = viewer.getServer();
        if (server == null) {
            return;
        }

        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            if (target == viewer || target.isDisconnected()) {
                continue;
            }
            sendState(viewer, target, canViewerSeeGlowing(viewer, target));
        }
    }

    private static void sendState(ServerPlayerEntity viewer, ServerPlayerEntity target, boolean glowing) {
        byte flags = (byte) (target.isInvisible() ? 0x20 : 0);
        if (target.isSneaking()) {
            flags |= 0x02;
        }
        if (target.isSprinting()) {
            flags |= 0x08;
        }
        if (target.isSwimming()) {
            flags |= 0x10;
        }
        if (target.isFallFlying()) {
            flags |= (byte) 0x80;
        }
        if (glowing && target.isGlowing()) {
            flags |= GLOWING_FLAG;
        }

        viewer.networkHandler.sendPacket(new EntityTrackerUpdateS2CPacket(
            target.getId(),
            List.of(new DataTracker.SerializedEntry<>(
                ENTITY_FLAGS_TRACKED_DATA_ID,
                TrackedDataHandlerRegistry.BYTE,
                flags
            ))
        ));
    }
}
