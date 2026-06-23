package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionLaunchStatus;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class AdminWorkspaceView implements WorkspaceView {
    private static final int ROW_HEIGHT = 148;
    private static final int ROW_GAP = 10;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 8;
    private static final int SETTINGS_ROW_HEIGHT = 28;
    private static final int SETTINGS_SECTION_TITLE_HEIGHT = 18;
    private static final int SETTINGS_SECTION_GAP = 8;
    private static final int SETTINGS_SAVE_WIDTH = 68;
    private static final String[] DIFFICULTY_VALUES = {"peaceful", "easy", "normal", "hard"};
    private static final String[] DIFFICULTY_LABELS = {"Peaceful", "Easy", "Medium", "Hard"};
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("'['hh:mm a , dd/MMM/yyyy']'").withZone(ZoneId.systemDefault());

    private final Mode mode;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<ActionButton> buttons = new ArrayList<>();
    private SessionScreen screen;
    private UiLayout.Rect panel = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect listArea = new UiLayout.Rect(0, 0, 0, 0);
    private String statusMessage = "";
    private String assignmentSessionId = "";
    private int scrollOffset;
    private int maxScroll;
    private HistorySortOrder historySortOrder = HistorySortOrder.LATEST_PLAYED;
    private boolean draggingScrollbar;

    private TextFieldWidget viewDistanceField;
    private TextFieldWidget simulationDistanceField;
    private TextFieldWidget spawnProtectionField;
    private TextFieldWidget advertisedHostField;
    private TextFieldWidget maxHeapField;
    private TextFieldWidget initialHeapField;
    private TextFieldWidget maxConcurrentLaunchesField;

    private TextFieldWidget maxAgeDaysField;

    private boolean onlineMode;
    private boolean allowFlight;
    private boolean acceptsTransfers;
    private boolean memoryEnabled;
    private String difficultyValue;

    public AdminWorkspaceView(Mode mode) {
        this.mode = mode;
    }

    public static boolean hasLiveSessions() {
        return SessionSnapshotData.sessions().stream().anyMatch(session -> !session.retained());
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.screen = screen;
        this.buttons.clear();
        this.panel = workspace.inset(4);
        this.listArea = new UiLayout.Rect(this.panel.x() + 14, this.panel.y() + 58, this.panel.width() - 28, this.panel.height() - 86);
        this.loadSettingsState();

        if (this.mode == Mode.SERVER) {
            this.initServerFields();
        } else if (this.mode == Mode.LAUNCHER) {
            this.initLauncherFields();
        } else if (this.mode == Mode.HISTORY) {
            this.initHistoryFields();
        } else {
            this.rebuildScrollableButtons();
        }
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiRenderer.panel(context, this.panel.x(), this.panel.y(), this.panel.width(), this.panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(this.panel.x() + 1, this.panel.y() + 1, this.panel.x() + this.panel.width() - 1, this.panel.y() + 40, 0x701B2634);
        context.drawText(textRenderer, Text.literal(this.title()), this.panel.x() + 14, this.panel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(this.subtitle()), this.panel.x() + 14, this.panel.y() + 28, UiTheme.TEXT_DIM, false);

        if (this.mode == Mode.SERVER) {
            this.renderServer(context, textRenderer);
        } else if (this.mode == Mode.LAUNCHER) {
            this.renderLauncher(context, textRenderer);
        } else if (this.mode == Mode.HISTORY) {
            this.renderHistory(context, textRenderer, mouseX, mouseY, delta);
        } else {
            this.renderSessions(context, textRenderer, mouseX, mouseY, delta);
        }

        if (!this.statusMessage.isEmpty()) {
            context.drawText(textRenderer, Text.literal(this.statusMessage), this.panel.x() + 14, this.panel.y() + this.panel.height() - 20, UiTheme.SUCCESS, false);
        }
    }

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        for (ActionButton button : this.buttons) {
            if (!button.scrollManaged()) {
                button.render(context, textRenderer, mouseX, mouseY);
            }
        }

        if (this.mode == Mode.SESSIONS || this.mode == Mode.HISTORY) {
            int top = this.mode == Mode.HISTORY ? this.listArea.y() + 36 : this.listArea.y();
            int bottom = this.listArea.y() + this.listArea.height();
            context.enableScissor(this.listArea.x(), top, this.listArea.x() + this.listArea.width(), bottom);
            for (ActionButton button : this.buttons) {
                if (button.scrollManaged()) {
                    button.render(context, textRenderer, mouseX, mouseY);
                }
            }
            context.disableScissor();

            if (this.maxScroll > 0) {
                int scrollbarX = this.listArea.x() + this.listArea.width() - 8;
                int height = bottom - top;
                int thumbHeight = Math.max(20, (int) (height * ((float) height / (height + this.maxScroll))));
                int thumbY = top + (int) ((height - thumbHeight) * ((float) this.scrollOffset / this.maxScroll));
                context.fill(scrollbarX, top, scrollbarX + 8, bottom, 0x40000000);
                context.fill(scrollbarX, thumbY, scrollbarX + 8, thumbY + thumbHeight, this.draggingScrollbar ? 0xFFFFFFFF : 0xFFAAAAAA);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        if (this.maxScroll > 0 && (this.mode == Mode.SESSIONS || this.mode == Mode.HISTORY)) {
            int top = this.mode == Mode.HISTORY ? this.listArea.y() + 36 : this.listArea.y();
            int height = this.mode == Mode.HISTORY ? this.listArea.height() - 36 : this.listArea.height();
            int scrollbarX = this.listArea.x() + this.listArea.width() - 8;
            if (mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= top && mouseY <= top + height) {
                this.draggingScrollbar = true;
                return true;
            }
        }

        for (ActionButton actionButton : this.buttons) {
            if (actionButton.scrollManaged() && !this.inScrollableButtonViewport(mouseX, mouseY)) {
                continue;
            }
            if (actionButton.click(mouseX, mouseY)) {
                return true;
            }
        }

        if (this.mode == Mode.HISTORY && this.inScrollableButtonViewport(mouseX, mouseY)) {
            int top = this.listArea.y() + 36;
            int rowY = top - this.scrollOffset;
            for (SessionEntry entry : this.retainedSessions()) {
                if (mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT && mouseX >= this.listArea.x() && mouseX <= this.listArea.x() + this.listArea.width() - 10) {
                    this.screen.openWorkspaceView(new HistorySessionDetailsWorkspaceView(entry, this));
                    return true;
                }
                rowY += ROW_HEIGHT + ROW_GAP;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingScrollbar && this.maxScroll > 0) {
            int top = this.mode == Mode.HISTORY ? this.listArea.y() + 36 : this.listArea.y();
            int height = this.mode == Mode.HISTORY ? this.listArea.height() - 36 : this.listArea.height();
            int thumbHeight = Math.max(20, (int) (height * ((float) height / (height + this.maxScroll))));
            int trackHeight = height - thumbHeight;
            double scrollRatio = (mouseY - top - (thumbHeight / 2.0)) / (double) trackHeight;
            this.scrollOffset = Math.clamp((int) (scrollRatio * this.maxScroll), 0, this.maxScroll);
            this.rebuildScrollableButtons();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if ((this.mode == Mode.SESSIONS || this.mode == Mode.HISTORY) && this.maxScroll > 0 && this.listArea.contains(mouseX, mouseY)) {
            int delta = (int) Math.round(-verticalAmount * 18);
            this.scrollOffset = Math.clamp(this.scrollOffset + delta, 0, this.maxScroll);
            this.rebuildScrollableButtons();
            return true;
        }
        return false;
    }

    @Override
    public String title() {
        return switch (this.mode) {
            case SESSIONS -> "Sessions";
            case HISTORY -> "Session History";
            case SERVER -> "Server Defaults";
            case LAUNCHER -> "Launcher";
        };
    }

    @Override
    public String subtitle() {
        return switch (this.mode) {
            case SESSIONS -> "Live session control";
            case HISTORY -> "Retained sessions and inspection copies";
            case SERVER -> "World, transfer, and memory defaults";
            case LAUNCHER -> "Backend launch capacity";
        };
    }

    public Mode mode() {
        return this.mode;
    }

    private void initHistoryFields() {
        SessionSnapshotData.RetentionSettings retention = SessionSnapshotData.retentionSettings();
        int y = this.listArea.y();
        this.maxAgeDaysField = this.addField(this.listArea.x() + 160, y, 36, "Retention Days", Integer.toString(retention.maxAgeDays()), 3);
        this.buttons.add(new ActionButton(new UiLayout.Rect(this.listArea.x() + 204, y, 44, BUTTON_HEIGHT), () -> "Save", this::saveMaxAgeDays, UiTheme.ACCENT_GREEN, () -> true));
        this.buttons.add(new ActionButton(new UiLayout.Rect(this.listArea.x() + this.listArea.width() - 250, y, 90, BUTTON_HEIGHT), () -> "Delete All", this::confirmDeleteAllSessions, UiTheme.ACCENT_RED, () -> !this.retainedSessions().isEmpty()));
        this.buttons.add(new ActionButton(new UiLayout.Rect(this.listArea.x() + this.listArea.width() - 150, y, 150, BUTTON_HEIGHT), this::historySortLabel, this::toggleHistorySort, UiTheme.ACCENT_BLUE, () -> true));
        this.rebuildScrollableButtons();
    }

    private void initServerFields() {
        SessionSnapshotData.ServerSettings server = SessionSnapshotData.serverSettings();
        SessionSnapshotData.MemorySettings memory = SessionSnapshotData.memorySettings();
        this.onlineMode = server.onlineMode();
        this.allowFlight = server.allowFlight();
        this.acceptsTransfers = server.acceptsTransfers();
        this.memoryEnabled = memory.enabled();
        this.difficultyValue = server.difficulty();

        int y = this.serverStartY() + SETTINGS_SECTION_TITLE_HEIGHT;
        this.viewDistanceField = this.addServerField(y, this.numberFieldWidth(), "View Distance", Integer.toString(server.viewDistance()), 2);
        this.addServerSaveButton(y, this.numberFieldWidth(), "Save", this::saveViewDistance);
        y += SETTINGS_ROW_HEIGHT;
        this.simulationDistanceField = this.addServerField(y, this.numberFieldWidth(), "Simulation Distance", Integer.toString(server.simulationDistance()), 2);
        this.addServerSaveButton(y, this.numberFieldWidth(), "Save", this::saveSimulationDistance);
        y += SETTINGS_ROW_HEIGHT;
        this.spawnProtectionField = this.addServerField(y, this.numberFieldWidth(), "Spawn Protection", Integer.toString(server.spawnProtection()), 3);
        this.addServerSaveButton(y, this.numberFieldWidth(), "Save", this::saveSpawnProtection);

        y += SETTINGS_ROW_HEIGHT + SETTINGS_SECTION_GAP + SETTINGS_SECTION_TITLE_HEIGHT;
        this.addServerButton(y, this.controlWidth(), () -> this.getDifficultyLabel(this.difficultyValue), this::cycleDifficulty, UiTheme.ACCENT_BLUE);
        this.addServerSaveButton(y, this.controlWidth(), "Save", this::saveDifficulty);
        y += SETTINGS_ROW_HEIGHT;
        this.addServerButton(y, this.controlWidth(), () -> onOff(this.onlineMode), () -> this.onlineMode = !this.onlineMode, UiTheme.ACCENT_BLUE);
        this.addServerSaveButton(y, this.controlWidth(), "Save", this::saveOnlineMode);
        y += SETTINGS_ROW_HEIGHT;
        this.addServerButton(y, this.controlWidth(), () -> onOff(this.allowFlight), () -> this.allowFlight = !this.allowFlight, UiTheme.ACCENT_BLUE);
        this.addServerSaveButton(y, this.controlWidth(), "Save", this::saveAllowFlight);
        y += SETTINGS_ROW_HEIGHT;
        this.addServerButton(y, this.controlWidth(), () -> onOff(this.acceptsTransfers), () -> this.acceptsTransfers = !this.acceptsTransfers, UiTheme.ACCENT_BLUE);
        this.addServerSaveButton(y, this.controlWidth(), "Save", this::saveAcceptsTransfers);

        y += SETTINGS_ROW_HEIGHT + SETTINGS_SECTION_GAP + SETTINGS_SECTION_TITLE_HEIGHT;
        this.advertisedHostField = this.addServerField(y, this.controlWidth(), "Transfer Host", server.advertisedHost(), 255);
        this.addServerSaveButton(y, this.controlWidth(), "Save", this::saveAdvertisedHost);

        y += SETTINGS_ROW_HEIGHT + SETTINGS_SECTION_GAP + SETTINGS_SECTION_TITLE_HEIGHT;
        this.addServerButton(y, this.controlWidth(), () -> onOff(this.memoryEnabled), () -> this.memoryEnabled = !this.memoryEnabled, UiTheme.ACCENT_BLUE);
        this.addServerSaveButton(y, this.controlWidth(), "Save", this::saveMemoryEnabled);
        y += SETTINGS_ROW_HEIGHT;
        this.maxHeapField = this.addServerField(y, this.numberFieldWidth(), "Max Heap", Integer.toString(memory.maxHeapGb()), 3);
        this.addServerSaveButton(y, this.numberFieldWidth(), "Save", this::saveMaxHeap);
        y += SETTINGS_ROW_HEIGHT;
        this.initialHeapField = this.addServerField(y, this.numberFieldWidth(), "Initial Heap", Integer.toString(memory.initialHeapGb()), 3);
        this.addServerSaveButton(y, this.numberFieldWidth(), "Save", this::saveInitialHeap);

        y += SETTINGS_ROW_HEIGHT + SETTINGS_SECTION_GAP + SETTINGS_SECTION_TITLE_HEIGHT;
        this.maxConcurrentLaunchesField = this.addServerField(y, this.numberFieldWidth(), "Max Concurrent Launches", Integer.toString(SessionSnapshotData.maxConcurrentLaunches()), 2);
        this.addServerSaveButton(y, this.numberFieldWidth(), "Save Launch Limit", this::saveMaxConcurrentLaunches);
    }

    private void initLauncherFields() {
        int x = this.listArea.x();
        int y = this.listArea.y() + 34;
        this.maxConcurrentLaunchesField = this.addField(x, y, 80, "Max Concurrent Launches", Integer.toString(SessionSnapshotData.maxConcurrentLaunches()), 2);
        this.buttons.add(new ActionButton(new UiLayout.Rect(x + 90, y, 132, BUTTON_HEIGHT), () -> "Save Launch Limit", this::saveMaxConcurrentLaunches, UiTheme.ACCENT_GREEN, () -> true));
    }

    private TextFieldWidget addField(int x, int y, int width, String label, String value, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(this.client.textRenderer, x, y, width, 20, Text.literal(label));
        field.setMaxLength(maxLength);
        field.setText(value);
        return this.screen.addWorkspaceChild(field);
    }

    private TextFieldWidget addServerField(int y, int width, String label, String value, int maxLength) {
        return this.addField(this.serverControlX(), y, width, label, value, maxLength);
    }

    private void addServerButton(int y, int width, Supplier<String> label, Runnable action, int accent) {
        this.buttons.add(new ActionButton(new UiLayout.Rect(this.serverControlX(), y, width, BUTTON_HEIGHT), label, action, accent, () -> true));
    }

    private void addServerSaveButton(int y, int controlWidth, String label, Runnable action) {
        int x = this.serverControlX() + controlWidth + 8;
        int width = "Save".equals(label) ? SETTINGS_SAVE_WIDTH : 132;
        this.buttons.add(new ActionButton(new UiLayout.Rect(x, y, width, BUTTON_HEIGHT), () -> label, action, UiTheme.ACCENT_GREEN, () -> true));
    }

    private void renderSessions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        List<SessionEntry> entries = this.activeSessions();
        this.updateScrollBounds(entries, false);
        if (entries.isEmpty()) {
            SessionLaunchStatus.latest().ifPresentOrElse(
                status -> SessionLaunchStatus.renderPanel(context, textRenderer, this.listArea.x(), this.listArea.y(), Math.min(320, this.listArea.width()), status),
                () -> this.renderEmpty(context, textRenderer, "No active sessions.", false)
            );
            return;
        }
        this.renderSessionRows(context, textRenderer, entries, false, mouseX, mouseY);
    }

    private void renderHistory(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        context.drawText(textRenderer, Text.literal("Delete sessions older than"), this.listArea.x(), this.listArea.y() + 6, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("days (0 to disable)"), this.listArea.x() + 256, this.listArea.y() + 6, UiTheme.TEXT_MUTED, false);
        List<SessionEntry> entries = this.retainedSessions();
        this.updateScrollBounds(entries, true);
        if (entries.isEmpty()) {
            this.renderEmpty(context, textRenderer, "No retained sessions.", true);
            return;
        }
        this.renderSessionRows(context, textRenderer, entries, true, mouseX, mouseY);
    }

    private void renderSessionRows(DrawContext context, TextRenderer textRenderer, List<SessionEntry> entries, boolean history, int mouseX, int mouseY) {
        int top = history ? this.listArea.y() + 36 : this.listArea.y();
        int height = history ? this.listArea.height() - 36 : this.listArea.height();
        context.enableScissor(this.listArea.x(), top, this.listArea.x() + this.listArea.width(), top + height);
        int y = top - this.scrollOffset;
        for (SessionEntry entry : entries) {
            int rowHeight = history ? ROW_HEIGHT : this.rowHeight(entry);
            int rowWidth = this.listArea.width() - (this.maxScroll > 0 ? 12 : 0);
            boolean hovered = history && this.inScrollableButtonViewport(mouseX, mouseY) && mouseY >= y && mouseY <= y + rowHeight && mouseX >= this.listArea.x() && mouseX <= this.listArea.x() + rowWidth;
            int fill = hovered ? 0x20FFFFFF : 0x00000000;
            UiRenderer.card(context, this.listArea.x(), y, rowWidth, rowHeight, 0.0F, history ? UiTheme.ACCENT_BLUE : UiTheme.ACCENT_GREEN);
            if (fill != 0) {
                context.fill(this.listArea.x(), y, this.listArea.x() + rowWidth, y + rowHeight, fill);
            }
            int textX = this.listArea.x() + 14;
            if (history) {
                Text gamemodeText = Text.literal("Gamemode: ").styled(s -> s.withColor(UiTheme.TEXT_MUTED)).append(Text.literal(entry.game()).styled(s -> s.withColor(UiTheme.ACCENT_GREEN)));
                context.drawText(textRenderer, gamemodeText, textX, y + 10, 0xFFFFFFFF, false);

                Text nameText = Text.literal("Session name: ").styled(s -> s.withColor(UiTheme.TEXT_MUTED)).append(Text.literal(entry.id()).styled(s -> s.withColor(UiTheme.TEXT)));
                context.drawText(textRenderer, nameText, textX, y + 24, 0xFFFFFFFF, false);

                Text playersText = Text.literal("Players: ").styled(s -> s.withColor(UiTheme.TEXT_MUTED)).append(Text.literal(String.valueOf(entry.playerCount())).styled(s -> s.withColor(UiTheme.TEXT)));
                context.drawText(textRenderer, playersText, textX, y + 38, 0xFFFFFFFF, false);

                long timeAgoMillis = System.currentTimeMillis() - entry.updatedAtMillis();
                String timeAgo = formatTimeAgo(timeAgoMillis);
                String actualTime = formatTime(entry.updatedAtMillis());
                Text lastPlayedText = Text.literal("Last played: ").styled(s -> s.withColor(UiTheme.TEXT_MUTED)).append(Text.literal(timeAgo + " ago " + actualTime).styled(s -> s.withColor(UiTheme.TEXT)));
                context.drawText(textRenderer, lastPlayedText, textX, y + 52, 0xFFFFFFFF, false);

                Text playtimeText = Text.literal("Total Playtime: ").styled(s -> s.withColor(UiTheme.TEXT_MUTED)).append(Text.literal(formatDuration(entry.playedMillis())).styled(s -> s.withColor(UiTheme.ACCENT_BLUE)));
                context.drawText(textRenderer, playtimeText, textX, y + 66, 0xFFFFFFFF, false);

                if (!entry.inspectable()) {
                    context.drawText(textRenderer, Text.literal("No world copy available"), textX + 220, y + 66, UiTheme.WARNING, false);
                }
            } else {
                context.drawText(textRenderer, Text.literal(entry.id() + " - " + entry.game()), textX, y + 10, UiTheme.TEXT, false);
                context.drawText(textRenderer, Text.literal("State: " + entry.state() + "  Players: " + entry.playerCount()), textX, y + 24, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Seed: " + entry.seed()), textX, y + 38, UiTheme.TEXT_DIM, false);
                context.drawText(textRenderer, Text.literal("Started: " + formatTime(entry.launchedAtMillis())), textX, y + 52, UiTheme.TEXT_DIM, false);
                SessionSnapshotData.PendingJoiner pendingJoiner = this.firstPendingJoiner();
                if (pendingJoiner != null) {
                    int maxWidth = Math.max(60, this.listArea.width() - 320);
                    String assignmentHint = this.assignmentSessionId.equals(entry.id()) ? "select a target team below" : "click Assign to select a team";
                    context.drawText(
                        textRenderer,
                        Text.literal(textRenderer.trimToWidth("Pending: " + pendingJoiner.name() + " - " + assignmentHint, maxWidth)),
                        textX,
                        y + 66,
                        UiTheme.WARNING,
                        false
                    );
                    if (this.assignmentSessionId.equals(entry.id())) {
                        int assignY = y + 84;
                        context.drawText(textRenderer, Text.literal("Target team"), textX, assignY, UiTheme.TEXT_MUTED, false);
                        if (entry.groups().isEmpty()) {
                            context.drawText(textRenderer, Text.literal("No teams available"), textX + 82, assignY, UiTheme.WARNING, false);
                        }
                    }
                }
                int rowY = y;
                SessionLaunchStatus.latestFor(entry.id()).ifPresent(status -> this.renderLaunchProgress(context, textRenderer, rowY, status));
            }
            y += rowHeight + ROW_GAP;
        }
        context.disableScissor();
    }

    private void renderLaunchProgress(DrawContext context, TextRenderer textRenderer, int y, SessionLaunchStatus.Status status) {
        int barX = this.listArea.x() + 14;
        int barY = y + ROW_HEIGHT - 12;
        int barWidth = Math.max(80, this.listArea.width() - 330);
        context.drawText(textRenderer, Text.literal(textRenderer.trimToWidth(status.stage(), barWidth)), barX, y + 64, status.accentColor(), false);
        context.fill(barX + 86, barY, barX + 86 + barWidth, barY + 4, 0x66334155);
        context.fill(barX + 86, barY, barX + 86 + Math.round(barWidth * (status.progress() / 100.0F)), barY + 4, status.accentColor());
    }

    private void renderServer(DrawContext context, TextRenderer textRenderer) {
        int y = this.serverStartY();
        this.renderServerSection(context, textRenderer, "World", y);
        y += SETTINGS_SECTION_TITLE_HEIGHT;
        this.renderServerLabel(context, textRenderer, "View distance", y);
        y += SETTINGS_ROW_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Simulation distance", y);
        y += SETTINGS_ROW_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Spawn protection", y);

        y += SETTINGS_ROW_HEIGHT + SETTINGS_SECTION_GAP;
        this.renderServerSection(context, textRenderer, "Access", y);
        y += SETTINGS_SECTION_TITLE_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Difficulty", y);
        y += SETTINGS_ROW_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Online mode", y);
        y += SETTINGS_ROW_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Allow flight", y);
        y += SETTINGS_ROW_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Accepts transfers", y);

        y += SETTINGS_ROW_HEIGHT + SETTINGS_SECTION_GAP;
        this.renderServerSection(context, textRenderer, "Transfer", y);
        y += SETTINGS_SECTION_TITLE_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Transfer host", y);

        y += SETTINGS_ROW_HEIGHT + SETTINGS_SECTION_GAP;
        this.renderServerSection(context, textRenderer, "Memory", y);
        y += SETTINGS_SECTION_TITLE_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Memory limits", y);
        y += SETTINGS_ROW_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Max heap (GB)", y);
        y += SETTINGS_ROW_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Initial heap (GB)", y);

        y += SETTINGS_ROW_HEIGHT + SETTINGS_SECTION_GAP;
        this.renderServerSection(context, textRenderer, "Launcher", y);
        y += SETTINGS_SECTION_TITLE_HEIGHT;
        this.renderServerLabel(context, textRenderer, "Max concurrent launches", y);
        context.drawText(textRenderer, Text.literal("Queue capacity: " + SessionSnapshotData.launcherQueueCapacity()), this.serverControlX(), y + SETTINGS_ROW_HEIGHT, UiTheme.TEXT_DIM, false);
    }

    private int serverStartY() {
        return this.listArea.y() + 2;
    }

    private int serverLabelWidth() {
        return Math.min(190, Math.max(142, this.listArea.width() / 4));
    }

    private int serverControlX() {
        return this.listArea.x() + this.serverLabelWidth();
    }

    private int numberFieldWidth() {
        return 70;
    }

    private int controlWidth() {
        int available = this.listArea.width() - this.serverLabelWidth() - 8 - 132 - 8;
        return Math.max(140, Math.min(220, available));
    }

    private void renderServerSection(DrawContext context, TextRenderer textRenderer, String label, int y) {
        context.drawText(textRenderer, Text.literal(label), this.listArea.x(), y + 3, UiTheme.ACCENT_BLUE, false);
        UiRenderer.divider(context, this.listArea.x(), y + SETTINGS_SECTION_TITLE_HEIGHT - 2, Math.min(this.listArea.width(), 520));
    }

    private void renderServerLabel(DrawContext context, TextRenderer textRenderer, String label, int y) {
        context.drawText(textRenderer, Text.literal(label), this.listArea.x(), y + 7, UiTheme.TEXT_MUTED, false);
    }

    private void renderLauncher(DrawContext context, TextRenderer textRenderer) {
        context.drawText(textRenderer, Text.literal("Max Concurrent Launches"), this.listArea.x(), this.listArea.y() + 20, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Current value: " + SessionSnapshotData.maxConcurrentLaunches()), this.listArea.x(), this.listArea.y() + 76, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal("Queued launch capacity: " + SessionSnapshotData.launcherQueueCapacity()), this.listArea.x(), this.listArea.y() + 92, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Lower values reduce CPU and memory spikes during session launches."), this.listArea.x(), this.listArea.y() + 116, UiTheme.TEXT_DIM, false);
    }

    private void renderEmpty(DrawContext context, TextRenderer textRenderer, String message, boolean history) {
        int yOffset = history ? 36 : 0;
        UiRenderer.panel(context, this.listArea.x(), this.listArea.y() + yOffset, this.listArea.width(), 64, UiTheme.PANEL_SOFT, UiTheme.BORDER_SUBTLE);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(message), this.listArea.x() + this.listArea.width() / 2, this.listArea.y() + yOffset + 27, UiTheme.TEXT_MUTED);
    }

    private void rebuildScrollableButtons() {
        this.buttons.removeIf(ActionButton::scrollManaged);
        int top = this.mode == Mode.HISTORY ? this.listArea.y() + 36 : this.listArea.y();
        int rowY = top - this.scrollOffset;
        if (this.mode == Mode.HISTORY) {
            this.updateScrollBounds(this.retainedSessions(), true);
        } else {
            for (SessionEntry entry : this.activeSessions()) {
                int capturedY = rowY;
                int capturedRowHeight = this.rowHeight(entry);
                int buttonX = this.listArea.x() + this.listArea.width() - 270;
                this.buttons.add(new ActionButton(new UiLayout.Rect(buttonX, capturedY + 10, 98, BUTTON_HEIGHT), () -> "Save and return", () -> this.confirmStopSession(entry.id()), UiTheme.ACCENT_RED, () -> this.rowVisible(capturedY, capturedRowHeight), true));
                this.buttons.add(new ActionButton(new UiLayout.Rect(buttonX + 106, capturedY + 10, 72, BUTTON_HEIGHT), () -> entry.paused() ? "Resume" : "Pause", () -> this.setPaused(entry.id(), !entry.paused()), entry.paused() ? UiTheme.ACCENT_GREEN : UiTheme.WARNING, () -> this.rowVisible(capturedY, capturedRowHeight), true));
                this.buttons.add(new ActionButton(new UiLayout.Rect(buttonX + 186, capturedY + 10, 78, BUTTON_HEIGHT), () -> "Seed", () -> this.changeSeed(entry.id()), UiTheme.ACCENT_BLUE, () -> this.rowVisible(capturedY, capturedRowHeight), true));
                SessionSnapshotData.PendingJoiner pendingJoiner = this.firstPendingJoiner();
                if (pendingJoiner != null) {
                    this.buttons.add(new ActionButton(new UiLayout.Rect(buttonX, capturedY + 42, 86, BUTTON_HEIGHT), () -> this.assignmentSessionId.equals(entry.id()) ? "Close" : "Assign", () -> this.toggleAssignment(entry.id()), UiTheme.ACCENT_GREEN, () -> this.rowVisible(capturedY, capturedRowHeight), true));
                    if (this.assignmentSessionId.equals(entry.id())) {
                        this.addAssignmentButtons(entry, pendingJoiner, capturedY, capturedRowHeight);
                    }
                }
                rowY += capturedRowHeight + ROW_GAP;
            }
            this.updateScrollBounds(this.activeSessions(), false);
        }
    }

    private void addAssignmentButtons(SessionEntry entry, SessionSnapshotData.PendingJoiner pendingJoiner, int rowY, int rowHeight) {
        int x = this.listArea.x() + 96;
        int y = rowY + 80;
        int width = 104;
        int gap = 8;
        int columns = this.assignmentButtonColumns(x, width, gap);
        int index = 0;
        if (entry.supportsRoles()) {
            this.buttons.add(new ActionButton(new UiLayout.Rect(x, y, width, BUTTON_HEIGHT), () -> "Speedrunner", () -> this.assignPending(entry, pendingJoiner, "speedrunner"), UiTheme.ACCENT_BLUE, () -> this.rowVisible(rowY, rowHeight), true));
            this.buttons.add(new ActionButton(new UiLayout.Rect(x + width + gap, y, width, BUTTON_HEIGHT), () -> "Hunter", () -> this.assignPending(entry, pendingJoiner, "hunter"), UiTheme.ACCENT_BLUE, () -> this.rowVisible(rowY, rowHeight), true));
            return;
        }
        if (entry.isSpeedrun()) {
            if (entry.groups().isEmpty()) {
                this.buttons.add(new ActionButton(new UiLayout.Rect(x, y, width, BUTTON_HEIGHT), () -> "Join", () -> this.assignPending(entry, pendingJoiner, "", "runner"), UiTheme.ACCENT_BLUE, () -> this.rowVisible(rowY, rowHeight), true));
                return;
            }
            for (SessionSnapshotData.GroupSummary group : entry.groups()) {
                String label = group.displayName().isBlank() ? group.label() : group.displayName();
                int buttonX = x + (index % columns) * (width + gap);
                int buttonY = y + (index / columns) * (BUTTON_HEIGHT + gap);
                this.buttons.add(new ActionButton(
                    new UiLayout.Rect(buttonX, buttonY, width, BUTTON_HEIGHT),
                    () -> label.length() > 12 ? label.substring(0, 12) : label,
                    () -> this.assignPending(entry, pendingJoiner, group.label(), "runner"),
                    UiTheme.ACCENT_BLUE,
                    () -> this.rowVisible(rowY, rowHeight),
                    true
                ));
                index++;
            }
            return;
        }
        for (SessionSnapshotData.GroupSummary group : entry.groups()) {
            String label = group.displayName().isBlank() ? group.label() : group.displayName();
            String role = entry.roleForGroup(group);
            int buttonX = x + (index % columns) * (width + gap);
            int buttonY = y + (index / columns) * (BUTTON_HEIGHT + gap);
            this.buttons.add(new ActionButton(
                new UiLayout.Rect(buttonX, buttonY, width, BUTTON_HEIGHT),
                () -> label.length() > 12 ? label.substring(0, 12) : label,
                () -> this.assignPending(entry, pendingJoiner, group.label(), role),
                UiTheme.ACCENT_BLUE,
                () -> this.rowVisible(rowY, rowHeight),
                true
            ));
            index++;
        }
        if (index == 0) {
            this.buttons.add(new ActionButton(new UiLayout.Rect(x, y, width, BUTTON_HEIGHT), () -> "Join", () -> this.assignPending(entry, pendingJoiner, entry.game(), ""), UiTheme.ACCENT_BLUE, () -> this.rowVisible(rowY, rowHeight), true));
        }
    }

    private boolean rowVisible(int rowY) {
        return this.rowVisible(rowY, ROW_HEIGHT);
    }

    private boolean rowVisible(int rowY, int rowHeight) {
        int top = this.mode == Mode.HISTORY ? this.listArea.y() + 36 : this.listArea.y();
        int bottom = this.listArea.y() + this.listArea.height();
        return rowY + rowHeight > top && rowY < bottom;
    }

    private boolean inScrollableButtonViewport(double mouseX, double mouseY) {
        int top = this.mode == Mode.HISTORY ? this.listArea.y() + 36 : this.listArea.y();
        int bottom = this.listArea.y() + this.listArea.height();
        return mouseX >= this.listArea.x()
            && mouseX <= this.listArea.x() + this.listArea.width()
            && mouseY >= top
            && mouseY <= bottom;
    }

    private void updateScrollBounds(List<SessionEntry> entries, boolean history) {
        int visibleHeight = this.mode == Mode.HISTORY ? this.listArea.height() - 36 : this.listArea.height();
        int totalHeight = this.totalRowsHeight(entries, history);
        this.maxScroll = Math.max(0, totalHeight - visibleHeight);
        this.scrollOffset = Math.clamp(this.scrollOffset, 0, this.maxScroll);
    }

    private int totalRowsHeight(List<SessionEntry> entries, boolean history) {
        if (entries.isEmpty()) {
            return 0;
        }
        int totalHeight = 0;
        for (SessionEntry entry : entries) {
            totalHeight += history ? ROW_HEIGHT : this.rowHeight(entry);
        }
        return totalHeight + (entries.size() - 1) * ROW_GAP;
    }

    private int rowHeight(SessionEntry entry) {
        if (!this.assignmentSessionId.equals(entry.id())) {
            return ROW_HEIGHT;
        }
        int rows = this.assignmentButtonRows(entry);
        return ROW_HEIGHT + Math.max(0, rows - 2) * (BUTTON_HEIGHT + BUTTON_GAP);
    }

    private int assignmentButtonRows(SessionEntry entry) {
        int count;
        if (entry.supportsRoles()) {
            count = 2;
        } else {
            count = Math.max(1, entry.groups().size());
        }
        int columns = this.assignmentButtonColumns(this.listArea.x() + 96, 104, 8);
        return Math.max(1, (count + columns - 1) / columns);
    }

    private int assignmentButtonColumns(int x, int width, int gap) {
        int availableWidth = Math.max(width, this.listArea.x() + this.listArea.width() - x - 14);
        return Math.max(1, (availableWidth + gap) / (width + gap));
    }

    private List<SessionEntry> activeSessions() {
        return SessionSnapshotData.sessions().stream().filter(session -> !session.retained()).map(SessionEntry::from).toList();
    }

    private List<SessionEntry> retainedSessions() {
        Comparator<SessionEntry> comparator = Comparator.comparingLong(this::historySortTimestamp).thenComparing(SessionEntry::id);
        if (this.historySortOrder == HistorySortOrder.LATEST_PLAYED) {
            comparator = comparator.reversed();
        }
        return SessionSnapshotData.sessions().stream().filter(SessionSnapshotData.SessionSummary::retained).map(SessionEntry::from).sorted(comparator).toList();
    }

    private long historySortTimestamp(SessionEntry entry) {
        if (entry.updatedAtMillis() > 0L) {
            return entry.updatedAtMillis();
        }
        if (entry.launchedAtMillis() > 0L) {
            return entry.launchedAtMillis();
        }
        return entry.createdAtMillis();
    }

    private SessionSnapshotData.PendingJoiner firstPendingJoiner() {
        List<SessionSnapshotData.PendingJoiner> pending = SessionSnapshotData.pendingJoiners();
        return pending.isEmpty() ? null : pending.getFirst();
    }

    private void loadSettingsState() {
        SessionSnapshotData.ServerSettings server = SessionSnapshotData.serverSettings();
        SessionSnapshotData.MemorySettings memory = SessionSnapshotData.memorySettings();
        this.onlineMode = server.onlineMode();
        this.allowFlight = server.allowFlight();
        this.acceptsTransfers = server.acceptsTransfers();
        this.difficultyValue = server.difficulty();
        this.memoryEnabled = memory.enabled();
    }

    private void confirmStopSession(String sessionId) {
        this.client.setScreen(new ConfirmScreen(
            confirmed -> {
                this.client.setScreen(this.screen);
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
        if (!this.connected()) {
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.CleanupPlayerPayload(sessionId));
        ClientPlayNetworking.send(new NetworkConstants.StopSessionPayload(sessionId));
        this.statusMessage = "Saving " + sessionId + " and returning players.";
    }

    private void setPaused(String sessionId, boolean paused) {
        if (!this.connected()) {
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.PauseSessionPayload(sessionId, paused));
        this.statusMessage = (paused ? "Pausing " : "Resuming ") + sessionId + ".";
    }

    private void changeSeed(String sessionId) {
        if (!this.connected()) {
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.ChangeSeedPayload(sessionId));
        this.statusMessage = "Changing seed for " + sessionId + ".";
    }

    private void inspectSession(String sessionId) {
        if (!this.connected()) {
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.InspectSessionPayload(sessionId));
        this.statusMessage = "Launching inspection copy for " + sessionId + ".";
    }

    private void relaunchSession(String sessionId) {
        if (!this.connected()) {
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.RelaunchSessionPayload(sessionId));
        this.statusMessage = "Relaunching " + sessionId + ".";
    }

    private void confirmDeleteSession(String sessionId) {
        this.client.setScreen(new ConfirmScreen(
            confirmed -> {
                this.client.setScreen(this.screen);
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
        if (!this.connected()) {
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.DeleteSessionPayload(sessionId));
        this.statusMessage = "Deleting " + sessionId + ".";
    }

    private void confirmDeleteAllSessions() {
        this.client.setScreen(new ConfirmScreen(
            confirmed -> {
                this.client.setScreen(this.screen);
                if (confirmed) {
                    this.deleteAllSessions();
                }
            },
            Text.literal("Delete all retained sessions?"),
            Text.literal("This permanently deletes all " + this.retainedSessions().size() + " saved sessions."),
            Text.literal("Yes"),
            Text.literal("No")
        ));
    }

    private void deleteAllSessions() {
        if (!this.connected()) {
            return;
        }
        java.util.List<String> ids = this.retainedSessions().stream().map(SessionEntry::id).toList();
        if (ids.isEmpty()) {
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.DeleteAllSessionsPayload(ids));
        this.statusMessage = "Deleting " + ids.size() + " retained sessions.";
        this.scrollOffset = 0;
        this.rebuildScrollableButtons();
    }

    private void toggleAssignment(String sessionId) {
        this.assignmentSessionId = this.assignmentSessionId.equals(sessionId) ? "" : sessionId;
        this.rebuildScrollableButtons();
    }

    private void assignPending(SessionEntry entry, SessionSnapshotData.PendingJoiner pendingJoiner, String role) {
        this.assignPending(entry, pendingJoiner, entry.teamLabelForRole(role), role);
    }

    private void assignPending(SessionEntry entry, SessionSnapshotData.PendingJoiner pendingJoiner, String teamLabel, String role) {
        if (!this.connected()) {
            return;
        }
        ClientPlayNetworking.send(new NetworkConstants.AssignMidGamePlayerPayload(entry.id(), pendingJoiner.uuid(), teamLabel, role));
        this.statusMessage = "Assigning " + pendingJoiner.name() + " to " + entry.id() + ".";
        this.assignmentSessionId = "";
        this.rebuildScrollableButtons();
    }

    private boolean connected() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return false;
        }
        return true;
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
        this.saveIntServer(this.viewDistanceField, 2, 32, "viewDistance", "View distance");
    }

    private void saveSimulationDistance() {
        this.saveIntServer(this.simulationDistanceField, 2, 32, "simulationDistance", "Simulation distance");
    }

    private void saveSpawnProtection() {
        this.saveIntServer(this.spawnProtectionField, 0, 64, "spawnProtection", "Spawn protection");
    }

    private void saveDifficulty() {
        NbtCompound server = new NbtCompound();
        server.putString("difficulty", this.difficultyValue);
        this.sendServerSettings(null, server, null);
        this.statusMessage = "Saved difficulty " + this.getDifficultyLabel(this.difficultyValue) + ".";
    }

    private void saveOnlineMode() {
        this.saveBooleanServer("onlineMode", this.onlineMode, "Online mode");
    }

    private void saveAllowFlight() {
        this.saveBooleanServer("allowFlight", this.allowFlight, "Allow flight");
    }

    private void saveAcceptsTransfers() {
        this.saveBooleanServer("acceptsTransfers", this.acceptsTransfers, "Transfers");
    }

    private void saveAdvertisedHost() {
        String value = this.advertisedHostField.getText().trim();
        if (value.isBlank()) {
            this.statusMessage = "Transfer host cannot be empty.";
            return;
        }
        NbtCompound server = new NbtCompound();
        server.putString("advertisedHost", value);
        this.sendServerSettings(null, server, null);
        this.statusMessage = "Saved transfer host " + value + ".";
    }

    private void saveMemoryEnabled() {
        NbtCompound memory = new NbtCompound();
        memory.putBoolean("enabled", this.memoryEnabled);
        this.sendServerSettings(memory, null, null);
        this.statusMessage = "Saved memory limits " + onOff(this.memoryEnabled) + ".";
    }

    private void saveMaxHeap() {
        this.saveIntMemory(this.maxHeapField, 1, 128, "maxHeapGb", "Max heap");
    }

    private void saveInitialHeap() {
        this.saveIntMemory(this.initialHeapField, 1, 128, "initialHeapGb", "Initial heap");
    }

    private void saveMaxConcurrentLaunches() {
        Integer value = this.parseInt(this.maxConcurrentLaunchesField, 1, 64, "Max concurrent launches");
        if (value == null || !this.connected()) {
            return;
        }
        NbtCompound settings = new NbtCompound();
        settings.putInt("maxConcurrentLaunches", value);
        ClientPlayNetworking.send(new NetworkConstants.LauncherSettingsPayload(settings));
        this.statusMessage = "Max concurrent launches set to " + value + ".";
    }



    private void saveMaxAgeDays() {
        Integer value = this.parseInt(this.maxAgeDaysField, 0, 365, "Retention days");
        if (value == null) {
            return;
        }
        NbtCompound retention = new NbtCompound();
        retention.putInt("maxAgeDays", value);
        this.sendServerSettings(null, null, retention);
        this.statusMessage = "Saved retention age " + value + " day(s).";
    }

    private void saveIntServer(TextFieldWidget field, int min, int max, String key, String label) {
        Integer value = this.parseInt(field, min, max, label);
        if (value == null) {
            return;
        }
        NbtCompound server = new NbtCompound();
        server.putInt(key, value);
        this.sendServerSettings(null, server, null);
        this.statusMessage = "Saved " + label.toLowerCase() + " " + value + ".";
    }

    private void saveIntMemory(TextFieldWidget field, int min, int max, String key, String label) {
        Integer value = this.parseInt(field, min, max, label);
        if (value == null) {
            return;
        }
        NbtCompound memory = new NbtCompound();
        memory.putInt(key, value);
        this.sendServerSettings(memory, null, null);
        this.statusMessage = "Saved " + label.toLowerCase() + " " + value + ".";
    }

    private void saveBooleanServer(String key, boolean value, String label) {
        NbtCompound server = new NbtCompound();
        server.putBoolean(key, value);
        this.sendServerSettings(null, server, null);
        this.statusMessage = "Saved " + label.toLowerCase() + " " + onOff(value) + ".";
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

    private void sendServerSettings(NbtCompound memorySettings, NbtCompound serverSettings, NbtCompound retentionSettings) {
        if (!this.connected()) {
            return;
        }
        NbtCompound payload = new NbtCompound();
        if (memorySettings != null && !memorySettings.getKeys().isEmpty()) {
            payload.put("memory", memorySettings);
        }
        if (serverSettings != null && !serverSettings.getKeys().isEmpty()) {
            payload.put("server", serverSettings);
        }
        if (retentionSettings != null && !retentionSettings.getKeys().isEmpty()) {
            payload.put("retention", retentionSettings);
        }
        if (!payload.getKeys().isEmpty()) {
            ClientPlayNetworking.send(new NetworkConstants.ServerSettingsPayload(payload));
        }
    }

    private void toggleHistorySort() {
        this.historySortOrder = this.historySortOrder == HistorySortOrder.LATEST_PLAYED ? HistorySortOrder.OLDEST_PLAYED : HistorySortOrder.LATEST_PLAYED;
        this.scrollOffset = 0;
        this.rebuildScrollableButtons();
    }

    private String historySortLabel() {
        return this.historySortOrder == HistorySortOrder.LATEST_PLAYED ? "Sort: Latest Played" : "Sort: Oldest Played";
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String formatTime(long epochMillis) {
        return epochMillis <= 0L ? "unknown" : TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String formatDuration(long millis) {
        long totalMinutes = Math.max(0L, millis) / 60_000L;
        return (totalMinutes / 60L) + "h " + (totalMinutes % 60L) + "m";
    }

    private static String formatTimeAgo(long millis) {
        if (millis < 0) return "just now";
        long seconds = millis / 1000L;
        if (seconds < 60) return "just now";
        long minutes = seconds / 60L;
        if (minutes < 60) return minutes + " min" + (minutes == 1 ? "" : "s");
        long hours = minutes / 60L;
        if (hours < 24) return hours + " hour" + (hours == 1 ? "" : "s");
        long days = hours / 24L;
        if (days < 30) {
            long remainingHours = hours % 24L;
            if (remainingHours > 0) {
                return days + " day" + (days == 1 ? "" : "s") + " " + remainingHours + " hour" + (remainingHours == 1 ? "" : "s");
            }
            return days + " day" + (days == 1 ? "" : "s");
        }
        long months = days / 30L;
        if (months < 12) return months + " month" + (months == 1 ? "" : "s");
        long years = days / 365L;
        return years + " year" + (years == 1 ? "" : "s");
    }

    public enum Mode {
        SESSIONS,
        HISTORY,
        SERVER,
        LAUNCHER
    }

    private enum HistorySortOrder {
        LATEST_PLAYED,
        OLDEST_PLAYED
    }

    public record SessionEntry(String id, String game, String state, long seed, int playerCount, long createdAtMillis, long launchedAtMillis, long updatedAtMillis, long playedMillis, boolean inspectable, List<SessionSnapshotData.GroupSummary> groups, List<String> playerNames) {
        private static SessionEntry from(SessionSnapshotData.SessionSummary summary) {
            return new SessionEntry(summary.id(), summary.game(), summary.state(), summary.seed(), summary.players(), summary.createdAtMillis(), summary.launchedAtMillis(), summary.updatedAtMillis(), summary.playedMillis(), summary.inspectable(), summary.groups(), summary.playerNames());
        }

        private boolean paused() {
            return "PAUSED".equalsIgnoreCase(this.state);
        }

        private boolean supportsRoles() {
            return this.game.toLowerCase().contains("manhunt");
        }

        private boolean isSpeedrun() {
            return this.game.toLowerCase().contains("speedrun");
        }

        private String teamLabelForRole(String role) {
            if (this.isSpeedrun()) {
                return "";
            }
            if (!this.supportsRoles()) {
                return this.game;
            }
            return switch (role.toLowerCase()) {
                case "speedrunner" -> "Speedrunners";
                case "hunter" -> "Hunters";
                default -> this.game;
            };
        }

        private String roleForGroup(SessionSnapshotData.GroupSummary group) {
            if (this.isSpeedrun()) {
                return "runner";
            }
            if (!this.supportsRoles()) {
                return "";
            }
            String value = (group.label() + " " + group.displayName()).toLowerCase();
            if (value.contains("hunter")) {
                return "hunter";
            }
            if (value.contains("runner") || value.contains("speedrunner")) {
                return "speedrunner";
            }
            return "";
        }
    }

    private record ActionButton(UiLayout.Rect bounds, Supplier<String> label, Runnable action, int accent, BooleanSupplier enabled, boolean scrollManaged) {
        private ActionButton(UiLayout.Rect bounds, Supplier<String> label, Runnable action, int accent, BooleanSupplier enabled) {
            this(bounds, label, action, accent, enabled, false);
        }

        private void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
            if (!this.enabled.getAsBoolean()) {
                return;
            }
            boolean hovered = this.bounds.contains(mouseX, mouseY);
            int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(this.accent, 0.30F), hovered ? 1.0F : 0.0F);
            UiRenderer.panel(context, this.bounds.x(), this.bounds.y(), this.bounds.width(), this.bounds.height(), fill, hovered ? this.accent : UiTheme.BORDER_SUBTLE);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(this.label.get()), this.bounds.x() + this.bounds.width() / 2, this.bounds.y() + 7, UiTheme.TEXT);
        }

        private boolean click(double mouseX, double mouseY) {
            if (this.enabled.getAsBoolean() && this.bounds.contains(mouseX, mouseY)) {
                this.action.run();
                return true;
            }
            return false;
        }
    }
}
