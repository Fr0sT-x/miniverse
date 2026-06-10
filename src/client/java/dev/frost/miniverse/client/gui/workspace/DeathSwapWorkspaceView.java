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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public final class DeathSwapWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private TextFieldWidget swapIntervalField;
    private TextFieldWidget gracePeriodField;
    private TextFieldWidget borderSizeField;
    private TextFieldWidget seedValueField;
    private ButtonWidget seedModeButton;
    private ButtonWidget keepInventoryButton;
    private ButtonWidget preserveVelocityButton;

    private int swapIntervalSeconds = 300;
    private int gracePeriodSeconds = 30;
    private int borderSize = 3000;
    private DeathSwapSettings.SeedMode seedMode = DeathSwapSettings.SeedMode.RANDOM;
    private long seedValue = System.currentTimeMillis();
    private boolean keepInventory = true;
    private boolean preserveVelocity = true;

    public DeathSwapWorkspaceView() {
        super("deathswap");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure swap timing and match rules.", UiTheme.ACCENT_BLUE);
        this.moduleManager.register("summary", "U", "Summary", "Summary", "Review and launch the match.", UiTheme.ACCENT_RED);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.swapIntervalField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 96, Integer.toString(this.swapIntervalSeconds), "Swap interval seconds");
            this.gracePeriodField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 128, Integer.toString(this.gracePeriodSeconds), "Grace period seconds");
            this.borderSizeField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 160, Integer.toString(this.borderSize), "Border size");
            this.seedModeButton = this.addButton(screen, seedModeLabel(), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 192, 170, () -> {
                this.seedMode = this.seedMode == DeathSwapSettings.SeedMode.RANDOM ? DeathSwapSettings.SeedMode.FIXED : DeathSwapSettings.SeedMode.RANDOM;
                this.seedModeButton.setMessage(Text.literal(seedModeLabel()));
            });
            this.seedValueField = this.addField(screen, this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 224, Long.toString(this.seedValue), 170, "Seed value");
            this.keepInventoryButton = this.addButton(screen, toggleLabel("Keep Inventory", this.keepInventory), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 256, 170, () -> {
                this.keepInventory = !this.keepInventory;
                this.keepInventoryButton.setMessage(Text.literal(toggleLabel("Keep Inventory", this.keepInventory)));
            });
            this.preserveVelocityButton = this.addButton(screen, toggleLabel("Preserve Velocity", this.preserveVelocity), this.layout.mainPanel().x() + 180, this.layout.mainPanel().y() + 288, 190, () -> {
                this.preserveVelocity = !this.preserveVelocity;
                this.preserveVelocityButton.setMessage(Text.literal(toggleLabel("Preserve Velocity", this.preserveVelocity)));
            });
        }
    }

    @Override
    protected void renderGamemodeBackground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
        if (this.moduleManager.isActive("summary")) {
            this.syncStateFromWidgets();
            this.renderSettingsModulePanel(context, textRenderer, "Summary", UiTheme.ACCENT_RED);
            int x = this.layout.mainPanel().x() + 14;
            int y = this.layout.mainPanel().y() + 72;
            int line = y + 18;
            context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
            line += 20;
            context.drawText(textRenderer, Text.literal("Swap Interval: " + this.swapIntervalSeconds + "s"), x + 14, line, UiTheme.TEXT, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Grace: " + this.gracePeriodSeconds + "s"), x + 14, line, UiTheme.TEXT, false);
            line += 18;
            context.drawText(textRenderer, Text.literal("Seed: " + this.seedMode.nbtValue()), x + 14, line, UiTheme.TEXT, false);
        } else if (this.moduleManager.isActive("rules")) {
            this.renderSettingsModulePanel(context, textRenderer, this.moduleManager.getActiveModule().label(), this.moduleManager.getActiveModule().accent());
        }
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
            context.drawText(textRenderer, Text.literal("Keep Inventory"), labelX, labelY + 160, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Preserve Velocity"), labelX, labelY + 192, UiTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public void setActiveModule(String moduleId) {
        this.syncStateFromWidgets();
        super.setActiveModule(moduleId);
    }

    private void syncStateFromWidgets() {
        if (this.moduleManager.isActive("rules")) {
            this.swapIntervalSeconds = readClamped(this.swapIntervalField, this.swapIntervalSeconds, 10, 3600);
            this.gracePeriodSeconds = readClamped(this.gracePeriodField, this.gracePeriodSeconds, 0, 3600);
            this.borderSize = readClamped(this.borderSizeField, this.borderSize, 100, 30000);
            if (this.seedValueField != null) {
                try {
                    this.seedValue = Long.parseLong(this.seedValueField.getText().trim());
                } catch (NumberFormatException ignored) {
                    this.seedValueField.setText(Long.toString(this.seedValue));
                }
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
        builder.settings().putBoolean("keepInventory", this.keepInventory);
        builder.settings().putBoolean("preserveVelocity", this.preserveVelocity);
        builder.settings().putString("seedMode", this.seedMode.nbtValue());
        builder.settings().putLong("seed", this.seedMode == DeathSwapSettings.SeedMode.FIXED ? this.seedValue : System.currentTimeMillis());
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
