package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.TeamDraft;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.DynamicTeamSelectionGrid;
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
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final DynamicTeamSelectionGrid teamGrid = new DynamicTeamSelectionGrid();

    private Module activeModule = Module.TEAMS;
    private UiLayout.Rect workspace = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect teamsArea = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionCreate = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionAssign = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionRemove = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionDelete = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect actionSolo = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect startButton = new UiLayout.Rect(0, 0, 0, 0);
    private static final int BUTTON_HEIGHT = 22;

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

        this.teamGrid.setBounds(this.teamsArea);
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
            this.teamGrid.render(context, textRenderer, mouseX, mouseY, delta);
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
        } else if (this.activeModule == Module.TEAMS) {
            this.teamGrid.renderForeground(context, textRenderer, workspace, mouseX, mouseY, delta);
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
            this.teamGrid.createTeam();
            this.statusMessage = "Created " + this.teamGrid.fallbackTeamLabel(this.teamGrid.getTeams().size() - 1) + ".";
            return true;
        }
        if (this.actionAssign.contains(mouseX, mouseY)) {
            if (this.teamGrid.assignSelectedPlayersToTeam()) {
                TeamDraft team = this.teamGrid.getSelectedTeam();
                this.statusMessage = "Assigned player(s) to " + (team != null ? team.label().isBlank() ? "selected team" : team.label() : "team") + ".";
            } else {
                this.statusMessage = "Select a team and one or more players first.";
            }
            return true;
        }
        if (this.actionRemove.contains(mouseX, mouseY)) {
            if (this.teamGrid.removeSelectedPlayersFromTeams()) {
                this.statusMessage = "Removed player(s) from teams.";
            } else {
                this.statusMessage = "Selected players are not assigned.";
            }
            return true;
        }
        if (this.actionDelete.contains(mouseX, mouseY)) {
            if (this.teamGrid.deleteSelectedTeam()) {
                this.statusMessage = "Deleted team.";
            } else {
                this.statusMessage = "Select a team first.";
            }
            return true;
        }
        if (this.actionSolo.contains(mouseX, mouseY)) {
            if (this.teamGrid.soloAll()) {
                this.statusMessage = "Created one solo team per player.";
            } else {
                this.statusMessage = "No players to split into solo teams.";
            }
            return true;
        }
        
        return this.teamGrid.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.activeModule == Module.TEAMS) {
            return this.teamGrid.mouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.activeModule == Module.TEAMS) {
            return this.teamGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeModule != Module.TEAMS) {
            return false;
        }
        return this.teamGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
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
        this.teamGrid.refreshRoster();
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
        for (int i = 0; i < this.teamGrid.getTeams().size(); i++) {
            TeamDraft team = this.teamGrid.getTeams().get(i);
            if (team.isEmpty()) {
                continue;
            }
            groups.add(team.toPlanCompound(this.teamGrid.fallbackTeamLabel(i)));
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



    private static String shortDistribution(ResourceSprintSettings.ObjectiveDistributionMode mode) {
        return mode == ResourceSprintSettings.ObjectiveDistributionMode.SHARED ? "Shared" : "Probabilistic";
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
        field.setMaxLength(64);
        field.setText(value);
        return screen.addWorkspaceChild(field);
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



