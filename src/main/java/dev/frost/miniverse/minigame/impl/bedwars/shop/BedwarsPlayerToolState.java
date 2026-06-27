package dev.frost.miniverse.minigame.impl.bedwars.shop;

import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;

public final class BedwarsPlayerToolState {
    private int pickaxeTier = 0;
    private int axeTier     = 0;
    private int armorTier   = 0;

    public void degrade() {
        pickaxeTier = Math.max(0, pickaxeTier - 1);
        axeTier     = Math.max(0, axeTier - 1);
    }

    public void upgradePickaxe(int tier) { pickaxeTier = Math.max(pickaxeTier, tier); }
    public void upgradeAxe(int tier)     { axeTier = Math.max(axeTier, tier); }
    public void upgradeArmor(int tier)   { armorTier = Math.max(armorTier, tier); }
    
    public int getPickaxeTier() { return pickaxeTier; }
    public int getAxeTier() { return axeTier; }
    public int getArmorTier() { return armorTier; }

    public ItemStack buildPickaxe(RegistryWrapper.WrapperLookup reg) {
        return BedwarsToolTier.pickaxeAtTier(pickaxeTier).buildStack(reg);
    }
    public ItemStack buildAxe(RegistryWrapper.WrapperLookup reg) {
        return BedwarsToolTier.axeAtTier(axeTier).buildStack(reg);
    }

    public JsonObject save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("pickaxeTier", pickaxeTier);
        obj.addProperty("axeTier", axeTier);
        obj.addProperty("armorTier", armorTier);
        return obj;
    }

    public static BedwarsPlayerToolState load(JsonObject obj) {
        BedwarsPlayerToolState state = new BedwarsPlayerToolState();
        if (obj != null) {
            if (obj.has("pickaxeTier")) state.pickaxeTier = obj.get("pickaxeTier").getAsInt();
            if (obj.has("axeTier")) state.axeTier = obj.get("axeTier").getAsInt();
            if (obj.has("armorTier")) state.armorTier = obj.get("armorTier").getAsInt();
        }
        return state;
    }
}
