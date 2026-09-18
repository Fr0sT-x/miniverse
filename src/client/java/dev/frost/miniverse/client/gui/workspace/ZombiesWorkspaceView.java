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
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficulty;
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
    private ButtonWidget difficultyButton;
    private ButtonWidget endlessModeButton;

    private int startGold = 500;
    private int maxRounds = 30;
    private int intermissionSeconds = 10;
    private int bleedoutSeconds = 30;
    private boolean friendlyFire = false;
    private boolean endlessMode = false;
    private ZombiesDifficulty difficulty = ZombiesDifficulty.EASY;

    public ZombiesWorkspaceView() {
        super(ZombiesDefinition.ID);
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Survivors", UiTheme.ACCENT_RED, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Survivors", "Setup", "Select participating survivors.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated Zombies map.", UiTheme.ACCENT_BLUE, "Valid Zombies Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure gold, round limits, and bleedout timers.", UiTheme.ACCENT_BLUE);
        this.moduleManager.register("summary", "U", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT_RED);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Difficulty", (s, x, y, w) -> {
                    this.difficultyButton = this.addCycleButton(s,
                        () -> "Difficulty: " + this.difficulty.getDisplayName(),
                        () -> this.difficulty.ordinal(),
                        x, y, w,
                        new String[] {
                            "Easy: Baseline zombie health and damage. Standard wave pacing.",
                            "Normal: +35% zombie health, +50% melee damage, +5% speed, denser waves.",
                            "Hard: +80% zombie health, +100% melee damage, +10% speed, relentless waves."
                        },
                        ZombiesDifficulty.values().length,
                        () -> this.difficulty = this.difficulty.next()
                    );
                }
            );

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

            this.rulesLayout.addRow(
                "Endless Mode", (s, x, y, w) -> {
                    this.endlessModeButton = this.addToggleButton(s, "Endless Mode", () -> this.endlessMode, x, y, w,
                        new BinaryTooltip("Game continues infinitely past round 30 with escalating waves until defeat.", "Game ends in victory after round 30."),
                        () -> this.endlessMode = !this.endlessMode);
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
            Text.literal("Difficulty: " + this.difficulty.getDisplayName()),
            Text.literal("Starting Gold: " + this.startGold),
            Text.literal("Max Rounds: " + this.maxRounds),
            Text.literal("Endless Mode: " + (this.endlessMode ? "ON" : "OFF")),
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
        return "Zombies Setup";
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
        builder.settings().putString("difficulty", this.difficulty.name());
        builder.settings().putBoolean("endlessMode", this.endlessMode);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Survivors", this.playerGrid.getMembers("selected"));
    }
}
