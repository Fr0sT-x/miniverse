package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.StandardWorkspaceLayout;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceModuleManager;
import dev.frost.miniverse.minigame.impl.dropper.DropperDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DropperWorkspaceView extends AbstractGamemodeWorkspaceView {
    public record DetectedLevel(String id, String name) {}
    public record LevelRowButton(UiLayout.Rect rect, String levelId, String label) {}

    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private final List<DetectedLevel> detectedLevels = new ArrayList<>();
    private final Set<String> enabledLevelIds = new HashSet<>();
    private String lastSyncedMapId = null;
    private int levelListScrollOffset = 0;

    private UiLayout.Rect inspectLevelsRect;
    private UiLayout.Rect backToMapRect;
    private UiLayout.Rect selectAllRect;
    private UiLayout.Rect deselectAllRect;
    private final List<LevelRowButton> levelRowButtons = new ArrayList<>();
    private UiLayout.Rect prevPageRect;
    private UiLayout.Rect nextPageRect;

    private IntFieldWidget levelsToPlayField;
    private IntFieldWidget finalCountdownField;
    private IntFieldWidget timeLimitField;
    private IntFieldWidget skipThresholdField;

    private int levelsToPlay = 5;
    private String selectionMode = "ORDER"; // ORDER, RANDOM_N, SHUFFLE
    private int finalCountdownSeconds = 60;
    private int timeLimitSeconds = 600;
    private boolean allowSkip = true;
    private int skipFailsThreshold = 20;

    private SessionScreen sessionScreen;

    public DropperWorkspaceView() {
        super("dropper");

        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);

        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating runners.", UiTheme.ACCENT);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a map with dropper levels.", UiTheme.ACCENT_BLUE, "Dropper Maps");
        this.moduleManager.register("levels", "L", "Level Pool", "Setup", "Inspect and toggle levels for this match.", 0xFFFFAA00);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure level counts, timers, and skips.", UiTheme.ACCENT_GREEN);
    }

    private void syncLevelsFromSelectedMap() {
        if (this.selectedMapId == null || this.selectedMapId.isBlank()) {
            this.detectedLevels.clear();
            this.enabledLevelIds.clear();
            this.lastSyncedMapId = null;
            return;
        }

        boolean mapChanged = !this.selectedMapId.equals(this.lastSyncedMapId);

        SessionSnapshotData.MapSummary map = SessionSnapshotData.maps().stream()
            .filter(m -> m.id().equals(this.selectedMapId))
            .findFirst().orElse(null);

        this.detectedLevels.clear();
        if (map != null) {
            for (String tag : map.tags()) {
                if (tag.startsWith("dropper_level:")) {
                    String[] parts = tag.substring("dropper_level:".length()).split(":", 2);
                    if (parts.length == 2) {
                        this.detectedLevels.add(new DetectedLevel(parts[0], parts[1]));
                    } else if (parts.length == 1) {
                        this.detectedLevels.add(new DetectedLevel(parts[0], parts[0]));
                    }
                }
            }
        }

        if (mapChanged) {
            this.lastSyncedMapId = this.selectedMapId;
            this.enabledLevelIds.clear();
            for (DetectedLevel level : this.detectedLevels) {
                this.enabledLevelIds.add(level.id());
            }
            this.levelListScrollOffset = 0;
        } else {
            // Prune any ids not in detectedLevels
            Set<String> validIds = new HashSet<>();
            for (DetectedLevel l : this.detectedLevels) {
                validIds.add(l.id());
            }
            this.enabledLevelIds.removeIf(id -> !validIds.contains(id));
        }

        this.updateLevelsToPlayClamp();
    }

    private void updateLevelsToPlayClamp() {
        if (this.levelsToPlayField != null) {
            int max = Math.max(1, this.enabledLevelIds.size());
            this.levelsToPlay = Math.min(this.levelsToPlay, max);
            this.levelsToPlayField.setText(String.valueOf(this.levelsToPlay));
        }
    }

    public int calculateMaxVisibleRows() {
        if (this.layout == null) return 6;
        int moduleY = this.layout.mainPanel().y() + 72;
        int moduleHeight = this.layout.mainPanel().height() - 104;
        int moduleBottom = moduleY + moduleHeight;
        int rowY = this.layout.mainPanel().y() + 132;
        int available = (moduleBottom - 12) - rowY;
        if (available < 20) return 1;
        return Math.max(1, ((available - 20) / 24) + 1);
    }

    private void rebuildLevelRowLayout() {
        this.levelRowButtons.clear();
        this.prevPageRect = null;
        this.nextPageRect = null;

        if (this.layout == null) return;

        // Shift 26px from panel left: 14px moduleX + 12px padding, clearing the yellow accent line cleanly
        int startX = this.layout.mainPanel().x() + 26;
        int startY = this.layout.mainPanel().y() + 104;

        if (this.selectedMapId == null || this.selectedMapId.isBlank() || this.detectedLevels.isEmpty()) {
            this.backToMapRect = new UiLayout.Rect(this.layout.actionStartX(), this.layout.actionY(), 160, StandardWorkspaceLayout.BUTTON_HEIGHT);
            this.selectAllRect = null;
            this.deselectAllRect = null;
            return;
        }

        this.backToMapRect = null;
        this.selectAllRect = new UiLayout.Rect(startX, startY, 90, 20);
        this.deselectAllRect = new UiLayout.Rect(startX + 98, startY, 90, 20);

        int rowY = startY + 28;
        int maxRows = this.calculateMaxVisibleRows();
        int total = this.detectedLevels.size();

        if (this.levelListScrollOffset + maxRows > total) {
            this.levelListScrollOffset = Math.max(0, total - maxRows);
        }

        for (int i = 0; i < maxRows && (i + this.levelListScrollOffset) < total; i++) {
            int index = i + this.levelListScrollOffset;
            DetectedLevel level = this.detectedLevels.get(index);
            boolean enabled = this.enabledLevelIds.contains(level.id());
            String btnLabel = (enabled ? "[✓] " : "[  ] ") + (index + 1) + ". " + level.name();
            int currentY = rowY + (i * 24);
            this.levelRowButtons.add(new LevelRowButton(new UiLayout.Rect(startX, currentY, 320, 20), level.id(), btnLabel));
        }

        if (total > maxRows) {
            this.prevPageRect = new UiLayout.Rect(startX + 330, rowY, 80, 20);
            this.nextPageRect = new UiLayout.Rect(startX + 330, rowY + 30, 80, 20);
        }
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        this.sessionScreen = screen;
        this.syncLevelsFromSelectedMap();

        if (this.moduleManager.isActive("map")) {
            if (this.selectedMapId != null && !this.selectedMapId.isBlank()) {
                int btnX = this.layout.actionStartX();
                int btnY = this.layout.actionY();
                this.inspectLevelsRect = new UiLayout.Rect(btnX, btnY, 200, StandardWorkspaceLayout.BUTTON_HEIGHT);
            } else {
                this.inspectLevelsRect = null;
            }
        } else if (this.moduleManager.isActive("levels")) {
            this.rebuildLevelRowLayout();
        } else if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Levels to Play", (s, x, y, w) -> {
                    int max = Math.max(1, this.enabledLevelIds.isEmpty() ? 10 : this.enabledLevelIds.size());
                    this.levelsToPlayField = this.addIntField(s, x, y, Math.min(this.levelsToPlay, max), w, "Levels", val -> "Play " + val + " level(s) in this match.");
                },
                "Selection Mode", (s, x, y, w) -> {
                    this.addCycleButton(s,
                        () -> "Mode: " + formatMode(this.selectionMode),
                        () -> modeIndex(this.selectionMode),
                        x, y, w,
                        new String[] {
                            "Play the enabled levels in order.",
                            "Choose N random levels from the enabled pool.",
                            "Shuffle all enabled levels into a random order."
                        },
                        3,
                        () -> {
                            this.selectionMode = nextMode(this.selectionMode);
                        }
                    );
                }
            );

            this.rulesLayout.addRow(
                "Final Countdown (s)", (s, x, y, w) -> {
                    this.finalCountdownField = this.addIntField(s, x, y, this.finalCountdownSeconds, w, "Final Countdown", val -> val + " seconds left once 1st place finishes.");
                },
                "Max Match Time (s)", (s, x, y, w) -> {
                    this.timeLimitField = this.addIntField(s, x, y, this.timeLimitSeconds, w, "Time Limit", val -> val <= 0 ? "No time limit." : "Match ends after " + val + " seconds.");
                }
            );

            this.rulesLayout.addRow(
                "Allow Skip on Fails", (s, x, y, w) -> {
                    this.addToggleButton(s, "Skip Mechanic", () -> this.allowSkip, x, y, w,
                        new BinaryTooltip("Players stuck on a level can type /dropper skip.", "Level skipping is completely disabled."),
                        () -> this.allowSkip = !this.allowSkip
                    );
                },
                "Skip Fails Threshold", (s, x, y, w) -> {
                    this.skipThresholdField = this.addIntField(s, x, y, this.skipFailsThreshold, w, "Skip Fails", val -> "Enables /dropper skip after " + val + " fails.");
                }
            );
        }
    }

    private static String formatMode(String mode) {
        return switch (mode) {
            case "RANDOM_N" -> "Random N";
            case "SHUFFLE" -> "Shuffle All";
            default -> "In Order";
        };
    }

    private static int modeIndex(String mode) {
        return switch (mode) {
            case "RANDOM_N" -> 1;
            case "SHUFFLE" -> 2;
            default -> 0;
        };
    }

    private static String nextMode(String current) {
        return switch (current) {
            case "ORDER" -> "RANDOM_N";
            case "RANDOM_N" -> "SHUFFLE";
            default -> "ORDER";
        };
    }

    @Override
    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            int maxLevels = Math.max(1, this.enabledLevelIds.isEmpty() ? 50 : this.enabledLevelIds.size());
            this.levelsToPlay = readClamped(this.levelsToPlayField, this.levelsToPlay, 1, maxLevels);
            this.finalCountdownSeconds = readClamped(this.finalCountdownField, this.finalCountdownSeconds, 5, 300);
            this.timeLimitSeconds = readClamped(this.timeLimitField, this.timeLimitSeconds, 0, 3600);
            this.skipFailsThreshold = readClamped(this.skipThresholdField, this.skipFailsThreshold, 1, 100);
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("map")) {
            if (this.selectedMapId != null && !this.selectedMapId.isBlank() && this.inspectLevelsRect != null) {
                String inspectLabel = "Inspect Levels (" + this.enabledLevelIds.size() + "/" + this.detectedLevels.size() + ") ->";
                this.renderActionButton(context, textRenderer, this.inspectLevelsRect, inspectLabel, UiTheme.ACCENT_BLUE, this.inspectLevelsRect.contains(mouseX, mouseY));
            }
        } else if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        } else if (this.moduleManager.isActive("levels")) {
            WorkspaceModuleManager.RegisteredModule active = this.moduleManager.getActiveModule();
            this.renderSettingsModulePanel(context, textRenderer, active.label(), active.accent());

            int startX = this.layout.mainPanel().x() + 26;
            int headerY = this.layout.mainPanel().y() + 84;

            if (this.selectedMapId == null || this.selectedMapId.isBlank()) {
                if (this.backToMapRect != null) {
                    this.renderActionButton(context, textRenderer, this.backToMapRect, "<- Map Selection", UiTheme.ACCENT_BLUE, this.backToMapRect.contains(mouseX, mouseY));
                }
                context.drawText(textRenderer, Text.literal("No map selected. Please choose a dropper map in Map Selection first."), startX, headerY + 28, UiTheme.TEXT_MUTED, false);
            } else if (this.detectedLevels.isEmpty()) {
                if (this.backToMapRect != null) {
                    this.renderActionButton(context, textRenderer, this.backToMapRect, "<- Map Selection", UiTheme.ACCENT_BLUE, this.backToMapRect.contains(mouseX, mouseY));
                }
                context.drawText(textRenderer, Text.literal("No dropper levels detected on map '" + this.selectedMapId + "'."), startX, headerY + 28, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Use the Map Editor to place 'Dropper Level' markers on this map."), startX, headerY + 44, UiTheme.TEXT_DIM, false);
            } else {
                String poolInfo = "Active Pool: " + this.enabledLevelIds.size() + " / " + this.detectedLevels.size() + " levels enabled";
                context.drawText(textRenderer, Text.literal(poolInfo), startX + 110, headerY + 1, UiTheme.TEXT_MUTED, false);

                // Mod-themed Select All & Deselect All buttons
                if (this.selectAllRect != null) {
                    this.renderActionButton(context, textRenderer, this.selectAllRect, "Select All", UiTheme.ACCENT_BLUE, this.selectAllRect.contains(mouseX, mouseY));
                }
                if (this.deselectAllRect != null) {
                    this.renderActionButton(context, textRenderer, this.deselectAllRect, "Deselect All", UiTheme.ACCENT, this.deselectAllRect.contains(mouseX, mouseY));
                }

                // Level rows using mod-themed buttons
                for (LevelRowButton btn : this.levelRowButtons) {
                    boolean enabled = this.enabledLevelIds.contains(btn.levelId());
                    int accent = enabled ? UiTheme.ACCENT_GREEN : 0x7C8088;
                    this.renderActionButton(context, textRenderer, btn.rect(), btn.label(), accent, btn.rect().contains(mouseX, mouseY));
                }

                // Pagination
                int maxRows = this.calculateMaxVisibleRows();
                int total = this.detectedLevels.size();
                if (total > maxRows) {
                    if (this.prevPageRect != null) {
                        boolean canPrev = this.levelListScrollOffset > 0;
                        int accent = canPrev ? UiTheme.ACCENT_BLUE : 0x444444;
                        this.renderActionButton(context, textRenderer, this.prevPageRect, "▲ Prev", accent, canPrev && this.prevPageRect.contains(mouseX, mouseY));
                    }
                    if (this.nextPageRect != null) {
                        boolean canNext = this.levelListScrollOffset + maxRows < total;
                        int accent = canNext ? UiTheme.ACCENT_BLUE : 0x444444;
                        this.renderActionButton(context, textRenderer, this.nextPageRect, "▼ Next", accent, canNext && this.nextPageRect.contains(mouseX, mouseY));
                    }
                    int showingFrom = this.levelListScrollOffset + 1;
                    int showingTo = Math.min(total, this.levelListScrollOffset + maxRows);
                    String pageInfo = showingFrom + "-" + showingTo + " of " + total;
                    int rowY = this.layout.mainPanel().y() + 132;
                    context.drawText(textRenderer, Text.literal(pageInfo), startX + 332, rowY + 58, UiTheme.TEXT_DIM, false);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return this.gamemodeMouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean gamemodeMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (this.moduleManager.isActive("map")) {
            if (this.inspectLevelsRect != null && this.inspectLevelsRect.contains(mouseX, mouseY)) {
                this.setActiveModule("levels");
                if (this.sessionScreen != null) {
                    this.sessionScreen.rebuildWorkspaceChildren();
                }
                return true;
            }
        }

        if (this.moduleManager.isActive("levels")) {
            if (this.backToMapRect != null && this.backToMapRect.contains(mouseX, mouseY)) {
                this.setActiveModule("map");
                if (this.sessionScreen != null) {
                    this.sessionScreen.rebuildWorkspaceChildren();
                }
                return true;
            }

            if (this.selectAllRect != null && this.selectAllRect.contains(mouseX, mouseY)) {
                for (DetectedLevel l : this.detectedLevels) {
                    this.enabledLevelIds.add(l.id());
                }
                this.status = ValidationResult.info("Selected all levels (" + this.enabledLevelIds.size() + ").");
                this.updateLevelsToPlayClamp();
                this.rebuildLevelRowLayout();
                return true;
            }

            if (this.deselectAllRect != null && this.deselectAllRect.contains(mouseX, mouseY)) {
                this.enabledLevelIds.clear();
                this.status = ValidationResult.info("Deselected all levels.");
                this.updateLevelsToPlayClamp();
                this.rebuildLevelRowLayout();
                return true;
            }

            for (LevelRowButton row : this.levelRowButtons) {
                if (row.rect().contains(mouseX, mouseY)) {
                    if (this.enabledLevelIds.contains(row.levelId())) {
                        this.enabledLevelIds.remove(row.levelId());
                    } else {
                        this.enabledLevelIds.add(row.levelId());
                    }
                    this.updateLevelsToPlayClamp();
                    this.rebuildLevelRowLayout();
                    return true;
                }
            }

            int maxRows = this.calculateMaxVisibleRows();
            int total = this.detectedLevels.size();

            if (this.prevPageRect != null && this.prevPageRect.contains(mouseX, mouseY)) {
                if (this.levelListScrollOffset > 0) {
                    this.levelListScrollOffset = Math.max(0, this.levelListScrollOffset - 1);
                    this.rebuildLevelRowLayout();
                }
                return true;
            }

            if (this.nextPageRect != null && this.nextPageRect.contains(mouseX, mouseY)) {
                if (this.levelListScrollOffset + maxRows < total) {
                    this.levelListScrollOffset = Math.min(total - maxRows, this.levelListScrollOffset + 1);
                    this.rebuildLevelRowLayout();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean gamemodeMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxRows = this.calculateMaxVisibleRows();
        int total = this.detectedLevels.size();
        if (this.moduleManager.isActive("levels") && total > maxRows) {
            if (verticalAmount < 0 && this.levelListScrollOffset + maxRows < total) {
                this.levelListScrollOffset = Math.min(total - maxRows, this.levelListScrollOffset + 1);
                this.rebuildLevelRowLayout();
                return true;
            } else if (verticalAmount > 0 && this.levelListScrollOffset > 0) {
                this.levelListScrollOffset = Math.max(0, this.levelListScrollOffset - 1);
                this.rebuildLevelRowLayout();
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<Text> getSummaryLines() {
        return List.of(
            Text.literal("Map: §e" + (!this.selectedMapId.isBlank() ? this.selectedMapId : "None")),
            Text.literal("Detected Levels: §e" + this.detectedLevels.size()),
            Text.literal("Active in Pool: §a" + this.enabledLevelIds.size() + " levels"),
            Text.literal("Levels to Play: §b" + this.levelsToPlay + " §7(" + formatMode(this.selectionMode) + ")"),
            Text.literal("Final Countdown: §e" + this.finalCountdownSeconds + "s"),
            Text.literal("Max Match Time: §e" + (this.timeLimitSeconds > 0 ? this.timeLimitSeconds + "s" : "Unlimited")),
            Text.literal("Skip on Fails: §e" + (this.allowSkip ? "After " + this.skipFailsThreshold + " fails" : "Disabled")),
            Text.literal("Players: §e" + this.playerGrid.getMembers("selected").size())
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
    }

    @Override
    public String title() {
        return "Dropper Setup";
    }

    @Override
    public String subtitle() {
        return "Drop through obstacles to reach the water pool!";
    }

    @Override
    public String gameId() {
        return DropperDefinition.ID;
    }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();

        if (this.selectedMapId == null || this.selectedMapId.isBlank()) {
            return ValidationResult.error("Please select a dropper map.");
        }

        if (this.enabledLevelIds.isEmpty()) {
            return ValidationResult.error("At least 1 level must be enabled in the Level Pool.");
        }

        if (this.playerGrid.getMembers("selected").isEmpty()) {
            return ValidationResult.error("At least 1 player must be selected to play.");
        }

        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putString("mapId", this.selectedMapId);
        builder.settings().putInt("levelsToPlay", this.levelsToPlay);
        builder.settings().putString("selectionMode", this.selectionMode);
        builder.settings().putString("selectedLevelIds", String.join(",", this.enabledLevelIds));
        builder.settings().putInt("finalCountdownSeconds", this.finalCountdownSeconds);
        builder.settings().putInt("timeLimitSeconds", this.timeLimitSeconds);
        builder.settings().putBoolean("allowSkip", this.allowSkip);
        builder.settings().putInt("skipFailsThreshold", this.skipFailsThreshold);
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        if (settings.contains("mapId", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            this.selectedMapId = settings.getString("mapId");
        }
        if (settings.contains("levelsToPlay", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.levelsToPlay = settings.getInt("levelsToPlay");
        }
        if (settings.contains("selectionMode", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            this.selectionMode = settings.getString("selectionMode");
        }
        if (settings.contains("selectedLevelIds", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            this.enabledLevelIds.clear();
            String raw = settings.getString("selectedLevelIds");
            if (!raw.isBlank()) {
                for (String id : raw.split(",")) {
                    if (!id.trim().isEmpty()) {
                        this.enabledLevelIds.add(id.trim());
                    }
                }
            }
        }
        if (settings.contains("finalCountdownSeconds", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.finalCountdownSeconds = settings.getInt("finalCountdownSeconds");
        }
        if (settings.contains("timeLimitSeconds", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.timeLimitSeconds = settings.getInt("timeLimitSeconds");
        }
        if (settings.contains("allowSkip", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.allowSkip = settings.getBoolean("allowSkip");
        }
        if (settings.contains("skipFailsThreshold", net.minecraft.nbt.NbtElement.NUMBER_TYPE)) {
            this.skipFailsThreshold = settings.getInt("skipFailsThreshold");
        }

        if (this.levelsToPlayField != null) this.levelsToPlayField.setText(String.valueOf(this.levelsToPlay));
        if (this.finalCountdownField != null) this.finalCountdownField.setText(String.valueOf(this.finalCountdownSeconds));
        if (this.timeLimitField != null) this.timeLimitField.setText(String.valueOf(this.timeLimitSeconds));
        if (this.skipThresholdField != null) this.skipThresholdField.setText(String.valueOf(this.skipFailsThreshold));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.levelsToPlay = 1;
        this.selectionMode = "RANDOM";
        this.finalCountdownSeconds = 30;
        this.timeLimitSeconds = 300;
        this.allowSkip = false;
        this.skipFailsThreshold = 5;

        if (this.levelsToPlayField != null) this.levelsToPlayField.setText(String.valueOf(this.levelsToPlay));
        if (this.finalCountdownField != null) this.finalCountdownField.setText(String.valueOf(this.finalCountdownSeconds));
        if (this.timeLimitField != null) this.timeLimitField.setText(String.valueOf(this.timeLimitSeconds));
        if (this.skipThresholdField != null) this.skipThresholdField.setText(String.valueOf(this.skipFailsThreshold));
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Runners", this.playerGrid.getMembers("selected"));
    }
}
