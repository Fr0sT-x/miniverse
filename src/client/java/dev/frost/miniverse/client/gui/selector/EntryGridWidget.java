package dev.frost.miniverse.client.gui.selector;

import dev.frost.miniverse.client.gui.ui.UiRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class EntryGridWidget<T> extends AlwaysSelectedEntryListWidget<EntryGridWidget.RowEntry<T>> {
    private final RegistrySelectorScreen<T> screen;
    private final RegistryContentProvider<T> provider;
    private final int columns;
    private static final int ITEM_SIZE = 24;
    private static final int PADDING = 4;
    private static final int SPACING = ITEM_SIZE + PADDING;

    public EntryGridWidget(RegistrySelectorScreen<T> screen, MinecraftClient client, int width, int height, int top, int bottom, int itemHeight, int columns, RegistryContentProvider<T> provider) {
        super(client, width, height, top, provider.isListView() ? 32 : itemHeight);
        this.screen = screen;
        this.columns = provider.isListView() ? 1 : columns;
        this.provider = provider;
        this.setRenderHeader(false, 0);
    }

    public void updateEntries(List<T> entries) {
        this.clearEntries();
        List<T> currentRow = new ArrayList<>(this.columns);
        for (T entry : entries) {
            currentRow.add(entry);
            if (currentRow.size() == this.columns) {
                this.addEntry(new RowEntry<>(this, new ArrayList<>(currentRow)));
                currentRow.clear();
            }
        }
        if (!currentRow.isEmpty()) {
            this.addEntry(new RowEntry<>(this, currentRow));
        }
        this.setScrollAmount(this.screen.getState().getScrollPosition());
    }



    @Override
    public int getRowWidth() {
        return this.provider.isListView() ? this.width - 20 : this.columns * SPACING;
    }

    public static class RowEntry<T> extends AlwaysSelectedEntryListWidget.Entry<RowEntry<T>> {
        private final EntryGridWidget<T> grid;
        private final List<T> items;

        public RowEntry(EntryGridWidget<T> grid, List<T> items) {
            this.grid = grid;
            this.items = items;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            RegistrySelectorState state = this.grid.screen.getState();
            Set<T> selected = this.grid.screen.getSelectedEntries();

            if (this.grid.provider.isListView()) {
                if (this.items.isEmpty()) return;
                T item = this.items.get(0);
                boolean isSelected = selected.contains(item);
                boolean isHovered = mouseX >= x && mouseX < x + entryWidth && mouseY >= y && mouseY < y + entryHeight;
                if (!this.grid.provider.renderCustomEntry(context, item, x, y, entryWidth, entryHeight, isHovered, isSelected, mouseX, mouseY)) {
                    // Fallback if provider returns false
                    context.fill(x, y, x + entryWidth, y + entryHeight, isSelected ? 0x8033AA33 : (isHovered ? 0x60FFFFFF : 0x40000000));
                    context.drawText(this.grid.client.textRenderer, this.grid.provider.getDisplayName(item), x + 4, y + 4, 0xFFFFFF, false);
                }
                return;
            }

            int currentX = x + (entryWidth - this.grid.getRowWidth()) / 2;
            for (T item : this.items) {
                boolean isHovered = mouseX >= currentX && mouseX < currentX + ITEM_SIZE && mouseY >= y && mouseY < y + ITEM_SIZE;
                boolean isSelected = selected.contains(item);
                boolean isFavorited = state.getFavorites().contains(this.grid.provider.getId(item));

                // Draw background
                context.fill(currentX, y, currentX + ITEM_SIZE, y + ITEM_SIZE, isSelected ? 0x8033AA33 : (isHovered ? 0x60FFFFFF : 0x40000000));
                
                // Draw Icon
                this.grid.provider.renderIcon(context, item, currentX + 4, y + 4);

                // Draw Favorite Star (top right)
                if (isFavorited) {
                    context.drawText(this.grid.client.textRenderer, Text.literal("★"), currentX + ITEM_SIZE - 8, y + 2, 0xFFFF00, false);
                }

                if (isHovered) {
                    this.grid.screen.setHoverTooltip(this.grid.provider.getTooltip(item));
                }

                currentX += SPACING;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.grid.provider.isListView()) {
                if (this.items.isEmpty()) return false;
                T item = this.items.get(0);
                
                // Need to compute y and height which are missing from mouseClicked signature. 
                // We'll estimate or just let provider handle the exact click if it tracks Y bounds, but Entry doesn't store its own Y.
                // We'll just pass 0s to x, y and let provider use mouseX/mouseY directly if it can.
                // Wait, if it's ListView, the whole row is clickable:
                if (this.grid.provider.handleCustomClick(item, mouseX, mouseY, button, 0, 0, 0, 0)) {
                    return true;
                }
                if (button == 0) {
                    this.grid.screen.toggleSelection(item);
                    return true;
                }
                return false;
            }

            if (button == 0) {
                int index = getHoveredIndex(mouseX);
                if (index != -1) {
                    T item = this.items.get(index);
                    this.grid.screen.toggleSelection(item);
                    return true;
                }
            } else if (button == 1) { // Right click to favorite
                int index = getHoveredIndex(mouseX);
                if (index != -1) {
                    T item = this.items.get(index);
                    this.grid.screen.getState().toggleFavorite(this.grid.provider.getId(item));
                    this.grid.screen.saveData();
                    return true;
                }
            }
            return false;
        }

        private int getHoveredIndex(double mouseX) {
            int startX = this.grid.getX() + (this.grid.getWidth() - this.grid.getRowWidth()) / 2;
            for (int i = 0; i < this.items.size(); i++) {
                int itemX = startX + i * SPACING;
                if (mouseX >= itemX && mouseX < itemX + ITEM_SIZE) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public Text getNarration() {
            return Text.empty();
        }
    }
}
