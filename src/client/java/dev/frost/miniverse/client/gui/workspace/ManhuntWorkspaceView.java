package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class ManhuntWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid teamGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget gracePeriodField;
    private TextFieldWidget seedValueField;
    private IntFieldWidget runnerRespawnDelayField;
    private IntFieldWidget hunterRespawnDelayField;
    private ButtonWidget midGameJoinTeleportButton;
    private IntFieldWidget compassCooldownField;
    private IntFieldWidget runnerGlowPulseField;
    private ButtonWidget seedModeButton;
    private ButtonWidget huntersCompassButton;
    private ButtonWidget netherTrackingButton;
    private ButtonWidget runnerLivesButton;
    private ButtonWidget hunterLivesButton;
    private ButtonWidget runnerRespawnAtTeammateButton;
    private ButtonWidget hunterRespawnAtTeammateButton;

    private final dev.frost.miniverse.minigame.impl.manhunt.ManhuntSettings defaults = dev.frost.miniverse.minigame.impl.manhunt.ManhuntSettings.defaults();

    private boolean huntersCompassEnabled = defaults.huntersCompassEnabled();
    private boolean netherTrackingEnabled = defaults.netherTrackingEnabled();
    private boolean midGameJoinTeleportEnabled = defaults.midGameJoinTeleportEnabled();
    private int gracePeriodSeconds = defaults.hunterReleaseDelaySeconds();
    private int runnerRespawnDelaySeconds = defaults.runnerRespawnDelaySeconds();
    private int hunterRespawnDelaySeconds = defaults.hunterRespawnDelaySeconds();
    private int compassCooldownSeconds = defaults.compassCooldownSeconds();
    private int runnerGlowPulseMinutes = defaults.runnerGlowPulseMinutes();
    private int runnerLives = defaults.runnerLives();
    private int hunterLives = defaults.hunterLives();
    private boolean runnerRespawnAtTeammate = defaults.runnerRespawnAtTeammate();
    private boolean hunterRespawnAtTeammate = defaults.hunterRespawnAtTeammate();
    private SeedMode seedMode = SeedMode.RANDOM;
    private String seedValue = "";

    public ManhuntWorkspaceView() {
        super("manhunt");
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("speedrunner", "Speedrunners", 0x4D8DFF, false);
        this.teamGrid.addColumn("hunter", "Hunters", 0xE85D75, false);
        
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players into speedrunners and hunters.", UiTheme.ACCENT_RED);
        this.moduleManager.register("rules", "r", "Match Rules", "Rules", "Configure seed, tracking, lives, and difficulty.", UiTheme.ACCENT);
        this.moduleManager.register("summary", "s", "Summary", "Summary", "Review and launch the configured match.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addHeading("World & Startup");
            this.rulesLayout.addRow(
                "Seed Mode", (s, x, y, w) -> {
                    this.seedModeButton = this.addCycleButton(s, () -> this.seedMode.displayName, () -> this.seedMode.ordinal(), x, y, w, new String[]{
                        "Random world seed will be used.",
                        "Specify an exact world seed in the text field."
                    }, 2, () -> {
                        this.seedMode = this.seedMode == SeedMode.RANDOM ? SeedMode.FIXED : SeedMode.RANDOM;
                        this.seedModeButton.setMessage(Text.literal(this.seedMode.displayName));
                        if (this.seedMode == SeedMode.RANDOM) {
                            this.seedValueField.setEditable(false);
                            this.seedValueField.active = false;
                            this.seedValueField.setText("");
                            this.seedValueField.setSuggestion("Enter world seed");
                        } else {
                            this.seedValueField.setEditable(true);
                            this.seedValueField.active = true;
                            this.seedValueField.setSuggestion("");
                            this.seedValueField.setText(this.seedValue);
                        }
                    });
                },
                "Fixed Seed", (s, x, y, w) -> {
                    this.seedValueField = this.addField(s, x, y, this.seedMode == SeedMode.FIXED ? this.seedValue : "", w, "Fixed seed", () -> "Specify an exact world seed in the text field.");
                    if (this.seedMode == SeedMode.RANDOM) {
                        this.seedValueField.setEditable(false);
                        this.seedValueField.active = false;
                        this.seedValueField.setSuggestion("Enter world seed");
                    } else {
                        this.seedValueField.setEditable(true);
                        this.seedValueField.active = true;
                        this.seedValueField.setSuggestion("");
                    }
                }
            );

            this.rulesLayout.addRow(
                "Release Delay", (s, x, y, w) -> {
                    this.gracePeriodField = this.addIntField(s, x, y, this.gracePeriodSeconds, w, "Hunter release seconds",
                        "No hunter release delay.",
                        val -> "Hunters cannot move for the first " + val + " seconds.");
                    this.addStepper(s, this.gracePeriodField, x + w + 4, y, 0, 3600, 5);
                }
            );

            this.rulesLayout.addHeading("Tracking & Difficulty");
            this.rulesLayout.addRow(
                "Hunters Compass", (s, x, y, w) -> {
                    this.huntersCompassButton = this.addToggleButton(s, "Hunters Compass", () -> this.huntersCompassEnabled, x, y, w,
                        new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("ON: Hunters receive a compass pointing to the nearest speedrunner.", "OFF: Hunters will not receive any compass to track speedrunners."),
                        () -> this.huntersCompassEnabled = !this.huntersCompassEnabled);
                },
                "Compass Cooldown", (s, x, y, w) -> {
                    this.compassCooldownField = this.addIntField(s, x, y, this.compassCooldownSeconds, w, "Compass cooldown seconds",
                        "No compass cooldown.",
                        val -> "Hunters must wait " + val + " seconds between compass uses.");
                    this.addStepper(s, this.compassCooldownField, x + w + 4, y, 0, 300, 1);
                }
            );

            this.rulesLayout.addRow(
                "Nether Tracking", (s, x, y, w) -> {
                    this.netherTrackingButton = this.addToggleButton(s, "Nether Tracking", () -> this.netherTrackingEnabled, x, y, w,
                        new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("ON: Compasses work when target is in a different dimension.", "OFF: Compasses spin randomly if target is in a different dimension."),
                        () -> this.netherTrackingEnabled = !this.netherTrackingEnabled);
                },
                "Runner Glow Pulse", (s, x, y, w) -> {
                    this.runnerGlowPulseField = this.addIntField(s, x, y, this.runnerGlowPulseMinutes, w, "Runner glow pulse minutes",
                        "Speedrunners will not glow.",
                        val -> "Speedrunners will glow every " + val + " minutes to reveal their location.");
                    this.addStepper(s, this.runnerGlowPulseField, x + w + 4, y, 0, 120, 5);
                }
            );

            this.rulesLayout.addHeading("Lives & Respawns");
            this.rulesLayout.addRow(
                "Runner Lives", (s, x, y, w) -> {
                    this.runnerLivesButton = this.addCycleButton(s, () -> "Runner Lives: " + formatLives(this.runnerLives), () -> livesToIndex(this.runnerLives), x, y, w, new String[]{
                        "Unlimited lives for speedrunners.",
                        "Speedrunners have 1 life.",
                        "Speedrunners have 2 lives.",
                        "Speedrunners have 3 lives.",
                        "Speedrunners have 5 lives."
                    }, 5, () -> {
                        this.runnerLives = nextLivesValue(this.runnerLives);
                        this.runnerLivesButton.setMessage(Text.literal("Runner Lives: " + formatLives(this.runnerLives)));
                    });
                },
                "Runner Respawn", (s, x, y, w) -> {
                    this.runnerRespawnDelayField = this.addIntField(s, x, y, this.runnerRespawnDelaySeconds, w, "Runner respawn seconds",
                        "Speedrunners will respawn instantly.",
                        val -> "Speedrunners will be forced to spectate for " + val + " seconds before respawning.");
                    this.addStepper(s, this.runnerRespawnDelayField, x + w + 4, y, 0, 3600, 30);
                }
            );

            this.rulesLayout.addRow(
                "Hunter Lives", (s, x, y, w) -> {
                    this.hunterLivesButton = this.addCycleButton(s, () -> "Hunter Lives: " + formatLives(this.hunterLives), () -> livesToIndex(this.hunterLives), x, y, w, new String[]{
                        "Unlimited lives for hunters.",
                        "Hunters have 1 life.",
                        "Hunters have 2 lives.",
                        "Hunters have 3 lives.",
                        "Hunters have 5 lives."
                    }, 5, () -> {
                        this.hunterLives = nextLivesValue(this.hunterLives);
                        this.hunterLivesButton.setMessage(Text.literal("Hunter Lives: " + formatLives(this.hunterLives)));
                    });
                },
                "Hunter Respawn", (s, x, y, w) -> {
                    this.hunterRespawnDelayField = this.addIntField(s, x, y, this.hunterRespawnDelaySeconds, w, "Hunter respawn seconds",
                        "Hunters will respawn instantly.",
                        val -> "Hunters will be forced to spectate for " + val + " seconds before respawning.");
                    this.addStepper(s, this.hunterRespawnDelayField, x + w + 4, y, 0, 3600, 5);
                }
            );

            this.rulesLayout.addRow(
                "Runner Respawn TP", (s, x, y, w) -> {
                    this.runnerRespawnAtTeammateButton = this.addToggleButton(s, "Runner Respawn TP", () -> this.runnerRespawnAtTeammate, x, y, w,
                        new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("ON: Speedrunners will spawn at spectating teammate's location.", "OFF: Speedrunners will spawn at normal spawn location."),
                        () -> this.runnerRespawnAtTeammate = !this.runnerRespawnAtTeammate);
                },
                "Hunter Respawn TP", (s, x, y, w) -> {
                    this.hunterRespawnAtTeammateButton = this.addToggleButton(s, "Hunter Respawn TP", () -> this.hunterRespawnAtTeammate, x, y, w,
                        new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("ON: Hunters will spawn at spectating teammate's location.", "OFF: Hunters will spawn at normal spawn location."),
                        () -> this.hunterRespawnAtTeammate = !this.hunterRespawnAtTeammate);
                }
            );

            this.rulesLayout.addHeading("Advanced Options");
            this.rulesLayout.addRow(
                "Mid-Game Join TP Layout", (s, x, y, w) -> {
                    this.midGameJoinTeleportButton = this.addToggleButton(s, "Mid-Game Join TP", () -> this.midGameJoinTeleportEnabled, x, y, w, 
                        new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("ON: Late joiners will be placed in spectator and prompted to teleport to an active teammate.", "OFF: Late joiners will spawn at world spawn."), 
                        () -> {
                        this.midGameJoinTeleportEnabled = !this.midGameJoinTeleportEnabled;
                        this.midGameJoinTeleportButton.setMessage(Text.literal("Mid-Game Join TP: " + onOff(this.midGameJoinTeleportEnabled)));
                    });
                }
            );
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (!this.moduleManager.isActive("teams")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
    }

    @Override
    protected java.util.List<Text> getSummaryLines() {
        return java.util.List.of(
            Text.literal(this.teamGrid.getMembers("speedrunner").size() + " speedrunner(s) vs " + this.teamGrid.getMembers("hunter").size() + " hunter(s)"),
            Text.literal("Release delay: " + this.gracePeriodSeconds + "s"),
            Text.literal("Tracking: " + onOff(this.huntersCompassEnabled) + ", Nether " + onOff(this.netherTrackingEnabled) + ", cooldown " + this.compassCooldownSeconds + "s"),
            Text.literal("Runner lives: " + formatLives(this.runnerLives) + ", respawn " + this.runnerRespawnDelaySeconds + "s"),
            Text.literal("Hunter lives: " + formatLives(this.hunterLives) + ", respawn " + this.hunterRespawnDelaySeconds + "s"),
            Text.literal("Seed: " + this.seedMode.displayName)
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        
        if (this.moduleManager.isActive("rules") && this.seedMode == SeedMode.RANDOM && this.seedValueField != null) {
            if (this.seedValueField.isMouseOver(mouseX, mouseY)) {
                this.status = ValidationResult.error("Seed mode is random, change to Fixed to enter your own world seed.");
                return true;
            }
        }
        return false;
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            if (this.seedValueField != null && this.seedMode == SeedMode.FIXED) {
                this.seedValue = this.seedValueField.getText().trim();
            }
            this.gracePeriodSeconds = readClamped(this.gracePeriodField, this.gracePeriodSeconds, 0, 3600);
            this.compassCooldownSeconds = readClamped(this.compassCooldownField, this.compassCooldownSeconds, 0, 300);
            this.runnerRespawnDelaySeconds = readClamped(this.runnerRespawnDelayField, this.runnerRespawnDelaySeconds, 0, 3600);
            this.hunterRespawnDelaySeconds = readClamped(this.hunterRespawnDelayField, this.hunterRespawnDelaySeconds, 0, 3600);
            this.runnerGlowPulseMinutes = readClamped(this.runnerGlowPulseField, this.runnerGlowPulseMinutes, 0, 120);
        }
    }

    @Override
    public String title() { return "Manhunt Setup"; }

    @Override
    public String subtitle() { return "Embedded workspace / module-driven setup"; }

    @Override
    public String gameId() { return ManhuntDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (SessionSnapshotData.roster().isEmpty()) {
            return ValidationResult.error("No players online.");
        }
        if (this.teamGrid.getMembers("speedrunner").isEmpty()) {
            return ValidationResult.error("Need at least one speedrunner.");
        }
        if (this.teamGrid.getMembers("hunter").isEmpty()) {
            return ValidationResult.error("Need at least one hunter.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putBoolean("huntersCompass", this.huntersCompassEnabled);
        builder.settings().putInt("hunterReleaseDelaySeconds", this.gracePeriodSeconds);
        builder.settings().putInt("speedrunnerRespawnDelaySeconds", this.runnerRespawnDelaySeconds);
        builder.settings().putInt("hunterRespawnDelaySeconds", this.hunterRespawnDelaySeconds);
        builder.settings().putInt("compassCooldownSeconds", this.compassCooldownSeconds);
        builder.settings().putInt("runnerGlowPulseMinutes", this.runnerGlowPulseMinutes);
        builder.settings().putInt("runnerLives", this.runnerLives);
        builder.settings().putInt("hunterLives", this.hunterLives);
        builder.settings().putBoolean("netherTracking", this.netherTrackingEnabled);
        builder.settings().putBoolean("midGameJoinTeleportEnabled", this.midGameJoinTeleportEnabled);
        builder.settings().putBoolean("runnerRespawnAtTeammate", this.runnerRespawnAtTeammate);
        builder.settings().putBoolean("hunterRespawnAtTeammate", this.hunterRespawnAtTeammate);
        builder.settings().putString("seedMode", this.seedMode.nbtValue);
        if (this.seedMode == SeedMode.FIXED) {
            long parsedSeed;
            if (this.seedValue.isEmpty()) {
                parsedSeed = System.currentTimeMillis();
            } else {
                try {
                    parsedSeed = Long.parseLong(this.seedValue);
                } catch (NumberFormatException e) {
                    parsedSeed = (long) this.seedValue.hashCode();
                }
            }
            builder.settings().putLong("seed", parsedSeed);
        }
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        java.util.Map<String, Iterable<SessionSnapshotData.RosterEntry>> roles = new java.util.HashMap<>();
        roles.put("speedrunner", this.teamGrid.getMembers("speedrunner"));
        roles.put("hunter", this.teamGrid.getMembers("hunter"));
        builder.addGroupWithRoles("manhunt", "Manhunt", roles);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String formatLives(int lives) {
        return lives < 0 ? "Unlimited" : Integer.toString(lives);
    }

    private static int nextLivesValue(int current) {
        int[] cycle = {-1, 1, 2, 3, 5};
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) {
                return cycle[(i + 1) % cycle.length];
            }
        }
        return -1;
    }

    private static int livesToIndex(int current) {
        int[] cycle = {-1, 1, 2, 3, 5};
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) {
                return i;
            }
        }
        return 0;
    }

    private enum SeedMode {
        RANDOM("Random", "random"),
        FIXED("Fixed", "fixed");

        private final String displayName;
        private final String nbtValue;

        SeedMode(String displayName, String nbtValue) {
            this.displayName = displayName;
            this.nbtValue = nbtValue;
        }
    }
}
