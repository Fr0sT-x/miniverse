package dev.frost.miniverse.client.protection;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side protection overlay manager for translucent shield/aura effects.
 *
 * Manages overlay state for players with active protection (respawn invincibility, grace periods, etc.)
 * and integrates with the player entity rendering pipeline via feature renderers.
 *
 * The overlay uses proper translucent rendering with depth testing enabled, so it:
 * - Appears as a soft shimmer/aura over the player model
 * - Preserves full visibility of the player skin underneath
 * - Automatically hides behind walls through GPU depth testing
 * - Avoids flickering and manual visibility calculations
 */
public final class ProtectionOverlayClient {
    private static final Map<UUID, Map<Identifier, ProtectionOverlayState>> overlaysByPlayer = new ConcurrentHashMap<>();
    private static long clientTicks;

    private ProtectionOverlayClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.PROTECTION_OVERLAY_ID, (payload, context) ->
            context.client().execute(() -> {
                if (payload.active()) {
                    activateOverlay(payload.playerId(), payload.overlayId(), payload.remainingTicks(), payload.argbColor());
                } else {
                    deactivateOverlay(payload.playerId(), payload.overlayId());
                }
            })
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                clearAll();
                return;
            }
            clientTicks++;
            pruneExpiredOverlays();
        });

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, registrationHelper, context) -> {
            if (renderer instanceof PlayerEntityRenderer<?> playerRenderer) {
                registrationHelper.register(new ProtectionOverlayFeatureRenderer(playerRenderer));
            }
        });
    }

    /**
     * Get the active overlay state for a player.
     *
     * Returns the overlay with the highest alpha value if multiple are active,
     * preferring longer-lasting overlays as a tiebreaker.
     */
    public static ProtectionOverlayState getOverlayForPlayer(UUID playerId) {
        Map<Identifier, ProtectionOverlayState> overlays = overlaysByPlayer.get(playerId);
        if (overlays == null || overlays.isEmpty()) {
            return null;
        }

        Optional<ProtectionOverlayState> selected = overlays.values().stream()
            .filter(overlay -> overlay.expiresAt() > clientTicks)
            .max(Comparator
                .comparingInt(ProtectionOverlayState::alpha)
                .thenComparingLong(ProtectionOverlayState::expiresAt));
        return selected.orElse(null);
    }

    /**
     * Activate or update a protection overlay for a player.
     *
     * @param playerId The UUID of the protected player
     * @param overlayId A unique identifier for this overlay effect (e.g., "respawn_protection", "grace_period")
     * @param remainingTicks How many client ticks this overlay remains active
     * @param argbColor The ARGB color for the overlay (includes alpha in high byte)
     */
    public static void activateOverlay(UUID playerId, Identifier overlayId, int remainingTicks, int argbColor) {
        if (remainingTicks <= 0) {
            deactivateOverlay(playerId, overlayId);
            return;
        }

        overlaysByPlayer
            .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
            .put(overlayId, new ProtectionOverlayState(overlayId, clientTicks + remainingTicks, argbColor));
    }

    /**
     * Deactivate a specific protection overlay for a player.
     */
    public static void deactivateOverlay(UUID playerId, Identifier overlayId) {
        overlaysByPlayer.computeIfPresent(playerId, (uuid, overlays) -> {
            overlays.remove(overlayId);
            if (overlays.isEmpty()) {
                return null;
            }
            return overlays;
        });
    }

    /**
     * Clear all overlays (usually called on world unload).
     */
    public static void clearAll() {
        overlaysByPlayer.clear();
        clientTicks = 0L;
    }

    private static void pruneExpiredOverlays() {
        overlaysByPlayer.entrySet().removeIf(entry -> {
            Map<Identifier, ProtectionOverlayState> overlays = entry.getValue();
            overlays.entrySet().removeIf(overlay -> overlay.getValue().expiresAt() <= clientTicks);
            return overlays.isEmpty();
        });
    }

    /**
     * Represents the state of a protection overlay on a player.
     *
     * @param overlayId Unique identifier for this overlay effect
     * @param expiresAt Client tick when this overlay expires
     * @param argbColor ARGB color (alpha in high byte)
     */
    public record ProtectionOverlayState(Identifier overlayId, long expiresAt, int argbColor) {
        private int alpha() {
            return (this.argbColor >>> 24) & 0xFF;
        }
    }
}

