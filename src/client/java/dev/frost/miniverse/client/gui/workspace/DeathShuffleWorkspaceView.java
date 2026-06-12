package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.providers.DeathObjectiveRegistryProvider;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.deathshuffle.DeathShuffleDefinition;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.stream.Collectors;

public final class DeathShuffleWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private TextFieldWidget roundDurationField;
    private TextFieldWidget pointsToWinField;
    private ButtonWidget perPlayerButton;
    private ButtonWidget blockPoolButton;

    private static int lastRoundDurationSeconds = 300;
    private static int lastPointsToWin = 5;
    private static boolean lastPerPlayerObjectives = true;
    private static Set<Identifier> lastBlockPool = new java.util.HashSet<>();

    private int roundDurationSeconds = lastRoundDurationSeconds;
    private int pointsToWin = lastPointsToWin;
    private boolean perPlayerObjectives = lastPerPlayerObjectives;
    private Set<Identifier> blockPool = new java.util.HashSet<>(lastBlockPool);
    private RegistrySelectorState selectorState = new RegistrySelectorState();
    private int timeLimitSeconds = 3600;

    @Override
    protected dev.frost.miniverse.minigame.core.rules.GlobalMatchRules defaultMatchRules() {
        return new dev.frost.miniverse.minigame.core.rules.GlobalMatchRules(true, false, true, true, true, true, true);
    }

    public DeathShuffleWorkspaceView() {
        super("deathshuffle");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure scoring and win rules.", UiTheme.ACCENT_BLUE);
        this.useGameRules();
        this.moduleManager.register("summary", "U", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT_BLUE);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.pointsToWinField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, Integer.toString(this.pointsToWin), 160, "Points to Win");
            this.roundDurationField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, Integer.toString(this.roundDurationSeconds), 160, "Round Duration (s)");
            
            this.perPlayerButton = this.addButton(screen, "Per-Player DeathObjectives: " + (this.perPlayerObjectives ? "ON" : "OFF"), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, 220, () -> {
                this.perPlayerObjectives = !this.perPlayerObjectives;
                this.perPlayerButton.setMessage(Text.literal("Per-Player DeathObjectives: " + (this.perPlayerObjectives ? "ON" : "OFF")));
            });
            
            this.blockPoolButton = this.addButton(screen, "Configure DeathObjective Pool (" + this.blockPool.size() + " objectives)", this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 192, 240, () -> {
                Registry<DeathObjective> registry = client.world.getRegistryManager().get(DeathObjective.REGISTRY_KEY);
                Set<DeathObjective> initialSelection = this.blockPool.stream()
                    .map(id -> DeathObjectiveManager.get(client.getServer(), id))
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
                    
                DeathObjectiveRegistryProvider provider = new DeathObjectiveRegistryProvider(registry);
                RegistrySelectorContext<DeathObjective> context = new RegistrySelectorContext<>(
                    "miniverse:death_objective",
                    "Select DeathObjective Pool",
                    RegistrySelectorContext.SelectionMode.MULTI,
                    this.selectorState,
                    newSelection -> {
                        this.blockPool = newSelection.selectedEntries().stream()
                            .map(provider::getId)
                            .filter(java.util.Objects::nonNull)
                            .collect(Collectors.toSet());
                        lastBlockPool = new java.util.HashSet<>(this.blockPool);
                    },
                    "deathshuffle",
                    initialSelection
                );
                
                client.setScreen(new RegistrySelectorScreen<>(context, provider));
            });
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
    }

    @Override
    protected java.util.List<Text> getSummaryLines() {
        return java.util.List.of(
            Text.literal("Points to Win: " + this.pointsToWin),
            Text.literal("Round Duration: " + this.roundDurationSeconds + "s"),
            Text.literal("Per-Player DeathObjectives: " + (this.perPlayerObjectives ? "Yes" : "No")),
            Text.literal("DeathObjective Pool Size: " + this.blockPool.size() + " objectives")
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int labelX = this.layout.mainPanel().x() + 38;
        int labelY = this.layout.mainPanel().y() + 102;
        if (this.moduleManager.isActive("rules")) {
            context.drawText(textRenderer, Text.literal("Points to Win"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Round Duration"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Player Assignment"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("DeathObjective Pool"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.pointsToWin = readClamped(this.pointsToWinField, this.pointsToWin, 1, 100);
            this.roundDurationSeconds = readClamped(this.roundDurationField, this.roundDurationSeconds, 10, 3600);
            lastRoundDurationSeconds = this.roundDurationSeconds;
            lastPointsToWin = this.pointsToWin;
            lastPerPlayerObjectives = this.perPlayerObjectives;
            lastBlockPool = new java.util.HashSet<>(this.blockPool);
        }
    }

    @Override
    public String title() { return "Death Shuffle Setup"; }

    @Override
    public String subtitle() { return "Workspace-based draft and rules"; }

    @Override
    public String gameId() { return DeathShuffleDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (SessionSnapshotData.roster().isEmpty()) {
            return ValidationResult.error("No players online.");
        }
        if (this.blockPool.isEmpty()) {
            return ValidationResult.error("DeathObjective pool cannot be empty.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putInt("roundDurationSeconds", this.roundDurationSeconds);
        builder.settings().putInt("pointsToWin", this.pointsToWin);
        builder.settings().putBoolean("perPlayerObjectives", this.perPlayerObjectives);
        NbtList blockList = new NbtList();
        for (Identifier id : this.blockPool) {
            blockList.add(NbtString.of(id.toString()));
        }
        builder.settings().put("activeObjectivePool", blockList);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }
}
