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
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SessionScreen extends Screen {
    private static final int GRID_COLUMNS = 2;
    private static final int PANEL_PADDING = 16;
    private static final int PANEL_MAX_WIDTH = 520;
    private static final int GRID_START_OFFSET = 96;
    private static final int GRID_ROW_GAP = 10;
    private static final int CARD_HEIGHT = 58;
    private static final int FOOTER_TOP_GAP = 16;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int SETTINGS_BUTTON_WIDTH = 92;
    private static final int CLOSE_BUTTON_WIDTH = 68;
    private static final Map<String, Consumer<SessionScreen>> CUSTOM_SETUP_SCREENS = Map.of(
        "manhunt", SessionScreen::openManhunt,
        "speedrun", SessionScreen::openSpeedrun,
        "bountyhunt", SessionScreen::openBountyHunt,
        "resource_sprint", SessionScreen::openResourceSprint,
        "deathswap", SessionScreen::openDeathSwap
    );

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<MinigameEntry> minigameEntries = new ArrayList<>();
    private TextFieldWidget searchField;
    private String statusMessage = "";

    public SessionScreen() {
        super(Text.literal("Minigame Selector"));
    }

    public static void onServerSnapshot(NbtCompound root) {
        List<SessionSnapshotData.SessionSummary> sessions = new ArrayList<>();
        NbtList sessionList = root.getList("sessions").orElseGet(NbtList::new);
        for (int i = 0; i < sessionList.size(); i++) {
            NbtCompound entry = sessionList.getCompoundOrEmpty(i);
            sessions.add(new SessionSnapshotData.SessionSummary(
                entry.getString("id", ""),
                entry.getString("game", ""),
                entry.getString("state", ""),
                entry.getLong("seed").orElse(0L),
                entry.getInt("playerCount").orElse(0),
                entry.getLong("createdAt").orElse(0L),
                entry.getLong("launchedAt").orElse(0L),
                entry.getLong("updatedAt").orElse(0L),
                entry.getLong("playedMillis").orElse(0L),
                entry.getBoolean("inspectable", false),
                entry.getBoolean("retained", false)
            ));
        }

        List<SessionSnapshotData.RosterEntry> roster = new ArrayList<>();
        NbtList rosterList = root.getList("players").orElseGet(NbtList::new);
        for (int i = 0; i < rosterList.size(); i++) {
            NbtCompound entry = rosterList.getCompoundOrEmpty(i);
            roster.add(new SessionSnapshotData.RosterEntry(entry.getString("uuid", ""), entry.getString("name", "")));
        }

        List<SessionSnapshotData.GameMetadata> games = new ArrayList<>();
        NbtList gameList = root.getList("games").orElseGet(NbtList::new);
        for (int i = 0; i < gameList.size(); i++) {
            NbtCompound entry = gameList.getCompoundOrEmpty(i);
            List<SessionSnapshotData.SetupField> fields = new ArrayList<>();
            NbtList fieldList = entry.getList("fields").orElseGet(NbtList::new);
            for (int fieldIndex = 0; fieldIndex < fieldList.size(); fieldIndex++) {
                NbtCompound field = fieldList.getCompoundOrEmpty(fieldIndex);
                fields.add(new SessionSnapshotData.SetupField(
                    field.getString("key", ""),
                    field.getString("label", ""),
                    field.getString("type", "string"),
                    field.getString("default", ""),
                    field.getBoolean("required", false),
                    field.getInt("min").orElse(0),
                    field.getInt("max").orElse(0)
                ));
            }

            games.add(new SessionSnapshotData.GameMetadata(
                entry.getString("id", ""),
                entry.getString("displayName", ""),
                entry.getString("description", ""),
                entry.getString("icon", "?"),
                entry.getString("topology", ""),
                entry.getString("setupKind", "generic"),
                entry.getBoolean("enabled", true),
                fields
            ));
        }

        NbtCompound launcher = root.getCompound("launcher").orElseGet(NbtCompound::new);
        NbtCompound memory = root.getCompound("memory").orElseGet(NbtCompound::new);
        NbtCompound serverSettings = root.getCompound("server").orElseGet(NbtCompound::new);
        NbtCompound retention = root.getCompound("retention").orElseGet(NbtCompound::new);
        SessionSnapshotData.update(
            sessions,
            roster,
            games,
            launcher.getInt("maxConcurrentLaunches").orElse(SessionSnapshotData.maxConcurrentLaunches()),
            launcher.getInt("queueCapacity").orElse(SessionSnapshotData.launcherQueueCapacity()),
            new SessionSnapshotData.MemorySettings(
                memory.getInt("maxHeapGb").orElse(SessionSnapshotData.memorySettings().maxHeapGb()),
                memory.getInt("initialHeapGb").orElse(SessionSnapshotData.memorySettings().initialHeapGb()),
                memory.getBoolean("enabled", SessionSnapshotData.memorySettings().enabled())
            ),
            new SessionSnapshotData.ServerSettings(
                serverSettings.getInt("viewDistance").orElse(SessionSnapshotData.serverSettings().viewDistance()),
                serverSettings.getInt("simulationDistance").orElse(SessionSnapshotData.serverSettings().simulationDistance()),
                serverSettings.getBoolean("onlineMode", SessionSnapshotData.serverSettings().onlineMode()),
                serverSettings.getInt("spawnProtection").orElse(SessionSnapshotData.serverSettings().spawnProtection()),
                serverSettings.getString("difficulty", SessionSnapshotData.serverSettings().difficulty()),
                serverSettings.getBoolean("allowFlight", SessionSnapshotData.serverSettings().allowFlight()),
                serverSettings.getBoolean("acceptsTransfers", SessionSnapshotData.serverSettings().acceptsTransfers()),
                serverSettings.getString("advertisedHost", SessionSnapshotData.serverSettings().advertisedHost())
            ),
            new SessionSnapshotData.RetentionSettings(
                retention.getInt("keepLatestSessions").orElse(SessionSnapshotData.retentionSettings().keepLatestSessions()),
                retention.getInt("maxAgeDays").orElse(SessionSnapshotData.retentionSettings().maxAgeDays())
            )
        );
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof SessionScreen sessionScreen) {
            sessionScreen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        super.init();
        this.rebuildWidgets();
        this.requestSnapshot();
    }

    private void rebuildWidgets() {
        this.clearChildren();
        this.minigameEntries.clear();
        this.minigameEntries.addAll(this.createMinigameEntries());

        Layout layout = this.createLayout(this.minigameEntries.size());

        this.searchField = new TextFieldWidget(this.textRenderer, layout.searchX(), layout.searchY(), layout.searchWidth(), 20, Text.literal("search"));
        this.searchField.setMaxLength(64);
        this.searchField.setText("");
        this.addDrawableChild(this.searchField);

        int totalSlots = layout.gridRows() * GRID_COLUMNS;
        for (int index = 0; index < totalSlots; index++) {
            int column = index % GRID_COLUMNS;
            int row = index / GRID_COLUMNS;
            int x = layout.gridX() + column * (layout.cardWidth() + GRID_ROW_GAP);
            int y = layout.gridY() + row * (CARD_HEIGHT + GRID_ROW_GAP);

            if (index < this.minigameEntries.size()) {
                MinigameEntry entry = this.minigameEntries.get(index);
                ButtonWidget button = ButtonWidget.builder(entry.buttonLabel(), unused -> entry.activate(this))
                    .dimensions(x, y, layout.cardWidth(), CARD_HEIGHT)
                    .build();
                button.active = entry.enabled();
                button.setTooltip(Tooltip.of(Text.literal(entry.description())));
                this.addDrawableChild(button);
            } else {
                ButtonWidget button = ButtonWidget.builder(Text.literal("Coming Soon"), unused -> { })
                    .dimensions(x, y, layout.cardWidth(), CARD_HEIGHT)
                    .build();
                button.active = false;
                button.setTooltip(Tooltip.of(Text.literal("Reserved for future gamemodes.")));
                this.addDrawableChild(button);
            }
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("⚙ Settings"), unused -> this.openSettingsPlaceholder())
            .dimensions(layout.settingsButtonX(), layout.footerY(), SETTINGS_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT)
            .build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("✕ Close"), unused -> this.close())
            .dimensions(layout.closeButtonX(), layout.footerY(), CLOSE_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = this.createLayout(this.minigameEntries.size());

        this.drawShell(context, layout);

        int titleCenterX = layout.panelX() + layout.panelWidth() / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, titleCenterX, layout.panelY() + 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Choose a gamemode to configure it before starting a session."), titleCenterX, layout.panelY() + 26, 0xB0B0B0);

        context.drawText(this.textRenderer, Text.literal("Search"), layout.searchX(), layout.searchY() - 10, 0xFFE0E0E0, false);
        if (!this.statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), titleCenterX, layout.footerY() + FOOTER_BUTTON_HEIGHT + 10, 0xA0FFA0);
        }

        super.render(context, mouseX, mouseY, delta);

        if (this.searchField != null && this.searchField.getText().isEmpty()) {
            context.drawText(this.textRenderer, Text.literal("Search..."), layout.searchX() + 6, layout.searchY() + 6, 0xFF808080, false);
        }
    }

    private void drawShell(DrawContext context, Layout layout) {
        int x = layout.panelX();
        int y = layout.panelY();
        int width = layout.panelWidth();
        int height = layout.panelHeight();

        context.fill(x, y, x + width, y + height, 0xD0121212);
        context.fill(x + 1, y + 1, x + width - 1, y + 32, 0xCC1F1F1F);
        context.fill(x + 1, y + 33, x + width - 1, y + height - 1, 0xAA181818);
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0x66FFFFFF);
        context.fill(x, y, x + 1, y + height, 0x66FFFFFF);
        context.fill(x + width - 1, y, x + width, y + height, 0x66FFFFFF);

        int gridTop = layout.gridY() - 8;
        int gridBottom = layout.footerY() - 8;
        context.fill(x + PANEL_PADDING - 4, gridTop, x + width - PANEL_PADDING + 4, gridBottom, 0x1AFFFFFF);
    }

    private List<MinigameEntry> createMinigameEntries() {
        List<MinigameEntry> entries = new ArrayList<>();
        for (SessionSnapshotData.GameMetadata metadata : SessionSnapshotData.games()) {
            Consumer<SessionScreen> customAction = CUSTOM_SETUP_SCREENS.get(metadata.id());
            boolean enabled = metadata.enabled() && (!"custom".equalsIgnoreCase(metadata.setupKind()) || customAction != null);
            entries.add(new MinigameEntry(
                metadata.id(),
                metadata.displayName(),
                metadata.description(),
                metadata.icon(),
                enabled,
                metadata.setupKind(),
                metadata.fields(),
                customAction
            ));
        }
        return entries;
    }
    private Layout createLayout(int entryCount) {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 24);
        int gridRows = Math.max(2, (entryCount + GRID_COLUMNS - 1) / GRID_COLUMNS);
        int cardWidth = (panelWidth - (PANEL_PADDING * 2) - GRID_ROW_GAP) / GRID_COLUMNS;
        int gridHeight = gridRows * CARD_HEIGHT + Math.max(0, gridRows - 1) * GRID_ROW_GAP;
        int panelHeight = GRID_START_OFFSET + gridHeight + FOOTER_TOP_GAP + FOOTER_BUTTON_HEIGHT + PANEL_PADDING;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = Math.max(12, (this.height - panelHeight) / 2);

        return new Layout(
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            panelX + PANEL_PADDING,
            panelY + 58,
            panelWidth - (PANEL_PADDING * 2),
            panelX + PANEL_PADDING,
            panelY + GRID_START_OFFSET,
            cardWidth,
            gridRows,
            panelY + GRID_START_OFFSET + gridHeight + FOOTER_TOP_GAP,
            panelX + PANEL_PADDING,
            panelX + panelWidth - PANEL_PADDING - CLOSE_BUTTON_WIDTH
        );
    }

    private void openManhunt() {
        this.client.setScreen(new ManhuntSetupScreen());
    }

    private void openSpeedrun() {
        this.client.setScreen(new SpeedrunSetupScreen());
    }

    private void openBountyHunt() {
        this.client.setScreen(new BountyHuntSetupScreen());
    }

    private void openResourceSprint() {
        this.client.setScreen(new ResourceSprintSetupScreen());
    }

    private void openDeathSwap() {
        this.client.setScreen(new DeathSwapSetupScreen());
    }

    public void openGenericSetup(MinigameEntry entry) {
        this.client.setScreen(new GenericSetupScreen(entry));
    }

    private void openSettingsPlaceholder() {
        this.client.setScreen(new SettingsScreen());
    }

    private void requestSnapshot() {
        if (this.client.player != null) {
            ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("metadata"));
        }
    }
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private record Layout(
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        int searchX,
        int searchY,
        int searchWidth,
        int gridX,
        int gridY,
        int cardWidth,
        int gridRows,
        int footerY,
        int settingsButtonX,
        int closeButtonX
    ) {
    }
}







