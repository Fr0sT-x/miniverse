package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.components.MapThumbnailGrid;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.providers.KitRegistryProvider;
import dev.frost.miniverse.minigame.core.kit.Kit;
import dev.frost.miniverse.minigame.core.kit.KitRegistry;
import dev.frost.miniverse.minigame.impl.duels.DuelsDefinition;
import dev.frost.miniverse.minigame.impl.duels.DuelType;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DuelsWorkspaceView extends AbstractGamemodeWorkspaceView {

    private static final int TEAM_ROW_HEIGHT = 20;
    private static final int COLUMN_HEADER_HEIGHT = 22;
    private static final int COLUMN_GAP = 12;

    private SessionScreen parentScreen;
    private final StaticTeamSelectionGrid teamGrid = new StaticTeamSelectionGrid();

    // Duel Types
    private TextFieldWidget newTypeNameField;
    private DuelType selectedType = null;
    private SessionSnapshotData.MapSummary selectedMap = null;
    private Kit selectedKit = null;
    
    private boolean createTypeDialogOpen;
    private boolean isEditingType;
    
    private int duelTypeScrollOffset = 0;
    private MapThumbnailGrid mapGrid;
    private int duelKitScrollOffset = 0;

    private boolean contextMenuOpen = false;
    private DuelType contextMenuTarget = null;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    
    private String kitStatusMessage = null;
    private long kitStatusMessageTime = 0;

    private boolean deleteWarningOpen = false;
    private DuelType deleteTarget = null;
    
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
    private DuelType kitSelectedType = null;
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

    private boolean kitDeleteWarningOpen = false;

    public DuelsWorkspaceView() {
        super("duels");
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("team_1", "Team 1", 0xDD3333, false);
        this.teamGrid.addColumn("team_2", "Team 2", 0x3344DD, false);
        
        this.mapGrid = new MapThumbnailGrid("Supported Maps", mapId -> {
            SessionSnapshotData.maps().stream().filter(m -> m.id().equals(mapId)).findFirst().ifPresent(m -> {
                this.selectedMap = m;
                this.mapGrid.setSelectedMapId(m.id());
            });
        });
        this.mapGrid.setAccentColor(0xFF00AA66);
        
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players to Team 1 and Team 2.", UiTheme.ACCENT_RED);
        this.moduleManager.register("duel_types", "D", "Types, Map, Kit selection", "Setup", "Configure duel types, maps, and kits.", 0xFF6600CC);
        
        this.moduleManager.register("kits", "K", "Kits", "Kits & Duel Types", "Manage kit layouts and inventory.", 0xFFFFAA00);
        this.moduleManager.register("duel_types_config", "D", "Duel Types", "Kits & Duel Types", "Create and manage Duel Types.", 0xFF6600CC);
        
        this.useGameRules();
        this.moduleManager.register("summary", "S", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        this.parentScreen = screen;
        UiLayout.Rect mainPanel = this.layout.mainPanel();

        if (this.moduleManager.isActive("duel_types_config")) {
            if (!this.createTypeDialogOpen && !this.deleteWarningOpen) {
                this.addButton(screen, "Create Type", mainPanel.x() + 14, mainPanel.y() + 40, 110, () -> "Create a new duel type.", () -> {
                    this.isEditingType = false;
                    this.createTypeDialogOpen = true;
                    this.configKnockbackOnly = false;
                    this.configAllowBuilding = true;
                    this.configAllowBreaking = true;
                    this.configAllowHunger = true;
                    this.configNaturalRegen = true;
                    screen.openWorkspaceView(this);
                });
            }

            if (this.contextMenuOpen) {
                this.addButton(screen, "Edit", this.contextMenuX, this.contextMenuY, 100, () -> "Edit this duel type.", () -> {
                    if (this.contextMenuTarget != null) {
                        this.selectedType = this.contextMenuTarget;
                        this.configKnockbackOnly = this.contextMenuTarget.knockbackOnly();
                        this.configAllowBuilding = this.contextMenuTarget.allowBuilding();
                        this.configAllowBreaking = this.contextMenuTarget.allowBreaking();
                        this.configAllowHunger = this.contextMenuTarget.allowHunger();
                        this.configNaturalRegen = this.contextMenuTarget.naturalRegen();
                        this.isEditingType = true;
                        this.createTypeDialogOpen = true;
                        this.contextMenuOpen = false;
                        screen.openWorkspaceView(this);
                    }
                });
                ButtonWidget deleteBtn = this.addButton(screen, "Delete", this.contextMenuX, this.contextMenuY + 20, 100, () -> "Delete this duel type.", () -> {
                    if (this.contextMenuTarget != null) {
                        this.deleteTarget = this.contextMenuTarget;
                        this.deleteWarningOpen = true;
                        this.contextMenuOpen = false;
                        screen.openWorkspaceView(this);
                    }
                });
                deleteBtn.setMessage(Text.literal("Delete").formatted(net.minecraft.util.Formatting.RED));
            }
        } else if (this.moduleManager.isActive("kits")) {
            if (!this.createKitDialogOpen && !this.kitDeleteWarningOpen) {
                this.addButton(screen, "Create Kit", mainPanel.x() + 14, mainPanel.y() + 40, 110, () -> "Create a new custom kit.", () -> {
                    if (this.kitSelectedType != null) {
                        this.isEditingKit = false;
                        this.createKitDialogOpen = true;
                        screen.openWorkspaceView(this);
                    } else {
                        this.kitStatusMessage = "Select a Duel type first.";
                        this.kitStatusMessageTime = System.currentTimeMillis();
                    }
                });
                this.addButton(screen, "Open Kit Registry", mainPanel.x() + 130, mainPanel.y() + 40, 120, () -> "Browse existing kits.", () -> {
                    RegistrySelectorContext<Kit> context = new RegistrySelectorContext<>(
                        "miniverse:kit", "Duel Kits", RegistrySelectorContext.SelectionMode.SINGLE,
                        this.kitRegistryState, result -> {}, "duel_kits", Set.of()
                    );
                    MinecraftClient.getInstance().setScreen(new RegistrySelectorScreen<>(context, new KitRegistryProvider()));
                });
            }

            if (this.kitContextMenuOpen) {
                this.addButton(screen, "Rename", this.kitContextMenuX, this.kitContextMenuY, 100, () -> "Rename this kit.", () -> {
                    if (this.kitContextMenuTarget != null) {
                        this.isEditingKit = true;
                        this.createKitDialogOpen = true;
                        this.kitContextMenuOpen = false;
                        screen.openWorkspaceView(this);
                    }
                });
                ButtonWidget deleteBtn = this.addButton(screen, "Delete", this.kitContextMenuX, this.kitContextMenuY + 20, 100, () -> "Delete this kit.", () -> {
                    if (this.kitContextMenuTarget != null) {
                        this.kitDeleteTarget = this.kitContextMenuTarget;
                        this.kitDeleteWarningOpen = true;
                        this.kitContextMenuOpen = false;
                        screen.openWorkspaceView(this);
                    }
                });
                deleteBtn.setMessage(Text.literal("Delete").formatted(net.minecraft.util.Formatting.RED));
            }
        }

        // Dialogs
        int dialogW = 260;
        int dialogX = mainPanel.x() + (mainPanel.width() - dialogW) / 2;
        int dialogY = mainPanel.y() + 48;

        if (this.createTypeDialogOpen) {
            this.newTypeNameField = this.addField(screen, dialogX + 14, dialogY + 48, "", "Type Name", () -> "Name of the new duel type.");
            this.newTypeNameField.setWidth(dialogW - 28);
            if (this.isEditingType && this.selectedType != null) {
                this.newTypeNameField.setText(this.selectedType.name());
            }
            this.btnKnockbackOnly = this.addButton(screen, "Knockback Only: " + (this.configKnockbackOnly ? "ON" : "OFF"), dialogX + 14, dialogY + 76, dialogW - 28, () -> this.configKnockbackOnly ? "Players only take knockback, no damage." : "Players take normal damage.", () -> {
                this.configKnockbackOnly = !this.configKnockbackOnly;
                this.btnKnockbackOnly.setMessage(Text.literal("Knockback Only: " + (this.configKnockbackOnly ? "ON" : "OFF")));
            });
            this.btnAllowBuilding = this.addButton(screen, "Allow Building: " + (this.configAllowBuilding ? "ON" : "OFF"), dialogX + 14, dialogY + 101, dialogW - 28, () -> this.configAllowBuilding ? "Players can place blocks." : "Block placement is disabled.", () -> {
                this.configAllowBuilding = !this.configAllowBuilding;
                this.btnAllowBuilding.setMessage(Text.literal("Allow Building: " + (this.configAllowBuilding ? "ON" : "OFF")));
            });
            this.btnAllowBreaking = this.addButton(screen, "Allow Breaking: " + (this.configAllowBreaking ? "ON" : "OFF"), dialogX + 14, dialogY + 126, dialogW - 28, () -> this.configAllowBreaking ? "Players can break placed blocks." : "Block breaking is disabled.", () -> {
                this.configAllowBreaking = !this.configAllowBreaking;
                this.btnAllowBreaking.setMessage(Text.literal("Allow Breaking: " + (this.configAllowBreaking ? "ON" : "OFF")));
            });
            this.btnAllowHunger = this.addButton(screen, "Allow Hunger: " + (this.configAllowHunger ? "ON" : "OFF"), dialogX + 14, dialogY + 151, dialogW - 28, () -> this.configAllowHunger ? "Players lose hunger over time." : "Hunger is frozen.", () -> {
                this.configAllowHunger = !this.configAllowHunger;
                this.btnAllowHunger.setMessage(Text.literal("Allow Hunger: " + (this.configAllowHunger ? "ON" : "OFF")));
            });
            this.btnNaturalRegen = this.addButton(screen, "Natural Regen: " + (this.configNaturalRegen ? "ON" : "OFF"), dialogX + 14, dialogY + 176, dialogW - 28, () -> this.configNaturalRegen ? "Players regenerate health naturally." : "Natural health regeneration is disabled.", () -> {
                this.configNaturalRegen = !this.configNaturalRegen;
                this.btnNaturalRegen.setMessage(Text.literal("Natural Regen: " + (this.configNaturalRegen ? "ON" : "OFF")));
            });

            this.addButton(screen, this.isEditingType ? "Save Changes" : "Save Type", dialogX + 14, dialogY + 202, 145, () -> "Save this duel type.", () -> {
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
                    this.createTypeDialogOpen = false;
                    screen.openWorkspaceView(this);
                }
            });
            this.addButton(screen, "Cancel", dialogX + 166, dialogY + 202, 66, () -> "Discard changes.", () -> {
                this.createTypeDialogOpen = false;
                screen.openWorkspaceView(this);
            });
        }

        if (this.deleteWarningOpen && this.deleteTarget != null) {
            ButtonWidget confirmDeleteBtn = this.addButton(screen, "Confirm Delete", dialogX + 14, dialogY + 120, 145, () -> "Permanently delete this duel type.", () -> {
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
                screen.openWorkspaceView(this);
            });
            confirmDeleteBtn.setMessage(Text.literal("Confirm Delete").formatted(net.minecraft.util.Formatting.RED));
            this.addButton(screen, "Cancel", dialogX + 166, dialogY + 120, 66, () -> "Cancel deletion.", () -> {
                this.deleteWarningOpen = false;
                this.deleteTarget = null;
                screen.openWorkspaceView(this);
            });
        }

        if (this.createKitDialogOpen) {
            this.kitNameField = this.addField(screen, dialogX + 14, dialogY + 48, "", "Kit Name", () -> "Name of the new kit.");
            this.kitNameField.setWidth(dialogW - 28);
            if (this.isEditingKit && this.kitContextMenuTarget != null) {
                this.kitNameField.setText(this.kitContextMenuTarget.getDisplayName().getString());
            }
            this.addButton(screen, this.isEditingKit ? "Save Changes" : "Save Kit", dialogX + 14, dialogY + 80, 145, () -> "Save this kit.", () -> {
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
                    this.createKitDialogOpen = false;
                    screen.openWorkspaceView(this);
                }
            });
            this.addButton(screen, "Cancel", dialogX + 166, dialogY + 80, 66, () -> "Discard changes.", () -> {
                this.createKitDialogOpen = false;
                screen.openWorkspaceView(this);
            });
        }

        if (this.kitDeleteWarningOpen && this.kitDeleteTarget != null) {
            ButtonWidget confirmDeleteBtn = this.addButton(screen, "Confirm Delete", dialogX + 14, dialogY + 80, 145, () -> "Permanently delete this kit.", () -> {
                net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new dev.frost.miniverse.common.NetworkConstants.DeleteKitPayload(this.kitDeleteTarget.getId().toString())
                );
                this.kitDeleteTarget = null;
                this.kitDeleteWarningOpen = false;
                screen.openWorkspaceView(this);
            });
            confirmDeleteBtn.setMessage(Text.literal("Confirm Delete").formatted(net.minecraft.util.Formatting.RED));
            this.addButton(screen, "Cancel", dialogX + 166, dialogY + 80, 66, () -> "Cancel deletion.", () -> {
                this.kitDeleteWarningOpen = false;
                this.kitDeleteTarget = null;
                screen.openWorkspaceView(this);
            });
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = this.layout.mainPanel();
        UiLayout.Rect teamsArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 72, mainPanel.width() - 24, mainPanel.height() - 94);

        if (this.moduleManager.isActive("duel_types")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP * 2) / 3;
            
            // Col 1: Types
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());
            List<DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
            this.renderCustomColumn(context, textRenderer, rect1, "Duel Types (" + types.size() + ")", 0xFF6600CC, types.size(), this.duelTypeScrollOffset, mouseX, mouseY, row -> {
                int index = this.duelTypeScrollOffset + row;
                if (index < types.size()) {
                    DuelType t = types.get(index);
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
            UiLayout.Rect rect2 = new UiLayout.Rect(teamsArea.x() + columnWidth + COLUMN_GAP, teamsArea.y(), columnWidth, teamsArea.height());
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
            UiLayout.Rect rect3 = new UiLayout.Rect(teamsArea.x() + (columnWidth + COLUMN_GAP) * 2, teamsArea.y(), columnWidth, teamsArea.height());
            if (this.selectedType != null) {
                List<Kit> kits = KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.selectedType.id()))
                    .toList();
                this.renderCustomColumn(context, textRenderer, rect3, "Available Kits (" + kits.size() + ")", 0xFFFFAA00, kits.size(), this.duelKitScrollOffset, mouseX, mouseY, row -> {
                    int index = this.duelKitScrollOffset + row;
                    if (index < kits.size()) {
                        Kit k = kits.get(index);
                        int rowY = rect3.y() + COLUMN_HEADER_HEIGHT + 4 + row * TEAM_ROW_HEIGHT;
                        this.drawKitRow(context, textRenderer, k, rect3.x(), rowY, rect3.width(), TEAM_ROW_HEIGHT, this.selectedKit == k, mouseX, mouseY);
                    }
                });
            } else {
                this.renderCustomColumn(context, textRenderer, rect3, "Available Kits", 0xFFFFAA00, 0, 0, mouseX, mouseY, row -> {});
                context.drawText(textRenderer, Text.literal("Select a type first"), rect3.x() + 12, rect3.y() + COLUMN_HEADER_HEIGHT + 10, UiTheme.TEXT_MUTED, false);
            }
        } else if (this.moduleManager.isActive("duel_types_config")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP * 2) / 3;
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());
            List<DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
            this.renderCustomColumn(context, textRenderer, rect1, "Duel Types (" + types.size() + ")", 0xFF6600CC, types.size(), this.duelTypeScrollOffset, mouseX, mouseY, row -> {
                int index = this.duelTypeScrollOffset + row;
                if (index < types.size()) {
                    DuelType t = types.get(index);
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
        } else if (this.moduleManager.isActive("kits")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP) / 2;
            
            // Col 1: Types
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());
            List<DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
            this.renderCustomColumn(context, textRenderer, rect1, "Duel Types (" + types.size() + ")", 0xFF6600CC, types.size(), this.kitTypeScrollOffset, mouseX, mouseY, row -> {
                int index = this.kitTypeScrollOffset + row;
                if (index < types.size()) {
                    DuelType t = types.get(index);
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
            UiLayout.Rect rect2 = new UiLayout.Rect(teamsArea.x() + columnWidth + COLUMN_GAP, teamsArea.y(), columnWidth, teamsArea.height());
            if (this.kitSelectedType != null) {
                List<Kit> kits = KitRegistry.getAll().stream()
                    .filter(k -> k.getCategories().contains("duel_type:" + this.kitSelectedType.id()))
                    .toList();
                this.renderCustomColumn(context, textRenderer, rect2, "Kits (" + kits.size() + ")", 0xFFFFAA00, kits.size(), this.kitListScrollOffset, mouseX, mouseY, row -> {
                    int index = this.kitListScrollOffset + row;
                    if (index < kits.size()) {
                        Kit k = kits.get(index);
                        int rowY = rect2.y() + COLUMN_HEADER_HEIGHT + 4 + row * TEAM_ROW_HEIGHT;
                        this.drawKitRow(context, textRenderer, k, rect2.x(), rowY, rect2.width(), TEAM_ROW_HEIGHT, false, mouseX, mouseY);
                    }
                });
            } else {
                this.renderCustomColumn(context, textRenderer, rect2, "Kits", 0xFFFFAA00, 0, 0, mouseX, mouseY, row -> {});
                context.drawText(textRenderer, Text.literal("Select a type first"), rect2.x() + 12, rect2.y() + COLUMN_HEADER_HEIGHT + 10, UiTheme.TEXT_MUTED, false);
            }
        }
        
        if (this.moduleManager.isActive("kits") && this.kitStatusMessage != null) {
            long elapsed = System.currentTimeMillis() - this.kitStatusMessageTime;
            if (elapsed < 3000) {
                context.drawText(textRenderer, Text.literal(this.kitStatusMessage), teamsArea.x() + 14, teamsArea.y() + teamsArea.height() - 18, UiTheme.WARNING, false);
            }
        }
        
        if (this.createTypeDialogOpen || this.deleteWarningOpen || this.createKitDialogOpen || this.kitDeleteWarningOpen) {
            context.fill(mainPanel.x(), mainPanel.y(), mainPanel.x() + mainPanel.width(), mainPanel.y() + mainPanel.height(), 0x99000000);
        }

        int dialogW = 260;
        int dialogX = mainPanel.x() + (mainPanel.width() - dialogW) / 2;
        int dialogY = mainPanel.y() + 48;

        if (this.createTypeDialogOpen) {
            UiRenderer.panel(context, dialogX, dialogY, dialogW, 236, UiTheme.PANEL_RAISED, UiTheme.ACCENT_BLUE);
            context.drawText(textRenderer, Text.literal(this.isEditingType ? "Edit Duel Type" : "Create Duel Type"), dialogX + 14, dialogY + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Name"), dialogX + 14, dialogY + 34, UiTheme.TEXT_MUTED, false);
        }

        if (this.deleteWarningOpen && this.deleteTarget != null) {
            UiRenderer.panel(context, dialogX, dialogY, dialogW, 150, UiTheme.PANEL_RAISED, 0xFFCC0000);
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
            UiRenderer.panel(context, dialogX, dialogY, dialogW, 114, UiTheme.PANEL_RAISED, UiTheme.ACCENT_BLUE);
            context.drawText(textRenderer, Text.literal(this.isEditingKit ? "Rename Kit" : "Create Kit"), dialogX + 14, dialogY + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Name"), dialogX + 14, dialogY + 34, UiTheme.TEXT_MUTED, false);
        }

        if (this.kitDeleteWarningOpen && this.kitDeleteTarget != null) {
            UiRenderer.panel(context, dialogX, dialogY, dialogW, 114, UiTheme.PANEL_RAISED, 0xFFCC0000);
            context.drawText(textRenderer, Text.literal("Delete Kit").formatted(net.minecraft.util.Formatting.RED), dialogX + 14, dialogY + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Are you sure you want to delete kit:"), dialogX + 14, dialogY + 34, UiTheme.TEXT, false);
            context.drawText(textRenderer, this.kitDeleteTarget.getDisplayName().copy().formatted(net.minecraft.util.Formatting.YELLOW), dialogX + 14, dialogY + 46, UiTheme.TEXT, false);
        }
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.contextMenuOpen) {
            UiRenderer.panel(context, this.contextMenuX - 2, this.contextMenuY - 2, 104, 44, UiTheme.PANEL_RAISED, UiTheme.BORDER_STRONG);
        }

        if (this.kitContextMenuOpen) {
            UiRenderer.panel(context, this.kitContextMenuX - 2, this.kitContextMenuY - 2, 104, 44, UiTheme.PANEL_RAISED, UiTheme.BORDER_STRONG);
        }
    }

    @Override
    protected boolean gamemodeMouseClicked(double mouseX, double mouseY, int button) {
        if (this.contextMenuOpen) {
            UiLayout.Rect contextRect = new UiLayout.Rect(this.contextMenuX - 2, this.contextMenuY - 2, 104, 44);
            if (!contextRect.contains(mouseX, mouseY)) {
                this.contextMenuOpen = false;
                if (this.parentScreen != null) this.parentScreen.openWorkspaceView(this);
                return true;
            } else {
                return false;
            }
        }

        if (this.kitContextMenuOpen) {
            UiLayout.Rect contextRect = new UiLayout.Rect(this.kitContextMenuX - 2, this.kitContextMenuY - 2, 104, 44);
            if (!contextRect.contains(mouseX, mouseY)) {
                this.kitContextMenuOpen = false;
                if (this.parentScreen != null) this.parentScreen.openWorkspaceView(this);
                return true;
            } else {
                return false;
            }
        }

        if (this.createTypeDialogOpen || this.deleteWarningOpen || this.createKitDialogOpen || this.kitDeleteWarningOpen) {
            return false;
        }

        UiLayout.Rect mainPanel = this.layout.mainPanel();
        UiLayout.Rect teamsArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 72, mainPanel.width() - 24, mainPanel.height() - 94);

        if (this.moduleManager.isActive("duel_types")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP * 2) / 3;
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());
            UiLayout.Rect rect2 = new UiLayout.Rect(teamsArea.x() + columnWidth + COLUMN_GAP, teamsArea.y(), columnWidth, teamsArea.height());
            UiLayout.Rect rect3 = new UiLayout.Rect(teamsArea.x() + (columnWidth + COLUMN_GAP) * 2, teamsArea.y(), columnWidth, teamsArea.height());

            if (rect1.contains(mouseX, mouseY)) {
                int rowStartY = rect1.y() + COLUMN_HEADER_HEIGHT + 4;
                if (mouseY >= rowStartY) {
                    int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
                    int index = this.duelTypeScrollOffset + row;
                    List<DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
                    if (index >= 0 && index < types.size()) {
                        if (button == 0) {
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
        } else if (this.moduleManager.isActive("duel_types_config")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP * 2) / 3;
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());

            if (rect1.contains(mouseX, mouseY)) {
                int rowStartY = rect1.y() + COLUMN_HEADER_HEIGHT + 4;
                if (mouseY >= rowStartY) {
                    int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
                    int index = this.duelTypeScrollOffset + row;
                    List<DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
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
                            return true;
                        }
                    }
                }
            }
        } else if (this.moduleManager.isActive("kits")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP) / 2;
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());
            UiLayout.Rect rect2 = new UiLayout.Rect(teamsArea.x() + columnWidth + COLUMN_GAP, teamsArea.y(), columnWidth, teamsArea.height());

            if (rect1.contains(mouseX, mouseY)) {
                int rowStartY = rect1.y() + COLUMN_HEADER_HEIGHT + 4;
                if (mouseY >= rowStartY) {
                    int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
                    int index = this.kitTypeScrollOffset + row;
                    List<DuelType> types = new ArrayList<>(SessionSnapshotData.duelTypes());
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
    protected boolean gamemodeMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.createTypeDialogOpen || this.deleteWarningOpen || this.contextMenuOpen || this.createKitDialogOpen || this.kitDeleteWarningOpen || this.kitContextMenuOpen) return false;

        UiLayout.Rect mainPanel = this.layout.mainPanel();
        UiLayout.Rect teamsArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 72, mainPanel.width() - 24, mainPanel.height() - 94);

        if (this.moduleManager.isActive("duel_types")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP * 2) / 3;
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());
            UiLayout.Rect rect2 = new UiLayout.Rect(teamsArea.x() + columnWidth + COLUMN_GAP, teamsArea.y(), columnWidth, teamsArea.height());
            UiLayout.Rect rect3 = new UiLayout.Rect(teamsArea.x() + (columnWidth + COLUMN_GAP) * 2, teamsArea.y(), columnWidth, teamsArea.height());

            int visibleRows = Math.max(0, (teamsArea.height() - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);

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
        } else if (this.moduleManager.isActive("duel_types_config")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP * 2) / 3;
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());

            int visibleRows = Math.max(0, (teamsArea.height() - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);

            if (rect1.contains(mouseX, mouseY)) {
                int total = SessionSnapshotData.duelTypes().size();
                int maxScroll = Math.max(0, total - visibleRows);
                this.duelTypeScrollOffset = Math.clamp(this.duelTypeScrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                return maxScroll > 0;
            }
        } else if (this.moduleManager.isActive("kits")) {
            int columnWidth = (teamsArea.width() - COLUMN_GAP) / 2;
            UiLayout.Rect rect1 = new UiLayout.Rect(teamsArea.x(), teamsArea.y(), columnWidth, teamsArea.height());
            UiLayout.Rect rect2 = new UiLayout.Rect(teamsArea.x() + columnWidth + COLUMN_GAP, teamsArea.y(), columnWidth, teamsArea.height());

            int visibleRows = Math.max(0, (teamsArea.height() - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);

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

    private void renderCustomColumn(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String title, int accentColor, int totalRows, int scrollOffset, int mouseX, int mouseY, java.util.function.Consumer<Integer> rowRenderer) {
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + 3, accentColor);
        context.drawText(textRenderer, Text.literal(title), rect.x() + 8, rect.y() + 7, UiTheme.TEXT, false);

        int visibleRows = Math.max(0, (rect.height() - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);
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

    private boolean sameType(DuelType left, DuelType right) {
        return left != null && right != null && left.id().equalsIgnoreCase(right.id());
    }

    @Override
    public String title() { return "Duels Setup"; }

    @Override
    public String subtitle() { return "Configure Duel variants and arenas"; }

    @Override
    public String gameId() { return DuelsDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        if (this.selectedType == null) return ValidationResult.error("Select a duel type.");
        if (this.selectedMap == null) return ValidationResult.error("Select a map.");
        if (this.selectedKit == null) return ValidationResult.error("Select a kit.");
        if (this.teamGrid.getMembers("team_1").isEmpty()) return ValidationResult.error("Team 1 is empty.");
        if (this.teamGrid.getMembers("team_2").isEmpty()) return ValidationResult.error("Team 2 is empty.");
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putString("mapId", this.selectedMap.id());
        builder.settings().putString("duelType", this.selectedType.id());
        builder.settings().putString("kitId", this.selectedKit.getId().toString());
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("team_1", "Team 1", this.teamGrid.getMembers("team_1"));
        builder.addGroup("team_2", "Team 2", this.teamGrid.getMembers("team_2"));
    }

    @Override
    protected java.util.List<Text> getSummaryLines() {
        int t1 = this.teamGrid.getMembers("team_1").size();
        int t2 = this.teamGrid.getMembers("team_2").size();
        return java.util.List.of(
            Text.literal("Mode: " + (this.selectedType != null ? this.selectedType.name() : "None selected")),
            Text.literal("Map: " + (this.selectedMap != null ? this.selectedMap.id() : "None selected")),
            Text.literal("Kit: " + (this.selectedKit != null ? this.selectedKit.getDisplayName().getString() : "None selected")),
            Text.literal("Players: " + t1 + " vs " + t2)
        );
    }
}
