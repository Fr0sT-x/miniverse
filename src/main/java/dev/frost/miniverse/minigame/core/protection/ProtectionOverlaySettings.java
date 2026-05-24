package dev.frost.miniverse.minigame.core.protection;

public record ProtectionOverlaySettings(
    ProtectionOverlayStyle style,
    ProtectionOverlayRenderMode renderMode,
    int glowColor,
    int outlineColor,
    float alpha,
    float intensity
) {
    public static final ProtectionOverlaySettings DEFAULT = new ProtectionOverlaySettings(
        ProtectionOverlayStyle.VANILLA_GLOW,
        ProtectionOverlayRenderMode.DEPTH_TESTED,
        0xFFFFDD00,
        0xFFFFFFFF,
        0.8F,
        1.0F
    );

    public ProtectionOverlaySettings {
        style = style == null ? ProtectionOverlayStyle.VANILLA_GLOW : style;
        renderMode = renderMode == null ? ProtectionOverlayRenderMode.DEPTH_TESTED : renderMode;
        alpha = clamp(alpha, 0.0F, 1.0F);
        intensity = clamp(intensity, 0.0F, 3.0F);
    }

    public static ProtectionOverlaySettings legacy(int argbColor) {
        int rgb = 0xFF000000 | (argbColor & 0x00FFFFFF);
        float alpha = ((argbColor >>> 24) & 0xFF) / 255.0F;
        return DEFAULT.withGlowColor(rgb).withAlpha(alpha);
    }

    public ProtectionOverlaySettings withStyle(ProtectionOverlayStyle style) {
        return new ProtectionOverlaySettings(style, this.renderMode, this.glowColor, this.outlineColor, this.alpha, this.intensity);
    }

    public ProtectionOverlaySettings withRenderMode(ProtectionOverlayRenderMode renderMode) {
        return new ProtectionOverlaySettings(this.style, renderMode, this.glowColor, this.outlineColor, this.alpha, this.intensity);
    }

    public ProtectionOverlaySettings withGlowColor(int glowColor) {
        return new ProtectionOverlaySettings(this.style, this.renderMode, 0xFF000000 | glowColor, this.outlineColor, this.alpha, this.intensity);
    }

    public ProtectionOverlaySettings withOutlineColor(int outlineColor) {
        return new ProtectionOverlaySettings(this.style, this.renderMode, this.glowColor, 0xFF000000 | outlineColor, this.alpha, this.intensity);
    }

    public ProtectionOverlaySettings withAlpha(float alpha) {
        return new ProtectionOverlaySettings(this.style, this.renderMode, this.glowColor, this.outlineColor, alpha, this.intensity);
    }

    public ProtectionOverlaySettings withIntensity(float intensity) {
        return new ProtectionOverlaySettings(this.style, this.renderMode, this.glowColor, this.outlineColor, this.alpha, intensity);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
