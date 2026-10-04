package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.TeamDraft;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.DynamicTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import dev.frost.miniverse.minigame.impl.skywars.SkywarsDefinition;
import dev.frost.miniverse.minigame.impl.skywars.SkywarsSettings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class SkywarsWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final DynamicTeamSelectionGrid teamGrid = new DynamicTeamSelectionGrid();

    private ButtonWidget modeButton;
    private IntFieldWidget refillIntervalField;
    private IntFieldWidget cageTimerField;
    private IntFieldWidget timeLimitField;
    private ButtonWidget instantVoidButton;

    private String mode = SkywarsSettings.MODE_NORMAL;
    private int refillIntervalSeconds = SkywarsSettings.DEFAULT_REFILL_INTERVAL;
    private int cageTimerSeconds = SkywarsSettings.DEFAULT_CAGE_TIMER;
    private int timeLimitSeconds = SkywarsSettings.DEFAULT_TIME_LIMIT;
    private boolean instantVoidDeath = true;
    private boolean teamChatEnabled = false;

    public SkywarsWorkspaceView() {
        super("skywars");
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players to teams.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for Skywars.", UiTheme.ACCENT_BLUE, "Valid Skywars Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure mode, refills, and timers.", UiTheme.ACCENT);

        // Pre-create two teams
        this.teamGrid.clear();
        this.teamGrid.addTeam("");
        this.teamGrid.addTeam("");
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("teams")) {
            this.addActionButton(screen, "Add Team", this.layout.mainPanel().x() + 14, this.layout.mainPanel().y() + 40, 100, "Add a new team.", this.teamGrid::createTeam);
            this.addActionButton(screen, "Remove Team", this.layout.mainPanel().x() + 120, this.layout.mainPanel().y() + 40, 100, "Remove selected team.", this.teamGrid::deleteSelectedTeam);
        } else if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            WorkspaceTooltip modeTooltip = WorkspaceTooltip.cycle(() -> this.mode.equalsIgnoreCase(SkywarsSettings.MODE_INSANE) ? 1 : 0, new String[]{
                "Normal Mode: Balanced starter gear, iron armor, and standard mid loot.",
                "Insane Mode: Diamond gear, enchantments, ender pearls, and powerful loot."
            });
            WorkspaceTooltip chatTooltip = WorkspaceTooltip.toggle(() -> this.teamChatEnabled,
                "Chat is routed to team members only (use ! for global chat).",
                "Vanilla chat is used for all players.");

            this.rulesLayout.addRow(
                "Game Mode", modeTooltip, (s, x, y, w) -> {
                    this.modeButton = this.addCycleButton(
                        s,
                        () -> "Mode: " + this.mode.toUpperCase(),
                        () -> this.mode.equalsIgnoreCase(SkywarsSettings.MODE_INSANE) ? 1 : 0,
                        x, y, w,
                        modeTooltip,
                        2,
                        () -> {
                            this.mode = this.mode.equalsIgnoreCase(SkywarsSettings.MODE_INSANE)
                                ? SkywarsSettings.MODE_NORMAL
                                : SkywarsSettings.MODE_INSANE;
                            this.modeButton.setMessage(Text.literal("Mode: " + this.mode.toUpperCase()));
                        }
                    );
                },
                "Team Chat", chatTooltip, (s, x, y, w) -> {
                    this.addToggleButton(s, "Team Chat", () -> this.teamChatEnabled, x, y, w,
                        chatTooltip,
                        () -> this.teamChatEnabled = !this.teamChatEnabled);
                }
            );

            WorkspaceTooltip refillTooltip = WorkspaceTooltip.dynamic(() -> "All chests will be repopulated with fresh loot every " + (this.refillIntervalField != null ? this.refillIntervalField.getIntValue(this.refillIntervalSeconds) : this.refillIntervalSeconds) + " seconds.");
            WorkspaceTooltip cageTooltip = WorkspaceTooltip.dynamic(() -> "Players are encased in cages for " + (this.cageTimerField != null ? this.cageTimerField.getIntValue(this.cageTimerSeconds) : this.cageTimerSeconds) + " seconds before release.");

            this.rulesLayout.addRow(
                "Chest Refill (s)", refillTooltip, (s, x, y, w) -> {
                    this.refillIntervalField = this.addIntField(
                        s, "Chest refill interval", x, y, this.refillIntervalSeconds, 30, 1800, w, refillTooltip
                    );
                },
                "Cage Timer (s)", cageTooltip, (s, x, y, w) -> {
                    this.cageTimerField = this.addIntField(
                        s, "Pre-match cage timer", x, y, this.cageTimerSeconds, 3, 60, w, cageTooltip
                    );
                }
            );

            WorkspaceTooltip timeLimitTooltip = WorkspaceTooltip.dynamic(() -> "Match ends in a draw after " + (this.timeLimitField != null ? this.timeLimitField.getIntValue(this.timeLimitSeconds) : this.timeLimitSeconds) + " seconds.");
            WorkspaceTooltip voidTooltip = WorkspaceTooltip.cycle(() -> this.instantVoidDeath ? 0 : 1, new String[]{
                "Players are immediately eliminated when falling into the void.",
                "Vanilla void damage applies tick-by-tick."
            });

            this.rulesLayout.addRow(
                "Time Limit (s)", timeLimitTooltip, (s, x, y, w) -> {
                    this.timeLimitField = this.addIntField(
                        s, "Time limit seconds", x, y, this.timeLimitSeconds, 60, 3600, w, timeLimitTooltip
                    );
                },
                "Void Death", voidTooltip, (s, x, y, w) -> {
                    this.instantVoidButton = this.addCycleButton(
                        s,
                        () -> "Void Death: " + (this.instantVoidDeath ? "INSTANT" : "VANILLA"),
                        () -> this.instantVoidDeath ? 0 : 1,
                        x, y, w,
                        voidTooltip,
                        2,
                        () -> {
                            this.instantVoidDeath = !this.instantVoidDeath;
                            this.instantVoidButton.setMessage(Text.literal("Void Death: " + (this.instantVoidDeath ? "INSTANT" : "VANILLA")));
                        }
                    );
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
            Text.literal("Mode: " + this.mode.toUpperCase()),
            Text.literal("Refill: " + this.refillIntervalSeconds + "s"),
            Text.literal("Cage Timer: " + this.cageTimerSeconds + "s"),
            Text.literal("Time Limit: " + this.timeLimitSeconds + "s"),
            Text.literal("Instant Void: " + (this.instantVoidDeath ? "Yes" : "No")),
            Text.literal("Team Chat: " + (this.teamChatEnabled ? "ON" : "OFF")),
            Text.literal("Teams: " + this.teamGrid.getTeams().size())
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
            this.refillIntervalSeconds = readClamped(this.refillIntervalField, this.refillIntervalSeconds, 30, 1800);
            this.cageTimerSeconds = readClamped(this.cageTimerField, this.cageTimerSeconds, 3, 60);
            this.timeLimitSeconds = readClamped(this.timeLimitField, this.timeLimitSeconds, 60, 3600);
        }
    }

    @Override
    public String title() {
        return "Skywars Setup";
    }

    @Override
    public String subtitle() {
        return "Team-based island battle and chest loot configuration";
    }

    @Override
    public String gameId() {
        return SkywarsDefinition.ID;
    }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.teamGrid.getTeams().size() < 2) {
            return ValidationResult.error("Skywars requires at least 2 teams.");
        }
        int activeTeams = 0;
        for (TeamDraft team : this.teamGrid.getTeams()) {
            if (!team.members().isEmpty()) {
                activeTeams++;
            }
        }
        if (activeTeams < 2) {
            return ValidationResult.error("At least 2 teams must have players assigned.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putString("mode", this.mode);
        builder.settings().putInt("refillIntervalSeconds", this.refillIntervalSeconds);
        builder.settings().putInt("cageTimerSeconds", this.cageTimerSeconds);
        builder.settings().putInt("timeLimitSeconds", this.timeLimitSeconds);
        builder.settings().putBoolean("instantVoidDeath", this.instantVoidDeath);
        builder.settings().putBoolean("teamChatEnabled", this.teamChatEnabled);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        List<TeamDraft> teams = this.teamGrid.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            TeamDraft team = teams.get(i);
            String groupId = "team_" + i;
            String groupLabel = team.label().isBlank() ? "Team " + (i + 1) : team.label();

            List<SessionSnapshotData.RosterEntry> members = new ArrayList<>();
            for (TeamDraft.Member member : team.members()) {
                members.add(new SessionSnapshotData.RosterEntry(member.uuid().toString(), member.name()));
            }
            builder.addGroup(groupId, groupLabel, members);
        }
    }

    @Override
    protected void applyPresetSettings(NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("mode")) this.mode = settings.getString("mode");
        if (settings.contains("refillIntervalSeconds")) this.refillIntervalSeconds = settings.getInt("refillIntervalSeconds");
        if (settings.contains("cageTimerSeconds")) this.cageTimerSeconds = settings.getInt("cageTimerSeconds");
        if (settings.contains("timeLimitSeconds")) this.timeLimitSeconds = settings.getInt("timeLimitSeconds");
        if (settings.contains("instantVoidDeath")) this.instantVoidDeath = settings.getBoolean("instantVoidDeath");
        if (settings.contains("teamChatEnabled")) this.teamChatEnabled = settings.getBoolean("teamChatEnabled");

        if (this.modeButton != null) this.modeButton.setMessage(Text.literal("Mode: " + this.mode.toUpperCase()));
        if (this.refillIntervalField != null) this.refillIntervalField.setText(String.valueOf(this.refillIntervalSeconds));
        if (this.cageTimerField != null) this.cageTimerField.setText(String.valueOf(this.cageTimerSeconds));
        if (this.timeLimitField != null) this.timeLimitField.setText(String.valueOf(this.timeLimitSeconds));
        if (this.instantVoidButton != null) this.instantVoidButton.setMessage(Text.literal("Void Death: " + (this.instantVoidDeath ? "INSTANT" : "VANILLA")));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.mode = SkywarsSettings.MODE_NORMAL;
        this.refillIntervalSeconds = SkywarsSettings.DEFAULT_REFILL_INTERVAL;
        this.cageTimerSeconds = SkywarsSettings.DEFAULT_CAGE_TIMER;
        this.timeLimitSeconds = SkywarsSettings.DEFAULT_TIME_LIMIT;
        this.instantVoidDeath = true;
        this.teamChatEnabled = false;

        if (this.modeButton != null) this.modeButton.setMessage(Text.literal("Mode: " + this.mode.toUpperCase()));
        if (this.refillIntervalField != null) this.refillIntervalField.setText(String.valueOf(this.refillIntervalSeconds));
        if (this.cageTimerField != null) this.cageTimerField.setText(String.valueOf(this.cageTimerSeconds));
        if (this.timeLimitField != null) this.timeLimitField.setText(String.valueOf(this.timeLimitSeconds));
        if (this.instantVoidButton != null) this.instantVoidButton.setMessage(Text.literal("Void Death: " + (this.instantVoidDeath ? "INSTANT" : "VANILLA")));
    }
}
