package dev.frost.miniverse.client.gui.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class UiPrimitives {
    private UiPrimitives() {
    }

    public static class UiPanel extends UiComponent {
        private String title = "";

        public UiPanel title(String title) {
            this.title = title;
            return this;
        }

        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
            UiRenderer.panel(context, this.bounds.x(), this.bounds.y(), this.bounds.width(), this.bounds.height());
            if (!this.title.isEmpty()) {
                context.drawText(textRenderer, Text.literal(this.title), this.bounds.x() + 10, this.bounds.y() + 8, UiTheme.TEXT, false);
            }
        }
    }

    public static class UiSection extends UiPanel {
    }

    public static class UiCard extends UiComponent {
        private final UiAnimation.Value hover = new UiAnimation.Value(0.0F);
        private String title = "";
        private String subtitle = "";
        private int accent = UiTheme.ACCENT;
        private Runnable action = () -> {
        };

        public UiCard content(String title, String subtitle, int accent, Runnable action) {
            this.title = title;
            this.subtitle = subtitle;
            this.accent = accent;
            this.action = action == null ? () -> {
            } : action;
            return this;
        }

        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
            this.hover.animateTo(this.bounds.contains(mouseX, mouseY) ? 1.0F : 0.0F, UiTheme.HOVER_MS);
            float progress = this.hover.get();
            UiRenderer.card(context, this.bounds.x(), this.bounds.y(), this.bounds.width(), this.bounds.height(), progress, this.accent);
            int iconSize = Math.min(36, this.bounds.height() - 18);
            context.fill(this.bounds.x() + 12, this.bounds.y() + 12, this.bounds.x() + 12 + iconSize, this.bounds.y() + 12 + iconSize, UiAnimation.alpha(this.accent, 0.20F + progress * 0.20F));
            UiRenderer.border(context, this.bounds.x() + 12, this.bounds.y() + 12, iconSize, iconSize, UiAnimation.alpha(this.accent, 0.75F));
            context.drawText(textRenderer, Text.literal(this.title), this.bounds.x() + 24 + iconSize, this.bounds.y() + 13, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(this.subtitle), this.bounds.x() + 24 + iconSize, this.bounds.y() + 28, UiTheme.TEXT_MUTED, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && this.bounds.contains(mouseX, mouseY)) {
                this.action.run();
                return true;
            }
            return false;
        }
    }

    public static class UiButton extends UiComponent {
        private final UiAnimation.Value hover = new UiAnimation.Value(0.0F);
        private String label;
        private Runnable action;
        private int accent = UiTheme.ACCENT;
        private boolean enabled = true;

        public UiButton(String label, Runnable action) {
            this.label = label;
            this.action = action == null ? () -> {
            } : action;
        }

        public UiButton accent(int accent) {
            this.accent = accent;
            return this;
        }

        public UiButton enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
            this.hover.animateTo(this.enabled && this.bounds.contains(mouseX, mouseY) ? 1.0F : 0.0F, UiTheme.HOVER_MS);
            float progress = this.hover.get();
            int fill = this.enabled ? UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(this.accent, 0.30F), progress) : 0x8010151D;
            int border = this.enabled ? UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, this.accent, progress) : 0x44333A44;
            UiRenderer.panel(context, this.bounds.x(), this.bounds.y(), this.bounds.width(), this.bounds.height(), fill, border);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(this.label), this.bounds.x() + this.bounds.width() / 2, this.bounds.y() + (this.bounds.height() - textRenderer.fontHeight) / 2 + 1, this.enabled ? UiTheme.TEXT : UiTheme.TEXT_DIM);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.enabled && button == 0 && this.bounds.contains(mouseX, mouseY)) {
                this.action.run();
                return true;
            }
            return false;
        }
    }

    public static class UiSidebar extends UiComponent {
        private final List<Item> items = new ArrayList<>();
        private int selected;

        public UiSidebar item(String icon, String label, Runnable action) {
            this.items.add(new Item(icon, label, action));
            return this;
        }

        @Override
        public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
            UiRenderer.panel(context, this.bounds.x(), this.bounds.y(), this.bounds.width(), this.bounds.height(), UiTheme.SIDEBAR, UiTheme.BORDER_SUBTLE);
            context.drawText(textRenderer, Text.literal("MINIVERSE"), this.bounds.x() + 12, this.bounds.y() + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Workspace"), this.bounds.x() + 12, this.bounds.y() + 24, UiTheme.TEXT_DIM, false);
            int y = this.bounds.y() + 48;
            for (int i = 0; i < this.items.size(); i++) {
                Item item = this.items.get(i);
                boolean hovered = mouseX >= this.bounds.x() + 7 && mouseX <= this.bounds.x() + this.bounds.width() - 7 && mouseY >= y && mouseY <= y + 24;
                int fill = i == this.selected ? 0x442F4154 : hovered ? 0x2AFFFFFF : 0x00000000;
                context.fill(this.bounds.x() + 7, y, this.bounds.x() + this.bounds.width() - 7, y + 24, fill);
                if (i == this.selected) {
                    context.fill(this.bounds.x() + 7, y, this.bounds.x() + 10, y + 24, UiTheme.ACCENT);
                }
                context.drawText(textRenderer, Text.literal(item.icon), this.bounds.x() + 16, y + 8, UiTheme.ACCENT, false);
                context.drawText(textRenderer, Text.literal(item.label), this.bounds.x() + 34, y + 8, UiTheme.TEXT_MUTED, false);
                y += 28;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = this.bounds.y() + 48;
            for (int i = 0; i < this.items.size(); i++) {
                if (button == 0 && mouseX >= this.bounds.x() + 7 && mouseX <= this.bounds.x() + this.bounds.width() - 7 && mouseY >= y && mouseY <= y + 24) {
                    this.selected = i;
                    if (this.items.get(i).action != null) {
                        this.items.get(i).action.run();
                    }
                    return true;
                }
                y += 28;
            }
            return false;
        }

        private record Item(String icon, String label, Runnable action) {
        }
    }

    public static class UiWorkspace extends UiPanel {
    }

    public static class UiToolbar extends UiPanel {
    }

    public static class UiColumn extends UiPanel {
    }

    public static class UiIconButton extends UiButton {
        public UiIconButton(String label, Runnable action) {
            super(label, action);
        }
    }

    public static class UiToggle extends UiButton {
        public UiToggle(String label, Runnable action) {
            super(label, action);
        }
    }

    public static class UiSlider extends UiComponent {
    }

    public static class UiList extends UiComponent {
    }

    public static class UiSettingRow extends UiComponent {
    }

    public static class UiDivider extends UiComponent {
    }

    public static class UiHeader extends UiComponent {
    }

    public static class UiTooltip extends UiComponent {
    }

    public static class UiScrollContainer extends UiComponent {
    }

    public static class UiModal extends UiPanel {
    }
}
