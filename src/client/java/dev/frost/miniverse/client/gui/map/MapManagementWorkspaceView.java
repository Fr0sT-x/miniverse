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

import dev.frost.miniverse.client.gui.ui.UiComponent;
import dev.frost.miniverse.client.gui.ui.UiPrimitives.UiButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MapManagementWorkspaceView implements WorkspaceView {
    private static final int CARD_GAP = 10;
    /** Badge text drawn on cards with no world template. */
    private static final String NO_WORLD_BADGE = "⚠ No World";

    private final Runnable refreshAction;
    private UiLayout.Rect listArea = new UiLayout.Rect(0, 0, 0, 0);
    private TextFieldWidget mapNameField;
    private final List<UiComponent> components = new ArrayList<>();
    private String status = "";
    /** Separate status line for import-specific feedback. */
    private String importStatus = "";

    public MapManagementWorkspaceView(Runnable refreshAction) {
        this.refreshAction = refreshAction == null ? () -> {
        } : refreshAction;
    }

    private SessionScreen screen;

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.screen = screen;
        UiLayout.Rect panel = workspace.inset(4);
        // Shift list area down to make room for two toolbar rows (void map + import world)
        this.listArea = new UiLayout.Rect(panel.x() + 12, panel.y() + 116, panel.width() - 24, panel.height() - 128);
        this.mapNameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, panel.x() + 12, panel.y() + 18, 156, 22, Text.literal("Map name"));
        this.mapNameField.setMaxLength(48);
        this.mapNameField.setText("new-map-" + System.currentTimeMillis());
        screen.addWorkspaceChild(this.mapNameField);

        // ── Row 1: Create Void Map ──────────────────────────────────────────
        UiButton createVoid = new UiButton("Create Void Map", this::createVoidMap);
        createVoid.setBounds(new UiLayout.Rect(panel.x() + 176, panel.y() + 18, 116, 22));
        this.components.add(createVoid);

        UiButton openFolder = new UiButton("Open Map Folder", () -> this.status = "Map folder: " + this.mapRootHint());
        openFolder.setBounds(new UiLayout.Rect(panel.x() + 300, panel.y() + 18, 124, 22));
        this.components.add(openFolder);

        UiButton refresh = new UiButton("Refresh", () -> {
            ThumbnailManager.invalidateAll();
            this.refreshAction.run();
        });
        refresh.setBounds(new UiLayout.Rect(panel.x() + 432, panel.y() + 18, 76, 22));
        this.components.add(refresh);

        // ── Row 2: Import World ─────────────────────────────────────────────
        UiButton importWorld = new UiButton("Import World", this::startImport);
        importWorld.setBounds(new UiLayout.Rect(panel.x() + 12, panel.y() + 50, 116, 22));
        this.components.add(importWorld);
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect panel = workspace.inset(4);
        UiRenderer.panel(context, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(panel.x() + 1, panel.y() + 1, panel.x() + panel.width() - 1, panel.y() + 80, 0x701B3428);

        if (this.mapNameField != null && this.mapNameField.getText().isBlank()) {
            context.drawText(textRenderer, Text.literal("Map name"), this.mapNameField.getX() + 6, this.mapNameField.getY() + 7, UiTheme.TEXT_DIM, false);
        }
        context.drawText(textRenderer, Text.literal("Create a temporary creative void world, then run /miniverse_map_save in that server."), panel.x() + 12, panel.y() + 46, UiTheme.TEXT_DIM, false);
        context.drawText(textRenderer, Text.literal("Import an existing Minecraft world folder directly as a map."), panel.x() + 136, panel.y() + 57, UiTheme.TEXT_DIM, false);

        this.renderCards(context, textRenderer, mouseX, mouseY);

        for (UiComponent component : this.components) {
            component.render(context, textRenderer, mouseX, mouseY, delta);
        }

        // Status lines
        if (!this.status.isBlank()) {
            context.drawText(textRenderer, Text.literal(this.status), panel.x() + 12, panel.y() + panel.height() - 30, UiTheme.TEXT_DIM, false);
        }
        if (!this.importStatus.isBlank()) {
            int color = this.importStatus.startsWith("✗") ? UiTheme.ACCENT_RED : UiTheme.TEXT_DIM;
            context.drawText(textRenderer, Text.literal(this.importStatus), panel.x() + 12, panel.y() + panel.height() - 18, color, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (UiComponent component : this.components) {
            if (component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (button == 1) {
            SessionSnapshotData.MapSummary map = this.mapAt(mouseX, mouseY);
            if (map != null) {
                this.openDetails(map.id());
                return true;
            }
        }
        if (button == 0) {
            SessionSnapshotData.MapSummary map = this.mapAt(mouseX, mouseY);
            if (map != null) {
                this.openDetails(map.id());
                return true;
            }
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
        int cardWidth = (this.listArea.width() - CARD_GAP * (columns - 1)) / columns;
        int imgHeight = (int)(cardWidth * 9.0 / 16.0);
        int cardHeight = imgHeight + 42;

        for (int i = 0; i < maps.size(); i++) {
            SessionSnapshotData.MapSummary map = maps.get(i);
            UiLayout.Rect card = UiLayout.grid(this.listArea, i, columns, cardHeight, CARD_GAP);
            boolean hovered = card.contains(mouseX, mouseY);
            boolean hasWorld = map.hasWorld();

            // Cards without a world get a grey border; cards with a world get the normal green accent.
            int accentColor = hasWorld ? UiTheme.ACCENT_GREEN : 0xFF888888;
            UiRenderer.card(context, card.x(), card.y(), card.width(), card.height(), hovered ? 1.0F : 0.0F, accentColor);

            // Thumbnail
            net.minecraft.util.Identifier thumb = dev.frost.miniverse.client.gui.map.ThumbnailManager.getThumbnail(map);
            context.drawTexture(thumb, card.x() + 3, card.y() + 2, 0, 0, card.width() - 5, imgHeight, card.width() - 5, imgHeight);

            // "⚠ No World" badge in top-right corner of the thumbnail area
            if (!hasWorld) {
                int badgeColor = 0xFFFFAA00; // amber
                context.drawText(textRenderer, Text.literal(NO_WORLD_BADGE), card.x() + card.width() - textRenderer.getWidth(NO_WORLD_BADGE) - 7, card.y() + 6, badgeColor, false);
            }

            // Map name + supported modes row
            int textY = card.y() + imgHeight + 8;
            context.drawText(textRenderer, Text.literal(map.name()), card.x() + 10, textY, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(map.gamemodes().size() + " Supported Modes"), card.x() + 10, textY + 14, UiTheme.TEXT_MUTED, false);
        }
    }

    private SessionSnapshotData.MapSummary mapAt(double mouseX, double mouseY) {
        List<SessionSnapshotData.MapSummary> maps = SessionSnapshotData.maps();
        if (maps.isEmpty() || !this.listArea.contains((int) mouseX, (int) mouseY)) {
            return null;
        }
        int columns = this.listArea.width() >= 720 ? 3 : this.listArea.width() >= 460 ? 2 : 1;
        int cardWidth = (this.listArea.width() - CARD_GAP * (columns - 1)) / columns;
        int imgHeight = (int)(cardWidth * 9.0 / 16.0);
        int cardHeight = imgHeight + 42;

        for (int i = 0; i < maps.size(); i++) {
            UiLayout.Rect card = UiLayout.grid(this.listArea, i, columns, cardHeight, CARD_GAP);
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

    private void startImport() {
        this.importStatus = "Opening file picker...";
        MapImportHelper.openFolderPicker(
            this::onWorldSelected,
            this::onImportError
        );
    }

    private void onWorldSelected(File worldFolder) {
        String displayName = worldFolder.getName();
        this.importStatus = "Importing '" + displayName + "'...";
        ClientPlayNetworking.send(new NetworkConstants.ImportWorldMapPayload(
            worldFolder.getAbsolutePath(),
            displayName
        ));
    }

    private void onImportError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            // User cancelled — clear the status silently
            this.importStatus = "";
        } else {
            this.importStatus = "✗ " + errorMessage;
        }
    }

    private void openDetails(String mapId) {
        if (mapId == null || mapId.isBlank()) {
            this.status = "Invalid map selection.";
            return;
        }
        if (this.screen != null) {
            this.screen.openMapDetails(mapId);
            this.status = "Opened details for " + mapId + ".";
        }
    }
}
