package dev.frost.miniverse.client.gui.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

public abstract class UiComponent {
    protected UiLayout.Rect bounds = new UiLayout.Rect(0, 0, 0, 0);

    public void setBounds(UiLayout.Rect bounds) {
        this.bounds = bounds;
    }

    public UiLayout.Rect bounds() {
        return this.bounds;
    }

    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }
}
