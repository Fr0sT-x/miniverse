package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.ui.AbstractPopupScreen;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

public class SavePresetPopupScreen extends AbstractPopupScreen {
    private final List<String> existingPresetNames;
    private final Consumer<String> onSave;
    private final String initialName;

    private TextFieldWidget nameField;
    private ButtonWidget saveButton;
    private boolean isOverwrite;

    public SavePresetPopupScreen(Screen parent, String currentName, List<String> existingPresetNames, Consumer<String> onSave) {
        super(parent, Text.literal("Save Preset"), 260, 130);
        this.existingPresetNames = existingPresetNames != null ? existingPresetNames : List.of();
        this.onSave = onSave;
        this.initialName = (currentName != null && !currentName.equalsIgnoreCase("Default")) ? currentName : "";
    }

    @Override
    protected void initPopup() {
        this.nameField = new TextFieldWidget(this.textRenderer, this.popupX + 20, this.popupY + 34, this.popupWidth - 40, 20, Text.literal("Preset Name"));
        this.nameField.setMaxLength(48);
        this.nameField.setText(this.initialName);
        this.nameField.setChangedListener(this::onTextChanged);
        this.addDrawableChild(this.nameField);
        this.setInitialFocus(this.nameField);

        this.saveButton = ButtonWidget.builder(Text.literal("Save"), btn -> submit())
            .dimensions(this.popupX + 20, this.popupY + 92, 105, 22)
            .build();
        this.addDrawableChild(this.saveButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> this.close())
            .dimensions(this.popupX + 135, this.popupY + 92, 105, 22)
            .build());

        onTextChanged(this.nameField.getText());
    }

    private void onTextChanged(String text) {
        String trimmed = text.trim();
        boolean exists = false;
        for (String existing : this.existingPresetNames) {
            if (existing.equalsIgnoreCase(trimmed)) {
                exists = true;
                break;
            }
        }
        this.isOverwrite = exists;

        boolean canSave = !trimmed.isBlank() && !trimmed.equalsIgnoreCase("Default");
        if (this.saveButton != null) {
            this.saveButton.active = canSave;
            if (this.isOverwrite) {
                this.saveButton.setMessage(Text.literal("Overwrite"));
            } else {
                this.saveButton.setMessage(Text.literal("Save"));
            }
        }
    }

    private void submit() {
        String trimmed = this.nameField.getText().trim();
        if (trimmed.isBlank() || trimmed.equalsIgnoreCase("Default")) {
            return;
        }
        if (this.onSave != null) {
            this.onSave.accept(trimmed);
        }
        this.close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        String trimmed = this.nameField.getText().trim();
        if (trimmed.equalsIgnoreCase("Default")) {
            context.drawText(this.textRenderer, Text.literal("Cannot overwrite built-in 'Default' preset."), this.popupX + 20, this.popupY + 62, 0xFFFF5555, false);
        } else if (this.isOverwrite) {
            context.drawText(this.textRenderer, Text.literal("Warning: Preset already exists, will overwrite."), this.popupX + 20, this.popupY + 62, UiTheme.ACCENT, false);
        } else if (trimmed.isBlank()) {
            context.drawText(this.textRenderer, Text.literal("Enter a preset name."), this.popupX + 20, this.popupY + 62, UiTheme.TEXT_MUTED, false);
        } else {
            context.drawText(this.textRenderer, Text.literal("Will be saved to server presets folder."), this.popupX + 20, this.popupY + 62, UiTheme.TEXT_DIM, false);
        }
    }
}
