package dev.frost.miniverse.client.gui.selector;

import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

import java.util.*;

public class CollapsibleListWidget<T> implements Drawable, Element, Selectable {
    private final RegistrySelectorScreen<T> screen;
    private final RegistryContentProvider<T> provider;
    private final int x, y, width, height;
    private double scrollAmount;
    
    private List<T> entries = new ArrayList<>();
    private final Map<String, List<T>> groupedEntries = new LinkedHashMap<>();
    private final Map<String, UiAnimation.Value> animations = new HashMap<>();

    public CollapsibleListWidget(RegistrySelectorScreen<T> screen, int x, int y, int width, int height, RegistryContentProvider<T> provider) {
        this.screen = screen;
        this.provider = provider;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void updateEntries(List<T> entries) {
        this.entries = entries;
        this.groupedEntries.clear();
        for (T entry : entries) {
            String cat = this.provider.getPrimaryCategory(entry);
            if (cat == null) cat = "Uncategorised";
            this.groupedEntries.computeIfAbsent(cat, k -> new ArrayList<>()).add(entry);
        }
        for (String cat : this.groupedEntries.keySet()) {
            this.animations.putIfAbsent(cat, new UiAnimation.Value(this.screen.getState().getCollapsedCategories().contains(cat) ? 0.0F : 1.0F));
        }
    }

    private int getMaxScroll() {
        int totalHeight = 0;
        for (Map.Entry<String, List<T>> group : this.groupedEntries.entrySet()) {
            totalHeight += 24; // Header height
            int childHeight = group.getValue().size() * 32; // Item height
            totalHeight += Math.round(childHeight * this.animations.get(group.getKey()).get());
        }
        return Math.max(0, totalHeight - this.height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        for (Map.Entry<String, UiAnimation.Value> anim : this.animations.entrySet()) {
            boolean collapsed = this.screen.getState().getCollapsedCategories().contains(anim.getKey());
            anim.getValue().animateTo(collapsed ? 0.0F : 1.0F, UiTheme.TRANSITION_MS, UiAnimation::easeInOutQuad);
        }

        this.scrollAmount = Math.max(0, Math.min(this.scrollAmount, this.getMaxScroll()));

        context.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);
        
        int currentY = this.y - (int) this.scrollAmount;
        int index = 0;

        for (Map.Entry<String, List<T>> group : this.groupedEntries.entrySet()) {
            String cat = group.getKey();
            int headerY = currentY;
            boolean hoveredHeader = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= headerY && mouseY < headerY + 24 && mouseY >= this.y && mouseY < this.y + this.height;
            
            int fill = hoveredHeader ? 0x1E2D3B4A : 0x14222C37;
            context.fill(this.x, headerY, this.x + this.width, headerY + 24, fill);
            context.fill(this.x, headerY, this.x + this.width, headerY + 1, UiTheme.BORDER_SUBTLE);
            context.fill(this.x, headerY + 23, this.x + this.width, headerY + 24, UiTheme.BORDER_SUBTLE);
            
            float progress = this.animations.get(cat).get();
            // Draw arrow
            context.getMatrices().push();
            context.getMatrices().translate(this.x + 12, headerY + 12, 0);
            context.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F * (1.0F - progress)));
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal("▼"), -4, -4, 0xAAAAAA, false);
            context.getMatrices().pop();
            
            context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(cat), this.x + 24, headerY + 8, UiTheme.TEXT, false);
            
            currentY += 24;

            int childHeight = group.getValue().size() * 32;
            int visibleHeight = Math.round(childHeight * progress);
            
            if (visibleHeight > 0) {
                int scissorTop = Math.max(this.y, currentY);
                int scissorBottom = Math.min(this.y + this.height, currentY + visibleHeight);
                if (scissorBottom > scissorTop) {
                    context.enableScissor(this.x, scissorTop, this.x + this.width, scissorBottom);
                    int itemY = currentY;
                    for (T item : group.getValue()) {
                        boolean isHovered = mouseX >= this.x && mouseX < this.x + this.width && mouseY >= itemY && mouseY < itemY + 32 && mouseY >= this.y && mouseY < this.y + this.height;
                        boolean isSelected = this.screen.getSelectedEntries().contains(item);
                        if (!this.provider.renderCustomEntry(context, item, index, this.x, itemY, this.width, 32, isHovered, isSelected, mouseX, mouseY)) {
                            context.fill(this.x, itemY, this.x + this.width, itemY + 32, isSelected ? 0x8033AA33 : (isHovered ? 0x60FFFFFF : 0x40000000));
                            context.drawText(MinecraftClient.getInstance().textRenderer, this.provider.getDisplayName(item), this.x + 4, itemY + 4, 0xFFFFFF, false);
                        }
                        itemY += 32;
                        index++;
                    }
                    context.disableScissor(); // disable inner scissor
                    context.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height); // re-enable outer scissor
                }
            }
            currentY += visibleHeight;
        }

        context.disableScissor();

        // Draw scrollbar
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0) {
            int scrollbarX = this.x + this.width - 6;
            int scrollbarHeight = Math.max(20, (int) ((this.height / (float) (this.height + maxScroll)) * this.height));
            int scrollbarY = this.y + (int) ((this.scrollAmount / (float) maxScroll) * (this.height - scrollbarHeight));
            context.fill(scrollbarX, this.y, scrollbarX + 6, this.y + this.height, 0x80000000);
            context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < this.y || mouseY >= this.y + this.height) return false;
        
        int currentY = this.y - (int) this.scrollAmount;
        for (Map.Entry<String, List<T>> group : this.groupedEntries.entrySet()) {
            String cat = group.getKey();
            int headerY = currentY;
            if (mouseY >= headerY && mouseY < headerY + 24) {
                if (button == 0) {
                    this.screen.getState().toggleCollapsedCategory(cat);
                    return true;
                }
            }
            currentY += 24;
            
            float progress = this.animations.get(cat).get();
            int childHeight = group.getValue().size() * 32;
            int visibleHeight = Math.round(childHeight * progress);
            
            if (mouseY >= currentY && mouseY < currentY + visibleHeight) {
                int itemIndex = (int) ((mouseY - currentY) / 32);
                if (itemIndex >= 0 && itemIndex < group.getValue().size()) {
                    T item = group.getValue().get(itemIndex);
                    int itemY = currentY + itemIndex * 32;
                    if (this.provider.handleCustomClick(item, mouseX, mouseY, button, this.x, itemY, this.width, 32)) {
                        return true;
                    }
                    if (button == 0) {
                        this.screen.toggleSelection(item);
                        return true;
                    }
                }
            }
            currentY += visibleHeight;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height) {
            this.scrollAmount = Math.max(0, Math.min(this.scrollAmount - verticalAmount * 16.0, this.getMaxScroll()));
            return true;
        }
        return false;
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        int maxScroll = this.getMaxScroll();
        if (maxScroll > 0 && button == 0 && mouseX >= this.x + this.width - 6 && mouseX <= this.x + this.width) {
            int scrollbarHeight = Math.max(20, (int) ((this.height / (float) (this.height + maxScroll)) * this.height));
            double scrollRatio = deltaY / (this.height - scrollbarHeight);
            this.scrollAmount = Math.max(0, Math.min(this.scrollAmount + scrollRatio * maxScroll, maxScroll));
            return true;
        }
        return false;
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() { return false; }

    @Override
    public SelectionType getType() { return SelectionType.NONE; }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {}
}
