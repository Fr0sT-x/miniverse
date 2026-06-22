package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class BountyHuntWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();
    
    private TextFieldWidget pointsToWinField;
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
        this.useGameRules();
        this.moduleManager.register("summary", "S", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT_GREEN);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            int cx1 = this.layout.mainPanel().x() + 150;
            int cx2 = this.layout.mainPanel().x() + 500;
            int sx1 = this.layout.mainPanel().x() + 250;
            int sx2 = this.layout.mainPanel().x() + 600;

            // Row 1
            this.pointsToWinField = this.addField(screen, cx1, this.layout.mainPanel().y() + 124, String.valueOf(this.scoreToWin), "Points to win", () -> "Points needed to win.");
            
            // Row 2
            this.gracePeriodField = this.addIntField(screen, cx1, this.layout.mainPanel().y() + 156, this.gracePeriodSeconds, "Grace seconds",
                "No grace period.",
                val -> "Players have " + val + " seconds of peace.");
            this.addStepper(screen, this.gracePeriodField, sx1, this.layout.mainPanel().y() + 156, 0, 600, 10);
            
            this.targetSwapIntervalField = this.addIntField(screen, cx2, this.layout.mainPanel().y() + 156, this.targetSwapIntervalSeconds, "Swap seconds",
                "Targets will not rotate.",
                val -> "Targets rotate every " + val + " seconds.");
            this.addStepper(screen, this.targetSwapIntervalField, sx2, this.layout.mainPanel().y() + 156, 10, 3600, 30);

            this.respawnDelayField = this.addIntField(screen, cx1, this.layout.mainPanel().y() + 188, this.respawnDelaySeconds, "Respawn delay",
                "Instant respawn.",
                val -> "Dead players spectate for " + val + " seconds.");
            this.addStepper(screen, this.respawnDelayField, sx1, this.layout.mainPanel().y() + 188, 0, 300, 1);

            // Row 3
            this.trackerToggle = this.addToggleButton(screen, "Tracker", () -> this.trackerEnabled, cx1, this.layout.mainPanel().y() + 208, 170,
                new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("Players receive a tracker pointing to their target.", "Tracking is disabled."),
                () -> this.trackerEnabled = !this.trackerEnabled);
            this.netherToggle = this.addToggleButton(screen, "Nether Tracking", () -> this.netherTrackingEnabled, cx2, this.layout.mainPanel().y() + 208, 170,
                new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("ON: Trackers work when the target is in a different dimension.", "OFF: Trackers spin randomly if the target is in a different dimension."),
                () -> this.netherTrackingEnabled = !this.netherTrackingEnabled);

            // Row 4
            this.compassCooldownField = this.addIntField(screen, cx1, this.layout.mainPanel().y() + 240, this.compassCooldownSeconds, "Cooldown seconds",
                "No tracker cooldown.",
                val -> "Players must wait " + val + " seconds between tracker uses.");
            this.trackerItemField = this.addField(screen, cx2, this.layout.mainPanel().y() + 240, this.trackerItemId, "Tracker item", () -> "The item id used for tracking targets.");

            // Row 5
            this.hvtToggle = this.addToggleButton(screen, "HVT", () -> this.highValueTargetEnabled, cx1, this.layout.mainPanel().y() + 292, 170,
                new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("ON: The High Value Target system is active.", "OFF: The High Value Target system is disabled."),
                () -> this.highValueTargetEnabled = !this.highValueTargetEnabled);
            this.revengeToggle = this.addToggleButton(screen, "Revenge", () -> this.revengeAssignmentEnabled, cx2, this.layout.mainPanel().y() + 292, 170,
                new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("ON: Players can be assigned their killer as a target.", "OFF: Players will not be assigned their killer as a target."),
                () -> this.revengeAssignmentEnabled = !this.revengeAssignmentEnabled);
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("players")) {
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
        int lx1 = this.layout.mainPanel().x() + 24;
        int lx2 = this.layout.mainPanel().x() + 374;
        int by = this.layout.mainPanel().y();

        if (this.moduleManager.isActive("rules")) {
            context.drawText(textRenderer, Text.literal("Match Settings"), lx1, by + 108, UiTheme.ACCENT_BLUE, false);
            context.drawText(textRenderer, Text.literal("Points To Win"), lx1, by + 130, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Grace Period"), lx1, by + 162, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Target Shuffle"), lx2, by + 162, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Respawn Delay"), lx1, by + 194, UiTheme.TEXT_MUTED, false);

            context.drawText(textRenderer, Text.literal("Tracking Options"), lx1, by + 192, UiTheme.ACCENT_BLUE, false);
            context.drawText(textRenderer, Text.literal("Tracker Toggle"), lx1, by + 214, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Nether Toggle"), lx2, by + 214, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Cooldown"), lx1, by + 246, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Tracker Item"), lx2, by + 246, UiTheme.TEXT_MUTED, false);

            context.drawText(textRenderer, Text.literal("Bonus Features"), lx1, by + 276, UiTheme.ACCENT_BLUE, false);
            context.drawText(textRenderer, Text.literal("High Value Target"), lx1, by + 298, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Revenge Contracts"), lx2, by + 298, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            try { this.scoreToWin = Integer.parseInt(this.pointsToWinField.getText()); } catch (Exception ignored) {}
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

    private static String toggleLabel(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }
}
