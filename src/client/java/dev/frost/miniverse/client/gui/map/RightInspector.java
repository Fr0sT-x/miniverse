package dev.frost.miniverse.client.gui.map;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class RightInspector extends SidebarWidget {

    public RightInspector(int x, int y, int width, int height) {
        super(x, y, width, height, false);
        initWidgets();
    }

    private int currentTeamIndex = 0;
    private String[] teams = {"Red", "Blue", "Green", "Yellow", "Cyan", "Pink", "White", "Gray"};

    public void update() {
        initWidgets();
    }

    private void initWidgets() {
        this.clearChildren();
        int padding = 10;
        int btnHeight = 20;
        int btnWidth = this.width - padding * 2;
        int currentY = padding;

        this.addWidget(ButtonWidget.builder(Text.literal("Confirm Paste"), b -> handleConfirmPaste())
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
        currentY += btnHeight + 5;

        this.addWidget(ButtonWidget.builder(Text.literal("Rotate 90°"), b -> handleRotate())
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
        currentY += btnHeight + 20; // Extra spacing

        int clipboardSize = MapEditorState.INSTANCE.clipboard.size();
        if (clipboardSize > 0) {
            this.addWidget(ButtonWidget.builder(Text.literal("Selected: " + clipboardSize), b -> {})
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
            currentY += btnHeight + 5;

            this.addWidget(ButtonWidget.builder(Text.literal("Team: " + teams[currentTeamIndex]), b -> {
                currentTeamIndex = (currentTeamIndex + 1) % teams.length;
                MapEditorState.INSTANCE.selectedTeam = teams[currentTeamIndex];
                initWidgets();
            }).dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
            currentY += btnHeight + 5;

            this.addWidget(ButtonWidget.builder(Text.literal("Save to Team"), b -> {
                MapEditorState.INSTANCE.selectedTeam = teams[currentTeamIndex];
                net.minecraft.client.MinecraftClient.getInstance().inGameHud.getChatHud()
                    .addMessage(net.minecraft.text.Text.literal("Assigned " + clipboardSize + " markers to Team " + teams[currentTeamIndex]));
            }).dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
            currentY += btnHeight + 5;
        }
    }

    private void handleConfirmPaste() {
        MapEditorWorkspaceScreen.INSTANCE.confirmPaste();
    }

    private void handleRotate() {
        MapEditorWorkspaceScreen.INSTANCE.rotatePaste();
    }
}
