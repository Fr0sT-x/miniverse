package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.network.TransitionTransferCoordinator;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Properties;
import java.util.UUID;

public class SessionFlagPoller {
    private boolean startupAborted;
    private final SessionConfigParser configParser;
    private final ClientReadinessCoordinator readinessCoordinator;
    private final String gameId;

    public SessionFlagPoller(SessionConfigParser configParser, ClientReadinessCoordinator readinessCoordinator, String gameId) {
        this.configParser = configParser;
        this.readinessCoordinator = readinessCoordinator;
        this.gameId = gameId;
    }

    public boolean isStartupAborted() {
        return this.startupAborted;
    }

    public void abortStartup(MinecraftServer server, Properties properties, Text message) {
        this.startupAborted = true;
        this.readinessCoordinator.clear();

        String sessionId = properties.getProperty("sessionId", "");
        if (!sessionId.isBlank()) {
            SessionRegistry.markStopRequested(sessionId);
        }

        String host = SessionRuntimeConfig.getReturnHost();
        int port = SessionRuntimeConfig.getReturnPort();
        Miniverse.LOGGER.warn("Aborting {} session startup: {}", this.gameId, message.getString());

        for (UUID uuid : this.configParser.getExpectedPlayerIds(properties)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null || player.isDisconnected()) {
                continue;
            }

            FreezeService.getInstance().unfreeze(player, FreezeReason.MATCH_LOADING);
            player.sendMessage(message, false);
            if (host != null && !host.isBlank() && port > 0) {
                TransitionTransferCoordinator.transfer(player, host, port, "Returning to Lobby");
            } else if (ServerPlayNetworking.canSend(player, NetworkConstants.MATCH_START_ID)) {
                ServerPlayNetworking.send(player, new NetworkConstants.MatchStartPayload(sessionId));
            }
        }

        MinigameManager.getInstance().reset();
    }
}
