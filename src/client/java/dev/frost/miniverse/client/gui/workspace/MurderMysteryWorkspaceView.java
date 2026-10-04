package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import dev.frost.miniverse.minigame.impl.murdermystery.MurderMysteryDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class MurderMysteryWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget durationField;
    private IntFieldWidget detectiveCountField;
    private IntFieldWidget coinIntervalField;
    private IntFieldWidget bowPriceField;

    private int durationSeconds = 300;
    private int detectiveCount = 1;
    private int coinInterval = 5;
    private int bowPrice = 10;

    public MurderMysteryWorkspaceView() {
        super("murdermystery");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for Murder Mystery.", UiTheme.ACCENT_BLUE, "Valid Murder Mystery Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Tune duration, detective count, and coin economy.", UiTheme.ACCENT_BLUE);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            WorkspaceTooltip durationTooltip = WorkspaceTooltip.dynamic(() -> "The murderer must eliminate everyone within " + (this.durationField != null ? this.durationField.getIntValue(this.durationSeconds) : this.durationSeconds) + " seconds.");
            this.rulesLayout.addRow(
                "Match Duration (s)", durationTooltip, (s, x, y, w) -> {
                    this.durationField = this.addIntField(s, "Round duration (seconds)", x, y, this.durationSeconds, 10, 3600, w, durationTooltip);
                }
            );

            WorkspaceTooltip detectiveTooltip = WorkspaceTooltip.dynamic(() -> "The match will have " + (this.detectiveCountField != null ? this.detectiveCountField.getIntValue(this.detectiveCount) : this.detectiveCount) + " detective(s).");
            this.rulesLayout.addRow(
                "Detective Count", detectiveTooltip, (s, x, y, w) -> {
                    this.detectiveCountField = this.addIntField(s, "Detective count", x, y, this.detectiveCount, 1, 100, w, detectiveTooltip);
                }
            );

            WorkspaceTooltip coinTooltip = WorkspaceTooltip.dynamic(() -> "Coins will spawn on the map every " + (this.coinIntervalField != null ? this.coinIntervalField.getIntValue(this.coinInterval) : this.coinInterval) + " seconds.");
            this.rulesLayout.addRow(
                "Coin Interval (s)", coinTooltip, (s, x, y, w) -> {
                    this.coinIntervalField = this.addIntField(s, "Coin spawn interval (seconds)", x, y, this.coinInterval, 1, 600, w, coinTooltip);
                }
            );

            WorkspaceTooltip bowTooltip = WorkspaceTooltip.dynamic(() -> "Innocents must collect " + (this.bowPriceField != null ? this.bowPriceField.getIntValue(this.bowPrice) : this.bowPrice) + " coins to receive a bow.");
            this.rulesLayout.addRow(
                "Detective Bow Price", bowTooltip, (s, x, y, w) -> {
                    this.bowPriceField = this.addIntField(s, "Detective bow price (coins)", x, y, this.bowPrice, 1, 64, w, bowTooltip);
                }
            );
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
    }

    @Override
    protected java.util.List<Text> getSummaryLines() {
        return java.util.List.of(
            Text.literal("Players: " + this.playerGrid.getMembers("selected").size()),
            Text.literal("Round Duration: " + this.durationSeconds + "s"),
            Text.literal("Detectives: " + this.detectiveCount)
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
            this.durationSeconds = readClamped(this.durationField, this.durationSeconds, 10, 3600);
            this.detectiveCount = readClamped(this.detectiveCountField, this.detectiveCount, 1, 100);
            this.coinInterval = readClamped(this.coinIntervalField, this.coinInterval, 1, 600);
            this.bowPrice = readClamped(this.bowPriceField, this.bowPrice, 1, 64);
        }
    }

    @Override
    public String title() { return "Murder Mystery Setup"; }

    @Override
    public String subtitle() { return "Find the murderer before it's too late!"; }

    @Override
    public String gameId() { return MurderMysteryDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.playerGrid.getMembers("selected").size() < 3) {
            return ValidationResult.error("Select at least three players.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putString("seedMode", "random");
        builder.settings().putInt("roundDurationTicks", this.durationSeconds * 20);
        builder.settings().putInt("detectiveCount", this.detectiveCount);
        builder.settings().putInt("coinSpawnIntervalTicks", this.coinInterval * 20);
        builder.settings().putInt("detectiveBowPrice", this.bowPrice);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("roundDurationTicks")) this.durationSeconds = settings.getInt("roundDurationTicks") / 20;
        if (settings.contains("detectiveCount")) this.detectiveCount = settings.getInt("detectiveCount");
        if (settings.contains("coinSpawnIntervalTicks")) this.coinInterval = settings.getInt("coinSpawnIntervalTicks") / 20;
        if (settings.contains("detectiveBowPrice")) this.bowPrice = settings.getInt("detectiveBowPrice");

        if (this.durationField != null) this.durationField.setText(String.valueOf(this.durationSeconds));
        if (this.detectiveCountField != null) this.detectiveCountField.setText(String.valueOf(this.detectiveCount));
        if (this.coinIntervalField != null) this.coinIntervalField.setText(String.valueOf(this.coinInterval));
        if (this.bowPriceField != null) this.bowPriceField.setText(String.valueOf(this.bowPrice));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.durationSeconds = 240;
        this.detectiveCount = 1;
        this.coinInterval = 5;
        this.bowPrice = 10;

        if (this.durationField != null) this.durationField.setText(String.valueOf(this.durationSeconds));
        if (this.detectiveCountField != null) this.detectiveCountField.setText(String.valueOf(this.detectiveCount));
        if (this.coinIntervalField != null) this.coinIntervalField.setText(String.valueOf(this.coinInterval));
        if (this.bowPriceField != null) this.bowPriceField.setText(String.valueOf(this.bowPrice));
    }
}
