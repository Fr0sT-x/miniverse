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

    protected record TooltipZone(int x, int y, int width, int height, java.util.function.Supplier<String> text) {}
    protected final java.util.List<TooltipZone> activeTooltips = new java.util.ArrayList<>();

    private TeamSelectionGrid rosterGrid;
    private MapThumbnailGrid mapGrid;
    private String selectedMapId = "";
    private UiLayout.Rect selectAllButtonRect;
    private UiLayout.Rect clearButtonRect;

    private TriState keepInventoryState = TriState.DEFAULT;
    private TriState pvpEnabledState = TriState.DEFAULT;
    private TriState doDaylightCycleState = TriState.DEFAULT;
    private TriState doWeatherCycleState = TriState.DEFAULT;
    private TriState fallDamageState = TriState.DEFAULT;
    private TriState naturalRegenerationState = TriState.DEFAULT;
    private TriState announceAdvancementsState = TriState.DEFAULT;

    private ButtonWidget keepInventoryBtn;
    private ButtonWidget pvpEnabledBtn;
    private ButtonWidget doDaylightCycleBtn;
    private ButtonWidget doWeatherCycleBtn;
    private ButtonWidget fallDamageBtn;
    private ButtonWidget naturalRegenerationBtn;
    private ButtonWidget announceAdvancementsBtn;
    private ButtonWidget immediateRespawnBtn;
    
    protected TriState immediateRespawnState = TriState.DEFAULT;

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
        this.activeTooltips.clear();
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
            this.keepInventoryBtn = this.addButton(screen, "Keep Inventory: " + formatRuleState(this.keepInventoryState, defaultMatchRules().keepInventory()), this.layout.mainPanel().x() + 180, y, 200, TriStateTooltip.of(() -> this.keepInventoryState, "FORCE ON: Players keep their inventory upon death.", "FORCE OFF: Players drop their items upon death.", "DEFAULT: Use the global server setting for keep inventory."), () -> {
                this.keepInventoryState = this.keepInventoryState.next();
                this.keepInventoryBtn.setMessage(Text.literal("Keep Inventory: " + formatRuleState(this.keepInventoryState, defaultMatchRules().keepInventory())));
            });
            y += 30;
            this.pvpEnabledBtn = this.addButton(screen, "PvP Enabled: " + formatRuleState(this.pvpEnabledState, defaultMatchRules().pvpEnabled()), this.layout.mainPanel().x() + 180, y, 200, TriStateTooltip.of(() -> this.pvpEnabledState, "FORCE ON: Players can damage each other.", "FORCE OFF: Players cannot damage each other.", "DEFAULT: Use the global server setting for PvP."), () -> {
                this.pvpEnabledState = this.pvpEnabledState.next();
                this.pvpEnabledBtn.setMessage(Text.literal("PvP Enabled: " + formatRuleState(this.pvpEnabledState, defaultMatchRules().pvpEnabled())));
            });
            y += 30;
            this.doDaylightCycleBtn = this.addButton(screen, "Daylight Cycle: " + formatRuleState(this.doDaylightCycleState, defaultMatchRules().doDaylightCycle()), this.layout.mainPanel().x() + 180, y, 200, TriStateTooltip.of(() -> this.doDaylightCycleState, "FORCE ON: The sun and moon progress normally.", "FORCE OFF: Time is frozen.", "DEFAULT: Use the global server setting for daylight cycle."), () -> {
                this.doDaylightCycleState = this.doDaylightCycleState.next();
                this.doDaylightCycleBtn.setMessage(Text.literal("Daylight Cycle: " + formatRuleState(this.doDaylightCycleState, defaultMatchRules().doDaylightCycle())));
            });
            y += 30;
            this.doWeatherCycleBtn = this.addButton(screen, "Weather Cycle: " + formatRuleState(this.doWeatherCycleState, defaultMatchRules().doWeatherCycle()), this.layout.mainPanel().x() + 180, y, 200, TriStateTooltip.of(() -> this.doWeatherCycleState, "FORCE ON: Rain and thunder will occur naturally.", "FORCE OFF: Weather will remain clear.", "DEFAULT: Use the global server setting for weather cycle."), () -> {
                this.doWeatherCycleState = this.doWeatherCycleState.next();
                this.doWeatherCycleBtn.setMessage(Text.literal("Weather Cycle: " + formatRuleState(this.doWeatherCycleState, defaultMatchRules().doWeatherCycle())));
            });
            y += 30;
            this.fallDamageBtn = this.addButton(screen, "Fall Damage: " + formatRuleState(this.fallDamageState, defaultMatchRules().fallDamage()), this.layout.mainPanel().x() + 180, y, 200, TriStateTooltip.of(() -> this.fallDamageState, "FORCE ON: Players take damage from falling.", "FORCE OFF: Players are immune to fall damage.", "DEFAULT: Use the global server setting for fall damage."), () -> {
                this.fallDamageState = this.fallDamageState.next();
                this.fallDamageBtn.setMessage(Text.literal("Fall Damage: " + formatRuleState(this.fallDamageState, defaultMatchRules().fallDamage())));
            });
            y += 30;
            this.naturalRegenerationBtn = this.addButton(screen, "Natural Regen: " + formatRuleState(this.naturalRegenerationState, defaultMatchRules().naturalRegeneration()), this.layout.mainPanel().x() + 180, y, 200, TriStateTooltip.of(() -> this.naturalRegenerationState, "FORCE ON: Health regenerates naturally.", "FORCE OFF: Health does not regenerate naturally.", "DEFAULT: Use the global server setting for natural regeneration."), () -> {
                this.naturalRegenerationState = this.naturalRegenerationState.next();
                this.naturalRegenerationBtn.setMessage(Text.literal("Natural Regen: " + formatRuleState(this.naturalRegenerationState, defaultMatchRules().naturalRegeneration())));
            });
            y += 30;
            this.announceAdvancementsBtn = this.addButton(screen, "Advancements: " + formatRuleState(this.announceAdvancementsState, defaultMatchRules().announceAdvancements()), this.layout.mainPanel().x() + 180, y, 200, TriStateTooltip.of(() -> this.announceAdvancementsState, "FORCE ON: Player advancements are broadcasted to chat.", "FORCE OFF: Player advancements are not broadcasted.", "DEFAULT: Use the global server setting for advancements."), () -> {
                this.announceAdvancementsState = this.announceAdvancementsState.next();
                this.announceAdvancementsBtn.setMessage(Text.literal("Advancements: " + formatRuleState(this.announceAdvancementsState, defaultMatchRules().announceAdvancements())));
            });
            y += 30;
            this.immediateRespawnBtn = this.addButton(screen, "Immediate Respawn: " + formatRuleState(this.immediateRespawnState, defaultMatchRules().doImmediateRespawn()), this.layout.mainPanel().x() + 180, y, 200, getImmediateRespawnTooltip(), () -> {
                this.immediateRespawnState = this.immediateRespawnState.next();
                this.immediateRespawnBtn.setMessage(Text.literal("Immediate Respawn: " + formatRuleState(this.immediateRespawnState, defaultMatchRules().doImmediateRespawn())));
            });
        }
    }

    protected java.util.function.Supplier<String> getImmediateRespawnTooltip() {
        return TriStateTooltip.of(() -> this.immediateRespawnState, "FORCE ON: Players instantly respawn without the vanilla death screen.", "FORCE OFF: Players see the vanilla death screen.", "DEFAULT: Use the global server setting for immediate respawn.");
    }


    protected dev.frost.miniverse.minigame.core.rules.GlobalMatchRules defaultMatchRules() {
        return dev.frost.miniverse.minigame.core.rules.GlobalMatchRules.defaults();
    }

    protected String formatRuleState(TriState state, boolean defaultVal) {
        if (state == TriState.DEFAULT) {
            return "DEFAULT (§" + (defaultVal ? "aON" : "cOFF") + "§r)";
        }
        if (state == TriState.FORCE_ON) {
            return "FORCE §aON";
        }
        if (state == TriState.FORCE_OFF) {
            return "FORCE §cOFF";
        }
        return state.label();
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
            y += 30;
            context.drawText(textRenderer, Text.literal("Immediate Respawn"), labelX, y, UiTheme.TEXT_MUTED, false);
        }

        this.renderGamemodeForeground(context, textRenderer, mouseX, mouseY, delta);

        if (this.moduleManager.isActive("rules") || this.moduleManager.isActive("settings") || this.moduleManager.isActive("gamerules")) {
            for (TooltipZone zone : this.activeTooltips) {
                if (mouseX >= zone.x() && mouseX < zone.x() + zone.width() && mouseY >= zone.y() && mouseY < zone.y() + zone.height()) {
                    context.drawTooltip(textRenderer, Text.literal(zone.text().get()), mouseX, mouseY);
                    break;
                }
            }
        }
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

    protected void addStepper(SessionScreen screen, TextFieldWidget field, int x, int y, int min, int max, int step) {
        screen.addWidget(ButtonWidget.builder(Text.literal("-"), btn -> {
            try {
                int val = Integer.parseInt(field.getText().trim());
                field.setText(Integer.toString(Math.max(min, val - step)));
            } catch (Exception ignored) {}
        }).dimensions(x, y, 20, 20).build());
        screen.addWidget(ButtonWidget.builder(Text.literal("+"), btn -> {
            try {
                int val = Integer.parseInt(field.getText().trim());
                field.setText(Integer.toString(Math.min(max, val + step)));
            } catch (Exception ignored) {}
        }).dimensions(x + 22, y, 20, 20).build());
    }

    protected ButtonWidget addButton(SessionScreen screen, String label, int x, int y, int width, java.util.function.Supplier<String> tooltip, Runnable action) {
        ButtonWidget btn = new ButtonWidget.Builder(Text.literal(label), b -> action.run())
            .dimensions(x, y, width, 20)
            .build();
        screen.addWidget(btn);
        if (tooltip != null) {
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        }
        return btn;
    }

    protected TextFieldWidget addField(SessionScreen screen, int x, int y, String value, int width, String placeholder, java.util.function.Supplier<String> tooltip) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(256);
        field.setText(value);
        screen.addWidget(field);
        if (tooltip != null) {
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        }
        return field;
    }

    protected TextFieldWidget addField(SessionScreen screen, int x, int y, String value, String placeholder, java.util.function.Supplier<String> tooltip) {
        return this.addField(screen, x, y, value, 120, placeholder, tooltip);
    }

    protected dev.frost.miniverse.client.gui.ui.IntFieldWidget addIntField(SessionScreen screen, int x, int y, int value, int width, String placeholder, String zeroText, java.util.function.Function<Integer, String> activeText) {
        dev.frost.miniverse.client.gui.ui.IntFieldWidget field = new dev.frost.miniverse.client.gui.ui.IntFieldWidget(this.client.textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(256);
        field.setText(Integer.toString(value));
        screen.addWidget(field);
        
        if (zeroText != null && activeText != null) {
            java.util.function.Supplier<String> tooltip = () -> {
                int val = field.getIntValue(0);
                return val <= 0 ? zeroText : activeText.apply(val);
            };
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        }
        return field;
    }

    protected dev.frost.miniverse.client.gui.ui.IntFieldWidget addIntField(SessionScreen screen, int x, int y, int value, String placeholder, String zeroText, java.util.function.Function<Integer, String> activeText) {
        return this.addIntField(screen, x, y, value, 120, placeholder, zeroText, activeText);
    }

    protected dev.frost.miniverse.client.gui.ui.IntFieldWidget addIntField(SessionScreen screen, int x, int y, int value, int width, String placeholder, java.util.function.Function<Integer, String> tooltipFormatter) {
        dev.frost.miniverse.client.gui.ui.IntFieldWidget field = new dev.frost.miniverse.client.gui.ui.IntFieldWidget(this.client.textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(256);
        field.setText(Integer.toString(value));
        screen.addWidget(field);
        
        if (tooltipFormatter != null) {
            java.util.function.Supplier<String> tooltip = () -> tooltipFormatter.apply(field.getIntValue(0));
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        }
        return field;
    }

    protected dev.frost.miniverse.client.gui.ui.IntFieldWidget addIntField(SessionScreen screen, int x, int y, int value, String placeholder, java.util.function.Function<Integer, String> tooltipFormatter) {
        return this.addIntField(screen, x, y, value, 120, placeholder, tooltipFormatter);
    }

    protected ButtonWidget addToggleButton(SessionScreen screen, String labelPrefix, java.util.function.Supplier<Boolean> stateSupplier, int x, int y, int width, String onTooltip, String offTooltip, Runnable onToggle) {
        ButtonWidget btn = new ButtonWidget.Builder(Text.literal(labelPrefix + ": " + (stateSupplier.get() ? "ON" : "OFF")), b -> {
            onToggle.run();
            b.setMessage(Text.literal(labelPrefix + ": " + (stateSupplier.get() ? "ON" : "OFF")));
        }).dimensions(x, y, width, 20).build();
        screen.addWidget(btn);
        
        java.util.function.Supplier<String> tooltip = () -> stateSupplier.get() ? onTooltip : offTooltip;
        int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
        int zoneW = (x + width) - zoneX;
        this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        
        return btn;
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

    protected int readClamped(dev.frost.miniverse.client.gui.ui.IntFieldWidget field, int fallback, int min, int max) {
        if (field == null) return fallback;
        int value = Math.clamp(field.getIntValue(fallback), min, max);
        field.setText(Integer.toString(value));
        return value;
    }

    protected void stepField(dev.frost.miniverse.client.gui.ui.IntFieldWidget field, int min, int max, int delta) {
        int value = this.readClamped(field, min, min, max);
        field.setText(Integer.toString(Math.clamp(value + delta, min, max)));
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
        if (this.keepInventoryState != TriState.DEFAULT) builder.settings().putString("gamerule.keepInventory", this.keepInventoryState == TriState.FORCE_ON ? "true" : "false");
        if (this.pvpEnabledState != TriState.DEFAULT) builder.settings().putString("gamerule.pvpEnabled", this.pvpEnabledState == TriState.FORCE_ON ? "true" : "false");
        if (this.doDaylightCycleState != TriState.DEFAULT) builder.settings().putString("gamerule.doDaylightCycle", this.doDaylightCycleState == TriState.FORCE_ON ? "true" : "false");
        if (this.doWeatherCycleState != TriState.DEFAULT) builder.settings().putString("gamerule.doWeatherCycle", this.doWeatherCycleState == TriState.FORCE_ON ? "true" : "false");
        if (this.fallDamageState != TriState.DEFAULT) builder.settings().putString("gamerule.fallDamage", this.fallDamageState == TriState.FORCE_ON ? "true" : "false");
        if (this.naturalRegenerationState != TriState.DEFAULT) builder.settings().putString("gamerule.naturalRegeneration", this.naturalRegenerationState == TriState.FORCE_ON ? "true" : "false");
        if (this.announceAdvancementsState != TriState.DEFAULT) builder.settings().putString("gamerule.announceAdvancements", this.announceAdvancementsState == TriState.FORCE_ON ? "true" : "false");
    }

    protected abstract ValidationResult validateGamemodeStart();
    protected abstract void buildSessionSettings(SessionPayloadBuilder builder);
    protected abstract void buildSessionGroups(SessionPayloadBuilder builder);
}
