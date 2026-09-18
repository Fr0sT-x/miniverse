package dev.frost.miniverse.client.gui.workspace.framework;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.GamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.WorkspaceView;
import dev.frost.miniverse.client.gui.workspace.components.MapThumbnailGrid;
import dev.frost.miniverse.client.gui.workspace.components.PresetDropdownWidget;
import dev.frost.miniverse.client.gui.workspace.components.SavePresetPopupScreen;
import dev.frost.miniverse.client.gui.workspace.components.TeamSelectionGrid;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractGamemodeWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider, GamemodeWorkspaceView.RosterRefreshable {
    protected static final Map<String, String> LAST_ACTIVE_PRESET_NAMES = new ConcurrentHashMap<>();
    protected static final Map<String, NbtCompound> LAST_ACTIVE_SETTINGS_CACHE = new ConcurrentHashMap<>();

    protected final MinecraftClient client = MinecraftClient.getInstance();
    protected final WorkspaceModuleManager moduleManager = new WorkspaceModuleManager();
    protected StandardWorkspaceLayout layout;
    protected SettingsLayoutBuilder rulesLayout;
    
    protected String sessionName;
    protected ValidationResult status = null;

    protected static boolean rightSidebarOpen = true;
    protected String activePresetName = "Default";
    protected PresetDropdownWidget presetDropdown;
    protected UiLayout.Rect savePresetBtnRect;
    protected UiLayout.Rect saveAsPresetBtnRect;
    protected UiLayout.Rect deletePresetBtnRect;
    protected UiLayout.Rect resetPresetBtnRect;
    protected UiLayout.Rect dockToggleBtnRect;
    protected UiLayout.Rect dockCloseBtnRect;
    protected UiLayout.Rect lastWorkspaceRect;
    protected SessionScreen currentScreen;

    protected record TooltipZone(int x, int y, int width, int height, java.util.function.Supplier<String> text) {}
    protected final java.util.List<TooltipZone> activeTooltips = new java.util.ArrayList<>();

    private TeamSelectionGrid rosterGrid;
    private MapThumbnailGrid mapGrid;
    protected String selectedMapId = "";
    private UiLayout.Rect selectAllButtonRect;
    private UiLayout.Rect clearButtonRect;

    public String getSelectedMapId() {
        return this.selectedMapId;
    }

    public AbstractGamemodeWorkspaceView(String defaultSessionNamePrefix) {
        this.sessionName = defaultSessionNamePrefix + "-" + System.currentTimeMillis();
    }

    protected void useRosterGrid(TeamSelectionGrid grid, String moduleId, String icon, String label, String group, String description, int accent) {
        this.rosterGrid = grid;
        this.moduleManager.register(moduleId, icon, label, group, description, accent);
    }

    protected void useMapSelection(String moduleId, String icon, String label, String group, String description, int accent, String gridTitle) {
        this.mapGrid = new MapThumbnailGrid(gridTitle, mapId -> {
            this.selectedMapId = mapId;
            this.status = ValidationResult.success("Selected map.");
            this.mapGrid.setSelectedMapId(mapId);
        });
        this.moduleManager.register(moduleId, icon, label, group, description, accent);
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.currentScreen = screen;
        this.lastWorkspaceRect = workspace;
        this.activeTooltips.clear();
        this.layout = new StandardWorkspaceLayout(workspace, rightSidebarOpen);

        this.dockToggleBtnRect = this.layout.dockToggleRect();

        if (this.layout.isRightSidebarOpen()) {
            UiLayout.Rect dock = this.layout.rightSidebar();
            int dockX = dock.x();
            int dockY = dock.y();
            int dockW = dock.width();
            int contentX = dockX + 10;
            int contentW = dockW - 20;

            this.dockCloseBtnRect = new UiLayout.Rect(dockX + dockW - 24, dockY + 7, 16, 16);

            int curY = dockY + 40;
            curY += 12;
            this.presetDropdown = new PresetDropdownWidget(contentX, curY, contentW, 20, this::onPresetSelected);
            curY += 24;
            int btnW = (contentW - 4) / 2;
            this.savePresetBtnRect = new UiLayout.Rect(contentX, curY, btnW, 19);
            this.saveAsPresetBtnRect = new UiLayout.Rect(contentX + btnW + 4, curY, contentW - btnW - 4, 19);
            curY += 23;
            this.resetPresetBtnRect = new UiLayout.Rect(contentX, curY, btnW, 19);
            this.deletePresetBtnRect = new UiLayout.Rect(contentX + btnW + 4, curY, contentW - btnW - 4, 19);
        } else {
            this.dockCloseBtnRect = null;
            this.presetDropdown = null;
            this.savePresetBtnRect = null;
            this.saveAsPresetBtnRect = null;
            this.deletePresetBtnRect = null;
            this.resetPresetBtnRect = null;
        }

        this.refreshPresetList();

        String rememberedPreset = LAST_ACTIVE_PRESET_NAMES.get(this.gameId());
        if (rememberedPreset != null && !rememberedPreset.isBlank()) {
            this.activePresetName = rememberedPreset;
            if (this.presetDropdown != null) {
                this.presetDropdown.setSelected(rememberedPreset);
            }
        }

        if (this.rosterGrid != null) {
            this.rosterGrid.setBounds(this.layout.contentArea());
            if (this.rosterGrid instanceof dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) {
                this.selectAllButtonRect = new UiLayout.Rect(this.layout.actionStartX(), this.layout.actionY(), 90, StandardWorkspaceLayout.BUTTON_HEIGHT);
                this.clearButtonRect = new UiLayout.Rect(this.layout.actionStartX() + 98, this.layout.actionY(), 70, StandardWorkspaceLayout.BUTTON_HEIGHT);
            }
        }
        if (this.mapGrid != null) {
            this.mapGrid.setBounds(this.layout.contentArea());
            this.mapGrid.setMaps(dev.frost.miniverse.client.gui.SessionSnapshotData.maps().stream().filter(map -> map.validFor(this.gameId())).toList());
        }
        this.initGamemode(screen);

        NbtCompound cachedSettings = LAST_ACTIVE_SETTINGS_CACHE.get(this.gameId());
        if (cachedSettings != null) {
            this.applyPresetSettings(cachedSettings);
        }
    }

    protected abstract void initGamemode(SessionScreen screen);

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        this.activeTooltips.clear();
        UiLayout.Rect mainPanel = this.layout.mainPanel();
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(mainPanel.x() + 1, mainPanel.y() + 1, mainPanel.x() + mainPanel.width() - 1, mainPanel.y() + 40, 0x701B2634);
        
        WorkspaceModuleManager.RegisteredModule active = this.moduleManager.getActiveModule();
        if (active != null) {
            context.drawText(textRenderer, Text.literal(active.label()), mainPanel.x() + 14, mainPanel.y() + 14, UiTheme.TEXT, false);
            context.drawText(textRenderer, Text.literal(active.description()), mainPanel.x() + 14, mainPanel.y() + 28, UiTheme.TEXT_DIM, false);
        }

        // Header Dock Toggle Button
        if (this.dockToggleBtnRect != null) {
            boolean hover = this.dockToggleBtnRect.contains(mouseX, mouseY);
            String toggleText = this.layout.isRightSidebarOpen() ? "Dock \u25B6" : "\u25F0 Dock";
            this.renderActionButton(context, textRenderer, this.dockToggleBtnRect, toggleText, UiTheme.ACCENT_BLUE, hover);
            this.activeTooltips.add(new TooltipZone(this.dockToggleBtnRect.x(), this.dockToggleBtnRect.y(), this.dockToggleBtnRect.width(), this.dockToggleBtnRect.height(), () -> this.layout.isRightSidebarOpen() ? "Collapse Match Dock" : "Expand Match Dock & Presets"));
        }

        // When dock is collapsed, render collapsed Start Button and quick validation badge in the header!
        if (!this.layout.isRightSidebarOpen()) {
            UiLayout.Rect startBtn = this.layout.startButton();
            ValidationResult validation = this.validateGamemodeStart();
            boolean canStart = validation.canStart();
            int startColor = canStart ? UiTheme.ACCENT_GREEN : UiTheme.BORDER_SUBTLE;
            this.renderActionButton(context, textRenderer, startBtn, "\u25B6 Start", startColor, canStart && startBtn.contains(mouseX, mouseY));
            if (!canStart) {
                this.activeTooltips.add(new TooltipZone(startBtn.x(), startBtn.y(), startBtn.width(), startBtn.height(), () -> "\u26A0 " + validation.message()));
            }

            int dotX = startBtn.x() - 14;
            int dotY = startBtn.y() + 7;
            context.fill(dotX, dotY, dotX + 8, dotY + 8, canStart ? UiTheme.ACCENT_GREEN : UiTheme.ACCENT_RED);
            this.activeTooltips.add(new TooltipZone(dotX - 2, dotY - 2, 12, 12, () -> canStart ? "\u2714 Ready to Launch" : ("\u26A0 " + validation.message())));
        }

        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
             if (this.selectAllButtonRect != null && this.clearButtonRect != null) {
                 this.renderActionButton(context, textRenderer, this.selectAllButtonRect, "Select All", UiTheme.ACCENT_BLUE, this.selectAllButtonRect.contains(mouseX, mouseY));
                 this.renderActionButton(context, textRenderer, this.clearButtonRect, "Clear", UiTheme.ACCENT, this.clearButtonRect.contains(mouseX, mouseY));
             }
             this.rosterGrid.render(context, textRenderer, mouseX, mouseY, delta);
        }
        if (this.mapGrid != null && this.moduleManager.isActive("map")) {
             this.mapGrid.render(context, textRenderer, mouseX, mouseY, delta);
        }

        this.renderGamemodeBackground(context, textRenderer, mouseX, mouseY, delta);

        if (this.status != null && !this.status.message().isBlank()) {
            String msg = this.status.message();
            int maxStatusW = mainPanel.width() - 28;
            int msgW = textRenderer.getWidth(msg);
            int statusX = mainPanel.x() + 14;
            int statusY = mainPanel.y() + mainPanel.height() - 18;
            int statusColor = this.status.type().color();

            if (msgW > maxStatusW) {
                int overflow = msgW - maxStatusW + 16;
                long time = System.currentTimeMillis();
                long cycle = 4000L + (long) overflow * 45L;
                long progress = time % cycle;
                int offset;
                if (progress < 1500L) {
                    offset = 0;
                } else if (progress < cycle - 1000L) {
                    float t = (float) (progress - 1500L) / (float) (cycle - 2500L);
                    offset = (int) (t * overflow);
                } else {
                    offset = overflow;
                }
                context.enableScissor(statusX, statusY - 2, statusX + maxStatusW, statusY + 14);
                context.drawText(textRenderer, Text.literal(msg), statusX - offset, statusY, statusColor, false);
                context.disableScissor();
            } else {
                context.drawText(textRenderer, Text.literal(msg), statusX, statusY, statusColor, false);
            }
        }

        if (this.layout.isRightSidebarOpen()) {
            this.renderRightDock(context, textRenderer, mouseX, mouseY, delta);
        }
    }

    private void renderRightDock(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        UiLayout.Rect dock = this.layout.rightSidebar();
        if (dock == null) return;

        int dockX = dock.x();
        int dockY = dock.y();
        int dockW = dock.width();
        int dockH = dock.height();
        int contentX = dockX + 10;
        int contentW = dockW - 20;

        UiRenderer.panel(context, dockX, dockY, dockW, dockH, UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(dockX + 1, dockY + 1, dockX + dockW - 1, dockY + 32, 0x701B2634);
        context.drawText(textRenderer, Text.literal("MATCH DOCK"), contentX, dockY + 12, UiTheme.TEXT, false);

        if (this.dockCloseBtnRect != null) {
            boolean closeHover = this.dockCloseBtnRect.contains(mouseX, mouseY);
            context.fill(this.dockCloseBtnRect.x(), this.dockCloseBtnRect.y(), this.dockCloseBtnRect.x() + this.dockCloseBtnRect.width(), this.dockCloseBtnRect.y() + this.dockCloseBtnRect.height(), closeHover ? 0x40FFFFFF : 0x20FFFFFF);
            context.drawText(textRenderer, Text.literal("\u2715"), this.dockCloseBtnRect.x() + 4, this.dockCloseBtnRect.y() + 4, closeHover ? UiTheme.ACCENT_RED : UiTheme.TEXT_MUTED, false);
            this.activeTooltips.add(new TooltipZone(this.dockCloseBtnRect.x(), this.dockCloseBtnRect.y(), this.dockCloseBtnRect.width(), this.dockCloseBtnRect.height(), () -> "Collapse Match Dock"));
        }

        // Section 1: Presets
        int curY = dockY + 40;
        context.drawText(textRenderer, Text.literal("PRESET CONFIG"), contentX, curY, UiTheme.TEXT_DIM, false);
        curY += 12;

        if (this.presetDropdown != null) {
            this.presetDropdown.renderButton(context, textRenderer, mouseX, mouseY);
        }
        curY += 24;

        if (this.savePresetBtnRect != null) {
            this.renderActionButton(context, textRenderer, this.savePresetBtnRect, "Save", UiTheme.ACCENT_BLUE, this.savePresetBtnRect.contains(mouseX, mouseY));
            this.activeTooltips.add(new TooltipZone(this.savePresetBtnRect.x(), this.savePresetBtnRect.y(), this.savePresetBtnRect.width(), this.savePresetBtnRect.height(), () -> "Save current settings to '" + this.activePresetName + "'"));
        }
        if (this.saveAsPresetBtnRect != null) {
            this.renderActionButton(context, textRenderer, this.saveAsPresetBtnRect, "Save As", UiTheme.ACCENT, this.saveAsPresetBtnRect.contains(mouseX, mouseY));
            this.activeTooltips.add(new TooltipZone(this.saveAsPresetBtnRect.x(), this.saveAsPresetBtnRect.y(), this.saveAsPresetBtnRect.width(), this.saveAsPresetBtnRect.height(), () -> "Save configuration as a new named preset"));
        }
        curY += 23;

        if (this.resetPresetBtnRect != null) {
            this.renderActionButton(context, textRenderer, this.resetPresetBtnRect, "Reset", UiTheme.ACCENT_BLUE, this.resetPresetBtnRect.contains(mouseX, mouseY));
            this.activeTooltips.add(new TooltipZone(this.resetPresetBtnRect.x(), this.resetPresetBtnRect.y(), this.resetPresetBtnRect.width(), this.resetPresetBtnRect.height(), () -> "Revert all settings to Default preset"));
        }
        if (this.deletePresetBtnRect != null) {
            boolean canDelete = !this.activePresetName.equalsIgnoreCase("Default");
            int delFill = canDelete ? UiTheme.ACCENT_RED : UiTheme.BORDER_SUBTLE;
            this.renderActionButton(context, textRenderer, this.deletePresetBtnRect, "Delete", delFill, canDelete && this.deletePresetBtnRect.contains(mouseX, mouseY));
            this.activeTooltips.add(new TooltipZone(this.deletePresetBtnRect.x(), this.deletePresetBtnRect.y(), this.deletePresetBtnRect.width(), this.deletePresetBtnRect.height(), () -> canDelete ? "Delete preset '" + this.activePresetName + "' from server" : "Default preset cannot be deleted"));
        }
        curY += 27;

        // Divider
        context.fill(contentX, curY, contentX + contentW, curY + 1, UiTheme.BORDER_SUBTLE);
        curY += 8;

        // Section 2: Match Overview
        context.drawText(textRenderer, Text.literal("MATCH OVERVIEW"), contentX, curY, UiTheme.ACCENT, false);
        curY += 13;

        String sessText = "Session: " + this.sessionName;
        if (textRenderer.getWidth(sessText) > contentW) {
            sessText = textRenderer.trimToWidth(sessText, contentW - 8) + "..";
        }
        context.drawText(textRenderer, Text.literal(sessText), contentX, curY, UiTheme.TEXT_MUTED, false);
        curY += 14;

        this.syncStateFromWidgets();
        List<Text> summaryLines = this.getSummaryLines();
        
        int maxSummaryBottom = dockY + dockH - 64;
        for (Text line : summaryLines) {
            if (curY + 12 > maxSummaryBottom) {
                context.drawText(textRenderer, Text.literal("§7..."), contentX, curY, UiTheme.TEXT_DIM, false);
                break;
            }
            Text formatted = this.formatSummaryLine(line);
            String displayStr = formatted.getString();
            if (textRenderer.getWidth(displayStr) > contentW) {
                displayStr = textRenderer.trimToWidth(displayStr, contentW - 6) + "..";
                formatted = Text.literal(displayStr);
            }
            context.drawText(textRenderer, formatted, contentX, curY, UiTheme.TEXT, false);
            curY += 13;
        }

        // Section 3: Live Validation & Status Badge
        ValidationResult validation = this.validateGamemodeStart();
        boolean canStart = validation.canStart();
        int badgeY = dockY + dockH - 58;
        int badgeH = 18;

        int badgeBg = canStart ? 0x2500FF66 : 0x25FF4444;
        int badgeBorder = canStart ? UiTheme.ACCENT_GREEN : UiTheme.ACCENT_RED;
        context.fill(contentX, badgeY, contentX + contentW, badgeY + badgeH, badgeBg);
        context.fill(contentX, badgeY, contentX + contentW, badgeY + 1, badgeBorder);
        context.fill(contentX, badgeY + badgeH - 1, contentX + contentW, badgeY + badgeH, badgeBorder);
        context.fill(contentX, badgeY, contentX + 1, badgeY + badgeH, badgeBorder);
        context.fill(contentX + contentW - 1, badgeY, contentX + contentW, badgeY + badgeH, badgeBorder);

        String badgeText = canStart ? "\u2714 Ready to Launch" : ("\u26A0 " + validation.message());
        int badgeTextColor = canStart ? UiTheme.ACCENT_GREEN : UiTheme.ACCENT_RED;
        int maxBadgeW = contentW - 12;
        int badgeTextW = textRenderer.getWidth(badgeText);
        int badgeX = contentX + 6;
        int badgeYPos = badgeY + 5;

        if (badgeTextW > maxBadgeW) {
            int overflow = badgeTextW - maxBadgeW + 16;
            long time = System.currentTimeMillis();
            long cycle = 4000L + (long) overflow * 45L;
            long progress = time % cycle;
            int offset;
            if (progress < 1500L) {
                offset = 0;
            } else if (progress < cycle - 1000L) {
                float t = (float) (progress - 1500L) / (float) (cycle - 2500L);
                offset = (int) (t * overflow);
            } else {
                offset = overflow;
            }
            context.enableScissor(contentX + 2, badgeY, contentX + contentW - 2, badgeY + badgeH);
            context.drawText(textRenderer, Text.literal(badgeText), badgeX - offset, badgeYPos, badgeTextColor, false);
            context.disableScissor();
        } else {
            context.drawText(textRenderer, Text.literal(badgeText), badgeX, badgeYPos, badgeTextColor, false);
        }
        this.activeTooltips.add(new TooltipZone(contentX, badgeY, contentW, badgeH, () -> canStart ? "Configuration valid. Ready to launch match." : ("Validation issue: " + validation.message())));

        // Section 4: Start Match Button
        UiLayout.Rect startBtn = this.layout.startButton();
        int startFill = canStart ? UiTheme.ACCENT_GREEN : UiTheme.BORDER_SUBTLE;
        this.renderActionButton(context, textRenderer, startBtn, "\u25B6 START MATCH", startFill, canStart && startBtn.contains(mouseX, mouseY));
        if (!canStart) {
            this.activeTooltips.add(new TooltipZone(startBtn.x(), startBtn.y(), startBtn.width(), startBtn.height(), () -> "Cannot start: " + validation.message()));
        }
    }

    protected Text formatSummaryLine(Text raw) {
        String str = raw.getString();
        if (str.contains("§")) {
            return Text.literal("§7• ").append(raw);
        }
        int colonIdx = str.indexOf(':');
        if (colonIdx > 0 && colonIdx + 1 < str.length()) {
            String prop = str.substring(0, colonIdx).trim();
            String val = str.substring(colonIdx + 1).trim();

            String valColor = "§e";
            String valLower = val.toLowerCase(java.util.Locale.ROOT);
            if (valLower.equals("on") || valLower.equals("true") || valLower.equals("enabled") || valLower.equals("yes")) {
                valColor = "§a";
            } else if (valLower.equals("off") || valLower.equals("false") || valLower.equals("disabled") || valLower.equals("none") || valLower.equals("no")) {
                valColor = "§c";
            } else if (valLower.contains("hard") || valLower.contains("insane") || valLower.contains("endless")) {
                valColor = "§b";
            } else if (valLower.equals("easy") || valLower.equals("normal")) {
                valColor = "§a";
            }
            return Text.literal("§7• " + prop + ": " + valColor + val);
        }
        return Text.literal("§7• §f" + str);
    }

    public static Text summaryProp(String label, Object value) {
        return Text.literal("§7" + label + ": §e" + value);
    }

    public static Text summaryProp(String label, Object value, String colorCode) {
        return Text.literal("§7" + label + ": " + colorCode + value);
    }

    public static Text summaryToggle(String label, boolean enabled) {
        return Text.literal("§7" + label + ": " + (enabled ? "§aON" : "§cOFF"));
    }

    protected abstract void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta);

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            this.rosterGrid.renderForeground(context, textRenderer, workspace, mouseX, mouseY, delta);
        }

        if (this.moduleManager.isActive("rules") && this.rulesLayout != null) {
            this.rulesLayout.renderForeground(context, textRenderer);
        }

        this.renderGamemodeForeground(context, textRenderer, mouseX, mouseY, delta);

        if (this.layout.isRightSidebarOpen() && this.presetDropdown != null) {
            this.presetDropdown.renderDropdown(context, textRenderer, mouseX, mouseY);
        }

        for (TooltipZone zone : this.activeTooltips) {
            if (mouseX >= zone.x() && mouseX < zone.x() + zone.width() && mouseY >= zone.y() && mouseY < zone.y() + zone.height()) {
                context.drawTooltip(textRenderer, Text.literal(zone.text().get()), mouseX, mouseY);
                break;
            }
        }
    }
    
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        if (this.dockToggleBtnRect != null && this.dockToggleBtnRect.contains(mouseX, mouseY)) {
            rightSidebarOpen = !rightSidebarOpen;
            if (this.currentScreen != null && this.lastWorkspaceRect != null) {
                this.init(this.currentScreen, this.lastWorkspaceRect);
                this.currentScreen.rebuildWorkspaceChildren();
            }
            return true;
        }

        if (this.dockCloseBtnRect != null && this.dockCloseBtnRect.contains(mouseX, mouseY)) {
            rightSidebarOpen = false;
            if (this.currentScreen != null && this.lastWorkspaceRect != null) {
                this.init(this.currentScreen, this.lastWorkspaceRect);
                this.currentScreen.rebuildWorkspaceChildren();
            }
            return true;
        }

        if (this.layout.isRightSidebarOpen()) {
            if (this.presetDropdown != null && this.presetDropdown.isOpen()) {
                return this.presetDropdown.mouseClicked(mouseX, mouseY, button);
            }
            if (this.presetDropdown != null && this.presetDropdown.contains(mouseX, mouseY)) {
                return this.presetDropdown.mouseClicked(mouseX, mouseY, button);
            }
            if (this.savePresetBtnRect != null && this.savePresetBtnRect.contains(mouseX, mouseY)) {
                this.handleSavePreset();
                return true;
            }
            if (this.saveAsPresetBtnRect != null && this.saveAsPresetBtnRect.contains(mouseX, mouseY)) {
                this.handleSaveAsPreset();
                return true;
            }
            if (this.deletePresetBtnRect != null && this.deletePresetBtnRect.contains(mouseX, mouseY)) {
                this.handleDeletePreset();
                return true;
            }
            if (this.resetPresetBtnRect != null && this.resetPresetBtnRect.contains(mouseX, mouseY)) {
                this.handleResetPreset();
                return true;
            }
        }

        if (this.layout.startButton().contains(mouseX, mouseY)) {
            if (this.validateGamemodeStart().canStart()) {
                this.createSession();
            }
            return true;
        }
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            if (this.selectAllButtonRect != null && this.selectAllButtonRect.contains(mouseX, mouseY) && this.rosterGrid instanceof dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) {
                dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid staticGrid = (dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) this.rosterGrid;
                staticGrid.clear();
                String target = staticGrid.getColumnIds().size() > 1 ? staticGrid.getColumnIds().get(1) : "selected";
                for (dev.frost.miniverse.client.gui.SessionSnapshotData.RosterEntry entry : dev.frost.miniverse.client.gui.SessionSnapshotData.roster()) {
                    staticGrid.addMember(target, entry);
                }
                this.status = ValidationResult.info("Selected all players.");
                return true;
            }
            if (this.clearButtonRect != null && this.clearButtonRect.contains(mouseX, mouseY) && this.rosterGrid instanceof dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) {
                ((dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid) this.rosterGrid).clear();
                this.status = ValidationResult.info("Selection cleared.");
                return true;
            }
            if (this.rosterGrid.mouseClicked(mouseX, mouseY, button)) return true;
        }
        if (this.mapGrid != null && this.moduleManager.isActive("map")) {
            if (this.mapGrid.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return this.gamemodeMouseClicked(mouseX, mouseY, button);
    }

    public void onPresetsUpdated() {
        this.refreshPresetList();
    }

    private void refreshPresetList() {
        if (this.presetDropdown == null) return;
        List<String> names = new java.util.ArrayList<>();
        for (SessionSnapshotData.GamemodePresetEntry p : SessionSnapshotData.getPresets(this.gameId())) {
            names.add(p.name());
        }
        this.presetDropdown.setItems(names, this.activePresetName);
    }

    private void onPresetSelected(String presetName) {
        this.activePresetName = presetName;
        LAST_ACTIVE_PRESET_NAMES.put(this.gameId(), presetName);
        if (presetName.equalsIgnoreCase("Default")) {
            this.resetToDefaultSettings();
            LAST_ACTIVE_SETTINGS_CACHE.put(this.gameId(), this.exportPresetSettings());
            this.status = ValidationResult.info("Switched to Default preset.");
        } else {
            for (SessionSnapshotData.GamemodePresetEntry p : SessionSnapshotData.getPresets(this.gameId())) {
                if (p.name().equalsIgnoreCase(presetName)) {
                    this.applyPresetSettings(p.settings());
                    LAST_ACTIVE_SETTINGS_CACHE.put(this.gameId(), p.settings().copy());
                    this.status = ValidationResult.success("Loaded preset '" + presetName + "'.");
                    break;
                }
            }
        }
        if (this.currentScreen != null) {
            this.currentScreen.rebuildWorkspaceChildren();
        }
    }

    private void handleSavePreset() {
        if (this.activePresetName.equalsIgnoreCase("Default")) {
            this.handleSaveAsPreset();
            return;
        }
        this.syncStateFromWidgets();
        NbtCompound settings = this.exportPresetSettings();
        LAST_ACTIVE_SETTINGS_CACHE.put(this.gameId(), settings.copy());
        ClientPlayNetworking.send(
            new NetworkConstants.SaveGamemodePresetPayload(this.gameId(), this.activePresetName, settings, true)
        );
        this.status = ValidationResult.success("Saved preset '" + this.activePresetName + "'.");
    }

    private void handleSaveAsPreset() {
        this.syncStateFromWidgets();
        List<String> names = new java.util.ArrayList<>();
        for (SessionSnapshotData.GamemodePresetEntry p : SessionSnapshotData.getPresets(this.gameId())) {
            names.add(p.name());
        }

        this.client.setScreen(new SavePresetPopupScreen(
            this.currentScreen,
            this.activePresetName,
            names,
            chosenName -> {
                this.syncStateFromWidgets();
                NbtCompound settings = this.exportPresetSettings();
                LAST_ACTIVE_SETTINGS_CACHE.put(this.gameId(), settings.copy());
                ClientPlayNetworking.send(
                    new NetworkConstants.SaveGamemodePresetPayload(this.gameId(), chosenName, settings, true)
                );
                this.activePresetName = chosenName;
                LAST_ACTIVE_PRESET_NAMES.put(this.gameId(), chosenName);
                if (this.presetDropdown != null) {
                    this.presetDropdown.setSelected(chosenName);
                }
                this.status = ValidationResult.success("Saved preset '" + chosenName + "'.");
            }
        ));
    }

    private void handleDeletePreset() {
        if (this.activePresetName.equalsIgnoreCase("Default")) {
            return;
        }
        String toDelete = this.activePresetName;
        ClientPlayNetworking.send(
            new NetworkConstants.DeleteGamemodePresetPayload(this.gameId(), toDelete)
        );
        this.activePresetName = "Default";
        LAST_ACTIVE_PRESET_NAMES.put(this.gameId(), "Default");
        if (this.presetDropdown != null) {
            this.presetDropdown.setSelected("Default");
        }
        this.resetToDefaultSettings();
        LAST_ACTIVE_SETTINGS_CACHE.put(this.gameId(), this.exportPresetSettings());
        this.status = ValidationResult.info("Deleted preset '" + toDelete + "'. Reverted to Default.");
        if (this.currentScreen != null) {
            this.currentScreen.rebuildWorkspaceChildren();
        }
    }

    private void handleResetPreset() {
        this.activePresetName = "Default";
        LAST_ACTIVE_PRESET_NAMES.put(this.gameId(), "Default");
        if (this.presetDropdown != null) {
            this.presetDropdown.setSelected("Default");
        }
        this.resetToDefaultSettings();
        LAST_ACTIVE_SETTINGS_CACHE.put(this.gameId(), this.exportPresetSettings());
        this.status = ValidationResult.info("Reset to default settings.");
        if (this.currentScreen != null) {
            this.currentScreen.rebuildWorkspaceChildren();
        }
    }

    protected NbtCompound exportPresetSettings() {
        SessionPayloadBuilder builder = new SessionPayloadBuilder(this.gameId(), this.sessionName);
        this.buildSessionSettings(builder);
        return builder.settings();
    }

    protected void applyPresetSettings(NbtCompound settings) {
    }

    protected void resetToDefaultSettings() {
    }

    protected boolean gamemodeMouseClicked(double mouseX, double mouseY, int button) { return false; }

    protected void syncStateFromWidgets() {}

    protected java.util.List<Text> getSummaryLines() {
        return java.util.Collections.emptyList();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            return this.rosterGrid.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            return this.rosterGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.rosterGrid != null && (this.moduleManager.isActive("players") || this.moduleManager.isActive("teams"))) {
            return this.rosterGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        if (this.mapGrid != null && this.moduleManager.isActive("map")) {
            return this.mapGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
        }
        return this.gamemodeMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    protected boolean gamemodeMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) { return false; }

    @Override
    public void refreshRoster() {
        if (this.rosterGrid != null) {
            this.rosterGrid.refreshRoster();
        }
    }

    @Override
    public List<WorkspaceModule> modules() {
        return this.moduleManager.getVisibleModules();
    }

    @Override
    public String activeModuleId() {
        return this.moduleManager.getActiveModuleId();
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.moduleManager.setActiveModuleId(moduleId);
    }

    protected void renderActionButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(accent, 0.34F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, border);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    protected void addStepper(SessionScreen screen, TextFieldWidget field, int x, int y, int min, int max, int step) {
        screen.addWidget(ButtonWidget.builder(Text.literal("-"), btn -> {
            try {
                int val = Integer.parseInt(field.getText().trim());
                field.setText(Integer.toString(Math.max(min, val - step)));
            } catch (Exception ignored) {}
        }).dimensions(x, y, 20, 20).build());
        screen.addWidget(ButtonWidget.builder(Text.literal("+"), btn -> {
            try {
                int val = Integer.parseInt(field.getText().trim());
                field.setText(Integer.toString(Math.min(max, val + step)));
            } catch (Exception ignored) {}
        }).dimensions(x + 22, y, 20, 20).build());
    }

    private ButtonWidget addButton(SessionScreen screen, String label, int x, int y, int width, java.util.function.Supplier<String> tooltip, Runnable action) {
        ButtonWidget btn = new ButtonWidget.Builder(Text.literal(label), b -> action.run())
            .dimensions(x, y, width, 20)
            .build();
        screen.addWidget(btn);
        if (tooltip != null) {
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        }
        return btn;
    }

    protected TextFieldWidget addField(SessionScreen screen, int x, int y, String value, int width, String placeholder, java.util.function.Supplier<String> tooltip) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(256);
        field.setText(value);
        screen.addWidget(field);
        if (tooltip != null) {
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        }
        return field;
    }

    protected TextFieldWidget addField(SessionScreen screen, int x, int y, String value, String placeholder, java.util.function.Supplier<String> tooltip) {
        return this.addField(screen, x, y, value, 120, placeholder, tooltip);
    }

    protected dev.frost.miniverse.client.gui.ui.IntFieldWidget addIntField(SessionScreen screen, int x, int y, int value, int width, String placeholder, String zeroText, java.util.function.Function<Integer, String> activeText) {
        dev.frost.miniverse.client.gui.ui.IntFieldWidget field = new dev.frost.miniverse.client.gui.ui.IntFieldWidget(this.client.textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(256);
        field.setText(Integer.toString(value));
        screen.addWidget(field);
        
        if (zeroText != null && activeText != null) {
            java.util.function.Supplier<String> tooltip = () -> {
                int val = field.getIntValue(0);
                return val <= 0 ? zeroText : activeText.apply(val);
            };
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width + 44) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        }
        return field;
    }

    protected dev.frost.miniverse.client.gui.ui.IntFieldWidget addIntField(SessionScreen screen, int x, int y, int value, String placeholder, String zeroText, java.util.function.Function<Integer, String> activeText) {
        return this.addIntField(screen, x, y, value, 120, placeholder, zeroText, activeText);
    }

    protected dev.frost.miniverse.client.gui.ui.IntFieldWidget addIntField(SessionScreen screen, int x, int y, int value, int width, String placeholder, java.util.function.Function<Integer, String> tooltipFormatter) {
        dev.frost.miniverse.client.gui.ui.IntFieldWidget field = new dev.frost.miniverse.client.gui.ui.IntFieldWidget(this.client.textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(256);
        field.setText(Integer.toString(value));
        screen.addWidget(field);
        
        if (tooltipFormatter != null) {
            java.util.function.Supplier<String> tooltip = () -> tooltipFormatter.apply(field.getIntValue(0));
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width + 44) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, tooltip));
        }
        return field;
    }

    protected dev.frost.miniverse.client.gui.ui.IntFieldWidget addIntField(SessionScreen screen, int x, int y, int value, String placeholder, java.util.function.Function<Integer, String> tooltipFormatter) {
        return this.addIntField(screen, x, y, value, 120, placeholder, tooltipFormatter);
    }

    protected ButtonWidget addToggleButton(SessionScreen screen, String labelPrefix, java.util.function.Supplier<Boolean> stateSupplier, int x, int y, int width, BinaryTooltip tooltip, Runnable onToggle) {
        ButtonWidget btn = new ButtonWidget.Builder(Text.literal(labelPrefix + ": " + (stateSupplier.get() ? "ON" : "OFF")), b -> {
            onToggle.run();
            b.setMessage(Text.literal(labelPrefix + ": " + (stateSupplier.get() ? "ON" : "OFF")));
        }).dimensions(x, y, width, 20).build();
        screen.addWidget(btn);
        
        int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
        int zoneW = (x + width) - zoneX;
        this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, () -> tooltip.resolve(stateSupplier.get())));
        
        return btn;
    }



    protected ButtonWidget addCycleButton(SessionScreen screen, java.util.function.Supplier<String> labelSupplier, java.util.function.Supplier<Integer> cycleIndexSupplier, int x, int y, int width, String[] stateTooltips, int cycleLength, Runnable onCycle) {
        if (stateTooltips.length != cycleLength) {
            throw new IllegalArgumentException("addCycleButton: stateTooltips length (" + stateTooltips.length + ") must match cycle length (" + cycleLength + ")");
        }
        ButtonWidget btn = new ButtonWidget.Builder(Text.literal(labelSupplier.get()), b -> {
            onCycle.run();
            b.setMessage(Text.literal(labelSupplier.get()));
        }).dimensions(x, y, width, 20).build();
        screen.addWidget(btn);

        int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
        int zoneW = (x + width) - zoneX;
        this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, () -> stateTooltips[cycleIndexSupplier.get()]));

        return btn;
    }

    protected ButtonWidget addActionButton(SessionScreen screen, String label, int x, int y, int width, String tooltip, Runnable action) {
        ButtonWidget btn = new ButtonWidget.Builder(Text.literal(label), b -> action.run())
            .dimensions(x, y, width, 20)
            .build();
        screen.addWidget(btn);
        
        if (tooltip != null) {
            int zoneX = Math.max(this.layout.mainPanel().x() + 14, x - 145);
            int zoneW = (x + width) - zoneX;
            this.activeTooltips.add(new TooltipZone(zoneX, y - 4, zoneW, 28, () -> tooltip));
        }
        return btn;
    }

    protected void drawLabel(DrawContext context, TextRenderer textRenderer, String label, int x, int y) {
        context.drawText(textRenderer, Text.literal(label), x, y, UiTheme.TEXT_MUTED, false);
    }
    
    protected void renderSettingsModulePanel(DrawContext context, TextRenderer textRenderer, String title, int accent) {
        int moduleX = this.layout.mainPanel().x() + 14;
        int moduleY = this.layout.mainPanel().y() + 72;
        int moduleWidth = this.layout.mainPanel().width() - 28;
        int moduleHeight = this.layout.mainPanel().height() - 104;
        UiRenderer.panel(context, moduleX, moduleY, moduleWidth, moduleHeight, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(moduleX, moduleY, moduleX + 3, moduleY + moduleHeight, accent);
        context.drawText(textRenderer, Text.literal(title), moduleX + 12, moduleY + 12, accent, false);
    }

    protected int readClamped(dev.frost.miniverse.client.gui.ui.IntFieldWidget field, int fallback, int min, int max) {
        if (field == null) return fallback;
        int value = Math.clamp(field.getIntValue(fallback), min, max);
        field.setText(Integer.toString(value));
        return value;
    }

    protected void stepField(dev.frost.miniverse.client.gui.ui.IntFieldWidget field, int min, int max, int delta) {
        int value = this.readClamped(field, min, min, max);
        field.setText(Integer.toString(Math.clamp(value + delta, min, max)));
    }

    private void createSession() {
        if (this.client.player == null) {
            this.status = ValidationResult.error("Not connected to a server.");
            return;
        }
        if (this.sessionName == null || this.sessionName.isBlank()) {
            this.status = ValidationResult.error("Enter a session name.");
            return;
        }
        if (this.mapGrid != null && this.selectedMapId.isBlank()) {
            this.status = ValidationResult.error("Select a valid map first.");
            return;
        }
        
        ValidationResult gamemodeValidation = this.validateGamemodeStart();
        if (gamemodeValidation != null && !gamemodeValidation.canStart()) {
            this.status = gamemodeValidation;
            return;
        }
        
        SessionPayloadBuilder builder = new SessionPayloadBuilder(this.gameId(), this.sessionName);
        if (this.mapGrid != null) {
            builder.settings().putString("mapId", this.selectedMapId);
        }
        this.buildSessionSettings(builder);
        this.buildSessionGroups(builder);
        builder.dispatch();
        
        this.status = ValidationResult.success("Requested " + this.title() + " session creation.");
    }

    protected abstract ValidationResult validateGamemodeStart();
    protected abstract void buildSessionSettings(SessionPayloadBuilder builder);
    protected abstract void buildSessionGroups(SessionPayloadBuilder builder);

    public interface WidgetFactory {
        void create(SessionScreen screen, int x, int y, int width);
    }

    protected class SettingsLayoutBuilder {
        private int currentY;
        private final SessionScreen screen;
        private final java.util.List<java.util.function.BiConsumer<DrawContext, TextRenderer>> foregroundRenderers = new java.util.ArrayList<>();

        public SettingsLayoutBuilder(SessionScreen screen) {
            this.screen = screen;
            this.currentY = layout.mainPanel().y() + 104;
        }

        public void addHeading(String text) {
            int y = this.currentY;
            int x = layout.mainPanel().x() + 24;
            this.foregroundRenderers.add((context, textRenderer) -> context.drawText(textRenderer, Text.literal(text), x, y + 4, UiTheme.ACCENT_BLUE, false));
            this.currentY += 24;
        }

        public void addRow(String leftLabel, WidgetFactory leftWidget, String rightLabel, WidgetFactory rightWidget) {
            int y = this.currentY;
            int totalW = layout.mainPanel().width() - 48;
            int halfW = totalW / 2;
            int lx1 = layout.mainPanel().x() + 24;
            int widgetW = Math.max(100, Math.min(170, halfW - 116));
            int cx1 = lx1 + 112;
            int lx2 = lx1 + halfW;
            int cx2 = lx2 + 112;

            if (leftLabel != null && leftWidget != null) {
                this.foregroundRenderers.add((context, textRenderer) -> context.drawText(textRenderer, Text.literal(leftLabel), lx1, y + 6, UiTheme.TEXT_MUTED, false));
                leftWidget.create(this.screen, cx1, y, widgetW);
            }
            if (rightLabel != null && rightWidget != null) {
                this.foregroundRenderers.add((context, textRenderer) -> context.drawText(textRenderer, Text.literal(rightLabel), lx2, y + 6, UiTheme.TEXT_MUTED, false));
                rightWidget.create(this.screen, cx2, y, widgetW);
            }
            this.currentY += 32;
        }

        public void addRow(String leftLabel, WidgetFactory leftWidget) {
            this.addRow(leftLabel, leftWidget, null, null);
        }

        public void addFullRow(String label, WidgetFactory widget) {
            int y = this.currentY;
            int lx1 = layout.mainPanel().x() + 24;
            int cx1 = lx1 + 126;
            if (label != null && widget != null) {
                this.foregroundRenderers.add((context, textRenderer) -> context.drawText(textRenderer, Text.literal(label), lx1, y + 6, UiTheme.TEXT_MUTED, false));
                widget.create(this.screen, cx1, y, layout.mainPanel().width() - 174);
            }
            this.currentY += 32;
        }

        public void renderForeground(DrawContext context, TextRenderer textRenderer) {
            for (java.util.function.BiConsumer<DrawContext, TextRenderer> renderer : this.foregroundRenderers) {
                renderer.accept(context, textRenderer);
            }
        }
    }
}
