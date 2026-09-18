package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
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

            this.rulesLayout.addRow(
                "Total Waves", (s, x, y, w) -> {
                    this.totalWavesField = this.addIntField(s, x, y, this.totalWaves, w, "Total Waves", val -> "Survive " + val + " waves before the final Extraction Run.");
                }
            );

            this.rulesLayout.addRow(
                "Uplink Duration (s)", (s, x, y, w) -> {
                    this.uplinkDurationField = this.addIntField(s, x, y, this.uplinkDurationSeconds, w, "Uplink Duration (s)", val -> "Data transmission requires " + val + " seconds of active fueled signal.");
                }
            );

            this.rulesLayout.addRow(
                "Intermission (s)", (s, x, y, w) -> {
                    this.intermissionField = this.addIntField(s, x, y, this.intermissionSeconds, w, "Intermission seconds", val -> "Survivors have " + val + " seconds of peace between waves to shop and travel.");
                }
            );

            this.rulesLayout.addRow(
                "Initial Fuel (%)", (s, x, y, w) -> {
                    this.initialPodFuelField = this.addIntField(s, x, y, this.initialPodFuel, w, "Initial Pod Fuel", val -> "Transmitter starts with " + val + "% battery charge.");
                }
            );

            this.rulesLayout.addRow(
                "Ring Radius (m)", (s, x, y, w) -> {
                    this.harvestRadiusField = this.addIntField(s, x, y, this.harvestRadius, w, "Fuel Harvest Radius", val -> "Mobs killed within " + val + "m of the Pod grant +2% fuel.");
                }
            );

            this.rulesLayout.addRow(
                "Fuel Drain (%/s)", (s, x, y, w) -> {
                    this.fuelDrainField = this.addIntField(s, x, y, this.fuelDrainPerSecond, w, "Fuel Drain Rate", val -> "Transmitter drains " + val + "% fuel per second while transmitting.");
                }
            );

            this.rulesLayout.addRow(
                "Border Size", (s, x, y, w) -> {
                    this.borderSizeField = this.addIntField(s, x, y, this.borderSize, w, "Border size", val -> "Play area boundary diameter in blocks.");
                }
            );

            this.rulesLayout.addRow(
                "Emergency Flares", (s, x, y, w) -> {
                    this.emergencyFlaresButton = this.addToggleButton(s, "Emergency Flares", () -> this.emergencyFlaresEnabled, x, y, w,
                        new BinaryTooltip("Survivors can buy and use single-transaction pocket flares.", "Emergency flares are disabled."),
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
}
