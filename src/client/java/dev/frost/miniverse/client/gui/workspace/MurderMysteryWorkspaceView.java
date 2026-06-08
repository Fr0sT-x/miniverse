package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.murdermystery.MurderMysteryDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.List;

public final class MurderMysteryWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider {
    private static final int ROW_HEIGHT = 28;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private Module activeModule = Module.MAP;
    private UiLayout.Rect mapList = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect startButton = new UiLayout.Rect(0, 0, 0, 0);
    private TextFieldWidget durationField;
    private TextFieldWidget detectiveCountField;
    private TextFieldWidget coinIntervalField;
    private TextFieldWidget bowPriceField;
    private String sessionName = "murdermystery-" + System.currentTimeMillis();
    private String selectedMapId = "";
    private String status = "";

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        UiLayout.Rect panel = workspace.inset(4);
        this.mapList = new UiLayout.Rect(panel.x() + 14, panel.y() + 72, Math.min(560, panel.width() - 28), panel.height() - 116);
        this.startButton = new UiLayout.Rect(panel.x() + panel.width() - 126, panel.y() + 12, 112, 22);
        if (this.activeModule == Module.RULES) {
            this.durationField = field(screen, panel.x() + 180, panel.y() + 88, "300", "Round duration (seconds)");
            this.detectiveCountField = field(screen, panel.x() + 180, panel.y() + 118, "1", "Detective count");
            this.coinIntervalField = field(screen, panel.x() + 180, panel.y() + 148, "5", "Coin spawn interval (seconds)");
            this.bowPriceField = field(screen, panel.x() + 180, panel.y() + 178, "10", "Detective bow price (coins)");
        }
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect panel = workspace.inset(4);
        UiRenderer.panel(context, panel.x(), panel.y(), panel.width(), panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(panel.x() + 1, panel.y() + 1, panel.x() + panel.width() - 1, panel.y() + 46, 0x701A1A2E);
        context.drawText(textRenderer, Text.literal(this.activeModule.label), panel.x() + 14, panel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(this.activeModule.description), panel.x() + 14, panel.y() + 28, UiTheme.TEXT_DIM, false);
        this.renderButton(context, textRenderer, this.startButton, "Start Match", UiTheme.ACCENT_BLUE, this.startButton.contains(mouseX, mouseY));
        if (this.activeModule == Module.MAP) {
            this.renderMaps(context, textRenderer, mouseX, mouseY);
        } else if (this.activeModule == Module.RULES) {
            this.renderRules(context, textRenderer, panel);
        } else {
            this.renderSummary(context, textRenderer, panel);
        }
        if (!this.status.isBlank()) {
            context.drawText(textRenderer, Text.literal(this.status), panel.x() + 14, panel.y() + panel.height() - 18, UiTheme.WARNING, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (this.startButton.contains(mouseX, mouseY)) {
            this.createSession();
            return true;
        }
        if (this.activeModule == Module.MAP) {
            int index = mapIndexAt(mouseX, mouseY);
            List<SessionSnapshotData.MapSummary> maps = compatibleMaps();
            if (index >= 0 && index < maps.size()) {
                this.selectedMapId = maps.get(index).id();
                this.status = "Selected " + maps.get(index).name() + ".";
                return true;
            }
        }
        return false;
    }

    @Override
    public String title() {
        return "Murder Mystery Setup";
    }

    @Override
    public String subtitle() {
        return "Find the murderer before it's too late!";
    }

    @Override
    public String gameId() {
        return MurderMysteryDefinition.ID;
    }

    @Override
    public List<WorkspaceModule> modules() {
        return List.of(
            new WorkspaceModule(Module.MAP.id, "M", Module.MAP.label, "Setup"),
            new WorkspaceModule(Module.RULES.id, "R", Module.RULES.label, "Rules"),
            new WorkspaceModule(Module.SUMMARY.id, "S", Module.SUMMARY.label, "Summary")
        );
    }

    @Override
    public String activeModuleId() {
        return this.activeModule.id;
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.activeModule = Module.fromId(moduleId);
    }

    private void renderMaps(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        List<SessionSnapshotData.MapSummary> maps = compatibleMaps();
        UiRenderer.panel(context, this.mapList.x(), this.mapList.y(), this.mapList.width(), this.mapList.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal("Valid Murder Mystery Maps (" + maps.size() + ")"), this.mapList.x() + 10, this.mapList.y() + 10, UiTheme.TEXT, false);
        if (maps.isEmpty()) {
            context.drawText(textRenderer, Text.literal("Open a map editor and configure a Murder Mystery map."), this.mapList.x() + 10, this.mapList.y() + 30, UiTheme.TEXT_DIM, false);
            return;
        }
        int y = this.mapList.y() + 32;
        for (SessionSnapshotData.MapSummary map : maps) {
            boolean selected = map.id().equals(this.selectedMapId);
            boolean hovered = mouseX >= this.mapList.x() + 8 && mouseX <= this.mapList.x() + this.mapList.width() - 8 && mouseY >= y && mouseY <= y + ROW_HEIGHT - 2;
            context.fill(this.mapList.x() + 8, y, this.mapList.x() + this.mapList.width() - 8, y + ROW_HEIGHT - 2, selected ? 0xAA2A2A4A : hovered ? 0x661A1A2A : 0x33222222);
            context.drawText(textRenderer, Text.literal(map.name()), this.mapList.x() + 16, y + 6, selected ? UiTheme.ACCENT_BLUE : UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(map.id()), this.mapList.x() + 210, y + 6, UiTheme.TEXT_DIM, false);
            y += ROW_HEIGHT;
        }
    }

    private void renderRules(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        context.drawText(textRenderer, Text.literal("Match Duration (s)"), panel.x() + 38, panel.y() + 94, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Detective Count"), panel.x() + 38, panel.y() + 124, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Coin Interval (s)"), panel.x() + 38, panel.y() + 154, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Detective Bow Price"), panel.x() + 38, panel.y() + 184, UiTheme.TEXT_MUTED, false);
    }

    private void renderSummary(DrawContext context, TextRenderer textRenderer, UiLayout.Rect panel) {
        UiRenderer.panel(context, panel.x() + 14, panel.y() + 72, 420, 130, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.drawText(textRenderer, Text.literal("Map: " + (this.selectedMapId.isBlank() ? "None selected" : this.selectedMapId)), panel.x() + 28, panel.y() + 92, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal("Players: all online players"), panel.x() + 28, panel.y() + 112, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), panel.x() + 28, panel.y() + 132, UiTheme.TEXT_MUTED, false);
    }

    private void createSession() {
        if (this.client.player == null) {
            this.status = "Not connected to a server.";
            return;
        }
        if (this.selectedMapId.isBlank()) {
            this.status = "Select a valid Murder Mystery map first.";
            return;
        }
        NbtCompound plan = new NbtCompound();
        plan.putString("game", MurderMysteryDefinition.ID);
        plan.putString("name", this.sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.settingsNbt());
        plan.put("groups", new NbtList());
        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(MurderMysteryDefinition.ID, this.sessionName, plan));
        this.status = "Requested Murder Mystery session creation.";
    }

    private NbtCompound settingsNbt() {
        NbtCompound settings = new NbtCompound();
        settings.putString("seedMode", "random");
        settings.putString("mapId", this.selectedMapId);
        settings.putInt("roundDurationTicks", parseInt(this.durationField, 300) * 20);
        settings.putInt("detectiveCount", parseInt(this.detectiveCountField, 1));
        settings.putInt("coinSpawnIntervalTicks", parseInt(this.coinIntervalField, 5) * 20);
        settings.putInt("detectiveBowPrice", parseInt(this.bowPriceField, 10));
        return settings;
    }

    private List<SessionSnapshotData.MapSummary> compatibleMaps() {
        return SessionSnapshotData.maps().stream().filter(map -> map.validFor(MurderMysteryDefinition.ID)).toList();
    }

    private int mapIndexAt(double mouseX, double mouseY) {
        if (!this.mapList.contains(mouseX, mouseY)) {
            return -1;
        }
        int row = (int) ((mouseY - (this.mapList.y() + 32)) / ROW_HEIGHT);
        return row < 0 ? -1 : row;
    }

    private TextFieldWidget field(SessionScreen screen, int x, int y, String value, String narration) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, 170, 22, Text.literal(narration));
        field.setText(value);
        return screen.addWorkspaceChild(field);
    }

    private void renderButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), hovered ? 0x88202040 : UiTheme.PANEL_RAISED, accent);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    private static int parseInt(TextFieldWidget widget, int fallback) {
        if (widget == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(widget.getText().trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private enum Module {
        MAP("map", "Map Selection", "Choose a validated map configured for Murder Mystery."),
        RULES("rules", "Match Rules", "Tune duration, detective count, and coin economy."),
        SUMMARY("summary", "Summary", "Review and launch the map-backed session.");

        private final String id;
        private final String label;
        private final String description;

        Module(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }

        private static Module fromId(String id) {
            for (Module module : values()) {
                if (module.id.equalsIgnoreCase(id)) {
                    return module;
                }
            }
            return MAP;
        }
    }
}
