package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.PillarsOfFortuneDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class PillarsOfFortuneWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget timeLimitField;
    private IntFieldWidget lootDropIntervalField;
    private ButtonWidget activeModifierButton;

    private int timeLimitSeconds = 600;
    private int lootDropIntervalSeconds = 15;
    private String activeModifier = "none";

    public PillarsOfFortuneWorkspaceView() {
        super("pillarsoffortune");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for Pillars of Fortune.", UiTheme.ACCENT_BLUE, "Valid Pillars of Fortune Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure time limit, loot drops, and modifiers.", UiTheme.ACCENT_BLUE);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            WorkspaceTooltip timeTooltip = WorkspaceTooltip.dynamic(() -> "Match ends in a draw after " + (this.timeLimitField != null ? this.timeLimitField.getIntValue(this.timeLimitSeconds) : this.timeLimitSeconds) + " seconds.");
            this.rulesLayout.addRow(
                "Time Limit", timeTooltip, (s, x, y, w) -> {
                    this.timeLimitField = this.addIntField(s, "Time limit seconds", x, y, this.timeLimitSeconds, 60, 3600, w, timeTooltip);
                }
            );

            WorkspaceTooltip lootTooltip = WorkspaceTooltip.dynamic(() -> "Random loot drops every " + (this.lootDropIntervalField != null ? this.lootDropIntervalField.getIntValue(this.lootDropIntervalSeconds) : this.lootDropIntervalSeconds) + " seconds.");
            this.rulesLayout.addRow(
                "Loot Interval", lootTooltip, (s, x, y, w) -> {
                    this.lootDropIntervalField = this.addIntField(s, "Loot drop interval", x, y, this.lootDropIntervalSeconds, 5, 300, w, lootTooltip);
                }
            );

            WorkspaceTooltip modTooltip = WorkspaceTooltip.cycle(() -> modifierIndex(this.activeModifier), new String[]{
                "No game modifiers.",
                "Players swap positions randomly.",
                "Inventories are shuffled randomly."
            });
            this.rulesLayout.addRow(
                "Active Modifier", modTooltip, (s, x, y, w) -> {
                    this.activeModifierButton = this.addCycleButton(s, () -> "Modifier: " + this.activeModifier.toUpperCase(), 
                        () -> modifierIndex(this.activeModifier), x, y, w, modTooltip, 3, () -> {
                        this.activeModifier = nextModifier(this.activeModifier);
                        this.activeModifierButton.setMessage(Text.literal("Modifier: " + this.activeModifier.toUpperCase()));
                    });
                }
            );
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
    }

    @Override
    protected List<Text> getSummaryLines() {
        return List.of(
            Text.literal("Time Limit: " + this.timeLimitSeconds + "s"),
            Text.literal("Loot Drop: " + this.lootDropIntervalSeconds + "s"),
            Text.literal("Modifier: " + this.activeModifier.toUpperCase())
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.timeLimitSeconds = readClamped(this.timeLimitField, this.timeLimitSeconds, 60, 3600);
            this.lootDropIntervalSeconds = readClamped(this.lootDropIntervalField, this.lootDropIntervalSeconds, 5, 300);
        }
    }

    @Override
    public String title() {
        return "Pillars of Fortune Setup";
    }

    @Override
    public String subtitle() {
        return "Workspace-based roster selection";
    }

    @Override
    public String gameId() {
        return PillarsOfFortuneDefinition.ID;
    }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.playerGrid.getMembers("selected").size() < 2) {
            return ValidationResult.error("Select at least two players.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putInt("timeLimitSeconds", this.timeLimitSeconds);
        builder.settings().putInt("lootDropIntervalSeconds", this.lootDropIntervalSeconds);
        builder.settings().putString("activeModifier", this.activeModifier);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("timeLimitSeconds")) this.timeLimitSeconds = settings.getInt("timeLimitSeconds");
        if (settings.contains("lootDropIntervalSeconds")) this.lootDropIntervalSeconds = settings.getInt("lootDropIntervalSeconds");
        if (settings.contains("activeModifier")) this.activeModifier = settings.getString("activeModifier");

        if (this.timeLimitField != null) this.timeLimitField.setText(String.valueOf(this.timeLimitSeconds));
        if (this.lootDropIntervalField != null) this.lootDropIntervalField.setText(String.valueOf(this.lootDropIntervalSeconds));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.timeLimitSeconds = 600;
        this.lootDropIntervalSeconds = 15;
        this.activeModifier = "none";

        if (this.timeLimitField != null) this.timeLimitField.setText(String.valueOf(this.timeLimitSeconds));
        if (this.lootDropIntervalField != null) this.lootDropIntervalField.setText(String.valueOf(this.lootDropIntervalSeconds));
    }

    private int modifierIndex(String mod) {
        if (mod.equals("swapper")) return 1;
        if (mod.equals("shuffle")) return 2;
        return 0;
    }

    private String nextModifier(String current) {
        if (current.equals("none")) return "swapper";
        if (current.equals("swapper")) return "shuffle";
        return "none";
    }
}
