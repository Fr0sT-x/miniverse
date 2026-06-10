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
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure scoring and timers.", UiTheme.ACCENT_BLUE);
        this.moduleManager.register("tracking", "T", "Tracking", "Rules", "Configure tracker behavior.", UiTheme.ACCENT_GREEN);
        this.moduleManager.register("summary", "U", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.graceField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, Integer.toString(this.graceSeconds), "Grace seconds");
            this.invincibilityField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, Integer.toString(this.invincibilitySeconds), "Invincibility seconds");
            this.scoreToWinField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, Integer.toString(this.scoreToWin), "Score to win");
            this.targetSwapField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 192, Integer.toString(this.targetSwapSeconds), "Target swap seconds");
        } else if (this.moduleManager.isActive("tracking")) {
            this.trackerToggle = this.addButton(screen, toggleLabel("Tracker", this.trackerEnabled), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, 170, () -> {
                this.trackerEnabled = !this.trackerEnabled;
                this.trackerToggle.setMessage(Text.literal(toggleLabel("Tracker", this.trackerEnabled)));
            });
            this.netherToggle = this.addButton(screen, toggleLabel("Nether", this.netherTrackingEnabled), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, 170, () -> {
                this.netherTrackingEnabled = !this.netherTrackingEnabled;
                this.netherToggle.setMessage(Text.literal(toggleLabel("Nether", this.netherTrackingEnabled)));
            });
            this.compassCooldownField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, Integer.toString(this.compassCooldownSeconds), "Cooldown seconds");
            this.trackerItemField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 192, this.trackerItemId, "Tracker item");
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("players")) {
            // Action buttons are not auto-handled yet, so let's just let players use drag/drop or we could add them to init
        } else if (this.moduleManager.isActive("summary")) {
            this.syncStateFromWidgets();
            this.renderSettingsModulePanel(context, textRenderer, "Summary", UiTheme.ACCENT);
            int x = this.layout.mainPanel().x() + 14;
            int y = this.layout.mainPanel().y() + 72;
            int line = y + 18;
            context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
            line += 20;
            context.drawText(textRenderer, Text.literal("Players: " + this.playerGrid.getMembers("selected").size()), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Score To Win: " + this.scoreToWin), x + 14, line, UiTheme.TEXT_MUTED, false);
        } else if (this.moduleManager.isActive("rules") || this.moduleManager.isActive("tracking")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int labelX = this.layout.mainPanel().x() + 38;
        int labelY = this.layout.mainPanel().y() + 102;
        if (this.moduleManager.isActive("rules")) {
            context.drawText(textRenderer, Text.literal("Grace Period"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Invincibility"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Score To Win"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Target Swap"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
        } else if (this.moduleManager.isActive("tracking")) {
            context.drawText(textRenderer, Text.literal("Tracker"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Nether"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Cooldown"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Tracker Item"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    private void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.graceSeconds = readClamped(this.graceField, this.graceSeconds, 0, 3600);
            this.invincibilitySeconds = readClamped(this.invincibilityField, this.invincibilitySeconds, 0, 3600);
            this.scoreToWin = readClamped(this.scoreToWinField, this.scoreToWin, 1, 99);
            this.targetSwapSeconds = readClamped(this.targetSwapField, this.targetSwapSeconds, 0, 3600);
        } else if (this.moduleManager.isActive("tracking")) {
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
