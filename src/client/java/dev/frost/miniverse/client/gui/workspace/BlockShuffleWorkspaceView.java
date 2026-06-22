package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.providers.BlockRegistryProvider;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.blockshuffle.BlockShuffleDefinition;
import dev.frost.miniverse.minigame.impl.blockshuffle.BlockShuffleWeights;
import net.minecraft.block.Block;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Set;
import java.util.stream.Collectors;

public final class BlockShuffleWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget roundDurationField;
    private IntFieldWidget pointsToWinField;
    private ButtonWidget perPlayerButton;
    private ButtonWidget blockPoolButton;

    private static int lastRoundDurationSeconds = 300;
    private static int lastPointsToWin = 5;
    private static boolean lastPerPlayerBlocks = true;
    private static Set<Identifier> lastBlockPool = new java.util.HashSet<>(BlockShuffleWeights.STANDARD_POOL);

    private int roundDurationSeconds = lastRoundDurationSeconds;
    private int pointsToWin = lastPointsToWin;
    private boolean perPlayerBlocks = lastPerPlayerBlocks;
    private Set<Identifier> blockPool = new java.util.HashSet<>(lastBlockPool);
    private RegistrySelectorState selectorState = new RegistrySelectorState();
    private int timeLimitSeconds = 3600;

    @Override
    protected dev.frost.miniverse.minigame.core.rules.GlobalMatchRules defaultMatchRules() {
        return dev.frost.miniverse.minigame.core.rules.GlobalMatchRules.defaults();
    }

    public BlockShuffleWorkspaceView() {
        super("blockshuffle");
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
            this.pointsToWinField = this.addIntField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, this.pointsToWin, 160, "Points to Win", val -> "Score needed to win the match.");
            this.roundDurationField = this.addIntField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, this.roundDurationSeconds, 160, "Round Duration (s)", val -> "Players have " + val + " seconds to find their block.");
            
            this.perPlayerButton = this.addToggleButton(screen, "Per-Player Blocks", () -> this.perPlayerBlocks, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, 220,
                new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("Each player gets a different block.", "All players hunt the same block."),
                () -> this.perPlayerBlocks = !this.perPlayerBlocks);
            
            this.blockPoolButton = this.addActionButton(screen, "Configure Block Pool (" + this.blockPool.size() + " blocks)", this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 192, 220, "Click to select which blocks can be chosen during the match.", () -> {
                Set<Block> initialSelection = this.blockPool.stream()
                    .map(Registries.BLOCK::get)
                    .collect(Collectors.toSet());
                    
                RegistrySelectorContext<Block> context = new RegistrySelectorContext<>(
                    "minecraft:block",
                    "Select Block Pool",
                    RegistrySelectorContext.SelectionMode.MULTI,
                    this.selectorState,
                    result -> {
                        this.blockPool = result.selectedEntries().stream()
                            .map(Registries.BLOCK::getId)
                            .collect(Collectors.toSet());
                        lastBlockPool = new java.util.HashSet<>(this.blockPool);
                    },
                    "blockshuffle",
                    initialSelection
                );
                
                this.client.setScreen(new RegistrySelectorScreen<>(context, new BlockRegistryProvider()));
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
            Text.literal("Per-Player Blocks: " + (this.perPlayerBlocks ? "Yes" : "No")),
            Text.literal("Block Pool Size: " + this.blockPool.size() + " blocks")
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
            context.drawText(textRenderer, Text.literal("Block Pool"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
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
            lastPerPlayerBlocks = this.perPlayerBlocks;
            lastBlockPool = new java.util.HashSet<>(this.blockPool);
        }
    }

    @Override
    public String title() { return "Block Shuffle Setup"; }

    @Override
    public String subtitle() { return "Workspace-based draft and rules"; }

    @Override
    public String gameId() { return BlockShuffleDefinition.ID; }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (SessionSnapshotData.roster().isEmpty()) {
            return ValidationResult.error("No players online.");
        }
        if (this.blockPool.isEmpty()) {
            return ValidationResult.error("Block pool cannot be empty.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putInt("roundDurationSeconds", this.roundDurationSeconds);
        builder.settings().putInt("pointsToWin", this.pointsToWin);
        builder.settings().putBoolean("perPlayerBlocks", this.perPlayerBlocks);
        NbtList blockList = new NbtList();
        for (Identifier id : this.blockPool) {
            blockList.add(NbtString.of(id.toString()));
        }
        builder.settings().put("blockPool", blockList);
    }

    @Override
    protected void buildSessionGroups(SessionPayloadBuilder builder) {
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }
}
