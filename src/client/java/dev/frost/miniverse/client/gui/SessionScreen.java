package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.AdminWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.AppearanceWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.ManhuntWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.WorkspaceView;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class SessionScreen extends Screen {
    private static final int CARD_HEIGHT = 84;
    private static final int CARD_GAP = 12;
    private static final int TOOLBAR_BUTTON_WIDTH = 84;
    private static final Map<String, Consumer<SessionScreen>> CUSTOM_SETUP_SCREENS = Map.of(
        "manhunt", SessionScreen::openManhunt,
        "speedrun", SessionScreen::openSpeedrun,
        "bountyhunt", SessionScreen::openBountyHunt,
        "resource_sprint", SessionScreen::openResourceSprint,
        "deathswap", SessionScreen::openDeathSwap
    );

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<MinigameEntry> minigameEntries = new ArrayList<>();
    private final Map<String, UiAnimation.Value> cardHover = new HashMap<>();
    private final Map<SidebarSection, UiAnimation.Value> sidebarAnimations = new EnumMap<>(SidebarSection.class);
    private final Set<SidebarSection> expandedSections = EnumSet.of(SidebarSection.GAMEMODES);
    private TextFieldWidget searchField;
    private WorkspaceView workspaceView;
    private String statusMessage = "";
    private long openedAt;

    public SessionScreen() {
        super(Text.literal("Miniverse"));
        for (SidebarSection section : SidebarSection.values()) {
            this.sidebarAnimations.put(section, new UiAnimation.Value(this.expandedSections.contains(section) ? 1.0F : 0.0F));
        }
    }

    public static void onServerSnapshot(NbtCompound root) {
        List<SessionSnapshotData.SessionSummary> sessions = new ArrayList<>();
        NbtList sessionList = root.getList("sessions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < sessionList.size(); i++) {
            NbtCompound entry = sessionList.getCompound(i);
            sessions.add(new SessionSnapshotData.SessionSummary(
                getStringOrDefault(entry, "id", ""),
                getStringOrDefault(entry, "game", ""),
                getStringOrDefault(entry, "state", ""),
                getLongOrDefault(entry, "seed", 0L),
                getIntOrDefault(entry, "playerCount", 0),
                getLongOrDefault(entry, "createdAt", 0L),
                getLongOrDefault(entry, "launchedAt", 0L),
                getLongOrDefault(entry, "updatedAt", 0L),
                getLongOrDefault(entry, "playedMillis", 0L),
                getBooleanOrDefault(entry, "inspectable", false),
                getBooleanOrDefault(entry, "retained", false)
            ));
        }

        List<SessionSnapshotData.RosterEntry> roster = new ArrayList<>();
        NbtList rosterList = root.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rosterList.size(); i++) {
            NbtCompound entry = rosterList.getCompound(i);
            roster.add(new SessionSnapshotData.RosterEntry(getStringOrDefault(entry, "uuid", ""), getStringOrDefault(entry, "name", "")));
        }

        List<SessionSnapshotData.GameMetadata> games = new ArrayList<>();
        NbtList gameList = root.getList("games", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < gameList.size(); i++) {
            NbtCompound entry = gameList.getCompound(i);
            List<SessionSnapshotData.SetupField> fields = new ArrayList<>();
            NbtList fieldList = entry.getList("fields", NbtElement.COMPOUND_TYPE);
            for (int fieldIndex = 0; fieldIndex < fieldList.size(); fieldIndex++) {
                NbtCompound field = fieldList.getCompound(fieldIndex);
                fields.add(new SessionSnapshotData.SetupField(
                    getStringOrDefault(field, "key", ""),
                    getStringOrDefault(field, "label", ""),
                    getStringOrDefault(field, "type", "string"),
                    getStringOrDefault(field, "default", ""),
                    getBooleanOrDefault(field, "required", false),
                    getIntOrDefault(field, "min", 0),
                    getIntOrDefault(field, "max", 0)
                ));
            }

            games.add(new SessionSnapshotData.GameMetadata(
                getStringOrDefault(entry, "id", ""),
                getStringOrDefault(entry, "displayName", ""),
                getStringOrDefault(entry, "description", ""),
                getStringOrDefault(entry, "icon", "?"),
                getStringOrDefault(entry, "topology", ""),
                getStringOrDefault(entry, "setupKind", "generic"),
                getBooleanOrDefault(entry, "enabled", true),
                fields
            ));
        }

        NbtCompound launcher = getCompoundOrEmpty(root, "launcher");
        NbtCompound memory = getCompoundOrEmpty(root, "memory");
        NbtCompound serverSettings = getCompoundOrEmpty(root, "server");
        NbtCompound retention = getCompoundOrEmpty(root, "retention");
        SessionSnapshotData.update(
            sessions,
            roster,
            games,
            getIntOrDefault(launcher, "maxConcurrentLaunches", SessionSnapshotData.maxConcurrentLaunches()),
            getIntOrDefault(launcher, "queueCapacity", SessionSnapshotData.launcherQueueCapacity()),
            new SessionSnapshotData.MemorySettings(
                getIntOrDefault(memory, "maxHeapGb", SessionSnapshotData.memorySettings().maxHeapGb()),
                getIntOrDefault(memory, "initialHeapGb", SessionSnapshotData.memorySettings().initialHeapGb()),
                getBooleanOrDefault(memory, "enabled", SessionSnapshotData.memorySettings().enabled())
            ),
            new SessionSnapshotData.ServerSettings(
                getIntOrDefault(serverSettings, "viewDistance", SessionSnapshotData.serverSettings().viewDistance()),
                getIntOrDefault(serverSettings, "simulationDistance", SessionSnapshotData.serverSettings().simulationDistance()),
                getBooleanOrDefault(serverSettings, "onlineMode", SessionSnapshotData.serverSettings().onlineMode()),
                getIntOrDefault(serverSettings, "spawnProtection", SessionSnapshotData.serverSettings().spawnProtection()),
                getStringOrDefault(serverSettings, "difficulty", SessionSnapshotData.serverSettings().difficulty()),
                getBooleanOrDefault(serverSettings, "allowFlight", SessionSnapshotData.serverSettings().allowFlight()),
                getBooleanOrDefault(serverSettings, "acceptsTransfers", SessionSnapshotData.serverSettings().acceptsTransfers()),
                getStringOrDefault(serverSettings, "advertisedHost", SessionSnapshotData.serverSettings().advertisedHost())
            ),
            new SessionSnapshotData.RetentionSettings(
                getIntOrDefault(retention, "keepLatestSessions", SessionSnapshotData.retentionSettings().keepLatestSessions()),
                getIntOrDefault(retention, "maxAgeDays", SessionSnapshotData.retentionSettings().maxAgeDays())
            )
        );
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof SessionScreen sessionScreen) {
            sessionScreen.rebuildEntries();
            if (sessionScreen.workspaceView instanceof AdminWorkspaceView) {
                sessionScreen.rebuildWorkspaceChildren();
            }
            if (sessionScreen.workspaceView instanceof ManhuntWorkspaceView manhuntWorkspaceView) {
                manhuntWorkspaceView.refreshRoster();
            }
        }
    }

    private static int getIntOrDefault(NbtCompound nbt, String key, int fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getInt(key) : fallback;
    }

    private static long getLongOrDefault(NbtCompound nbt, String key, long fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getLong(key) : fallback;
    }

    private static boolean getBooleanOrDefault(NbtCompound nbt, String key, boolean fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE) ? nbt.getBoolean(key) : fallback;
    }

    private static String getStringOrDefault(NbtCompound nbt, String key, String fallback) {
        return nbt != null && nbt.contains(key, NbtElement.STRING_TYPE) ? nbt.getString(key) : fallback;
    }

    private static NbtCompound getCompoundOrEmpty(NbtCompound nbt, String key) {
        return nbt != null && nbt.contains(key, NbtElement.COMPOUND_TYPE) ? nbt.getCompound(key) : new NbtCompound();
    }

    @Override
    protected void init() {
        super.init();
        this.openedAt = System.currentTimeMillis();
        this.rebuildEntries();
        this.rebuildWorkspaceChildren();
        this.requestSnapshot();
    }

    private void rebuildEntries() {
        this.minigameEntries.clear();
        this.minigameEntries.addAll(this.createMinigameEntries());
    }

    public <T extends net.minecraft.client.gui.Element & net.minecraft.client.gui.Drawable & net.minecraft.client.gui.Selectable> T addWorkspaceChild(T child) {
        return this.addDrawableChild(child);
    }

    public void openSelectorWorkspace() {
        this.workspaceView = null;
        this.statusMessage = "";
        this.rebuildWorkspaceChildren();
    }

    private void openWorkspaceView(WorkspaceView view) {
        this.workspaceView = view;
        this.statusMessage = "";
        this.rebuildWorkspaceChildren();
    }

    private void rebuildWorkspaceChildren() {
        this.clearChildren();
        Layout layout = this.createLayout();
        if (this.workspaceView == null) {
            this.searchField = new TextFieldWidget(this.textRenderer, layout.search().x(), layout.search().y(), layout.search().width(), layout.search().height(), Text.literal("Search gamemodes"));
            this.searchField.setMaxLength(64);
            this.addDrawableChild(this.searchField);
        } else {
            this.searchField = null;
            this.workspaceView.init(this, layout.content());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.workspaceView != null) {
            this.workspaceView.tick();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = this.createLayout();
        float time = (System.currentTimeMillis() - this.openedAt) / 1000.0F;
        UiRenderer.workspace(context, this.width, this.height, time);

        this.drawWorkspaceNavigation(context, layout.sidebar(), mouseX, mouseY);
        UiRenderer.panel(context, layout.toolbar().x(), layout.toolbar().y(), layout.toolbar().width(), layout.toolbar().height(), UiTheme.PANEL_SOFT, UiTheme.BORDER_SUBTLE);
        String toolbarTitle = this.workspaceView == null ? "Miniverse Multiplayer Platform" : this.workspaceView.title();
        String toolbarSubtitle = this.workspaceView == null ? "Gamemode studio" : this.workspaceView.subtitle();
        context.drawText(this.textRenderer, Text.literal(toolbarTitle), layout.toolbar().x() + 12, layout.toolbar().y() + 8, UiTheme.TEXT, false);
        context.drawText(this.textRenderer, Text.literal(toolbarSubtitle), layout.toolbar().x() + 12, layout.toolbar().y() + 20, UiTheme.TEXT_DIM, false);

        if (this.workspaceView == null) {
            UiRenderer.panel(context, layout.detail().x(), layout.detail().y(), layout.detail().width(), layout.detail().height(), UiTheme.PANEL_SOFT, UiTheme.BORDER_SUBTLE);
            this.drawDetailPanel(context, layout.detail());

            context.drawText(this.textRenderer, Text.literal("Search"), layout.search().x(), layout.search().y() - 11, UiTheme.TEXT_MUTED, false);
        } else {
            this.workspaceView.renderBackground(context, this.textRenderer, layout.content(), mouseX, mouseY, delta);
        }
        super.render(context, mouseX, mouseY, delta);
        if (this.workspaceView == null && this.searchField != null && this.searchField.getText().isEmpty()) {
            context.drawText(this.textRenderer, Text.literal("Filter gamemodes..."), layout.search().x() + 6, layout.search().y() + 6, UiTheme.TEXT_DIM, false);
        }

        this.renderToolbarClose(context, layout.toolbar(), mouseX, mouseY);
        if (this.workspaceView == null) {
            this.drawGamemodeCards(context, layout.cards(), mouseX, mouseY);
        } else {
            this.workspaceView.renderForeground(context, this.textRenderer, layout.content(), mouseX, mouseY, delta);
        }
        if (this.workspaceView == null && !this.statusMessage.isEmpty()) {
            context.drawText(this.textRenderer, Text.literal(this.statusMessage), layout.cards().x(), layout.cards().y() + layout.cards().height() + 10, UiTheme.SUCCESS, false);
        }
    }

    private void drawWorkspaceNavigation(DrawContext context, UiLayout.Rect sidebar, int mouseX, int mouseY) {
        UiRenderer.panel(context, sidebar.x(), sidebar.y(), sidebar.width(), sidebar.height(), UiTheme.SIDEBAR, UiTheme.BORDER_SUBTLE);
        context.drawText(this.textRenderer, Text.literal("MINIVERSE"), sidebar.x() + 12, sidebar.y() + 12, UiTheme.TEXT, false);
        context.drawText(this.textRenderer, Text.literal("Workspace"), sidebar.x() + 12, sidebar.y() + 24, UiTheme.TEXT_DIM, false);
        int y = sidebar.y() + 50;
        for (SidebarSection section : SidebarSection.values()) {
            y = this.drawSidebarSection(context, sidebar, section, y, mouseX, mouseY);
        }
    }

    private int drawSidebarSection(DrawContext context, UiLayout.Rect sidebar, SidebarSection section, int y, int mouseX, int mouseY) {
        UiAnimation.Value animation = this.sidebarAnimations.get(section);
        animation.animateTo(this.expandedSections.contains(section) ? 1.0F : 0.0F, UiTheme.TRANSITION_MS, UiAnimation::easeInOutQuad);
        float progress = animation.get();
        boolean hovered = this.navRowContains(sidebar, y, mouseX, mouseY);
        this.drawNavItem(context, sidebar, y, this.expandedSections.contains(section) ? "⌄" : ">", section.label, false, hovered, 0);
        y += 28;

        List<SidebarChild> children = this.childrenFor(section);
        int childHeight = children.size() * 24;
        int visibleHeight = Math.round(childHeight * progress);
        if (visibleHeight > 0) {
            context.enableScissor(sidebar.x(), y, sidebar.x() + sidebar.width(), y + visibleHeight);
            for (int i = 0; i < children.size(); i++) {
                SidebarChild child = children.get(i);
                int childY = y + i * 24;
                boolean childHovered = this.navRowContains(sidebar, childY, mouseX, mouseY);
                this.drawNavItem(context, sidebar, childY, child.icon, child.label, child.selected.getAsBoolean(), childHovered, child.indent);
            }
            context.disableScissor();
        }
        return y + visibleHeight + 4;
    }

    private void drawNavItem(DrawContext context, UiLayout.Rect sidebar, int y, String icon, String label, boolean selected, boolean hovered, int indent) {
        int fill = selected ? 0x442F4154 : hovered ? 0x2AFFFFFF : 0x00000000;
        int highlightX = sidebar.x() + 7 + indent;
        context.fill(highlightX, y, sidebar.x() + sidebar.width() - 7, y + 22, fill);
        if (selected) {
            context.fill(highlightX, y, highlightX + 3, y + 22, UiTheme.ACCENT_RED);
        }
        int textX = sidebar.x() + 34 + indent;
        int maxTextWidth = Math.max(20, sidebar.x() + sidebar.width() - 14 - textX);
        context.drawText(this.textRenderer, Text.literal(icon), sidebar.x() + 16 + indent, y + 7, selected ? UiTheme.ACCENT_RED : UiTheme.ACCENT, false);
        context.drawText(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(label, maxTextWidth)), textX, y + 7, selected ? UiTheme.TEXT : UiTheme.TEXT_MUTED, false);
    }

    private boolean navRowContains(UiLayout.Rect sidebar, int y, double mouseX, double mouseY) {
        return mouseX >= sidebar.x() + 7 && mouseX <= sidebar.x() + sidebar.width() - 7 && mouseY >= y && mouseY <= y + 22;
    }

    private void renderToolbarClose(DrawContext context, UiLayout.Rect toolbar, int mouseX, int mouseY) {
        UiLayout.Rect close = this.closeButtonBounds(toolbar);
        boolean hovered = close.contains(mouseX, mouseY);
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(UiTheme.ACCENT_RED, 0.30F), hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, close.x(), close.y(), close.width(), close.height(), fill, hovered ? UiTheme.ACCENT_RED : UiTheme.BORDER_SUBTLE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Close"), close.x() + close.width() / 2, close.y() + 7, UiTheme.TEXT);
    }

    private void drawGamemodeCards(DrawContext context, UiLayout.Rect area, int mouseX, int mouseY) {
        List<MinigameEntry> entries = this.filteredEntries();
        int columns = this.cardColumns(area.width());
        for (int i = 0; i < entries.size(); i++) {
            MinigameEntry entry = entries.get(i);
            UiLayout.Rect card = UiLayout.grid(area, i, columns, CARD_HEIGHT, CARD_GAP);
            UiAnimation.Value hoverValue = this.cardHover.computeIfAbsent(entry.id(), ignored -> new UiAnimation.Value(0.0F));
            hoverValue.animateTo(card.contains(mouseX, mouseY) && entry.enabled() ? 1.0F : 0.0F, UiTheme.HOVER_MS);
            float hover = hoverValue.get();
            int accent = this.accentFor(entry.id(), i);
            UiRenderer.card(context, card.x(), card.y(), card.width(), card.height(), hover, accent);
            context.drawText(this.textRenderer, Text.literal(entry.icon()), card.x() + 14, card.y() + 13, accent, false);
            context.drawText(this.textRenderer, entry.buttonLabel(), card.x() + 36, card.y() + 12, entry.enabled() ? UiTheme.TEXT : UiTheme.TEXT_DIM, false);
            List<String> descriptionLines = this.wrapText(entry.description(), Math.max(24, card.width() - 50), 3);
            int descriptionY = card.y() + 28;
            for (String line : descriptionLines) {
                context.drawText(this.textRenderer, Text.literal(line), card.x() + 36, descriptionY, UiTheme.TEXT_MUTED, false);
                descriptionY += 11;
            }
            context.drawText(this.textRenderer, Text.literal(displayTopology(entry.id())), card.x() + 36, card.y() + 65, UiTheme.TEXT_DIM, false);
            if (!entry.enabled()) {
                context.drawText(this.textRenderer, Text.literal("Unavailable"), card.x() + card.width() - 74, card.y() + 55, UiTheme.WARNING, false);
            }
        }
        if (entries.isEmpty()) {
            UiRenderer.panel(context, area.x(), area.y(), area.width(), 64, UiTheme.PANEL_SOFT, UiTheme.BORDER_SUBTLE);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No gamemodes match the current search."), area.x() + area.width() / 2, area.y() + 26, UiTheme.TEXT_MUTED);
        }
    }

    private void drawDetailPanel(DrawContext context, UiLayout.Rect detail) {
        context.drawText(this.textRenderer, Text.literal("Live Workspace"), detail.x() + 12, detail.y() + 12, UiTheme.TEXT, false);
        UiRenderer.divider(context, detail.x() + 12, detail.y() + 28, detail.width() - 24);
        int y = detail.y() + 42;
        drawMetric(context, detail.x() + 14, y, "Players Online", Integer.toString(SessionSnapshotData.roster().size()), UiTheme.ACCENT_GREEN);
        y += 38;
        drawMetric(context, detail.x() + 14, y, "Registered Modes", Integer.toString(SessionSnapshotData.games().size()), UiTheme.ACCENT_BLUE);
        y += 38;
        drawMetric(context, detail.x() + 14, y, "Tracked Sessions", Integer.toString(SessionSnapshotData.sessions().size()), UiTheme.ACCENT);
        y += 48;
        context.drawText(this.textRenderer, Text.literal("Active Players"), detail.x() + 14, y, UiTheme.TEXT_MUTED, false);
        y += 14;
        int shown = 0;
        for (SessionSnapshotData.RosterEntry player : SessionSnapshotData.roster()) {
            if (shown >= 6) {
                context.drawText(this.textRenderer, Text.literal("+" + (SessionSnapshotData.roster().size() - shown) + " more"), detail.x() + 14, y, UiTheme.TEXT_DIM, false);
                break;
            }
            context.fill(detail.x() + 14, y + 2, detail.x() + 18, y + 6, UiTheme.ACCENT_GREEN);
            context.drawText(this.textRenderer, Text.literal(player.name()), detail.x() + 24, y, UiTheme.TEXT_MUTED, false);
            y += 12;
            shown++;
        }
    }

    private void drawMetric(DrawContext context, int x, int y, String label, String value, int accent) {
        context.fill(x, y, x + 4, y + 28, accent);
        context.drawText(this.textRenderer, Text.literal(value), x + 12, y, UiTheme.TEXT, false);
        context.drawText(this.textRenderer, Text.literal(label), x + 12, y + 13, UiTheme.TEXT_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout layout = this.createLayout();
        if (button == 0 && this.handleWorkspaceNavigationClick(layout.sidebar(), mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && this.closeButtonBounds(layout.toolbar()).contains(mouseX, mouseY)) {
            this.close();
            return true;
        }
        if (this.workspaceView != null) {
            if (this.workspaceView.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        List<MinigameEntry> entries = this.filteredEntries();
        int columns = this.cardColumns(layout.cards().width());
        for (int i = 0; i < entries.size(); i++) {
            UiLayout.Rect card = UiLayout.grid(layout.cards(), i, columns, CARD_HEIGHT, CARD_GAP);
            if (button == 0 && card.contains(mouseX, mouseY)) {
                MinigameEntry entry = entries.get(i);
                if (entry.enabled()) {
                    entry.activate(this);
                } else {
                    this.statusMessage = "That gamemode is not available in this client build.";
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleWorkspaceNavigationClick(UiLayout.Rect sidebar, double mouseX, double mouseY) {
        if (mouseX < sidebar.x() || mouseX > sidebar.x() + sidebar.width()) {
            return false;
        }
        int y = sidebar.y() + 50;
        for (SidebarSection section : SidebarSection.values()) {
            if (this.navRowContains(sidebar, y, mouseX, mouseY)) {
                this.toggleSection(section);
                return true;
            }
            y += 28;
            List<SidebarChild> children = this.childrenFor(section);
            int visibleHeight = Math.round(children.size() * 24 * this.sidebarAnimations.get(section).get());
            if (visibleHeight > 0 && mouseY >= y && mouseY <= y + visibleHeight) {
                int index = (int) ((mouseY - y) / 24);
                if (index >= 0 && index < children.size()) {
                    children.get(index).action.run();
                    return true;
                }
            }
            y += visibleHeight + 4;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.workspaceView != null && this.workspaceView.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.workspaceView != null && this.workspaceView.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.workspaceView != null && this.workspaceView.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    private List<SidebarChild> childrenFor(SidebarSection section) {
        List<SidebarChild> children = new ArrayList<>();
        switch (section) {
            case GAMEMODES -> {
                children.add(new SidebarChild("*", "All gamemodes", this::openSelectorWorkspace, () -> this.workspaceView == null));
                for (MinigameEntry entry : this.minigameEntries) {
                    children.add(new SidebarChild(entry.icon(), entry.name(), () -> this.openGamemode(entry), () -> this.isCurrentGamemodeWorkspace(entry.id())));
                    if ("manhunt".equals(entry.id()) && this.workspaceView instanceof ManhuntWorkspaceView manhuntWorkspaceView) {
                        for (ManhuntWorkspaceView.Module module : ManhuntWorkspaceView.Module.values()) {
                            children.add(new SidebarChild(module.icon(), module.label(), () -> this.openManhuntModule(module), () -> manhuntWorkspaceView.activeModule() == module, 24));
                        }
                    }
                }
            }
            case SESSION -> {
                children.add(new SidebarChild("#", "Live sessions", this::openSessionsWorkspace, () -> this.isAdminMode(AdminWorkspaceView.Mode.SESSIONS)));
                children.add(new SidebarChild(":", "History", this::openHistoryWorkspace, () -> this.isAdminMode(AdminWorkspaceView.Mode.HISTORY)));
            }
            case SERVER -> children.add(new SidebarChild("!", "Session server properties", this::openServerWorkspace, () -> this.isAdminMode(AdminWorkspaceView.Mode.SERVER)));
            case APPEARANCE -> children.add(new SidebarChild("~", "Workspace", this::openAppearance, () -> this.workspaceView instanceof AppearanceWorkspaceView));
        }
        return children;
    }

    private void toggleSection(SidebarSection section) {
        if (this.expandedSections.contains(section)) {
            this.expandedSections.remove(section);
        } else {
            this.expandedSections.add(section);
        }
    }

    private void openGamemode(MinigameEntry entry) {
        if (entry.enabled()) {
            entry.activate(this);
        } else {
            this.statusMessage = "That gamemode is not available in this client build.";
        }
    }

    private boolean isCurrentGamemodeWorkspace(String id) {
        return "manhunt".equals(id) && this.workspaceView instanceof ManhuntWorkspaceView;
    }

    private void openManhuntModule(ManhuntWorkspaceView.Module module) {
        if (this.workspaceView instanceof ManhuntWorkspaceView manhuntWorkspaceView) {
            manhuntWorkspaceView.setActiveModule(module);
            this.rebuildWorkspaceChildren();
        }
    }

    private UiLayout.Rect closeButtonBounds(UiLayout.Rect toolbar) {
        return new UiLayout.Rect(toolbar.x() + toolbar.width() - TOOLBAR_BUTTON_WIDTH, toolbar.y() + 6, TOOLBAR_BUTTON_WIDTH, UiTheme.BUTTON_HEIGHT);
    }

    private List<MinigameEntry> filteredEntries() {
        String query = this.searchField == null ? "" : this.searchField.getText().trim().toLowerCase();
        if (query.isEmpty()) {
            return this.minigameEntries;
        }
        List<MinigameEntry> filtered = new ArrayList<>();
        for (MinigameEntry entry : this.minigameEntries) {
            if (entry.name().toLowerCase().contains(query)
                || entry.description().toLowerCase().contains(query)
                || entry.id().toLowerCase().contains(query)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private Layout createLayout() {
        int margin = 12;
        UiLayout.Rect sidebarRect = new UiLayout.Rect(margin, margin, UiTheme.SIDEBAR_WIDTH, this.height - margin * 2);
        int workspaceX = sidebarRect.x() + sidebarRect.width() + UiTheme.GAP;
        int workspaceWidth = Math.max(1, this.width - workspaceX - margin);
        UiLayout.Rect toolbar = new UiLayout.Rect(workspaceX, margin, workspaceWidth, UiTheme.TOOLBAR_HEIGHT);
        int detailWidth = Math.min(230, Math.max(180, workspaceWidth / 4));
        UiLayout.Rect detail = new UiLayout.Rect(workspaceX + workspaceWidth - detailWidth, toolbar.y() + toolbar.height() + UiTheme.GAP, detailWidth, this.height - toolbar.height() - margin * 2 - UiTheme.GAP);
        UiLayout.Rect search = new UiLayout.Rect(workspaceX, toolbar.y() + toolbar.height() + UiTheme.GAP + 14, Math.max(140, workspaceWidth - detailWidth - UiTheme.GAP), UiTheme.INPUT_HEIGHT);
        UiLayout.Rect cards = new UiLayout.Rect(workspaceX, search.y() + search.height() + 18, Math.max(1, workspaceWidth - detailWidth - UiTheme.GAP), Math.max(1, this.height - search.y() - search.height() - 42));
        UiLayout.Rect content = new UiLayout.Rect(workspaceX, toolbar.y() + toolbar.height() + UiTheme.GAP, workspaceWidth, Math.max(1, this.height - toolbar.y() - toolbar.height() - UiTheme.GAP - margin));
        return new Layout(sidebarRect, toolbar, search, cards, detail, content);
    }

    private int cardColumns(int width) {
        if (width >= 760) {
            return 3;
        }
        if (width >= 470) {
            return 2;
        }
        return 1;
    }

    private int accentFor(String id, int index) {
        if ("manhunt".equals(id)) {
            return UiTheme.ACCENT_RED;
        }
        if ("speedrun".equals(id)) {
            return UiTheme.ACCENT_GREEN;
        }
        if ("bountyhunt".equals(id)) {
            return UiTheme.ACCENT;
        }
        if ("resource_sprint".equals(id)) {
            return UiTheme.ACCENT_BLUE;
        }
        return switch (index % 4) {
            case 0 -> UiTheme.ACCENT_BLUE;
            case 1 -> UiTheme.ACCENT_RED;
            case 2 -> UiTheme.ACCENT_GREEN;
            default -> UiTheme.ACCENT;
        };
    }

    private List<String> wrapText(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) {
            return lines;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (this.textRenderer.getWidth(candidate) <= maxWidth) {
                line = new StringBuilder(candidate);
                continue;
            }

            if (!line.isEmpty()) {
                lines.add(line.toString());
                if (lines.size() >= maxLines) {
                    return lines;
                }
                line = new StringBuilder();
            }

            if (this.textRenderer.getWidth(word) <= maxWidth) {
                line.append(word);
            } else {
                lines.add(this.textRenderer.trimToWidth(word, maxWidth));
                if (lines.size() >= maxLines) {
                    return lines;
                }
            }
        }

        if (!line.isEmpty() && lines.size() < maxLines) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String displayTopology(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "speedrun" -> "Isolated worlds";
            case "manhunt", "bountyhunt", "resource_sprint", "deathswap" -> "Shared arena";
            default -> "Session ready";
        };
    }

    private void openManhunt() {
        this.openWorkspaceView(new ManhuntWorkspaceView());
    }

    private void openAppearance() {
        this.openWorkspaceView(new AppearanceWorkspaceView());
    }

    private void openSessionsWorkspace() {
        this.openWorkspaceView(new AdminWorkspaceView(AdminWorkspaceView.Mode.SESSIONS));
    }

    private void openHistoryWorkspace() {
        this.openWorkspaceView(new AdminWorkspaceView(AdminWorkspaceView.Mode.HISTORY));
    }

    private void openServerWorkspace() {
        this.openWorkspaceView(new AdminWorkspaceView(AdminWorkspaceView.Mode.SERVER));
    }

    private boolean isAdminMode(AdminWorkspaceView.Mode mode) {
        return this.workspaceView instanceof AdminWorkspaceView adminWorkspaceView && adminWorkspaceView.mode() == mode;
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

    private void requestSnapshot() {
        if (this.client.player != null) {
            ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("metadata"));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private record Layout(UiLayout.Rect sidebar, UiLayout.Rect toolbar, UiLayout.Rect search, UiLayout.Rect cards, UiLayout.Rect detail, UiLayout.Rect content) {
    }

    private record SidebarChild(String icon, String label, Runnable action, java.util.function.BooleanSupplier selected, int indent) {
        private SidebarChild(String icon, String label, Runnable action, java.util.function.BooleanSupplier selected) {
            this(icon, label, action, selected, 12);
        }
    }

    private enum SidebarSection {
        GAMEMODES("Gamemodes"),
        SESSION("Session"),
        SERVER("Server"),
        APPEARANCE("Appearance");

        private final String label;

        SidebarSection(String label) {
            this.label = label;
        }
    }
}
