package dev.frost.miniverse.client.microparty;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Locale;

public final class StopClockClientOverlay {
    private static boolean active = false;
    private static long startSystemTimeMs = 0;
    private static int durationMs = 5000;
    private static int targetRemainingMs = 2000;
    private static int toleranceMs = 350;

    private static boolean stopped = false;
    private static long stoppedRemainingMs = 0;
    private static boolean stoppedPassed = false;
    private static long stoppedTimestampMs = 0;

    private static boolean registered = false;

    private StopClockClientOverlay() {}

    public static void register() {
        if (registered) return;

        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.STOP_CLOCK_START_ID, (payload, context) -> {
            context.client().execute(() -> start(payload));
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (active && MinecraftClient.getInstance().currentScreen == null) {
                render(context);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(StopClockClientOverlay::onClientTick);
        registered = true;
    }

    public static void start(NetworkConstants.StopClockStartPayload payload) {
        active = true;
        stopped = false;
        startSystemTimeMs = System.currentTimeMillis();
        durationMs = payload.durationMs();
        targetRemainingMs = payload.targetRemainingMs();
        toleranceMs = payload.toleranceMs();
    }

    private static void onClientTick(MinecraftClient client) {
        if (!active || client.player == null) return;

        long now = System.currentTimeMillis();
        long elapsed = now - startSystemTimeMs;

        if (!stopped) {
            // Check if player sneaks (shift)
            if (client.options.sneakKey.isPressed() || client.player.isSneaking()) {
                stopTimer(client, elapsed);
            } else if (elapsed >= durationMs) {
                // Time ran out without stopping
                stopped = true;
                stoppedRemainingMs = 0;
                stoppedPassed = false;
                stoppedTimestampMs = now;
            }
        } else {
            // Keep display for 2 seconds after stopping or duration
            if (now - stoppedTimestampMs > 2200 && elapsed >= durationMs) {
                active = false;
            }
        }
    }

    private static void stopTimer(MinecraftClient client, long elapsed) {
        stopped = true;
        stoppedTimestampMs = System.currentTimeMillis();
        stoppedRemainingMs = Math.max(0, durationMs - elapsed);
        stoppedPassed = Math.abs(stoppedRemainingMs - targetRemainingMs) <= toleranceMs;

        // Send accurate client elapsed time to server
        ClientPlayNetworking.send(new NetworkConstants.StopClockStopPayload((int) elapsed));

        if (client.player != null) {
            if (stoppedPassed) {
                client.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.6f);
            } else {
                client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
            }
        }
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer tr = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int cx = screenWidth / 2;
        int y = 35;
        int w = 180;
        int h = 38;
        int x = cx - w / 2;

        long now = System.currentTimeMillis();
        long remainingMs;
        int borderColor;
        String timerStr;
        String statusStr;

        if (!stopped) {
            long elapsed = now - startSystemTimeMs;
            remainingMs = Math.max(0, durationMs - elapsed);
            borderColor = 0xFF38BDF8; // Light blue
            timerStr = String.format(Locale.ROOT, "%.2fs", remainingMs / 1000.0f);
            statusStr = "§eTarget: §a2.00s §7| Press §f[SHIFT]";
        } else {
            remainingMs = stoppedRemainingMs;
            borderColor = stoppedPassed ? 0xFF22C55E : 0xFFEF4444; // Green or Red
            timerStr = String.format(Locale.ROOT, "%.2fs", remainingMs / 1000.0f);
            statusStr = stoppedPassed ? "§a§l✔ PERFECT CLUTCH!" : "§c§l✖ MISSED WINDOW!";
        }

        // Sleek translucent background panel
        context.fill(x, y, x + w, y + h, 0xCC090D16);
        context.drawBorder(x, y, w, h, borderColor);

        // Header / Timer digits
        Text timerText = Text.literal("⏱ " + timerStr);
        int timerW = tr.getWidth(timerText);
        context.drawTextWithShadow(tr, timerText, cx - timerW / 2, y + 6, stoppedPassed ? 0x22C55E : (stopped ? 0xEF4444 : 0xFFFFFF));

        // Subtitle status
        Text statusText = Text.literal(statusStr);
        int statusW = tr.getWidth(statusText);
        context.drawTextWithShadow(tr, statusText, cx - statusW / 2, y + 22, 0x94A3B8);
    }
}
