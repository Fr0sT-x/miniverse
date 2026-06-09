package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.map.ThumbnailManager;
import dev.frost.miniverse.client.gui.ui.UiComponent;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MapThumbnailGrid extends UiComponent {
    private static final int COLUMN_HEADER_HEIGHT = 22;

    private final String title;
    private final Consumer<String> onMapSelected;
    private int accentColor = 0xFF00AA66; // Default accent

    private List<SessionSnapshotData.MapSummary> maps = new ArrayList<>();
    private String selectedMapId = "";
    private int scrollOffset = 0;

    public MapThumbnailGrid(String title, Consumer<String> onMapSelected) {
        this.title = title;
        this.onMapSelected = onMapSelected;
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }

    public void setMaps(List<SessionSnapshotData.MapSummary> maps) {
        this.maps = new ArrayList<>(maps);
        int maxScroll = Math.max(0, this.maps.size() - this.getVisibleRows());
        this.scrollOffset = Math.clamp(this.scrollOffset, 0, maxScroll);
    }

    public void setSelectedMapId(String mapId) {
        this.selectedMapId = mapId;
    }

    private int getColumnCount() {
        if (bounds.width() <= 0) return 1;
        return Math.min(3, Math.max(1, (bounds.width() - 8) / 160));
    }

    private int getCardWidth(int columns) {
        int availableWidth = bounds.width() - 10; // 8px for scrollbar, 2px padding
        int gaps = (columns - 1) * 4;
        return Math.max(10, (availableWidth - gaps) / columns);
    }

    private int getMapRowHeight(int columns) {
        int cardWidth = getCardWidth(columns);
        int imgHeight = (int) (cardWidth * 9.0 / 16.0);
        return imgHeight + 36;
    }

    private int getVisibleRows() {
        if (bounds.height() <= 0) return 0;
        int columns = getColumnCount();
        int mapRowHeight = getMapRowHeight(columns);
        return Math.max(0, (bounds.height() - COLUMN_HEADER_HEIGHT - 8) / mapRowHeight);
    }

    @Override
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        UiRenderer.panel(context, bounds.x(), bounds.y(), bounds.width(), bounds.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(bounds.x() + 1, bounds.y() + 1, bounds.x() + bounds.width() - 1, bounds.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.fill(bounds.x() + 1, bounds.y() + 1, bounds.x() + bounds.width() - 1, bounds.y() + 3, accentColor);

        String displayTitle = maps.isEmpty() ? title : title + " (" + maps.size() + ")";
        context.drawText(textRenderer, Text.literal(displayTitle), bounds.x() + 8, bounds.y() + 7, UiTheme.TEXT, false);

        if (maps.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No maps available"), bounds.x() + 12, bounds.y() + COLUMN_HEADER_HEIGHT + 10, UiTheme.TEXT_MUTED, false);
            return;
        }

        int columns = getColumnCount();
        int cardWidth = getCardWidth(columns);
        int imgHeight = (int) (cardWidth * 9.0 / 16.0);
        int mapRowHeight = getMapRowHeight(columns);

        int totalRows = (int) Math.ceil((double) maps.size() / columns);
        int visibleRows = getVisibleRows();
        int rows = Math.min(totalRows - scrollOffset, visibleRows);

        for (int row = 0; row < rows; row++) {
            int rowY = bounds.y() + COLUMN_HEADER_HEIGHT + 4 + row * mapRowHeight;
            for (int col = 0; col < columns; col++) {
                int index = (scrollOffset + row) * columns + col;
                if (index < maps.size()) {
                    SessionSnapshotData.MapSummary m = maps.get(index);
                    boolean isSelected = m.id().equals(this.selectedMapId);
                    
                    int cardX = bounds.x() + 1 + col * (cardWidth + 4);

                    // Draw Background
                    int bg = isSelected ? 0x663E79B8 : 0x26222A34;
                    boolean hovered = !isSelected && mouseX >= cardX && mouseX <= cardX + cardWidth && mouseY >= rowY && mouseY < rowY + mapRowHeight;
                    if (hovered) {
                        bg = 0x44304052;
                    }
                    context.fill(cardX, rowY, cardX + cardWidth, rowY + mapRowHeight - 2, bg);

                    // Draw Thumbnail
                    net.minecraft.util.Identifier thumb = ThumbnailManager.getThumbnail(m);
                    context.drawTexture(thumb, cardX + 2, rowY + 2, 0, 0, cardWidth - 4, imgHeight, cardWidth - 4, imgHeight);

                    // Draw Text
                    context.drawText(textRenderer, Text.literal(m.name()), cardX + 7, rowY + imgHeight + 8, isSelected ? UiTheme.TEXT : UiTheme.TEXT_MUTED, false);

                    if (isSelected) {
                        context.drawBorder(cardX + 2, rowY + 2, cardWidth - 4, imgHeight, accentColor);
                    }
                }
            }
        }

        drawScrollbar(context, bounds, totalRows, visibleRows, scrollOffset);
    }

    private void drawScrollbar(DrawContext context, UiLayout.Rect rect, int totalRows, int visibleRows, int scrollOffset) {
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || maps.isEmpty() || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        
        // Don't click header
        if (mouseY < bounds.y() + COLUMN_HEADER_HEIGHT + 4) {
            return false;
        }

        int columns = getColumnCount();
        int cardWidth = getCardWidth(columns);
        int mapRowHeight = getMapRowHeight(columns);
        
        int row = (int) ((mouseY - (bounds.y() + COLUMN_HEADER_HEIGHT + 4)) / mapRowHeight);
        int relX = (int) (mouseX - (bounds.x() + 1));
        int col = relX / (cardWidth + 4);

        if (col >= 0 && col < columns) {
            int index = (scrollOffset + row) * columns + col;
            if (index >= 0 && index < maps.size()) {
                SessionSnapshotData.MapSummary m = maps.get(index);
                onMapSelected.accept(m.id());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        int columns = getColumnCount();
        int visibleRows = getVisibleRows();
        int totalRows = (int) Math.ceil((double) maps.size() / columns);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        if (maxScroll > 0) {
            this.scrollOffset = Math.clamp(this.scrollOffset - (int) Math.signum(amount), 0, maxScroll);
            return true;
        }
        return false;
    }
}
