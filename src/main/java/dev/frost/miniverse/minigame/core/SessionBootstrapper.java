package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.session.SessionConfigJson;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class SessionBootstrapper {
    private static final Set<String> REGISTERED_GAME_IDS = new HashSet<>();

    private SessionBootstrapper() {
    }

    public static synchronized <T extends Minigame> void register(Handler<T> handler) {
        String gameId = handler.gameId();
        if (!REGISTERED_GAME_IDS.add(gameId)) {
            return;
        }

        State<T> state = new State<>(handler);
        ServerPlayConnectionEvents.JOIN.register((connectionHandler, sender, server) -> state.onJoin(connectionHandler.player));
    }

    public interface Handler<T extends Minigame> {
        String gameId();

        Class<T> runtimeType();

        T createRuntime();

        void applySettings(T minigame, Properties properties);

        default void onPlayerJoin(T minigame, ServerPlayerEntity player, Properties properties) {
        }

        default MatchLifecycleOptions lifecycleOptions(T minigame, Properties properties) {
            return MatchLifecycleOptions.defaults(minigame.getName())
                .withStartTitle(Text.literal(minigame.getName()), Text.literal("Get ready."));
        }

        boolean canStart(T minigame);
    }

    private static final class State<T extends Minigame> {
        private final Handler<T> handler;
        private Properties config;
        private boolean settingsApplied;

        private State(Handler<T> handler) {
            this.handler = handler;
        }

        private void onJoin(ServerPlayerEntity player) {
            Properties properties = this.getConfig();
            if (!this.handler.gameId().equalsIgnoreCase(properties.getProperty("game", ""))) {
                return;
            }

            if (!properties.containsKey("player." + player.getUuid())) {
                return;
            }

            T minigame = this.getOrCreateRuntime();
            if (minigame == null) {
                return;
            }

            if (!this.settingsApplied) {
                this.handler.applySettings(minigame, properties);
                this.settingsApplied = true;
            }

            MinigameManager.getInstance().addParticipant(player);
            MatchLifecycleController.getInstance().onParticipantJoin(player);
            this.handler.onPlayerJoin(minigame, player, properties);

            if (minigame.getState() == GameState.WAITING_FOR_PLAYERS
                && this.handler.canStart(minigame)
                && this.expectedPlayersOnline(properties)) {
                MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
                if (runtime != null) {
                    MatchLifecycleOptions options = this.handler.lifecycleOptions(minigame, properties);
                    MatchLifecycleController.getInstance().beginMatch(runtime, options, minigame::startGame);
                }
            }
        }

        private T getOrCreateRuntime() {
            Minigame active = MinigameManager.getInstance().getActiveMinigame();
            if (this.handler.runtimeType().isInstance(active)) {
                return this.handler.runtimeType().cast(active);
            }

            if (active != null) {
                Miniverse.LOGGER.warn(
                    "Session config requested {} but active minigame is {}.",
                    this.handler.gameId(),
                    active.getName()
                );
                return null;
            }

            T minigame = this.handler.createRuntime();
            MinigameManager.getInstance().setActiveMinigame(minigame);
            return minigame;
        }

        private boolean expectedPlayersOnline(Properties properties) {
            for (String name : properties.stringPropertyNames()) {
                if (!isExpectedPlayerKey(properties, name)) {
                    continue;
                }

                try {
                    UUID uuid = UUID.fromString(name.substring("player.".length()));
                    if (!MinigameManager.getInstance().isParticipant(uuid)) {
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

        private synchronized Properties getConfig() {
            if (this.config != null) {
                return this.config;
            }

            this.config = new Properties();
            String configPath = System.getProperty("miniverse.session.config", "");
            if (configPath.isBlank()) {
                return this.config;
            }

            this.config = SessionConfigJson.readRuntimeProperties(Path.of(configPath));
            return this.config;
        }
    }
}
