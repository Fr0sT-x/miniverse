package dev.frost.miniverse.minigame.impl.zombies.map;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

public class ZombiesArmorShop {
    public enum ArmorPart {
        UPPER_BODY,
        LOWER_BODY
    }

    public enum ArmorQuality {
        LEATHER(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS, 1),
        GOLD(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS, 2),
        IRON(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS, 3),
        DIAMOND(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, 4);

        private final Item helmet;
        private final Item chestplate;
        private final Item leggings;
        private final Item boots;
        private final int tier;

        ArmorQuality(Item helmet, Item chestplate, Item leggings, Item boots, int tier) {
            this.helmet = helmet;
            this.chestplate = chestplate;
            this.leggings = leggings;
            this.boots = boots;
            this.tier = tier;
        }

        public Item getHelmet() { return helmet; }
        public Item getChestplate() { return chestplate; }
        public Item getLeggings() { return leggings; }
        public Item getBoots() { return boots; }
        public int getTier() { return tier; }
    }

    private final String id;
    private final BlockPos pos;
    private final ArmorPart part;
    private final ArmorQuality quality;
    private final int price;

    public ZombiesArmorShop(String id, BlockPos pos, ArmorPart part, ArmorQuality quality, int price) {
        this.id = id;
        this.pos = pos;
        this.part = part;
        this.quality = quality;
        this.price = price;
    }

    public String getId() { return this.id; }
    public BlockPos getPos() { return this.pos; }
    public ArmorPart getPart() { return this.part; }
    public ArmorQuality getQuality() { return this.quality; }
    public int getPrice() { return this.price; }
}
