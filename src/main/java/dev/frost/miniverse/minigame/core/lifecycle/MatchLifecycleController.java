package dev.frost.miniverse.minigame.core.lifecycle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.chat.ChatRouter;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.network.TransitionTransferCoordinator;
import dev.frost.miniverse.session.SessionPermissions;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class MatchLifecycleController {
    private static final int TICKS_PER_SECOND = 20;
    private static final int START_OVERLAY_REVEAL_SECONDS = 10;
    private static final int RETURN_COMPLETION_TIMEOUT_TICKS = 15 * TICKS_PER_SECOND;

    private final Map<UUID, ServerPlayerEntity> lifecyclePlayers = new HashMap<>();
    private final FreezeService freezeService = FreezeService.getInstance();
    @Nullable
    private MinigameRuntime runtime;
    private MatchLifecycleOptions options = MatchLifecycleOptions.defaults("Minigame");
    @Nullable
    private Runnable startCallback;
    @Nullable
    private MatchEndResult endResult;
    private Phase phase = Phase.IDLE;
    private int ticksRemaining;
    private int lastAnnouncedSecond = -1;
    private long sequence;
    private boolean startOverlayReleased;
    private final Set<UUID> pendingReturnPlayerIds = new HashSet<>();
    private int returnCompletionTicksRemaining;

    public MatchLifecycleController() {
    }

    public synchronized boolean beginMatch(MinigameRuntime runtime, MatchLifecycleOptions options, Runnable onStart) {
        if (this.phase != Phase.IDLE) {
            return false;
        }

        this.runtime = runtime;
        this.options = options == null ? MatchLifecycleOptions.defaults(runtime.minigame().getName()) : options;
        this.startCallback = onStart;
        this.endResult = null;
        this.startOverlayReleased = false;
        this.pendingReturnPlayerIds.clear();
        this.returnCompletionTicksRemaining = 0;
        this.sequence++;
        this.snapshotParticipants();
        runtime.setState(GameState.STARTING);

        if (!this.options.freezeEnabled() || this.options.freezeSeconds() <= 0) {
            this.startRunning();
            return true;
        }

        this.phase = Phase.START_FREEZE;
        this.ticksRemaining = this.options.freezeSeconds() * TICKS_PER_SECOND;
        this.lastAnnouncedSecond = -1;
        runtime.setState(GameState.FROZEN);
        this.freezeParticipants(true);
        this.announceCountdown(this.options.freezeSeconds());
        return true;
    }

    public synchronized boolean endMatch(MinigameRuntime runtime, MatchEndResult result, MatchLifecycleOptions options) {
        if (this.phase == Phase.END_RETURN || this.phase == Phase.ENDED || this.phase == Phase.START_FREEZE) {
            return false;
        }

        this.runtime = runtime;
        this.options = options == null ? MatchLifecycleOptions.defaults(runtime.minigame().getName()) : options;
        this.endResult = result;
        this.startCallback = null;
        this.sequence++;
        this.snapshotParticipants();
        this.phase = this.options.returnTeleportEnabled() ? Phase.END_RETURN : Phase.ENDED;
        this.ticksRemaining = this.options.returnSeconds() * TICKS_PER_SECOND;
        this.lastAnnouncedSecond = -1;
        runtime.setState(GameState.ENDING);
        this.unfreezeParticipants();
        this.showEndTitles();

        if (!this.options.returnTeleportEnabled()) {
            this.completeLifecycle();
            return true;
        }

        runtime.setState(GameState.RETURNING);
        this.sendAdminCancelMessage();
        this.announceCountdown(this.options.returnSeconds());
        return true;
    }

    public synchronized void tick(MinecraftServer server) {
        if (this.phase == Phase.IDLE) {
            return;
        }

        this.bindServer(server);
        if (this.runtime != null && this.runtime.state() == GameState.PAUSED) {
            return;
        }
        if (this.phase == Phase.START_FREEZE) {
            this.reconcileStartFreezeParticipants();
            this.ticksRemaining = Math.max(0, this.ticksRemaining - 1);
            int secondsRemaining = (this.ticksRemaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
            this.announceCountdown(secondsRemaining);
            if (secondsRemaining <= START_OVERLAY_REVEAL_SECONDS) {
                this.releaseTransitionOverlays();
            }

            if (this.ticksRemaining > 0) {
                return;
            }

            this.startRunning();
            return;
        }

        if (this.phase == Phase.END_RETURN) {
            this.tickReturnCompletion();
            if (!this.pendingReturnPlayerIds.isEmpty()) {
                return;
            }
            this.ticksRemaining = Math.max(0, this.ticksRemaining - 1);
            int secondsRemaining = (this.ticksRemaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
            this.announceCountdown(secondsRemaining);
            if (this.ticksRemaining > 0) {
                return;
            }

            long currentSequence = this.sequence;
            if (currentSequence == this.sequence) {
                this.returnPlayers();
            }
            return;
        }

        if (this.phase == Phase.RUNNING) {
            dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState state = this.runtime.minigame().checkProgression(this.runtime.context().roster());
            if (state.blocked() && state.actionBarMessage() != null) {
                for (ServerPlayerEntity player : this.roster()) {
                    player.sendMessage(state.actionBarMessage(), true);
                }
            }
        }
    }

    public synchronized void onParticipantLeave(ServerPlayerEntity player) {
        if (this.runtime == null || !this.runtime.context().roster().contains(player)) {
            return;
        }
        this.lifecyclePlayers.putIfAbsent(player.getUuid(), player);
    }

    public synchronized void onParticipantJoin(ServerPlayerEntity player) {
        if (this.runtime == null || !this.runtime.context().roster().contains(player)) {
            return;
        }
        this.lifecyclePlayers.put(player.getUuid(), player);
        if (this.phase == Phase.START_FREEZE) {
            this.addStartFreezeParticipant(player);
        }
    }






    public synchronized JsonObject saveState(MinigameRuntime runtime) {
        JsonObject lifecycle = new JsonObject();
        lifecycle.addProperty("phase", this.runtime == runtime ? this.phase.name() : Phase.IDLE.name());
        lifecycle.addProperty("gameState", runtime == null || runtime.state() == null ? GameState.WAITING_FOR_PLAYERS.name() : runtime.state().name());
        lifecycle.addProperty("ticksRemaining", this.runtime == runtime ? this.ticksRemaining : 0);
        lifecycle.addProperty("lastAnnouncedSecond", this.runtime == runtime ? this.lastAnnouncedSecond : -1);
        lifecycle.addProperty("startOverlayReleased", this.runtime == runtime && this.startOverlayReleased);
        lifecycle.addProperty("returnPending", this.runtime == runtime && this.phase == Phase.END_RETURN);
        lifecycle.addProperty("returnCompletionTicksRemaining", this.runtime == runtime ? this.returnCompletionTicksRemaining : 0);
        JsonArray pendingReturns = new JsonArray();
        if (this.runtime == runtime) {
            this.pendingReturnPlayerIds.stream().map(UUID::toString).forEach(pendingReturns::add);
        }
        lifecycle.add("pendingReturnPlayers", pendingReturns);


        return lifecycle;
    }

    public synchronized void restoreState(MinigameRuntime runtime, MatchLifecycleOptions options, JsonObject lifecycle, @Nullable Runnable startCallback) {
        if (runtime == null || lifecycle == null) {
            return;
        }

        Phase restoredPhase = parsePhase(stringValue(lifecycle, "phase", ""));
        GameState restoredState = parseGameState(stringValue(lifecycle, "gameState", runtime.state() == null ? "" : runtime.state().name()), runtime.state());
        if (restoredPhase == Phase.IDLE) {
            restoredPhase = phaseForState(restoredState);
        }
        if (restoredPhase == Phase.IDLE) {
            runtime.setState(restoredState);
            return;
        }

        this.runtime = runtime;
        this.options = options == null ? MatchLifecycleOptions.defaults(runtime.minigame().getName()) : options;
        this.startCallback = restoredPhase == Phase.START_FREEZE ? startCallback : null;
        this.endResult = null;
        this.phase = restoredPhase;
        this.ticksRemaining = Math.max(0, intValue(lifecycle, "ticksRemaining", 0));
        this.lastAnnouncedSecond = intValue(lifecycle, "lastAnnouncedSecond", -1);
        this.startOverlayReleased = booleanValue(lifecycle, "startOverlayReleased", restoredPhase != Phase.START_FREEZE);
        this.returnCompletionTicksRemaining = Math.max(0, intValue(lifecycle, "returnCompletionTicksRemaining", 0));
        this.restorePendingReturns(lifecycle);
        this.sequence++;
        this.lifecyclePlayers.clear();

        runtime.setState(restoredState);
        if (restoredPhase == Phase.RUNNING) {
            runtime.setState(restoredState == GameState.PAUSED ? GameState.PAUSED : GameState.RUNNING);
            return;
        }
        if (restoredPhase == Phase.START_FREEZE) {
            runtime.setState(GameState.FROZEN);
            return;
        }
        if (restoredPhase == Phase.END_RETURN) {
            runtime.setState(GameState.RETURNING);
            return;
        }
        if (restoredPhase == Phase.ENDED) {
            runtime.setState(GameState.ENDING);
        }
    }

    private void startRunning() {
        this.releaseTransitionOverlays();
        this.setParticipantsGameMode(GameMode.SURVIVAL);
        this.unfreezeParticipants();
        this.phase = Phase.RUNNING;
        this.runtimeState(GameState.RUNNING);
        if (this.options.startSound() != null) {
            this.roster().forEach(player -> player.playSound(this.options.startSound(), 1.0F, 1.0F));
        }
        Runnable callback = this.startCallback;
        this.startCallback = null;
        if (callback != null) {
            callback.run();
        }
    }

    private void releaseTransitionOverlays() {
        if (this.startOverlayReleased) {
            return;
        }
        this.startOverlayReleased = true;
        String sessionId = SessionRuntimeConfig.getSessionId().orElse("");
        for (ServerPlayerEntity player : this.roster()) {
            if (ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_START_ID)) {
                ServerPlayNetworking.send(player, new NetworkConstants.MatchStartPayload(sessionId));
            }
        }
    }

    private void returnPlayers() {
        this.unfreezeParticipants();
        this.runtimeState(GameState.FINISHED);
        SessionRuntimeConfig.getSessionId().ifPresent(SessionRegistry::markStopRequested);
        String host = SessionRuntimeConfig.getReturnHost();
        int port = SessionRuntimeConfig.getReturnPort();
        List<ServerPlayerEntity> players = this.roster();
        if (players.isEmpty()) {
            SessionRuntimeConfig.getSessionId().ifPresent(SessionRegistry::markReturnComplete);
            this.completeLifecycle();
            return;
        }

        this.pendingReturnPlayerIds.clear();
        players.stream().map(ServerPlayerEntity::getUuid).forEach(this.pendingReturnPlayerIds::add);
        this.returnCompletionTicksRemaining = RETURN_COMPLETION_TIMEOUT_TICKS;
        AtomicInteger pendingTransfers = new AtomicInteger(players.size());
        for (ServerPlayerEntity player : players) {
            TransitionTransferCoordinator.transfer(player, host, port, "Returning to Lobby", () -> {
                if (pendingTransfers.decrementAndGet() == 0) {
                    this.returnCompletionTicksRemaining = Math.min(this.returnCompletionTicksRemaining, 5 * TICKS_PER_SECOND);
                }
            });
        }
    }

    private void tickReturnCompletion() {
        if (this.pendingReturnPlayerIds.isEmpty()) {
            return;
        }
        MinecraftServer server = this.runtime == null ? null : this.runtime.context().nullableServer();
        if (server != null) {
            this.pendingReturnPlayerIds.removeIf(playerId -> {
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                return player == null || player.isDisconnected();
            });
        }
        this.returnCompletionTicksRemaining = Math.max(0, this.returnCompletionTicksRemaining - 1);
        if (!this.pendingReturnPlayerIds.isEmpty() && this.returnCompletionTicksRemaining > 0) {
            return;
        }
        if (!this.pendingReturnPlayerIds.isEmpty()) {
            Miniverse.LOGGER.warn("Completing match return with {} player(s) still connected to backend after timeout: {}", this.pendingReturnPlayerIds.size(), this.pendingReturnPlayerIds);
        }
        this.pendingReturnPlayerIds.clear();
        this.returnCompletionTicksRemaining = 0;
        SessionRuntimeConfig.getSessionId().ifPresent(SessionRegistry::markReturnComplete);
        this.completeLifecycle();
    }

    private void bindServer(MinecraftServer server) {
        if (this.runtime != null) {
            this.runtime.bindServer(server);
        }
    }

    private void runtimeState(GameState state) {
        if (this.runtime != null) {
            this.runtime.setState(state);
        }
    }

    private void freezeParticipants(boolean prepareInventory) {
        for (ServerPlayerEntity player : this.roster()) {
            if (prepareInventory) {
                player.getInventory().clear();
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0F);
            }
            player.changeGameMode(GameMode.ADVENTURE);
            this.freeze(player);
        }
    }

    private void freeze(ServerPlayerEntity player) {
        this.freezeService.freeze(player, FreezeReason.MATCH_START);
    }

    private void reconcileStartFreezeParticipants() {
        if (this.runtime == null) {
            return;
        }
        for (ServerPlayerEntity player : this.runtime.context().liveParticipants()) {
            if (!this.lifecyclePlayers.containsKey(player.getUuid())) {
                this.addStartFreezeParticipant(player);
            }
        }
    }

    private void addStartFreezeParticipant(ServerPlayerEntity player) {
        this.lifecyclePlayers.put(player.getUuid(), player);
        player.getInventory().clear();
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0F);
        player.changeGameMode(GameMode.ADVENTURE);
        this.freeze(player);
        this.sendCountdownTo(player, this.secondsRemaining());
        ChatRouter.sendTeamChatNotice(List.of(player));
    }

    private void setParticipantsGameMode(GameMode mode) {
        for (ServerPlayerEntity player : this.roster()) {
            player.changeGameMode(mode);
        }
    }

    private void unfreezeParticipants() {
        for (ServerPlayerEntity player : this.roster()) {
            this.freezeService.clear(player);
        }
    }

    private void sendCountdownTo(ServerPlayerEntity player, int secondsRemaining) {
        if (secondsRemaining < 0) {
            return;
        }
        Text text = Text.literal("Starting in " + secondsRemaining + "s").formatted(Formatting.YELLOW);
        player.sendMessage(text, true);
        if (this.options.countdownSound() != null && secondsRemaining <= 5 && secondsRemaining > 0) {
            player.playSound(this.options.countdownSound(), 1.0F, 1.0F);
        }
    }

    private int secondsRemaining() {
        return (this.ticksRemaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    private void showEndTitles() {
        MatchEndResult result = this.endResult;
        for (ServerPlayerEntity player : this.roster()) {
            boolean winner = result != null && result.isWinner(player);
            Text title = winner ? this.options.winTitle() : this.options.loseTitle();
            if (result != null) {
                player.networkHandler.sendPacket(new SubtitleS2CPacket(
                    Text.literal("Winner: ").formatted(Formatting.GOLD).append(result.winnerLabel())
                ));
            }
            player.networkHandler.sendPacket(new TitleS2CPacket(title.copy().formatted(winner ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)));
            if (result != null) {
                player.sendMessage(Text.literal("Winner: ").formatted(Formatting.GOLD).append(result.winnerLabel()), true);
            }
            if (this.options.endSound() != null) {
                player.playSound(this.options.endSound(), 0.8F, winner ? 1.2F : 0.8F);
            }
        }
    }

    private void announceCountdown(int secondsRemaining) {
        if (secondsRemaining < 0 || secondsRemaining == this.lastAnnouncedSecond) {
            return;
        }
        this.lastAnnouncedSecond = secondsRemaining;
        Text text = this.phase == Phase.END_RETURN
            ? Text.literal("Returning in " + secondsRemaining + "s").formatted(Formatting.YELLOW)
            : Text.literal("Starting in " + secondsRemaining + "s").formatted(Formatting.YELLOW);
        for (ServerPlayerEntity player : this.roster()) {
            player.sendMessage(text, true);
            if (this.options.countdownSound() != null && secondsRemaining <= 5 && secondsRemaining > 0) {
                player.playSound(this.options.countdownSound(), 1.0F, 1.0F);
            }
        }
        // Show team chat notice when start freeze countdown reaches 5 seconds
        if (this.phase == Phase.START_FREEZE && secondsRemaining == 5) {
            ChatRouter.sendTeamChatNotice(this.roster());
        }
    }






    private void restorePendingReturns(JsonObject lifecycle) {
        this.pendingReturnPlayerIds.clear();
        if (!lifecycle.has("pendingReturnPlayers") || !lifecycle.get("pendingReturnPlayers").isJsonArray()) {
            return;
        }
        for (var element : lifecycle.getAsJsonArray("pendingReturnPlayers")) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            try {
                this.pendingReturnPlayerIds.add(UUID.fromString(element.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }



    private void sendAdminCancelMessage() {
        Text message = Text.literal("[CANCEL RETURN TELEPORT]")
            .formatted(Formatting.RED, Formatting.BOLD)
            .styled(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/miniverse_cancel_return"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to keep this match session alive and cancel return teleport."))));
        this.admins().forEach(player -> player.sendMessage(message, false));
    }

    private List<ServerPlayerEntity> roster() {
        MinecraftServer server = this.runtime == null ? null : this.runtime.context().nullableServer();
        if (server != null && !this.lifecyclePlayers.isEmpty()) {
            return this.lifecyclePlayers.keySet().stream()
                .map(server.getPlayerManager()::getPlayer)
                .filter(player -> player != null && !player.isDisconnected())
                .toList();
        }
        if (this.runtime != null) {
            List<ServerPlayerEntity> players = this.runtime.context().liveParticipants();
            if (!players.isEmpty()) {
                return players;
            }
        }
        return MinigameManager.getInstance().getParticipants();
    }

    private void snapshotParticipants() {
        this.lifecyclePlayers.clear();
        for (ServerPlayerEntity player : this.runtime == null ? List.<ServerPlayerEntity>of() : this.runtime.context().liveParticipants()) {
            this.lifecyclePlayers.put(player.getUuid(), player);
        }
    }

    private List<ServerPlayerEntity> admins() {
        MinecraftServer server = this.runtime == null ? null : this.runtime.context().nullableServer();
        if (server == null) {
            return List.of();
        }
        return server.getPlayerManager().getPlayerList().stream()
            .filter(SessionPermissions::canManageSessions)
            .toList();
    }

    public synchronized boolean cancelReturn(ServerPlayerEntity requester) {
        if (this.phase != Phase.END_RETURN) {
            return false;
        }
        this.phase = Phase.ENDED;
        this.ticksRemaining = 0;
        this.lastAnnouncedSecond = -1;
        this.runtimeState(GameState.ENDING);
        Text message = this.options.returnCancelledMessage();
        this.roster().forEach(player -> player.sendMessage(message, false));
        return true;
    }

    public synchronized boolean isMatchActive() {
        return this.phase != Phase.IDLE;
    }

    public synchronized void reset() {
        this.sequence++;
        this.phase = Phase.IDLE;
        this.ticksRemaining = 0;
        this.lastAnnouncedSecond = -1;
        this.startOverlayReleased = false;
        this.pendingReturnPlayerIds.clear();
        this.returnCompletionTicksRemaining = 0;
        this.unfreezeParticipants();
        this.lifecyclePlayers.clear();
        this.startCallback = null;
        this.endResult = null;
        this.runtime = null;
    }

    private void completeLifecycle() {
        this.sequence++;
        this.phase = Phase.IDLE;
        this.ticksRemaining = 0;
        this.lastAnnouncedSecond = -1;
        this.startOverlayReleased = false;
        this.unfreezeParticipants();
        this.lifecyclePlayers.clear();
        this.startCallback = null;
        this.endResult = null;
        this.runtime = null;
    }

    private enum Phase {
        IDLE,
        START_FREEZE,
        RUNNING,
        END_RETURN,
        ENDED
    }

    private static Phase parsePhase(String value) {
        if (value == null || value.isBlank()) {
            return Phase.IDLE;
        }
        try {
            return Phase.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Phase.IDLE;
        }
    }

    private static Phase phaseForState(GameState state) {
        if (state == GameState.STARTING || state == GameState.FROZEN) {
            return Phase.START_FREEZE;
        }
        if (state == GameState.RUNNING || state == GameState.IN_PROGRESS || state == GameState.PAUSED) {
            return Phase.RUNNING;
        }
        if (state == GameState.RETURNING) {
            return Phase.END_RETURN;
        }
        if (state == GameState.ENDING || state == GameState.FINISHED) {
            return Phase.ENDED;
        }
        return Phase.IDLE;
    }

    private static GameState parseGameState(String value, GameState fallback) {
        if (value == null || value.isBlank()) {
            return fallback == null ? GameState.WAITING_FOR_PLAYERS : fallback;
        }
        try {
            return GameState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return fallback == null ? GameState.WAITING_FOR_PLAYERS : fallback;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private record PendingDisconnect(UUID playerId, String playerName, @Nullable ServerPlayerEntity player, boolean critical) {
    }

}
