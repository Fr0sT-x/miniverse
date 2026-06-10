package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiComponent;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public abstract class TeamSelectionGrid extends UiComponent {
    protected static final int ROW_HEIGHT = 20;
    protected static final int COLUMN_HEADER_HEIGHT = 22;
    protected static final int COLUMN_GAP = 12;

    protected final Map<String, UiAnimation.Value> rowHovers = new HashMap<>();

    protected SessionSnapshotData.RosterEntry draggedEntry;
    protected String draggedFromId;
    protected double dragX;
    protected double dragY;
    
    public SessionSnapshotData.RosterEntry getDraggedEntry() {
        return this.draggedEntry;
    }

    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect bounds, int mouseX, int mouseY, float delta) {
        if (this.draggedEntry != null) {
            int x = (int) this.dragX + 10;
            int y = (int) this.dragY + 10;
            int width = Math.max(104, textRenderer.getWidth(this.draggedEntry.name()) + 28);
            UiRenderer.panel(context, x, y, width, 22, UiTheme.PANEL_RAISED, UiTheme.ACCENT);
            context.drawText(textRenderer, Text.literal(this.draggedEntry.name()), x + 12, y + 7, UiTheme.TEXT, false);
        }
    }

    protected void renderPlayerRow(DrawContext context, TextRenderer textRenderer, int rowY, int rectX, int rectWidth, int mouseX, int mouseY, SessionSnapshotData.RosterEntry entry, boolean selected, int accent) {
        boolean hovered = mouseX >= rectX + 1 && mouseX <= rectX + rectWidth - 1 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
        UiAnimation.Value hover = this.rowHovers.computeIfAbsent(entry.uuid(), ignored -> new UiAnimation.Value(0.0F));
        hover.animateTo(hovered ? 1.0F : 0.0F, UiTheme.HOVER_MS);
        float progress = hover.get();
        int background = selected ? UiAnimation.lerpColor(0xAA2F5D94, 0xCC3E79B8, progress) : UiAnimation.lerpColor(0x26222A34, 0x66304052, progress);
        context.fill(rectX + 1, rowY, rectX + rectWidth - 1, rowY + ROW_HEIGHT - 2, background);
        context.fill(rectX + 6, rowY + 4, rectX + 10, rowY + ROW_HEIGHT - 5, UiAnimation.lerpColor(accent, UiTheme.ACCENT, progress * 0.35F));
        context.drawText(textRenderer, Text.literal(entry.name()), rectX + 16, rowY + 6, selected ? UiTheme.TEXT : UiAnimation.lerpColor(UiTheme.TEXT_MUTED, UiTheme.TEXT, progress), false);
    }

    protected void drawScrollbar(DrawContext context, UiLayout.Rect rect, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows || visibleRows <= 0) {
            return;
        }
        int trackX = rect.x() + rect.width() - 7;
        int trackY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int trackHeight = rect.height() - COLUMN_HEADER_HEIGHT - 8;
        int thumbHeight = Math.max(14, (int) ((trackHeight * (double) visibleRows) / totalRows));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbY = trackY + (int) (((trackHeight - thumbHeight) * (double) scrollOffset) / maxScroll);
        context.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0x66101822);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, UiTheme.BORDER_STRONG);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && this.draggedEntry != null) {
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        return false;
    }
    
    public abstract void refreshRoster();
}
