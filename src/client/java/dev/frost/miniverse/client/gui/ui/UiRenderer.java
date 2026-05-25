package dev.frost.miniverse.client.gui.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class UiRenderer {
    private UiRenderer() {
    }

    public static void workspace(DrawContext context, int width, int height, float time) {
        boolean inWorld = MinecraftClient.getInstance().world != null && UiPreferences.worldBackdropEnabled();
        if (!inWorld) {
            context.fill(0, 0, width, height, UiTheme.BACKGROUND);
        }
        int horizon = Math.max(1, height / 2);
        context.fill(0, 0, width, horizon, inWorld ? 0xA00A0F17 : 0xB00A0F17);
        context.fill(0, horizon, width, height, inWorld ? 0xB806080C : 0xD006080C);
        int grid = 24;
        int offset = (int) (time * 9.0F) % grid;
        for (int x = -offset; x < width; x += grid) {
            context.fill(x, 0, x + 1, height, inWorld ? 0x28FFFFFF : UiTheme.BACKGROUND_GRID);
        }
        for (int y = offset; y < height; y += grid) {
            context.fill(0, y, width, y + 1, inWorld ? 0x28FFFFFF : UiTheme.BACKGROUND_GRID);
        }
        context.fill(0, 0, width, height, inWorld ? 0x76090C12 : UiTheme.WORKSPACE_SHADE);
        context.fill(0, 0, width, 30, inWorld ? 0x52000000 : 0x40000000);
        context.fill(0, height - 42, width, height, inWorld ? 0x86000000 : 0x72000000);
        context.fill(0, 0, 18, height, 0x42000000);
        context.fill(width - 18, 0, width, height, 0x42000000);
    }

    public static void panel(DrawContext context, int x, int y, int width, int height) {
        panel(context, x, y, width, height, UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
    }

    public static void panel(DrawContext context, int x, int y, int width, int height, int fill, int border) {
        context.fill(x + 3, y + 4, x + width + 3, y + height + 4, 0x40000000);
        context.fill(x, y, x + width, y + height, fill);
        border(context, x, y, width, height, border);
        context.fill(x + 1, y + 1, x + width - 1, y + 2, 0x25FFFFFF);
    }

    public static void card(DrawContext context, int x, int y, int width, int height, float hover, int accent) {
        int fill = UiAnimation.lerpColor(UiTheme.CARD, UiTheme.CARD_HOVER, hover);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hover);
        context.fill(x + 2, y + 3, x + width + 2, y + height + 3, UiAnimation.alpha(accent, 0.08F + hover * 0.10F));
        context.fill(x, y, x + width, y + height, fill);
        border(context, x, y, width, height, border);
        context.fill(x, y, x + 3, y + height, UiAnimation.alpha(accent, 0.65F + hover * 0.25F));
        context.fill(x + 1, y + 1, x + width - 1, y + 2, 0x2AFFFFFF);
    }

    public static void border(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y, x + 1, y + height, color);
        context.fill(x + width - 1, y, x + width, y + height, color);
    }

    public static void divider(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, UiTheme.BORDER_SUBTLE);
    }

    public static void label(TextRenderer textRenderer, DrawContext context, String text, int x, int y, int color) {
        context.drawText(textRenderer, Text.literal(text), x, y, color, false);
    }
}
