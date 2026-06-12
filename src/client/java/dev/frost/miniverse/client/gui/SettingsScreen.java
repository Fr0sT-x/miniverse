package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("'['hh:mm a , dd/MMM/yyyy']'").withZone(ZoneId.systemDefault());

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<SessionEntry> sessions = new ArrayList<>();
    private String statusMessage = "";
    private final List<ButtonWidget> sessionButtons = new ArrayList<>();
    private final List<HistoryRow> historyRows = new ArrayList<>();
    private SettingsTab activeTab = SettingsTab.SESSIONS;
    private int historyScrollOffset;
    private int historyMaxScroll;
    private HistorySortOrder historySortOrder = HistorySortOrder.LATEST_PLAYED;
    private ButtonWidget historySortToggle;

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
    private TextFieldWidget advertisedHostField;
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
    private String advertisedHostValue;

    // Launcher config state
    private TextFieldWidget maxConcurrentLaunchesField;
    private int maxConcurrentLaunchesValue;

    // Retention config state
    private TextFieldWidget maxAgeDaysField;
    private int maxAgeDaysValue;

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
        this.advertisedHostValue = serverSettings.advertisedHost();
        this.maxConcurrentLaunchesValue = SessionSnapshotData.maxConcurrentLaunches();
        SessionSnapshotData.RetentionSettings retentionSettings = SessionSnapshotData.retentionSettings();

        this.maxAgeDaysValue = retentionSettings.maxAgeDays();

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

        this.addDrawableChild(ButtonWidget.builder(Text.literal("History"), button -> {
            this.switchTab(SettingsTab.HISTORY);
        })
            .dimensions(layout.contentX + 340, layout.panelY + 45, 100, TAB_HEIGHT)
            .build());

        if (this.activeTab == SettingsTab.SERVER) {
            this.initServerSettings(layout);
        } else if (this.activeTab == SettingsTab.LAUNCHER) {
            this.initLauncherSettings(layout);
        } else if (this.activeTab == SettingsTab.HISTORY) {
            this.initHistoryActions(layout);
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
            this.sessions.add(SessionEntry.from(session));
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
            if (entry.retained) {
                continue;
            }
            int buttonX = layout.contentX + layout.contentWidth - 260;
            int buttonY = y + 8;

            ButtonWidget stopButton = ButtonWidget.builder(Text.literal("Save and return"), button -> this.confirmStopSession(entry.id))
                .dimensions(buttonX, buttonY, 95, BUTTON_HEIGHT)
                .build();
            this.addDrawableChild(stopButton);

            ButtonWidget pauseButton = ButtonWidget.builder(Text.literal(entry.paused() ? "Resume" : "Pause"), button -> this.setPaused(entry.id, !entry.paused()))
                .dimensions(buttonX + 95 + BUTTON_GAP, buttonY, 62, BUTTON_HEIGHT)
                .build();
            pauseButton.setTooltip(Tooltip.of(Text.literal(entry.paused() ? "Resume gameplay progression." : "Pause gameplay progression and freeze participants.")));
            this.addDrawableChild(pauseButton);

            ButtonWidget changeSeedButton = ButtonWidget.builder(Text.literal("Change seed"), button -> this.changeSeed(entry.id))
                .dimensions(buttonX + 95 + BUTTON_GAP + 62 + BUTTON_GAP, buttonY, 85, BUTTON_HEIGHT)
                .build();
            changeSeedButton.setTooltip(Tooltip.of(Text.literal("Restart this session with a new world seed.")));
            this.addDrawableChild(changeSeedButton);

            y += SESSION_ROW_HEIGHT + ROW_GAP;
        }
    }

    private void initHistoryActions(Layout layout) {
        int x = layout.contentX;
        int y = layout.listY + 4;
        int fieldWidth = 58;

        this.maxAgeDaysField = new TextFieldWidget(this.textRenderer, x + 160, y, fieldWidth, 20, Text.literal("Retention Days"));
        this.maxAgeDaysField.setMaxLength(3);
        this.maxAgeDaysField.setText(String.valueOf(this.maxAgeDaysValue));
        this.addDrawableChild(this.maxAgeDaysField);
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveMaxAgeDays())
            .dimensions(x + 226, y, 55, BUTTON_HEIGHT)
            .build());

        int sortWidth = 150;
        int sortX = x + layout.contentWidth - sortWidth;
        this.historySortToggle = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.getHistorySortLabel()), button -> this.toggleHistorySort())
            .dimensions(sortX, y, sortWidth, BUTTON_HEIGHT)
            .build());
        this.historySortToggle.setTooltip(Tooltip.of(Text.literal("Toggle sort order for retained sessions.")));

        this.historyRows.clear();
        int rowY = this.historyListStartY(layout);
        List<SessionEntry> retainedSessions = this.getSortedRetainedSessions();
        for (SessionEntry entry : retainedSessions) {
            int relaunchWidth = 105;
            int inspectWidth = 100;
            int deleteWidth = 70;
            int totalWidth = relaunchWidth + inspectWidth + deleteWidth + (BUTTON_GAP * 2);
            int buttonX = layout.contentX + layout.contentWidth - totalWidth;
            ButtonWidget relaunchButton = ButtonWidget.builder(Text.literal("Relaunch"), button -> this.relaunchSession(entry.id))
                .dimensions(buttonX, rowY + 8, relaunchWidth, BUTTON_HEIGHT)
                .build();
            relaunchButton.setTooltip(Tooltip.of(Text.literal("Relaunch this retained session.")));
            this.addDrawableChild(relaunchButton);

            int inspectButtonX = buttonX + relaunchWidth + BUTTON_GAP;
            ButtonWidget inspectButton = ButtonWidget.builder(Text.literal("Inspect Copy"), button -> this.inspectSession(entry.id))
                .dimensions(inspectButtonX, rowY + 8, inspectWidth, BUTTON_HEIGHT)
                .build();
            inspectButton.setTooltip(Tooltip.of(Text.literal("Launches a copied spectator world; the retained session folder is not modified.")));
            this.addDrawableChild(inspectButton);

            int deleteButtonX = inspectButtonX + inspectWidth + BUTTON_GAP;
            ButtonWidget deleteButton = ButtonWidget.builder(Text.literal("Delete"), button -> this.confirmDeleteSession(entry.id))
                .dimensions(deleteButtonX, rowY + 8, deleteWidth, BUTTON_HEIGHT)
                .build();
            deleteButton.setTooltip(Tooltip.of(Text.literal("Delete this retained session.")));
            this.addDrawableChild(deleteButton);

            this.historyRows.add(new HistoryRow(entry, relaunchButton, inspectButton, deleteButton));
            rowY += SESSION_ROW_HEIGHT + ROW_GAP;
        }

        this.updateHistoryScrollBounds(layout, retainedSessions.size());
        this.applyHistoryRowPositions(layout);
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
        y += rowHeight;

        this.advertisedHostField = new TextFieldWidget(this.textRenderer, fieldX, y, Math.max(fieldWidth, 190), 20, Text.literal("Transfer Host"));
        this.advertisedHostField.setMaxLength(255);
        this.advertisedHostField.setText(this.advertisedHostValue);
        this.addDrawableChild(this.advertisedHostField);

        ButtonWidget saveAdvertisedHost = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> this.saveAdvertisedHost())
            .dimensions(fieldX + Math.max(fieldWidth, 190) + 10, y, saveWidth, BUTTON_HEIGHT)
            .build());
        saveAdvertisedHost.setTooltip(Tooltip.of(Text.literal("Host clients use to join launched sessions.")));
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

    private void saveAdvertisedHost() {
        String value = this.advertisedHostField.getText().trim();
        if (value.isBlank()) {
            this.statusMessage = "Transfer host cannot be empty.";
            return;
        }
        this.advertisedHostValue = value;
        NbtCompound server = new NbtCompound();
        server.putString("advertisedHost", value);
        this.sendServerSettings(null, server);
        this.statusMessage = "Saved transfer host " + value + ".";
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
        this.sendServerSettings(memorySettings, serverSettings, null);
    }

    private void sendServerSettings(NbtCompound memorySettings, NbtCompound serverSettings, NbtCompound retentionSettings) {
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
        if (retentionSettings != null && !retentionSettings.getKeys().isEmpty()) {
            payload.put("retention", retentionSettings);
            hasUpdates = true;
        }
        if (!hasUpdates) {
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.ServerSettingsPayload(payload));
    }



    private void saveMaxAgeDays() {
        Integer value = this.parseInt(this.maxAgeDaysField, 0, 365, "Retention days");
        if (value == null) {
            return;
        }
        this.maxAgeDaysValue = value;
        NbtCompound retention = new NbtCompound();
        retention.putInt("maxAgeDays", value);
        this.sendServerSettings(null, null, retention);
        this.statusMessage = "Saved retention age " + value + " day(s).";
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
        if (sessionsChanged && (this.activeTab == SettingsTab.SESSIONS || this.activeTab == SettingsTab.HISTORY)) {
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
            List<SessionEntry> activeSessions = this.sessions.stream().filter(entry -> !entry.retained).toList();
            if (activeSessions.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Waiting for session data..."), titleCenterX, layout.listY + 12, TEXT_PRIMARY);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No active sessions."), titleCenterX, layout.listY + 28, TEXT_MUTED);
            } else {
                int y = layout.listY;
                for (SessionEntry entry : activeSessions) {
                    this.drawSessionRow(context, layout, y, entry);
                    y += SESSION_ROW_HEIGHT + ROW_GAP;
                }
            }
        } else if (this.activeTab == SettingsTab.SERVER) {
            this.drawServerFieldLabels(context, layout);
        } else if (this.activeTab == SettingsTab.HISTORY) {
            this.drawHistory(context, layout);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeTab == SettingsTab.HISTORY && this.historyMaxScroll > 0) {
            int delta = (int) Math.round(-verticalAmount * 18);
            if (delta != 0) {
                this.historyScrollOffset = Math.clamp(this.historyScrollOffset + delta, 0, this.historyMaxScroll);
                this.applyHistoryRowPositions(this.createLayout());
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
        y += rowHeight * 5;
        context.drawText(this.textRenderer, Text.literal("Transfer host:"), x, y + 6, TEXT_PRIMARY, false);
        y += rowHeight * 2 + 6;
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
            this.sessions.add(SessionEntry.from(session));
        }
        return true;
    }

    private boolean matchesSnapshot(List<SessionSnapshotData.SessionSummary> snapshot) {
        for (int i = 0; i < snapshot.size(); i++) {
            SessionSnapshotData.SessionSummary summary = snapshot.get(i);
            SessionEntry entry = this.sessions.get(i);
            if (!entry.id.equals(summary.id()) || !entry.game.equals(summary.game()) || !entry.state.equals(summary.state()) || entry.playerCount != summary.players() || entry.inspectable != summary.inspectable() || entry.retained != summary.retained()) {
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

    private void drawHistory(DrawContext context, Layout layout) {
        int x = layout.contentX;
        context.drawText(this.textRenderer, Text.literal("Delete sessions older than"), x, layout.listY + 10, TEXT_MUTED, false);
        context.drawText(this.textRenderer, Text.literal("days (0 to disable)"), x + 330, layout.listY + 10, TEXT_MUTED, false);

        List<SessionEntry> retainedSessions = this.getSortedRetainedSessions();
        this.updateHistoryScrollBounds(layout, retainedSessions.size());
        this.applyHistoryRowPositions(layout);
        if (retainedSessions.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No retained sessions."), layout.contentX + layout.contentWidth / 2, layout.listY + 52, TEXT_MUTED);
            return;
        }

        int listTopY = this.historyListStartY(layout);
        int listHeight = this.historyListHeight(layout);
        context.enableScissor(x, listTopY, x + layout.contentWidth, listTopY + listHeight);

        int y = listTopY - this.historyScrollOffset;
        for (SessionEntry entry : retainedSessions) {
            context.fill(x, y, x + layout.contentWidth, y + SESSION_ROW_HEIGHT, 0x2AFFFFFF);
            context.fill(x + 1, y + 1, x + layout.contentWidth - 1, y + SESSION_ROW_HEIGHT - 1, 0xCC1F1F1F);

            int textX = x + 12;
            int textY = y + 8;
            context.drawText(this.textRenderer, Text.literal(entry.id + " - " + entry.game), textX, textY, TEXT_PRIMARY, false);
            context.drawText(this.textRenderer, Text.literal("State: " + entry.state + "  Players: " + entry.playerCount), textX, textY + 14, TEXT_MUTED, false);
            context.drawText(this.textRenderer, Text.literal("Created: " + formatTime(entry.createdAtMillis) + "  Updated: " + formatTime(entry.updatedAtMillis)), textX, textY + 28, TEXT_MUTED, false);
            context.drawText(this.textRenderer, Text.literal("Played: " + formatDuration(entry.playedMillis) + "  Seed: " + entry.seed), textX, textY + 42, TEXT_MUTED, false);
            if (!entry.inspectable) {
                context.drawText(this.textRenderer, Text.literal("No world copy available"), textX, textY + 56, 0xFFFFB0B0, false);
            }
            y += SESSION_ROW_HEIGHT + ROW_GAP;
        }

        context.disableScissor();
    }

    private void toggleHistorySort() {
        this.historySortOrder = this.historySortOrder == HistorySortOrder.LATEST_PLAYED ? HistorySortOrder.OLDEST_PLAYED : HistorySortOrder.LATEST_PLAYED;
        this.historyScrollOffset = 0;
        this.init();
    }

    private String getHistorySortLabel() {
        return this.historySortOrder == HistorySortOrder.LATEST_PLAYED ? "Sort: Latest Played" : "Sort: Oldest Played";
    }

    private List<SessionEntry> getSortedRetainedSessions() {
        Comparator<SessionEntry> comparator = Comparator
            .comparingLong(this::historySortTimestamp)
            .thenComparing(entry -> entry.id);
        if (this.historySortOrder == HistorySortOrder.LATEST_PLAYED) {
            comparator = comparator.reversed();
        }
        return this.sessions.stream()
            .filter(entry -> entry.retained)
            .sorted(comparator)
            .toList();
    }

    private long historySortTimestamp(SessionEntry entry) {
        if (entry.updatedAtMillis > 0L) {
            return entry.updatedAtMillis;
        }
        if (entry.launchedAtMillis > 0L) {
            return entry.launchedAtMillis;
        }
        return entry.createdAtMillis;
    }

    private int historyListStartY(Layout layout) {
        return layout.listY + 38;
    }

    private int historyListHeight(Layout layout) {
        int headerHeight = this.historyListStartY(layout) - layout.listY;
        return Math.max(0, layout.listHeight - headerHeight);
    }

    private void updateHistoryScrollBounds(Layout layout, int rowCount) {
        int listHeight = this.historyListHeight(layout);
        int totalRowsHeight = rowCount <= 0 ? 0 : (rowCount * (SESSION_ROW_HEIGHT + ROW_GAP)) - ROW_GAP;
        this.historyMaxScroll = Math.max(0, totalRowsHeight - listHeight);
        this.historyScrollOffset = Math.clamp(this.historyScrollOffset, 0, this.historyMaxScroll);
    }

    private void applyHistoryRowPositions(Layout layout) {
        int listTopY = this.historyListStartY(layout);
        int listBottomY = listTopY + this.historyListHeight(layout);
        int rowY = listTopY - this.historyScrollOffset;
        for (HistoryRow row : this.historyRows) {
            int buttonY = rowY + 8;
            boolean visible = rowY + SESSION_ROW_HEIGHT > listTopY && rowY < listBottomY;
            row.relaunchButton.setY(buttonY);
            row.relaunchButton.visible = visible;
            row.relaunchButton.active = visible && row.entry.retained;
            row.inspectButton.setY(buttonY);
            row.inspectButton.visible = visible;
            row.inspectButton.active = visible && row.entry.retained && row.entry.inspectable;
            row.deleteButton.setY(buttonY);
            row.deleteButton.visible = visible;
            row.deleteButton.active = visible && row.entry.retained;
            rowY += SESSION_ROW_HEIGHT + ROW_GAP;
        }
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0L) {
            return "unknown";
        }
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String formatDuration(long millis) {
        long totalMinutes = Math.max(0L, millis) / 60_000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours + "h " + minutes + "m";
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

    private void confirmStopSession(String sessionId) {
        this.client.setScreen(new ConfirmScreen(
            confirmed -> {
                this.client.setScreen(this);
                if (confirmed) {
                    this.stopSession(sessionId);
                }
            },
            Text.literal("Save and return players?"),
            Text.literal("This will save session " + sessionId + " and return players to the main server."),
            Text.literal("Yes"),
            Text.literal("No")
        ));
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
        this.statusMessage = "Saving session " + sessionId + " and returning to main server...";
    }

    private void setPaused(String sessionId, boolean paused) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.PauseSessionPayload(sessionId, paused));
        this.statusMessage = (paused ? "Pausing session " : "Resuming session ") + sessionId + "...";
    }

    private void changeSeed(String sessionId) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.ChangeSeedPayload(sessionId));
        this.statusMessage = "Changing seed for session " + sessionId + "...";
    }

    private void inspectSession(String sessionId) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.InspectSessionPayload(sessionId));
        this.statusMessage = "Launching inspection copy for " + sessionId + "...";
    }

    private void relaunchSession(String sessionId) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.RelaunchSessionPayload(sessionId));
        this.statusMessage = "Relaunching session " + sessionId + "...";
    }

    private void confirmDeleteSession(String sessionId) {
        this.client.setScreen(new ConfirmScreen(
            confirmed -> {
                this.client.setScreen(this);
                if (confirmed) {
                    this.deleteSession(sessionId);
                }
            },
            Text.literal("Delete retained session?"),
            Text.literal("This permanently deletes session " + sessionId + "."),
            Text.literal("Yes"),
            Text.literal("No")
        ));
    }

    private void deleteSession(String sessionId) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.DeleteSessionPayload(sessionId));
        this.statusMessage = "Deleting session " + sessionId + "...";
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

    private record SessionEntry(String id, String game, String state, long seed, int playerCount, long createdAtMillis, long launchedAtMillis, long updatedAtMillis, long playedMillis, boolean inspectable, boolean retained) {
        private static SessionEntry from(SessionSnapshotData.SessionSummary summary) {
            return new SessionEntry(
                summary.id(),
                summary.game(),
                summary.state(),
                summary.seed(),
                summary.players(),
                summary.createdAtMillis(),
                summary.launchedAtMillis(),
                summary.updatedAtMillis(),
                summary.playedMillis(),
                summary.inspectable(),
                summary.retained()
            );
        }

        private boolean paused() {
            return "PAUSED".equalsIgnoreCase(this.state);
        }
    }

    private record HistoryRow(SessionEntry entry, ButtonWidget relaunchButton, ButtonWidget inspectButton, ButtonWidget deleteButton) {
    }

    private enum SettingsTab {
        SESSIONS,
        SERVER,
        LAUNCHER,
        HISTORY
    }

    private enum HistorySortOrder {
        LATEST_PLAYED,
        OLDEST_PLAYED
    }
}

