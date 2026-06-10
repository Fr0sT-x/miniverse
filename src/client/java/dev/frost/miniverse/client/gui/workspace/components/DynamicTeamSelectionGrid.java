package dev.frost.miniverse.client.gui.workspace.components;

import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.TeamDraft;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DynamicTeamSelectionGrid extends TeamSelectionGrid {
    private static final int TEAM_ROW_HEIGHT = 36;
    private static final int[] TEAM_COLORS = {
        0xFF5B8CFF, 0xFF6BCB77, 0xFFFF8A5B, 0xFFE26EFD,
        0xFFF7D154, 0xFF52D0D1, 0xFFB06CFF, 0xFFFF6E91
    };

    private final List<TeamDraft> teams = new ArrayList<>();
    private final Set<String> selectedPlayerUuids = new LinkedHashSet<>();
    
    private int selectedTeamIndex = -1;
    private int rosterScrollOffset;
    private int teamScrollOffset;

    public DynamicTeamSelectionGrid() {
        if (this.teams.isEmpty()) {
            this.addTeam("");
            this.selectedTeamIndex = 0;
        }
    }

    public List<TeamDraft> getTeams() {
        return this.teams;
    }

    public void clear() {
        this.teams.clear();
        this.selectedPlayerUuids.clear();
        this.selectedTeamIndex = -1;
        this.addTeam("");
        this.selectedTeamIndex = 0;
    }

    public TeamDraft addTeam(String label) {
        TeamDraft team = new TeamDraft(label);
        this.teams.add(team);
        return team;
    }

    public void createTeam() {
        TeamDraft team = this.addTeam("");
        this.selectedTeamIndex = this.teams.indexOf(team);
    }

    public boolean assignSelectedPlayersToTeam() {
        TeamDraft team = this.getSelectedTeam();
        if (team == null) return false;
        List<SessionSnapshotData.RosterEntry> selectedPlayers = this.getSelectedPlayers();
        if (selectedPlayers.isEmpty()) return false;
        
        for (SessionSnapshotData.RosterEntry player : selectedPlayers) {
            this.removePlayerFromAllTeams(player.uuid());
            team.add(new TeamDraft.Member(UUID.fromString(player.uuid()), player.name()));
        }
        this.selectedPlayerUuids.clear();
        return true;
    }

    public boolean removeSelectedPlayersFromTeams() {
        List<SessionSnapshotData.RosterEntry> selectedPlayers = this.getSelectedPlayers();
        if (selectedPlayers.isEmpty()) return false;
        
        boolean removedAny = false;
        for (SessionSnapshotData.RosterEntry player : selectedPlayers) {
            removedAny |= this.removePlayerFromAllTeams(player.uuid());
        }
        this.selectedPlayerUuids.clear();
        return removedAny;
    }

    public boolean deleteSelectedTeam() {
        if (this.selectedTeamIndex < 0 || this.selectedTeamIndex >= this.teams.size()) return false;
        this.teams.remove(this.selectedTeamIndex);
        if (this.teams.isEmpty()) {
            this.selectedTeamIndex = -1;
        } else if (this.selectedTeamIndex >= this.teams.size()) {
            this.selectedTeamIndex = this.teams.size() - 1;
        }
        return true;
    }

    public boolean soloAll() {
        List<SessionSnapshotData.RosterEntry> roster = SessionSnapshotData.roster();
        if (roster.isEmpty()) return false;
        
        this.teams.clear();
        for (SessionSnapshotData.RosterEntry entry : roster) {
            TeamDraft team = new TeamDraft("");
            team.add(new TeamDraft.Member(UUID.fromString(entry.uuid()), entry.name()));
            this.teams.add(team);
        }
        this.selectedPlayerUuids.clear();
        this.selectedTeamIndex = this.teams.isEmpty() ? -1 : 0;
        this.teamScrollOffset = 0;
        return true;
    }

    @Override
    public void refreshRoster() {
        Set<String> currentUuids = new LinkedHashSet<>();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            currentUuids.add(entry.uuid());
        }
        this.selectedPlayerUuids.retainAll(currentUuids);
        
        for (TeamDraft team : this.teams) {
            team.members().forEach(member -> {
                if (!currentUuids.contains(member.uuid().toString())) {
                    team.remove(member.uuid());
                }
            });
        }
        if (this.selectedTeamIndex >= this.teams.size()) {
            this.selectedTeamIndex = this.teams.isEmpty() ? -1 : this.teams.size() - 1;
        }
    }

    @Override
    public void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        UiLayout.Rect rosterRect = this.rosterRect();
        UiLayout.Rect teamRect = this.teamRect();
        this.renderRosterPanel(context, textRenderer, rosterRect, mouseX, mouseY);
        this.renderTeamPanel(context, textRenderer, teamRect, mouseX, mouseY);
    }

    private void renderRosterPanel(DrawContext context, TextRenderer textRenderer, UiLayout.Rect rect, int mouseX, int mouseY) {
        List<SessionSnapshotData.RosterEntry> roster = this.getAvailablePlayers();
        int accent = UiTheme.ACCENT;
        UiRenderer.panel(context, rect.x(), rect.y(), rect.width(), rect.height(), UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + COLUMN_HEADER_HEIGHT, 0xA0192230);
        context.fill(rect.x() + 1, rect.y() + 1, rect.x() + rect.width() - 1, rect.y() + 3, accent);
        context.drawText(textRenderer, Text.literal("Available Players (" + roster.size() + ")"), rect.x() + 8, rect.y() + 7, UiTheme.TEXT, false);

        int visibleRows = this.visibleRows(rect.height());
        int rows = Math.min(visibleRows, roster.size() - this.rosterScrollOffset);
        int listTop = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        
        for (int row = 0; row < rows; row++) {
            SessionSnapshotData.RosterEntry entry = roster.get(this.rosterScrollOffset + row);
            if (this.draggedEntry != null && this.draggedEntry.uuid().equals(entry.uuid())) {
                continue;
            }
            int rowY = listTop + row * ROW_HEIGHT;
            boolean selected = this.selectedPlayerUuids.contains(entry.uuid());
            this.renderPlayerRow(context, textRenderer, rowY, rect.x(), rect.width(), mouseX, mouseY, entry, selected, accent);
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
            boolean dropTarget = this.draggedEntry != null && mouseX >= rect.x() + 1 && mouseX <= rect.x() + rect.width() - 1 && mouseY >= rowY && mouseY <= rowY + TEAM_ROW_HEIGHT - 2;
            int accent = TEAM_COLORS[teamIndex % TEAM_COLORS.length];
            
            int background = selected ? 0xAA405D8A : (dropTarget ? 0xAA2F5D94 : 0x33222222);
            context.fill(rect.x() + 1, rowY, rect.x() + rect.width() - 1, rowY + TEAM_ROW_HEIGHT - 2, background);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int rosterIndex = this.getRosterRowAt(mouseX, mouseY);
        if (rosterIndex >= 0) {
            SessionSnapshotData.RosterEntry entry = this.getAvailablePlayers().get(rosterIndex);
            this.togglePlayerSelection(entry.uuid());
            
            this.draggedEntry = entry;
            this.draggedFromId = "available";
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }

        MemberHit memberHit = this.getTeamMemberHit(mouseX, mouseY);
        if (memberHit != null) {
            this.selectedTeamIndex = memberHit.teamIndex;
            this.togglePlayerSelection(memberHit.uuid);
            
            for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
                if (entry.uuid().equals(memberHit.uuid)) {
                    this.draggedEntry = entry;
                    this.draggedFromId = "team_" + memberHit.teamIndex;
                    this.dragX = mouseX;
                    this.dragY = mouseY;
                    return true;
                }
            }
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
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || this.draggedEntry == null) return false;

        int targetTeamIndex = this.getTeamRowAt(mouseX, mouseY);
        if (targetTeamIndex >= 0) {
            this.removePlayerFromAllTeams(this.draggedEntry.uuid());
            TeamDraft targetTeam = this.teams.get(targetTeamIndex);
            targetTeam.add(new TeamDraft.Member(UUID.fromString(this.draggedEntry.uuid()), this.draggedEntry.name()));
            this.selectedTeamIndex = targetTeamIndex;
            this.selectedPlayerUuids.clear();
        } else if (this.rosterRect().contains(mouseX, mouseY)) {
            this.removePlayerFromAllTeams(this.draggedEntry.uuid());
        }

        this.draggedEntry = null;
        this.draggedFromId = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int delta = (int) Math.signum(amount);
        if (delta == 0) return false;

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

    private UiLayout.Rect rosterRect() {
        int columnWidth = (this.bounds.width() - COLUMN_GAP) / 2;
        return new UiLayout.Rect(this.bounds.x(), this.bounds.y(), columnWidth, this.bounds.height());
    }

    private UiLayout.Rect teamRect() {
        int columnWidth = (this.bounds.width() - COLUMN_GAP) / 2;
        return new UiLayout.Rect(this.bounds.x() + columnWidth + COLUMN_GAP, this.bounds.y(), columnWidth, this.bounds.height());
    }

    private int visibleRows(int height) {
        return Math.max(0, (height - COLUMN_HEADER_HEIGHT - 8) / ROW_HEIGHT);
    }

    private int visibleTeamRows(int height) {
        return Math.max(0, (height - COLUMN_HEADER_HEIGHT - 8) / TEAM_ROW_HEIGHT);
    }

    private int getRosterRowAt(double mouseX, double mouseY) {
        UiLayout.Rect rect = this.rosterRect();
        if (!rect.contains(mouseX, mouseY)) return -1;
        int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int row = (int) ((mouseY - rowStartY) / ROW_HEIGHT);
        int index = this.rosterScrollOffset + row;
        if (row < 0 || index < 0 || index >= this.getAvailablePlayers().size()) return -1;
        return index;
    }

    private int getTeamRowAt(double mouseX, double mouseY) {
        UiLayout.Rect rect = this.teamRect();
        if (!rect.contains(mouseX, mouseY)) return -1;
        int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
        int index = this.teamScrollOffset + row;
        if (row < 0 || index < 0 || index >= this.teams.size()) return -1;
        return index;
    }

    private MemberHit getTeamMemberHit(double mouseX, double mouseY) {
        UiLayout.Rect rect = this.teamRect();
        if (!rect.contains(mouseX, mouseY)) return null;
        
        int rowStartY = rect.y() + COLUMN_HEADER_HEIGHT + 4;
        int row = (int) ((mouseY - rowStartY) / TEAM_ROW_HEIGHT);
        int teamIndex = this.teamScrollOffset + row;
        if (row < 0 || teamIndex < 0 || teamIndex >= this.teams.size()) return null;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int rowY = rowStartY + row * TEAM_ROW_HEIGHT;
        int textY = rowY + 19;
        if (mouseY < textY || mouseY > textY + textRenderer.fontHeight) return null;

        TeamDraft team = this.teams.get(teamIndex);
        MemberLine line = this.buildMemberLine(team, rect.width() - 24, 6, textRenderer);
        if (line.spans.isEmpty()) return null;

        int textX = rect.x() + 16;
        double relativeX = mouseX - textX;
        if (relativeX < 0 || relativeX > textRenderer.getWidth(line.text)) return null;

        for (MemberSpan span : line.spans) {
            if (relativeX >= span.startX && relativeX <= span.endX) {
                return new MemberHit(span.uuid, span.name, teamIndex);
            }
        }
        return null;
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

    private boolean removePlayerFromAllTeams(String uuid) {
        boolean removed = false;
        UUID playerUuid = UUID.fromString(uuid);
        for (TeamDraft team : this.teams) {
            removed |= team.remove(playerUuid);
        }
        return removed;
    }

    public TeamDraft getSelectedTeam() {
        if (this.selectedTeamIndex < 0 || this.selectedTeamIndex >= this.teams.size()) return null;
        return this.teams.get(this.selectedTeamIndex);
    }

    public String fallbackTeamLabel(int index) {
        return "Team " + (index + 1);
    }

    private MemberLine buildMemberLine(TeamDraft team, int maxWidth, int maxNames, TextRenderer textRenderer) {
        int memberCount = team.members().size();
        if (memberCount == 0) return new MemberLine("No players", List.of());

        List<MemberSpan> spans = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int width = 0;
        int shown = 0;

        for (TeamDraft.Member member : team.members()) {
            if (shown >= maxNames) break;
            
            String prefix = builder.isEmpty() ? "" : ", ";
            int prefixWidth = textRenderer.getWidth(prefix);
            int nameWidth = textRenderer.getWidth(member.name());
            if (width + prefixWidth + nameWidth > maxWidth) break;

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
}
