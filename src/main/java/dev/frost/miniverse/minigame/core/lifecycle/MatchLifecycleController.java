package dev.frost.miniverse.minigame.core.lifecycle;

import dev.frost.miniverse.chat.ChatRouter;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.session.SessionPermissions;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.minecraft.network.packet.s2c.common.ServerTransferS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MatchLifecycleController {
    private static final MatchLifecycleController INSTANCE = new MatchLifecycleController();
    private static final int TICKS_PER_SECOND = 20;

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

    private MatchLifecycleController() {
    }

    public static MatchLifecycleController getInstance() {
        return INSTANCE;
    }

    public synchronized boolean beginMatch(MinigameRuntime runtime, MatchLifecycleOptions options, Runnable onStart) {
        if (this.phase != Phase.IDLE) {
            return false;
        }

        this.runtime = runtime;
        this.options = options == null ? MatchLifecycleOptions.defaults(runtime.minigame().getName()) : options;
        this.startCallback = onStart;
        this.endResult = null;
        this.sequence++;
        this.snapshotParticipants();
        runtime.setState(GameState.STARTING);
        ChatRouter.sendTeamChatNotice(this.participants());

        if (!this.options.freezeEnabled() || this.options.freezeSeconds() <= 0) {
            this.startRunning();
            return true;
        }

        this.phase = Phase.START_FREEZE;
        this.ticksRemaining = this.options.freezeSeconds() * TICKS_PER_SECOND;
        this.lastAnnouncedSecond = -1;
        runtime.setState(GameState.FROZEN);
        this.freezeParticipants(true);
        this.showStartTitle();
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
        if (this.phase == Phase.START_FREEZE) {
            this.reconcileStartFreezeParticipants();
        }
        if (this.phase != Phase.START_FREEZE && this.phase != Phase.END_RETURN) {
            return;
        }

        this.ticksRemaining = Math.max(0, this.ticksRemaining - 1);
        int secondsRemaining = (this.ticksRemaining + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        this.announceCountdown(secondsRemaining);

        if (this.ticksRemaining > 0) {
            return;
        }

        long currentSequence = this.sequence;
        if (this.phase == Phase.START_FREEZE) {
            this.startRunning();
        } else if (this.phase == Phase.END_RETURN && currentSequence == this.sequence) {
            this.returnPlayers();
        }
    }

    public synchronized boolean cancelReturn(ServerPlayerEntity admin) {
        if (this.phase != Phase.END_RETURN) {
            admin.sendMessage(Text.literal("No return teleport countdown is active.").formatted(Formatting.YELLOW), false);
            return false;
        }

        this.sequence++;
        this.phase = Phase.ENDED;
        this.ticksRemaining = 0;
        this.runtimeState(GameState.ENDING);
        Text message = Text.literal(admin.getName().getString() + " cancelled the match return teleport.")
            .formatted(Formatting.RED, Formatting.BOLD);
        this.admins().forEach(player -> player.sendMessage(message, false));
        this.participants().forEach(player -> player.sendMessage(this.options.returnCancelledMessage(), false));
        return true;
    }


    public synchronized void onParticipantJoin(ServerPlayerEntity player) {
        if (this.phase != Phase.START_FREEZE || this.runtime == null) {
            return;
        }
        if (!this.runtime.context().participants().contains(player)) {
            return;
        }

        this.addStartFreezeParticipant(player);
    }

    private void startRunning() {
        this.setParticipantsGameMode(GameMode.SURVIVAL);
        this.unfreezeParticipants();
        this.phase = Phase.RUNNING;
        this.runtimeState(GameState.RUNNING);
        if (this.options.startSound() != null) {
            this.participants().forEach(player -> player.playSound(this.options.startSound(), 1.0F, 1.0F));
        }
        Runnable callback = this.startCallback;
        this.startCallback = null;
        if (callback != null) {
            callback.run();
        }
    }

    private void returnPlayers() {
        this.phase = Phase.ENDED;
        this.unfreezeParticipants();
        this.runtimeState(GameState.FINISHED);
        // Prevent main-server join routing from bouncing returned players back into this session.
        SessionRuntimeConfig.getSessionId().ifPresent(SessionRegistry::markStopRequested);
        String host = SessionRuntimeConfig.getReturnHost();
        int port = SessionRuntimeConfig.getReturnPort();
        for (ServerPlayerEntity player : this.participants()) {
            player.networkHandler.sendPacket(new ServerTransferS2CPacket(host, port));
        }
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
        for (ServerPlayerEntity player : this.participants()) {
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
        this.showStartTitleTo(player);
        this.sendCountdownTo(player, this.secondsRemaining());
        ChatRouter.sendTeamChatNotice(List.of(player));
    }

    private void setParticipantsGameMode(GameMode mode) {
        for (ServerPlayerEntity player : this.participants()) {
            player.changeGameMode(mode);
        }
    }

    private void unfreezeParticipants() {
        for (ServerPlayerEntity player : this.participants()) {
            this.freezeService.unfreeze(player, FreezeReason.MATCH_START);
        }
    }

    private void showStartTitle() {
        for (ServerPlayerEntity player : this.participants()) {
            player.networkHandler.sendPacket(new SubtitleS2CPacket(this.options.startSubtitle().copy().formatted(Formatting.YELLOW)));
            player.networkHandler.sendPacket(new TitleS2CPacket(this.options.startTitle().copy().formatted(Formatting.GOLD, Formatting.BOLD)));
        }
    }

    private void showStartTitleTo(ServerPlayerEntity player) {
        player.networkHandler.sendPacket(new SubtitleS2CPacket(this.options.startSubtitle().copy().formatted(Formatting.YELLOW)));
        player.networkHandler.sendPacket(new TitleS2CPacket(this.options.startTitle().copy().formatted(Formatting.GOLD, Formatting.BOLD)));
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
        for (ServerPlayerEntity player : this.participants()) {
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
                player.playSound(this.options.endSound(), 1.0F, winner ? 1.2F : 0.8F);
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
        for (ServerPlayerEntity player : this.participants()) {
            player.sendMessage(text, true);
            if (this.options.countdownSound() != null && secondsRemaining <= 5 && secondsRemaining > 0) {
                player.playSound(this.options.countdownSound(), 1.0F, 1.0F);
            }
        }
    }

    private void sendAdminCancelMessage() {
        Text message = Text.literal("[CANCEL RETURN TELEPORT]")
            .formatted(Formatting.RED, Formatting.BOLD)
            .styled(style -> style
                .withClickEvent(new ClickEvent.RunCommand("/miniverse_cancel_return"))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to keep this match session alive and cancel return teleport."))));
        this.admins().forEach(player -> player.sendMessage(message, false));
    }

    private List<ServerPlayerEntity> participants() {
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

    public synchronized boolean isMatchActive() {
        return this.phase != Phase.IDLE;
    }

    public synchronized void reset() {
        this.sequence++;
        this.phase = Phase.IDLE;
        this.ticksRemaining = 0;
        this.lastAnnouncedSecond = -1;
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

}
