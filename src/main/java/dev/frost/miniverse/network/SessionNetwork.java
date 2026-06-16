package dev.frost.miniverse.network;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.minigame.core.SessionBootstrapper;
import dev.frost.miniverse.network.handlers.DuelTypeNetworkHandler;
import dev.frost.miniverse.network.handlers.KitNetworkHandler;
import dev.frost.miniverse.network.handlers.MapNetworkHandler;
import dev.frost.miniverse.network.handlers.SessionManagementNetworkHandler;
import dev.frost.miniverse.network.handlers.SessionPlayerNetworkHandler;
import dev.frost.miniverse.network.handlers.SessionSettingsNetworkHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class SessionNetwork {
    private static boolean registered;

    private SessionNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        SessionManagementNetworkHandler.register();
        SessionPlayerNetworkHandler.register();
        MapNetworkHandler.register();
        SessionSettingsNetworkHandler.register();
        DuelTypeNetworkHandler.register();
        KitNetworkHandler.register();

        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CLIENT_CONNECTION_HOST_ID, (payload, context) ->
            ClientConnectionHosts.remember(context.player(), payload.host())
        );
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CLIENT_MATCH_READY_ID, (payload, context) ->
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSessionBootstrapper().markClientReady(context.player(), payload.sessionId())
        );

        registered = true;
    }
}
