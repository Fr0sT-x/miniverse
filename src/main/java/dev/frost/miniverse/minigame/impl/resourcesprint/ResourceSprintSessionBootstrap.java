package dev.frost.miniverse.minigame.impl.resourcesprint;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintMinigame;
import dev.frost.miniverse.minigame.impl.resourcesprint.ResourceSprintSettings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

final class ResourceSprintSessionBootstrap {
    private static boolean registered;
    private static Properties config;
    private static boolean settingsApplied;

    private ResourceSprintSessionBootstrap() {
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
        if (!"resource_sprint".equalsIgnoreCase(properties.getProperty("game", ""))) {
            return;
        }

        if (!properties.containsKey("player." + player.getUuid())) {
            return;
        }

        ResourceSprintMinigame resourceSprint = getOrCreateResourceSprint();
        resourceSprint.setPlayerTeam(player, properties.getProperty("player." + player.getUuid() + ".team", properties.getProperty("assignmentLabel", "Team")));
        if (!settingsApplied) {
            resourceSprint.applySettings(ResourceSprintSettings.fromProperties(properties));
            settingsApplied = true;
        }

        MinigameManager.getInstance().addParticipant(player);
        if (resourceSprint.getState() == dev.frost.miniverse.minigame.core.GameState.WAITING_FOR_PLAYERS
            && resourceSprint.canStartMatch()
            && expectedPlayersOnline(properties)) {
            resourceSprint.startGame();
        }
    }

    private static ResourceSprintMinigame getOrCreateResourceSprint() {
        Minigame active = MinigameManager.getInstance().getActiveMinigame();
        if (active instanceof ResourceSprintMinigame resourceSprint) {
            return resourceSprint;
        }

        ResourceSprintMinigame resourceSprint = new ResourceSprintMinigame();
        MinigameManager.getInstance().setActiveMinigame(resourceSprint);
        return resourceSprint;
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


