package dev.frost.miniverse.client.gui.workspace;

import java.util.Set;
import java.util.stream.Collectors;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.providers.DeathObjectiveRegistryProvider;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
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
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class DeathShuffleWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget roundDurationField;
    private IntFieldWidget pointsToWinField;
    private IntFieldWidget respawnDelayField;
    private ButtonWidget perPlayerButton;
    private ButtonWidget blockPoolButton;

    private static int lastRoundDurationSeconds = 300;
    private static int lastPointsToWin = 5;
    private static int lastRespawnDelaySeconds = 5;
    private static boolean lastPerPlayerObjectives = true;
    private static Set<Identifier> lastBlockPool = new java.util.HashSet<>();

    private int roundDurationSeconds = lastRoundDurationSeconds;
    private int pointsToWin = lastPointsToWin;
    private int respawnDelaySeconds = lastRespawnDelaySeconds;
    private boolean perPlayerObjectives = lastPerPlayerObjectives;
    private Set<Identifier> blockPool = new java.util.HashSet<>(lastBlockPool);
    private RegistrySelectorState selectorState = new RegistrySelectorState();
    private int timeLimitSeconds = 3600;

    public DeathShuffleWorkspaceView() {
        super("deathshuffle");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure scoring and win rules.", UiTheme.ACCENT_BLUE);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            this.rulesLayout.addRow(
                "Points to Win", (s, x, y, w) -> {
                    this.pointsToWinField = this.addIntField(s, x, y, this.pointsToWin, w, "Points to Win", val -> "Score needed to win the match.");
                }
            );

            this.rulesLayout.addRow(
                "Round Duration", (s, x, y, w) -> {
                    this.roundDurationField = this.addIntField(s, x, y, this.roundDurationSeconds, w, "Round Duration (s)", val -> "Players have " + val + " seconds to complete their death objective.");
                }
            );

            this.rulesLayout.addRow(
                "Player Assignment", (s, x, y, w) -> {
                    this.perPlayerButton = this.addToggleButton(s, "Per-Player DeathObjectives", () -> this.perPlayerObjectives, x, y, w,
                        new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("Each player gets a different objective.", "All players get the same objective."),
                        () -> this.perPlayerObjectives = !this.perPlayerObjectives);
                }
            );

            this.rulesLayout.addRow(
                "DeathObjective Pool", (s, x, y, w) -> {
                    this.blockPoolButton = this.addActionButton(s, "Configure DeathObjective Pool (" + this.blockPool.size() + " objectives)", x, y, w, "Click to configure the pool of possible death objectives.", () -> {
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
            );

            this.rulesLayout.addRow(
                "Respawn Delay", (s, x, y, w) -> {
                    this.respawnDelayField = this.addIntField(s, x, y, this.respawnDelaySeconds, w, "Respawn Delay (s)", val -> "Delay before players respawn.");
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
    protected java.util.List<Text> getSummaryLines() {
        return java.util.List.of(
            Text.literal("Points to Win: " + this.pointsToWin),
            Text.literal("Round Duration: " + this.roundDurationSeconds + "s"),
            Text.literal("Per-Player DeathObjectives: " + (this.perPlayerObjectives ? "Yes" : "No")),
            Text.literal("Respawn Delay: " + this.respawnDelaySeconds + "s"),
            Text.literal("DeathObjective Pool Size: " + this.blockPool.size() + " objectives")
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
            this.pointsToWin = readClamped(this.pointsToWinField, this.pointsToWin, 1, 100);
            this.roundDurationSeconds = readClamped(this.roundDurationField, this.roundDurationSeconds, 10, 3600);
            this.respawnDelaySeconds = readClamped(this.respawnDelayField, this.respawnDelaySeconds, 0, 60);
            lastRoundDurationSeconds = this.roundDurationSeconds;
            lastPointsToWin = this.pointsToWin;
            lastRespawnDelaySeconds = this.respawnDelaySeconds;
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
        if (this.playerGrid.getMembers("selected").isEmpty()) {
            return ValidationResult.error("Select at least one player to participate.");
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
        builder.settings().putInt("respawnDelaySeconds", this.respawnDelaySeconds);
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

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("roundDurationSeconds")) this.roundDurationSeconds = settings.getInt("roundDurationSeconds");
        if (settings.contains("pointsToWin")) this.pointsToWin = settings.getInt("pointsToWin");
        if (settings.contains("respawnDelaySeconds")) this.respawnDelaySeconds = settings.getInt("respawnDelaySeconds");
        if (settings.contains("perPlayerObjectives")) this.perPlayerObjectives = settings.getBoolean("perPlayerObjectives");
        if (settings.contains("activeObjectivePool", net.minecraft.nbt.NbtElement.LIST_TYPE)) {
            NbtList list = settings.getList("activeObjectivePool", net.minecraft.nbt.NbtElement.STRING_TYPE);
            Set<Identifier> restored = new java.util.HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                try {
                    restored.add(Identifier.of(list.getString(i)));
                } catch (Exception ignored) {}
            }
            if (!restored.isEmpty()) {
                this.blockPool = restored;
            }
        }
        if (this.roundDurationField != null) this.roundDurationField.setText(String.valueOf(this.roundDurationSeconds));
        if (this.pointsToWinField != null) this.pointsToWinField.setText(String.valueOf(this.pointsToWin));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelaySeconds));
    }

    @Override
    protected void resetToDefaultSettings() {
        this.roundDurationSeconds = 300;
        this.pointsToWin = 5;
        this.respawnDelaySeconds = 0;
        this.perPlayerObjectives = true;
        if (this.client.world != null) {
            Registry<DeathObjective> reg = this.client.world.getRegistryManager().get(DeathObjective.REGISTRY_KEY);
            if (reg != null && !reg.getIds().isEmpty()) {
                this.blockPool = new java.util.HashSet<>(reg.getIds());
            } else if (!lastBlockPool.isEmpty()) {
                this.blockPool = new java.util.HashSet<>(lastBlockPool);
            }
        } else if (!lastBlockPool.isEmpty()) {
            this.blockPool = new java.util.HashSet<>(lastBlockPool);
        }
        if (this.roundDurationField != null) this.roundDurationField.setText(String.valueOf(this.roundDurationSeconds));
        if (this.pointsToWinField != null) this.pointsToWinField.setText(String.valueOf(this.pointsToWin));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelaySeconds));
    }
}
