package dev.frost.miniverse.client.gui.ui;

import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public abstract class AbstractPopupScreen extends Screen {
    protected final Screen parent;
    protected final Text titleText;
    
    protected int popupWidth;
    protected int popupHeight;
    protected int popupX;
    protected int popupY;

    protected AbstractPopupScreen(Screen parent, Text title, int width, int height) {
        super(title);
        this.parent = parent;
        this.titleText = title;
        this.popupWidth = width;
        this.popupHeight = height;
    }

    @Override
    protected void init() {
        super.init();
        this.popupX = (this.width - this.popupWidth) / 2;
        this.popupY = (this.height - this.popupHeight) / 2;
        this.initPopup();
    }

    /**
     * Override to add custom widgets to the popup.
     */
    protected abstract void initPopup();

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.parent != null) {
            this.parent.render(context, -1, -1, delta); // Render parent without hover
        } else {
            this.renderBackground(context, mouseX, mouseY, delta);
        }

        // Darken background
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Draw popup panel
        context.fill(this.popupX, this.popupY, this.popupX + this.popupWidth, this.popupY + this.popupHeight, UiTheme.PANEL_SOFT);
        context.drawBorder(this.popupX, this.popupY, this.popupWidth, this.popupHeight, UiTheme.BORDER_STRONG);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, this.titleText, this.width / 2, this.popupY + 10, UiTheme.TEXT);

        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
    
    @Override
    public boolean shouldPause() {
        return false;
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false; // Force interaction
    }
}
