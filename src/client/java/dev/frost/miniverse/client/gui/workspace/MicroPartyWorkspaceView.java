package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceModuleManager;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyDefinition;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartySettings;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRuleRegistry;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;

import java.util.*;

public final class MicroPartyWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget maxRoundsField;
    private IntFieldWidget intermissionSecondsField;
    private TextFieldWidget speedMultiplierField;

    private int startingLives = 3;
    private int maxRounds = 25;
    private String gameMode = "POINTS";
    private boolean speedScaling = true;
    private float speedMultiplier = 2.5f;
    private int intermissionSeconds = 1;

    // Micro Game Pool state
    private final Set<String> enabledRuleIds = new LinkedHashSet<>();
    private final Map<String, Integer> customRuleDurations = new LinkedHashMap<>();
    private int poolScrollOffset = 0;
    private int descHorizontalOffset = 0;
    private UiLayout.Rect selectAllRect;
    private UiLayout.Rect deselectAllRect;
    private UiLayout.Rect resetPoolRect;
    private UiLayout.Rect prevPageRect;
    private UiLayout.Rect nextPageRect;
    private final List<RuleRowButton> ruleRowButtons = new ArrayList<>();

    private record RuleRowButton(
        UiLayout.Rect rect,
        UiLayout.Rect minusRect,
        UiLayout.Rect badgeRect,
        UiLayout.Rect plusRect,
        String ruleId,
        String ruleName,
        String description,
        String numberText,
        int defaultDuration
    ) {}

    public MicroPartyWorkspaceView() {
        super(MicroPartyDefinition.ID);
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a map with Micro Party arena markers.", UiTheme.ACCENT_BLUE, "Valid Micro Party Maps");
        this.moduleManager.register("pool", "G", "Micro Game Pool", "Setup", "Enable, disable, and fine-tune base duration for micro-games.", 0xFFFFAA00);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure rounds, speed scaling, and intermission.", UiTheme.ACCENT_GREEN);

        // All micro-games enabled by default
        for (MicroRule rule : MicroRuleRegistry.getAllRules()) {
            this.enabledRuleIds.add(rule.id());
        }
    }

    public int calculateMaxVisibleRows() {
        if (this.layout == null) return 7;
        int moduleY = this.layout.mainPanel().y() + 72;
        int moduleHeight = this.layout.mainPanel().height() - 104;
        int moduleBottom = moduleY + moduleHeight;
        int rowY = this.layout.mainPanel().y() + 132;
        int available = (moduleBottom - 12) - rowY;
        if (available < 20) return 1;
        return Math.max(1, ((available - 20) / 24) + 1);
    }

    private void rebuildPoolRowLayout() {
        this.ruleRowButtons.clear();
        this.prevPageRect = null;
        this.nextPageRect = null;
        this.resetPoolRect = null;
        if (this.layout == null) return;

        int startX = this.layout.mainPanel().x() + 26;
        int startY = this.layout.mainPanel().y() + 104;

        this.selectAllRect = new UiLayout.Rect(startX, startY, 74, 20);
        this.deselectAllRect = new UiLayout.Rect(startX + 78, startY, 80, 20);
        this.resetPoolRect = new UiLayout.Rect(startX + 162, startY, 52, 20);

        List<MicroRule> allRules = MicroRuleRegistry.getAllRules();
        int rowY = startY + 28;
        int maxRows = this.calculateMaxVisibleRows();
        int total = allRules.size();

        if (this.poolScrollOffset + maxRows > total) {
            this.poolScrollOffset = Math.max(0, total - maxRows);
        }

        for (int i = 0; i < maxRows && (i + this.poolScrollOffset) < total; i++) {
            int index = i + this.poolScrollOffset;
            MicroRule rule = allRules.get(index);
            int currentY = rowY + (i * 24);
            this.ruleRowButtons.add(new RuleRowButton(
                new UiLayout.Rect(startX + 26, currentY, 134, 20),
                new UiLayout.Rect(startX + 164, currentY, 18, 20),
                new UiLayout.Rect(startX + 184, currentY, 34, 20),
                new UiLayout.Rect(startX + 220, currentY, 18, 20),
                rule.id(),
                rule.name(),
                rule.description(),
                (index + 1) + ".",
                rule.baseDurationSeconds()
            ));
        }

        if (total > maxRows) {
            this.prevPageRect = new UiLayout.Rect(startX + 220, startY, 50, 20);
            this.nextPageRect = new UiLayout.Rect(startX + 274, startY, 50, 20);
        }
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("pool")) {
            this.rebuildPoolRowLayout();
        } else if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Max Rounds",
                WorkspaceTooltip.of("Maximum micro-challenges played before determining match winners."),
                (s, x, y, w) -> {
                    this.maxRoundsField = this.addIntField(s, "Max Rounds", x, y, this.maxRounds, 5, 100, w,
                        WorkspaceTooltip.dynamic(() -> "Match runs up to " + this.maxRoundsField.getIntValue(this.maxRounds) + " micro-challenges (5-100)."),
                        val -> this.maxRounds = val
                    );
                }
            );

            this.rulesLayout.addRow(
                "Speed Scaling",
                WorkspaceTooltip.of("Controls whether game speed escalates as rounds progress."),
                (s, x, y, w) -> {
                    this.addToggleButton(s, "Speed Scaling", () -> this.speedScaling, x, y, w,
                        "Tempo speeds up every 5 rounds with faster timers.",
                        "Tempo remains constant throughout the match.",
                        () -> this.speedScaling = !this.speedScaling
                    );
                }
            );

            this.rulesLayout.addRow(
                "Speed Multiplier",
                WorkspaceTooltip.of("Peak speedup multiplier reached at max tempo. Allows values from 1.0 to 5.0 with decimal precision (e.g. 1.25, 2.5, 3.8)."),
                (s, x, y, w) -> {
                    this.speedMultiplierField = this.addField(s, x, y,
                        String.format(Locale.ROOT, "%.2f", this.speedMultiplier),
                        w,
                        "1.0 - 5.0",
                        WorkspaceTooltip.dynamic(() -> "Peak speedup multiplier reached at max tempo. Allows decimal point precision from 1.0 to 5.0 (Current: " + String.format(Locale.ROOT, "%.2f", parseSpeedMultiplier(this.speedMultiplierField, this.speedMultiplier)) + "x).")
                    );
                }
            );

            this.rulesLayout.addRow(
                "Intermission",
                WorkspaceTooltip.of("Pause duration in seconds between rounds to show scores and instructions."),
                (s, x, y, w) -> {
                    this.intermissionSecondsField = this.addIntField(s, "Intermission", x, y, this.intermissionSeconds, 1, 10, w,
                        WorkspaceTooltip.dynamic(() -> "Breather of " + this.intermissionSecondsField.getIntValue(this.intermissionSeconds) + " seconds between challenges (1-10s)."),
                        val -> this.intermissionSeconds = val
                    );
                }
            );
        }
    }

    private float parseSpeedMultiplier(TextFieldWidget field, float fallback) {
        if (field == null) return fallback;
        try {
            String clean = field.getText().replaceAll("[^0-9.]", "").trim();
            if (clean.isEmpty()) return fallback;
            float val = Float.parseFloat(clean);
            return Math.max(1.0f, Math.min(5.0f, val));
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("pool")) {
            WorkspaceModuleManager.RegisteredModule active = this.moduleManager.getActiveModule();
            this.renderSettingsModulePanel(context, textRenderer, active.label(), active.accent());

            int startX = this.layout.mainPanel().x() + 26;
            int headerY = this.layout.mainPanel().y() + 84;
            int startY = this.layout.mainPanel().y() + 104;

            List<MicroRule> allRules = MicroRuleRegistry.getAllRules();
            String poolInfo = "Active Pool: " + this.enabledRuleIds.size() + " / " + allRules.size() + " enabled" +
                (this.customRuleDurations.isEmpty() ? "" : " §6(" + this.customRuleDurations.size() + " custom durations)");
            context.drawText(textRenderer, Text.literal(poolInfo), startX, headerY + 8, UiTheme.TEXT_MUTED, false);

            if (this.selectAllRect != null) {
                this.renderActionButton(context, textRenderer, this.selectAllRect, "Select All", UiTheme.ACCENT_BLUE, this.selectAllRect.contains(mouseX, mouseY));
            }
            if (this.deselectAllRect != null) {
                this.renderActionButton(context, textRenderer, this.deselectAllRect, "Deselect All", UiTheme.ACCENT, this.deselectAllRect.contains(mouseX, mouseY));
            }
            if (this.resetPoolRect != null) {
                this.renderActionButton(context, textRenderer, this.resetPoolRect, "Reset", UiTheme.ACCENT_BLUE, this.resetPoolRect.contains(mouseX, mouseY));
            }

            int maxRows = this.calculateMaxVisibleRows();
            int total = allRules.size();
            if (total > maxRows) {
                if (this.prevPageRect != null) {
                    boolean canPrev = this.poolScrollOffset > 0;
                    int accent = canPrev ? UiTheme.ACCENT_BLUE : 0x444444;
                    this.renderActionButton(context, textRenderer, this.prevPageRect, "◀ Prev", accent, canPrev && this.prevPageRect.contains(mouseX, mouseY));
                }
                if (this.nextPageRect != null) {
                    boolean canNext = this.poolScrollOffset + maxRows < total;
                    int accent = canNext ? UiTheme.ACCENT_BLUE : 0x444444;
                    this.renderActionButton(context, textRenderer, this.nextPageRect, "Next ▶", accent, canNext && this.nextPageRect.contains(mouseX, mouseY));
                }
                int showingFrom = this.poolScrollOffset + 1;
                int showingTo = Math.min(total, this.poolScrollOffset + maxRows);
                String pageInfo = showingFrom + "-" + showingTo + " of " + total;
                context.drawText(textRenderer, Text.literal(pageInfo), startX + 328, startY + 6, UiTheme.TEXT_DIM, false);
            }

            int descStartX = startX + 244;
            int descEndX = this.layout.mainPanel().x() + this.layout.mainPanel().width() - 14;
            int availableDescWidth = Math.max(10, descEndX - descStartX);

            int maxDescWidth = 0;
            for (MicroRule rule : allRules) {
                maxDescWidth = Math.max(maxDescWidth, textRenderer.getWidth(rule.description()));
            }
            int maxScroll = Math.max(0, maxDescWidth - availableDescWidth + 20);
            if (this.descHorizontalOffset > maxScroll) {
                this.descHorizontalOffset = maxScroll;
            }

            for (RuleRowButton btn : this.ruleRowButtons) {
                boolean enabled = this.enabledRuleIds.contains(btn.ruleId());

                // Render numbering outside and to the left of the button
                context.drawText(textRenderer, Text.literal(btn.numberText()), startX, btn.rect().y() + 6, UiTheme.TEXT_MUTED, false);

                // Render toggle button with tick always at the beginning
                this.renderToggleRowButton(context, textRenderer, btn.rect(), btn.ruleName(), enabled, btn.rect().contains(mouseX, mouseY));

                // Render duration controls [-] [ <sec>s ] [+]
                int currentDuration = this.customRuleDurations.getOrDefault(btn.ruleId(), btn.defaultDuration);
                boolean isCustom = this.customRuleDurations.containsKey(btn.ruleId());

                boolean minusHovered = btn.minusRect().contains(mouseX, mouseY);
                boolean plusHovered = btn.plusRect().contains(mouseX, mouseY);
                boolean badgeHovered = btn.badgeRect().contains(mouseX, mouseY);

                // Minus button
                this.renderActionButton(context, textRenderer, btn.minusRect(), "-", UiTheme.ACCENT_BLUE, minusHovered);

                // Duration badge
                int badgeFill = isCustom ? 0x40FFAA00 : 0x20000000;
                int badgeBorder = isCustom ? 0xFFFFAA00 : (badgeHovered ? UiTheme.ACCENT_BLUE : UiTheme.BORDER_SUBTLE);
                UiRenderer.panel(context, btn.badgeRect().x(), btn.badgeRect().y(), btn.badgeRect().width(), btn.badgeRect().height(), badgeFill, badgeBorder);
                String durText = currentDuration + "s";
                int durColor = isCustom ? 0xFFFFCC00 : (enabled ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
                int durTextW = textRenderer.getWidth(durText);
                int durX = btn.badgeRect().x() + (btn.badgeRect().width() - durTextW) / 2;
                context.drawText(textRenderer, Text.literal(durText), durX, btn.badgeRect().y() + 6, durColor, false);

                // Plus button
                this.renderActionButton(context, textRenderer, btn.plusRect(), "+", UiTheme.ACCENT_BLUE, plusHovered);

                // Render clipped description with horizontal scroll
                int textColor = enabled ? UiTheme.TEXT_MUTED : UiTheme.TEXT_DIM;
                if (descEndX > descStartX) {
                    context.enableScissor(descStartX, btn.rect().y(), descEndX, btn.rect().y() + 20);
                    context.drawText(textRenderer, Text.literal(btn.description()), descStartX - this.descHorizontalOffset, btn.rect().y() + 6, textColor, false);
                    context.disableScissor();
                }
            }
        } else if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
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

        if (this.moduleManager.isActive("pool")) {
            if (this.selectAllRect != null && this.selectAllRect.contains(mouseX, mouseY)) {
                for (MicroRule r : MicroRuleRegistry.getAllRules()) {
                    this.enabledRuleIds.add(r.id());
                }
                this.rebuildPoolRowLayout();
                return true;
            }

            if (this.deselectAllRect != null && this.deselectAllRect.contains(mouseX, mouseY)) {
                this.enabledRuleIds.clear();
                this.rebuildPoolRowLayout();
                return true;
            }

            if (this.resetPoolRect != null && this.resetPoolRect.contains(mouseX, mouseY)) {
                this.enabledRuleIds.clear();
                for (MicroRule r : MicroRuleRegistry.getAllRules()) {
                    this.enabledRuleIds.add(r.id());
                }
                this.customRuleDurations.clear();
                this.poolScrollOffset = 0;
                this.descHorizontalOffset = 0;
                this.rebuildPoolRowLayout();
                this.status = ValidationResult.info("Reset micro-game pool and rule durations to default.");
                return true;
            }

            for (RuleRowButton btn : this.ruleRowButtons) {
                if (btn.rect().contains(mouseX, mouseY)) {
                    if (this.enabledRuleIds.contains(btn.ruleId())) {
                        this.enabledRuleIds.remove(btn.ruleId());
                    } else {
                        this.enabledRuleIds.add(btn.ruleId());
                    }
                    this.rebuildPoolRowLayout();
                    return true;
                }
                if (btn.minusRect().contains(mouseX, mouseY)) {
                    int cur = this.customRuleDurations.getOrDefault(btn.ruleId(), btn.defaultDuration);
                    int next = Math.max(1, cur - 1);
                    if (next == btn.defaultDuration) {
                        this.customRuleDurations.remove(btn.ruleId());
                    } else {
                        this.customRuleDurations.put(btn.ruleId(), next);
                    }
                    return true;
                }
                if (btn.plusRect().contains(mouseX, mouseY)) {
                    int cur = this.customRuleDurations.getOrDefault(btn.ruleId(), btn.defaultDuration);
                    int next = Math.min(60, cur + 1);
                    if (next == btn.defaultDuration) {
                        this.customRuleDurations.remove(btn.ruleId());
                    } else {
                        this.customRuleDurations.put(btn.ruleId(), next);
                    }
                    return true;
                }
                if (btn.badgeRect().contains(mouseX, mouseY)) {
                    this.customRuleDurations.remove(btn.ruleId());
                    return true;
                }
            }

            int maxRows = this.calculateMaxVisibleRows();
            int total = MicroRuleRegistry.getAllRules().size();
            if (this.prevPageRect != null && this.prevPageRect.contains(mouseX, mouseY)) {
                if (this.poolScrollOffset > 0) {
                    this.poolScrollOffset = Math.max(0, this.poolScrollOffset - 1);
                    this.rebuildPoolRowLayout();
                }
                return true;
            }

            if (this.nextPageRect != null && this.nextPageRect.contains(mouseX, mouseY)) {
                if (this.poolScrollOffset + maxRows < total) {
                    this.poolScrollOffset = Math.min(total - maxRows, this.poolScrollOffset + 1);
                    this.rebuildPoolRowLayout();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean gamemodeMouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.moduleManager.isActive("pool")) return false;

        if (horizontalAmount != 0) {
            this.descHorizontalOffset = Math.max(0, this.descHorizontalOffset - (int)(horizontalAmount * 24));
            return true;
        }

        if (net.minecraft.client.gui.screen.Screen.hasShiftDown()) {
            this.descHorizontalOffset = Math.max(0, this.descHorizontalOffset - (int)(verticalAmount * 24));
            return true;
        }

        int maxRows = this.calculateMaxVisibleRows();
        int total = MicroRuleRegistry.getAllRules().size();
        if (total > maxRows) {
            if (verticalAmount < 0 && this.poolScrollOffset + maxRows < total) {
                this.poolScrollOffset = Math.min(total - maxRows, this.poolScrollOffset + 1);
                this.rebuildPoolRowLayout();
                return true;
            } else if (verticalAmount > 0 && this.poolScrollOffset > 0) {
                this.poolScrollOffset = Math.max(0, this.poolScrollOffset - 1);
                this.rebuildPoolRowLayout();
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<Text> getSummaryLines() {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("Micro-Games: §a" + this.enabledRuleIds.size() + "/" + MicroRuleRegistry.getAllRules().size() + " enabled"));
        lines.add(Text.literal("Max Rounds: " + this.maxRounds));
        lines.add(Text.literal("Speed Scaling: " + (this.speedScaling ? "ON" : "OFF")));
        if (this.speedScaling) {
            lines.add(Text.literal("Speed Multiplier: §b" + String.format(Locale.ROOT, "%.2f", this.speedMultiplier) + "x"));
        }
        lines.add(Text.literal("Intermission: " + this.intermissionSeconds + "s"));
        if (!this.customRuleDurations.isEmpty()) {
            lines.add(Text.literal("Custom Durations: §6" + this.customRuleDurations.size() + " modified"));
        }
        return lines;
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (!this.moduleManager.isActive("pool")) return;

        for (RuleRowButton btn : this.ruleRowButtons) {
            int currentDuration = this.customRuleDurations.getOrDefault(btn.ruleId(), btn.defaultDuration);
            if (btn.badgeRect().contains(mouseX, mouseY)) {
                List<Text> lines = List.of(
                    Text.literal("Base Duration: §e" + currentDuration + "s §7(Default: " + btn.defaultDuration + "s)"),
                    Text.literal("§8• Click - / + to adjust (1-60s)"),
                    Text.literal("§8• Click badge to reset to default")
                );
                context.drawTooltip(textRenderer, lines, mouseX, mouseY);
                return;
            } else if (btn.minusRect().contains(mouseX, mouseY)) {
                context.drawTooltip(textRenderer, Text.literal("Decrease base duration (-1s)"), mouseX, mouseY);
                return;
            } else if (btn.plusRect().contains(mouseX, mouseY)) {
                context.drawTooltip(textRenderer, Text.literal("Increase base duration (+1s)"), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.maxRounds = readClamped(this.maxRoundsField, this.maxRounds, 5, 100);
            this.intermissionSeconds = readClamped(this.intermissionSecondsField, this.intermissionSeconds, 1, 10);
            this.speedMultiplier = parseSpeedMultiplier(this.speedMultiplierField, this.speedMultiplier);
        }
    }

    @Override
    public String title() {
        return "Micro Party Setup";
    }

    @Override
    public String subtitle() {
        return "Rapid-fire micro-game challenges";
    }

    @Override
    public String gameId() {
        return MicroPartyDefinition.ID;
    }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.playerGrid.getMembers("selected").size() < 1) {
            return ValidationResult.error("Select at least one player.");
        }
        if (this.selectedMapId == null || this.selectedMapId.isBlank()) {
            return ValidationResult.error("Select a map.");
        }
        if (this.enabledRuleIds.isEmpty()) {
            return ValidationResult.error("At least 1 micro-game must be enabled in the Micro Game Pool.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putString("mapId", this.selectedMapId != null ? this.selectedMapId : "");
        builder.settings().putInt("startingLives", this.startingLives);
        builder.settings().putInt("maxRounds", this.maxRounds);
        builder.settings().putString("gameMode", this.gameMode);
        builder.settings().putBoolean("speedScaling", this.speedScaling);
        builder.settings().putFloat("speedMultiplier", this.speedMultiplier);
        builder.settings().putInt("intermissionSeconds", this.intermissionSeconds);
        builder.settings().putString("enabledRules", String.join(",", this.enabledRuleIds));
        builder.settings().putString("ruleDurations", MicroPartySettings.serializeRuleDurations(this.customRuleDurations));
    }

    @Override
    protected void applyPresetSettings(NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("startingLives", NbtElement.INT_TYPE)) {
            this.startingLives = settings.getInt("startingLives");
        }
        if (settings.contains("maxRounds", NbtElement.INT_TYPE)) {
            this.maxRounds = settings.getInt("maxRounds");
            if (this.maxRoundsField != null) this.maxRoundsField.setText(String.valueOf(this.maxRounds));
        }
        if (settings.contains("speedScaling")) {
            this.speedScaling = settings.getBoolean("speedScaling");
        }
        if (settings.contains("speedMultiplier", NbtElement.NUMBER_TYPE)) {
            this.speedMultiplier = Math.max(1.0f, Math.min(5.0f, settings.getFloat("speedMultiplier")));
            if (this.speedMultiplierField != null) this.speedMultiplierField.setText(String.format(Locale.ROOT, "%.2f", this.speedMultiplier));
        }
        if (settings.contains("intermissionSeconds", NbtElement.INT_TYPE)) {
            this.intermissionSeconds = settings.getInt("intermissionSeconds");
            if (this.intermissionSecondsField != null) this.intermissionSecondsField.setText(String.valueOf(this.intermissionSeconds));
        }
        if (settings.contains("enabledRules", NbtElement.STRING_TYPE)) {
            String raw = settings.getString("enabledRules");
            this.enabledRuleIds.clear();
            if (!raw.isBlank()) {
                for (String id : raw.split(",")) {
                    String trimmed = id.trim();
                    if (!trimmed.isEmpty()) {
                        this.enabledRuleIds.add(trimmed);
                    }
                }
            }
            this.rebuildPoolRowLayout();
        }
        if (settings.contains("ruleDurations", NbtElement.STRING_TYPE)) {
            this.customRuleDurations.clear();
            this.customRuleDurations.putAll(MicroPartySettings.deserializeRuleDurations(settings.getString("ruleDurations")));
            this.rebuildPoolRowLayout();
        }
    }

    @Override
    protected void resetToDefaultSettings() {
        this.startingLives = 3;
        this.maxRounds = 25;
        this.gameMode = "POINTS";
        this.speedScaling = true;
        this.speedMultiplier = 2.5f;
        this.intermissionSeconds = 1;
        this.enabledRuleIds.clear();
        for (MicroRule rule : MicroRuleRegistry.getAllRules()) {
            this.enabledRuleIds.add(rule.id());
        }
        this.customRuleDurations.clear();
        this.poolScrollOffset = 0;
        this.descHorizontalOffset = 0;
        if (this.maxRoundsField != null) this.maxRoundsField.setText("25");
        if (this.speedMultiplierField != null) this.speedMultiplierField.setText("2.50");
        if (this.intermissionSecondsField != null) this.intermissionSecondsField.setText("1");
        this.rebuildPoolRowLayout();
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }
}
