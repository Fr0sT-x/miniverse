package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
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
    private final List<ColumnState> columns = new ArrayList<>();
    private final Map<String, UiAnimation.Value> rowHovers = new HashMap<>();
    private SessionSnapshotData.RosterEntry draggedEntry;
    private ColumnKind draggedFrom = ColumnKind.AVAILABLE;
    private double dragX;
    private double dragY;
    private String selectedPlayerUuid = "";

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
        this.columns.add(new ColumnState(ColumnKind.AVAILABLE, "Available", 0x7C8088));
        this.columns.add(new ColumnState(ColumnKind.RED_TEAM, "Red Team", 0xDD3333));
        this.columns.add(new ColumnState(ColumnKind.BLUE_TEAM, "Blue Team", 0x3344DD));
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
            this.renderTeams(context, textRenderer, this.teamsArea, mouseX, mouseY);
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
        if (this.draggedEntry != null) {
            int x = (int) this.dragX + 10;
            int y = (int) this.dragY + 10;
            int width = Math.max(104, textRenderer.getWidth(this.draggedEntry.name()) + 28);
            UiRenderer.panel(context, x, y, width, 22, UiTheme.PANEL_RAISED, UiTheme.ACCENT);
            context.drawText(textRenderer, Text.literal(this.draggedEntry.name()), x + 12, y + 7, UiTheme.TEXT, false);
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
                this.resetAssignments();
                return true;
            }

            for (int i = 0; i < this.columns.size(); i++) {
                ColumnState column = this.columns.get(i);
                UiLayout.Rect rect = this.columnRect(i);
                if (!rect.contains(mouseX, mouseY)) continue;
                int row = column.rowAt(mouseY, rect);
                List<SessionSnapshotData.RosterEntry> entries = this.getEntries(column.kind);
                int index = column.scrollOffset + row;
                if (row >= 0 && index >= 0 && index < entries.size()) {
                    this.selectedPlayerUuid = entries.get(index).uuid();
                    this.draggedEntry = entries.get(index);
                    this.draggedFrom = column.kind;
                    this.dragX = mouseX;
                    this.dragY = mouseY;
                    return true;
                }
            }
        }
        if (this.activeModule == Module.MAP) {
            return this.mapGrid.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && this.draggedEntry != null) {
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || this.draggedEntry == null) {
            return false;
        }
        ColumnKind target = this.dragTarget(mouseX, mouseY);
        if (target != ColumnKind.NONE) {
            this.moveEntryTo(this.draggedEntry, target);
            this.status = this.draggedEntry.name() + " moved to " + target.displayName + ".";
        }
        this.draggedEntry = null;
        this.draggedFrom = ColumnKind.NONE;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeModule == Module.TEAMS) {
            for (int i = 0; i < this.columns.size(); i++) {
                ColumnState column = this.columns.get(i);
                UiLayout.Rect rect = this.columnRect(i);
                if (rect.contains(mouseX, mouseY)) {
                    int maxScroll = Math.max(0, this.getEntries(column.kind).size() - column.visibleRows(rect.height()));
                    column.scrollOffset = Math.clamp(column.scrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                    return maxScroll > 0;
                }
            }
        } else if (this.activeModule == Module.MAP) {
            return this.mapGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        return false;
    }

    @Override
    public void refreshRoster() {
        this.getColumn(ColumnKind.RED_TEAM).members.removeIf(entry -> !this.isOnline(entry.uuid()));
        this.getColumn(ColumnKind.BLUE_TEAM).members.removeIf(entry -> !this.isOnline(entry.uuid()));
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

    private void renderTeams(DrawContext context, TextRenderer textRenderer, UiLayout.Rect area, int mouseX, int mouseY) {
        int columnWidth = (area.width() - COLUMN_GAP * 2) / 3;
        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            UiLayout.Rect rect = new UiLayout.Rect(area.x() + i * (columnWidth + COLUMN_GAP), area.y(), columnWidth, area.height());
            this.renderColumn(context, textRenderer, rect, column, mouseX, mouseY, this.draggedEntry != null && rect.contains(mouseX, mouseY));
        }
    }

    private void renderColumn(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, ColumnState column, int mouseX, int mouseY, boolean dropTarget) {
        List<SessionSnapshotData.RosterEntry> entries = this.getEntries(column.kind);
        int accent = 0xFF000000 | column.accentColor;
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), dropTarget ? UiTheme.CARD_HOVER : UiTheme.CARD, dropTarget ? accent : UiTheme.BORDER_SUBTLE);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + 3, accent);
        context.drawText(textRenderer, Text.literal(column.title + " (" + entries.size() + ")"), rect.x() + 8, rect.y() + 7, UiTheme.TEXT, false);

        int visibleRows = column.visibleRows(rect.height());
        int rows = Math.min(entries.size() - column.scrollOffset, visibleRows);
        int listTop = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        for (int row = 0; row < rows; row++) {
            SessionSnapshotData.RosterEntry entry = entries.get(column.scrollOffset + row);
            if (this.draggedEntry != null && this.draggedEntry.uuid().equals(entry.uuid())) {
                continue;
            }
            int rowY = listTop + row * TEAM_ROW_HEIGHT;
            boolean selected = entry.uuid().equals(this.selectedPlayerUuid);
            boolean hovered = mouseX >= rect.x() + 1 && mouseX <= rect.x() + rect.width() - 1 && mouseY >= rowY && mouseY <= rowY + TEAM_ROW_HEIGHT - 2;
            UiAnimation.Value hover = this.rowHovers.computeIfAbsent(column.kind.name() + ":" + entry.uuid(), ignored -> new UiAnimation.Value(0.0F));
            hover.animateTo(hovered ? 1.0F : 0.0F, UiTheme.HOVER_MS);
            float progress = hover.get();
            int background = selected ? UiAnimation.lerpColor(0xAA2F5D94, 0xCC3E79B8, progress) : UiAnimation.lerpColor(0x26222A34, 0x66304052, progress);
            context.fill(rect.x() + 1, rowY, rect.x() + rect.width() - 1, rowY + TEAM_ROW_HEIGHT - 2, background);
            context.fill(rect.x() + 6, rowY + 4, rect.x() + 10, rowY + TEAM_ROW_HEIGHT - 5, UiAnimation.lerpColor(accent, UiTheme.ACCENT, progress * 0.35F));
            context.drawText(textRenderer, Text.literal(entry.name()), rect.x() + 16, rowY + 6, selected ? UiTheme.TEXT : UiAnimation.lerpColor(UiTheme.TEXT_MUTED, UiTheme.TEXT, progress), false);
        }
        this.drawScrollbar(context, rect, entries.size(), visibleRows, column.scrollOffset);
    }

    private UiLayout.Rect columnRect(int index) {
        int columnWidth = (this.teamsArea.width() - COLUMN_GAP * 2) / 3;
        return new UiLayout.Rect(this.teamsArea.x() + index * (columnWidth + COLUMN_GAP), this.teamsArea.y(), columnWidth, this.teamsArea.height());
    }

    private void drawScrollbar(DrawContext context, UiLayout.Rect rect, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows || visibleRows <= 0) {
            return;
        }
        int trackX = rect.x() + rect.width() - 7;
        int trackY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int trackHeight = rect.height() - COLUMN_HEADER_HEIGHT - 8;
        int thumbHeight = Math.max(14, (int) ((trackHeight * (double) visibleRows) / totalRows));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbY = trackY + (int) (((trackHeight - thumbHeight) * (double) scrollOffset) / maxScroll);
        context.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0x66101822);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, UiTheme.BORDER_STRONG);
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
        
        int red = this.getColumn(ColumnKind.RED_TEAM).members.size();
        int blue = this.getColumn(ColumnKind.BLUE_TEAM).members.size();
        int unassigned = this.getEntries(ColumnKind.AVAILABLE).size();
        String players = red + " Red, " + blue + " Blue" + (unassigned > 0 ? " (" + unassigned + " unassigned, will be auto-filled)" : "");
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
        
        NbtCompound redGroup = new NbtCompound();
        redGroup.putString("id", "red");
        redGroup.putString("name", "Red");
        NbtList redMembers = new NbtList();
        NbtList redRoles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.getColumn(ColumnKind.RED_TEAM).members) {
            redMembers.add(this.member(entry));
            redRoles.add(this.roleMember(entry, "member"));
        }
        redGroup.put("members", redMembers);
        redGroup.put("roles", redRoles);
        
        NbtCompound blueGroup = new NbtCompound();
        blueGroup.putString("id", "blue");
        blueGroup.putString("name", "Blue");
        NbtList blueMembers = new NbtList();
        NbtList blueRoles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.getColumn(ColumnKind.BLUE_TEAM).members) {
            blueMembers.add(this.member(entry));
            blueRoles.add(this.roleMember(entry, "member"));
        }
        blueGroup.put("members", blueMembers);
        blueGroup.put("roles", blueRoles);

        // For unassigned players, BridgeMinigame has logic to automatically shuffle players who aren't in teams yet?
        // Actually, let's just assign everyone left in AVAILABLE evenly to red and blue right now if we want, or rely on core.
        // I will add them round-robin to the NBT if they are available.
        int rb = redMembers.size();
        int bb = blueMembers.size();
        for (SessionSnapshotData.RosterEntry entry : this.getEntries(ColumnKind.AVAILABLE)) {
            if (rb <= bb) {
                redMembers.add(this.member(entry));
                redRoles.add(this.roleMember(entry, "member"));
                rb++;
            } else {
                blueMembers.add(this.member(entry));
                blueRoles.add(this.roleMember(entry, "member"));
                bb++;
            }
        }

        groups.add(redGroup);
        groups.add(blueGroup);
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

    private void resetAssignments() {
        this.getColumn(ColumnKind.RED_TEAM).members.clear();
        this.getColumn(ColumnKind.BLUE_TEAM).members.clear();
        this.selectedPlayerUuid = "";
    }

    private void autoAssign() {
        this.resetAssignments();
        List<SessionSnapshotData.RosterEntry> available = new ArrayList<>(this.getEntries(ColumnKind.AVAILABLE));
        // Simple round-robin for auto assign preview
        for (int i = 0; i < available.size(); i++) {
            if (i % 2 == 0) {
                this.getColumn(ColumnKind.RED_TEAM).members.add(available.get(i));
            } else {
                this.getColumn(ColumnKind.BLUE_TEAM).members.add(available.get(i));
            }
        }
        this.status = "Auto-assigned players to Red and Blue.";
    }

    private void moveEntryTo(SessionSnapshotData.RosterEntry entry, ColumnKind target) {
        this.getColumn(ColumnKind.RED_TEAM).remove(entry.uuid());
        this.getColumn(ColumnKind.BLUE_TEAM).remove(entry.uuid());
        if (target != ColumnKind.AVAILABLE) {
            this.getColumn(target).members.add(entry);
        }
        this.selectedPlayerUuid = entry.uuid();
    }

    private List<SessionSnapshotData.RosterEntry> getEntries(ColumnKind kind) {
        if (kind == ColumnKind.AVAILABLE) {
            List<SessionSnapshotData.RosterEntry> available = new ArrayList<>();
            for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
                if (!this.isAssigned(entry.uuid())) {
                    available.add(entry);
                }
            }
            return available;
        }
        return this.getColumn(kind).members;
    }

    private boolean isAssigned(String uuid) {
        return this.getColumn(ColumnKind.RED_TEAM).members.stream().anyMatch(entry -> entry.uuid().equals(uuid))
            || this.getColumn(ColumnKind.BLUE_TEAM).members.stream().anyMatch(entry -> entry.uuid().equals(uuid));
    }

    private boolean isOnline(String uuid) {
        return SessionSnapshotData.roster().stream().anyMatch(entry -> entry.uuid().equals(uuid));
    }

    private ColumnKind dragTarget(double mouseX, double mouseY) {
        for (int i = 0; i < this.columns.size(); i++) {
            UiLayout.Rect rect = this.columnRect(i);
            if (rect.contains(mouseX, mouseY)) {
                return this.columns.get(i).kind;
            }
        }
        return this.draggedFrom;
    }

    private ColumnState getColumn(ColumnKind kind) {
        for (ColumnState column : this.columns) {
            if (column.kind == kind) {
                return column;
            }
        }
        throw new IllegalStateException("Missing column: " + kind);
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

    private enum ColumnKind {
        NONE(""),
        AVAILABLE("Available"),
        RED_TEAM("Red Team"),
        BLUE_TEAM("Blue Team");

        private final String displayName;

        ColumnKind(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class ColumnState {
        private final ColumnKind kind;
        private final String title;
        private final int accentColor;
        private final List<SessionSnapshotData.RosterEntry> members = new ArrayList<>();
        private int scrollOffset;

        private ColumnState(ColumnKind kind, String title, int accentColor) {
            this.kind = kind;
            this.title = title;
            this.accentColor = accentColor;
        }

        private int visibleRows(int height) {
            return Math.max(0, (height - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);
        }

        private int rowAt(double mouseY, UiLayout.Rect rect) {
            int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
            if (mouseY < rowStartY || mouseY > rect.y() + rect.height()) {
                return -1;
            }
            return (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
        }

        private void remove(String uuid) {
            this.members.removeIf(entry -> entry.uuid().equals(uuid));
        }

        private boolean contains(String uuid) {
            return this.members.stream().anyMatch(entry -> entry.uuid().equals(uuid));
        }
    }
}
