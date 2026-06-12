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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class ManhuntWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid teamGrid = new StaticTeamSelectionGrid();

    private TextFieldWidget gracePeriodField;
    private TextFieldWidget seedValueField;
    private TextFieldWidget respawnDelayField;
    private TextFieldWidget hunterRespawnDelayField;
    private TextFieldWidget compassCooldownField;
    private TextFieldWidget runnerGlowPulseField;
    private ButtonWidget seedModeButton;
    private ButtonWidget huntersCompassButton;
    private ButtonWidget netherTrackingButton;
    private ButtonWidget runnerLivesButton;
    private ButtonWidget hunterLivesButton;

    private boolean huntersCompassEnabled = true;
    private boolean netherTrackingEnabled = true;
    private int gracePeriodSeconds = 30;
    private int respawnDelaySeconds = 300;
    private int hunterRespawnDelaySeconds = 0;
    private int compassCooldownSeconds = 2;
    private int runnerGlowPulseMinutes = 0;
    private int runnerLives = -1;
    private int hunterLives = -1;
    private SeedMode seedMode = SeedMode.RANDOM;
    private String seedValue = "";

    public ManhuntWorkspaceView() {
        super("manhunt");
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("speedrunner", "Speedrunners", 0x4D8DFF, false);
        this.teamGrid.addColumn("hunter", "Hunters", 0xE85D75, false);
        
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players into speedrunners and hunters.", UiTheme.ACCENT_RED);
        this.moduleManager.register("rules", "r", "Match Rules", "Rules", "Configure seed, tracking, lives, and difficulty.", UiTheme.ACCENT);
        this.useGameRules();
        this.moduleManager.register("summary", "s", "Summary", "Summary", "Review and launch the configured match.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            int cx1 = this.layout.mainPanel().x() + 150;
            int cx2 = this.layout.mainPanel().x() + 500;
            int sx1 = cx1 + 178;
            int sx2 = cx2 + 178;

            // Row 1
            this.seedModeButton = this.addButton(screen, this.seedMode.displayName, cx1, this.layout.mainPanel().y() + 124, 170, () -> {
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
            this.seedValueField = this.addField(screen, cx2, this.layout.mainPanel().y() + 124, this.seedMode == SeedMode.FIXED ? this.seedValue : "", "Fixed seed");
            if (this.seedMode == SeedMode.RANDOM) {
                this.seedValueField.setEditable(false);
                this.seedValueField.active = false;
                this.seedValueField.setSuggestion("Enter world seed");
            } else {
                this.seedValueField.setEditable(true);
                this.seedValueField.active = true;
                this.seedValueField.setSuggestion("");
            }

            // Row 2
            this.gracePeriodField = this.addField(screen, cx1, this.layout.mainPanel().y() + 156, Integer.toString(this.gracePeriodSeconds), "Hunter release seconds");
            this.addStepper(screen, this.gracePeriodField, sx1, this.layout.mainPanel().y() + 156, 0, 3600, 5);

            // Row 3
            this.huntersCompassButton = this.addButton(screen, "Hunters Compass: " + onOff(this.huntersCompassEnabled), cx1, this.layout.mainPanel().y() + 208, 170, () -> {
                this.huntersCompassEnabled = !this.huntersCompassEnabled;
                this.huntersCompassButton.setMessage(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)));
            });
            this.compassCooldownField = this.addField(screen, cx2, this.layout.mainPanel().y() + 208, Integer.toString(this.compassCooldownSeconds), "Compass cooldown seconds");
            this.addStepper(screen, this.compassCooldownField, sx2, this.layout.mainPanel().y() + 208, 0, 300, 1);

            // Row 4
            this.netherTrackingButton = this.addButton(screen, "Nether Tracking: " + onOff(this.netherTrackingEnabled), cx1, this.layout.mainPanel().y() + 240, 170, () -> {
                this.netherTrackingEnabled = !this.netherTrackingEnabled;
                this.netherTrackingButton.setMessage(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)));
            });
            this.runnerGlowPulseField = this.addField(screen, cx2, this.layout.mainPanel().y() + 240, Integer.toString(this.runnerGlowPulseMinutes), "Runner glow pulse minutes");
            this.addStepper(screen, this.runnerGlowPulseField, sx2, this.layout.mainPanel().y() + 240, 0, 120, 5);

            // Row 5
            this.runnerLivesButton = this.addButton(screen, "Runner Lives: " + formatLives(this.runnerLives), cx1, this.layout.mainPanel().y() + 292, 170, () -> {
                this.runnerLives = nextLivesValue(this.runnerLives);
                this.runnerLivesButton.setMessage(Text.literal("Runner Lives: " + formatLives(this.runnerLives)));
            });
            this.respawnDelayField = this.addField(screen, cx2, this.layout.mainPanel().y() + 292, Integer.toString(this.respawnDelaySeconds), "Runner respawn seconds");
            this.addStepper(screen, this.respawnDelayField, sx2, this.layout.mainPanel().y() + 292, 0, 3600, 30);

            // Row 6
            this.hunterLivesButton = this.addButton(screen, "Hunter Lives: " + formatLives(this.hunterLives), cx1, this.layout.mainPanel().y() + 324, 170, () -> {
                this.hunterLives = nextLivesValue(this.hunterLives);
                this.hunterLivesButton.setMessage(Text.literal("Hunter Lives: " + formatLives(this.hunterLives)));
            });
            this.hunterRespawnDelayField = this.addField(screen, cx2, this.layout.mainPanel().y() + 324, Integer.toString(this.hunterRespawnDelaySeconds), "Hunter respawn seconds");
            this.addStepper(screen, this.hunterRespawnDelayField, sx2, this.layout.mainPanel().y() + 324, 0, 3600, 5);
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
            Text.literal("Runner lives: " + formatLives(this.runnerLives) + ", respawn " + this.respawnDelaySeconds + "s"),
            Text.literal("Hunter lives: " + formatLives(this.hunterLives) + ", respawn " + this.hunterRespawnDelaySeconds + "s"),
            Text.literal("Seed: " + this.seedMode.displayName)
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int lx1 = this.layout.mainPanel().x() + 24;
        int lx2 = this.layout.mainPanel().x() + 374;
        int by = this.layout.mainPanel().y();

        if (this.moduleManager.isActive("rules")) {
            context.drawText(textRenderer, Text.literal("World & Startup"), lx1, by + 108, UiTheme.ACCENT_BLUE, false);
            context.drawText(textRenderer, Text.literal("Seed Mode"), lx1, by + 130, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Fixed Seed"), lx2, by + 130, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Release Delay"), lx1, by + 162, UiTheme.TEXT_MUTED, false);

            context.drawText(textRenderer, Text.literal("Tracking & Difficulty"), lx1, by + 192, UiTheme.ACCENT_BLUE, false);
            context.drawText(textRenderer, Text.literal("Hunters Compass"), lx1, by + 214, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Compass Cooldown"), lx2, by + 214, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Nether Tracking"), lx1, by + 246, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Runner Glow Pulse"), lx2, by + 246, UiTheme.TEXT_MUTED, false);

            context.drawText(textRenderer, Text.literal("Lives & Respawns"), lx1, by + 276, UiTheme.ACCENT_BLUE, false);
            context.drawText(textRenderer, Text.literal("Runner Lives"), lx1, by + 298, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Runner Respawn"), lx2, by + 298, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Hunter Lives"), lx1, by + 330, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Hunter Respawn"), lx2, by + 330, UiTheme.TEXT_MUTED, false);
        }
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
            this.respawnDelaySeconds = readClamped(this.respawnDelayField, this.respawnDelaySeconds, 0, 3600);
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
        builder.settings().putInt("speedrunnerRespawnDelaySeconds", this.respawnDelaySeconds);
        builder.settings().putInt("hunterRespawnDelaySeconds", this.hunterRespawnDelaySeconds);
        builder.settings().putInt("compassCooldownSeconds", this.compassCooldownSeconds);
        builder.settings().putInt("runnerGlowPulseMinutes", this.runnerGlowPulseMinutes);
        builder.settings().putInt("runnerLives", this.runnerLives);
        builder.settings().putInt("hunterLives", this.hunterLives);
        builder.settings().putBoolean("netherTracking", this.netherTrackingEnabled);
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
