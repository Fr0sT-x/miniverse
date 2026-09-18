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
    private String editingGameId = "";
    private String editingMarkerKey = "";
    private UiLayout.Rect activeSaveButtonRect = null;
    private TextFieldWidget renameField;
    private int pendingRefreshTicks = -1;
    private final Set<String> localDeletedMarkerIds = new HashSet<>();
    private String drillDownParentId = null;
    private String drillDownParentKey = null;
    
    private double scrollY = 0;
    private double maxScrollY = 0;
    
    private UiLayout.Rect listArea = new UiLayout.Rect(0, 0, 0, 0);
    private SessionScreen screen;
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
        this.listArea = new UiLayout.Rect(panel.x() + 12, panel.y() + 86, panel.width() - 24, panel.height() - 98);
        
        int[] topRightX = { panel.x() + panel.width() - 12 };
        int topY = panel.y() + 44;

        java.util.function.BiConsumer<UiButton, Integer> addTopRightBtn = (btn, width) -> {
            topRightX[0] -= width;
            btn.setBounds(new UiLayout.Rect(topRightX[0], topY, width, 20));
            this.components.add(btn);
            topRightX[0] -= 4; // padding
        };

        UiButton quitNoSave = new UiButton("Quit (No Save)", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_quit");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Quit without saving?"), Text.literal("All unsaved changes to this map will be lost.")));
        }).accent(UiTheme.ACCENT_RED);
        addTopRightBtn.accept(quitNoSave, 110);
        
        UiButton saveQuit = new UiButton("Save & Quit", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_save_and_quit");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Save and Quit?"), Text.literal("This will save all changes and exit the map editor.")));
        });
        addTopRightBtn.accept(saveQuit, 100);
        
        UiButton saveWorld = new UiButton("Save World", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_save");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Save Map?"), Text.literal("This will overwrite the current map data with your changes.")));
        });
        addTopRightBtn.accept(saveWorld, 92);
        
        UiButton refresh = new UiButton("Refresh", this.refreshAction);
        addTopRightBtn.accept(refresh, 80);

        UiButton thumbnailBtn = new UiButton("Take Thumbnail", () -> {
            this.sendCommand("miniverse_map_thumbnail");
            this.status = "Requested thumbnail capture. Look around to capture the best view.";
        }).accent(UiTheme.ACCENT_BLUE);
        addTopRightBtn.accept(thumbnailBtn, 106);

        boolean overlaysVisible = !this.state.enabledOverlays.isEmpty();
        String globalOverlayLabel = overlaysVisible ? "Hide All Overlays" : "Show All Overlays";
        UiButton toggleOverlays = new UiButton(globalOverlayLabel, () -> {
            if (overlaysVisible) {
                this.state.enabledOverlays.clear();
                this.state.explicitlyShownMarkers.clear();
                this.state.hiddenIndividualMarkers.clear();
            } else {
                this.state.hiddenIndividualMarkers.clear();
                for (SessionSnapshotData.EditorExtension ext : SessionSnapshotData.editorExtensions()) {
                    for (SessionSnapshotData.EditorMarkerDefinition def : ext.markers()) {
                        this.state.enableOverlay(ext.gameId(), def.key());
                    }
                }
            }
            if (this.screen != null) {
                if (this.viewDefinitionKey != null && !this.viewDefinitionKey.isEmpty()) {
                    this.screen.openWorkspaceView(MapEditorWorkspaceView.forMarker(this.state, this.refreshAction, this.viewGameId, this.viewDefinitionKey));
                } else if (this.viewGameId != null && !this.viewGameId.isEmpty()) {
                    this.screen.openWorkspaceView(MapEditorWorkspaceView.forGamemode(this.state, this.refreshAction, this.viewGameId));
                } else {
                    this.screen.openWorkspaceView(MapEditorWorkspaceView.forGeneral(this.state, this.refreshAction));
                }
            }
        });
        addTopRightBtn.accept(toggleOverlays, 114);

        Selected selected = this.selected();
        if (selected.extension != null && selected.definition == null) {
            UiButton clearBtn = new UiButton("Clear Gamemode", () -> {
                net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                    if (confirmed) {
                        for (SessionSnapshotData.EditorMarkerDefinition d : selected.extension.markers()) {
                            java.util.List<SessionSnapshotData.EditorMarker> ms = SessionSnapshotData.editorState().markers(selected.extension.gameId(), d.key());
                            if (ms != null) {
                                for (SessionSnapshotData.EditorMarker m : ms) {
                                    this.sendMarkerAction("delete", selected.extension.gameId(), d.key(), m.id());
                                    this.localDeletedMarkerIds.add(m.id());
                                }
                            }
                        }
                        this.refreshAction.run();
                    }
                    net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
                }, net.minecraft.text.Text.literal("Clear All Markers"), net.minecraft.text.Text.literal("Are you sure you want to delete ALL markers for " + selected.extension.displayName() + "? This cannot be undone.")));
            }).accent(UiTheme.ACCENT_RED);
            addTopRightBtn.accept(clearBtn, 110);

            UiButton expandAllBtn = new UiButton("Expand All", () -> {
                selected.extension.markers().forEach(m -> this.state.expandedMarkers.add(m.key()));
            });
            addTopRightBtn.accept(expandAllBtn, 86);

            UiButton collapseAllBtn = new UiButton("Collapse All", () -> {
                this.state.expandedMarkers.clear();
                this.editingMarkerId = "";
                this.renameField.setX(-1000);
            });
            addTopRightBtn.accept(collapseAllBtn, 86);
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
            addBtn.setBounds(new UiLayout.Rect(panel.x() + 12, topY, 130, 20));
            this.components.add(addBtn);

            boolean overlayOn = this.state.isOverlayEnabled(selected.extension.gameId(), selected.definition.key());
            String overlayLabel = overlayOn ? "\u25C9 Overlay ON" : "\u25CB Overlay OFF";
            UiButton toggleOverlay = new UiButton(overlayLabel, () -> {
                this.state.toggleOverlay(selected.extension.gameId(), selected.definition.key());
                if (this.screen != null) this.screen.openWorkspaceView(MapEditorWorkspaceView.forMarker(this.state, this.refreshAction, selected.extension.gameId(), selected.definition.key()));
            });
            addTopRightBtn.accept(toggleOverlay, 100);
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
        
        if (!this.editingMarkerId.isEmpty() && this.renameField != null && this.renameField.getX() > -500 && this.renameField.getText().isBlank()) {
            context.drawText(textRenderer, Text.literal("New marker name"), this.renameField.getX() + 6, this.renameField.getY() + 5, UiTheme.TEXT_DIM, false);
        } else if (this.editingMarkerId.isEmpty() && this.renameField != null && this.renameField.getX() > -500) {
            this.renameField.setX(-1000);
        }

        context.enableScissor(this.listArea.x(), this.listArea.y(), this.listArea.x() + this.listArea.width(), this.listArea.y() + this.listArea.height());

        int contentBottom = this.listArea.y();
        if (selected.extension == null) {
            contentBottom = renderGeneral(context, textRenderer, panel);
        } else if (selected.definition == null) {
            contentBottom = renderGamemodeOverview(context, textRenderer, panel, selected);
        } else {
            this.maxScrollY = Math.max(0, renderMarkerEditor(context, textRenderer, panel, selected, mouseX, mouseY) - panel.y() - panel.height() + 10);
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

    private void startRename(String gameId, String markerKey, String markerId, String currentName) {
        this.editingGameId = gameId;
        this.editingMarkerKey = markerKey;
        this.editingMarkerId = markerId;
        if (this.renameField != null) {
            this.renameField.setText(currentName);
            this.renameField.setSelectionStart(0);
            this.renameField.setSelectionEnd(currentName.length());
            this.renameField.setFocused(true);
            if (this.screen != null) {
                this.screen.setFocused(this.renameField);
            }
        }
        this.status = "Editing name for marker '" + currentName + "'. Press Enter to save, or click away to cancel.";
    }

    private void confirmRename() {
        if (this.editingMarkerId.isEmpty()) {
            return;
        }
        String newName = this.renameField != null ? this.renameField.getText().trim() : "";
        if (newName.isBlank()) {
            this.status = "Enter a new marker name first.";
            return;
        }
        this.sendMarkerAction("rename", this.editingGameId, this.editingMarkerKey, this.editingMarkerId, newName);
        this.status = "Renamed marker to '" + newName + "'.";
        this.editingMarkerId = "";
        this.editingGameId = "";
        this.editingMarkerKey = "";
        this.activeSaveButtonRect = null;
        if (this.renameField != null) {
            this.renameField.setX(-1000);
            this.renameField.setFocused(false);
        }
        if (this.screen != null) {
            this.screen.setFocused(null);
        }
        this.pendingRefreshTicks = 3;
    }

    private void cancelRename() {
        if (this.editingMarkerId.isEmpty()) {
            return;
        }
        this.editingMarkerId = "";
        this.editingGameId = "";
        this.editingMarkerKey = "";
        this.activeSaveButtonRect = null;
        if (this.renameField != null) {
            this.renameField.setX(-1000);
            this.renameField.setFocused(false);
        }
        if (this.screen != null) {
            this.screen.setFocused(null);
        }
        this.status = "Marker renaming cancelled.";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.editingMarkerId.isEmpty() && this.renameField != null) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                this.confirmRename();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                this.cancelRename();
                return true;
            }
            return this.renameField.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!this.editingMarkerId.isEmpty() && this.renameField != null) {
            return this.renameField.charTyped(chr, modifiers);
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.editingMarkerId.isEmpty()) {
            if (this.renameField != null && this.renameField.getX() > -500) {
                UiLayout.Rect fieldRect = new UiLayout.Rect(this.renameField.getX(), this.renameField.getY(), this.renameField.getWidth(), this.renameField.getHeight());
                if (fieldRect.contains((int) mouseX, (int) mouseY)) {
                    return this.renameField.mouseClicked(mouseX, mouseY, button);
                }
            }
            if (this.activeSaveButtonRect != null && this.activeSaveButtonRect.contains((int) mouseX, (int) mouseY)) {
                this.confirmRename();
                return true;
            }
            this.cancelRename();
            return true;
        }

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
            
            if (selected.extension.markers().isEmpty()) {
                return false;
            }

            if (this.drillDownParentId != null) {
                UiLayout.Rect backBtn = new UiLayout.Rect(this.listArea.x(), rowY, 100, 20);
                if (backBtn.contains(mouseX, adjustedMouseY)) {
                    this.drillDownParentId = null;
                    this.drillDownParentKey = null;
                    return true;
                }
                rowY += 30;
            }
            
            for (SessionSnapshotData.EditorMarkerDefinition marker : selected.extension.markers()) {
                boolean isChild = marker.grouping() != null && marker.grouping().parentKey() != null;
                if (this.drillDownParentId == null && isChild) continue;
                if (this.drillDownParentId != null && (!isChild || !marker.grouping().parentKey().equals(this.drillDownParentKey))) continue;

                boolean expanded = this.state.expandedMarkers.contains(marker.key());
                int headerHeight = 36;
                UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
                
                SessionSnapshotData.EditorMarker drillDownParentMarker = this.drillDownParentId != null ? SessionSnapshotData.editorState().markers(selected.extension.gameId(), this.drillDownParentKey).stream().filter(m -> m.id().equals(this.drillDownParentId)).findFirst().orElse(null) : null;
                
                List<SessionSnapshotData.EditorMarker> placedMarkers = SessionSnapshotData.editorState().markers(selected.extension.gameId(), marker.key()).stream()
                    .filter(p -> drillDownParentMarker == null || belongsToParent(p, marker.grouping(), drillDownParentMarker))
                    .toList();
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
                            
                            boolean isHidden = this.state.hiddenIndividualMarkers.contains(placed.id());
                            String toggleLabel = isHidden ? "Show" : "Hide";
                            
                            boolean isParent = selected.extension.markers().stream().anyMatch(m -> m.grouping() != null && marker.key().equals(m.grouping().parentKey()));
                            if (isParent && this.drillDownParentId == null) {
                                UiLayout.Rect configureBtn = new UiLayout.Rect(row.x() + row.width() - 392, row.y() + 10, 84, 20);
                                if (configureBtn.contains(mouseX, adjustedMouseY)) {
                                    this.drillDownParentId = placed.id();
                                    this.drillDownParentKey = marker.key();
                                    return true;
                                }
                            }
                            
                            if (toggle.contains(mouseX, adjustedMouseY)) {
                                this.state.toggleMarkerVisibility(selected.extension.gameId(), marker.key(), placed.id());
                                return true;
                            }
                            if (rename.contains(mouseX, adjustedMouseY)) {
                                if (this.editingMarkerId.equals(placed.id())) {
                                    this.confirmRename();
                                } else {
                                    this.startRename(selected.extension.gameId(), marker.key(), placed.id(), placed.name());
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
                                    this.cancelRename();
                                }
                                this.localDeletedMarkerIds.add(placed.id());
                                this.pendingRefreshTicks = 2;
                                return true;
                            }
                            MapEditorCustomRenderer customRenderer = MapEditorCustomRendererRegistry.get(marker.key());
                            if (customRenderer != null) {
                                // Just call it to see if it consumes the click by checking its bounds
                                int rendererHeight = customRenderer.renderProperties(null, null, placed, indentX + 10, rowY + rowHeight + 4, innerWidth - 20, (int)mouseX, (int)adjustedMouseY, true, (m, props) -> {
                                    NbtCompound nbt = new NbtCompound();
                                    nbt.putString("action", "update_properties");
                                    nbt.putString("gameId", selected.extension.gameId());
                                    nbt.putString("definitionKey", marker.key());
                                    nbt.putString("markerId", m.id());
                                    nbt.putString("properties", props.toString());
                                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.MapEditorActionPayload(nbt));
                                    this.pendingRefreshTicks = 2;
                                });
                                // If the click is within the renderer's bounds, assume it was handled
                                if (rendererHeight > 0 && mouseX >= indentX + 10 && mouseX <= indentX + 10 + innerWidth - 20 && adjustedMouseY >= rowY + rowHeight + 4 && adjustedMouseY <= rowY + rowHeight + 4 + rendererHeight) {
                                    return true;
                                }
                                if (rendererHeight > 0) {
                                    rowHeight += rendererHeight + 4;
                                }
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
        int rowY = this.listArea.y() + 10 - (int) this.scrollY;
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
                this.state.toggleMarkerVisibility(selected.extension.gameId(), selected.definition.key(), marker.id());
                return true;
            }
            if (rename.contains(mouseX, adjustedMouseY)) {
                if (this.editingMarkerId.equals(marker.id())) {
                    this.confirmRename();
                } else {
                    this.startRename(selected.extension.gameId(), selected.definition.key(), marker.id(), marker.name());
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
                    this.cancelRename();
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

        if (this.drillDownParentId != null) {
            UiLayout.Rect backBtn = new UiLayout.Rect(this.listArea.x(), rowY, 100, 20);
            UiRenderer.panel(context, backBtn.x(), backBtn.y(), backBtn.width(), backBtn.height(), UiTheme.PANEL_RAISED, UiTheme.BORDER_SUBTLE);
            context.drawText(textRenderer, Text.literal("\u25C0 Back"), backBtn.x() + 30, backBtn.y() + 6, UiTheme.TEXT, false);
            rowY += 30;
        }

        for (SessionSnapshotData.EditorMarkerDefinition marker : selected.extension.markers()) {
            boolean isChild = marker.grouping() != null && marker.grouping().parentKey() != null;
            if (this.drillDownParentId == null && isChild) continue;
            if (this.drillDownParentId != null && (!isChild || !marker.grouping().parentKey().equals(this.drillDownParentKey))) continue;

            boolean expanded = this.state.expandedMarkers.contains(marker.key());
            int headerHeight = 36;
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
            UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), expanded ? UiTheme.CARD_HOVER : UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            
            String expandIcon = expanded ? "▼" : "▶";
            context.drawText(textRenderer, Text.literal(expandIcon), row.x() + 10, row.y() + 14, UiTheme.TEXT_DIM, false);
            context.drawText(textRenderer, Text.literal(marker.displayName()), row.x() + 26, row.y() + 14, UiTheme.TEXT, false);
            
            SessionSnapshotData.EditorMarker drillDownParentMarker = this.drillDownParentId != null ? SessionSnapshotData.editorState().markers(selected.extension.gameId(), this.drillDownParentKey).stream().filter(m -> m.id().equals(this.drillDownParentId)).findFirst().orElse(null) : null;
            
            List<SessionSnapshotData.EditorMarker> placedMarkers = SessionSnapshotData.editorState().markers(selected.extension.gameId(), marker.key()).stream()
                .filter(p -> drillDownParentMarker == null || belongsToParent(p, marker.grouping(), drillDownParentMarker))
                .toList();
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
                rowY = renderMarkerEditorInline(context, textRenderer, selected.extension, marker, rowY, placedMarkers, -1, -1, false);
            }
            rowY += 10;
        }
        return rowY + (int) this.scrollY;
    }

    private int renderMarkerEditorInline(DrawContext context, TextRenderer textRenderer, SessionSnapshotData.EditorExtension extension, SessionSnapshotData.EditorMarkerDefinition definition, int startY, List<SessionSnapshotData.EditorMarker> markers, int mouseX, int adjustedMouseY, boolean clicked) {
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
            
            String prefix = index + ". ";
            int prefixW = textRenderer.getWidth(prefix);
            boolean isEditingThis = this.editingMarkerId.equals(marker.id());

            if (isEditingThis) {
                context.drawText(textRenderer, Text.literal(prefix), row.x() + 10, row.y() + 8, UiTheme.ACCENT, false);
            } else {
                context.drawText(textRenderer, Text.literal(prefix + marker.name()), row.x() + 10, row.y() + 8, UiTheme.TEXT, false);
            }
            context.drawText(textRenderer, Text.literal(locationText(marker)), row.x() + 10, row.y() + 22, UiTheme.TEXT_DIM, false);
            
            boolean isVisible = this.state.isMarkerVisible(extension.gameId(), definition.key(), marker.id());
            String toggleLabel = isVisible ? "Hide" : "Show";
            
            boolean isParent = extension.markers().stream().anyMatch(m -> m.grouping() != null && marker.definitionKey().equals(m.grouping().parentKey()));
            if (isParent && this.drillDownParentId == null) {
                renderSmallButton(context, textRenderer, row.x() + row.width() - 392, row.y() + 10, 84, "Configure...");
            }
            
            renderSmallButton(context, textRenderer, row.x() + row.width() - 302, row.y() + 10, 68, toggleLabel);
            renderSmallButton(context, textRenderer, row.x() + row.width() - 226, row.y() + 10, 68, isEditingThis ? "Save" : "Rename");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 150, row.y() + 10, 68, "Teleport");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 74, row.y() + 10, 60, "Delete");
            
            if (isEditingThis) {
                this.activeSaveButtonRect = new UiLayout.Rect(row.x() + row.width() - 226, row.y() + 10, 68, 20);
                int fieldX = row.x() + 10 + prefixW;
                int fieldY = row.y() + 4;
                int rightLimit = row.x() + row.width() - (isParent && this.drillDownParentId == null ? 398 : 308);
                int fieldW = Math.max(120, Math.min(240, rightLimit - fieldX - 8));
                if (fieldY >= this.listArea.y() - 10 && fieldY + 18 <= this.listArea.y() + this.listArea.height() + 10) {
                    this.renameField.setX(fieldX);
                    this.renameField.setY(fieldY);
                    this.renameField.setWidth(fieldW);
                    this.renameField.setHeight(18);
                    this.renameField.setVisible(true);
                    this.renameField.setFocused(true);
                    if (this.screen != null && this.screen.getFocused() != this.renameField) {
                        this.screen.setFocused(this.renameField);
                    }
                } else {
                    this.renameField.setX(-1000);
                    this.renameField.setVisible(false);
                }
            }
            MapEditorCustomRenderer customRenderer = MapEditorCustomRendererRegistry.get(definition.key());
            if (customRenderer != null) {
                int rendererHeight = customRenderer.renderProperties(context, textRenderer, marker, indentX + 10, rowY + rowHeight + 4, innerWidth - 20, mouseX, adjustedMouseY, clicked, (m, props) -> {
                    NbtCompound nbt = new NbtCompound();
                    nbt.putString("action", "update_properties");
                    nbt.putString("gameId", extension.gameId());
                    nbt.putString("definitionKey", definition.key());
                    nbt.putString("markerId", m.id());
                    nbt.putString("properties", props.toString());
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.MapEditorActionPayload(nbt));
                    this.pendingRefreshTicks = 2;
                });
                if (rendererHeight > 0) {
                    rowHeight += rendererHeight + 4;
                    // Update the panel height to encompass the new content
                    UiRenderer.panel(context, row.x(), row.y(), row.width(), rowHeight, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
                    // Re-draw text since we just overwrote it with the panel
                    if (isEditingThis) {
                        context.drawText(textRenderer, Text.literal(prefix), row.x() + 10, row.y() + 8, UiTheme.ACCENT, false);
                    } else {
                        context.drawText(textRenderer, Text.literal(prefix + marker.name()), row.x() + 10, row.y() + 8, UiTheme.TEXT, false);
                    }
                    context.drawText(textRenderer, Text.literal(locationText(marker)), row.x() + 10, row.y() + 22, UiTheme.TEXT_DIM, false);
                }
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

    private int renderMarkerEditor(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel, Selected selected, int mouseX, int adjustedMouseY) {
        int rowY = panel.y() + 52 - (int) this.scrollY;
        List<SessionSnapshotData.EditorMarker> markers = SessionSnapshotData.editorState().markers(selected.extension.gameId(), selected.definition.key());
        return renderMarkerEditorInline(context, textRenderer, selected.extension, selected.definition, rowY, markers, mouseX, adjustedMouseY, false) + (int) this.scrollY;
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
        if ("team_config".equalsIgnoreCase(definition.key())) {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.frost.miniverse.client.gui.workspace.components.TeamNamePopupScreen(
                net.minecraft.client.MinecraftClient.getInstance().currentScreen,
                (name, color) -> {
                    com.google.gson.JsonObject props = new com.google.gson.JsonObject();
                    props.addProperty("color", color.getName());

                    if (this.drillDownParentId != null && definition.grouping() != null && "LOGICAL".equals(definition.grouping().type()) && definition.grouping().propertyKey() != null) {
                        props.addProperty(definition.grouping().propertyKey(), this.drillDownParentId);
                    }

                    NbtCompound nbt = new NbtCompound();
                    nbt.putString("action", "start_add");
                    nbt.putString("gameId", extension.gameId());
                    nbt.putString("definitionKey", definition.key());
                    nbt.putString("markerId", "");
                    nbt.putString("name", name);
                    nbt.putString("properties", props.toString());
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.MapEditorActionPayload(nbt));
                    this.status = "Placement mode started. Close the screen and left click a block; right click cancels.";
                }
            ));
            return;
        }

        if (this.drillDownParentId != null && definition.grouping() != null && "LOGICAL".equals(definition.grouping().type()) && definition.grouping().propertyKey() != null) {
            com.google.gson.JsonObject defaultProps = new com.google.gson.JsonObject();
            defaultProps.addProperty(definition.grouping().propertyKey(), this.drillDownParentId);
            
            NbtCompound nbt = new NbtCompound();
            nbt.putString("action", "start_add");
            nbt.putString("gameId", extension.gameId());
            nbt.putString("definitionKey", definition.key());
            nbt.putString("markerId", "");
            nbt.putString("name", "");
            nbt.putString("properties", defaultProps.toString());
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.MapEditorActionPayload(nbt));
            this.status = "Placement mode started. Close the screen and left click a block; right click cancels.";
            return;
        }

        this.sendMarkerAction("start_add", extension.gameId(), definition.key(), "");
        this.status = "Placement mode started. Close the screen and left click a block; right click cancels.";
    }

    private boolean belongsToParent(SessionSnapshotData.EditorMarker child, SessionSnapshotData.EditorMarkerGrouping grouping, SessionSnapshotData.EditorMarker parent) {
        if (grouping == null || parent == null) return true;
        if ("SPATIAL".equals(grouping.type())) {
            return isInside(child, parent);
        }
        if ("LOGICAL".equals(grouping.type())) {
            if (grouping.propertyKey() != null && child.properties() != null && child.properties().has(grouping.propertyKey())) {
                return child.properties().get(grouping.propertyKey()).getAsString().equals(parent.id());
            }
        }
        return false;
    }

    private boolean isInside(SessionSnapshotData.EditorMarker pointMarker, SessionSnapshotData.EditorMarker regionMarker) {
        if (pointMarker.points().isEmpty()) return false;
        SessionSnapshotData.EditorPoint p = pointMarker.points().getFirst();

        if (regionMarker.regions() == null) return false;
        for (SessionSnapshotData.EditorRegionPart part : regionMarker.regions()) {
            double minX = Math.min(part.min().x(), part.max().x());
            double maxX = Math.max(part.min().x(), part.max().x());
            double minY = Math.min(part.min().y(), part.max().y());
            double maxY = Math.max(part.min().y(), part.max().y());
            double minZ = Math.min(part.min().z(), part.max().z());
            double maxZ = Math.max(part.min().z(), part.max().z());
            
            if (p.x() >= minX && p.x() <= maxX &&
                p.y() >= minY && p.y() <= maxY &&
                p.z() >= minZ && p.z() <= maxZ) {
                return true;
            }
        }
        return false;
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
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.MapEditorActionPayload(nbt));
    }

    private void sendCommand(String command) {
        if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
            net.minecraft.client.MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command);
        }
    }

    private record Selected(SessionSnapshotData.EditorExtension extension, SessionSnapshotData.EditorMarkerDefinition definition) {
    }
}
