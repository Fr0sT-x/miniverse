package dev.frost.miniverse.client.gui.map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class SidebarWidget extends ClickableWidget {

    private final List<ClickableWidget> children = new ArrayList<>();
    private final boolean isLeft;

    public SidebarWidget(int x, int y, int width, int height, boolean isLeft) {
        super(x, y, width, height, Text.empty());
        this.isLeft = isLeft;
    }

    public void addWidget(ClickableWidget widget) {
        this.children.add(widget);
    }

    public void clearChildren() {
        this.children.clear();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw dark translucent background
        context.fill(getX(), getY(), getX() + width, getY() + height, 0xAA000000);

        // Draw a subtle border
        int borderColor = 0xFF555555;
        if (isLeft) {
            context.fill(getX() + width - 1, getY(), getX() + width, getY() + height, borderColor);
        } else {
            context.fill(getX(), getY(), getX() + 1, getY() + height, borderColor);
        }

        // Render children
        for (ClickableWidget child : children) {
            child.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isHovered()) return false;
        
        for (ClickableWidget child : children) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true; // Consume clicks on the sidebar background
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isHovered()) return false;

        for (ClickableWidget child : children) {
            if (child.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        for (ClickableWidget child : children) {
            if (child.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isHovered()) return false;

        for (ClickableWidget child : children) {
            if (child.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        // No-op
    }
}
