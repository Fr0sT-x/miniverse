package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.session.SessionConfigJson;
import dev.frost.miniverse.session.SessionRegistry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class SessionConfigParser {
    private Properties config;

    public synchronized Properties getConfig() {
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

    public Properties getEffectiveProperties() {
        Properties base = this.getConfig();
        String sessionId = base.getProperty("sessionId", "");
        if (sessionId.isBlank()) {
            return base;
        }
        Properties merged = new Properties();
        merged.putAll(base);
        Properties registry = SessionRegistry.loadRuntimeProperties(sessionId);
        for (String name : registry.stringPropertyNames()) {
            if (name.startsWith("player.") || name.contains(".role.")) {
                merged.setProperty(name, registry.getProperty(name));
            }
        }
        return merged;
    }

    public String getConfiguredGameId() {
        return this.getConfig().getProperty("game", "").trim().toLowerCase();
    }

    public String getSessionId() {
        return this.getConfig().getProperty("sessionId", "");
    }

    public List<UUID> getExpectedPlayerIds(Properties properties) {
        List<UUID> ids = new ArrayList<>();
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("player.") && "true".equalsIgnoreCase(properties.getProperty(name))) {
                try {
                    ids.add(UUID.fromString(name.substring("player.".length())));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return ids;
    }

    public String getPlayerName(Properties properties, UUID uuid) {
        return properties.getProperty("player." + uuid + ".name", uuid.toString().substring(0, 8));
    }

    public String getRoleTeamId(Properties properties, String gameId, UUID uuid) {
        String role = properties.getProperty(gameId + ".role." + uuid, "").trim();
        if (!role.isBlank()) {
            String normalized = role.toLowerCase().replace(' ', '_');
            return normalized.endsWith("s") ? normalized : normalized + "s";
        }
        return properties.getProperty("player." + uuid + ".team", "Players");
    }

    public static String getDisplayName(String id) {
        if (id == null || id.isBlank()) {
            return "Players";
        }
        String normalized = id.replace('_', ' ').replace('-', ' ').trim();
        StringBuilder builder = new StringBuilder();
        for (String part : normalized.split("\\s+")) {
            if (part.isBlank()) continue;
            if (!builder.isEmpty()) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) builder.append(part.substring(1).toLowerCase());
        }
        return builder.isEmpty() ? id : builder.toString();
    }

    public static int getTeamColor(String id) {
        String normalized = id == null ? "" : id.toLowerCase();
        if (normalized.contains("red") || normalized.contains("hunter")) return 0xFFE11D48;
        if (normalized.contains("blue") || normalized.contains("runner")) return 0xFF2563EB;
        if (normalized.contains("green")) return 0xFF16A34A;
        if (normalized.contains("yellow")) return 0xFFFACC15;
        return 0xFF38BDF8;
    }
}
