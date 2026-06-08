package dev.frost.miniverse.client.minigame.layout;

import dev.frost.miniverse.common.NetworkConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class InventoryLayoutClient {
    private static String currentGamemode = null;
    private static String currentProfile = null;

    private InventoryLayoutClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkConstants.LAYOUT_SUPPORT_ID, (payload, context) -> {
            context.client().execute(() -> {
                String gm = payload.gamemode();
                if (gm == null || gm.isBlank()) {
                    currentGamemode = null;
                    currentProfile = null;
                } else {
                    currentGamemode = gm;
                    currentProfile = payload.layoutProfile();
                }
            });
        });
    }

    public static boolean isLayoutSupported() {
        return currentGamemode != null && !currentGamemode.isBlank();
    }

    public static String getCurrentGamemode() {
        return currentGamemode;
    }

    public static String getCurrentProfile() {
        return currentProfile;
    }

    public static void clear() {
        currentGamemode = null;
        currentProfile = null;
    }
}
