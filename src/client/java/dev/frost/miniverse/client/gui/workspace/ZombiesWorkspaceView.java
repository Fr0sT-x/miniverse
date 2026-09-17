package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class ZombiesWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget startGoldField;
    private IntFieldWidget maxRoundsField;
    private IntFieldWidget intermissionField;
    private IntFieldWidget bleedoutField;
    private ButtonWidget friendlyFireButton;

    private int startGold = 500;
    private int maxRounds = 30;
    private int intermissionSeconds = 10;
    private int bleedoutSeconds = 30;
    private boolean friendlyFire = false;

    public ZombiesWorkspaceView() {
        super(ZombiesDefinition.ID);
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Survivors", UiTheme.ACCENT_RED, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Survivors", "Setup", "Select participating survivors.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated Dead End map.", UiTheme.ACCENT_BLUE, "Valid Zombies Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure gold, round limits, and bleedout timers.", UiTheme.ACCENT_BLUE);
        this.moduleManager.register("summary", "U", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT_RED);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Starting Gold", (s, x, y, w) -> {
                    this.startGoldField = this.addIntField(s, x, y, this.startGold, w, "Starting Gold", val -> "Each survivor starts with " + val + " gold to purchase weapons/doors.");
                }
            );

            this.rulesLayout.addRow(
                "Max Rounds", (s, x, y, w) -> {
                    this.maxRoundsField = this.addIntField(s, x, y, this.maxRounds, w, "Max Rounds", val -> "Survive up to round " + val + ". Bosses spawn on R10, R20, R30.");
                }
            );

            this.rulesLayout.addRow(
                "Intermission (s)", (s, x, y, w) -> {
                    this.intermissionField = this.addIntField(s, x, y, this.intermissionSeconds, w, "Intermission (s)", val -> "Seconds of peace between rounds to buy ammo, perks, and repair barricades.");
                }
            );

            this.rulesLayout.addRow(
                "Bleedout (s)", (s, x, y, w) -> {
                    this.bleedoutField = this.addIntField(s, x, y, this.bleedoutSeconds, w, "Bleedout (s)", val -> "Time in seconds before a downed survivor bleeds out and becomes a spectator.");
                }
            );

            this.rulesLayout.addRow(
                "Friendly Fire", (s, x, y, w) -> {
                    this.friendlyFireButton = this.addToggleButton(s, "Friendly Fire", () -> this.friendlyFire, x, y, w,
                        new BinaryTooltip("Guns and explosives can damage teammates.", "Bullets and rockets pass harmlessly through teammates."),
                        () -> this.friendlyFire = !this.friendlyFire);
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
    protected List<Text> getSummaryLines() {
        return List.of(
            Text.literal("Starting Gold: " + this.startGold),
            Text.literal("Max Rounds: " + this.maxRounds),
            Text.literal("Intermission: " + this.intermissionSeconds + "s"),
            Text.literal("Bleedout Timer: " + this.bleedoutSeconds + "s"),
            Text.literal("Friendly Fire: " + (this.friendlyFire ? "ON" : "OFF"))
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
            this.startGold = readClamped(this.startGoldField, this.startGold, 0, 100000);
            this.maxRounds = readClamped(this.maxRoundsField, this.maxRounds, 1, 100);
            this.intermissionSeconds = readClamped(this.intermissionField, this.intermissionSeconds, 0, 120);
            this.bleedoutSeconds = readClamped(this.bleedoutField, this.bleedoutSeconds, 5, 120);
        }
    }

    @Override
    public String title() {
        return "Zombies: Dead End Setup";
    }

    @Override
    public String subtitle() {
        return "Co-op round-based undead survival";
    }

    @Override
    public String gameId() {
        return ZombiesDefinition.ID;
    }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.playerGrid.getMembers("selected").isEmpty()) {
            return ValidationResult.error("Select at least one survivor.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        String mapId = this.selectedMapId == null || this.selectedMapId.isBlank() ? "dead_end" : this.selectedMapId;
        builder.settings().putString("mapId", mapId);
        builder.settings().putInt("startGold", this.startGold);
        builder.settings().putInt("maxRounds", this.maxRounds);
        builder.settings().putInt("intermissionSeconds", this.intermissionSeconds);
        builder.settings().putInt("bleedoutSeconds", this.bleedoutSeconds);
        builder.settings().putBoolean("friendlyFire", this.friendlyFire);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Survivors", this.playerGrid.getMembers("selected"));
    }
}
