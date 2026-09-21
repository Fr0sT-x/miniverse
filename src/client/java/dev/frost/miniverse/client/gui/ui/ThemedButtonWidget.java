package dev.frost.miniverse.client.gui.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Supplier;

public class ThemedButtonWidget extends ButtonWidget {
    private int accent = UiTheme.ACCENT_BLUE;
    private int relativeX = 0;
    private int relativeY = 0;
    private Supplier<String> tooltipSupplier;

    public ThemedButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    public ThemedButtonWidget(int x, int y, int width, int height, Text message, int accent, Runnable action) {
        super(x, y, width, height, message, b -> action.run(), DEFAULT_NARRATION_SUPPLIER);
        this.accent = accent;
    }

    public ThemedButtonWidget setAccent(int accent) {
        this.accent = accent;
        return this;
    }

    public int getAccent() {
        return this.accent;
    }

    public ThemedButtonWidget setRelative(int relativeX, int relativeY) {
        this.relativeX = relativeX;
        this.relativeY = relativeY;
        return this;
    }

    public int getRelativeX() {
        return this.relativeX;
    }

    public int getRelativeY() {
        return this.relativeY;
    }

    public void updatePosition(int baseX, int baseY) {
        this.setX(baseX + this.relativeX);
        this.setY(baseY + this.relativeY);
    }

    public ThemedButtonWidget setTooltipSupplier(Supplier<String> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    public Supplier<String> getTooltipSupplier() {
        return this.tooltipSupplier;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        boolean hovered = this.isHovered();

        if (!this.active) {
            // Visually obvious greyed-out / disabled state
            int fill = 0x4010151E;
            int border = 0x502C3848;
            UiRenderer.panel(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(), fill, border);
            int textColor = 0xFF586676; // Muted stone grey
            context.drawCenteredTextWithShadow(textRenderer, this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, textColor);
            return;
        }

        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(this.accent, 0.34F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, this.accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, this.getX(), this.getY(), this.getWidth(), this.getHeight(), fill, border);
        int textColor = hovered ? UiTheme.TEXT : 0xFFE0E6ED;
        context.drawCenteredTextWithShadow(textRenderer, this.getMessage(), this.getX() + this.getWidth() / 2, this.getY() + (this.getHeight() - 8) / 2, textColor);
    }
}
