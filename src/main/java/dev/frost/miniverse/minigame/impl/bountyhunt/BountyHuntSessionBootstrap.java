package dev.frost.miniverse.minigame.impl.bountyhunt;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

final class BountyHuntSessionBootstrap {
    private static boolean registered;
    private static Properties config;
    private static boolean settingsApplied;

    private BountyHuntSessionBootstrap() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        registered = true;
    }

    private static void onJoin(ServerPlayerEntity player) {
        Properties properties = getConfig();
        if (!"bountyhunt".equalsIgnoreCase(properties.getProperty("game", ""))) {
            return;
        }

        if (!properties.containsKey("player." + player.getUuid())) {
            return;
        }

        BountyHuntMinigame bountyHunt = getOrCreateBountyHunt();
        if (!settingsApplied) {
            bountyHunt.applySettings(settingsFromProperties(properties));
            settingsApplied = true;
        }

        MinigameManager.getInstance().addParticipant(player);

        if (bountyHunt.getState() == dev.frost.miniverse.minigame.core.GameState.WAITING_FOR_PLAYERS
            && bountyHunt.canStartMatch()
            && expectedPlayersOnline(properties)) {
            bountyHunt.startGame();
        }
    }

    private static BountyHuntMinigame getOrCreateBountyHunt() {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof BountyHuntMinigame bountyHunt) {
            return bountyHunt;
        }

        BountyHuntMinigame bountyHunt = new BountyHuntMinigame();
        MinigameManager.getInstance().setActiveMinigame(bountyHunt);
        return bountyHunt;
    }

    private static boolean expectedPlayersOnline(Properties properties) {
        for (String name : properties.stringPropertyNames()) {
            if (!isExpectedPlayerKey(properties, name)) {
                continue;
            }

            try {
                UUID uuid = UUID.fromString(name.substring("player.".length()));
                if (MinigameManager.getInstance().getParticipants().stream().noneMatch(player -> player.getUuid().equals(uuid))) {
                    return false;
                }
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
        return true;
    }

    private static boolean isExpectedPlayerKey(Properties properties, String name) {
        return name.startsWith("player.") && "true".equalsIgnoreCase(properties.getProperty(name));
    }

    private static BountyHuntSettings settingsFromProperties(Properties properties) {
        return new BountyHuntSettings(
            intProperty(properties, "bountyhunt.gracePeriodSeconds", 300),
            intProperty(properties, "bountyhunt.respawnInvincibilitySeconds", 120),
            intProperty(properties, "bountyhunt.scoreToWin", 5),
            intProperty(properties, "bountyhunt.targetSwapIntervalSeconds", 0),
            booleanProperty(properties, "bountyhunt.trackerEnabled", true),
            booleanProperty(properties, "bountyhunt.netherTracking", true),
            intProperty(properties, "bountyhunt.compassCooldownSeconds", 2),
            properties.getProperty("bountyhunt.trackerItemId", "minecraft:compass")
        );
    }

    private static int intProperty(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean booleanProperty(Properties properties, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    private static synchronized Properties getConfig() {
        if (config != null) {
            return config;
        }

        config = new Properties();
        String configPath = System.getProperty("miniverse.session.config", "");
        if (configPath.isBlank()) {
            return config;
        }

        try (Reader reader = Files.newBufferedReader(Path.of(configPath))) {
            config.load(reader);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Could not load Miniverse session config {}", configPath, e);
        }
        return config;
    }
}

