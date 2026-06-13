package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.TeamDraft;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.DynamicTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.StandardWorkspaceLayout;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.speedrun.SpeedrunDefinition;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class SpeedrunWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final DynamicTeamSelectionGrid teamGrid = new DynamicTeamSelectionGrid();

    private UiLayout.Rect actionCreate;
    private UiLayout.Rect actionAssign;
    private UiLayout.Rect actionRemove;
    private UiLayout.Rect actionDelete;
    private UiLayout.Rect actionSolo;

    private TextFieldWidget seedValueField;
    private ButtonWidget seedModeButton;

    private SeedMode seedMode = SeedMode.RANDOM;
    private String seedValue = "";

    public SpeedrunWorkspaceView() {
        super("speedrun");
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Draft and assign teams.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Control seed and world behavior.", UiTheme.ACCENT_BLUE);
        this.useGameRules();
        this.moduleManager.register("summary", "S", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT_GREEN);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("teams")) {
            int actionY = this.layout.actionY();
            int actionStart = this.layout.actionStartX();
            this.actionCreate = new UiLayout.Rect(actionStart, actionY, 96, StandardWorkspaceLayout.BUTTON_HEIGHT);
            this.actionAssign = new UiLayout.Rect(actionStart + 104, actionY, 164, StandardWorkspaceLayout.BUTTON_HEIGHT);
            this.actionRemove = new UiLayout.Rect(actionStart + 276, actionY, 140, StandardWorkspaceLayout.BUTTON_HEIGHT);
            this.actionDelete = new UiLayout.Rect(actionStart + 424, actionY, 96, StandardWorkspaceLayout.BUTTON_HEIGHT);
            this.actionSolo = new UiLayout.Rect(actionStart + 530, actionY, 96, StandardWorkspaceLayout.BUTTON_HEIGHT);
        } else if (this.moduleManager.isActive("rules")) {
            int y = this.layout.mainPanel().y() + 96;
            this.seedModeButton = this.addButton(screen, "Seed Mode: " + this.seedMode.label, this.layout.mainPanel().x() + 180, y, 170, () -> this.seedMode == SeedMode.RANDOM ? "Random world seed will be used." : "Specify an exact world seed in the text field.", () -> {
                this.seedMode = this.seedMode.next();
                this.seedModeButton.setMessage(Text.literal("Seed Mode: " + this.seedMode.label));
                if (this.seedMode == SeedMode.RANDOM) {
                    this.seedValueField.setEditable(false);
                    this.seedValueField.active = false;
                    this.seedValueField.setText("");
                    this.seedValueField.setSuggestion("Enter world seed");
                } else {
                    this.seedValueField.setEditable(true);
                    this.seedValueField.active = true;
                    this.seedValueField.setSuggestion("");
                    this.seedValueField.setText(this.seedValue);
                }
            });
            y += 32;
            this.seedValueField = this.addField(screen, this.layout.mainPanel().x() + 180, y, this.seedMode == SeedMode.FIXED ? this.seedValue : "", 170, "Seed value", () -> "Specify an exact world seed in the text field.");
            if (this.seedMode == SeedMode.RANDOM) {
                this.seedValueField.setEditable(false);
                this.seedValueField.active = false;
                this.seedValueField.setSuggestion("Enter world seed");
            } else {
                this.seedValueField.setEditable(true);
                this.seedValueField.active = true;
                this.seedValueField.setSuggestion("");
            }
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("teams")) {
            this.renderActionButton(context, textRenderer, this.actionCreate, "Create Team", UiTheme.ACCENT_BLUE, this.actionCreate.contains(mouseX, mouseY));
            this.renderActionButton(context, textRenderer, this.actionAssign, "Assign To Team", UiTheme.ACCENT_GREEN, this.actionAssign.contains(mouseX, mouseY));
            this.renderActionButton(context, textRenderer, this.actionRemove, "Remove From Team", UiTheme.ACCENT, this.actionRemove.contains(mouseX, mouseY));
            this.renderActionButton(context, textRenderer, this.actionDelete, "Delete Team", UiTheme.ACCENT_RED, this.actionDelete.contains(mouseX, mouseY));
            this.renderActionButton(context, textRenderer, this.actionSolo, "Solo All", UiTheme.ACCENT_GREEN, this.actionSolo.contains(mouseX, mouseY));
        } else if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, "Match Rules", UiTheme.ACCENT_BLUE);
        }
    }

    @Override
    protected java.util.List<Text> getSummaryLines() {
        return java.util.List.of(
            Text.literal("Teams: " + this.teamGrid.getTeams().size()),
            Text.literal("Seed: " + this.seedMode.label)
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("rules")) {
            this.drawLabel(context, textRenderer, "Seed Mode", this.layout.mainPanel().x() + 38, this.layout.mainPanel().y() + 102);
            this.drawLabel(context, textRenderer, "Fixed Seed", this.layout.mainPanel().x() + 38, this.layout.mainPanel().y() + 134);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        
        if (this.moduleManager.isActive("rules") && this.seedMode == SeedMode.RANDOM && this.seedValueField != null) {
            if (this.seedValueField.isMouseOver(mouseX, mouseY)) {
                this.status = ValidationResult.error("Seed mode is random, change to Fixed to enter your own world seed.");
                return true;
            }
        }
        
        return this.gamemodeMouseClicked(mouseX, mouseY, button);
    }

    protected boolean gamemodeMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.moduleManager.isActive("teams")) return false;
        
        if (this.actionCreate != null && this.actionCreate.contains(mouseX, mouseY)) {
            this.teamGrid.createTeam();
            this.status = ValidationResult.success("Created " + this.teamGrid.fallbackTeamLabel(this.teamGrid.getTeams().size() - 1) + ".");
            return true;
        }
        if (this.actionAssign != null && this.actionAssign.contains(mouseX, mouseY)) {
            if (this.teamGrid.assignSelectedPlayersToTeam()) {
                TeamDraft team = this.teamGrid.getSelectedTeam();
                this.status = ValidationResult.success("Assigned player(s) to " + (team != null ? (team.label().isBlank() ? "selected team" : team.label()) : "team") + ".");
            } else {
                this.status = ValidationResult.error("Select a team and one or more players first.");
            }
            return true;
        }
        if (this.actionRemove != null && this.actionRemove.contains(mouseX, mouseY)) {
            if (this.teamGrid.removeSelectedPlayersFromTeams()) {
                this.status = ValidationResult.success("Removed player(s) from teams.");
            } else {
                this.status = ValidationResult.error("Selected players are not assigned.");
            }
            return true;
        }
        if (this.actionDelete != null && this.actionDelete.contains(mouseX, mouseY)) {
            if (this.teamGrid.deleteSelectedTeam()) {
                this.status = ValidationResult.success("Deleted team.");
            } else {
                this.status = ValidationResult.error("Select a team first.");
            }
            return true;
        }
        if (this.actionSolo != null && this.actionSolo.contains(mouseX, mouseY)) {
            if (this.teamGrid.soloAll()) {
                this.status = ValidationResult.success("Created one solo team per player.");
            } else {
                this.status = ValidationResult.error("No players to split into solo teams.");
            }
            return true;
        }
        return false;
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            if (this.seedValueField != null && this.seedMode == SeedMode.FIXED) {
                this.seedValue = this.seedValueField.getText().trim();
            }
        }
    }

    @Override
    public String title() { return "Speedrun Setup"; }

    @Override
    public String subtitle() { return "Workspace-based team draft"; }

    @Override
    public String gameId() { return SpeedrunDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        int exportedTeams = 0;
        for (TeamDraft team : this.teamGrid.getTeams()) {
            if (!team.isEmpty()) exportedTeams++;
        }
        if (exportedTeams == 0) {
            return ValidationResult.error("Create at least one team with one player.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        this.syncStateFromWidgets();
        builder.settings().putString("seedMode", this.seedMode.nbtValue);
        if (this.seedMode == SeedMode.FIXED) {
            long parsedSeed;
            if (this.seedValue.isEmpty()) {
                parsedSeed = System.currentTimeMillis();
            } else {
                try {
                    parsedSeed = Long.parseLong(this.seedValue);
                } catch (NumberFormatException e) {
                    parsedSeed = (long) this.seedValue.hashCode();
                }
            }
            builder.settings().putLong("seed", parsedSeed);
        }
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        for (int i = 0; i < this.teamGrid.getTeams().size(); i++) {
            TeamDraft team = this.teamGrid.getTeams().get(i);
            if (!team.isEmpty()) {
                java.util.List<SessionSnapshotData.RosterEntry> entries = new java.util.ArrayList<>();
                for (TeamDraft.Member m : team.members()) {
                    entries.add(new SessionSnapshotData.RosterEntry(m.uuid().toString(), m.name()));
                }
                builder.addGroup(team.label().isBlank() ? this.teamGrid.fallbackTeamLabel(i) : team.label(), this.teamGrid.fallbackTeamLabel(i), entries);
            }
        }
    }

    private enum SeedMode {
        RANDOM("Random", "random"),
        FIXED("Fixed", "fixed");

        private final String label;
        private final String nbtValue;

        SeedMode(String label, String nbtValue) {
            this.label = label;
            this.nbtValue = nbtValue;
        }

        private SeedMode next() {
            return this == RANDOM ? FIXED : RANDOM;
        }
    }
}
