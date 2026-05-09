package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionMemoryConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<SessionEntry> sessions = new ArrayList<>();
    private String statusMessage = "";
    private final List<ButtonWidget> sessionButtons = new ArrayList<>();

    // Memory config state
    private boolean showMemorySettings = false;
    private TextFieldWidget maxHeapField;
    private TextFieldWidget initialHeapField;
    private ButtonWidget enabledToggle;
    private int maxHeapValue;
    private int initialHeapValue;
    private boolean memoryEnabled;

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
        SessionMemoryConfig config = SessionMemoryConfig.getInstance();
        this.maxHeapValue = config.getMaxHeap();
        this.initialHeapValue = config.getInitialHeap();
        this.memoryEnabled = config.isEnabled();

        // Request session list from server
        if (this.client.player != null) {
            ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("refresh"));
        }

        this.refreshSessionsFromSnapshot();

        Layout layout = this.createLayout();

        // Tab buttons
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📋 Sessions"), button -> {
            this.switchTab(false);
        })
            .dimensions(layout.contentX, layout.panelY + 45, 100, TAB_HEIGHT)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("💾 Memory"), button -> {
            this.switchTab(true);
        })
            .dimensions(layout.contentX + 110, layout.panelY + 45, 100, TAB_HEIGHT)
            .build());

        if (this.showMemorySettings) {
            this.initMemorySettings(layout);
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

    private void switchTab(boolean memorySettings) {
        if (this.showMemorySettings == memorySettings) {
            return;
        }

        this.showMemorySettings = memorySettings;
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

            ButtonWidget opButton = ButtonWidget.builder(Text.literal("👑 Grant OP"), button -> this.grantOp(entry.id))
                .dimensions(buttonX + 95 + BUTTON_GAP, buttonY, 85, BUTTON_HEIGHT)
                .build();
            opButton.setTooltip(Tooltip.of(Text.literal("Grant operator role to yourself on this session.")));
            this.addDrawableChild(opButton);

            y += SESSION_ROW_HEIGHT + ROW_GAP;
        }
    }

    private void initMemorySettings(Layout layout) {
        int startY = layout.listY + 20;
        int fieldWidth = 100;
        int labelWidth = 150;

        // Enable/Disable toggle
        this.enabledToggle = this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Memory Limits: " + (this.memoryEnabled ? "ON" : "OFF")),
            button -> {
                this.memoryEnabled = !this.memoryEnabled;
                SessionMemoryConfig.getInstance().setEnabled(this.memoryEnabled);
                button.setMessage(Text.literal("Memory Limits: " + (this.memoryEnabled ? "ON" : "OFF")));
                this.statusMessage = "Memory limits " + (this.memoryEnabled ? "enabled" : "disabled") + ".";
            })
            .dimensions(layout.contentX, startY, 200, BUTTON_HEIGHT)
            .build());

        // Max Heap field
        this.maxHeapField = new TextFieldWidget(this.textRenderer, layout.contentX, startY + 35, fieldWidth, 20, Text.literal("Max Heap"));
        this.maxHeapField.setMaxLength(3);
        this.maxHeapField.setText(String.valueOf(this.maxHeapValue));
        this.addDrawableChild(this.maxHeapField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Set Max Heap"), button -> this.updateMaxHeap())
            .dimensions(layout.contentX + fieldWidth + 10, startY + 35, 120, BUTTON_HEIGHT)
            .build());

        // Initial Heap field
        this.initialHeapField = new TextFieldWidget(this.textRenderer, layout.contentX, startY + 70, fieldWidth, 20, Text.literal("Initial Heap"));
        this.initialHeapField.setMaxLength(3);
        this.initialHeapField.setText(String.valueOf(this.initialHeapValue));
        this.addDrawableChild(this.initialHeapField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Set Initial Heap"), button -> this.updateInitialHeap())
            .dimensions(layout.contentX + fieldWidth + 10, startY + 70, 120, BUTTON_HEIGHT)
            .build());
    }

    private void updateMaxHeap() {
        try {
            int value = Integer.parseInt(this.maxHeapField.getText().trim());
            if (value < 1 || value > 128) {
                this.statusMessage = "Max heap must be between 1 and 128 GB.";
                return;
            }
            SessionMemoryConfig.getInstance().setMaxHeap(value);
            this.maxHeapValue = value;
            this.statusMessage = "Max heap set to " + value + " GB. New sessions will use this limit.";
        } catch (NumberFormatException e) {
            this.statusMessage = "Invalid number for max heap.";
        }
    }

    private void updateInitialHeap() {
        try {
            int value = Integer.parseInt(this.initialHeapField.getText().trim());
            if (value < 1 || value > this.maxHeapValue) {
                this.statusMessage = "Initial heap must be between 1 and " + this.maxHeapValue + " GB.";
                return;
            }
            SessionMemoryConfig.getInstance().setInitialHeap(value);
            this.initialHeapValue = value;
            this.statusMessage = "Initial heap set to " + value + " GB. New sessions will use this limit.";
        } catch (NumberFormatException e) {
            this.statusMessage = "Invalid number for initial heap.";
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean sessionsChanged = this.syncSessionsFromSnapshot();
        if (sessionsChanged && !this.showMemorySettings) {
            this.init();
            return;
        }

        Layout layout = this.createLayout();
        this.drawShell(context, layout);

        int titleCenterX = layout.contentX + layout.contentWidth / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, titleCenterX, layout.panelY + TITLE_TOP_Y, TEXT_WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Manage your game sessions and memory allocation."), titleCenterX, layout.panelY + SUBTITLE_TOP_Y, TEXT_MUTED);

        if (!this.showMemorySettings) {
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
        } else {
            // Draw memory settings info
            context.drawText(this.textRenderer, Text.literal("Max Heap (GB): For maximum memory per session"), layout.contentX, layout.listY, TEXT_PRIMARY, false);
            context.drawText(this.textRenderer, Text.literal("Initial Heap (GB): Starting memory allocation per session"), layout.contentX, layout.listY + 100, TEXT_PRIMARY, false);
            context.drawText(this.textRenderer, Text.literal("Changes apply to all new sessions created after this."), layout.contentX, layout.listY + 150, TEXT_MUTED, false);
        }

        // Draw status message
        if (!this.statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), titleCenterX, layout.panelY + layout.panelHeight - 40, 0xA0FFA0);
        }

        super.render(context, mouseX, mouseY, delta);
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

    private void grantOp(String sessionId) {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.GrantOpPayload(sessionId));
        this.statusMessage = "Operator access granted for session " + sessionId + ".";
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
}




