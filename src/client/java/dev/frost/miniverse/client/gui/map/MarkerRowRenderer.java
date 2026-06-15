package dev.frost.miniverse.client.gui.map;

import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

final class MarkerRowRenderer {
    static void renderActionButtons(DrawContext context, TextRenderer textRenderer, SessionSnapshotData.EditorMarker marker, int rowX, int rowY, int rowWidth, boolean isHidden) {
        String toggleLabel = isHidden ? "Show" : "Hide";
        renderSmallButton(context, textRenderer, rowX + rowWidth - 302, rowY + 10, 68, toggleLabel);
        renderSmallButton(context, textRenderer, rowX + rowWidth - 226, rowY + 10, 68, "Rename");
        renderSmallButton(context, textRenderer, rowX + rowWidth - 150, rowY + 10, 68, "Teleport");
        renderSmallButton(context, textRenderer, rowX + rowWidth - 74, rowY + 10, 60, "Delete");
    }

    static void renderSmallButton(DrawContext context, TextRenderer textRenderer, int x, int y, int width, String label) {
        UiRenderer.panel(context, x, y, width, 20, UiTheme.PANEL_RAISED, UiTheme.BORDER_SUBTLE);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), x + width / 2, y + 6, UiTheme.TEXT_MUTED);
    }
}
