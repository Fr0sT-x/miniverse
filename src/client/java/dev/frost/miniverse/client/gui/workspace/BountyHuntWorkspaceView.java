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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class BountyHuntWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();
    
    private TextFieldWidget graceField;
    private TextFieldWidget invincibilityField;
    private TextFieldWidget scoreToWinField;
    private TextFieldWidget targetSwapField;
    private TextFieldWidget compassCooldownField;
    private TextFieldWidget trackerItemField;
    private ButtonWidget trackerToggle;
    private ButtonWidget netherToggle;

    private int graceSeconds = 300;
    private int invincibilitySeconds = 120;
    private int scoreToWin = 5;
    private int targetSwapSeconds = 0;
    private int compassCooldownSeconds = 2;
    private boolean trackerEnabled = true;
    private boolean netherTrackingEnabled = true;
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
            this.scoreToWinField = this.addField(screen, cx1, this.layout.mainPanel().y() + 124, Integer.toString(this.scoreToWin), "Score to win");
            this.targetSwapField = this.addField(screen, cx2, this.layout.mainPanel().y() + 124, Integer.toString(this.targetSwapSeconds), "Target swap seconds");

            // Row 2
            this.graceField = this.addField(screen, cx1, this.layout.mainPanel().y() + 156, Integer.toString(this.graceSeconds), "Grace seconds");
            this.invincibilityField = this.addField(screen, cx2, this.layout.mainPanel().y() + 156, Integer.toString(this.invincibilitySeconds), "Invincibility seconds");

            // Row 3
            this.trackerToggle = this.addButton(screen, toggleLabel("Tracker", this.trackerEnabled), cx1, this.layout.mainPanel().y() + 208, 170, () -> {
                this.trackerEnabled = !this.trackerEnabled;
                this.trackerToggle.setMessage(Text.literal(toggleLabel("Tracker", this.trackerEnabled)));
            });
            this.netherToggle = this.addButton(screen, toggleLabel("Nether Tracking", this.netherTrackingEnabled), cx2, this.layout.mainPanel().y() + 208, 170, () -> {
                this.netherTrackingEnabled = !this.netherTrackingEnabled;
                this.netherToggle.setMessage(Text.literal(toggleLabel("Nether Tracking", this.netherTrackingEnabled)));
            });

            // Row 4
            this.compassCooldownField = this.addField(screen, cx1, this.layout.mainPanel().y() + 240, Integer.toString(this.compassCooldownSeconds), "Cooldown seconds");
            this.trackerItemField = this.addField(screen, cx2, this.layout.mainPanel().y() + 240, this.trackerItemId, "Tracker item");
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
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }

    private static String toggleLabel(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }
}
