package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.TeamDraft;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.DynamicTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.bedwars.BedwarsDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class BedwarsWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final DynamicTeamSelectionGrid teamGrid = new DynamicTeamSelectionGrid();

    private IntFieldWidget respawnDelayField;
    private IntFieldWidget ironGenField;
    private IntFieldWidget goldGenField;
    private IntFieldWidget diamondGenField;
    private IntFieldWidget emeraldGenField;

    private int respawnDelay = 5;
    private int ironGenTicks = 20;
    private int goldGenTicks = 160;
    private int diamondGenTicks = 500;
    private int emeraldGenTicks = 700;

    public BedwarsWorkspaceView() {
        super("bedwars");
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Assign players to teams.", UiTheme.ACCENT_RED);
        this.useMapSelection("map", "M", "Map Selection", "Setup", "Choose a validated map configured for Bed Wars.", UiTheme.ACCENT_BLUE, "Valid Bed Wars Maps");
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure respawn and generator speeds.", UiTheme.ACCENT);
        this.moduleManager.register("summary", "S", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT);

        // Pre-create two teams
        this.teamGrid.clear();
        this.teamGrid.addTeam("");
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("teams")) {
            // Add buttons for dynamic teams
            this.addActionButton(screen, "Add Team", this.layout.mainPanel().x() + 14, this.layout.mainPanel().y() + 40, 100, "Add a new team.", this.teamGrid::createTeam);
            this.addActionButton(screen, "Remove Team", this.layout.mainPanel().x() + 120, this.layout.mainPanel().y() + 40, 100, "Remove selected team.", this.teamGrid::deleteSelectedTeam);
        } else if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Respawn Delay (s)", (s, x, y, w) -> {
                    this.respawnDelayField = this.addIntField(s, x, y, this.respawnDelay, w, "Respawn Delay", val -> "Seconds to respawn: " + val);
                }
            );

            this.rulesLayout.addRow(
                "Iron Gen (ticks)", (s, x, y, w) -> {
                    this.ironGenField = this.addIntField(s, x, y, this.ironGenTicks, w, "Iron Gen Interval", val -> "Iron generates every " + val + " ticks.");
                },
                "Gold Gen (ticks)", (s, x, y, w) -> {
                    this.goldGenField = this.addIntField(s, x, y, this.goldGenTicks, w, "Gold Gen Interval", val -> "Gold generates every " + val + " ticks.");
                }
            );

            this.rulesLayout.addRow(
                "Diamond Gen (ticks)", (s, x, y, w) -> {
                    this.diamondGenField = this.addIntField(s, x, y, this.diamondGenTicks, w, "Diamond Gen Interval", val -> "Diamond generates every " + val + " ticks.");
                },
                "Emerald Gen (ticks)", (s, x, y, w) -> {
                    this.emeraldGenField = this.addIntField(s, x, y, this.emeraldGenTicks, w, "Emerald Gen Interval", val -> "Emerald generates every " + val + " ticks.");
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
            Text.literal("Respawn Delay: " + this.respawnDelay + "s"),
            Text.literal("Iron Gen: " + this.ironGenTicks + "t"),
            Text.literal("Gold Gen: " + this.goldGenTicks + "t"),
            Text.literal("Diamond Gen: " + this.diamondGenTicks + "t"),
            Text.literal("Emerald Gen: " + this.emeraldGenTicks + "t"),
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
            this.respawnDelay = readClamped(this.respawnDelayField, this.respawnDelay, 1, 30);
            this.ironGenTicks = readClamped(this.ironGenField, this.ironGenTicks, 1, 1000);
            this.goldGenTicks = readClamped(this.goldGenField, this.goldGenTicks, 1, 1000);
            this.diamondGenTicks = readClamped(this.diamondGenField, this.diamondGenTicks, 1, 2000);
            this.emeraldGenTicks = readClamped(this.emeraldGenField, this.emeraldGenTicks, 1, 2000);
        }
    }

    @Override
    public String title() { return "Bed Wars Setup"; }

    @Override
    public String subtitle() { return "Defend your bed and destroy others"; }

    @Override
    public String gameId() { return BedwarsDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.teamGrid.getTeams().size() < 2) {
            return ValidationResult.error("Bed Wars requires at least 2 teams.");
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
        builder.settings().putInt("respawnDelaySeconds", this.respawnDelay);
        builder.settings().putInt("ironGenIntervalTicks", this.ironGenTicks);
        builder.settings().putInt("goldGenIntervalTicks", this.goldGenTicks);
        builder.settings().putInt("diamondGenIntervalTicks", this.diamondGenTicks);
        builder.settings().putInt("emeraldGenIntervalTicks", this.emeraldGenTicks);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        List<TeamDraft> teams = this.teamGrid.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            TeamDraft team = teams.get(i);
            String groupId = team.label().isBlank() ? "team_" + i : team.label().toLowerCase().replace(" ", "_");
            String groupLabel = team.label().isBlank() ? "Team " + (i + 1) : team.label();
            
            List<SessionSnapshotData.RosterEntry> members = new ArrayList<>();
            for (TeamDraft.Member member : team.members()) {
                members.add(new SessionSnapshotData.RosterEntry(member.uuid().toString(), member.name()));
            }
            builder.addGroup(groupId, groupLabel, members);
        }
    }
}
