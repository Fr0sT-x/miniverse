package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.ui.AbstractPopupScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import net.minecraft.util.Formatting;
import java.util.function.BiConsumer;

public class TeamNamePopupScreen extends AbstractPopupScreen {
    private final BiConsumer<String, Formatting> onConfirm;
    private TextFieldWidget nameField;
    private Formatting selectedColor = null;

    private static final Formatting[] COLORS = new Formatting[]{
        Formatting.DARK_RED, Formatting.RED, Formatting.GOLD, Formatting.YELLOW,
        Formatting.DARK_GREEN, Formatting.GREEN, Formatting.AQUA, Formatting.DARK_AQUA,
        Formatting.DARK_BLUE, Formatting.BLUE, Formatting.LIGHT_PURPLE, Formatting.DARK_PURPLE,
        Formatting.WHITE, Formatting.GRAY, Formatting.DARK_GRAY, Formatting.BLACK
    };

    public TeamNamePopupScreen(Screen parent, BiConsumer<String, Formatting> onConfirm) {
        super(parent, Text.literal("New Team"), 200, 140);
        this.onConfirm = onConfirm;
    }

    @Override
    protected void initPopup() {
        this.nameField = new TextFieldWidget(this.textRenderer, this.popupX + 20, this.popupY + 30, this.popupWidth - 40, 20, Text.literal("Team Name"));
        this.nameField.setMaxLength(32);
        this.addDrawableChild(this.nameField);
        this.setInitialFocus(this.nameField);

        int startX = this.popupX + 20;
        int startY = this.popupY + 60;
        for (int i = 0; i < COLORS.length; i++) {
            Formatting color = COLORS[i];
            int col = i % 8;
            int row = i / 8;
            int x = startX + col * 20;
            int y = startY + row * 20;
            
            this.addDrawableChild(ButtonWidget.builder(Text.literal(""), btn -> {
                this.selectedColor = color;
                if (this.nameField.getText().isBlank()) {
                    String name = color.getName().replace("_", " ");
                    name = name.substring(0, 1).toUpperCase() + name.substring(1) + " Team";
                    this.nameField.setText(name);
                }
            }).dimensions(x, y, 16, 16).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(color.getName()))).build());
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), btn -> {
            if (!this.nameField.getText().isBlank() && this.selectedColor != null) {
                this.onConfirm.accept(this.nameField.getText().trim(), this.selectedColor);
                this.close();
            }
        }).dimensions(this.popupX + 20, this.popupY + 110, 75, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> {
            this.close();
        }).dimensions(this.popupX + 105, this.popupY + 110, 75, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int startX = this.popupX + 20;
        int startY = this.popupY + 60;
        for (int i = 0; i < COLORS.length; i++) {
            Formatting color = COLORS[i];
            int col = i % 8;
            int row = i / 8;
            int x = startX + col * 20;
            int y = startY + row * 20;
            Integer colorValue = color.getColorValue();
            int rgb = colorValue != null ? colorValue : 0xFFFFFF;
            context.fill(x + 2, y + 2, x + 14, y + 14, 0xFF000000 | rgb);
            if (this.selectedColor == color) {
                context.drawBorder(x, y, 16, 16, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!this.nameField.getText().isBlank() && this.selectedColor != null) {
                this.onConfirm.accept(this.nameField.getText().trim(), this.selectedColor);
                this.close();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
