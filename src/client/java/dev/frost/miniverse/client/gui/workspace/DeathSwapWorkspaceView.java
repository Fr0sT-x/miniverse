package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapDefinition;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapSettings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class DeathSwapWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget swapIntervalField;
    private IntFieldWidget gracePeriodField;
    private IntFieldWidget borderSizeField;
    private TextFieldWidget seedValueField;
    private ButtonWidget seedModeButton;
    private ButtonWidget preserveVelocityButton;

    private int swapIntervalSeconds = 300;
    private int gracePeriodSeconds = 30;
    private int borderSize = 3000;
    private DeathSwapSettings.SeedMode seedMode = DeathSwapSettings.SeedMode.RANDOM;
    private String seedValue = "";
    private boolean preserveVelocity = true;

    @Override
    protected dev.frost.miniverse.minigame.core.rules.GlobalMatchRules defaultMatchRules() {
        return new dev.frost.miniverse.minigame.core.rules.GlobalMatchRules(true, false, true, true, true, true, true, false);
    }

    public DeathSwapWorkspaceView() {
        super("deathswap");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure swap timing and match rules.", UiTheme.ACCENT_BLUE);
        this.useGameRules();
        this.moduleManager.register("summary", "U", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT_RED);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.swapIntervalField = this.addIntField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, this.swapIntervalSeconds, "Swap interval seconds", val -> "Players will swap positions every " + val + " seconds.");
            this.gracePeriodField = this.addIntField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, this.gracePeriodSeconds, "Grace period seconds",
                "No grace period, swapping begins immediately.",
                val -> "Players have " + val + " seconds of peace before swapping begins.");
            this.borderSizeField = this.addIntField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, this.borderSize, "Border size", val -> "Size of the world border in blocks.");
            this.seedModeButton = this.addCycleButton(screen, () -> seedModeLabel(), () -> this.seedMode.ordinal(), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 192, 170, new String[]{
                "Random world seed will be used.",
                "Specify an exact world seed in the text field."
            }, 2, () -> {
                this.seedMode = this.seedMode == DeathSwapSettings.SeedMode.RANDOM ? DeathSwapSettings.SeedMode.FIXED : DeathSwapSettings.SeedMode.RANDOM;
                this.seedModeButton.setMessage(Text.literal(seedModeLabel()));
                if (this.seedMode == DeathSwapSettings.SeedMode.RANDOM) {
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
            this.seedValueField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 224, this.seedMode == DeathSwapSettings.SeedMode.FIXED ? this.seedValue : "", 170, "Seed value", () -> "The exact world seed to use.");
            if (this.seedMode == DeathSwapSettings.SeedMode.RANDOM) {
                this.seedValueField.setEditable(false);
                this.seedValueField.active = false;
                this.seedValueField.setSuggestion("Enter world seed");
            } else {
                this.seedValueField.setEditable(true);
                this.seedValueField.active = true;
                this.seedValueField.setSuggestion("");
            }
            this.preserveVelocityButton = this.addToggleButton(screen, "Preserve Velocity", () -> this.preserveVelocity, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 256, 190,
                new dev.frost.miniverse.client.gui.workspace.framework.BinaryTooltip("Players keep their momentum when teleported.", "Players lose their momentum when teleported."),
                () -> this.preserveVelocity = !this.preserveVelocity);
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
            Text.literal("Swap Interval: " + this.swapIntervalSeconds + "s"),
            Text.literal("Grace: " + this.gracePeriodSeconds + "s"),
            Text.literal("Seed: " + this.seedMode.nbtValue())
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        int labelX = this.layout.mainPanel().x() + 38;
        int labelY = this.layout.mainPanel().y() + 102;
        if (this.moduleManager.isActive("rules")) {
            context.drawText(textRenderer, Text.literal("Swap Interval"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Grace Period"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Border Size"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Seed Mode"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Seed Value"), labelX, labelY + 128, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Preserve Velocity"), labelX, labelY + 160, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        
        if (this.moduleManager.isActive("rules") && this.seedMode == DeathSwapSettings.SeedMode.RANDOM && this.seedValueField != null) {
            if (this.seedValueField.isMouseOver(mouseX, mouseY)) {
                this.status = ValidationResult.error("Seed mode is random, change to Fixed to enter your own world seed.");
                return true;
            }
        }
        
        return false;
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    protected void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.swapIntervalSeconds = readClamped(this.swapIntervalField, this.swapIntervalSeconds, 10, 3600);
            this.gracePeriodSeconds = readClamped(this.gracePeriodField, this.gracePeriodSeconds, 0, 3600);
            this.borderSize = readClamped(this.borderSizeField, this.borderSize, 100, 30000);
            if (this.seedValueField != null && this.seedMode == DeathSwapSettings.SeedMode.FIXED) {
                this.seedValue = this.seedValueField.getText().trim();
            }
        }
    }

    @Override
    public String title() {
        return "Death Swap Setup";
    }

    @Override
    public String subtitle() {
        return "Workspace-based roster selection";
    }

    @Override
    public String gameId() {
        return DeathSwapDefinition.ID;
    }

    @Override
    protected ValidationResult validateGamemodeStart() {
        this.syncStateFromWidgets();
        if (this.playerGrid.getMembers("selected").size() < 2) {
            return ValidationResult.error("Select at least two players.");
        }
        return ValidationResult.success("");
    }

    @Override
    protected void buildSessionSettings(SessionPayloadBuilder builder) {
        builder.settings().putInt("swapIntervalSeconds", this.swapIntervalSeconds);
        builder.settings().putInt("initialGracePeriodSeconds", this.gracePeriodSeconds);
        builder.settings().putInt("borderSize", this.borderSize);
        builder.settings().putBoolean("preserveVelocity", this.preserveVelocity);
        builder.settings().putString("seedMode", this.seedMode.nbtValue());
        if (this.seedMode == DeathSwapSettings.SeedMode.FIXED) {
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
        builder.addGroup("players", "Players", this.playerGrid.getMembers("selected"));
    }

    private String seedModeLabel() {
        return this.seedMode == DeathSwapSettings.SeedMode.RANDOM ? "Seed: Random" : "Seed: Fixed";
    }

    private static String toggleLabel(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }
}
