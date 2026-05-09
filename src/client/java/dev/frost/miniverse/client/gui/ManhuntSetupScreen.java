package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionGameType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ManhuntSetupScreen extends Screen {
    private static final int PANEL_PADDING = 16;
    private static final int PANEL_MAX_WIDTH = 980;
    private static final int PANEL_MAX_HEIGHT = 720;
    private static final int TITLE_TOP_Y = 12;
    private static final int SUBTITLE_TOP_Y = 28;
    private static final int SESSION_ROW_Y = 48;
    private static final int COLUMN_TOP_Y = 86;
    private static final int COLUMN_HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 18;
    private static final int COLUMN_GAP = 12;
    private static final int SETTINGS_SECTION_HEIGHT = 330;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int FOOTER_BUTTON_GAP = 10;
    private static final int TOP_BUTTON_GAP = 10;
    private static final int TOP_ACTION_HEIGHT = 20;
    private static final int STEPPER_BUTTON_WIDTH = 18;
    private static final int PRESET_BUTTON_WIDTH = 30;
    private static final int PRESET_BUTTON_GAP = 6;
    private static final int CONTROL_BUTTON_GAP = 6;
    private static final int GRACE_MIN_SECONDS = 0;
    private static final int GRACE_MAX_SECONDS = 3600;
    private static final int RESPAWN_DELAY_DEFAULT_SECONDS = 5 * 60;
    private static final int RESPAWN_DELAY_MAX_SECONDS = 3600;
    private static final int LIVES_MAX = 99;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_SECONDARY = 0xFFD8D8D8;
    private static final int TEXT_MUTED = 0xFFB8B8B8;
    private static final int TEXT_STATUS = 0xFFA0FFA0;
    private static final int SETTINGS_FIELD_MAX_WIDTH = 180;

    private static final int[] COLUMN_COLORS = {0x6F7682, 0x4E88FF, 0xE05555};
    private static final int[] PRESET_SECONDS = {0, 10, 30, 60, 300};
    private static final int[] PRESET_MINUTES = {0, 1, 5, 10, 30};

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<ColumnState> columns = new ArrayList<>();
    private TextFieldWidget sessionNameField;
    private ButtonWidget autoBalanceButton;
    private ButtonWidget randomRunnerButton;
    private ButtonWidget twoRunnersButton;
    private ButtonWidget evenSplitButton;
    private ButtonWidget allHuntersButton;
    private ButtonWidget clearRolesButton;
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
    private TextFieldWidget hunterRespawnDelayField;
    private TextFieldWidget compassCooldownField;
    private TextFieldWidget runnerGlowPulseField;
    private ButtonWidget runnerLivesButton;
    private ButtonWidget hunterLivesButton;
    // saveGraceButton removed — grace is applied directly from the text field
    private TextFieldWidget seedValueField;
    private ButtonWidget resetButton;
    private ButtonWidget backButton;
    private String selectedPlayerUuid = "";
    private boolean huntersCompassEnabled = true;
    private boolean netherTrackingEnabled = true;
    private int gracePeriodSeconds = 30;
    private int respawnDelaySeconds = RESPAWN_DELAY_DEFAULT_SECONDS;
    private int hunterRespawnDelaySeconds = 0;
    private int compassCooldownSeconds = 2;
    private int runnerGlowPulseMinutes = 0;
    private int runnerLives = -1;
    private int hunterLives = -1;
    private SeedMode seedMode = SeedMode.RANDOM;
    private final List<ButtonWidget> numericSettingButtons = new ArrayList<>();
    private String statusMessage = "";

    public ManhuntSetupScreen() {
        super(Text.literal("Manhunt Setup"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.numericSettingButtons.clear();
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
        this.autoBalanceButton.setTooltip(Tooltip.of(Text.literal("Assigns the first listed player as runner and everyone else as hunters.")));

        this.randomRunnerButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Random Runner"), button -> this.randomRunner())
            .dimensions(layout.randomRunnerX, layout.sessionFieldY, layout.randomRunnerWidth, TOP_ACTION_HEIGHT)
            .build());
        this.randomRunnerButton.setTooltip(Tooltip.of(Text.literal("Chooses one random online player as speedrunner.")));

        this.twoRunnersButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("2 Runners"), button -> this.twoRunners())
            .dimensions(layout.twoRunnersX, layout.sessionFieldY, layout.twoRunnersWidth, TOP_ACTION_HEIGHT)
            .build());
        this.twoRunnersButton.setTooltip(Tooltip.of(Text.literal("Assigns two speedrunners and puts everyone else on hunters.")));

        this.evenSplitButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Even Split"), button -> this.evenSplit())
            .dimensions(layout.evenSplitX, layout.sessionFieldY, layout.evenSplitWidth, TOP_ACTION_HEIGHT)
            .build());
        this.evenSplitButton.setTooltip(Tooltip.of(Text.literal("Splits players evenly between speedrunners and hunters.")));

        this.allHuntersButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("All Hunters"), button -> this.allOthersHunters())
            .dimensions(layout.allHuntersX, layout.sessionFieldY, layout.allHuntersWidth, TOP_ACTION_HEIGHT)
            .build());
        this.allHuntersButton.setTooltip(Tooltip.of(Text.literal("Keeps selected speedrunners and moves all other players to hunters.")));

        this.clearRolesButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear Roles"), button -> this.resetAssignments())
            .dimensions(layout.clearRolesX, layout.sessionFieldY, layout.clearRolesWidth, TOP_ACTION_HEIGHT)
            .build());
        this.clearRolesButton.setTooltip(Tooltip.of(Text.literal("Clears all assignments without changing settings.")));

        this.startButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start Match"), button -> this.createSession())
            .dimensions(layout.startButtonX, layout.sessionFieldY, layout.startButtonWidth, TOP_ACTION_HEIGHT)
            .build());
        this.startButton.setTooltip(Tooltip.of(Text.literal("Creates and launches a Manhunt session using the current roles and settings.")));

        this.assignSpeedrunnerButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Assign → Speedrunner"), button -> this.moveSelectedTo(ColumnKind.SPEEDRUNNER))
            .dimensions(layout.assignSpeedrunnerX, layout.actionButtonsY, layout.assignSpeedrunnerWidth, TOP_ACTION_HEIGHT)
            .build());
        this.assignSpeedrunnerButton.setTooltip(Tooltip.of(Text.literal("Moves the selected player into the speedrunner team.")));

        this.assignHunterButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Assign → Hunter"), button -> this.moveSelectedTo(ColumnKind.HUNTER))
            .dimensions(layout.assignHunterX, layout.actionButtonsY, layout.assignHunterWidth, TOP_ACTION_HEIGHT)
            .build());
        this.assignHunterButton.setTooltip(Tooltip.of(Text.literal("Moves the selected player into the hunter team.")));

        this.removeFromTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Remove From Team"), button -> this.moveSelectedTo(ColumnKind.AVAILABLE))
            .dimensions(layout.removeFromTeamX, layout.actionButtonsY, layout.removeFromTeamWidth, TOP_ACTION_HEIGHT)
            .build());
        this.removeFromTeamButton.setTooltip(Tooltip.of(Text.literal("Returns the selected player to the available list.")));

        // Build aligned settings rows (label + input) using a consistent label width
        SettingsRowLayout rowLayout = this.createSettingsRowLayout(layout);
        int baseY = layout.settingsY;
        int topMatchGap = 3;
        int postFixedSeedGap = 3;
        int postNetherTrackingGap = 3;
        int postHunterRespawnGap = 3;
        int releaseDelayY = baseY + 32 + topMatchGap;
        int seedModeY = baseY + 52 + topMatchGap;
        int fixedSeedY = baseY + 72 + topMatchGap;
        int huntersCompassY = baseY + 108 + topMatchGap + postFixedSeedGap;
        int compassCooldownY = baseY + 128 + topMatchGap + postFixedSeedGap;
        int netherTrackingY = baseY + 148 + topMatchGap + postFixedSeedGap;
        int runnerLivesY = baseY + 184 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int runnerRespawnY = baseY + 204 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int hunterLivesY = baseY + 224 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int hunterRespawnY = baseY + 244 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int runnerGlowY = baseY + 280 + topMatchGap + postFixedSeedGap + postNetherTrackingGap + postHunterRespawnGap;

        // Match: Release Delay
        this.gracePeriodField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, releaseDelayY, rowLayout.fieldWidth, 20, Text.literal("hunter release seconds"));
        this.gracePeriodField.setMaxLength(4);
        this.gracePeriodField.setText(Integer.toString(this.gracePeriodSeconds));
        this.addDrawableChild(this.gracePeriodField);
        this.addStepperButtons(rowLayout, releaseDelayY, this.gracePeriodField, GRACE_MIN_SECONDS, GRACE_MAX_SECONDS, 5);
        this.addPresetButtons(rowLayout, releaseDelayY, this.gracePeriodField, PRESET_SECONDS, "s");

        // Match: Seed Mode
        this.seedModeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.seedMode.displayName), button -> {
            this.seedMode = this.seedMode.next();
            button.setMessage(Text.literal(this.seedMode.displayName));
        }).dimensions(rowLayout.fieldX, seedModeY, rowLayout.fieldWidth, 20).build());
        this.seedModeButton.setTooltip(Tooltip.of(Text.literal("Choose random seed or fixed seed for repeatable games.")));

        // Match: Fixed Seed (only active when Fixed mode is selected)
        this.seedValueField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, fixedSeedY, rowLayout.fieldWidth, 20, Text.literal("fixed seed"));
        this.seedValueField.setMaxLength(36);
        this.seedValueField.setText(Long.toString(System.currentTimeMillis()));
        this.addDrawableChild(this.seedValueField);

        // Lives & Respawns: Runner Lives
        this.runnerLivesButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Runner Lives: " + formatLives(this.runnerLives)), button -> {
            this.runnerLives = nextLivesValue(this.runnerLives);
            button.setMessage(Text.literal("Runner Lives: " + formatLives(this.runnerLives)));
        }).dimensions(rowLayout.fieldX, runnerLivesY, rowLayout.fieldWidth, 20).build());
        this.runnerLivesButton.setTooltip(Tooltip.of(Text.literal("Cycles runner lives: Unlimited, 1, 2, 3, 5.")));
        this.addLivesStepperButtons(rowLayout, runnerLivesY, true);
        this.addLivesPresetButtons(rowLayout, runnerLivesY, true);

        // Lives & Respawns: Runner Respawn Delay
        this.respawnDelayField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, runnerRespawnY, rowLayout.fieldWidth, 20, Text.literal("runner respawn delay seconds"));
        this.respawnDelayField.setMaxLength(4);
        this.respawnDelayField.setText(Integer.toString(this.respawnDelaySeconds));
        this.addDrawableChild(this.respawnDelayField);
        this.addStepperButtons(rowLayout, runnerRespawnY, this.respawnDelayField, 0, RESPAWN_DELAY_MAX_SECONDS, 30);
        this.addPresetButtons(rowLayout, runnerRespawnY, this.respawnDelayField, PRESET_SECONDS, "s");

        // Lives & Respawns: Hunter Lives
        this.hunterLivesButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Hunter Lives: " + formatLives(this.hunterLives)), button -> {
            this.hunterLives = nextLivesValue(this.hunterLives);
            button.setMessage(Text.literal("Hunter Lives: " + formatLives(this.hunterLives)));
        }).dimensions(rowLayout.fieldX, hunterLivesY, rowLayout.fieldWidth, 20).build());
        this.hunterLivesButton.setTooltip(Tooltip.of(Text.literal("Cycles hunter lives: Unlimited, 1, 2, 3, 5.")));
        this.addLivesStepperButtons(rowLayout, hunterLivesY, false);
        this.addLivesPresetButtons(rowLayout, hunterLivesY, false);

        // Lives & Respawns: Hunter Respawn Delay
        this.hunterRespawnDelayField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, hunterRespawnY, rowLayout.fieldWidth, 20, Text.literal("hunter respawn delay seconds"));
        this.hunterRespawnDelayField.setMaxLength(4);
        this.hunterRespawnDelayField.setText(Integer.toString(this.hunterRespawnDelaySeconds));
        this.addDrawableChild(this.hunterRespawnDelayField);
        this.addStepperButtons(rowLayout, hunterRespawnY, this.hunterRespawnDelayField, 0, RESPAWN_DELAY_MAX_SECONDS, 5);
        this.addPresetButtons(rowLayout, hunterRespawnY, this.hunterRespawnDelayField, PRESET_SECONDS, "s");

        // Tracking: Hunters Compass toggle
        this.huntersCompassToggle = this.addDrawableChild(ButtonWidget.builder(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)), button -> {
            this.toggleHuntersCompass();
            button.setMessage(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)));
        }).dimensions(rowLayout.fieldX, huntersCompassY, rowLayout.fieldWidth, 20).build());
        this.huntersCompassToggle.setTooltip(Tooltip.of(Text.literal("Controls whether hunters receive tracking compasses.")));

        // Tracking: Compass Cooldown
        this.compassCooldownField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, compassCooldownY, rowLayout.fieldWidth, 20, Text.literal("compass cooldown seconds"));
        this.compassCooldownField.setMaxLength(3);
        this.compassCooldownField.setText(Integer.toString(this.compassCooldownSeconds));
        this.addDrawableChild(this.compassCooldownField);
        this.compassCooldownField.setTooltip(Tooltip.of(Text.literal("Delay between hunter compass target cycling.")));
        this.addStepperButtons(rowLayout, compassCooldownY, this.compassCooldownField, 0, 300, 1);
        this.addPresetButtons(rowLayout, compassCooldownY, this.compassCooldownField, PRESET_SECONDS, "s");

        // Tracking: Nether Tracking toggle
        this.netherTrackingToggle = this.addDrawableChild(ButtonWidget.builder(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)), button -> {
            this.toggleNetherTracking();
            button.setMessage(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)));
        }).dimensions(rowLayout.fieldX, netherTrackingY, rowLayout.fieldWidth, 20).build());
        this.netherTrackingToggle.setTooltip(Tooltip.of(Text.literal("Whether compasses remember runner positions while in the Nether.")));

        // Difficulty: Runner Glow Pulse
        this.runnerGlowPulseField = new TextFieldWidget(this.textRenderer, rowLayout.fieldX, runnerGlowY, rowLayout.fieldWidth, 20, Text.literal("runner glow pulse minutes"));
        this.runnerGlowPulseField.setMaxLength(3);
        this.runnerGlowPulseField.setText(Integer.toString(this.runnerGlowPulseMinutes));
        this.addDrawableChild(this.runnerGlowPulseField);
        this.runnerGlowPulseField.setTooltip(Tooltip.of(Text.literal("Briefly gives runners glowing every N minutes.")));
        this.addStepperButtons(rowLayout, runnerGlowY, this.runnerGlowPulseField, 0, 120, 1);
        this.addPresetButtons(rowLayout, runnerGlowY, this.runnerGlowPulseField, PRESET_MINUTES, "m");

        // Removed explicit Save button — grace period is read directly from the text field when creating a session

        this.resetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> this.resetAssignments())
            .dimensions(layout.footerResetX, layout.footerButtonsY, layout.footerResetWidth, FOOTER_BUTTON_HEIGHT)
            .build());
        this.resetButton.setTooltip(Tooltip.of(Text.literal("Clears role assignments and keeps current settings.")));

        this.backButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(new SessionScreen()))
            .dimensions(layout.footerBackX, layout.footerButtonsY, layout.footerBackWidth, FOOTER_BUTTON_HEIGHT)
            .build());
        this.backButton.setTooltip(Tooltip.of(Text.literal("Return to the minigame selector.")));

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
        this.randomRunnerButton.active = connected && !SessionSnapshotData.roster().isEmpty();
        this.twoRunnersButton.active = connected && SessionSnapshotData.roster().size() >= 2;
        this.evenSplitButton.active = connected && SessionSnapshotData.roster().size() >= 2;
        this.allHuntersButton.active = connected && !SessionSnapshotData.roster().isEmpty();
        this.clearRolesButton.active = connected;
        this.startButton.active = connected && this.getStartValidationMessage().isEmpty();
        this.assignSpeedrunnerButton.active = connected && hasSelection && selectedColumn != ColumnKind.SPEEDRUNNER;
        this.assignHunterButton.active = connected && hasSelection && selectedColumn != ColumnKind.HUNTER;
        this.removeFromTeamButton.active = connected && hasSelection && selectedColumn != ColumnKind.AVAILABLE && selectedColumn != ColumnKind.NONE;
        if (this.huntersCompassToggle != null) this.huntersCompassToggle.active = connected;
        if (this.netherTrackingToggle != null) this.netherTrackingToggle.active = connected;
        // seedMode button is interactive; seedValueField is enabled only when parsed mode is FIXED
        if (this.seedModeButton != null) this.seedModeButton.active = connected;
        SeedMode parsedMode = parseSeedModeField();
        this.hunterRespawnDelayField.active = connected;
        this.compassCooldownField.active = connected;
        this.runnerGlowPulseField.active = connected;
        this.runnerLivesButton.active = connected;
        this.hunterLivesButton.active = connected;
        for (ButtonWidget button : this.numericSettingButtons) {
            button.active = connected;
        }
        this.respawnDelayField.active = connected;
        this.seedValueField.active = connected && parsedMode == SeedMode.FIXED;
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
        boolean shiftDown = this.isShiftDown();
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
                    if (column.kind == ColumnKind.AVAILABLE) {
                        if (doubled) {
                            ColumnKind target = shiftDown ? ColumnKind.SPEEDRUNNER : ColumnKind.HUNTER;
                            this.moveSelectedTo(target);
                        }
                    } else if (column.kind == ColumnKind.SPEEDRUNNER || column.kind == ColumnKind.HUNTER) {
                        this.moveSelectedTo(ColumnKind.AVAILABLE);
                    }
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
        this.drawMatchSummary(context, layout);

        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(validation), centerX, layout.footerButtonsY + 28, 0xFFFFD070);
        } else if (!this.statusMessage.isEmpty()) {
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
        int topMatchGap = 3;
        int postFixedSeedGap = 3;
        int postNetherTrackingGap = 3;
        int postHunterRespawnGap = 3;
        int matchHeaderY = y + 19 + topMatchGap;
        int releaseDelayY = y + 32 + topMatchGap;
        int seedModeY = y + 52 + topMatchGap;
        int fixedSeedY = y + 72 + topMatchGap;
        int huntersCompassY = y + 108 + topMatchGap + postFixedSeedGap;
        int compassCooldownY = y + 128 + topMatchGap + postFixedSeedGap;
        int netherTrackingY = y + 148 + topMatchGap + postFixedSeedGap;
        int trackingHeaderY = huntersCompassY - 7;
        int livesHeaderY = y + 170 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int runnerLivesY = y + 184 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int runnerRespawnY = y + 204 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int hunterLivesY = y + 224 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int hunterRespawnY = y + 244 + topMatchGap + postFixedSeedGap + postNetherTrackingGap;
        int difficultyHeaderY = y + 264 + topMatchGap + postFixedSeedGap + postNetherTrackingGap + postHunterRespawnGap;
        int runnerGlowY = y + 280 + topMatchGap + postFixedSeedGap + postNetherTrackingGap + postHunterRespawnGap;

        context.drawText(this.textRenderer, Text.literal("Settings"), x + 8, y + 8, TEXT_WHITE, false);

        this.drawSectionHeader(context, rowLayout.labelX, matchHeaderY, "Match");
        context.drawText(this.textRenderer, Text.literal("Release Delay:"), rowLayout.labelX, releaseDelayY + 5, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Seed Mode:"), rowLayout.labelX, seedModeY + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Fixed Seed:"), rowLayout.labelX, fixedSeedY + 5, TEXT_SECONDARY, false);

        this.drawSectionHeader(context, rowLayout.labelX, trackingHeaderY, "Tracking");
        context.drawText(this.textRenderer, Text.literal("Hunters Compass:"), rowLayout.labelX, huntersCompassY + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Compass Cooldown:"), rowLayout.labelX, compassCooldownY + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Nether Tracking:"), rowLayout.labelX, netherTrackingY + 5, TEXT_SECONDARY, false);

        this.drawSectionHeader(context, rowLayout.labelX, livesHeaderY, "Lives & Respawns");
        context.drawText(this.textRenderer, Text.literal("Runner Lives:"), rowLayout.labelX, runnerLivesY + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Runner Respawn:"), rowLayout.labelX, runnerRespawnY + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Hunter Lives:"), rowLayout.labelX, hunterLivesY + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Hunter Respawn:"), rowLayout.labelX, hunterRespawnY + 5, TEXT_SECONDARY, false);

        this.drawSectionHeader(context, rowLayout.labelX, difficultyHeaderY, "Difficulty");
        context.drawText(this.textRenderer, Text.literal("Glow Pulse:"), rowLayout.labelX, runnerGlowY + 5, TEXT_SECONDARY, false);
    }

    private void drawSectionHeader(DrawContext context, int x, int y, String label) {
        context.drawText(this.textRenderer, Text.literal(label), x, y, 0xFFFFD070, false);
    }

    private void addStepperButtons(SettingsRowLayout rowLayout, int y, TextFieldWidget field, int min, int max, int step) {
        int minusX = rowLayout.fieldX + rowLayout.fieldWidth + CONTROL_BUTTON_GAP;
        ButtonWidget minus = this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> this.stepField(field, min, max, -step))
            .dimensions(minusX, y, STEPPER_BUTTON_WIDTH, 20)
            .build());
        minus.setTooltip(Tooltip.of(Text.literal("Decrease value.")));

        ButtonWidget plus = this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> this.stepField(field, min, max, step))
            .dimensions(minusX + STEPPER_BUTTON_WIDTH + CONTROL_BUTTON_GAP, y, STEPPER_BUTTON_WIDTH, 20)
            .build());
        plus.setTooltip(Tooltip.of(Text.literal("Increase value.")));

        this.numericSettingButtons.add(minus);
        this.numericSettingButtons.add(plus);
    }

    private void addLivesStepperButtons(SettingsRowLayout rowLayout, int y, boolean runner) {
        int minusX = rowLayout.fieldX + rowLayout.fieldWidth + CONTROL_BUTTON_GAP;
        ButtonWidget minus = this.addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> this.stepLivesValue(runner, -1))
            .dimensions(minusX, y, STEPPER_BUTTON_WIDTH, 20)
            .build());
        minus.setTooltip(Tooltip.of(Text.literal("Decrease lives.")));

        ButtonWidget plus = this.addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> this.stepLivesValue(runner, 1))
            .dimensions(minusX + STEPPER_BUTTON_WIDTH + CONTROL_BUTTON_GAP, y, STEPPER_BUTTON_WIDTH, 20)
            .build());
        plus.setTooltip(Tooltip.of(Text.literal("Increase lives.")));

        this.numericSettingButtons.add(minus);
        this.numericSettingButtons.add(plus);
    }

    private void addPresetButtons(SettingsRowLayout rowLayout, int y, TextFieldWidget field, int[] presets, String suffix) {
        int startX = rowLayout.fieldX + rowLayout.fieldWidth + CONTROL_BUTTON_GAP + (STEPPER_BUTTON_WIDTH * 2) + CONTROL_BUTTON_GAP;
        for (int i = 0; i < presets.length; i++) {
            int value = presets[i];
            String label = value + suffix;
            int x = startX + i * (PRESET_BUTTON_WIDTH + PRESET_BUTTON_GAP);
            ButtonWidget preset = this.addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> field.setText(Integer.toString(value)))
                .dimensions(x, y, PRESET_BUTTON_WIDTH, 20)
                .build());
            preset.setTooltip(Tooltip.of(Text.literal("Set to " + label + ".")));
            this.numericSettingButtons.add(preset);
        }
    }

    private void addLivesPresetButtons(SettingsRowLayout rowLayout, int y, boolean runner) {
        int startX = rowLayout.fieldX + rowLayout.fieldWidth + CONTROL_BUTTON_GAP + (STEPPER_BUTTON_WIDTH * 2) + CONTROL_BUTTON_GAP;
        LivesPreset[] presets = {
            new LivesPreset("Unl", -1),
            new LivesPreset("1", 1),
            new LivesPreset("2", 2),
            new LivesPreset("3", 3),
            new LivesPreset("5", 5)
        };
        for (int i = 0; i < presets.length; i++) {
            LivesPreset preset = presets[i];
            int x = startX + i * (PRESET_BUTTON_WIDTH + PRESET_BUTTON_GAP);
            ButtonWidget presetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(preset.label), button -> this.setLivesValue(runner, preset.value))
                .dimensions(x, y, PRESET_BUTTON_WIDTH, 20)
                .build());
            presetButton.setTooltip(Tooltip.of(Text.literal(preset.value < 0 ? "Set lives to Unlimited." : "Set lives to " + preset.value + ".")));
            this.numericSettingButtons.add(presetButton);
        }
    }

    private void stepField(TextFieldWidget field, int min, int max, int delta) {
        int value;
        try {
            value = Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException ignored) {
            value = min;
        }
        field.setText(Integer.toString(clamp(value + delta, min, max)));
    }

    private void stepLivesValue(boolean runner, int delta) {
        int current = runner ? this.runnerLives : this.hunterLives;
        int updated;
        if (current < 0) {
            updated = delta > 0 ? 1 : -1;
        } else {
            updated = clamp(current + delta, 1, LIVES_MAX);
        }
        this.setLivesValue(runner, updated);
    }

    private void setLivesValue(boolean runner, int value) {
        if (runner) {
            this.runnerLives = value;
            this.updateLivesButton(this.runnerLivesButton, "Runner Lives:", value);
        } else {
            this.hunterLives = value;
            this.updateLivesButton(this.hunterLivesButton, "Hunter Lives:", value);
        }
    }

    private void updateLivesButton(ButtonWidget button, String label, int value) {
        if (button != null) {
            button.setMessage(Text.literal(label + " " + formatLives(value)));
        }
    }

    private void drawMatchSummary(DrawContext context, Layout layout) {
        int summaryWidth = 250;
        int summaryHeight = 118;
        int summaryX = layout.panelX + layout.panelWidth - PANEL_PADDING - summaryWidth;
        int summaryY = layout.settingsY + (SETTINGS_SECTION_HEIGHT - summaryHeight) / 2;
        int line = summaryY + 18;
        List<SessionSnapshotData.RosterEntry> runners = this.getColumn(ColumnKind.SPEEDRUNNER).members;
        List<SessionSnapshotData.RosterEntry> hunters = this.getColumn(ColumnKind.HUNTER).members;

        context.fill(summaryX - 8, summaryY - 8, summaryX + summaryWidth, summaryY + summaryHeight, 0x33101010);
        context.drawText(this.textRenderer, Text.literal("Match Summary"), summaryX, summaryY, 0xFFFFD070, false);
        context.drawText(this.textRenderer, Text.literal(runners.size() + " Speedrunner(s) vs " + hunters.size() + " Hunter(s)"), summaryX, line, TEXT_PRIMARY, false);
        line += 14;
        context.drawText(this.textRenderer, Text.literal("Runner lives: " + formatLives(this.runnerLives)), summaryX, line, TEXT_SECONDARY, false);
        line += 12;
        context.drawText(this.textRenderer, Text.literal("Hunter lives: " + formatLives(this.hunterLives)), summaryX, line, TEXT_SECONDARY, false);
        line += 12;
        context.drawText(this.textRenderer, Text.literal("Release: " + this.gracePeriodField.getText().trim() + "s"), summaryX, line, TEXT_SECONDARY, false);
        line += 12;
        context.drawText(this.textRenderer, Text.literal("Compass: " + onOff(this.huntersCompassEnabled) + " / " + this.compassCooldownField.getText().trim() + "s"), summaryX, line, TEXT_SECONDARY, false);
        line += 12;
        String seed = this.seedMode == SeedMode.FIXED ? "Fixed" : "Random";
        context.drawText(this.textRenderer, Text.literal("Seed: " + seed), summaryX, line, TEXT_SECONDARY, false);
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
        int gapWidth = 6;
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
            "Release Delay:",
            "Runner Respawn:",
            "Hunter Respawn:",
            "Compass Cooldown:",
            "Glow Pulse:",
            "Runner Lives:",
            "Hunter Lives:",
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

    private void randomRunner() {
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        this.clearAssignments();
        if (roster.isEmpty()) {
            this.statusMessage = "No players to assign.";
            return;
        }

        SessionSnapshotData.RosterEntry runner = roster.get(ThreadLocalRandom.current().nextInt(roster.size()));
        this.getColumn(ColumnKind.SPEEDRUNNER).members.add(runner);
        for (SessionSnapshotData.RosterEntry entry : roster) {
            if (!entry.uuid().equals(runner.uuid())) {
                this.getColumn(ColumnKind.HUNTER).members.add(entry);
            }
        }
        this.statusMessage = "Random runner assigned.";
    }

    private void twoRunners() {
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        this.clearAssignments();
        if (roster.size() < 2) {
            this.statusMessage = "Need at least two players.";
            return;
        }

        this.getColumn(ColumnKind.SPEEDRUNNER).members.add(roster.get(0));
        this.getColumn(ColumnKind.SPEEDRUNNER).members.add(roster.get(1));
        for (int i = 2; i < roster.size(); i++) {
            this.getColumn(ColumnKind.HUNTER).members.add(roster.get(i));
        }
        this.statusMessage = "Two runners assigned.";
    }

    private void evenSplit() {
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        this.clearAssignments();
        if (roster.size() < 2) {
            this.statusMessage = "Need at least two players.";
            return;
        }

        boolean toRunner = true;
        for (SessionSnapshotData.RosterEntry entry : roster) {
            if (toRunner) {
                this.getColumn(ColumnKind.SPEEDRUNNER).members.add(entry);
            } else {
                this.getColumn(ColumnKind.HUNTER).members.add(entry);
            }
            toRunner = !toRunner;
        }
        this.statusMessage = "Even split applied.";
    }

    private void allOthersHunters() {
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        if (roster.isEmpty()) {
            this.statusMessage = "No players to assign.";
            return;
        }

        this.getColumn(ColumnKind.HUNTER).members.clear();
        for (SessionSnapshotData.RosterEntry entry : roster) {
            if (this.getColumn(ColumnKind.SPEEDRUNNER).members.stream().noneMatch(runner -> runner.uuid().equals(entry.uuid()))) {
                this.getColumn(ColumnKind.HUNTER).members.add(entry);
            }
        }
        this.statusMessage = "All other players moved to hunters.";
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

         String validation = this.getStartValidationMessage();
         if (!validation.isEmpty()) {
             this.statusMessage = validation;
             return;
         }

         NbtCompound plan = new NbtCompound();
         plan.putString("game", SessionGameType.MANHUNT.getCommandName());
         plan.putString("name", this.sessionNameField.getText().trim());
         plan.putBoolean("launch", true);
         plan.put("settings", this.buildSettingsCompound());

         // Don't add groups - all players (speedrunners and hunters) will be on the same server
         // Settings will contain the roles for players
         ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(SessionGameType.MANHUNT.getCommandName(), this.sessionNameField.getText().trim(), plan));
         this.statusMessage = "Requested Manhunt session creation.";
     }

     private NbtCompound buildSettingsCompound() {
         NbtCompound settings = new NbtCompound();
         settings.putBoolean("huntersCompass", this.huntersCompassEnabled);

         this.gracePeriodSeconds = this.readClampedInt(this.gracePeriodField, this.gracePeriodSeconds, GRACE_MIN_SECONDS, GRACE_MAX_SECONDS, "Release delay must be a number.");
         settings.putInt("hunterReleaseDelaySeconds", this.gracePeriodSeconds);

         this.respawnDelaySeconds = this.readClampedInt(this.respawnDelayField, this.respawnDelaySeconds, 0, RESPAWN_DELAY_MAX_SECONDS, "Runner respawn delay must be a number.");
         settings.putInt("speedrunnerRespawnDelaySeconds", this.respawnDelaySeconds);

         this.hunterRespawnDelaySeconds = this.readClampedInt(this.hunterRespawnDelayField, this.hunterRespawnDelaySeconds, 0, RESPAWN_DELAY_MAX_SECONDS, "Hunter respawn delay must be a number.");
         settings.putInt("hunterRespawnDelaySeconds", this.hunterRespawnDelaySeconds);

         this.compassCooldownSeconds = this.readClampedInt(this.compassCooldownField, this.compassCooldownSeconds, 0, 300, "Compass cooldown must be a number.");
         settings.putInt("compassCooldownSeconds", this.compassCooldownSeconds);

         this.runnerGlowPulseMinutes = this.readClampedInt(this.runnerGlowPulseField, this.runnerGlowPulseMinutes, 0, 120, "Glow pulse must be a number.");
         settings.putInt("runnerGlowPulseMinutes", this.runnerGlowPulseMinutes);

         settings.putInt("runnerLives", this.runnerLives);
         settings.putInt("hunterLives", this.hunterLives);

         settings.putBoolean("netherTracking", this.netherTrackingEnabled);

         SeedMode parsed = parseSeedModeField();
         settings.putString("seedMode", parsed.nbtValue);
         if (parsed == SeedMode.FIXED) {
             settings.putLong("seed", this.getSelectedSeedValue());
         }

         // Add roles to settings (since they're no longer in groups)
         List<SessionSnapshotData.RosterEntry> speedrunners = this.getColumn(ColumnKind.SPEEDRUNNER).members;
         List<SessionSnapshotData.RosterEntry> hunters = this.getColumn(ColumnKind.HUNTER).members;
         NbtList roles = new NbtList();
         for (SessionSnapshotData.RosterEntry entry : speedrunners) {
             roles.add(this.roleMember(entry, "speedrunner"));
         }
         for (SessionSnapshotData.RosterEntry entry : hunters) {
             roles.add(this.roleMember(entry, "hunter"));
         }
         if (!roles.isEmpty()) {
             settings.put("roles", roles);
         }

         return settings;
     }

    private int readClampedInt(TextFieldWidget field, int fallback, int min, int max, String errorMessage) {
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

    private boolean isShiftDown() {
        return InputUtil.isKeyPressed(this.client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
            || InputUtil.isKeyPressed(this.client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
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

    private String getStartValidationMessage() {
        if (this.client.player == null) {
            return "Not connected to a server.";
        }
        if (this.sessionNameField.getText().trim().isEmpty()) {
            return "Enter a session name.";
        }
        if (SessionSnapshotData.roster().isEmpty()) {
            return "No players online.";
        }

        List<SessionSnapshotData.RosterEntry> speedrunners = this.getColumn(ColumnKind.SPEEDRUNNER).members;
        List<SessionSnapshotData.RosterEntry> hunters = this.getColumn(ColumnKind.HUNTER).members;
        if (speedrunners.isEmpty()) {
            return "Need at least one speedrunner.";
        }
        if (hunters.isEmpty()) {
            return "Need at least one hunter.";
        }

        SeedMode requestedMode = parseSeedModeField();
        if (requestedMode == SeedMode.FIXED) {
            try {
                this.getSelectedSeedValue();
            } catch (NumberFormatException ignored) {
                return "Fixed seed is invalid.";
            }
        }

        return "";
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

        int startButtonWidth = 96;
        int clearRolesWidth = 82;
        int allHuntersWidth = 84;
        int evenSplitWidth = 80;
        int twoRunnersWidth = 70;
        int randomRunnerWidth = 96;
        int autoBalanceWidth = 104;

        int startButtonX = panelX + panelWidth - PANEL_PADDING - startButtonWidth;
        int clearRolesX = startButtonX - TOP_BUTTON_GAP - clearRolesWidth;
        int allHuntersX = clearRolesX - TOP_BUTTON_GAP - allHuntersWidth;
        int evenSplitX = allHuntersX - TOP_BUTTON_GAP - evenSplitWidth;
        int twoRunnersX = evenSplitX - TOP_BUTTON_GAP - twoRunnersWidth;
        int randomRunnerX = twoRunnersX - TOP_BUTTON_GAP - randomRunnerWidth;
        int autoBalanceX = randomRunnerX - TOP_BUTTON_GAP - autoBalanceWidth;

        int sessionFieldX = panelX + 118;
        int sessionFieldWidth = Math.min(220, Math.max(140, autoBalanceX - sessionFieldX - 12));

        int footerButtonsY = panelY + panelHeight - 32;
        int footerResetWidth = 64;
        int footerBackWidth = 60;
        int footerTotalWidth = footerResetWidth + footerBackWidth + FOOTER_BUTTON_GAP;
        int footerLeft = panelX + (panelWidth - footerTotalWidth) / 2;

        int actionButtonsY = footerButtonsY - SETTINGS_SECTION_HEIGHT - 42;
        int settingsY = actionButtonsY + 30;
        int columnsY = panelY + COLUMN_TOP_Y;
        int columnsHeight = Math.max(168, actionButtonsY - columnsY - 10);

        int assignSpeedrunnerWidth = 150;
        int assignHunterWidth = 126;
        int removeFromTeamWidth = 140;
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
            randomRunnerX,
            randomRunnerWidth,
            twoRunnersX,
            twoRunnersWidth,
            evenSplitX,
            evenSplitWidth,
            startButtonX,
            startButtonWidth,
            allHuntersX,
            allHuntersWidth,
            clearRolesX,
            clearRolesWidth,
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
            footerButtonsY,
            footerLeft,
            footerResetWidth,
            footerLeft + footerResetWidth + FOOTER_BUTTON_GAP,
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

    private static String formatLives(int lives) {
        return lives < 0 ? "Unlimited" : Integer.toString(lives);
    }

    private static int nextLivesValue(int current) {
        int[] cycle = {-1, 1, 2, 3, 5};
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) {
                return cycle[(i + 1) % cycle.length];
            }
        }
        return -1;
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
        int randomRunnerX,
        int randomRunnerWidth,
        int twoRunnersX,
        int twoRunnersWidth,
        int evenSplitX,
        int evenSplitWidth,
        int startButtonX,
        int startButtonWidth,
        int allHuntersX,
        int allHuntersWidth,
        int clearRolesX,
        int clearRolesWidth,
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
        int footerButtonsY,
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

    private record LivesPreset(String label, int value) {
    }
}
