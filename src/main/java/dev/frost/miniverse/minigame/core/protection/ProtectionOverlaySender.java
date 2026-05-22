package dev.frost.miniverse.minigame.core.protection;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Server-side broadcaster for player protection overlays.
 *
 * Sends protection overlay state to clients for translucent rendering
 * of respawn invincibility, grace periods, and other temporary effects.
 *
 * Usage:
 * - Activate overlay: send(..., remainingTicks, true, color)
 * - Update overlay: send(..., newRemainingTicks, true, color)
 * - Deactivate overlay: send(..., 0, false, color)
 *
 * All overlays use the translucent rendering system and respect GPU depth testing
 * for automatic occlusion behind walls.
 */
public final class ProtectionOverlaySender {
    private ProtectionOverlaySender() {
    }

    /**
     * Send a protection overlay update to a specific player.
     *
     * @param recipient The player who should see the overlay update
     * @param playerId The UUID of the protected player
     * @param overlayId Unique identifier for this overlay effect
     * @param remainingTicks How many ticks the overlay remains active
     * @param active Whether the overlay is active
     * @param argbColor ARGB color (alpha in high byte)
     */
    public static void send(ServerPlayerEntity recipient,
                            UUID playerId,
                            Identifier overlayId,
                            int remainingTicks,
                            boolean active,
                            int argbColor) {
        if (!ServerPlayNetworking.canSend(recipient, NetworkConstants.PROTECTION_OVERLAY_ID)) {
            return;
        }
        ServerPlayNetworking.send(
            recipient,
            new NetworkConstants.ProtectionOverlayPayload(playerId, overlayId, remainingTicks, active, argbColor)
        );
    }

    /**
     * Send a protection overlay update to all online players.
     *
     * @param server The Minecraft server
     * @param playerId The UUID of the protected player
     * @param overlayId Unique identifier for this overlay effect
     * @param remainingTicks How many ticks the overlay remains active
     * @param active Whether the overlay is active
     * @param argbColor ARGB color (alpha in high byte)
     */
    public static void broadcast(MinecraftServer server,
                                 UUID playerId,
                                 Identifier overlayId,
                                 int remainingTicks,
                                 boolean active,
                                 int argbColor) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            send(online, playerId, overlayId, remainingTicks, active, argbColor);
        }
    }

    /**
     * Send a respawn protection overlay to a specific player.
     * Uses the standard respawn protection preset.
     *
     * @param recipient The player who should see the overlay
     * @param protectedPlayerId The UUID of the player with respawn protection
     * @param durationTicks How long the protection lasts
     */
    public static void sendRespawnProtection(ServerPlayerEntity recipient,
                                              UUID protectedPlayerId,
                                              int durationTicks) {
        send(
            recipient,
            protectedPlayerId,
            ProtectionOverlayPresets.RESPAWN_PROTECTION.overlayId(),
            durationTicks,
            true,
            ProtectionOverlayPresets.RESPAWN_PROTECTION.argbColor()
        );
    }

    /**
     * Broadcast a respawn protection overlay to all online players.
     *
     * @param server The Minecraft server
     * @param protectedPlayerId The UUID of the player with respawn protection
     * @param durationTicks How long the protection lasts
     */
    public static void broadcastRespawnProtection(MinecraftServer server,
                                                   UUID protectedPlayerId,
                                                   int durationTicks) {
        broadcast(
            server,
            protectedPlayerId,
            ProtectionOverlayPresets.RESPAWN_PROTECTION.overlayId(),
            durationTicks,
            true,
            ProtectionOverlayPresets.RESPAWN_PROTECTION.argbColor()
        );
    }

    /**
     * Send a grace period overlay to a specific player.
     * Uses the standard grace period preset for subtle protection.
     *
     * @param recipient The player who should see the overlay
     * @param gracePeriodPlayerId The UUID of the player with grace period protection
     * @param durationTicks How long the grace period lasts
     */
    public static void sendGracePeriod(ServerPlayerEntity recipient,
                                        UUID gracePeriodPlayerId,
                                        int durationTicks) {
        send(
            recipient,
            gracePeriodPlayerId,
            ProtectionOverlayPresets.GRACE_PERIOD.overlayId(),
            durationTicks,
            true,
            ProtectionOverlayPresets.GRACE_PERIOD.argbColor()
        );
    }

    /**
     * Broadcast a grace period overlay to all online players.
     *
     * @param server The Minecraft server
     * @param gracePeriodPlayerId The UUID of the player with grace period protection
     * @param durationTicks How long the grace period lasts
     */
    public static void broadcastGracePeriod(MinecraftServer server,
                                             UUID gracePeriodPlayerId,
                                             int durationTicks) {
        broadcast(
            server,
            gracePeriodPlayerId,
            ProtectionOverlayPresets.GRACE_PERIOD.overlayId(),
            durationTicks,
            true,
            ProtectionOverlayPresets.GRACE_PERIOD.argbColor()
        );
    }

    /**
     * Deactivate a protection overlay for a specific player.
     *
     * @param recipient The player who should stop seeing the overlay
     * @param playerId The UUID of the protected player
     * @param overlayId The identifier of the overlay to deactivate
     */
    public static void clearOverlay(ServerPlayerEntity recipient,
                                    UUID playerId,
                                    Identifier overlayId) {
        send(recipient, playerId, overlayId, 0, false, 0);
    }

    /**
     * Deactivate a protection overlay for all online players.
     *
     * @param server The Minecraft server
     * @param playerId The UUID of the protected player
     * @param overlayId The identifier of the overlay to deactivate
     */
    public static void broadcastClearOverlay(MinecraftServer server,
                                              UUID playerId,
                                              Identifier overlayId) {
        broadcast(server, playerId, overlayId, 0, false, 0);
    }
}

