package dev.frost.miniverse.client.gui;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SessionLaunchStatus {
    private static final long DONE_VISIBLE_MS = 5_000L;
    private static final long STALE_VISIBLE_MS = 60_000L;
    private static final int PANEL = 0xDD111827;
    private static final int BORDER = 0xAA334155;
    private static final int TRACK = 0x66334155;
    private static final int ACCENT = 0xFF38BDF8;
    private static final int SUCCESS = 0xFF22C55E;
    private static final int ERROR = 0xFFEF4444;
    private static final int TEXT = 0xFFF8FAFC;
    private static final int TEXT_MUTED = 0xFFCBD5E1;

    private static final Map<String, Status> STATUSES = new LinkedHashMap<>();
    private static boolean registered;

    private SessionLaunchStatus() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.LAUNCH_PROGRESS_ID, (payload, context) ->
            context.client().execute(() -> update(payload))
        );
        HudRenderCallback.EVENT.register((context, tickCounter) -> renderHud(context));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        registered = true;
    }

    public static Optional<Status> latestFor(String sessionId) {
        prune();
        return Optional.ofNullable(STATUSES.get(sessionId));
    }

    public static Optional<Status> latest() {
        prune();
        Status latest = null;
        for (Status status : STATUSES.values()) {
            latest = status;
        }
        return Optional.ofNullable(latest);
    }

    public static void clear() {
        STATUSES.clear();
    }

    private static void update(NetworkConstants.LaunchProgressPayload payload) {
        STATUSES.put(payload.sessionId(), new Status(
            payload.sessionId(),
            payload.title(),
            payload.stage(),
            payload.detail(),
            Math.clamp(payload.progress(), 0, 100),
            payload.done(),
            System.currentTimeMillis()
        ));
    }

    private static void renderHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null) {
            return;
        }

        Optional<Status> latest = latest();
        if (latest.isEmpty()) {
            return;
        }

        Status status = latest.get();
        int width = Math.min(260, client.getWindow().getScaledWidth() - 24);
        int x = client.getWindow().getScaledWidth() / 2 - width / 2;
        int y = 18;
        renderPanel(context, client.textRenderer, x, y, width, status);
    }

    public static void renderPanel(DrawContext context, TextRenderer textRenderer, int x, int y, int width, Status status) {
        context.fill(x, y, x + width, y + 50, PANEL);
        context.fill(x, y, x + width, y + 1, BORDER);
        context.fill(x, y + 49, x + width, y + 50, BORDER);
        context.fill(x, y, x + 1, y + 50, BORDER);
        context.fill(x + width - 1, y, x + width, y + 50, BORDER);

        String title = textRenderer.trimToWidth(status.title(), width - 20);
        String stage = textRenderer.trimToWidth(status.stage(), width - 20);
        String detail = textRenderer.trimToWidth(status.detail(), width - 20);
        context.drawText(textRenderer, Text.literal(title), x + 10, y + 8, TEXT, false);
        context.drawText(textRenderer, Text.literal(stage), x + 10, y + 20, status.accentColor(), false);
        if (!detail.isBlank()) {
            context.drawText(textRenderer, Text.literal(detail), x + 10, y + 32, TEXT_MUTED, false);
        }

        int barX = x + 10;
        int barY = y + 43;
        int barWidth = width - 20;
        context.fill(barX, barY, barX + barWidth, barY + 3, TRACK);
        context.fill(barX, barY, barX + Math.round(barWidth * (status.progress() / 100.0F)), barY + 3, status.accentColor());
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        STATUSES.entrySet().removeIf(entry -> {
            Status status = entry.getValue();
            long age = now - status.updatedAtMillis();
            return status.done() ? age > DONE_VISIBLE_MS : age > STALE_VISIBLE_MS;
        });
    }

    public record Status(String sessionId, String title, String stage, String detail, int progress, boolean done, long updatedAtMillis) {
        public int accentColor() {
            if ("Failed".equalsIgnoreCase(this.stage)) {
                return ERROR;
            }
            return this.done ? SUCCESS : ACCENT;
        }
    }
}
