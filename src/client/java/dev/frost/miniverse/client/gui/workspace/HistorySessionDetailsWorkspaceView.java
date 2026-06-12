package dev.frost.miniverse.client.gui.workspace;

import dev.frost.miniverse.client.gui.SessionScreen;
import dev.frost.miniverse.client.gui.ui.UiAnimation;
import dev.frost.miniverse.client.gui.ui.UiLayout;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class HistorySessionDetailsWorkspaceView implements WorkspaceView {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("'['hh:mm a , dd/MMM/yyyy']'").withZone(ZoneId.systemDefault());

    private final AdminWorkspaceView.SessionEntry session;
    private final AdminWorkspaceView parentView;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private SessionScreen screen;
    private UiLayout.Rect panel = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect contentArea = new UiLayout.Rect(0, 0, 0, 0);
    private final List<ActionButton> buttons = new ArrayList<>();
    private String statusMessage = "";

    public HistorySessionDetailsWorkspaceView(AdminWorkspaceView.SessionEntry session, AdminWorkspaceView parentView) {
        this.session = session;
        this.parentView = parentView;
    }

    @Override
    public void init(SessionScreen screen, UiLayout.Rect workspace) {
        this.screen = screen;
        this.panel = workspace.inset(4);
        this.contentArea = new UiLayout.Rect(this.panel.x() + 14, this.panel.y() + 58, this.panel.width() - 28, this.panel.height() - 86);
        this.buttons.clear();

        int buttonY = this.contentArea.y() + 140;
        int btnWidth = 100;
        int startX = this.contentArea.x();
        this.buttons.add(new ActionButton(new UiLayout.Rect(startX, buttonY, btnWidth, 22), () -> "Relaunch", this::relaunchSession, UiTheme.ACCENT_GREEN, () -> true));
        this.buttons.add(new ActionButton(new UiLayout.Rect(startX + btnWidth + 8, buttonY, btnWidth, 22), () -> "Inspect Copy", this::inspectSession, UiTheme.ACCENT_BLUE, () -> this.session.inspectable()));
        this.buttons.add(new ActionButton(new UiLayout.Rect(startX + btnWidth * 2 + 16, buttonY, btnWidth, 22), () -> "Delete", this::confirmDeleteSession, UiTheme.ACCENT_RED, () -> true));

        // Back button
        this.buttons.add(new ActionButton(new UiLayout.Rect(this.panel.x() + this.panel.width() - 64, this.panel.y() + 10, 50, 22), () -> "Back", this::goBack, UiTheme.BORDER_SUBTLE, () -> true));
    }

    private void goBack() {
        this.screen.openWorkspaceView(this.parentView);
    }

    @Override
    public void renderBackground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        UiRenderer.panel(context, this.panel.x(), this.panel.y(), this.panel.width(), this.panel.height(), UiTheme.PANEL, UiTheme.BORDER_SUBTLE);
        context.fill(this.panel.x() + 1, this.panel.y() + 1, this.panel.x() + this.panel.width() - 1, this.panel.y() + 40, 0x701B2634);
        context.drawText(textRenderer, Text.literal(this.title()), this.panel.x() + 14, this.panel.y() + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal(this.subtitle()), this.panel.x() + 14, this.panel.y() + 28, UiTheme.TEXT_DIM, false);

        int x = this.contentArea.x();
        int y = this.contentArea.y();
        
        context.drawText(textRenderer, Text.literal("Session ID: " + session.id()), x, y, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal("Game: " + session.game()), x, y + 14, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal("State: " + session.state()), x, y + 28, UiTheme.TEXT, false);
        context.drawText(textRenderer, Text.literal("Seed: " + session.seed()), x, y + 42, UiTheme.TEXT_DIM, false);
        
        context.drawText(textRenderer, Text.literal("Created: " + formatTime(session.createdAtMillis())), x, y + 60, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Updated: " + formatTime(session.updatedAtMillis())), x, y + 74, UiTheme.TEXT_MUTED, false);
        context.drawText(textRenderer, Text.literal("Played Time: " + formatDuration(session.playedMillis())), x, y + 88, UiTheme.TEXT_MUTED, false);

        if (!session.inspectable()) {
            context.drawText(textRenderer, Text.literal("No world copy available"), x, y + 106, UiTheme.WARNING, false);
        }

        int playerListX = x + 250;
        int playerListY = y;
        context.drawText(textRenderer, Text.literal("Players (" + session.playerCount() + ")"), playerListX, playerListY, UiTheme.ACCENT_BLUE, false);
        playerListY += 14;
        
        if (session.playerNames() != null && !session.playerNames().isEmpty()) {
            for (String playerName : session.playerNames()) {
                context.drawText(textRenderer, Text.literal("- " + playerName), playerListX, playerListY, UiTheme.TEXT, false);
                playerListY += 12;
            }
        } else {
            context.drawText(textRenderer, Text.literal("No players recorded."), playerListX, playerListY, UiTheme.TEXT_DIM, false);
        }

        if (!this.statusMessage.isEmpty()) {
            context.drawText(textRenderer, Text.literal(this.statusMessage), this.panel.x() + 14, this.panel.y() + this.panel.height() - 20, UiTheme.SUCCESS, false);
        }
    }

    @Override
    public void renderForeground(DrawContext context, TextRenderer textRenderer, UiLayout.Rect workspace, int mouseX, int mouseY, float delta) {
        for (ActionButton button : this.buttons) {
            button.render(context, textRenderer, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        for (ActionButton actionButton : this.buttons) {
            if (actionButton.click(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String title() {
        return "Session Details";
    }

    @Override
    public String subtitle() {
        return "Inspection and management for session " + session.id();
    }

    private void inspectSession() {
        if (!this.connected()) return;
        ClientPlayNetworking.send(new NetworkConstants.InspectSessionPayload(session.id()));
        this.statusMessage = "Launching inspection copy for " + session.id() + ".";
    }

    private void relaunchSession() {
        if (!this.connected()) return;
        ClientPlayNetworking.send(new NetworkConstants.RelaunchSessionPayload(session.id()));
        this.statusMessage = "Relaunching " + session.id() + ".";
    }

    private void confirmDeleteSession() {
        this.client.setScreen(new ConfirmScreen(
            confirmed -> {
                this.client.setScreen(this.screen);
                if (confirmed) {
                    if (this.connected()) {
                        ClientPlayNetworking.send(new NetworkConstants.DeleteSessionPayload(session.id()));
                        this.statusMessage = "Deleting " + session.id() + ".";
                        goBack(); // Go back to history list after delete
                    }
                }
            },
            Text.literal("Delete retained session?"),
            Text.literal("This permanently deletes session " + session.id() + "."),
            Text.literal("Yes"),
            Text.literal("No")
        ));
    }

    private boolean connected() {
        if (this.client.player == null) {
            this.statusMessage = "Not connected to server.";
            return false;
        }
        return true;
    }

    private static String formatTime(long epochMillis) {
        return epochMillis <= 0L ? "unknown" : TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    private static String formatDuration(long millis) {
        long totalMinutes = Math.max(0L, millis) / 60_000L;
        return (totalMinutes / 60L) + "h " + (totalMinutes % 60L) + "m";
    }

    private record ActionButton(UiLayout.Rect bounds, Supplier<String> label, Runnable action, int accent, BooleanSupplier enabled) {
        private void render(DrawContext context, TextRenderer textRenderer, int mouseX, int mouseY) {
            if (!this.enabled.getAsBoolean()) return;
            boolean hovered = this.bounds.contains(mouseX, mouseY);
            int fill = UiAnimation.lerpColor(UiTheme.PANEL_RAISED, UiAnimation.alpha(this.accent, 0.30F), hovered ? 1.0F : 0.0F);
            UiRenderer.panel(context, this.bounds.x(), this.bounds.y(), this.bounds.width(), this.bounds.height(), fill, hovered ? this.accent : UiTheme.BORDER_SUBTLE);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(this.label.get()), this.bounds.x() + this.bounds.width() / 2, this.bounds.y() + 7, UiTheme.TEXT);
        }

        private boolean click(double mouseX, double mouseY) {
            if (this.enabled.getAsBoolean() && this.bounds.contains(mouseX, mouseY)) {
                this.action.run();
                return true;
            }
            return false;
        }
    }
}
