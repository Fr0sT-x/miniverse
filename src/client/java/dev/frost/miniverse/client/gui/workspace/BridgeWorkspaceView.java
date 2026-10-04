package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
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
    private boolean teamChatEnabled = false;

    public BridgeWorkspaceView() {
        super("bridge");
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("red", "Red", 0xDD3333, false);
        this.teamGrid.addColumn("blue", "Blue", 0x3344DD, false);
        
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players to Red and Blue teams.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for The Bridge.", UiTheme.ACCENT_BLUE, "Valid Bridge Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Tune score limits, respawn delays, and item permissions.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            WorkspaceTooltip scoreTooltip = WorkspaceTooltip.dynamic(() -> "Goals needed to win the match (" + (this.targetScoreField != null ? this.targetScoreField.getIntValue(this.targetScore) : this.targetScore) + ").");
            WorkspaceTooltip respawnTooltip = WorkspaceTooltip.dynamic(() -> {
                int val = this.respawnDelayField != null ? this.respawnDelayField.getIntValue(this.respawnDelay) : this.respawnDelay;
                return val <= 0 ? "Players will respawn instantly." : "Players will be forced to spectate for " + val + " seconds before respawning.";
            });

            this.rulesLayout.addRow(
                "Target Score", scoreTooltip, (s, x, y, w) -> {
                    this.targetScoreField = this.addIntField(s, "Target Score", x, y, this.targetScore, 1, 100, w, scoreTooltip);
                },
                "Respawn Delay", respawnTooltip, (s, x, y, w) -> {
                    this.respawnDelayField = this.addIntField(s, "Respawn Delay", x, y, this.respawnDelay, 0, 60, w, respawnTooltip);
                }
            );

            WorkspaceTooltip resetTooltip = WorkspaceTooltip.dynamic(() -> {
                int val = this.roundResetDelayField != null ? this.roundResetDelayField.getIntValue(this.roundResetDelay) : this.roundResetDelay;
                return val <= 0 ? "Next round starts instantly." : "Time in seconds before the next round starts: " + val;
            });
            WorkspaceTooltip voidTooltip = WorkspaceTooltip.dynamic(() -> "Y-level offset from the void point selected in map editor, to trigger a void death (" + (this.voidDeathOffsetField != null ? this.voidDeathOffsetField.getIntValue(this.voidDeathOffset) : this.voidDeathOffset) + ").");

            this.rulesLayout.addRow(
                "Round Reset Delay", resetTooltip, (s, x, y, w) -> {
                    this.roundResetDelayField = this.addIntField(s, "Round Reset Delay", x, y, this.roundResetDelay, 0, 60, w, resetTooltip);
                },
                "Void Death Offset", voidTooltip, (s, x, y, w) -> {
                    this.voidDeathOffsetField = this.addIntField(s, "Void Death Offset", x, y, this.voidDeathOffset, -100, 300, w, voidTooltip);
                }
            );

            WorkspaceTooltip buildTooltip = WorkspaceTooltip.toggle(() -> this.allowBuilding,
                "Players can place blocks.", "Block placement is disabled.");
            WorkspaceTooltip breakTooltip = WorkspaceTooltip.toggle(() -> this.allowBlockBreaking,
                "Players can break placed blocks.", "Block breaking is disabled.");

            this.rulesLayout.addRow(
                "Allow Building", buildTooltip, (s, x, y, w) -> {
                    this.allowBuildingBtn = this.addToggleButton(s, "Allow Building", () -> this.allowBuilding, x, y, w, buildTooltip,
                        () -> this.allowBuilding = !this.allowBuilding);
                },
                "Allow Block Breaking", breakTooltip, (s, x, y, w) -> {
                    this.allowBlockBreakingBtn = this.addToggleButton(s, "Allow Block Breaking", () -> this.allowBlockBreaking, x, y, w, breakTooltip,
                        () -> this.allowBlockBreaking = !this.allowBlockBreaking);
                }
            );

            WorkspaceTooltip bowTooltip = WorkspaceTooltip.toggle(() -> this.enableBow,
                "Players spawn with a bow.", "Bows are disabled.");
            WorkspaceTooltip pickaxeTooltip = WorkspaceTooltip.toggle(() -> this.enablePickaxe,
                "Players spawn with a pickaxe.", "Pickaxes are disabled.");

            this.rulesLayout.addRow(
                "Enable Bows", bowTooltip, (s, x, y, w) -> {
                    this.enableBowBtn = this.addToggleButton(s, "Enable Bows", () -> this.enableBow, x, y, w, bowTooltip,
                        () -> this.enableBow = !this.enableBow);
                },
                "Enable Pickaxes", pickaxeTooltip, (s, x, y, w) -> {
                    this.enablePickaxeBtn = this.addToggleButton(s, "Enable Pickaxes", () -> this.enablePickaxe, x, y, w, pickaxeTooltip,
                        () -> this.enablePickaxe = !this.enablePickaxe);
                }
            );

            WorkspaceTooltip chatTooltip = WorkspaceTooltip.toggle(() -> this.teamChatEnabled,
                "Chat is routed to team members only (use ! for global chat).",
                "Vanilla chat is used for all players.");

            this.rulesLayout.addRow(
                "Team Chat", chatTooltip, (s, x, y, w) -> {
                    this.addToggleButton(s, "Team Chat", () -> this.teamChatEnabled, x, y, w, chatTooltip,
                        () -> this.teamChatEnabled = !this.teamChatEnabled);
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
            Text.literal("Target Score: " + this.targetScore),
            Text.literal("Allow Building: " + (this.allowBuilding ? "Yes" : "No")),
            Text.literal("Allow Block Breaking: " + (this.allowBlockBreaking ? "Yes" : "No")),
            Text.literal("Enable Bows: " + (this.enableBow ? "Yes" : "No")),
            Text.literal("Enable Pickaxes: " + (this.enablePickaxe ? "Yes" : "No")),
            Text.literal("Team Chat: " + (this.teamChatEnabled ? "ON" : "OFF"))
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
        int team1 = this.teamGrid.getMembers("red").size();
        int team2 = this.teamGrid.getMembers("blue").size();
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
        builder.settings().putBoolean("teamChatEnabled", this.teamChatEnabled);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        List<SessionSnapshotData.RosterEntry> redMembers = new ArrayList<>(this.teamGrid.getMembers("red"));
        List<SessionSnapshotData.RosterEntry> blueMembers = new ArrayList<>(this.teamGrid.getMembers("blue"));
        
        int t1 = redMembers.size();
        int t2 = blueMembers.size();
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("available")) {
            if (t1 <= t2) {
                redMembers.add(entry);
                t1++;
            } else {
                blueMembers.add(entry);
                t2++;
            }
        }
        
        builder.addGroup("red", "Red", redMembers);
        builder.addGroup("blue", "Blue", blueMembers);
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("targetScore")) this.targetScore = settings.getInt("targetScore");
        if (settings.contains("respawnDelaySeconds")) this.respawnDelay = settings.getInt("respawnDelaySeconds");
        if (settings.contains("roundResetDelaySeconds")) this.roundResetDelay = settings.getInt("roundResetDelaySeconds");
        if (settings.contains("voidDeathOffset")) this.voidDeathOffset = settings.getInt("voidDeathOffset");
        if (settings.contains("allowBuilding")) this.allowBuilding = settings.getBoolean("allowBuilding");
        if (settings.contains("allowBlockBreaking")) this.allowBlockBreaking = settings.getBoolean("allowBlockBreaking");
        if (settings.contains("enableBow")) this.enableBow = settings.getBoolean("enableBow");
        if (settings.contains("enablePickaxe")) this.enablePickaxe = settings.getBoolean("enablePickaxe");
        if (settings.contains("teamChatEnabled")) this.teamChatEnabled = settings.getBoolean("teamChatEnabled");
        if (this.targetScoreField != null) this.targetScoreField.setText(String.valueOf(this.targetScore));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelay));
        if (this.roundResetDelayField != null) this.roundResetDelayField.setText(String.valueOf(this.roundResetDelay));
        if (this.voidDeathOffsetField != null) this.voidDeathOffsetField.setText(String.valueOf(this.voidDeathOffset));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.targetScore = 5;
        this.respawnDelay = 3;
        this.roundResetDelay = 5;
        this.voidDeathOffset = 60;
        this.allowBuilding = true;
        this.allowBlockBreaking = true;
        this.enableBow = true;
        this.enablePickaxe = true;
        this.teamChatEnabled = false;
        if (this.targetScoreField != null) this.targetScoreField.setText(String.valueOf(this.targetScore));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelay));
        if (this.roundResetDelayField != null) this.roundResetDelayField.setText(String.valueOf(this.roundResetDelay));
        if (this.voidDeathOffsetField != null) this.voidDeathOffsetField.setText(String.valueOf(this.voidDeathOffset));
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
