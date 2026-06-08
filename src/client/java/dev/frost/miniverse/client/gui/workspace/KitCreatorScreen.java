package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.kit.Kit;
import dev.frost.miniverse.minigame.core.kit.KitRegistry;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.stream.Collectors;

public class KitCreatorScreen extends Screen {
    private final Screen parent;
    private final Kit editingKit;

    private TextFieldWidget idField;
    private TextFieldWidget nameField;
    private TextFieldWidget categoriesField;
    private ButtonWidget saveButton;
    private Text warningText = null;

    public KitCreatorScreen(Screen parent, Kit editingKit) {
        super(Text.literal(editingKit == null ? "Create New Kit" : "Edit Kit"));
        this.parent = parent;
        this.editingKit = editingKit;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int y = this.height / 2 - 80;

        this.idField = new TextFieldWidget(this.textRenderer, centerX - 100, y, 200, 20, Text.literal("Kit ID"));
        this.idField.setSuggestion("e.g. nodebuff");
        this.idField.setChangedListener(text -> {
            this.idField.setSuggestion(text.isEmpty() ? "e.g. nodebuff" : "");
            this.validateId(text);
        });
        this.addDrawableChild(this.idField);

        this.nameField = new TextFieldWidget(this.textRenderer, centerX - 100, y + 35, 200, 20, Text.literal("Display Name"));
        this.nameField.setSuggestion("e.g. No Debuff");
        this.nameField.setChangedListener(text -> this.nameField.setSuggestion(text.isEmpty() ? "e.g. No Debuff" : ""));
        this.addDrawableChild(this.nameField);

        this.categoriesField = new TextFieldWidget(this.textRenderer, centerX - 100, y + 70, 200, 20, Text.literal("Categories (comma separated)"));
        this.categoriesField.setSuggestion("e.g. nodebuff, small");
        this.categoriesField.setChangedListener(text -> this.categoriesField.setSuggestion(text.isEmpty() ? "e.g. nodebuff, small" : ""));
        this.addDrawableChild(this.categoriesField);

        if (this.editingKit != null) {
            this.idField.setText(this.editingKit.getId().getPath());
            this.idField.setSuggestion("");
            this.idField.setEditable(false);
            this.nameField.setText(this.editingKit.getDisplayName().getString());
            this.nameField.setSuggestion("");
            this.categoriesField.setText(String.join(", ", this.editingKit.getCategories()));
            this.categoriesField.setSuggestion("");

            ButtonWidget loadButton = ButtonWidget.builder(Text.literal("Load Kit into Inventory"), b -> {
                ClientPlayNetworking.send(new NetworkConstants.LoadKitIntoInventoryPayload(this.editingKit.getId().toString()));
                this.client.setScreen(null); // Close to let them edit inventory
            }).dimensions(centerX - 100, y + 105, 200, 20).build();
            this.addDrawableChild(loadButton);
        }

        this.saveButton = ButtonWidget.builder(Text.literal(this.editingKit == null ? "Create from Current Inventory" : "Save Overwrite"), b -> {
            ClientPlayNetworking.send(new NetworkConstants.CreateKitPayload(
                this.idField.getText().trim().toLowerCase(Locale.ROOT),
                this.nameField.getText().trim(),
                this.categoriesField.getText().trim()
            ));
            this.client.setScreen(this.parent);
        }).dimensions(centerX - 100, y + 140, 200, 20).build();
        this.addDrawableChild(this.saveButton);

        ButtonWidget cancelButton = ButtonWidget.builder(Text.literal("Cancel"), b -> this.client.setScreen(this.parent))
            .dimensions(centerX - 100, y + 165, 200, 20)
            .build();
        this.addDrawableChild(cancelButton);

        validateId(this.idField.getText());
    }

    private void validateId(String text) {
        if (this.editingKit != null) {
            this.saveButton.active = true;
            this.warningText = null;
            return;
        }

        String id = text.trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            this.saveButton.active = false;
            this.warningText = null;
            return;
        }

        Identifier identifier = Identifier.tryParse("miniverse:" + id);
        if (identifier == null) {
            this.saveButton.active = false;
            this.warningText = Text.literal("Invalid ID format.").copy().withColor(UiTheme.ACCENT_RED);
            return;
        }

        if (KitRegistry.get(identifier).isPresent()) {
            this.saveButton.active = false;
            this.warningText = Text.literal("Error: Kit ID already exists!").copy().withColor(UiTheme.ACCENT_RED);
        } else {
            this.saveButton.active = true;
            this.warningText = null;
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        this.parent.render(context, -1, -1, delta);
        context.fill(0, 0, this.width, this.height, 0xCC000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        int centerX = this.width / 2;
        int y = this.height / 2 - 80;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, y - 25, 0xFFFFFF);
        
        context.drawTextWithShadow(this.textRenderer, Text.literal("ID"), centerX - 100, y - 10, 0xAAAAAA);
        int idLabelWidth = this.textRenderer.getWidth("ID");
        context.drawTextWithShadow(this.textRenderer, Text.literal("[i]"), centerX - 100 + idLabelWidth + 5, y - 10, UiTheme.ACCENT_BLUE);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Display Name"), centerX - 100, y + 25, 0xAAAAAA);
        int nameLabelWidth = this.textRenderer.getWidth("Display Name");
        context.drawTextWithShadow(this.textRenderer, Text.literal("[i]"), centerX - 100 + nameLabelWidth + 5, y + 25, UiTheme.ACCENT_BLUE);

        context.drawTextWithShadow(this.textRenderer, Text.literal("Categories"), centerX - 100, y + 60, 0xAAAAAA);
        int catLabelWidth = this.textRenderer.getWidth("Categories");
        context.drawTextWithShadow(this.textRenderer, Text.literal("[i]"), centerX - 100 + catLabelWidth + 5, y + 60, UiTheme.ACCENT_BLUE);

        if (this.warningText != null) {
            context.drawTextWithShadow(this.textRenderer, this.warningText, centerX - 100, y + 93, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);

        int iconWidth = this.textRenderer.getWidth("[i]");
        if (mouseX >= centerX - 100 + idLabelWidth + 5 && mouseX <= centerX - 100 + idLabelWidth + 5 + iconWidth && mouseY >= y - 10 && mouseY <= y - 10 + 9) {
            context.drawTooltip(this.textRenderer, Text.literal("The unique identifier for the kit (e.g. 'nodebuff', 'sumo')."), mouseX, mouseY);
        } else if (mouseX >= centerX - 100 + nameLabelWidth + 5 && mouseX <= centerX - 100 + nameLabelWidth + 5 + iconWidth && mouseY >= y + 25 && mouseY <= y + 25 + 9) {
            context.drawTooltip(this.textRenderer, Text.literal("The formatted name displayed to players (e.g. 'No Debuff')."), mouseX, mouseY);
        } else if (mouseX >= centerX - 100 + catLabelWidth + 5 && mouseX <= centerX - 100 + catLabelWidth + 5 + iconWidth && mouseY >= y + 60 && mouseY <= y + 60 + 9) {
            context.drawTooltip(this.textRenderer, Text.literal("Tags to filter arenas that can support this kit (e.g. 'nodebuff, small')."), mouseX, mouseY);
        }
    }
}
