package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiPreferences;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class AppearanceWorkspaceView implements WorkspaceView {
    private UiLayout.Rect worldBackdropToggle = new UiLayout.Rect(0, 0, 0, 0);

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        UiLayout.Rect panel = workspace.inset(4);
        this.worldBackdropToggle = new UiLayout.Rect(panel.x() + 24, panel.y() + 92, 260, 28);
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect panel = workspace.inset(4);
        UiRenderer.panel(context, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(panel.x() + 1, panel.y() + 1, panel.x() + panel.width() - 1, panel.y() + 40, 0x701B2634);
        context.drawText(textRenderer, Text.literal("Appearance"), panel.x() + 14, panel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal("Client-side workspace presentation options."), panel.x() + 14, panel.y() + 28, UiTheme.TEXT_DIM, false);

        UiRenderer.panel(context, panel.x() + 14, panel.y() + 68, Math.min(420, panel.width() - 28), 92, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(panel.x() + 14, panel.y() + 68, panel.x() + 17, panel.y() + 160, UiTheme.ACCENT_BLUE);
        context.drawText(textRenderer, Text.literal("Workspace Background"), panel.x() + 26, panel.y() + 80, UiTheme.ACCENT_BLUE, false);
        this.renderToggle(context, textRenderer, this.worldBackdropToggle, "World backdrop", UiPreferences.worldBackdropEnabled(), this.worldBackdropToggle.contains(mouseX, mouseY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.worldBackdropToggle.contains(mouseX, mouseY)) {
            UiPreferences.setWorldBackdropEnabled(!UiPreferences.worldBackdropEnabled());
            return true;
        }
        return false;
    }

    @Override
    public String title() {
        return "Appearance";
    }

    @Override
    public String subtitle() {
        return "Workspace visuals";
    }

    private void renderToggle(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, boolean enabled, boolean hovered) {
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, 0x44364B63, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, hovered ? UiTheme.ACCENT_BLUE : UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal(label), rect.x() + 10, rect.y() + 10, UiTheme.TEXT, false);
        int trackX = rect.x() + rect.width() - 54;
        int trackY = rect.y() + 8;
        int trackColor = enabled ? 0xFF1E5F3A : 0xFF4C1D24;
        int knobX = enabled ? trackX + 25 : trackX + 3;
        context.fill(trackX, trackY, trackX + 44, trackY + 14, trackColor);
        UiRenderer.border(context, trackX, trackY, 44, 14, enabled ? UiTheme.ACCENT_GREEN : UiTheme.ACCENT_RED);
        context.fill(knobX, trackY + 2, knobX + 12, trackY + 12, UiTheme.TEXT);
    }
}
