package dev.frost.miniverse.minigame.core.protection;

import net.minecraft.util.Identifier;

/**
 * Standard protection overlay presets for common gamemode scenarios.
 *
 * Provides predefined overlays with consistent styling, colors, and alphas
 * for different types of player protection (respawn invincibility, grace periods, etc).
 *
 * All presets use the translucent rendering system and integrate with the
 * Fabric rendering pipeline for proper depth testing and occlusion.
 */
public final class ProtectionOverlayPresets {
    private ProtectionOverlayPresets() {
    }

    /**
     * Respawn protection overlay - high visibility protective aura.
     *
     * Used when a player has just respawned and has temporary invincibility.
     * Creates a visible defensive barrier effect with moderate transparency.
     *
     * - Color: Golden protective glow
     * - Alpha: 0xE6 (~90% opacity) for clear visibility while preserving player skin
     * - Identifier: miniverse:respawn_protection
     */
    public static final ProtectionOverlayPreset RESPAWN_PROTECTION = new ProtectionOverlayPreset(
        Identifier.of("miniverse", "respawn_protection"),
        0xE6FFDD00  // ARGB: 90% opacity, gold/yellow (R=255, G=221, B=0)
    );

    /**
     * Grace period invincibility overlay - subtle defensive shimmer.
     *
     * Used for brief invincibility windows or grace periods before gameplay begins.
     * Creates a soft shimmer with light transparency to remain unobtrusive.
     *
     * - Color: Golden shimmer
     * - Alpha: 0xE6 (~90% opacity) for a visible but translucent effect
     * - Identifier: miniverse:grace_period
     */
    public static final ProtectionOverlayPreset GRACE_PERIOD = new ProtectionOverlayPreset(
        Identifier.of("miniverse", "grace_period"),
        0xE6FFDD00  // ARGB: 90% opacity, gold/yellow
    );

    /**
     * Alternative respawn indicator - warmer protective tone.
     *
     * Similar to RESPAWN_PROTECTION but with warmer yellow tones
     * for gamemodes that prefer a different visual aesthetic.
     *
     * - Color: Yellow/gold protective glow
     * - Alpha: 0xE6 (~90% opacity)
     * - Identifier: miniverse:respawn_protection_warm
     */
    public static final ProtectionOverlayPreset RESPAWN_PROTECTION_WARM = new ProtectionOverlayPreset(
        Identifier.of("miniverse", "respawn_protection_warm"),
        0xE6FFDD00  // ARGB: 90% opacity, gold/yellow (R=255, G=221, B=0)
    );

    /**
     * Spectator indicator overlay - subtle presence marker.
     *
     * Used to mark spectators with a faint shimmer to distinguish them
     * from regular invisible players.
     *
     * - Color: Purple/lavender spectator marker
     * - Alpha: 0x20 (~12% opacity) for minimal visibility impact
     * - Identifier: miniverse:spectator
     */
    public static final ProtectionOverlayPreset SPECTATOR = new ProtectionOverlayPreset(
        Identifier.of("miniverse", "spectator"),
        0x20CC88FF  // ARGB: 12% opacity, purple
    );

    /**
     * Represents a reusable protection overlay configuration.
     *
     * @param overlayId Unique identifier for this overlay type
     * @param argbColor ARGB color value (alpha in high byte)
     */
    public record ProtectionOverlayPreset(Identifier overlayId, int argbColor) {
    }
}

