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
import dev.frost.miniverse.minigame.impl.duels.DuelsDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class DuelsMapEditorWorkspaceView implements WorkspaceView {
    private final MapEditorState state;
    private final Runnable refreshAction;
    private final List<UiComponent> components = new ArrayList<>();

    private String selectedArenaId = "";
    private String editingMarkerId = "";
    private double scrollY = 0;
    private double maxScrollY = 0;
    private int pendingRefreshTicks = -1;

    private UiLayout.Rect listArea = new UiLayout.Rect(0, 0, 0, 0);
    private SessionScreen screen;
    private TextFieldWidget renameField;
    private String status = "";

    public DuelsMapEditorWorkspaceView(MapEditorState state, Runnable refreshAction) {
        this.state = state;
        this.refreshAction = refreshAction == null ? () -> {} : refreshAction;
        this.state.selectedGameId = DuelsDefinition.ID;
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.screen = screen;
        UiLayout.Rect panel = workspace.inset(4);
        this.components.clear();
        this.listArea = new UiLayout.Rect(panel.x() + 12, panel.y() + 86, panel.width() - 24, panel.height() - 98);

        int rightX = panel.x() + panel.width() - 12;

        UiButton quitNoSave = new UiButton("Quit (No Save)", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_quit");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Quit without saving?"), Text.literal("All unsaved changes to this map will be lost.")));
        }).accent(UiTheme.ACCENT_RED);
        rightX -= 110;
        quitNoSave.setBounds(new UiLayout.Rect(rightX, panel.y() + 14, 110, 20));
        this.components.add(quitNoSave);

        rightX -= 104;
        UiButton saveQuit = new UiButton("Save & Quit", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_save_and_quit");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Save and Quit?"), Text.literal("This will save all changes and exit the map editor.")));
        });
        saveQuit.setBounds(new UiLayout.Rect(rightX, panel.y() + 14, 100, 20));
        this.components.add(saveQuit);

        rightX -= 96;
        UiButton saveWorld = new UiButton("Save World", () -> {
            net.minecraft.client.MinecraftClient.getInstance().setScreen(new net.minecraft.client.gui.screen.ConfirmScreen((confirmed) -> {
                if (confirmed) this.sendCommand("miniverse_map_save");
                net.minecraft.client.MinecraftClient.getInstance().setScreen(this.screen);
            }, Text.literal("Save Map?"), Text.literal("This will overwrite the current map data with your changes.")));
        });
        saveWorld.setBounds(new UiLayout.Rect(rightX, panel.y() + 14, 92, 20));
        this.components.add(saveWorld);

        rightX -= 84;
        UiButton refresh = new UiButton("Refresh", this.refreshAction);
        refresh.setBounds(new UiLayout.Rect(rightX, panel.y() + 14, 80, 20));
        this.components.add(refresh);

        if (this.selectedArenaId.isBlank()) {
            UiButton addBtn = new UiButton("Add New Arena", () -> this.startAdd(DuelsDefinition.ARENA));
            addBtn.setBounds(new UiLayout.Rect(panel.x() + 12, panel.y() + 48, 130, 20));
            this.components.add(addBtn);
        } else {
            UiButton backBtn = new UiButton("< Back to Arenas", () -> {
                this.selectedArenaId = "";
                this.status = "";
                if (this.screen != null) this.screen.openWorkspaceView(this);
            });
            backBtn.setBounds(new UiLayout.Rect(panel.x() + 12, panel.y() + 48, 130, 20));
            this.components.add(backBtn);
        }

        this.renameField = new TextFieldWidget(net.minecraft.client.MinecraftClient.getInstance().textRenderer, -1000, -1000, 150, 20, Text.literal("New marker name"));
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

        String rootStr = "Duels Editor";
        context.drawText(textRenderer, Text.literal(rootStr), crumbX, crumbY, UiTheme.TEXT, false);
        crumbX += textRenderer.getWidth(rootStr);

        if (!this.selectedArenaId.isBlank()) {
            SessionSnapshotData.EditorMarker arena = getArena(this.selectedArenaId);
            String arenaStr = arena != null ? arena.name() : "Unknown Arena";
            String arrowStr = " > ";
            context.drawText(textRenderer, Text.literal(arrowStr), crumbX, crumbY, UiTheme.TEXT_DIM, false);
            crumbX += textRenderer.getWidth(arrowStr);
            context.drawText(textRenderer, Text.literal(arenaStr), crumbX, crumbY, UiTheme.TEXT, false);
        }

        if (this.renameField != null && this.renameField.getText().isBlank()) {
            context.drawText(textRenderer, Text.literal("New marker name"), this.renameField.getX() + 6, this.renameField.getY() + 6, UiTheme.TEXT_DIM, false);
        }

        context.enableScissor(this.listArea.x(), this.listArea.y(), this.listArea.x() + this.listArea.width(), this.listArea.y() + this.listArea.height());

        int contentBottom = this.listArea.y();
        if (this.selectedArenaId.isBlank()) {
            contentBottom = renderArenaList(context, textRenderer, panel);
        } else {
            contentBottom = renderArenaDetail(context, textRenderer, panel);
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

        if (this.selectedArenaId.isBlank()) {
            return handleArenaListClick(mouseX, adjustedMouseY);
        } else {
            return handleArenaDetailClick(mouseX, adjustedMouseY);
        }
    }

    private int renderArenaList(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        int rowY = this.listArea.y() + 10 - (int) this.scrollY;
        List<SessionSnapshotData.EditorMarker> arenas = SessionSnapshotData.editorState().markers(DuelsDefinition.ID, DuelsDefinition.ARENA);

        if (arenas.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No arenas configured. Click 'Add New Arena' to start."), this.listArea.x() + 10, rowY, UiTheme.TEXT_DIM, false);
            return rowY + 20;
        }

        for (SessionSnapshotData.EditorMarker arena : arenas) {
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), 40);
            UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);

            context.drawText(textRenderer, Text.literal(arena.name()), row.x() + 10, row.y() + 8, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(locationText(arena)), row.x() + 10, row.y() + 22, UiTheme.TEXT_DIM, false);

            renderSmallButton(context, textRenderer, row.x() + row.width() - 226, row.y() + 10, 68, "Rename");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 150, row.y() + 10, 68, "Teleport");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 74, row.y() + 10, 60, "Delete");

            if (this.editingMarkerId.equals(arena.id())) {
                this.renameField.setX(row.x() + row.width() - 226);
                this.renameField.setY(row.y() + 10);
                this.renameField.render(context, 0, 0, 0);
            }

            rowY += 44;
        }

        return rowY + (int) this.scrollY;
    }

    private boolean handleArenaListClick(double mouseX, int adjustedMouseY) {
        List<SessionSnapshotData.EditorMarker> arenas = SessionSnapshotData.editorState().markers(DuelsDefinition.ID, DuelsDefinition.ARENA);
        int rowY = this.listArea.y() + 10;
        
        for (SessionSnapshotData.EditorMarker arena : arenas) {
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), 40);
            UiLayout.Rect rename = new UiLayout.Rect(row.x() + row.width() - 226, row.y() + 10, 68, 20);
            UiLayout.Rect teleport = new UiLayout.Rect(row.x() + row.width() - 150, row.y() + 10, 68, 20);
            UiLayout.Rect delete = new UiLayout.Rect(row.x() + row.width() - 74, row.y() + 10, 60, 20);

            if (rename.contains(mouseX, adjustedMouseY)) {
                if (this.editingMarkerId.equals(arena.id())) {
                    if (this.renameField.getText().trim().isBlank()) {
                        this.status = "Enter a new marker name first.";
                        return true;
                    }
                    this.sendMarkerAction("rename", DuelsDefinition.ID, DuelsDefinition.ARENA, arena.id(), this.renameField.getText().trim());
                    this.editingMarkerId = "";
                    this.renameField.setX(-1000);
                    this.pendingRefreshTicks = 5;
                } else {
                    this.editingMarkerId = arena.id();
                    this.renameField.setText(arena.name());
                }
                return true;
            }
            if (teleport.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("teleport", DuelsDefinition.ID, DuelsDefinition.ARENA, arena.id());
                return true;
            }
            if (delete.contains(mouseX, adjustedMouseY)) {
                this.sendMarkerAction("delete", DuelsDefinition.ID, DuelsDefinition.ARENA, arena.id());
                if (this.editingMarkerId.equals(arena.id())) {
                    this.editingMarkerId = "";
                    this.renameField.setX(-1000);
                }
                this.pendingRefreshTicks = 5;
                return true;
            }

            if (row.contains(mouseX, adjustedMouseY)) {
                this.selectedArenaId = arena.id();
                if (this.screen != null) this.screen.openWorkspaceView(this);
                return true;
            }

            rowY += 44;
        }
        return false;
    }

    private int renderArenaDetail(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        SessionSnapshotData.EditorMarker arena = getArena(this.selectedArenaId);
        if (arena == null) {
            this.selectedArenaId = "";
            return this.listArea.y();
        }

        int rowY = this.listArea.y() + 10 - (int) this.scrollY;

        // Arena Bounds Box
        UiLayout.Rect arenaRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), 80);
        UiRenderer.panel(context, arenaRow.x(), arenaRow.y(), arenaRow.width(), arenaRow.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal("Arena Bounds: " + arena.name()), arenaRow.x() + 10, arenaRow.y() + 8, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(locationText(arena)), arenaRow.x() + 10, arenaRow.y() + 22, UiTheme.TEXT_DIM, false);

        UiLayout.Rect toggleArena = new UiLayout.Rect(arenaRow.x() + arenaRow.width() - 302, arenaRow.y() + 10, 68, 20);
        UiLayout.Rect renameArena = new UiLayout.Rect(arenaRow.x() + arenaRow.width() - 226, arenaRow.y() + 10, 68, 20);
        UiLayout.Rect teleportArena = new UiLayout.Rect(arenaRow.x() + arenaRow.width() - 150, arenaRow.y() + 10, 68, 20);
        UiLayout.Rect deleteArena = new UiLayout.Rect(arenaRow.x() + arenaRow.width() - 74, arenaRow.y() + 10, 60, 20);

        boolean isHidden = this.state.hiddenIndividualMarkers.contains(arena.id());
        renderSmallButton(context, textRenderer, toggleArena.x(), toggleArena.y(), toggleArena.width(), isHidden ? "Show" : "Hide");
        renderSmallButton(context, textRenderer, renameArena.x(), renameArena.y(), renameArena.width(), "Rename");
        renderSmallButton(context, textRenderer, teleportArena.x(), teleportArena.y(), teleportArena.width(), "Teleport");
        renderSmallButton(context, textRenderer, deleteArena.x(), deleteArena.y(), deleteArena.width(), "Delete");

        if (this.editingMarkerId.equals(arena.id())) {
            this.renameField.setX(renameArena.x());
            this.renameField.setY(renameArena.y());
            this.renameField.render(context, 0, 0, 0);
        }

        context.drawText(textRenderer, Text.literal("Restrictions:"), arenaRow.x() + 10, arenaRow.y() + 40, UiTheme.TEXT_DIM, false);
        int cx = arenaRow.x() + 80;
        for (dev.frost.miniverse.minigame.core.region.RegionRestriction res : dev.frost.miniverse.minigame.core.region.RegionRestriction.values()) {
            boolean active = false;
            if (arena.properties() != null && arena.properties().has("restrictions") && arena.properties().get("restrictions").isJsonArray()) {
                for (com.google.gson.JsonElement e : arena.properties().getAsJsonArray("restrictions")) {
                    if (e.getAsString().equals(res.name())) active = true;
                }
            }
            String label = (active ? "[X] " : "[ ] ") + res.name();
            context.drawText(textRenderer, Text.literal(label), cx, arenaRow.y() + 40, active ? UiTheme.TEXT : UiTheme.TEXT_DIM, false);
            cx += textRenderer.getWidth(label) + 12;
        }

        context.drawText(textRenderer, Text.literal("Supports Types:"), arenaRow.x() + 10, arenaRow.y() + 58, UiTheme.TEXT_DIM, false);
        int dx = arenaRow.x() + 90;
        for (dev.frost.miniverse.minigame.impl.duels.DuelType type : SessionSnapshotData.duelTypes()) {
            boolean active = false;
            if (arena.properties() != null && arena.properties().has("supported_duel_types") && arena.properties().get("supported_duel_types").isJsonArray()) {
                for (com.google.gson.JsonElement e : arena.properties().getAsJsonArray("supported_duel_types")) {
                    if (e.getAsString().equals(type.id())) active = true;
                }
            }
            String label = (active ? "[X] " : "[ ] ") + type.name();
            context.drawText(textRenderer, Text.literal(label), dx, arenaRow.y() + 58, active ? UiTheme.TEXT : UiTheme.TEXT_DIM, false);
            dx += textRenderer.getWidth(label) + 12;
        }

        rowY += 90;

        // Spawn Points
        rowY = renderSpawnSection(context, textRenderer, "Player 1 Spawns", DuelsDefinition.PLAYER_1_SPAWN, arena, rowY);
        rowY = renderSpawnSection(context, textRenderer, "Player 2 Spawns", DuelsDefinition.PLAYER_2_SPAWN, arena, rowY);
        rowY = renderSpawnSection(context, textRenderer, "Spectator Spawns", DuelsDefinition.SPECTATOR_SPAWN, arena, rowY);

        return rowY + (int) this.scrollY;
    }

    private int renderSpawnSection(DrawContext context, TextRenderer textRenderer, String title, String definitionKey, SessionSnapshotData.EditorMarker arena, int rowY) {
        int headerHeight = 36;
        UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
        UiRenderer.panel(context, headerRow.x(), headerRow.y(), headerRow.width(), headerRow.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        
        context.drawText(textRenderer, Text.literal(title), headerRow.x() + 10, headerRow.y() + 14, UiTheme.TEXT, false);

        List<SessionSnapshotData.EditorMarker> allPoints = SessionSnapshotData.editorState().markers(DuelsDefinition.ID, definitionKey);
        List<SessionSnapshotData.EditorMarker> arenaPoints = allPoints.stream()
            .filter(p -> isInside(p, arena))
            .collect(Collectors.toList());

        String statsStr = "Placed in Arena: " + arenaPoints.size() + " ";
        int textEnd = headerRow.x() + headerRow.width() - textRenderer.getWidth(statsStr) - 14;
        
        renderSmallButton(context, textRenderer, textEnd - 36, headerRow.y() + 8, 30, "+");
        context.drawText(textRenderer, Text.literal(statsStr), textEnd, headerRow.y() + 14, UiTheme.TEXT_DIM, false);
        
        rowY += headerHeight;

        if (arenaPoints.isEmpty()) {
            UiRenderer.panel(context, this.listArea.x() + 20, rowY, this.listArea.width() - 20, 40, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            context.drawText(textRenderer, Text.literal("No spawns placed inside this arena bounds."), this.listArea.x() + 30, rowY + 16, UiTheme.TEXT_DIM, false);
            return rowY + 50;
        }

        int index = 1;
        for (SessionSnapshotData.EditorMarker marker : arenaPoints) {
            UiLayout.Rect row = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 40);
            UiRenderer.panel(context, row.x(), row.y(), row.width(), row.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
            
            context.drawText(textRenderer, Text.literal(index + ". " + marker.name()), row.x() + 10, row.y() + 8, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(locationText(marker)), row.x() + 10, row.y() + 22, UiTheme.TEXT_DIM, false);

            renderSmallButton(context, textRenderer, row.x() + row.width() - 150, row.y() + 10, 68, "Teleport");
            renderSmallButton(context, textRenderer, row.x() + row.width() - 74, row.y() + 10, 60, "Delete");

            rowY += 44;
            index++;
        }

        return rowY + 10;
    }

    private boolean handleArenaDetailClick(double mouseX, int adjustedMouseY) {
        SessionSnapshotData.EditorMarker arena = getArena(this.selectedArenaId);
        if (arena == null) return false;

        int[] yRef = new int[]{this.listArea.y() + 10};
        UiLayout.Rect arenaRow = new UiLayout.Rect(this.listArea.x(), yRef[0], this.listArea.width(), 60);
        UiLayout.Rect toggleArena = new UiLayout.Rect(arenaRow.x() + arenaRow.width() - 302, arenaRow.y() + 10, 68, 20);
        UiLayout.Rect renameArena = new UiLayout.Rect(arenaRow.x() + arenaRow.width() - 226, arenaRow.y() + 10, 68, 20);
        UiLayout.Rect teleportArena = new UiLayout.Rect(arenaRow.x() + arenaRow.width() - 150, arenaRow.y() + 10, 68, 20);
        UiLayout.Rect deleteArena = new UiLayout.Rect(arenaRow.x() + arenaRow.width() - 74, arenaRow.y() + 10, 60, 20);

        if (toggleArena.contains(mouseX, adjustedMouseY)) {
            if (!this.state.hiddenIndividualMarkers.remove(arena.id())) {
                this.state.hiddenIndividualMarkers.add(arena.id());
            } else {
                this.state.enableOverlay(DuelsDefinition.ID, DuelsDefinition.ARENA);
            }
            return true;
        }
        if (renameArena.contains(mouseX, adjustedMouseY)) {
            if (this.editingMarkerId.equals(arena.id())) {
                if (this.renameField.getText().trim().isBlank()) {
                    this.status = "Enter a new marker name first.";
                    return true;
                }
                this.sendMarkerAction("rename", DuelsDefinition.ID, DuelsDefinition.ARENA, arena.id(), this.renameField.getText().trim());
                this.editingMarkerId = "";
                this.renameField.setX(-1000);
                this.pendingRefreshTicks = 5;
            } else {
                this.editingMarkerId = arena.id();
                this.renameField.setText(arena.name());
            }
            return true;
        }
        if (teleportArena.contains(mouseX, adjustedMouseY)) {
            this.sendMarkerAction("teleport", DuelsDefinition.ID, DuelsDefinition.ARENA, arena.id());
            return true;
        }
        if (deleteArena.contains(mouseX, adjustedMouseY)) {
            this.sendMarkerAction("delete", DuelsDefinition.ID, DuelsDefinition.ARENA, arena.id());
            if (this.editingMarkerId.equals(arena.id())) {
                this.editingMarkerId = "";
                this.renameField.setX(-1000);
            }
            this.selectedArenaId = "";
            this.pendingRefreshTicks = 5;
            if (this.screen != null) this.screen.openWorkspaceView(this);
            return true;
        }

        int cx = arenaRow.x() + 80;
        com.google.gson.JsonObject properties = arena.properties() != null ? arena.properties() : new com.google.gson.JsonObject();
        com.google.gson.JsonArray restrictions = properties.has("restrictions") && properties.get("restrictions").isJsonArray() ? properties.getAsJsonArray("restrictions") : new com.google.gson.JsonArray();
        java.util.Set<String> activeRestrictions = new java.util.HashSet<>();
        for (com.google.gson.JsonElement e : restrictions) activeRestrictions.add(e.getAsString());

        for (dev.frost.miniverse.minigame.core.region.RegionRestriction res : dev.frost.miniverse.minigame.core.region.RegionRestriction.values()) {
            boolean active = activeRestrictions.contains(res.name());
            String label = (active ? "[X] " : "[ ] ") + res.name();
            int w = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(label);
            UiLayout.Rect cb = new UiLayout.Rect(cx, arenaRow.y() + 40 - 2, w, 12);
            if (cb.contains(mouseX, adjustedMouseY)) {
                this.toggleRestriction(DuelsDefinition.ID, DuelsDefinition.ARENA, arena, res);
                return true;
            }
            cx += w + 12;
        }

        int dx = arenaRow.x() + 90;
        com.google.gson.JsonObject arenaProps = arena.properties() != null ? arena.properties() : new com.google.gson.JsonObject();
        com.google.gson.JsonArray types = arenaProps.has("supported_duel_types") && arenaProps.get("supported_duel_types").isJsonArray() ? arenaProps.getAsJsonArray("supported_duel_types") : new com.google.gson.JsonArray();
        java.util.Set<String> supportedTypes = new java.util.HashSet<>();
        for (com.google.gson.JsonElement e : types) supportedTypes.add(e.getAsString());

        for (dev.frost.miniverse.minigame.impl.duels.DuelType type : SessionSnapshotData.duelTypes()) {
            boolean isSupported = supportedTypes.contains(type.id());
            String label = (isSupported ? "[X] " : "[ ] ") + type.name();
            int w = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(label);
            UiLayout.Rect cb = new UiLayout.Rect(dx, arenaRow.y() + 58 - 2, w, 12);
            if (cb.contains(mouseX, adjustedMouseY)) {
                this.toggleSupportedDuelType(DuelsDefinition.ID, DuelsDefinition.ARENA, arena, type);
                return true;
            }
            dx += w + 12;
        }

        yRef[0] += 90;

        if (handleSpawnSectionClick(mouseX, adjustedMouseY, DuelsDefinition.PLAYER_1_SPAWN, arena, yRef)) return true;
        if (handleSpawnSectionClick(mouseX, adjustedMouseY, DuelsDefinition.PLAYER_2_SPAWN, arena, yRef)) return true;
        if (handleSpawnSectionClick(mouseX, adjustedMouseY, DuelsDefinition.SPECTATOR_SPAWN, arena, yRef)) return true;

        return false;
    }

    private boolean handleSpawnSectionClick(double mouseX, int adjustedMouseY, String definitionKey, SessionSnapshotData.EditorMarker arena, int[] yRef) {
        int rowY = yRef[0];
        int headerHeight = 36;
        UiLayout.Rect headerRow = new UiLayout.Rect(this.listArea.x(), rowY, this.listArea.width(), headerHeight);
        
        List<SessionSnapshotData.EditorMarker> allPoints = SessionSnapshotData.editorState().markers(DuelsDefinition.ID, definitionKey);
        List<SessionSnapshotData.EditorMarker> arenaPoints = allPoints.stream()
            .filter(p -> isInside(p, arena))
            .collect(Collectors.toList());

        String statsStr = "Placed in Arena: " + arenaPoints.size() + " ";
        int textEnd = headerRow.x() + headerRow.width() - net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(statsStr) - 14;
        UiLayout.Rect addBtn = new UiLayout.Rect(textEnd - 36, headerRow.y() + 8, 30, 20);

        if (addBtn.contains(mouseX, adjustedMouseY)) {
            this.startAdd(definitionKey);
            return true;
        }

        rowY += headerHeight;

        if (arenaPoints.isEmpty()) {
            rowY += 50;
        } else {
            for (SessionSnapshotData.EditorMarker marker : arenaPoints) {
                UiLayout.Rect row = new UiLayout.Rect(this.listArea.x() + 20, rowY, this.listArea.width() - 20, 40);
                UiLayout.Rect teleport = new UiLayout.Rect(row.x() + row.width() - 150, row.y() + 10, 68, 20);
                UiLayout.Rect delete = new UiLayout.Rect(row.x() + row.width() - 74, row.y() + 10, 60, 20);

                if (teleport.contains(mouseX, adjustedMouseY)) {
                    this.sendMarkerAction("teleport", DuelsDefinition.ID, definitionKey, marker.id());
                    return true;
                }
                if (delete.contains(mouseX, adjustedMouseY)) {
                    this.sendMarkerAction("delete", DuelsDefinition.ID, definitionKey, marker.id());
                    this.pendingRefreshTicks = 5;
                    return true;
                }

                rowY += 44;
            }
            rowY += 10;
        }

        yRef[0] = rowY;
        return false;
    }

    private SessionSnapshotData.EditorMarker getArena(String arenaId) {
        if (arenaId == null || arenaId.isBlank()) return null;
        return SessionSnapshotData.editorState().markers(DuelsDefinition.ID, DuelsDefinition.ARENA).stream()
            .filter(m -> m.id().equals(arenaId))
            .findFirst().orElse(null);
    }

    private boolean isInside(SessionSnapshotData.EditorMarker pointMarker, SessionSnapshotData.EditorMarker regionMarker) {
        if (pointMarker.points().isEmpty()) return false;
        SessionSnapshotData.EditorPoint p = pointMarker.points().getFirst();

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
        this.refreshAction.run();
    }

    private void toggleSupportedDuelType(String gameId, String definitionKey, SessionSnapshotData.EditorMarker marker, dev.frost.miniverse.minigame.impl.duels.DuelType type) {
        com.google.gson.JsonObject properties = marker.properties() != null ? marker.properties().deepCopy() : new com.google.gson.JsonObject();
        com.google.gson.JsonArray types = properties.has("supported_duel_types") && properties.get("supported_duel_types").isJsonArray() ? properties.getAsJsonArray("supported_duel_types") : new com.google.gson.JsonArray();
        boolean found = false;
        com.google.gson.JsonArray updated = new com.google.gson.JsonArray();
        for (com.google.gson.JsonElement e : types) {
            if (e.getAsString().equals(type.id())) found = true;
            else updated.add(e);
        }
        if (!found) {
            updated.add(type.id());
        }
        properties.add("supported_duel_types", updated);

        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", "update_properties");
        nbt.putString("gameId", gameId);
        nbt.putString("definitionKey", definitionKey);
        nbt.putString("markerId", marker.id());
        nbt.putString("properties", properties.toString());
        ClientPlayNetworking.send(new NetworkConstants.MapEditorActionPayload(nbt));
        this.refreshAction.run();
    }

    @Override
    public String title() {
        return "Duels Editor";
    }

    @Override
    public String subtitle() {
        return this.selectedArenaId.isBlank() ? "Arena Management" : "Arena Details";
    }

    private void startAdd(String definitionKey) {
        this.sendMarkerAction("start_add", DuelsDefinition.ID, definitionKey, "");
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

    private static void renderSmallButton(DrawContext context, TextRenderer textRenderer, int x, int y, int width, String label) {
        UiRenderer.panel(context, x, y, width, 20, UiTheme.PANEL_RAISED, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal(label), x + (width - textRenderer.getWidth(label)) / 2, y + 6, UiTheme.TEXT, false);
    }

    private static String locationText(SessionSnapshotData.EditorMarker marker) {
        if ("REGION".equalsIgnoreCase(marker.type()) || !marker.regions().isEmpty()) {
            if (marker.regions().isEmpty()) {
                return "Bounds: not set";
            }
            SessionSnapshotData.EditorRegionPart part = marker.regions().getFirst();
            return "Bounds: " + format(part.min()) + " to " + format(part.max());
        }
        if (marker.points().isEmpty()) {
            return "Location: not set";
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
}
