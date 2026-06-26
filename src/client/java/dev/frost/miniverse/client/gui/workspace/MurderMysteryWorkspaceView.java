package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.murdermystery.MurderMysteryDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
        this.moduleManager.register("summary", "U", "Summary", "Summary", "Review and launch the map-backed session.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Match Duration (s)", (s, x, y, w) -> {
                    this.durationField = this.addIntField(s, x, y, this.durationSeconds, w, "Round duration (seconds)", val -> "The murderer must eliminate everyone within " + val + " seconds.");
                }
            );

            this.rulesLayout.addRow(
                "Detective Count", (s, x, y, w) -> {
                    this.detectiveCountField = this.addIntField(s, x, y, this.detectiveCount, w, "Detective count", val -> "The match will have " + val + " detective(s).");
                }
            );

            this.rulesLayout.addRow(
                "Coin Interval (s)", (s, x, y, w) -> {
                    this.coinIntervalField = this.addIntField(s, x, y, this.coinInterval, w, "Coin spawn interval (seconds)", val -> "Coins will spawn on the map every " + val + " seconds.");
                }
            );

            this.rulesLayout.addRow(
                "Detective Bow Price", (s, x, y, w) -> {
                    this.bowPriceField = this.addIntField(s, x, y, this.bowPrice, w, "Detective bow price (coins)", val -> "Innocents must collect " + val + " coins to receive a bow.");
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
}
