package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.speedrun.SpeedrunDefinition;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SpeedrunSetupScreen extends Screen {
    private static final String GAME_ID = SpeedrunDefinition.ID;
    private static final int PANEL_PADDING = 16;
    private static final int PANEL_MAX_WIDTH = 1040;
    private static final int PANEL_MAX_HEIGHT = 680;
    private static final int TITLE_TOP_Y = 12;
    private static final int SUBTITLE_TOP_Y = 28;
    private static final int SESSION_ROW_Y = 48;
    private static final int LIST_TOP_Y = 92;
    private static final int COLUMN_HEADER_HEIGHT = 18;
    private static final int PLAYER_ROW_HEIGHT = 20;
    private static final int TEAM_ROW_HEIGHT = 44;
    private static final int TEAM_GAP = 12;
    private static final int ACTION_BUTTON_HEIGHT = 20;
    private static final int FOOTER_BUTTON_HEIGHT = 20;
    private static final int TOP_BUTTON_HEIGHT = 20;
    private static final int SETTINGS_HEIGHT = 118;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SESSION_LABEL_WIDTH = 110;
    private static final int SETTINGS_FIELD_MAX_WIDTH = 260;
    private static final int SOLO_ALL_BUTTON_WIDTH = 88;
    private static final int START_MATCH_BUTTON_WIDTH = 108;
    private static final int CREATE_TEAM_BUTTON_WIDTH = 94;
    private static final int ASSIGN_BUTTON_WIDTH = 174;
    private static final int REMOVE_BUTTON_WIDTH = 126;
    private static final int DELETE_BUTTON_WIDTH = 92;
    private static final int SAVE_PRESET_BUTTON_WIDTH = 98;
    private static final int RESET_BUTTON_WIDTH = 72;
    private static final int BACK_BUTTON_WIDTH = 68;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_SECONDARY = 0xFFD8D8D8;
    private static final int TEXT_MUTED = 0xFFB8B8B8;
    private static final int TEXT_STATUS = 0xFFA0FFA0;

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
    private final List<TeamDraft> teams = new ArrayList<>();
    private final Set<String> selectedPlayerUuids = new LinkedHashSet<>();
    private TextFieldWidget sessionNameField;
    private TextFieldWidget fixedSeedField;
    private ButtonWidget seedModeButton;
    private ButtonWidget startButton;
    private ButtonWidget createTeamButton;
    private ButtonWidget assignToSelectedTeamButton;
    private ButtonWidget removeFromTeamButton;
    private ButtonWidget deleteTeamButton;
    private ButtonWidget soloAllButton;
    private ButtonWidget savePresetButton;
    private ButtonWidget resetButton;
    private ButtonWidget backButton;
    private int selectedTeamIndex = -1;
    private int playerScrollOffset = 0;
    private int teamScrollOffset = 0;
    private int nextTeamNumber = 1;
    private SeedMode seedMode = SeedMode.RANDOM;
    private String statusMessage = "";

    public SpeedrunSetupScreen() {
        super(Text.literal("Speedrun Setup"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();
        this.teams.clear();
        this.selectedPlayerUuids.clear();
        this.selectedTeamIndex = -1;
        this.playerScrollOffset = 0;
        this.teamScrollOffset = 0;
        this.nextTeamNumber = 1;
        this.seedMode = SeedMode.RANDOM;

        this.addTeam(this.nextTeamLabel());
        this.selectedTeamIndex = 0;

        Layout layout = this.createLayout();

        this.sessionNameField = new TextFieldWidget(this.textRenderer, layout.sessionFieldX, layout.sessionFieldY, layout.sessionFieldWidth, 20, Text.literal("session name"));
        this.sessionNameField.setMaxLength(48);
        this.sessionNameField.setText("speedrun-" + System.currentTimeMillis());
        this.addDrawableChild(this.sessionNameField);

        this.soloAllButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("👤 Solo All"), button -> this.soloAll())
            .dimensions(layout.soloAllX, layout.topButtonsY, SOLO_ALL_BUTTON_WIDTH, TOP_BUTTON_HEIGHT)
            .build());

        this.startButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start Match"), button -> this.createSession())
            .dimensions(layout.startMatchX, layout.topButtonsY, START_MATCH_BUTTON_WIDTH, TOP_BUTTON_HEIGHT)
            .build());

        this.createTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("➕ Create Team"), button -> this.createTeam())
            .dimensions(layout.createTeamX, layout.actionButtonsY, CREATE_TEAM_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT)
            .build());

        this.assignToSelectedTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("→ Assign To Selected Team"), button -> this.assignSelectedPlayersToTeam())
            .dimensions(layout.assignTeamX, layout.actionButtonsY, ASSIGN_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT)
            .build());

        this.removeFromTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("↩ Remove From Team"), button -> this.removeSelectedPlayersFromTeams())
            .dimensions(layout.removeTeamX, layout.actionButtonsY, REMOVE_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT)
            .build());

        this.deleteTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("✕ Delete Team"), button -> this.deleteSelectedTeam())
            .dimensions(layout.deleteTeamX, layout.actionButtonsY, DELETE_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT)
            .build());

        this.seedModeButton = this.addDrawableChild(ButtonWidget.builder(this.seedModeButtonLabel(), button -> {
            this.toggleSeedMode();
            button.setMessage(this.seedModeButtonLabel());
        }).dimensions(this.createSettingsRowLayout(layout).fieldX, layout.settingsY + 20, this.createSettingsRowLayout(layout).fieldWidth, 20).build());

        this.fixedSeedField = new TextFieldWidget(this.textRenderer, this.createSettingsRowLayout(layout).fieldX, layout.settingsY + 48, this.createSettingsRowLayout(layout).fieldWidth, 20, Text.literal("fixed seed"));
        this.fixedSeedField.setMaxLength(36);
        this.fixedSeedField.setText(Long.toString(System.currentTimeMillis()));
        this.addDrawableChild(this.fixedSeedField);

        this.savePresetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Preset"), button -> this.savePreset())
            .dimensions(layout.footerSaveX, layout.footerButtonsY, SAVE_PRESET_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT)
            .build());

        this.resetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> this.resetDraft())
            .dimensions(layout.footerResetX, layout.footerButtonsY, RESET_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT)
            .build());

        this.backButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(new SessionScreen()))
            .dimensions(layout.footerBackX, layout.footerButtonsY, BACK_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT)
            .build());

        this.requestSnapshot();
    }

    @Override
    public void tick() {
        super.tick();

        Layout layout = this.createLayout();
        this.syncState(layout);
        this.updateButtonLabels();

        boolean connected = this.client.player != null;
        boolean hasRosterSelection = !this.selectedPlayerUuids.isEmpty();
        boolean hasTeamSelection = this.getSelectedTeam() != null;
        boolean hasPlayers = !SessionSnapshotData.roster().isEmpty();

        this.sessionNameField.active = connected;
        this.seedModeButton.active = connected;
        this.fixedSeedField.active = connected && this.seedMode == SeedMode.FIXED;
        this.startButton.active = connected;
        this.createTeamButton.active = connected;
        this.assignToSelectedTeamButton.active = connected && hasTeamSelection && hasRosterSelection;
        this.removeFromTeamButton.active = connected && hasRosterSelection;
        this.deleteTeamButton.active = connected && hasTeamSelection;
        this.soloAllButton.active = connected && hasPlayers;
        this.savePresetButton.active = connected;
        this.resetButton.active = connected;
        this.backButton.active = true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout layout = this.createLayout();

        int selectedTeamRow = this.getTeamRowAt(mouseX, mouseY, layout);
        if (selectedTeamRow >= 0) {
            this.selectedTeamIndex = selectedTeamRow;
            return true;
        }

        int selectedPlayerRow = this.getRosterRowAt(mouseX, mouseY, layout);
        if (selectedPlayerRow >= 0) {
            SessionSnapshotData.RosterEntry entry = SessionSnapshotData.roster().get(selectedPlayerRow);
            this.togglePlayerSelection(entry.uuid());
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = this.createLayout();
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (this.isWithin(mouseX, mouseY, layout.teamX, layout.listsY, layout.teamWidth, layout.listsHeight)) {
            int maxScroll = this.getMaxTeamScroll(layout);
            if (maxScroll > 0) {
                this.teamScrollOffset = clamp(this.teamScrollOffset - delta, 0, maxScroll);
                return true;
            }
        }

        if (this.isWithin(mouseX, mouseY, layout.availableX, layout.listsY, layout.availableWidth, layout.listsHeight)) {
            int maxScroll = this.getMaxRosterScroll(layout);
            if (maxScroll > 0) {
                this.playerScrollOffset = clamp(this.playerScrollOffset - delta, 0, maxScroll);
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Layout layout = this.createLayout();

        this.drawPanelShell(context, layout);
        this.drawTopPanelBackground(context, layout);
        this.drawListPanels(context, layout);
        this.drawActionStrip(context, layout);
        this.drawSettingsPanel(context, layout);

        super.render(context, mouseX, mouseY, delta);

        int centerX = layout.panelX + layout.panelWidth / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, layout.panelY + TITLE_TOP_Y, TEXT_WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Build dynamic teams, assign players, and launch a clean Speedrun match."), centerX, layout.panelY + SUBTITLE_TOP_Y, TEXT_MUTED);

        context.drawText(this.textRenderer, Text.literal("Session Name:"), layout.contentX, layout.sessionFieldY + 6, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Click players to select one or more."), layout.availableX + 8, layout.listsY - 10, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Select a team to target assignments."), layout.teamX + 8, layout.listsY - 10, TEXT_SECONDARY, false);

        this.drawSettingsLabels(context, layout);

        if (!this.statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), centerX, layout.footerButtonsY + 28, TEXT_STATUS);
        }
    }

    private void drawPanelShell(DrawContext context, Layout layout) {
        int x = layout.panelX;
        int y = layout.panelY;
        int width = layout.panelWidth;
        int height = layout.panelHeight;

        context.fill(x, y, x + width, y + height, 0xD0121212);
        context.fill(x + 1, y + 1, x + width - 1, y + 33, 0xCC202020);
        context.fill(x + 1, y + 34, x + width - 1, y + height - 1, 0xAA181818);
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0x66FFFFFF);
        context.fill(x, y, x + 1, y + height, 0x66FFFFFF);
        context.fill(x + width - 1, y, x + width, y + height, 0x66FFFFFF);
    }

    private void drawTopPanelBackground(DrawContext context, Layout layout) {
        int x = layout.panelX + PANEL_PADDING;
        int y = layout.sessionFieldY - 6;
        int width = layout.panelWidth - (PANEL_PADDING * 2);
        int height = 36;

        context.fill(x, y, x + width, y + height, 0x14000000);
    }

    private void drawListPanels(DrawContext context, Layout layout) {
        this.drawRosterPanel(context, layout);
        this.drawTeamPanel(context, layout);
    }

    private void drawRosterPanel(DrawContext context, Layout layout) {
        int x = layout.availableX;
        int y = layout.listsY;
        int width = layout.availableWidth;
        int height = layout.listsHeight;
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        int visibleRows = this.getRosterVisibleRows(layout);
        int rowsToDraw = Math.min(Math.max(0, roster.size() - this.playerScrollOffset), visibleRows);

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
        context.drawText(this.textRenderer, Text.literal("Available Players (" + roster.size() + ")"), x + 8, y + 5, TEXT_WHITE, false);

        if (roster.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No players online."), x + width / 2, y + height / 2 - 4, TEXT_MUTED);
            return;
        }

        for (int row = 0; row < rowsToDraw; row++) {
            int rosterIndex = this.playerScrollOffset + row;
            SessionSnapshotData.RosterEntry entry = roster.get(rosterIndex);
            int rowY = y + COLUMN_HEADER_HEIGHT + 2 + row * PLAYER_ROW_HEIGHT;
            boolean selected = this.selectedPlayerUuids.contains(entry.uuid());
            int background = selected ? 0xAA3A5C8F : 0x33222222;
            context.fill(x + 1, rowY, x + width - 1, rowY + PLAYER_ROW_HEIGHT - 1, background);
            context.fill(x + 4, rowY + 3, x + 8, rowY + PLAYER_ROW_HEIGHT - 4, 0xFF5B8CFF);

            String teamLabel = this.findTeamForPlayer(entry.uuid());
            String rowText = teamLabel.isBlank() ? entry.name() : entry.name() + "  [" + teamLabel + "]";
            context.drawText(this.textRenderer, Text.literal(this.elide(rowText, width - 26)), x + 14, rowY + 5, TEXT_SECONDARY, false);
        }

        this.drawScrollBar(context, x, y, width, height, roster.size(), visibleRows, this.playerScrollOffset);
    }

    private void drawTeamPanel(DrawContext context, Layout layout) {
        int x = layout.teamX;
        int y = layout.listsY;
        int width = layout.teamWidth;
        int height = layout.listsHeight;
        int visibleRows = this.getTeamVisibleRows(layout);
        int rowsToDraw = Math.min(Math.max(0, this.teams.size() - this.teamScrollOffset), visibleRows);

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
        context.drawText(this.textRenderer, Text.literal("Teams (" + this.teams.size() + ")"), x + 8, y + 5, TEXT_WHITE, false);

        if (this.teams.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Create a team to begin."), x + width / 2, y + height / 2 - 4, TEXT_MUTED);
            return;
        }

        for (int row = 0; row < rowsToDraw; row++) {
            int teamIndex = this.teamScrollOffset + row;
            TeamDraft team = this.teams.get(teamIndex);
            int rowY = y + COLUMN_HEADER_HEIGHT + 2 + row * TEAM_ROW_HEIGHT;
            boolean selected = teamIndex == this.selectedTeamIndex;
            int accentColor = TEAM_COLORS[teamIndex % TEAM_COLORS.length];
            int background = selected ? 0xAA405D8A : 0x33222222;

            context.fill(x + 1, rowY, x + width - 1, rowY + TEAM_ROW_HEIGHT - 1, background);
            context.fill(x + 4, rowY + 3, x + 10, rowY + TEAM_ROW_HEIGHT - 4, accentColor);
            String countText = team.size() + " player" + (team.size() == 1 ? "" : "s");
            context.drawText(this.textRenderer, Text.literal(team.label().isBlank() ? this.fallbackTeamLabel(teamIndex) : team.label()), x + 14, rowY + 5, TEXT_WHITE, false);
            context.drawText(this.textRenderer, Text.literal(countText), x + width - 8 - this.textRenderer.getWidth(countText), rowY + 5, TEXT_MUTED, false);

            String membersText = team.isEmpty() ? "No members yet" : this.joinMemberNames(team);
            context.drawText(this.textRenderer, Text.literal(this.elide(membersText, width - 28)), x + 14, rowY + 20, TEXT_SECONDARY, false);
        }

        this.drawScrollBar(context, x, y, width, height, this.teams.size(), visibleRows, this.teamScrollOffset);
    }

    private void drawActionStrip(DrawContext context, Layout layout) {
        int x = layout.panelX + PANEL_PADDING;
        int y = layout.actionButtonsY - 6;
        int width = layout.panelWidth - (PANEL_PADDING * 2);
        int height = ACTION_BUTTON_HEIGHT + 16;

        context.fill(x, y, x + width, y + height, 0x14000000);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0x44FFFFFF);
    }

    private void drawSettingsPanel(DrawContext context, Layout layout) {
        int x = layout.panelX + PANEL_PADDING;
        int y = layout.settingsY;
        int width = layout.settingsWidth;
        int height = layout.footerButtonsY - y - 8;

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
    }

    private void drawSettingsLabels(DrawContext context, Layout layout) {
        SettingsRowLayout rowLayout = this.createSettingsRowLayout(layout);
        int x = layout.panelX + PANEL_PADDING;
        int y = layout.settingsY;
        int rowHeight = 28;
        int row0Y = y + 20;
        int row1Y = row0Y + rowHeight;

        context.drawText(this.textRenderer, Text.literal("Speedrun Settings"), x + 8, y + 5, TEXT_WHITE, false);
        context.drawText(this.textRenderer, Text.literal("Seed Mode:"), rowLayout.labelX, row0Y + 5, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Fixed Seed:"), rowLayout.labelX, row1Y + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Teams in the same group spawn together in the speedrun world."), x + 10, y + layout.footerButtonsY - y - 28, TEXT_MUTED, false);
    }

    private void drawScrollBar(DrawContext context, int x, int y, int width, int height, int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= visibleRows || visibleRows <= 0) {
            return;
        }

        int trackX = x + width - 5;
        int trackY = y + COLUMN_HEADER_HEIGHT + 2;
        int trackHeight = height - COLUMN_HEADER_HEIGHT - 4;
        int thumbHeight = Math.max(12, (int) ((trackHeight * (double) visibleRows) / totalRows));
        int maxScroll = Math.max(1, totalRows - visibleRows);
        int thumbY = trackY + (int) (((trackHeight - thumbHeight) * (double) scrollOffset) / maxScroll);

        context.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0x55000000);
        context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xCCB0B0B0);
    }

    private void createTeam() {
        TeamDraft team = this.addTeam(this.nextTeamLabel());
        this.selectedTeamIndex = this.teams.indexOf(team);
        this.statusMessage = "Created " + team.label() + ".";
    }

    private TeamDraft addTeam(String label) {
        TeamDraft team = new TeamDraft(label);
        this.teams.add(team);
        return team;
    }

    private String nextTeamLabel() {
        return "Team " + this.nextTeamNumber++;
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
        this.statusMessage = "Assigned " + selectedPlayers.size() + " player" + (selectedPlayers.size() == 1 ? "" : "s") + " to " + this.displayTeamName(team) + ".";
    }

    private void removeSelectedPlayersFromTeams() {
        List<SessionSnapshotData.RosterEntry> selectedPlayers = this.getSelectedPlayers();
        if (selectedPlayers.isEmpty()) {
            this.statusMessage = "Select one or more players first.";
            return;
        }

        int removedCount = 0;
        for (SessionSnapshotData.RosterEntry player : selectedPlayers) {
            removedCount += this.removePlayerFromAllTeams(player.uuid()) ? 1 : 0;
        }

        this.selectedPlayerUuids.clear();
        this.statusMessage = removedCount == 0 ? "Selected players are not assigned to any team." : "Removed " + removedCount + " player" + (removedCount == 1 ? "" : "s") + " from teams.";
    }

    private void deleteSelectedTeam() {
        TeamDraft team = this.getSelectedTeam();
        if (team == null) {
            this.statusMessage = "Select a team first.";
            return;
        }

        String label = this.displayTeamName(team);
        int index = this.selectedTeamIndex;
        this.teams.remove(index);
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

    private void savePreset() {
        this.statusMessage = "Preset saving is coming soon.";
    }

    private void resetDraft() {
        this.seedMode = SeedMode.RANDOM;
        this.fixedSeedField.setText(Long.toString(System.currentTimeMillis()));
        this.teams.clear();
        this.selectedPlayerUuids.clear();
        this.selectedTeamIndex = -1;
        this.playerScrollOffset = 0;
        this.teamScrollOffset = 0;
        this.nextTeamNumber = 1;
        this.addTeam(this.nextTeamLabel());
        this.selectedTeamIndex = 0;
        this.statusMessage = "Speedrun draft reset.";
    }

    private void createSession() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        String sessionName = this.sessionNameField.getText().trim();
        if (sessionName.isEmpty()) {
            this.statusMessage = "Enter a session name.";
            return;
        }

        NbtCompound plan = new NbtCompound();
        plan.putString("game", GAME_ID);
        plan.putString("name", sessionName);
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
        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(GAME_ID, sessionName, plan));
        this.statusMessage = "Requested Speedrun session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putString("seedMode", this.seedMode.nbtValue);
        if (this.seedMode == SeedMode.FIXED) {
            settings.putLong("seed", this.getFixedSeedValue());
        }
        return settings;
    }

    private long getFixedSeedValue() {
        String text = this.fixedSeedField.getText().trim();
        if (text.isEmpty()) {
            throw new NumberFormatException("empty seed");
        }
        return Long.parseLong(text);
    }

    private void requestSnapshot() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("refresh"));
        this.statusMessage = "Requested current roster.";
    }

    private TeamDraft getSelectedTeam() {
        if (this.selectedTeamIndex < 0 || this.selectedTeamIndex >= this.teams.size()) {
            return null;
        }
        return this.teams.get(this.selectedTeamIndex);
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

    private void togglePlayerSelection(String uuid) {
        if (this.selectedPlayerUuids.contains(uuid)) {
            this.selectedPlayerUuids.remove(uuid);
        } else {
            this.selectedPlayerUuids.add(uuid);
        }
    }

    private void toggleSeedMode() {
        this.seedMode = this.seedMode.next();
        this.statusMessage = this.seedMode == SeedMode.FIXED ? "Fixed seed mode enabled." : "Random seed mode enabled.";
    }

    private void updateButtonLabels() {
        if (this.seedModeButton != null) {
            this.seedModeButton.setMessage(this.seedModeButtonLabel());
        }
    }

    private Text seedModeButtonLabel() {
        return Text.literal("Seed Mode: " + this.seedMode.displayName);
    }

    private String displayTeamName(TeamDraft team) {
        String label = team.label();
        if (!label.isBlank()) {
            return label;
        }
        int index = this.teams.indexOf(team);
        return this.fallbackTeamLabel(index >= 0 ? index : 0);
    }

    private String fallbackTeamLabel(int index) {
        return "Team " + (index + 1);
    }

    private String findTeamForPlayer(String uuid) {
        UUID playerUuid = UUID.fromString(uuid);
        for (TeamDraft team : this.teams) {
            if (team.contains(playerUuid)) {
                return this.displayTeamName(team);
            }
        }
        return "";
    }

    private String joinMemberNames(TeamDraft team) {
        StringBuilder builder = new StringBuilder();
        for (TeamDraft.Member member : team.members()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(member.name());
        }
        return builder.toString();
    }

    private int getRosterVisibleRows(Layout layout) {
        return Math.max(0, (layout.listsHeight - COLUMN_HEADER_HEIGHT - 4) / PLAYER_ROW_HEIGHT);
    }

    private int getTeamVisibleRows(Layout layout) {
        return Math.max(0, (layout.listsHeight - COLUMN_HEADER_HEIGHT - 4) / TEAM_ROW_HEIGHT);
    }

    private int getMaxRosterScroll(Layout layout) {
        return Math.max(0, SessionSnapshotData.roster().size() - this.getRosterVisibleRows(layout));
    }

    private int getMaxTeamScroll(Layout layout) {
        return Math.max(0, this.teams.size() - this.getTeamVisibleRows(layout));
    }

    private int getRosterRowAt(double mouseX, double mouseY, Layout layout) {
        if (!this.isWithin(mouseX, mouseY, layout.availableX, layout.listsY, layout.availableWidth, layout.listsHeight)) {
            return -1;
        }

        int rowStartY = layout.listsY + COLUMN_HEADER_HEIGHT + 2;
        int visibleRows = this.getRosterVisibleRows(layout);
        int row = (int) ((mouseY - rowStartY) / PLAYER_ROW_HEIGHT);
        int index = this.playerScrollOffset + row;
        if (row < 0 || row >= visibleRows || index < 0 || index >= SessionSnapshotData.roster().size()) {
            return -1;
        }
        return index;
    }

    private int getTeamRowAt(double mouseX, double mouseY, Layout layout) {
        if (!this.isWithin(mouseX, mouseY, layout.teamX, layout.listsY, layout.teamWidth, layout.listsHeight)) {
            return -1;
        }

        int rowStartY = layout.listsY + COLUMN_HEADER_HEIGHT + 2;
        int visibleRows = this.getTeamVisibleRows(layout);
        int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
        int index = this.teamScrollOffset + row;
        if (row < 0 || row >= visibleRows || index < 0 || index >= this.teams.size()) {
            return -1;
        }
        return index;
    }

    private void syncState(Layout layout) {
        Set<String> rosterUuids = new LinkedHashSet<>();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            rosterUuids.add(entry.uuid());
        }

        this.selectedPlayerUuids.retainAll(rosterUuids);
        if (this.selectedTeamIndex >= this.teams.size()) {
            this.selectedTeamIndex = this.teams.isEmpty() ? -1 : this.teams.size() - 1;
        }

        this.playerScrollOffset = clamp(this.playerScrollOffset, 0, this.getMaxRosterScroll(layout));
        this.teamScrollOffset = clamp(this.teamScrollOffset, 0, this.getMaxTeamScroll(layout));

        if (this.selectedTeamIndex < 0 && !this.teams.isEmpty()) {
            this.selectedTeamIndex = 0;
        }
    }

    private SettingsRowLayout createSettingsRowLayout(Layout layout) {
        int labelWidth = this.calculateMaxSettingLabelWidth();
        int labelX = layout.panelX + PANEL_PADDING + 10;
        int fieldX = labelX + labelWidth + 8;
        int panelRight = layout.panelX + PANEL_PADDING + layout.settingsWidth;
        int fieldWidth = Math.min(SETTINGS_FIELD_MAX_WIDTH, Math.max(180, panelRight - fieldX - 10));
        return new SettingsRowLayout(labelX, fieldX, fieldWidth);
    }

    private int calculateMaxSettingLabelWidth() {
        int seedModeWidth = this.textRenderer.getWidth("Seed Mode:");
        int fixedSeedWidth = this.textRenderer.getWidth("Fixed Seed:");
        return Math.max(seedModeWidth, fixedSeedWidth) + 4;
    }

    private Layout createLayout() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 24);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 24);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = Math.max(12, (this.height - panelHeight) / 2);

        int contentX = panelX + PANEL_PADDING;
        int contentWidth = panelWidth - (PANEL_PADDING * 2);

        int sessionFieldX = contentX + SESSION_LABEL_WIDTH;
        int topButtonsY = panelY + SESSION_ROW_Y;
        int startMatchX = panelX + panelWidth - PANEL_PADDING - START_MATCH_BUTTON_WIDTH;
        int soloAllX = startMatchX - 8 - SOLO_ALL_BUTTON_WIDTH;
        int sessionFieldWidth = Math.max(220, soloAllX - 12 - sessionFieldX);

        int footerButtonsY = panelY + panelHeight - FOOTER_HEIGHT;
        int footerStartWidth = SAVE_PRESET_BUTTON_WIDTH;
        int footerResetWidth = RESET_BUTTON_WIDTH;
        int footerBackWidth = BACK_BUTTON_WIDTH;
        int footerGap = 8;
        int footerTotalWidth = footerStartWidth + footerResetWidth + footerBackWidth + (footerGap * 2);
        int footerLeft = panelX + (panelWidth - footerTotalWidth) / 2;

        int settingsY = footerButtonsY - SETTINGS_HEIGHT - 10;
        int actionButtonsY = settingsY - 40;
        int listTop = panelY + LIST_TOP_Y;
        int listBottom = actionButtonsY - 10;
        int listsHeight = Math.max(190, listBottom - listTop);

        int listGap = TEAM_GAP;
        int availableWidth = Math.max(320, (contentWidth - listGap) / 2);
        int teamWidth = contentWidth - availableWidth - listGap;
        if (teamWidth < 320) {
            teamWidth = Math.max(320, contentWidth - availableWidth - listGap);
        }
        int availableX = contentX;
        int teamX = availableX + availableWidth + listGap;

        int actionTotalWidth = CREATE_TEAM_BUTTON_WIDTH + ASSIGN_BUTTON_WIDTH + REMOVE_BUTTON_WIDTH + DELETE_BUTTON_WIDTH + (8 * 3);
        int actionLeft = panelX + (panelWidth - actionTotalWidth) / 2;

        return new Layout(
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            contentX,
            contentWidth,
            sessionFieldX,
            panelY + SESSION_ROW_Y,
            sessionFieldWidth,
            topButtonsY,
            soloAllX,
            startMatchX,
            availableX,
            teamX,
            listTop,
            listsHeight,
            availableWidth,
            teamWidth,
            actionButtonsY,
            actionLeft,
            actionLeft + CREATE_TEAM_BUTTON_WIDTH + 8,
            actionLeft + CREATE_TEAM_BUTTON_WIDTH + ASSIGN_BUTTON_WIDTH + (8 * 2),
            actionLeft + CREATE_TEAM_BUTTON_WIDTH + ASSIGN_BUTTON_WIDTH + REMOVE_BUTTON_WIDTH + (8 * 3),
            settingsY,
            contentWidth,
            footerButtonsY,
            footerLeft,
            footerLeft + footerStartWidth + 8,
            footerLeft + footerStartWidth + footerResetWidth + 16,
            footerStartWidth,
            footerResetWidth,
            footerBackWidth
        );
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String elide(String text, int width) {
        if (text == null || text.isEmpty() || width <= 0) {
            return "";
        }
        if (this.textRenderer.getWidth(text) <= width) {
            return text;
        }

        String ellipsis = "…";
        int end = text.length();
        while (end > 0 && this.textRenderer.getWidth(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return end <= 0 ? ellipsis : text.substring(0, end) + ellipsis;
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

        private SeedMode next() {
            return this == RANDOM ? FIXED : RANDOM;
        }
    }

    private record Layout(
        int panelX,
        int panelY,
        int panelWidth,
        int panelHeight,
        int contentX,
        int contentWidth,
        int sessionFieldX,
        int sessionFieldY,
        int sessionFieldWidth,
        int topButtonsY,
        int soloAllX,
        int startMatchX,
        int availableX,
        int teamX,
        int listsY,
        int listsHeight,
        int availableWidth,
        int teamWidth,
        int actionButtonsY,
        int createTeamX,
        int assignTeamX,
        int removeTeamX,
        int deleteTeamX,
        int settingsY,
        int settingsWidth,
        int footerButtonsY,
        int footerSaveX,
        int footerResetX,
        int footerBackX,
        int footerStartWidth,
        int footerResetWidth,
        int footerBackWidth
    ) {
    }

    private record SettingsRowLayout(int labelX, int fieldX, int fieldWidth) {
    }
}
