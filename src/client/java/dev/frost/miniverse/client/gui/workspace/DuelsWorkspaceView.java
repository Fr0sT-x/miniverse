package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.providers.KitRegistryProvider;
import dev.frost.miniverse.minigame.core.kit.Kit;
import dev.frost.miniverse.minigame.core.kit.KitRegistry;
import dev.frost.miniverse.minigame.impl.duels.DuelsDefinition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DuelsWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider, GamemodeWorkspaceView.RosterRefreshable {

    private static final int TEAM_ROW_HEIGHT = 20;
    private static final int COLUMN_HEADER_HEIGHT = 22;
    private static final int COLUMN_GAP = 12;

    private SessionScreen parentScreen;
    private UiLayout.Rect workspace = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect teamsArea = new UiLayout.Rect(0, 0, 0, 0);
    private String activeModuleId = "team_selection";

    // --- State ---
    // Team Selection
    private final List<ColumnState> columns = new ArrayList<>();
    private final Map<String, UiAnimation.Value> rowHovers = new HashMap<>();
    private SessionSnapshotData.RosterEntry draggedEntry;
    private ColumnKind draggedFrom = ColumnKind.NONE;
    private double dragX;
    private double dragY;
    private String selectedPlayerUuid = "";
    private ButtonWidget randomizeTeamsBtn;

    // Duel Types
    private TextFieldWidget newTypeNameField;
    private ButtonWidget createTypeBtn;
    private ButtonWidget confirmCreateTypeBtn;
    private ButtonWidget cancelCreateTypeBtn;
    private dev.frost.miniverse.minigame.impl.duels.DuelType selectedType = null;
    private SessionSnapshotData.MapSummary selectedMap = null;
    private Kit selectedKit = null;
    private ButtonWidget startMatchBtn;
    private boolean createTypeDialogOpen;
    
    // Duel Type Configs
    private boolean configKnockbackOnly = false;
    private boolean configAllowBuilding = true;
    private boolean configAllowBreaking = true;
    private boolean configAllowHunger = true;
    private boolean configNaturalRegen = true;
    private ButtonWidget btnKnockbackOnly;
    private ButtonWidget btnAllowBuilding;
    private ButtonWidget btnAllowBreaking;
    private ButtonWidget btnAllowHunger;
    private ButtonWidget btnNaturalRegen;

    // Kits
    private TextFieldWidget kitNameField;
    private ButtonWidget createKitBtn;
    private ButtonWidget openKitRegistryBtn;
    private dev.frost.miniverse.minigame.impl.duels.DuelType kitSelectedType = null;
    private final RegistrySelectorState kitRegistryState = new RegistrySelectorState();

    public DuelsWorkspaceView() {
        this.columns.add(new ColumnState(ColumnKind.AVAILABLE, "Available", 0x7C8088));
        this.columns.add(new ColumnState(ColumnKind.TEAM_1, "Team 1", 0xDD3333));
        this.columns.add(new ColumnState(ColumnKind.TEAM_2, "Team 2", 0x3344DD));
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.parentScreen = screen;
        this.workspace = workspace;
        UiLayout.Rect mainPanel = workspace.inset(4);
        this.teamsArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 84, mainPanel.width() - 24, mainPanel.height() - 106);
        int dialogW = 260;
        int dialogX = mainPanel.x() + (mainPanel.width() - dialogW) / 2;
        int dialogY = mainPanel.y() + 48;

        // Team Selection Widgets
        this.randomizeTeamsBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Randomize Teams"), btn -> {
            this.autoAssign();
        }).dimensions(mainPanel.x() + 14, mainPanel.y() + 40, 120, 20).build());
        this.randomizeTeamsBtn.visible = activeModuleId.equals("team_selection");

        // Duel Types Widgets
        this.newTypeNameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, dialogX + 14, dialogY + 48, dialogW - 28, 20, Text.literal("Type Name"));
        screen.addWorkspaceChild(this.newTypeNameField);
        this.newTypeNameField.setVisible(this.createTypeDialogOpen);

        this.btnKnockbackOnly = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Knockback Only: OFF"), btn -> {
            this.configKnockbackOnly = !this.configKnockbackOnly;
            btn.setMessage(Text.literal("Knockback Only: " + (this.configKnockbackOnly ? "ON" : "OFF")));
        }).dimensions(dialogX + 14, dialogY + 76, dialogW - 28, 20).build());
        this.btnKnockbackOnly.visible = this.createTypeDialogOpen;

        this.btnAllowBuilding = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Allow Building: ON"), btn -> {
            this.configAllowBuilding = !this.configAllowBuilding;
            btn.setMessage(Text.literal("Allow Building: " + (this.configAllowBuilding ? "ON" : "OFF")));
        }).dimensions(dialogX + 14, dialogY + 101, dialogW - 28, 20).build());
        this.btnAllowBuilding.visible = this.createTypeDialogOpen;

        this.btnAllowBreaking = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Allow Breaking: ON"), btn -> {
            this.configAllowBreaking = !this.configAllowBreaking;
            btn.setMessage(Text.literal("Allow Breaking: " + (this.configAllowBreaking ? "ON" : "OFF")));
        }).dimensions(dialogX + 14, dialogY + 126, dialogW - 28, 20).build());
        this.btnAllowBreaking.visible = this.createTypeDialogOpen;

        this.btnAllowHunger = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Allow Hunger: ON"), btn -> {
            this.configAllowHunger = !this.configAllowHunger;
            btn.setMessage(Text.literal("Allow Hunger: " + (this.configAllowHunger ? "ON" : "OFF")));
        }).dimensions(dialogX + 14, dialogY + 151, dialogW - 28, 20).build());
        this.btnAllowHunger.visible = this.createTypeDialogOpen;

        this.btnNaturalRegen = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Natural Regen: ON"), btn -> {
            this.configNaturalRegen = !this.configNaturalRegen;
            btn.setMessage(Text.literal("Natural Regen: " + (this.configNaturalRegen ? "ON" : "OFF")));
        }).dimensions(dialogX + 14, dialogY + 176, dialogW - 28, 20).build());
        this.btnNaturalRegen.visible = this.createTypeDialogOpen;

        this.createTypeBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Create Type"), btn -> {
            this.createTypeDialogOpen = true;
            this.parentScreen.openWorkspaceView(this);
        }).dimensions(mainPanel.x() + 14, mainPanel.y() + 40, 110, 20).build());
        this.createTypeBtn.visible = activeModuleId.equals("duel_types") && !this.createTypeDialogOpen;

        this.confirmCreateTypeBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Create Duel Type"), btn -> {
            if (!this.newTypeNameField.getText().isBlank()) {
                String typeName = this.newTypeNameField.getText().trim();
                String id = typeName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("_+", "_");
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.frost.miniverse.common.NetworkConstants.CreateDuelTypePayload(
                        id, typeName, this.configKnockbackOnly, this.configAllowBuilding, this.configAllowBreaking, this.configAllowHunger, this.configNaturalRegen
                    )
                );
                this.newTypeNameField.setText("");
                this.createTypeDialogOpen = false;
                this.parentScreen.openWorkspaceView(this);
            }
        }).dimensions(dialogX + 14, dialogY + 202, 145, 20).build());
        this.confirmCreateTypeBtn.visible = this.createTypeDialogOpen;

        this.cancelCreateTypeBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> {
            this.createTypeDialogOpen = false;
            this.parentScreen.openWorkspaceView(this);
        }).dimensions(dialogX + 166, dialogY + 202, 66, 20).build());
        this.cancelCreateTypeBtn.visible = this.createTypeDialogOpen;

        this.startMatchBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("START MATCH").formatted(net.minecraft.util.Formatting.GREEN, net.minecraft.util.Formatting.BOLD), btn -> {
            if (this.selectedType != null && this.selectedMap != null && this.selectedKit != null) {
                if (this.getColumn(ColumnKind.TEAM_1).members.isEmpty() || this.getColumn(ColumnKind.TEAM_2).members.isEmpty()) return;

                net.minecraft.nbt.NbtCompound settings = new net.minecraft.nbt.NbtCompound();
                settings.putString("mapId", this.selectedMap.id());
                settings.putString("duelType", this.selectedType.id());
                settings.putString("kitId", this.selectedKit.getId().toString());

                net.minecraft.nbt.NbtCompound nbtPlan = new net.minecraft.nbt.NbtCompound();
                nbtPlan.putString("game", DuelsDefinition.ID);
                nbtPlan.putString("name", "Duel " + this.selectedType.name());
                nbtPlan.putBoolean("launch", true);
                nbtPlan.put("settings", settings);
                net.minecraft.nbt.NbtList groups = new net.minecraft.nbt.NbtList();
                groups.add(this.group("team_1", this.getColumn(ColumnKind.TEAM_1).members));
                groups.add(this.group("team_2", this.getColumn(ColumnKind.TEAM_2).members));
                nbtPlan.put("groups", groups);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.frost.miniverse.common.NetworkConstants.CreateSessionPayload(DuelsDefinition.ID, "Duel " + this.selectedType.name(), nbtPlan)
                );
            }
        }).dimensions(mainPanel.x() + mainPanel.width() - 140, mainPanel.y() + mainPanel.height() - 35, 120, 20).build());
        this.startMatchBtn.visible = activeModuleId.equals("duel_types");

        // Kits Widgets
        this.kitNameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, mainPanel.x() + 14, mainPanel.y() + 40, 150, 20, Text.literal("Kit Name"));
        screen.addWorkspaceChild(this.kitNameField);
        this.kitNameField.setVisible(activeModuleId.equals("kits"));

        this.createKitBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Create Kit"), btn -> {
            if (this.kitSelectedType != null && !this.kitNameField.getText().isBlank()) {
                String kitName = this.kitNameField.getText().trim();
                String id = kitName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("_+", "_");
                String categories = "duels,duel_type:" + this.kitSelectedType.id();
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.CreateKitPayload(id, kitName, categories));
                this.kitNameField.setText("");
            }
        }).dimensions(mainPanel.x() + 170, mainPanel.y() + 40, 100, 20).build());
        this.createKitBtn.visible = activeModuleId.equals("kits");

        this.openKitRegistryBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Open Kit Registry"), btn -> {
            RegistrySelectorContext<Kit> context = new RegistrySelectorContext<>(
                "miniverse:kit",
                "Duel Kits",
                RegistrySelectorContext.SelectionMode.SINGLE,
                this.kitRegistryState,
                result -> {},
                "duel_kits",
                Set.of()
            );
            MinecraftClient.getInstance().setScreen(new RegistrySelectorScreen<>(context, new KitRegistryProvider()));
        }).dimensions(mainPanel.x() + 280, mainPanel.y() + 40, 120, 20).build());
        this.openKitRegistryBtn.visible = activeModuleId.equals("kits");
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        
        int yOffset = mainPanel.y() + 14;

        if (activeModuleId.equals("team_selection")) {
            context.drawText(textRenderer, Text.literal("Team Selection"), mainPanel.x() + 14, yOffset, UiTheme.TEXT, false);
            this.renderTeams(context, textRenderer, this.teamsArea, mouseX, mouseY);
        } else if (activeModuleId.equals("duel_types")) {
            context.drawText(textRenderer, Text.literal("Duel Types"), mainPanel.x() + 14, yOffset, UiTheme.TEXT, false);
            int rowY = mainPanel.y() + 70;
            for (dev.frost.miniverse.minigame.impl.duels.DuelType type : SessionSnapshotData.duelTypes()) {
                UiRenderer.panel(context, mainPanel.x() + 14, rowY, 150, 30, this.sameType(this.selectedType, type) ? UiTheme.CARD_HOVER : UiTheme.CARD, UiTheme.BORDER_SUBTLE);
                context.drawText(textRenderer, Text.literal(type.name()), mainPanel.x() + 24, rowY + 10, UiTheme.TEXT, false);
                rowY += 34;
            }

            if (this.selectedType != null) {
                context.drawText(textRenderer, Text.literal("Supported Maps for " + this.selectedType.name() + ":"), mainPanel.x() + 180, mainPanel.y() + 70, UiTheme.TEXT, false);
                int mapY = mainPanel.y() + 90;
                List<SessionSnapshotData.MapSummary> maps = SessionSnapshotData.maps().stream()
                    .filter(m -> m.supports(DuelsDefinition.ID) && m.validFor(DuelsDefinition.ID) && m.hasTag("duel_type:" + this.selectedType.id()))
                    .toList();
                
                if (maps.isEmpty()) {
                    context.drawText(textRenderer, Text.literal("No maps support this duel type."), mainPanel.x() + 180, mapY, UiTheme.TEXT_MUTED, false);
                    mapY += 34;
                } else {
                    for (SessionSnapshotData.MapSummary map : maps) {
                        UiRenderer.panel(context, mainPanel.x() + 180, mapY, 200, 30, this.selectedMap == map ? UiTheme.CARD_HOVER : UiTheme.CARD, UiTheme.BORDER_SUBTLE);
                        context.drawText(textRenderer, Text.literal(map.name()), mainPanel.x() + 190, mapY + 10, UiTheme.TEXT, false);
                        mapY += 34;
                    }
                }

                context.drawText(textRenderer, Text.literal("Available Kits:"), mainPanel.x() + 180, mapY, UiTheme.TEXT, false);
                int kitY = mapY + 20;
                List<Kit> kits = KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.selectedType.id()))
                    .toList();
                
                if (kits.isEmpty()) {
                    context.drawText(textRenderer, Text.literal("No kits for this duel type."), mainPanel.x() + 180, kitY, UiTheme.TEXT_MUTED, false);
                } else {
                    for (Kit kit : kits) {
                        UiRenderer.panel(context, mainPanel.x() + 180, kitY, 200, 30, this.selectedKit == kit ? UiTheme.CARD_HOVER : UiTheme.CARD, UiTheme.BORDER_SUBTLE);
                        context.drawText(textRenderer, kit.getDisplayName(), mainPanel.x() + 190, kitY + 10, UiTheme.TEXT, false);
                        kitY += 34;
                    }
                }
            }
        } else if (activeModuleId.equals("kits")) {
            context.drawText(textRenderer, Text.literal("Kit Creation"), mainPanel.x() + 14, yOffset, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Select a Duel Type below to bind this kit:"), mainPanel.x() + 14, yOffset + 60, UiTheme.TEXT_MUTED, false);

            int rowY = mainPanel.y() + 90;
            for (dev.frost.miniverse.minigame.impl.duels.DuelType type : SessionSnapshotData.duelTypes()) {
                UiRenderer.panel(context, mainPanel.x() + 14, rowY, 150, 30, this.sameType(this.kitSelectedType, type) ? UiTheme.CARD_HOVER : UiTheme.CARD, UiTheme.BORDER_SUBTLE);
                context.drawText(textRenderer, Text.literal(type.name()), mainPanel.x() + 24, rowY + 10, UiTheme.TEXT, false);
                rowY += 34;
            }

            if (this.kitSelectedType != null) {
                context.drawText(textRenderer, Text.literal("Kits for " + this.kitSelectedType.name() + ":"), mainPanel.x() + 180, mainPanel.y() + 70, UiTheme.TEXT, false);
                int kitY = mainPanel.y() + 90;
                List<Kit> kits = KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.kitSelectedType.id()))
                    .toList();
                
                if (kits.isEmpty()) {
                    context.drawText(textRenderer, Text.literal("No kits created yet."), mainPanel.x() + 180, kitY, UiTheme.TEXT_MUTED, false);
                } else {
                    for (Kit kit : kits) {
                        UiRenderer.panel(context, mainPanel.x() + 180, kitY, 200, 30, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
                        context.drawText(textRenderer, kit.getDisplayName(), mainPanel.x() + 190, kitY + 10, UiTheme.TEXT, false);
                        kitY += 34;
                    }
                }
            }
        }
        if (this.createTypeDialogOpen) {
            int dialogW = 260;
            int dialogH = 236;
            int dialogX = mainPanel.x() + (mainPanel.width() - dialogW) / 2;
            int dialogY = mainPanel.y() + 48;
            context.fill(mainPanel.x(), mainPanel.y(), mainPanel.x() + mainPanel.width(), mainPanel.y() + mainPanel.height(), 0x99000000);
            UiRenderer.panel(context, dialogX, dialogY, dialogW, dialogH, UiTheme.PANEL_RAISED, UiTheme.ACCENT_BLUE);
            context.drawText(textRenderer, Text.literal("Create Duel Type"), dialogX + 14, dialogY + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Name"), dialogX + 14, dialogY + 34, UiTheme.TEXT_MUTED, false);
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
        if (activeModuleId.equals("team_selection")) {
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
        
        UiLayout.Rect mainPanel = workspace.inset(4);
        if (this.createTypeDialogOpen) {
            return false;
        }

        if (activeModuleId.equals("duel_types")) {
            int rowY = mainPanel.y() + 70;
            for (dev.frost.miniverse.minigame.impl.duels.DuelType type : SessionSnapshotData.duelTypes()) {
                UiLayout.Rect rect = new UiLayout.Rect(mainPanel.x() + 14, rowY, 150, 30);
                if (rect.contains(mouseX, mouseY)) {
                    this.selectedType = type;
                    this.selectedMap = null; // reset map on type change
                    this.selectedKit = null;
                    return true;
                }
                rowY += 34;
            }

            if (this.selectedType != null) {
                int mapY = mainPanel.y() + 90;
                List<SessionSnapshotData.MapSummary> maps = SessionSnapshotData.maps().stream()
                    .filter(m -> m.supports(DuelsDefinition.ID) && m.validFor(DuelsDefinition.ID) && m.hasTag("duel_type:" + this.selectedType.id()))
                    .toList();
                
                for (SessionSnapshotData.MapSummary map : maps) {
                    UiLayout.Rect rect = new UiLayout.Rect(mainPanel.x() + 180, mapY, 200, 30);
                    if (rect.contains(mouseX, mouseY)) {
                        this.selectedMap = map;
                        return true;
                    }
                    mapY += 34;
                }

                if (maps.isEmpty()) mapY += 34;

                int kitY = mapY + 20;
                List<Kit> kits = KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.selectedType.id()))
                    .toList();
                
                for (Kit kit : kits) {
                    UiLayout.Rect rect = new UiLayout.Rect(mainPanel.x() + 180, kitY, 200, 30);
                    if (rect.contains(mouseX, mouseY)) {
                        this.selectedKit = kit;
                        return true;
                    }
                    kitY += 34;
                }
            }
        } else if (activeModuleId.equals("kits")) {
            int rowY = mainPanel.y() + 90;
            for (dev.frost.miniverse.minigame.impl.duels.DuelType type : SessionSnapshotData.duelTypes()) {
                UiLayout.Rect rect = new UiLayout.Rect(mainPanel.x() + 14, rowY, 150, 30);
                if (rect.contains(mouseX, mouseY)) {
                    this.kitSelectedType = type;
                    return true;
                }
                rowY += 34;
            }
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
        }
        this.draggedEntry = null;
        this.draggedFrom = ColumnKind.NONE;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!activeModuleId.equals("team_selection")) {
            return false;
        }
        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            UiLayout.Rect rect = this.columnRect(i);
            if (rect.contains(mouseX, mouseY)) {
                int maxScroll = Math.max(0, this.getEntries(column.kind).size() - column.visibleRows(rect.height()));
                column.scrollOffset = Math.clamp(column.scrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                return maxScroll > 0;
            }
        }
        return false;
    }

    @Override
    public void refreshRoster() {
        this.getColumn(ColumnKind.TEAM_1).members.removeIf(entry -> !this.isOnline(entry.uuid()));
        this.getColumn(ColumnKind.TEAM_2).members.removeIf(entry -> !this.isOnline(entry.uuid()));
    }

    private void autoAssign() {
        this.getColumn(ColumnKind.TEAM_1).members.clear();
        this.getColumn(ColumnKind.TEAM_2).members.clear();
        this.selectedPlayerUuid = "";
        List<SessionSnapshotData.RosterEntry> available = new ArrayList<>(this.getEntries(ColumnKind.AVAILABLE));
        for (int i = 0; i < available.size(); i++) {
            if (i % 2 == 0) {
                this.getColumn(ColumnKind.TEAM_1).members.add(available.get(i));
            } else {
                this.getColumn(ColumnKind.TEAM_2).members.add(available.get(i));
            }
        }
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

    private void moveEntryTo(SessionSnapshotData.RosterEntry entry, ColumnKind target) {
        this.getColumn(ColumnKind.TEAM_1).remove(entry.uuid());
        this.getColumn(ColumnKind.TEAM_2).remove(entry.uuid());
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
        return this.getColumn(ColumnKind.TEAM_1).members.stream().anyMatch(entry -> entry.uuid().equals(uuid))
            || this.getColumn(ColumnKind.TEAM_2).members.stream().anyMatch(entry -> entry.uuid().equals(uuid));
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

    private boolean sameType(dev.frost.miniverse.minigame.impl.duels.DuelType left, dev.frost.miniverse.minigame.impl.duels.DuelType right) {
        return left != null && right != null && left.id().equalsIgnoreCase(right.id());
    }

    private net.minecraft.nbt.NbtCompound group(String label, List<SessionSnapshotData.RosterEntry> members) {
        net.minecraft.nbt.NbtCompound group = new net.minecraft.nbt.NbtCompound();
        group.putString("label", label);
        net.minecraft.nbt.NbtList memberList = new net.minecraft.nbt.NbtList();
        for (SessionSnapshotData.RosterEntry entry : members) {
            net.minecraft.nbt.NbtCompound member = new net.minecraft.nbt.NbtCompound();
            member.putString("uuid", entry.uuid());
            member.putString("name", entry.name());
            memberList.add(member);
        }
        group.put("members", memberList);
        return group;
    }

    @Override
    public String title() { return "Duels Setup"; }

    @Override
    public String subtitle() { return "Configure Duel variants and arenas"; }

    @Override
    public String gameId() { return DuelsDefinition.ID; }

    @Override
    public List<WorkspaceModule> modules() {
        return List.of(
            new WorkspaceModule("team_selection", "T", "Team Selection", "Teams"),
            new WorkspaceModule("duel_types", "D", "Duel Types", "Types"),
            new WorkspaceModule("kits", "K", "Kits", "Kits")
        );
    }

    @Override
    public String activeModuleId() { return activeModuleId; }

    @Override
    public void setActiveModule(String moduleId) {
        this.activeModuleId = moduleId;
        if (this.parentScreen != null) {
            this.parentScreen.openWorkspaceView(this);
        }
    }

    private enum ColumnKind {
        NONE,
        AVAILABLE,
        TEAM_1,
        TEAM_2;
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
