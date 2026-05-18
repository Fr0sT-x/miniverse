package dev.frost.miniverse.session;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class SessionPermissions {
    private static final String DEV_BYPASS_PROPERTY = "miniverse.session.devBypass";
    private static final String DEV_BYPASS_DEFAULT = "true";
    private static final PermissionLevel REQUIRED_LEVEL = PermissionLevel.fromLevel(2);

    private SessionPermissions() {
    }

    public static boolean canManageSessions(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }

        if (isDevBypassEnabled()) {
            return true;
        }

        if (player.getPermissions() instanceof LeveledPermissionPredicate leveled) {
            return leveled.getLevel().isAtLeast(REQUIRED_LEVEL);
        }

        return false;
    }

    public static boolean checkCanManageSessions(ServerPlayerEntity player, String action) {
        if (canManageSessions(player)) {
            return true;
        }

        if (player != null) {
            player.sendMessage(Text.literal("You do not have permission to " + action + "."), false);
        }
        return false;
    }

    public static boolean isDevBypassEnabled() {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            return Boolean.parseBoolean(System.getProperty(DEV_BYPASS_PROPERTY, DEV_BYPASS_DEFAULT));
        }

        return isBackendDevSessionBypassEnabled();
    }

    public static boolean isBackendDevSessionBypassEnabled() {
        return SessionRuntimeConfig.isSessionServer()
            && Boolean.getBoolean("miniverse.devSession")
            && Boolean.parseBoolean(System.getProperty(DEV_BYPASS_PROPERTY, "false"));
    }
}
