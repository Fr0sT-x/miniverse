package dev.frost.miniverse.client.transition;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.PlayerInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TransitionOverlay {
    private static final long COVER_NANOS = 500_000_000L;
    private static final long REVEAL_NANOS = 500_000_000L;
    private static final int READY_GRACE_TICKS = 80;
    private static final int MAX_HOLD_TICKS = 200;
    private static final int BACKGROUND = 0xFF050507;
    private static final int PANEL = 0xFF0D1117;
    private static final int PANEL_ALT = 0xFF161B22;
    private static final int ACCENT = 0xFF38BDF8;
    private static final int TEXT_PRIMARY = 0xFFF8FAFC;
    private static final int TEXT_SECONDARY = 0xFF94A3B8;
    private static final int CHEVRON = 0x181F2937;
    private static final int CHEVRON_ACCENT = 0x2238BDF8;
    private static final int DISCONNECT_BUTTON_WIDTH = 96;
    private static final int DISCONNECT_BUTTON_HEIGHT = 18;
    private static final float MCSR_FRONT_DELAY = 0.20F;
    private static final float MCSR_BACK_DURATION = 0.80F;
    private static final Pattern NUMBERED_TEAM_NAME = Pattern.compile("^\\s*team\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    private static Phase phase = Phase.IDLE;
    private static long phaseStartNanos;
    private static int readyTicks;
    private static int holdTicks;
    private static String pendingToken;
    private static String matchSessionId = "";
    private static String contextText = "Transferring";
    private static String descriptionText = "";
    private static String mapText = "";
    private static String statusText = "Preparing transfer";
    private static String localStatusText = "Connecting";
    private static String serverStatusText = "";
    private static boolean readySent;
    private static boolean matchReadySent;
    private static boolean awaitingMatchStart;
    private static boolean registered;
    private static boolean wasSuppressingInput;
    private static boolean mouseDown;
    private static boolean cursorUnlockedByOverlay;
    private static int disconnectButtonX;
    private static int disconnectButtonY;
    private static int disconnectButtonWidth;
    private static int disconnectButtonHeight;
    private static int readyPlayers;
    private static int totalPlayers;
    private static List<TeamIntro> teams = List.of();

    private TransitionOverlay() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.TRANSITION_START_ID, (payload, context) ->
            context.client().execute(() -> start(payload.token(), payload.context()))
        );
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.MATCH_INTRO_ID, (payload, context) ->
            context.client().execute(() -> showMatchIntro(payload.data()))
        );
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.MATCH_READY_STATE_ID, (payload, context) ->
            context.client().execute(() -> updateReadyState(payload.sessionId(), payload.readyPlayers(), payload.totalPlayers(), payload.status(), payload.data()))
        );
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.MATCH_START_ID, (payload, context) ->
            context.client().execute(() -> releaseForMatch(payload.sessionId()))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(TransitionOverlay::handleDisconnect));
        ClientTickEvents.END_CLIENT_TICK.register(TransitionOverlay::tick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (MinecraftClient.getInstance().currentScreen == null) {
                render(context);
            }
        });
        registered = true;
    }

    public static void start(String token, String context) {
        pendingToken = token;
        matchSessionId = "";
        contextText = context == null || context.isBlank() ? "Transferring" : context;
        descriptionText = "";
        mapText = "";
        statusText = "Preparing transfer";
        localStatusText = "Connecting";
        serverStatusText = "";
        readySent = false;
        matchReadySent = false;
        awaitingMatchStart = false;
        readyPlayers = 0;
        totalPlayers = 0;
        teams = List.of();
        readyTicks = 0;
        holdTicks = 0;
        phaseStartNanos = System.nanoTime();
        phase = Phase.COVERING;
    }

    public static void renderOnScreen(DrawContext context) {
        render(context);
    }

    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    private static void showMatchIntro(NbtCompound data) {
        matchSessionId = data.getString("sessionId", "");
        contextText = data.getString("title", contextText);
        descriptionText = data.getString("description", "");
        mapText = data.getString("map", "");
        readyPlayers = data.getInt("readyPlayers").orElse(0);
        totalPlayers = data.getInt("totalPlayers").orElse(0);
        teams = parseTeams(data.getList("teams").orElseGet(NbtList::new));
        awaitingMatchStart = true;
        matchReadySent = false;
        readyTicks = 0;
        holdTicks = 0;
        if (phase == Phase.IDLE) {
            phase = Phase.HOLDING;
            phaseStartNanos = System.nanoTime();
        }
        statusText = readyLine("Waiting for players...");
        localStatusText = "Connecting";
        serverStatusText = readyLine("Waiting for players...");
    }

    private static void updateReadyState(String sessionId, int ready, int total, String status, NbtCompound data) {
        if (!matchSessionId.isBlank() && !matchSessionId.equals(sessionId)) {
            return;
        }
        readyPlayers = Math.max(0, ready);
        totalPlayers = Math.max(0, total);
        teams = parseTeams(data.getList("teams").orElseGet(NbtList::new));
        serverStatusText = readyLine(status == null || status.isBlank() ? "Waiting for players..." : status);
    }

    private static void releaseForMatch(String sessionId) {
        if (!matchSessionId.isBlank() && !matchSessionId.equals(sessionId)) {
            return;
        }
        awaitingMatchStart = false;
        statusText = "Entering match";
        localStatusText = "Entering match";
        serverStatusText = "";
        phase = Phase.REVEALING;
        phaseStartNanos = System.nanoTime();
    }

    private static void handleDisconnect() {
        if (phase == Phase.IDLE) {
            return;
        }

        if (awaitingMatchStart || matchReadySent || !matchSessionId.isBlank()) {
            awaitingMatchStart = false;
            serverStatusText = "";
            localStatusText = "Connection closed";
            statusText = "Connection closed";
            phase = Phase.REVEALING;
            phaseStartNanos = System.nanoTime();
        }
    }

    private static void tick(MinecraftClient client) {
        if (phase == Phase.IDLE) {
            if (wasSuppressingInput) {
                resyncMovementKeys(client);
                wasSuppressingInput = false;
            }
            return;
        }

        ensureOverlayScreen(client);
        suppressMovement(client);
        updateDisconnectButton(client);

        switch (phase) {
            case COVERING -> {
            }
            case HOLDING -> {
                holdTicks++;
                WorldReadyState readyState = getWorldReadyState(client);
                if (awaitingMatchStart) {
                    localStatusText = matchReadySent ? "World ready" : (readyState.ready() ? "Synchronizing match..." : readyState.statusText());
                } else {
                    statusText = readyState.statusText();
                }
                if (readyState.ready()) {
                    readyTicks++;
                } else {
                    readyTicks = 0;
                }

                if (awaitingMatchStart && readyTicks >= READY_GRACE_TICKS) {
                    sendMatchReady();
                    localStatusText = "World ready";
                    return;
                }

                if (!awaitingMatchStart && (readyTicks >= READY_GRACE_TICKS || holdTicks >= MAX_HOLD_TICKS)) {
                    phase = Phase.REVEALING;
                    phaseStartNanos = System.nanoTime();
                }
            }
            case REVEALING -> {
            }
            case IDLE -> {
            }
        }
    }

    private static void sendReady() {
        if (readySent || pendingToken == null) {
            return;
        }

        readySent = true;
        if (!canSendReady()) {
            statusText = "Connection closed";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.TransitionReadyPayload(pendingToken));
    }

    private static void sendMatchReady() {
        if (matchReadySent || matchSessionId.isBlank()) {
            return;
        }

        matchReadySent = true;
        if (!canSendMatchReady()) {
            statusText = "Connection closed";
            return;
        }

        ClientPlayNetworking.send(new NetworkConstants.ClientMatchReadyPayload(matchSessionId));
    }

    private static boolean canSendReady() {
        try {
            return ClientPlayNetworking.canSend(NetworkConstants.TRANSITION_READY_ID);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static boolean canSendMatchReady() {
        try {
            return ClientPlayNetworking.canSend(NetworkConstants.CLIENT_MATCH_READY_ID);
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private static WorldReadyState getWorldReadyState(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return new WorldReadyState(false, "Connecting");
        }

        BlockPos pos = client.player.getBlockPos();
        if (!client.world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return new WorldReadyState(false, "Waiting for chunks");
        }

        boolean renderable = client.worldRenderer.isTerrainRenderComplete() || client.worldRenderer.isRenderingReady(pos);
        if (renderable && awaitingMatchStart && client.currentScreen != null && !(client.currentScreen instanceof TransitionOverlayScreen)) {
            client.setScreen(null);
        }
        return new WorldReadyState(renderable, renderable ? "Ready" : "Preparing world");
    }

    private static void render(DrawContext context) {
        if (phase == Phase.IDLE) {
            return;
        }

        int width = context.getScaledWindowWidth();
        int height = context.getScaledWindowHeight();
        float progress = animationProgress();
        float eased = phase == Phase.REVEALING ? easeIn(progress) : easeOut(progress);

        if (phase == Phase.COVERING) {
            renderCovering(context, width, height, progress);
            if (progress >= 1.0F) {
            phase = Phase.HOLDING;
            statusText = "Connecting";
            localStatusText = "Connecting";
            sendReady();
            }
            return;
        }

        if (phase == Phase.REVEALING) {
            renderRevealing(context, width, height, progress);
            if (progress >= 1.0F) {
                finish();
            }
            return;
        }

        context.fill(0, 0, width, height, BACKGROUND);
        renderChevronSweep(context, width, height);
        renderText(context, width, height);
    }

    private static void renderCovering(DrawContext context, int width, int height, float progress) {
        int backRight = Math.round(width * easeOut(Math.min(1.0F, progress / MCSR_BACK_DURATION)));
        int frontRight = Math.round(width * easeOut(delayedFrontProgress(progress)));

        context.fill(0, 0, backRight, height, PANEL_ALT);
        if (backRight > frontRight) {
            context.fill(frontRight, 0, backRight, height, ACCENT);
        }
        context.fill(0, 0, frontRight, height, PANEL);
    }

    private static void renderRevealing(DrawContext context, int width, int height, float progress) {
        int frontLeft = Math.round(width * easeIn(Math.min(1.0F, progress / MCSR_BACK_DURATION)));
        int backLeft = Math.round(width * easeIn(delayedFrontProgress(progress)));

        context.fill(frontLeft, 0, width, height, PANEL);
        if (backLeft > frontLeft) {
            context.fill(frontLeft, 0, backLeft, height, ACCENT);
        }
        context.fill(backLeft, 0, width, height, PANEL_ALT);
    }

    private static float delayedFrontProgress(float progress) {
        float clamped = MathHelper.clamp(progress, 0.0F, 1.0F);
        return MathHelper.clamp((clamped - MCSR_FRONT_DELAY) / (1.0F - MCSR_FRONT_DELAY), 0.0F, 1.0F);
    }

    private static void renderChevronSweep(DrawContext context, int width, int height) {
        int chevronWidth = Math.max(180, width / 5);
        int chevronHeight = Math.max(120, height / 4);
        int bandHeight = Math.max(28, chevronHeight / 4);
        int spacing = chevronWidth + Math.max(90, width / 12);
        int drift = (int) ((System.nanoTime() / 8_000_000L) % spacing);
        int centerY = height / 2;

        for (int x = -spacing + drift; x < width + spacing; x += spacing) {
            renderChevron(context, x, centerY, chevronWidth, chevronHeight, bandHeight, CHEVRON);
            renderChevron(context, x + Math.max(10, chevronWidth / 24), centerY, chevronWidth, chevronHeight, Math.max(3, bandHeight / 7), CHEVRON_ACCENT);
        }
    }

    private static void renderChevron(DrawContext context, int x, int centerY, int width, int height, int bandHeight, int color) {
        int halfWidth = width / 2;
        int halfHeight = height / 2;
        int steps = 10;

        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps;
            float t1 = (i + 1) / (float) steps;
            int y0 = Math.round(centerY - halfHeight + height * t0);
            int y1 = Math.round(centerY - halfHeight + height * t1);
            int edge0 = x + halfWidth + Math.round(halfWidth * (1.0F - Math.abs(0.5F - t0) * 2.0F));
            int edge1 = x + halfWidth + Math.round(halfWidth * (1.0F - Math.abs(0.5F - t1) * 2.0F));
            int left = Math.min(edge0, edge1);
            int right = Math.max(edge0, edge1) + bandHeight;
            context.fill(left, y0, right, y1, color);
        }
    }

    private static void renderText(DrawContext context, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        int centerX = width / 2;
        int top = Math.max(34, height / 2 - 118);
        int titleColor = pulseColor(TEXT_PRIMARY, 0.12F);
        context.drawCenteredTextWithShadow(client.textRenderer, contextText.toUpperCase(), centerX, top, titleColor);

        int y = top + 18;
        if (!descriptionText.isBlank()) {
            context.drawCenteredTextWithShadow(client.textRenderer, descriptionText, centerX, y, TEXT_SECONDARY);
            y += 14;
        }
        if (!mapText.isBlank()) {
            context.drawCenteredTextWithShadow(client.textRenderer, "Map: " + mapText, centerX, y, ACCENT);
            y += 24;
        } else {
            y += 10;
        }

        int teamsBottom = renderVersusTeams(context, client, centerX, y, width, height);
        int statusY = Math.min(height - 48, Math.max(y + 48, teamsBottom + 18));
        if (awaitingMatchStart || !matchSessionId.isBlank()) {
            context.drawCenteredTextWithShadow(client.textRenderer, localStatusText, centerX, statusY, TEXT_SECONDARY);
            if (!serverStatusText.isBlank()) {
                context.drawCenteredTextWithShadow(client.textRenderer, serverStatusText, centerX, statusY + 13, TEXT_PRIMARY);
            }
            renderLoadingBar(context, centerX, statusY + 29, Math.min(220, width - 60));
        } else {
            context.drawCenteredTextWithShadow(client.textRenderer, statusText, centerX, statusY, TEXT_PRIMARY);
            renderLoadingBar(context, centerX, statusY + 15, Math.min(220, width - 60));
        }

        renderDisconnectButton(context, client, width);
    }

    private static void renderDisconnectButton(DrawContext context, MinecraftClient client, int width) {
        disconnectButtonWidth = DISCONNECT_BUTTON_WIDTH;
        disconnectButtonHeight = DISCONNECT_BUTTON_HEIGHT;
        disconnectButtonX = width - disconnectButtonWidth - 12;
        disconnectButtonY = 12;

        int mouseX = scaledMouseX(client);
        int mouseY = scaledMouseY(client);
        boolean hovered = isInDisconnectButton(mouseX, mouseY);
        int fill = hovered ? 0xFF7F1D1D : 0xFF451A1A;
        int border = hovered ? 0xFFF87171 : 0xFFB91C1C;
        context.fill(disconnectButtonX, disconnectButtonY, disconnectButtonX + disconnectButtonWidth, disconnectButtonY + disconnectButtonHeight, fill);
        context.fill(disconnectButtonX, disconnectButtonY, disconnectButtonX + disconnectButtonWidth, disconnectButtonY + 1, border);
        context.fill(disconnectButtonX, disconnectButtonY + disconnectButtonHeight - 1, disconnectButtonX + disconnectButtonWidth, disconnectButtonY + disconnectButtonHeight, border);
        context.fill(disconnectButtonX, disconnectButtonY, disconnectButtonX + 1, disconnectButtonY + disconnectButtonHeight, border);
        context.fill(disconnectButtonX + disconnectButtonWidth - 1, disconnectButtonY, disconnectButtonX + disconnectButtonWidth, disconnectButtonY + disconnectButtonHeight, border);
        context.drawCenteredTextWithShadow(client.textRenderer, "Disconnect", disconnectButtonX + disconnectButtonWidth / 2, disconnectButtonY + 5, TEXT_PRIMARY);
    }

    private static int renderVersusTeams(DrawContext context, MinecraftClient client, int centerX, int y, int width, int height) {
        if (teams.isEmpty()) {
            return y;
        }

        int cardWidth = Math.min(176, Math.max(116, width / 4));
        int gap = Math.min(44, Math.max(20, width / 18));
        int columns = Math.max(1, Math.min(teams.size(), Math.max(1, (width - 48 + gap) / (cardWidth + gap))));
        int totalWidth = columns * cardWidth + (columns - 1) * gap;
        int startX = centerX - totalWidth / 2;
        int rowGap = 10;
        int maxCardHeight = teams.stream()
            .mapToInt(team -> Math.max(44, 28 + team.players().size() * 12))
            .max()
            .orElse(44);
        int bottom = y;

        for (int i = 0; i < teams.size(); i++) {
            TeamIntro team = teams.get(i);
            int column = i % columns;
            int row = i / columns;
            int cardHeight = Math.max(44, 28 + team.players().size() * 12);
            int x = startX + column * (cardWidth + gap);
            int cardY = y + row * (maxCardHeight + rowGap);
            int cappedHeight = cardHeight;
            context.fill(x, cardY, x + cardWidth, cardY + cappedHeight, PANEL_ALT);
            context.fill(x, cardY, x + 4, cardY + cappedHeight, team.color());
            context.drawTextWithShadow(client.textRenderer, trim(client, team.name().toUpperCase(), cardWidth - 18), x + 10, cardY + 8, team.color());
            int playerY = cardY + 23;
            for (int playerIndex = 0; playerIndex < team.players().size() && playerY + 9 <= cardY + cappedHeight; playerIndex++) {
                MemberIntro player = team.players().get(playerIndex);
                int markerColor = player.ready() ? 0xFF22C55E : 0xFF64748B;
                context.fill(x + 10, playerY + 2, x + 15, playerY + 7, markerColor);
                String label = (playerIndex + 1) + ". " + player.name();
                context.drawTextWithShadow(client.textRenderer, trim(client, label, cardWidth - 30), x + 20, playerY, TEXT_PRIMARY);
                playerY += 11;
            }
            bottom = Math.max(bottom, cardY + cappedHeight);
        }

        if (teams.size() == 2) {
            context.drawCenteredTextWithShadow(client.textRenderer, "VS", centerX, y + 24, ACCENT);
        }
        return bottom;
    }

    private static void renderLoadingBar(DrawContext context, int centerX, int y, int width) {
        int left = centerX - width / 2;
        context.fill(left, y, left + width, y + 2, 0x55233442);
        int sweep = Math.max(28, width / 4);
        int offset = (int) ((System.nanoTime() / 7_000_000L) % (width + sweep));
        int x0 = left + offset - sweep;
        context.fill(Math.max(left, x0), y, Math.min(left + width, x0 + sweep), y + 2, ACCENT);
    }

    private static int pulseColor(int color, float amount) {
        float wave = (float) ((Math.sin(System.nanoTime() / 450_000_000.0) + 1.0) * 0.5);
        int boost = Math.round(255 * amount * wave);
        int r = Math.min(255, ((color >> 16) & 0xFF) + boost);
        int g = Math.min(255, ((color >> 8) & 0xFF) + boost);
        int b = Math.min(255, (color & 0xFF) + boost);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String trim(MinecraftClient client, String text, int maxWidth) {
        if (client.textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        return client.textRenderer.trimToWidth(text, Math.max(8, maxWidth - client.textRenderer.getWidth("..."))) + "...";
    }

    private static String readyLine(String status) {
        if (totalPlayers <= 0) {
            return status;
        }
        return status + "  " + readyPlayers + "/" + totalPlayers + " Players Ready";
    }

    private static float animationProgress() {
        long duration = phase == Phase.REVEALING ? REVEAL_NANOS : COVER_NANOS;
        long elapsed = System.nanoTime() - phaseStartNanos;
        return MathHelper.clamp((float) elapsed / duration, 0.0F, 1.0F);
    }

    private static void finish() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof TransitionOverlayScreen) {
            client.setScreen(null);
        }
        restoreCursor();
        phase = Phase.IDLE;
        pendingToken = null;
        matchSessionId = "";
        contextText = "Transferring";
        descriptionText = "";
        mapText = "";
        statusText = "Preparing transfer";
        localStatusText = "Connecting";
        serverStatusText = "";
        readySent = false;
        matchReadySent = false;
        awaitingMatchStart = false;
        readyPlayers = 0;
        totalPlayers = 0;
        teams = List.of();
        readyTicks = 0;
        holdTicks = 0;
        phaseStartNanos = 0L;
        mouseDown = false;
        disconnectButtonX = 0;
        disconnectButtonY = 0;
        disconnectButtonWidth = 0;
        disconnectButtonHeight = 0;
    }

    private static float easeOut(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        return 1.0F - (1.0F - clamped) * (1.0F - clamped);
    }

    private static float easeIn(float value) {
        float clamped = MathHelper.clamp(value, 0.0F, 1.0F);
        return clamped * clamped;
    }

    private static void suppressMovement(MinecraftClient client) {
        if (client.player == null) {
            return;
        }

        suppressKey(client.options.forwardKey);
        suppressKey(client.options.backKey);
        suppressKey(client.options.leftKey);
        suppressKey(client.options.rightKey);
        suppressKey(client.options.jumpKey);
        suppressKey(client.options.sprintKey);

        if (client.player.input != null) {
            client.player.input.playerInput = PlayerInput.DEFAULT;
        }
        wasSuppressingInput = true;
    }

    private static void updateDisconnectButton(MinecraftClient client) {
        if (client.getWindow() == null) {
            return;
        }

        if (client.mouse.isCursorLocked()) {
            client.mouse.unlockCursor();
            cursorUnlockedByOverlay = true;
        }

        boolean down = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !mouseDown && isInDisconnectButton(scaledMouseX(client), scaledMouseY(client))) {
            disconnectFromServer(client);
        }
        mouseDown = down;
    }

    private static void ensureOverlayScreen(MinecraftClient client) {
        if (client.currentScreen == null) {
            client.setScreen(new TransitionOverlayScreen());
        }
    }

    private static void disconnectFromServer(MinecraftClient client) {
        pendingToken = null;
        awaitingMatchStart = false;
        matchReadySent = false;
        readySent = false;
        cursorUnlockedByOverlay = false;
        client.disconnect(Text.literal("Disconnected from match transfer."));
        finish();
    }

    private static void restoreCursor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (cursorUnlockedByOverlay && client.currentScreen == null && client.player != null) {
            client.mouse.lockCursor();
        }
        cursorUnlockedByOverlay = false;
    }

    private static boolean isInDisconnectButton(int mouseX, int mouseY) {
        return disconnectButtonWidth > 0
            && mouseX >= disconnectButtonX
            && mouseX <= disconnectButtonX + disconnectButtonWidth
            && mouseY >= disconnectButtonY
            && mouseY <= disconnectButtonY + disconnectButtonHeight;
    }

    private static int scaledMouseX(MinecraftClient client) {
        return (int) (client.mouse.getX() * client.getWindow().getScaledWidth() / Math.max(1, client.getWindow().getWidth()));
    }

    private static int scaledMouseY(MinecraftClient client) {
        return (int) (client.mouse.getY() * client.getWindow().getScaledHeight() / Math.max(1, client.getWindow().getHeight()));
    }

    private static void suppressKey(KeyBinding key) {
        InputUtil.Key boundKey = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        KeyBinding.setKeyPressed(boundKey, false);
        key.setPressed(false);
    }

    private static void resyncMovementKeys(MinecraftClient client) {
        resyncKey(client, client.options.forwardKey);
        resyncKey(client, client.options.backKey);
        resyncKey(client, client.options.leftKey);
        resyncKey(client, client.options.rightKey);
        resyncKey(client, client.options.jumpKey);
        resyncKey(client, client.options.sprintKey);
    }

    private static void resyncKey(MinecraftClient client, KeyBinding key) {
        InputUtil.Key boundKey = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
        boolean pressed = false;

        if (boundKey.getCategory() == InputUtil.Type.KEYSYM) {
            pressed = InputUtil.isKeyPressed(client.getWindow(), boundKey.getCode());
        } else if (boundKey.getCategory() == InputUtil.Type.MOUSE) {
            pressed = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), boundKey.getCode()) == GLFW.GLFW_PRESS;
        }

        KeyBinding.setKeyPressed(boundKey, pressed);
        key.setPressed(pressed);
    }

    private enum Phase {
        IDLE,
        COVERING,
        HOLDING,
        REVEALING
    }

    private record WorldReadyState(boolean ready, String statusText) {
    }

    private static List<TeamIntro> parseTeams(NbtList list) {
        List<TeamIntro> parsed = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound team = list.getCompoundOrEmpty(i);
            List<MemberIntro> players = new ArrayList<>();
            NbtList playerList = team.getList("players").orElseGet(NbtList::new);
            for (int playerIndex = 0; playerIndex < playerList.size(); playerIndex++) {
                NbtCompound member = playerList.getCompoundOrEmpty(playerIndex);
                String name = member.getString("name", "");
                if (!name.isBlank()) {
                    players.add(new MemberIntro(name, member.getBoolean("ready").orElse(false)));
                }
            }
            parsed.add(new TeamIntro(
                team.getString("name", "Team"),
                team.getInt("color").orElse(ACCENT),
                List.copyOf(players)
            ));
        }
        parsed.sort(Comparator
            .comparingInt(TransitionOverlay::teamSortGroup)
            .thenComparingInt(TransitionOverlay::teamNumber)
            .thenComparing(team -> team.name().toLowerCase(Locale.ROOT)));
        return List.copyOf(parsed);
    }

    private static int teamSortGroup(TeamIntro team) {
        return teamNumber(team) == Integer.MAX_VALUE ? 1 : 0;
    }

    private static int teamNumber(TeamIntro team) {
        Matcher matcher = NUMBERED_TEAM_NAME.matcher(team.name());
        if (!matcher.matches()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private record TeamIntro(String name, int color, List<MemberIntro> players) {
    }

    private record MemberIntro(String name, boolean ready) {
    }
}
