package dev.frost.miniverse.client.gui.map;

import dev.frost.miniverse.client.gui.SessionSnapshotData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ThumbnailManager {
    private static final Map<String, Identifier> THUMBNAILS = new HashMap<>();
    private static final Map<String, NativeImageBackedTexture> TEXTURES = new HashMap<>();
    
    // Vanilla identifier for a fallback or placeholder thumbnail
    public static final Identifier PLACEHOLDER = Identifier.of("minecraft", "textures/misc/unknown_server.png");

    public static Identifier getThumbnail(SessionSnapshotData.MapSummary map) {
        if (THUMBNAILS.containsKey(map.id())) {
            Identifier id = THUMBNAILS.get(map.id());
            return id != null ? id : PLACEHOLDER;
        }

        Path thumbnailPath = Path.of(map.folder(), "thumbnail.png");
        if (Files.exists(thumbnailPath)) {
            try (InputStream is = Files.newInputStream(thumbnailPath)) {
                NativeImage image = NativeImage.read(is);
                NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                Identifier id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("miniverse_thumbnail_" + map.id().replaceAll("[^a-z0-9_.-]", "_").toLowerCase(), texture);
                THUMBNAILS.put(map.id(), id);
                TEXTURES.put(map.id(), texture);
                return id;
            } catch (Exception e) {
                System.err.println("Failed to load thumbnail for map " + map.id() + ": " + e.getMessage());
            }
        }
        
        THUMBNAILS.put(map.id(), null); // Cache missing to avoid re-reading
        return PLACEHOLDER;
    }

    public static void invalidate(String mapId) {
        if (TEXTURES.containsKey(mapId)) {
            NativeImageBackedTexture texture = TEXTURES.remove(mapId);
            texture.close(); // Frees OpenGL resources
            Identifier id = THUMBNAILS.remove(mapId);
            if (id != null) {
                MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
            }
        } else {
            THUMBNAILS.remove(mapId);
        }
    }

    public static void invalidateAll() {
        for (String mapId : new java.util.ArrayList<>(THUMBNAILS.keySet())) {
            invalidate(mapId);
        }
    }
}
