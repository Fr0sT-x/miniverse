package dev.frost.miniverse.client.protection;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlayRenderMode;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlaySettings;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlayStyle;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProtectionOverlayClient {
    private static final Map<UUID, Map<Identifier, OverlayState>> overlaysByPlayer = new ConcurrentHashMap<>();
    private static long clientTicks;
    private static ProtectionOverlaySettings runtimeOverride;

    private ProtectionOverlayClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.PROTECTION_OVERLAY_ID, (payload, context) ->
            context.client().execute(() -> {
                if (payload.active()) {
                    activateOverlay(
                        payload.playerId(),
                        payload.overlayId(),
                        payload.remainingTicks(),
                        new ProtectionOverlaySettings(
                            ProtectionOverlayStyle.byId(payload.style()),
                            ProtectionOverlayRenderMode.byId(payload.renderMode()),
                            payload.glowColor(),
                            payload.outlineColor(),
                            payload.alpha(),
                            payload.intensity()
                        )
                    );
                } else {
                    deactivateOverlay(payload.playerId(), payload.overlayId());
                }
            })
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                clearAll();
                return;
            }
            clientTicks++;
            pruneExpiredOverlays();
        });

        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, registrationHelper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                registrationHelper.register(new ProtectionOverlayFeatureRenderer(playerRenderer));
            }
        });

        registerCommands();
    }

    public static ActiveOverlay getOverlayForPlayer(UUID playerId, float tickDelta) {
        Map<Identifier, OverlayState> overlays = overlaysByPlayer.get(playerId);
        if (overlays == null || overlays.isEmpty()) {
            return null;
        }

        Optional<OverlayState> selected = overlays.values().stream()
            .filter(overlay -> overlay.expiresAt() > clientTicks)
            .max(Comparator
                .<OverlayState>comparingDouble(overlay -> overlay.settings().alpha() * overlay.settings().intensity())
                .thenComparingLong(OverlayState::expiresAt));
        return selected.map(overlay -> overlay.toActive(tickDelta, runtimeOverride)).orElse(null);
    }

    public static void activateOverlay(UUID playerId, Identifier overlayId, int remainingTicks, ProtectionOverlaySettings settings) {
        if (remainingTicks <= 0) {
            deactivateOverlay(playerId, overlayId);
            return;
        }

        overlaysByPlayer
            .computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>())
            .put(overlayId, new OverlayState(overlayId, clientTicks, clientTicks + remainingTicks, settings));
    }

    public static void deactivateOverlay(UUID playerId, Identifier overlayId) {
        overlaysByPlayer.computeIfPresent(playerId, (uuid, overlays) -> {
            overlays.remove(overlayId);
            return overlays.isEmpty() ? null : overlays;
        });
    }

    public static void clearAll() {
        overlaysByPlayer.clear();
        clientTicks = 0L;
    }

    private static void pruneExpiredOverlays() {
        overlaysByPlayer.entrySet().removeIf(entry -> {
            Map<Identifier, OverlayState> overlays = entry.getValue();
            overlays.entrySet().removeIf(overlay -> overlay.getValue().expiresAt() <= clientTicks);
            return overlays.isEmpty();
        });
    }

    private static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("miniverseprotection")
                .then(ClientCommandManager.literal("style")
                    .then(ClientCommandManager.argument("style", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (ProtectionOverlayStyle style : ProtectionOverlayStyle.values()) {
                                builder.suggest(style.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            runtimeOverride = settings().withStyle(ProtectionOverlayStyle.byId(StringArgumentType.getString(context, "style")));
                            feedback(context.getSource(), "style=" + runtimeOverride.style().id());
                            return 1;
                        })))
                .then(ClientCommandManager.literal("mode")
                    .then(ClientCommandManager.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (ProtectionOverlayRenderMode mode : ProtectionOverlayRenderMode.values()) {
                                builder.suggest(mode.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            runtimeOverride = settings().withRenderMode(ProtectionOverlayRenderMode.byId(StringArgumentType.getString(context, "mode")));
                            feedback(context.getSource(), "mode=" + runtimeOverride.renderMode().id());
                            return 1;
                        })))
                .then(ClientCommandManager.literal("glow")
                    .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                        .executes(context -> {
                            runtimeOverride = settings().withGlowColor(parseColor(StringArgumentType.getString(context, "hex")));
                            feedback(context.getSource(), "glow updated");
                            return 1;
                        })))
                .then(ClientCommandManager.literal("outline")
                    .then(ClientCommandManager.argument("hex", StringArgumentType.word())
                        .executes(context -> {
                            runtimeOverride = settings().withOutlineColor(parseColor(StringArgumentType.getString(context, "hex")));
                            feedback(context.getSource(), "outline updated");
                            return 1;
                        })))
                .then(ClientCommandManager.literal("alpha")
                    .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.0F, 1.0F))
                        .executes(context -> {
                            runtimeOverride = settings().withAlpha(FloatArgumentType.getFloat(context, "value"));
                            feedback(context.getSource(), "alpha=" + runtimeOverride.alpha());
                            return 1;
                        })))
                .then(ClientCommandManager.literal("intensity")
                    .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg(0.0F, 3.0F))
                        .executes(context -> {
                            runtimeOverride = settings().withIntensity(FloatArgumentType.getFloat(context, "value"));
                            feedback(context.getSource(), "intensity=" + runtimeOverride.intensity());
                            return 1;
                        })))
                .then(ClientCommandManager.literal("reset")
                    .executes(context -> {
                        runtimeOverride = null;
                        feedback(context.getSource(), "runtime override cleared");
                        return 1;
                    }))
        ));
    }

    private static ProtectionOverlaySettings settings() {
        return runtimeOverride == null ? ProtectionOverlaySettings.DEFAULT : runtimeOverride;
    }

    private static int parseColor(String raw) {
        String value = raw.toLowerCase(Locale.ROOT).replace("#", "").replace("0x", "");
        if (value.length() == 6) {
            return 0xFF000000 | Integer.parseUnsignedInt(value, 16);
        }
        return (int) Long.parseLong(value, 16);
    }

    private static void feedback(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, String message) {
        source.sendFeedback(Text.literal("Protection overlay " + message));
    }

    private record OverlayState(Identifier overlayId, long startedAt, long expiresAt, ProtectionOverlaySettings settings) {
        ActiveOverlay toActive(float tickDelta, ProtectionOverlaySettings override) {
            ProtectionOverlaySettings effective = override == null ? this.settings : override;
            float age = Math.max(0.0F, clientTicks + tickDelta - this.startedAt);
            float remaining = Math.max(0.0F, this.expiresAt - (clientTicks + tickDelta));
            float fadeIn = Math.min(1.0F, age / 6.0F);
            float fadeOut = Math.min(1.0F, remaining / 8.0F);
            float pulse = 0.88F + 0.12F * (float) Math.sin((clientTicks + tickDelta) * 0.18F);
            return new ActiveOverlay(effective, Math.min(fadeIn, fadeOut), pulse);
        }
    }

    public record ActiveOverlay(ProtectionOverlaySettings settings, float fade, float animationPulse) {
    }
}
