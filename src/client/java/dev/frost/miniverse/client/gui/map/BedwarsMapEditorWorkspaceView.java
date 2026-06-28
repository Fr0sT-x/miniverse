package dev.frost.miniverse.client.gui.map;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiComponent;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiPrimitives.UiButton;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.WorkspaceView;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class BedwarsMapEditorWorkspaceView implements WorkspaceView {
    private final MapEditorState state;
    private final Runnable refreshAction;
    private final List<UiComponent> components = new ArrayList<>();

    private String selectedTeamId = "";
    private String editingMarkerId = "";
    private double scrollY = 0;
    private double maxScrollY = 0;
    private int pendingRefreshTicks = -1;

    private UiLayout.Rect listArea = new UiLayout.Rect(0, 0, 0, 0);
    private SessionScreen screen;
    private TextFieldWidget renameField;
    private String status = "";

    public BedwarsMapEditorWorkspaceView(MapEditorState state, Runnable refreshAction) {
        this.state = state;
        this.refreshAction = refreshAction == null ? () -> {} : refreshAction;
        this.state.selectedGameId = BedwarsDefinition.ID;
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.screen = screen;
        UiLayout.Rect panel = workspace.inset(4);
        this.components.clear();
        this.listArea = new UiLayout.Rect(panel.x() + 12, panel.y() + 86, panel.width() - 24, panel.height() - 98);

        int rightX = panel.x() + panel.width() - 12;

        UiButton saveQuit = new UiButton("Save & Quit", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_save_and_quit");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Save and Quit?"), Text.literal("This will save all changes and exit the map editor.")));
        });
        rightX -= 100;
        saveQuit.setBounds(new UiLayout.Rect(rightX, panel.y() + 14, 100, 20));
        this.components.add(saveQuit);

        UiButton refresh = new UiButton("Refresh", this.refreshAction);
        rightX -= 84;
        refresh.setBounds(new UiLayout.Rect(rightX, panel.y() + 14, 80, 20));
        this.components.add(refresh);

        UiButton limitsBtn = new UiButton("Gen Limits", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.frost.miniverse.client.gui.workspace.components.GeneratorLimitPopupScreen(this.screen, limitStr -> {
                try {
                    int limit = Integer.parseInt(limitStr);
                    com.google.gson.JsonObject props = new com.google.gson.JsonObject();
                    props.addProperty("limit", limit);
                    this.sendMarkerAction("add_logical", BedwarsDefinition.ID, "global_limits", "", "Global Limits", props.toString());
                    this.pendingRefreshTicks = 5;
                } catch (NumberFormatException ignored) {}
            }));
        });
        rightX -= 84;
        limitsBtn.setBounds(new UiLayout.Rect(rightX, panel.y() + 14, 80, 20));
        this.components.add(limitsBtn);

        if (this.selectedTeamId.isBlank()) {
            UiButton addBtn = new UiButton("Add New Team", () -> {
                net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.frost.miniverse.client.gui.workspace.components.TeamNamePopupScreen(this.screen, (name, color) -> {
                    com.google.gson.JsonObject props = new com.google.gson.JsonObject();
                    props.addProperty("color", color.getName());
                    this.sendMarkerAction("add_logical", BedwarsDefinition.ID, "team_config", "", name, props.toString());
                    this.pendingRefreshTicks = 5;
                }));
            });
            addBtn.setBounds(new UiLayout.Rect(panel.x() + 12, panel.y() + 48, 130, 20));
            this.components.add(addBtn);
        } else {
            UiButton backBtn = new UiButton("< Back to Teams", () -> {
                this.selectedTeamId = "";
                this.status = "";
                if (this.screen != null) this.screen.openWorkspaceView(this);
            });
            backBtn.setBounds(new UiLayout.Rect(panel.x() + 12, panel.y() + 48, 130, 20));
            this.components.add(backBtn);
        }

        this.renameField = new TextFieldWidget(net.minecraft.client.MinecraftClient.getInstance().textRenderer, -1000, -1000, 150, 20, Text.literal("New name"));
        this.renameField.setMaxLength(48);
        screen.addWorkspaceChild(this.renameField);
        this.editingMarkerId = "";
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        if (this.pendingRefreshTicks > 0) {
            this.pendingRefreshTicks--;
        } else if (this.pendingRefreshTicks == 0) {
            this.pendingRefreshTicks = -1;
            this.refreshAction.run();
        }

        UiLayout.Rect panel = workspace.inset(4);
        UiRenderer.panel(context, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(panel.x() + 1, panel.y() + 1, panel.x() + panel.width() - 1, panel.y() + 76, 0x70283A32);

        int crumbX = panel.x() + 12;
        int crumbY = panel.y() + 20;

        String rootStr = "Bed Wars Editor";
        context.drawText(textRenderer, Text.literal(rootStr), crumbX, crumbY, UiTheme.TEXT, false);
        crumbX += textRenderer.getWidth(rootStr);

        if (!this.selectedTeamId.isBlank()) {
            SessionSnapshotData.EditorMarker team = getTeam(this.selectedTeamId);
            String teamStr = team != null ? team.name() : "Unknown Team";
            String arrowStr = " > ";
            context.drawText(textRenderer, Text.literal(arrowStr), crumbX, crumbY, UiTheme.TEXT_DIM, false);
            crumbX += textRenderer.getWidth(arrowStr);
            context.drawText(textRenderer, Text.literal(teamStr), crumbX, crumbY, UiTheme.TEXT, false);

            if (this.renameField != null && this.renameField.getText().isBlank()) {
                context.drawText(textRenderer, Text.literal("New name"), this.renameField.getX() + 6, this.renameField.getY() + 6, UiTheme.TEXT_DIM, false);
            }
        }

        context.enableScissor(this.listArea.x(), this.listArea.y(), this.listArea.x() + this.listArea.width(), this.listArea.y() + this.listArea.height());

        int contentBottom = this.listArea.y();
        if (this.selectedTeamId.isBlank()) {
            contentBottom = renderGlobalList(context, textRenderer, panel);
        } else {
            contentBottom = renderTeamDetail(context, textRenderer, panel);
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
        if (button != 0) return false;

        for (UiComponent component : this.components) {
            if (component.mouseClicked(mouseX, mouseY, button)) return true;
        }

        if (this.selectedTeamId.isBlank()) {
            return handleGlobalListClick(mouseX, adjustedMouseY);
        } else {
            return handleTeamDetailClick(mouseX, adjustedMouseY);
        }
    }

    private int renderGlobalList(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        int rowY = this.listArea.y() + 10 - (int) this.scrollY;
        
        // Render Teams
        context.drawText(textRenderer, Text.literal("Teams"), this.listArea.x() + 10, rowY, UiTheme.TEXT, false);
        renderSmallButton(context, textRenderer, this.listArea.x() + this.listArea.width() - 84, rowY - 5, 74, "Delete All");
        rowY += 16;
        
        List<SessionSnapshotData.EditorMarker> teams = SessionSnapshotData.editorState().markers(BedwarsDefinition.ID, "team_config");
        if (teams.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No teams configured."), this.listArea.x() + 20, rowY, UiTheme.TEXT_DIM, false);
            rowY += 20;
        } else {
            for (SessionSnapshotData.EditorMarker team : teams) {
                UiLayout.Rect row = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 30);
                UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
                context.drawText(textRenderer, Text.literal(team.name()), row.x() + 10, row.y() + 11, UiTheme.TEXT, false);

                renderSmallButton(context, textRenderer, row.x() + row.width() - 74, row.y() + 5, 60, "Delete");

                rowY += 34;
            }
        }

        rowY += 10;
        
        // Render Globals
        rowY = renderGlobalSection(context, textRenderer, "Diamond Generators", "mid_diamond_gen", rowY);
        rowY = renderGlobalSection(context, textRenderer, "Emerald Generators", "mid_emerald_gen", rowY);
        rowY = renderGlobalSection(context, textRenderer, "Spectator Spawn", "spectator_spawn", rowY);
        
        return rowY + (int) this.scrollY;
    }

    private int renderGlobalSection(DrawContext context, TextRenderer textRenderer, String title, String definitionKey, int rowY) {
        int headerHeight = 36;
        UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
        UiRenderer.panel(context, headerRow.x(), headerRow.y(), headerRow.width(), headerRow.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal(title), headerRow.x() + 10, headerRow.y() + 14, UiTheme.TEXT, false);

        List<SessionSnapshotData.EditorMarker> points = SessionSnapshotData.editorState().markers(BedwarsDefinition.ID, definitionKey);
        String statsStr = "Count: " + points.size() + " ";
        int textEnd = headerRow.x() + headerRow.width() - net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(statsStr) - 14;
        
        renderSmallButton(context, textRenderer, textEnd - 36, headerRow.y() + 8, 30, "+");
        context.drawText(textRenderer, Text.literal(statsStr), textEnd, headerRow.y() + 14, UiTheme.TEXT_DIM, false);
        rowY += headerHeight;

        for (SessionSnapshotData.EditorMarker marker : points) {
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 30);
            UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            context.drawText(textRenderer, Text.literal(marker.name()), row.x() + 10, row.y() + 11, UiTheme.TEXT, false);
            
            renderSmallButton(context, textRenderer, row.x() + row.width() - 150, row.y() + 5, 68, "Teleport");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 74, row.y() + 5, 60, "Delete");
            rowY += 34;
        }
        return rowY + 10;
    }

    private boolean handleGlobalListClick(double mouseX, int adjustedMouseY) {
        int rowY = this.listArea.y() + 10;
        UiLayout.Rect deleteAll = new UiLayout.Rect(this.listArea.x() + this.listArea.width() - 84, rowY - 5, 74, 20);
        if (deleteAll.contains(mouseX, adjustedMouseY)) {
            this.sendMarkerAction("delete_all", BedwarsDefinition.ID, "all", "all");
            this.pendingRefreshTicks = 5;
            return true;
        }

        rowY += 16;
        List<SessionSnapshotData.EditorMarker> teams = SessionSnapshotData.editorState().markers(BedwarsDefinition.ID, "team_config");
        
        for (SessionSnapshotData.EditorMarker team : teams) {
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 30);
            UiLayout.Rect delete = new UiLayout.Rect(row.x() + row.width() - 74, row.y() + 5, 60, 20);

            if (delete.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("delete", BedwarsDefinition.ID, "team_config", team.id());
                this.editingMarkerId = "";
                this.pendingRefreshTicks = 5;
                return true;
            }

            if (row.contains(mouseX, adjustedMouseY)) {
                this.selectedTeamId = team.id();
                if (this.screen != null) this.screen.openWorkspaceView(this);
                return true;
            }
            rowY += 34;
        }
        rowY += 10;

        int[] yRef = new int[]{rowY};
        if (handleGlobalSectionClick(mouseX, adjustedMouseY, "mid_diamond_gen", yRef)) return true;
        if (handleGlobalSectionClick(mouseX, adjustedMouseY, "mid_emerald_gen", yRef)) return true;
        if (handleGlobalSectionClick(mouseX, adjustedMouseY, "spectator_spawn", yRef)) return true;

        return false;
    }

    private boolean handleGlobalSectionClick(double mouseX, int adjustedMouseY, String definitionKey, int[] yRef) {
        int rowY = yRef[0];
        int headerHeight = 36;
        UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
        
        List<SessionSnapshotData.EditorMarker> points = SessionSnapshotData.editorState().markers(BedwarsDefinition.ID, definitionKey);
        String statsStr = "Count: " + points.size() + " ";
        int textEnd = headerRow.x() + headerRow.width() - net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(statsStr) - 14;
        UiLayout.Rect addBtn = new UiLayout.Rect(textEnd - 36, headerRow.y() + 8, 30, 20);

        if (addBtn.contains(mouseX, adjustedMouseY)) {
            this.startAdd(definitionKey, null);
            return true;
        }
        rowY += headerHeight;

        for (SessionSnapshotData.EditorMarker marker : points) {
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 30);
            UiLayout.Rect teleport = new UiLayout.Rect(row.x() + row.width() - 150, row.y() + 5, 68, 20);
            UiLayout.Rect delete = new UiLayout.Rect(row.x() + row.width() - 74, row.y() + 5, 60, 20);

            if (teleport.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("teleport", BedwarsDefinition.ID, definitionKey, marker.id());
                return true;
            }
            if (delete.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("delete", BedwarsDefinition.ID, definitionKey, marker.id());
                this.pendingRefreshTicks = 5;
                return true;
            }
            rowY += 34;
        }

        yRef[0] = rowY + 10;
        return false;
    }

    private int renderTeamDetail(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        SessionSnapshotData.EditorMarker team = getTeam(this.selectedTeamId);
        if (team == null) {
            this.selectedTeamId = "";
            return this.listArea.y();
        }

        int rowY = this.listArea.y() + 10 - (int) this.scrollY;

        // Render header for the team itself
        UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 30);
        UiRenderer.panel(context, headerRow.x(), headerRow.y(), headerRow.width(), headerRow.height(), UiTheme.PANEL_SOFT, UiTheme.BORDER_STRONG);
        context.drawText(textRenderer, Text.literal("Team Details - " + team.name()), headerRow.x() + 10, headerRow.y() + 11, UiTheme.TEXT, false);
        
        renderSmallButton(context, textRenderer, headerRow.x() + headerRow.width() - 74, headerRow.y() + 5, 68, "Rename");
        if (this.editingMarkerId.equals(team.id())) {
            this.renameField.setX(headerRow.x() + headerRow.width() - 74);
            this.renameField.setY(headerRow.y() + 5);
        } else {
            this.renameField.setX(-1000);
        }

        rowY += 40;

        rowY = renderTeamSection(context, textRenderer, "Bed", "team_bed", team, rowY);
        rowY = renderTeamSection(context, textRenderer, "Spawns", "team_spawn", team, rowY);
        rowY = renderTeamSection(context, textRenderer, "Iron Generators", "team_island_iron", team, rowY);
        rowY = renderTeamSection(context, textRenderer, "Gold Generators", "team_island_gold", team, rowY);
        rowY = renderTeamSection(context, textRenderer, "Shop NPCs", "shop_npc", team, rowY);
        rowY = renderTeamSection(context, textRenderer, "Upgrade NPCs", "upgrade_npc", team, rowY);

        return rowY + (int) this.scrollY;
    }

    private int renderTeamSection(DrawContext context, TextRenderer textRenderer, String title, String definitionKey, SessionSnapshotData.EditorMarker team, int rowY) {
        int headerHeight = 36;
        UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
        UiRenderer.panel(context, headerRow.x(), headerRow.y(), headerRow.width(), headerRow.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal(title), headerRow.x() + 10, headerRow.y() + 14, UiTheme.TEXT, false);

        List<SessionSnapshotData.EditorMarker> allPoints = SessionSnapshotData.editorState().markers(BedwarsDefinition.ID, definitionKey);
        List<SessionSnapshotData.EditorMarker> teamPoints = allPoints.stream()
            .filter(p -> p.properties() != null && p.properties().has("teamId") && p.properties().get("teamId").getAsString().equals(team.id()))
            .collect(Collectors.toList());

        String statsStr = "Count: " + teamPoints.size() + " ";
        int textEnd = headerRow.x() + headerRow.width() - net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(statsStr) - 14;
        
        renderSmallButton(context, textRenderer, textEnd - 36, headerRow.y() + 8, 30, "+");
        context.drawText(textRenderer, Text.literal(statsStr), textEnd, headerRow.y() + 14, UiTheme.TEXT_DIM, false);
        rowY += headerHeight;

        for (SessionSnapshotData.EditorMarker marker : teamPoints) {
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 30);
            UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            context.drawText(textRenderer, Text.literal(marker.name()), row.x() + 10, row.y() + 11, UiTheme.TEXT, false);

            renderSmallButton(context, textRenderer, row.x() + row.width() - 150, row.y() + 5, 68, "Teleport");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 74, row.y() + 5, 60, "Delete");
            rowY += 34;
        }

        return rowY + 10;
    }

    private boolean handleTeamDetailClick(double mouseX, int adjustedMouseY) {
        SessionSnapshotData.EditorMarker team = getTeam(this.selectedTeamId);
        if (team == null) return false;

        int rowY = this.listArea.y() + 10;
        UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 30);
        UiLayout.Rect rename = new UiLayout.Rect(headerRow.x() + headerRow.width() - 74, headerRow.y() + 5, 68, 20);

        if (rename.contains(mouseX, adjustedMouseY)) {
            if (this.editingMarkerId.equals(team.id())) {
                if (!this.renameField.getText().trim().isBlank()) {
                    this.sendMarkerAction("rename", BedwarsDefinition.ID, "team_config", team.id(), this.renameField.getText().trim());
                }
                this.editingMarkerId = "";
                this.renameField.setX(-1000);
                this.pendingRefreshTicks = 5;
            } else {
                this.editingMarkerId = team.id();
                this.renameField.setText(team.name());
            }
            return true;
        }
        
        rowY += 40;
        int[] yRef = new int[]{rowY};

        if (handleTeamSectionClick(mouseX, adjustedMouseY, "team_bed", team, yRef)) return true;
        if (handleTeamSectionClick(mouseX, adjustedMouseY, "team_spawn", team, yRef)) return true;
        if (handleTeamSectionClick(mouseX, adjustedMouseY, "team_island_iron", team, yRef)) return true;
        if (handleTeamSectionClick(mouseX, adjustedMouseY, "team_island_gold", team, yRef)) return true;
        if (handleTeamSectionClick(mouseX, adjustedMouseY, "shop_npc", team, yRef)) return true;
        if (handleTeamSectionClick(mouseX, adjustedMouseY, "upgrade_npc", team, yRef)) return true;

        return false;
    }

    private boolean handleTeamSectionClick(double mouseX, int adjustedMouseY, String definitionKey, SessionSnapshotData.EditorMarker team, int[] yRef) {
        int rowY = yRef[0];
        int headerHeight = 36;
        UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);

        List<SessionSnapshotData.EditorMarker> allPoints = SessionSnapshotData.editorState().markers(BedwarsDefinition.ID, definitionKey);
        List<SessionSnapshotData.EditorMarker> teamPoints = allPoints.stream()
            .filter(p -> p.properties() != null && p.properties().has("teamId") && p.properties().get("teamId").getAsString().equals(team.id()))
            .collect(Collectors.toList());

        String statsStr = "Count: " + teamPoints.size() + " ";
        int textEnd = headerRow.x() + headerRow.width() - net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(statsStr) - 14;
        UiLayout.Rect addBtn = new UiLayout.Rect(textEnd - 36, headerRow.y() + 8, 30, 20);

        if (addBtn.contains(mouseX, adjustedMouseY)) {
            com.google.gson.JsonObject props = new com.google.gson.JsonObject();
            props.addProperty("teamId", team.id());
            this.startAdd(definitionKey, props);
            return true;
        }

        rowY += headerHeight;

        for (SessionSnapshotData.EditorMarker marker : teamPoints) {
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 30);
            UiLayout.Rect teleport = new UiLayout.Rect(row.x() + row.width() - 150, row.y() + 5, 68, 20);
            UiLayout.Rect delete = new UiLayout.Rect(row.x() + row.width() - 74, row.y() + 5, 60, 20);

            if (teleport.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("teleport", BedwarsDefinition.ID, definitionKey, marker.id());
                return true;
            }
            if (delete.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("delete", BedwarsDefinition.ID, definitionKey, marker.id());
                this.pendingRefreshTicks = 5;
                return true;
            }
            rowY += 34;
        }

        yRef[0] = rowY + 10;
        return false;
    }

    private SessionSnapshotData.EditorMarker getTeam(String teamId) {
        if (teamId == null || teamId.isBlank()) return null;
        return SessionSnapshotData.editorState().markers(BedwarsDefinition.ID, "team_config").stream()
            .filter(m -> m.id().equals(teamId))
            .findFirst().orElse(null);
    }

    @Override
    public String title() { return "Bed Wars Editor"; }

    @Override
    public String subtitle() { return this.selectedTeamId.isBlank() ? "Team & Map Management" : "Team Details"; }

    private void startAdd(String definitionKey, com.google.gson.JsonObject defaultProperties) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", "start_add");
        nbt.putString("gameId", BedwarsDefinition.ID);
        nbt.putString("definitionKey", definitionKey);
        nbt.putString("markerId", "");
        nbt.putString("name", "");
        if (defaultProperties != null) {
            nbt.putString("properties", defaultProperties.toString());
        }
        ClientPlayNetworking.send(new NetworkConstants.MapEditorActionPayload(nbt));
        this.status = "Placement mode started. Close the screen and left click a block; right click cancels.";
    }

    private void sendMarkerAction(String action, String gameId, String definitionKey, String markerId) {
        this.sendMarkerAction(action, gameId, definitionKey, markerId, "", "{}");
    }

    private void sendMarkerAction(String action, String gameId, String definitionKey, String markerId, String name) {
        this.sendMarkerAction(action, gameId, definitionKey, markerId, name, "{}");
    }

    private void sendMarkerAction(String action, String gameId, String definitionKey, String markerId, String name, String properties) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", action);
        nbt.putString("gameId", gameId);
        nbt.putString("definitionKey", definitionKey);
        nbt.putString("markerId", markerId == null ? "" : markerId);
        nbt.putString("name", name == null ? "" : name);
        nbt.putString("properties", properties == null ? "{}" : properties);
        ClientPlayNetworking.send(new NetworkConstants.MapEditorActionPayload(nbt));
    }

    private void sendCommand(String command) {
        if (net.minecraft.client.MinecraftClient.getInstance().player != null) {
            net.minecraft.client.MinecraftClient.getInstance().player.networkHandler.sendChatCommand(command);
        }
    }

    private static void renderSmallButton(DrawContext context, TextRenderer textRenderer, int x, int y, int width, String label) {
        UiRenderer.panel(context, x, y, width, 20, UiTheme.PANEL_RAISED, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal(label), x + (width - textRenderer.getWidth(label)) / 2, y + 6, UiTheme.TEXT, false);
    }
}
