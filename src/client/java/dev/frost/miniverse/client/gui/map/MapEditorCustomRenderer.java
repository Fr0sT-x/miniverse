package dev.frost.miniverse.client.gui.map;

import com.google.gson.JsonObject;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.function.BiConsumer;

@FunctionalInterface
public interface MapEditorCustomRenderer {
    int renderProperties(DrawContext context, TextRenderer textRenderer, SessionSnapshotData.EditorMarker marker, int x, int y, int width, int mouseX, int mouseY, boolean clicked, BiConsumer<SessionSnapshotData.EditorMarker, JsonObject> saveAction);
}
