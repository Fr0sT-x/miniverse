package dev.frost.miniverse.minigame.core.kit;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class Kit {
    private final Identifier id;
    private final Text displayName;
    private final Set<String> categories;
    private final ItemStack[] armor;
    private final ItemStack[] inventory;
    private final ItemStack[] offhand;
    private final List<StatusEffectInstance> effects;

    public Kit(Identifier id, Text displayName, Set<String> categories, ItemStack[] armor, ItemStack[] inventory, ItemStack[] offhand, List<StatusEffectInstance> effects) {
        this.id = id;
        this.displayName = displayName;
        this.categories = Set.copyOf(categories);
        this.armor = armor;
        this.inventory = inventory;
        this.offhand = offhand;
        this.effects = List.copyOf(effects);
    }

    public Identifier getId() {
        return id;
    }

    public Text getDisplayName() {
        return displayName;
    }

    public Set<String> getCategories() {
        return categories;
    }

    public ItemStack[] getArmor() {
        return armor;
    }

    public ItemStack[] getInventory() {
        return inventory;
    }

    public ItemStack[] getOffhand() {
        return offhand;
    }

    public List<StatusEffectInstance> getEffects() {
        return effects;
    }

    public com.google.gson.JsonObject toJson(net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("display_name", displayName.getString());
        
        com.google.gson.JsonArray categoriesArray = new com.google.gson.JsonArray();
        for (String c : categories) categoriesArray.add(c);
        json.add("categories", categoriesArray);

        com.google.gson.JsonObject contents = new com.google.gson.JsonObject();
        com.google.gson.JsonArray inventoryArray = new com.google.gson.JsonArray();
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] != null && !inventory[i].isEmpty()) {
                com.google.gson.JsonObject itemObj = new com.google.gson.JsonObject();
                itemObj.addProperty("slot", i);
                try {
                    net.minecraft.nbt.NbtElement nbt = ItemStack.CODEC.encodeStart(registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE), inventory[i]).getOrThrow();
                    itemObj.addProperty("nbt", nbt.toString());
                    inventoryArray.add(itemObj);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }
        contents.add("inventory", inventoryArray);

        com.google.gson.JsonArray armorArray = new com.google.gson.JsonArray();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && !armor[i].isEmpty()) {
                com.google.gson.JsonObject itemObj = new com.google.gson.JsonObject();
                itemObj.addProperty("slot", i);
                try {
                    net.minecraft.nbt.NbtElement nbt = ItemStack.CODEC.encodeStart(registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE), armor[i]).getOrThrow();
                    itemObj.addProperty("nbt", nbt.toString());
                    armorArray.add(itemObj);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }
        contents.add("armor", armorArray);

        com.google.gson.JsonArray offhandArray = new com.google.gson.JsonArray();
        for (int i = 0; i < offhand.length; i++) {
            if (offhand[i] != null && !offhand[i].isEmpty()) {
                com.google.gson.JsonObject itemObj = new com.google.gson.JsonObject();
                itemObj.addProperty("slot", i);
                try {
                    net.minecraft.nbt.NbtElement nbt = ItemStack.CODEC.encodeStart(registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE), offhand[i]).getOrThrow();
                    itemObj.addProperty("nbt", nbt.toString());
                    offhandArray.add(itemObj);
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        }
        contents.add("offhand", offhandArray);

        json.add("contents", contents);
        return json;
    }

    public static Kit fromJson(com.google.gson.JsonObject json, net.minecraft.registry.RegistryWrapper.WrapperLookup registries) {
        Identifier id = Identifier.tryParse(json.get("id").getAsString());
        Text displayName = Text.literal(json.get("display_name").getAsString());
        
        java.util.Set<String> categories = new java.util.HashSet<>();
        if (json.has("categories")) {
            json.getAsJsonArray("categories").forEach(c -> categories.add(c.getAsString()));
        }

        ItemStack[] armor = new ItemStack[4];
        ItemStack[] inventory = new ItemStack[36];
        ItemStack[] offhand = new ItemStack[1];
        
        if (json.has("contents")) {
            com.google.gson.JsonObject contents = json.getAsJsonObject("contents");
            
            if (contents.has("inventory")) {
                contents.getAsJsonArray("inventory").forEach(e -> {
                    com.google.gson.JsonObject itemObj = e.getAsJsonObject();
                    int slot = itemObj.get("slot").getAsInt();
                    try {
                        net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.StringNbtReader.parse(itemObj.get("nbt").getAsString());
                        inventory[slot] = ItemStack.CODEC.parse(registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE), nbt).getOrThrow();
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
            if (contents.has("armor")) {
                contents.getAsJsonArray("armor").forEach(e -> {
                    com.google.gson.JsonObject itemObj = e.getAsJsonObject();
                    int slot = itemObj.get("slot").getAsInt();
                    try {
                        net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.StringNbtReader.parse(itemObj.get("nbt").getAsString());
                        armor[slot] = ItemStack.CODEC.parse(registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE), nbt).getOrThrow();
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
            if (contents.has("offhand")) {
                contents.getAsJsonArray("offhand").forEach(e -> {
                    com.google.gson.JsonObject itemObj = e.getAsJsonObject();
                    int slot = itemObj.get("slot").getAsInt();
                    try {
                        net.minecraft.nbt.NbtCompound nbt = net.minecraft.nbt.StringNbtReader.parse(itemObj.get("nbt").getAsString());
                        offhand[slot] = ItemStack.CODEC.parse(registries.getOps(net.minecraft.nbt.NbtOps.INSTANCE), nbt).getOrThrow();
                    } catch (Exception ex) { ex.printStackTrace(); }
                });
            }
        }

        return new Kit(id, displayName, categories, armor, inventory, offhand, java.util.List.of());
    }

    public void apply(ServerPlayerEntity player) {
        player.getInventory().clear();
        
        // Apply armor (indices 0: boots, 1: leggings, 2: chestplate, 3: helmet)
        for (int i = 0; i < armor.length && i < 4; i++) {
            if (armor[i] != null && !armor[i].isEmpty()) {
                player.getInventory().armor.set(i, armor[i].copy());
            }
        }
        
        // Apply inventory
        for (int i = 0; i < inventory.length && i < player.getInventory().main.size(); i++) {
            if (inventory[i] != null && !inventory[i].isEmpty()) {
                player.getInventory().main.set(i, inventory[i].copy());
            }
        }
        
        // Apply offhand
        if (offhand[0] != null && !offhand[0].isEmpty()) {
            player.getInventory().offHand.set(0, offhand[0].copy());
        }
        
        // Apply effects
        player.clearStatusEffects();
        for (StatusEffectInstance effect : effects) {
            player.addStatusEffect(new StatusEffectInstance(effect));
        }
        
        // Update client inventory
        player.currentScreenHandler.sendContentUpdates();
    }
}
