package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.bountyhunt.BountyHuntDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BountyHuntSetupScreen extends Screen {
    private static final String GAME_ID = BountyHuntDefinition.ID;
    private static final int PANEL_PADDING = 16;
    private static final int PANEL_MAX_WIDTH = 920;
    private static final int PANEL_MAX_HEIGHT = 700;
    private static final int TITLE_TOP_Y = 12;
    private static final int SUBTITLE_TOP_Y = 28;
    private static final int SESSION_ROW_Y = 48;
    private static final int LIST_TOP_Y = 96;
    private static final int COLUMN_HEADER_HEIGHT = 18;
    private static final int PLAYER_ROW_HEIGHT = 20;
    private static final int SETTINGS_HEIGHT = 200;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_SECONDARY = 0xFFD8D8D8;
    private static final int TEXT_MUTED = 0xFFB8B8B8;
    private static final int TEXT_STATUS = 0xFFA0FFA0;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Set<String> selectedPlayerUuids = new LinkedHashSet<>();
    private TextFieldWidget sessionNameField;
    private TextFieldWidget graceField;
    private TextFieldWidget invincibilityField;
    private TextFieldWidget scoreToWinField;
    private TextFieldWidget targetSwapField;
    private TextFieldWidget compassCooldownField;
    private TextFieldWidget trackerItemField;
    private ButtonWidget trackerToggle;
    private ButtonWidget netherTrackingToggle;
    private ButtonWidget startButton;
    private ButtonWidget selectAllButton;
    private ButtonWidget clearButton;
    private ButtonWidget backButton;
    private int playerScrollOffset;
    private boolean trackerEnabled = true;
    private boolean netherTrackingEnabled = true;
    private String statusMessage = "";

    public BountyHuntSetupScreen() {
        super(Text.literal("Bounty Hunt Setup"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.selectedPlayerUuids.clear();
        this.playerScrollOffset = 0;

        Layout layout = this.createLayout();

        this.sessionNameField = new TextFieldWidget(this.textRenderer, layout.sessionFieldX, layout.sessionFieldY, layout.sessionFieldWidth, 20, Text.literal("session name"));
        this.sessionNameField.setMaxLength(48);
        this.sessionNameField.setText("bountyhunt-" + System.currentTimeMillis());
        this.addDrawableChild(this.sessionNameField);

        this.startButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start Match"), button -> this.createSession())
            .dimensions(layout.startButtonX, layout.sessionFieldY, layout.startButtonWidth, 20)
            .build());
        this.startButton.setTooltip(Tooltip.of(Text.literal("Create and launch a Bounty Hunt session.")));

        this.selectAllButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Select All"), button -> this.selectAll())
            .dimensions(layout.selectAllX, layout.sessionFieldY, 86, 20)
            .build());

        this.clearButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), button -> this.clearSelection())
            .dimensions(layout.clearX, layout.sessionFieldY, 60, 20)
            .build());

        SettingsRowLayout row = this.createSettingsRowLayout(layout);
        int rowY = layout.settingsY + 24;
        int rowGap = 26;

        this.graceField = this.makeNumberField(row.fieldX, rowY, row.fieldWidth, "grace seconds", "300");
        this.invincibilityField = this.makeNumberField(row.fieldX, rowY + rowGap, row.fieldWidth, "invincibility seconds", "120");
        this.scoreToWinField = this.makeNumberField(row.fieldX, rowY + rowGap * 2, row.fieldWidth, "score to win", "5");
        this.targetSwapField = this.makeNumberField(row.fieldX, rowY + rowGap * 3, row.fieldWidth, "target swap seconds", "0");
        this.compassCooldownField = this.makeNumberField(row.fieldX, rowY + rowGap * 4, row.fieldWidth, "tracker cooldown seconds", "2");
        this.trackerItemField = new TextFieldWidget(this.textRenderer, row.fieldX, rowY + rowGap * 5, row.fieldWidth, 20, Text.literal("tracker item"));
        this.trackerItemField.setMaxLength(64);
        this.trackerItemField.setText("minecraft:compass");
        this.addDrawableChild(this.trackerItemField);

        this.trackerToggle = this.addDrawableChild(ButtonWidget.builder(this.trackerToggleLabel(), button -> {
            this.trackerEnabled = !this.trackerEnabled;
            button.setMessage(this.trackerToggleLabel());
        }).dimensions(row.toggleX, rowY, row.toggleWidth, 20).build());

        this.netherTrackingToggle = this.addDrawableChild(ButtonWidget.builder(this.netherToggleLabel(), button -> {
            this.netherTrackingEnabled = !this.netherTrackingEnabled;
            button.setMessage(this.netherToggleLabel());
        }).dimensions(row.toggleX, rowY + rowGap, row.toggleWidth, 20).build());

        this.backButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(new SessionScreen()))
            .dimensions(layout.footerBackX, layout.footerButtonsY, 64, FOOTER_BUTTON_HEIGHT)
            .build());

        this.requestSnapshot();
    }

    @Override
    public void tick() {
        super.tick();
        boolean connected = this.client.player != null;
        boolean hasSelection = this.selectedPlayerUuids.size() >= 2;

        this.sessionNameField.active = connected;
        this.startButton.active = connected && hasSelection && !this.sessionNameField.getText().trim().isEmpty();
        this.selectAllButton.active = connected;
        this.clearButton.active = connected;
        this.trackerToggle.active = connected;
        this.netherTrackingToggle.active = connected;
        this.graceField.active = connected;
        this.invincibilityField.active = connected;
        this.scoreToWinField.active = connected;
        this.targetSwapField.active = connected;
        this.compassCooldownField.active = connected;
        this.trackerItemField.active = connected;
        this.backButton.active = true;

        this.syncSelection();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout layout = this.createLayout();

        int selectedRow = this.getRosterRowAt(mouseX, mouseY, layout);
        if (selectedRow >= 0) {
            SessionSnapshotData.RosterEntry entry = SessionSnapshotData.roster().get(selectedRow);
            this.togglePlayerSelection(entry.uuid());
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = this.createLayout();
        if (!this.isWithin(mouseX, mouseY, layout.listX, layout.listY, layout.listWidth, layout.listHeight)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        int delta = (int) Math.signum(verticalAmount);
        int maxScroll = this.getMaxRosterScroll(layout);
        if (maxScroll > 0) {
            this.playerScrollOffset = clamp(this.playerScrollOffset - delta, 0, maxScroll);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = this.createLayout();
        this.drawPanelShell(context, layout);
        this.drawRosterPanel(context, layout);
        this.drawSettingsPanel(context, layout);

        super.render(context, mouseX, mouseY, delta);

        int centerX = layout.panelX + layout.panelWidth / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, layout.panelY + TITLE_TOP_Y, TEXT_WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Assign players, tune rules, and start the hunt."), centerX, layout.panelY + SUBTITLE_TOP_Y, TEXT_MUTED);

        context.drawText(this.textRenderer, Text.literal("Session Name:"), layout.panelX + PANEL_PADDING, layout.sessionFieldY + 6, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Players"), layout.listX + 8, layout.listY - 10, TEXT_SECONDARY, false);
        this.drawSettingsLabels(context, layout);

        if (!this.statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), centerX, layout.footerButtonsY + 24, TEXT_STATUS);
        }
    }

    private void drawPanelShell(DrawContext context, Layout layout) {
        int x = layout.panelX;
        int y = layout.panelY;
        int width = layout.panelWidth;
        int height = layout.panelHeight;

        context.fill(x, y, x + width, y + height, 0xD0121212);
        context.fill(x + 1, y + 1, x + width - 1, y + 33, 0xCC202020);
        context.fill(x + 1, y + 34, x + width - 1, y + height - 1, 0xAA181818);
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0x66FFFFFF);
        context.fill(x, y, x + 1, y + height, 0x66FFFFFF);
        context.fill(x + width - 1, y, x + width, y + height, 0x66FFFFFF);
    }

    private void drawRosterPanel(DrawContext context, Layout layout) {
        int x = layout.listX;
        int y = layout.listY;
        int width = layout.listWidth;
        int height = layout.listHeight;
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        int visibleRows = this.getRosterVisibleRows(layout);
        int rowsToDraw = Math.min(Math.max(0, roster.size() - this.playerScrollOffset), visibleRows);

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
        context.drawText(this.textRenderer, Text.literal("Players (" + roster.size() + ")"), x + 8, y + 5, TEXT_WHITE, false);

        if (roster.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No players online."), x + width / 2, y + height / 2 - 4, TEXT_MUTED);
            return;
        }

        for (int row = 0; row < rowsToDraw; row++) {
            int rosterIndex = this.playerScrollOffset + row;
            SessionSnapshotData.RosterEntry entry = roster.get(rosterIndex);
            int rowY = y + COLUMN_HEADER_HEIGHT + 2 + row * PLAYER_ROW_HEIGHT;
            boolean selected = this.selectedPlayerUuids.contains(entry.uuid());
            int background = selected ? 0xAA3A5C8F : 0x33222222;
            context.fill(x + 1, rowY, x + width - 1, rowY + PLAYER_ROW_HEIGHT - 1, background);
            context.fill(x + 4, rowY + 3, x + 8, rowY + PLAYER_ROW_HEIGHT - 4, 0xFF5B8CFF);
            context.drawText(this.textRenderer, Text.literal(entry.name()), x + 14, rowY + 5, TEXT_SECONDARY, false);
        }

        this.drawScrollBar(context, x, y, width, height, roster.size(), visibleRows, this.playerScrollOffset);
    }

    private void drawSettingsPanel(DrawContext context, Layout layout) {
        int x = layout.panelX + PANEL_PADDING;
        int y = layout.settingsY;
        int width = layout.panelWidth - (PANEL_PADDING * 2);
        int height = layout.settingsHeight;

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
    }

    private void drawSettingsLabels(DrawContext context, Layout layout) {
        SettingsRowLayout row = this.createSettingsRowLayout(layout);
        int y = layout.settingsY;
        int rowY = y + 24;
        int rowGap = 26;

        context.drawText(this.textRenderer, Text.literal("Bounty Hunt Settings"), layout.panelX + PANEL_PADDING + 8, y + 6, TEXT_WHITE, false);
        context.drawText(this.textRenderer, Text.literal("Grace Period:"), row.labelX, rowY + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Respawn Invincibility:"), row.labelX, rowY + rowGap + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Score To Win:"), row.labelX, rowY + rowGap * 2 + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Target Swap:"), row.labelX, rowY + rowGap * 3 + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Tracker Cooldown:"), row.labelX, rowY + rowGap * 4 + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Tracker Item:"), row.labelX, rowY + rowGap * 5 + 5, TEXT_SECONDARY, false);
    }

    private TextFieldWidget makeNumberField(int x, int y, int width, String placeholder, String value) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(6);
        field.setText(value);
        this.addDrawableChild(field);
        return field;
    }

    private void drawScrollBar(DrawContext context, int x, int y, int width, int height, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows || visibleRows <= 0) {
            return;
        }

        int trackX = x + width - 5;
        int trackY = y + COLUMN_HEADER_HEIGHT + 2;
        int trackHeight = height - COLUMN_HEADER_HEIGHT - 4;
        int thumbHeight = Math.max(12, (int) ((trackHeight * (double) visibleRows) / totalRows));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbY = trackY + (int) (((trackHeight - thumbHeight) * (double) scrollOffset) / maxScroll);

        context.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0x55000000);
        context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xCCB0B0B0);
    }

    private void createSession() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        String sessionName = this.sessionNameField.getText().trim();
        if (sessionName.isEmpty()) {
            this.statusMessage = "Enter a session name.";
            return;
        }

        if (this.selectedPlayerUuids.size() < 2) {
            this.statusMessage = "Select at least two players.";
            return;
        }

        NbtCompound plan = new NbtCompound();
        plan.putString("game", GAME_ID);
        plan.putString("name", sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.buildSettingsCompound());

        // Don't add groups - all players will be on the same server
        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(GAME_ID, sessionName, plan));
        this.statusMessage = "Requested Bounty Hunt session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putInt("gracePeriodSeconds", this.readInt(this.graceField, 300, 0, 3600, "Grace period must be a number."));
        settings.putInt("respawnInvincibilitySeconds", this.readInt(this.invincibilityField, 120, 0, 3600, "Invincibility must be a number."));
        settings.putInt("scoreToWin", this.readInt(this.scoreToWinField, 5, 1, 99, "Score to win must be a number."));
        settings.putInt("targetSwapIntervalSeconds", this.readInt(this.targetSwapField, 0, 0, 3600, "Target swap must be a number."));
        settings.putInt("compassCooldownSeconds", this.readInt(this.compassCooldownField, 2, 0, 300, "Cooldown must be a number."));
        settings.putBoolean("trackerEnabled", this.trackerEnabled);
        settings.putBoolean("netherTracking", this.netherTrackingEnabled);
        settings.putString("trackerItemId", this.trackerItemField.getText().trim());
        return settings;
    }

    private int readInt(TextFieldWidget field, int fallback, int min, int max, String errorMessage) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
        try {
            int value = clamp(Integer.parseInt(text), min, max);
            field.setText(Integer.toString(value));
            return value;
        } catch (NumberFormatException ignored) {
            this.statusMessage = errorMessage;
            field.setText(Integer.toString(fallback));
            return fallback;
        }
    }

    private void selectAll() {
        this.selectedPlayerUuids.clear();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            this.selectedPlayerUuids.add(entry.uuid());
        }
        this.statusMessage = "Selected all players.";
    }

    private void clearSelection() {
        this.selectedPlayerUuids.clear();
        this.statusMessage = "Selection cleared.";
    }

    private void togglePlayerSelection(String uuid) {
        if (this.selectedPlayerUuids.contains(uuid)) {
            this.selectedPlayerUuids.remove(uuid);
        } else {
            this.selectedPlayerUuids.add(uuid);
        }
    }

    private void syncSelection() {
        Set<String> rosterUuids = new LinkedHashSet<>();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            rosterUuids.add(entry.uuid());
        }
        this.selectedPlayerUuids.retainAll(rosterUuids);
        if (this.selectedPlayerUuids.isEmpty() && !rosterUuids.isEmpty()) {
            this.selectedPlayerUuids.addAll(rosterUuids);
        }

        Layout layout = this.createLayout();
        this.playerScrollOffset = clamp(this.playerScrollOffset, 0, this.getMaxRosterScroll(layout));
    }

    private int getRosterVisibleRows(Layout layout) {
        return Math.max(0, (layout.listHeight - COLUMN_HEADER_HEIGHT - 4) / PLAYER_ROW_HEIGHT);
    }

    private int getMaxRosterScroll(Layout layout) {
        return Math.max(0, SessionSnapshotData.roster().size() - this.getRosterVisibleRows(layout));
    }

    private int getRosterRowAt(double mouseX, double mouseY, Layout layout) {
        if (!this.isWithin(mouseX, mouseY, layout.listX, layout.listY, layout.listWidth, layout.listHeight)) {
            return -1;
        }

        int rowStartY = layout.listY + COLUMN_HEADER_HEIGHT + 2;
        int visibleRows = this.getRosterVisibleRows(layout);
        int row = (int) ((mouseY - rowStartY) / PLAYER_ROW_HEIGHT);
        int index = this.playerScrollOffset + row;
        if (row < 0 || row >= visibleRows || index < 0 || index >= SessionSnapshotData.roster().size()) {
            return -1;
        }
        return index;
    }

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void requestSnapshot() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("refresh"));
        this.statusMessage = "Requested current roster.";
    }

    private NbtCompound member(SessionSnapshotData.RosterEntry entry) {
        NbtCompound compound = new NbtCompound();
        compound.putString("uuid", entry.uuid());
        compound.putString("name", entry.name());
        return compound;
    }

    private Text trackerToggleLabel() {
        return Text.literal("Tracker: " + (this.trackerEnabled ? "ON" : "OFF"));
    }

    private Text netherToggleLabel() {
        return Text.literal("Nether Tracking: " + (this.netherTrackingEnabled ? "ON" : "OFF"));
    }

    private SettingsRowLayout createSettingsRowLayout(Layout layout) {
        int labelWidth = this.calculateMaxLabelWidth();
        int labelX = layout.panelX + PANEL_PADDING + 10;
        int fieldX = labelX + labelWidth + 8;
        int toggleWidth = 150;
        int toggleX = layout.panelX + layout.panelWidth - PANEL_PADDING - toggleWidth;
        int fieldWidth = Math.min(220, toggleX - fieldX - 10);
        return new SettingsRowLayout(labelX, fieldX, fieldWidth, toggleX, toggleWidth);
    }

    private int calculateMaxLabelWidth() {
        String[] labels = {
            "Grace Period:",
            "Respawn Invincibility:",
            "Score To Win:",
            "Target Swap:",
            "Tracker Cooldown:",
            "Tracker Item:"
        };
        int maxWidth = 0;
        for (String label : labels) {
            maxWidth = Math.max(maxWidth, this.textRenderer.getWidth(label));
        }
        return maxWidth + 4;
    }

    private Layout createLayout() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 24);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 24);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        int sessionFieldX = panelX + PANEL_PADDING + 110;
        int startButtonWidth = 110;
        int startButtonX = panelX + panelWidth - PANEL_PADDING - startButtonWidth;
        int clearX = startButtonX - 6 - 60;
        int selectAllX = clearX - 6 - 86;
        int sessionFieldWidth = Math.max(220, selectAllX - 12 - sessionFieldX);

        int listY = panelY + LIST_TOP_Y;
        int settingsY = panelY + panelHeight - SETTINGS_HEIGHT - 48;
        int listHeight = Math.max(160, settingsY - listY - 12);

        int footerButtonsY = panelY + panelHeight - 32;
        int footerBackX = panelX + (panelWidth - 64) / 2;

        return new Layout(
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            sessionFieldX,
            panelY + SESSION_ROW_Y,
            sessionFieldWidth,
            startButtonX,
            startButtonWidth,
            selectAllX,
            clearX,
            panelX + PANEL_PADDING,
            listY,
            panelWidth - (PANEL_PADDING * 2),
            listHeight,
            settingsY,
            SETTINGS_HEIGHT,
            footerButtonsY,
            footerBackX
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private record Layout(
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        int sessionFieldX,
        int sessionFieldY,
        int sessionFieldWidth,
        int startButtonX,
        int startButtonWidth,
        int selectAllX,
        int clearX,
        int listX,
        int listY,
        int listWidth,
        int listHeight,
        int settingsY,
        int settingsHeight,
        int footerButtonsY,
        int footerBackX
    ) {
    }

    private record SettingsRowLayout(int labelX, int fieldX, int fieldWidth, int toggleX, int toggleWidth) {
    }
}


