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
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class MurderMysteryWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private TextFieldWidget durationField;
    private TextFieldWidget detectiveCountField;
    private TextFieldWidget coinIntervalField;
    private TextFieldWidget bowPriceField;

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
            this.durationField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, Integer.toString(this.durationSeconds), "Round duration (seconds)");
            this.detectiveCountField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, Integer.toString(this.detectiveCount), "Detective count");
            this.coinIntervalField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, Integer.toString(this.coinInterval), "Coin spawn interval (seconds)");
            this.bowPriceField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 192, Integer.toString(this.bowPrice), "Detective bow price (coins)");
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("summary")) {
            this.syncStateFromWidgets();
            this.renderSettingsModulePanel(context, textRenderer, "Summary", UiTheme.ACCENT);
            int x = this.layout.mainPanel().x() + 14;
            int y = this.layout.mainPanel().y() + 72;
            int line = y + 18;
            context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
            line += 20;
            context.drawText(textRenderer, Text.literal("Players: " + this.playerGrid.getMembers("selected").size()), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Round Duration: " + this.durationSeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Detectives: " + this.detectiveCount), x + 14, line, UiTheme.TEXT_MUTED, false);
        } else if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int labelX = this.layout.mainPanel().x() + 38;
        int labelY = this.layout.mainPanel().y() + 102;
        if (this.moduleManager.isActive("rules")) {
            context.drawText(textRenderer, Text.literal("Match Duration (s)"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Detective Count"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Coin Interval (s)"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Detective Bow Price"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    private void syncStateFromWidgets() {
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
