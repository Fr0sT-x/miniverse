package dev.frost.miniverse.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.frost.miniverse.Miniverse;
import dev.frost.miniverse.common.MiniversePaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class SessionServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MiniversePaths.miniverseConfig("session-server.json");
    private static final Set<String> DIFFICULTIES = Set.of("peaceful", "easy", "normal", "hard");

    private static SessionServerConfig instance;
    private Config config;

    private SessionServerConfig() {
        this.load();
    }

    public static synchronized SessionServerConfig getInstance() {
        if (instance == null) {
            instance = new SessionServerConfig();
        }
        return instance;
    }

    private void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                this.config = GSON.fromJson(Files.readString(CONFIG_PATH), Config.class);
                if (this.config == null) {
                    this.config = Config.defaults();
                }
            } else {
                this.config = Config.defaults();
                this.save();
            }
            this.config.normalize();
            Miniverse.LOGGER.info("Loaded session server configuration: {}", this.config);
        } catch (IOException e) {
            Miniverse.LOGGER.warn("Failed to load session server config, using defaults", e);
            this.config = Config.defaults();
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this.config));
        } catch (IOException e) {
            Miniverse.LOGGER.error("Failed to save session server config", e);
        }
    }

    public int viewDistance() {
        return this.config.viewDistance;
    }

    public int simulationDistance() {
        return this.config.simulationDistance;
    }

    public boolean onlineMode() {
        return this.config.onlineMode;
    }

    public int spawnProtection() {
        return this.config.spawnProtection;
    }

    public String difficulty() {
        return this.config.difficulty;
    }

    public boolean allowFlight() {
        return this.config.allowFlight;
    }

    public boolean acceptsTransfers() {
        return this.config.acceptsTransfers;
    }

    public synchronized void setViewDistance(int value) {
        this.config.viewDistance = value;
        this.config.normalize();
        this.save();
    }

    public synchronized void setSimulationDistance(int value) {
        this.config.simulationDistance = value;
        this.config.normalize();
        this.save();
    }

    public synchronized void setOnlineMode(boolean value) {
        this.config.onlineMode = value;
        this.save();
    }

    public synchronized void setSpawnProtection(int value) {
        this.config.spawnProtection = value;
        this.config.normalize();
        this.save();
    }

    public synchronized void setDifficulty(String value) {
        this.config.difficulty = normalizeDifficulty(value);
        this.save();
    }

    public synchronized void setAllowFlight(boolean value) {
        this.config.allowFlight = value;
        this.save();
    }

    public synchronized void setAcceptsTransfers(boolean value) {
        this.config.acceptsTransfers = value;
        this.save();
    }

    private static String normalizeDifficulty(String value) {
        if (value == null) {
            return "easy";
        }
        String normalized = value.toLowerCase().trim();
        return DIFFICULTIES.contains(normalized) ? normalized : "easy";
    }

    public static final class Config {
        public int viewDistance;
        public int simulationDistance;
        public boolean onlineMode;
        public int spawnProtection;
        public String difficulty;
        public boolean allowFlight;
        public boolean acceptsTransfers;

        public Config(int viewDistance, int simulationDistance, boolean onlineMode, int spawnProtection, String difficulty, boolean allowFlight, boolean acceptsTransfers) {
            this.viewDistance = viewDistance;
            this.simulationDistance = simulationDistance;
            this.onlineMode = onlineMode;
            this.spawnProtection = spawnProtection;
            this.difficulty = difficulty;
            this.allowFlight = allowFlight;
            this.acceptsTransfers = acceptsTransfers;
        }

        public static Config defaults() {
            return new Config(16, 8, false, 0, "easy", true, true);
        }

        private void normalize() {
            this.viewDistance = Math.clamp(this.viewDistance, 2, 32);
            this.simulationDistance = Math.clamp(this.simulationDistance, 2, 32);
            this.spawnProtection = Math.max(0, this.spawnProtection);
            this.difficulty = normalizeDifficulty(this.difficulty);
        }

        @Override
        public String toString() {
            return "Config{" +
                "viewDistance=" + viewDistance +
                ", simulationDistance=" + simulationDistance +
                ", onlineMode=" + onlineMode +
                ", spawnProtection=" + spawnProtection +
                ", difficulty='" + difficulty + '\'' +
                ", allowFlight=" + allowFlight +
                ", acceptsTransfers=" + acceptsTransfers +
                '}';
        }
    }
}

