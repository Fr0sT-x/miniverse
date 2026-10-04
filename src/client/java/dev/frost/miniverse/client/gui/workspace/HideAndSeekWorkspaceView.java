package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.providers.BlockRegistryProvider;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.StandardWorkspaceLayout;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceModuleManager;
import dev.frost.miniverse.minigame.impl.hideandseek.HideAndSeekDefinition;
import dev.frost.miniverse.minigame.impl.hideandseek.HideAndSeekSettings;
import dev.frost.miniverse.minigame.impl.hideandseek.disguise.DisguiseType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class HideAndSeekWorkspaceView extends AbstractGamemodeWorkspaceView {
    public record BlockRowButton(UiLayout.Rect rect, String blockId, String displayName, ItemStack icon, String numberText) {}

    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private final List<String> detectedBlocks = new ArrayList<>();
    private final Set<String> enabledBlocks = new HashSet<>();
    private String lastSyncedMapId = null;
    private int blockListScrollOffset = 0;
    private final RegistrySelectorState selectorState = new RegistrySelectorState();

    private UiLayout.Rect inspectBlocksRect;
    private UiLayout.Rect backToMapRect;
    private UiLayout.Rect selectAllRect;
    private UiLayout.Rect deselectAllRect;
    private UiLayout.Rect registrySelectorRect;
    private final List<BlockRowButton> blockRowButtons = new ArrayList<>();
    private UiLayout.Rect prevPageRect;
    private UiLayout.Rect nextPageRect;

    private SessionScreen sessionScreen;

    private IntFieldWidget hidingTimeField;
    private IntFieldWidget durationField;
    private IntFieldWidget seekerCountField;
    private IntFieldWidget tauntCooldownField;
    private IntFieldWidget sonarUnlockField;
    private ButtonWidget missPenaltyButton;

    private int hidingTimeSeconds = HideAndSeekSettings.DEFAULT_HIDING_TIME;
    private int durationSeconds = HideAndSeekSettings.DEFAULT_GAME_DURATION;
    private int seekerCount = HideAndSeekSettings.DEFAULT_SEEKER_COUNT;
    private double solidifyDelay = HideAndSeekSettings.DEFAULT_SOLIDIFY_DELAY;
    private float seekerMissPenalty = HideAndSeekSettings.DEFAULT_SEEKER_MISS_PENALTY;
    private int tauntCooldown = HideAndSeekSettings.DEFAULT_TAUNT_COOLDOWN;
    private int sonarUnlock = HideAndSeekSettings.DEFAULT_SONAR_UNLOCK;
    private float seekerPassiveRegenCap = HideAndSeekSettings.DEFAULT_PASSIVE_REGEN_CAP;

    public HideAndSeekWorkspaceView() {
        super(HideAndSeekDefinition.ID);
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for Hide and Seek.", UiTheme.ACCENT_RED, "Valid Hide and Seek Maps");
        this.moduleManager.register("blocks", "B", "Block Pool", "Setup", "Inspect and toggle disguise blocks for this match.", 0xFFFFAA00);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Tune hiding time, seekers, duration, and abilities.", UiTheme.ACCENT_BLUE);
    }

    private void syncBlocksFromSelectedMap() {
        if (this.selectedMapId == null || this.selectedMapId.isBlank()) {
            this.detectedBlocks.clear();
            this.enabledBlocks.clear();
            this.lastSyncedMapId = null;
            return;
        }

        boolean mapChanged = !this.selectedMapId.equals(this.lastSyncedMapId);

        SessionSnapshotData.MapSummary map = SessionSnapshotData.maps().stream()
            .filter(m -> m.id().equals(this.selectedMapId))
            .findFirst().orElse(null);

        this.detectedBlocks.clear();
        if (map != null) {
            for (String tag : map.tags()) {
                if (tag.startsWith("hideandseek_block:")) {
                    String blockId = normalizeBlockId(tag.substring("hideandseek_block:".length()));
                    if (!blockId.isBlank() && !this.detectedBlocks.contains(blockId)) {
                        this.detectedBlocks.add(blockId);
                    }
                }
            }
        }

        if (this.detectedBlocks.isEmpty()) {
            for (DisguiseType d : DisguiseType.ALL) {
                String id = normalizeBlockId(d.id());
                if (!this.detectedBlocks.contains(id)) {
                    this.detectedBlocks.add(id);
                }
            }
        }

        if (mapChanged) {
            this.lastSyncedMapId = this.selectedMapId;
            this.enabledBlocks.clear();
            this.enabledBlocks.addAll(this.detectedBlocks);
            this.blockListScrollOffset = 0;
        } else {
            this.enabledBlocks.removeIf(id -> !this.detectedBlocks.contains(id));
        }
    }

    private static String normalizeBlockId(String idStr) {
        if (idStr == null || idStr.isBlank()) return "";
        String clean = idStr.trim().toLowerCase();
        if (clean.startsWith("minecraft:")) {
            clean = clean.substring("minecraft:".length());
        }
        return clean;
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

    private void rebuildBlockRowLayout() {
        this.blockRowButtons.clear();
        this.prevPageRect = null;
        this.nextPageRect = null;

        if (this.layout == null) return;

        int startX = this.layout.mainPanel().x() + 26;
        int startY = this.layout.mainPanel().y() + 104;

        if (this.selectedMapId == null || this.selectedMapId.isBlank()) {
            this.backToMapRect = new UiLayout.Rect(this.layout.actionStartX(), this.layout.actionY(), 160, StandardWorkspaceLayout.BUTTON_HEIGHT);
            this.selectAllRect = null;
            this.deselectAllRect = null;
            this.registrySelectorRect = null;
            return;
        }

        this.backToMapRect = null;
        this.selectAllRect = new UiLayout.Rect(startX, startY, 78, 20);
        this.deselectAllRect = new UiLayout.Rect(startX + 84, startY, 84, 20);
        this.registrySelectorRect = new UiLayout.Rect(startX + 174, startY, 130, 20);

        int rowY = startY + 28;
        int maxRows = this.calculateMaxVisibleRows();
        int total = this.detectedBlocks.size();

        if (this.blockListScrollOffset + maxRows > total) {
            this.blockListScrollOffset = Math.max(0, total - maxRows);
        }

        for (int i = 0; i < maxRows && (i + this.blockListScrollOffset) < total; i++) {
            int index = i + this.blockListScrollOffset;
            String blockId = this.detectedBlocks.get(index);
            DisguiseType disguise = DisguiseType.fromId(blockId);
            int currentY = rowY + (i * 24);
            this.blockRowButtons.add(new BlockRowButton(
                new UiLayout.Rect(startX + 24, currentY, 280, 20),
                blockId,
                disguise.displayName(),
                disguise.icon(),
                (index + 1) + "."
            ));
        }

        if (total > maxRows) {
            this.prevPageRect = new UiLayout.Rect(startX + 314, rowY, 76, 20);
            this.nextPageRect = new UiLayout.Rect(startX + 314, rowY + 28, 76, 20);
        }
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        this.sessionScreen = screen;
        this.syncBlocksFromSelectedMap();

        if (this.moduleManager.isActive("map")) {
            if (this.selectedMapId != null && !this.selectedMapId.isBlank()) {
                int btnX = this.layout.actionStartX();
                int btnY = this.layout.actionY();
                this.inspectBlocksRect = new UiLayout.Rect(btnX, btnY, 200, StandardWorkspaceLayout.BUTTON_HEIGHT);
            } else {
                this.inspectBlocksRect = null;
            }
        } else if (this.moduleManager.isActive("blocks")) {
            this.rebuildBlockRowLayout();
        } else if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Hiding Grace Period", (s, x, y, w) -> {
                    this.hidingTimeField = this.addIntField(s, x, y, this.hidingTimeSeconds, w, "Grace period", val -> "Hiders will have " + val + " seconds to hide before Seekers release.");
                }
            );

            this.rulesLayout.addRow(
                "Match Duration", (s, x, y, w) -> {
                    this.durationField = this.addIntField(s, x, y, this.durationSeconds, w, "Match duration", val -> "Total hunt duration will be " + val + " seconds (" + (val / 60) + "m).");
                }
            );

            this.rulesLayout.addRow(
                "Initial Seekers", (s, x, y, w) -> {
                    this.seekerCountField = this.addIntField(s, x, y, this.seekerCount, w, "Initial seekers", val -> "Match will start with " + val + " initial Seeker(s).");
                }
            );

            this.rulesLayout.addRow(
                "Taunt Cooldown", (s, x, y, w) -> {
                    this.tauntCooldownField = this.addIntField(s, x, y, this.tauntCooldown, w, "Taunt cooldown", val -> "Hiders can make taunt sounds every " + val + " seconds for bonus points.");
                }
            );

            this.rulesLayout.addRow(
                "Sonar Unlock", (s, x, y, w) -> {
                    this.sonarUnlockField = this.addIntField(s, x, y, this.sonarUnlock, w, "Sonar unlock", val -> "Seekers gain the tracking Sonar Compass in the final " + val + " seconds.");
                }
            );

            this.rulesLayout.addRow(
                "Miss Penalty", (s, x, y, w) -> {
                    this.missPenaltyButton = this.addToggleButton(s, "Seeker Miss Penalty", () -> this.seekerMissPenalty > 0, x, y, w,
                        new BinaryTooltip(
                            "Seekers take 0.5 heart damage when attacking incorrect map blocks.",
                            "Seekers take no penalty when swinging at blocks."
                        ),
                        () -> this.seekerMissPenalty = this.seekerMissPenalty > 0 ? 0.0F : 1.0F);
                }
            );

            this.rulesLayout.addRow(
                "Rest Regen Cap", (s, x, y, w) -> {
                    this.addCycleButton(
                        s,
                        () -> "Rest Regen Cap: " + (this.seekerPassiveRegenCap >= 20.0F ? "10 Hearts (Full)" : "5 Hearts (Capped)"),
                        () -> this.seekerPassiveRegenCap >= 20.0F ? 1 : 0,
                        x, y, w,
                        new String[]{
                            "Resting passively recovers stamina up to 5 hearts. Full 10 hearts requires finding Hiders.",
                            "Resting passively recovers stamina all the way up to full 10 hearts."
                        },
                        2,
                        () -> this.seekerPassiveRegenCap = (this.seekerPassiveRegenCap >= 20.0F ? 10.0F : 20.0F)
                    );
                }
            );
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("map")) {
            if (this.selectedMapId != null && !this.selectedMapId.isBlank() && this.inspectBlocksRect != null) {
                String inspectLabel = "Inspect Blocks (" + this.enabledBlocks.size() + "/" + this.detectedBlocks.size() + ") ->";
                this.renderActionButton(context, textRenderer, this.inspectBlocksRect, inspectLabel, 0xFFFFAA00, this.inspectBlocksRect.contains(mouseX, mouseY));
            }
        } else if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        } else if (this.moduleManager.isActive("blocks")) {
            WorkspaceModuleManager.RegisteredModule active = this.moduleManager.getActiveModule();
            this.renderSettingsModulePanel(context, textRenderer, active.label(), active.accent());

            int startX = this.layout.mainPanel().x() + 26;
            int headerY = this.layout.mainPanel().y() + 84;

            if (this.selectedMapId == null || this.selectedMapId.isBlank()) {
                if (this.backToMapRect != null) {
                    this.renderActionButton(context, textRenderer, this.backToMapRect, "<- Map Selection", UiTheme.ACCENT_RED, this.backToMapRect.contains(mouseX, mouseY));
                }
                context.drawText(textRenderer, Text.literal("No map selected. Please choose a map in Map Selection first."), startX, headerY + 28, UiTheme.TEXT_MUTED, false);
            } else {
                String poolInfo = "Active Pool: " + this.enabledBlocks.size() + " / " + this.detectedBlocks.size() + " blocks enabled";
                context.drawText(textRenderer, Text.literal(poolInfo), startX, headerY + 1, UiTheme.TEXT_MUTED, false);

                if (this.selectAllRect != null) {
                    this.renderActionButton(context, textRenderer, this.selectAllRect, "Select All", UiTheme.ACCENT_BLUE, this.selectAllRect.contains(mouseX, mouseY));
                }
                if (this.deselectAllRect != null) {
                    this.renderActionButton(context, textRenderer, this.deselectAllRect, "Deselect All", UiTheme.ACCENT, this.deselectAllRect.contains(mouseX, mouseY));
                }
                if (this.registrySelectorRect != null) {
                    this.renderActionButton(context, textRenderer, this.registrySelectorRect, "+ Selector", 0xFFFFAA00, this.registrySelectorRect.contains(mouseX, mouseY));
                }

                for (BlockRowButton btn : this.blockRowButtons) {
                    boolean enabled = this.enabledBlocks.contains(btn.blockId());
                    context.drawText(textRenderer, Text.literal(btn.numberText()), startX, btn.rect().y() + 6, UiTheme.TEXT_MUTED, false);
                    this.renderBlockRowButton(context, textRenderer, btn.rect(), btn.displayName(), btn.icon(), enabled, btn.rect().contains(mouseX, mouseY));
                }

                int maxRows = this.calculateMaxVisibleRows();
                int total = this.detectedBlocks.size();
                if (total > maxRows) {
                    if (this.prevPageRect != null) {
                        boolean canPrev = this.blockListScrollOffset > 0;
                        int accent = canPrev ? UiTheme.ACCENT_BLUE : 0x444444;
                        this.renderActionButton(context, textRenderer, this.prevPageRect, "▲ Prev", accent, canPrev && this.prevPageRect.contains(mouseX, mouseY));
                    }
                    if (this.nextPageRect != null) {
                        boolean canNext = this.blockListScrollOffset + maxRows < total;
                        int accent = canNext ? UiTheme.ACCENT_BLUE : 0x444444;
                        this.renderActionButton(context, textRenderer, this.nextPageRect, "▼ Next", accent, canNext && this.nextPageRect.contains(mouseX, mouseY));
                    }
                    int showingFrom = this.blockListScrollOffset + 1;
                    int showingTo = Math.min(total, this.blockListScrollOffset + maxRows);
                    String pageInfo = showingFrom + "-" + showingTo + " of " + total;
                    int rowY = this.layout.mainPanel().y() + 132;
                    context.drawText(textRenderer, Text.literal(pageInfo), startX + 316, rowY + 58, UiTheme.TEXT_DIM, false);
                }
            }
        }
    }

    private void renderBlockRowButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, ItemStack icon, boolean enabled, boolean hovered) {
        int accent = enabled ? UiTheme.ACCENT_GREEN : 0x7C8088;
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(accent, 0.28F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, border);

        int squareSize = rect.height();
        int dividerX = rect.x() + squareSize;
        context.fill(rect.x() + 1, rect.y() + 1, dividerX, rect.y() + rect.height() - 1, 0x30000000);
        context.fill(dividerX, rect.y() + 2, dividerX + 1, rect.y() + rect.height() - 2, 0xD0FFFFFF);

        int textY = rect.y() + (rect.height() - 8) / 2;
        String tick = enabled ? "✓" : "—";
        int tickWidth = textRenderer.getWidth(tick);
        int tickX = rect.x() + (squareSize - tickWidth) / 2;
        int tickColor = enabled ? UiTheme.ACCENT_GREEN : UiTheme.TEXT_DIM;
        context.drawTextWithShadow(textRenderer, Text.literal(tick), tickX, textY, tickColor);

        if (icon != null && !icon.isEmpty()) {
            context.drawItem(icon, dividerX + 4, rect.y() + 2);
        }

        int textX = dividerX + 24;
        int maxTextWidth = Math.max(10, rect.width() - squareSize - 28);
        String trimmedLabel = textRenderer.trimToWidth(label, maxTextWidth);
        int textColor = enabled ? UiTheme.TEXT : UiTheme.TEXT_MUTED;
        context.drawTextWithShadow(textRenderer, Text.literal(trimmedLabel), textX, textY, textColor);
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
            if (this.inspectBlocksRect != null && this.inspectBlocksRect.contains(mouseX, mouseY)) {
                this.setActiveModule("blocks");
                if (this.sessionScreen != null) {
                    this.sessionScreen.rebuildWorkspaceChildren();
                }
                return true;
            }
        }

        if (this.moduleManager.isActive("blocks")) {
            if (this.backToMapRect != null && this.backToMapRect.contains(mouseX, mouseY)) {
                this.setActiveModule("map");
                if (this.sessionScreen != null) {
                    this.sessionScreen.rebuildWorkspaceChildren();
                }
                return true;
            }

            if (this.selectAllRect != null && this.selectAllRect.contains(mouseX, mouseY)) {
                this.enabledBlocks.addAll(this.detectedBlocks);
                this.status = ValidationResult.info("Selected all blocks (" + this.enabledBlocks.size() + ").");
                this.rebuildBlockRowLayout();
                return true;
            }

            if (this.deselectAllRect != null && this.deselectAllRect.contains(mouseX, mouseY)) {
                this.enabledBlocks.clear();
                this.status = ValidationResult.info("Deselected all blocks.");
                this.rebuildBlockRowLayout();
                return true;
            }

            if (this.registrySelectorRect != null && this.registrySelectorRect.contains(mouseX, mouseY)) {
                this.openRegistrySelector();
                return true;
            }

            for (BlockRowButton row : this.blockRowButtons) {
                if (row.rect().contains(mouseX, mouseY)) {
                    if (this.enabledBlocks.contains(row.blockId())) {
                        this.enabledBlocks.remove(row.blockId());
                    } else {
                        this.enabledBlocks.add(row.blockId());
                    }
                    this.rebuildBlockRowLayout();
                    return true;
                }
            }

            int maxRows = this.calculateMaxVisibleRows();
            int total = this.detectedBlocks.size();

            if (this.prevPageRect != null && this.prevPageRect.contains(mouseX, mouseY)) {
                if (this.blockListScrollOffset > 0) {
                    this.blockListScrollOffset = Math.max(0, this.blockListScrollOffset - 1);
                    this.rebuildBlockRowLayout();
                }
                return true;
            }

            if (this.nextPageRect != null && this.nextPageRect.contains(mouseX, mouseY)) {
                if (this.blockListScrollOffset + maxRows < total) {
                    this.blockListScrollOffset = Math.min(total - maxRows, this.blockListScrollOffset + 1);
                    this.rebuildBlockRowLayout();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean gamemodeMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxRows = this.calculateMaxVisibleRows();
        int total = this.detectedBlocks.size();
        if (this.moduleManager.isActive("blocks") && total > maxRows) {
            if (verticalAmount < 0 && this.blockListScrollOffset + maxRows < total) {
                this.blockListScrollOffset = Math.min(total - maxRows, this.blockListScrollOffset + 1);
                this.rebuildBlockRowLayout();
                return true;
            } else if (verticalAmount > 0 && this.blockListScrollOffset > 0) {
                this.blockListScrollOffset = Math.max(0, this.blockListScrollOffset - 1);
                this.rebuildBlockRowLayout();
                return true;
            }
        }
        return false;
    }

    private void openRegistrySelector() {
        Set<Block> initialSelection = this.enabledBlocks.stream()
            .map(idStr -> {
                Identifier id = Identifier.tryParse(idStr.contains(":") ? idStr : "minecraft:" + idStr);
                return id != null ? Registries.BLOCK.get(id) : null;
            })
            .filter(b -> b != null && b != Blocks.AIR)
            .collect(Collectors.toSet());

        RegistrySelectorContext<Block> context = new RegistrySelectorContext<>(
            "minecraft:block",
            "Select Disguise Blocks",
            RegistrySelectorContext.SelectionMode.MULTI,
            this.selectorState,
            result -> {
                for (Block b : result.selectedEntries()) {
                    Identifier id = Registries.BLOCK.getId(b);
                    String idStr = normalizeBlockId(id.getPath());
                    if (!this.detectedBlocks.contains(idStr)) {
                        this.detectedBlocks.add(idStr);
                    }
                    this.enabledBlocks.add(idStr);
                }
                Set<String> selectedPaths = result.selectedEntries().stream()
                    .map(b -> normalizeBlockId(Registries.BLOCK.getId(b).getPath()))
                    .collect(Collectors.toSet());
                this.enabledBlocks.removeIf(id -> !selectedPaths.contains(id));
                this.rebuildBlockRowLayout();
            },
            "hideandseek",
            initialSelection
        );

        this.client.setScreen(new RegistrySelectorScreen<>(context, new BlockRegistryProvider()));
    }

    @Override
    protected List<Text> getSummaryLines() {
        return List.of(
            Text.literal("Map: §e" + (!this.selectedMapId.isBlank() ? this.selectedMapId : "None")),
            Text.literal("Players: " + this.playerGrid.getMembers("selected").size()),
            Text.literal("Block Pool: §a" + this.enabledBlocks.size() + " blocks"),
            Text.literal("Grace Period: " + this.hidingTimeSeconds + "s"),
            Text.literal("Duration: " + (this.durationSeconds / 60) + "m " + (this.durationSeconds % 60) + "s"),
            Text.literal("Initial Seekers: " + this.seekerCount),
            Text.literal("Miss Penalty: " + (this.seekerMissPenalty > 0 ? "0.5♥" : "Disabled")),
            Text.literal("Regen Cap: " + (this.seekerPassiveRegenCap >= 20.0F ? "10♥ (Full)" : "5♥"))
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.hidingTimeSeconds = readClamped(this.hidingTimeField, this.hidingTimeSeconds, 5, 120);
            this.durationSeconds = readClamped(this.durationField, this.durationSeconds, 60, 1800);
            this.seekerCount = readClamped(this.seekerCountField, this.seekerCount, 1, 10);
            this.tauntCooldown = readClamped(this.tauntCooldownField, this.tauntCooldown, 5, 60);
            this.sonarUnlock = readClamped(this.sonarUnlockField, this.sonarUnlock, 10, 180);
        }
    }

    @Override
    public String title() { return "Hide and Seek Setup"; }

    @Override
    public String subtitle() { return "Disguise as blocks, blend in, and survive the hunt"; }

    @Override
    public String gameId() { return HideAndSeekDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.selectedMapId == null || this.selectedMapId.isBlank()) {
            return ValidationResult.error("Please select a Hide and Seek map.");
        }
        if (this.enabledBlocks.isEmpty()) {
            return ValidationResult.error("At least 1 block must be enabled in the Block Pool.");
        }
        if (this.playerGrid.getMembers("selected").size() < 2) {
            return ValidationResult.error("Select at least two players.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putString("seedMode", "random");
        builder.settings().putString("mapId", this.selectedMapId);
        builder.settings().putInt("hidingTimeSeconds", this.hidingTimeSeconds);
        builder.settings().putInt("gameDurationSeconds", this.durationSeconds);
        builder.settings().putInt("seekerCount", this.seekerCount);
        builder.settings().putDouble("solidifyDelaySeconds", this.solidifyDelay);
        builder.settings().putFloat("seekerMissPenalty", this.seekerMissPenalty);
        builder.settings().putInt("tauntCooldownSeconds", this.tauntCooldown);
        builder.settings().putInt("sonarUnlockSeconds", this.sonarUnlock);
        builder.settings().putFloat("seekerPassiveRegenCap", this.seekerPassiveRegenCap);
        builder.settings().putString("disguiseBlocks", String.join(",", this.enabledBlocks));
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("mapId", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            this.selectedMapId = settings.getString("mapId");
            this.syncBlocksFromSelectedMap();
        }
        if (settings.contains("disguiseBlocks", net.minecraft.nbt.NbtElement.STRING_TYPE)) {
            this.enabledBlocks.clear();
            String raw = settings.getString("disguiseBlocks");
            if (!raw.isBlank()) {
                for (String id : raw.split(",")) {
                    String clean = normalizeBlockId(id);
                    if (!clean.isEmpty()) {
                        if (!this.detectedBlocks.contains(clean)) {
                            this.detectedBlocks.add(clean);
                        }
                        this.enabledBlocks.add(clean);
                    }
                }
            }
        }
        if (settings.contains("hidingTimeSeconds")) this.hidingTimeSeconds = settings.getInt("hidingTimeSeconds");
        if (settings.contains("gameDurationSeconds")) this.durationSeconds = settings.getInt("gameDurationSeconds");
        if (settings.contains("seekerCount")) this.seekerCount = settings.getInt("seekerCount");
        if (settings.contains("solidifyDelaySeconds")) this.solidifyDelay = settings.getDouble("solidifyDelaySeconds");
        if (settings.contains("seekerMissPenalty")) this.seekerMissPenalty = settings.getFloat("seekerMissPenalty");
        if (settings.contains("tauntCooldownSeconds")) this.tauntCooldown = settings.getInt("tauntCooldownSeconds");
        if (settings.contains("sonarUnlockSeconds")) this.sonarUnlock = settings.getInt("sonarUnlockSeconds");
        if (settings.contains("seekerPassiveRegenCap")) this.seekerPassiveRegenCap = settings.getFloat("seekerPassiveRegenCap");

        if (this.hidingTimeField != null) this.hidingTimeField.setText(String.valueOf(this.hidingTimeSeconds));
        if (this.durationField != null) this.durationField.setText(String.valueOf(this.durationSeconds));
        if (this.seekerCountField != null) this.seekerCountField.setText(String.valueOf(this.seekerCount));
        if (this.tauntCooldownField != null) this.tauntCooldownField.setText(String.valueOf(this.tauntCooldown));
        if (this.sonarUnlockField != null) this.sonarUnlockField.setText(String.valueOf(this.sonarUnlock));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.syncBlocksFromSelectedMap();
        this.hidingTimeSeconds = HideAndSeekSettings.DEFAULT_HIDING_TIME;
        this.durationSeconds = HideAndSeekSettings.DEFAULT_GAME_DURATION;
        this.seekerCount = HideAndSeekSettings.DEFAULT_SEEKER_COUNT;
        this.solidifyDelay = HideAndSeekSettings.DEFAULT_SOLIDIFY_DELAY;
        this.seekerMissPenalty = HideAndSeekSettings.DEFAULT_SEEKER_MISS_PENALTY;
        this.tauntCooldown = HideAndSeekSettings.DEFAULT_TAUNT_COOLDOWN;
        this.sonarUnlock = HideAndSeekSettings.DEFAULT_SONAR_UNLOCK;
        this.seekerPassiveRegenCap = HideAndSeekSettings.DEFAULT_PASSIVE_REGEN_CAP;

        if (this.hidingTimeField != null) this.hidingTimeField.setText(String.valueOf(this.hidingTimeSeconds));
        if (this.durationField != null) this.durationField.setText(String.valueOf(this.durationSeconds));
        if (this.seekerCountField != null) this.seekerCountField.setText(String.valueOf(this.seekerCount));
        if (this.tauntCooldownField != null) this.tauntCooldownField.setText(String.valueOf(this.tauntCooldown));
        if (this.sonarUnlockField != null) this.sonarUnlockField.setText(String.valueOf(this.sonarUnlock));
    }
}
