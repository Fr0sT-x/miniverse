package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.AdminWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.AppearanceWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.BountyHuntWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.BridgeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.DeathSwapWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.GamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.ManhuntWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.ResourceSprintWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.BlockShuffleWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.DeathShuffleWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.SpeedrunWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.WorkspaceView;
import dev.frost.miniverse.client.gui.map.MapManagementWorkspaceView;
import dev.frost.miniverse.client.gui.map.MapDetailsWorkspaceView;
import dev.frost.miniverse.client.gui.map.MapEditorState;
import dev.frost.miniverse.client.gui.map.MapEditorWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.InfectionWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.DuelsWorkspaceView;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

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
    private static final int RESET_BUTTON_WIDTH = 80;
    private static final int SIDEBAR_HEADER_HEIGHT = 44;
    private static final int SIDEBAR_SECTION_HEIGHT = 28;
    private static final int SIDEBAR_ROW_HEIGHT = 26;
    private static final int SIDEBAR_GROUP_HEIGHT = 20;
    private static final int SIDEBAR_ROW_GAP = 4;
    private static final int SIDEBAR_INSET = 10;
    private static final int SIDEBAR_SCROLLBAR_WIDTH = 4;
    private static final int SIDEBAR_SCROLLBAR_INSET = 3;
    private static final int MODULE_RAIL_INSET = 8;
    private static final int MODULE_RAIL_WIDTH = 4;
    private static final int MODULE_RAIL_GAP = 4;
    private static final int MODULE_ITEM_INDENT = 26;
    private static final int ARROW_TEXTURE_SIZE = 16;
    private static final int ARROW_RENDER_SIZE = 11;
    private static final Identifier DROPDOWN_ARROW_TEXTURE = Identifier.of("miniverse", "textures/gui/icons/dropdown_arrow.png");
    private static final Map<String, Consumer<SessionScreen>> CUSTOM_SETUP_SCREENS = Map.ofEntries(
        Map.entry("manhunt", SessionScreen::openManhunt),
        Map.entry("speedrun", SessionScreen::openSpeedrun),
        Map.entry("bountyhunt", SessionScreen::openBountyHunt),
        Map.entry("bridge", SessionScreen::openBridge),
        Map.entry("bedwars", SessionScreen::openBedwars),
        Map.entry("resource_sprint", SessionScreen::openResourceSprint),
        Map.entry("deathswap", SessionScreen::openDeathSwap),
        Map.entry("infection", SessionScreen::openInfection),
        Map.entry("block_shuffle", SessionScreen::openBlockShuffle),
        Map.entry("death_shuffle", SessionScreen::openDeathShuffle),
        Map.entry("murdermystery", SessionScreen::openMurderMystery),
        Map.entry("duels", SessionScreen::openDuels),
        Map.entry("pillarsoffortune", SessionScreen::openPillarsOfFortune),
        Map.entry("horde_survival", SessionScreen::openHordeSurvival),
        Map.entry("zombies", SessionScreen::openZombies),
        Map.entry("dropper", SessionScreen::openDropper),
        Map.entry("microfrenzy", SessionScreen::openMicroFrenzy),
        Map.entry("microfrezy", SessionScreen::openMicroFrenzy)
    );

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<MinigameEntry> minigameEntries = new ArrayList<>();
    private final Map<String, UiAnimation.Value> cardHover = new HashMap<>();
    private final Map<SidebarSection, UiAnimation.Value> sidebarAnimations = new EnumMap<>(SidebarSection.class);
    private final MapEditorState mapEditorState = MapEditorState.INSTANCE;
    private final Set<SidebarSection> expandedSections = EnumSet.of(SidebarSection.GAMEMODES);
    private final Set<String> collapsedModuleGroups = new java.util.HashSet<>();
    private final Map<String, UiAnimation.Value> moduleGroupAnimations = new HashMap<>();
    private TextFieldWidget searchField;
    private TextFieldWidget sidebarSearchField;
    private WorkspaceView workspaceView;

    private static WorkspaceView lastActiveWorkspace = null;
    private static final Map<String, WorkspaceView> WORKSPACE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearWorkspaceCache() {
        lastActiveWorkspace = null;
        WORKSPACE_CACHE.clear();
        dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView.clearAllCaches();
    }

    public WorkspaceView getWorkspaceView() {
        return this.workspaceView;
    }
    private String statusMessage = "";
    private long openedAt;
    private boolean defaultWorkspaceApplied;
    public static final int MIN_LEFT_SIDEBAR_WIDTH = 150;
    public static final int MAX_LEFT_SIDEBAR_WIDTH = 360;
    private static int leftSidebarTargetWidth = UiTheme.SIDEBAR_WIDTH;
    private static boolean leftSidebarCollapsed = false;
    private static final UiAnimation.Value leftSidebarAnimation = new UiAnimation.Value(1.0F);

    private boolean draggingLeftSplitter = false;
    private double leftSplitterDragStartMouseX = 0;
    private int leftSplitterDragStartWidth = UiTheme.SIDEBAR_WIDTH;
    private UiLayout.Rect leftSplitterHitRect = null;
    private UiLayout.Rect leftCollapseHandleRect = null;
    private UiLayout.Rect leftCollapseStripRect = null;
    private boolean lastLeftSidebarAnimating = false;

    // Unified Right Sidebar / Match Dock State (shared across All Gamemodes overview and gamemode workspaces)
    public static int rightDockTargetWidth = dev.frost.miniverse.client.gui.workspace.framework.StandardWorkspaceLayout.DEFAULT_DOCK_WIDTH;
    public static boolean rightDockCollapsed = false;
    public static final UiAnimation.Value rightDockAnimation = new UiAnimation.Value(1.0F);

    public static boolean isRightDockCollapsed() {
        return rightDockCollapsed;
    }

    public static void setRightDockCollapsed(boolean collapsed) {
        rightDockCollapsed = collapsed;
        rightDockAnimation.animateTo(collapsed ? 0.0F : 1.0F, 220, UiAnimation::easeInOutQuad);
    }

    private boolean draggingOverviewSplitter = false;
    private double overviewSplitterDragStartMouseX = 0;
    private int overviewSplitterDragStartWidth = 220;
    private UiLayout.Rect overviewSplitterHitRect = null;
    private UiLayout.Rect overviewCollapseHandleRect = null;
    private UiLayout.Rect overviewCollapseStripRect = null;
    private boolean lastOverviewDockAnimating = false;

    private double sidebarScroll;
    private double sidebarMaxScroll;
    private boolean draggingSidebarScrollbar;
    private double sidebarDragClickOffsetY;
    private final List<WorkspaceView> history = new ArrayList<>();
    private int historyIndex = -1;

    public SessionScreen() {
        super(Text.literal("Miniverse"));
        for (SidebarSection section : SidebarSection.values()) {
            this.sidebarAnimations.put(section, new UiAnimation.Value(this.expandedSections.contains(section) ? 1.0F : 0.0F));
        }
    }

    public static void onServerSnapshot(NbtCompound root) {
        boolean includesHistory = root.getBoolean("includesHistory");
        List<SessionSnapshotData.SessionSummary> sessions = new ArrayList<>();
        NbtList sessionList = root.getList("sessions", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < sessionList.size(); i++) {
            NbtCompound entry = sessionList.getCompound(i);
            List<SessionSnapshotData.GroupSummary> groups = new ArrayList<>();
            NbtList groupList = entry.getList("groups", NbtElement.COMPOUND_TYPE);
            for (int groupIndex = 0; groupIndex < groupList.size(); groupIndex++) {
                NbtCompound group = groupList.getCompound(groupIndex);
                groups.add(new SessionSnapshotData.GroupSummary(
                    getStringOrDefault(group, "label", ""),
                    getStringOrDefault(group, "displayName", ""),
                    getStringOrDefault(group, "state", ""),
                    getIntOrDefault(group, "playerCount", 0)
                ));
            }
            List<String> playerNames = new ArrayList<>();
            NbtList playersListNbt = entry.getList("players", NbtElement.STRING_TYPE);
            for (int p = 0; p < playersListNbt.size(); p++) {
                playerNames.add(playersListNbt.getString(p));
            }
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
                getBooleanOrDefault(entry, "retained", false),
                groups,
                playerNames
            ));
        }
        
        if (!includesHistory) {
            sessions.addAll(SessionSnapshotData.sessions().stream()
                .filter(SessionSnapshotData.SessionSummary::retained)
                .toList());
        } else {
            SessionSnapshotData.setHistoryLoaded(true);
        }

        List<SessionSnapshotData.RosterEntry> roster = new ArrayList<>();
        NbtList rosterList = root.getList("players", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < rosterList.size(); i++) {
            NbtCompound entry = rosterList.getCompound(i);
            roster.add(new SessionSnapshotData.RosterEntry(getStringOrDefault(entry, "uuid", ""), getStringOrDefault(entry, "name", "")));
        }

        List<SessionSnapshotData.PendingJoiner> pendingJoiners = new ArrayList<>();
        NbtList pendingList = root.getList("pendingJoiners", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < pendingList.size(); i++) {
            NbtCompound entry = pendingList.getCompound(i);
            pendingJoiners.add(new SessionSnapshotData.PendingJoiner(
                getStringOrDefault(entry, "uuid", ""),
                getStringOrDefault(entry, "name", ""),
                getLongOrDefault(entry, "joinedAt", 0L)
            ));
        }

        List<SessionSnapshotData.MapSummary> maps = new ArrayList<>();
        NbtList mapList = root.getList("maps", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < mapList.size(); i++) {
            NbtCompound entry = mapList.getCompound(i);
            List<String> gamemodes = new ArrayList<>();
            NbtList gamesForMap = entry.getList("gamemodes", NbtElement.STRING_TYPE);
            for (int gameIndex = 0; gameIndex < gamesForMap.size(); gameIndex++) {
                gamemodes.add(gamesForMap.getString(gameIndex));
            }
            List<SessionSnapshotData.MapValidation> validations = new ArrayList<>();
            NbtList validationList = entry.getList("validations", NbtElement.COMPOUND_TYPE);
            for (int validationIndex = 0; validationIndex < validationList.size(); validationIndex++) {
                NbtCompound validation = validationList.getCompound(validationIndex);
                List<String> errors = new ArrayList<>();
                NbtList errorList = validation.getList("errors", NbtElement.STRING_TYPE);
                for (int errorIndex = 0; errorIndex < errorList.size(); errorIndex++) {
                    errors.add(errorList.getString(errorIndex));
                }
                validations.add(new SessionSnapshotData.MapValidation(
                    getStringOrDefault(validation, "game", ""),
                    getBooleanOrDefault(validation, "valid", false),
                    errors
                ));
            }
            List<String> tags = new ArrayList<>();
            if (entry.contains("tags", NbtElement.LIST_TYPE)) {
                NbtList tagList = entry.getList("tags", NbtElement.STRING_TYPE);
                for (int tagIndex = 0; tagIndex < tagList.size(); tagIndex++) {
                    tags.add(tagList.getString(tagIndex));
                }
            }

            maps.add(new SessionSnapshotData.MapSummary(
                getStringOrDefault(entry, "id", ""),
                getStringOrDefault(entry, "name", ""),
                getStringOrDefault(entry, "description", ""),
                getStringOrDefault(entry, "folder", ""),
                getLongOrDefault(entry, "lastModified", 0L),
                getBooleanOrDefault(entry, "hasWorld", false),
                gamemodes,
                validations,
                tags
            ));
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
        boolean sessionServer = getBooleanOrDefault(root, "sessionServer", false);
        boolean mapEditor = getBooleanOrDefault(root, "mapEditor", false);
        List<SessionSnapshotData.EditorExtension> editorExtensions = readEditorExtensions(root.getList("mapEditorExtensions", NbtElement.COMPOUND_TYPE));
        SessionSnapshotData.EditorState editorState = readEditorState(getCompoundOrEmpty(root, "mapEditorState"));
        List<dev.frost.miniverse.minigame.impl.duels.DuelType> duelTypes = new ArrayList<>();
        if (root.contains("duelTypes", NbtElement.LIST_TYPE)) {
            NbtList dtList = root.getList("duelTypes", NbtElement.COMPOUND_TYPE);
            for (int dtIndex = 0; dtIndex < dtList.size(); dtIndex++) {
                NbtCompound dt = dtList.getCompound(dtIndex);
                duelTypes.add(new dev.frost.miniverse.minigame.impl.duels.DuelType(
                    getStringOrDefault(dt, "id", ""),
                    getStringOrDefault(dt, "name", ""),
                    getBooleanOrDefault(dt, "knockbackOnly", false),
                    getBooleanOrDefault(dt, "allowBuilding", true),
                    getBooleanOrDefault(dt, "allowBreaking", true),
                    getBooleanOrDefault(dt, "allowHunger", true),
                    getBooleanOrDefault(dt, "naturalRegen", true)
                ));
            }
        }

        SessionSnapshotData.update(
            sessions,
            roster,
            pendingJoiners,
            maps,
            duelTypes,
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
                getIntOrDefault(retention, "maxAgeDays", SessionSnapshotData.retentionSettings().maxAgeDays())
            ),
            sessionServer
        );
        SessionSnapshotData.updateEditor(mapEditor, editorExtensions, editorState);
        if (root.contains("presets", NbtElement.COMPOUND_TYPE)) {
            SessionSnapshotData.updatePresets(root.getCompound("presets"));
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof SessionScreen sessionScreen) {
            sessionScreen.rebuildEntries();
            sessionScreen.applyDefaultWorkspace();
            if (sessionScreen.workspaceView instanceof AdminWorkspaceView
                || sessionScreen.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView) {
                sessionScreen.rebuildWorkspaceChildren();
            }
            if (sessionScreen.workspaceView instanceof GamemodeWorkspaceView.RosterRefreshable refreshable) {
                refreshable.refreshRoster();
            }
            if (sessionScreen.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView agw) {
                agw.onPresetsUpdated();
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

    private static List<SessionSnapshotData.EditorExtension> readEditorExtensions(NbtList list) {
        List<SessionSnapshotData.EditorExtension> extensions = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            List<SessionSnapshotData.EditorMarkerDefinition> markers = new ArrayList<>();
            NbtList markerList = entry.getList("markers", NbtElement.COMPOUND_TYPE);
            for (int markerIndex = 0; markerIndex < markerList.size(); markerIndex++) {
                NbtCompound marker = markerList.getCompound(markerIndex);
                SessionSnapshotData.EditorMarkerGrouping grouping = null;
                if (marker.contains("grouping", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound groupingNbt = marker.getCompound("grouping");
                    grouping = new SessionSnapshotData.EditorMarkerGrouping(
                        getStringOrDefault(groupingNbt, "parentKey", ""),
                        getStringOrDefault(groupingNbt, "type", "LOGICAL"),
                        getStringOrDefault(groupingNbt, "propertyKey", null)
                    );
                }
                markers.add(new SessionSnapshotData.EditorMarkerDefinition(
                    getStringOrDefault(marker, "key", ""),
                    getStringOrDefault(marker, "displayName", ""),
                    getStringOrDefault(marker, "type", "POINT"),
                    getStringOrDefault(marker, "configKey", ""),
                    getIntOrDefault(marker, "minCount", 0),
                    getIntOrDefault(marker, "maxCount", -1),
                    getStringOrDefault(marker, "description", ""),
                    grouping
                ));
            }
            extensions.add(new SessionSnapshotData.EditorExtension(
                getStringOrDefault(entry, "gameId", ""),
                getStringOrDefault(entry, "displayName", ""),
                markers
            ));
        }
        return extensions;
    }

    private static SessionSnapshotData.EditorState readEditorState(NbtCompound root) {
        List<SessionSnapshotData.EditorGameState> games = new ArrayList<>();
        NbtList gameList = root.getList("games", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < gameList.size(); i++) {
            NbtCompound game = gameList.getCompound(i);
            List<SessionSnapshotData.EditorMarkerGroup> groups = new ArrayList<>();
            NbtList groupList = game.getList("markerGroups", NbtElement.COMPOUND_TYPE);
            for (int groupIndex = 0; groupIndex < groupList.size(); groupIndex++) {
                NbtCompound group = groupList.getCompound(groupIndex);
                List<SessionSnapshotData.EditorMarker> markers = new ArrayList<>();
                NbtList markerList = group.getList("markers", NbtElement.COMPOUND_TYPE);
                for (int markerIndex = 0; markerIndex < markerList.size(); markerIndex++) {
                    NbtCompound marker = markerList.getCompound(markerIndex);
                    List<SessionSnapshotData.EditorPoint> points = new ArrayList<>();
                    NbtList pointList = marker.getList("points", NbtElement.COMPOUND_TYPE);
                    for (int pointIndex = 0; pointIndex < pointList.size(); pointIndex++) {
                        NbtCompound point = pointList.getCompound(pointIndex);
                        points.add(new SessionSnapshotData.EditorPoint(
                            point.getDouble("x"),
                            point.getDouble("y"),
                            point.getDouble("z"),
                            point.getFloat("yaw"),
                            point.getFloat("pitch")
                        ));
                    }
                    List<SessionSnapshotData.EditorRegionPart> regions = new ArrayList<>();
                    if (marker.contains("regions", NbtElement.LIST_TYPE)) {
                        NbtList regionList = marker.getList("regions", NbtElement.COMPOUND_TYPE);
                        for (int regionIndex = 0; regionIndex < regionList.size(); regionIndex++) {
                            NbtCompound region = regionList.getCompound(regionIndex);
                            NbtCompound minNbt = region.getCompound("min");
                            NbtCompound maxNbt = region.getCompound("max");
                            SessionSnapshotData.EditorPoint min = new SessionSnapshotData.EditorPoint(
                                minNbt.getDouble("x"), minNbt.getDouble("y"), minNbt.getDouble("z"), minNbt.getFloat("yaw"), minNbt.getFloat("pitch")
                            );
                            SessionSnapshotData.EditorPoint max = new SessionSnapshotData.EditorPoint(
                                maxNbt.getDouble("x"), maxNbt.getDouble("y"), maxNbt.getDouble("z"), maxNbt.getFloat("yaw"), maxNbt.getFloat("pitch")
                            );
                            regions.add(new SessionSnapshotData.EditorRegionPart(min, max));
                        }
                    }
                    String propertiesStr = getStringOrDefault(marker, "properties", "{}");
                    com.google.gson.JsonObject properties = new com.google.gson.JsonObject();
                    try {
                        properties = com.google.gson.JsonParser.parseString(propertiesStr).getAsJsonObject();
                    } catch (Exception e) {}
                    markers.add(new SessionSnapshotData.EditorMarker(
                        getStringOrDefault(marker, "id", ""),
                        getStringOrDefault(marker, "definitionKey", ""),
                        getStringOrDefault(marker, "name", ""),
                        getStringOrDefault(marker, "type", "POINT"),
                        points,
                        regions,
                        properties
                    ));
                }
                groups.add(new SessionSnapshotData.EditorMarkerGroup(getStringOrDefault(group, "definitionKey", ""), markers));
            }
            NbtCompound validationNbt = getCompoundOrEmpty(game, "validation");
            boolean valid = getBooleanOrDefault(validationNbt, "valid", false);
            List<String> errors = new ArrayList<>();
            NbtList errorsNbt = validationNbt.getList("errors", NbtElement.STRING_TYPE);
            for (int e = 0; e < errorsNbt.size(); e++) {
                errors.add(errorsNbt.getString(e));
            }
            List<String> warnings = new ArrayList<>();
            NbtList warningsNbt = validationNbt.getList("warnings", NbtElement.STRING_TYPE);
            for (int w = 0; w < warningsNbt.size(); w++) {
                warnings.add(warningsNbt.getString(w));
            }
            SessionSnapshotData.EditorValidation validation = new SessionSnapshotData.EditorValidation(valid, errors, warnings);

            games.add(new SessionSnapshotData.EditorGameState(getStringOrDefault(game, "gameId", ""), groups, validation));
        }
        return new SessionSnapshotData.EditorState(getStringOrDefault(root, "mapId", ""), games);
    }

    public <T extends net.minecraft.client.gui.widget.ClickableWidget> T addWidget(T widget) {
        return this.addDrawableChild(widget);
    }

    @Override
    protected void init() {
        super.init();
        this.openedAt = System.currentTimeMillis();
        this.rebuildEntries();
        if (lastActiveWorkspace != null) {
            this.openWorkspaceView(lastActiveWorkspace);
        } else {
            this.applyDefaultWorkspace();
        }
        this.resetExpandedSectionsForWorkspace();
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
        if (this.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView agw) {
            agw.flushWidgetState();
        }
        if (this.historyIndex < this.history.size() - 1) {
            this.history.subList(this.historyIndex + 1, this.history.size()).clear();
        }
        this.history.add(null);
        this.historyIndex++;
        
        this.workspaceView = null;
        lastActiveWorkspace = null;
        this.statusMessage = "";
        this.syncExpandedSectionsForWorkspace();
        this.rebuildWorkspaceChildren();
    }

    public void openWorkspaceView(WorkspaceView view) {
        if (this.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView agw) {
            agw.flushWidgetState();
        }
        if (this.historyIndex < this.history.size() - 1) {
            this.history.subList(this.historyIndex + 1, this.history.size()).clear();
        }
        this.history.add(view);
        this.historyIndex++;
        
        this.workspaceView = view;
        lastActiveWorkspace = view;
        this.statusMessage = "";
        this.defaultWorkspaceApplied = true;
        if (view instanceof GamemodeWorkspaceView) {
            this.collapsedModuleGroups.clear();
        }
        this.syncExpandedSectionsForWorkspace();
        this.rebuildWorkspaceChildren();
        this.requestSnapshot();
    }
    
    public void goBack() {
        if (this.historyIndex > 0) {
            this.historyIndex--;
            this.restoreWorkspaceFromHistory(this.history.get(this.historyIndex));
        }
    }
    
    public void goForward() {
        if (this.historyIndex < this.history.size() - 1) {
            this.historyIndex++;
            this.restoreWorkspaceFromHistory(this.history.get(this.historyIndex));
        }
    }
    
    private void restoreWorkspaceFromHistory(WorkspaceView view) {
        if (this.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView agw) {
            agw.flushWidgetState();
        }
        this.workspaceView = view;
        lastActiveWorkspace = view;
        this.statusMessage = "";
        this.defaultWorkspaceApplied = true;
        if (view instanceof GamemodeWorkspaceView) {
            this.collapsedModuleGroups.clear();
        }
        this.syncExpandedSectionsForWorkspace();
        this.rebuildWorkspaceChildren();
    }

    private void applyDefaultWorkspace() {
        if (SessionSnapshotData.mapEditor()) {
            if (!this.defaultWorkspaceApplied && this.workspaceView == null) {
                this.openMapEditorWorkspace();
                this.defaultWorkspaceApplied = true;
            }
            return;
        }
        if (this.defaultWorkspaceApplied || !SessionSnapshotData.sessionServer() || this.workspaceView != null) {
            return;
        }
        this.openSessionsWorkspace();
        this.defaultWorkspaceApplied = true;
    }

    public void openMapDetails(String mapId) {
        this.openWorkspaceView(new MapDetailsWorkspaceView(mapId, this::openMapsWorkspace));
    }

    public void openMapEditorGamemode(String gameId) {
        this.mapEditorState.selectedGameId = gameId;
        this.mapEditorState.selectedDefinitionKey = "";
        this.openWorkspaceView(MapEditorWorkspaceView.forGamemode(this.mapEditorState, this::requestSnapshot, gameId));
    }

    private void syncExpandedSectionsForWorkspace() {
        this.expandedSections.add(SidebarSection.GAMEMODES);
        this.expandedSections.add(this.resolveSectionForWorkspace());
    }

    private void resetExpandedSectionsForWorkspace() {
        this.expandedSections.add(SidebarSection.GAMEMODES);
        this.expandedSections.add(this.resolveSectionForWorkspace());
        for (SidebarSection section : SidebarSection.values()) {
            UiAnimation.Value anim = this.sidebarAnimations.get(section);
            if (anim != null && this.expandedSections.contains(section)) {
                anim.set(1.0F);
            }
        }
    }

    private SidebarSection resolveSectionForWorkspace() {
        if (this.workspaceView instanceof AdminWorkspaceView adminWorkspaceView) {
            return switch (adminWorkspaceView.mode()) {
                case SESSIONS, HISTORY -> SidebarSection.SESSION;
                case SERVER -> SidebarSection.SERVER;
                default -> SidebarSection.SESSION;
            };
        }
        if (this.workspaceView instanceof AppearanceWorkspaceView) {
            return SidebarSection.APPEARANCE;
        }
        if (this.workspaceView instanceof MapManagementWorkspaceView) {
            return SidebarSection.MAPS;
        }
        if (this.workspaceView instanceof MapEditorWorkspaceView) {
            return SidebarSection.MAPS;
        }
        return SidebarSection.GAMEMODES;
    }

    public void rebuildWorkspaceChildren() {
        this.clearChildren();
        Layout layout = this.createLayout();
        if (this.workspaceView == null) {
            this.searchField = new TextFieldWidget(this.textRenderer, layout.search().x(), layout.search().y(), layout.search().width(), layout.search().height(), Text.literal("Search gamemodes"));
            this.searchField.setMaxLength(64);
            this.addDrawableChild(this.searchField);
        } else {
            this.searchField = null;
        }

        if (this.sidebarSearchField == null) {
            this.sidebarSearchField = new TextFieldWidget(this.textRenderer, layout.sidebarSearch().x(), layout.sidebarSearch().y(), layout.sidebarSearch().width(), layout.sidebarSearch().height(), Text.literal("Search"));
            this.sidebarSearchField.setMaxLength(48);
            this.addDrawableChild(this.sidebarSearchField);
        } else {
            this.sidebarSearchField.setX(layout.sidebarSearch().x());
            this.sidebarSearchField.setY(layout.sidebarSearch().y());
            this.sidebarSearchField.setWidth(layout.sidebarSearch().width());
            this.sidebarSearchField.setHeight(layout.sidebarSearch().height());
            this.addDrawableChild(this.sidebarSearchField);
        }

        if (this.workspaceView != null) {
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
        boolean wasAnimating = this.lastLeftSidebarAnimating;
        boolean nowAnimating = leftSidebarAnimation.isAnimating();
        this.lastLeftSidebarAnimating = nowAnimating;
        if (wasAnimating && !nowAnimating) {
            this.rebuildWorkspaceChildren();
        }

        boolean wasOverviewAnimating = this.lastOverviewDockAnimating;
        boolean nowOverviewAnimating = rightDockAnimation.isAnimating();
        this.lastOverviewDockAnimating = nowOverviewAnimating;
        if (wasOverviewAnimating && !nowOverviewAnimating && this.workspaceView == null) {
            this.rebuildWorkspaceChildren();
        }

        Layout layout = this.createLayout();
        float time = (System.currentTimeMillis() - this.openedAt) / 1000.0F;
        UiRenderer.workspace(context, this.width, this.height, time);

        if (this.sidebarSearchField != null) {
            this.sidebarSearchField.setX(layout.sidebarSearch().x());
            this.sidebarSearchField.setY(layout.sidebarSearch().y());
            this.sidebarSearchField.setWidth(layout.sidebarSearch().width());
            this.sidebarSearchField.setHeight(layout.sidebarSearch().height());
            this.sidebarSearchField.setVisible(layout.sidebar().width() >= 80);
        }

        if (this.searchField != null) {
            this.searchField.setX(layout.search().x());
            this.searchField.setY(layout.search().y());
            this.searchField.setWidth(layout.search().width());
            this.searchField.setHeight(layout.search().height());
        }

        this.drawWorkspaceNavigation(context, layout.sidebar(), layout.sidebarSearch(), mouseX, mouseY);
        this.renderLeftSplitter(context, layout.sidebar(), mouseX, mouseY);

        UiRenderer.panel(context, layout.toolbar().x(), layout.toolbar().y(), layout.toolbar().width(), layout.toolbar().height(), UiTheme.PANEL_SOFT, UiTheme.BORDER_SUBTLE);
        String toolbarTitle = this.workspaceView == null ? "Miniverse Multiplayer Platform" : this.workspaceView.title();
        String toolbarSubtitle = this.workspaceView == null ? "Gamemode studio" : this.workspaceView.subtitle();
        context.drawText(this.textRenderer, Text.literal(toolbarTitle), layout.toolbar().x() + 12, layout.toolbar().y() + 8, UiTheme.TEXT, false);
        context.drawText(this.textRenderer, Text.literal(toolbarSubtitle), layout.toolbar().x() + 12, layout.toolbar().y() + 20, UiTheme.TEXT_DIM, false);

        if (this.workspaceView == null) {
            this.renderOverviewDock(context, layout.detail(), mouseX, mouseY);
            this.renderOverviewSplitter(context, layout.detail(), mouseX, mouseY);

            context.drawText(this.textRenderer, Text.literal("Search"), layout.search().x(), layout.search().y() - 11, UiTheme.TEXT_MUTED, false);
        } else {
            this.workspaceView.renderBackground(context, this.textRenderer, layout.content(), mouseX, mouseY, delta);
        }
        super.render(context, mouseX, mouseY, delta);
        if (this.workspaceView == null && this.searchField != null && this.searchField.getText().isEmpty()) {
            context.drawText(this.textRenderer, Text.literal("Filter gamemodes..."), layout.search().x() + 6, layout.search().y() + 6, UiTheme.TEXT_DIM, false);
        }
        if (this.sidebarSearchField != null && this.sidebarSearchField.getText().isEmpty() && layout.sidebar().width() >= 80) {
            context.drawText(this.textRenderer, Text.literal("Search..."), layout.sidebarSearch().x() + 6, layout.sidebarSearch().y() + 6, UiTheme.TEXT_DIM, false);
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
        if (this.workspaceView != null && this.resetButtonBounds(layout.toolbar()).contains(mouseX, mouseY)) {
            context.drawTooltip(this.textRenderer, Text.literal("Reset current workspace to default settings"), mouseX, mouseY);
        }
    }

    private void renderLeftSplitter(DrawContext context, UiLayout.Rect sidebar, int mouseX, int mouseY) {
        boolean isCollapsed = leftSidebarCollapsed || sidebar.width() <= 20;
        int handleWidth = 17; // 20% larger than 14px
        int handleHeight = 44; // 20% larger than 36px
        int handleY = (this.height - handleHeight) / 2;

        if (isCollapsed) {
            int stripX = 0;
            int stripY = 12;
            int stripWidth = 26;
            int stripHeight = this.height - 24;
            this.leftCollapseStripRect = new UiLayout.Rect(stripX, stripY, stripWidth, stripHeight);
            this.leftSplitterHitRect = null;

            int handleX = stripX + (stripWidth - handleWidth) / 2;
            this.leftCollapseHandleRect = new UiLayout.Rect(handleX, handleY, handleWidth, handleHeight);

            boolean isStripHovered = this.leftCollapseStripRect.contains(mouseX, mouseY);
            boolean isHandleHovered = this.leftCollapseHandleRect.contains(mouseX, mouseY);
            boolean active = isStripHovered || isHandleHovered;

            // Full-height colored gutter strip in left padding
            int fill = active ? 0x335294E2 : 0x18304860;
            int border = active ? UiTheme.ACCENT : 0x405294E2;
            context.fill(stripX, stripY, stripX + stripWidth - 2, stripY + stripHeight, fill);
            context.fill(stripX + stripWidth - 2, stripY, stripX + stripWidth - 1, stripY + stripHeight, border);

            // Centered 17x44 pill button
            int pillBg = isHandleHovered ? 0xFA1E2C3D : 0xEE141F2B;
            int pillBorder = isHandleHovered ? UiTheme.ACCENT : (active ? 0xAA5294E2 : UiTheme.BORDER_STRONG);
            UiRenderer.panel(context, handleX, handleY, handleWidth, handleHeight, pillBg, pillBorder);

            String arrow = "\u276F"; // ❯ pointing right to expand
            int arrowColor = isHandleHovered ? UiTheme.ACCENT : (active ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(arrow), handleX + handleWidth / 2, handleY + (handleHeight - 8) / 2, arrowColor);
        } else {
            this.leftCollapseStripRect = null;
            if (this.leftSplitterHitRect == null || this.leftCollapseHandleRect == null) return;

            int splitterLineX = this.leftSplitterHitRect.x() + this.leftSplitterHitRect.width() / 2;
            boolean isSplitterHovered = this.leftSplitterHitRect.contains(mouseX, mouseY);
            boolean isHandleHovered = this.leftCollapseHandleRect.contains(mouseX, mouseY);
            boolean active = isSplitterHovered || isHandleHovered || this.draggingLeftSplitter;

            int lineColor = this.draggingLeftSplitter ? UiTheme.ACCENT : (active ? 0x885294E2 : 0x22FFFFFF);
            context.fill(splitterLineX - 1, 12, splitterLineX, this.height - 12, lineColor);

            int hx = this.leftCollapseHandleRect.x();
            int hy = this.leftCollapseHandleRect.y();
            int hw = this.leftCollapseHandleRect.width();
            int hh = this.leftCollapseHandleRect.height();

            int pillBg = isHandleHovered ? 0xFA1E2C3D : 0xEE141F2B;
            int pillBorder = isHandleHovered ? UiTheme.ACCENT : (active ? 0xAA5294E2 : UiTheme.BORDER_STRONG);
            UiRenderer.panel(context, hx, hy, hw, hh, pillBg, pillBorder);

            String arrow = "\u276E"; // ❮ pointing left to collapse
            int arrowColor = isHandleHovered ? UiTheme.ACCENT : (active ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(arrow), hx + hw / 2, hy + (hh - 8) / 2, arrowColor);
        }
    }

    private void drawWorkspaceNavigation(DrawContext context, UiLayout.Rect sidebar, UiLayout.Rect search, int mouseX, int mouseY) {
        if (sidebar.width() <= 4) {
            return;
        }
        context.enableScissor(sidebar.x(), sidebar.y(), sidebar.x() + sidebar.width(), sidebar.y() + sidebar.height());
        UiRenderer.panel(context, sidebar.x(), sidebar.y(), sidebar.width(), sidebar.height(), UiTheme.SIDEBAR, UiTheme.BORDER_SUBTLE);
        if (sidebar.width() >= 80) {
            context.drawText(this.textRenderer, Text.literal("MINIVERSE"), sidebar.x() + 12, sidebar.y() + 12, UiTheme.TEXT, false);
            context.drawText(this.textRenderer, Text.literal("Workspace"), sidebar.x() + 12, sidebar.y() + 24, UiTheme.TEXT_DIM, false);
        }
        if (this.sidebarSearchField != null && sidebar.width() >= 80) {
            int outline = this.sidebarSearchField.isFocused() ? UiTheme.ACCENT : UiTheme.BORDER_SUBTLE;
            UiRenderer.panel(context, search.x() - 1, search.y() - 1, search.width() + 2, search.height() + 2, UiTheme.SIDEBAR, outline);
        }
        int contentStart = this.sidebarContentStart(sidebar, search);
        int contentHeight = this.sidebarContentHeight();
        int viewportHeight = sidebar.y() + sidebar.height() - contentStart - 6;
        this.sidebarMaxScroll = Math.max(0.0, contentHeight - Math.max(0, viewportHeight));
        this.sidebarScroll = Math.max(0.0, Math.min(this.sidebarScroll, this.sidebarMaxScroll));

        int y = contentStart - (int) Math.round(this.sidebarScroll);
        int clipBottom = sidebar.y() + sidebar.height() - 4;
        context.enableScissor(sidebar.x(), contentStart, sidebar.x() + sidebar.width(), clipBottom);
        for (SidebarSection section : SidebarSection.values()) {
            y = this.drawSidebarSection(context, sidebar, section, y, mouseX, mouseY);
        }
        context.disableScissor();

        if (this.sidebarMaxScroll > 0.0 && sidebar.width() >= 60) {
            this.drawSidebarScrollbar(context, sidebar, search, mouseX, mouseY);
        }
        context.disableScissor();
    }

    private int sidebarTrackTop(UiLayout.Rect sidebar, UiLayout.Rect search) {
        return this.sidebarContentStart(sidebar, search);
    }

    private int sidebarTrackBottom(UiLayout.Rect sidebar) {
        return sidebar.y() + sidebar.height() - 6;
    }

    private int sidebarTrackHeight(UiLayout.Rect sidebar, UiLayout.Rect search) {
        return Math.max(1, this.sidebarTrackBottom(sidebar) - this.sidebarTrackTop(sidebar, search));
    }

    private int sidebarThumbHeight(UiLayout.Rect sidebar, UiLayout.Rect search) {
        int trackHeight = this.sidebarTrackHeight(sidebar, search);
        int contentHeight = this.sidebarContentHeight();
        int viewportHeight = sidebar.y() + sidebar.height() - this.sidebarContentStart(sidebar, search) - 6;
        if (contentHeight <= 0) return trackHeight;
        double ratio = (double) viewportHeight / (double) contentHeight;
        return Math.max(22, Math.min(trackHeight, (int) Math.round(trackHeight * ratio)));
    }

    private int sidebarThumbY(UiLayout.Rect sidebar, UiLayout.Rect search) {
        int trackTop = this.sidebarTrackTop(sidebar, search);
        int trackHeight = this.sidebarTrackHeight(sidebar, search);
        int thumbH = this.sidebarThumbHeight(sidebar, search);
        int scrollable = trackHeight - thumbH;
        if (scrollable <= 0 || this.sidebarMaxScroll <= 0.0) {
            return trackTop;
        }
        double fraction = this.sidebarScroll / this.sidebarMaxScroll;
        return trackTop + (int) Math.round(scrollable * fraction);
    }

    private void drawSidebarScrollbar(DrawContext context, UiLayout.Rect sidebar, UiLayout.Rect search, int mouseX, int mouseY) {
        int barX = sidebar.x() + SIDEBAR_SCROLLBAR_INSET;
        int barW = SIDEBAR_SCROLLBAR_WIDTH;
        int trackTop = this.sidebarTrackTop(sidebar, search);
        int trackHeight = this.sidebarTrackHeight(sidebar, search);
        int thumbH = this.sidebarThumbHeight(sidebar, search);
        int thumbY = this.sidebarThumbY(sidebar, search);

        // Subtle dark track channel in sidebar gutter
        context.fill(barX, trackTop, barX + barW, trackTop + trackHeight, 0x33000000);

        boolean isHovered = mouseX >= sidebar.x() && mouseX < sidebar.x() + SIDEBAR_INSET && mouseY >= trackTop && mouseY <= trackTop + trackHeight;
        boolean isThumbHovered = mouseX >= sidebar.x() && mouseX < sidebar.x() + SIDEBAR_INSET && mouseY >= thumbY && mouseY <= thumbY + thumbH;

        int thumbColor;
        int drawX = barX;
        int drawW = barW;
        if (this.draggingSidebarScrollbar) {
            thumbColor = UiTheme.ACCENT;
            drawX = barX - 1;
            drawW = barW + 2;
        } else if (isThumbHovered) {
            thumbColor = 0xDDFFC857; // Bright golden accent on hover
            drawX = barX - 1;
            drawW = barW + 2;
        } else if (isHovered) {
            thumbColor = 0xBBFFC857; // Soft golden accent on track hover
        } else {
            thumbColor = 0x758DA4B7; // Refined theme border slate
        }

        context.fill(drawX, thumbY, drawX + drawW, thumbY + thumbH, thumbColor);
    }

    private int sidebarContentHeight() {
        int height = 0;
        for (SidebarSection section : SidebarSection.values()) {
            height += SIDEBAR_SECTION_HEIGHT;
            UiAnimation.Value anim = this.sidebarAnimations.get(section);
            float progress = anim != null ? anim.get() : (this.expandedSections.contains(section) ? 1.0F : 0.0F);
            int sectionRows = this.rowsFor(section).stream().mapToInt(this::rowHeight).sum();
            height += Math.round(sectionRows * progress);
            height += SIDEBAR_ROW_GAP;
        }
        return height;
    }

    private int sidebarContentStart(UiLayout.Rect sidebar, UiLayout.Rect search) {
        if (this.sidebarSearchField != null) {
            return search.y() + search.height() + 10;
        }
        return sidebar.y() + SIDEBAR_HEADER_HEIGHT + 8;
    }

    private int drawSidebarSection(DrawContext context, UiLayout.Rect sidebar, SidebarSection section, int y, int mouseX, int mouseY) {
        UiAnimation.Value animation = this.sidebarAnimations.get(section);
        animation.animateTo(this.expandedSections.contains(section) ? 1.0F : 0.0F, UiTheme.TRANSITION_MS, UiAnimation::easeInOutQuad);
        float progress = animation.get();
        boolean hovered = this.navRowContains(sidebar, y, SIDEBAR_SECTION_HEIGHT, mouseX, mouseY);
        this.drawSectionHeader(context, sidebar, y, section.label, hovered, progress);
        y += SIDEBAR_SECTION_HEIGHT;

        List<SidebarRow> rows = this.rowsFor(section);
        int totalHeight = rows.stream().mapToInt(this::rowHeight).sum();
        int visibleHeight = Math.round(totalHeight * progress);
        RailSpan railSpan = this.activeModuleRail(section, rows, y);
        if (visibleHeight > 0) {
            if (railSpan != null) {
                int railX = sidebar.x() + SIDEBAR_INSET + MODULE_RAIL_INSET;
                int railTop = Math.max(railSpan.startY(), y);
                int railBottom = Math.min(railSpan.endY(), y + visibleHeight);
                if (railBottom > railTop) {
                    context.fill(railX, railTop, railX + MODULE_RAIL_WIDTH, railBottom, UiAnimation.alpha(railSpan.accent(), 0.9F));
                }
            }
            context.enableScissor(sidebar.x(), y, sidebar.x() + sidebar.width(), y + visibleHeight);
            int rowY = y;
            int used = 0;
            for (SidebarRow row : rows) {
                int height = this.rowHeight(row);
                if (used + height > visibleHeight) {
                    break;
                }
                this.drawSidebarRow(context, sidebar, rowY, row, mouseX, mouseY);
                rowY += height;
                used += height;
            }
            context.disableScissor();
        }
        return y + visibleHeight + SIDEBAR_ROW_GAP;
    }

    private void drawSidebarRow(DrawContext context, UiLayout.Rect sidebar, int y, SidebarRow row, int mouseX, int mouseY) {
        int height = this.rowHeight(row);
        boolean hovered = this.navRowContains(sidebar, y, height, mouseX, mouseY);
        switch (row.type) {
            case GROUP_HEADER -> this.drawGroupHeader(context, sidebar, y, row, hovered);
            case DIVIDER -> {
                int inset = this.moduleBlockInset();
                int x = sidebar.x() + SIDEBAR_INSET + inset;
                int width = sidebar.width() - SIDEBAR_INSET * 2 - inset;
                UiRenderer.divider(context, x, y + height / 2, width);
            }
            case ITEM -> this.drawNavItem(context, sidebar, y, row.icon, row.label, row.selected.getAsBoolean(), hovered, row.indent, row.accent, false);
        }
    }

    private void drawSectionHeader(DrawContext context, UiLayout.Rect sidebar, int y, String label, boolean hovered, float progress) {
        int x = sidebar.x() + SIDEBAR_INSET;
        int width = sidebar.width() - SIDEBAR_INSET * 2;
        int height = SIDEBAR_SECTION_HEIGHT;
        int fill = hovered ? 0x2AFFFFFF : 0x00000000;
        int border = hovered ? UiTheme.BORDER_STRONG : 0x00000000;
        context.fill(x, y + 1, x + width, y + height - 1, fill);
        if (border != 0) {
            context.fill(x, y, x + width, y + 1, border);
            context.fill(x, y + height - 1, x + width, y + height, border);
        }
        int iconX = x + 10;
        int iconY = y + (height - ARROW_RENDER_SIZE) / 2;
        this.drawDropdownArrow(context, iconX, iconY, progress);
        int textX = x + 28;
        int maxTextWidth = Math.max(20, x + width - textX - 6);
        context.drawText(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(label, maxTextWidth)), textX, y + (height - 9) / 2, UiTheme.TEXT_MUTED, false);
    }

    private void drawGroupHeader(DrawContext context, UiLayout.Rect sidebar, int y, SidebarRow row, boolean hovered) {
        int inset = this.moduleBlockInset();
        int x = sidebar.x() + SIDEBAR_INSET + inset;
        int width = sidebar.width() - SIDEBAR_INSET * 2 - inset;
        int fill = hovered ? 0x1E2D3B4A : 0x14222C37;
        int borderColor = UiTheme.BORDER_SUBTLE;
        context.fill(x, y, x + width, y + SIDEBAR_GROUP_HEIGHT, fill);
        context.fill(x, y, x + width, y + 1, borderColor);
        context.fill(x, y + SIDEBAR_GROUP_HEIGHT - 1, x + width, y + SIDEBAR_GROUP_HEIGHT, borderColor);
        boolean expanded = this.isGroupExpanded(row.groupId);
        UiAnimation.Value animation = this.moduleGroupAnimations.computeIfAbsent(row.groupId, ignored -> new UiAnimation.Value(expanded ? 1.0F : 0.0F));
        animation.animateTo(expanded ? 1.0F : 0.0F, 160, UiAnimation::easeInOutQuad);
        int iconX = sidebar.x() + SIDEBAR_INSET + MODULE_ITEM_INDENT + 10;
        int iconY = y + (SIDEBAR_GROUP_HEIGHT - ARROW_RENDER_SIZE) / 2;
        this.drawDropdownArrow(context, iconX, iconY, animation.get());
        int textX = sidebar.x() + SIDEBAR_INSET + MODULE_ITEM_INDENT + 28;
        context.drawText(this.textRenderer, Text.literal(row.label), textX, y + 6, UiTheme.TEXT_MUTED, false);
    }

    private void drawDropdownArrow(DrawContext context, int x, int y, float progress) {
        float angle = UiAnimation.lerp(0.0F, 90.0F, progress);
        context.getMatrices().push();
        context.getMatrices().translate(x + ARROW_RENDER_SIZE / 2.0F, y + ARROW_RENDER_SIZE / 2.0F, 0.0F);
        context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        context.getMatrices().translate(-ARROW_RENDER_SIZE / 2.0F, -ARROW_RENDER_SIZE / 2.0F, 0.0F);
        context.drawTexture(DROPDOWN_ARROW_TEXTURE, 0, 0, 0.0F, 0.0F, ARROW_RENDER_SIZE, ARROW_RENDER_SIZE, ARROW_TEXTURE_SIZE, ARROW_TEXTURE_SIZE);
        context.getMatrices().pop();
    }

    private int moduleBlockInset() {
        if (this.workspaceView instanceof GamemodeWorkspaceView.ModuleProvider) {
            return MODULE_RAIL_INSET + MODULE_RAIL_WIDTH + MODULE_RAIL_GAP;
        }
        return MODULE_RAIL_INSET;
    }

    private void drawNavItem(DrawContext context, UiLayout.Rect sidebar, int y, String icon, String label, boolean selected, boolean hovered, int indent, int accent, boolean sectionHeader) {
        int x = sidebar.x() + SIDEBAR_INSET + indent;
        int width = sidebar.width() - SIDEBAR_INSET * 2 - indent;
        int height = sectionHeader ? SIDEBAR_SECTION_HEIGHT : SIDEBAR_ROW_HEIGHT;
        int fill = selected ? 0x5532455C : hovered ? 0x2AFFFFFF : 0x00000000;
        int border = selected ? accent : hovered ? UiTheme.BORDER_STRONG : 0x00000000;
        context.fill(x, y + 1, x + width, y + height - 1, fill);
        if (border != 0) {
            context.fill(x, y, x + width, y + 1, border);
            context.fill(x, y + height - 1, x + width, y + height, border);
        }
        if (selected) {
            context.fill(x, y + 1, x + 4, y + height - 1, accent);
        }
        int iconX = x + 10;
        int iconY = y + (height - 8) / 2;
        context.getMatrices().push();
        context.getMatrices().translate(iconX, iconY, 0);
        context.getMatrices().scale(1.15F, 1.15F, 1.0F);
        context.drawText(this.textRenderer, Text.literal(icon), 0, 0, selected ? accent : UiTheme.ACCENT, false);
        context.getMatrices().pop();
        int textX = x + 28;
        int maxTextWidth = Math.max(20, x + width - textX - 6);
        int textColor = selected ? UiTheme.TEXT : UiTheme.TEXT_MUTED;
        context.drawText(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(label, maxTextWidth)), textX, y + (height - 9) / 2, textColor, false);
    }

    private boolean navRowContains(UiLayout.Rect sidebar, int y, int height, double mouseX, double mouseY) {
        return mouseX >= sidebar.x() + SIDEBAR_INSET && mouseX <= sidebar.x() + sidebar.width() - SIDEBAR_INSET && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 3) {
            this.goBack();
            return true;
        }
        if (button == 4) {
            this.goForward();
            return true;
        }
        Layout layout = this.createLayout();
        boolean isLeftCollapsed = leftSidebarCollapsed || layout.sidebar().width() <= 20;
        if (button == 0 && isLeftCollapsed && this.leftCollapseStripRect != null && this.leftCollapseStripRect.contains(mouseX, mouseY)) {
            leftSidebarCollapsed = false;
            leftSidebarAnimation.animateTo(1.0F, 220, UiAnimation::easeInOutQuad);
            return true;
        }
        if (button == 0 && this.leftCollapseHandleRect != null && this.leftCollapseHandleRect.contains(mouseX, mouseY)) {
            leftSidebarCollapsed = !leftSidebarCollapsed;
            leftSidebarAnimation.animateTo(leftSidebarCollapsed ? 0.0F : 1.0F, 220, UiAnimation::easeInOutQuad);
            return true;
        }
        if (button == 0 && this.leftSplitterHitRect != null && this.leftSplitterHitRect.contains(mouseX, mouseY) && !leftSidebarCollapsed) {
            this.draggingLeftSplitter = true;
            this.leftSplitterDragStartMouseX = mouseX;
            this.leftSplitterDragStartWidth = leftSidebarTargetWidth;
            return true;
        }
        if (this.workspaceView == null) {
            boolean isOverviewCollapsed = rightDockCollapsed || layout.detail().width() <= 20;
            if (button == 0 && isOverviewCollapsed && this.overviewCollapseStripRect != null && this.overviewCollapseStripRect.contains(mouseX, mouseY)) {
                rightDockCollapsed = false;
                rightDockAnimation.animateTo(1.0F, 220, UiAnimation::easeInOutQuad);
                return true;
            }
            if (button == 0 && this.overviewCollapseHandleRect != null && this.overviewCollapseHandleRect.contains(mouseX, mouseY)) {
                rightDockCollapsed = !rightDockCollapsed;
                rightDockAnimation.animateTo(rightDockCollapsed ? 0.0F : 1.0F, 220, UiAnimation::easeInOutQuad);
                return true;
            }
            if (button == 0 && this.overviewSplitterHitRect != null && this.overviewSplitterHitRect.contains(mouseX, mouseY) && !rightDockCollapsed) {
                this.draggingOverviewSplitter = true;
                this.overviewSplitterDragStartMouseX = mouseX;
                this.overviewSplitterDragStartWidth = rightDockTargetWidth;
                return true;
            }
        }
        if (button == 0 && this.sidebarMaxScroll > 0.0) {
            UiLayout.Rect sidebar = layout.sidebar();
            UiLayout.Rect search = layout.sidebarSearch();
            int trackTop = this.sidebarTrackTop(sidebar, search);
            int trackBottom = this.sidebarTrackBottom(sidebar);
            if (mouseX >= sidebar.x() && mouseX < sidebar.x() + SIDEBAR_INSET && mouseY >= trackTop && mouseY <= trackBottom) {
                int thumbY = this.sidebarThumbY(sidebar, search);
                int thumbH = this.sidebarThumbHeight(sidebar, search);
                if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    this.draggingSidebarScrollbar = true;
                    this.sidebarDragClickOffsetY = mouseY - thumbY;
                } else {
                    int trackHeight = this.sidebarTrackHeight(sidebar, search);
                    int scrollable = trackHeight - thumbH;
                    if (scrollable > 0) {
                        double targetThumbY = mouseY - (thumbH / 2.0);
                        double fraction = (targetThumbY - trackTop) / (double) scrollable;
                        this.sidebarScroll = Math.max(0.0, Math.min(fraction * this.sidebarMaxScroll, this.sidebarMaxScroll));
                    }
                    this.draggingSidebarScrollbar = true;
                    this.sidebarDragClickOffsetY = thumbH / 2.0;
                }
                return true;
            }
        }
        if (button == 0 && this.handleWorkspaceNavigationClick(layout.sidebar(), layout.sidebarSearch(), mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && this.workspaceView != null && this.resetButtonBounds(layout.toolbar()).contains(mouseX, mouseY)) {
            this.resetCurrentWorkspace();
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

    private boolean handleWorkspaceNavigationClick(UiLayout.Rect sidebar, UiLayout.Rect search, double mouseX, double mouseY) {
        if (sidebar.width() <= 20) {
            return false;
        }
        if (mouseX < sidebar.x() || mouseX > sidebar.x() + sidebar.width()) {
            return false;
        }
        int contentStart = this.sidebarContentStart(sidebar, search);
        if (mouseY < contentStart || mouseY > sidebar.y() + sidebar.height()) {
            return false;
        }
        double adjustedMouseY = mouseY + this.sidebarScroll;
        int y = contentStart;
        for (SidebarSection section : SidebarSection.values()) {
            if (this.navRowContains(sidebar, y, SIDEBAR_SECTION_HEIGHT, mouseX, adjustedMouseY)) {
                this.toggleSection(section);
                return true;
            }
            y += SIDEBAR_SECTION_HEIGHT;
            List<SidebarRow> rows = this.rowsFor(section);
            int totalHeight = rows.stream().mapToInt(this::rowHeight).sum();
            int visibleHeight = Math.round(totalHeight * this.sidebarAnimations.get(section).get());
            if (visibleHeight > 0 && adjustedMouseY >= y && adjustedMouseY <= y + visibleHeight) {
                int rowY = y;
                int used = 0;
                for (SidebarRow row : rows) {
                    int height = this.rowHeight(row);
                    if (used + height > visibleHeight) {
                        break;
                    }
                    if (adjustedMouseY >= rowY && adjustedMouseY <= rowY + height) {
                        if (row.action != null) {
                            row.action.run();
                            return true;
                        }
                        return false;
                    }
                    rowY += height;
                    used += height;
                }
            }
            y += visibleHeight + SIDEBAR_ROW_GAP;
        }
        return false;
    }

    private void toggleSection(SidebarSection section) {
        if (this.expandedSections.contains(section)) {
            this.expandedSections.remove(section);
        } else {
            this.expandedSections.add(section);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = this.createLayout();
        UiLayout.Rect sidebar = layout.sidebar();
        UiLayout.Rect search = layout.sidebarSearch();
        int contentStart = this.sidebarContentStart(sidebar, search);
        if (mouseX >= sidebar.x() && mouseX <= sidebar.x() + sidebar.width()
            && mouseY >= contentStart && mouseY <= sidebar.y() + sidebar.height()
            && this.sidebarMaxScroll > 0.0) {
            this.sidebarScroll = Math.max(0.0, Math.min(this.sidebarScroll - verticalAmount * 18.0, this.sidebarMaxScroll));
            return true;
        }
        if (this.workspaceView != null && this.workspaceView.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && this.draggingLeftSplitter) {
            int newW = (int) (this.leftSplitterDragStartWidth + (mouseX - this.leftSplitterDragStartMouseX));
            leftSidebarTargetWidth = Math.max(MIN_LEFT_SIDEBAR_WIDTH, Math.min(MAX_LEFT_SIDEBAR_WIDTH, newW));
            leftSidebarAnimation.set(1.0F);
            leftSidebarCollapsed = false;
            return true;
        }
        if (button == 0 && this.workspaceView == null && this.draggingOverviewSplitter) {
            int newW = (int) (this.overviewSplitterDragStartWidth - (mouseX - this.overviewSplitterDragStartMouseX));
            rightDockTargetWidth = Math.max(dev.frost.miniverse.client.gui.workspace.framework.StandardWorkspaceLayout.MIN_DOCK_WIDTH, Math.min(dev.frost.miniverse.client.gui.workspace.framework.StandardWorkspaceLayout.MAX_DOCK_WIDTH, newW));
            rightDockAnimation.set(1.0F);
            rightDockCollapsed = false;
            return true;
        }
        if (button == 0 && this.draggingSidebarScrollbar && this.sidebarMaxScroll > 0.0) {
            Layout layout = this.createLayout();
            UiLayout.Rect sidebar = layout.sidebar();
            UiLayout.Rect search = layout.sidebarSearch();
            int trackTop = this.sidebarTrackTop(sidebar, search);
            int trackHeight = this.sidebarTrackHeight(sidebar, search);
            int thumbH = this.sidebarThumbHeight(sidebar, search);
            int scrollable = trackHeight - thumbH;
            if (scrollable > 0) {
                double targetThumbY = mouseY - this.sidebarDragClickOffsetY;
                double fraction = (targetThumbY - trackTop) / (double) scrollable;
                this.sidebarScroll = Math.max(0.0, Math.min(fraction * this.sidebarMaxScroll, this.sidebarMaxScroll));
            }
            return true;
        }
        if (this.workspaceView != null && this.workspaceView.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingLeftSplitter) {
            this.draggingLeftSplitter = false;
            this.rebuildWorkspaceChildren();
            return true;
        }
        if (button == 0 && this.draggingOverviewSplitter) {
            this.draggingOverviewSplitter = false;
            this.rebuildWorkspaceChildren();
            return true;
        }
        if (button == 0 && this.draggingSidebarScrollbar) {
            this.draggingSidebarScrollbar = false;
            return true;
        }
        if (this.workspaceView != null && this.workspaceView.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.workspaceView != null && this.workspaceView.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (this.workspaceView != null && this.workspaceView.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
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

    private String sidebarQuery() {
        if (this.sidebarSearchField == null) {
            return "";
        }
        return this.sidebarSearchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private List<SidebarRow> rowsFor(SidebarSection section) {
        List<SidebarRow> rows = new ArrayList<>();
        String query = this.sidebarQuery();
        switch (section) {
            case GAMEMODES -> {
                if (SessionSnapshotData.mapEditor()) {
                    for (SessionSnapshotData.EditorExtension extension : SessionSnapshotData.editorExtensions()) {
                        if (!query.isEmpty()) {
                            String name = extension.displayName().toLowerCase(Locale.ROOT);
                            String id = extension.gameId().toLowerCase(Locale.ROOT);
                            if (!name.contains(query) && !id.contains(query)) {
                                continue;
                            }
                        }
                        int accent = this.accentFor(extension.gameId(), rows.size());
                        rows.add(SidebarRow.item(new SidebarChild(">", extension.displayName(), () -> this.openMapEditorGamemode(extension.gameId()), () -> (this.workspaceView instanceof MapEditorWorkspaceView editor && editor.gameSelected(extension.gameId())), 8, accent)));
                        if (this.workspaceView instanceof MapEditorWorkspaceView editor && editor.gameSelected(extension.gameId())) {
                            for (SessionSnapshotData.EditorMarkerDefinition marker : extension.markers()) {
                                rows.add(SidebarRow.item(new SidebarChild("+", marker.displayName(), () -> this.openWorkspaceView(MapEditorWorkspaceView.forMarker(this.mapEditorState, this::requestSnapshot, extension.gameId(), marker.key())), () -> editor.markerSelected(extension.gameId(), marker.key()), MODULE_ITEM_INDENT, accent)));
                            }
                        }
                    }
                    break;
                }
                rows.add(SidebarRow.item(new SidebarChild("*", "All gamemodes", this::openSelectorWorkspace, () -> this.workspaceView == null, 8, UiTheme.ACCENT)));
                for (MinigameEntry entry : this.minigameEntries) {
                    if (!query.isEmpty()) {
                        String name = entry.name().toLowerCase(Locale.ROOT);
                        String id = entry.id().toLowerCase(Locale.ROOT);
                        String description = entry.description().toLowerCase(Locale.ROOT);
                        if (!name.contains(query) && !id.contains(query) && !description.contains(query)) {
                            continue;
                        }
                    }
                    int accent = this.accentFor(entry.id(), rows.size());
                    rows.add(SidebarRow.item(new SidebarChild(entry.icon(), entry.name(), () -> this.openGamemode(entry), () -> this.isCurrentGamemodeWorkspace(entry.id()), 8, accent)));
                    if (this.isCurrentGamemodeWorkspace(entry.id()) && this.workspaceView instanceof GamemodeWorkspaceView.ModuleProvider moduleProvider) {
                        Map<String, List<GamemodeWorkspaceView.WorkspaceModule>> grouped = new java.util.LinkedHashMap<>();
                        for (GamemodeWorkspaceView.WorkspaceModule module : moduleProvider.modules()) {
                            if (!query.isEmpty()) {
                                String label = module.label().toLowerCase(Locale.ROOT);
                                String id = module.id().toLowerCase(Locale.ROOT);
                                if (!label.contains(query) && !id.contains(query)) {
                                    continue;
                                }
                            }
                            grouped.computeIfAbsent(module.group(), ignored -> new ArrayList<>()).add(module);
                        }
                        for (Map.Entry<String, List<GamemodeWorkspaceView.WorkspaceModule>> group : grouped.entrySet()) {
                            String groupKey = entry.id() + ":" + group.getKey();
                            rows.add(SidebarRow.group(groupKey, group.getKey(), () -> this.toggleModuleGroup(groupKey)));
                            boolean expanded = query.isEmpty() ? this.isGroupExpanded(groupKey) : true;
                            if (expanded) {
                                for (GamemodeWorkspaceView.WorkspaceModule module : group.getValue()) {
                                    rows.add(SidebarRow.item(new SidebarChild(module.icon(), module.label(), () -> this.openWorkspaceModule(module), () -> moduleProvider.activeModuleId().equalsIgnoreCase(module.id()), MODULE_ITEM_INDENT, accent)));
                                }
                            }
                            if (query.isEmpty()) {
                                rows.add(SidebarRow.divider());
                            }
                        }
                    }
                }
            }
            case SESSION -> {
                rows.add(SidebarRow.item(new SidebarChild("#", "Live sessions", this::openSessionsWorkspace, () -> this.isAdminMode(AdminWorkspaceView.Mode.SESSIONS), 8, UiTheme.ACCENT_GREEN)));
                rows.add(SidebarRow.item(new SidebarChild(":", "History", this::openHistoryWorkspace, () -> this.isAdminMode(AdminWorkspaceView.Mode.HISTORY), 8, UiTheme.ACCENT)));
            }
            case SERVER -> rows.add(SidebarRow.item(new SidebarChild("!", "Session server properties", this::openServerWorkspace, () -> this.isAdminMode(AdminWorkspaceView.Mode.SERVER), 8, UiTheme.ACCENT_RED)));
            case MAPS -> {
                if (SessionSnapshotData.mapEditor()) {
                    rows.add(SidebarRow.item(new SidebarChild("E", "Map Editor", this::openMapEditorWorkspace, () -> this.workspaceView instanceof MapEditorWorkspaceView editor && editor.isOverviewSelected(), 8, UiTheme.ACCENT_GREEN)));
                    rows.add(SidebarRow.item(new SidebarChild("G", "General", () -> this.openWorkspaceView(MapEditorWorkspaceView.forGeneral(this.mapEditorState, this::requestSnapshot)), () -> this.workspaceView instanceof MapEditorWorkspaceView editor && editor.generalSelected(), MODULE_ITEM_INDENT, UiTheme.ACCENT_BLUE)));
                } else {
                    rows.add(SidebarRow.item(new SidebarChild("M", "Maps", this::openMapsWorkspace, () -> this.workspaceView instanceof MapManagementWorkspaceView, 8, UiTheme.ACCENT_GREEN)));
                }
            }
            case APPEARANCE -> rows.add(SidebarRow.item(new SidebarChild("~", "Workspace", this::openAppearance, () -> this.workspaceView instanceof AppearanceWorkspaceView, 8, UiTheme.ACCENT_BLUE)));
        }
        if (query.isEmpty()) {
            return rows;
        }
        return this.filterRowsByQuery(rows, query);
    }

    private List<SidebarRow> filterRowsByQuery(List<SidebarRow> rows, String query) {
        List<SidebarRow> filtered = new ArrayList<>();
        SidebarRow pendingGroup = null;
        boolean includeGroup = false;
        boolean pendingAdded = false;
        for (SidebarRow row : rows) {
            if (row.type == SidebarRowType.GROUP_HEADER) {
                pendingGroup = row;
                pendingAdded = false;
                includeGroup = row.label.toLowerCase(Locale.ROOT).contains(query);
                if (includeGroup) {
                    filtered.add(row);
                    pendingAdded = true;
                }
                continue;
            }
            if (row.type == SidebarRowType.DIVIDER) {
                continue;
            }
            if (includeGroup) {
                filtered.add(row);
                continue;
            }
            if (row.label.toLowerCase(Locale.ROOT).contains(query)) {
                if (pendingGroup != null && !pendingAdded) {
                    filtered.add(pendingGroup);
                    pendingAdded = true;
                }
                filtered.add(row);
            }
        }
        return filtered;
    }

    private int rowHeight(SidebarRow row) {
        return switch (row.type) {
            case GROUP_HEADER -> SIDEBAR_GROUP_HEIGHT;
            case DIVIDER -> 8;
            case ITEM -> SIDEBAR_ROW_HEIGHT;
        };
    }

    private boolean isGroupExpanded(String groupId) {
        return !this.collapsedModuleGroups.contains(groupId);
    }

    private void toggleModuleGroup(String groupId) {
        if (this.collapsedModuleGroups.contains(groupId)) {
            this.collapsedModuleGroups.remove(groupId);
        } else {
            this.collapsedModuleGroups.add(groupId);
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
        return this.workspaceView instanceof GamemodeWorkspaceView gamemodeView && gamemodeView.gameId().equals(id);
    }

    private void openWorkspaceModule(GamemodeWorkspaceView.WorkspaceModule module) {
        if (this.workspaceView instanceof GamemodeWorkspaceView.ModuleProvider moduleProvider) {
            moduleProvider.setActiveModule(module.id());
            this.rebuildWorkspaceChildren();
        }
    }

    private void renderToolbarClose(DrawContext context, UiLayout.Rect toolbar, int mouseX, int mouseY) {
        if (this.workspaceView != null) {
            UiLayout.Rect resetBounds = this.resetButtonBounds(toolbar);
            boolean resetHovered = resetBounds.contains(mouseX, mouseY);
            int resetFill = resetHovered ? 0x33EF4444 : UiTheme.PANEL;
            int resetBorder = resetHovered ? 0xFFEF4444 : UiTheme.BORDER_SUBTLE;
            UiRenderer.panel(context, resetBounds.x(), resetBounds.y(), resetBounds.width(), resetBounds.height(), resetFill, resetBorder);
            int resetTextX = resetBounds.x() + (resetBounds.width() - this.textRenderer.getWidth("↺ Reset")) / 2;
            int resetTextY = resetBounds.y() + (resetBounds.height() - 9) / 2;
            int resetTextColor = resetHovered ? 0xFFFCA5A5 : UiTheme.TEXT_MUTED;
            context.drawText(this.textRenderer, Text.literal("↺ Reset"), resetTextX, resetTextY, resetTextColor, false);
        }

        UiLayout.Rect bounds = this.closeButtonBounds(toolbar);
        boolean hovered = bounds.contains(mouseX, mouseY);
        int fill = hovered ? UiTheme.PANEL_RAISED : UiTheme.PANEL;
        int border = hovered ? UiTheme.BORDER_STRONG : UiTheme.BORDER_SUBTLE;
        UiRenderer.panel(context, bounds.x(), bounds.y(), bounds.width(), bounds.height(), fill, border);
        int labelX = bounds.x() + (bounds.width() - this.textRenderer.getWidth("Close")) / 2;
        int labelY = bounds.y() + (bounds.height() - 9) / 2;
        context.drawText(this.textRenderer, Text.literal("Close"), labelX, labelY, UiTheme.TEXT, false);
    }

    private void renderOverviewDock(DrawContext context, UiLayout.Rect dock, int mouseX, int mouseY) {
        if (dock.width() <= 4) return;

        context.enableScissor(dock.x(), dock.y(), dock.x() + dock.width(), dock.y() + dock.height());
        try {
            // Main solid background panel
            UiRenderer.panel(context, dock.x(), dock.y(), dock.width(), dock.height(), UiTheme.PANEL_SOFT, UiTheme.BORDER_SUBTLE);

            // Header bar
            int headerH = 42;
            context.fill(dock.x() + 1, dock.y() + 1, dock.x() + dock.width() - 1, dock.y() + headerH, 0x601B2634);
            context.fill(dock.x() + 1, dock.y() + headerH - 1, dock.x() + dock.width() - 1, dock.y() + headerH, UiTheme.BORDER_SUBTLE);

            context.drawText(this.textRenderer, Text.literal("✦ Platform Overview"), dock.x() + 14, dock.y() + 11, UiTheme.ACCENT_BLUE, false);
            context.drawText(this.textRenderer, Text.literal("Network state & stats"), dock.x() + 14, dock.y() + 24, UiTheme.TEXT_DIM, false);

            int curY = dock.y() + headerH + 12;
            int cardW = Math.max(20, dock.width() - 24);

            // Stat Card 1: Gamemodes
            curY = this.drawOverviewStatCard(context, dock.x() + 12, curY, cardW, "🎮  Gamemodes", String.valueOf(SessionSnapshotData.games().size()), "Available catalog modes", UiTheme.ACCENT);
            curY += 8;

            // Stat Card 2: Active Sessions
            curY = this.drawOverviewStatCard(context, dock.x() + 12, curY, cardW, "⚡  Sessions", String.valueOf(SessionSnapshotData.sessions().size()), "Active or queued matches", UiTheme.ACCENT_BLUE);
            curY += 8;

            // Stat Card 3: Online Players
            curY = this.drawOverviewStatCard(context, dock.x() + 12, curY, cardW, "👥  Players", String.valueOf(SessionSnapshotData.roster().size()), "Connected to network", UiTheme.ACCENT_GREEN);
            curY += 14;

            // Tip / Quick Info Card
            int tipH = 76;
            if (curY + tipH <= dock.y() + dock.height() - 10) {
                UiRenderer.panel(context, dock.x() + 12, curY, cardW, tipH, 0x40141F2B, UiTheme.BORDER_SUBTLE);
                context.fill(dock.x() + 13, curY + 1, dock.x() + 15, curY + tipH - 1, UiTheme.ACCENT);
                context.drawText(this.textRenderer, Text.literal("💡 Quick Guide"), dock.x() + 22, curY + 8, UiTheme.TEXT, false);
                int tipTextY = curY + 24;
                for (String line : this.wrapText("Select any gamemode card to open its workspace, configure rules & teams, and launch a match.", cardW - 20, 3)) {
                    context.drawText(this.textRenderer, Text.literal(line), dock.x() + 22, tipTextY, UiTheme.TEXT_MUTED, false);
                    tipTextY += this.textRenderer.fontHeight + 2;
                }
            }
        } finally {
            context.disableScissor();
        }
    }

    private int drawOverviewStatCard(DrawContext context, int x, int y, int width, String title, String value, String desc, int valueColor) {
        int cardH = 46;
        UiRenderer.panel(context, x, y, width, cardH, 0x40172230, UiTheme.BORDER_SUBTLE);
        context.drawText(this.textRenderer, Text.literal(title), x + 10, y + 8, UiTheme.TEXT, false);
        context.drawText(this.textRenderer, Text.literal(value), x + width - 12 - this.textRenderer.getWidth(value), y + 8, valueColor, false);
        context.drawText(this.textRenderer, Text.literal(desc), x + 10, y + 24, UiTheme.TEXT_DIM, false);
        return y + cardH;
    }

    private void renderOverviewSplitter(DrawContext context, UiLayout.Rect dock, int mouseX, int mouseY) {
        boolean isClosed = rightDockCollapsed || dock.width() <= 20;
        int handleWidth = 17;
        int handleHeight = 44;

        if (isClosed) {
            if (this.overviewCollapseStripRect == null || this.overviewCollapseHandleRect == null) return;
            int stripX = this.overviewCollapseStripRect.x();
            int stripY = this.overviewCollapseStripRect.y();
            int stripWidth = this.overviewCollapseStripRect.width();
            int stripHeight = this.overviewCollapseStripRect.height();
            int handleX = this.overviewCollapseHandleRect.x();
            int handleY = this.overviewCollapseHandleRect.y();

            boolean isStripHovered = this.overviewCollapseStripRect.contains(mouseX, mouseY);
            boolean isHandleHovered = this.overviewCollapseHandleRect.contains(mouseX, mouseY);
            boolean active = isStripHovered || isHandleHovered;

            // Full-height colored gutter strip along physical right edge
            int fill = active ? 0x335294E2 : 0x18304860;
            int border = active ? UiTheme.ACCENT : 0x405294E2;
            context.fill(stripX + 1, stripY, this.width, stripY + stripHeight, fill);
            context.fill(stripX, stripY, stripX + 1, stripY + stripHeight, border);

            // Centered 17x44 pill button
            int pillBg = isHandleHovered ? 0xFA1E2C3D : 0xEE141F2B;
            int pillBorder = isHandleHovered ? UiTheme.ACCENT : (active ? 0xAA5294E2 : UiTheme.BORDER_STRONG);
            UiRenderer.panel(context, handleX, handleY, handleWidth, handleHeight, pillBg, pillBorder);

            String arrow = "\u276E"; // ❮ pointing left to expand
            int arrowColor = isHandleHovered ? UiTheme.ACCENT : (active ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(arrow), handleX + handleWidth / 2, handleY + (handleHeight - 8) / 2, arrowColor);
        } else {
            if (this.overviewSplitterHitRect == null || this.overviewCollapseHandleRect == null) return;
            int splitterLineX = this.overviewSplitterHitRect.x() + this.overviewSplitterHitRect.width() / 2;
            boolean isSplitterHovered = this.overviewSplitterHitRect.contains(mouseX, mouseY);
            boolean isHandleHovered = this.overviewCollapseHandleRect.contains(mouseX, mouseY);
            boolean active = isSplitterHovered || isHandleHovered || this.draggingOverviewSplitter;

            int lineColor = this.draggingOverviewSplitter ? UiTheme.ACCENT : (active ? 0x885294E2 : 0x22FFFFFF);
            context.fill(splitterLineX - 1, dock.y(), splitterLineX + 1, dock.y() + dock.height(), lineColor);

            int handleX = this.overviewCollapseHandleRect.x();
            int handleY = this.overviewCollapseHandleRect.y();
            int pillBg = isHandleHovered ? 0xFA1E2C3D : 0xEE141F2B;
            int pillBorder = isHandleHovered ? UiTheme.ACCENT : (active ? 0xAA5294E2 : UiTheme.BORDER_STRONG);
            UiRenderer.panel(context, handleX, handleY, handleWidth, handleHeight, pillBg, pillBorder);

            String arrow = "\u276F"; // ❯ pointing right to collapse
            int arrowColor = isHandleHovered ? UiTheme.ACCENT : (active ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(arrow), handleX + handleWidth / 2, handleY + (handleHeight - 8) / 2, arrowColor);
        }
    }

    private void drawGamemodeCards(DrawContext context, UiLayout.Rect cards, int mouseX, int mouseY) {
        List<MinigameEntry> entries = this.filteredEntries();
        int columns = this.cardColumns(cards.width());
        for (int i = 0; i < entries.size(); i++) {
            MinigameEntry entry = entries.get(i);
            UiLayout.Rect card = UiLayout.grid(cards, i, columns, CARD_HEIGHT, CARD_GAP);
            boolean hovered = card.contains(mouseX, mouseY);
            UiAnimation.Value hover = this.cardHover.computeIfAbsent(entry.id(), ignored -> new UiAnimation.Value(hovered ? 1.0F : 0.0F));
            hover.animateTo(hovered ? 1.0F : 0.0F, UiTheme.HOVER_MS, UiAnimation::easeOutCubic);
            int accent = entry.enabled() ? this.accentFor(entry.id(), i) : UiTheme.BORDER;
            UiRenderer.card(context, card.x(), card.y(), card.width(), card.height(), hover.get(), accent);

            int iconX = card.x() + 14;
            int iconY = card.y() + 12;
            context.getMatrices().push();
            context.getMatrices().translate(iconX, iconY, 0);
            context.getMatrices().scale(1.4F, 1.4F, 1.0F);
            context.drawText(this.textRenderer, Text.literal(entry.icon()), 0, 0, entry.enabled() ? accent : UiTheme.TEXT_DIM, false);
            context.getMatrices().pop();

            int textX = iconX + 24;
            int titleColor = entry.enabled() ? UiTheme.TEXT : UiTheme.TEXT_DIM;
            context.drawText(this.textRenderer, Text.literal(entry.name()), textX, card.y() + 10, titleColor, false);
            String topology = displayTopology(entry.id());
            context.drawText(this.textRenderer, Text.literal(topology), textX, card.y() + 22, UiTheme.TEXT_DIM, false);

            List<String> descriptionLines = this.wrapText(entry.description(), card.width() - textX + card.x() - 18, 2);
            int descY = card.y() + 36;
            for (String line : descriptionLines) {
                context.drawText(this.textRenderer, Text.literal(line), textX, descY, UiTheme.TEXT_MUTED, false);
                descY += this.textRenderer.fontHeight + 2;
            }

            if (!entry.enabled()) {
                context.drawText(this.textRenderer, Text.literal("Unavailable"), card.x() + card.width() - 78, card.y() + 10, UiTheme.TEXT_DIM, false);
            }
        }
    }

    private RailSpan activeModuleRail(SidebarSection section, List<SidebarRow> rows, int startY) {
        if (section != SidebarSection.GAMEMODES) {
            return null;
        }
        if (!(this.workspaceView instanceof GamemodeWorkspaceView.ModuleProvider)) {
            return null;
        }
        int startIndex = -1;
        int endIndex = -1;
        int accent = UiTheme.ACCENT;
        for (int i = 0; i < rows.size(); i++) {
            SidebarRow row = rows.get(i);
            if (row.type == SidebarRowType.ITEM && row.indent == 8 && row.selected.getAsBoolean()) {
                startIndex = i;
                accent = row.accent;
                continue;
            }
            if (startIndex != -1 && row.type == SidebarRowType.ITEM && row.indent == 8) {
                endIndex = i - 1;
                break;
            }
        }
        if (startIndex == -1) {
            return null;
        }
        if (endIndex == -1) {
            endIndex = rows.size() - 1;
        }
        int y = startY;
        int top = startY;
        int bottom = startY;
        for (int i = 0; i < rows.size(); i++) {
            int height = this.rowHeight(rows.get(i));
            if (i == startIndex) {
                top = y + 2;
            }
            if (i == endIndex) {
                bottom = y + height - 2;
                break;
            }
            y += height;
        }
        return new RailSpan(top, bottom, accent);
    }

    private List<MinigameEntry> filteredEntries() {
        String query = this.searchField == null ? "" : this.searchField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            return this.minigameEntries;
        }
        List<MinigameEntry> filtered = new ArrayList<>();
        for (MinigameEntry entry : this.minigameEntries) {
            if (entry.name().toLowerCase(Locale.ROOT).contains(query)
                || entry.description().toLowerCase(Locale.ROOT).contains(query)
                || entry.id().toLowerCase(Locale.ROOT).contains(query)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private UiLayout.Rect closeButtonBounds(UiLayout.Rect toolbar) {
        return new UiLayout.Rect(toolbar.x() + toolbar.width() - TOOLBAR_BUTTON_WIDTH, toolbar.y() + 6, TOOLBAR_BUTTON_WIDTH, UiTheme.BUTTON_HEIGHT);
    }

    private UiLayout.Rect resetButtonBounds(UiLayout.Rect toolbar) {
        return new UiLayout.Rect(toolbar.x() + toolbar.width() - TOOLBAR_BUTTON_WIDTH - 6 - RESET_BUTTON_WIDTH, toolbar.y() + 6, RESET_BUTTON_WIDTH, UiTheme.BUTTON_HEIGHT);
    }

    private Layout createLayout() {
        int margin = 12;
        leftSidebarAnimation.tick();
        float animProgress = leftSidebarAnimation.get();
        int currentSidebarWidth = Math.round(animProgress * leftSidebarTargetWidth);

        UiLayout.Rect sidebarRect;
        UiLayout.Rect sidebarSearch;
        int workspaceX;
        int splitterLineX;

        if (currentSidebarWidth > 4) {
            sidebarRect = new UiLayout.Rect(margin, margin, currentSidebarWidth, this.height - margin * 2);
            sidebarSearch = new UiLayout.Rect(sidebarRect.x() + 12, sidebarRect.y() + SIDEBAR_HEADER_HEIGHT, Math.max(10, sidebarRect.width() - 24), UiTheme.INPUT_HEIGHT);
            splitterLineX = sidebarRect.x() + sidebarRect.width() + UiTheme.GAP / 2;
            workspaceX = sidebarRect.x() + sidebarRect.width() + UiTheme.GAP;
        } else {
            sidebarRect = new UiLayout.Rect(margin, margin, 0, this.height - margin * 2);
            sidebarSearch = new UiLayout.Rect(margin, margin, 0, 0);
            splitterLineX = margin + 5;
            workspaceX = margin + 14;
        }

        this.leftSplitterHitRect = new UiLayout.Rect(splitterLineX - 4, margin, 8, this.height - margin * 2);
        int handleWidth = 17; // 20% larger than 14px
        int handleHeight = 44; // 20% larger than 36px
        int handleY = (this.height - handleHeight) / 2;
        int handleX = splitterLineX - handleWidth / 2;
        this.leftCollapseHandleRect = new UiLayout.Rect(handleX, handleY, handleWidth, handleHeight);

        int workspaceWidth = Math.max(1, this.width - workspaceX - margin);
        UiLayout.Rect toolbar = new UiLayout.Rect(workspaceX, margin, workspaceWidth, UiTheme.TOOLBAR_HEIGHT);
        int dockY = toolbar.y() + toolbar.height() + UiTheme.GAP;
        int dockHeight = Math.max(1, this.height - dockY - margin);
        UiLayout.Rect content = new UiLayout.Rect(workspaceX, dockY, workspaceWidth, dockHeight);

        rightDockAnimation.tick();
        float overviewProgress = rightDockAnimation.get();
        int currentOverviewWidth = Math.round(overviewProgress * rightDockTargetWidth);

        UiLayout.Rect detail;
        UiLayout.Rect search;
        UiLayout.Rect cards;

        int centerPadLeft = currentSidebarWidth <= 4 ? 20 : 16;
        int centerStartX = workspaceX + centerPadLeft;

        if (currentOverviewWidth > 4) {
            int dockX = workspaceX + workspaceWidth - currentOverviewWidth;
            detail = new UiLayout.Rect(dockX, dockY, currentOverviewWidth, dockHeight);
            int centerPadRight = 16;
            int availableWidth = Math.max(140, dockX - centerStartX - centerPadRight);
            search = new UiLayout.Rect(centerStartX, dockY + 14, availableWidth, UiTheme.INPUT_HEIGHT);
            cards = new UiLayout.Rect(centerStartX, search.y() + search.height() + 18, availableWidth, Math.max(1, this.height - search.y() - search.height() - 42));

            int rightSplitterLineX = dockX - UiTheme.GAP / 2;
            this.overviewSplitterHitRect = new UiLayout.Rect(rightSplitterLineX - 4, dockY, 8, dockHeight);
            this.overviewCollapseHandleRect = new UiLayout.Rect(rightSplitterLineX - handleWidth / 2, dockY + (dockHeight - handleHeight) / 2, handleWidth, handleHeight);
            this.overviewCollapseStripRect = null;
        } else {
            detail = new UiLayout.Rect(workspaceX + workspaceWidth, dockY, 0, dockHeight);

            this.overviewSplitterHitRect = null;
            int stripWidth = 26;
            int stripX = this.width - stripWidth;
            this.overviewCollapseStripRect = new UiLayout.Rect(stripX, dockY, stripWidth, dockHeight);
            this.overviewCollapseHandleRect = new UiLayout.Rect(stripX + (stripWidth - handleWidth) / 2, dockY + (dockHeight - handleHeight) / 2, handleWidth, handleHeight);

            int centerPadRight = 20;
            int availableWidth = Math.max(140, stripX - centerStartX - centerPadRight);
            search = new UiLayout.Rect(centerStartX, dockY + 14, availableWidth, UiTheme.INPUT_HEIGHT);
            cards = new UiLayout.Rect(centerStartX, search.y() + search.height() + 18, availableWidth, Math.max(1, this.height - search.y() - search.height() - 42));
        }
        return new Layout(sidebarRect, sidebarSearch, toolbar, search, cards, detail, content);
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
        if ("infection".equals(id)) {
            return UiTheme.ACCENT_RED;
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
            case "manhunt", "bountyhunt", "resource_sprint", "deathswap", "infection" -> "Shared arena";
            default -> "Session ready";
        };
    }

    private void openCachedWorkspace(String key, java.util.function.Supplier<WorkspaceView> factory) {
        if (this.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView agw) {
            agw.flushWidgetState();
        }
        WorkspaceView view = WORKSPACE_CACHE.computeIfAbsent(key, k -> factory.get());
        this.openWorkspaceView(view);
    }

    public void resetCurrentWorkspace() {
        if (this.workspaceView == null) {
            return;
        }
        if (this.workspaceView instanceof GamemodeWorkspaceView gamemodeView) {
            String gameId = gamemodeView.gameId();
            String title = this.workspaceView.title();
            WORKSPACE_CACHE.remove(gameId);
            dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView.clearCacheForGame(gameId);
            Consumer<SessionScreen> action = CUSTOM_SETUP_SCREENS.get(gameId);
            if (action != null) {
                action.accept(this);
            } else {
                this.openSelectorWorkspace();
            }
            if (this.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView agw) {
                agw.setStatus(dev.frost.miniverse.client.gui.workspace.framework.ValidationResult.info("Reset to default settings."));
            }
            this.statusMessage = "Reset " + title + " to default.";
        } else if (this.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.AppearanceWorkspaceView) {
            WORKSPACE_CACHE.remove("appearance");
            this.openAppearance();
        } else if (this.workspaceView instanceof AdminWorkspaceView) {
            this.requestSnapshot();
            this.statusMessage = "Refreshed server data.";
        } else if (this.workspaceView instanceof dev.frost.miniverse.client.gui.map.MapManagementWorkspaceView) {
            this.requestSnapshot();
            this.statusMessage = "Refreshed maps.";
        } else {
            this.openSelectorWorkspace();
        }
    }

    private void openManhunt() {
        this.openCachedWorkspace("manhunt", ManhuntWorkspaceView::new);
    }

    private void openAppearance() {
        this.openCachedWorkspace("appearance", AppearanceWorkspaceView::new);
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

    private void openMapsWorkspace() {
        this.openWorkspaceView(new MapManagementWorkspaceView(this::requestSnapshot));
    }

    private void openMapEditorWorkspace() {
        // Restore the last-viewed screen if user had one selected
        if (this.mapEditorState.selectedGameId != null && !this.mapEditorState.selectedGameId.isBlank()) {
            if (this.mapEditorState.selectedDefinitionKey != null && !this.mapEditorState.selectedDefinitionKey.isBlank()) {
                this.openWorkspaceView(MapEditorWorkspaceView.forMarker(this.mapEditorState, this::requestSnapshot, this.mapEditorState.selectedGameId, this.mapEditorState.selectedDefinitionKey));
            } else {
                this.openWorkspaceView(MapEditorWorkspaceView.forGamemode(this.mapEditorState, this::requestSnapshot, this.mapEditorState.selectedGameId));
            }
        } else {
            this.openWorkspaceView(new MapEditorWorkspaceView(this.mapEditorState, this::requestSnapshot));
        }
    }

    private boolean isAdminMode(AdminWorkspaceView.Mode mode) {
        return this.workspaceView instanceof AdminWorkspaceView adminWorkspaceView && adminWorkspaceView.mode() == mode;
    }

    private void openSpeedrun() {
        this.openCachedWorkspace("speedrun", SpeedrunWorkspaceView::new);
    }

    private void openBountyHunt() {
        this.openCachedWorkspace("bountyhunt", BountyHuntWorkspaceView::new);
    }

    private void openResourceSprint() {
        this.openCachedWorkspace("resource_sprint", ResourceSprintWorkspaceView::new);
    }

    private void openDeathSwap() {
        this.openCachedWorkspace("deathswap", DeathSwapWorkspaceView::new);
    }

    private void openInfection() {
        this.openCachedWorkspace("infection", InfectionWorkspaceView::new);
    }

    private void openBridge() {
        this.openCachedWorkspace("bridge", BridgeWorkspaceView::new);
    }

    private void openBedwars() {
        this.openCachedWorkspace("bedwars", dev.frost.miniverse.client.gui.workspace.BedwarsWorkspaceView::new);
    }

    private void openBlockShuffle() {
        this.openCachedWorkspace("block_shuffle", BlockShuffleWorkspaceView::new);
    }

    private void openDeathShuffle() {
        this.openCachedWorkspace("death_shuffle", DeathShuffleWorkspaceView::new);
    }

    private void openMurderMystery() {
        this.openCachedWorkspace("murdermystery", dev.frost.miniverse.client.gui.workspace.MurderMysteryWorkspaceView::new);
    }

    private void openDuels() {
        this.openCachedWorkspace("duels", DuelsWorkspaceView::new);
    }

    private void openPillarsOfFortune() {
        this.openCachedWorkspace("pillarsoffortune", dev.frost.miniverse.client.gui.workspace.PillarsOfFortuneWorkspaceView::new);
    }

    private void openHordeSurvival() {
        this.openCachedWorkspace("horde_survival", dev.frost.miniverse.client.gui.workspace.HordeSurvivalWorkspaceView::new);
    }

    private void openZombies() {
        this.openCachedWorkspace("zombies", dev.frost.miniverse.client.gui.workspace.ZombiesWorkspaceView::new);
    }

    private void openDropper() {
        this.openCachedWorkspace("dropper", dev.frost.miniverse.client.gui.workspace.DropperWorkspaceView::new);
    }

    private void openMicroFrenzy() {
        this.openCachedWorkspace("microfrenzy", dev.frost.miniverse.client.gui.workspace.MicroFrenzyWorkspaceView::new);
    }

    public void openGenericSetup(MinigameEntry entry) {
        this.client.setScreen(new GenericSetupScreen(entry));
    }

    private void requestSnapshot() {
        if (this.client.player != null) {
            String marker = SessionSnapshotData.historyLoaded() ? "history" : "metadata";
            ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload(marker));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        this.saveCurrentWorkspaceState();
        super.close();
    }

    @Override
    public void removed() {
        this.saveCurrentWorkspaceState();
        super.removed();
    }

    private void saveCurrentWorkspaceState() {
        if (this.workspaceView instanceof dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView agw) {
            agw.flushWidgetState();
        }
        lastActiveWorkspace = this.workspaceView;
    }

    private record Layout(UiLayout.Rect sidebar, UiLayout.Rect sidebarSearch, UiLayout.Rect toolbar, UiLayout.Rect search, UiLayout.Rect cards, UiLayout.Rect detail, UiLayout.Rect content) {
    }

    private record RailSpan(int startY, int endY, int accent) {
    }

    private enum SidebarSection {
        GAMEMODES("Gamemodes"),
        SESSION("Sessions"),
        SERVER("Server"),
        MAPS("Maps"),
        APPEARANCE("Appearance");

        private final String label;

        SidebarSection(String label) {
            this.label = label;
        }
    }

    private record SidebarChild(String icon, String label, Runnable action, java.util.function.BooleanSupplier selected, int indent, int accent) {
        private SidebarChild(String icon, String label, Runnable action, java.util.function.BooleanSupplier selected) {
            this(icon, label, action, selected, 8, UiTheme.ACCENT);
        }
    }

    private enum SidebarRowType {
        ITEM,
        GROUP_HEADER,
        DIVIDER
    }

    private record SidebarRow(SidebarRowType type, String icon, String label, Runnable action, java.util.function.BooleanSupplier selected, int indent, int accent, String groupId) {
        private static SidebarRow item(SidebarChild child) {
            return new SidebarRow(SidebarRowType.ITEM, child.icon, child.label, child.action, child.selected, child.indent, child.accent, null);
        }

        private static SidebarRow group(String groupId, String label, Runnable action) {
            return new SidebarRow(SidebarRowType.GROUP_HEADER, "", label, action, () -> false, 0, UiTheme.TEXT_DIM, groupId);
        }

        private static SidebarRow divider() {
            return new SidebarRow(SidebarRowType.DIVIDER, "", "", null, () -> false, 0, 0, null);
        }
    }
}
