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
        super(client, width, height, top, itemHeight);
        this.screen = screen;
        this.columns = columns;
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
        return this.columns * SPACING;
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
            int currentX = x + (entryWidth - this.grid.getRowWidth()) / 2;
            RegistrySelectorState state = this.grid.screen.getState();
            Set<T> selected = this.grid.screen.getSelectedEntries();
            
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
