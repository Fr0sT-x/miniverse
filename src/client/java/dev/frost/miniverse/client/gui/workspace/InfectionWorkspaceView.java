package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.infection.InfectionDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public final class InfectionWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget durationField;
    private IntFieldWidget infectedField;
    private IntFieldWidget respawnField;
    private ButtonWidget friendlyFireButton;

    private int durationSeconds = 600;
    private int startingInfected = 1;
    private int respawnDelay = 3;
    private boolean allowFriendlyFire = false;

    public InfectionWorkspaceView() {
        super("infection");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for Infection.", UiTheme.ACCENT_RED, "Valid Infection Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Tune duration, infected count, respawn delay, and friendly fire.", UiTheme.ACCENT_BLUE);
        this.moduleManager.register("summary", "U", "Summary", "Summary", "Review and launch the map-backed session.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Match Duration", (s, x, y, w) -> {
                    this.durationField = this.addIntField(s, x, y, this.durationSeconds, w, "Match duration", val -> "Total match duration will be " + val + " seconds.");
                }
            );

            this.rulesLayout.addRow(
                "Starting Infected", (s, x, y, w) -> {
                    this.infectedField = this.addIntField(s, x, y, this.startingInfected, w, "Starting infected", val -> "The match will start with " + val + " alpha infected.");
                }
            );

            this.rulesLayout.addRow(
                "Respawn Delay", (s, x, y, w) -> {
                    this.respawnField = this.addIntField(s, x, y, this.respawnDelay, w, "Respawn delay",
                        "Infected will respawn instantly.",
                        val -> "Infected will be forced to spectate for " + val + " seconds before respawning.");
                }
            );

            this.rulesLayout.addRow(
                "Friendly Fire", (s, x, y, w) -> {
                    this.friendlyFireButton = this.addToggleButton(s, "Friendly Fire", () -> this.allowFriendlyFire, x, y, w,
                        new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("Survivors can damage other survivors.", "Survivors cannot damage each other."),
                        () -> this.allowFriendlyFire = !this.allowFriendlyFire);
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
            Text.literal("Match Duration: " + this.durationSeconds + "s"),
            Text.literal("Starting Infected: " + this.startingInfected)
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
            this.startingInfected = readClamped(this.infectedField, this.startingInfected, 1, 100);
            this.respawnDelay = readClamped(this.respawnField, this.respawnDelay, 0, 60);
        }
    }

    @Override
    public String title() { return "Infection Setup"; }

    @Override
    public String subtitle() { return "Map-aware reference gamemode"; }

    @Override
    public String gameId() { return InfectionDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.playerGrid.getMembers("selected").size() < 2) {
            return ValidationResult.error("Select at least two players.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putString("seedMode", "random");
        builder.settings().putInt("matchDurationSeconds", this.durationSeconds);
        builder.settings().putInt("startingInfectedCount", this.startingInfected);
        builder.settings().putInt("respawnDelaySeconds", this.respawnDelay);
        builder.settings().putBoolean("allowFriendlyFire", this.allowFriendlyFire);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }

}
