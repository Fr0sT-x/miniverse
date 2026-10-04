package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalDefinition;
import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalSettings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class HordeSurvivalWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget totalWavesField;
    private IntFieldWidget uplinkDurationField;
    private IntFieldWidget intermissionField;
    private IntFieldWidget initialPodFuelField;
    private IntFieldWidget harvestRadiusField;
    private IntFieldWidget fuelDrainField;
    private IntFieldWidget borderSizeField;
    private ButtonWidget emergencyFlaresButton;

    private int totalWaves = HordeSurvivalSettings.DEFAULT_TOTAL_WAVES;
    private int uplinkDurationSeconds = HordeSurvivalSettings.DEFAULT_UPLINK_DURATION_SECONDS;
    private int intermissionSeconds = HordeSurvivalSettings.DEFAULT_INTERMISSION_SECONDS;
    private int initialPodFuel = HordeSurvivalSettings.DEFAULT_INITIAL_POD_FUEL;
    private int harvestRadius = HordeSurvivalSettings.DEFAULT_HARVEST_RADIUS;
    private int fuelDrainPerSecond = HordeSurvivalSettings.DEFAULT_FUEL_DRAIN_PER_SECOND;
    private int borderSize = HordeSurvivalSettings.DEFAULT_BORDER_SIZE;
    private boolean emergencyFlaresEnabled = true;

    public HordeSurvivalWorkspaceView() {
        super(HordeSurvivalDefinition.ID);
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Survivors", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Survivors", "Setup", "Select participating survivors.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure wave counts and match rules.", UiTheme.ACCENT_BLUE);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            WorkspaceTooltip wavesTooltip = WorkspaceTooltip.dynamic(() -> "Survive " + (this.totalWavesField != null ? this.totalWavesField.getIntValue(this.totalWaves) : this.totalWaves) + " waves before the final Extraction Run.");
            this.rulesLayout.addRow(
                "Total Waves", wavesTooltip, (s, x, y, w) -> {
                    this.totalWavesField = this.addIntField(s, "Total Waves", x, y, this.totalWaves, 1, 100, w, wavesTooltip);
                }
            );

            WorkspaceTooltip uplinkTooltip = WorkspaceTooltip.dynamic(() -> "Data transmission requires " + (this.uplinkDurationField != null ? this.uplinkDurationField.getIntValue(this.uplinkDurationSeconds) : this.uplinkDurationSeconds) + " seconds of active fueled signal.");
            this.rulesLayout.addRow(
                "Uplink Duration (s)", uplinkTooltip, (s, x, y, w) -> {
                    this.uplinkDurationField = this.addIntField(s, "Uplink Duration", x, y, this.uplinkDurationSeconds, 15, 600, w, uplinkTooltip);
                }
            );

            WorkspaceTooltip intermissionTooltip = WorkspaceTooltip.dynamic(() -> "Survivors have " + (this.intermissionField != null ? this.intermissionField.getIntValue(this.intermissionSeconds) : this.intermissionSeconds) + " seconds of peace between waves to shop and travel.");
            this.rulesLayout.addRow(
                "Intermission (s)", intermissionTooltip, (s, x, y, w) -> {
                    this.intermissionField = this.addIntField(s, "Intermission seconds", x, y, this.intermissionSeconds, 5, 600, w, intermissionTooltip);
                }
            );

            WorkspaceTooltip fuelTooltip = WorkspaceTooltip.dynamic(() -> "Transmitter starts with " + (this.initialPodFuelField != null ? this.initialPodFuelField.getIntValue(this.initialPodFuel) : this.initialPodFuel) + "% battery charge.");
            this.rulesLayout.addRow(
                "Initial Fuel (%)", fuelTooltip, (s, x, y, w) -> {
                    this.initialPodFuelField = this.addIntField(s, "Initial Pod Fuel", x, y, this.initialPodFuel, 10, 100, w, fuelTooltip);
                }
            );

            WorkspaceTooltip radiusTooltip = WorkspaceTooltip.dynamic(() -> "Mobs killed within " + (this.harvestRadiusField != null ? this.harvestRadiusField.getIntValue(this.harvestRadius) : this.harvestRadius) + "m of the Pod grant +2% fuel.");
            this.rulesLayout.addRow(
                "Ring Radius (m)", radiusTooltip, (s, x, y, w) -> {
                    this.harvestRadiusField = this.addIntField(s, "Fuel Harvest Radius", x, y, this.harvestRadius, 5, 25, w, radiusTooltip);
                }
            );

            WorkspaceTooltip drainTooltip = WorkspaceTooltip.dynamic(() -> "Transmitter drains " + (this.fuelDrainField != null ? this.fuelDrainField.getIntValue(this.fuelDrainPerSecond) : this.fuelDrainPerSecond) + "% fuel per second while transmitting.");
            this.rulesLayout.addRow(
                "Fuel Drain (%/s)", drainTooltip, (s, x, y, w) -> {
                    this.fuelDrainField = this.addIntField(s, "Fuel Drain Rate", x, y, this.fuelDrainPerSecond, 1, 10, w, drainTooltip);
                }
            );

            WorkspaceTooltip borderTooltip = WorkspaceTooltip.dynamic(() -> "Play area boundary diameter in blocks (" + (this.borderSizeField != null ? this.borderSizeField.getIntValue(this.borderSize) : this.borderSize) + ").");
            this.rulesLayout.addRow(
                "Border Size", borderTooltip, (s, x, y, w) -> {
                    this.borderSizeField = this.addIntField(s, "Border size", x, y, this.borderSize, 50, 10000, w, borderTooltip);
                }
            );

            WorkspaceTooltip flaresTooltip = WorkspaceTooltip.toggle(() -> this.emergencyFlaresEnabled,
                "Survivors can buy and use single-transaction pocket flares.",
                "Emergency flares are disabled.");
            this.rulesLayout.addRow(
                "Emergency Flares", flaresTooltip, (s, x, y, w) -> {
                    this.emergencyFlaresButton = this.addToggleButton(s, "Emergency Flares", () -> this.emergencyFlaresEnabled, x, y, w,
                        flaresTooltip,
                        () -> this.emergencyFlaresEnabled = !this.emergencyFlaresEnabled);
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
            Text.literal("Total Waves: " + this.totalWaves),
            Text.literal("Uplink Duration: " + this.uplinkDurationSeconds + "s"),
            Text.literal("Intermission: " + this.intermissionSeconds + "s"),
            Text.literal("Initial Fuel: " + this.initialPodFuel + "%"),
            Text.literal("Ring Radius: " + this.harvestRadius + "m"),
            Text.literal("Drain: " + this.fuelDrainPerSecond + "%/s"),
            Text.literal("Border Size: " + this.borderSize + "m"),
            Text.literal("Flares: " + (this.emergencyFlaresEnabled ? "ON" : "OFF"))
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
            this.totalWaves = readClamped(this.totalWavesField, this.totalWaves, 1, 100);
            this.uplinkDurationSeconds = readClamped(this.uplinkDurationField, this.uplinkDurationSeconds, 15, 600);
            this.intermissionSeconds = readClamped(this.intermissionField, this.intermissionSeconds, 5, 600);
            this.initialPodFuel = readClamped(this.initialPodFuelField, this.initialPodFuel, 10, 100);
            this.harvestRadius = readClamped(this.harvestRadiusField, this.harvestRadius, 5, 25);
            this.fuelDrainPerSecond = readClamped(this.fuelDrainField, this.fuelDrainPerSecond, 1, 10);
            this.borderSize = readClamped(this.borderSizeField, this.borderSize, 50, 10000);
        }
    }

    @Override
    public String title() {
        return "Horde Survival Setup";
    }

    @Override
    public String subtitle() {
        return "Co-op wave survival and extraction";
    }

    @Override
    public String gameId() {
        return HordeSurvivalDefinition.ID;
    }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.playerGrid.getMembers("selected").isEmpty()) {
            return ValidationResult.error("Select at least one survivor.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putInt("totalWaves", this.totalWaves);
        builder.settings().putInt("uplinkDurationSeconds", this.uplinkDurationSeconds);
        builder.settings().putInt("intermissionSeconds", this.intermissionSeconds);
        builder.settings().putInt("initialPodFuel", this.initialPodFuel);
        builder.settings().putInt("harvestRadius", this.harvestRadius);
        builder.settings().putInt("fuelDrainPerSecond", this.fuelDrainPerSecond);
        builder.settings().putInt("borderSize", this.borderSize);
        builder.settings().putBoolean("emergencyFlaresEnabled", this.emergencyFlaresEnabled);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Survivors", this.playerGrid.getMembers("selected"));
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("totalWaves")) this.totalWaves = settings.getInt("totalWaves");
        if (settings.contains("uplinkDurationSeconds")) this.uplinkDurationSeconds = settings.getInt("uplinkDurationSeconds");
        if (settings.contains("intermissionSeconds")) this.intermissionSeconds = settings.getInt("intermissionSeconds");
        if (settings.contains("initialPodFuel")) this.initialPodFuel = settings.getInt("initialPodFuel");
        if (settings.contains("harvestRadius")) this.harvestRadius = settings.getInt("harvestRadius");
        if (settings.contains("fuelDrainPerSecond")) this.fuelDrainPerSecond = settings.getInt("fuelDrainPerSecond");
        if (settings.contains("borderSize")) this.borderSize = settings.getInt("borderSize");
        if (settings.contains("emergencyFlaresEnabled")) this.emergencyFlaresEnabled = settings.getBoolean("emergencyFlaresEnabled");

        if (this.totalWavesField != null) this.totalWavesField.setText(String.valueOf(this.totalWaves));
        if (this.uplinkDurationField != null) this.uplinkDurationField.setText(String.valueOf(this.uplinkDurationSeconds));
        if (this.intermissionField != null) this.intermissionField.setText(String.valueOf(this.intermissionSeconds));
        if (this.initialPodFuelField != null) this.initialPodFuelField.setText(String.valueOf(this.initialPodFuel));
        if (this.harvestRadiusField != null) this.harvestRadiusField.setText(String.valueOf(this.harvestRadius));
        if (this.fuelDrainField != null) this.fuelDrainField.setText(String.valueOf(this.fuelDrainPerSecond));
        if (this.borderSizeField != null) this.borderSizeField.setText(String.valueOf(this.borderSize));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.totalWaves = HordeSurvivalSettings.DEFAULT_TOTAL_WAVES;
        this.uplinkDurationSeconds = HordeSurvivalSettings.DEFAULT_UPLINK_DURATION_SECONDS;
        this.intermissionSeconds = HordeSurvivalSettings.DEFAULT_INTERMISSION_SECONDS;
        this.initialPodFuel = HordeSurvivalSettings.DEFAULT_INITIAL_POD_FUEL;
        this.harvestRadius = HordeSurvivalSettings.DEFAULT_HARVEST_RADIUS;
        this.fuelDrainPerSecond = HordeSurvivalSettings.DEFAULT_FUEL_DRAIN_PER_SECOND;
        this.borderSize = HordeSurvivalSettings.DEFAULT_BORDER_SIZE;
        this.emergencyFlaresEnabled = true;

        if (this.totalWavesField != null) this.totalWavesField.setText(String.valueOf(this.totalWaves));
        if (this.uplinkDurationField != null) this.uplinkDurationField.setText(String.valueOf(this.uplinkDurationSeconds));
        if (this.intermissionField != null) this.intermissionField.setText(String.valueOf(this.intermissionSeconds));
        if (this.initialPodFuelField != null) this.initialPodFuelField.setText(String.valueOf(this.initialPodFuel));
        if (this.harvestRadiusField != null) this.harvestRadiusField.setText(String.valueOf(this.harvestRadius));
        if (this.fuelDrainField != null) this.fuelDrainField.setText(String.valueOf(this.fuelDrainPerSecond));
        if (this.borderSizeField != null) this.borderSizeField.setText(String.valueOf(this.borderSize));
    }
}
