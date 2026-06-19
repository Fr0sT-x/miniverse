package dev.frost.miniverse.client.gui.map;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.WorkspaceView;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import dev.frost.miniverse.client.gui.ui.UiComponent;
import dev.frost.miniverse.client.gui.ui.UiPrimitives.UiButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MapEditorWorkspaceView implements WorkspaceView {
    private final MapEditorState state;
    private final Runnable refreshAction;
    private final List<UiComponent> components = new ArrayList<>();
    private final String viewGameId;
    private final String viewDefinitionKey;
    private final boolean isGeneral;
    
    private String editingMarkerId = "";
    private double scrollY = 0;
    private double maxScrollY = 0;
    private int pendingRefreshTicks = -1;
    /** Markers removed optimistically — filtered from the list immediately on delete. */
    private final Set<String> localDeletedMarkerIds = new HashSet<>();
    
    private UiLayout.Rect listArea = new UiLayout.Rect(0, 0, 0, 0);
    private SessionScreen screen;
    private TextFieldWidget renameField;
    private String status = "";

    public MapEditorWorkspaceView(MapEditorState state, Runnable refreshAction) {
        this(state, refreshAction, "", "", false);
    }
    
    private MapEditorWorkspaceView(MapEditorState state, Runnable refreshAction, String gameId, String definitionKey, boolean isGeneral) {
        this.state = state;
        this.refreshAction = refreshAction == null ? () -> {} : refreshAction;
        this.viewGameId = gameId == null ? "" : gameId;
        this.viewDefinitionKey = definitionKey == null ? "" : definitionKey;
        this.isGeneral = isGeneral;
    }

    public static MapEditorWorkspaceView forGamemode(MapEditorState state, Runnable refreshAction, String gameId) {
        state.selectedGameId = gameId == null ? "" : gameId;
        state.selectedDefinitionKey = "";
        return new MapEditorWorkspaceView(state, refreshAction, state.selectedGameId, state.selectedDefinitionKey, false);
    }

    public static MapEditorWorkspaceView forMarker(MapEditorState state, Runnable refreshAction, String gameId, String definitionKey) {
        state.selectedGameId = gameId == null ? "" : gameId;
        state.selectedDefinitionKey = definitionKey == null ? "" : definitionKey;
        return new MapEditorWorkspaceView(state, refreshAction, state.selectedGameId, state.selectedDefinitionKey, false);
    }

    public static MapEditorWorkspaceView forGeneral(MapEditorState state, Runnable refreshAction) {
        state.selectedGameId = "";
        state.selectedDefinitionKey = "";
        return new MapEditorWorkspaceView(state, refreshAction, "", "", true);
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.state.selectedGameId = this.viewGameId;
        this.state.selectedDefinitionKey = this.viewDefinitionKey;
        
        this.screen = screen;
        UiLayout.Rect panel = workspace.inset(4);
        this.components.clear();
        // Header has 2 rows: row 1 = breadcrumb (y+4..y+16), row 2 = buttons (y+20..y+42)
        this.listArea = new UiLayout.Rect(panel.x() + 12, panel.y() + 122, panel.width() - 24, panel.height() - 134);
        
        int rightX = panel.x() + panel.width() - 12;
        
        UiButton quitNoSave = new UiButton("Quit (No Save)", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_quit");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Quit without saving?"), Text.literal("All unsaved changes to this map will be lost.")));
        }).accent(UiTheme.ACCENT_RED);
        rightX -= 110;
        quitNoSave.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 110, 20));
        this.components.add(quitNoSave);
        
        rightX -= 104;
        UiButton saveQuit = new UiButton("Save & Quit", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_save_and_quit");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Save and Quit?"), Text.literal("This will save all changes and exit the map editor.")));
        });
        saveQuit.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 100, 20));
        this.components.add(saveQuit);
        
        rightX -= 96;
        UiButton saveWorld = new UiButton("Save World", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_save");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Save Map?"), Text.literal("This will overwrite the current map data with your changes.")));
        });
        saveWorld.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 92, 20));
        this.components.add(saveWorld);
        
        rightX -= 84;
        UiButton refresh = new UiButton("Refresh", this.refreshAction);
        refresh.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 80, 20));
        this.components.add(refresh);

        rightX -= 110;
        UiButton thumbnailBtn = new UiButton("Take Thumbnail", () -> {
            this.sendCommand("miniverse_map_thumbnail");
            this.status = "Requested thumbnail capture. Look around to capture the best view.";
        }).accent(UiTheme.ACCENT_BLUE);
        thumbnailBtn.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 106, 20));
        this.components.add(thumbnailBtn);

        boolean overlaysVisible = !this.state.enabledOverlays.isEmpty();
        String globalOverlayLabel = overlaysVisible ? "Hide Overlays" : "Show Overlays";
        rightX -= 100;
        UiButton toggleOverlays = new UiButton(globalOverlayLabel, () -> {
            if (overlaysVisible) {
                this.state.enabledOverlays.clear();
            } else {
                for (SessionSnapshotData.EditorExtension ext : SessionSnapshotData.editorExtensions()) {
                    for (SessionSnapshotData.EditorMarkerDefinition def : ext.markers()) {
                        this.state.enableOverlay(ext.gameId(), def.key());
                    }
                }
            }
            if (this.screen != null) {
                // Keep the current view but refresh the UI
                if (this.viewDefinitionKey != null && !this.viewDefinitionKey.isEmpty()) {
                    this.screen.openWorkspaceView(MapEditorWorkspaceView.forMarker(this.state, this.refreshAction, this.viewGameId, this.viewDefinitionKey));
                } else if (this.viewGameId != null && !this.viewGameId.isEmpty()) {
                    this.screen.openWorkspaceView(MapEditorWorkspaceView.forGamemode(this.state, this.refreshAction, this.viewGameId));
                } else {
                    this.screen.openWorkspaceView(MapEditorWorkspaceView.forGeneral(this.state, this.refreshAction));
                }
            }
        });
        toggleOverlays.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 96, 20));
        this.components.add(toggleOverlays);

        Selected selected = this.selected();
        if (selected.extension != null && selected.definition == null) {
            rightX -= 114;
            UiButton validateBtn = new UiButton("Validate Map", () -> {
                this.refreshAction.run();
                this.status = "Validation updated.";
            }).accent(UiTheme.ACCENT_BLUE);
            validateBtn.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 110, 20));
            this.components.add(validateBtn);

            rightX -= 90;
            UiButton expandAllBtn = new UiButton("Expand All", () -> {
                selected.extension.markers().forEach(m -> this.state.expandedMarkers.add(m.key()));
            });
            expandAllBtn.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 86, 20));
            this.components.add(expandAllBtn);

            rightX -= 90;
            UiButton collapseAllBtn = new UiButton("Collapse All", () -> {
                this.state.expandedMarkers.clear();
                this.editingMarkerId = "";
                this.renameField.setX(-1000);
            });
            collapseAllBtn.setBounds(new UiLayout.Rect(rightX, panel.y() + 74, 86, 20));
            this.components.add(collapseAllBtn);
        }

        this.renameField = new TextFieldWidget(net.minecraft.client.MinecraftClient.getInstance().textRenderer, -1000, -1000, 150, 20, Text.literal("New marker name"));
        this.renameField.setMaxLength(48);
        screen.addWorkspaceChild(this.renameField);
        this.editingMarkerId = "";

        if (selected.definition != null) {
            String addLabel = switch (selected.definition.type()) {
                case "REGION" -> "Create Region";
                case "MULTI_POINT" -> "Add Point";
                default -> "Add " + selected.definition.displayName();
            };
            UiButton addBtn = new UiButton(addLabel, () -> this.startAdd(selected.extension, selected.definition));
            addBtn.setBounds(new UiLayout.Rect(panel.x() + 12, panel.y() + 78, 130, 20));
            this.components.add(addBtn);

            // Toggle overlay button for this specific marker definition
            boolean overlayOn = this.state.isOverlayEnabled(selected.extension.gameId(), selected.definition.key());
            String overlayLabel = overlayOn ? "\u25C9 Overlay ON" : "\u25CB Overlay OFF";
            UiButton toggleOverlay = new UiButton(overlayLabel, () -> {
                this.state.toggleOverlay(selected.extension.gameId(), selected.definition.key());
                if (this.screen != null) this.screen.openWorkspaceView(MapEditorWorkspaceView.forMarker(this.state, this.refreshAction, selected.extension.gameId(), selected.definition.key()));
            });
            toggleOverlay.setBounds(new UiLayout.Rect(panel.x() + panel.width() - 132, panel.y() + 78, 120, 20));
            this.components.add(toggleOverlay);
        } else if (selected.extension != null) {
            // Auto-expand validation failures
            SessionSnapshotData.EditorGameState gameState = SessionSnapshotData.editorState().games().stream()
                .filter(g -> g.gameId().equalsIgnoreCase(selected.extension.gameId()))
                .findFirst().orElse(null);
            if (gameState != null && gameState.validation() != null && !gameState.validation().valid()) {
                for (SessionSnapshotData.EditorMarkerDefinition markerDef : selected.extension.markers()) {
                    List<SessionSnapshotData.EditorMarker> placedMarkers = SessionSnapshotData.editorState().markers(selected.extension.gameId(), markerDef.key());
                    int count = placedMarkers.size();
                    if (count < markerDef.minCount() || (markerDef.maxCount() > 0 && count > markerDef.maxCount())) {
                        this.state.expandedMarkers.add(markerDef.key());
                    }
                }
            }
        }
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        if (this.pendingRefreshTicks > 0) {
            this.pendingRefreshTicks--;
        } else if (this.pendingRefreshTicks == 0) {
            this.pendingRefreshTicks = -1;
            this.localDeletedMarkerIds.clear(); // Server confirmed — local set no longer needed
            this.refreshAction.run();
        }

        UiLayout.Rect panel = workspace.inset(4);
        UiRenderer.panel(context, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(panel.x() + 1, panel.y() + 1, panel.x() + panel.width() - 1, panel.y() + 76, 0x70283A32);
        
        Selected selected = this.selected();
        
        int crumbX = panel.x() + 12;
        int crumbY = panel.y() + 20;
        boolean yHover = mouseY >= this.listArea.y() - 86 + 14 && mouseY <= this.listArea.y() - 86 + 34;
        
        String rootStr = "Map Editor";
        int rootW = textRenderer.getWidth(rootStr);
        boolean rootHovered = yHover && mouseX >= crumbX && mouseX <= crumbX + rootW;
        context.drawText(textRenderer, Text.literal(rootStr), crumbX, crumbY, rootHovered ? 0xFFFFFFFF : UiTheme.TEXT, false);
        if (rootHovered) context.fill(crumbX, crumbY + 9, crumbX + rootW, crumbY + 10, 0xFFFFFFFF);
        crumbX += rootW;
        
        if (selected.extension != null) {
            String arrowStr = " > ";
            context.drawText(textRenderer, Text.literal(arrowStr), crumbX, crumbY, UiTheme.TEXT_DIM, false);
            crumbX += textRenderer.getWidth(arrowStr);
            
            String gameStr = selected.extension.displayName();
            int gameW = textRenderer.getWidth(gameStr);
            boolean gameHovered = yHover && mouseX >= crumbX && mouseX <= crumbX + gameW;
            context.drawText(textRenderer, Text.literal(gameStr), crumbX, crumbY, gameHovered ? 0xFFFFFFFF : UiTheme.TEXT, false);
            if (gameHovered) context.fill(crumbX, crumbY + 9, crumbX + gameW, crumbY + 10, 0xFFFFFFFF);
            crumbX += gameW;
            
            if (selected.definition != null) {
                context.drawText(textRenderer, Text.literal(arrowStr), crumbX, crumbY, UiTheme.TEXT_DIM, false);
                crumbX += textRenderer.getWidth(arrowStr);
                
                String defStr = selected.definition.displayName();
                context.drawText(textRenderer, Text.literal(defStr), crumbX, crumbY, UiTheme.TEXT, false);
            }
        }
        
        // Remove old map id draw text here as breadcrumbs are above now
        
        if (this.renameField != null && this.renameField.getText().isBlank()) {
            context.drawText(textRenderer, Text.literal("New marker name"), this.renameField.getX() + 6, this.renameField.getY() + 6, UiTheme.TEXT_DIM, false);
        }

        context.enableScissor(this.listArea.x(), this.listArea.y(), this.listArea.x() + this.listArea.width(), this.listArea.y() + this.listArea.height());

        int contentBottom = this.listArea.y();
        if (selected.extension == null) {
            contentBottom = renderGeneral(context, textRenderer, panel);
        } else if (selected.definition == null) {
            contentBottom = renderGamemodeOverview(context, textRenderer, panel, selected);
        } else {
            contentBottom = renderMarkerEditor(context, textRenderer, panel, selected);
        }
        
        context.disableScissor();

        this.maxScrollY = Math.max(0, contentBottom - this.listArea.y() - this.listArea.height());
        this.scrollY = Math.max(0, Math.min(this.scrollY, this.maxScrollY));

        for (UiComponent component : this.components) {
            component.render(context, textRenderer, mouseX, mouseY, delta);
        }
        
        if (!this.status.isBlank()) {
            context.drawText(textRenderer, Text.literal(this.status), panel.x() + 12, panel.y() + panel.height() - 18, UiTheme.TEXT_DIM, false);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.listArea.contains((int) mouseX, (int) mouseY) && this.maxScrollY > 0) {
            this.scrollY = Math.max(0, Math.min(this.scrollY - verticalAmount * 16.0, this.maxScrollY));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int adjustedMouseY = (int) mouseY;
        if (this.listArea.contains((int) mouseX, (int) mouseY)) {
            adjustedMouseY += (int) this.scrollY;
        }
        if (button != 0) {
            return false;
        }
        for (UiComponent component : this.components) {
            if (component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        Selected selected = this.selected();
        
        if (mouseY >= this.listArea.y() - 86 + 14 && mouseY <= this.listArea.y() - 86 + 34) {
            int crumbX = this.listArea.x();
            int rootWidth = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth("Map Editor");
            if (mouseX >= crumbX && mouseX <= crumbX + rootWidth) {
                if (this.screen != null) this.screen.openWorkspaceView(MapEditorWorkspaceView.forGeneral(this.state, this.refreshAction));
                return true;
            }
            crumbX += rootWidth;
            if (selected.extension != null) {
                crumbX += net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(" > ");
                int gameWidth = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(selected.extension.displayName());
                if (mouseX >= crumbX && mouseX <= crumbX + gameWidth) {
                    if (this.screen != null) this.screen.openMapEditorGamemode(selected.extension.gameId());
                    return true;
                }
            }
        }

        if (selected.extension == null) {
            int y = this.listArea.y() + 20;
            for (SessionSnapshotData.EditorExtension extension : SessionSnapshotData.editorExtensions()) {
                UiLayout.Rect row = new UiLayout.Rect(this.listArea.x(), y, this.listArea.width(), 34);
                if (row.contains(mouseX, mouseY)) {
                    if (this.screen != null) {
                        this.screen.openMapEditorGamemode(extension.gameId());
                    }
                    return true;
                }
                y += 40;
            }
            return false;
        }
        if (selected.definition == null) {
            int rowY = this.listArea.y() + 10;
            SessionSnapshotData.EditorGameState gameState = SessionSnapshotData.editorState().games().stream()
                .filter(g -> g.gameId().equalsIgnoreCase(selected.extension.gameId()))
                .findFirst().orElse(null);
            
            if (gameState != null && gameState.validation() != null) {
                if (!gameState.validation().valid()) {
                    rowY += gameState.validation().errors().size() * 14;
                }
                rowY += 10;
            }
            
            for (SessionSnapshotData.EditorMarkerDefinition marker : selected.extension.markers()) {
                boolean expanded = this.state.expandedMarkers.contains(marker.key());
                int headerHeight = 36;
                UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
                
                List<SessionSnapshotData.EditorMarker> placedMarkers = SessionSnapshotData.editorState().markers(selected.extension.gameId(), marker.key());
                int count = placedMarkers.size();
                String countText = marker.maxCount() < 0 ? count + " / \u221E" : count + " / " + marker.maxCount();
                String statusText = "✓ Valid";
                if (count < marker.minCount()) statusText = "⚠ Missing";
                else if (marker.maxCount() > 0 && count > marker.maxCount()) statusText = "⚠ Too Many";
                String statsStr = "Placed: " + countText + "   Status: ";
                int statsW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(statsStr);
                int statusW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(statusText);
                int textEnd = headerRow.x() + headerRow.width() - statsW - statusW - 14;
                UiLayout.Rect addBtn = new UiLayout.Rect(textEnd - 36, headerRow.y() + 8, 30, 20);
                
                if (addBtn.contains(mouseX, adjustedMouseY)) {
                    this.startAdd(selected.extension, marker);
                    return true;
                }
                
                if (headerRow.contains(mouseX, adjustedMouseY)) {
                    if (expanded) {
                        this.state.expandedMarkers.remove(marker.key());
                        if (this.editingMarkerId != null && !this.editingMarkerId.isEmpty()) {
                            for (SessionSnapshotData.EditorMarker m : placedMarkers) {
                                if (m.id().equals(this.editingMarkerId)) {
                                    this.editingMarkerId = "";
                                    this.renameField.setX(-1000);
                                    break;
                                }
                            }
                        }
                    } else {
                        this.state.expandedMarkers.add(marker.key());
                    }
                    return true;
                }
                
                rowY += headerHeight;
                if (expanded) {
                    if (placedMarkers.isEmpty()) {
                        rowY += 40;
                    } else {
                        int indentX = this.listArea.x() + 20;
                        int innerWidth = this.listArea.width() - 20;
                        for (SessionSnapshotData.EditorMarker placed : placedMarkers) {
                            boolean isRegion = "REGION".equalsIgnoreCase(marker.type());
                            int rowHeight = isRegion ? 60 : 40;
                            UiLayout.Rect row = new UiLayout.Rect(indentX, rowY, innerWidth, rowHeight);
                            
                            UiLayout.Rect toggle = new UiLayout.Rect(row.x() + row.width() - 302, row.y() + 10, 68, 20);
                            UiLayout.Rect rename = new UiLayout.Rect(row.x() + row.width() - 226, row.y() + 10, 68, 20);
                            UiLayout.Rect teleport = new UiLayout.Rect(row.x() + row.width() - 150, row.y() + 10, 68, 20);
                            UiLayout.Rect delete = new UiLayout.Rect(row.x() + row.width() - 74, row.y() + 10, 60, 20);
                            
                            if (toggle.contains(mouseX, adjustedMouseY)) {
                                if (!this.state.hiddenIndividualMarkers.remove(placed.id())) {
                                    this.state.hiddenIndividualMarkers.add(placed.id());
                                }
                                return true;
                            }
                            if (rename.contains(mouseX, adjustedMouseY)) {
                                if (this.editingMarkerId.equals(placed.id())) {
                                    if (this.renameField.getText().trim().isBlank()) {
                                        this.status = "Enter a new marker name first.";
                                        return true;
                                    }
                                    this.sendMarkerAction("rename", selected.extension.gameId(), marker.key(), placed.id(), this.renameField.getText().trim());
                                    this.editingMarkerId = "";
                                    this.renameField.setX(-1000);
                                    this.pendingRefreshTicks = 5;
                                } else {
                                    this.editingMarkerId = placed.id();
                                    this.renameField.setText(placed.name());
                                }
                                return true;
                            }
                            if (teleport.contains(mouseX, adjustedMouseY)) {
                                this.sendMarkerAction("teleport", selected.extension.gameId(), marker.key(), placed.id());
                                return true;
                            }
                            if (delete.contains(mouseX, adjustedMouseY)) {
                                this.sendMarkerAction("delete", selected.extension.gameId(), marker.key(), placed.id());
                                if (this.editingMarkerId.equals(placed.id())) {
                                    this.editingMarkerId = "";
                                    this.renameField.setX(-1000);
                                }
                                this.localDeletedMarkerIds.add(placed.id());
                                this.pendingRefreshTicks = 2;
                                return true;
                            }
                            if (isRegion) {
                                int cx = row.x() + 10;
                                int pillY = row.y() + 36;
                                for (dev.frost.miniverse.minigame.core.region.RegionRestriction res : dev.frost.miniverse.minigame.core.region.RegionRestriction.values()) {
                                    int pillW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(res.name()) + 16;
                                    UiLayout.Rect pill = new UiLayout.Rect(cx, pillY, pillW, 16);
                                    if (pill.contains(mouseX, adjustedMouseY)) {
                                        this.toggleRestriction(selected.extension.gameId(), marker.key(), placed, res);
                                        return true;
                                    }
                                    cx += pillW + 6;
                                }
                            }
                            rowY += rowHeight + 4;
                        }
                    }
                }
                rowY += 10;
            }
            return false;
        }
        
        List<SessionSnapshotData.EditorMarker> markers = SessionSnapshotData.editorState().markers(selected.extension.gameId(), selected.definition.key());
        int rowY = this.screen != null ? this.screen.height / 2 - 200 + 52 : 52; // Fallback calculation, but panel is actually bounded differently
        // Wait, listArea doesn't represent panel.y() directly. Let's just calculate from listArea since we know it's there
        rowY = this.listArea.y() - 86 + 52; 
        int indentX = this.listArea.x() + 20;
        int innerWidth = this.listArea.width() - 20;
        
        if (markers.isEmpty()) return false;
        
        for (SessionSnapshotData.EditorMarker marker : markers) {
            boolean isRegion = "REGION".equalsIgnoreCase(selected.definition.type());
            int rowHeight = isRegion ? 60 : 40;
            UiLayout.Rect row = new UiLayout.Rect(indentX, rowY, innerWidth, rowHeight);
            
            UiLayout.Rect toggle = new UiLayout.Rect(row.x() + row.width() - 302, row.y() + 10, 68, 20);
            UiLayout.Rect rename = new UiLayout.Rect(row.x() + row.width() - 226, row.y() + 10, 68, 20);
            UiLayout.Rect teleport = new UiLayout.Rect(row.x() + row.width() - 150, row.y() + 10, 68, 20);
            UiLayout.Rect delete = new UiLayout.Rect(row.x() + row.width() - 74, row.y() + 10, 60, 20);
            
            if (toggle.contains(mouseX, adjustedMouseY)) {
                if (!this.state.hiddenIndividualMarkers.remove(marker.id())) {
                    this.state.hiddenIndividualMarkers.add(marker.id());
                }
                return true;
            }
            if (rename.contains(mouseX, adjustedMouseY)) {
                if (this.editingMarkerId.equals(marker.id())) {
                    if (this.renameField.getText().trim().isBlank()) {
                        this.status = "Enter a new marker name first.";
                        return true;
                    }
                    this.sendMarkerAction("rename", selected.extension.gameId(), selected.definition.key(), marker.id(), this.renameField.getText().trim());
                    this.editingMarkerId = "";
                    this.renameField.setX(-1000);
                    this.pendingRefreshTicks = 5;
                } else {
                    this.editingMarkerId = marker.id();
                    this.renameField.setText(marker.name());
                }
                return true;
            }
            if (teleport.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("teleport", selected.extension.gameId(), selected.definition.key(), marker.id());
                return true;
            }
            if (delete.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("delete", selected.extension.gameId(), selected.definition.key(), marker.id());
                if (this.editingMarkerId.equals(marker.id())) {
                    this.editingMarkerId = "";
                    this.renameField.setX(-1000);
                }
                this.localDeletedMarkerIds.add(marker.id());
                this.pendingRefreshTicks = 2;
                return true;
            }
            if (isRegion) {
                int cx = row.x() + 10;
                int pillY = row.y() + 36;
                for (dev.frost.miniverse.minigame.core.region.RegionRestriction res : dev.frost.miniverse.minigame.core.region.RegionRestriction.values()) {
                    int pillW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(res.name()) + 16;
                    UiLayout.Rect pill = new UiLayout.Rect(cx, pillY, pillW, 16);
                    if (pill.contains(mouseX, adjustedMouseY)) {
                        this.toggleRestriction(selected.extension.gameId(), selected.definition.key(), marker, res);
                        return true;
                    }
                    cx += pillW + 6;
                }
            }
            rowY += rowHeight + 4;
        }
        return false;
    }

    private void toggleRestriction(String gameId, String definitionKey, SessionSnapshotData.EditorMarker marker, dev.frost.miniverse.minigame.core.region.RegionRestriction restriction) {
        com.google.gson.JsonObject properties = marker.properties() != null ? marker.properties().deepCopy() : new com.google.gson.JsonObject();
        com.google.gson.JsonArray restrictions = properties.has("restrictions") && properties.get("restrictions").isJsonArray() ? properties.getAsJsonArray("restrictions") : new com.google.gson.JsonArray();
        boolean found = false;
        com.google.gson.JsonArray updated = new com.google.gson.JsonArray();
        for (com.google.gson.JsonElement e : restrictions) {
            if (e.getAsString().equals(restriction.name())) found = true;
            else updated.add(e);
        }
        if (!found) {
            updated.add(restriction.name());
        }
        properties.add("restrictions", updated);
        
        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", "update_properties");
        nbt.putString("gameId", gameId);
        nbt.putString("definitionKey", definitionKey);
        nbt.putString("markerId", marker.id());
        nbt.putString("properties", properties.toString());
        ClientPlayNetworking.send(new NetworkConstants.MapEditorActionPayload(nbt));
        this.pendingRefreshTicks = 2;
    }

    @Override
    public String title() {
        return "Map Editor";
    }

    @Override
    public String subtitle() {
        return "";
    }

    public boolean generalSelected() {
        return this.viewGameId.isBlank() && this.isGeneral;
    }

    public boolean isOverviewSelected() {
        return this.viewGameId.isBlank() && !this.isGeneral;
    }

    public boolean gameSelected(String gameId) {
        Selected selected = this.selected();
        return selected.extension != null && selected.extension.gameId().equalsIgnoreCase(gameId);
    }

    public boolean markerSelected(String gameId, String definitionKey) {
        Selected selected = this.selected();
        return selected.extension != null
            && selected.definition != null
            && selected.extension.gameId().equalsIgnoreCase(gameId)
            && selected.definition.key().equalsIgnoreCase(definitionKey);
    }

    private int renderGeneral(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        int x = panel.x() + 12;
        int y = this.listArea.y() - (int) this.scrollY;
        context.drawText(textRenderer, Text.literal("Registered Editor Gamemodes"), x, y, UiTheme.TEXT, false);
        y += 20;
        for (SessionSnapshotData.EditorExtension extension : SessionSnapshotData.editorExtensions()) {
            UiLayout.Rect row = new UiLayout.Rect(x, y, panel.width() - 24, 34);
            UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            context.drawText(textRenderer, Text.literal(extension.displayName()), x + 10, y + 8, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(extension.markers().size() + " editor area(s). Click to edit."), x + 170, y + 8, UiTheme.TEXT_DIM, false);
            y += 40;
        }
        return y + (int) this.scrollY;
    }

    private int renderGamemodeOverview(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel, Selected selected) {
        int rowY = this.listArea.y() + 10 - (int) this.scrollY;
        
        SessionSnapshotData.EditorGameState gameState = SessionSnapshotData.editorState().games().stream()
            .filter(g -> g.gameId().equalsIgnoreCase(selected.extension.gameId()))
            .findFirst().orElse(null);
            
        if (gameState != null && gameState.validation() != null) {
            SessionSnapshotData.EditorValidation v = gameState.validation();
            if (v.valid()) {
                context.drawText(textRenderer, Text.literal("Status: ").append(Text.literal("✓ Valid").withColor(UiTheme.ACCENT_GREEN)), this.listArea.x(), this.listArea.y(), UiTheme.TEXT, false);
            } else {
                context.drawText(textRenderer, Text.literal("Status: ").append(Text.literal("⚠ Invalid").withColor(UiTheme.ACCENT_RED)), this.listArea.x(), this.listArea.y(), UiTheme.TEXT, false);
                for (String err : v.errors()) {
                    context.drawText(textRenderer, Text.literal("⚠ " + err).withColor(UiTheme.ACCENT_RED), this.listArea.x() + 10, rowY, UiTheme.TEXT, false);
                    rowY += 14;
                }
            }
            rowY += 10;
        }
        
        if (selected.extension.markers().isEmpty()) {
            context.drawText(textRenderer, Text.literal("⚠ " + selected.extension.displayName() + " is not yet configured.").withColor(UiTheme.ACCENT_RED), this.listArea.x(), rowY + 10, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Create the required markers to make this map playable."), this.listArea.x(), rowY + 24, UiTheme.TEXT_DIM, false);
            return rowY;
        }
        
        for (SessionSnapshotData.EditorMarkerDefinition marker : selected.extension.markers()) {
            boolean expanded = this.state.expandedMarkers.contains(marker.key());
            int headerHeight = 36;
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
            UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), expanded ? UiTheme.CARD_HOVER : UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            
            String expandIcon = expanded ? "▼" : "▶";
            context.drawText(textRenderer, Text.literal(expandIcon), row.x() + 10, row.y() + 14, UiTheme.TEXT_DIM, false);
            context.drawText(textRenderer, Text.literal(marker.displayName()), row.x() + 26, row.y() + 14, UiTheme.TEXT, false);
            
            List<SessionSnapshotData.EditorMarker> placedMarkers = SessionSnapshotData.editorState().markers(selected.extension.gameId(), marker.key());
            int count = placedMarkers.size();
            String countText = marker.maxCount() < 0 ? count + " / \u221E" : count + " / " + marker.maxCount();
            
            int statusColor = UiTheme.ACCENT_GREEN;
            String statusText = "✓ Valid";
            if (count < marker.minCount()) {
                statusText = "⚠ Missing";
                statusColor = UiTheme.ACCENT_RED;
            } else if (marker.maxCount() > 0 && count > marker.maxCount()) {
                statusText = "⚠ Too Many";
                statusColor = UiTheme.ACCENT_RED;
            }
            
            String statsStr = "Placed: " + countText + "   Status: ";
            int statsW = textRenderer.getWidth(statsStr);
            int textEnd = row.x() + row.width() - statsW - textRenderer.getWidth(statusText) - 14;
            
            renderSmallButton(context, textRenderer, textEnd - 36, row.y() + 8, 30, "+");
            
            context.drawText(textRenderer, Text.literal(statsStr).append(Text.literal(statusText).withColor(statusColor)), textEnd, row.y() + 14, UiTheme.TEXT_DIM, false);
            
            rowY += headerHeight;
            
            if (expanded) {
                rowY = renderMarkerEditorInline(context, textRenderer, selected.extension, marker, rowY, placedMarkers);
            }
            rowY += 10;
        }
        return rowY + (int) this.scrollY;
    }

    private int renderMarkerEditorInline(DrawContext context, TextRenderer textRenderer, SessionSnapshotData.EditorExtension extension, SessionSnapshotData.EditorMarkerDefinition definition, int startY, List<SessionSnapshotData.EditorMarker> markers) {
        int rowY = startY;
        int indentX = this.listArea.x() + 20;
        int innerWidth = this.listArea.width() - 20;
        
        // Filter out optimistically-deleted markers
        List<SessionSnapshotData.EditorMarker> visibleMarkers = markers.stream()
            .filter(m -> !this.localDeletedMarkerIds.contains(m.id()))
            .toList();
        
        if (visibleMarkers.isEmpty()) {
            UiRenderer.panel(context, indentX, rowY, innerWidth, 40, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            context.drawText(textRenderer, Text.literal("No markers placed."), indentX + 10, rowY + 16, UiTheme.TEXT_DIM, false);
            return rowY + 40;
        }
        
        int index = 1;
        for (SessionSnapshotData.EditorMarker marker : visibleMarkers) {
            boolean isRegion = "REGION".equalsIgnoreCase(definition.type());
            // Region rows are taller to accommodate restriction pills below the action buttons
            int rowHeight = isRegion ? 66 : 40;
            UiLayout.Rect row = new UiLayout.Rect(indentX, rowY, innerWidth, rowHeight);
            UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            
            context.drawText(textRenderer, Text.literal(index + ". " + marker.name()), row.x() + 10, row.y() + 8, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(locationText(marker)), row.x() + 10, row.y() + 22, UiTheme.TEXT_DIM, false);
            
            boolean isHidden = this.state.hiddenIndividualMarkers.contains(marker.id());
            String toggleLabel = isHidden ? "Show" : "Hide";
            renderSmallButton(context, textRenderer, row.x() + row.width() - 302, row.y() + 10, 68, toggleLabel);
            renderSmallButton(context, textRenderer, row.x() + row.width() - 226, row.y() + 10, 68, "Rename");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 150, row.y() + 10, 68, "Teleport");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 74, row.y() + 10, 60, "Delete");
            
            if (this.editingMarkerId.equals(marker.id())) {
                this.renameField.setX(row.x() + row.width() - 226);
                this.renameField.setY(row.y() + 10);
            }
            
            if (isRegion) {
                // Restriction pills row
                int cx = row.x() + 10;
                int pillY = row.y() + 36;
                for (dev.frost.miniverse.minigame.core.region.RegionRestriction res : dev.frost.miniverse.minigame.core.region.RegionRestriction.values()) {
                    boolean active = false;
                    if (marker.properties() != null && marker.properties().has("restrictions") && marker.properties().get("restrictions").isJsonArray()) {
                        for (com.google.gson.JsonElement e : marker.properties().getAsJsonArray("restrictions")) {
                            if (e.getAsString().equals(res.name())) active = true;
                        }
                    }
                    int pillW = textRenderer.getWidth(res.name()) + 16;
                    int pillColor = active ? 0xFF1A3A1A : 0xFF1A1A2E;
                    int pillBorder = active ? 0xFF33AA33 : 0xFF555577;
                    int textColor = active ? 0xFF66FF66 : UiTheme.TEXT_MUTED;
                    UiRenderer.panel(context, cx, pillY, pillW, 16, pillColor, pillBorder);
                    context.drawText(textRenderer, Text.literal(res.name()), cx + 8, pillY + 4, textColor, false);
                    cx += pillW + 6;
                }
            }
            
            rowY += rowHeight + 4;
            index++;
        }
        return rowY;
    }

    private int renderMarkerEditor(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel, Selected selected) {
        int rowY = panel.y() + 52 - (int) this.scrollY;
        List<SessionSnapshotData.EditorMarker> markers = SessionSnapshotData.editorState().markers(selected.extension.gameId(), selected.definition.key());
        return renderMarkerEditorInline(context, textRenderer, selected.extension, selected.definition, rowY, markers) + (int) this.scrollY;
    }

    private static void renderSmallButton(DrawContext context, TextRenderer textRenderer, int x, int y, int width, String label) {
        UiRenderer.panel(context, x, y, width, 20, UiTheme.PANEL_RAISED, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal(label), x + (width - textRenderer.getWidth(label)) / 2, y + 6, UiTheme.TEXT, false);
    }

    private static String locationText(SessionSnapshotData.EditorMarker marker) {
        if ("REGION".equalsIgnoreCase(marker.type())) {
            if (marker.regions() == null || marker.regions().isEmpty()) return "Bounds: Not Set";
            var part = marker.regions().getFirst();
            return "Bounds: " + format(part.min()) + " \u2192 " + format(part.max())
                + (marker.regions().size() > 1 ? " (+" + (marker.regions().size() - 1) + " more)" : "");
        }
        if (marker.points().isEmpty()) {
            return "Location: Not Set";
        }
        if (marker.points().size() == 1) {
            SessionSnapshotData.EditorPoint point = marker.points().getFirst();
            return "Location: " + format(point);
        }
        return "Points: " + marker.points().size() + "  First: " + format(marker.points().getFirst());
    }

    private static String format(SessionSnapshotData.EditorPoint point) {
        return "(" + Math.round(point.x()) + ", " + Math.round(point.y()) + ", " + Math.round(point.z()) + ")";
    }

    private Selected selected() {
        if (this.viewGameId.isBlank()) {
            return new Selected(null, null);
        }
        SessionSnapshotData.EditorExtension extension = SessionSnapshotData.editorExtensions().stream()
            .filter(candidate -> candidate.gameId().equalsIgnoreCase(this.viewGameId))
            .findFirst()
            .orElse(null);
        if (extension == null) {
            return new Selected(null, null);
        }
        if (this.viewDefinitionKey.isBlank()) {
            return new Selected(extension, null);
        }
        SessionSnapshotData.EditorMarkerDefinition definition = extension.markers().stream()
            .filter(candidate -> candidate.key().equalsIgnoreCase(this.viewDefinitionKey))
            .findFirst()
            .orElse(null);
        return new Selected(extension, definition);
    }

    private void startAdd(SessionSnapshotData.EditorExtension extension, SessionSnapshotData.EditorMarkerDefinition definition) {
        this.sendMarkerAction("start_add", extension.gameId(), definition.key(), "");
        this.status = "Placement mode started. Close the screen and left click a block; right click cancels.";
    }

    private void sendMarkerAction(String action, String gameId, String definitionKey, String markerId) {
        this.sendMarkerAction(action, gameId, definitionKey, markerId, "");
    }

    private void sendMarkerAction(String action, String gameId, String definitionKey, String markerId, String name) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", action);
        nbt.putString("gameId", gameId);
        nbt.putString("definitionKey", definitionKey);
        nbt.putString("markerId", markerId == null ? "" : markerId);
        nbt.putString("name", name == null ? "" : name);
        ClientPlayNetworking.send(new NetworkConstants.MapEditorActionPayload(nbt));
    }

    private void sendCommand(String command) {
        if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
            net.minecraft.client.MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command);
        }
    }

    private record Selected(SessionSnapshotData.EditorExtension extension, SessionSnapshotData.EditorMarkerDefinition definition) {
    }
}
