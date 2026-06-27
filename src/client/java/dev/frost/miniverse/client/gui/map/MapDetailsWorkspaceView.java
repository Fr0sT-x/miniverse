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
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import dev.frost.miniverse.client.gui.ui.UiComponent;
import dev.frost.miniverse.client.gui.ui.UiPrimitives.UiButton;

import java.util.ArrayList;
import java.util.List;

public class MapDetailsWorkspaceView implements WorkspaceView {
    private String mapId;
    private final Runnable backAction;
    private final List<UiComponent> components = new ArrayList<>();
    private TextFieldWidget renameField;
    private boolean confirmingDelete = false;

    public MapDetailsWorkspaceView(String mapId, Runnable backAction) {
        this.mapId = mapId;
        this.backAction = backAction;
    }

    @Override
    public String title() {
        return "Map Details";
    }

    @Override
    public String subtitle() {
        return "Manage map and gamemodes";
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        UiLayout.Rect panel = workspace.inset(4);
        this.components.clear();
        this.confirmingDelete = false;
        
        UiButton back = new UiButton("Back", this.backAction);
        back.setBounds(new UiLayout.Rect(panel.x() + 12, panel.y() + 12, 60, 20));
        this.components.add(back);

        SessionSnapshotData.MapSummary map = SessionSnapshotData.maps().stream().filter(m -> m.id().equals(this.mapId)).findFirst().orElse(null);
        if (map == null) return;

        int actionsX = panel.x() + panel.width() - 170;
        int actionsY = panel.y() + 12;

        UiButton launch = new UiButton("Launch Map Editor", () -> {
            ClientPlayNetworking.send(new NetworkConstants.EditMapPayload(this.mapId));
            screen.close();
        });
        launch.setBounds(new UiLayout.Rect(actionsX, actionsY, 156, 20));
        this.components.add(launch);

        actionsY += 28;
        UiButton delete = new UiButton("Delete Map", () -> {
            if (!this.confirmingDelete) {
                this.confirmingDelete = true;
            } else {
                ClientPlayNetworking.send(new NetworkConstants.DeleteMapPayload(this.mapId));
                this.backAction.run();
            }
        }) {
            @Override
            public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
                this.setLabel(MapDetailsWorkspaceView.this.confirmingDelete ? "Click to Confirm Delete" : "Delete Map");
                super.render(context, textRenderer, mouseX, mouseY, delta);
            }
        }.accent(UiTheme.ACCENT_RED);
        delete.setBounds(new UiLayout.Rect(actionsX, actionsY, 156, 20));
        this.components.add(delete);

        actionsY += 36;
        this.renameField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, actionsX, actionsY, 156, 20, Text.literal("New name"));
        this.renameField.setMaxLength(48);
        this.renameField.setText(map.name());
        screen.addWorkspaceChild(this.renameField);

        actionsY += 24;
        UiButton rename = new UiButton("Rename Map", () -> {
            String newName = this.renameField.getText();
            if (!newName.isBlank() && !newName.equals(map.name())) {
                ClientPlayNetworking.send(new NetworkConstants.RenameMapPayload(this.mapId, newName));
                this.mapId = newName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_\\-]+", "_").replaceAll("_+", "_");
            }
        });
        rename.setBounds(new UiLayout.Rect(actionsX, actionsY, 156, 20));
        this.components.add(rename);
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect panel = workspace.inset(4);
        UiRenderer.panel(context, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        
        for (UiComponent component : this.components) {
            component.render(context, textRenderer, mouseX, mouseY, delta);
        }
        
        SessionSnapshotData.MapSummary map = SessionSnapshotData.maps().stream().filter(m -> m.id().equals(this.mapId)).findFirst().orElse(null);
        if (map != null) {
            int textX = panel.x() + 16;
            
            // Background panel for details to make it look prettier
            int detailsWidth = panel.width() - 200;
            UiRenderer.panel(context, textX - 4, panel.y() + 40, detailsWidth, panel.height() - 56, UiTheme.BACKGROUND, UiTheme.BORDER_SUBTLE);
            
            context.drawText(textRenderer, Text.literal(map.name()), textX, panel.y() + 48, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal("ID: " + map.id()), textX, panel.y() + 62, UiTheme.TEXT_DIM, false);
            context.drawText(textRenderer, Text.literal("Folder: " + map.folder()), textX, panel.y() + 76, UiTheme.TEXT_DIM, false);
            
            int y = panel.y() + 102;
            context.drawText(textRenderer, Text.literal("Gamemodes: " + (map.gamemodes().isEmpty() ? "None configured" : String.join(", ", map.gamemodes()))), textX, y, UiTheme.TEXT, false);
            
            y += 20;
            for (SessionSnapshotData.MapValidation validation : map.validations()) {
                int color = validation.valid() ? UiTheme.SUCCESS : UiTheme.ACCENT_RED;
                context.drawText(textRenderer, Text.literal("Validation [" + validation.game() + "]: " + (validation.valid() ? "Valid" : "Invalid")), textX, y, color, false);
                y += 12;
                for (String error : validation.errors()) {
                    context.drawText(textRenderer, Text.literal("- " + error), textX + 10, y, UiTheme.ACCENT_RED, false);
                    y += 12;
                }
            }

            // Warning section shown when the map has no world template
            if (!map.hasWorld()) {
                y += 8;
                context.drawText(textRenderer, Text.literal("⚠ This map has no world template yet."), textX, y, 0xFFFFAA00, false);
                y += 14;
                context.drawText(textRenderer, Text.literal("The map editor can still be opened to build a world from scratch,"), textX + 10, y, UiTheme.TEXT_DIM, false);
                y += 12;
                context.drawText(textRenderer, Text.literal("but this map cannot be launched as a game session until a world is present."), textX + 10, y, UiTheme.TEXT_DIM, false);
                y += 12;
                context.drawText(textRenderer, Text.literal("To add a world: use the \"Import World\" button on the Maps tab."), textX + 10, y, UiTheme.TEXT_DIM, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.confirmingDelete) {
            // Check if clicking outside the delete button to cancel
            boolean clickedButton = false;
            for (UiComponent component : this.components) {
                if (component.mouseClicked(mouseX, mouseY, button)) {
                    clickedButton = true;
                    break;
                }
            }
            if (!clickedButton) {
                this.confirmingDelete = false; // Cancel deletion if clicked elsewhere
            }
            return clickedButton;
        }

        for (UiComponent component : this.components) {
            if (component.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }
}
