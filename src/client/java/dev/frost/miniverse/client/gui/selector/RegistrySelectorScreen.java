package dev.frost.miniverse.client.gui.selector;

import dev.frost.miniverse.client.gui.selector.storage.SelectorDataManager;
import dev.frost.miniverse.client.gui.ui.UiPrimitives;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class RegistrySelectorScreen<T> extends Screen {
    private final RegistrySelectorContext<T> context;
    private final RegistryContentProvider<T> provider;
    private final Set<T> selectedEntries;
    
    private TextFieldWidget searchField;
    private EntryGridWidget<T> gridWidget;
    private UiPrimitives.UiSidebar sidebar;
    private List<Text> currentTooltip = List.of();
    
    private List<T> allEntries;
    private List<T> visibleEntries;

    public RegistrySelectorScreen(RegistrySelectorContext<T> context, RegistryContentProvider<T> provider) {
        super(Text.literal(context.title()));
        this.context = context;
        this.provider = provider;
        this.selectedEntries = new HashSet<>(context.initialSelection() != null ? context.initialSelection() : Set.of());
        this.allEntries = new ArrayList<>(provider.getEntries());
        // Ensure initial selection items are present in allEntries (for dynamically loaded persistent ones)
        for (T sel : this.selectedEntries) {
            if (!this.allEntries.contains(sel)) {
                this.allEntries.add(0, sel);
            }
        }
        SelectorDataManager.getPresetManager(); // Ensure loaded
    }

    public void refreshEntries() {
        this.allEntries = new ArrayList<>(this.provider.getEntries());
        for (T sel : this.selectedEntries) {
            if (!this.allEntries.contains(sel)) {
                this.allEntries.add(0, sel);
            }
        }
        if (this.gridWidget != null) {
            this.filterEntries();
        }
    }

    @Override
    protected void init() {
        super.init();
        int sidebarWidth = this.context.state().isSidebarExpanded() ? 160 : 0;
        int headerHeight = 40;
        
        this.searchField = new TextFieldWidget(this.textRenderer, sidebarWidth + 40, 10, 200, 20, Text.literal("Search..."));
        this.searchField.setText(this.context.state().getSearchQuery());
        this.searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(this.searchField);

        ButtonWidget presetsButton = ButtonWidget.builder(Text.literal("Presets"), b -> openPresetsManager())
            .dimensions(this.width - 90, 10, 80, 20)
            .build();
        this.addDrawableChild(presetsButton);

        if ("deathshuffle".equals(this.context.presetNamespace())) {
            presetsButton.setX(this.width - 180);
            ButtonWidget helpButton = ButtonWidget.builder(Text.literal("Datapack Help"), b -> showDatapackHelp())
                .dimensions(this.width - 90, 10, 80, 20)
                .build();
            this.addDrawableChild(helpButton);
        }

        ButtonWidget confirmButton = ButtonWidget.builder(Text.literal("Confirm"), b -> confirmSelection())
            .dimensions(this.width - 90, this.height - 30, 80, 20)
            .build();
        this.addDrawableChild(confirmButton);

        if (this.provider.supportsCreation()) {
            ButtonWidget createNewButton = ButtonWidget.builder(Text.literal("Create New"), b -> this.provider.openCreator(this, null))
                .dimensions(this.width - 180, this.height - 30, 80, 20)
                .build();
            this.addDrawableChild(createNewButton);
            
            ButtonWidget editButton = ButtonWidget.builder(Text.literal("Edit Selected"), b -> {
                if (this.selectedEntries.size() == 1) {
                    this.provider.openCreator(this, this.selectedEntries.iterator().next());
                }
            }).dimensions(this.width - 270, this.height - 30, 80, 20).build();
            this.addDrawableChild(editButton);
        }

        ButtonWidget toggleSidebarButton = ButtonWidget.builder(Text.literal("≡"), b -> toggleSidebar())
            .dimensions(10, 10, 20, 20)
            .build();
        this.addDrawableChild(toggleSidebarButton);

        int gridX = sidebarWidth;
        int gridY = headerHeight;
        int gridWidth = this.width - sidebarWidth;
        int gridHeight = this.height - headerHeight - 40; // bottom footer area
        
        int itemSize = 28;
        int columns = Math.max(1, (gridWidth - 20) / itemSize);
        
        this.gridWidget = new EntryGridWidget<>(this, this.client, gridWidth, gridHeight, gridY, gridY + gridHeight, itemSize, columns, this.provider);
        this.gridWidget.setPosition(gridX, gridY);
        this.addDrawableChild(this.gridWidget);

        buildSidebar();
        filterEntries();
    }

    private void buildSidebar() {
        if (!this.context.state().isSidebarExpanded()) {
            this.sidebar = null;
            return;
        }
        this.sidebar = new UiPrimitives.UiSidebar();
        this.sidebar.setBounds(new dev.frost.miniverse.client.gui.ui.UiLayout.Rect(0, 40, 160, this.height - 40));
        
        this.sidebar.item("☰", "All", () -> {
            this.context.state().clearCategories();
            this.context.state().setSelectedFilterActive(false);
            filterEntries();
        });

        this.sidebar.item("★", "Favorites", () -> {
            this.context.state().clearCategories();
            this.context.state().getActiveCategories().add("favorites");
            this.context.state().setSelectedFilterActive(false);
            filterEntries();
        });
        
        this.sidebar.item("✓", "Selected", () -> {
            this.context.state().clearCategories();
            this.context.state().setSelectedFilterActive(true);
            filterEntries();
        });

        for (RegistryCategory category : this.provider.getAllCategories()) {
            this.sidebar.item(category.iconPath(), category.displayName().getString(), () -> {
                this.context.state().clearCategories();
                this.context.state().getActiveCategories().add(category.id());
                this.context.state().setSelectedFilterActive(false);
                filterEntries();
            });
        }
    }

    private void toggleSidebar() {
        this.context.state().setSidebarExpanded(!this.context.state().isSidebarExpanded());
        this.init(this.client, this.width, this.height);
    }

    private void onSearchChanged(String query) {
        this.context.state().setSearchQuery(query);
        filterEntries();
    }

    private void filterEntries() {
        String query = this.context.state().getSearchQuery().toLowerCase();
        boolean filterFavs = this.context.state().getActiveCategories().contains("favorites");
        boolean filterSelected = this.context.state().isSelectedFilterActive();
        Set<String> activeCats = this.context.state().getActiveCategories();

        this.visibleEntries = this.allEntries.stream().filter(entry -> {
            if (filterSelected && !this.selectedEntries.contains(entry)) return false;
            
            if (filterFavs) {
                if (!this.context.state().getFavorites().contains(this.provider.getId(entry))) return false;
            }

            if (!activeCats.isEmpty() && !filterFavs) { // Assuming if favs is the only active cat, we don't strict filter by others
                boolean matchesCat = false;
                for (RegistryCategory cat : this.provider.getCategories(entry)) {
                    if (activeCats.contains(cat.id())) {
                        matchesCat = true;
                        break;
                    }
                }
                if (!matchesCat) return false;
            }

            if (!query.isEmpty()) {
                if (query.startsWith("#")) {
                    String tagQuery = query.substring(1);
                    boolean hasTag = this.provider.getTags(entry).stream().anyMatch(id -> id.getPath().contains(tagQuery));
                    if (!hasTag) return false;
                } else {
                    String name = this.provider.getDisplayName(entry).getString().toLowerCase();
                    String id = this.provider.getId(entry).toString();
                    if (!name.contains(query) && !id.contains(query)) return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        this.gridWidget.updateEntries(this.visibleEntries);
    }

    public void toggleSelection(T entry) {
        Identifier id = this.provider.getId(entry);
        if (id.getNamespace().equals("miniverse") && id.getPath().startsWith("template/")) {
            if (id.getPath().equals("template/y_level")) {
                this.client.setScreen(new dev.frost.miniverse.client.gui.selector.config.YLevelConfigScreen<>(this, id));
            }
            return;
        }

        if (this.context.selectionMode() == RegistrySelectorContext.SelectionMode.SINGLE) {
            this.selectedEntries.clear();
            this.selectedEntries.add(entry);
        } else {
            if (!this.selectedEntries.remove(entry)) {
                this.selectedEntries.add(entry);
            }
        }
        saveData();
    }

    public void addDynamicEntry(T entry) {
        if (!this.allEntries.contains(entry)) {
            this.allEntries.add(0, entry);
        }
        if (this.context.selectionMode() == RegistrySelectorContext.SelectionMode.SINGLE) {
            this.selectedEntries.clear();
        }
        this.selectedEntries.add(entry);
        saveData();
        filterEntries();
    }

    public Set<T> getSelectedEntries() {
        return this.selectedEntries;
    }

    public RegistrySelectorState getState() {
        return this.context.state();
    }

    public void setHoverTooltip(List<Text> tooltip) {
        this.currentTooltip = tooltip;
    }

    public RegistryContentProvider<T> getProvider() {
        return this.provider;
    }

    public void loadSelection(Set<Identifier> ids) {
        this.selectedEntries.clear();
        for (T entry : this.allEntries) {
            if (ids.contains(this.provider.getId(entry))) {
                this.selectedEntries.add(entry);
            }
        }
        filterEntries();
    }

    public void saveData() {
        SelectorDataManager.save();
    }

    private void openPresetsManager() {
        this.client.setScreen(new PresetsManagerWidget<>(this, this.context.presetNamespace()));
    }

    private void showDatapackHelp() {
        this.client.setScreen(new DatapackHelpScreen(this));
    }

    private class DatapackHelpScreen extends Screen {
        private final Screen parent;
        private int page = 0;
        private ButtonWidget actionButton;

        private final String[] pages = {
            "Death Objectives are data-driven!\n\n" +
            "To add custom deaths, create a Datapack with the following structure:\n" +
            "data/miniverse/death_objective/your_objective.json\n\n" +
            "Place the datapack in the 'datapacks' folder of your world.\n" +
            "Supported conditions use the vanilla 'minecraft:damage_condition' predicate.",
            
            "JSON Format Example:\n\n" +
            "{\n" +
            "  \"display_name\": {\"text\": \"Name\", \"color\": \"red\"},\n" +
            "  \"icon\": \"minecraft:skull\",\n" +
            "  \"damage_condition\": {\n" +
            "    \"type\": { \"tags\": [ { \"id\": \"minecraft:is_fire\", \"expected\": true } ] }\n" +
            "  }\n" +
            "}"
        };

        protected DatapackHelpScreen(Screen parent) {
            super(Text.literal("How to Add Custom Death Objectives"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            super.init();
            this.actionButton = ButtonWidget.builder(Text.literal("Next"), b -> {
                if (this.page == 0) {
                    this.page = 1;
                    this.actionButton.setMessage(Text.literal("Close"));
                } else {
                    this.client.setScreen(this.parent);
                }
            }).dimensions(this.width / 2 - 40, this.height - 40, 80, 20).build();
            this.addDrawableChild(this.actionButton);
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            this.parent.render(context, -1, -1, delta);
            context.fill(0, 0, this.width, this.height, 0xAA000000);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
            
            int y = 50;
            for (String line : this.pages[this.page].split("\n")) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(line), this.width / 2 - 140, y, 0xFFFFFF);
                y += 12;
            }
            
            super.render(context, mouseX, mouseY, delta);
        }
    }

    private void confirmSelection() {
        this.context.state().setScrollPosition(this.gridWidget.getScrollAmount());
        this.client.setScreen(null);
        if (this.context.callback() != null) {
            this.context.callback().accept(new RegistrySelectionResult<>(this.selectedEntries, false));
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        UiRenderer.workspace(context, this.width, this.height, 0);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        this.currentTooltip = List.of();
        
        super.render(context, mouseX, mouseY, delta);

        if (this.sidebar != null) {
            this.sidebar.render(context, this.textRenderer, mouseX, mouseY, delta);
        }

        // Render Stats
        String stats = String.format("Selected: %d | Visible: %d | Total: %d", this.selectedEntries.size(), this.visibleEntries.size(), this.allEntries.size());
        context.drawText(this.textRenderer, Text.literal(stats), this.width / 2 - 50, this.height - 20, 0xAAAAAA, false);

        if (!this.currentTooltip.isEmpty()) {
            context.drawTooltip(this.textRenderer, this.currentTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.sidebar != null && this.sidebar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
