package dev.frost.miniverse.client.gui.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.client.gui.SessionSnapshotData;
import dev.frost.miniverse.client.gui.ui.UiRenderer;
import dev.frost.miniverse.client.gui.ui.UiTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.function.BiConsumer;

public final class DuelsEditorClient {
    public static void register() {
        MapEditorCustomRendererRegistry.register("arena", (context, textRenderer, marker, x, y, width, mouseX, mouseY, clicked, saveAction) -> {
            int cx = x;
            int height = 0;
            
            // Types row
            if (context != null) {
                context.drawText(textRenderer, Text.literal("Supported Duel Types:"), cx, y + 4, UiTheme.TEXT_DIM, false);
            }
            cx += 130; // approx width of "Supported Duel Types:" + 10
            
            JsonObject properties = marker.properties() != null ? marker.properties() : new JsonObject();
            JsonArray types = properties.has("types") && properties.get("types").isJsonArray() ? properties.getAsJsonArray("types") : new JsonArray();
            
            for (dev.frost.miniverse.minigame.impl.duels.DuelType type : dev.frost.miniverse.minigame.impl.duels.DuelTypeRegistry.getAll()) {
                boolean active = false;
                for (JsonElement e : types) {
                    if (e.getAsString().equals(type.id())) active = true;
                }
                
                // If textRenderer is null, we use a fixed estimated width to perform click detection
                int textW = textRenderer != null ? textRenderer.getWidth(type.name()) : (type.name().length() * 6);
                int pillW = textW + 16;
                int pillColor = active ? 0xFF3A1A1A : 0xFF1A1A2E;
                int pillBorder = active ? 0xFFAA3333 : 0xFF555577;
                int textColor = active ? 0xFFFF6666 : UiTheme.TEXT_MUTED;
                
                if (clicked && mouseX >= cx && mouseX <= cx + pillW && mouseY >= y && mouseY <= y + 16) {
                    JsonObject newProps = properties.deepCopy();
                    JsonArray newTypes = new JsonArray();
                    boolean found = false;
                    for (JsonElement e : types) {
                        if (e.getAsString().equals(type.id())) found = true;
                        else newTypes.add(e);
                    }
                    if (!found) newTypes.add(type.id());
                    newProps.add("types", newTypes);
                    saveAction.accept(marker, newProps);
                    return 16; // Return height to indicate we consumed the click
                }
                
                if (context != null) {
                    UiRenderer.panel(context, cx, y, pillW, 16, pillColor, pillBorder);
                    context.drawText(textRenderer, Text.literal(type.name()), cx + 8, y + 4, textColor, false);
                }
                
                cx += pillW + 6;
            }
            height = 16;
            
            return height;
        });
    }
}
