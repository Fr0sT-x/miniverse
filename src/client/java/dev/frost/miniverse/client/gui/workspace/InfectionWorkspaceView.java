package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import dev.frost.miniverse.minigame.impl.infection.InfectionDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class InfectionWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget durationField;
    private IntFieldWidget infectedField;
    private IntFieldWidget respawnField;
    private ButtonWidget friendlyFireButton;

    private int durationSeconds = 600;
    private int startingInfected = 1;
    private int respawnDelay = 3;
    private boolean allowFriendlyFire = false;

    public InfectionWorkspaceView() {
        super("infection");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for Infection.", UiTheme.ACCENT_RED, "Valid Infection Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Tune duration, infected count, respawn delay, and friendly fire.", UiTheme.ACCENT_BLUE);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            WorkspaceTooltip durationTooltip = WorkspaceTooltip.dynamic(() -> "Total match duration will be " + (this.durationField != null ? this.durationField.getIntValue(this.durationSeconds) : this.durationSeconds) + " seconds.");
            this.rulesLayout.addRow(
                "Match Duration", durationTooltip, (s, x, y, w) -> {
                    this.durationField = this.addIntField(s, "Match duration", x, y, this.durationSeconds, 10, 3600, w, durationTooltip);
                }
            );

            WorkspaceTooltip infectedTooltip = WorkspaceTooltip.dynamic(() -> "The match will start with " + (this.infectedField != null ? this.infectedField.getIntValue(this.startingInfected) : this.startingInfected) + " alpha infected.");
            this.rulesLayout.addRow(
                "Starting Infected", infectedTooltip, (s, x, y, w) -> {
                    this.infectedField = this.addIntField(s, "Starting infected", x, y, this.startingInfected, 1, 100, w, infectedTooltip);
                }
            );

            WorkspaceTooltip respawnTooltip = WorkspaceTooltip.dynamic(() -> {
                int val = this.respawnField != null ? this.respawnField.getIntValue(this.respawnDelay) : this.respawnDelay;
                return val <= 0 ? "Infected will respawn instantly." : "Infected will be forced to spectate for " + val + " seconds before respawning.";
            });
            this.rulesLayout.addRow(
                "Respawn Delay", respawnTooltip, (s, x, y, w) -> {
                    this.respawnField = this.addIntField(s, "Respawn delay", x, y, this.respawnDelay, 0, 60, w, respawnTooltip);
                }
            );

            WorkspaceTooltip ffTooltip = WorkspaceTooltip.toggle(() -> this.allowFriendlyFire,
                "Survivors can damage other survivors.",
                "Survivors cannot damage each other.");
            this.rulesLayout.addRow(
                "Friendly Fire", ffTooltip, (s, x, y, w) -> {
                    this.friendlyFireButton = this.addToggleButton(s, "Friendly Fire", () -> this.allowFriendlyFire, x, y, w,
                        ffTooltip,
                        () -> this.allowFriendlyFire = !this.allowFriendlyFire);
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
    protected java.util.List<Text> getSummaryLines() {
        return java.util.List.of(
            Text.literal("Players: " + this.playerGrid.getMembers("selected").size()),
            Text.literal("Match Duration: " + this.durationSeconds + "s"),
            Text.literal("Starting Infected: " + this.startingInfected)
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
            this.durationSeconds = readClamped(this.durationField, this.durationSeconds, 10, 3600);
            this.startingInfected = readClamped(this.infectedField, this.startingInfected, 1, 100);
            this.respawnDelay = readClamped(this.respawnField, this.respawnDelay, 0, 60);
        }
    }

    @Override
    public String title() { return "Infection Setup"; }

    @Override
    public String subtitle() { return "Map-aware reference gamemode"; }

    @Override
    public String gameId() { return InfectionDefinition.ID; }

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
        builder.settings().putString("seedMode", "random");
        builder.settings().putInt("matchDurationSeconds", this.durationSeconds);
        builder.settings().putInt("startingInfectedCount", this.startingInfected);
        builder.settings().putInt("respawnDelaySeconds", this.respawnDelay);
        builder.settings().putBoolean("allowFriendlyFire", this.allowFriendlyFire);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("matchDurationSeconds")) this.durationSeconds = settings.getInt("matchDurationSeconds");
        if (settings.contains("startingInfectedCount")) this.startingInfected = settings.getInt("startingInfectedCount");
        if (settings.contains("respawnDelaySeconds")) this.respawnDelay = settings.getInt("respawnDelaySeconds");
        if (settings.contains("allowFriendlyFire")) this.allowFriendlyFire = settings.getBoolean("allowFriendlyFire");

        if (this.durationField != null) this.durationField.setText(String.valueOf(this.durationSeconds));
        if (this.infectedField != null) this.infectedField.setText(String.valueOf(this.startingInfected));
        if (this.respawnField != null) this.respawnField.setText(String.valueOf(this.respawnDelay));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.durationSeconds = 300;
        this.startingInfected = 1;
        this.respawnDelay = 3;
        this.allowFriendlyFire = false;

        if (this.durationField != null) this.durationField.setText(String.valueOf(this.durationSeconds));
        if (this.infectedField != null) this.infectedField.setText(String.valueOf(this.startingInfected));
        if (this.respawnField != null) this.respawnField.setText(String.valueOf(this.respawnDelay));
    }

}
