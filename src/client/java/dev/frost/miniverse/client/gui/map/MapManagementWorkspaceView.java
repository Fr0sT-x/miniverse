package dev.frost.miniverse.client.gui.map;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.WorkspaceView;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class MapManagementWorkspaceView implements WorkspaceView {
    private static final int CARD_HEIGHT = 76;
    private static final int CARD_GAP = 10;

    private final Runnable refreshAction;
    private UiLayout.Rect listArea = new UiLayout.Rect(0, 0, 0, 0);
    private TextFieldWidget mapNameField;
    private String contextMapId = "";
    private int contextMenuX;
    private int contextMenuY;
    private String status = "";

    public MapManagementWorkspaceView(Runnable refreshAction) {
        this.refreshAction = refreshAction == null ? () -> {
        } : refreshAction;
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        UiLayout.Rect panel = workspace.inset(4);
        this.listArea = new UiLayout.Rect(panel.x() + 12, panel.y() + 88, panel.width() - 24, panel.height() - 100);
        this.mapNameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, panel.x() + 12, panel.y() + 18, 156, 22, Text.literal("Map name"));
        this.mapNameField.setMaxLength(48);
        this.mapNameField.setText("new-map-" + System.currentTimeMillis());
        screen.addWorkspaceChild(this.mapNameField);
        screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Create Void Map"), ignored -> this.createVoidMap())
            .dimensions(panel.x() + 176, panel.y() + 18, 116, 22)
            .build());
        screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Open Map Folder"), ignored -> this.status = "Map folder: " + this.mapRootHint())
            .dimensions(panel.x() + 300, panel.y() + 18, 124, 22)
            .build());
        screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Refresh"), ignored -> this.refreshAction.run())
            .dimensions(panel.x() + 432, panel.y() + 18, 76, 22)
            .build());
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect panel = workspace.inset(4);
        UiRenderer.panel(context, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(panel.x() + 1, panel.y() + 1, panel.x() + panel.width() - 1, panel.y() + 48, 0x701B3428);
        if (this.mapNameField != null && this.mapNameField.getText().isBlank()) {
            context.drawText(textRenderer, Text.literal("Map name"), this.mapNameField.getX() + 6, this.mapNameField.getY() + 7, UiTheme.TEXT_DIM, false);
        }
        context.drawText(textRenderer, Text.literal("Create a temporary creative void world, then run /miniverse_map_save in that server."), panel.x() + 12, panel.y() + 54, UiTheme.TEXT_DIM, false);
        this.renderCards(context, textRenderer, mouseX, mouseY);
        this.renderContextMenu(context, textRenderer, mouseX, mouseY);
        if (!this.status.isBlank()) {
            context.drawText(textRenderer, Text.literal(this.status), panel.x() + 12, panel.y() + panel.height() - 18, UiTheme.TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            SessionSnapshotData.MapSummary map = this.mapAt(mouseX, mouseY);
            if (map != null) {
                this.contextMapId = map.id();
                this.contextMenuX = (int) mouseX;
                this.contextMenuY = (int) mouseY;
                this.status = "Right-click menu for " + map.name() + ".";
                return true;
            }
            this.contextMapId = "";
            return false;
        }

        if (button == 0 && !this.contextMapId.isBlank()) {
            UiLayout.Rect edit = this.contextEditBounds();
            if (edit.contains(mouseX, mouseY)) {
                this.editMap(this.contextMapId);
                this.contextMapId = "";
                return true;
            }
            this.contextMapId = "";
        }
        return false;
    }

    @Override
    public String title() {
        return "Maps";
    }

    @Override
    public String subtitle() {
        return "Template maps and gamemode compatibility";
    }

    private void renderCards(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        List<SessionSnapshotData.MapSummary> maps = SessionSnapshotData.maps();
        if (maps.isEmpty()) {
            UiRenderer.panel(context, this.listArea.x(), this.listArea.y(), this.listArea.width(), 72, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            context.drawText(textRenderer, Text.literal("No maps found."), this.listArea.x() + 12, this.listArea.y() + 16, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("Expected layout: .minecraft/miniverse/maps/<map>/world + map.json + gamemodes/<game>.json"), this.listArea.x() + 12, this.listArea.y() + 34, UiTheme.TEXT_DIM, false);
            return;
        }
        int columns = this.listArea.width() >= 720 ? 3 : this.listArea.width() >= 460 ? 2 : 1;
        for (int i = 0; i < maps.size(); i++) {
            SessionSnapshotData.MapSummary map = maps.get(i);
            UiLayout.Rect card = UiLayout.grid(this.listArea, i, columns, CARD_HEIGHT, CARD_GAP);
            boolean hovered = card.contains(mouseX, mouseY);
            UiRenderer.card(context, card.x(), card.y(), card.width(), card.height(), hovered ? 1.0F : 0.0F, UiTheme.ACCENT_GREEN);
            context.fill(card.x() + 10, card.y() + 10, card.x() + 58, card.y() + 58, 0xFF223026);
            context.drawText(textRenderer, Text.literal("MAP"), card.x() + 22, card.y() + 29, UiTheme.ACCENT_GREEN, false);
            int textX = card.x() + 70;
            context.drawText(textRenderer, Text.literal(map.name()), textX, card.y() + 12, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(map.gamemodes().size() + " Supported Modes"), textX, card.y() + 28, UiTheme.TEXT_MUTED, false);
            String folder = textRenderer.trimToWidth(map.folder(), Math.max(40, card.width() - 82));
            context.drawText(textRenderer, Text.literal(folder), textX, card.y() + 46, UiTheme.TEXT_DIM, false);
        }
    }

    private void renderContextMenu(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        if (this.contextMapId.isBlank()) {
            return;
        }
        UiLayout.Rect menu = new UiLayout.Rect(this.contextMenuX, this.contextMenuY, 112, 28);
        UiLayout.Rect edit = this.contextEditBounds();
        boolean hovered = edit.contains(mouseX, mouseY);
        UiRenderer.panel(context, menu.x(), menu.y(), menu.width(), menu.height(), UiTheme.PANEL_RAISED, UiTheme.BORDER_STRONG);
        context.fill(edit.x(), edit.y(), edit.x() + edit.width(), edit.y() + edit.height(), hovered ? 0x663B5B45 : 0x22222222);
        context.drawText(textRenderer, Text.literal("Edit Map"), edit.x() + 8, edit.y() + 7, UiTheme.TEXT, false);
    }

    private UiLayout.Rect contextEditBounds() {
        return new UiLayout.Rect(this.contextMenuX + 3, this.contextMenuY + 3, 106, 22);
    }

    private SessionSnapshotData.MapSummary mapAt(double mouseX, double mouseY) {
        List<SessionSnapshotData.MapSummary> maps = SessionSnapshotData.maps();
        if (maps.isEmpty() || !this.listArea.contains(mouseX, mouseY)) {
            return null;
        }
        int columns = this.listArea.width() >= 720 ? 3 : this.listArea.width() >= 460 ? 2 : 1;
        for (int i = 0; i < maps.size(); i++) {
            UiLayout.Rect card = UiLayout.grid(this.listArea, i, columns, CARD_HEIGHT, CARD_GAP);
            if (card.contains(mouseX, mouseY)) {
                return maps.get(i);
            }
        }
        return null;
    }

    private String mapRootHint() {
        return ".minecraft/miniverse/maps";
    }

    private void createVoidMap() {
        if (this.mapNameField == null || this.mapNameField.getText().trim().isBlank()) {
            this.status = "Enter a map name first.";
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.CreateVoidMapPayload(this.mapNameField.getText().trim()));
        this.status = "Requested map editor launch.";
    }

    private void editMap(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            this.status = "Invalid map selection.";
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.EditMapPayload(mapId));
        this.status = "Requested editor launch for " + mapId + ".";
    }
}
