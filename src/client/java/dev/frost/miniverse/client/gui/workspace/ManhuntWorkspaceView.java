package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.manhunt.ManhuntDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ManhuntWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider, GamemodeWorkspaceView.RosterRefreshable {
    private static final int BUTTON_HEIGHT = 22;
    private static final int SETTINGS_FIELD_WIDTH = 170;
    private static final int RESPAWN_DELAY_DEFAULT_SECONDS = 300;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final StaticTeamSelectionGrid teamGrid = new StaticTeamSelectionGrid();
    private Module activeModule = Module.TEAMS;
    private UiLayout.Rect workspace = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect teamsArea = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect restHuntersButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect clearButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect startButton = new UiLayout.Rect(0, 0, 0, 0);
    private String statusMessage = "";

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

    private String sessionName = "manhunt-" + System.currentTimeMillis();
    private boolean huntersCompassEnabled = true;
    private boolean netherTrackingEnabled = true;
    private int gracePeriodSeconds = 30;
    private int respawnDelaySeconds = RESPAWN_DELAY_DEFAULT_SECONDS;
    private int hunterRespawnDelaySeconds = 0;
    private int compassCooldownSeconds = 2;
    private int runnerGlowPulseMinutes = 0;
    private int runnerLives = -1;
    private int hunterLives = -1;
    private SeedMode seedMode = SeedMode.RANDOM;
    private long seedValue = System.currentTimeMillis();

    public ManhuntWorkspaceView() {
        this.teamGrid.addColumn("available", "Available", 0x7C8088, true);
        this.teamGrid.addColumn("speedrunner", "Speedrunners", 0x4D8DFF, false);
        this.teamGrid.addColumn("hunter", "Hunters", 0xE85D75, false);
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.workspace = workspace;
        UiLayout.Rect mainPanel = workspace.inset(4);
        this.teamsArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 84, mainPanel.width() - 24, mainPanel.height() - 106);
        this.restHuntersButton = new UiLayout.Rect(mainPanel.x() + 14, mainPanel.y() + 50, 102, BUTTON_HEIGHT);
        this.clearButton = new UiLayout.Rect(mainPanel.x() + 124, mainPanel.y() + 50, 62, BUTTON_HEIGHT);
        this.startButton = new UiLayout.Rect(mainPanel.x() + mainPanel.width() - 126, mainPanel.y() + 10, 112, BUTTON_HEIGHT);

        if (this.activeModule == Module.SEED_SETTINGS) {
            this.addSeedWidgets(screen, mainPanel);
        } else if (this.activeModule == Module.MATCH_RULES) {
            this.gracePeriodField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 96, Integer.toString(this.gracePeriodSeconds), "Hunter release seconds");
            this.addStepper(screen, this.gracePeriodField, mainPanel.x() + 338, mainPanel.y() + 96, 0, 3600, 5);
        } else if (this.activeModule == Module.TRACKING) {
            this.huntersCompassButton = this.addButton(screen, "Hunters Compass: " + onOff(this.huntersCompassEnabled), mainPanel.x() + 180, mainPanel.y() + 96, 170, () -> {
                this.huntersCompassEnabled = !this.huntersCompassEnabled;
                this.huntersCompassButton.setMessage(Text.literal("Hunters Compass: " + onOff(this.huntersCompassEnabled)));
            });
            this.compassCooldownField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 128, Integer.toString(this.compassCooldownSeconds), "Compass cooldown seconds");
            this.addStepper(screen, this.compassCooldownField, mainPanel.x() + 338, mainPanel.y() + 128, 0, 300, 1);
            this.netherTrackingButton = this.addButton(screen, "Nether Tracking: " + onOff(this.netherTrackingEnabled), mainPanel.x() + 180, mainPanel.y() + 160, 170, () -> {
                this.netherTrackingEnabled = !this.netherTrackingEnabled;
                this.netherTrackingButton.setMessage(Text.literal("Nether Tracking: " + onOff(this.netherTrackingEnabled)));
            });
        } else if (this.activeModule == Module.LIVES_RESPAWNS) {
            this.runnerLivesButton = this.addButton(screen, "Runner Lives: " + formatLives(this.runnerLives), mainPanel.x() + 180, mainPanel.y() + 96, 170, () -> {
                this.runnerLives = nextLivesValue(this.runnerLives);
                this.runnerLivesButton.setMessage(Text.literal("Runner Lives: " + formatLives(this.runnerLives)));
            });
            this.respawnDelayField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 128, Integer.toString(this.respawnDelaySeconds), "Runner respawn seconds");
            this.addStepper(screen, this.respawnDelayField, mainPanel.x() + 338, mainPanel.y() + 128, 0, 3600, 30);
            this.hunterLivesButton = this.addButton(screen, "Hunter Lives: " + formatLives(this.hunterLives), mainPanel.x() + 180, mainPanel.y() + 168, 170, () -> {
                this.hunterLives = nextLivesValue(this.hunterLives);
                this.hunterLivesButton.setMessage(Text.literal("Hunter Lives: " + formatLives(this.hunterLives)));
            });
            this.hunterRespawnDelayField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 200, Integer.toString(this.hunterRespawnDelaySeconds), "Hunter respawn seconds");
            this.addStepper(screen, this.hunterRespawnDelayField, mainPanel.x() + 338, mainPanel.y() + 200, 0, 3600, 5);
        } else if (this.activeModule == Module.DIFFICULTY) {
            this.runnerGlowPulseField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 96, Integer.toString(this.runnerGlowPulseMinutes), "Runner glow pulse minutes");
            this.addStepper(screen, this.runnerGlowPulseField, mainPanel.x() + 338, mainPanel.y() + 96, 0, 120, 5);
        }
        
        this.teamGrid.setBounds(this.teamsArea);
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(mainPanel.x() + 1, mainPanel.y() + 1, mainPanel.x() + mainPanel.width() - 1, mainPanel.y() + 40, 0x701B2634);
        context.drawText(textRenderer, Text.literal(activeModule.label()), mainPanel.x() + 14, mainPanel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(activeModule.description()), mainPanel.x() + 14, mainPanel.y() + 28, UiTheme.TEXT_DIM, false);

        if (this.activeModule == Module.TEAMS) {
            this.renderTeamActions(context, textRenderer, mouseX, mouseY);
            this.teamGrid.render(context, textRenderer, mouseX, mouseY, delta);
        } else if (this.activeModule == Module.SUMMARY) {
            this.renderSummary(context, textRenderer, mainPanel);
        } else {
            this.renderSettingsModule(context, textRenderer, mainPanel);
        }
        if (!this.statusMessage.isEmpty()) {
            context.drawText(textRenderer, Text.literal(this.statusMessage), mainPanel.x() + 14, mainPanel.y() + mainPanel.height() - 18, UiTheme.SUCCESS, false);
        }
        this.renderActionButton(context, textRenderer, this.startButton, "Start Match", UiTheme.ACCENT_GREEN, this.startButton.contains(mouseX, mouseY));
    }

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        if (this.activeModule != Module.TEAMS && this.activeModule != Module.SUMMARY) {
            this.drawModuleLabels(context, textRenderer, mainPanel);
        } else if (this.activeModule == Module.TEAMS) {
            this.teamGrid.renderForeground(context, textRenderer, workspace, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (this.startButton.contains(mouseX, mouseY)) {
            this.createSession();
            return true;
        }
        if (this.activeModule != Module.TEAMS) {
            return false;
        }
        if (this.restHuntersButton.contains(mouseX, mouseY)) {
            this.restHunters();
            return true;
        }
        if (this.clearButton.contains(mouseX, mouseY)) {
            this.teamGrid.clear();
            return true;
        }

        return this.teamGrid.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.activeModule == Module.TEAMS) {
            return this.teamGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.activeModule == Module.TEAMS) {
            return this.teamGrid.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeModule != Module.TEAMS) {
            return false;
        }
        return this.teamGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    public void refreshRoster() {
        this.teamGrid.refreshRoster();
    }

    @Override
    public String gameId() {
        return ManhuntDefinition.ID;
    }

    @Override
    public java.util.List<WorkspaceModule> modules() {
        java.util.List<WorkspaceModule> modules = new java.util.ArrayList<>();
        for (Module module : Module.values()) {
            String group = module == Module.SUMMARY ? "Summary" : module == Module.TEAMS ? "Setup" : "Rules";
            modules.add(new WorkspaceModule(module.id(), module.icon(), module.label(), group));
        }
        return modules;
    }

    @Override
    public String activeModuleId() {
        return this.activeModule.id();
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        this.activeModule = Module.fromId(moduleId).orElse(this.activeModule);
    }

    @Override
    public String title() {
        return "Manhunt Setup";
    }

    @Override
    public String subtitle() {
        return "Embedded workspace / module-driven setup";
    }

    private void renderSettingsModule(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        int moduleX = mainPanel.x() + 14;
        int moduleY = mainPanel.y() + 72;
        int moduleWidth = Math.min(520, mainPanel.width() - 28);
        int moduleHeight = Math.min(230, mainPanel.height() - 104);
        UiRenderer.panel(context, moduleX, moduleY, moduleWidth, moduleHeight, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(moduleX, moduleY, moduleX + 3, moduleY + moduleHeight, this.activeModule.accent());
        context.drawText(textRenderer, Text.literal(this.activeModule.label()), moduleX + 12, moduleY + 12, this.activeModule.accent(), false);
    }

    private void drawModuleLabels(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        int labelX = mainPanel.x() + 38;
        int y = mainPanel.y() + 102;
        switch (this.activeModule) {
            case SEED_SETTINGS -> {
                context.drawText(textRenderer, Text.literal("Seed Mode"), labelX, y, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Fixed Seed"), labelX, y + 32, UiTheme.TEXT_MUTED, false);
            }
            case MATCH_RULES -> context.drawText(textRenderer, Text.literal("Hunter Release Delay"), labelX, y, UiTheme.TEXT_MUTED, false);
            case TRACKING -> {
                context.drawText(textRenderer, Text.literal("Hunters Compass"), labelX, y, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Compass Cooldown"), labelX, y + 32, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Nether Tracking"), labelX, y + 64, UiTheme.TEXT_MUTED, false);
            }
            case LIVES_RESPAWNS -> {
                context.drawText(textRenderer, Text.literal("Runner Lives"), labelX, y, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Runner Respawn"), labelX, y + 32, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Hunter Lives"), labelX, y + 72, UiTheme.TEXT_MUTED, false);
                context.drawText(textRenderer, Text.literal("Hunter Respawn"), labelX, y + 104, UiTheme.TEXT_MUTED, false);
            }
            case DIFFICULTY -> context.drawText(textRenderer, Text.literal("Runner Glow Pulse"), labelX, y, UiTheme.TEXT_MUTED, false);
            default -> {
            }
        }
    }

    private void addSeedWidgets(SessionScreen screen, UiLayout.Rect mainPanel) {
        this.seedModeButton = this.addButton(screen, this.seedMode.displayName, mainPanel.x() + 180, mainPanel.y() + 96, SETTINGS_FIELD_WIDTH, () -> {
            this.seedMode = this.seedMode == SeedMode.RANDOM ? SeedMode.FIXED : SeedMode.RANDOM;
            this.seedModeButton.setMessage(Text.literal(this.seedMode.displayName));
        });
        this.seedValueField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 128, Long.toString(this.seedValue), "Fixed seed");
    }

    private ButtonWidget addButton(SessionScreen screen, String label, int x, int y, int width, Runnable action) {
        return screen.addWorkspaceChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
            .dimensions(x, y, width, BUTTON_HEIGHT)
            .build());
    }

    private TextFieldWidget addField(SessionScreen screen, int x, int y, String value, String narration) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, SETTINGS_FIELD_WIDTH, BUTTON_HEIGHT, Text.literal(narration));
        field.setMaxLength(64);
        field.setText(value);
        return screen.addWorkspaceChild(field);
    }

    private void addStepper(SessionScreen screen, TextFieldWidget field, int x, int y, int min, int max, int step) {
        this.addButton(screen, "-", x, y, 24, () -> stepField(field, min, max, -step));
        this.addButton(screen, "+", x + 30, y, 24, () -> stepField(field, min, max, step));
    }



    private void renderTeamActions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        this.renderActionButton(context, textRenderer, this.restHuntersButton, "Rest Hunters", UiTheme.ACCENT_RED, this.restHuntersButton.contains(mouseX, mouseY));
        this.renderActionButton(context, textRenderer, this.clearButton, "Clear", UiTheme.ACCENT, this.clearButton.contains(mouseX, mouseY));
        context.drawText(textRenderer, Text.literal("Drag players between columns to assign roles."), this.clearButton.x() + this.clearButton.width() + 12, this.clearButton.y() + 7, UiTheme.TEXT_DIM, false);
    }

    private void renderActionButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(accent, 0.34F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, border);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    private void renderSummary(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        this.syncStateFromWidgets();
        int x = mainPanel.x() + 14;
        int y = mainPanel.y() + 72;
        int width = Math.min(540, mainPanel.width() - 28);
        UiRenderer.panel(context, x, y, width, 194, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(x, y, x + 3, y + 194, UiTheme.ACCENT);
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
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            context.drawText(textRenderer, Text.literal(validation), x + 14, y + 168, UiTheme.WARNING, false);
        }
    }



    private void createSession() {
        this.syncStateFromWidgets();
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            this.statusMessage = validation;
            return;
        }

        NbtCompound plan = new NbtCompound();
        plan.putString("game", ManhuntDefinition.ID);
        plan.putString("name", this.sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.buildSettingsCompound());

        NbtList groups = new NbtList();
        NbtCompound group = new NbtCompound();
        group.putString("id", "manhunt");
        group.putString("name", "Manhunt");
        NbtList members = new NbtList();
        NbtList roles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("speedrunner")) {
            members.add(this.member(entry));
            roles.add(this.roleMember(entry, "speedrunner"));
        }
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("hunter")) {
            members.add(this.member(entry));
            roles.add(this.roleMember(entry, "hunter"));
        }
        group.put("members", members);
        group.put("roles", roles);
        groups.add(group);
        plan.put("groups", groups);

        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(ManhuntDefinition.ID, this.sessionName, plan));
        this.statusMessage = "Requested Manhunt session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putBoolean("huntersCompass", this.huntersCompassEnabled);
        settings.putInt("hunterReleaseDelaySeconds", this.gracePeriodSeconds);
        settings.putInt("speedrunnerRespawnDelaySeconds", this.respawnDelaySeconds);
        settings.putInt("hunterRespawnDelaySeconds", this.hunterRespawnDelaySeconds);
        settings.putInt("compassCooldownSeconds", this.compassCooldownSeconds);
        settings.putInt("runnerGlowPulseMinutes", this.runnerGlowPulseMinutes);
        settings.putInt("runnerLives", this.runnerLives);
        settings.putInt("hunterLives", this.hunterLives);
        settings.putBoolean("netherTracking", this.netherTrackingEnabled);
        settings.putString("seedMode", this.seedMode.nbtValue);
        if (this.seedMode == SeedMode.FIXED) {
            settings.putLong("seed", this.seedValue);
        }
        NbtList roles = new NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("speedrunner")) {
            roles.add(this.roleMember(entry, "speedrunner"));
        }
        for (SessionSnapshotData.RosterEntry entry : this.teamGrid.getMembers("hunter")) {
            roles.add(this.roleMember(entry, "hunter"));
        }
        settings.put("roles", roles);
        return settings;
    }

    private void syncStateFromWidgets() {
        this.gracePeriodSeconds = readClamped(this.gracePeriodField, this.gracePeriodSeconds, 0, 3600);
        this.respawnDelaySeconds = readClamped(this.respawnDelayField, this.respawnDelaySeconds, 0, 3600);
        this.hunterRespawnDelaySeconds = readClamped(this.hunterRespawnDelayField, this.hunterRespawnDelaySeconds, 0, 3600);
        this.compassCooldownSeconds = readClamped(this.compassCooldownField, this.compassCooldownSeconds, 0, 300);
        this.runnerGlowPulseMinutes = readClamped(this.runnerGlowPulseField, this.runnerGlowPulseMinutes, 0, 120);
        if (this.seedValueField != null) {
            try {
                this.seedValue = Long.parseLong(this.seedValueField.getText().trim());
            } catch (NumberFormatException ignored) {
                this.seedValueField.setText(Long.toString(this.seedValue));
            }
        }
    }

    private static int readClamped(TextFieldWidget field, int fallback, int min, int max) {
        if (field == null) {
            return fallback;
        }
        try {
            int value = Math.clamp(Integer.parseInt(field.getText().trim()), min, max);
            field.setText(Integer.toString(value));
            return value;
        } catch (NumberFormatException ignored) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
    }

    private void stepField(TextFieldWidget field, int min, int max, int delta) {
        int value = readClamped(field, min, min, max);
        field.setText(Integer.toString(Math.clamp(value + delta, min, max)));
    }

    private String getStartValidationMessage() {
        if (this.client.player == null) {
            return "Not connected to a server.";
        }
        if (this.sessionName == null || this.sessionName.isBlank()) {
            return "Enter a session name.";
        }
        if (SessionSnapshotData.roster().isEmpty()) {
            return "No players online.";
        }
        if (this.teamGrid.getMembers("speedrunner").isEmpty()) {
            return "Need at least one speedrunner.";
        }
        if (this.teamGrid.getMembers("hunter").isEmpty()) {
            return "Need at least one hunter.";
        }
        return "";
    }

    private void restHunters() {
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            if (this.teamGrid.getMembers("speedrunner").stream().noneMatch(e -> e.uuid().equals(entry.uuid())) && this.teamGrid.getMembers("hunter").stream().noneMatch(e -> e.uuid().equals(entry.uuid()))) {
                this.teamGrid.addMember("hunter", entry);
            }
        }
        this.statusMessage = "Moved all available players to hunters.";
    }

    private NbtCompound member(SessionSnapshotData.RosterEntry entry) {
        NbtCompound compound = new NbtCompound();
        compound.putString("uuid", entry.uuid());
        compound.putString("name", entry.name());
        return compound;
    }

    private NbtCompound roleMember(SessionSnapshotData.RosterEntry entry, String role) {
        NbtCompound compound = new NbtCompound();
        compound.putString("uuid", entry.uuid());
        compound.putString("role", role);
        return compound;
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

    public enum Module {
        TEAMS("teams", ">", "Teams", "Assign players into speedrunners and hunters.", UiTheme.ACCENT_RED),
        SEED_SETTINGS("seed", "w", "World Seed", "Choose the world seed behavior.", UiTheme.ACCENT),
        MATCH_RULES("rules", "r", "Match Rules", "Tune release timing and match startup rules.", UiTheme.ACCENT),
        TRACKING("tracking", "t", "Tracking", "Configure compass behavior and Nether tracking.", UiTheme.ACCENT_BLUE),
        LIVES_RESPAWNS("lives", "l", "Lives & Respawns", "Set life limits and respawn delays.", UiTheme.ACCENT_RED),
        DIFFICULTY("difficulty", "d", "Difficulty", "Add pressure and visibility modifiers.", UiTheme.ACCENT_GREEN),
        SUMMARY("summary", "s", "Summary", "Review and launch the configured match.", UiTheme.ACCENT);

        private final String id;
        private final String icon;
        private final String label;
        private final String description;
        private final int accent;

        Module(String id, String icon, String label, String description, int accent) {
            this.id = id;
            this.icon = icon;
            this.label = label;
            this.description = description;
            this.accent = accent;
        }

        public String id() {
            return this.id;
        }

        public String icon() {
            return this.icon;
        }

        public String label() {
            return this.label;
        }

        private String description() {
            return this.description;
        }

        private int accent() {
            return this.accent;
        }

        private static java.util.Optional<Module> fromId(String id) {
            if (id == null) {
                return java.util.Optional.empty();
            }
            for (Module module : values()) {
                if (module.id.equalsIgnoreCase(id)) {
                    return java.util.Optional.of(module);
                }
            }
            return java.util.Optional.empty();
        }
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
