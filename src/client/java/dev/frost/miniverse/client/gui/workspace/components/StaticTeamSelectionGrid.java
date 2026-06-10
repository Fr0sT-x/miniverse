package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class StaticTeamSelectionGrid extends TeamSelectionGrid {
    private final List<ColumnState> columns = new ArrayList<>();
    private String selectedPlayerUuid = "";
    private BiConsumer<String, String> onDropCallback; // Entry UUID, Target Column ID

    public void addColumn(String id, String title, int accentColor, boolean isAvailablePool) {
        this.columns.add(new ColumnState(id, title, accentColor, isAvailablePool));
    }

    public List<String> getColumnIds() {
        return this.columns.stream().map(c -> c.id).collect(java.util.stream.Collectors.toList());
    }

    public void setOnDropCallback(BiConsumer<String, String> callback) {
        this.onDropCallback = callback;
    }

    public void clear() {
        for (ColumnState column : this.columns) {
            column.members.clear();
        }
        this.selectedPlayerUuid = "";
    }

    public void addMember(String columnId, SessionSnapshotData.RosterEntry entry) {
        ColumnState column = this.getColumn(columnId);
        if (column != null && !column.isAvailablePool) {
            column.members.add(entry);
        }
    }

    public void removeMember(String uuid) {
        for (ColumnState column : this.columns) {
            column.members.removeIf(entry -> entry.uuid().equals(uuid));
        }
    }

    public List<SessionSnapshotData.RosterEntry> getMembers(String columnId) {
        ColumnState column = this.getColumn(columnId);
        if (column == null) return new ArrayList<>();

        if (column.isAvailablePool) {
            List<SessionSnapshotData.RosterEntry> available = new ArrayList<>();
            for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
                if (!this.isAssigned(entry.uuid())) {
                    available.add(entry);
                }
            }
            return available;
        }
        return column.members;
    }

    private boolean isAssigned(String uuid) {
        for (ColumnState column : this.columns) {
            if (!column.isAvailablePool) {
                if (column.members.stream().anyMatch(entry -> entry.uuid().equals(uuid))) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void refreshRoster() {
        for (ColumnState column : this.columns) {
            if (!column.isAvailablePool) {
                column.members.removeIf(entry -> !this.isOnline(entry.uuid()));
            }
        }
    }

    private boolean isOnline(String uuid) {
        return SessionSnapshotData.roster().stream().anyMatch(entry -> entry.uuid().equals(uuid));
    }

    private ColumnState getColumn(String id) {
        for (ColumnState column : this.columns) {
            if (column.id.equals(id)) {
                return column;
            }
        }
        return null;
    }

    @Override
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.columns.isEmpty()) return;
        int columnWidth = (this.bounds.width() - COLUMN_GAP * (this.columns.size() - 1)) / this.columns.size();
        
        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            UiLayout.Rect rect = new UiLayout.Rect(this.bounds.x() + i * (columnWidth + COLUMN_GAP), this.bounds.y(), columnWidth, this.bounds.height());
            this.renderColumn(context, textRenderer, rect, column, mouseX, mouseY, this.draggedEntry != null && rect.contains(mouseX, mouseY));
        }
    }

    private void renderColumn(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, ColumnState column, int mouseX, int mouseY, boolean dropTarget) {
        List<SessionSnapshotData.RosterEntry> entries = this.getMembers(column.id);
        int accent = 0xFF000000 | column.accentColor;
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), dropTarget ? UiTheme.CARD_HOVER : UiTheme.CARD, dropTarget ? accent : UiTheme.BORDER_SUBTLE);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + 3, accent);
        context.drawText(textRenderer, Text.literal(column.title + " (" + entries.size() + ")"), rect.x() + 8, rect.y() + 7, UiTheme.TEXT, false);

        int visibleRows = column.visibleRows(rect.height());
        int rows = Math.min(entries.size() - column.scrollOffset, visibleRows);
        int listTop = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        
        for (int row = 0; row < rows; row++) {
            SessionSnapshotData.RosterEntry entry = entries.get(column.scrollOffset + row);
            if (this.draggedEntry != null && this.draggedEntry.uuid().equals(entry.uuid())) {
                continue;
            }
            int rowY = listTop + row * ROW_HEIGHT;
            boolean selected = entry.uuid().equals(this.selectedPlayerUuid);
            this.renderPlayerRow(context, textRenderer, rowY, rect.x(), rect.width(), mouseX, mouseY, entry, selected, accent);
        }
        this.drawScrollbar(context, rect, entries.size(), visibleRows, column.scrollOffset);
    }

    private UiLayout.Rect columnRect(int index) {
        int columnWidth = (this.bounds.width() - COLUMN_GAP * (this.columns.size() - 1)) / this.columns.size();
        return new UiLayout.Rect(this.bounds.x() + index * (columnWidth + COLUMN_GAP), this.bounds.y(), columnWidth, this.bounds.height());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            UiLayout.Rect rect = this.columnRect(i);
            if (!rect.contains(mouseX, mouseY)) {
                continue;
            }
            int row = column.rowAt(mouseY, rect);
            List<SessionSnapshotData.RosterEntry> entries = this.getMembers(column.id);
            int index = column.scrollOffset + row;
            if (row >= 0 && index >= 0 && index < entries.size()) {
                this.selectedPlayerUuid = entries.get(index).uuid();
                this.draggedEntry = entries.get(index);
                this.draggedFromId = column.id;
                this.dragX = mouseX;
                this.dragY = mouseY;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || this.draggedEntry == null) {
            return false;
        }
        String targetId = this.dragTarget(mouseX, mouseY);
        if (targetId != null && !targetId.equals(this.draggedFromId)) {
            this.moveEntryTo(this.draggedEntry, targetId);
            if (this.onDropCallback != null) {
                this.onDropCallback.accept(this.draggedEntry.uuid(), targetId);
            }
        }
        this.draggedEntry = null;
        this.draggedFromId = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            UiLayout.Rect rect = this.columnRect(i);
            if (rect.contains(mouseX, mouseY)) {
                int maxScroll = Math.max(0, this.getMembers(column.id).size() - column.visibleRows(rect.height()));
                column.scrollOffset = Math.clamp(column.scrollOffset - (int) Math.signum(amount), 0, maxScroll);
                return maxScroll > 0;
            }
        }
        return false;
    }

    private void moveEntryTo(SessionSnapshotData.RosterEntry entry, String targetId) {
        this.removeMember(entry.uuid());
        ColumnState target = this.getColumn(targetId);
        if (target != null && !target.isAvailablePool) {
            target.members.add(entry);
        }
        this.selectedPlayerUuid = entry.uuid();
    }

    private String dragTarget(double mouseX, double mouseY) {
        for (int i = 0; i < this.columns.size(); i++) {
            UiLayout.Rect rect = this.columnRect(i);
            if (rect.contains(mouseX, mouseY)) {
                return this.columns.get(i).id;
            }
        }
        return this.draggedFromId;
    }

    private static final class ColumnState {
        private final String id;
        private final String title;
        private final int accentColor;
        private final boolean isAvailablePool;
        private final List<SessionSnapshotData.RosterEntry> members = new ArrayList<>();
        private int scrollOffset;

        private ColumnState(String id, String title, int accentColor, boolean isAvailablePool) {
            this.id = id;
            this.title = title;
            this.accentColor = accentColor;
            this.isAvailablePool = isAvailablePool;
        }

        private int visibleRows(int height) {
            return Math.max(0, (height - COLUMN_HEADER_HEIGHT - 8) / ROW_HEIGHT);
        }

        private int rowAt(double mouseY, UiLayout.Rect rect) {
            int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
            if (mouseY < rowStartY || mouseY > rect.y() + rect.height()) {
                return -1;
            }
            return (int) ((mouseY - rowStartY) / ROW_HEIGHT);
        }
    }
}
