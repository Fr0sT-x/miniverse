package dev.frost.miniverse.client.gui.map;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class LeftToolPalette extends SidebarWidget {

    public LeftToolPalette(int x, int y, int width, int height) {
        super(x, y, width, height, true);
        initWidgets();
    }

    private int currentGameModeIndex = -1;

    private boolean isDropdownOpen = false;

    private void initWidgets() {
        this.clearChildren(); // clear old widgets if we re-init
        int padding = 10;
        int btnHeight = 20;
        int btnWidth = this.width - padding * 2;
        int currentY = padding;

        java.util.List<String> gameModes = new java.util.ArrayList<>();
        for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorExtension ext : dev.frost.miniverse.client.gui.SessionSnapshotData.editorExtensions()) {
            gameModes.add(ext.gameId());
        }

        if (!gameModes.isEmpty()) {
            if (currentGameModeIndex >= gameModes.size()) currentGameModeIndex = -1;
            String currentMode = currentGameModeIndex >= 0 ? gameModes.get(currentGameModeIndex) : "Select Gamemode";
            if (currentGameModeIndex < 0) {
                MapEditorState.INSTANCE.selectedGameId = "";
            }
            net.minecraft.text.MutableText modeText = Text.literal("Mode: " + currentMode + (isDropdownOpen ? " \u25B2" : " \u25BC"));
            if (currentGameModeIndex < 0) {
                modeText.formatted(net.minecraft.util.Formatting.RED);
            }
            
            this.addWidget(ButtonWidget.builder(modeText, b -> {
                isDropdownOpen = !isDropdownOpen;
                initWidgets();
            }).dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
            currentY += btnHeight + 5;

            if (isDropdownOpen) {
                for (int i = 0; i < gameModes.size(); i++) {
                    final int idx = i;
                    this.addWidget(ButtonWidget.builder(Text.literal("  " + gameModes.get(i)), b -> {
                        currentGameModeIndex = idx;
                        MapEditorState.INSTANCE.selectedGameId = gameModes.get(idx);
                        isDropdownOpen = false;
                        initWidgets();
                    }).dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
                    currentY += btnHeight + 2;
                }
                currentY += 5;
            }
        }

        this.addWidget(ButtonWidget.builder(Text.literal("Select Tool"), b -> selectTool("SELECT"))
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
        currentY += btnHeight + 5;

        this.addWidget(ButtonWidget.builder(Text.literal("Paste Preview"), b -> selectTool("PASTE"))
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
        currentY += btnHeight + 5;

        this.addWidget(ButtonWidget.builder(Text.literal("⚡ Scan Map Chests"), b -> scanChests())
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
    }

    private void scanChests() {
        String gameId = MapEditorState.INSTANCE.selectedGameId;
        if (gameId == null || gameId.isBlank()) {
            gameId = "skywars";
        }
        net.minecraft.nbt.NbtCompound action = new net.minecraft.nbt.NbtCompound();
        action.putString("action", "scan_chests");
        action.putString("gameId", gameId);
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.MapEditorActionPayload(action));
    }

    private void selectTool(String tool) {
        MapEditorWorkspaceScreen.INSTANCE.setActiveTool(tool);
    }
}
