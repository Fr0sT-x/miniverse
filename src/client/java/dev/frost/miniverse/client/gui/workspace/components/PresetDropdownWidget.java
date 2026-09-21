package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class PresetDropdownWidget {
    private int x;
    private int y;
    private int width;
    private int height;
    private String selected = "Default";
    private final List<String> items = new ArrayList<>();
    private boolean open = false;
    private Consumer<String> onSelect;

    public PresetDropdownWidget(int x, int y, int width, int height, Consumer<String> onSelect) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.onSelect = onSelect;
        this.items.add("Default");
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setItems(List<String> presets, String activePreset) {
        this.items.clear();
        this.items.add("Default");
        if (presets != null) {
            for (String p : presets) {
                if (!p.equalsIgnoreCase("Default") && !this.items.contains(p)) {
                    this.items.add(p);
                }
            }
        }
        if (activePreset != null && !activePreset.isBlank()) {
            this.selected = activePreset;
        } else if (!this.items.contains(this.selected)) {
            this.selected = "Default";
        }
    }

    public String getSelected() {
        return this.selected;
    }

    public void setSelected(String selected) {
        this.selected = (selected != null && !selected.isBlank()) ? selected : "Default";
    }

    public boolean isOpen() {
        return this.open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean contains(double mouseX, double mouseY) {
        if (mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height) {
            return true;
        }
        if (this.open) {
            int menuHeight = this.items.size() * 20 + 4;
            return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y + this.height && mouseY <= this.y + this.height + menuHeight;
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        // Click on header button
        if (mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height) {
            this.open = !this.open;
            return true;
        }

        // Click on menu item
        if (this.open) {
            int menuY = this.y + this.height + 2;
            for (int i = 0; i < this.items.size(); i++) {
                int itemY = menuY + 2 + (i * 20);
                if (mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= itemY && mouseY < itemY + 20) {
                    String chosen = this.items.get(i);
                    this.selected = chosen;
                    this.open = false;
                    if (this.onSelect != null) {
                        this.onSelect.accept(chosen);
                    }
                    return true;
                }
            }
            // Click outside closes menu
            this.open = false;
            return true;
        }

        return false;
    }

    public void renderButton(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        boolean hovered = mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.height;
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(UiTheme.ACCENT_BLUE, 0.25F), hovered || this.open ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, UiTheme.ACCENT_BLUE, hovered || this.open ? 1.0F : 0.0F);
        UiRenderer.panel(context, this.x, this.y, this.width, this.height, fill, border);

        String displayText = "Preset: " + this.selected;
        int maxTextWidth = this.width - 24;
        String trimmed = textRenderer.trimToWidth(displayText, maxTextWidth);
        if (trimmed.length() < displayText.length()) {
            trimmed = trimmed.substring(0, Math.max(0, trimmed.length() - 2)) + "..";
        }
        context.drawText(textRenderer, Text.literal(trimmed), this.x + 8, this.y + 7, UiTheme.TEXT, false);

        String arrow = this.open ? "▲" : "▼";
        context.drawText(textRenderer, Text.literal(arrow), this.x + this.width - 14, this.y + 7, UiTheme.TEXT_MUTED, false);
    }

    public void renderDropdown(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (!this.open) return;

        int menuY = this.y + this.height + 2;
        int menuHeight = this.items.size() * 20 + 4;

        // Flush all background rendering (including previously queued text) so it appears strictly UNDER the dropdown
        context.draw();

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400.0F);

        // Draw 100% solid opaque shadow + panel
        context.fill(this.x + 2, menuY + 2, this.x + this.width + 2, menuY + menuHeight + 2, 0xC0000000);
        context.fill(this.x, menuY, this.x + this.width, menuY + menuHeight, 0xFF0D131C);
        UiRenderer.border(context, this.x, menuY, this.width, menuHeight, UiTheme.ACCENT_BLUE);

        for (int i = 0; i < this.items.size(); i++) {
            String item = this.items.get(i);
            int itemY = menuY + 2 + (i * 20);
            boolean isItemHovered = mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= itemY && mouseY < itemY + 20;
            boolean isSelected = item.equalsIgnoreCase(this.selected);

            if (isItemHovered && isSelected) {
                context.fill(this.x + 1, itemY, this.x + this.width - 1, itemY + 20, 0xFF223750);
            } else if (isItemHovered) {
                context.fill(this.x + 1, itemY, this.x + this.width - 1, itemY + 20, 0xFF1D2C40);
            } else if (isSelected) {
                context.fill(this.x + 1, itemY, this.x + this.width - 1, itemY + 20, 0xFF162334);
            }

            int textColor = isSelected ? UiTheme.ACCENT_BLUE : (isItemHovered ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            String prefix = item.equalsIgnoreCase("Default") ? "★ " : "• ";
            String label = textRenderer.trimToWidth(prefix + item, this.width - 16);
            context.drawText(textRenderer, Text.literal(label), this.x + 8, itemY + 6, textColor, false);
        }

        context.draw();
        context.getMatrices().pop();
    }
}
