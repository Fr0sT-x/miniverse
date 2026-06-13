package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.bridge.BridgeDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class BridgeWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid teamGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget targetScoreField;
    private IntFieldWidget respawnDelayField;
    private IntFieldWidget roundResetDelayField;
    private IntFieldWidget voidDeathOffsetField;
    private ButtonWidget allowBuildingBtn;
    private ButtonWidget allowBlockBreakingBtn;
    private ButtonWidget enableBowBtn;
    private ButtonWidget enablePickaxeBtn;

    private int targetScore = 5;
    private int respawnDelay = 3;
    private int roundResetDelay = 5;
    private int voidDeathOffset = 60;
    private boolean allowBuilding = true;
    private boolean allowBlockBreaking = true;
    private boolean enableBow = true;
    private boolean enablePickaxe = true;

    @Override
    protected dev.frost.miniverse.minigame.core.rules.GlobalMatchRules defaultMatchRules() {
        return new dev.frost.miniverse.minigame.core.rules.GlobalMatchRules(true, true, true, true, true, true, true, false);
    }

    public BridgeWorkspaceView() {
        super("bridge");
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("team_1", "Team 1", 0xDD3333, false);
        this.teamGrid.addColumn("team_2", "Team 2", 0x3344DD, false);
        
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players to Team 1 and Team 2.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for The Bridge.", UiTheme.ACCENT_BLUE, "Valid Bridge Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Tune score limits, respawn delays, and item permissions.", UiTheme.ACCENT);
        this.useGameRules();
        this.moduleManager.register("summary", "S", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            int y = this.layout.mainPanel().y() + 96;
            this.targetScoreField = this.addIntField(screen, this.layout.mainPanel().x() + 180, y, this.targetScore, "Target Score", val -> "Goals needed to win the match.");
            y += 30;
            this.respawnDelayField = this.addIntField(screen, this.layout.mainPanel().x() + 180, y, this.respawnDelay, "Respawn delay",
                "Players will respawn instantly.",
                val -> "Players will be forced to spectate for " + val + " seconds before respawning.");
            y += 30;
            this.roundResetDelayField = this.addIntField(screen, this.layout.mainPanel().x() + 180, y, this.roundResetDelay, "Round reset delay",
                "Next round starts instantly.",
                val -> "Time in seconds before the next round starts: " + val);
            y += 30;
            this.voidDeathOffsetField = this.addIntField(screen, this.layout.mainPanel().x() + 180, y, this.voidDeathOffset, "Void Death Offset", val -> "Y-level offset from the void point selected in map editor, to trigger a void death.");
            y += 30;
            
            this.allowBuildingBtn = this.addToggleButton(screen, "Allow Building", () -> this.allowBuilding, this.layout.mainPanel().x() + 180, y, 170,
                "Players can place blocks.",
                "Block placement is disabled.",
                () -> this.allowBuilding = !this.allowBuilding);
            y += 30;
            this.allowBlockBreakingBtn = this.addToggleButton(screen, "Allow Block Breaking", () -> this.allowBlockBreaking, this.layout.mainPanel().x() + 180, y, 170,
                "Players can break placed blocks.",
                "Block breaking is disabled.",
                () -> this.allowBlockBreaking = !this.allowBlockBreaking);
            y += 30;
            this.enableBowBtn = this.addToggleButton(screen, "Enable Bows", () -> this.enableBow, this.layout.mainPanel().x() + 180, y, 170,
                "Players spawn with a bow.",
                "Bows are disabled.",
                () -> this.enableBow = !this.enableBow);
            y += 30;
            this.enablePickaxeBtn = this.addToggleButton(screen, "Enable Pickaxes", () -> this.enablePickaxe, this.layout.mainPanel().x() + 180, y, 170,
                "Players spawn with a pickaxe.",
                "Pickaxes are disabled.",
                () -> this.enablePickaxe = !this.enablePickaxe);
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
            Text.literal("Target Score: " + this.targetScore),
            Text.literal("Allow Building: " + (this.allowBuilding ? "Yes" : "No")),
            Text.literal("Allow Block Breaking: " + (this.allowBlockBreaking ? "Yes" : "No")),
            Text.literal("Enable Bows: " + (this.enableBow ? "Yes" : "No")),
            Text.literal("Enable Pickaxes: " + (this.enablePickaxe ? "Yes" : "No"))
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int labelX = this.layout.mainPanel().x() + 38;
        int y = this.layout.mainPanel().y() + 102;
        if (this.moduleManager.isActive("rules")) {
            context.drawText(textRenderer, Text.literal("Target Score"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Respawn Delay"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Round Reset Delay"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Void Death Offset"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Allow Building"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Allow Block Breaking"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Enable Bows"), labelX, y, UiTheme.TEXT_MUTED, false);
            y += 30;
            context.drawText(textRenderer, Text.literal("Enable Pickaxes"), labelX, y, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.targetScore = readClamped(this.targetScoreField, this.targetScore, 1, 100);
            this.respawnDelay = readClamped(this.respawnDelayField, this.respawnDelay, 0, 60);
            this.roundResetDelay = readClamped(this.roundResetDelayField, this.roundResetDelay, 0, 60);
            this.voidDeathOffset = readClamped(this.voidDeathOffsetField, this.voidDeathOffset, -100, 300);
        }
    }

    @Override
    public String title() { return "Bridge Setup"; }

    @Override
    public String subtitle() { return "Objective-based team gamemode"; }

    @Override
    public String gameId() { return BridgeDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        int team1 = this.teamGrid.getMembers("team_1").size();
        int team2 = this.teamGrid.getMembers("team_2").size();
        int unassigned = this.teamGrid.getMembers("available").size();
        if (team1 + team2 + unassigned < 2) {
            return ValidationResult.error("Need at least two players.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putInt("targetScore", this.targetScore);
        builder.settings().putInt("respawnDelaySeconds", this.respawnDelay);
        builder.settings().putInt("roundResetDelaySeconds", this.roundResetDelay);
        builder.settings().putInt("voidDeathOffset", this.voidDeathOffset);
        builder.settings().putBoolean("allowBuilding", this.allowBuilding);
        builder.settings().putBoolean("allowBlockBreaking", this.allowBlockBreaking);
        builder.settings().putBoolean("enableBow", this.enableBow);
        builder.settings().putBoolean("enablePickaxe", this.enablePickaxe);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        List<SessionSnapshotData.RosterEntry> team1Members = new ArrayList<>(this.teamGrid.getMembers("team_1"));
        List<SessionSnapshotData.RosterEntry> team2Members = new ArrayList<>(this.teamGrid.getMembers("team_2"));
        
        int t1 = team1Members.size();
        int t2 = team2Members.size();
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("available")) {
            if (t1 <= t2) {
                team1Members.add(entry);
                t1++;
            } else {
                team2Members.add(entry);
                t2++;
            }
        }
        
        builder.addGroup("team_1", "Team 1", team1Members);
        builder.addGroup("team_2", "Team 2", team2Members);
    }
    
    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
