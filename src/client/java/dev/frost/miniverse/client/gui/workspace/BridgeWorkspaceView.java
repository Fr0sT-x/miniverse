package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.bridge.BridgeDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BridgeWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider, GamemodeWorkspaceView.RosterRefreshable {
    private static final int ROW_HEIGHT = 28;
    private static final int TEAM_ROW_HEIGHT = 20;
    private static final int COLUMN_HEADER_HEIGHT = 22;
    private static final int COLUMN_GAP = 12;
    private static final int BUTTON_HEIGHT = 22;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private Module activeModule = Module.TEAMS;
    
    // UI Layouts
    private UiLayout.Rect mapList = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect startButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect teamsArea = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect clearButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect autoAssignButton = new UiLayout.Rect(0, 0, 0, 0);
    
    // Team State
    private final StaticTeamSelectionGrid teamGrid = new StaticTeamSelectionGrid();

    // Config fields
    private TextFieldWidget targetScoreField;
    private TextFieldWidget respawnDelayField;
    private TextFieldWidget roundResetDelayField;
    private TextFieldWidget voidDeathOffsetField;
    private ButtonWidget allowBuildingBtn;
    private ButtonWidget allowBlockBreakingBtn;
    private ButtonWidget keepInventoryBtn;
    private ButtonWidget enableBowBtn;
    private ButtonWidget enablePickaxeBtn;
    
    private String sessionName = "bridge-" + System.currentTimeMillis();
    private String selectedMapId = "";
    
    private dev.frost.miniverse.client.gui.workspace.components.MapThumbnailGrid mapGrid = new dev.frost.miniverse.client.gui.workspace.components.MapThumbnailGrid("Valid Bridge Maps", mapId -> {
        this.selectedMapId = mapId;
        this.status = "Selected map.";
        this.mapGrid.setSelectedMapId(mapId);
    });
    
    // Config values
    private boolean allowBuilding = true;
    private boolean allowBlockBreaking = true;
    private boolean keepInventory = true;
    private boolean enableBow = true;
    private boolean enablePickaxe = true;
    
    private String status = "";

    public BridgeWorkspaceView() {
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("team_1", "Team 1", 0xDD3333, false);
        this.teamGrid.addColumn("team_2", "Team 2", 0x3344DD, false);
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        UiLayout.Rect panel = workspace.inset(4);
        this.mapList = new UiLayout.Rect(panel.x() + 14, panel.y() + 72, panel.width() - 28, panel.height() - 116);
        this.mapGrid.setBounds(this.mapList);
        this.mapGrid.setMaps(compatibleMaps());
        this.mapGrid.setAccentColor(0xFF223366); // Dark blue accent for bridge map list
        this.startButton = new UiLayout.Rect(panel.x() + panel.width() - 126, panel.y() + 12, 112, 22);
        this.teamsArea = new UiLayout.Rect(panel.x() + 12, panel.y() + 84, panel.width() - 24, panel.height() - 106);
        this.teamGrid.setBounds(this.teamsArea);
        this.autoAssignButton = new UiLayout.Rect(panel.x() + 14, panel.y() + 50, 102, BUTTON_HEIGHT);
        this.clearButton = new UiLayout.Rect(panel.x() + 124, panel.y() + 50, 62, BUTTON_HEIGHT);
        
        if (this.activeModule == Module.RULES) {
            int y = panel.y() + 68;
            this.targetScoreField = field(screen, panel.x() + 180, y, "5", "Target Score");
            y += 30;
            this.respawnDelayField = field(screen, panel.x() + 180, y, "3", "Respawn delay");
            y += 30;
            this.roundResetDelayField = field(screen, panel.x() + 180, y, "5", "Round reset delay");
            y += 30;
            this.voidDeathOffsetField = field(screen, panel.x() + 180, y, "60", "Void Death Offset");
            y += 30;
            
            this.allowBuildingBtn = toggle(screen, panel.x() + 180, y, this.allowBuilding, val -> this.allowBuilding = val);
            y += 30;
            this.allowBlockBreakingBtn = toggle(screen, panel.x() + 180, y, this.allowBlockBreaking, val -> this.allowBlockBreaking = val);
            y += 30;
            this.keepInventoryBtn = toggle(screen, panel.x() + 180, y, this.keepInventory, val -> this.keepInventory = val);
            y += 30;
            this.enableBowBtn = toggle(screen, panel.x() + 180, y, this.enableBow, val -> this.enableBow = val);
            y += 30;
            this.enablePickaxeBtn = toggle(screen, panel.x() + 180, y, this.enablePickaxe, val -> this.enablePickaxe = val);
        }
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect panel = workspace.inset(4);
        UiRenderer.panel(context, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(panel.x() + 1, panel.y() + 1, panel.x() + panel.width() - 1, panel.y() + 46, 0x701B2A32);
        context.drawText(textRenderer, Text.literal(this.activeModule.label), panel.x() + 14, panel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(this.activeModule.description), panel.x() + 14, panel.y() + 28, UiTheme.TEXT_DIM, false);
        this.renderButton(context, textRenderer, this.startButton, "Start Match", UiTheme.ACCENT_BLUE, this.startButton.contains(mouseX, mouseY));
        
        if (this.activeModule == Module.TEAMS) {
            this.renderTeamActions(context, textRenderer, mouseX, mouseY);
            this.teamGrid.render(context, textRenderer, mouseX, mouseY, delta);
        } else if (this.activeModule == Module.MAP) {
            this.mapGrid.render(context, textRenderer, mouseX, mouseY, delta);
        } else if (this.activeModule == Module.RULES) {
            this.renderRules(context, textRenderer, panel);
        } else {
            this.renderSummary(context, textRenderer, panel);
        }
        if (!this.status.isBlank()) {
            context.drawText(textRenderer, Text.literal(this.status), panel.x() + 14, panel.y() + panel.height() - 18, UiTheme.WARNING, false);
        }
    }

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        if (this.activeModule == Module.TEAMS) {
            this.teamGrid.renderForeground(context, textRenderer, workspace, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (this.startButton.contains(mouseX, mouseY)) {
            this.createSession();
            return true;
        }
        if (this.activeModule == Module.TEAMS) {
            if (this.autoAssignButton.contains(mouseX, mouseY)) {
                this.autoAssign();
                return true;
            }
            if (this.clearButton.contains(mouseX, mouseY)) {
                this.teamGrid.clear();
                return true;
            }

            return this.teamGrid.mouseClicked(mouseX, mouseY, button);
        }
        if (this.activeModule == Module.MAP) {
            return this.mapGrid.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.activeModule == Module.TEAMS) {
            return this.teamGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.activeModule == Module.TEAMS) {
            return this.teamGrid.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeModule == Module.TEAMS) {
            return this.teamGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
        } else if (this.activeModule == Module.MAP) {
            return this.mapGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        return false;
    }

    @Override
    public void refreshRoster() {
        this.teamGrid.refreshRoster();
    }

    @Override
    public String title() {
        return "Bridge Setup";
    }

    @Override
    public String subtitle() {
        return "Objective-based team gamemode";
    }

    @Override
    public String gameId() {
        return BridgeDefinition.ID;
    }

    @Override
    public List<WorkspaceModule> modules() {
        return List.of(
            new WorkspaceModule(Module.TEAMS.id, "T", Module.TEAMS.label, "Setup"),
            new WorkspaceModule(Module.MAP.id, "M", Module.MAP.label, "Setup"),
            new WorkspaceModule(Module.RULES.id, "R", Module.RULES.label, "Rules"),
            new WorkspaceModule(Module.SUMMARY.id, "S", Module.SUMMARY.label, "Summary")
        );
    }

    @Override
    public String activeModuleId() {
        return this.activeModule.id;
    }

    @Override
    public void setActiveModule(String moduleId) {
        if (this.activeModule == Module.RULES) {
            this.syncStateFromWidgets();
        }
        this.activeModule = Module.fromId(moduleId);
    }

    private void renderTeamActions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        this.renderButton(context, textRenderer, this.autoAssignButton, "Auto Assign", UiTheme.ACCENT, this.autoAssignButton.contains(mouseX, mouseY));
        this.renderButton(context, textRenderer, this.clearButton, "Clear", UiTheme.ACCENT_RED, this.clearButton.contains(mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("Drag players to teams. Empty teams will be filled randomly."), this.clearButton.x() + this.clearButton.width() + 12, this.clearButton.y() + 7, UiTheme.TEXT_DIM, false);
    }



    private void renderRules(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        int y = panel.y() + 74;
        context.drawText(textRenderer, Text.literal("Target Score"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
        y += 30;
        context.drawText(textRenderer, Text.literal("Respawn Delay"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
        y += 30;
        context.drawText(textRenderer, Text.literal("Round Reset Delay"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
        y += 30;
        context.drawText(textRenderer, Text.literal("Void Death Offset"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
        y += 30;
        context.drawText(textRenderer, Text.literal("Allow Building"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
        y += 30;
        context.drawText(textRenderer, Text.literal("Allow Block Breaking"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
        y += 30;
        context.drawText(textRenderer, Text.literal("Keep Inventory"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
        y += 30;
        context.drawText(textRenderer, Text.literal("Enable Bows"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
        y += 30;
        context.drawText(textRenderer, Text.literal("Enable Pickaxes"), panel.x() + 38, y, UiTheme.TEXT_MUTED, false);
    }

    private void renderSummary(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        UiRenderer.panel(context, panel.x() + 14, panel.y() + 72, 420, 150, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), panel.x() + 28, panel.y() + 92, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Map: " + (this.selectedMapId.isBlank() ? "None selected" : this.selectedMapId)), panel.x() + 28, panel.y() + 112, UiTheme.TEXT, false);
        
        int team1 = this.teamGrid.getMembers("team_1").size();
        int team2 = this.teamGrid.getMembers("team_2").size();
        int unassigned = this.teamGrid.getMembers("available").size();
        String players = team1 + " Team 1, " + team2 + " Team 2" + (unassigned > 0 ? " (" + unassigned + " unassigned, will be auto-filled)" : "");
        context.drawText(textRenderer, Text.literal("Players: " + players), panel.x() + 28, panel.y() + 132, UiTheme.TEXT_MUTED, false);
    }

    private void createSession() {
        if (this.client.player == null) {
            this.status = "Not connected to a server.";
            return;
        }
        if (this.selectedMapId.isBlank()) {
            this.status = "Select a valid Bridge map first.";
            return;
        }
        if (this.activeModule == Module.RULES) {
            this.syncStateFromWidgets();
        }
        
        NbtCompound plan = new NbtCompound();
        plan.putString("game", BridgeDefinition.ID);
        plan.putString("name", this.sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.settingsNbt());
        
        NbtList groups = new NbtList();
        
        NbtCompound team1Group = new NbtCompound();
        team1Group.putString("id", "team_1");
        team1Group.putString("name", "Team 1");
        NbtList team1Members = new NbtList();
        NbtList team1Roles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("team_1")) {
            team1Members.add(this.member(entry));
            team1Roles.add(this.roleMember(entry, "member"));
        }
        team1Group.put("members", team1Members);
        team1Group.put("roles", team1Roles);
        
        NbtCompound team2Group = new NbtCompound();
        team2Group.putString("id", "team_2");
        team2Group.putString("name", "Team 2");
        NbtList team2Members = new NbtList();
        NbtList team2Roles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("team_2")) {
            team2Members.add(this.member(entry));
            team2Roles.add(this.roleMember(entry, "member"));
        }
        team2Group.put("members", team2Members);
        team2Group.put("roles", team2Roles);

        int t1 = team1Members.size();
        int t2 = team2Members.size();
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("available")) {
            if (t1 <= t2) {
                team1Members.add(this.member(entry));
                team1Roles.add(this.roleMember(entry, "member"));
                t1++;
            } else {
                team2Members.add(this.member(entry));
                team2Roles.add(this.roleMember(entry, "member"));
                t2++;
            }
        }

        groups.add(team1Group);
        groups.add(team2Group);
        plan.put("groups", groups);
        
        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(BridgeDefinition.ID, this.sessionName, plan));
        this.status = "Requested Bridge session creation.";
    }

    private void syncStateFromWidgets() {
        if (this.targetScoreField != null) this.targetScoreField.getText(); // just to ensure no crash, value is pulled dynamically via parseInt in settingsNbt
    }

    private NbtCompound settingsNbt() {
        NbtCompound settings = new NbtCompound();
        settings.putString("mapId", this.selectedMapId);
        settings.putInt("targetScore", parseInt(this.targetScoreField, 5));
        settings.putInt("respawnDelaySeconds", parseInt(this.respawnDelayField, 3));
        settings.putInt("roundResetDelaySeconds", parseInt(this.roundResetDelayField, 5));
        settings.putInt("voidDeathOffset", parseInt(this.voidDeathOffsetField, 60));
        settings.putBoolean("allowBuilding", this.allowBuilding);
        settings.putBoolean("allowBlockBreaking", this.allowBlockBreaking);
        settings.putBoolean("keepInventoryOnDeath", this.keepInventory);
        settings.putBoolean("enableBow", this.enableBow);
        settings.putBoolean("enablePickaxe", this.enablePickaxe);
        return settings;
    }

    private List<SessionSnapshotData.MapSummary> compatibleMaps() {
        return SessionSnapshotData.maps().stream().filter(map -> map.validFor(BridgeDefinition.ID)).toList();
    }

    private TextFieldWidget field(SessionScreen screen, int x, int y, String value, String narration) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, 170, 22, Text.literal(narration));
        field.setText(value);
        return screen.addWorkspaceChild(field);
    }

    private ButtonWidget toggle(SessionScreen screen, int x, int y, boolean currentValue, java.util.function.Consumer<Boolean> action) {
        ButtonWidget btn = ButtonWidget.builder(Text.literal(currentValue ? "Enabled" : "Disabled"), widget -> {
            boolean next = !widget.getMessage().getString().equals("Enabled");
            widget.setMessage(Text.literal(next ? "Enabled" : "Disabled"));
            action.accept(next);
        }).dimensions(x, y, 170, 22).build();
        return screen.addWorkspaceChild(btn);
    }

    private void renderButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), hovered ? 0x88203040 : UiTheme.PANEL_RAISED, accent);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    private static int parseInt(TextFieldWidget widget, int fallback) {
        if (widget == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(widget.getText().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void autoAssign() {
        this.teamGrid.clear();
        List<SessionSnapshotData.RosterEntry> available = new ArrayList<>(this.teamGrid.getMembers("available"));
        for (int i = 0; i < available.size(); i++) {
            if (i % 2 == 0) {
                this.teamGrid.addMember("team_1", available.get(i));
            } else {
                this.teamGrid.addMember("team_2", available.get(i));
            }
        }
        this.status = "Auto-assigned players to Team 1 and Team 2.";
    }

    private NbtCompound member(SessionSnapshotData.RosterEntry entry) {
        NbtCompound compound = new NbtCompound();
        compound.putString("uuid", entry.uuid());
        compound.putString("name", entry.name());
        return compound;
    }

    private NbtCompound roleMember(SessionSnapshotData.RosterEntry entry, String role) {
        NbtCompound compound = new NbtCompound();
        compound.putString("uuid", entry.uuid());
        compound.putString("role", role);
        return compound;
    }

    private enum Module {
        TEAMS("teams", "Teams", "Assign players to Red and Blue teams."),
        MAP("map", "Map Selection", "Choose a validated map configured for The Bridge."),
        RULES("rules", "Match Rules", "Tune score limits, respawn delays, and item permissions."),
        SUMMARY("summary", "Summary", "Review and launch the match.");

        private final String id;
        private final String label;
        private final String description;

        Module(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }

        private static Module fromId(String id) {
            for (Module module : values()) {
                if (module.id.equalsIgnoreCase(id)) {
                    return module;
                }
            }
            return TEAMS;
        }
    }


}
