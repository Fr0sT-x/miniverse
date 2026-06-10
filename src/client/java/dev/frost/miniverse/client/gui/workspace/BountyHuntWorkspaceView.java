package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BountyHuntWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider, GamemodeWorkspaceView.RosterRefreshable {
    private static final int ROW_HEIGHT = 20;
    private static final int COLUMN_HEADER_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 22;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Map<String, UiAnimation.Value> rowHovers = new HashMap<>();
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private Module activeModule = Module.PLAYERS;
    private UiLayout.Rect workspace = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect rosterArea = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect selectAllButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect clearButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect startButton = new UiLayout.Rect(0, 0, 0, 0);

    private TextFieldWidget graceField;
    private TextFieldWidget invincibilityField;
    private TextFieldWidget scoreToWinField;
    private TextFieldWidget targetSwapField;
    private TextFieldWidget compassCooldownField;
    private TextFieldWidget trackerItemField;
    private ButtonWidget trackerToggle;
    private ButtonWidget netherToggle;

    private String sessionName = "bountyhunt-" + System.currentTimeMillis();
    private int graceSeconds = 300;
    private int invincibilitySeconds = 120;
    private int scoreToWin = 5;
    private int targetSwapSeconds = 0;
    private int compassCooldownSeconds = 2;
    private boolean trackerEnabled = true;
    private boolean netherTrackingEnabled = true;
    private String trackerItemId = "minecraft:compass";
    private String statusMessage = "";

    public BountyHuntWorkspaceView() {
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.workspace = workspace;
        UiLayout.Rect mainPanel = workspace.inset(4);
        this.rosterArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 88, mainPanel.width() - 24, mainPanel.height() - 116);
        this.playerGrid.setBounds(this.rosterArea);
        this.selectAllButton = new UiLayout.Rect(mainPanel.x() + 14, mainPanel.y() + 50, 90, BUTTON_HEIGHT);
        this.clearButton = new UiLayout.Rect(mainPanel.x() + 112, mainPanel.y() + 50, 70, BUTTON_HEIGHT);
        this.startButton = new UiLayout.Rect(mainPanel.x() + mainPanel.width() - 126, mainPanel.y() + 10, 112, BUTTON_HEIGHT);

        if (this.activeModule == Module.MATCH_RULES) {
            this.graceField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 96, Integer.toString(this.graceSeconds), 160, "Grace seconds");
            this.invincibilityField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 128, Integer.toString(this.invincibilitySeconds), 160, "Invincibility seconds");
            this.scoreToWinField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 160, Integer.toString(this.scoreToWin), 160, "Score to win");
            this.targetSwapField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 192, Integer.toString(this.targetSwapSeconds), 160, "Target swap seconds");
        } else if (this.activeModule == Module.TRACKING) {
            this.trackerToggle = this.addButton(screen, toggleLabel("Tracker", this.trackerEnabled), mainPanel.x() + 180, mainPanel.y() + 96, 160, () -> {
                this.trackerEnabled = !this.trackerEnabled;
                this.trackerToggle.setMessage(Text.literal(toggleLabel("Tracker", this.trackerEnabled)));
            });
            this.netherToggle = this.addButton(screen, toggleLabel("Nether", this.netherTrackingEnabled), mainPanel.x() + 180, mainPanel.y() + 128, 160, () -> {
                this.netherTrackingEnabled = !this.netherTrackingEnabled;
                this.netherToggle.setMessage(Text.literal(toggleLabel("Nether", this.netherTrackingEnabled)));
            });
            this.compassCooldownField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 160, Integer.toString(this.compassCooldownSeconds), 160, "Cooldown seconds");
            this.trackerItemField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 192, this.trackerItemId, 200, "Tracker item");
        }
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(mainPanel.x() + 1, mainPanel.y() + 1, mainPanel.x() + mainPanel.width() - 1, mainPanel.y() + 40, 0x701B2634);
        context.drawText(textRenderer, Text.literal(this.activeModule.label), mainPanel.x() + 14, mainPanel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(this.activeModule.description), mainPanel.x() + 14, mainPanel.y() + 28, UiTheme.TEXT_DIM, false);

        if (this.activeModule == Module.PLAYERS) {
            this.renderPlayerActions(context, textRenderer, mouseX, mouseY);
            this.playerGrid.render(context, textRenderer, mouseX, mouseY, delta);
        } else if (this.activeModule == Module.SUMMARY) {
            this.renderSummary(context, textRenderer, mainPanel);
        } else {
            this.renderSettingsModule(context, textRenderer, mainPanel);
        }

        if (!this.statusMessage.isEmpty()) {
            context.drawText(textRenderer, Text.literal(this.statusMessage), mainPanel.x() + 14, mainPanel.y() + mainPanel.height() - 18, UiTheme.SUCCESS, false);
        }
        this.renderActionButton(context, textRenderer, this.startButton, "Start Match", UiTheme.ACCENT_GREEN, this.startButton.contains(mouseX, mouseY));
    }

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        int labelX = mainPanel.x() + 38;
        int labelY = mainPanel.y() + 102;
        if (this.activeModule == Module.MATCH_RULES) {
            context.drawText(textRenderer, Text.literal("Grace Period"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Invincibility"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Score To Win"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Target Swap"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
        } else if (this.activeModule == Module.TRACKING) {
            context.drawText(textRenderer, Text.literal("Tracker"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Nether"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Cooldown"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Tracker Item"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
        } else if (this.activeModule == Module.PLAYERS) {
            this.playerGrid.renderForeground(context, textRenderer, workspace, mouseX, mouseY, delta);
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
        if (this.activeModule != Module.PLAYERS) {
            return false;
        }
        if (this.selectAllButton.contains(mouseX, mouseY)) {
            this.selectAll();
            return true;
        }
        if (this.clearButton.contains(mouseX, mouseY)) {
            this.clearSelection();
            return true;
        }
        if (this.playerGrid.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.activeModule == Module.PLAYERS && this.playerGrid.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.activeModule == Module.PLAYERS && this.playerGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeModule != Module.PLAYERS) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        return this.playerGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public String title() {
        return "Bounty Hunt Setup";
    }

    @Override
    public String subtitle() {
        return "Workspace-based roster selection";
    }

    @Override
    public String gameId() {
        return BountyHuntDefinition.ID;
    }

    @Override
    public List<WorkspaceModule> modules() {
        List<WorkspaceModule> modules = new ArrayList<>();
        for (Module module : Module.values()) {
            String group = module == Module.SUMMARY ? "Summary" : module == Module.PLAYERS ? "Setup" : "Rules";
            modules.add(new WorkspaceModule(module.id, module.icon, module.label, group));
        }
        return modules;
    }

    @Override
    public String activeModuleId() {
        return this.activeModule.id;
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        this.activeModule = Module.fromId(moduleId).orElse(this.activeModule);
    }

    @Override
    public void refreshRoster() {
        this.playerGrid.refreshRoster();
    }

    private void renderSettingsModule(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        int moduleX = mainPanel.x() + 14;
        int moduleY = mainPanel.y() + 72;
        int moduleWidth = Math.min(520, mainPanel.width() - 28);
        int moduleHeight = Math.min(200, mainPanel.height() - 104);
        UiRenderer.panel(context, moduleX, moduleY, moduleWidth, moduleHeight, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(moduleX, moduleY, moduleX + 3, moduleY + moduleHeight, this.activeModule.accent);
        context.drawText(textRenderer, Text.literal(this.activeModule.label), moduleX + 12, moduleY + 12, this.activeModule.accent, false);
    }

    private void renderSummary(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        this.syncStateFromWidgets();
        int x = mainPanel.x() + 14;
        int y = mainPanel.y() + 72;
        int width = Math.min(520, mainPanel.width() - 28);
        UiRenderer.panel(context, x, y, width, 168, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(x, y, x + 3, y + 168, UiTheme.ACCENT);
        int line = y + 18;
        context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
        line += 20;
        context.drawText(textRenderer, Text.literal("Players: " + this.playerGrid.getMembers("selected").size()), x + 14, line, UiTheme.TEXT_MUTED, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Score To Win: " + this.scoreToWin), x + 14, line, UiTheme.TEXT_MUTED, false);
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            context.drawText(textRenderer, Text.literal(validation), x + 14, y + 132, UiTheme.WARNING, false);
        }
    }

    private void renderPlayerActions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        this.renderActionButton(context, textRenderer, this.selectAllButton, "Select All", UiTheme.ACCENT_BLUE, this.selectAllButton.contains(mouseX, mouseY));
        this.renderActionButton(context, textRenderer, this.clearButton, "Clear", UiTheme.ACCENT, this.clearButton.contains(mouseX, mouseY));
    }

    private void renderActionButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(accent, 0.34F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, border);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    private ButtonWidget addButton(SessionScreen screen, String label, int x, int y, int width, Runnable action) {
        return screen.addWorkspaceChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
            .dimensions(x, y, width, BUTTON_HEIGHT)
            .build());
    }

    private TextFieldWidget addField(SessionScreen screen, int x, int y, String value, int width, String narration) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, width, BUTTON_HEIGHT, Text.literal(narration));
        field.setMaxLength(64);
        field.setText(value);
        return screen.addWorkspaceChild(field);
    }

    private void selectAll() {
        this.playerGrid.clear();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            this.playerGrid.addMember("selected", entry);
        }
        this.statusMessage = "Selected all players.";
    }

    private void clearSelection() {
        this.playerGrid.clear();
        this.statusMessage = "Selection cleared.";
    }

    private void createSession() {
        this.syncStateFromWidgets();
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            this.statusMessage = validation;
            return;
        }
        NbtCompound plan = new NbtCompound();
        plan.putString("game", BountyHuntDefinition.ID);
        plan.putString("name", this.sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.buildSettingsCompound());

        net.minecraft.nbt.NbtList groups = new net.minecraft.nbt.NbtList();
        net.minecraft.nbt.NbtCompound group = new net.minecraft.nbt.NbtCompound();
        group.putString("id", "players");
        group.putString("name", "Players");
        net.minecraft.nbt.NbtList members = new net.minecraft.nbt.NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.playerGrid.getMembers("selected")) {
            net.minecraft.nbt.NbtCompound compound = new net.minecraft.nbt.NbtCompound();
            compound.putString("uuid", entry.uuid());
            compound.putString("name", entry.name());
            members.add(compound);
        }
        group.put("members", members);
        groups.add(group);
        plan.put("groups", groups);

        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(BountyHuntDefinition.ID, this.sessionName, plan));
        this.statusMessage = "Requested Bounty Hunt session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putInt("gracePeriodSeconds", this.graceSeconds);
        settings.putInt("respawnInvincibilitySeconds", this.invincibilitySeconds);
        settings.putInt("scoreToWin", this.scoreToWin);
        settings.putInt("targetSwapIntervalSeconds", this.targetSwapSeconds);
        settings.putInt("compassCooldownSeconds", this.compassCooldownSeconds);
        settings.putBoolean("trackerEnabled", this.trackerEnabled);
        settings.putBoolean("netherTracking", this.netherTrackingEnabled);
        settings.putString("trackerItemId", this.trackerItemId);
        return settings;
    }

    private void syncStateFromWidgets() {
        this.graceSeconds = readInt(this.graceField, this.graceSeconds, 0, 3600);
        this.invincibilitySeconds = readInt(this.invincibilityField, this.invincibilitySeconds, 0, 3600);
        this.scoreToWin = readInt(this.scoreToWinField, this.scoreToWin, 1, 99);
        this.targetSwapSeconds = readInt(this.targetSwapField, this.targetSwapSeconds, 0, 3600);
        this.compassCooldownSeconds = readInt(this.compassCooldownField, this.compassCooldownSeconds, 0, 300);
        if (this.trackerItemField != null) {
            this.trackerItemId = this.trackerItemField.getText().trim();
        }
    }

    private int readInt(TextFieldWidget field, int fallback, int min, int max) {
        if (field == null) {
            return fallback;
        }
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
        try {
            int value = Math.clamp(Integer.parseInt(text), min, max);
            field.setText(Integer.toString(value));
            return value;
        } catch (NumberFormatException ignored) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
    }

    private String getStartValidationMessage() {
        if (this.client.player == null) {
            return "Not connected to a server.";
        }
        if (this.sessionName.isBlank()) {
            return "Enter a session name.";
        }
        if (this.playerGrid.getMembers("selected").size() < 2) {
            return "Select at least two players.";
        }
        return "";
    }

    private static String toggleLabel(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }

    private enum Module {
        PLAYERS("players", "P", "Players", "Select participating players.", UiTheme.ACCENT),
        MATCH_RULES("rules", "R", "Match Rules", "Configure scoring and timers.", UiTheme.ACCENT_BLUE),
        TRACKING("tracking", "T", "Tracking", "Configure tracker behavior.", UiTheme.ACCENT_GREEN),
        SUMMARY("summary", "U", "Summary", "Review and launch the match.", UiTheme.ACCENT);

        private final String id;
        private final String icon;
        private final String label;
        private final String description;
        private final int accent;

        Module(String id, String icon, String label, String description, int accent) {
            this.id = id;
            this.icon = icon;
            this.label = label;
            this.description = description;
            this.accent = accent;
        }

        private static java.util.Optional<Module> fromId(String id) {
            if (id == null) {
                return java.util.Optional.empty();
            }
            for (Module module : values()) {
                if (module.id.equalsIgnoreCase(id)) {
                    return java.util.Optional.of(module);
                }
            }
            return java.util.Optional.empty();
        }
    }
}
