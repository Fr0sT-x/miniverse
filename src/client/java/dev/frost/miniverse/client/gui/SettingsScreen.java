package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class SettingsScreen extends Screen {
    private static final int PANEL_PADDING = 16;
    private static final int PANEL_MAX_WIDTH = 700;
    private static final int PANEL_MAX_HEIGHT = 600;
    private static final int TITLE_TOP_Y = 12;
    private static final int SUBTITLE_TOP_Y = 28;
    private static final int TAB_HEIGHT = 25;
    private static final int LIST_TOP_Y = 75;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SESSION_ROW_HEIGHT = 80;
    private static final int ROW_GAP = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 8;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_MUTED = 0xFFB8B8B8;
    private static final String[] DIFFICULTY_VALUES = {"peaceful", "easy", "normal", "hard"};
    private static final String[] DIFFICULTY_LABELS = {"Peaceful", "Easy", "Medium", "Hard"};

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<SessionEntry> sessions = new ArrayList<>();
    private String statusMessage = "";
    private final List<ButtonWidget> sessionButtons = new ArrayList<>();
    private SettingsTab activeTab = SettingsTab.SESSIONS;

    // Memory config state
    private TextFieldWidget maxHeapField;
    private TextFieldWidget initialHeapField;
    private ButtonWidget memoryEnabledToggle;
    private int maxHeapValue;
    private int initialHeapValue;
    private boolean memoryEnabled;

    // Server config state
    private TextFieldWidget viewDistanceField;
    private TextFieldWidget simulationDistanceField;
    private TextFieldWidget spawnProtectionField;
    private ButtonWidget onlineModeToggle;
    private ButtonWidget allowFlightToggle;
    private ButtonWidget acceptsTransfersToggle;
    private ButtonWidget difficultyToggle;
    private int viewDistanceValue;
    private int simulationDistanceValue;
    private int spawnProtectionValue;
    private boolean onlineMode;
    private boolean allowFlight;
    private boolean acceptsTransfers;
    private String difficultyValue;

    // Launcher config state
    private TextFieldWidget maxConcurrentLaunchesField;
    private int maxConcurrentLaunchesValue;

    public SettingsScreen() {
        super(Text.literal("Settings"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.sessionButtons.clear();
        this.statusMessage = "";

        // Load memory config
        SessionSnapshotData.MemorySettings memorySettings = SessionSnapshotData.memorySettings();
        SessionSnapshotData.ServerSettings serverSettings = SessionSnapshotData.serverSettings();
        this.maxHeapValue = memorySettings.maxHeapGb();
        this.initialHeapValue = memorySettings.initialHeapGb();
        this.memoryEnabled = memorySettings.enabled();
        this.viewDistanceValue = serverSettings.viewDistance();
        this.simulationDistanceValue = serverSettings.simulationDistance();
        this.spawnProtectionValue = serverSettings.spawnProtection();
        this.onlineMode = serverSettings.onlineMode();
        this.allowFlight = serverSettings.allowFlight();
        this.acceptsTransfers = serverSettings.acceptsTransfers();
        this.difficultyValue = serverSettings.difficulty();
        this.maxConcurrentLaunchesValue = SessionSnapshotData.maxConcurrentLaunches();

        // Request session list from server
        if (this.client.player != null) {
            ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("refresh"));
        }

        this.refreshSessionsFromSnapshot();

        Layout layout = this.createLayout();

        // Tab buttons
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📋 Sessions"), button -> {
            this.switchTab(SettingsTab.SESSIONS);
        })
            .dimensions(layout.contentX, layout.panelY + 45, 100, TAB_HEIGHT)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("🖥 Server"), button -> {
            this.switchTab(SettingsTab.SERVER);
        })
            .dimensions(layout.contentX + 110, layout.panelY + 45, 100, TAB_HEIGHT)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("🚀 Launcher"), button -> {
            this.switchTab(SettingsTab.LAUNCHER);
        })
            .dimensions(layout.contentX + 220, layout.panelY + 45, 110, TAB_HEIGHT)
            .build());

        if (this.activeTab == SettingsTab.SERVER) {
            this.initServerSettings(layout);
        } else if (this.activeTab == SettingsTab.LAUNCHER) {
            this.initLauncherSettings(layout);
        } else {
            this.initSessionActions(layout);
        }

        // Back button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("← Back"), button -> this.close())
            .dimensions(layout.contentX, layout.panelY + layout.panelHeight - 28, 60, BUTTON_HEIGHT)
            .build());
    }

    private void refreshSessionsFromSnapshot() {
        this.sessions.clear();
        for (SessionSnapshotData.SessionSummary session : SessionSnapshotData.sessions()) {
            this.sessions.add(new SessionEntry(session.id(), session.game(), session.state(), session.players()));
        }
    }

    private void switchTab(SettingsTab tab) {
        if (this.activeTab == tab) {
            return;
        }

        this.activeTab = tab;
        this.init();
    }

    private void initSessionActions(Layout layout) {
        int y = layout.listY;
        for (SessionEntry entry : this.sessions) {
            int buttonX = layout.contentX + layout.contentWidth - 200;
            int buttonY = y + 8;

            ButtonWidget stopButton = ButtonWidget.builder(Text.literal("⏹ Stop & Return"), button -> this.stopSession(entry.id))
                .dimensions(buttonX, buttonY, 95, BUTTON_HEIGHT)
                .build();
            this.addDrawableChild(stopButton);

            ButtonWidget changeSeedButton = ButtonWidget.builder(Text.literal("Change seed"), button -> this.changeSeed(entry.id))
                .dimensions(buttonX + 95 + BUTTON_GAP, buttonY, 85, BUTTON_HEIGHT)
                .build();
            changeSeedButton.setTooltip(Tooltip.of(Text.literal("Restart this session with a new world seed.")));
            this.addDrawableChild(changeSeedButton);

            y += SESSION_ROW_HEIGHT + ROW_GAP;
        }
    }

    private void initServerSettings(Layout layout) {
        int startY = layout.listY + 6;
        int rowHeight = 28;
        int labelWidth = 130;
        int fieldWidth = 90;
        int toggleWidth = 190;
        int saveWidth = 70;
        int x = layout.contentX;
        int fieldX = x + labelWidth;
        int y = startY;

        this.viewDistanceField = new TextFieldWidget(this.textRenderer, fieldX, y, fieldWidth, 20, Text.literal("View Distance"));
        this.viewDistanceField.setMaxLength(2);
        this.viewDistanceField.setText(String.valueOf(this.viewDistanceValue));
        this.addDrawableChild(this.viewDistanceField);

        ButtonWidget saveViewDistance = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveViewDistance())
            .dimensions(fieldX + fieldWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveViewDistance.setTooltip(Tooltip.of(Text.literal("Controls how far chunks are sent to players.")));
        y += rowHeight;

        this.simulationDistanceField = new TextFieldWidget(this.textRenderer, fieldX, y, fieldWidth, 20, Text.literal("Simulation Distance"));
        this.simulationDistanceField.setMaxLength(2);
        this.simulationDistanceField.setText(String.valueOf(this.simulationDistanceValue));
        this.addDrawableChild(this.simulationDistanceField);

        ButtonWidget saveSimulationDistance = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveSimulationDistance())
            .dimensions(fieldX + fieldWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveSimulationDistance.setTooltip(Tooltip.of(Text.literal("Controls how far entities tick and spawn.")));
        y += rowHeight;

        this.spawnProtectionField = new TextFieldWidget(this.textRenderer, fieldX, y, fieldWidth, 20, Text.literal("Spawn Protection"));
        this.spawnProtectionField.setMaxLength(3);
        this.spawnProtectionField.setText(String.valueOf(this.spawnProtectionValue));
        this.addDrawableChild(this.spawnProtectionField);

        ButtonWidget saveSpawnProtection = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveSpawnProtection())
            .dimensions(fieldX + fieldWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveSpawnProtection.setTooltip(Tooltip.of(Text.literal("Prevents block edits near spawn.")));
        y += rowHeight;

        this.difficultyToggle = this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Difficulty: " + this.getDifficultyLabel(this.difficultyValue)),
            button -> {
                this.cycleDifficulty();
                button.setMessage(Text.literal("Difficulty: " + this.getDifficultyLabel(this.difficultyValue)));
            })
            .dimensions(x, y, toggleWidth, BUTTON_HEIGHT)
            .build());
        this.difficultyToggle.setTooltip(Tooltip.of(Text.literal("Sets global damage and mob behavior.")));

        ButtonWidget saveDifficulty = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveDifficulty())
            .dimensions(x + toggleWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveDifficulty.setTooltip(Tooltip.of(Text.literal("Applies the selected difficulty.")));
        y += rowHeight;

        this.onlineModeToggle = this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Online Mode: " + (this.onlineMode ? "ON" : "OFF")),
            button -> {
                this.onlineMode = !this.onlineMode;
                button.setMessage(Text.literal("Online Mode: " + (this.onlineMode ? "ON" : "OFF")));
            })
            .dimensions(x, y, toggleWidth, BUTTON_HEIGHT)
            .build());
        this.onlineModeToggle.setTooltip(Tooltip.of(Text.literal("Authenticates players with Mojang servers.")));

        ButtonWidget saveOnlineMode = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveOnlineMode())
            .dimensions(x + toggleWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveOnlineMode.setTooltip(Tooltip.of(Text.literal("Applies online mode for new sessions.")));
        y += rowHeight;

        this.allowFlightToggle = this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Allow Flight: " + (this.allowFlight ? "ON" : "OFF")),
            button -> {
                this.allowFlight = !this.allowFlight;
                button.setMessage(Text.literal("Allow Flight: " + (this.allowFlight ? "ON" : "OFF")));
            })
            .dimensions(x, y, toggleWidth, BUTTON_HEIGHT)
            .build());
        this.allowFlightToggle.setTooltip(Tooltip.of(Text.literal("Allows players to fly in survival.")));

        ButtonWidget saveAllowFlight = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveAllowFlight())
            .dimensions(x + toggleWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveAllowFlight.setTooltip(Tooltip.of(Text.literal("Applies flight permission for new sessions.")));
        y += rowHeight;

        this.acceptsTransfersToggle = this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Accepts Transfers: " + (this.acceptsTransfers ? "ON" : "OFF")),
            button -> {
                this.acceptsTransfers = !this.acceptsTransfers;
                button.setMessage(Text.literal("Accepts Transfers: " + (this.acceptsTransfers ? "ON" : "OFF")));
            })
            .dimensions(x, y, toggleWidth, BUTTON_HEIGHT)
            .build());
        this.acceptsTransfersToggle.setTooltip(Tooltip.of(Text.literal("Allows server-to-server transfers.")));

        ButtonWidget saveAcceptsTransfers = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveAcceptsTransfers())
            .dimensions(x + toggleWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveAcceptsTransfers.setTooltip(Tooltip.of(Text.literal("Applies transfer policy for new sessions.")));
        y += rowHeight + 6;

        this.memoryEnabledToggle = this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Memory Limits: " + (this.memoryEnabled ? "ON" : "OFF")),
            button -> {
                this.memoryEnabled = !this.memoryEnabled;
                button.setMessage(Text.literal("Memory Limits: " + (this.memoryEnabled ? "ON" : "OFF")));
            })
            .dimensions(x, y, toggleWidth, BUTTON_HEIGHT)
            .build());
        this.memoryEnabledToggle.setTooltip(Tooltip.of(Text.literal("Enables custom heap limits for sessions.")));

        ButtonWidget saveMemoryEnabled = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveMemoryEnabled())
            .dimensions(x + toggleWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveMemoryEnabled.setTooltip(Tooltip.of(Text.literal("Applies memory limit toggle.")));
        y += rowHeight;

        this.maxHeapField = new TextFieldWidget(this.textRenderer, fieldX, y, fieldWidth, 20, Text.literal("Max Heap"));
        this.maxHeapField.setMaxLength(3);
        this.maxHeapField.setText(String.valueOf(this.maxHeapValue));
        this.addDrawableChild(this.maxHeapField);

        ButtonWidget saveMaxHeap = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveMaxHeap())
            .dimensions(fieldX + fieldWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveMaxHeap.setTooltip(Tooltip.of(Text.literal("Maximum heap size per session (GB).")));
        y += rowHeight;

        this.initialHeapField = new TextFieldWidget(this.textRenderer, fieldX, y, fieldWidth, 20, Text.literal("Initial Heap"));
        this.initialHeapField.setMaxLength(3);
        this.initialHeapField.setText(String.valueOf(this.initialHeapValue));
        this.addDrawableChild(this.initialHeapField);

        ButtonWidget saveInitialHeap = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveInitialHeap())
            .dimensions(fieldX + fieldWidth + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveInitialHeap.setTooltip(Tooltip.of(Text.literal("Initial heap size per session (GB).")));
    }

    private void cycleDifficulty() {
        for (int i = 0; i < DIFFICULTY_VALUES.length; i++) {
            if (DIFFICULTY_VALUES[i].equals(this.difficultyValue)) {
                this.difficultyValue = DIFFICULTY_VALUES[(i + 1) % DIFFICULTY_VALUES.length];
                return;
            }
        }
        this.difficultyValue = DIFFICULTY_VALUES[1];
    }

    private String getDifficultyLabel(String value) {
        for (int i = 0; i < DIFFICULTY_VALUES.length; i++) {
            if (DIFFICULTY_VALUES[i].equals(value)) {
                return DIFFICULTY_LABELS[i];
            }
        }
        return "Easy";
    }

    private void saveViewDistance() {
        Integer value = this.parseInt(this.viewDistanceField, 2, 32, "View distance");
        if (value == null) {
            return;
        }
        this.viewDistanceValue = value;
        NbtCompound server = new NbtCompound();
        server.putInt("viewDistance", value);
        this.sendServerSettings(null, server);
        this.statusMessage = "Saved value " + value + ".";
    }

    private void saveSimulationDistance() {
        Integer value = this.parseInt(this.simulationDistanceField, 2, 32, "Simulation distance");
        if (value == null) {
            return;
        }
        this.simulationDistanceValue = value;
        NbtCompound server = new NbtCompound();
        server.putInt("simulationDistance", value);
        this.sendServerSettings(null, server);
        this.statusMessage = "Saved value " + value + ".";
    }

    private void saveSpawnProtection() {
        Integer value = this.parseInt(this.spawnProtectionField, 0, 64, "Spawn protection");
        if (value == null) {
            return;
        }
        this.spawnProtectionValue = value;
        NbtCompound server = new NbtCompound();
        server.putInt("spawnProtection", value);
        this.sendServerSettings(null, server);
        this.statusMessage = "Saved value " + value + ".";
    }

    private void saveDifficulty() {
        NbtCompound server = new NbtCompound();
        server.putString("difficulty", this.difficultyValue);
        this.sendServerSettings(null, server);
        this.statusMessage = "Saved value " + this.getDifficultyLabel(this.difficultyValue) + ".";
    }

    private void saveOnlineMode() {
        NbtCompound server = new NbtCompound();
        server.putBoolean("onlineMode", this.onlineMode);
        this.sendServerSettings(null, server);
        this.statusMessage = "Saved value " + this.onlineMode + ".";
    }

    private void saveAllowFlight() {
        NbtCompound server = new NbtCompound();
        server.putBoolean("allowFlight", this.allowFlight);
        this.sendServerSettings(null, server);
        this.statusMessage = "Saved value " + this.allowFlight + ".";
    }

    private void saveAcceptsTransfers() {
        NbtCompound server = new NbtCompound();
        server.putBoolean("acceptsTransfers", this.acceptsTransfers);
        this.sendServerSettings(null, server);
        this.statusMessage = "Saved value " + this.acceptsTransfers + ".";
    }

    private void saveMemoryEnabled() {
        NbtCompound memory = new NbtCompound();
        memory.putBoolean("enabled", this.memoryEnabled);
        this.sendServerSettings(memory, null);
        this.statusMessage = "Saved value " + this.memoryEnabled + ".";
    }

    private void saveMaxHeap() {
        Integer value = this.parseInt(this.maxHeapField, 1, 128, "Max heap");
        if (value == null) {
            return;
        }
        this.maxHeapValue = value;
        NbtCompound memory = new NbtCompound();
        memory.putInt("maxHeapGb", value);
        this.sendServerSettings(memory, null);
        this.statusMessage = "Saved value " + value + ".";
    }

    private void saveInitialHeap() {
        Integer value = this.parseInt(this.initialHeapField, 1, Math.max(1, this.maxHeapValue), "Initial heap");
        if (value == null) {
            return;
        }
        this.initialHeapValue = value;
        NbtCompound memory = new NbtCompound();
        memory.putInt("initialHeapGb", value);
        this.sendServerSettings(memory, null);
        this.statusMessage = "Saved value " + value + ".";
    }

    private Integer parseInt(TextFieldWidget field, int min, int max, String label) {
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < min || value > max) {
                this.statusMessage = label + " must be between " + min + " and " + max + ".";
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            this.statusMessage = "Invalid number for " + label.toLowerCase() + ".";
            return null;
        }
    }

    private void sendServerSettings(NbtCompound memorySettings, NbtCompound serverSettings) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }

        NbtCompound payload = new NbtCompound();
        boolean hasUpdates = false;
        if (memorySettings != null && !memorySettings.getKeys().isEmpty()) {
            payload.put("memory", memorySettings);
            hasUpdates = true;
        }
        if (serverSettings != null && !serverSettings.getKeys().isEmpty()) {
            payload.put("server", serverSettings);
            hasUpdates = true;
        }
        if (!hasUpdates) {
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.ServerSettingsPayload(payload));
    }

    private void initLauncherSettings(Layout layout) {
        int startY = layout.listY + 35;
        int fieldWidth = 100;

        this.maxConcurrentLaunchesField = new TextFieldWidget(this.textRenderer, layout.contentX, startY, fieldWidth, 20, Text.literal("Max Concurrent Launches"));
        this.maxConcurrentLaunchesField.setMaxLength(2);
        this.maxConcurrentLaunchesField.setText(String.valueOf(this.maxConcurrentLaunchesValue));
        this.addDrawableChild(this.maxConcurrentLaunchesField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Launch Limit"), button -> this.updateMaxConcurrentLaunches())
            .dimensions(layout.contentX + fieldWidth + 10, startY, 140, BUTTON_HEIGHT)
            .build());
    }

    private void updateMaxConcurrentLaunches() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }

        try {
            int value = Integer.parseInt(this.maxConcurrentLaunchesField.getText().trim());
            if (value < 1 || value > 64) {
                this.statusMessage = "Max concurrent launches must be between 1 and 64.";
                return;
            }

            NbtCompound settings = new NbtCompound();
            settings.putInt("maxConcurrentLaunches", value);
            ClientPlayNetworking.send(new NetworkConstants.LauncherSettingsPayload(settings));
            this.maxConcurrentLaunchesValue = value;
            this.statusMessage = "Max concurrent launches set to " + value + ".";
        } catch (NumberFormatException e) {
            this.statusMessage = "Invalid number for max concurrent launches.";
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean sessionsChanged = this.syncSessionsFromSnapshot();
        if (sessionsChanged && this.activeTab == SettingsTab.SESSIONS) {
            this.init();
            return;
        }

        Layout layout = this.createLayout();
        this.drawShell(context, layout);

        int titleCenterX = layout.contentX + layout.contentWidth / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, titleCenterX, layout.panelY + TITLE_TOP_Y, TEXT_WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Manage sessions, server defaults, and launcher limits."), titleCenterX, layout.panelY + SUBTITLE_TOP_Y, TEXT_MUTED);

        if (this.activeTab == SettingsTab.SESSIONS) {
            // Draw sessions list
            if (this.sessions.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Waiting for session data..."), titleCenterX, layout.listY + 12, TEXT_PRIMARY);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No active sessions."), titleCenterX, layout.listY + 28, TEXT_MUTED);
            } else {
                int y = layout.listY;
                for (SessionEntry entry : this.sessions) {
                    this.drawSessionRow(context, layout, y, entry);
                    y += SESSION_ROW_HEIGHT + ROW_GAP;
                }
            }
        } else if (this.activeTab == SettingsTab.SERVER) {
            this.drawServerFieldLabels(context, layout);
        } else {
            context.drawText(this.textRenderer, Text.literal("Max Concurrent Launches: backend servers allowed to start at the same time"), layout.contentX, layout.listY, TEXT_PRIMARY, false);
            context.drawText(this.textRenderer, Text.literal("Current server value: " + SessionSnapshotData.maxConcurrentLaunches()), layout.contentX, layout.listY + 70, TEXT_PRIMARY, false);
            context.drawText(this.textRenderer, Text.literal("Queued launch capacity: " + SessionSnapshotData.launcherQueueCapacity()), layout.contentX, layout.listY + 86, TEXT_MUTED, false);
            context.drawText(this.textRenderer, Text.literal("Lower values reduce CPU and memory spikes during speedrun launches."), layout.contentX, layout.listY + 120, TEXT_MUTED, false);
        }

        // Draw status message
        if (!this.statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), titleCenterX, layout.panelY + layout.panelHeight - 40, 0xA0FFA0);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawServerFieldLabels(DrawContext context, Layout layout) {
        int x = layout.contentX;
        int y = layout.listY + 6;
        int rowHeight = 28;

        context.drawText(this.textRenderer, Text.literal("View distance:"), x, y + 6, TEXT_PRIMARY, false);
        y += rowHeight;
        context.drawText(this.textRenderer, Text.literal("Simulation distance:"), x, y + 6, TEXT_PRIMARY, false);
        y += rowHeight;
        context.drawText(this.textRenderer, Text.literal("Spawn protection:"), x, y + 6, TEXT_PRIMARY, false);
        y += rowHeight * 6 + 6;
        context.drawText(this.textRenderer, Text.literal("Max memory:"), x, y + 6, TEXT_PRIMARY, false);
        y += rowHeight;
        context.drawText(this.textRenderer, Text.literal("Min memory:"), x, y + 6, TEXT_PRIMARY, false);
    }

    private boolean syncSessionsFromSnapshot() {
        List<SessionSnapshotData.SessionSummary> snapshot = SessionSnapshotData.sessions();
        if (this.sessions.size() == snapshot.size() && this.matchesSnapshot(snapshot)) {
            return false;
        }

        this.sessions.clear();
        for (SessionSnapshotData.SessionSummary session : snapshot) {
            this.sessions.add(new SessionEntry(session.id(), session.game(), session.state(), session.players()));
        }
        return true;
    }

    private boolean matchesSnapshot(List<SessionSnapshotData.SessionSummary> snapshot) {
        for (int i = 0; i < snapshot.size(); i++) {
            SessionSnapshotData.SessionSummary summary = snapshot.get(i);
            SessionEntry entry = this.sessions.get(i);
            if (!entry.id.equals(summary.id()) || !entry.game.equals(summary.game()) || !entry.state.equals(summary.state()) || entry.playerCount != summary.players()) {
                return false;
            }
        }
        return true;
    }

    private void drawSessionRow(DrawContext context, Layout layout, int y, SessionEntry entry) {
        int x = layout.contentX;
        int width = layout.contentWidth;

        // Draw background
        context.fill(x, y, x + width, y + SESSION_ROW_HEIGHT, 0x2AFFFFFF);
        context.fill(x + 1, y + 1, x + width - 1, y + SESSION_ROW_HEIGHT - 1, 0xCC1F1F1F);

        // Draw session info
        int textX = x + 12;
        int textY = y + 8;
        context.drawText(this.textRenderer, Text.literal("Session: " + entry.id), textX, textY, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Game: " + entry.game + " (Players: " + entry.playerCount + ")"), textX, textY + 14, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("State: " + entry.state), textX, textY + 28, TEXT_MUTED, false);
    }

    private void drawShell(DrawContext context, Layout layout) {
        int x = layout.panelX;
        int y = layout.panelY;
        int width = layout.panelWidth;
        int height = layout.panelHeight;

        context.fill(x, y, x + width, y + height, 0xD0121212);
        context.fill(x + 1, y + 1, x + width - 1, y + 40, 0xCC1F1F1F);
        context.fill(x + 1, y + 41, x + width - 1, y + height - 1, 0xAA181818);
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0x66FFFFFF);
        context.fill(x, y, x + 1, y + height, 0x66FFFFFF);
        context.fill(x + width - 1, y, x + width, y + height, 0x66FFFFFF);
    }

    private void stopSession(String sessionId) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }

        // Send cleanup payload first
        ClientPlayNetworking.send(new NetworkConstants.CleanupPlayerPayload(sessionId));

        // Send stop session payload
        ClientPlayNetworking.send(new NetworkConstants.StopSessionPayload(sessionId));
        this.statusMessage = "Stopping session " + sessionId + " and returning to main server...";
    }

    private void changeSeed(String sessionId) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.ChangeSeedPayload(sessionId));
        this.statusMessage = "Changing seed for session " + sessionId + "...";
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        this.client.setScreen(new SessionScreen());
    }

    private Layout createLayout() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 24);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 24);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        return new Layout(
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            panelX + PANEL_PADDING,
            panelY + LIST_TOP_Y,
            panelWidth - (PANEL_PADDING * 2),
            panelHeight - LIST_TOP_Y - FOOTER_HEIGHT - PANEL_PADDING
        );
    }

    private record Layout(
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        int contentX,
        int listY,
        int contentWidth,
        int listHeight
    ) {
    }

    private record SessionEntry(String id, String game, String state, int playerCount) {
    }

    private enum SettingsTab {
        SESSIONS,
        SERVER,
        LAUNCHER
    }
}

