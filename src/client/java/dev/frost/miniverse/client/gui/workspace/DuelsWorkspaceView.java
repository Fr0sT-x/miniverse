package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorContext;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen;
import dev.frost.miniverse.client.gui.selector.RegistrySelectorState;
import dev.frost.miniverse.client.gui.selector.providers.KitRegistryProvider;
import dev.frost.miniverse.minigame.core.kit.Kit;
import dev.frost.miniverse.minigame.core.kit.KitRegistry;
import dev.frost.miniverse.minigame.impl.duels.DuelsDefinition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DuelsWorkspaceView implements WorkspaceView, GamemodeWorkspaceView, GamemodeWorkspaceView.ModuleProvider {

    private SessionScreen parentScreen;
    private UiLayout.Rect workspace = new UiLayout.Rect(0, 0, 0, 0);
    private String activeModuleId = "settings";
    
    private RegistrySelectorState selectorState = new RegistrySelectorState();
    private ButtonWidget defaultKitButton;
    private Set<Identifier> selectedKits = new java.util.HashSet<>();

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.parentScreen = screen;
        this.workspace = workspace;
        UiLayout.Rect mainPanel = workspace.inset(4);
        
        this.defaultKitButton = screen.addWorkspaceChild(ButtonWidget.builder(Text.literal("Select Allowed Kits"), btn -> {
            RegistrySelectorContext<Kit> context = new RegistrySelectorContext<>(
                "miniverse:kit",
                "Select Allowed Kits",
                RegistrySelectorContext.SelectionMode.MULTI,
                this.selectorState,
                result -> {
                    this.selectedKits = new java.util.HashSet<>();
                    for (Kit k : result.selectedEntries()) {
                        this.selectedKits.add(k.getId());
                    }
                    MinecraftClient.getInstance().setScreen(this.parentScreen);
                },
                "duels",
                Set.of()
            );
            MinecraftClient.getInstance().setScreen(new RegistrySelectorScreen<>(context, new KitRegistryProvider()));
        }).dimensions(mainPanel.x() + 14, mainPanel.y() + 110, 200, 20).build());
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiLayout.Rect mainPanel = workspace.inset(4);
        UiRenderer.panel(context, mainPanel.x(), mainPanel.y(), mainPanel.width(), mainPanel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        
        int yOffset = mainPanel.y() + 20;
        context.drawText(textRenderer, Text.literal("Duels Configuration"), mainPanel.x() + 14, yOffset, UiTheme.TEXT, false);
        yOffset += 20;

        // Arena Diagnostics Placeholder
        context.drawText(textRenderer, Text.literal("Arena Diagnostics:"), mainPanel.x() + 14, yOffset, UiTheme.TEXT_MUTED, false);
        yOffset += 15;
        context.drawText(textRenderer, Text.literal("Total Arenas: 0"), mainPanel.x() + 20, yOffset, UiTheme.TEXT, false);
        yOffset += 15;
        context.drawText(textRenderer, Text.literal("Available Arenas: 0"), mainPanel.x() + 20, yOffset, UiTheme.TEXT, false);
        yOffset += 15;
        context.drawText(textRenderer, Text.literal("Busy Arenas: 0"), mainPanel.x() + 20, yOffset, UiTheme.TEXT, false);
        yOffset += 20;
        
        context.drawText(textRenderer, Text.literal("Validation Warnings: None"), mainPanel.x() + 14, yOffset, UiTheme.SUCCESS, false);
        yOffset += 25;
        
        String kitName = selectedKits.isEmpty() ? "None" : String.join(", ", selectedKits.stream().map(Identifier::getPath).toList());
        context.drawText(textRenderer, Text.literal("Allowed Kits: " + kitName), mainPanel.x() + 14, yOffset, UiTheme.TEXT, false);
    }

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    @Override
    public String title() { return "Duels Setup"; }

    @Override
    public String subtitle() { return "Configure Duel variants and arenas"; }

    @Override
    public String gameId() { return DuelsDefinition.ID; }

    @Override
    public List<WorkspaceModule> modules() {
        return List.of(new WorkspaceModule("settings", "S", "Settings", "Settings"));
    }

    @Override
    public String activeModuleId() { return activeModuleId; }

    @Override
    public void setActiveModule(String moduleId) {
        this.activeModuleId = moduleId;
    }
}
