package dev.frost.miniverse.client.gui.workspace.framework;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.GamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.WorkspaceView;
import dev.frost.miniverse.client.gui.workspace.components.MapThumbnailGrid;
import dev.frost.miniverse.client.gui.workspace.components.TeamSelectionGrid;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public abstract class AbstractGamemodeWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider, GamemodeWorkspaceView.RosterRefreshable {
    protected final MinecraftClient client = MinecraftClient.getInstance();
    protected final WorkspaceModuleManager moduleManager = new WorkspaceModuleManager();
    protected StandardWorkspaceLayout layout;
    
    protected String sessionName;
    protected ValidationResult status = null;

    private TeamSelectionGrid rosterGrid;
    private MapThumbnailGrid mapGrid;
    private String selectedMapId = "";
    private UiLayout.Rect selectAllButtonRect;
    private UiLayout.Rect clearButtonRect;

    private String keepInventoryState = "DEFAULT";
    private String pvpEnabledState = "DEFAULT";
    private String doDaylightCycleState = "DEFAULT";
    private String doWeatherCycleState = "DEFAULT";
    private String fallDamageState = "DEFAULT";
    private String naturalRegenerationState = "DEFAULT";
    private String announceAdvancementsState = "DEFAULT";

    private ButtonWidget keepInventoryBtn;
    private ButtonWidget pvpEnabledBtn;
    private ButtonWidget doDaylightCycleBtn;
    private ButtonWidget doWeatherCycleBtn;
    private ButtonWidget fallDamageBtn;
    private ButtonWidget naturalRegenerationBtn;
    private ButtonWidget announceAdvancementsBtn;

    protected void useGameRules() {
        this.moduleManager.register("gamerules", "G", "Game Rules", "Rules", "Override default session match rules.", UiTheme.ACCENT_BLUE);
    }

    public AbstractGamemodeWorkspaceView(String defaultSessionNamePrefix) {
        this.sessionName = defaultSessionNamePrefix + "-" + System.currentTimeMillis();
    }

    protected void useRosterGrid(TeamSelectionGrid grid, String moduleId, String icon, String label, String group, String description, int accent) {
        this.rosterGrid = grid;
        this.moduleManager.register(moduleId, icon, label, group, description, accent);
    }

    protected void useMapSelection(String moduleId, String icon, String label, String group, String description, int accent, String gridTitle) {
        this.mapGrid = new MapThumbnailGrid(gridTitle, mapId -> {
            this.selectedMapId = mapId;
            this.status = ValidationResult.success("Selected map.");
            this.mapGrid.setSelectedMapId(mapId);
        });
        this.moduleManager.register(moduleId, icon, label, group, description, accent);
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.layout = new StandardWorkspaceLayout(workspace);
        if (this.rosterGrid != null) {
            this.rosterGrid.setBounds(this.layout.contentArea());
            if (this.rosterGrid instanceof dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) {
                this.selectAllButtonRect = new UiLayout.Rect(this.layout.actionStartX(), this.layout.actionY(), 90, StandardWorkspaceLayout.BUTTON_HEIGHT);
                this.clearButtonRect = new UiLayout.Rect(this.layout.actionStartX() + 98, this.layout.actionY(), 70, StandardWorkspaceLayout.BUTTON_HEIGHT);
            }
        }
        if (this.mapGrid != null) {
            this.mapGrid.setBounds(this.layout.contentArea());
            this.mapGrid.setMaps(dev.frost.miniverse.client.gui.SessionSnapshotData.maps().stream().filter(map -> map.validFor(this.gameId())).toList());
        }
        this.initGamemode(screen);
        this.initGameRules(screen);
    }

    private void initGameRules(SessionScreen screen) {
        if (this.moduleManager.isActive("gamerules")) {
            int y = this.layout.mainPanel().y() + 96;
            this.keepInventoryBtn = this.addButton(screen, "Keep Inventory: " + formatRuleState(this.keepInventoryState, defaultMatchRules().keepInventory()), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.keepInventoryState = nextTriState(this.keepInventoryState);
                this.keepInventoryBtn.setMessage(Text.literal("Keep Inventory: " + formatRuleState(this.keepInventoryState, defaultMatchRules().keepInventory())));
            });
            y += 30;
            this.pvpEnabledBtn = this.addButton(screen, "PvP Enabled: " + formatRuleState(this.pvpEnabledState, defaultMatchRules().pvpEnabled()), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.pvpEnabledState = nextTriState(this.pvpEnabledState);
                this.pvpEnabledBtn.setMessage(Text.literal("PvP Enabled: " + formatRuleState(this.pvpEnabledState, defaultMatchRules().pvpEnabled())));
            });
            y += 30;
            this.doDaylightCycleBtn = this.addButton(screen, "Daylight Cycle: " + formatRuleState(this.doDaylightCycleState, defaultMatchRules().doDaylightCycle()), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.doDaylightCycleState = nextTriState(this.doDaylightCycleState);
                this.doDaylightCycleBtn.setMessage(Text.literal("Daylight Cycle: " + formatRuleState(this.doDaylightCycleState, defaultMatchRules().doDaylightCycle())));
            });
            y += 30;
            this.doWeatherCycleBtn = this.addButton(screen, "Weather Cycle: " + formatRuleState(this.doWeatherCycleState, defaultMatchRules().doWeatherCycle()), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.doWeatherCycleState = nextTriState(this.doWeatherCycleState);
                this.doWeatherCycleBtn.setMessage(Text.literal("Weather Cycle: " + formatRuleState(this.doWeatherCycleState, defaultMatchRules().doWeatherCycle())));
            });
            y += 30;
            this.fallDamageBtn = this.addButton(screen, "Fall Damage: " + formatRuleState(this.fallDamageState, defaultMatchRules().fallDamage()), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.fallDamageState = nextTriState(this.fallDamageState);
                this.fallDamageBtn.setMessage(Text.literal("Fall Damage: " + formatRuleState(this.fallDamageState, defaultMatchRules().fallDamage())));
            });
            y += 30;
            this.naturalRegenerationBtn = this.addButton(screen, "Natural Regen: " + formatRuleState(this.naturalRegenerationState, defaultMatchRules().naturalRegeneration()), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.naturalRegenerationState = nextTriState(this.naturalRegenerationState);
                this.naturalRegenerationBtn.setMessage(Text.literal("Natural Regen: " + formatRuleState(this.naturalRegenerationState, defaultMatchRules().naturalRegeneration())));
            });
            y += 30;
            this.announceAdvancementsBtn = this.addButton(screen, "Advancements: " + formatRuleState(this.announceAdvancementsState, defaultMatchRules().announceAdvancements()), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.announceAdvancementsState = nextTriState(this.announceAdvancementsState);
                this.announceAdvancementsBtn.setMessage(Text.literal("Advancements: " + formatRuleState(this.announceAdvancementsState, defaultMatchRules().announceAdvancements())));
            });
        }
    }

    private String nextTriState(String state) {
        return state.equals("DEFAULT") ? "FORCE ON" : state.equals("FORCE ON") ? "FORCE OFF" : "DEFAULT";
    }

    protected dev.frost.miniverse.minigame.core.rules.GlobalMatchRules defaultMatchRules() {
        return dev.frost.miniverse.minigame.core.rules.GlobalMatchRules.defaults();
    }

    private String formatRuleState(String state, boolean defaultValue) {
        if ("DEFAULT".equals(state)) {
            return "DEFAULT: " + (defaultValue ? "§aON" : "§cOFF");
        }
        if ("FORCE ON".equals(state)) {
            return "FORCE §aON";
        }
        if ("FORCE OFF".equals(state)) {
            return "FORCE §cOFF";
        }
        return state;
    }

    protected abstract void initGamemode(SessionScreen screen);

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = this.layout.mainPanel();
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(mainPanel.x() + 1, mainPanel.y() + 1, mainPanel.x() + mainPanel.width() - 1, mainPanel.y() + 40, 0x701B2634);
        
        WorkspaceModuleManager.RegisteredModule active = this.moduleManager.getActiveModule();
        if (active != null) {
            context.drawText(textRenderer, Text.literal(active.label()), mainPanel.x() + 14, mainPanel.y() + 14, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(active.description()), mainPanel.x() + 14, mainPanel.y() + 28, UiTheme.TEXT_DIM, false);
        }

        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
             if (this.selectAllButtonRect != null && this.clearButtonRect != null) {
                 this.renderActionButton(context, textRenderer, this.selectAllButtonRect, "Select All", UiTheme.ACCENT_BLUE, this.selectAllButtonRect.contains(mouseX, mouseY));
                 this.renderActionButton(context, textRenderer, this.clearButtonRect, "Clear", UiTheme.ACCENT, this.clearButtonRect.contains(mouseX, mouseY));
             }
             this.rosterGrid.render(context, textRenderer, mouseX, mouseY, delta);
        }
        if (this.mapGrid != null && this.moduleManager.isActive("map")) {
             this.mapGrid.render(context, textRenderer, mouseX, mouseY, delta);
        }

        this.renderGamemodeBackground(context, textRenderer, mouseX, mouseY, delta);

        if (this.moduleManager.isActive("gamerules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }

        if (this.moduleManager.isActive("summary")) {
            this.syncStateFromWidgets();
            WorkspaceModuleManager.RegisteredModule activeModule = this.moduleManager.getActiveModule();
            this.renderSettingsModulePanel(context, textRenderer, activeModule.label(), activeModule.accent());
            int x = this.layout.mainPanel().x() + 14;
            int y = this.layout.mainPanel().y() + 92;
            int line = y + 18;
            context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
            line += 20;
            for (Text t : this.getSummaryLines()) {
                context.drawText(textRenderer, t, x + 14, line, UiTheme.TEXT_MUTED, false);
                line += 18;
            }
        }

        if (this.status != null && !this.status.message().isBlank()) {
            context.drawText(textRenderer, Text.literal(this.status.message()), mainPanel.x() + 14, mainPanel.y() + mainPanel.height() - 18, this.status.type().color(), false);
        }
        
        UiLayout.Rect startBtn = this.layout.startButton();
        this.renderActionButton(context, textRenderer, startBtn, "Start Match", UiTheme.ACCENT_GREEN, startBtn.contains(mouseX, mouseY));
    }

    protected abstract void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta);

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            this.rosterGrid.renderForeground(context, textRenderer, workspace, mouseX, mouseY, delta);
        }
        
        if (this.moduleManager.isActive("gamerules")) {
            int labelX = this.layout.mainPanel().x() + 38;
            int y = this.layout.mainPanel().y() + 102;
            context.drawText(textRenderer, Text.literal("Keep Inventory"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("PvP Enabled"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Daylight Cycle"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Weather Cycle"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Fall Damage"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Natural Regen"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Advancements"), labelX, y, UiTheme.TEXT_MUTED, false);
        }

        this.renderGamemodeForeground(context, textRenderer, mouseX, mouseY, delta);
    }
    
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (this.layout.startButton().contains(mouseX, mouseY)) {
            this.createSession();
            return true;
        }
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            if (this.selectAllButtonRect != null && this.selectAllButtonRect.contains(mouseX, mouseY) && this.rosterGrid instanceof dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) {
                dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid staticGrid = (dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) this.rosterGrid;
                staticGrid.clear();
                String target = staticGrid.getColumnIds().size() > 1 ? staticGrid.getColumnIds().get(1) : "selected";
                for (dev.frost.miniverse.client.gui.SessionSnapshotData.RosterEntry entry : dev.frost.miniverse.client.gui.SessionSnapshotData.roster()) {
                    staticGrid.addMember(target, entry);
                }
                this.status = ValidationResult.info("Selected all players.");
                return true;
            }
            if (this.clearButtonRect != null && this.clearButtonRect.contains(mouseX, mouseY) && this.rosterGrid instanceof dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) {
                ((dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) this.rosterGrid).clear();
                this.status = ValidationResult.info("Selection cleared.");
                return true;
            }
            if (this.rosterGrid.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (this.mapGrid != null && this.moduleManager.isActive("map")) {
            if (this.mapGrid.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return this.gamemodeMouseClicked(mouseX, mouseY, button);
    }

    protected boolean gamemodeMouseClicked(double mouseX, double mouseY, int button) { return false; }

    protected void syncStateFromWidgets() {}

    protected java.util.List<Text> getSummaryLines() {
        return java.util.Collections.emptyList();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            return this.rosterGrid.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            return this.rosterGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            return this.rosterGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        if (this.mapGrid != null && this.moduleManager.isActive("map")) {
            return this.mapGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        return this.gamemodeMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    protected boolean gamemodeMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return false; }

    @Override
    public void refreshRoster() {
        if (this.rosterGrid != null) {
            this.rosterGrid.refreshRoster();
        }
    }

    @Override
    public List<WorkspaceModule> modules() {
        return this.moduleManager.getVisibleModules();
    }

    @Override
    public String activeModuleId() {
        return this.moduleManager.getActiveModuleId();
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.moduleManager.setActiveModuleId(moduleId);
    }

    protected void renderActionButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(accent, 0.34F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, border);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    protected ButtonWidget addButton(SessionScreen screen, String label, int x, int y, int width, Runnable action) {
        return screen.addWorkspaceChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
            .dimensions(x, y, width, StandardWorkspaceLayout.BUTTON_HEIGHT)
            .build());
    }

    protected TextFieldWidget addField(SessionScreen screen, int x, int y, String value, int width, String narration) {
        TextFieldWidget field = new TextFieldWidget(this.client.textRenderer, x, y, width, StandardWorkspaceLayout.BUTTON_HEIGHT, Text.literal(narration));
        field.setMaxLength(64);
        field.setText(value);
        return screen.addWorkspaceChild(field);
    }

    protected TextFieldWidget addField(SessionScreen screen, int x, int y, String value, String narration) {
        return this.addField(screen, x, y, value, StandardWorkspaceLayout.SETTINGS_FIELD_WIDTH, narration);
    }

    protected void addStepper(SessionScreen screen, TextFieldWidget field, int x, int y, int min, int max, int step) {
        this.addButton(screen, "-", x, y, 24, () -> this.stepField(field, min, max, -step));
        this.addButton(screen, "+", x + 30, y, 24, () -> this.stepField(field, min, max, step));
    }

    protected void drawLabel(DrawContext context, TextRenderer textRenderer, String label, int x, int y) {
        context.drawText(textRenderer, Text.literal(label), x, y, UiTheme.TEXT_MUTED, false);
    }
    
    protected void renderSettingsModulePanel(DrawContext context, TextRenderer textRenderer, String title, int accent) {
        int moduleX = this.layout.mainPanel().x() + 14;
        int moduleY = this.layout.mainPanel().y() + 72;
        int moduleWidth = this.layout.mainPanel().width() - 28;
        int moduleHeight = this.layout.mainPanel().height() - 104;
        UiRenderer.panel(context, moduleX, moduleY, moduleWidth, moduleHeight, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(moduleX, moduleY, moduleX + 3, moduleY + moduleHeight, accent);
        context.drawText(textRenderer, Text.literal(title), moduleX + 12, moduleY + 12, accent, false);
    }

    protected int readClamped(TextFieldWidget field, int fallback, int min, int max) {
        if (field == null) return fallback;
        try {
            int value = Math.clamp(Integer.parseInt(field.getText().trim()), min, max);
            field.setText(Integer.toString(value));
            return value;
        } catch (NumberFormatException ignored) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
    }

    protected void stepField(TextFieldWidget field, int min, int max, int delta) {
        int value = this.readClamped(field, min, min, max);
        field.setText(Integer.toString(Math.clamp(value + delta, min, max)));
    }

    private void createSession() {
        if (this.client.player == null) {
            this.status = ValidationResult.error("Not connected to a server.");
            return;
        }
        if (this.sessionName == null || this.sessionName.isBlank()) {
            this.status = ValidationResult.error("Enter a session name.");
            return;
        }
        if (this.mapGrid != null && this.selectedMapId.isBlank()) {
            this.status = ValidationResult.error("Select a valid map first.");
            return;
        }
        
        ValidationResult gamemodeValidation = this.validateGamemodeStart();
        if (gamemodeValidation != null && !gamemodeValidation.canStart()) {
            this.status = gamemodeValidation;
            return;
        }
        
        SessionPayloadBuilder builder = new SessionPayloadBuilder(this.gameId(), this.sessionName);
        if (this.mapGrid != null) {
            builder.settings().putString("mapId", this.selectedMapId);
        }
        this.buildSessionSettings(builder);
        this.buildGameRulesSettings(builder);
        this.buildSessionGroups(builder);
        builder.dispatch();
        
        this.status = ValidationResult.success("Requested " + this.title() + " session creation.");
    }

    private void buildGameRulesSettings(SessionPayloadBuilder builder) {
        if (!"DEFAULT".equals(this.keepInventoryState)) builder.settings().putString("gamerule.keepInventory", "FORCE ON".equals(this.keepInventoryState) ? "true" : "false");
        if (!"DEFAULT".equals(this.pvpEnabledState)) builder.settings().putString("gamerule.pvpEnabled", "FORCE ON".equals(this.pvpEnabledState) ? "true" : "false");
        if (!"DEFAULT".equals(this.doDaylightCycleState)) builder.settings().putString("gamerule.doDaylightCycle", "FORCE ON".equals(this.doDaylightCycleState) ? "true" : "false");
        if (!"DEFAULT".equals(this.doWeatherCycleState)) builder.settings().putString("gamerule.doWeatherCycle", "FORCE ON".equals(this.doWeatherCycleState) ? "true" : "false");
        if (!"DEFAULT".equals(this.fallDamageState)) builder.settings().putString("gamerule.fallDamage", "FORCE ON".equals(this.fallDamageState) ? "true" : "false");
        if (!"DEFAULT".equals(this.naturalRegenerationState)) builder.settings().putString("gamerule.naturalRegeneration", "FORCE ON".equals(this.naturalRegenerationState) ? "true" : "false");
        if (!"DEFAULT".equals(this.announceAdvancementsState)) builder.settings().putString("gamerule.announceAdvancements", "FORCE ON".equals(this.announceAdvancementsState) ? "true" : "false");
    }

    protected abstract ValidationResult validateGamemodeStart();
    protected abstract void buildSessionSettings(SessionPayloadBuilder builder);
    protected abstract void buildSessionGroups(SessionPayloadBuilder builder);
}
