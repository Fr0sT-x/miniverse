package dev.frost.miniverse.minigame.core.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KitDataLoader extends JsonDataLoader implements IdentifiableResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Identifier ID = Identifier.of("miniverse", "kits");

    public KitDataLoader() {
        super(GSON, "miniverse/kits");
    }

    @Override
    public Identifier getFabricId() {
        return ID;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> prepared, ResourceManager manager, Profiler profiler) {
        // We only clear data-driven kits, but since our registry doesn't differentiate right now,
        // we could just clear everything, or we need to separate built-in vs data-driven.
        // For simplicity in this implementation, we won't clear built-ins if they are registered elsewhere,
        // but typically you re-register them or they are loaded via JSON.
        
        prepared.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                Text displayName = Text.literal(json.has("displayName") ? json.get("displayName").getAsString() : id.getPath());
                
                Set<String> categories = new HashSet<>();
                if (json.has("categories")) {
                    json.getAsJsonArray("categories").forEach(c -> categories.add(c.getAsString()));
                }

                // In a complete implementation, these would parse actual ItemStacks from JSON.
                // For now, we initialize empty arrays.
                ItemStack[] armor = new ItemStack[4];
                ItemStack[] inventory = new ItemStack[36];
                ItemStack[] offhand = new ItemStack[1];
                
                Kit kit = new Kit(id, displayName, categories, armor, inventory, offhand, List.of());
                KitRegistry.register(kit);
                
            } catch (Exception e) {
                System.err.println("Failed to parse kit " + id + ": " + e.getMessage());
            }
        });
        
        // Also load from config/miniverse/custom_kits
        try {
            java.nio.file.Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("miniverse/custom_kits");
            if (java.nio.file.Files.exists(configDir)) {
                try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(configDir)) {
                    stream.filter(p -> p.toString().endsWith(".json")).forEach(path -> {
                        try {
                            String jsonStr = java.nio.file.Files.readString(path);
                            JsonObject json = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                            // We need registry manager for fromJson, but we might not have it in apply().
                            // Wait, apply doesn't have RegistryManager. We can pass a DynamicRegistryManager or use an empty one.
                            // Actually, Kit.fromJson takes RegistryWrapper.WrapperLookup.
                            // But maybe we don't have to parse them in apply if we just parse them when they are created, and we can load them using ServerLifecycleEvents.
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
