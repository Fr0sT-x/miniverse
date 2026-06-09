package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.TeamDraft;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintDefinition;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ResourceSprintWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider, GamemodeWorkspaceView.RosterRefreshable {
    private static final int ROW_HEIGHT = 20;
    private static final int TEAM_ROW_HEIGHT = 36;
    private static final int COLUMN_HEADER_HEIGHT = 22;
    private static final int COLUMN_GAP = 12;
    private static final int BUTTON_HEIGHT = 22;
    private static final int[] TEAM_COLORS = {
        0xFF5B8CFF,
        0xFF6BCB77,
        0xFFFF8A5B,
        0xFFE26EFD,
        0xFFF7D154,
        0xFF52D0D1,
        0xFFB06CFF,
        0xFFFF6E91
    };

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Map<String, UiAnimation.Value> rowHovers = new HashMap<>();
    private final List<TeamDraft> teams = new ArrayList<>();
    private final Set<String> selectedPlayerUuids = new LinkedHashSet<>();

    private Module activeModule = Module.TEAMS;
    private UiLayout.Rect workspace = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect teamsArea = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionCreate = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionAssign = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionRemove = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionDelete = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionSolo = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect startButton = new UiLayout.Rect(0, 0, 0, 0);

    private TextFieldWidget timeLimitField;
    private ButtonWidget modeButton;
    private ButtonWidget distributionButton;
    private ButtonWidget tieBreakButton;
    private ButtonWidget objectivesResetButton;

    private String sessionName = "resourcesprint-" + System.currentTimeMillis();
    private ResourceSprintSettings.Mode mode = ResourceSprintSettings.Mode.FIRST_TO_COMPLETE;
    private ResourceSprintSettings.ObjectiveDistributionMode distributionMode = ResourceSprintSettings.ObjectiveDistributionMode.SHARED;
    private ResourceSprintSettings.TieBreakRule tieBreakRule = ResourceSprintSettings.TieBreakRule.SUDDEN_DEATH;
    private int timeLimitSeconds = 3600;
    private java.util.Set<String> objectivesPool = new LinkedHashSet<>();

    private int selectedTeamIndex = -1;
    private int rosterScrollOffset;
    private int teamScrollOffset;
    private int nextTeamNumber = 1;
    private String statusMessage = "";

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.workspace = workspace;
        UiLayout.Rect mainPanel = workspace.inset(4);
        this.teamsArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 88, mainPanel.width() - 24, mainPanel.height() - 116);
        int actionY = mainPanel.y() + 50;
        int actionStart = mainPanel.x() + 14;
        this.actionCreate = new UiLayout.Rect(actionStart, actionY, 96, BUTTON_HEIGHT);
        this.actionAssign = new UiLayout.Rect(actionStart + 104, actionY, 164, BUTTON_HEIGHT);
        this.actionRemove = new UiLayout.Rect(actionStart + 276, actionY, 140, BUTTON_HEIGHT);
        this.actionDelete = new UiLayout.Rect(actionStart + 424, actionY, 96, BUTTON_HEIGHT);
        this.actionSolo = new UiLayout.Rect(actionStart + 530, actionY, 96, BUTTON_HEIGHT);
        this.startButton = new UiLayout.Rect(mainPanel.x() + mainPanel.width() - 126, mainPanel.y() + 10, 112, BUTTON_HEIGHT);

        if (this.activeModule == Module.MATCH_RULES) {
            this.modeButton = this.addButton(screen, "Mode: " + titleCase(this.mode.nbtValue()), mainPanel.x() + 180, mainPanel.y() + 96, 190, () -> {
                this.mode = this.mode == ResourceSprintSettings.Mode.FIRST_TO_COMPLETE ? ResourceSprintSettings.Mode.TIME_LIMITED : ResourceSprintSettings.Mode.FIRST_TO_COMPLETE;
                this.modeButton.setMessage(Text.literal("Mode: " + titleCase(this.mode.nbtValue())));
            });
            this.timeLimitField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 128, Integer.toString(this.timeLimitSeconds), 160, "Time limit seconds");
            this.tieBreakButton = this.addButton(screen, "Tie-Break: " + titleCase(this.tieBreakRule.nbtValue()), mainPanel.x() + 180, mainPanel.y() + 160, 190, () -> {
                this.tieBreakRule = this.tieBreakRule == ResourceSprintSettings.TieBreakRule.SUDDEN_DEATH ? ResourceSprintSettings.TieBreakRule.FASTEST_TOTAL_TIME : ResourceSprintSettings.TieBreakRule.SUDDEN_DEATH;
                this.tieBreakButton.setMessage(Text.literal("Tie-Break: " + titleCase(this.tieBreakRule.nbtValue())));
            });
            this.distributionButton = this.addButton(screen, "Distribution: " + shortDistribution(this.distributionMode), mainPanel.x() + 180, mainPanel.y() + 192, 190, () -> {
                this.distributionMode = this.distributionMode.next();
                this.distributionButton.setMessage(Text.literal("Distribution: " + shortDistribution(this.distributionMode)));
            });
            this.addButton(screen, "Configure Objectives", mainPanel.x() + 180, mainPanel.y() + 224, 190, () -> {
                this.syncStateFromWidgets();
                RegistrySelectorContext<net.minecraft.item.Item> selectorContext = new RegistrySelectorContext<>(
                    "minecraft:item",
                    "Select Objectives",
                    RegistrySelectorContext.SelectionMode.MULTI,
                    new dev.frost.miniverse.client.gui.selector.RegistrySelectorState(),
                    result -> {
                        this.objectivesPool = result.selectedEntries().stream()
                            .map(net.minecraft.registry.Registries.ITEM::getId)
                            .map(net.minecraft.util.Identifier::toString)
                            .collect(Collectors.toSet());
                    },
                    "resourcesprint",
                    this.objectivesPool.stream()
                        .map(net.minecraft.util.Identifier::of)
                        .map(net.minecraft.registry.Registries.ITEM::get)
                        .collect(Collectors.toSet())
                );
                client.setScreen(new dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen<>(selectorContext, new dev.frost.miniverse.client.gui.selector.providers.ItemRegistryProvider()));
            });
        }

        if (this.teams.isEmpty()) {
            this.addTeam(this.nextTeamLabel());
            this.selectedTeamIndex = 0;
        }
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(mainPanel.x() + 1, mainPanel.y() + 1, mainPanel.x() + mainPanel.width() - 1, mainPanel.y() + 40, 0x701B2634);
        context.drawText(textRenderer, Text.literal(this.activeModule.label), mainPanel.x() + 14, mainPanel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(this.activeModule.description), mainPanel.x() + 14, mainPanel.y() + 28, UiTheme.TEXT_DIM, false);

        if (this.activeModule == Module.TEAMS) {
            this.renderTeamActions(context, textRenderer, mouseX, mouseY);
            this.renderTeams(context, textRenderer, this.teamsArea, mouseX, mouseY);
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
        int labelX = mainPanel.x() + 38;
        int labelY = mainPanel.y() + 102;
        if (this.activeModule == Module.MATCH_RULES) {
            context.drawText(textRenderer, Text.literal("Mode"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Time Limit"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Distribution"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Tie Break"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Objectives"), labelX, labelY + 128, UiTheme.TEXT_MUTED, false);
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
        if (this.actionCreate.contains(mouseX, mouseY)) {
            this.createTeam();
            return true;
        }
        if (this.actionAssign.contains(mouseX, mouseY)) {
            this.assignSelectedPlayersToTeam();
            return true;
        }
        if (this.actionRemove.contains(mouseX, mouseY)) {
            this.removeSelectedPlayersFromTeams();
            return true;
        }
        if (this.actionDelete.contains(mouseX, mouseY)) {
            this.deleteSelectedTeam();
            return true;
        }
        if (this.actionSolo.contains(mouseX, mouseY)) {
            this.soloAll();
            return true;
        }
        int rosterIndex = this.getRosterRowAt(mouseX, mouseY);
        if (rosterIndex >= 0) {
            SessionSnapshotData.RosterEntry entry = this.getAvailablePlayers().get(rosterIndex);
            this.togglePlayerSelection(entry.uuid());
            return true;
        }

        MemberHit memberHit = this.getTeamMemberHit(mouseX, mouseY);
        if (memberHit != null) {
            this.selectedTeamIndex = memberHit.teamIndex;
            this.togglePlayerSelection(memberHit.uuid);
            this.statusMessage = this.selectedPlayerUuids.contains(memberHit.uuid)
                ? "Selected " + memberHit.name + "."
                : "Deselected " + memberHit.name + ".";
            return true;
        }
        int teamIndex = this.getTeamRowAt(mouseX, mouseY);
        if (teamIndex >= 0) {
            this.selectedTeamIndex = teamIndex;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeModule != Module.TEAMS) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        UiLayout.Rect rosterRect = this.rosterRect();
        UiLayout.Rect teamRect = this.teamRect();
        if (rosterRect.contains(mouseX, mouseY)) {
            int max = Math.max(0, this.getAvailablePlayers().size() - this.visibleRows(rosterRect.height()));
            this.rosterScrollOffset = Math.clamp(this.rosterScrollOffset - delta, 0, max);
            return max > 0;
        }
        if (teamRect.contains(mouseX, mouseY)) {
            int max = Math.max(0, this.teams.size() - this.visibleTeamRows(teamRect.height()));
            this.teamScrollOffset = Math.clamp(this.teamScrollOffset - delta, 0, max);
            return max > 0;
        }
        return false;
    }

    @Override
    public String title() {
        return "Resource Sprint Setup";
    }

    @Override
    public String subtitle() {
        return "Workspace-based draft and rules";
    }

    @Override
    public String gameId() {
        return ResourceSprintDefinition.ID;
    }

    @Override
    public List<WorkspaceModule> modules() {
        List<WorkspaceModule> modules = new ArrayList<>();
        for (Module module : Module.values()) {
            String group = module == Module.SUMMARY ? "Summary" : module == Module.TEAMS ? "Setup" : "Rules";
            modules.add(new WorkspaceModule(module.id, module.icon, module.label, group));
        }
        return modules;
    }

    @Override
    public String activeModuleId() {
        return this.activeModule.id;
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        this.activeModule = Module.fromId(moduleId).orElse(this.activeModule);
    }

    @Override
    public void refreshRoster() {
        this.selectedPlayerUuids.retainAll(this.currentRosterUuids());
        for (TeamDraft team : this.teams) {
            team.members().forEach(member -> {
                if (!this.currentRosterUuids().contains(member.uuid().toString())) {
                    team.remove(member.uuid());
                }
            });
        }
        if (this.selectedTeamIndex >= this.teams.size()) {
            this.selectedTeamIndex = this.teams.isEmpty() ? -1 : this.teams.size() - 1;
        }
    }

    private void renderSettingsModule(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        int moduleX = mainPanel.x() + 14;
        int moduleY = mainPanel.y() + 72;
        int moduleWidth = Math.min(520, mainPanel.width() - 28);
        int moduleHeight = Math.min(210, mainPanel.height() - 104);
        UiRenderer.panel(context, moduleX, moduleY, moduleWidth, moduleHeight, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(moduleX, moduleY, moduleX + 3, moduleY + moduleHeight, this.activeModule.accent);
        context.drawText(textRenderer, Text.literal(this.activeModule.label), moduleX + 12, moduleY + 12, this.activeModule.accent, false);
    }


    private void renderSummary(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        this.syncStateFromWidgets();
        int x = mainPanel.x() + 14;
        int y = mainPanel.y() + 72;
        int width = Math.min(520, mainPanel.width() - 28);
        UiRenderer.panel(context, x, y, width, 170, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(x, y, x + 3, y + 170, UiTheme.ACCENT_BLUE);
        int line = y + 18;
        context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
        line += 20;
        context.drawText(textRenderer, Text.literal("Mode: " + titleCase(this.mode.nbtValue())), x + 14, line, UiTheme.TEXT_MUTED, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Time Limit: " + this.timeLimitSeconds + "s"), x + 14, line, UiTheme.TEXT_MUTED, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Distribution: " + shortDistribution(this.distributionMode)), x + 14, line, UiTheme.TEXT_MUTED, false);
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            context.drawText(textRenderer, Text.literal(validation), x + 14, y + 134, UiTheme.WARNING, false);
        }
    }

    private void renderTeamActions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        this.renderActionButton(context, textRenderer, this.actionCreate, "Create Team", UiTheme.ACCENT_BLUE, this.actionCreate.contains(mouseX, mouseY));
        this.renderActionButton(context, textRenderer, this.actionAssign, "Assign To Team", UiTheme.ACCENT_GREEN, this.actionAssign.contains(mouseX, mouseY));
        this.renderActionButton(context, textRenderer, this.actionRemove, "Remove From Team", UiTheme.ACCENT, this.actionRemove.contains(mouseX, mouseY));
        this.renderActionButton(context, textRenderer, this.actionDelete, "Delete Team", UiTheme.ACCENT_RED, this.actionDelete.contains(mouseX, mouseY));
        this.renderActionButton(context, textRenderer, this.actionSolo, "Solo All", UiTheme.ACCENT_GREEN, this.actionSolo.contains(mouseX, mouseY));
    }

    private void renderTeams(DrawContext context, TextRenderer textRenderer, UiLayout.Rect area, int mouseX, int mouseY) {
        UiLayout.Rect rosterRect = this.rosterRect();
        UiLayout.Rect teamRect = this.teamRect();
        this.renderRosterPanel(context, textRenderer, rosterRect, mouseX, mouseY);
        this.renderTeamPanel(context, textRenderer, teamRect, mouseX, mouseY);
    }

    private void renderRosterPanel(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, int mouseX, int mouseY) {
        List<SessionSnapshotData.RosterEntry> roster = this.getAvailablePlayers();
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.drawText(textRenderer, Text.literal("Available Players (" + roster.size() + ")"), rect.x() + 8, rect.y() + 7, UiTheme.TEXT, false);

        int visibleRows = this.visibleRows(rect.height());
        int rows = Math.min(visibleRows, roster.size() - this.rosterScrollOffset);
        int listTop = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        for (int row = 0; row < rows; row++) {
            SessionSnapshotData.RosterEntry entry = roster.get(this.rosterScrollOffset + row);
            int rowY = listTop + row * ROW_HEIGHT;
            boolean selected = this.selectedPlayerUuids.contains(entry.uuid());
            boolean hovered = mouseX >= rect.x() + 1 && mouseX <= rect.x() + rect.width() - 1 && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2;
            UiAnimation.Value hover = this.rowHovers.computeIfAbsent("roster:" + entry.uuid(), ignored -> new UiAnimation.Value(0.0F));
            hover.animateTo(hovered ? 1.0F : 0.0F, UiTheme.HOVER_MS);
            int background = selected ? UiAnimation.lerpColor(0xAA2F5D94, 0xCC3E79B8, hover.get()) : UiAnimation.lerpColor(0x26222A34, 0x66304052, hover.get());
            context.fill(rect.x() + 1, rowY, rect.x() + rect.width() - 1, rowY + ROW_HEIGHT - 2, background);
            context.drawText(textRenderer, Text.literal(entry.name()), rect.x() + 12, rowY + 6, UiTheme.TEXT, false);
        }
        this.drawScrollbar(context, rect, roster.size(), visibleRows, this.rosterScrollOffset);
    }

    private void renderTeamPanel(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, int mouseX, int mouseY) {
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.drawText(textRenderer, Text.literal("Teams (" + this.teams.size() + ")"), rect.x() + 8, rect.y() + 7, UiTheme.TEXT, false);

        int visibleRows = this.visibleTeamRows(rect.height());
        int rows = Math.min(visibleRows, this.teams.size() - this.teamScrollOffset);
        int listTop = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        for (int row = 0; row < rows; row++) {
            int teamIndex = this.teamScrollOffset + row;
            TeamDraft team = this.teams.get(teamIndex);
            int rowY = listTop + row * TEAM_ROW_HEIGHT;
            boolean selected = teamIndex == this.selectedTeamIndex;
            int accent = TEAM_COLORS[teamIndex % TEAM_COLORS.length];
            context.fill(rect.x() + 1, rowY, rect.x() + rect.width() - 1, rowY + TEAM_ROW_HEIGHT - 2, selected ? 0xAA405D8A : 0x33222222);
            context.fill(rect.x() + 6, rowY + 4, rect.x() + 10, rowY + TEAM_ROW_HEIGHT - 5, accent);
            String label = team.label().isBlank() ? this.fallbackTeamLabel(teamIndex) : team.label();
            int textX = rect.x() + 16;
            String labelTrim = textRenderer.trimToWidth(label, rect.width() - 28);
            context.drawText(textRenderer, Text.literal(labelTrim), textX, rowY + 5, UiTheme.TEXT, false);
            MemberLine members = this.buildMemberLine(team, rect.width() - 24, 6, textRenderer);
            if (!members.text.isBlank()) {
                context.drawText(textRenderer, Text.literal(members.text), textX, rowY + 19, UiTheme.TEXT_MUTED, false);
            }
        }
        this.drawScrollbar(context, rect, this.teams.size(), visibleRows, this.teamScrollOffset);
    }

    private void renderActionButton(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, String label, int accent, boolean hovered) {
        int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(accent, 0.34F), hovered ? 1.0F : 0.0F);
        int border = UiAnimation.lerpColor(UiTheme.BORDER_SUBTLE, accent, hovered ? 1.0F : 0.0F);
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), fill, border);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(label), rect.x() + rect.width() / 2, rect.y() + 7, UiTheme.TEXT);
    }

    private ButtonWidget addButton(SessionScreen screen, String label, int x, int y, int width, Runnable action) {
        return screen.addWorkspaceChild(ButtonWidget.builder(Text.literal(label), ignored -> action.run())
            .dimensions(x, y, width, BUTTON_HEIGHT)
            .build());
    }

    private TextFieldWidget addField(SessionScreen screen, int x, int y, String value, int width, String narration) {
        TextFieldWidget field = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, x, y, width, BUTTON_HEIGHT, Text.literal(narration));
        field.setMaxLength(128);
        field.setText(value);
        return screen.addWorkspaceChild(field);
    }

    private void createTeam() {
        TeamDraft team = this.addTeam(this.nextTeamLabel());
        this.selectedTeamIndex = this.teams.indexOf(team);
        this.statusMessage = "Created " + this.displayTeamName(team) + ".";
    }

    private TeamDraft addTeam(String label) {
        TeamDraft team = new TeamDraft(label);
        this.teams.add(team);
        return team;
    }

    private void assignSelectedPlayersToTeam() {
        TeamDraft team = this.getSelectedTeam();
        if (team == null) {
            this.statusMessage = "Select a team first.";
            return;
        }
        List<SessionSnapshotData.RosterEntry> selectedPlayers = this.getSelectedPlayers();
        if (selectedPlayers.isEmpty()) {
            this.statusMessage = "Select one or more players first.";
            return;
        }
        for (SessionSnapshotData.RosterEntry player : selectedPlayers) {
            this.removePlayerFromAllTeams(player.uuid());
            team.add(new TeamDraft.Member(UUID.fromString(player.uuid()), player.name()));
        }
        this.selectedPlayerUuids.clear();
        this.statusMessage = "Assigned " + selectedPlayers.size() + " player(s) to " + this.displayTeamName(team) + ".";
    }

    private void removeSelectedPlayersFromTeams() {
        List<SessionSnapshotData.RosterEntry> selectedPlayers = this.getSelectedPlayers();
        if (selectedPlayers.isEmpty()) {
            this.statusMessage = "Select one or more players first.";
            return;
        }
        int removed = 0;
        for (SessionSnapshotData.RosterEntry player : selectedPlayers) {
            removed += this.removePlayerFromAllTeams(player.uuid()) ? 1 : 0;
        }
        this.selectedPlayerUuids.clear();
        this.statusMessage = removed == 0 ? "Selected players are not assigned." : "Removed " + removed + " player(s) from teams.";
    }

    private void deleteSelectedTeam() {
        TeamDraft team = this.getSelectedTeam();
        if (team == null) {
            this.statusMessage = "Select a team first.";
            return;
        }
        String label = this.displayTeamName(team);
        this.teams.remove(this.selectedTeamIndex);
        if (this.teams.isEmpty()) {
            this.selectedTeamIndex = -1;
        } else if (this.selectedTeamIndex >= this.teams.size()) {
            this.selectedTeamIndex = this.teams.size() - 1;
        }
        this.statusMessage = "Deleted " + label + ".";
    }

    private void soloAll() {
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        if (roster.isEmpty()) {
            this.statusMessage = "No players to split into solo teams.";
            return;
        }
        this.teams.clear();
        this.nextTeamNumber = 1;
        for (SessionSnapshotData.RosterEntry entry : roster) {
            TeamDraft team = new TeamDraft(this.nextTeamLabel());
            team.add(new TeamDraft.Member(UUID.fromString(entry.uuid()), entry.name()));
            this.teams.add(team);
        }
        this.selectedPlayerUuids.clear();
        this.selectedTeamIndex = this.teams.isEmpty() ? -1 : 0;
        this.teamScrollOffset = 0;
        this.statusMessage = "Created one solo team per player.";
    }

    private void createSession() {
        this.syncStateFromWidgets();
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            this.statusMessage = validation;
            return;
        }
        NbtCompound plan = new NbtCompound();
        plan.putString("game", ResourceSprintDefinition.ID);
        plan.putString("name", this.sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.buildSettingsCompound());

        NbtList groups = new NbtList();
        int exportedTeams = 0;
        for (int i = 0; i < this.teams.size(); i++) {
            TeamDraft team = this.teams.get(i);
            if (team.isEmpty()) {
                continue;
            }
            groups.add(team.toPlanCompound(this.fallbackTeamLabel(i)));
            exportedTeams++;
        }
        if (exportedTeams == 0) {
            this.statusMessage = "Create at least one team with one player.";
            return;
        }
        plan.put("groups", groups);
        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(ResourceSprintDefinition.ID, this.sessionName, plan));
        this.statusMessage = "Requested Resource Sprint session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putString("mode", this.mode.nbtValue());
        settings.putInt("timeLimitSeconds", this.timeLimitSeconds);
        settings.putString("tieBreakRule", this.tieBreakRule.nbtValue());
        settings.putString("objectiveDistributionMode", this.distributionMode.nbtValue());

        List<ResourceSprintSettings.ObjectiveEntry> objectives = this.parseObjectives();
        NbtList objectiveList = new NbtList();
        for (ResourceSprintSettings.ObjectiveEntry entry : objectives) {
            NbtCompound objective = new NbtCompound();
            objective.putString("id", entry.id());
            objective.putString("difficulty", entry.difficulty().nbtValue());
            objective.putDouble("probability", entry.probability());
            objectiveList.add(objective);
        }
        settings.put("objectives", objectiveList);
        settings.putInt("objectiveCount", objectives.size());
        return settings;
    }

    private void syncStateFromWidgets() {
        if (this.timeLimitField != null) {
            this.timeLimitSeconds = readInt(this.timeLimitField, this.timeLimitSeconds, 60, 7200);
        }
    }

    private int readInt(TextFieldWidget field, int fallback, int min, int max) {
        if (field == null) {
            return fallback;
        }
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
        try {
            int value = Math.clamp(Integer.parseInt(text), min, max);
            field.setText(Integer.toString(value));
            return value;
        } catch (NumberFormatException ignored) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
    }

    private String getStartValidationMessage() {
        if (this.client.player == null) {
            return "Not connected to a server.";
        }
        if (this.sessionName.isBlank()) {
            return "Enter a session name.";
        }
        if (SessionSnapshotData.roster().isEmpty()) {
            return "No players online.";
        }
        return "";
    }

    private List<String> objectiveIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceSprintSettings.ObjectiveEntry entry : this.parseObjectives()) {
            ids.add(entry.id());
        }
        return ids;
    }

    private List<ResourceSprintSettings.ObjectiveEntry> parseObjectives() {
        if (this.objectivesPool == null || this.objectivesPool.isEmpty()) {
            return new ArrayList<>(ResourceSprintSettings.defaults().objectives());
        }
        List<ResourceSprintSettings.ObjectiveEntry> entries = new ArrayList<>();
        for (String id : this.objectivesPool) {
            if (!id.isBlank()) {
                entries.add(new ResourceSprintSettings.ObjectiveEntry(id, ResourceSprintSettings.ObjectiveDifficulty.EASY, 1.0));
            }
        }
        return entries.isEmpty() ? new ArrayList<>(ResourceSprintSettings.defaults().objectives()) : entries;
    }

    private List<SessionSnapshotData.RosterEntry> getAvailablePlayers() {
        List<SessionSnapshotData.RosterEntry> available = new ArrayList<>();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            if (!this.isAssigned(entry.uuid())) {
                available.add(entry);
            }
        }
        return available;
    }

    private List<SessionSnapshotData.RosterEntry> getSelectedPlayers() {
        List<SessionSnapshotData.RosterEntry> selected = new ArrayList<>();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            if (this.selectedPlayerUuids.contains(entry.uuid())) {
                selected.add(entry);
            }
        }
        return selected;
    }

    private boolean removePlayerFromAllTeams(String uuid) {
        boolean removed = false;
        UUID playerUuid = UUID.fromString(uuid);
        for (TeamDraft team : this.teams) {
            removed |= team.remove(playerUuid);
        }
        return removed;
    }

    private TeamDraft getSelectedTeam() {
        if (this.selectedTeamIndex < 0 || this.selectedTeamIndex >= this.teams.size()) {
            return null;
        }
        return this.teams.get(this.selectedTeamIndex);
    }

    private String displayTeamName(TeamDraft team) {
        String label = team.label();
        if (!label.isBlank()) {
            return label;
        }
        int index = this.teams.indexOf(team);
        return this.fallbackTeamLabel(index >= 0 ? index : 0);
    }

    private MemberLine buildMemberLine(TeamDraft team, int maxWidth, int maxNames, TextRenderer textRenderer) {
        int memberCount = team.members().size();
        if (memberCount == 0) {
            return new MemberLine("No players", List.of());
        }
        List<MemberSpan> spans = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int width = 0;
        int shown = 0;
        for (TeamDraft.Member member : team.members()) {
            if (shown >= maxNames) {
                break;
            }
            String prefix = builder.isEmpty() ? "" : ", ";
            int prefixWidth = textRenderer.getWidth(prefix);
            int nameWidth = textRenderer.getWidth(member.name());
            if (width + prefixWidth + nameWidth > maxWidth) {
                break;
            }
            builder.append(prefix).append(member.name());
            int startX = width + prefixWidth;
            spans.add(new MemberSpan(startX, startX + nameWidth, member.uuid().toString(), member.name()));
            width += prefixWidth + nameWidth;
            shown++;
        }
        int remaining = memberCount - shown;
        if (shown == 0) {
            String summary = remaining == 0 ? "No players" : "+" + remaining;
            return new MemberLine(summary, List.of());
        }
        if (remaining > 0) {
            String suffix = " +" + remaining;
            if (width + textRenderer.getWidth(suffix) <= maxWidth) {
                builder.append(suffix);
            }
        }
        return new MemberLine(builder.toString(), spans);
    }

    private String fallbackTeamLabel(int index) {
        return "Team " + (index + 1);
    }

    private String nextTeamLabel() {
        return "Team " + this.nextTeamNumber++;
    }

    private boolean isAssigned(String uuid) {
        for (TeamDraft team : this.teams) {
            if (team.contains(UUID.fromString(uuid))) {
                return true;
            }
        }
        return false;
    }

    private void togglePlayerSelection(String uuid) {
        if (this.selectedPlayerUuids.contains(uuid)) {
            this.selectedPlayerUuids.remove(uuid);
        } else {
            this.selectedPlayerUuids.add(uuid);
        }
    }

    private MemberHit getTeamMemberHit(double mouseX, double mouseY) {
        UiLayout.Rect rect = this.teamRect();
        if (!rect.contains(mouseX, mouseY)) {
            return null;
        }
        int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
        int teamIndex = this.teamScrollOffset + row;
        if (row < 0 || teamIndex < 0 || teamIndex >= this.teams.size()) {
            return null;
        }
        TextRenderer textRenderer = this.client.textRenderer;
        int rowY = rowStartY + row * TEAM_ROW_HEIGHT;
        int textY = rowY + 19;
        if (mouseY < textY || mouseY > textY + textRenderer.fontHeight) {
            return null;
        }
        TeamDraft team = this.teams.get(teamIndex);
        MemberLine line = this.buildMemberLine(team, rect.width() - 24, 6, textRenderer);
        if (line.spans.isEmpty()) {
            return null;
        }
        int textX = rect.x() + 16;
        double relativeX = mouseX - textX;
        if (relativeX < 0 || relativeX > textRenderer.getWidth(line.text)) {
            return null;
        }
        for (MemberSpan span : line.spans) {
            if (relativeX >= span.startX && relativeX <= span.endX) {
                return new MemberHit(span.uuid, span.name, teamIndex);
            }
        }
        return null;
    }

    private UiLayout.Rect rosterRect() {
        int columnWidth = (this.teamsArea.width() - COLUMN_GAP) / 2;
        return new UiLayout.Rect(this.teamsArea.x(), this.teamsArea.y(), columnWidth, this.teamsArea.height());
    }

    private UiLayout.Rect teamRect() {
        int columnWidth = (this.teamsArea.width() - COLUMN_GAP) / 2;
        return new UiLayout.Rect(this.teamsArea.x() + columnWidth + COLUMN_GAP, this.teamsArea.y(), columnWidth, this.teamsArea.height());
    }

    private int visibleRows(int height) {
        return Math.max(0, (height - COLUMN_HEADER_HEIGHT - 8) / ROW_HEIGHT);
    }

    private int visibleTeamRows(int height) {
        return Math.max(0, (height - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);
    }

    private int getRosterRowAt(double mouseX, double mouseY) {
        UiLayout.Rect rect = this.rosterRect();
        if (!rect.contains(mouseX, mouseY)) {
            return -1;
        }
        int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int row = (int) ((mouseY - rowStartY) / ROW_HEIGHT);
        int index = this.rosterScrollOffset + row;
        if (row < 0 || index < 0 || index >= this.getAvailablePlayers().size()) {
            return -1;
        }
        return index;
    }

    private int getTeamRowAt(double mouseX, double mouseY) {
        UiLayout.Rect rect = this.teamRect();
        if (!rect.contains(mouseX, mouseY)) {
            return -1;
        }
        int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
        int index = this.teamScrollOffset + row;
        if (row < 0 || index < 0 || index >= this.teams.size()) {
            return -1;
        }
        return index;
    }

    private void drawScrollbar(DrawContext context, UiLayout.Rect rect, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows || visibleRows <= 0) {
            return;
        }
        int trackX = rect.x() + rect.width() - 7;
        int trackY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int trackHeight = rect.height() - COLUMN_HEADER_HEIGHT - 8;
        int thumbHeight = Math.max(14, (int) ((trackHeight * (double) visibleRows) / totalRows));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbY = trackY + (int) (((trackHeight - thumbHeight) * (double) scrollOffset) / maxScroll);
        context.fill(trackX, trackY, trackX + 3, trackY + trackHeight, 0x66101822);
        context.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, UiTheme.BORDER_STRONG);
    }

    private static final class MemberLine {
        private final String text;
        private final List<MemberSpan> spans;

        private MemberLine(String text, List<MemberSpan> spans) {
            this.text = text;
            this.spans = spans;
        }
    }

    private static final class MemberSpan {
        private final int startX;
        private final int endX;
        private final String uuid;
        private final String name;

        private MemberSpan(int startX, int endX, String uuid, String name) {
            this.startX = startX;
            this.endX = endX;
            this.uuid = uuid;
            this.name = name;
        }
    }

    private static final class MemberHit {
        private final String uuid;
        private final String name;
        private final int teamIndex;

        private MemberHit(String uuid, String name, int teamIndex) {
            this.uuid = uuid;
            this.name = name;
            this.teamIndex = teamIndex;
        }
    }

    private Set<String> currentRosterUuids() {
        Set<String> rosterUuids = new LinkedHashSet<>();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            rosterUuids.add(entry.uuid());
        }
        return rosterUuids;
    }

    private static String shortDistribution(ResourceSprintSettings.ObjectiveDistributionMode mode) {
        return mode == ResourceSprintSettings.ObjectiveDistributionMode.SHARED ? "Shared" : "Probabilistic";
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.split("[_\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private enum Module {
        TEAMS("teams", "T", "Teams", "Draft and assign teams.", UiTheme.ACCENT),
        MATCH_RULES("rules", "R", "Match Rules", "Configure scoring and win rules.", UiTheme.ACCENT_BLUE),
        SUMMARY("summary", "U", "Summary", "Review and launch the match.", UiTheme.ACCENT_BLUE);

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
}



