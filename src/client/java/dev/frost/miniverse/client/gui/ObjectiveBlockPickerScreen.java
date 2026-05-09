package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ObjectiveBlockPickerScreen extends Screen {
    private static final double[] PROBABILITY_OPTIONS = {1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1};
    private static final int PANEL_WIDTH = 860;
    private static final int PANEL_HEIGHT = 640;
    private static final int TILE_WIDTH = 200;
    private static final int TILE_HEIGHT = 40;
    private static final int TILE_GAP = 8;
    private static final int HEADER_HEIGHT = 84;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final ResourceSprintSetupScreen parent;
    private final List<BlockEntry> blocks = new ArrayList<>();
    private final List<BlockEntry> filtered = new ArrayList<>();
    private TextFieldWidget searchField;
    private ButtonWidget difficultyButton;
    private ButtonWidget probabilityButton;
    private int scrollOffset = 0;
    private ResourceSprintSettings.ObjectiveDifficulty difficulty = ResourceSprintSettings.ObjectiveDifficulty.EASY;
    private double probability = 1.0;
    private String lastSearchQuery = "";
    private String statusMessage = "";

    public ObjectiveBlockPickerScreen(ResourceSprintSetupScreen parent) {
        super(Text.literal("Add Objective"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.blocks.clear();
        this.filtered.clear();
        this.scrollOffset = 0;
        this.difficulty = ResourceSprintSettings.ObjectiveDifficulty.EASY;
        this.probability = this.defaultProbabilityFor(this.difficulty);

        this.loadBlocks();
        this.refreshFilter();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = Math.max(12, (this.height - PANEL_HEIGHT) / 2);
        int searchX = panelX + 16;
        int searchY = panelY + 44;
        int difficultyWidth = 150;
        int probabilityWidth = 140;
        int searchWidth = PANEL_WIDTH - 32 - difficultyWidth - probabilityWidth - 8;

        this.searchField = new TextFieldWidget(this.textRenderer, searchX, searchY, searchWidth, 20, Text.literal("search items"));
        this.searchField.setMaxLength(64);
        this.addDrawableChild(this.searchField);

        this.difficultyButton = this.addDrawableChild(ButtonWidget.builder(this.difficultyLabel(), button -> {
            this.difficulty = this.nextDifficulty();
            this.probability = this.defaultProbabilityFor(this.difficulty);
            button.setMessage(this.difficultyLabel());
            if (this.probabilityButton != null) {
                this.probabilityButton.setMessage(this.probabilityLabel());
            }
        }).dimensions(panelX + PANEL_WIDTH - 16 - probabilityWidth - 8 - difficultyWidth, searchY, difficultyWidth, 20).build());

        this.probabilityButton = this.addDrawableChild(ButtonWidget.builder(this.probabilityLabel(), button -> {
            this.probability = this.nextProbability(this.probability);
            button.setMessage(this.probabilityLabel());
        }).dimensions(panelX + PANEL_WIDTH - 16 - probabilityWidth, searchY, probabilityWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(this.parent))
            .dimensions(panelX + PANEL_WIDTH - 16 - 68, panelY + PANEL_HEIGHT - 16 - 20, 68, 20)
            .build());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.searchField != null) {
            String query = this.searchField.getText();
            if (!query.equals(this.lastSearchQuery)) {
                this.refreshFilter();
                this.lastSearchQuery = query;
            }
        }
        if (this.difficultyButton != null) {
            this.difficultyButton.setMessage(this.difficultyLabel());
        }
        if (this.probabilityButton != null) {
            this.probabilityButton.setMessage(this.probabilityLabel());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        Layout layout = this.layout();
        if (this.isWithin(mouseX, mouseY, layout.listX, layout.listY, layout.listWidth, layout.listHeight)) {
            int maxScroll = this.getMaxScroll(layout);
            if (maxScroll > 0) {
                this.scrollOffset = clamp(this.scrollOffset - delta, 0, maxScroll);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        Layout layout = this.layout();
        int index = this.getBlockAt(mouseX, mouseY, layout);
        if (index >= 0) {
            BlockEntry entry = this.filtered.get(index);
            this.parent.addObjective(new ResourceSprintSettings.ObjectiveEntry(entry.id(), this.difficulty, this.probability));
            this.client.setScreen(this.parent);
            return true;
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = this.layout();

        context.fill(layout.panelX, layout.panelY, layout.panelX + layout.panelWidth, layout.panelY + layout.panelHeight, 0xD0121212);
        context.fill(layout.panelX + 1, layout.panelY + 1, layout.panelX + layout.panelWidth - 1, layout.panelY + 32, 0xCC1F1F1F);
        context.fill(layout.panelX + 1, layout.panelY + 33, layout.panelX + layout.panelWidth - 1, layout.panelY + layout.panelHeight - 1, 0xAA181818);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, layout.panelX + layout.panelWidth / 2, layout.panelY + 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Click an item to add it. Choose difficulty and probability first."), layout.panelX + layout.panelWidth / 2, layout.panelY + 26, 0xB0B0B0);

        context.drawText(this.textRenderer, Text.literal("Search Items"), layout.panelX + 16, layout.panelY + 38, 0xFFE0E0E0, false);
        context.drawText(this.textRenderer, Text.literal("Difficulty"), layout.panelX + layout.panelWidth - 16 - 140 - 8 - 150, layout.panelY + 38, 0xFFE0E0E0, false);
        context.drawText(this.textRenderer, Text.literal("Probability"), layout.panelX + layout.panelWidth - 16 - 140, layout.panelY + 38, 0xFFE0E0E0, false);

        context.drawText(this.textRenderer, Text.literal("Showing " + this.filtered.size() + " item" + (this.filtered.size() == 1 ? "" : "s")), layout.panelX + 16, layout.panelY + 70, 0xFFB8B8B8, false);

        this.drawGrid(context, layout);
        super.render(context, mouseX, mouseY, delta);
    }

    private void drawGrid(DrawContext context, Layout layout) {
        int visibleRows = this.getVisibleRows(layout);
        int rowsToDraw = Math.clamp(this.filtered.size() - this.scrollOffset, 0, visibleRows);

        context.fill(layout.listX, layout.listY, layout.listX + layout.listWidth, layout.listY + layout.listHeight, 0x66141414);
        context.fill(layout.listX + 1, layout.listY + 1, layout.listX + layout.listWidth - 1, layout.listY + 3, 0xFF5B5B5B);

        if (this.filtered.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No matching blocks."), layout.listX + layout.listWidth / 2, layout.listY + layout.listHeight / 2 - 4, 0xFFB8B8B8);
            return;
        }

        for (int row = 0; row < rowsToDraw; row++) {
            int rowIndex = this.scrollOffset + row;
            int rowY = layout.listY + 8 + row * (TILE_HEIGHT + TILE_GAP);
            for (int column = 0; column < layout.columns; column++) {
                int index = rowIndex * layout.columns + column;
                if (index >= this.filtered.size()) {
                    return;
                }

                BlockEntry entry = this.filtered.get(index);
                int tileX = layout.listX + 8 + column * (TILE_WIDTH + TILE_GAP);
                context.fill(tileX, rowY, tileX + TILE_WIDTH, rowY + TILE_HEIGHT, 0x33222222);
                context.fill(tileX + 1, rowY + 1, tileX + TILE_WIDTH - 1, rowY + 3, 0x55FFFFFF);
                context.drawItem(entry.icon, tileX + 4, rowY + 4);
                context.drawText(this.textRenderer, Text.literal(this.elide(entry.displayName(), TILE_WIDTH - 38)), tileX + 24, rowY + 6, 0xFFE0E0E0, false);
                context.drawText(this.textRenderer, Text.literal(entry.id()), tileX + 24, rowY + 20, 0xFFB8B8B8, false);
                context.drawText(this.textRenderer, Text.literal("+ " + this.difficultyLabel().getString()), tileX + TILE_WIDTH - 72, rowY + 12, 0xFF5B8CFF, false);
            }
        }

        this.drawScrollBar(context, layout.listX, layout.listY, layout.listWidth, layout.listHeight, this.filtered.size(), visibleRows, this.scrollOffset);
    }

    private void refreshFilter() {
        String query = this.searchField == null ? "" : this.searchField.getText().trim().toLowerCase(Locale.ROOT);
        this.filtered.clear();
        for (BlockEntry entry : this.blocks) {
            if (query.isBlank() || entry.id().contains(query) || entry.displayName().toLowerCase(Locale.ROOT).contains(query)) {
                this.filtered.add(entry);
            }
        }
        this.scrollOffset = clamp(this.scrollOffset, 0, this.getMaxScroll(this.layout()));
    }

    private void loadBlocks() {
        List<Item> allItems = Registries.ITEM.stream()
            .filter(item -> item != Items.AIR)
            .sorted(Comparator.comparing(item -> this.itemId(item).toString()))
            .toList();

        for (Item item : allItems) {
            Identifier id = this.itemId(item);
            ItemStack stack = item.getDefaultStack();
            this.blocks.add(new BlockEntry(id.toString(), item.getName().getString(), stack));
        }
    }

    private Identifier itemId(Item item) {
        return Registries.ITEM.getId(item);
    }

    private int getBlockAt(double mouseX, double mouseY, Layout layout) {
        if (!this.isWithin(mouseX, mouseY, layout.listX, layout.listY, layout.listWidth, layout.listHeight)) {
            return -1;
        }

        int rowStartY = layout.listY + 8;
        int row = (int) ((mouseY - rowStartY) / (TILE_HEIGHT + TILE_GAP));
        int column = (int) ((mouseX - (layout.listX + 8)) / (TILE_WIDTH + TILE_GAP));
        if (column < 0 || column >= layout.columns) {
            return -1;
        }

        int visibleRows = this.getVisibleRows(layout);
        int index = this.scrollOffset + row;
        if (row < 0 || row >= visibleRows) {
            return -1;
        }

        int blockIndex = index * layout.columns + column;
        return blockIndex >= 0 && blockIndex < this.filtered.size() ? blockIndex : -1;
    }

    private int getVisibleRows(Layout layout) {
        return Math.max(1, (layout.listHeight - 16) / (TILE_HEIGHT + TILE_GAP));
    }

    private int getMaxScroll(Layout layout) {
        return Math.max(0, (int) Math.ceil(this.filtered.size() / (double) layout.columns) - this.getVisibleRows(layout));
    }

    private void drawScrollBar(DrawContext context, int x, int y, int width, int height, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows || visibleRows <= 0) {
            return;
        }

        int trackX = x + width - 5;
        int trackY = y + 2;
        int trackHeight = height - 4;
        int thumbHeight = Math.max(12, (int) ((trackHeight * (double) visibleRows) / totalRows));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbY = trackY + (int) (((trackHeight - thumbHeight) * (double) scrollOffset) / maxScroll);

        context.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0x55000000);
        context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xCCB0B0B0);
    }

    private Text difficultyLabel() {
        return Text.literal("Difficulty: " + this.difficulty.name().toLowerCase(Locale.ROOT));
    }

    private Text probabilityLabel() {
        return Text.literal("Probability: " + this.formatProbability(this.probability));
    }

    private ResourceSprintSettings.ObjectiveDifficulty nextDifficulty() {
        return switch (this.difficulty) {
            case EASY -> ResourceSprintSettings.ObjectiveDifficulty.MEDIUM;
            case MEDIUM -> ResourceSprintSettings.ObjectiveDifficulty.HARD;
            case HARD -> ResourceSprintSettings.ObjectiveDifficulty.EASY;
        };
    }

    private double defaultProbabilityFor(ResourceSprintSettings.ObjectiveDifficulty difficulty) {
        return switch (difficulty == null ? ResourceSprintSettings.ObjectiveDifficulty.EASY : difficulty) {
            case EASY -> 0.9;
            case MEDIUM -> 0.6;
            case HARD -> 0.3;
        };
    }

    private double nextProbability(double current) {
        double normalized = current > 0 && current <= 1.0 ? current : 1.0;
        int index = 0;
        for (int i = 0; i < PROBABILITY_OPTIONS.length; i++) {
            if (Math.abs(PROBABILITY_OPTIONS[i] - normalized) < 0.0001) {
                index = i;
                break;
            }
        }
        return PROBABILITY_OPTIONS[(index + 1) % PROBABILITY_OPTIONS.length];
    }

    private String formatProbability(double probability) {
        int percent = (int) Math.round(Math.max(0.0, Math.min(1.0, probability)) * 100.0);
        return percent + "%";
    }

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String elide(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        if (this.textRenderer.getWidth(text) <= width) {
            return text;
        }

        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && this.textRenderer.getWidth(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private record BlockEntry(String id, String displayName, ItemStack icon) {
    }

    private record Layout(
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        int listX,
        int listY,
        int listWidth,
        int listHeight,
        int columns
    ) {
    }

    private Layout layout() {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = Math.max(12, (this.height - PANEL_HEIGHT) / 2);
        int listX = panelX + 16;
        int listY = panelY + HEADER_HEIGHT;
        int listWidth = PANEL_WIDTH - 32;
        int listHeight = PANEL_HEIGHT - HEADER_HEIGHT - 56;
        int columns = Math.max(1, (listWidth - 16) / (TILE_WIDTH + TILE_GAP));
        return new Layout(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, listX, listY, listWidth, listHeight, columns);
    }
}

