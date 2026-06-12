package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.TeamDraft;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.providers.ItemRegistryProvider;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.DynamicTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.StandardWorkspaceLayout;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintDefinition;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ResourceSprintWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final DynamicTeamSelectionGrid teamGrid = new DynamicTeamSelectionGrid();

    private UiLayout.Rect actionCreate;
    private UiLayout.Rect actionAssign;
    private UiLayout.Rect actionRemove;
    private UiLayout.Rect actionDelete;
    private UiLayout.Rect actionSolo;

    private TextFieldWidget timeLimitField;
    private ButtonWidget modeButton;
    private ButtonWidget distributionButton;
    private ButtonWidget tieBreakButton;

    private ResourceSprintSettings.Mode mode = ResourceSprintSettings.Mode.FIRST_TO_COMPLETE;
    private ResourceSprintSettings.ObjectiveDistributionMode distributionMode = ResourceSprintSettings.ObjectiveDistributionMode.SHARED;
    private ResourceSprintSettings.TieBreakRule tieBreakRule = ResourceSprintSettings.TieBreakRule.SUDDEN_DEATH;
    private int timeLimitSeconds = 3600;
    private Set<String> objectivesPool = new LinkedHashSet<>();
    private int amount = 64;

    public ResourceSprintWorkspaceView() {
        super("resourcesprint");
        this.useRosterGrid(this.teamGrid, "teams", "T", "Teams", "Setup", "Draft and assign teams.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure scoring and win rules.", UiTheme.ACCENT_BLUE);
        this.useGameRules();
        this.moduleManager.register("summary", "S", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT_BLUE);
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
            this.modeButton = this.addButton(screen, "Mode: " + titleCase(this.mode.nbtValue()), this.layout.mainPanel().x() + 180, y, 190, () -> {
                this.mode = this.mode == ResourceSprintSettings.Mode.FIRST_TO_COMPLETE ? ResourceSprintSettings.Mode.TIME_LIMITED : ResourceSprintSettings.Mode.FIRST_TO_COMPLETE;
                this.modeButton.setMessage(Text.literal("Mode: " + titleCase(this.mode.nbtValue())));
            });
            y += 32;
            this.timeLimitField = this.addField(screen, this.layout.mainPanel().x() + 180, y, Integer.toString(this.timeLimitSeconds), 190, "Time limit seconds");
            y += 32;
            this.tieBreakButton = this.addButton(screen, "Tie-Break: " + titleCase(this.tieBreakRule.nbtValue()), this.layout.mainPanel().x() + 180, y, 190, () -> {
                this.tieBreakRule = this.tieBreakRule == ResourceSprintSettings.TieBreakRule.SUDDEN_DEATH ? ResourceSprintSettings.TieBreakRule.FASTEST_TOTAL_TIME : ResourceSprintSettings.TieBreakRule.SUDDEN_DEATH;
                this.tieBreakButton.setMessage(Text.literal("Tie-Break: " + titleCase(this.tieBreakRule.nbtValue())));
            });
            y += 32;
            this.distributionButton = this.addButton(screen, "Distribution: " + shortDistribution(this.distributionMode), this.layout.mainPanel().x() + 180, y, 190, () -> {
                this.distributionMode = this.distributionMode.next();
                this.distributionButton.setMessage(Text.literal("Distribution: " + shortDistribution(this.distributionMode)));
            });
            y += 32;
            this.addButton(screen, "Configure Objectives", this.layout.mainPanel().x() + 180, y, 190, () -> {
                this.syncStateFromWidgets();
                RegistrySelectorContext<net.minecraft.item.Item> selectorContext = new RegistrySelectorContext<>(
                    "minecraft:item",
                    "Select Objectives",
                    RegistrySelectorContext.SelectionMode.MULTI,
                    new RegistrySelectorState(),
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
                MinecraftClient.getInstance().setScreen(new RegistrySelectorScreen<>(selectorContext, new ItemRegistryProvider()));
            });
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
            Text.literal("Mode: " + titleCase(this.mode.nbtValue())),
            Text.literal("Time Limit: " + this.timeLimitSeconds + "s"),
            Text.literal("Distribution: " + shortDistribution(this.distributionMode))
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("rules")) {
            int labelX = this.layout.mainPanel().x() + 38;
            int labelY = this.layout.mainPanel().y() + 102;
            context.drawText(textRenderer, Text.literal("Mode"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Time Limit"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Tie Break"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Distribution"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Objectives"), labelX, labelY + 128, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
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
        if (this.timeLimitField != null) {
            this.timeLimitSeconds = this.readClamped(this.timeLimitField, this.timeLimitSeconds, 60, 7200);
        }
    }

    @Override
    public String title() { return "Resource Sprint Setup"; }

    @Override
    public String subtitle() { return "Workspace-based draft and rules"; }

    @Override
    public String gameId() { return ResourceSprintDefinition.ID; }

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
        builder.settings().putString("mode", this.mode.nbtValue());
        builder.settings().putInt("timeLimitSeconds", this.timeLimitSeconds);
        builder.settings().putString("tieBreakRule", this.tieBreakRule.nbtValue());
        builder.settings().putString("objectiveDistributionMode", this.distributionMode.nbtValue());

        List<ResourceSprintSettings.ObjectiveEntry> objectives = this.parseObjectives();
        NbtList objectiveList = new NbtList();
        for (ResourceSprintSettings.ObjectiveEntry entry : objectives) {
            NbtCompound objective = new NbtCompound();
            objective.putString("id", entry.id());
            objective.putString("difficulty", entry.difficulty().nbtValue());
            objective.putDouble("probability", entry.probability());
            objectiveList.add(objective);
        }
        builder.settings().put("objectives", objectiveList);
        builder.settings().putInt("objectiveCount", objectives.size());
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
}
