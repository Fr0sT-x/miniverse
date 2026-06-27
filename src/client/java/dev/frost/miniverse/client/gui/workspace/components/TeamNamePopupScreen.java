package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.ui.AbstractPopupScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class TeamNamePopupScreen extends AbstractPopupScreen {
    private final Consumer<String> onConfirm;
    private TextFieldWidget nameField;

    public TeamNamePopupScreen(Screen parent, Consumer<String> onConfirm) {
        super(parent, Text.literal("New Team"), 200, 100);
        this.onConfirm = onConfirm;
    }

    @Override
    protected void initPopup() {
        this.nameField = new TextFieldWidget(this.textRenderer, this.popupX + 20, this.popupY + 30, this.popupWidth - 40, 20, Text.literal("Team Name"));
        this.nameField.setMaxLength(32);
        this.addDrawableChild(this.nameField);
        this.setInitialFocus(this.nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), btn -> {
            if (!this.nameField.getText().isBlank()) {
                this.onConfirm.accept(this.nameField.getText().trim());
                this.close();
            }
        }).dimensions(this.popupX + 20, this.popupY + 65, 75, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> {
            this.close();
        }).dimensions(this.popupX + 105, this.popupY + 65, 75, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!this.nameField.getText().isBlank()) {
                this.onConfirm.accept(this.nameField.getText().trim());
                this.close();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
