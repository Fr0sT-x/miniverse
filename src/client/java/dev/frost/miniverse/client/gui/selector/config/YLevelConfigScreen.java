package dev.frost.miniverse.client.gui.selector.config;

import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class YLevelConfigScreen<T> extends Screen {
    private final RegistrySelectorScreen<T> parent;
    private final Identifier templateId;
    
    private boolean isAbove = true;
    private TextFieldWidget yLevelField;

    public YLevelConfigScreen(RegistrySelectorScreen<T> parent, Identifier templateId) {
        super(Text.literal("Configure Y-Level Objective"));
        this.parent = parent;
        this.templateId = templateId;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        ButtonWidget toggleDirectionButton = ButtonWidget.builder(Text.literal(isAbove ? "Condition: Above Y-Level" : "Condition: Below Y-Level"), b -> {
            this.isAbove = !this.isAbove;
            b.setMessage(Text.literal(this.isAbove ? "Condition: Above Y-Level" : "Condition: Below Y-Level"));
        }).dimensions(centerX - 100, centerY - 40, 200, 20).build();
        this.addDrawableChild(toggleDirectionButton);

        this.yLevelField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY - 10, 200, 20, Text.literal("Y Level"));
        this.yLevelField.setText("0");
        this.addDrawableChild(this.yLevelField);

        ButtonWidget saveButton = ButtonWidget.builder(Text.literal("Save & Add"), b -> saveAndClose())
            .dimensions(centerX - 100, centerY + 20, 95, 20).build();
        this.addDrawableChild(saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.literal("Cancel"), b -> this.client.setScreen(this.parent))
            .dimensions(centerX + 5, centerY + 20, 95, 20).build();
        this.addDrawableChild(cancelButton);
    }

    private void saveAndClose() {
        int y;
        try {
            y = Integer.parseInt(this.yLevelField.getText());
        } catch (NumberFormatException e) {
            y = 0; // Default fallback
        }

        String dir = this.isAbove ? "above" : "below";
        Identifier dynamicId = Identifier.of("miniverse", "dynamic/y_level/" + dir + "/" + y);

        // Resolve the dynamic objective natively via the manager to get the generated object
        T resolved = (T) dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager.get(this.client.getServer(), dynamicId);
        
        if (resolved != null) {
            this.parent.addDynamicEntry(resolved);
        }

        this.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "Enter Y-Level (e.g. 64, -50):", this.width / 2 - 100, this.height / 2 - 25, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
