package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ManhuntWorkspaceView implements WorkspaceView {
    private static final int ROW_HEIGHT = 20;
    private static final int COLUMN_HEADER_HEIGHT = 22;
    private static final int COLUMN_GAP = 12;
    private static final int SETTINGS_FIELD_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 22;
    private static final int[] COLUMN_COLORS = {0x7C8088, 0x4D8DFF, 0xE85D75};
    private static final int RESPAWN_DELAY_DEFAULT_SECONDS = 300;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<ColumnState> columns = new ArrayList<>();
    private final Map<String, UiAnimation.Value> rowHovers = new HashMap<>();
    private Module activeModule = Module.TEAMS;
    private UiLayout.Rect workspace = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect teamsArea = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect restHuntersButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect clearButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect startButton = new UiLayout.Rect(0, 0, 0, 0);
    private String selectedPlayerUuid = "";
    private String statusMessage = "";
    private SessionSnapshotData.RosterEntry draggedEntry;
    private ColumnKind draggedFrom = ColumnKind.AVAILABLE;
    private double dragX;
    private double dragY;

    private TextFieldWidget sessionNameField;
    private TextFieldWidget gracePeriodField;
    private TextFieldWidget seedValueField;
    private TextFieldWidget respawnDelayField;
    private TextFieldWidget hunterRespawnDelayField;
    private TextFieldWidget compassCooldownField;
    private TextFieldWidget runnerGlowPulseField;
    private ButtonWidget seedModeButton;
    private ButtonWidget huntersCompassButton;
    private ButtonWidget netherTrackingButton;
    private ButtonWidget runnerLivesButton;
    private ButtonWidget hunterLivesButton;

    private String sessionName = "manhunt-" + System.currentTimeMillis();
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
    private long seedValue = System.currentTimeMillis();

    public ManhuntWorkspaceView() {
        this.columns.add(new ColumnState(ColumnKind.AVAILABLE, "Available", COLUMN_COLORS[0]));
        this.columns.add(new ColumnState(ColumnKind.SPEEDRUNNER, "Speedrunners", COLUMN_COLORS[1]));
        this.columns.add(new ColumnState(ColumnKind.HUNTER, "Hunters", COLUMN_COLORS[2]));
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.workspace = workspace;
        UiLayout.Rect mainPanel = workspace.inset(4);
        this.teamsArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 84, mainPanel.width() - 24, mainPanel.height() - 106);
        this.restHuntersButton = new UiLayout.Rect(mainPanel.x() + 14, mainPanel.y() + 50, 102, BUTTON_HEIGHT);
        this.clearButton = new UiLayout.Rect(mainPanel.x() + 124, mainPanel.y() + 50, 62, BUTTON_HEIGHT);
        this.startButton = new UiLayout.Rect(mainPanel.x() + mainPanel.width() - 126, mainPanel.y() + 10, 112, BUTTON_HEIGHT);

        if (this.activeModule == Module.SESSION_INFO) {
            this.addSessionInfoWidgets(screen, mainPanel);
        } else if (this.activeModule == Module.MATCH_RULES) {
            this.gracePeriodField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 96, Integer.toString(this.gracePeriodSeconds), "Hunter release seconds");
            this.addStepper(screen, this.gracePeriodField, mainPanel.x() + 338, mainPanel.y() + 96, 0, 3600, 5);
        } else if (this.activeModule == Module.TRACKING) {
            this.huntersCompassButton = this.addButton(screen, "Hunters Compass: " + onOff(this.huntersCompassEnabled), mainPanel.x() + 180, mainPanel.y() + 96, 170, () -> {
                this.huntersCompassEnabled = !this.huntersCompassEnabled;
                this.huntersCompassButton.setMessage(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)));
            });
            this.compassCooldownField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 128, Integer.toString(this.compassCooldownSeconds), "Compass cooldown seconds");
            this.addStepper(screen, this.compassCooldownField, mainPanel.x() + 338, mainPanel.y() + 128, 0, 300, 1);
            this.netherTrackingButton = this.addButton(screen, "Nether Tracking: " + onOff(this.netherTrackingEnabled), mainPanel.x() + 180, mainPanel.y() + 160, 170, () -> {
                this.netherTrackingEnabled = !this.netherTrackingEnabled;
                this.netherTrackingButton.setMessage(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)));
            });
        } else if (this.activeModule == Module.LIVES_RESPAWNS) {
            this.runnerLivesButton = this.addButton(screen, "Runner Lives: " + formatLives(this.runnerLives), mainPanel.x() + 180, mainPanel.y() + 96, 170, () -> {
                this.runnerLives = nextLivesValue(this.runnerLives);
                this.runnerLivesButton.setMessage(Text.literal("Runner Lives: " + formatLives(this.runnerLives)));
            });
            this.respawnDelayField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 128, Integer.toString(this.respawnDelaySeconds), "Runner respawn seconds");
            this.addStepper(screen, this.respawnDelayField, mainPanel.x() + 338, mainPanel.y() + 128, 0, 3600, 30);
            this.hunterLivesButton = this.addButton(screen, "Hunter Lives: " + formatLives(this.hunterLives), mainPanel.x() + 180, mainPanel.y() + 168, 170, () -> {
                this.hunterLives = nextLivesValue(this.hunterLives);
                this.hunterLivesButton.setMessage(Text.literal("Hunter Lives: " + formatLives(this.hunterLives)));
            });
            this.hunterRespawnDelayField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 200, Integer.toString(this.hunterRespawnDelaySeconds), "Hunter respawn seconds");
            this.addStepper(screen, this.hunterRespawnDelayField, mainPanel.x() + 338, mainPanel.y() + 200, 0, 3600, 5);
        } else if (this.activeModule == Module.DIFFICULTY) {
            this.runnerGlowPulseField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 96, Integer.toString(this.runnerGlowPulseMinutes), "Runner glow pulse minutes");
            this.addStepper(screen, this.runnerGlowPulseField, mainPanel.x() + 338, mainPanel.y() + 96, 0, 120, 1);
        }
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(mainPanel.x() + 1, mainPanel.y() + 1, mainPanel.x() + mainPanel.width() - 1, mainPanel.y() + 40, 0x701B2634);
        context.drawText(textRenderer, Text.literal(activeModule.label()), mainPanel.x() + 14, mainPanel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(activeModule.description()), mainPanel.x() + 14, mainPanel.y() + 28, UiTheme.TEXT_DIM, false);

        if (this.activeModule == Module.TEAMS) {
            this.renderTeamActions(context, textRenderer, mouseX, mouseY);
            this.renderTeams(context, textRenderer, this.teamsArea, mouseX, mouseY);
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
        if (this.activeModule != Module.TEAMS && this.activeModule != Module.SUMMARY) {
            this.drawModuleLabels(context, textRenderer, mainPanel);
        }
        if (this.draggedEntry != null) {
            int x = (int) this.dragX + 10;
            int y = (int) this.dragY + 10;
            int width = Math.max(104, textRenderer.getWidth(this.draggedEntry.name()) + 28);
            UiRenderer.panel(context, x, y, width, 22, UiTheme.PANEL_RAISED, UiTheme.ACCENT);
            context.drawText(textRenderer, Text.literal(this.draggedEntry.name()), x + 12, y + 7, UiTheme.TEXT, false);
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
        if (this.activeModule != Module.TEAMS) {
            return false;
        }
        if (this.restHuntersButton.contains(mouseX, mouseY)) {
            this.restHunters();
            return true;
        }
        if (this.clearButton.contains(mouseX, mouseY)) {
            this.resetAssignments();
            return true;
        }

        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            UiLayout.Rect rect = this.columnRect(i);
            if (!rect.contains(mouseX, mouseY)) {
                continue;
            }
            int row = column.rowAt(mouseY, rect);
            List<SessionSnapshotData.RosterEntry> entries = this.getEntries(column.kind);
            int index = column.scrollOffset + row;
            if (row >= 0 && index >= 0 && index < entries.size()) {
                this.selectedPlayerUuid = entries.get(index).uuid();
                this.draggedEntry = entries.get(index);
                this.draggedFrom = column.kind;
                this.dragX = mouseX;
                this.dragY = mouseY;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && this.draggedEntry != null) {
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || this.draggedEntry == null) {
            return false;
        }
        ColumnKind target = this.dragTarget(mouseX, mouseY);
        if (target != ColumnKind.NONE) {
            this.moveEntryTo(this.draggedEntry, target);
            this.statusMessage = this.draggedEntry.name() + " moved to " + target.displayName + ".";
        }
        this.draggedEntry = null;
        this.draggedFrom = ColumnKind.NONE;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeModule != Module.TEAMS) {
            return false;
        }
        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            UiLayout.Rect rect = this.columnRect(i);
            if (rect.contains(mouseX, mouseY)) {
                int maxScroll = Math.max(0, this.getEntries(column.kind).size() - column.visibleRows(rect.height()));
                column.scrollOffset = Math.clamp(column.scrollOffset - (int) Math.signum(verticalAmount), 0, maxScroll);
                return maxScroll > 0;
            }
        }
        return false;
    }

    public void refreshRoster() {
        this.getColumn(ColumnKind.SPEEDRUNNER).members.removeIf(entry -> !this.isOnline(entry.uuid()));
        this.getColumn(ColumnKind.HUNTER).members.removeIf(entry -> !this.isOnline(entry.uuid()));
    }

    public Module activeModule() {
        return this.activeModule;
    }

    public void setActiveModule(Module activeModule) {
        this.syncStateFromWidgets();
        this.activeModule = activeModule;
    }

    @Override
    public String title() {
        return "Manhunt Setup";
    }

    @Override
    public String subtitle() {
        return "Embedded workspace / module-driven setup";
    }

    private void renderSettingsModule(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        int moduleX = mainPanel.x() + 14;
        int moduleY = mainPanel.y() + 72;
        int moduleWidth = Math.min(520, mainPanel.width() - 28);
        int moduleHeight = Math.min(230, mainPanel.height() - 104);
        UiRenderer.panel(context, moduleX, moduleY, moduleWidth, moduleHeight, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(moduleX, moduleY, moduleX + 3, moduleY + moduleHeight, this.activeModule.accent());
        context.drawText(textRenderer, Text.literal(this.activeModule.label()), moduleX + 12, moduleY + 12, this.activeModule.accent(), false);
    }

    private void drawModuleLabels(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        int labelX = mainPanel.x() + 38;
        int y = mainPanel.y() + 102;
        switch (this.activeModule) {
            case SESSION_INFO -> {
                context.drawText(textRenderer, Text.literal("Session Name"), labelX, y, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Seed Mode"), labelX, y + 32, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Fixed Seed"), labelX, y + 64, UiTheme.TEXT_MUTED, false);
            }
            case MATCH_RULES -> context.drawText(textRenderer, Text.literal("Hunter Release Delay"), labelX, y, UiTheme.TEXT_MUTED, false);
            case TRACKING -> {
                context.drawText(textRenderer, Text.literal("Hunters Compass"), labelX, y, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Compass Cooldown"), labelX, y + 32, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Nether Tracking"), labelX, y + 64, UiTheme.TEXT_MUTED, false);
            }
            case LIVES_RESPAWNS -> {
                context.drawText(textRenderer, Text.literal("Runner Lives"), labelX, y, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Runner Respawn"), labelX, y + 32, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Hunter Lives"), labelX, y + 72, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Hunter Respawn"), labelX, y + 104, UiTheme.TEXT_MUTED, false);
            }
            case DIFFICULTY -> context.drawText(textRenderer, Text.literal("Runner Glow Pulse"), labelX, y, UiTheme.TEXT_MUTED, false);
            default -> {
            }
        }
    }

    private void addSessionInfoWidgets(SessionScreen screen, UiLayout.Rect mainPanel) {
        this.sessionNameField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 96, this.sessionName, "Session name");
        this.seedModeButton = this.addButton(screen, this.seedMode.displayName, mainPanel.x() + 180, mainPanel.y() + 128, SETTINGS_FIELD_WIDTH, () -> {
            this.seedMode = this.seedMode == SeedMode.RANDOM ? SeedMode.FIXED : SeedMode.RANDOM;
            this.seedModeButton.setMessage(Text.literal(this.seedMode.displayName));
        });
        this.seedValueField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 160, Long.toString(this.seedValue), "Fixed seed");
    }

    private ButtonWidget addButton(SessionScreen screen, String label, int x, int y, int width, Runnable action) {
        return screen.addWorkspaceChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
            .dimensions(x, y, width, BUTTON_HEIGHT)
            .build());
    }

    private TextFieldWidget addField(SessionScreen screen, int x, int y, String value, String narration) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, SETTINGS_FIELD_WIDTH, BUTTON_HEIGHT, Text.literal(narration));
        field.setMaxLength(64);
        field.setText(value);
        return screen.addWorkspaceChild(field);
    }

    private void addStepper(SessionScreen screen, TextFieldWidget field, int x, int y, int min, int max, int step) {
        this.addButton(screen, "-", x, y, 24, () -> stepField(field, min, max, -step));
        this.addButton(screen, "+", x + 30, y, 24, () -> stepField(field, min, max, step));
    }

    private void renderTeams(DrawContext context, TextRenderer textRenderer, UiLayout.Rect area, int mouseX, int mouseY) {
        int columnWidth = (area.width() - COLUMN_GAP * 2) / 3;
        for (int i = 0; i < this.columns.size(); i++) {
            ColumnState column = this.columns.get(i);
            UiLayout.Rect rect = new UiLayout.Rect(area.x() + i * (columnWidth + COLUMN_GAP), area.y(), columnWidth, area.height());
            this.renderColumn(context, textRenderer, rect, column, mouseX, mouseY, this.draggedEntry != null && rect.contains(mouseX, mouseY));
        }
    }

    private void renderColumn(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, ColumnState column, int mouseX, int mouseY, boolean dropTarget) {
        List<SessionSnapshotData.RosterEntry> entries = this.getEntries(column.kind);
        int accent = 0xFF000000 | column.accentColor;
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), dropTarget ? UiTheme.CARD_HOVER : UiTheme.CARD, dropTarget ? accent : UiTheme.BORDER_SUBTLE);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + 3, accent);
        context.drawText(textRenderer, Text.literal(column.title + " (" + entries.size() + ")"), rect.x() + 8, rect.y() + 7, UiTheme.TEXT, false);

        int visibleRows = column.visibleRows(rect.height());
        int rows = Math.min(entries.size() - column.scrollOffset, visibleRows);
        int listTop = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        for (int row = 0; row < rows; row++) {
            SessionSnapshotData.RosterEntry entry = entries.get(column.scrollOffset + row);
            if (this.draggedEntry != null && this.draggedEntry.uuid().equals(entry.uuid())) {
                continue;
            }
            int rowY = listTop + row * ROW_HEIGHT;
            boolean selected = entry.uuid().equals(this.selectedPlayerUuid);
            boolean hovered = mouseX >= rect.x() + 1 && mouseX <= rect.x() + rect.width() - 1 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
            UiAnimation.Value hover = this.rowHovers.computeIfAbsent(column.kind.name() + ":" + entry.uuid(), ignored -> new UiAnimation.Value(0.0F));
            hover.animateTo(hovered ? 1.0F : 0.0F, UiTheme.HOVER_MS);
            float progress = hover.get();
            int background = selected ? UiAnimation.lerpColor(0xAA2F5D94, 0xCC3E79B8, progress) : UiAnimation.lerpColor(0x26222A34, 0x66304052, progress);
            context.fill(rect.x() + 1, rowY, rect.x() + rect.width() - 1, rowY + ROW_HEIGHT - 2, background);
            context.fill(rect.x() + 6, rowY + 4, rect.x() + 10, rowY + ROW_HEIGHT - 5, UiAnimation.lerpColor(accent, UiTheme.ACCENT, progress * 0.35F));
            context.drawText(textRenderer, Text.literal(entry.name()), rect.x() + 16, rowY + 6, selected ? UiTheme.TEXT : UiAnimation.lerpColor(UiTheme.TEXT_MUTED, UiTheme.TEXT, progress), false);
        }
        this.drawScrollbar(context, rect, entries.size(), visibleRows, column.scrollOffset);
    }

    private void renderTeamActions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        this.renderActionButton(context, textRenderer, this.restHuntersButton, "Rest Hunters", UiTheme.ACCENT_RED, this.restHuntersButton.contains(mouseX, mouseY));
        this.renderActionButton(context, textRenderer, this.clearButton, "Clear", UiTheme.ACCENT, this.clearButton.contains(mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("Drag players between columns to assign roles."), this.clearButton.x() + this.clearButton.width() + 12, this.clearButton.y() + 7, UiTheme.TEXT_DIM, false);
    }

    private void renderActionButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(accent, 0.34F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, border);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    private void renderSummary(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        this.syncStateFromWidgets();
        int x = mainPanel.x() + 14;
        int y = mainPanel.y() + 72;
        int width = Math.min(540, mainPanel.width() - 28);
        UiRenderer.panel(context, x, y, width, 194, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(x, y, x + 3, y + 194, UiTheme.ACCENT);
        int line = y + 18;
        context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
        line += 20;
        context.drawText(textRenderer, Text.literal(this.getColumn(ColumnKind.SPEEDRUNNER).members.size() + " speedrunner(s) vs " + this.getColumn(ColumnKind.HUNTER).members.size() + " hunter(s)"), x + 14, line, UiTheme.TEXT_MUTED, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Release delay: " + this.gracePeriodSeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Tracking: " + onOff(this.huntersCompassEnabled) + ", Nether " + onOff(this.netherTrackingEnabled) + ", cooldown " + this.compassCooldownSeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Runner lives: " + formatLives(this.runnerLives) + ", respawn " + this.respawnDelaySeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Hunter lives: " + formatLives(this.hunterLives) + ", respawn " + this.hunterRespawnDelaySeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Seed: " + this.seedMode.displayName), x + 14, line, UiTheme.TEXT_MUTED, false);
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            context.drawText(textRenderer, Text.literal(validation), x + 14, y + 168, UiTheme.WARNING, false);
        }
    }

    private UiLayout.Rect columnRect(int index) {
        int columnWidth = (this.teamsArea.width() - COLUMN_GAP * 2) / 3;
        return new UiLayout.Rect(this.teamsArea.x() + index * (columnWidth + COLUMN_GAP), this.teamsArea.y(), columnWidth, this.teamsArea.height());
    }

    private void drawScrollbar(DrawContext context, UiLayout.Rect rect, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows || visibleRows <= 0) {
            return;
        }
        int trackX = rect.x() + rect.width() - 7;
        int trackY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int trackHeight = rect.height() - COLUMN_HEADER_HEIGHT - 8;
        int thumbHeight = Math.max(14, (int) ((trackHeight * (double) visibleRows) / totalRows));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbY = trackY + (int) (((trackHeight - thumbHeight) * (double) scrollOffset) / maxScroll);
        context.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0x66101822);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, UiTheme.BORDER_STRONG);
    }

    private void createSession() {
        this.syncStateFromWidgets();
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            this.statusMessage = validation;
            return;
        }

        NbtCompound plan = new NbtCompound();
        plan.putString("game", ManhuntDefinition.ID);
        plan.putString("name", this.sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.buildSettingsCompound());

        NbtList groups = new NbtList();
        NbtCompound group = new NbtCompound();
        group.putString("id", "manhunt");
        group.putString("name", "Manhunt");
        NbtList members = new NbtList();
        NbtList roles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.getColumn(ColumnKind.SPEEDRUNNER).members) {
            members.add(this.member(entry));
            roles.add(this.roleMember(entry, "speedrunner"));
        }
        for (SessionSnapshotData.RosterEntry entry : this.getColumn(ColumnKind.HUNTER).members) {
            members.add(this.member(entry));
            roles.add(this.roleMember(entry, "hunter"));
        }
        group.put("members", members);
        group.put("roles", roles);
        groups.add(group);
        plan.put("groups", groups);

        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(ManhuntDefinition.ID, this.sessionName, plan));
        this.statusMessage = "Requested Manhunt session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putBoolean("huntersCompass", this.huntersCompassEnabled);
        settings.putInt("hunterReleaseDelaySeconds", this.gracePeriodSeconds);
        settings.putInt("speedrunnerRespawnDelaySeconds", this.respawnDelaySeconds);
        settings.putInt("hunterRespawnDelaySeconds", this.hunterRespawnDelaySeconds);
        settings.putInt("compassCooldownSeconds", this.compassCooldownSeconds);
        settings.putInt("runnerGlowPulseMinutes", this.runnerGlowPulseMinutes);
        settings.putInt("runnerLives", this.runnerLives);
        settings.putInt("hunterLives", this.hunterLives);
        settings.putBoolean("netherTracking", this.netherTrackingEnabled);
        settings.putString("seedMode", this.seedMode.nbtValue);
        if (this.seedMode == SeedMode.FIXED) {
            settings.putLong("seed", this.seedValue);
        }
        NbtList roles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.getColumn(ColumnKind.SPEEDRUNNER).members) {
            roles.add(this.roleMember(entry, "speedrunner"));
        }
        for (SessionSnapshotData.RosterEntry entry : this.getColumn(ColumnKind.HUNTER).members) {
            roles.add(this.roleMember(entry, "hunter"));
        }
        settings.put("roles", roles);
        return settings;
    }

    private void syncStateFromWidgets() {
        if (this.sessionNameField != null) {
            this.sessionName = this.sessionNameField.getText().trim();
        }
        this.gracePeriodSeconds = readClamped(this.gracePeriodField, this.gracePeriodSeconds, 0, 3600);
        this.respawnDelaySeconds = readClamped(this.respawnDelayField, this.respawnDelaySeconds, 0, 3600);
        this.hunterRespawnDelaySeconds = readClamped(this.hunterRespawnDelayField, this.hunterRespawnDelaySeconds, 0, 3600);
        this.compassCooldownSeconds = readClamped(this.compassCooldownField, this.compassCooldownSeconds, 0, 300);
        this.runnerGlowPulseMinutes = readClamped(this.runnerGlowPulseField, this.runnerGlowPulseMinutes, 0, 120);
        if (this.seedValueField != null) {
            try {
                this.seedValue = Long.parseLong(this.seedValueField.getText().trim());
            } catch (NumberFormatException ignored) {
                this.seedValueField.setText(Long.toString(this.seedValue));
            }
        }
    }

    private static int readClamped(TextFieldWidget field, int fallback, int min, int max) {
        if (field == null) {
            return fallback;
        }
        try {
            int value = Math.clamp(Integer.parseInt(field.getText().trim()), min, max);
            field.setText(Integer.toString(value));
            return value;
        } catch (NumberFormatException ignored) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
    }

    private void stepField(TextFieldWidget field, int min, int max, int delta) {
        int value = readClamped(field, min, min, max);
        field.setText(Integer.toString(Math.clamp(value + delta, min, max)));
    }

    private String getStartValidationMessage() {
        if (this.client.player == null) {
            return "Not connected to a server.";
        }
        if (this.sessionName == null || this.sessionName.isBlank()) {
            return "Enter a session name.";
        }
        if (SessionSnapshotData.roster().isEmpty()) {
            return "No players online.";
        }
        if (this.getColumn(ColumnKind.SPEEDRUNNER).members.isEmpty()) {
            return "Need at least one speedrunner.";
        }
        if (this.getColumn(ColumnKind.HUNTER).members.isEmpty()) {
            return "Need at least one hunter.";
        }
        return "";
    }

    private void resetAssignments() {
        this.getColumn(ColumnKind.SPEEDRUNNER).members.clear();
        this.getColumn(ColumnKind.HUNTER).members.clear();
        this.selectedPlayerUuid = "";
    }

    private void restHunters() {
        this.getColumn(ColumnKind.HUNTER).members.clear();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            if (!this.getColumn(ColumnKind.SPEEDRUNNER).contains(entry.uuid())) {
                this.getColumn(ColumnKind.HUNTER).members.add(entry);
            }
        }
        this.statusMessage = "Moved all non-speedrunners to hunters.";
    }

    private void moveEntryTo(SessionSnapshotData.RosterEntry entry, ColumnKind target) {
        this.getColumn(ColumnKind.SPEEDRUNNER).remove(entry.uuid());
        this.getColumn(ColumnKind.HUNTER).remove(entry.uuid());
        if (target != ColumnKind.AVAILABLE) {
            this.getColumn(target).members.add(entry);
        }
        this.selectedPlayerUuid = entry.uuid();
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

    private boolean isOnline(String uuid) {
        return SessionSnapshotData.roster().stream().anyMatch(entry -> entry.uuid().equals(uuid));
    }

    private ColumnKind dragTarget(double mouseX, double mouseY) {
        for (int i = 0; i < this.columns.size(); i++) {
            UiLayout.Rect rect = this.columnRect(i);
            if (rect.contains(mouseX, mouseY)) {
                return this.columns.get(i).kind;
            }
        }
        return this.draggedFrom;
    }

    private ColumnState getColumn(ColumnKind kind) {
        for (ColumnState column : this.columns) {
            if (column.kind == kind) {
                return column;
            }
        }
        throw new IllegalStateException("Missing column: " + kind);
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

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
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

    public enum Module {
        TEAMS(">", "Teams", "Assign players into speedrunners and hunters.", UiTheme.ACCENT_RED),
        SESSION_INFO("i", "Session Info", "Name the session and choose seed behavior.", UiTheme.ACCENT),
        MATCH_RULES("r", "Match Rules", "Tune release timing and match startup rules.", UiTheme.ACCENT),
        TRACKING("t", "Tracking", "Configure compass behavior and Nether tracking.", UiTheme.ACCENT_BLUE),
        LIVES_RESPAWNS("l", "Lives & Respawns", "Set life limits and respawn delays.", UiTheme.ACCENT_RED),
        DIFFICULTY("d", "Difficulty", "Add pressure and visibility modifiers.", UiTheme.ACCENT_GREEN),
        SUMMARY("s", "Summary", "Review and launch the configured match.", UiTheme.ACCENT);

        private final String icon;
        private final String label;
        private final String description;
        private final int accent;

        Module(String icon, String label, String description, int accent) {
            this.icon = icon;
            this.label = label;
            this.description = description;
            this.accent = accent;
        }

        public String icon() {
            return this.icon;
        }

        public String label() {
            return this.label;
        }

        private String description() {
            return this.description;
        }

        private int accent() {
            return this.accent;
        }
    }

    private enum ColumnKind {
        NONE(""),
        AVAILABLE("Available"),
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
            return Math.max(0, (height - COLUMN_HEADER_HEIGHT - 8) / ROW_HEIGHT);
        }

        private int rowAt(double mouseY, UiLayout.Rect rect) {
            int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
            if (mouseY < rowStartY || mouseY > rect.y() + rect.height()) {
                return -1;
            }
            return (int) ((mouseY - rowStartY) / ROW_HEIGHT);
        }

        private void remove(String uuid) {
            this.members.removeIf(entry -> entry.uuid().equals(uuid));
        }

        private boolean contains(String uuid) {
            return this.members.stream().anyMatch(entry -> entry.uuid().equals(uuid));
        }
    }
}
