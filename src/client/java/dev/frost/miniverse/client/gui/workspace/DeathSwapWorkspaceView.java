package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.IntFieldWidget;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import dev.frost.miniverse.client.gui.workspace.framework.AbstractGamemodeWorkspaceView;
import dev.frost.miniverse.client.gui.workspace.framework.SessionPayloadBuilder;
import dev.frost.miniverse.client.gui.workspace.framework.ValidationResult;
import dev.frost.miniverse.client.gui.workspace.framework.WorkspaceTooltip;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapDefinition;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapSettings;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class DeathSwapWorkspaceView extends AbstractGamemodeWorkspaceView {
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private IntFieldWidget swapIntervalField;
    private IntFieldWidget gracePeriodField;
    private IntFieldWidget borderSizeField;
    private IntFieldWidget respawnDelayField;
    private TextFieldWidget seedValueField;
    private ButtonWidget seedModeButton;
    private ButtonWidget preserveVelocityButton;

    private int swapIntervalSeconds = 300;
    private int gracePeriodSeconds = 30;
    private int borderSize = 3000;
    private int respawnDelaySeconds = 5;
    private DeathSwapSettings.SeedMode seedMode = DeathSwapSettings.SeedMode.RANDOM;
    private String seedValue = "";
    private boolean preserveVelocity = true;

    public DeathSwapWorkspaceView() {
        super("deathswap");
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
        this.useRosterGrid(this.playerGrid, "players", "P", "Players", "Setup", "Select participating players.", UiTheme.ACCENT);
        this.moduleManager.register("rules", "R", "Match Rules", "Rules", "Configure swap timing and match rules.", UiTheme.ACCENT_BLUE);
    }

    @Override
    protected void initGamemode(SessionScreen screen) {
        if (this.moduleManager.isActive("rules")) {
            this.rulesLayout = new SettingsLayoutBuilder(screen);

            WorkspaceTooltip swapTooltip = WorkspaceTooltip.dynamic(() -> "Players will swap positions every " + (this.swapIntervalField != null ? this.swapIntervalField.getIntValue(this.swapIntervalSeconds) : this.swapIntervalSeconds) + " seconds.");
            this.rulesLayout.addRow(
                "Swap Interval", swapTooltip, (s, x, y, w) -> {
                    this.swapIntervalField = this.addIntField(s, "Swap interval seconds", x, y, this.swapIntervalSeconds, 10, 3600, w, swapTooltip);
                }
            );

            WorkspaceTooltip graceTooltip = WorkspaceTooltip.dynamic(() -> {
                int val = this.gracePeriodField != null ? this.gracePeriodField.getIntValue(this.gracePeriodSeconds) : this.gracePeriodSeconds;
                return val <= 0 ? "No grace period, swapping begins immediately." : "Players have " + val + " seconds of peace before swapping begins.";
            });
            this.rulesLayout.addRow(
                "Grace Period", graceTooltip, (s, x, y, w) -> {
                    this.gracePeriodField = this.addIntField(s, "Grace period seconds", x, y, this.gracePeriodSeconds, 0, 3600, w, graceTooltip);
                }
            );

            WorkspaceTooltip borderTooltip = WorkspaceTooltip.dynamic(() -> "Size of the world border in blocks (" + (this.borderSizeField != null ? this.borderSizeField.getIntValue(this.borderSize) : this.borderSize) + ").");
            this.rulesLayout.addRow(
                "Border Size", borderTooltip, (s, x, y, w) -> {
                    this.borderSizeField = this.addIntField(s, "Border size", x, y, this.borderSize, 100, 30000, w, borderTooltip);
                }
            );

            WorkspaceTooltip seedModeTooltip = WorkspaceTooltip.cycle(() -> this.seedMode.ordinal(), new String[]{
                "Random world seed will be used.",
                "Specify an exact world seed in the text field."
            });
            this.rulesLayout.addRow(
                "Seed Mode", seedModeTooltip, (s, x, y, w) -> {
                    this.seedModeButton = this.addCycleButton(s, () -> seedModeLabel(), () -> this.seedMode.ordinal(), x, y, w, seedModeTooltip, 2, () -> {
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
                }
            );

            WorkspaceTooltip seedValTooltip = WorkspaceTooltip.of("The exact world seed to use.");
            this.rulesLayout.addRow(
                "Seed Value", seedValTooltip, (s, x, y, w) -> {
                    this.seedValueField = this.addField(s, x, y, this.seedMode == DeathSwapSettings.SeedMode.FIXED ? this.seedValue : "", w, "Seed value", () -> "The exact world seed to use.");
                    if (this.seedMode == DeathSwapSettings.SeedMode.RANDOM) {
                        this.seedValueField.setEditable(false);
                        this.seedValueField.active = false;
                        this.seedValueField.setSuggestion("Enter world seed");
                    } else {
                        this.seedValueField.setEditable(true);
                        this.seedValueField.active = true;
                        this.seedValueField.setSuggestion("");
                    }
                }
            );

            WorkspaceTooltip velocityTooltip = WorkspaceTooltip.toggle(() -> this.preserveVelocity,
                "Players keep their momentum when teleported.",
                "Players lose their momentum when teleported.");
            this.rulesLayout.addRow(
                "Preserve Velocity", velocityTooltip, (s, x, y, w) -> {
                    this.preserveVelocityButton = this.addToggleButton(s, "Preserve Velocity", () -> this.preserveVelocity, x, y, w, velocityTooltip,
                        () -> this.preserveVelocity = !this.preserveVelocity);
                }
            );

            WorkspaceTooltip respawnTooltip = WorkspaceTooltip.dynamic(() -> "Players wait " + (this.respawnDelayField != null ? this.respawnDelayField.getIntValue(this.respawnDelaySeconds) : this.respawnDelaySeconds) + " seconds before respawning.");
            this.rulesLayout.addRow(
                "Respawn Delay", respawnTooltip, (s, x, y, w) -> {
                    this.respawnDelayField = this.addIntField(s, "Respawn delay", x, y, this.respawnDelaySeconds, 0, 3600, w, respawnTooltip);
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
            Text.literal("Swap Interval: " + this.swapIntervalSeconds + "s"),
            Text.literal("Grace: " + this.gracePeriodSeconds + "s"),
            Text.literal("Seed: " + this.seedMode.nbtValue())
        );
    }

    @Override
    protected void renderGamemodeForeground(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY, float delta) {
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
            this.respawnDelaySeconds = readClamped(this.respawnDelayField, this.respawnDelaySeconds, 0, 3600);
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
        builder.settings().putInt("respawnDelaySeconds", this.respawnDelaySeconds);
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

    @Override
    protected void applyPresetSettings(net.minecraft.nbt.NbtCompound settings) {
        if (settings == null) return;
        super.applyPresetSettings(settings);
        if (settings.contains("swapIntervalSeconds")) this.swapIntervalSeconds = settings.getInt("swapIntervalSeconds");
        if (settings.contains("initialGracePeriodSeconds")) this.gracePeriodSeconds = settings.getInt("initialGracePeriodSeconds");
        if (settings.contains("borderSize")) this.borderSize = settings.getInt("borderSize");
        if (settings.contains("respawnDelaySeconds")) this.respawnDelaySeconds = settings.getInt("respawnDelaySeconds");
        if (settings.contains("preserveVelocity")) this.preserveVelocity = settings.getBoolean("preserveVelocity");
        if (settings.contains("seedMode")) {
            String modeStr = settings.getString("seedMode");
            this.seedMode = "fixed".equalsIgnoreCase(modeStr) ? DeathSwapSettings.SeedMode.FIXED : DeathSwapSettings.SeedMode.RANDOM;
        }
        if (settings.contains("seed")) {
            this.seedValue = String.valueOf(settings.getLong("seed"));
        }

        if (this.swapIntervalField != null) this.swapIntervalField.setText(String.valueOf(this.swapIntervalSeconds));
        if (this.gracePeriodField != null) this.gracePeriodField.setText(String.valueOf(this.gracePeriodSeconds));
        if (this.borderSizeField != null) this.borderSizeField.setText(String.valueOf(this.borderSize));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelaySeconds));
        if (this.seedValueField != null) this.seedValueField.setText(this.seedValue);
    }

    @Override
    protected void resetToDefaultSettings() {
        this.swapIntervalSeconds = 300;
        this.gracePeriodSeconds = 30;
        this.borderSize = 3000;
        this.respawnDelaySeconds = 5;
        this.preserveVelocity = true;
        this.seedMode = DeathSwapSettings.SeedMode.RANDOM;
        this.seedValue = "";

        if (this.swapIntervalField != null) this.swapIntervalField.setText(String.valueOf(this.swapIntervalSeconds));
        if (this.gracePeriodField != null) this.gracePeriodField.setText(String.valueOf(this.gracePeriodSeconds));
        if (this.borderSizeField != null) this.borderSizeField.setText(String.valueOf(this.borderSize));
        if (this.respawnDelayField != null) this.respawnDelayField.setText(String.valueOf(this.respawnDelaySeconds));
        if (this.seedValueField != null) this.seedValueField.setText("");
    }

    private String seedModeLabel() {
        return this.seedMode == DeathSwapSettings.SeedMode.RANDOM ? "Seed: Random" : "Seed: Fixed";
    }

    private static String toggleLabel(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }
}
