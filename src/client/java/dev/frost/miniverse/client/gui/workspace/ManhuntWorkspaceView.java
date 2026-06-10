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
    private long seedValue = System.currentTimeMillis();

    public ManhuntWorkspaceView() {
        super("manhunt");
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("speedrunner", "Speedrunners", 0x4D8DFF, false);
        this.teamGrid.addColumn("hunter", "Hunters", 0xE85D75, false);
        
        this.useRosterGrid(this.teamGrid, "teams", ">", "Teams", "Setup", "Assign players into speedrunners and hunters.", UiTheme.ACCENT_RED);
        this.moduleManager.register("seed", "w", "World Seed", "Rules", "Choose the world seed behavior.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "r", "Match Rules", "Rules", "Tune release timing and match startup rules.", UiTheme.ACCENT);
        this.moduleManager.register("tracking", "t", "Tracking", "Rules", "Configure compass behavior and Nether tracking.", UiTheme.ACCENT_BLUE);
        this.moduleManager.register("lives", "l", "Lives & Respawns", "Rules", "Set life limits and respawn delays.", UiTheme.ACCENT_RED);
        this.moduleManager.register("difficulty", "d", "Difficulty", "Rules", "Add pressure and visibility modifiers.", UiTheme.ACCENT_GREEN);
        this.moduleManager.register("summary", "s", "Summary", "Summary", "Review and launch the configured match.", UiTheme.ACCENT);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("seed")) {
            this.seedModeButton = this.addButton(screen, this.seedMode.displayName, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, 170, () -> {
                this.seedMode = this.seedMode == SeedMode.RANDOM ? SeedMode.FIXED : SeedMode.RANDOM;
                this.seedModeButton.setMessage(Text.literal(this.seedMode.displayName));
            });
            this.seedValueField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, Long.toString(this.seedValue), "Fixed seed");
        } else if (this.moduleManager.isActive("rules")) {
            this.gracePeriodField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, Integer.toString(this.gracePeriodSeconds), "Hunter release seconds");
            this.addStepper(screen, this.gracePeriodField, this.layout.mainPanel().x() + 358, this.layout.mainPanel().y() + 96, 0, 3600, 5);
        } else if (this.moduleManager.isActive("tracking")) {
            this.huntersCompassButton = this.addButton(screen, "Hunters Compass: " + onOff(this.huntersCompassEnabled), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, 170, () -> {
                this.huntersCompassEnabled = !this.huntersCompassEnabled;
                this.huntersCompassButton.setMessage(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)));
            });
            this.compassCooldownField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, Integer.toString(this.compassCooldownSeconds), "Compass cooldown seconds");
            this.addStepper(screen, this.compassCooldownField, this.layout.mainPanel().x() + 358, this.layout.mainPanel().y() + 128, 0, 300, 1);
            this.netherTrackingButton = this.addButton(screen, "Nether Tracking: " + onOff(this.netherTrackingEnabled), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, 170, () -> {
                this.netherTrackingEnabled = !this.netherTrackingEnabled;
                this.netherTrackingButton.setMessage(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)));
            });
        } else if (this.moduleManager.isActive("lives")) {
            this.runnerLivesButton = this.addButton(screen, "Runner Lives: " + formatLives(this.runnerLives), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, 170, () -> {
                this.runnerLives = nextLivesValue(this.runnerLives);
                this.runnerLivesButton.setMessage(Text.literal("Runner Lives: " + formatLives(this.runnerLives)));
            });
            this.respawnDelayField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, Integer.toString(this.respawnDelaySeconds), "Runner respawn seconds");
            this.addStepper(screen, this.respawnDelayField, this.layout.mainPanel().x() + 358, this.layout.mainPanel().y() + 128, 0, 3600, 30);
            this.hunterLivesButton = this.addButton(screen, "Hunter Lives: " + formatLives(this.hunterLives), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, 170, () -> {
                this.hunterLives = nextLivesValue(this.hunterLives);
                this.hunterLivesButton.setMessage(Text.literal("Hunter Lives: " + formatLives(this.hunterLives)));
            });
            this.hunterRespawnDelayField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 192, Integer.toString(this.hunterRespawnDelaySeconds), "Hunter respawn seconds");
            this.addStepper(screen, this.hunterRespawnDelayField, this.layout.mainPanel().x() + 358, this.layout.mainPanel().y() + 192, 0, 3600, 5);
        } else if (this.moduleManager.isActive("difficulty")) {
            this.runnerGlowPulseField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, Integer.toString(this.runnerGlowPulseMinutes), "Runner glow pulse minutes");
            this.addStepper(screen, this.runnerGlowPulseField, this.layout.mainPanel().x() + 358, this.layout.mainPanel().y() + 96, 0, 120, 5);
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
            context.drawText(textRenderer, Text.literal(this.teamGrid.getMembers("speedrunner").size() + " speedrunner(s) vs " + this.teamGrid.getMembers("hunter").size() + " hunter(s)"), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Release delay: " + this.gracePeriodSeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Tracking: " + onOff(this.huntersCompassEnabled) + ", Nether " + onOff(this.netherTrackingEnabled) + ", cooldown " + this.compassCooldownSeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Runner lives: " + formatLives(this.runnerLives) + ", respawn " + this.respawnDelaySeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Hunter lives: " + formatLives(this.hunterLives) + ", respawn " + this.hunterRespawnDelaySeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Seed: " + this.seedMode.displayName), x + 14, line, UiTheme.TEXT_MUTED, false);
        } else if (!this.moduleManager.isActive("teams")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int labelX = this.layout.mainPanel().x() + 38;
        int labelY = this.layout.mainPanel().y() + 102;
        if (this.moduleManager.isActive("seed")) {
            context.drawText(textRenderer, Text.literal("Seed Mode"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Fixed Seed"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
        } else if (this.moduleManager.isActive("rules")) {
            context.drawText(textRenderer, Text.literal("Hunter Release Delay"), labelX, labelY, UiTheme.TEXT_MUTED, false);
        } else if (this.moduleManager.isActive("tracking")) {
            context.drawText(textRenderer, Text.literal("Hunters Compass"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Compass Cooldown"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Nether Tracking"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
        } else if (this.moduleManager.isActive("lives")) {
            context.drawText(textRenderer, Text.literal("Runner Lives"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Runner Respawn"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Hunter Lives"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Hunter Respawn"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
        } else if (this.moduleManager.isActive("difficulty")) {
            context.drawText(textRenderer, Text.literal("Runner Glow Pulse"), labelX, labelY, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    private void syncStateFromWidgets() {
        if (this.moduleManager.isActive("seed")) {
            if (this.seedValueField != null) {
                try {
                    this.seedValue = Long.parseLong(this.seedValueField.getText().trim());
                } catch (NumberFormatException ignored) {
                    this.seedValueField.setText(Long.toString(this.seedValue));
                }
            }
        } else if (this.moduleManager.isActive("rules")) {
            this.gracePeriodSeconds = readClamped(this.gracePeriodField, this.gracePeriodSeconds, 0, 3600);
        } else if (this.moduleManager.isActive("tracking")) {
            this.compassCooldownSeconds = readClamped(this.compassCooldownField, this.compassCooldownSeconds, 0, 300);
        } else if (this.moduleManager.isActive("lives")) {
            this.respawnDelaySeconds = readClamped(this.respawnDelayField, this.respawnDelaySeconds, 0, 3600);
            this.hunterRespawnDelaySeconds = readClamped(this.hunterRespawnDelayField, this.hunterRespawnDelaySeconds, 0, 3600);
        } else if (this.moduleManager.isActive("difficulty")) {
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
            builder.settings().putLong("seed", this.seedValue);
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
