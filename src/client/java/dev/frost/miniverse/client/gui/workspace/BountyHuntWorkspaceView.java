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
    
    private IntFieldWidget graceField;
    private IntFieldWidget invincibilityField;
    private IntFieldWidget scoreToWinField;
    private IntFieldWidget targetSwapField;
    private IntFieldWidget compassCooldownField;
    private TextFieldWidget trackerItemField;
    private ButtonWidget trackerToggle;
    private ButtonWidget netherToggle;
    private ButtonWidget hvtToggle;
    private ButtonWidget revengeToggle;

    private int graceSeconds = 300;
    private int invincibilitySeconds = 120;
    private int scoreToWin = 5;
    private int targetSwapSeconds = 0;
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

            // Row 1
            this.scoreToWinField = this.addIntField(screen, cx1, this.layout.mainPanel().y() + 124, this.scoreToWin, "Score to win", val -> "Score needed to win the match.");
            this.targetSwapField = this.addIntField(screen, cx2, this.layout.mainPanel().y() + 124, this.targetSwapSeconds, "Target swap seconds",
                "Targets will not rotate.",
                val -> "Targets will rotate every " + val + " seconds.");

            // Row 2
            this.graceField = this.addIntField(screen, cx1, this.layout.mainPanel().y() + 156, this.graceSeconds, "Grace seconds",
                "No grace period, PvP is enabled immediately.",
                val -> "Players have " + val + " seconds of peace before PvP is enabled.");
            this.invincibilityField = this.addIntField(screen, cx2, this.layout.mainPanel().y() + 156, this.invincibilitySeconds, "Invincibility seconds",
                "No respawn invincibility.",
                val -> "Players are invincible for " + val + " seconds after respawning.");

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
            // Action buttons are not auto-handled yet, so let's just let players use drag/drop or we could add them to init
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
            context.drawText(textRenderer, Text.literal("Score To Win"), lx1, by + 130, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Target Swap"), lx2, by + 130, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Grace Period"), lx1, by + 162, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Invincibility"), lx2, by + 162, UiTheme.TEXT_MUTED, false);

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
            this.graceSeconds = readClamped(this.graceField, this.graceSeconds, 0, 3600);
            this.invincibilitySeconds = readClamped(this.invincibilityField, this.invincibilitySeconds, 0, 3600);
            this.scoreToWin = readClamped(this.scoreToWinField, this.scoreToWin, 1, 99);
            this.targetSwapSeconds = readClamped(this.targetSwapField, this.targetSwapSeconds, 0, 3600);
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
        builder.settings().putInt("gracePeriodSeconds", this.graceSeconds);
        builder.settings().putInt("respawnInvincibilitySeconds", this.invincibilitySeconds);
        builder.settings().putInt("scoreToWin", this.scoreToWin);
        builder.settings().putInt("targetSwapIntervalSeconds", this.targetSwapSeconds);
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
