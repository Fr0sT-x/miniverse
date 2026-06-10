package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
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

    private final StaticTeamSelectionGrid teamGrid = new StaticTeamSelectionGrid();
    private ButtonWidget randomizeTeamsBtn;

    // Duel Types
    private TextFieldWidget newTypeNameField;
    private ButtonWidget createTypeBtn;
    private ButtonWidget confirmCreateTypeBtn;
    private ButtonWidget cancelCreateTypeBtn;
    private dev.frost.miniverse.minigame.impl.duels.DuelType selectedType = null;
    private SessionSnapshotData.MapSummary selectedMap = null;
    private Kit selectedKit = null;
    private UiLayout.Rect startButton;
    private boolean createTypeDialogOpen;
    private boolean isEditingType;
    
    private int duelTypeScrollOffset = 0;
    private dev.frost.miniverse.client.gui.workspace.components.MapThumbnailGrid mapGrid;
    private int duelKitScrollOffset = 0;

    private boolean contextMenuOpen = false;
    private dev.frost.miniverse.minigame.impl.duels.DuelType contextMenuTarget = null;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    
    private String kitStatusMessage = null;
    private long kitStatusMessageTime = 0;
    private ButtonWidget contextEditBtn;
    private ButtonWidget contextDeleteBtn;

    private boolean deleteWarningOpen = false;
    private dev.frost.miniverse.minigame.impl.duels.DuelType deleteTarget = null;
    private ButtonWidget confirmDeleteBtn;
    private ButtonWidget cancelDeleteBtn;
    
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
    private ButtonWidget confirmCreateKitBtn;
    private ButtonWidget cancelCreateKitBtn;
    private ButtonWidget openKitRegistryBtn;
    private dev.frost.miniverse.minigame.impl.duels.DuelType kitSelectedType = null;
    private final RegistrySelectorState kitRegistryState = new RegistrySelectorState();
    
    private boolean createKitDialogOpen;
    private boolean isEditingKit;
    private Kit kitContextMenuTarget = null;
    private Kit kitDeleteTarget = null;
    
    private int kitTypeScrollOffset = 0;
    private int kitListScrollOffset = 0;

    private boolean kitContextMenuOpen = false;
    private int kitContextMenuX = 0;
    private int kitContextMenuY = 0;
    private ButtonWidget kitContextEditBtn;
    private ButtonWidget kitContextDeleteBtn;

    private boolean kitDeleteWarningOpen = false;
    private ButtonWidget kitConfirmDeleteBtn;
    private ButtonWidget kitCancelDeleteBtn;

    public DuelsWorkspaceView() {
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("team_1", "Team 1", 0xDD3333, false);
        this.teamGrid.addColumn("team_2", "Team 2", 0x3344DD, false);
        
        this.mapGrid = new dev.frost.miniverse.client.gui.workspace.components.MapThumbnailGrid("Supported Maps", mapId -> {
            SessionSnapshotData.maps().stream().filter(m -> m.id().equals(mapId)).findFirst().ifPresent(m -> {
                this.selectedMap = m;
                this.mapGrid.setSelectedMapId(m.id());
            });
        });
        this.mapGrid.setAccentColor(0xFF00AA66);
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.parentScreen = screen;
        this.workspace = workspace;
        UiLayout.Rect mainPanel = workspace.inset(4);
        this.teamsArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 84, mainPanel.width() - 24, mainPanel.height() - 106);
        this.teamGrid.setBounds(this.teamsArea);
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
        if (this.isEditingType && this.selectedType != null) {
            this.newTypeNameField.setText(this.selectedType.name());
        }
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
            this.isEditingType = false;
            this.createTypeDialogOpen = true;
            this.newTypeNameField.setText("");
            this.configKnockbackOnly = false;
            this.configAllowBuilding = true;
            this.configAllowBreaking = true;
            this.configAllowHunger = true;
            this.configNaturalRegen = true;
            this.updateConfigButtons();
            this.parentScreen.openWorkspaceView(this);
        }).dimensions(mainPanel.x() + 14, mainPanel.y() + 40, 110, 20).build());
        this.createTypeBtn.visible = activeModuleId.equals("duel_types") && !this.createTypeDialogOpen && !this.deleteWarningOpen;

        this.confirmCreateTypeBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Save Type"), btn -> {
            if (!this.newTypeNameField.getText().isBlank()) {
                String typeName = this.newTypeNameField.getText().trim();
                String id = this.isEditingType ? this.selectedType.id() : typeName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("_+", "_");
                if (this.isEditingType) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.frost.miniverse.common.NetworkConstants.EditDuelTypePayload(
                            id, typeName, this.configKnockbackOnly, this.configAllowBuilding, this.configAllowBreaking, this.configAllowHunger, this.configNaturalRegen
                        )
                    );
                } else {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.frost.miniverse.common.NetworkConstants.CreateDuelTypePayload(
                            id, typeName, this.configKnockbackOnly, this.configAllowBuilding, this.configAllowBreaking, this.configAllowHunger, this.configNaturalRegen
                        )
                    );
                }
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

        this.contextEditBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Edit"), btn -> {
            if (this.contextMenuTarget != null) {
                this.selectedType = this.contextMenuTarget;
                this.newTypeNameField.setText(this.contextMenuTarget.name());
                this.configKnockbackOnly = this.contextMenuTarget.knockbackOnly();
                this.configAllowBuilding = this.contextMenuTarget.allowBuilding();
                this.configAllowBreaking = this.contextMenuTarget.allowBreaking();
                this.configAllowHunger = this.contextMenuTarget.allowHunger();
                this.configNaturalRegen = this.contextMenuTarget.naturalRegen();
                this.updateConfigButtons();
                this.isEditingType = true;
                this.createTypeDialogOpen = true;
                this.contextMenuOpen = false;
                this.parentScreen.openWorkspaceView(this);
            }
        }).dimensions(0, 0, 100, 20).build());
        this.contextEditBtn.visible = false;

        this.contextDeleteBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Delete").formatted(net.minecraft.util.Formatting.RED), btn -> {
            if (this.contextMenuTarget != null) {
                this.deleteTarget = this.contextMenuTarget;
                this.deleteWarningOpen = true;
                this.contextMenuOpen = false;
                this.parentScreen.openWorkspaceView(this);
            }
        }).dimensions(0, 0, 100, 20).build());
        this.contextDeleteBtn.visible = false;

        this.confirmDeleteBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Confirm Delete").formatted(net.minecraft.util.Formatting.RED), btn -> {
            if (this.deleteTarget != null) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.frost.miniverse.common.NetworkConstants.DeleteDuelTypePayload(this.deleteTarget.id())
                );
                if (this.selectedType != null && this.selectedType.id().equals(this.deleteTarget.id())) {
                    this.selectedType = null;
                    this.selectedMap = null;
                    this.selectedKit = null;
                }
                this.deleteWarningOpen = false;
                this.deleteTarget = null;
                this.parentScreen.openWorkspaceView(this);
            }
        }).dimensions(dialogX + 14, dialogY + 120, 145, 20).build());
        this.confirmDeleteBtn.visible = this.deleteWarningOpen;

        this.cancelDeleteBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> {
            this.deleteWarningOpen = false;
            this.deleteTarget = null;
            this.parentScreen.openWorkspaceView(this);
        }).dimensions(dialogX + 166, dialogY + 120, 66, 20).build());
        this.cancelDeleteBtn.visible = this.deleteWarningOpen;

        this.startButton = new UiLayout.Rect(mainPanel.x() + mainPanel.width() - 126, mainPanel.y() + 10, 112, 22);

        // Kit Management Widgets
        this.kitNameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, dialogX + 14, dialogY + 48, dialogW - 28, 20, Text.literal("Kit Name"));
        if (this.isEditingKit && this.kitContextMenuTarget != null) {
            this.kitNameField.setText(this.kitContextMenuTarget.getDisplayName().getString());
        }
        screen.addWorkspaceChild(this.kitNameField);
        this.kitNameField.setVisible(this.createKitDialogOpen);

        this.createKitBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Create Kit"), btn -> {
            if (this.kitSelectedType != null) {
                this.isEditingKit = false;
                this.createKitDialogOpen = true;
                this.kitNameField.setText("");
                this.parentScreen.openWorkspaceView(this);
            } else {
                this.kitStatusMessage = "Select a Duel type first.";
                this.kitStatusMessageTime = System.currentTimeMillis();
            }
        }).dimensions(mainPanel.x() + 14, mainPanel.y() + 40, 110, 20).build());
        this.createKitBtn.visible = activeModuleId.equals("kits") && !this.createKitDialogOpen && !this.kitDeleteWarningOpen;

        this.confirmCreateKitBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Save Kit"), btn -> {
            if (this.kitSelectedType != null && !this.kitNameField.getText().isBlank()) {
                String kitName = this.kitNameField.getText().trim();
                if (this.isEditingKit && this.kitContextMenuTarget != null) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.frost.miniverse.common.NetworkConstants.RenameKitPayload(this.kitContextMenuTarget.getId().toString(), kitName)
                    );
                } else {
                    String id = kitName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("_+", "_");
                    String categories = "duel_type:" + this.kitSelectedType.id();
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                        new dev.frost.miniverse.common.NetworkConstants.CreateKitPayload(id, kitName, categories)
                    );
                }
                this.kitNameField.setText("");
                this.createKitDialogOpen = false;
                this.parentScreen.openWorkspaceView(this);
            }
        }).dimensions(dialogX + 14, dialogY + 80, 145, 20).build());
        this.confirmCreateKitBtn.visible = this.createKitDialogOpen;

        this.cancelCreateKitBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> {
            this.createKitDialogOpen = false;
            this.parentScreen.openWorkspaceView(this);
        }).dimensions(dialogX + 166, dialogY + 80, 66, 20).build());
        this.cancelCreateKitBtn.visible = this.createKitDialogOpen;

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
        }).dimensions(mainPanel.x() + 130, mainPanel.y() + 40, 120, 20).build());
        this.openKitRegistryBtn.visible = activeModuleId.equals("kits") && !this.createKitDialogOpen && !this.kitDeleteWarningOpen;

        this.kitContextEditBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Rename"), btn -> {
            if (this.kitContextMenuTarget != null) {
                this.kitNameField.setText(this.kitContextMenuTarget.getDisplayName().getString());
                this.isEditingKit = true;
                this.createKitDialogOpen = true;
                this.kitContextMenuOpen = false;
                if (this.confirmCreateKitBtn != null) this.confirmCreateKitBtn.setMessage(Text.literal("Save Changes"));
                this.parentScreen.openWorkspaceView(this);
            }
        }).dimensions(0, 0, 100, 20).build());
        this.kitContextEditBtn.visible = false;

        this.kitContextDeleteBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Delete").formatted(net.minecraft.util.Formatting.RED), btn -> {
            if (this.kitContextMenuTarget != null) {
                this.kitDeleteTarget = this.kitContextMenuTarget;
                this.kitDeleteWarningOpen = true;
                this.kitContextMenuOpen = false;
                this.parentScreen.openWorkspaceView(this);
            }
        }).dimensions(0, 0, 100, 20).build());
        this.kitContextDeleteBtn.visible = false;

        this.kitConfirmDeleteBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Confirm Delete").formatted(net.minecraft.util.Formatting.RED), btn -> {
            if (this.kitDeleteTarget != null) {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.frost.miniverse.common.NetworkConstants.DeleteKitPayload(this.kitDeleteTarget.getId().toString())
                );
                this.kitDeleteTarget = null;
                this.kitDeleteWarningOpen = false;
                this.parentScreen.openWorkspaceView(this);
            }
        }).dimensions(dialogX + 14, dialogY + 80, 145, 20).build());
        this.kitConfirmDeleteBtn.visible = this.kitDeleteWarningOpen;

        this.kitCancelDeleteBtn = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Cancel"), btn -> {
            this.kitDeleteWarningOpen = false;
            this.kitDeleteTarget = null;
            this.parentScreen.openWorkspaceView(this);
        }).dimensions(dialogX + 166, dialogY + 80, 66, 20).build());
        this.kitCancelDeleteBtn.visible = this.kitDeleteWarningOpen;
        
        this.updateConfigButtons();

        if (this.contextMenuOpen) {
            this.contextEditBtn.setX(this.contextMenuX);
            this.contextEditBtn.setY(this.contextMenuY);
            this.contextEditBtn.visible = true;
            this.contextDeleteBtn.setX(this.contextMenuX);
            this.contextDeleteBtn.setY(this.contextMenuY + 20);
            this.contextDeleteBtn.visible = true;
        }

        if (this.kitContextMenuOpen) {
            this.kitContextEditBtn.setX(this.kitContextMenuX);
            this.kitContextEditBtn.setY(this.kitContextMenuY);
            this.kitContextEditBtn.visible = true;
            this.kitContextDeleteBtn.setX(this.kitContextMenuX);
            this.kitContextDeleteBtn.setY(this.kitContextMenuY + 20);
            this.kitContextDeleteBtn.visible = true;
        }
    }

    private void renderActionButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(accent, 0.34F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, border);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    private void updateConfigButtons() {
        if (this.btnKnockbackOnly != null) this.btnKnockbackOnly.setMessage(Text.literal("Knockback Only: " + (this.configKnockbackOnly ? "ON" : "OFF")));
        if (this.btnAllowBuilding != null) this.btnAllowBuilding.setMessage(Text.literal("Allow Building: " + (this.configAllowBuilding ? "ON" : "OFF")));
        if (this.btnAllowBreaking != null) this.btnAllowBreaking.setMessage(Text.literal("Allow Breaking: " + (this.configAllowBreaking ? "ON" : "OFF")));
        if (this.btnAllowHunger != null) this.btnAllowHunger.setMessage(Text.literal("Allow Hunger: " + (this.configAllowHunger ? "ON" : "OFF")));
        if (this.btnNaturalRegen != null) this.btnNaturalRegen.setMessage(Text.literal("Natural Regen: " + (this.configNaturalRegen ? "ON" : "OFF")));
        if (this.confirmCreateTypeBtn != null) this.confirmCreateTypeBtn.setMessage(Text.literal(this.isEditingType ? "Save Changes" : "Create Duel Type"));
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        
        int yOffset = mainPanel.y() + 14;

        if (activeModuleId.equals("team_selection")) {
            context.drawText(textRenderer, Text.literal("Team Selection"), mainPanel.x() + 14, yOffset, UiTheme.TEXT, false);
            this.teamGrid.render(context, textRenderer, mouseX, mouseY, delta);
        } else if (activeModuleId.equals("duel_types")) {
            context.drawText(textRenderer, Text.literal("Duel Types"), mainPanel.x() + 14, yOffset, UiTheme.TEXT, false);
            
            // Draw 3 columns
            int columnWidth = (this.teamsArea.width() - COLUMN_GAP * 2) / 3;
            
            // Col 1: Types
            UiLayout.Rect rect1 = new UiLayout.Rect(this.teamsArea.x(), this.teamsArea.y(), columnWidth, this.teamsArea.height());
            List<dev.frost.miniverse.minigame.impl.duels.DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
            this.renderGenericColumn(context, textRenderer, rect1, "Duel Types (" + types.size() + ")", 0xFF6600CC, types.size(), this.duelTypeScrollOffset, mouseX, mouseY, row -> {
                int index = this.duelTypeScrollOffset + row;
                if (index < types.size()) {
                    dev.frost.miniverse.minigame.impl.duels.DuelType t = types.get(index);
                    boolean isSelected = this.sameType(this.selectedType, t);
                    int rowY = rect1.y() + COLUMN_HEADER_HEIGHT + 4 + row * TEAM_ROW_HEIGHT;
                    int bg = isSelected ? 0x663E79B8 : 0x26222A34;
                    if (!isSelected && mouseX >= rect1.x() && mouseX <= rect1.x() + rect1.width() && mouseY >= rowY && mouseY < rowY + TEAM_ROW_HEIGHT) {
                        bg = 0x44304052;
                    }
                    context.fill(rect1.x() + 1, rowY, rect1.x() + rect1.width() - 1, rowY + TEAM_ROW_HEIGHT - 2, bg);
                    context.drawText(textRenderer, Text.literal(t.name()), rect1.x() + 12, rowY + 6, isSelected ? UiTheme.TEXT : UiTheme.TEXT_MUTED, false);
                }
            });

            // Col 2: Maps
            UiLayout.Rect rect2 = new UiLayout.Rect(this.teamsArea.x() + columnWidth + COLUMN_GAP, this.teamsArea.y(), columnWidth, this.teamsArea.height());
            this.mapGrid.setBounds(rect2);
            if (this.selectedType != null) {
                List<SessionSnapshotData.MapSummary> maps = SessionSnapshotData.maps().stream()
                    .filter(m -> m.supports(DuelsDefinition.ID) && m.validFor(DuelsDefinition.ID) && m.hasTag("duel_type:" + this.selectedType.id()))
                    .toList();
                this.mapGrid.setMaps(maps);
            } else {
                this.mapGrid.setMaps(List.of());
            }
            this.mapGrid.render(context, textRenderer, mouseX, mouseY, delta);

            // Col 3: Kits
            UiLayout.Rect rect3 = new UiLayout.Rect(this.teamsArea.x() + (columnWidth + COLUMN_GAP) * 2, this.teamsArea.y(), columnWidth, this.teamsArea.height());
            if (this.selectedType != null) {
                List<Kit> kits = KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.selectedType.id()))
                    .toList();
                this.renderGenericColumn(context, textRenderer, rect3, "Available Kits (" + kits.size() + ")", 0xFFFFAA00, kits.size(), this.duelKitScrollOffset, mouseX, mouseY, row -> {
                    int index = this.duelKitScrollOffset + row;
                    if (index < kits.size()) {
                        Kit k = kits.get(index);
                        int rowY = rect3.y() + COLUMN_HEADER_HEIGHT + 4 + row * TEAM_ROW_HEIGHT;
                        this.drawKitRow(context, textRenderer, k, rect3.x(), rowY, rect3.width(), TEAM_ROW_HEIGHT, this.selectedKit == k, mouseX, mouseY);
                    }
                });
            } else {
                this.renderGenericColumn(context, textRenderer, rect3, "Available Kits", 0xFFFFAA00, 0, 0, mouseX, mouseY, row -> {});
                context.drawText(textRenderer, Text.literal("Select a type first"), rect3.x() + 12, rect3.y() + COLUMN_HEADER_HEIGHT + 10, UiTheme.TEXT_MUTED, false);
            }

        } else if (activeModuleId.equals("kits")) {
            context.drawText(textRenderer, Text.literal("Kit Management"), mainPanel.x() + 14, yOffset, UiTheme.TEXT, false);
            
            int columnWidth = (this.teamsArea.width() - COLUMN_GAP) / 2;
            
            // Col 1: Types
            UiLayout.Rect rect1 = new UiLayout.Rect(this.teamsArea.x(), this.teamsArea.y(), columnWidth, this.teamsArea.height());
            List<dev.frost.miniverse.minigame.impl.duels.DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
            this.renderGenericColumn(context, textRenderer, rect1, "Duel Types (" + types.size() + ")", 0xFF6600CC, types.size(), this.kitTypeScrollOffset, mouseX, mouseY, row -> {
                int index = this.kitTypeScrollOffset + row;
                if (index < types.size()) {
                    dev.frost.miniverse.minigame.impl.duels.DuelType t = types.get(index);
                    boolean isSelected = this.sameType(this.kitSelectedType, t);
                    int rowY = rect1.y() + COLUMN_HEADER_HEIGHT + 4 + row * TEAM_ROW_HEIGHT;
                    int bg = isSelected ? 0x663E79B8 : 0x26222A34;
                    if (!isSelected && mouseX >= rect1.x() && mouseX <= rect1.x() + rect1.width() && mouseY >= rowY && mouseY < rowY + TEAM_ROW_HEIGHT) {
                        bg = 0x44304052;
                    }
                    context.fill(rect1.x() + 1, rowY, rect1.x() + rect1.width() - 1, rowY + TEAM_ROW_HEIGHT - 2, bg);
                    context.drawText(textRenderer, Text.literal(t.name()), rect1.x() + 12, rowY + 6, isSelected ? UiTheme.TEXT : UiTheme.TEXT_MUTED, false);
                }
            });

            // Col 2: Kits
            UiLayout.Rect rect2 = new UiLayout.Rect(this.teamsArea.x() + columnWidth + COLUMN_GAP, this.teamsArea.y(), columnWidth, this.teamsArea.height());
            if (this.kitSelectedType != null) {
                List<Kit> kits = KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.kitSelectedType.id()))
                    .toList();
                this.renderGenericColumn(context, textRenderer, rect2, "Kits (" + kits.size() + ")", 0xFFFFAA00, kits.size(), this.kitListScrollOffset, mouseX, mouseY, row -> {
                    int index = this.kitListScrollOffset + row;
                    if (index < kits.size()) {
                        Kit k = kits.get(index);
                        int rowY = rect2.y() + COLUMN_HEADER_HEIGHT + 4 + row * TEAM_ROW_HEIGHT;
                        this.drawKitRow(context, textRenderer, k, rect2.x(), rowY, rect2.width(), TEAM_ROW_HEIGHT, false, mouseX, mouseY);
                    }
                });
            } else {
                this.renderGenericColumn(context, textRenderer, rect2, "Kits", 0xFFFFAA00, 0, 0, mouseX, mouseY, row -> {});
                context.drawText(textRenderer, Text.literal("Select a type first"), rect2.x() + 12, rect2.y() + COLUMN_HEADER_HEIGHT + 10, UiTheme.TEXT_MUTED, false);
            }
        }

        if (activeModuleId.equals("kits") && this.kitStatusMessage != null) {
            long elapsed = System.currentTimeMillis() - this.kitStatusMessageTime;
            if (elapsed < 3000) {
                context.drawText(textRenderer, Text.literal(this.kitStatusMessage), this.teamsArea.x() + 14, this.teamsArea.y() + this.teamsArea.height() - 18, UiTheme.WARNING, false);
            }
        }
        
        if (this.createTypeDialogOpen || this.deleteWarningOpen || this.createKitDialogOpen || this.kitDeleteWarningOpen) {
            context.fill(mainPanel.x(), mainPanel.y(), mainPanel.x() + mainPanel.width(), mainPanel.y() + mainPanel.height(), 0x99000000);
        }

        if (this.createTypeDialogOpen) {
            int dialogW = 260;
            int dialogH = 236;
            int dialogX = mainPanel.x() + (mainPanel.width() - dialogW) / 2;
            int dialogY = mainPanel.y() + 48;
            UiRenderer.panel(context, dialogX, dialogY, dialogW, dialogH, UiTheme.PANEL_RAISED, UiTheme.ACCENT_BLUE);
            context.drawText(textRenderer, Text.literal(this.isEditingType ? "Edit Duel Type" : "Create Duel Type"), dialogX + 14, dialogY + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Name"), dialogX + 14, dialogY + 34, UiTheme.TEXT_MUTED, false);
        }

        if (this.deleteWarningOpen && this.deleteTarget != null) {
            int dialogW = 260;
            int dialogH = 150;
            int dialogX = mainPanel.x() + (mainPanel.width() - dialogW) / 2;
            int dialogY = mainPanel.y() + 48;
            UiRenderer.panel(context, dialogX, dialogY, dialogW, dialogH, UiTheme.PANEL_RAISED, 0xFFCC0000);
            context.drawText(textRenderer, Text.literal("Delete Duel Type").formatted(net.minecraft.util.Formatting.RED), dialogX + 14, dialogY + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Are you sure you want to delete"), dialogX + 14, dialogY + 34, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(this.deleteTarget.name()).formatted(net.minecraft.util.Formatting.YELLOW), dialogX + 14, dialogY + 46, UiTheme.TEXT, false);
            
            int mapCount = (int) SessionSnapshotData.maps().stream().filter(m -> m.supports(DuelsDefinition.ID) && m.hasTag("duel_type:" + this.deleteTarget.id())).count();
            int kitCount = (int) KitRegistry.getAll().stream().filter(k -> k.getCategories().contains("duel_type:" + this.deleteTarget.id())).count();

            context.drawText(textRenderer, Text.literal("Maps affected: " + mapCount), dialogX + 14, dialogY + 70, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Kits affected: " + kitCount), dialogX + 14, dialogY + 82, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Tags will be removed from these items."), dialogX + 14, dialogY + 94, UiTheme.TEXT_MUTED, false);
        }

        if (this.createKitDialogOpen) {
            int dialogW = 260;
            int dialogH = 114;
            int dialogX = mainPanel.x() + (mainPanel.width() - dialogW) / 2;
            int dialogY = mainPanel.y() + 48;
            UiRenderer.panel(context, dialogX, dialogY, dialogW, dialogH, UiTheme.PANEL_RAISED, UiTheme.ACCENT_BLUE);
            context.drawText(textRenderer, Text.literal(this.isEditingKit ? "Rename Kit" : "Create Kit"), dialogX + 14, dialogY + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Name"), dialogX + 14, dialogY + 34, UiTheme.TEXT_MUTED, false);
        }

        if (this.kitDeleteWarningOpen && this.kitDeleteTarget != null) {
            int dialogW = 260;
            int dialogH = 114;
            int dialogX = mainPanel.x() + (mainPanel.width() - dialogW) / 2;
            int dialogY = mainPanel.y() + 48;
            UiRenderer.panel(context, dialogX, dialogY, dialogW, dialogH, UiTheme.PANEL_RAISED, 0xFFCC0000);
            context.drawText(textRenderer, Text.literal("Delete Kit").formatted(net.minecraft.util.Formatting.RED), dialogX + 14, dialogY + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Are you sure you want to delete kit:"), dialogX + 14, dialogY + 34, UiTheme.TEXT, false);
            context.drawText(textRenderer, this.kitDeleteTarget.getDisplayName().copy().formatted(net.minecraft.util.Formatting.YELLOW), dialogX + 14, dialogY + 46, UiTheme.TEXT, false);
        }

        boolean showStartBtn = !this.createTypeDialogOpen && !this.deleteWarningOpen && !this.createKitDialogOpen && !this.kitDeleteWarningOpen;
        if (showStartBtn && this.startButton != null) {
            this.renderActionButton(context, textRenderer, this.startButton, "Start Match", UiTheme.ACCENT_GREEN, this.startButton.contains(mouseX, mouseY));
        }
    }

    private void drawKitRow(DrawContext context, TextRenderer textRenderer, Kit kit, int x, int y, int width, int height, boolean isSelected, int mouseX, int mouseY) {
        int bg = isSelected ? 0x663E79B8 : 0x26222A34;
        if (!isSelected && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            bg = 0x44304052;
        }
        context.fill(x + 1, y, x + width - 1, y + height - 2, bg);
        
        int startX = x + 12;
        int hotbarY = y + (height - 22) / 2;
        int maxSlots = Math.min(9, Math.max(0, (width - textRenderer.getWidth(kit.getDisplayName()) - 30) / 22));
        
        for (int i = 0; i < maxSlots; i++) {
            int slotX = startX + i * 22;
            int slotY = hotbarY;
            
            context.fill(slotX, slotY, slotX + 20, slotY + 20, 0xAA101010);
            context.drawBorder(slotX, slotY, 20, 20, 0x55FFFFFF);
            
            if (kit.getInventory() != null && i < kit.getInventory().length) {
                net.minecraft.item.ItemStack stack = kit.getInventory()[i];
                if (stack != null && !stack.isEmpty()) {
                    context.drawItem(stack, slotX + 2, slotY + 2);
                    context.drawItemInSlot(textRenderer, stack, slotX + 2, slotY + 2);
                }
            }
        }
        
        int textX = startX + maxSlots * 22 + 8;
        context.drawText(textRenderer, kit.getDisplayName(), textX, y + (height - 9) / 2, isSelected ? UiTheme.TEXT : UiTheme.TEXT_MUTED, false);
    }

    private void renderGenericColumn(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String title, int accentColor, int totalRows, int scrollOffset, int mouseX, int mouseY, java.util.function.Consumer<Integer> rowRenderer) {
        this.renderCustomColumn(context, textRenderer, rect, title, accentColor, totalRows, scrollOffset, TEAM_ROW_HEIGHT, mouseX, mouseY, rowRenderer);
    }

    private void renderCustomColumn(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String title, int accentColor, int totalRows, int scrollOffset, int rowHeight, int mouseX, int mouseY, java.util.function.Consumer<Integer> rowRenderer) {
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + 3, accentColor);
        context.drawText(textRenderer, Text.literal(title), rect.x() + 8, rect.y() + 7, UiTheme.TEXT, false);

        int visibleRows = Math.max(0, (rect.height() - COLUMN_HEADER_HEIGHT - 8) / rowHeight);
        int rows = Math.min(totalRows - scrollOffset, visibleRows);
        for (int row = 0; row < rows; row++) {
            rowRenderer.accept(row);
        }
        this.drawScrollbar(context, rect, totalRows, visibleRows, scrollOffset);
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

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        if (activeModuleId.equals("team_selection")) {
            this.teamGrid.renderForeground(context, textRenderer, workspace, mouseX, mouseY, delta);
        }

        if (this.contextMenuOpen) {
            UiRenderer.panel(context, this.contextMenuX - 2, this.contextMenuY - 2, 104, 44, UiTheme.PANEL_RAISED, UiTheme.BORDER_STRONG);
        }

        if (this.kitContextMenuOpen) {
            UiRenderer.panel(context, this.kitContextMenuX - 2, this.kitContextMenuY - 2, 104, 44, UiTheme.PANEL_RAISED, UiTheme.BORDER_STRONG);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.contextMenuOpen) {
            UiLayout.Rect contextRect = new UiLayout.Rect(this.contextMenuX - 2, this.contextMenuY - 2, 104, 44);
            if (!contextRect.contains(mouseX, mouseY)) {
                this.contextMenuOpen = false;
                this.contextEditBtn.visible = false;
                this.contextDeleteBtn.visible = false;
                return true;
            } else {
                return false; // let the buttons handle it
            }
        }

        if (this.kitContextMenuOpen) {
            UiLayout.Rect contextRect = new UiLayout.Rect(this.kitContextMenuX - 2, this.kitContextMenuY - 2, 104, 44);
            if (!contextRect.contains(mouseX, mouseY)) {
                this.kitContextMenuOpen = false;
                this.kitContextEditBtn.visible = false;
                this.kitContextDeleteBtn.visible = false;
                return true;
            } else {
                return false; // let the buttons handle it
            }
        }

        if (this.createTypeDialogOpen || this.deleteWarningOpen || this.createKitDialogOpen || this.kitDeleteWarningOpen) {
            return false; // let buttons handle it
        }

        boolean showStartBtn = !this.createTypeDialogOpen && !this.deleteWarningOpen && !this.createKitDialogOpen && !this.kitDeleteWarningOpen;
        if (showStartBtn && this.startButton != null && this.startButton.contains(mouseX, mouseY) && button == 0) {
            if (this.selectedType != null && this.selectedMap != null && this.selectedKit != null) {
                if (this.teamGrid.getMembers("team_1").isEmpty() || this.teamGrid.getMembers("team_2").isEmpty()) return true;

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
                groups.add(this.group("team_1", this.teamGrid.getMembers("team_1")));
                groups.add(this.group("team_2", this.teamGrid.getMembers("team_2")));
                nbtPlan.put("groups", groups);
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.frost.miniverse.common.NetworkConstants.CreateSessionPayload(DuelsDefinition.ID, "Duel " + this.selectedType.name(), nbtPlan)
                );
            }
            return true;
        }

        if (activeModuleId.equals("team_selection")) {
            if (this.teamGrid.mouseClicked(mouseX, mouseY, button)) return true;
        }
        
        UiLayout.Rect mainPanel = workspace.inset(4);

        if (activeModuleId.equals("duel_types")) {
            int columnWidth = (this.teamsArea.width() - COLUMN_GAP * 2) / 3;
            UiLayout.Rect rect1 = new UiLayout.Rect(this.teamsArea.x(), this.teamsArea.y(), columnWidth, this.teamsArea.height());
            UiLayout.Rect rect2 = new UiLayout.Rect(this.teamsArea.x() + columnWidth + COLUMN_GAP, this.teamsArea.y(), columnWidth, this.teamsArea.height());
            UiLayout.Rect rect3 = new UiLayout.Rect(this.teamsArea.x() + (columnWidth + COLUMN_GAP) * 2, this.teamsArea.y(), columnWidth, this.teamsArea.height());

            if (rect1.contains(mouseX, mouseY)) {
                int rowStartY = rect1.y() + COLUMN_HEADER_HEIGHT + 4;
                if (mouseY >= rowStartY) {
                    int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
                    int index = this.duelTypeScrollOffset + row;
                    List<dev.frost.miniverse.minigame.impl.duels.DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
                    if (index >= 0 && index < types.size()) {
                        if (button == 1) { // Right click
                            this.contextMenuTarget = types.get(index);
                            this.contextMenuOpen = true;
                            this.contextMenuX = (int) mouseX;
                            this.contextMenuY = (int) mouseY;
                            if (this.parentScreen != null) this.parentScreen.openWorkspaceView(this);
                            return true;
                        } else if (button == 0) {
                            this.selectedType = types.get(index);
                            this.selectedMap = null;
                            this.selectedKit = null;
                            return true;
                        }
                    }
                }
            }

            if (button == 0 && this.selectedType != null) {
                if (rect2.contains(mouseX, mouseY)) {
                    return this.mapGrid.mouseClicked(mouseX, mouseY, button);
                }

                if (rect3.contains(mouseX, mouseY)) {
                    int rowStartY = rect3.y() + COLUMN_HEADER_HEIGHT + 4;
                    if (mouseY >= rowStartY) {
                        int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
                        int index = this.duelKitScrollOffset + row;
                        List<Kit> kits = KitRegistry.getAll().stream()
                            .filter(k -> k.getCategories().contains("duel_type:" + this.selectedType.id()))
                            .toList();
                        if (index >= 0 && index < kits.size()) {
                            this.selectedKit = kits.get(index);
                            return true;
                        }
                    }
                }
            }
        } else if (activeModuleId.equals("kits")) {
            int columnWidth = (this.teamsArea.width() - COLUMN_GAP) / 2;
            UiLayout.Rect rect1 = new UiLayout.Rect(this.teamsArea.x(), this.teamsArea.y(), columnWidth, this.teamsArea.height());
            UiLayout.Rect rect2 = new UiLayout.Rect(this.teamsArea.x() + columnWidth + COLUMN_GAP, this.teamsArea.y(), columnWidth, this.teamsArea.height());

            if (rect1.contains(mouseX, mouseY)) {
                int rowStartY = rect1.y() + COLUMN_HEADER_HEIGHT + 4;
                if (mouseY >= rowStartY) {
                    int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
                    int index = this.kitTypeScrollOffset + row;
                    List<dev.frost.miniverse.minigame.impl.duels.DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
                    if (index >= 0 && index < types.size()) {
                        if (button == 0) {
                            this.kitSelectedType = types.get(index);
                            return true;
                        }
                    }
                }
            }

            if (this.kitSelectedType != null && rect2.contains(mouseX, mouseY)) {
                int rowStartY = rect2.y() + COLUMN_HEADER_HEIGHT + 4;
                if (mouseY >= rowStartY) {
                    int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
                    int index = this.kitListScrollOffset + row;
                    List<Kit> kits = KitRegistry.getAll().stream()
                        .filter(k -> k.getCategories().contains("duel_type:" + this.kitSelectedType.id()))
                        .toList();
                    if (index >= 0 && index < kits.size()) {
                        if (button == 1) { // Right click
                            this.kitContextMenuTarget = kits.get(index);
                            this.kitContextMenuOpen = true;
                            this.kitContextMenuX = (int) mouseX;
                            this.kitContextMenuY = (int) mouseY;
                            if (this.parentScreen != null) this.parentScreen.openWorkspaceView(this);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (activeModuleId.equals("team_selection")) {
            return this.teamGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (activeModuleId.equals("team_selection")) {
            return this.teamGrid.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.createTypeDialogOpen || this.deleteWarningOpen || this.contextMenuOpen) return false;

        if (activeModuleId.equals("team_selection")) {
            return this.teamGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
        } else if (activeModuleId.equals("duel_types")) {
            int columnWidth = (this.teamsArea.width() - COLUMN_GAP * 2) / 3;
            UiLayout.Rect rect1 = new UiLayout.Rect(this.teamsArea.x(), this.teamsArea.y(), columnWidth, this.teamsArea.height());
            UiLayout.Rect rect2 = new UiLayout.Rect(this.teamsArea.x() + columnWidth + COLUMN_GAP, this.teamsArea.y(), columnWidth, this.teamsArea.height());
            UiLayout.Rect rect3 = new UiLayout.Rect(this.teamsArea.x() + (columnWidth + COLUMN_GAP) * 2, this.teamsArea.y(), columnWidth, this.teamsArea.height());

            int visibleRows = Math.max(0, (this.teamsArea.height() - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);

            if (rect1.contains(mouseX, mouseY)) {
                int total = SessionSnapshotData.duelTypes().size();
                int maxScroll = Math.max(0, total - visibleRows);
                this.duelTypeScrollOffset = Math.clamp(this.duelTypeScrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                return maxScroll > 0;
            } else if (rect2.contains(mouseX, mouseY) && this.selectedType != null) {
                return this.mapGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
            } else if (rect3.contains(mouseX, mouseY) && this.selectedType != null) {
                int total = (int) KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.selectedType.id()))
                    .count();
                int maxScroll = Math.max(0, total - visibleRows);
                this.duelKitScrollOffset = Math.clamp(this.duelKitScrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                return maxScroll > 0;
            }
        } else if (activeModuleId.equals("kits")) {
            int columnWidth = (this.teamsArea.width() - COLUMN_GAP) / 2;
            UiLayout.Rect rect1 = new UiLayout.Rect(this.teamsArea.x(), this.teamsArea.y(), columnWidth, this.teamsArea.height());
            UiLayout.Rect rect2 = new UiLayout.Rect(this.teamsArea.x() + columnWidth + COLUMN_GAP, this.teamsArea.y(), columnWidth, this.teamsArea.height());

            int visibleRows = Math.max(0, (this.teamsArea.height() - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);

            if (rect1.contains(mouseX, mouseY)) {
                int total = SessionSnapshotData.duelTypes().size();
                int maxScroll = Math.max(0, total - visibleRows);
                this.kitTypeScrollOffset = Math.clamp(this.kitTypeScrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                return maxScroll > 0;
            } else if (rect2.contains(mouseX, mouseY) && this.kitSelectedType != null) {
                int total = (int) KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.kitSelectedType.id()))
                    .count();
                int maxScroll = Math.max(0, total - visibleRows);
                this.kitListScrollOffset = Math.clamp(this.kitListScrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                return maxScroll > 0;
            }
        }
        return false;
    }

    @Override
    public void refreshRoster() {
        this.teamGrid.refreshRoster();
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


}
