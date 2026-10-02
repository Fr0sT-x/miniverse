package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.TeamDraft;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.DynamicTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class CaptureTheFlagWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final DynamicTeamSelectionGrid teamGrid = new DynamicTeamSelectionGrid();

    private IntFieldWidget targetCapturesField;
    private IntFieldWidget matchDurationField;
    private IntFieldWidget respawnDelayField;
    private IntFieldWidget flagReturnDelayField;

    private boolean eliminationMode = false;
    private int targetCaptures = 3;
    private int matchDurationMinutes = 15;
    private int respawnDelaySeconds = 5;
    private int flagReturnDelaySeconds = 15;
    private boolean requireOwnFlagAtBase = true;
    private boolean carrierGlowing = true;
    private boolean teamChatEnabled = false;
    private boolean allowInvisibilityPotion = true;
    private boolean naturalRegeneration = true;
    private boolean suddenDeath = true;

    public CaptureTheFlagWorkspaceView() {
        super(CaptureTheFlagDefinition.ID);
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players to teams.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for Capture The Flag.", UiTheme.ACCENT_BLUE, "Valid CTF Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure game mode, timers, and capture rules.", UiTheme.ACCENT);

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

            this.rulesLayout.addRow(
                "Game Mode", (s, x, y, w) -> {
                    this.addToggleButton(s, "Elimination Mode", () -> this.eliminationMode, x, y, w,
                        new BinaryTooltip("ON: Captured flags disable enemy respawns until squad wipe.", "OFF: Standard score race to target captures."),
                        () -> this.eliminationMode = !this.eliminationMode);
                },
                "Target Captures", (s, x, y, w) -> {
                    this.targetCapturesField = this.addIntField(s, x, y, this.targetCaptures, w, "Target Captures", val -> "Captures needed to win/eliminate: " + val);
                }
            );

            this.rulesLayout.addRow(
                "Match Duration (m)", (s, x, y, w) -> {
                    this.matchDurationField = this.addIntField(s, x, y, this.matchDurationMinutes, w, "Match Duration", val -> "Match length in minutes: " + val);
                },
                "Respawn Delay (s)", (s, x, y, w) -> {
                    this.respawnDelayField = this.addIntField(s, x, y, this.respawnDelaySeconds, w, "Respawn Delay", val -> "Seconds before respawning: " + val);
                }
            );

            this.rulesLayout.addRow(
                "Flag Return Delay (s)", (s, x, y, w) -> {
                    this.flagReturnDelayField = this.addIntField(s, x, y, this.flagReturnDelaySeconds, w, "Flag Return Delay", val -> "Seconds before dropped flag auto-returns: " + val);
                },
                "Own Flag at Base", (s, x, y, w) -> {
                    this.addToggleButton(s, "Own Flag Required", () -> this.requireOwnFlagAtBase, x, y, w,
                        new BinaryTooltip("ON: Your flag must be safe at base to capture enemy flags.", "OFF: Enemy flags can be captured anytime."),
                        () -> this.requireOwnFlagAtBase = !this.requireOwnFlagAtBase);
                }
            );

            this.rulesLayout.addRow(
                "Carrier Glowing", (s, x, y, w) -> {
                    this.addToggleButton(s, "Carrier Glowing", () -> this.carrierGlowing, x, y, w,
                        new BinaryTooltip("ON: Flag carriers are highlighted with glowing aura.", "OFF: Carriers are not highlighted."),
                        () -> this.carrierGlowing = !this.carrierGlowing);
                },
                "Team Chat", (s, x, y, w) -> {
                    this.addToggleButton(s, "Team Chat", () -> this.teamChatEnabled, x, y, w,
                        new BinaryTooltip("ON: Chat is routed to team members only (use ! for global chat).", "OFF: Vanilla chat is used for all players."),
                        () -> this.teamChatEnabled = !this.teamChatEnabled);
                }
            );

            this.rulesLayout.addRow(
                "Invis Potion", (s, x, y, w) -> {
                    this.addToggleButton(s, "Invisibility Potion", () -> this.allowInvisibilityPotion, x, y, w,
                        new BinaryTooltip("ON: Invisibility potions can be purchased in the shop.", "OFF: Invisibility potions are removed from the shop."),
                        () -> this.allowInvisibilityPotion = !this.allowInvisibilityPotion);
                },
                "Natural Regen", (s, x, y, w) -> {
                    this.addToggleButton(s, "Natural Regeneration", () -> this.naturalRegeneration, x, y, w,
                        new BinaryTooltip("ON: Players regenerate health naturally when hunger is full.", "OFF: Players only regenerate health from items/powerups."),
                        () -> this.naturalRegeneration = !this.naturalRegeneration);
                }
            );

            this.rulesLayout.addRow(
                "Sudden Death", (s, x, y, w) -> {
                    this.addToggleButton(s, "Sudden Death", () -> this.suddenDeath, x, y, w,
                        new BinaryTooltip("ON: Enters overtime if scores are tied or a flag is carried when time expires.", "OFF: Game ends immediately when match time expires."),
                        () -> this.suddenDeath = !this.suddenDeath);
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
            Text.literal("Mode: " + (this.eliminationMode ? "Elimination" : "Standard")),
            Text.literal("Target Captures: " + this.targetCaptures),
            Text.literal("Duration: " + this.matchDurationMinutes + "m"),
            Text.literal("Respawn Delay: " + this.respawnDelaySeconds + "s"),
            Text.literal("Flag Return: " + this.flagReturnDelaySeconds + "s"),
            Text.literal("Own Flag Base: " + (this.requireOwnFlagAtBase ? "YES" : "NO")),
            Text.literal("Invis Potion: " + (this.allowInvisibilityPotion ? "ON" : "OFF")),
            Text.literal("Natural Regen: " + (this.naturalRegeneration ? "ON" : "OFF")),
            Text.literal("Sudden Death: " + (this.suddenDeath ? "ON" : "OFF")),
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
            this.targetCaptures = readClamped(this.targetCapturesField, this.targetCaptures, 1, 10);
            this.matchDurationMinutes = readClamped(this.matchDurationField, this.matchDurationMinutes, 5, 60);
            this.respawnDelaySeconds = readClamped(this.respawnDelayField, this.respawnDelaySeconds, 1, 30);
            this.flagReturnDelaySeconds = readClamped(this.flagReturnDelayField, this.flagReturnDelaySeconds, 5, 60);
        }
    }

    @Override
    public String title() { return "Capture The Flag Setup"; }

    @Override
    public String subtitle() { return "Capture enemy banners and defend your base"; }

    @Override
    public String gameId() { return CaptureTheFlagDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.teamGrid.getTeams().size() < 2) {
            return ValidationResult.error("Capture The Flag requires at least 2 teams.");
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
        builder.settings().putBoolean("eliminationMode", this.eliminationMode);
        builder.settings().putInt("targetCaptures", this.targetCaptures);
        builder.settings().putInt("matchDurationMinutes", this.matchDurationMinutes);
        builder.settings().putInt("respawnDelaySeconds", this.respawnDelaySeconds);
        builder.settings().putInt("flagReturnDelaySeconds", this.flagReturnDelaySeconds);
        builder.settings().putBoolean("requireOwnFlagAtBase", this.requireOwnFlagAtBase);
        builder.settings().putBoolean("carrierGlowing", this.carrierGlowing);
        builder.settings().putBoolean("teamChatEnabled", this.teamChatEnabled);
        builder.settings().putBoolean("allowInvisibilityPotion", this.allowInvisibilityPotion);
        builder.settings().putBoolean("naturalRegeneration", this.naturalRegeneration);
        builder.settings().putBoolean("suddenDeath", this.suddenDeath);
    }

    @Override
    protected void applyPresetSettings(NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("eliminationMode")) {
            this.eliminationMode = settings.getBoolean("eliminationMode");
        }
        if (settings.contains("targetCaptures", NbtElement.NUMBER_TYPE)) {
            this.targetCaptures = settings.getInt("targetCaptures");
        }
        if (settings.contains("matchDurationMinutes", NbtElement.NUMBER_TYPE)) {
            this.matchDurationMinutes = settings.getInt("matchDurationMinutes");
        }
        if (settings.contains("respawnDelaySeconds", NbtElement.NUMBER_TYPE)) {
            this.respawnDelaySeconds = settings.getInt("respawnDelaySeconds");
        }
        if (settings.contains("flagReturnDelaySeconds", NbtElement.NUMBER_TYPE)) {
            this.flagReturnDelaySeconds = settings.getInt("flagReturnDelaySeconds");
        }
        if (settings.contains("requireOwnFlagAtBase")) {
            this.requireOwnFlagAtBase = settings.getBoolean("requireOwnFlagAtBase");
        }
        if (settings.contains("carrierGlowing")) {
            this.carrierGlowing = settings.getBoolean("carrierGlowing");
        }
        if (settings.contains("teamChatEnabled")) {
            this.teamChatEnabled = settings.getBoolean("teamChatEnabled");
        }
        if (settings.contains("allowInvisibilityPotion")) {
            this.allowInvisibilityPotion = settings.getBoolean("allowInvisibilityPotion");
        }
        if (settings.contains("naturalRegeneration")) {
            this.naturalRegeneration = settings.getBoolean("naturalRegeneration");
        }
        if (settings.contains("suddenDeath")) {
            this.suddenDeath = settings.getBoolean("suddenDeath");
        }

        if (this.targetCapturesField != null) this.targetCapturesField.setText(String.valueOf(this.targetCaptures));
        if (this.matchDurationField != null) this.matchDurationField.setText(String.valueOf(this.matchDurationMinutes));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelaySeconds));
        if (this.flagReturnDelayField != null) this.flagReturnDelayField.setText(String.valueOf(this.flagReturnDelaySeconds));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.eliminationMode = false;
        this.targetCaptures = 3;
        this.matchDurationMinutes = 15;
        this.respawnDelaySeconds = 5;
        this.flagReturnDelaySeconds = 15;
        this.requireOwnFlagAtBase = true;
        this.carrierGlowing = true;
        this.teamChatEnabled = false;
        this.allowInvisibilityPotion = true;
        this.naturalRegeneration = true;
        this.suddenDeath = true;

        if (this.targetCapturesField != null) this.targetCapturesField.setText(String.valueOf(this.targetCaptures));
        if (this.matchDurationField != null) this.matchDurationField.setText(String.valueOf(this.matchDurationMinutes));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelaySeconds));
        if (this.flagReturnDelayField != null) this.flagReturnDelayField.setText(String.valueOf(this.flagReturnDelaySeconds));
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
}
