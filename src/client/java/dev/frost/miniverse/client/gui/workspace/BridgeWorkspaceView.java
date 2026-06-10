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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class BridgeWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid teamGrid = new StaticTeamSelectionGrid();

    private TextFieldWidget targetScoreField;
    private TextFieldWidget respawnDelayField;
    private TextFieldWidget roundResetDelayField;
    private TextFieldWidget voidDeathOffsetField;
    private ButtonWidget allowBuildingBtn;
    private ButtonWidget allowBlockBreakingBtn;
    private ButtonWidget keepInventoryBtn;
    private ButtonWidget enableBowBtn;
    private ButtonWidget enablePickaxeBtn;

    private int targetScore = 5;
    private int respawnDelay = 3;
    private int roundResetDelay = 5;
    private int voidDeathOffset = 60;
    private boolean allowBuilding = true;
    private boolean allowBlockBreaking = true;
    private boolean keepInventory = true;
    private boolean enableBow = true;
    private boolean enablePickaxe = true;

    public BridgeWorkspaceView() {
        super("bridge");
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("team_1", "Team 1", 0xDD3333, false);
        this.teamGrid.addColumn("team_2", "Team 2", 0x3344DD, false);
        
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players to Team 1 and Team 2.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for The Bridge.", UiTheme.ACCENT_BLUE, "Valid Bridge Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Tune score limits, respawn delays, and item permissions.", UiTheme.ACCENT);
        this.moduleManager.register("summary", "S", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            int y = this.layout.mainPanel().y() + 96;
            this.targetScoreField = this.addField(screen, this.layout.mainPanel().x() + 180, y, Integer.toString(this.targetScore), "Target Score");
            y += 30;
            this.respawnDelayField = this.addField(screen, this.layout.mainPanel().x() + 180, y, Integer.toString(this.respawnDelay), "Respawn delay");
            y += 30;
            this.roundResetDelayField = this.addField(screen, this.layout.mainPanel().x() + 180, y, Integer.toString(this.roundResetDelay), "Round reset delay");
            y += 30;
            this.voidDeathOffsetField = this.addField(screen, this.layout.mainPanel().x() + 180, y, Integer.toString(this.voidDeathOffset), "Void Death Offset");
            y += 30;
            
            this.allowBuildingBtn = this.addButton(screen, "Allow Building: " + onOff(this.allowBuilding), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.allowBuilding = !this.allowBuilding;
                this.allowBuildingBtn.setMessage(Text.literal("Allow Building: " + onOff(this.allowBuilding)));
            });
            y += 30;
            this.allowBlockBreakingBtn = this.addButton(screen, "Allow Block Breaking: " + onOff(this.allowBlockBreaking), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.allowBlockBreaking = !this.allowBlockBreaking;
                this.allowBlockBreakingBtn.setMessage(Text.literal("Allow Block Breaking: " + onOff(this.allowBlockBreaking)));
            });
            y += 30;
            this.keepInventoryBtn = this.addButton(screen, "Keep Inventory: " + onOff(this.keepInventory), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.keepInventory = !this.keepInventory;
                this.keepInventoryBtn.setMessage(Text.literal("Keep Inventory: " + onOff(this.keepInventory)));
            });
            y += 30;
            this.enableBowBtn = this.addButton(screen, "Enable Bows: " + onOff(this.enableBow), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.enableBow = !this.enableBow;
                this.enableBowBtn.setMessage(Text.literal("Enable Bows: " + onOff(this.enableBow)));
            });
            y += 30;
            this.enablePickaxeBtn = this.addButton(screen, "Enable Pickaxes: " + onOff(this.enablePickaxe), this.layout.mainPanel().x() + 180, y, 170, () -> {
                this.enablePickaxe = !this.enablePickaxe;
                this.enablePickaxeBtn.setMessage(Text.literal("Enable Pickaxes: " + onOff(this.enablePickaxe)));
            });
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("summary")) {
            this.syncStateFromWidgets();
            this.renderSettingsModulePanel(context, textRenderer, "Summary", UiTheme.ACCENT);
            int x = this.layout.mainPanel().x() + 14;
            int y = this.layout.mainPanel().y() + 72;
            int line = y + 18;
            context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
            line += 20;
            int team1 = this.teamGrid.getMembers("team_1").size();
            int team2 = this.teamGrid.getMembers("team_2").size();
            int unassigned = this.teamGrid.getMembers("available").size();
            String players = team1 + " Team 1, " + team2 + " Team 2" + (unassigned > 0 ? " (" + unassigned + " unassigned, will be auto-filled)" : "");
            context.drawText(textRenderer, Text.literal("Players: " + players), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Target Score: " + this.targetScore), x + 14, line, UiTheme.TEXT_MUTED, false);
        } else if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
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
            context.drawText(textRenderer, Text.literal("Keep Inventory"), labelX, y, UiTheme.TEXT_MUTED, false);
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

    private void syncStateFromWidgets() {
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
        builder.settings().putBoolean("keepInventoryOnDeath", this.keepInventory);
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
