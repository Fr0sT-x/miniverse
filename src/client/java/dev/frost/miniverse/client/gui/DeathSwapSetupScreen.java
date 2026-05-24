package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapDefinition;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapSettings;
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

public class DeathSwapSetupScreen extends Screen {
    private static final String GAME_ID = DeathSwapDefinition.ID;
    private static final int PANEL_PADDING = 16;
    private static final int PANEL_MAX_WIDTH = 1040;
    private static final int PANEL_MAX_HEIGHT = 720;
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
    private static final int SETTINGS_HEIGHT = 170;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SOLO_ALL_BUTTON_WIDTH = 82;
    private static final int START_MATCH_BUTTON_WIDTH = 108;
    private static final int CREATE_TEAM_BUTTON_WIDTH = 94;
    private static final int ASSIGN_BUTTON_WIDTH = 174;
    private static final int REMOVE_BUTTON_WIDTH = 126;
    private static final int DELETE_BUTTON_WIDTH = 92;
    private static final int RESET_BUTTON_WIDTH = 72;
    private static final int BACK_BUTTON_WIDTH = 68;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_PRIMARY = 0xFFE0E0E0;
    private static final int TEXT_SECONDARY = 0xFFD8D8D8;
    private static final int TEXT_MUTED = 0xFFB8B8B8;
    private static final int TEXT_STATUS = 0xFFA0FFA0;

    private static final int[] TEAM_COLORS = {
        0xFF5B8CFF, 0xFF6BCB77, 0xFFFF8A5B, 0xFFE26EFD,
        0xFFF7D154, 0xFF52D0D1, 0xFFB06CFF, 0xFFFF6E91
    };

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final List<TeamDraft> teams = new ArrayList<>();
    private final Set<String> selectedPlayerUuids = new LinkedHashSet<>();

    private TextFieldWidget sessionNameField;
    private TextFieldWidget swapIntervalField;
    private TextFieldWidget gracePeriodField;
    private TextFieldWidget borderSizeField;
    private TextFieldWidget seedValueField;
    private ButtonWidget seedModeButton;
    private ButtonWidget keepInventoryButton;
    private ButtonWidget preserveVelocityButton;
    private ButtonWidget startButton;
    private ButtonWidget createTeamButton;
    private ButtonWidget assignToSelectedTeamButton;
    private ButtonWidget removeFromTeamButton;
    private ButtonWidget deleteTeamButton;
    private ButtonWidget soloAllButton;
    private ButtonWidget selectAllButton;
    private ButtonWidget clearSelectionButton;
    private ButtonWidget resetButton;
    private ButtonWidget backButton;

    private int selectedTeamIndex = -1;
    private int playerScrollOffset;
    private int teamScrollOffset;
    private DeathSwapSettings.SeedMode seedMode = DeathSwapSettings.SeedMode.RANDOM;
    private boolean keepInventory = true;
    private boolean preserveVelocity = true;
    private String statusMessage = "";

    public DeathSwapSetupScreen() {
        super(Text.literal("Death Swap Setup"));
    }

    @Override
    protected void init() {
        super.init();
        this.clearChildren();

        Layout layout = this.createLayout();
        this.selectedTeamIndex = this.teams.isEmpty() ? -1 : Math.clamp(this.selectedTeamIndex, 0, this.teams.size() - 1);

        this.sessionNameField = this.addField(layout.sessionFieldX(), layout.sessionFieldY(), layout.sessionFieldWidth(), "session name", "deathswap-" + System.currentTimeMillis());

        this.selectAllButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Select All"), button -> this.selectAll())
            .dimensions(layout.selectAllX(), layout.sessionFieldY(), 86, TOP_BUTTON_HEIGHT).build());
        this.clearSelectionButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), button -> this.clearSelection())
            .dimensions(layout.clearSelectionX(), layout.sessionFieldY(), 60, TOP_BUTTON_HEIGHT).build());
        this.soloAllButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Solo All"), button -> this.soloAll())
            .dimensions(layout.soloAllX(), layout.sessionFieldY(), SOLO_ALL_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());
        this.startButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("▶ Start Match"), button -> this.createSession())
            .dimensions(layout.startMatchX(), layout.sessionFieldY(), START_MATCH_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build());

        this.createTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("➕ Create Team"), button -> this.createTeam())
            .dimensions(layout.createTeamX(), layout.actionButtonsY(), CREATE_TEAM_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());
        this.assignToSelectedTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("→ Assign To Selected Team"), button -> this.assignSelectedPlayersToTeam())
            .dimensions(layout.assignTeamX(), layout.actionButtonsY(), ASSIGN_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());
        this.removeFromTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("↩ Remove From Team"), button -> this.removeSelectedPlayersFromTeams())
            .dimensions(layout.removeTeamX(), layout.actionButtonsY(), REMOVE_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());
        this.deleteTeamButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("✕ Delete Team"), button -> this.deleteSelectedTeam())
            .dimensions(layout.deleteTeamX(), layout.actionButtonsY(), DELETE_BUTTON_WIDTH, ACTION_BUTTON_HEIGHT).build());

        this.swapIntervalField = this.addField(layout.leftFieldX(), layout.swapIntervalY(), layout.leftFieldWidth(), "swap interval seconds", "300");
        this.gracePeriodField = this.addField(layout.leftFieldX(), layout.gracePeriodY(), layout.leftFieldWidth(), "initial grace seconds", "30");
        this.borderSizeField = this.addField(layout.leftFieldX(), layout.borderSizeY(), layout.leftFieldWidth(), "border size", "3000");

        this.seedModeButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.seedModeLabel()), button -> {
            this.seedMode = this.seedMode.next();
            button.setMessage(Text.literal(this.seedModeLabel()));
        }).dimensions(layout.rightFieldX(), layout.seedModeY(), layout.rightFieldWidth(), 20).build());
        this.seedValueField = this.addField(layout.rightFieldX(), layout.seedValueY(), layout.rightFieldWidth(), "world seed", Long.toString(System.currentTimeMillis()));
        this.keepInventoryButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.toggleLabel("Keep Inventory", this.keepInventory)), button -> {
            this.keepInventory = !this.keepInventory;
            button.setMessage(Text.literal(this.toggleLabel("Keep Inventory", this.keepInventory)));
        }).dimensions(layout.rightFieldX(), layout.keepInventoryY(), layout.rightFieldWidth(), 20).build());
        this.preserveVelocityButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(this.toggleLabel("Preserve Velocity", this.preserveVelocity)), button -> {
            this.preserveVelocity = !this.preserveVelocity;
            button.setMessage(Text.literal(this.toggleLabel("Preserve Velocity", this.preserveVelocity)));
        }).dimensions(layout.rightFieldX(), layout.preserveVelocityY(), layout.rightFieldWidth(), 20).build());

        this.resetButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> this.resetDraft())
            .dimensions(layout.footerResetX(), layout.footerButtonsY(), RESET_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());
        this.backButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> this.client.setScreen(new SessionScreen()))
            .dimensions(layout.footerBackX(), layout.footerButtonsY(), BACK_BUTTON_WIDTH, FOOTER_BUTTON_HEIGHT).build());

        this.requestSnapshot();
    }

    @Override
    public void tick() {
        super.tick();
        boolean connected = this.client.player != null;
        boolean hasSelection = !this.selectedPlayerUuids.isEmpty();

        this.sessionNameField.active = connected;
        this.swapIntervalField.active = connected;
        this.gracePeriodField.active = connected;
        this.borderSizeField.active = connected;
        this.seedModeButton.active = connected;
        this.seedValueField.active = connected && this.seedMode == DeathSwapSettings.SeedMode.FIXED;
        this.keepInventoryButton.active = connected;
        this.preserveVelocityButton.active = connected;
        this.selectAllButton.active = connected;
        this.clearSelectionButton.active = connected;
        this.soloAllButton.active = connected && hasSelection;
        this.createTeamButton.active = connected;
        this.assignToSelectedTeamButton.active = connected && hasSelection && this.selectedTeamIndex >= 0;
        this.removeFromTeamButton.active = connected && hasSelection;
        this.deleteTeamButton.active = connected && this.selectedTeamIndex >= 0;
        this.startButton.active = connected && this.getStartValidationMessage().isEmpty();
        this.resetButton.active = connected;
        this.backButton.active = true;

        this.syncState();
        this.updateButtonLabels();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Layout layout = this.createLayout();

        int teamIndex = this.getTeamRowAt(mouseX, mouseY, layout);
        if (teamIndex >= 0) {
            this.selectedTeamIndex = teamIndex;
            return true;
        }

        int rosterIndex = this.getRosterRowAt(mouseX, mouseY, layout);
        if (rosterIndex >= 0) {
            this.togglePlayerSelection(SessionSnapshotData.roster().get(rosterIndex).uuid());
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

        if (this.isWithin(mouseX, mouseY, layout.availableX(), layout.listsY(), layout.availableWidth(), layout.listsHeight())) {
            int maxScroll = this.getMaxRosterScroll(layout);
            if (maxScroll > 0) {
                this.playerScrollOffset = clamp(this.playerScrollOffset - delta, 0, maxScroll);
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
        this.drawRosterPanel(context, layout, mouseX, mouseY);
        this.drawTeamPanel(context, layout, mouseX, mouseY);
        this.drawSettingsPanel(context, layout);
        super.render(context, mouseX, mouseY, delta);

        int centerX = layout.panelX() + layout.panelWidth() / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, layout.panelY() + TITLE_TOP_Y, TEXT_WHITE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Survive swaps and build solo or team-based win conditions."), centerX, layout.panelY() + SUBTITLE_TOP_Y, TEXT_MUTED);
        context.drawText(this.textRenderer, Text.literal("Session Name:"), layout.sessionFieldX() - 104, layout.sessionFieldY() + 6, TEXT_PRIMARY, false);
        context.drawText(this.textRenderer, Text.literal("Click players to select them for the session."), layout.availableX() + 8, layout.listsY() - 10, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Teams are optional; unassigned players behave as solo teams."), layout.teamX() + 8, layout.listsY() - 10, TEXT_SECONDARY, false);
        this.drawSettingsLabels(context, layout);

        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(validation), centerX, layout.footerButtonsY() + 28, 0xFFFFD070);
        } else if (!this.statusMessage.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.statusMessage), centerX, layout.footerButtonsY() + 28, TEXT_STATUS);
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

    private void drawRosterPanel(DrawContext context, Layout layout, int mouseX, int mouseY) {
        int x = layout.availableX();
        int y = layout.listsY();
        int width = layout.availableWidth();
        int height = layout.listsHeight();
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        int visibleRows = this.getRosterVisibleRows(layout);
        int rowsToDraw = Math.clamp(roster.size() - this.playerScrollOffset, 0, visibleRows);

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
        context.drawText(this.textRenderer, Text.literal("Available Players (" + roster.size() + ")"), x + 8, y + 5, TEXT_WHITE, false);

        if (roster.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No players online."), x + width / 2, y + height / 2 - 4, TEXT_MUTED);
            return;
        }

        for (int row = 0; row < rowsToDraw; row++) {
            SessionSnapshotData.RosterEntry entry = roster.get(this.playerScrollOffset + row);
            int rowY = y + COLUMN_HEADER_HEIGHT + 2 + row * PLAYER_ROW_HEIGHT;
            boolean selected = this.selectedPlayerUuids.contains(entry.uuid());
            boolean hovered = mouseX >= x + 1 && mouseX <= x + width - 1 && mouseY >= rowY && mouseY <= rowY + PLAYER_ROW_HEIGHT - 1;
            int background = selected ? 0xAA3A5C8F : (hovered ? 0x66404040 : 0x33222222);
            context.fill(x + 1, rowY, x + width - 1, rowY + PLAYER_ROW_HEIGHT - 1, background);
            context.fill(x + 4, rowY + 3, x + 8, rowY + PLAYER_ROW_HEIGHT - 4, 0xFF5B8CFF);
            context.drawText(this.textRenderer, Text.literal(entry.name()), x + 14, rowY + 5, TEXT_SECONDARY, false);
        }

        this.drawScrollBar(context, x, y, width, height, roster.size(), visibleRows, this.playerScrollOffset);
    }

    private void drawTeamPanel(DrawContext context, Layout layout, int mouseX, int mouseY) {
        int x = layout.teamX();
        int y = layout.listsY();
        int width = layout.teamWidth();
        int height = layout.listsHeight();
        int visibleRows = this.getTeamVisibleRows(layout);
        int rowsToDraw = Math.clamp(this.teams.size() - this.teamScrollOffset, 0, visibleRows);

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
        context.drawText(this.textRenderer, Text.literal("Teams (" + this.teams.size() + ")"), x + 8, y + 5, TEXT_WHITE, false);

        if (this.teams.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Create a team or use Solo All."), x + width / 2, y + height / 2 - 4, TEXT_MUTED);
            return;
        }

        for (int row = 0; row < rowsToDraw; row++) {
            int teamIndex = this.teamScrollOffset + row;
            TeamDraft team = this.teams.get(teamIndex);
            int rowY = y + COLUMN_HEADER_HEIGHT + 2 + row * TEAM_ROW_HEIGHT;
            boolean selected = teamIndex == this.selectedTeamIndex;
            boolean hovered = mouseX >= x + 1 && mouseX <= x + width - 1 && mouseY >= rowY && mouseY <= rowY + TEAM_ROW_HEIGHT - 1;
            int background = selected ? 0xAA405D8A : (hovered ? 0x66404040 : 0x33222222);
            int accentColor = TEAM_COLORS[teamIndex % TEAM_COLORS.length];

            context.fill(x + 1, rowY, x + width - 1, rowY + TEAM_ROW_HEIGHT - 1, background);
            context.fill(x + 4, rowY + 3, x + 10, rowY + TEAM_ROW_HEIGHT - 4, accentColor);
            String countText = team.size() + " player" + (team.size() == 1 ? "" : "s");
            context.drawText(this.textRenderer, Text.literal(team.label().isBlank() ? this.fallbackTeamLabel(teamIndex) : team.label()), x + 14, rowY + 5, TEXT_WHITE, false);
            context.drawText(this.textRenderer, Text.literal(countText), x + width - 8 - this.textRenderer.getWidth(countText), rowY + 5, TEXT_MUTED, false);
            context.drawText(this.textRenderer, Text.literal(this.elide(team.isEmpty() ? "No members yet" : this.joinMemberNames(team), width - 28)), x + 14, rowY + 20, TEXT_SECONDARY, false);
        }

        this.drawScrollBar(context, x, y, width, height, this.teams.size(), visibleRows, this.teamScrollOffset);
    }

    private void drawSettingsPanel(DrawContext context, Layout layout) {
        int x = layout.panelX() + PANEL_PADDING;
        int y = layout.settingsY();
        int width = layout.settingsWidth();
        int height = layout.settingsHeight();

        context.fill(x, y, x + width, y + height, 0x66141414);
        context.fill(x + 1, y + 1, x + width - 1, y + COLUMN_HEADER_HEIGHT, 0xCC222222);
        context.fill(x + 1, y + 1, x + width - 1, y + 3, 0xFF5B5B5B);
        context.drawText(this.textRenderer, Text.literal("Death Swap Settings"), x + 8, y + 5, TEXT_WHITE, false);
    }

    private void drawSettingsLabels(DrawContext context, Layout layout) {
        context.drawText(this.textRenderer, Text.literal("Swap Interval:"), layout.leftLabelX(), layout.swapIntervalY() + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Initial Grace:"), layout.leftLabelX(), layout.gracePeriodY() + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Border Size:"), layout.leftLabelX(), layout.borderSizeY() + 5, TEXT_SECONDARY, false);

        context.drawText(this.textRenderer, Text.literal("World Seed:"), layout.rightLabelX(), layout.seedModeY() + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Keep Inventory:"), layout.rightLabelX(), layout.keepInventoryY() + 5, TEXT_SECONDARY, false);
        context.drawText(this.textRenderer, Text.literal("Preserve Velocity:"), layout.rightLabelX(), layout.preserveVelocityY() + 5, TEXT_SECONDARY, false);
    }

    private void createSession() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            this.statusMessage = validation;
            return;
        }

        List<SessionSnapshotData.RosterEntry> selectedParticipants = this.getSelectedParticipants();
        NbtCompound plan = new NbtCompound();
        plan.putString("game", GAME_ID);
        plan.putString("name", this.sessionNameField.getText().trim());
        plan.putBoolean("launch", true);
        plan.put("settings", this.buildSettingsCompound());

        // Don't add groups - all players will be on the same server
        // Team assignments are stored in settings instead
        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(GAME_ID, this.sessionNameField.getText().trim(), plan));
        this.statusMessage = "Requested Death Swap session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putInt("swapIntervalSeconds", this.readInt(this.swapIntervalField, 300, 1, 3600, "Swap interval must be a number."));
        settings.putInt("initialGracePeriodSeconds", this.readInt(this.gracePeriodField, 30, 0, 3600, "Grace period must be a number."));
        settings.putInt("borderSize", this.readInt(this.borderSizeField, 3000, 16, 60_000, "Border size must be a number."));
        settings.putBoolean("keepInventory", this.keepInventory);
        settings.putBoolean("preserveVelocity", this.preserveVelocity);
        settings.putString("seedMode", this.seedMode.nbtValue());
        settings.putLong("seed", this.seedMode == DeathSwapSettings.SeedMode.FIXED ? this.getSelectedSeedValue() : System.currentTimeMillis());

        NbtList teamsList = new NbtList();
        for (TeamDraft team : this.teams) {
            if (team.isEmpty()) {
                continue;
            }
            NbtCompound teamCompound = new NbtCompound();
            teamCompound.putString("label", team.label());
            NbtList teamMembers = new NbtList();
            for (TeamDraft.Member member : team.members()) {
                NbtCompound memberCompound = new NbtCompound();
                memberCompound.putString("uuid", member.uuid().toString());
                memberCompound.putString("name", member.name());
                teamMembers.add(memberCompound);
            }
            teamCompound.put("members", teamMembers);
            teamsList.add(teamCompound);
        }
        settings.put("teams", teamsList);
        return settings;
    }

    private int readInt(TextFieldWidget field, int fallback, int min, int max, String errorMessage) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.setText(Integer.toString(fallback));
            return fallback;
        }
        try {
            int value = clamp(Integer.parseInt(text), min, max);
            field.setText(Integer.toString(value));
            return value;
        } catch (NumberFormatException ignored) {
            this.statusMessage = errorMessage;
            field.setText(Integer.toString(fallback));
            return fallback;
        }
    }

    private void selectAll() {
        this.selectedPlayerUuids.clear();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            this.selectedPlayerUuids.add(entry.uuid());
        }
        this.statusMessage = "Selected all players.";
    }

    private void clearSelection() {
        this.selectedPlayerUuids.clear();
        this.statusMessage = "Selection cleared.";
    }

    private void soloAll() {
        this.teams.clear();
        for (SessionSnapshotData.RosterEntry entry : this.getSelectedParticipants()) {
            TeamDraft team = new TeamDraft(this.nextTeamLabel());
            team.add(new TeamDraft.Member(UUID.fromString(entry.uuid()), entry.name()));
            this.teams.add(team);
        }
        this.selectedTeamIndex = this.teams.isEmpty() ? -1 : 0;
        this.statusMessage = "Created one solo team per selected player.";
    }

    private void createTeam() {
        TeamDraft team = new TeamDraft(this.nextTeamLabel());
        this.teams.add(team);
        this.selectedTeamIndex = this.teams.size() - 1;
        this.statusMessage = "Created " + team.label() + ".";
    }

    private void assignSelectedPlayersToTeam() {
        TeamDraft team = this.getSelectedTeam();
        if (team == null) {
            this.statusMessage = "Select a team first.";
            return;
        }

        List<SessionSnapshotData.RosterEntry> selectedPlayers = this.getSelectedParticipants();
        if (selectedPlayers.isEmpty()) {
            this.statusMessage = "Select one or more players first.";
            return;
        }

        for (SessionSnapshotData.RosterEntry player : selectedPlayers) {
            this.removePlayerFromAllTeams(player.uuid());
            team.add(new TeamDraft.Member(UUID.fromString(player.uuid()), player.name()));
        }

        this.statusMessage = "Assigned " + selectedPlayers.size() + " player" + (selectedPlayers.size() == 1 ? "" : "s") + " to " + this.displayTeamName(team) + ".";
    }

    private void removeSelectedPlayersFromTeams() {
        List<SessionSnapshotData.RosterEntry> selectedPlayers = this.getSelectedParticipants();
        if (selectedPlayers.isEmpty()) {
            this.statusMessage = "Select one or more players first.";
            return;
        }

        int removed = 0;
        for (SessionSnapshotData.RosterEntry player : selectedPlayers) {
            removed += this.removePlayerFromAllTeams(player.uuid()) ? 1 : 0;
        }
        this.statusMessage = removed == 0 ? "Selected players are not assigned to any team." : "Removed " + removed + " player" + (removed == 1 ? "" : "s") + " from teams.";
    }

    private void deleteSelectedTeam() {
        TeamDraft team = this.getSelectedTeam();
        if (team == null) {
            this.statusMessage = "Select a team first.";
            return;
        }

        String label = this.displayTeamName(team);
        this.teams.remove(this.selectedTeamIndex);
        this.selectedTeamIndex = this.teams.isEmpty() ? -1 : Math.clamp(this.selectedTeamIndex, 0, this.teams.size() - 1);
        this.statusMessage = "Deleted " + label + ".";
    }

    private void togglePlayerSelection(String uuid) {
        if (this.selectedPlayerUuids.contains(uuid)) {
            this.selectedPlayerUuids.remove(uuid);
        } else {
            this.selectedPlayerUuids.add(uuid);
        }
    }

    private boolean removePlayerFromAllTeams(String uuid) {
        boolean removed = false;
        for (TeamDraft team : this.teams) {
            removed |= team.remove(uuid);
        }
        return removed;
    }

    private void syncState() {
        Set<String> rosterUuids = new LinkedHashSet<>();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            rosterUuids.add(entry.uuid());
        }
        this.selectedPlayerUuids.retainAll(rosterUuids);

        Layout layout = this.createLayout();
        this.playerScrollOffset = clamp(this.playerScrollOffset, 0, this.getMaxRosterScroll(layout));
        this.teamScrollOffset = clamp(this.teamScrollOffset, 0, this.getMaxTeamScroll(layout));
        if (this.selectedTeamIndex >= this.teams.size()) {
            this.selectedTeamIndex = this.teams.isEmpty() ? -1 : this.teams.size() - 1;
        }
    }

    private String getStartValidationMessage() {
        if (this.client.player == null) {
            return "Not connected to a server.";
        }
        if (this.sessionNameField.getText().trim().isEmpty()) {
            return "Enter a session name.";
        }
        if (this.selectedPlayerUuids.size() < 2) {
            return "Select at least two players.";
        }

        if (this.teams.isEmpty()) {
            return "";
        }

        int explicitTeams = 0;
        Set<String> assigned = new LinkedHashSet<>();
        for (TeamDraft team : this.teams) {
            if (team.isEmpty()) {
                continue;
            }
            explicitTeams++;
            for (TeamDraft.Member member : team.members()) {
                assigned.add(member.uuid().toString());
            }
        }

        int unassigned = 0;
        for (String uuid : this.selectedPlayerUuids) {
            if (!assigned.contains(uuid)) {
                unassigned++;
            }
        }

        return explicitTeams + unassigned < 2 ? "Need at least two living teams. Use Solo All or add another team." : "";
    }

    private void updateButtonLabels() {
        this.seedModeButton.setMessage(Text.literal(this.seedModeLabel()));
        this.keepInventoryButton.setMessage(Text.literal(this.toggleLabel("Keep Inventory", this.keepInventory)));
        this.preserveVelocityButton.setMessage(Text.literal(this.toggleLabel("Preserve Velocity", this.preserveVelocity)));
    }

    private String seedModeLabel() {
        return this.seedMode == DeathSwapSettings.SeedMode.RANDOM ? "World Seed: Random" : "World Seed: Fixed";
    }

    private String toggleLabel(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }

    private List<SessionSnapshotData.RosterEntry> getSelectedParticipants() {
        List<SessionSnapshotData.RosterEntry> selected = new ArrayList<>();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            if (this.selectedPlayerUuids.contains(entry.uuid())) {
                selected.add(entry);
            }
        }
        return selected;
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
        return this.fallbackTeamLabel(Math.max(0, this.teams.indexOf(team)));
    }

    private String fallbackTeamLabel(int index) {
        return "Team " + (index + 1);
    }

    private String nextTeamLabel() {
        return "Team " + (this.teams.size() + 1);
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

    private void resetDraft() {
        this.teams.clear();
        this.selectedPlayerUuids.clear();
        this.selectedTeamIndex = -1;
        this.playerScrollOffset = 0;
        this.teamScrollOffset = 0;
        this.seedMode = DeathSwapSettings.SeedMode.RANDOM;
        this.keepInventory = true;
        this.preserveVelocity = true;
        if (this.seedValueField != null) {
            this.seedValueField.setText(Long.toString(System.currentTimeMillis()));
        }
        this.statusMessage = "Draft reset.";
    }

    private TextFieldWidget addField(int x, int y, int width, String placeholder, String value) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, y, width, 20, Text.literal(placeholder));
        field.setMaxLength(36);
        field.setText(value);
        this.addDrawableChild(field);
        return field;
    }

    private long getSelectedSeedValue() {
        String text = this.seedValueField.getText().trim();
        if (text.isEmpty()) {
            throw new NumberFormatException("empty seed");
        }
        return Long.parseLong(text);
    }

    private NbtCompound member(SessionSnapshotData.RosterEntry entry) {
        NbtCompound compound = new NbtCompound();
        compound.putString("uuid", entry.uuid());
        compound.putString("name", entry.name());
        return compound;
    }

    private void requestSnapshot() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to a server.";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.RequestSessionsPayload("refresh"));
        this.statusMessage = "Requested current roster.";
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

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
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

    private static int clamp(int value, int min, int max) {
        return Math.clamp(value, min, max);
    }

    private Layout createLayout() {
        int panelWidth = Math.min(PANEL_MAX_WIDTH, this.width - 24);
        int panelHeight = Math.min(PANEL_MAX_HEIGHT, this.height - 24);
        int panelX = (this.width - panelWidth) / 2;
        int panelY = Math.max(12, (this.height - panelHeight) / 2);

        int contentX = panelX + PANEL_PADDING;
        int contentWidth = panelWidth - (PANEL_PADDING * 2);
        int topButtonsY = panelY + SESSION_ROW_Y;
        int startMatchX = panelX + panelWidth - PANEL_PADDING - START_MATCH_BUTTON_WIDTH;
        int soloAllX = startMatchX - 8 - SOLO_ALL_BUTTON_WIDTH;
        int clearSelectionX = soloAllX - 8 - 60;
        int selectAllX = clearSelectionX - 8 - 86;
        int sessionFieldX = contentX + 110;
        int sessionFieldWidth = Math.max(220, selectAllX - 12 - sessionFieldX);

        int footerButtonsY = panelY + panelHeight - FOOTER_HEIGHT;
        int footerResetWidth = RESET_BUTTON_WIDTH;
        int footerGap = 8;
        int footerTotalWidth = footerResetWidth + BACK_BUTTON_WIDTH + footerGap;
        int footerLeft = panelX + (panelWidth - footerTotalWidth) / 2;

        int settingsY = footerButtonsY - SETTINGS_HEIGHT - 10;
        int actionButtonsY = settingsY - 36;
        int listTop = panelY + LIST_TOP_Y;
        int listsHeight = Math.max(168, actionButtonsY - listTop - 10);

        int availableWidth = Math.max(320, (contentWidth - TEAM_GAP) / 2);
        int teamWidth = Math.max(320, contentWidth - availableWidth - TEAM_GAP);
        int teamX = contentX + availableWidth + TEAM_GAP;

        int actionTotalWidth = CREATE_TEAM_BUTTON_WIDTH + ASSIGN_BUTTON_WIDTH + REMOVE_BUTTON_WIDTH + DELETE_BUTTON_WIDTH + (8 * 3);
        int actionLeft = panelX + (panelWidth - actionTotalWidth) / 2;

        int leftLabelX = contentX + 8;
        int leftFieldX = leftLabelX + 140;
        int leftFieldWidth = 160;
        int rightLabelX = panelX + panelWidth / 2 + 20;
        int rightFieldX = rightLabelX + 140;
        int rightFieldWidth = Math.min(220, panelX + panelWidth - PANEL_PADDING - rightFieldX - 10);

        int swapIntervalY = settingsY + 24;
        int gracePeriodY = settingsY + 46;
        int borderSizeY = settingsY + 68;
        int seedModeY = settingsY + 24;
        int seedValueY = settingsY + 46;
        int keepInventoryY = settingsY + 68;
        int preserveVelocityY = settingsY + 90;

        return new Layout(panelX, panelY, panelWidth, panelHeight, contentX, sessionFieldX, topButtonsY, sessionFieldWidth, selectAllX, clearSelectionX, soloAllX, startMatchX, contentX, teamX, listTop, listsHeight, availableWidth, teamWidth, actionButtonsY, actionLeft, actionLeft + CREATE_TEAM_BUTTON_WIDTH + 8, actionLeft + CREATE_TEAM_BUTTON_WIDTH + ASSIGN_BUTTON_WIDTH + 16, actionLeft + CREATE_TEAM_BUTTON_WIDTH + ASSIGN_BUTTON_WIDTH + REMOVE_BUTTON_WIDTH + 24, settingsY, contentWidth, SETTINGS_HEIGHT, leftLabelX, leftFieldX, leftFieldWidth, rightLabelX, rightFieldX, rightFieldWidth, swapIntervalY, gracePeriodY, borderSizeY, seedModeY, seedValueY, keepInventoryY, preserveVelocityY, footerButtonsY, footerLeft, footerLeft + footerResetWidth + footerGap);
    }

    private record Layout(int panelX, int panelY, int panelWidth, int panelHeight, int contentX, int sessionFieldX, int sessionFieldY, int sessionFieldWidth, int selectAllX, int clearSelectionX, int soloAllX, int startMatchX, int availableX, int teamX, int listsY, int listsHeight, int availableWidth, int teamWidth, int actionButtonsY, int createTeamX, int assignTeamX, int removeTeamX, int deleteTeamX, int settingsY, int settingsWidth, int settingsHeight, int leftLabelX, int leftFieldX, int leftFieldWidth, int rightLabelX, int rightFieldX, int rightFieldWidth, int swapIntervalY, int gracePeriodY, int borderSizeY, int seedModeY, int seedValueY, int keepInventoryY, int preserveVelocityY, int footerButtonsY, int footerResetX, int footerBackX) {
    }

    private static final class TeamDraft {
        private final List<Member> members = new ArrayList<>();
        private final String label;

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

        private boolean remove(String uuid) {
            return this.members.removeIf(member -> member.uuid().toString().equals(uuid));
        }

        private record Member(UUID uuid, String name) {
        }
    }
}







