package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.ui.AbstractPopupScreen;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class BlockWeightConfigScreen extends AbstractPopupScreen {
    private final Consumer<Map<Identifier, Integer>> onSave;
    private final List<Identifier> sortedBlocks;
    private final Map<Identifier, TextFieldWidget> weightFields = new HashMap<>();
    private final Map<Identifier, Integer> initialWeights;

    private int scrollOffset = 0;
    private int maxScroll = 0;

    public BlockWeightConfigScreen(Screen parent, Set<Identifier> selectedBlocks, Map<Identifier, Integer> currentWeights, Consumer<Map<Identifier, Integer>> onSave) {
        super(parent, Text.literal("Configure Block Weights"), 260, 200);
        this.onSave = onSave;
        this.initialWeights = currentWeights;

        this.sortedBlocks = new ArrayList<>(selectedBlocks);
        this.sortedBlocks.sort((a, b) -> {
            boolean hasA = currentWeights.containsKey(a);
            boolean hasB = currentWeights.containsKey(b);
            if (hasA != hasB) {
                return hasA ? 1 : -1;
            }
            return a.compareTo(b);
        });
    }

    @Override
    protected void init() {
        this.popupHeight = Math.min(this.height - 40, Math.max(150, 60 + this.sortedBlocks.size() * 24));
        super.init();
    }

    @Override
    protected void initPopup() {
        this.weightFields.clear();
        int rowHeight = 24;
        
        for (int i = 0; i < this.sortedBlocks.size(); i++) {
            Identifier id = this.sortedBlocks.get(i);
            TextFieldWidget field = new TextFieldWidget(this.textRenderer, this.popupX + this.popupWidth - 70, 0, 50, 20, Text.literal("Weight"));
            if (this.initialWeights.containsKey(id)) {
                field.setText(String.valueOf(this.initialWeights.get(id)));
            } else {
                field.setText("10"); // Default unweighted
            }
            this.weightFields.put(id, field);
            this.addDrawableChild(field);
        }

        this.maxScroll = Math.max(0, this.sortedBlocks.size() * rowHeight - (this.popupHeight - 70));

        ButtonWidget saveButton = ButtonWidget.builder(Text.literal("Save Weights"), b -> {
            Map<Identifier, Integer> newWeights = new HashMap<>();
            for (Map.Entry<Identifier, TextFieldWidget> entry : this.weightFields.entrySet()) {
                try {
                    int w = Integer.parseInt(entry.getValue().getText());
                    newWeights.put(entry.getKey(), Math.max(1, w));
                } catch (NumberFormatException e) {
                    newWeights.put(entry.getKey(), 10);
                }
            }
            this.onSave.accept(newWeights);
            this.close();
        }).dimensions(this.popupX + this.popupWidth / 2 - 50, this.popupY + this.popupHeight - 25, 100, 20).build();
        this.addDrawableChild(saveButton);
        
        updateScroll();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta); // Renders background, panel, text fields, and button
        
        int startY = this.popupY + 30 - this.scrollOffset;
        int rowHeight = 24;
        
        int listTop = this.popupY + 30;
        int listBottom = this.popupY + this.popupHeight - 30;
        
        context.enableScissor(this.popupX, listTop, this.popupX + this.popupWidth, listBottom);
        for (int i = 0; i < this.sortedBlocks.size(); i++) {
            Identifier id = this.sortedBlocks.get(i);
            int y = startY + i * rowHeight;
            if (y > listBottom || y + rowHeight < listTop) continue;
            
            ItemStack stack = new ItemStack(Registries.BLOCK.get(id));
            context.drawItem(stack, this.popupX + 15, y + 2);
            
            String name = Registries.BLOCK.get(id).getName().getString();
            context.drawTextWithShadow(this.textRenderer, name, this.popupX + 40, y + 6, 0xFFFFFF);
        }
        context.disableScissor();

        int iconX = this.popupX + this.popupWidth - 25;
        int iconY = this.popupY + 10;
        context.drawTextWithShadow(this.textRenderer, "(?)", iconX, iconY, 0xAAAAAA);
        
        if (mouseX >= iconX && mouseX <= iconX + 15 && mouseY >= iconY && mouseY <= iconY + 10) {
            context.drawTooltip(this.textRenderer, List.of(
                Text.literal("Weight Info").formatted(net.minecraft.util.Formatting.YELLOW),
                Text.literal("Weights determine the relative chance").formatted(net.minecraft.util.Formatting.GRAY),
                Text.literal("for a block to be chosen.").formatted(net.minecraft.util.Formatting.GRAY),
                Text.literal(""),
                Text.literal("Minimum Weight: 1").formatted(net.minecraft.util.Formatting.WHITE),
                Text.literal("Maximum Weight: None").formatted(net.minecraft.util.Formatting.WHITE)
            ), mouseX, mouseY);
        }
    }

    private void updateScroll() {
        int listTop = this.popupY + 30;
        int listBottom = this.popupY + this.popupHeight - 30;
        
        for (int i = 0; i < this.sortedBlocks.size(); i++) {
            Identifier id = this.sortedBlocks.get(i);
            TextFieldWidget field = this.weightFields.get(id);
            if (field != null) {
                field.setY(listTop + i * 24 - this.scrollOffset);
                boolean visible = field.getY() >= listTop && field.getY() + 20 <= listBottom;
                field.visible = visible;
                field.active = visible;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollOffset = (int) Math.max(0, Math.min(this.maxScroll, this.scrollOffset - verticalAmount * 24));
        updateScroll();
        return true;
    }
}
