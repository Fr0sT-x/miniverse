package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapDefinition;
import dev.frost.miniverse.minigame.impl.deathswap.DeathSwapSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import dev.frost.miniverse.client.gui.workspace.components.StaticTeamSelectionGrid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DeathSwapWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider, GamemodeWorkspaceView.RosterRefreshable {
    private static final int ROW_HEIGHT = 20;
    private static final int COLUMN_HEADER_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 22;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Map<String, UiAnimation.Value> rowHovers = new HashMap<>();
    private final StaticTeamSelectionGrid playerGrid = new StaticTeamSelectionGrid();

    private Module activeModule = Module.PLAYERS;
    private UiLayout.Rect workspace = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect rosterArea = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect selectAllButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect clearButton = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect startButton = new UiLayout.Rect(0, 0, 0, 0);

    private TextFieldWidget swapIntervalField;
    private TextFieldWidget gracePeriodField;
    private TextFieldWidget borderSizeField;
    private TextFieldWidget seedValueField;
    private ButtonWidget seedModeButton;
    private ButtonWidget keepInventoryButton;
    private ButtonWidget preserveVelocityButton;

    private String sessionName = "deathswap-" + System.currentTimeMillis();
    private int swapIntervalSeconds = 300;
    private int gracePeriodSeconds = 30;
    private int borderSize = 3000;
    private DeathSwapSettings.SeedMode seedMode = DeathSwapSettings.SeedMode.RANDOM;
    private long seedValue = System.currentTimeMillis();
    private boolean keepInventory = true;
    private boolean preserveVelocity = true;

    private String statusMessage = "";

    public DeathSwapWorkspaceView() {
        this.playerGrid.addColumn("available", "Available", 0x7C8088, true);
        this.playerGrid.addColumn("selected", "Selected", UiTheme.ACCENT, false);
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.workspace = workspace;
        UiLayout.Rect mainPanel = workspace.inset(4);
        this.rosterArea = new UiLayout.Rect(mainPanel.x() + 12, mainPanel.y() + 88, mainPanel.width() - 24, mainPanel.height() - 116);
        this.playerGrid.setBounds(this.rosterArea);
        this.selectAllButton = new UiLayout.Rect(mainPanel.x() + 14, mainPanel.y() + 50, 90, BUTTON_HEIGHT);
        this.clearButton = new UiLayout.Rect(mainPanel.x() + 112, mainPanel.y() + 50, 70, BUTTON_HEIGHT);
        this.startButton = new UiLayout.Rect(mainPanel.x() + mainPanel.width() - 126, mainPanel.y() + 10, 112, BUTTON_HEIGHT);

        if (this.activeModule == Module.MATCH_RULES) {
            this.swapIntervalField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 96, Integer.toString(this.swapIntervalSeconds), 160, "Swap interval seconds");
            this.gracePeriodField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 128, Integer.toString(this.gracePeriodSeconds), 160, "Grace period seconds");
            this.borderSizeField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 160, Integer.toString(this.borderSize), 160, "Border size");
            this.seedModeButton = this.addButton(screen, seedModeLabel(), mainPanel.x() + 180, mainPanel.y() + 192, 170, () -> {
                this.seedMode = this.seedMode == DeathSwapSettings.SeedMode.RANDOM ? DeathSwapSettings.SeedMode.FIXED : DeathSwapSettings.SeedMode.RANDOM;
                this.seedModeButton.setMessage(Text.literal(seedModeLabel()));
            });
            this.seedValueField = this.addField(screen, mainPanel.x() + 180, mainPanel.y() + 224, Long.toString(this.seedValue), 170, "Seed value");
            this.keepInventoryButton = this.addButton(screen, toggleLabel("Keep Inventory", this.keepInventory), mainPanel.x() + 180, mainPanel.y() + 256, 170, () -> {
                this.keepInventory = !this.keepInventory;
                this.keepInventoryButton.setMessage(Text.literal(toggleLabel("Keep Inventory", this.keepInventory)));
            });
            this.preserveVelocityButton = this.addButton(screen, toggleLabel("Preserve Velocity", this.preserveVelocity), mainPanel.x() + 180, mainPanel.y() + 288, 190, () -> {
                this.preserveVelocity = !this.preserveVelocity;
                this.preserveVelocityButton.setMessage(Text.literal(toggleLabel("Preserve Velocity", this.preserveVelocity)));
            });
        }

    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(mainPanel.x() + 1, mainPanel.y() + 1, mainPanel.x() + mainPanel.width() - 1, mainPanel.y() + 40, 0x701B2634);
        context.drawText(textRenderer, Text.literal(this.activeModule.label), mainPanel.x() + 14, mainPanel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(this.activeModule.description), mainPanel.x() + 14, mainPanel.y() + 28, UiTheme.TEXT_DIM, false);

        if (this.activeModule == Module.PLAYERS) {
            this.renderPlayerActions(context, textRenderer, mouseX, mouseY);
            this.playerGrid.render(context, textRenderer, mouseX, mouseY, delta);
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
            context.drawText(textRenderer, Text.literal("Swap Interval"), labelX, labelY, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Grace Period"), labelX, labelY + 32, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Border Size"), labelX, labelY + 64, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Seed Mode"), labelX, labelY + 96, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Seed Value"), labelX, labelY + 128, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Keep Inventory"), labelX, labelY + 160, UiTheme.TEXT_MUTED, false);
            context.drawText(textRenderer, Text.literal("Preserve Velocity"), labelX, labelY + 192, UiTheme.TEXT_MUTED, false);
        } else if (this.activeModule == Module.PLAYERS) {
            this.playerGrid.renderForeground(context, textRenderer, workspace, mouseX, mouseY, delta);
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
        if (this.activeModule != Module.PLAYERS) {
            return false;
        }
        if (this.selectAllButton.contains(mouseX, mouseY)) {
            this.selectAll();
            return true;
        }
        if (this.clearButton.contains(mouseX, mouseY)) {
            this.clearSelection();
            return true;
        }
        if (this.playerGrid.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.activeModule == Module.PLAYERS && this.playerGrid.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.activeModule == Module.PLAYERS && this.playerGrid.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.activeModule != Module.PLAYERS) {
            return false;
        }
        int delta = (int) Math.signum(verticalAmount);
        if (delta == 0) {
            return false;
        }
        return this.playerGrid.mouseScrolled(mouseX, mouseY, verticalAmount);
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
    public List<WorkspaceModule> modules() {
        List<WorkspaceModule> modules = new ArrayList<>();
        for (Module module : Module.values()) {
            String group = module == Module.SUMMARY ? "Summary" : module == Module.PLAYERS ? "Setup" : "Rules";
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
        this.playerGrid.refreshRoster();
    }

    private void renderSettingsModule(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        int moduleX = mainPanel.x() + 14;
        int moduleY = mainPanel.y() + 72;
        int moduleWidth = Math.min(520, mainPanel.width() - 28);
        int moduleHeight = Math.min(250, mainPanel.height() - 104);
        UiRenderer.panel(context, moduleX, moduleY, moduleWidth, moduleHeight, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(moduleX, moduleY, moduleX + 3, moduleY + moduleHeight, this.activeModule.accent);
        context.drawText(textRenderer, Text.literal(this.activeModule.label), moduleX + 12, moduleY + 12, this.activeModule.accent, false);
    }

    private void renderSummary(DrawContext context, TextRenderer textRenderer, UiLayout.Rect mainPanel) {
        this.syncStateFromWidgets();
        int x = mainPanel.x() + 14;
        int y = mainPanel.y() + 72;
        int width = Math.min(520, mainPanel.width() - 28);
        UiRenderer.panel(context, x, y, width, 190, UiTheme.CARD, UiTheme.BORDER_SUBTLE);
        context.fill(x, y, x + 3, y + 190, UiTheme.ACCENT_RED);
        int line = y + 18;
        context.drawText(textRenderer, Text.literal("Session: " + this.sessionName), x + 14, line, UiTheme.TEXT, false);
        line += 20;
        context.drawText(textRenderer, Text.literal("Swap Interval: " + this.swapIntervalSeconds + "s"), x + 14, line, UiTheme.TEXT, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Grace: " + this.gracePeriodSeconds + "s"), x + 14, line, UiTheme.TEXT, false);
        line += 18;
        context.drawText(textRenderer, Text.literal("Seed: " + this.seedMode.nbtValue()), x + 14, line, UiTheme.TEXT, false);
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            context.drawText(textRenderer, Text.literal(validation), x + 14, y + 154, UiTheme.WARNING, false);
        }
    }

    private void renderPlayerActions(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
        this.renderActionButton(context, textRenderer, this.selectAllButton, "Select All", UiTheme.ACCENT_BLUE, this.selectAllButton.contains(mouseX, mouseY));
        this.renderActionButton(context, textRenderer, this.clearButton, "Clear", UiTheme.ACCENT, this.clearButton.contains(mouseX, mouseY));
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

    private void selectAll() {
        this.playerGrid.clear();
        for (SessionSnapshotData.RosterEntry entry : SessionSnapshotData.roster()) {
            this.playerGrid.addMember("selected", entry);
        }
        this.statusMessage = "Selected all players.";
    }

    private void clearSelection() {
        this.playerGrid.clear();
        this.statusMessage = "Selection cleared.";
    }

    private void createSession() {
        this.syncStateFromWidgets();
        String validation = this.getStartValidationMessage();
        if (!validation.isEmpty()) {
            this.statusMessage = validation;
            return;
        }
        NbtCompound plan = new NbtCompound();
        plan.putString("game", DeathSwapDefinition.ID);
        plan.putString("name", this.sessionName);
        plan.putBoolean("launch", true);
        plan.put("settings", this.buildSettingsCompound());

        net.minecraft.nbt.NbtList groups = new net.minecraft.nbt.NbtList();
        net.minecraft.nbt.NbtCompound group = new net.minecraft.nbt.NbtCompound();
        group.putString("id", "players");
        group.putString("name", "Players");
        net.minecraft.nbt.NbtList members = new net.minecraft.nbt.NbtList();
        for (SessionSnapshotData.RosterEntry entry : this.playerGrid.getMembers("selected")) {
            net.minecraft.nbt.NbtCompound compound = new net.minecraft.nbt.NbtCompound();
            compound.putString("uuid", entry.uuid());
            compound.putString("name", entry.name());
            members.add(compound);
        }
        group.put("members", members);
        groups.add(group);
        plan.put("groups", groups);

        ClientPlayNetworking.send(new NetworkConstants.CreateSessionPayload(DeathSwapDefinition.ID, this.sessionName, plan));
        this.statusMessage = "Requested Death Swap session creation.";
    }

    private NbtCompound buildSettingsCompound() {
        NbtCompound settings = new NbtCompound();
        settings.putInt("swapIntervalSeconds", this.swapIntervalSeconds);
        settings.putInt("initialGracePeriodSeconds", this.gracePeriodSeconds);
        settings.putInt("borderSize", this.borderSize);
        settings.putBoolean("keepInventory", this.keepInventory);
        settings.putBoolean("preserveVelocity", this.preserveVelocity);
        settings.putString("seedMode", this.seedMode.nbtValue());
        settings.putLong("seed", this.seedMode == DeathSwapSettings.SeedMode.FIXED ? this.seedValue : System.currentTimeMillis());
        return settings;
    }

    private void syncStateFromWidgets() {
        this.swapIntervalSeconds = readInt(this.swapIntervalField, this.swapIntervalSeconds, 10, 3600);
        this.gracePeriodSeconds = readInt(this.gracePeriodField, this.gracePeriodSeconds, 0, 3600);
        this.borderSize = readInt(this.borderSizeField, this.borderSize, 100, 30000);
        if (this.seedValueField != null) {
            try {
                this.seedValue = Long.parseLong(this.seedValueField.getText().trim());
            } catch (NumberFormatException ignored) {
                this.seedValueField.setText(Long.toString(this.seedValue));
            }
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
        if (this.playerGrid.getMembers("selected").size() < 2) {
            return "Select at least two players.";
        }
        return "";
    }

    private String seedModeLabel() {
        return this.seedMode == DeathSwapSettings.SeedMode.RANDOM ? "Seed: Random" : "Seed: Fixed";
    }

    private static String toggleLabel(String label, boolean value) {
        return label + ": " + (value ? "ON" : "OFF");
    }

    private enum Module {
        PLAYERS("players", "P", "Players", "Select participating players.", UiTheme.ACCENT),
        MATCH_RULES("rules", "R", "Match Rules", "Configure swap timing and match rules.", UiTheme.ACCENT_BLUE),
        SUMMARY("summary", "U", "Summary", "Review and launch the match.", UiTheme.ACCENT_RED);

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
