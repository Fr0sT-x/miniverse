package dev.frost.miniverse.network.handlers;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.session.SessionLauncherConfig;
import dev.frost.miniverse.session.SessionListSerializer;
import dev.frost.miniverse.session.SessionManager;
import dev.frost.miniverse.session.SessionMemoryConfig;
import dev.frost.miniverse.session.SessionPermissions;
import dev.frost.miniverse.session.SessionRegistry;
import dev.frost.miniverse.session.SessionRetentionConfig;
import dev.frost.miniverse.session.SessionRuntimeConfig;
import dev.frost.miniverse.session.SessionServerConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SessionSettingsNetworkHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.CHANGE_SEED_ID, (payload, context) -> handleChangeSeed(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.LAUNCHER_SETTINGS_ID, (payload, context) -> handleLauncherSettings(context.server(), context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(NetworkConstants.SERVER_SETTINGS_ID, (payload, context) -> handleServerSettings(context.server(), context.player(), payload));
    }

    private static void handleChangeSeed(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.ChangeSeedPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "change session seed")) {
            return;
        }

        String sessionId = payload.sessionId();
        if (!SessionRuntimeConfig.isSessionServer() && SessionManager.getInstance().getSession(sessionId).isEmpty()) {
            player.sendMessage(Text.literal("Unknown session '" + sessionId + "'."), false);
            return;
        }

        SessionRegistry.markSeedChangeRequested(sessionId);
        Text message = Text.literal("Seed is changing for session " + sessionId + ". A new world will generate in the background.").formatted(Formatting.GREEN);
        player.sendMessage(message, false);
        player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0F, 1.0F);
        SessionListSerializer.sendSessionList(server, player);
    }

    private static void handleLauncherSettings(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.LauncherSettingsPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "update launcher settings")) {
            return;
        }

        int maxConcurrentLaunches = getIntOrDefault(payload.settings(), "maxConcurrentLaunches", SessionLauncherConfig.getInstance().maxConcurrentLaunches());
        SessionManager.getInstance().setMaxConcurrentLaunches(maxConcurrentLaunches);
        int applied = SessionLauncherConfig.getInstance().maxConcurrentLaunches();
        player.sendMessage(Text.literal("Max concurrent session launches set to " + applied + "."), false);
        SessionListSerializer.sendSessionList(server, player);
    }

    private static void handleServerSettings(MinecraftServer server, ServerPlayerEntity player, NetworkConstants.ServerSettingsPayload payload) {
        if (!SessionPermissions.checkCanManageSessions(player, "update server settings")) {
            return;
        }

        NbtCompound settings = payload.settings();
        NbtCompound memory = getCompoundOrEmpty(settings, "memory");
        NbtCompound serverSettings = getCompoundOrEmpty(settings, "server");
        NbtCompound retentionSettings = getCompoundOrEmpty(settings, "retention");

        SessionMemoryConfig memoryConfig = SessionMemoryConfig.getInstance();
        if (memory.contains("enabled", NbtElement.NUMBER_TYPE)) {
            memoryConfig.setEnabled(memory.getBoolean("enabled"));
        }
        if (memory.contains("maxHeapGb", NbtElement.NUMBER_TYPE)) {
            memoryConfig.setMaxHeap(memory.getInt("maxHeapGb"));
        }
        if (memory.contains("initialHeapGb", NbtElement.NUMBER_TYPE)) {
            memoryConfig.setInitialHeap(memory.getInt("initialHeapGb"));
        }

        SessionServerConfig serverConfig = SessionServerConfig.getInstance();
        if (serverSettings.contains("viewDistance", NbtElement.NUMBER_TYPE)) {
            serverConfig.setViewDistance(serverSettings.getInt("viewDistance"));
        }
        if (serverSettings.contains("simulationDistance", NbtElement.NUMBER_TYPE)) {
            serverConfig.setSimulationDistance(serverSettings.getInt("simulationDistance"));
        }
        if (serverSettings.contains("onlineMode", NbtElement.NUMBER_TYPE)) {
            serverConfig.setOnlineMode(serverSettings.getBoolean("onlineMode"));
        }
        if (serverSettings.contains("spawnProtection", NbtElement.NUMBER_TYPE)) {
            serverConfig.setSpawnProtection(serverSettings.getInt("spawnProtection"));
        }
        if (serverSettings.contains("difficulty", NbtElement.STRING_TYPE)) {
            serverConfig.setDifficulty(serverSettings.getString("difficulty"));
        }
        if (serverSettings.contains("allowFlight", NbtElement.NUMBER_TYPE)) {
            serverConfig.setAllowFlight(serverSettings.getBoolean("allowFlight"));
        }
        if (serverSettings.contains("acceptsTransfers", NbtElement.NUMBER_TYPE)) {
            serverConfig.setAcceptsTransfers(serverSettings.getBoolean("acceptsTransfers"));
        }
        if (serverSettings.contains("advertisedHost", NbtElement.STRING_TYPE)) {
            serverConfig.setAdvertisedHost(serverSettings.getString("advertisedHost"));
        }

        SessionRetentionConfig retentionConfig = SessionRetentionConfig.getInstance();

        if (retentionSettings.contains("maxAgeDays", NbtElement.NUMBER_TYPE)) {
            retentionConfig.setMaxAgeDays(retentionSettings.getInt("maxAgeDays"));
        }

        SessionListSerializer.sendSessionList(server, player);
    }

    private static int getIntOrDefault(NbtCompound nbt, String key, int fallback) {
        return nbt != null && nbt.contains(key, NbtElement.NUMBER_TYPE)
            ? nbt.getInt(key)
            : fallback;
    }

    private static NbtCompound getCompoundOrEmpty(NbtCompound nbt, String key) {
        return nbt != null && nbt.contains(key, NbtElement.COMPOUND_TYPE)
            ? nbt.getCompound(key)
            : new NbtCompound();
    }
}
