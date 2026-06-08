package dev.frost.miniverse.client.gui.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class UiPreferences {
    private static final Gson GSON = new Gson();
    private static boolean worldBackdropEnabled = true;

    private UiPreferences() {
    }

    private static Path getConfigFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("miniverse").resolve("ui_preferences.json");
    }

    public static void load() {
        Path file = getConfigFile();
        if (Files.exists(file)) {
            try {
                JsonObject root = GSON.fromJson(Files.readString(file), JsonObject.class);
                if (root != null && root.has("worldBackdropEnabled")) {
                    worldBackdropEnabled = root.get("worldBackdropEnabled").getAsBoolean();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("worldBackdropEnabled", worldBackdropEnabled);
            Path file = getConfigFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean worldBackdropEnabled() {
        return worldBackdropEnabled;
    }

    public static void setWorldBackdropEnabled(boolean enabled) {
        worldBackdropEnabled = enabled;
        save();
    }
}
