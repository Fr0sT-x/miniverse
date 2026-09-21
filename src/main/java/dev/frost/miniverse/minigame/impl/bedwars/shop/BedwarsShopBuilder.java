package dev.frost.miniverse.minigame.impl.bedwars.shop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.frost.miniverse.minigame.core.shop.ShopCategory;
import dev.frost.miniverse.minigame.core.shop.ShopItem;
import dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency;
import dev.frost.miniverse.minigame.impl.bedwars.shop.items.ArmorUpgradeItem;
import dev.frost.miniverse.minigame.impl.bedwars.shop.items.SimpleShopItem;
import dev.frost.miniverse.minigame.impl.bedwars.shop.items.TeamColoredBlockItem;
import dev.frost.miniverse.minigame.impl.bedwars.shop.items.TieredToolItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BedwarsShopBuilder {

    private static class DefaultCategory implements ShopCategory {
        private final Text name;
        private final ItemStack icon;
        private final List<ShopItem> items;

        public DefaultCategory(String name, ItemStack icon, List<ShopItem> items) {
            this.name = Text.literal(name);
            this.icon = icon;
            this.items = items;
        }

        @Override
        public Text getName() { return name; }

        @Override
        public ItemStack getIcon() { return icon; }

        @Override
        public List<ShopItem> getItems(ServerPlayerEntity player) { return items; }
    }
    
    public static class Registry {
        private final Map<String, ShopItem> items = new HashMap<>();

        public void register(String id, ShopItem item) {
            items.put(id.toLowerCase(), item);
        }

        public ShopItem get(String id) {
            if (id == null) return null;
            return items.get(id.toLowerCase());
        }
    }

    public static List<ShopCategory> buildShop(BedwarsShopManager manager, boolean is3s4s) {
        Registry registry = new Registry();
        
        // 1. Blocks
        registry.register("wool", new TeamColoredBlockItem(TeamColoredBlockItem.BlockType.WOOL, 16, 4, BedwarsCurrency.IRON).withName("Wool").withLore("Great for bridging across islands.", "Turns into your team's color."));
        registry.register("terracotta", new SimpleShopItem(new ItemStack(Items.TERRACOTTA, 16), 12, BedwarsCurrency.IRON).withName("Hardened Clay").withLore("Basic block to defend your bed."));
        registry.register("glass", new SimpleShopItem(new ItemStack(Items.GLASS, 4), 12, BedwarsCurrency.IRON).withName("Blast-Proof Glass").withLore("Immune to explosions."));
        registry.register("end_stone", new SimpleShopItem(new ItemStack(Items.END_STONE, 12), 24, BedwarsCurrency.IRON).withName("End Stone").withLore("Solid block to defend your bed."));
        registry.register("ladder", new SimpleShopItem(new ItemStack(Items.LADDER, 8), 4, BedwarsCurrency.IRON).withName("Ladder").withLore("Useful to save yourself if", "you fall off your bridge."));
        registry.register("oak_planks", new SimpleShopItem(new ItemStack(Items.OAK_PLANKS, 16), 4, BedwarsCurrency.GOLD).withName("Wood").withLore("Good block to defend your bed.", "Strong against pickaxes."));
        registry.register("obsidian", new SimpleShopItem(new ItemStack(Items.OBSIDIAN, 4), 4, BedwarsCurrency.EMERALD).withName("Obsidian").withLore("Extreme protection for your bed."));

        // 2. Melee
        registry.register("stone_sword", new SimpleShopItem(new ItemStack(Items.STONE_SWORD), 10, BedwarsCurrency.IRON).withName("Stone Sword"));
        registry.register("iron_sword", new SimpleShopItem(new ItemStack(Items.IRON_SWORD), 7, BedwarsCurrency.GOLD).withName("Iron Sword"));
        registry.register("diamond_sword", new SimpleShopItem(new ItemStack(Items.DIAMOND_SWORD), 4, BedwarsCurrency.EMERALD).withName("Diamond Sword"));
        registry.register("knockback_stick", new dev.frost.miniverse.minigame.impl.bedwars.shop.items.EnchantedShopItem(Items.STICK, 1, 5, BedwarsCurrency.GOLD).withEnchantment(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.of("knockback")), 1).withName("Knockback Stick (Knockback I)").withLore("Great for knocking enemies off edges."));

        // 3. Armor
        registry.register("chainmail_armor", new ArmorUpgradeItem(1, 40, BedwarsCurrency.IRON, player -> manager.getToolState(player.getUuid())));
        registry.register("iron_armor", new ArmorUpgradeItem(2, 12, BedwarsCurrency.GOLD, player -> manager.getToolState(player.getUuid())));
        registry.register("diamond_armor", new ArmorUpgradeItem(3, 6, BedwarsCurrency.EMERALD, player -> manager.getToolState(player.getUuid())));

        // 4. Tools
        registry.register("shears", new SimpleShopItem(new ItemStack(Items.SHEARS), 20, BedwarsCurrency.IRON).withName("Permanent Shears").withLore("Great to quickly mine wool.", "You do not lose this on death."));
        
        List<TieredToolItem.TierCost> pickaxeCosts = Arrays.asList(
            new TieredToolItem.TierCost(10, BedwarsCurrency.IRON),
            new TieredToolItem.TierCost(10, BedwarsCurrency.IRON),
            new TieredToolItem.TierCost(3, BedwarsCurrency.GOLD),
            new TieredToolItem.TierCost(6, BedwarsCurrency.GOLD)
        );
        registry.register("pickaxe", new TieredToolItem(TieredToolItem.ToolType.PICKAXE, pickaxeCosts, player -> manager.getToolState(player.getUuid())).withLore("Can be used to mine blocks", "except wood and glass.", "Downgrades on death.", "You will permanently respawn", "with at least the lowest tier."));
        
        List<TieredToolItem.TierCost> axeCosts = Arrays.asList(
            new TieredToolItem.TierCost(10, BedwarsCurrency.IRON),
            new TieredToolItem.TierCost(10, BedwarsCurrency.IRON),
            new TieredToolItem.TierCost(3, BedwarsCurrency.GOLD),
            new TieredToolItem.TierCost(6, BedwarsCurrency.GOLD)
        );
        registry.register("axe", new TieredToolItem(TieredToolItem.ToolType.AXE, axeCosts, player -> manager.getToolState(player.getUuid())).withLore("Can be used to mine wood.", "Downgrades on death.", "You will permanently respawn", "with at least the lowest tier."));

        // 5. Bows
        registry.register("arrow", new SimpleShopItem(new ItemStack(Items.ARROW, 8), 2, BedwarsCurrency.GOLD).withName("Arrows"));
        registry.register("bow", new SimpleShopItem(new ItemStack(Items.BOW), 12, BedwarsCurrency.GOLD).withName("Bow"));
        
        registry.register("power_bow", new dev.frost.miniverse.minigame.impl.bedwars.shop.items.EnchantedShopItem(Items.BOW, 1, 20, BedwarsCurrency.GOLD).withEnchantment(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.of("power")), 1).withName("Bow (Power I)"));
        
        registry.register("punch_bow", new dev.frost.miniverse.minigame.impl.bedwars.shop.items.EnchantedShopItem(Items.BOW, 1, 6, BedwarsCurrency.EMERALD)
            .withEnchantment(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.of("power")), 1)
            .withEnchantment(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.ENCHANTMENT, net.minecraft.util.Identifier.of("punch")), 1)
            .withName("Bow (Power I, Punch I)").withLore("A powerful ranged weapon", "that knocks enemies back."));

        // 6. Potions
        ItemStack jumpPotion = new ItemStack(Items.POTION);
        jumpPotion.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS, new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.LEAPING));
        List<net.minecraft.entity.effect.StatusEffectInstance> jumpEffects = new ArrayList<>();
        // Jump V for 45 seconds (900 ticks)
        jumpEffects.add(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.JUMP_BOOST, 900, 4));
        jumpPotion.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS, new net.minecraft.component.type.PotionContentsComponent(java.util.Optional.empty(), java.util.Optional.empty(), jumpEffects));
        registry.register("jump_potion", new SimpleShopItem(jumpPotion, 1, BedwarsCurrency.EMERALD).withName("Jump Boost V Potion (45 seconds)").withLore("Jump Boost V for 45 seconds.", "Great for traversing islands."));

        ItemStack speedPotion = new ItemStack(Items.POTION);
        List<net.minecraft.entity.effect.StatusEffectInstance> speedEffects = new ArrayList<>();
        // Speed II for 45 seconds (900 ticks)
        speedEffects.add(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SPEED, 900, 1));
        speedPotion.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS, new net.minecraft.component.type.PotionContentsComponent(java.util.Optional.empty(), java.util.Optional.empty(), speedEffects));
        registry.register("speed_potion", new SimpleShopItem(speedPotion, 1, BedwarsCurrency.EMERALD).withName("Speed II Potion (45 seconds)").withLore("Speed II for 45 seconds.", "Great for quickly traversing islands."));

        // 7. Utility
        registry.register("golden_apple", new SimpleShopItem(new ItemStack(Items.GOLDEN_APPLE), 3, BedwarsCurrency.GOLD).withName("Golden Apple").withLore("Well-rounded healing."));
        registry.register("water_bucket", new SimpleShopItem(new ItemStack(Items.WATER_BUCKET), is3s4s ? 3 : 2, BedwarsCurrency.GOLD).withName("Water Bucket").withLore("Great to slow down enemies.", "Can also be used against TNT."));
        registry.register("fireball", new SimpleShopItem(new ItemStack(Items.FIRE_CHARGE), 40, BedwarsCurrency.IRON).withName("Fireball").withLore("Right-click to launch!", "Great to knock back enemies", "or destroy beds' defenses."));
        registry.register("tnt", new SimpleShopItem(new ItemStack(Items.TNT), is3s4s ? 8 : 4, BedwarsCurrency.GOLD).withName("TNT").withLore("Instantly ignites, appropriate to explode things!"));
        registry.register("bedbug", new SimpleShopItem(new ItemStack(Items.SNOWBALL), 24, BedwarsCurrency.IRON).withName("Bed Bug (Silverfish Snowball)").withLore("Spawns silverfish where the snowball", "lands to distract enemies.", "Lasts 15 seconds."));
        registry.register("dream_defender", new SimpleShopItem(new ItemStack(Items.IRON_GOLEM_SPAWN_EGG), 120, BedwarsCurrency.IRON).withName("Dream Defender (Iron Golem)").withLore("Iron Golem to defend your base.", "Lasts 4 minutes."));
        registry.register("ender_pearl", new SimpleShopItem(new ItemStack(Items.ENDER_PEARL), 4, BedwarsCurrency.EMERALD).withName("Ender Pearl").withLore("The quickest way to invade enemy bases."));
        registry.register("bridge_egg", new SimpleShopItem(new ItemStack(Items.EGG), 1, BedwarsCurrency.EMERALD).withName("Bridge Egg").withLore("This egg creates a bridge", "in its trail after being thrown."));
        registry.register("magic_milk", new SimpleShopItem(new ItemStack(Items.MILK_BUCKET), 4, BedwarsCurrency.GOLD).withName("Magic Milk").withLore("Avoid triggering traps for 30", "seconds after consuming."));
        registry.register("sponge", new SimpleShopItem(new ItemStack(Items.SPONGE, 4), is3s4s ? 3 : 2, BedwarsCurrency.GOLD).withName("Sponge").withLore("Great for soaking up water."));
        registry.register("pop_up_tower", new SimpleShopItem(new ItemStack(Items.CHEST), 24, BedwarsCurrency.IRON).withName("Compact Pop-up Tower").withLore("Place a pop-up defense!"));

        
        List<ShopCategory> categories = new ArrayList<>();
        
        // 0. Quick Buy
        categories.add(new ShopCategory() {
            @Override
            public Text getName() { return Text.literal("Quick Buy"); }
            @Override
            public ItemStack getIcon() { return new ItemStack(Items.NETHER_STAR); }
            @Override
            public List<ShopItem> getItems(ServerPlayerEntity player) {
                List<ShopItem> list = new ArrayList<>();
                for (String id : manager.getQuickBuyService().load(player.getUuid())) {
                    list.add(registry.get(id));
                }
                return list;
            }
        });
        
        // 1. Blocks
        categories.add(new DefaultCategory("Blocks", new ItemStack(Items.TERRACOTTA), Arrays.asList(
            registry.get("wool"), registry.get("terracotta"), registry.get("glass"),
            registry.get("end_stone"), registry.get("ladder"), registry.get("oak_planks"), registry.get("obsidian")
        )));
        
        // 2. Melee
        categories.add(new DefaultCategory("Melee", new ItemStack(Items.GOLDEN_SWORD), Arrays.asList(
            registry.get("stone_sword"), registry.get("iron_sword"), registry.get("diamond_sword"), registry.get("knockback_stick")
        )));

        // 3. Armor
        categories.add(new DefaultCategory("Armor", new ItemStack(Items.CHAINMAIL_BOOTS), Arrays.asList(
            registry.get("chainmail_armor"), registry.get("iron_armor"), registry.get("diamond_armor")
        )));
        
        // 4. Tools
        categories.add(new DefaultCategory("Tools", new ItemStack(Items.STONE_PICKAXE), Arrays.asList(
            registry.get("shears"), registry.get("pickaxe"), registry.get("axe")
        )));
        
        // 5. Bows
        categories.add(new DefaultCategory("Bows", new ItemStack(Items.BOW), Arrays.asList(
            registry.get("arrow"), registry.get("bow"), registry.get("power_bow"), registry.get("punch_bow")
        )));
        
        // 6. Potions
        categories.add(new DefaultCategory("Potions", new ItemStack(Items.GLASS_BOTTLE), Arrays.asList(
            registry.get("speed_potion"), registry.get("jump_potion")
        )));
        
        // 7. Utility
        categories.add(new DefaultCategory("Utility", new ItemStack(Items.TNT), Arrays.asList(
            registry.get("golden_apple"), registry.get("bedbug"), registry.get("dream_defender"), registry.get("fireball"), registry.get("tnt"), registry.get("ender_pearl"), registry.get("water_bucket"), registry.get("bridge_egg"), registry.get("magic_milk"), registry.get("sponge"), registry.get("pop_up_tower")
        )));
        
        return categories;
    }

    private static ItemStack createKnockbackStick() {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.addEnchantment(net.minecraft.registry.BuiltinRegistries.createWrapperLookup().getWrapperOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT).getOrThrow(net.minecraft.enchantment.Enchantments.KNOCKBACK), 1);
        return stack;
    }
}
