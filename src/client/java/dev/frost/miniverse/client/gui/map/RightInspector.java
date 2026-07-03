package dev.frost.miniverse.client.gui.map;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class RightInspector extends SidebarWidget {

    public RightInspector(int x, int y, int width, int height) {
        super(x, y, width, height, false);
        initWidgets();
    }

    private int currentTeamIndex = 0;

    public void update() {
        initWidgets();
    }

    private void initWidgets() {
        this.clearChildren();
        int padding = 10;
        int btnHeight = 20;
        int btnWidth = this.width - padding * 2;
        int currentY = padding;

        MapEditorState state = MapEditorState.INSTANCE;
        int selectedSize = state.selectedMarkers.size();
        int clipboardSize = state.clipboard.size();

        this.addWidget(ButtonWidget.builder(Text.literal("Selected: " + selectedSize), b -> {})
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
        currentY += btnHeight + 5;

        this.addWidget(ButtonWidget.builder(Text.literal("Copy Selection"), b -> handleCopy())
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
        currentY += btnHeight + 5;

        this.addWidget(ButtonWidget.builder(Text.literal("Paste Preview"), b -> handlePaste())
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
        currentY += btnHeight + 10;

        if (clipboardSize > 0) {
            this.addWidget(ButtonWidget.builder(Text.literal("Clipboard: " + clipboardSize), b -> {})
                .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
            currentY += btnHeight + 5;

            java.util.List<TeamOption> teams = teamOptions();
            if (currentTeamIndex >= teams.size()) {
                currentTeamIndex = 0;
            }
            TeamOption currentTeam = teams.get(currentTeamIndex);
            MapEditorState.INSTANCE.selectedTeam = currentTeam.id();

            this.addWidget(ButtonWidget.builder(Text.literal("Team: " + currentTeam.label()), b -> {
                currentTeamIndex = (currentTeamIndex + 1) % teams.size();
                MapEditorState.INSTANCE.selectedTeam = teams.get(currentTeamIndex).id();
                initWidgets();
            }).dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
            currentY += btnHeight + 5;

            this.addWidget(ButtonWidget.builder(Text.literal("Rotate 90"), b -> handleRotate())
                    .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
            currentY += btnHeight + 5;

            this.addWidget(ButtonWidget.builder(Text.literal("Save Paste"), b -> {
                MapEditorState.INSTANCE.selectedTeam = teams.get(currentTeamIndex).id();
                handleConfirmPaste();
            }).dimensions(this.getX() + padding, this.getY() + currentY, btnWidth, btnHeight).build());
            currentY += btnHeight + 10;

            int maxRows = Math.min(clipboardSize, 8);
            for (int i = 0; i < maxRows; i++) {
                MapEditorState.ClipboardMarkerData entry = MapEditorState.INSTANCE.clipboard.get(i);
                String label = markerLabel(entry.data().marker());
                int removeWidth = 22;
                this.addWidget(ButtonWidget.builder(Text.literal(label), b -> {})
                        .dimensions(this.getX() + padding, this.getY() + currentY, btnWidth - removeWidth - 4, btnHeight).build());
                final int index = i;
                this.addWidget(ButtonWidget.builder(Text.literal("X"), b -> handleRemove(index))
                        .dimensions(this.getX() + padding + btnWidth - removeWidth, this.getY() + currentY, removeWidth, btnHeight).build());
                currentY += btnHeight + 3;
            }
        }
    }

    private String markerLabel(dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker marker) {
        String name = marker.name() == null || marker.name().isBlank() ? marker.definitionKey() : marker.name();
        if (name.length() > 15) {
            name = name.substring(0, 14) + ".";
        }
        return name;
    }

    private java.util.List<TeamOption> teamOptions() {
        java.util.List<TeamOption> options = new java.util.ArrayList<>();
        options.add(new TeamOption("", "None"));

        String gameId = MapEditorState.INSTANCE.selectedGameId;
        if (gameId == null || gameId.isBlank()) {
            gameId = "bedwars";
        }

        for (dev.frost.miniverse.client.gui.SessionSnapshotData.EditorMarker marker
                : dev.frost.miniverse.client.gui.SessionSnapshotData.editorState().markers(gameId, "team_config")) {
            String label = marker.name() == null || marker.name().isBlank() ? marker.id() : marker.name();
            options.add(new TeamOption(marker.id(), label));
        }

        String selectedTeam = MapEditorState.INSTANCE.selectedTeam;
        if (selectedTeam != null && !selectedTeam.isBlank()) {
            for (int i = 0; i < options.size(); i++) {
                if (selectedTeam.equals(options.get(i).id())) {
                    currentTeamIndex = i;
                    break;
                }
            }
        } else {
            currentTeamIndex = 0;
        }

        return options;
    }

    private record TeamOption(String id, String label) {
    }

    private void handleConfirmPaste() {
        MapEditorWorkspaceScreen.INSTANCE.confirmPaste();
    }

    private void handleCopy() {
        MapEditorWorkspaceScreen.INSTANCE.copySelection();
        update();
    }

    private void handlePaste() {
        MapEditorWorkspaceScreen.INSTANCE.startPastePreview();
        update();
    }

    private void handleRotate() {
        MapEditorWorkspaceScreen.INSTANCE.rotatePaste();
        update();
    }

    private void handleRemove(int index) {
        MapEditorWorkspaceScreen.INSTANCE.removeClipboardMarker(index);
        update();
    }
}
