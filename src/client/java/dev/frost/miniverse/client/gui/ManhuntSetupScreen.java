package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionGameType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ManhuntSetupScreen extends Screen {
    private static final int PANEL_PADDING = 16;
    private static final int PANEL_MAX_WIDTH = 980;
    private static final int PANEL_MAX_HEIGHT = 620;
    private static final int TITLE_TOP_Y = 12;
    private static final int SUBTITLE_TOP_Y = 28;
    private static final int SESSION_ROW_Y = 48;
    private static final int COLUMN_TOP_Y = 86;
    private static final int COLUMN_HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    private static final int COLUMN_GAP = 12;
    private static final int SETTINGS_SECTION_HEIGHT = 210;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int FOOTER_BUTTON_GAP = 8;
    private static final int TOP_BUTTON_GAP = 8;
    private static final int TOP_ACTION_HEIGHT = 20;
    private static final int GRACE_MIN_SECONDS = 0;
    private static final int GRACE_MAX_SECONDS = 120;
    private static final int RESPAWN_DELAY_DEFAULT_SECONDS = 5 * 60;
    private static final int RESPAWN_DELAY_MAX_SECONDS = 3600;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_SECONDARY = 0xFFD8D8D8;
    private static final int TEXT_MUTED = 0xFFB8B8B8;
    private static final int TEXT_STATUS = 0xFFA0FFA0;
    private static final int SETTINGS_FIELD_MAX_WIDTH = 260;

    private static final int[] COLUMN_COLORS = {0x6F7682, 0x4E88FF, 0xE05555};

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<ColumnState> columns = new ArrayList<>();
    private TextFieldWidget sessionNameField;
    private ButtonWidget autoBalanceButton;
    private ButtonWidget startButton;
    private ButtonWidget assignSpeedrunnerButton;
    private ButtonWidget assignHunterButton;
    private ButtonWidget removeFromTeamButton;
    // Toggles and controls for settings use aligned rows below
    private ButtonWidget huntersCompassToggle;
    private ButtonWidget netherTrackingToggle;
    private ButtonWidget seedModeButton;
    private TextFieldWidget gracePeriodField;
    private TextFieldWidget respawnDelayField;
    // saveGraceButton removed — grace is applied directly from the text field
    private TextFieldWidget seedValueField;
    private ButtonWidget savePresetButton;
    private ButtonWidget resetButton;
    private ButtonWidget backButton;
    private String selectedPlayerUuid = "";
    private boolean huntersCompassEnabled = true;
    private boolean netherTrackingEnabled = true;
    private int gracePeriodSeconds = 30;
    private int respawnDelaySeconds = RESPAWN_DELAY_DEFAULT_SECONDS;
    private SeedMode seedMode = SeedMode.RANDOM;
    private String statusMessage = "";

    public ManhuntSetupScreen() {
        super(Text.literal("Manhunt Setup"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.columns.clear();
        this.columns.add(new ColumnState(ColumnKind.AVAILABLE, "Available Players", COLUMN_COLORS[0]));
        this.columns.add(new ColumnState(ColumnKind.SPEEDRUNNER, "Speedrunners", COLUMN_COLORS[1]));
        this.columns.add(new ColumnState(ColumnKind.HUNTER, "Hunters", COLUMN_COLORS[2]));

        Layout layout = this.createLayout();

        this.sessionNameField = new TextFieldWidget(this.textRenderer, layout.sessionFieldX, layout.sessionFieldY, layout.sessionFieldWidth, 20, Text.literal("session name"));
        this.sessionNameField.setMaxLength(48);
        this.sessionNameField.setText("manhunt-" + System.currentTimeMillis());
        this.addDrawableChild(this.sessionNameField);

        this.autoBalanceButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("⚖ Auto Balance"), button -> this.autoBalance())
            .dimensions(layout.autoBalanceX, layout.sessionFieldY, layout.autoBalanceWidth, TOP_ACTION_HEIGHT)
            .build());

        this.startButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start Match"), button -> this.createSession())
            .dimensions(layout.startButtonX, layout.sessionFieldY, layout.startButtonWidth, TOP_ACTION_HEIGHT)
            .build());

        this.assignSpeedrunnerButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Assign → Speedrunner"), button -> this.moveSelectedTo(ColumnKind.SPEEDRUNNER))
            .dimensions(layout.assignSpeedrunnerX, layout.actionButtonsY, layout.assignSpeedrunnerWidth, TOP_ACTION_HEIGHT)
            .build());

        this.assignHunterButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Assign → Hunter"), button -> this.moveSelectedTo(ColumnKind.HUNTER))
            .dimensions(layout.assignHunterX, layout.actionButtonsY, layout.assignHunterWidth, TOP_ACTION_HEIGHT)
            .build());

        this.removeFromTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Remove From Team"), button -> this.moveSelectedTo(ColumnKind.AVAILABLE))
            .dimensions(layout.removeFromTeamX, layout.actionButtonsY, layout.removeFromTeamWidth, TOP_ACTION_HEIGHT)
            .build());

        // Build aligned settings rows (label + input) using a consistent label width
        SettingsRowLayout rowLayout = this.createSettingsRowLayout(layout);
        int rowHeight = 28;

        // Row 0: Grace Period
        int row0Y = layout.settingsY + 20;
        this.gracePeriodField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, row0Y, rowLayout.fieldWidth, 20, Text.literal("grace seconds"));
        this.gracePeriodField.setMaxLength(4);
        this.gracePeriodField.setText(Integer.toString(this.gracePeriodSeconds));
        this.addDrawableChild(this.gracePeriodField);

        // Row 1: Respawn Delay
        int row1Y = row0Y + rowHeight;
        this.respawnDelayField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, row1Y, rowLayout.fieldWidth, 20, Text.literal("respawn delay seconds"));
        this.respawnDelayField.setMaxLength(4);
        this.respawnDelayField.setText(Integer.toString(this.respawnDelaySeconds));
        this.addDrawableChild(this.respawnDelayField);

        // Row 2: Seed Mode (toggle/dropdown-like button that cycles)
        int row2Y = row1Y + rowHeight;
        this.seedModeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.seedMode.displayName), button -> {
            this.seedMode = this.seedMode.next();
            button.setMessage(Text.literal(this.seedMode.displayName));
        }).dimensions(rowLayout.fieldX, row2Y, rowLayout.fieldWidth, 20).build());

        // Row 3: Fixed Seed (only active when Fixed mode is selected)
        int row3Y = row2Y + rowHeight;
        this.seedValueField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, row3Y, rowLayout.fieldWidth, 20, Text.literal("fixed seed"));
        this.seedValueField.setMaxLength(36);
        this.seedValueField.setText(Long.toString(System.currentTimeMillis()));
        this.addDrawableChild(this.seedValueField);

        // Row 4: Hunters Compass toggle
        int row4Y = row3Y + rowHeight;
        this.huntersCompassToggle = this.addDrawableChild(ButtonWidget.builder(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)), button -> {
            this.toggleHuntersCompass();
            button.setMessage(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)));
        }).dimensions(rowLayout.fieldX, row4Y, rowLayout.fieldWidth, 20).build());

        // Row 5: Nether Tracking toggle
        int row5Y = row4Y + rowHeight;
        this.netherTrackingToggle = this.addDrawableChild(ButtonWidget.builder(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)), button -> {
            this.toggleNetherTracking();
            button.setMessage(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)));
        }).dimensions(rowLayout.fieldX, row5Y, rowLayout.fieldWidth, 20).build());

        // Removed explicit Save button — grace period is read directly from the text field when creating a session

        this.savePresetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Preset"), button -> this.savePreset())
            .dimensions(layout.footerSaveX, layout.footerButtonsY, layout.footerSaveWidth, FOOTER_BUTTON_HEIGHT)
            .build());

        this.resetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> this.resetAssignments())
            .dimensions(layout.footerResetX, layout.footerButtonsY, layout.footerResetWidth, FOOTER_BUTTON_HEIGHT)
            .build());

        this.backButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(new SessionScreen()))
            .dimensions(layout.footerBackX, layout.footerButtonsY, layout.footerBackWidth, FOOTER_BUTTON_HEIGHT)
            .build());

        this.requestSnapshot();
    }

    @Override
    public void tick() {
        super.tick();
        boolean connected = this.client.player != null;
        boolean hasSelection = this.getSelectedRosterEntry() != null;
        ColumnKind selectedColumn = this.getSelectedColumn();

        this.updateSettingButtonLabels();
        this.autoBalanceButton.active = connected && !SessionSnapshotData.roster().isEmpty();
        this.startButton.active = connected;
        this.assignSpeedrunnerButton.active = connected && hasSelection && selectedColumn != ColumnKind.SPEEDRUNNER;
        this.assignHunterButton.active = connected && hasSelection && selectedColumn != ColumnKind.HUNTER;
        this.removeFromTeamButton.active = connected && hasSelection && selectedColumn != ColumnKind.AVAILABLE && selectedColumn != ColumnKind.NONE;
        if (this.huntersCompassToggle != null) this.huntersCompassToggle.active = connected;
        if (this.netherTrackingToggle != null) this.netherTrackingToggle.active = connected;
        // seedMode button is interactive; seedValueField is enabled only when parsed mode is FIXED
        if (this.seedModeButton != null) this.seedModeButton.active = connected;
        SeedMode parsedMode = parseSeedModeField();
        this.respawnDelayField.active = connected;
        this.seedValueField.active = connected && parsedMode == SeedMode.FIXED;
        this.savePresetButton.active = connected;
        this.resetButton.active = connected;
        this.backButton.active = true;

        this.syncScrollBounds();

        if (!this.selectedPlayerUuid.isEmpty() && !hasSelection) {
            this.selectedPlayerUuid = "";
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        Layout layout = this.createLayout();

        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            if (column.containsPoint(mouseX, mouseY, layout.columnX(i), layout.columnsY, layout.columnWidth, layout.columnsHeight)) {
                int row = column.rowAt(mouseX, mouseY, layout.columnX(i), layout.columnsY, layout.columnWidth, layout.columnsHeight);
                List<SessionSnapshotData.RosterEntry> entries = this.getEntries(column.kind);
                int visibleRows = column.visibleRows(layout.columnsHeight);
                int index = column.scrollOffset + row;
                if (row >= 0 && row < visibleRows && index >= 0 && index < entries.size()) {
                    this.selectedPlayerUuid = entries.get(index).uuid();
                    return true;
                }
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = this.createLayout();
        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            if (column.containsPoint(mouseX, mouseY, layout.columnX(i), layout.columnsY, layout.columnWidth, layout.columnsHeight)) {
                List<SessionSnapshotData.RosterEntry> entries = this.getEntries(column.kind);
                int maxScroll = Math.max(0, entries.size() - column.visibleRows(layout.columnsHeight));
                if (maxScroll > 0) {
                    column.scrollOffset = clamp(column.scrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                    return true;
                }
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = this.createLayout();

        // 1) Draw panel shell and non-widget elements (backgrounds, columns, settings backgrounds)
        this.drawPanelShell(context, layout);

        for (int i = 0; i < this.columns.size(); i++) {
            this.drawColumn(context, layout, i, mouseX, mouseY);
        }

        // Draw only the settings background and boxes here; labels will be drawn after widgets so they remain visible
        this.drawSettingsBackground(context, layout);

        // 2) Render widgets (text fields, buttons, etc.)
        super.render(context, mouseX, mouseY, delta);

        // 3) Draw overlay text/labels last so they appear above widgets
        int centerX = layout.panelX + layout.panelWidth / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, layout.panelY + TITLE_TOP_Y, TEXT_WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Build teams, balance roles, and start a Manhunt match."), centerX, layout.panelY + SUBTITLE_TOP_Y, TEXT_MUTED);

        context.drawText(this.textRenderer, Text.literal("Session Name:"), layout.sessionLabelX, layout.sessionFieldY + 6, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Minigame selector"), layout.sessionLabelX, layout.sessionFieldY - 10, TEXT_SECONDARY, false);

        this.drawSettingsLabels(context, layout);

        if (!this.statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), centerX, layout.footerButtonsY + 28, TEXT_STATUS);
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

        context.fill(layout.sessionFieldX - 8, layout.sessionFieldY - 6, x + width - PANEL_PADDING + 4, layout.columnsY + layout.columnsHeight + 4, 0x14000000);
    }

    private void drawColumn(DrawContext context, Layout layout, int columnIndex, int mouseX, int mouseY) {
        ColumnState column = this.columns.get(columnIndex);
        List<SessionSnapshotData.RosterEntry> entries = this.getEntries(column.kind);
        int x = layout.columnX(columnIndex);
        int y = layout.columnsY;
        int width = layout.columnWidth;
        int height = layout.columnsHeight;
        int headerColor = column.kind == ColumnKind.AVAILABLE ? 0xFF7C8088 : 0xFF000000 | column.accentColor;
        int headerTextColor = column.kind == ColumnKind.AVAILABLE ? 0xFFF0F0F0 : 0xFFFFFFFF;
        String headerText = switch (column.kind) {
            case AVAILABLE -> "Available Players (" + entries.size() + ")";
            case SPEEDRUNNER -> "Speedrunners (" + entries.size() + ")";
            case HUNTER -> "Hunters (" + entries.size() + ")";
            case NONE -> column.title;
        };

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, headerColor);
        context.fill(x + 1, y + COLUMN_HEADER_HEIGHT - 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, headerColor);
        context.fill(x + 1, y + 1, x + 4, y + COLUMN_HEADER_HEIGHT, headerColor);
        context.drawText(this.textRenderer, Text.literal(headerText), x + 8, y + 5, headerTextColor, false);

        int visibleRows = column.visibleRows(height);
        int rowsToDraw = Math.min(entries.size() - column.scrollOffset, visibleRows);
        int listTop = y + COLUMN_HEADER_HEIGHT + 2;
        for (int row = 0; row < rowsToDraw; row++) {
            SessionSnapshotData.RosterEntry entry = entries.get(column.scrollOffset + row);
            int rowY = listTop + row * ROW_HEIGHT;
            boolean selected = this.selectedPlayerUuid.equals(entry.uuid());
            boolean hovered = mouseX >= x + 1 && mouseX <= x + width - 1 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 1;
            int background = selected ? 0xAA3A5C8F : (hovered ? 0x66404040 : 0x33222222);
            context.fill(x + 1, rowY, x + width - 1, rowY + ROW_HEIGHT - 1, background);
            context.fill(x + 4, rowY + 3, x + 8, rowY + ROW_HEIGHT - 4, 0xFF000000 | column.accentColor);
            context.drawText(this.textRenderer, Text.literal(entry.name()), x + 14, rowY + 5, 0xFFD8D8D8, false);
        }

        this.drawScrollBar(context, x, y, width, height, entries.size(), visibleRows, column.scrollOffset);
    }

    private void drawSettingsBackground(DrawContext context, Layout layout) {
        int x = layout.panelX + PANEL_PADDING;
        int y = layout.settingsY;
        int width = layout.settingsWidth;

        context.fill(x, y, x + width, y + SETTINGS_SECTION_HEIGHT, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
    }

    private void drawSettingsLabels(DrawContext context, Layout layout) {
        SettingsRowLayout rowLayout = this.createSettingsRowLayout(layout);
        int x = layout.panelX + PANEL_PADDING;
        int y = layout.settingsY;

        context.drawText(this.textRenderer, Text.literal("Settings"), x + 8, y + 8, TEXT_WHITE, false);

        // Aligned setting rows: labels have a fixed width, inputs are positioned to the right
        int rowHeight = 28;
        int row0Y = y + 20;
        int row1Y = row0Y + rowHeight;
        int row2Y = row1Y + rowHeight;
        int row3Y = row2Y + rowHeight;
        int row4Y = row3Y + rowHeight;
        int row5Y = row4Y + rowHeight;

        // Labels are vertically centered with the 20px-high input widgets (approx +5)
        context.drawText(this.textRenderer, Text.literal("Grace Period:"), rowLayout.labelX, row0Y + 5, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Respawn Delay:"), rowLayout.labelX, row1Y + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Seed Mode:"), rowLayout.labelX, row2Y + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Fixed Seed:"), rowLayout.labelX, row3Y + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Hunters Compass:"), rowLayout.labelX, row4Y + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Nether Tracking:"), rowLayout.labelX, row5Y + 5, TEXT_SECONDARY, false);
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

    private SettingsRowLayout createSettingsRowLayout(Layout layout) {
        // Shared positioning for all settings rows to ensure labels and fields align
        // Calculate based on absolute panel bounds to prevent widget overlap
        int labelWidth = this.calculateMaxLabelWidth();
        int gapWidth = 8;
        int rightPadding = 10;

        int labelX = layout.panelX + PANEL_PADDING + 10;
        int fieldX = labelX + labelWidth + gapWidth;
        
        int panelRight = layout.panelX + PANEL_PADDING + layout.settingsWidth;
        int availableFieldWidth = Math.max(80, panelRight - fieldX - rightPadding);
        int fieldWidth = Math.min(SETTINGS_FIELD_MAX_WIDTH, availableFieldWidth);

        return new SettingsRowLayout(labelX, fieldX, fieldWidth);
    }

    private int calculateMaxLabelWidth() {
        String[] labels = {
            "Grace Period:",
            "Respawn Delay:",
            "Seed Mode:",
            "Fixed Seed:",
            "Hunters Compass:",
            "Nether Tracking:"
        };

        int maxWidth = 0;
        for (String label : labels) {
            int width = this.textRenderer.getWidth(label);
            maxWidth = Math.max(maxWidth, width);
        }

        // Add 4 pixels padding to ensure text doesn't clip right to the edge
        return maxWidth + 4;
    }

    private void autoBalance() {
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        this.clearAssignments();
        if (roster.isEmpty()) {
            this.statusMessage = "No players to balance.";
            return;
        }

        this.getColumn(ColumnKind.SPEEDRUNNER).members.add(roster.getFirst());
        for (int i = 1; i < roster.size(); i++) {
            this.getColumn(ColumnKind.HUNTER).members.add(roster.get(i));
        }
        this.statusMessage = "Auto-balanced teams.";
    }

    private void savePreset() {
        this.statusMessage = "Preset saving is coming soon.";
    }

    private void resetAssignments() {
        this.clearAssignments();
        this.selectedPlayerUuid = "";
        this.statusMessage = "Assignments reset.";
    }

    private void clearAssignments() {
        for (ColumnState column : this.columns) {
            if (column.kind != ColumnKind.AVAILABLE) {
                column.members.clear();
            }
        }
    }

    private void createSession() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        if (this.sessionNameField.getText().trim().isEmpty()) {
            this.statusMessage = "Enter a session name.";
            return;
        }

        List<SessionSnapshotData.RosterEntry> speedrunners = this.getColumn(ColumnKind.SPEEDRUNNER).members;
        List<SessionSnapshotData.RosterEntry> hunters = this.getColumn(ColumnKind.HUNTER).members;
        if (speedrunners.isEmpty() && hunters.isEmpty()) {
            this.statusMessage = "Assign players before starting.";
            return;
        }

        SeedMode requestedMode = parseSeedModeField();
        if (requestedMode == SeedMode.FIXED) {
            try {
                this.getSelectedSeedValue();
            } catch (NumberFormatException exception) {
                this.statusMessage = "Enter a valid numeric fixed seed.";
                return;
            }
        }

        NbtCompound plan = new NbtCompound();
        plan.putString("game", SessionGameType.MANHUNT.getCommandName());
        plan.putString("name", this.sessionNameField.getText().trim());
        plan.put("settings", this.buildSettingsCompound());

        NbtList groups = new NbtList();
        NbtCompound group = new NbtCompound();
        group.putString("label", "Manhunt");

        NbtList members = new NbtList();
        NbtList roles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : speedrunners) {
            members.add(this.member(entry));
            roles.add(this.roleMember(entry, "speedrunner"));
        }
        for (SessionSnapshotData.RosterEntry entry : hunters) {
            members.add(this.member(entry));
            roles.add(this.roleMember(entry, "hunter"));
        }

        group.put("members", members);
        group.put("roles", roles);
        groups.add(group);
        plan.put("groups", groups);

        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(SessionGameType.MANHUNT.getCommandName(), this.sessionNameField.getText().trim(), plan));
        this.statusMessage = "Requested Manhunt session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putBoolean("huntersCompass", this.huntersCompassEnabled);

        // Read grace period directly from the text field (no explicit save required)
        String graceText = this.gracePeriodField.getText().trim();
        if (!graceText.isEmpty()) {
            try {
                this.gracePeriodSeconds = clamp(Integer.parseInt(graceText), GRACE_MIN_SECONDS, GRACE_MAX_SECONDS);
            } catch (NumberFormatException ignored) {
                // leave existing gracePeriodSeconds
                this.statusMessage = "Grace period must be a number.";
            }
        }
        settings.putInt("gracePeriodSeconds", this.gracePeriodSeconds);

        String respawnDelayText = this.respawnDelayField.getText().trim();
        if (!respawnDelayText.isEmpty()) {
            try {
                this.respawnDelaySeconds = clamp(Integer.parseInt(respawnDelayText), 0, RESPAWN_DELAY_MAX_SECONDS);
            } catch (NumberFormatException ignored) {
                this.statusMessage = "Respawn delay must be a number.";
            }
        }
        settings.putInt("speedrunnerRespawnDelaySeconds", this.respawnDelaySeconds);

        settings.putString("respawns", "unlimited");
        settings.putBoolean("netherTracking", this.netherTrackingEnabled);

        SeedMode parsed = parseSeedModeField();
        settings.putString("seedMode", parsed.nbtValue);
        if (parsed == SeedMode.FIXED) {
            settings.putLong("seed", this.getSelectedSeedValue());
        }
        return settings;
    }

    private NbtCompound member(SessionSnapshotData.RosterEntry entry) {
        NbtCompound compound = new NbtCompound();
        compound.putString("uuid", entry.uuid());
        compound.putString("name", entry.name());
        return compound;
    }

    private NbtCompound roleMember(SessionSnapshotData.RosterEntry entry, String role) {
        NbtCompound compound = new NbtCompound();
        compound.putString("uuid", entry.uuid());
        compound.putString("role", role);
        return compound;
    }

    private void requestSnapshot() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("refresh"));
        this.statusMessage = "Requested current roster.";
    }

    private List<SessionSnapshotData.RosterEntry> getEntries(ColumnKind kind) {
        if (kind == ColumnKind.AVAILABLE) {
            List<SessionSnapshotData.RosterEntry> available = new ArrayList<>();
            for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
                if (!this.isAssigned(entry.uuid())) {
                    available.add(entry);
                }
            }
            return available;
        }

        return this.getColumn(kind).members;
    }

    private boolean isAssigned(String uuid) {
        return this.getColumn(ColumnKind.SPEEDRUNNER).members.stream().anyMatch(entry -> entry.uuid().equals(uuid))
            || this.getColumn(ColumnKind.HUNTER).members.stream().anyMatch(entry -> entry.uuid().equals(uuid));
    }

    private SessionSnapshotData.RosterEntry getSelectedRosterEntry() {
        if (this.selectedPlayerUuid.isEmpty()) {
            return null;
        }

        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            if (entry.uuid().equals(this.selectedPlayerUuid)) {
                return entry;
            }
        }
        return null;
    }

    private ColumnKind getSelectedColumn() {
        if (this.selectedPlayerUuid.isEmpty()) {
            return ColumnKind.NONE;
        }
        if (this.getColumn(ColumnKind.SPEEDRUNNER).members.stream().anyMatch(entry -> entry.uuid().equals(this.selectedPlayerUuid))) {
            return ColumnKind.SPEEDRUNNER;
        }
        if (this.getColumn(ColumnKind.HUNTER).members.stream().anyMatch(entry -> entry.uuid().equals(this.selectedPlayerUuid))) {
            return ColumnKind.HUNTER;
        }
        return SessionSnapshotData.roster().stream().anyMatch(entry -> entry.uuid().equals(this.selectedPlayerUuid)) ? ColumnKind.AVAILABLE : ColumnKind.NONE;
    }

    private void moveSelectedTo(ColumnKind target) {
        SessionSnapshotData.RosterEntry selected = this.getSelectedRosterEntry();
        if (selected == null) {
            this.statusMessage = "Select a player first.";
            return;
        }

        ColumnKind current = this.getSelectedColumn();
        if (target == current) {
            this.statusMessage = selected.name() + " is already in that column.";
            return;
        }

        this.getColumn(ColumnKind.SPEEDRUNNER).remove(selected.uuid());
        this.getColumn(ColumnKind.HUNTER).remove(selected.uuid());

        if (target != ColumnKind.AVAILABLE) {
            this.getColumn(target).members.add(selected);
            this.statusMessage = selected.name() + " assigned to " + target.displayName + ".";
        } else {
            this.statusMessage = selected.name() + " returned to available players.";
        }
    }

    private void toggleHuntersCompass() {
        this.huntersCompassEnabled = !this.huntersCompassEnabled;
        this.statusMessage = "Hunters Compass " + onOff(this.huntersCompassEnabled) + ".";
    }

    private void toggleNetherTracking() {
        this.netherTrackingEnabled = !this.netherTrackingEnabled;
        this.statusMessage = "Nether Tracking " + onOff(this.netherTrackingEnabled) + ".";
    }

    private SeedMode parseSeedModeField() {
        return this.seedMode;
    }

    private long getSelectedSeedValue() {
        String text = this.seedValueField.getText().trim();
        if (text.isEmpty()) {
            throw new NumberFormatException("empty seed");
        }
        return Long.parseLong(text);
    }

    private void updateSettingButtonLabels() {
        if (this.huntersCompassToggle != null) {
            this.huntersCompassToggle.setMessage(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)));
        }
        if (this.netherTrackingToggle != null) {
            this.netherTrackingToggle.setMessage(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)));
        }
        if (this.seedModeButton != null) {
            this.seedModeButton.setMessage(Text.literal(this.seedMode.displayName));
        }
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private void syncScrollBounds() {
        Layout layout = this.createLayout();
        for (ColumnState column : this.columns) {
            int maxScroll = Math.max(0, this.getEntries(column.kind).size() - column.visibleRows(layout.columnsHeight));
            column.scrollOffset = clamp(column.scrollOffset, 0, maxScroll);
        }
    }

    private ColumnState getColumn(ColumnKind kind) {
        for (ColumnState column : this.columns) {
            if (column.kind == kind) {
                return column;
            }
        }
        throw new IllegalStateException("Missing column: " + kind);
    }

    private Layout createLayout() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 24);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 24);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;

        int columnWidth = (panelWidth - (PANEL_PADDING * 2) - (COLUMN_GAP * 2)) / 3;
        int topButtonsY = panelY + SESSION_ROW_Y;

        int autoBalanceWidth = 118;
        int startButtonWidth = 108;
        int startButtonX = panelX + panelWidth - PANEL_PADDING - startButtonWidth;
        int autoBalanceX = startButtonX - TOP_BUTTON_GAP - autoBalanceWidth;

        int sessionFieldX = panelX + 118;
        int sessionFieldWidth = Math.max(180, autoBalanceX - sessionFieldX - 12);

        int footerButtonsY = panelY + panelHeight - 32;
        int footerSaveWidth = 98;
        int footerResetWidth = 72;
        int footerBackWidth = 68;
        int footerTotalWidth = footerSaveWidth + footerResetWidth + footerBackWidth + (FOOTER_BUTTON_GAP * 2);
        int footerLeft = panelX + (panelWidth - footerTotalWidth) / 2;

        int actionButtonsY = footerButtonsY - SETTINGS_SECTION_HEIGHT - 42;
        int settingsY = actionButtonsY + 30;
        int columnsY = panelY + COLUMN_TOP_Y;
        int columnsHeight = Math.max(168, actionButtonsY - columnsY - 10);

        int assignSpeedrunnerWidth = 166;
        int assignHunterWidth = 138;
        int removeFromTeamWidth = 156;
        int actionTotalWidth = assignSpeedrunnerWidth + assignHunterWidth + removeFromTeamWidth + (FOOTER_BUTTON_GAP * 2);
        int actionLeft = panelX + (panelWidth - actionTotalWidth) / 2;

        return new Layout(
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            sessionFieldX,
            topButtonsY,
            sessionFieldWidth,
            autoBalanceX,
            autoBalanceWidth,
            startButtonX,
            startButtonWidth,
            columnsY,
            columnsHeight,
            columnWidth,
            panelX + PANEL_PADDING,
            actionButtonsY,
            actionLeft,
            assignSpeedrunnerWidth,
            actionLeft + assignSpeedrunnerWidth + FOOTER_BUTTON_GAP,
            assignHunterWidth,
            actionLeft + assignSpeedrunnerWidth + assignHunterWidth + (FOOTER_BUTTON_GAP * 2),
            removeFromTeamWidth,
            settingsY,
            panelWidth - (PANEL_PADDING * 2),
            panelX + PANEL_PADDING + 10,
            140,
            panelX + PANEL_PADDING + 10,
            Math.max(180, panelWidth - (PANEL_PADDING * 2) - 230),
            panelX + PANEL_PADDING + 10,
            64,
            footerButtonsY,
            footerLeft,
            footerSaveWidth,
            footerLeft + footerSaveWidth + FOOTER_BUTTON_GAP,
            footerResetWidth,
            footerLeft + footerSaveWidth + footerResetWidth + (FOOTER_BUTTON_GAP * 2),
            footerBackWidth
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private enum ColumnKind {
        NONE(""),
        AVAILABLE("Available Players"),
        SPEEDRUNNER("Speedrunners"),
        HUNTER("Hunters");

        private final String displayName;

        ColumnKind(String displayName) {
            this.displayName = displayName;
        }
    }

    private enum SeedMode {
        RANDOM("Random", "random"),
        FIXED("Fixed", "fixed");

        private final String displayName;
        private final String nbtValue;

        SeedMode(String displayName, String nbtValue) {
            this.displayName = displayName;
            this.nbtValue = nbtValue;
        }

        private SeedMode next() {
            return this == RANDOM ? FIXED : RANDOM;
        }
    }

    private static final class ColumnState {
        private final ColumnKind kind;
        private final String title;
        private final int accentColor;
        private final List<SessionSnapshotData.RosterEntry> members = new ArrayList<>();
        private int scrollOffset;

        private ColumnState(ColumnKind kind, String title, int accentColor) {
            this.kind = kind;
            this.title = title;
            this.accentColor = accentColor;
        }

        private int visibleRows(int height) {
            return Math.max(0, (height - COLUMN_HEADER_HEIGHT - 4) / ROW_HEIGHT);
        }

        private void remove(String uuid) {
            this.members.removeIf(entry -> entry.uuid().equals(uuid));
        }

        private boolean containsPoint(double mouseX, double mouseY, int x, int y, int width, int height) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private int rowAt(double mouseX, double mouseY, int x, int y, int width, int height) {
            if (!this.containsPoint(mouseX, mouseY, x, y, width, height)) {
                return -1;
            }

            int rowStartY = y + COLUMN_HEADER_HEIGHT + 2;
            if (mouseY < rowStartY || mouseY > y + height) {
                return -1;
            }
            return (int) ((mouseY - rowStartY) / ROW_HEIGHT);
        }
    }

    private record Layout(
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        int sessionFieldX,
        int sessionFieldY,
        int sessionFieldWidth,
        int autoBalanceX,
        int autoBalanceWidth,
        int startButtonX,
        int startButtonWidth,
        int columnsY,
        int columnsHeight,
        int columnWidth,
        int sessionLabelX,
        int actionButtonsY,
        int assignSpeedrunnerX,
        int assignSpeedrunnerWidth,
        int assignHunterX,
        int assignHunterWidth,
        int removeFromTeamX,
        int removeFromTeamWidth,
        int settingsY,
        int settingsWidth,
        int settingsButtonX,
        int settingsButtonWidth,
        int settingsFieldX,
        int settingsFieldWidth,
        int settingsSaveX,
        int settingsSaveWidth,
        int footerButtonsY,
        int footerSaveX,
        int footerSaveWidth,
        int footerResetX,
        int footerResetWidth,
        int footerBackX,
        int footerBackWidth
    ) {
        private int columnX(int index) {
            return this.panelX + PANEL_PADDING + index * (this.columnWidth + COLUMN_GAP);
        }
    }

    private record SettingsRowLayout(int labelX, int fieldX, int fieldWidth) {
    }
}





