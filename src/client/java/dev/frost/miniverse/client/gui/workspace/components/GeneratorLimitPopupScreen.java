package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.ui.AbstractPopupScreen;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

import java.util.function.Consumer;

public class GeneratorLimitPopupScreen extends AbstractPopupScreen {
    private TextFieldWidget inputField;
    private final Consumer<String> onConfirm;

    public GeneratorLimitPopupScreen(Screen parent, Consumer<String> onConfirm) {
        super(parent, Text.literal("Set Generator Limits"), 200, 100);
        this.onConfirm = onConfirm;
    }

    @Override
    protected void initPopup() {
        this.inputField = new TextFieldWidget(this.textRenderer, this.popupX + 10, this.popupY + 30, 180, 20, Text.literal("Limit"));
        this.inputField.setMaxLength(10);
        this.inputField.setText("64");
        this.addDrawableChild(this.inputField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Confirm"), button -> {
            this.onConfirm.accept(this.inputField.getText());
            this.client.setScreen(this.parent);
        }).dimensions(this.popupX + 10, this.popupY + 65, 85, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(this.popupX + 105, this.popupY + 65, 85, 20).build());
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, "Enter max resource limit:", this.popupX + 10, this.popupY + 15, 0xFFFFFF, false);
    }
}
