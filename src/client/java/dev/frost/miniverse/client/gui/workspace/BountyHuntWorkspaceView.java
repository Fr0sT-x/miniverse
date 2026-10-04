package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class BountyHuntWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();
    
    private IntFieldWidget pointsToWinField;
    private IntFieldWidget gracePeriodField;
    private IntFieldWidget targetSwapIntervalField;
    private IntFieldWidget respawnDelayField;
    private IntFieldWidget compassCooldownField;
    private TextFieldWidget trackerItemField;
    private ButtonWidget trackerToggle;
    private ButtonWidget netherToggle;
    private ButtonWidget hvtToggle;
    private ButtonWidget revengeToggle;

    private int scoreToWin = 1000;
    private int gracePeriodSeconds = 120;
    private int targetSwapIntervalSeconds = 600;
    private int respawnDelaySeconds = 5;
    private int compassCooldownSeconds = 2;
    private boolean trackerEnabled = true;
    private boolean netherTrackingEnabled = true;
    private boolean highValueTargetEnabled = false;
    private boolean revengeAssignmentEnabled = false;
    private String trackerItemId = "minecraft:compass";

    public BountyHuntWorkspaceView() {
        super("bountyhunt");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure scoring, timers, and tracking.", UiTheme.ACCENT_BLUE);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addHeading("Match Settings");
            WorkspaceTooltip pointsTooltip = WorkspaceTooltip.dynamic(() -> "Points needed to win the match (" + (this.pointsToWinField != null ? this.pointsToWinField.getIntValue(this.scoreToWin) : this.scoreToWin) + " pts).");
            this.rulesLayout.addRow(
                "Points To Win", pointsTooltip, (s, x, y, w) -> {
                    this.pointsToWinField = this.addIntField(s, "Points to Win", x, y, this.scoreToWin, 10, 100000, w, pointsTooltip);
                }
            );

            WorkspaceTooltip graceTooltip = WorkspaceTooltip.dynamic(() -> {
                int val = this.gracePeriodField != null ? this.gracePeriodField.getIntValue(this.gracePeriodSeconds) : this.gracePeriodSeconds;
                return val <= 0 ? "No grace period." : "Players have " + val + " seconds of peace.";
            });
            WorkspaceTooltip swapTooltip = WorkspaceTooltip.dynamic(() -> {
                int val = this.targetSwapIntervalField != null ? this.targetSwapIntervalField.getIntValue(this.targetSwapIntervalSeconds) : this.targetSwapIntervalSeconds;
                return val <= 0 ? "Targets will not rotate." : "Targets rotate every " + val + " seconds.";
            });

            this.rulesLayout.addRow(
                "Grace Period", graceTooltip, (s, x, y, w) -> {
                    this.gracePeriodField = this.addIntField(s, "Grace seconds", x, y, this.gracePeriodSeconds, 0, 3600, w, graceTooltip);
                    this.addStepper(s, this.gracePeriodField, x + w + 4, y, 0, 600, 10);
                },
                "Target Shuffle", swapTooltip, (s, x, y, w) -> {
                    this.targetSwapIntervalField = this.addIntField(s, "Swap seconds", x, y, this.targetSwapIntervalSeconds, 10, 3600, w, swapTooltip);
                    this.addStepper(s, this.targetSwapIntervalField, x + w + 4, y, 10, 3600, 30);
                }
            );

            WorkspaceTooltip respawnTooltip = WorkspaceTooltip.dynamic(() -> {
                int val = this.respawnDelayField != null ? this.respawnDelayField.getIntValue(this.respawnDelaySeconds) : this.respawnDelaySeconds;
                return val <= 0 ? "Instant respawn." : "Dead players spectate for " + val + " seconds.";
            });
            this.rulesLayout.addRow(
                "Respawn Delay", respawnTooltip, (s, x, y, w) -> {
                    this.respawnDelayField = this.addIntField(s, "Respawn delay", x, y, this.respawnDelaySeconds, 0, 300, w, respawnTooltip);
                    this.addStepper(s, this.respawnDelayField, x + w + 4, y, 0, 300, 1);
                }
            );

            this.rulesLayout.addHeading("Tracking Options");
            WorkspaceTooltip trackerTooltip = WorkspaceTooltip.toggle(() -> this.trackerEnabled,
                "Players receive a tracker pointing to their target.",
                "Tracking is disabled.");
            WorkspaceTooltip netherTooltip = WorkspaceTooltip.toggle(() -> this.netherTrackingEnabled,
                "Trackers work when the target is in a different dimension.",
                "Trackers spin randomly if the target is in a different dimension.");

            this.rulesLayout.addRow(
                "Tracker Toggle", trackerTooltip, (s, x, y, w) -> {
                    this.trackerToggle = this.addToggleButton(s, "Tracker", () -> this.trackerEnabled, x, y, w, trackerTooltip,
                        () -> this.trackerEnabled = !this.trackerEnabled);
                },
                "Nether Toggle", netherTooltip, (s, x, y, w) -> {
                    this.netherToggle = this.addToggleButton(s, "Nether Tracking", () -> this.netherTrackingEnabled, x, y, w, netherTooltip,
                        () -> this.netherTrackingEnabled = !this.netherTrackingEnabled);
                }
            );

            WorkspaceTooltip cooldownTooltip = WorkspaceTooltip.dynamic(() -> {
                int val = this.compassCooldownField != null ? this.compassCooldownField.getIntValue(this.compassCooldownSeconds) : this.compassCooldownSeconds;
                return val <= 0 ? "No tracker cooldown." : "Players must wait " + val + " seconds between tracker uses.";
            });
            WorkspaceTooltip itemTooltip = WorkspaceTooltip.of("The item id used for tracking targets.");
            this.rulesLayout.addRow(
                "Cooldown", cooldownTooltip, (s, x, y, w) -> {
                    this.compassCooldownField = this.addIntField(s, "Cooldown seconds", x, y, this.compassCooldownSeconds, 0, 300, w, cooldownTooltip);
                },
                "Tracker Item", itemTooltip, (s, x, y, w) -> {
                    this.trackerItemField = this.addField(s, x, y, this.trackerItemId, w, "Tracker item", () -> "The item id used for tracking targets.");
                }
            );

            this.rulesLayout.addHeading("Bonus Features");
            WorkspaceTooltip hvtTooltip = WorkspaceTooltip.toggle(() -> this.highValueTargetEnabled,
                "The High Value Target system is active.",
                "The High Value Target system is disabled.");
            WorkspaceTooltip revengeTooltip = WorkspaceTooltip.toggle(() -> this.revengeAssignmentEnabled,
                "Players can be assigned their killer as a target.",
                "Players will not be assigned their killer as a target.");

            this.rulesLayout.addRow(
                "High Value Target", hvtTooltip, (s, x, y, w) -> {
                    this.hvtToggle = this.addToggleButton(s, "HVT", () -> this.highValueTargetEnabled, x, y, w, hvtTooltip,
                        () -> this.highValueTargetEnabled = !this.highValueTargetEnabled);
                },
                "Revenge Contracts", revengeTooltip, (s, x, y, w) -> {
                    this.revengeToggle = this.addToggleButton(s, "Revenge", () -> this.revengeAssignmentEnabled, x, y, w, revengeTooltip,
                        () -> this.revengeAssignmentEnabled = !this.revengeAssignmentEnabled);
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
            Text.literal("Score To Win: " + this.scoreToWin)
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
            this.scoreToWin = readClamped(this.pointsToWinField, this.scoreToWin, 10, 100000);
            this.gracePeriodSeconds = readClamped(this.gracePeriodField, this.gracePeriodSeconds, 0, 3600);
            this.targetSwapIntervalSeconds = readClamped(this.targetSwapIntervalField, this.targetSwapIntervalSeconds, 10, 3600);
            this.respawnDelaySeconds = readClamped(this.respawnDelayField, this.respawnDelaySeconds, 0, 300);
            this.compassCooldownSeconds = readClamped(this.compassCooldownField, this.compassCooldownSeconds, 0, 300);
            if (this.trackerItemField != null) {
                this.trackerItemId = this.trackerItemField.getText().trim();
            }
        }
    }

    @Override
    public String title() {
        return "Bounty Hunt Setup";
    }

    @Override
    public String subtitle() {
        return "Workspace-based roster selection";
    }

    @Override
    public String gameId() {
        return BountyHuntDefinition.ID;
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
        builder.settings().putInt("scoreToWin", this.scoreToWin);
        builder.settings().putInt("gracePeriodSeconds", this.gracePeriodSeconds);
        builder.settings().putInt("targetSwapIntervalSeconds", this.targetSwapIntervalSeconds);
        builder.settings().putInt("respawnDelaySeconds", this.respawnDelaySeconds);
        builder.settings().putInt("compassCooldownSeconds", this.compassCooldownSeconds);
        builder.settings().putBoolean("trackerEnabled", this.trackerEnabled);
        builder.settings().putBoolean("netherTracking", this.netherTrackingEnabled);
        builder.settings().putString("trackerItemId", this.trackerItemId);
        builder.settings().putBoolean("highValueTargetEnabled", this.highValueTargetEnabled);
        builder.settings().putBoolean("revengeAssignmentEnabled", this.revengeAssignmentEnabled);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("scoreToWin")) this.scoreToWin = settings.getInt("scoreToWin");
        if (settings.contains("gracePeriodSeconds")) this.gracePeriodSeconds = settings.getInt("gracePeriodSeconds");
        if (settings.contains("targetSwapIntervalSeconds")) this.targetSwapIntervalSeconds = settings.getInt("targetSwapIntervalSeconds");
        if (settings.contains("respawnDelaySeconds")) this.respawnDelaySeconds = settings.getInt("respawnDelaySeconds");
        if (settings.contains("compassCooldownSeconds")) this.compassCooldownSeconds = settings.getInt("compassCooldownSeconds");
        if (settings.contains("trackerEnabled")) this.trackerEnabled = settings.getBoolean("trackerEnabled");
        if (settings.contains("netherTracking")) this.netherTrackingEnabled = settings.getBoolean("netherTracking");
        if (settings.contains("trackerItemId")) this.trackerItemId = settings.getString("trackerItemId");
        if (settings.contains("highValueTargetEnabled")) this.highValueTargetEnabled = settings.getBoolean("highValueTargetEnabled");
        if (settings.contains("revengeAssignmentEnabled")) this.revengeAssignmentEnabled = settings.getBoolean("revengeAssignmentEnabled");

        if (this.pointsToWinField != null) this.pointsToWinField.setText(String.valueOf(this.scoreToWin));
        if (this.gracePeriodField != null) this.gracePeriodField.setText(String.valueOf(this.gracePeriodSeconds));
        if (this.targetSwapIntervalField != null) this.targetSwapIntervalField.setText(String.valueOf(this.targetSwapIntervalSeconds));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelaySeconds));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.scoreToWin = 1000;
        this.gracePeriodSeconds = 120;
        this.targetSwapIntervalSeconds = 600;
        this.respawnDelaySeconds = 5;
        this.compassCooldownSeconds = 2;
        this.trackerEnabled = true;
        this.netherTrackingEnabled = true;
        this.highValueTargetEnabled = false;
        this.revengeAssignmentEnabled = false;
        this.trackerItemId = "minecraft:compass";

        if (this.pointsToWinField != null) this.pointsToWinField.setText(String.valueOf(this.scoreToWin));
        if (this.gracePeriodField != null) this.gracePeriodField.setText(String.valueOf(this.gracePeriodSeconds));
        if (this.targetSwapIntervalField != null) this.targetSwapIntervalField.setText(String.valueOf(this.targetSwapIntervalSeconds));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelaySeconds));
    }

    private static String toggleLabel(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }
}
