package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintDefinition;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class ResourceSprintSetupScreen extends Screen {
    private static final String GAME_ID = ResourceSprintDefinition.ID;
    private static final double[] PROBABILITY_OPTIONS = {1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1};
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
    private static final int SETTINGS_HEIGHT = 176;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SESSION_LABEL_WIDTH = 110;
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
    private static final Path DRAFT_PATH = FabricLoader.getInstance().getConfigDir().resolve("miniverse-resource-sprint-draft.properties");

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
    private final List<ResourceSprintSettings.ObjectiveEntry> objectives = new ArrayList<>();
    private TextFieldWidget sessionNameField;
    private TextFieldWidget timeLimitField;
    private ButtonWidget winButton;
    private ButtonWidget distributionModeButton;
    private ButtonWidget tieBreakButton;
    private ButtonWidget addObjectiveButton;
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
    private int objectiveScrollOffset = 0;
    private int selectedObjectiveIndex = -1;
    private int nextTeamNumber = 1;
    private SprintMode mode = SprintMode.FIRST_TO_COMPLETE;
    private ResourceSprintSettings.ObjectiveDistributionMode objectiveDistributionMode = ResourceSprintSettings.ObjectiveDistributionMode.SHARED;
    private TieBreakRule tieBreakRule = TieBreakRule.SUDDEN_DEATH;
    private String timeLimitText = "3600";
    private boolean draftLoaded = false;
    private String lastDraftSignature = "";
    private String statusMessage = "";

    public ResourceSprintSetupScreen() {
        super(Text.literal("Resource Sprint Setup"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();

        if (!this.draftLoaded) {
            this.loadDraft();
            this.draftLoaded = true;
        }

        if (this.objectives.isEmpty()) {
            this.objectives.addAll(this.defaultObjectives());
        }

        if (this.teams.isEmpty()) {
            this.addTeam(this.nextTeamLabel());
        }

        this.selectedTeamIndex = this.teams.isEmpty() ? -1 : clamp(this.selectedTeamIndex, 0, this.teams.size() - 1);
        this.playerScrollOffset = clamp(this.playerScrollOffset, 0, this.getMaxRosterScroll(this.createLayout()));
        this.teamScrollOffset = clamp(this.teamScrollOffset, 0, this.getMaxTeamScroll(this.createLayout()));
        this.objectiveScrollOffset = clamp(this.objectiveScrollOffset, 0, this.getMaxObjectiveScroll(this.createLayout()));
        this.selectedObjectiveIndex = this.objectives.isEmpty() ? -1 : clamp(this.selectedObjectiveIndex, -1, this.objectives.size() - 1);
        this.nextTeamNumber = Math.max(this.nextTeamNumber, this.teams.size() + 1);

        Layout layout = this.createLayout();

        this.sessionNameField = new TextFieldWidget(this.textRenderer, layout.sessionFieldX(), layout.sessionFieldY(), layout.sessionFieldWidth(), 20, Text.literal("session name"));
        this.sessionNameField.setMaxLength(48);
        this.sessionNameField.setText("resource-sprint-" + System.currentTimeMillis());
        this.addDrawableChild(this.sessionNameField);

        this.soloAllButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("👤 Solo All"), button -> this.soloAll())
            .dimensions(layout.soloAllX(), layout.topButtonsY(), SOLO_ALL_BUTTON_WIDTH, TOP_BUTTON_HEIGHT)
            .build());

        this.startButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start Match"), button -> this.createSession())
            .dimensions(layout.startMatchX(), layout.topButtonsY(), START_MATCH_BUTTON_WIDTH, TOP_BUTTON_HEIGHT)
            .build());

        this.createTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("➕ Create Team"), button -> this.createTeam())
            .dimensions(layout.createTeamX(), layout.actionButtonsY(), CREATE_TEAM_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT)
            .build());

        this.assignToSelectedTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("→ Assign To Selected Team"), button -> this.assignSelectedPlayersToTeam())
            .dimensions(layout.assignTeamX(), layout.actionButtonsY(), ASSIGN_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT)
            .build());

        this.removeFromTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("↩ Remove From Team"), button -> this.removeSelectedPlayersFromTeams())
            .dimensions(layout.removeTeamX(), layout.actionButtonsY(), REMOVE_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT)
            .build());

        this.deleteTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("✕ Delete Team"), button -> this.deleteSelectedTeam())
            .dimensions(layout.deleteTeamX(), layout.actionButtonsY(), DELETE_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT)
            .build());

        this.timeLimitField = new TextFieldWidget(this.textRenderer, layout.settingsFieldX(), layout.settingsY() + 49, 120, 20, Text.literal("time limit"));
        this.timeLimitField.setMaxLength(8);
        this.timeLimitField.setText(this.timeLimitText);
        this.addDrawableChild(this.timeLimitField);

        this.winButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.mode.label()), button -> {
            this.mode = this.mode.next();
            button.setMessage(Text.literal(this.mode.label()));
        }).dimensions(layout.winButtonX(), layout.settingsY() + 49, 140, 20).build());

        this.distributionModeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.distributionModeButtonLabel()), button -> {
            this.objectiveDistributionMode = this.objectiveDistributionMode.next();
            button.setMessage(Text.literal(this.distributionModeButtonLabel()));
        }).dimensions(layout.distributionModeButtonX(), layout.settingsY() + 49, 160, 20).build());

        this.tieBreakButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.tieBreakRule.label()), button -> {
            this.tieBreakRule = this.tieBreakRule.next();
            button.setMessage(Text.literal(this.tieBreakRule.label()));
        }).dimensions(layout.tieBreakButtonX(), layout.settingsY() + 49, 150, 20).build());

        this.addObjectiveButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("➕ Add Objective"), button -> this.openObjectivePicker())
            .dimensions(layout.objectiveButtonX(), layout.settingsY() + 24, 120, 20)
            .build());

        this.savePresetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Save Preset"), button -> this.savePreset())
            .dimensions(layout.footerSaveX(), layout.footerButtonsY(), SAVE_PRESET_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT)
            .build());

        this.resetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> this.resetDraft())
            .dimensions(layout.footerResetX(), layout.footerButtonsY(), RESET_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT)
            .build());

        this.backButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(new SessionScreen()))
            .dimensions(layout.footerBackX(), layout.footerButtonsY(), BACK_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT)
            .build());

        this.requestSnapshot();
        this.lastDraftSignature = this.buildDraftSignature();
        this.saveDraft();
    }

    @Override
    public void tick() {
        super.tick();

        Layout layout = this.createLayout();
        this.syncState(layout);
        this.updateButtonLabels();

        String signature = this.buildDraftSignature();
        if (!signature.equals(this.lastDraftSignature)) {
            this.saveDraft();
        }

        boolean connected = this.client.player != null;
        boolean hasRosterSelection = !this.selectedPlayerUuids.isEmpty();
        boolean hasTeamSelection = this.getSelectedTeam() != null;
        boolean hasPlayers = !SessionSnapshotData.roster().isEmpty();
        this.sessionNameField.active = connected;
        this.timeLimitField.active = connected && this.mode == SprintMode.TIME_LIMITED;
        this.winButton.active = connected;
        this.distributionModeButton.active = connected;
        this.tieBreakButton.active = connected;
        this.addObjectiveButton.active = connected;
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
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        Layout layout = this.createLayout();

        int selectedObjectiveRow = this.getObjectiveRowAt(mouseX, mouseY, layout);
        if (selectedObjectiveRow >= 0) {
            this.handleObjectiveClick(selectedObjectiveRow, mouseX, mouseY, layout);
            return true;
        }

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

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        Layout layout = this.createLayout();
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (this.isWithin(mouseX, mouseY, layout.availableX(), layout.listsY(), layout.availableWidth(), layout.listsHeight())) {
            int maxScroll = this.getMaxRosterScroll(layout);
            if (maxScroll > 0) {
                this.playerScrollOffset = clamp(this.playerScrollOffset - delta, 0, maxScroll);
                return true;
            }
        }

        if (this.isWithin(mouseX, mouseY, layout.objectiveListX(), layout.objectiveListY(), layout.objectiveListWidth(), layout.objectiveListHeight())) {
            int maxScroll = this.getMaxObjectiveScroll(layout);
            if (maxScroll > 0) {
                this.objectiveScrollOffset = clamp(this.objectiveScrollOffset - delta, 0, maxScroll);
                return true;
            }
        }

        if (this.isWithin(mouseX, mouseY, layout.teamX(), layout.listsY(), layout.teamWidth(), layout.listsHeight())) {
            int maxScroll = this.getMaxTeamScroll(layout);
            if (maxScroll > 0) {
                this.teamScrollOffset = clamp(this.teamScrollOffset - delta, 0, maxScroll);
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

        int centerX = layout.panelX() + layout.panelWidth() / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, layout.panelY() + TITLE_TOP_Y, TEXT_WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Build teams on the right, select players on the left, and launch a shared objective chain."), centerX, layout.panelY() + SUBTITLE_TOP_Y, TEXT_MUTED);

        context.drawText(this.textRenderer, Text.literal("Session Name:"), layout.contentX(), layout.sessionFieldY() + 6, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Click players to select one or more."), layout.availableX() + 8, layout.listsY() - 10, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Select a team to target assignments."), layout.teamX() + 8, layout.listsY() - 10, TEXT_SECONDARY, false);

        this.drawSettingsLabels(context, layout);

        if (!this.statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), centerX, layout.footerButtonsY() + FOOTER_BUTTON_HEIGHT + 10, TEXT_STATUS);
        }
    }

    private void drawPanelShell(DrawContext context, Layout layout) {
        int x = layout.panelX();
        int y = layout.panelY();
        int width = layout.panelWidth();
        int height = layout.panelHeight();

        context.fill(x, y, x + width, y + height, 0xD0121212);
        context.fill(x + 1, y + 1, x + width - 1, y + 32, 0xCC1F1F1F);
        context.fill(x + 1, y + 33, x + width - 1, y + height - 1, 0xAA181818);
        context.fill(x, y, x + width, y + 1, 0x66FFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0x66FFFFFF);
        context.fill(x, y, x + 1, y + height, 0x66FFFFFF);
        context.fill(x + width - 1, y, x + width, y + height, 0x66FFFFFF);
    }

    private void drawTopPanelBackground(DrawContext context, Layout layout) {
        context.fill(layout.panelX() + PANEL_PADDING, layout.panelY() + 38, layout.panelX() + layout.panelWidth() - PANEL_PADDING, layout.panelY() + 84, 0x14000000);
    }

    private void drawListPanels(DrawContext context, Layout layout) {
        this.drawRosterPanel(context, layout);
        this.drawTeamPanel(context, layout);
    }

    private void drawRosterPanel(DrawContext context, Layout layout) {
        int x = layout.availableX();
        int y = layout.listsY();
        int width = layout.availableWidth();
        int height = layout.listsHeight();
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
        int x = layout.teamX();
        int y = layout.listsY();
        int width = layout.teamWidth();
        int height = layout.listsHeight();
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
        int x = layout.panelX() + PANEL_PADDING;
        int y = layout.actionButtonsY() - 6;
        int width = layout.panelWidth() - (PANEL_PADDING * 2);
        int height = ACTION_BUTTON_HEIGHT + 16;

        context.fill(x, y, x + width, y + height, 0x14000000);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0x44FFFFFF);
    }

    private void drawSettingsPanel(DrawContext context, Layout layout) {
        int x = layout.panelX() + PANEL_PADDING;
        int y = layout.settingsY();
        int width = layout.settingsWidth();
        int height = layout.footerButtonsY() - y - 8;

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
        context.drawText(this.textRenderer, Text.literal("Resource Sprint Settings"), x + 8, y + 5, TEXT_WHITE, false);

        int objectiveListY = y + 78;
        context.fill(x + 6, objectiveListY, x + width - 6, y + height - 8, 0x22141414);
        this.drawObjectiveList(context, layout);
    }

    private void drawSettingsLabels(DrawContext context, Layout layout) {
        context.drawText(this.textRenderer, Text.literal("Objectives:"), layout.settingsLabelX(), layout.settingsY() + 26, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Add items or blocks from the picker, then set difficulty and probability before launching."), layout.settingsLabelX(), layout.settingsY() + 40, TEXT_MUTED, false);
        context.drawText(this.textRenderer, Text.literal("Time Limit (Seconds):"), layout.settingsLabelX(), layout.settingsY() + 59, TEXT_SECONDARY, false);

        int winLabelX = layout.winButtonX() - 8 - this.textRenderer.getWidth("Win:");
        int distributionLabelX = layout.distributionModeButtonX() - 8 - this.textRenderer.getWidth("Mode:");
        int tieBreakLabelX = layout.tieBreakButtonX() - 8 - this.textRenderer.getWidth("Tie-Break:");
        context.drawText(this.textRenderer, Text.literal("Win:"), winLabelX, layout.settingsY() + 59, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Mode:"), distributionLabelX, layout.settingsY() + 59, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Tie-Break:"), tieBreakLabelX, layout.settingsY() + 59, TEXT_SECONDARY, false);
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

    private void drawObjectiveList(DrawContext context, Layout layout) {
        int x = layout.objectiveListX();
        int y = layout.objectiveListY();
        int width = layout.objectiveListWidth();
        int height = layout.objectiveListHeight();
        int visibleRows = this.getObjectiveVisibleRows(layout);
        int rowsToDraw = Math.min(Math.max(0, this.objectives.size() - this.objectiveScrollOffset), visibleRows);

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
        context.drawText(this.textRenderer, Text.literal("Objectives (" + this.objectives.size() + ")"), x + 8, y + 5, TEXT_WHITE, false);

        if (this.objectives.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Click Add Objective to select an objective."), x + width / 2, y + height / 2 - 4, TEXT_MUTED);
            return;
        }

        for (int row = 0; row < rowsToDraw; row++) {
            int objectiveIndex = this.objectiveScrollOffset + row;
            ResourceSprintSettings.ObjectiveEntry entry = this.objectives.get(objectiveIndex);
            int rowY = y + COLUMN_HEADER_HEIGHT + 2 + row * 24;
            boolean highlighted = objectiveIndex == this.getSelectedObjectiveIndex();
            int background = highlighted ? 0xAA3A5C8F : 0x33222222;

            context.fill(x + 1, rowY, x + width - 1, rowY + 23, background);
            ItemStack stack = this.resolveObjectiveStack(entry.id());
            context.drawItem(stack, x + 6, rowY + 3);

            String label = this.displayObjectiveName(entry);
            String difficultyText = entry.difficulty().name().toLowerCase(Locale.ROOT);
            String probabilityText = this.formatProbability(entry.probability());
            int removeX = x + width - 15;
            int probabilityTextX = x + width - 10 - this.textRenderer.getWidth(probabilityText) - 20;
            int difficultyTextX = probabilityTextX - 10 - this.textRenderer.getWidth(difficultyText) - 6;
            context.drawText(this.textRenderer, Text.literal(this.elide(label, Math.max(0, difficultyTextX - (x + 28) - 8))), x + 28, rowY + 4, TEXT_SECONDARY, false);
            context.drawText(this.textRenderer, Text.literal(difficultyText), difficultyTextX, rowY + 4, this.difficultyColor(entry.difficulty()), false);
            context.drawText(this.textRenderer, Text.literal(probabilityText), probabilityTextX, rowY + 4, TEXT_STATUS, false);
            context.drawText(this.textRenderer, Text.literal("✕"), removeX, rowY + 4, 0xFFFF8080, false);
        }

        this.drawScrollBar(context, x, y, width, height, this.objectives.size(), visibleRows, this.objectiveScrollOffset);
    }

    public void openObjectivePicker() {
        this.client.setScreen(new dev.frost.miniverse.client.gui.ObjectiveBlockPickerScreen(this));
    }

    public void addObjective(ResourceSprintSettings.ObjectiveEntry entry) {
        if (entry == null || entry.id() == null || entry.id().isBlank()) {
            return;
        }

        ResourceSprintSettings.ObjectiveEntry normalized = new ResourceSprintSettings.ObjectiveEntry(
            entry.id(),
            entry.difficulty() == null ? ResourceSprintSettings.ObjectiveDifficulty.EASY : entry.difficulty(),
            this.normalizeProbability(entry.probability(), entry.difficulty())
        );
        this.objectives.add(normalized);
        this.objectiveScrollOffset = Math.max(0, this.objectives.size() - 1);
        this.selectedObjectiveIndex = this.objectives.size() - 1;
        this.statusMessage = "Added " + this.displayObjectiveName(normalized) + " [" + normalized.difficulty().name().toLowerCase(Locale.ROOT) + ", " + this.formatProbability(normalized.probability()) + "].";
    }

    private void handleObjectiveClick(int objectiveIndex, double mouseX, double mouseY, Layout layout) {
        this.selectedObjectiveIndex = objectiveIndex;

        int x = layout.objectiveListX();
        int y = layout.objectiveListY();
        int width = layout.objectiveListWidth();
        int rowY = y + COLUMN_HEADER_HEIGHT + 2 + (objectiveIndex - this.objectiveScrollOffset) * 24;
        int removeX = x + width - 20;
        ResourceSprintSettings.ObjectiveEntry entry = this.objectives.get(objectiveIndex);
        String probabilityText = this.formatProbability(entry.probability());
        int probabilityTextX = x + width - 10 - this.textRenderer.getWidth(probabilityText) - 20;
        int difficultyTextX = probabilityTextX - 10 - this.textRenderer.getWidth(entry.difficulty().name().toLowerCase(Locale.ROOT)) - 6;

        if (mouseX >= removeX && mouseY >= rowY && mouseY <= rowY + 23) {
            this.removeObjective(objectiveIndex);
            return;
        }

        if (mouseX >= probabilityTextX && mouseX < removeX && mouseY >= rowY && mouseY <= rowY + 23) {
            this.cycleObjectiveProbability(objectiveIndex);
            return;
        }

        if (mouseX >= difficultyTextX && mouseX < probabilityTextX && mouseY >= rowY && mouseY <= rowY + 23) {
            this.cycleObjectiveDifficulty(objectiveIndex);
            return;
        }

        this.cycleObjectiveDifficulty(objectiveIndex);
    }

    private void cycleObjectiveDifficulty(int index) {
        if (index < 0 || index >= this.objectives.size()) {
            return;
        }

        ResourceSprintSettings.ObjectiveEntry current = this.objectives.get(index);
        ResourceSprintSettings.ObjectiveDifficulty next = switch (current.difficulty()) {
            case EASY -> ResourceSprintSettings.ObjectiveDifficulty.MEDIUM;
            case MEDIUM -> ResourceSprintSettings.ObjectiveDifficulty.HARD;
            case HARD -> ResourceSprintSettings.ObjectiveDifficulty.EASY;
        };
        this.objectives.set(index, new ResourceSprintSettings.ObjectiveEntry(current.id(), next, this.normalizeProbability(current.probability(), next)));
        this.statusMessage = "Set " + this.displayObjectiveName(this.objectives.get(index)) + " to " + next.name().toLowerCase(Locale.ROOT) + ".";
    }

    private void cycleObjectiveProbability(int index) {
        if (index < 0 || index >= this.objectives.size()) {
            return;
        }

        ResourceSprintSettings.ObjectiveEntry current = this.objectives.get(index);
        double next = this.nextProbability(current.probability());
        this.objectives.set(index, new ResourceSprintSettings.ObjectiveEntry(current.id(), current.difficulty(), next));
        this.statusMessage = "Set " + this.displayObjectiveName(this.objectives.get(index)) + " probability to " + this.formatProbability(next) + ".";
    }

    private void removeObjective(int index) {
        if (index < 0 || index >= this.objectives.size()) {
            return;
        }

        ResourceSprintSettings.ObjectiveEntry removed = this.objectives.remove(index);
        if (this.selectedObjectiveIndex >= this.objectives.size()) {
            this.selectedObjectiveIndex = this.objectives.isEmpty() ? -1 : this.objectives.size() - 1;
        }
        this.objectiveScrollOffset = clamp(this.objectiveScrollOffset, 0, this.getMaxObjectiveScroll(this.createLayout()));
        this.statusMessage = "Removed " + this.displayObjectiveName(removed) + ".";
    }

    private int getSelectedObjectiveIndex() {
        return this.selectedObjectiveIndex;
    }

    private int getObjectiveVisibleRows(Layout layout) {
        return Math.max(0, (layout.objectiveListHeight() - COLUMN_HEADER_HEIGHT - 4) / 24);
    }

    private int getMaxObjectiveScroll(Layout layout) {
        return Math.max(0, this.objectives.size() - this.getObjectiveVisibleRows(layout));
    }

    private int getObjectiveRowAt(double mouseX, double mouseY, Layout layout) {
        if (!this.isWithin(mouseX, mouseY, layout.objectiveListX(), layout.objectiveListY(), layout.objectiveListWidth(), layout.objectiveListHeight())) {
            return -1;
        }

        int rowStartY = layout.objectiveListY() + COLUMN_HEADER_HEIGHT + 2;
        int visibleRows = this.getObjectiveVisibleRows(layout);
        int row = (int) ((mouseY - rowStartY) / 24);
        int index = this.objectiveScrollOffset + row;
        if (row < 0 || row >= visibleRows || index < 0 || index >= this.objectives.size()) {
            return -1;
        }
        return index;
    }

    private List<ResourceSprintSettings.ObjectiveEntry> defaultObjectives() {
        return new ArrayList<>(ResourceSprintSettings.defaults().objectives());
    }

    private String displayObjectiveName(ResourceSprintSettings.ObjectiveEntry entry) {
        Item item = this.resolveObjectiveItem(entry.id());
        if (item != null) {
            return item.getName().getString();
        }

        return entry.id();
    }

    private ItemStack resolveObjectiveStack(String id) {
        Item item = this.resolveObjectiveItem(id);
        return item == null ? ItemStack.EMPTY : item.getDefaultStack();
    }

    private Item resolveObjectiveItem(String id) {
        try {
            Identifier identifier = Identifier.of(id);
            Item item = Registries.ITEM.get(identifier);
            return item == Items.AIR ? null : item;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private int difficultyColor(ResourceSprintSettings.ObjectiveDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 0xFF6BCB77;
            case MEDIUM -> 0xFFF7D154;
            case HARD -> 0xFFFF6E91;
        };
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
        for (SessionSnapshotData.RosterEntry entry : roster) {
            TeamDraft team = new TeamDraft(entry.name());
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
        this.mode = SprintMode.FIRST_TO_COMPLETE;
        this.objectiveDistributionMode = ResourceSprintSettings.ObjectiveDistributionMode.SHARED;
        this.tieBreakRule = TieBreakRule.SUDDEN_DEATH;
        this.objectives.clear();
        this.objectives.addAll(this.defaultObjectives());
        this.timeLimitText = "3600";
        if (this.timeLimitField != null) {
            this.timeLimitField.setText(this.timeLimitText);
        }
        this.teams.clear();
        this.selectedPlayerUuids.clear();
        this.selectedTeamIndex = -1;
        this.playerScrollOffset = 0;
        this.teamScrollOffset = 0;
        this.objectiveScrollOffset = 0;
        this.selectedObjectiveIndex = -1;
        this.nextTeamNumber = 1;
        this.addTeam(this.nextTeamLabel());
        this.selectedTeamIndex = 0;
        this.statusMessage = "Resource Sprint draft reset.";
        this.saveDraft();
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

        if (this.objectives.isEmpty()) {
            this.statusMessage = "Add at least one objective item.";
            return;
        }

        int exportedTeams = 0;
        for (TeamDraft team : this.teams) {
            if (!team.isEmpty()) {
                exportedTeams++;
            }
        }

        if (exportedTeams == 0) {
            this.statusMessage = "Create at least one team with one player.";
            return;
        }

        int timeLimitSeconds;
        try {
            timeLimitSeconds = Integer.parseInt(this.timeLimitField.getText().trim());
        } catch (NumberFormatException ignored) {
            this.statusMessage = "Enter a valid time limit in seconds.";
            return;
        }

        if (timeLimitSeconds <= 0) {
            this.statusMessage = "Time limit must be greater than zero.";
            return;
        }

        ResourceSprintSettings settingsModel = new ResourceSprintSettings(
            ResourceSprintSettings.Mode.valueOf(this.mode.name()),
            timeLimitSeconds,
            ResourceSprintSettings.TieBreakRule.valueOf(this.tieBreakRule.name()),
            this.objectiveDistributionMode,
            List.copyOf(this.objectives)
        );
        NbtCompound settings = settingsModel.toNbt();

        NbtCompound plan = new NbtCompound();
        plan.putString("game", GAME_ID);
        plan.putString("name", sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", settings);

        NbtList groups = new NbtList();
        for (int i = 0; i < this.teams.size(); i++) {
            TeamDraft team = this.teams.get(i);
            if (team.isEmpty()) {
                continue;
            }
            groups.add(team.toPlanCompound(this.fallbackTeamLabel(i)));
        }
        plan.put("groups", groups);

        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(GAME_ID, sessionName, plan));
        this.statusMessage = "Requested Resource Sprint session creation.";
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

    private void updateButtonLabels() {
        if (this.winButton != null) {
            this.winButton.setMessage(Text.literal(this.mode.label()));
        }
        if (this.distributionModeButton != null) {
            this.distributionModeButton.setMessage(Text.literal(this.distributionModeButtonLabel()));
        }
        if (this.tieBreakButton != null) {
            this.tieBreakButton.setMessage(Text.literal(this.tieBreakRule.label()));
        }
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
        return Math.max(0, (layout.listsHeight() - COLUMN_HEADER_HEIGHT - 4) / PLAYER_ROW_HEIGHT);
    }

    private int getTeamVisibleRows(Layout layout) {
        return Math.max(0, (layout.listsHeight() - COLUMN_HEADER_HEIGHT - 4) / TEAM_ROW_HEIGHT);
    }

    private int getMaxRosterScroll(Layout layout) {
        return Math.max(0, SessionSnapshotData.roster().size() - this.getRosterVisibleRows(layout));
    }

    private int getMaxTeamScroll(Layout layout) {
        return Math.max(0, this.teams.size() - this.getTeamVisibleRows(layout));
    }

    private int getRosterRowAt(double mouseX, double mouseY, Layout layout) {
        if (!this.isWithin(mouseX, mouseY, layout.availableX(), layout.listsY(), layout.availableWidth(), layout.listsHeight())) {
            return -1;
        }

        int rowStartY = layout.listsY() + COLUMN_HEADER_HEIGHT + 2;
        int visibleRows = this.getRosterVisibleRows(layout);
        int row = (int) ((mouseY - rowStartY) / PLAYER_ROW_HEIGHT);
        int index = this.playerScrollOffset + row;
        if (row < 0 || row >= visibleRows || index < 0 || index >= SessionSnapshotData.roster().size()) {
            return -1;
        }
        return index;
    }

    private int getTeamRowAt(double mouseX, double mouseY, Layout layout) {
        if (!this.isWithin(mouseX, mouseY, layout.teamX(), layout.listsY(), layout.teamWidth(), layout.listsHeight())) {
            return -1;
        }

        int rowStartY = layout.listsY() + COLUMN_HEADER_HEIGHT + 2;
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

        int settingsLabelX = contentX + 4;
        int settingsFieldWidth = 120;
        int settingsFieldXBase = settingsLabelX + this.textRenderer.getWidth("Time Limit (Seconds):") + 8;
        int winButtonX = settingsFieldXBase + settingsFieldWidth + 24 + this.textRenderer.getWidth("Win:") + 8;
        int distributionModeButtonX = winButtonX + 140 + 24 + this.textRenderer.getWidth("Mode:") + 8;
        int tieBreakButtonX = distributionModeButtonX + 160 + 24 + this.textRenderer.getWidth("Tie-Break:") + 8;
        int objectiveButtonX = contentX + contentWidth - 120;
        int objectiveListX = contentX + 6;
        int objectiveListY = settingsY + 78;
        int objectiveListWidth = contentWidth - 12;
        int objectiveListHeight = Math.max(72, footerButtonsY - objectiveListY - 10);

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
            footerBackWidth,
            settingsLabelX,
            settingsFieldXBase,
            settingsFieldWidth,
            winButtonX,
            distributionModeButtonX,
            tieBreakButtonX,
            objectiveButtonX,
            objectiveListX,
            objectiveListY,
            objectiveListWidth,
            objectiveListHeight
        );
    }

    private void requestSnapshot() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("refresh"));
        this.statusMessage = "Requested current roster.";
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private void loadDraft() {
        if (!Files.exists(DRAFT_PATH)) {
            return;
        }

        Properties properties = new Properties();
        try (var input = Files.newBufferedReader(DRAFT_PATH)) {
            properties.load(input);
        } catch (IOException ignored) {
            return;
        }

        this.mode = parseMode(properties.getProperty("resourcesprint.mode"), this.mode);
        this.objectiveDistributionMode = parseDistributionMode(properties.getProperty("resourcesprint.objectiveDistributionMode"), this.objectiveDistributionMode);
        this.tieBreakRule = parseTieBreakRule(properties.getProperty("resourcesprint.tieBreakRule"), this.tieBreakRule);
        this.timeLimitText = properties.getProperty("resourcesprint.timeLimitText", properties.getProperty("resourcesprint.timeLimitSeconds", this.timeLimitText));
        this.objectives.clear();
        this.objectives.addAll(readObjectives(properties));
    }

    private void saveDraft() {
        Properties properties = new Properties();
        properties.setProperty("resourcesprint.mode", this.mode.name());
        properties.setProperty("resourcesprint.objectiveDistributionMode", this.objectiveDistributionMode.name());
        properties.setProperty("resourcesprint.tieBreakRule", this.tieBreakRule.name());
        properties.setProperty("resourcesprint.timeLimitText", this.currentTimeLimitText());
        properties.setProperty("resourcesprint.objectives.count", Integer.toString(this.objectives.size()));
        for (int i = 0; i < this.objectives.size(); i++) {
            ResourceSprintSettings.ObjectiveEntry objective = this.objectives.get(i);
            properties.setProperty("resourcesprint.objective." + i + ".id", objective.id());
            properties.setProperty("resourcesprint.objective." + i + ".difficulty", objective.difficulty().name());
            properties.setProperty("resourcesprint.objective." + i + ".probability", Double.toString(objective.probability()));
        }

        try {
            Path parent = DRAFT_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (var output = Files.newBufferedWriter(DRAFT_PATH)) {
                properties.store(output, "Miniverse Resource Sprint draft");
            }
            this.lastDraftSignature = this.buildDraftSignature();
        } catch (IOException ignored) {
        }
    }

    private String currentTimeLimitText() {
        return this.timeLimitField != null ? this.timeLimitField.getText().trim() : this.timeLimitText;
    }

    private String buildDraftSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(this.mode.name()).append('|').append(this.objectiveDistributionMode.name()).append('|').append(this.tieBreakRule.name()).append('|').append(this.currentTimeLimitText()).append('|');
        for (ResourceSprintSettings.ObjectiveEntry objective : this.objectives) {
            builder.append(objective.id()).append('=').append(objective.difficulty().name()).append('~').append(objective.probability()).append(';');
        }
        return builder.toString();
    }

    private static ResourceSprintSettings.ObjectiveDistributionMode parseDistributionMode(String value, ResourceSprintSettings.ObjectiveDistributionMode fallback) {
        try {
            return value == null || value.isBlank() ? fallback : ResourceSprintSettings.ObjectiveDistributionMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static SprintMode parseMode(String value, SprintMode fallback) {
        try {
            return value == null || value.isBlank() ? fallback : SprintMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static TieBreakRule parseTieBreakRule(String value, TieBreakRule fallback) {
        try {
            return value == null || value.isBlank() ? fallback : TieBreakRule.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private List<ResourceSprintSettings.ObjectiveEntry> readObjectives(Properties properties) {
        int count = parseInt(properties.getProperty("resourcesprint.objectives.count"));
        List<ResourceSprintSettings.ObjectiveEntry> objectives = new ArrayList<>();

        if (count >= 0) {
            for (int i = 0; i < count; i++) {
                String id = properties.getProperty("resourcesprint.objective." + i + ".id", "").trim().toLowerCase(Locale.ROOT);
                if (id.isBlank()) {
                    continue;
                }
                ResourceSprintSettings.ObjectiveDifficulty difficulty = parseDifficulty(properties.getProperty("resourcesprint.objective." + i + ".difficulty"));
                double probability = parseDouble(properties.getProperty("resourcesprint.objective." + i + ".probability"), 1.0);
                objectives.add(new ResourceSprintSettings.ObjectiveEntry(id, difficulty, probability));
            }
        }

        if (objectives.isEmpty()) {
            objectives.addAll(this.defaultObjectives());
        }

        return objectives;
    }

    private static ResourceSprintSettings.ObjectiveDifficulty parseDifficulty(String value) {
        try {
            return value == null || value.isBlank() ? ResourceSprintSettings.ObjectiveDifficulty.EASY : ResourceSprintSettings.ObjectiveDifficulty.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ResourceSprintSettings.ObjectiveDifficulty.EASY;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            double d = Double.parseDouble(value.trim());
            return d > 0 && d <= 1.0 ? d : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String distributionModeButtonLabel() {
        return this.objectiveDistributionMode == ResourceSprintSettings.ObjectiveDistributionMode.SHARED ? "Shared" : "Probabilistic";
    }

    private double defaultProbabilityFor(ResourceSprintSettings.ObjectiveDifficulty difficulty) {
        return switch (difficulty == null ? ResourceSprintSettings.ObjectiveDifficulty.EASY : difficulty) {
            case EASY -> 0.8;
            case MEDIUM -> 0.6;
            case HARD -> 0.3;
        };
    }

    private double normalizeProbability(double probability, ResourceSprintSettings.ObjectiveDifficulty difficulty) {
        if (probability > 0 && probability <= 1.0) {
            return probability;
        }
        return this.defaultProbabilityFor(difficulty);
    }

    private double nextProbability(double current) {
        double value = current > 0 && current <= 1.0 ? current : 1.0;
        int index = 0;
        for (int i = 0; i < PROBABILITY_OPTIONS.length; i++) {
            if (Math.abs(PROBABILITY_OPTIONS[i] - value) < 0.0001) {
                index = i;
                break;
            }
        }
        return PROBABILITY_OPTIONS[(index + 1) % PROBABILITY_OPTIONS.length];
    }

    private String formatProbability(double probability) {
        int percent = (int) Math.round(Math.max(0.0, Math.min(1.0, probability)) * 100.0);
        return percent + "%";
    }

    private static int parseInt(String value) {
        try {
            return value == null || value.isBlank() ? -1 : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
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

    private enum SprintMode {
        FIRST_TO_COMPLETE("First to Complete"),
        TIME_LIMITED("Time Limited");

        private final String label;

        SprintMode(String label) {
            this.label = label;
        }

        private SprintMode next() {
            return this == FIRST_TO_COMPLETE ? TIME_LIMITED : FIRST_TO_COMPLETE;
        }

        private String label() {
            return this.label;
        }

        private String nbtValue() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private enum TieBreakRule {
        SUDDEN_DEATH("Sudden Death"),
        FASTEST_TOTAL_TIME("Fastest Total Time");

        private final String label;

        TieBreakRule(String label) {
            this.label = label;
        }

        private TieBreakRule next() {
            return this == SUDDEN_DEATH ? FASTEST_TOTAL_TIME : SUDDEN_DEATH;
        }

        private String label() {
            return this.label;
        }

        private String nbtValue() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class TeamDraft {
        private final List<Member> members = new ArrayList<>();
        private String label;

        private TeamDraft(String label) {
            this.label = label;
        }

        private String label() {
            return this.label == null ? "" : this.label;
        }

        private int size() {
            return this.members.size();
        }

        private boolean isEmpty() {
            return this.members.isEmpty();
        }

        private List<Member> members() {
            return List.copyOf(this.members);
        }

        private void add(Member member) {
            this.members.removeIf(existing -> existing.uuid().equals(member.uuid()));
            this.members.add(member);
        }

        private boolean remove(UUID uuid) {
            return this.members.removeIf(member -> member.uuid().equals(uuid));
        }

        private boolean contains(UUID uuid) {
            return this.members.stream().anyMatch(member -> member.uuid().equals(uuid));
        }

        private NbtCompound toPlanCompound(String fallbackLabel) {
            NbtCompound compound = new NbtCompound();
            compound.putString("label", this.label() == null || this.label().isBlank() ? fallbackLabel : this.label());

            NbtList membersList = new NbtList();
            for (Member member : this.members) {
                NbtCompound memberCompound = new NbtCompound();
                memberCompound.putString("uuid", member.uuid().toString());
                memberCompound.putString("name", member.name());
                membersList.add(memberCompound);
            }
            compound.put("members", membersList);
            return compound;
        }

        private record Member(UUID uuid, String name) {
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
        int footerSaveWidth,
        int footerResetWidth,
        int footerBackWidth,
        int settingsLabelX,
        int settingsFieldX,
        int settingsFieldWidth,
        int winButtonX,
        int distributionModeButtonX,
        int tieBreakButtonX,
        int objectiveButtonX,
        int objectiveListX,
        int objectiveListY,
        int objectiveListWidth,
        int objectiveListHeight
    ) {
    }
}




