package dev.frost.miniverse.minigame.impl.horde.shop;

import dev.frost.miniverse.minigame.core.shop.ShopCategory;
import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import dev.frost.miniverse.minigame.core.shop.ShopItem;
import dev.frost.miniverse.minigame.impl.bedwars.shop.items.SimpleShopItem;
import dev.frost.miniverse.minigame.impl.horde.HordeSurvivalMinigame;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class HordeShopProvider {

    private HordeShopProvider() {}

    public static List<ShopCategory> createCategories(HordeSurvivalMinigame minigame, boolean isEmergency) {
        ShopCurrency coins = minigame.getCurrency();

        List<ShopCategory> categories = new ArrayList<>();

        // Category 1: Weapons & Armor
        categories.add(new ShopCategory() {
            @Override
            public Text getName() {
                return Text.literal("Weapons & Armor").formatted(Formatting.AQUA);
            }

            @Override
            public ItemStack getIcon() {
                return new ItemStack(Items.IRON_SWORD);
            }

            @Override
            public List<ShopItem> getItems(ServerPlayerEntity player) {
                List<ShopItem> items = new ArrayList<>();
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.IRON_SWORD), 50, coins).withName("Iron Sword"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.DIAMOND_SWORD), 150, coins).withName("Diamond Sword"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.BOW), 60, coins).withName("Hunting Bow"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.CROSSBOW), 70, coins).withName("Heavy Crossbow"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.ARROW, 16), 25, coins).withName("Arrows (x16)"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.SHIELD), 35, coins).withName("Reinforced Shield"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.IRON_HELMET), 40, coins).withName("Iron Helmet"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.IRON_CHESTPLATE), 90, coins).withName("Iron Chestplate"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.IRON_LEGGINGS), 75, coins).withName("Iron Leggings"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.IRON_BOOTS), 40, coins).withName("Iron Boots"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.DIAMOND_CHESTPLATE), 250, coins).withName("Diamond Chestplate"), isEmergency, player, minigame));
                return items;
            }
        });

        // Category 2: Supplies & Healing
        categories.add(new ShopCategory() {
            @Override
            public Text getName() {
                return Text.literal("Supplies & Healing").formatted(Formatting.RED);
            }

            @Override
            public ItemStack getIcon() {
                return new ItemStack(Items.GOLDEN_APPLE);
            }

            @Override
            public List<ShopItem> getItems(ServerPlayerEntity player) {
                List<ShopItem> items = new ArrayList<>();
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.COOKED_BEEF, 8), 20, coins).withName("Cooked Steak (x8)"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.GOLDEN_APPLE), 60, coins).withName("Golden Apple"), isEmergency, player, minigame));

                ItemStack healSplash = new ItemStack(Items.SPLASH_POTION);
                healSplash.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.STRONG_HEALING));
                items.add(wrap(new SimpleShopItem(healSplash, 50, coins).withName("Splash Potion of Healing II"), isEmergency, player, minigame));

                ItemStack regenPot = new ItemStack(Items.POTION);
                regenPot.set(DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.REGENERATION));
                items.add(wrap(new SimpleShopItem(regenPot, 40, coins).withName("Potion of Regeneration"), isEmergency, player, minigame));

                items.add(wrap(new SimpleShopItem(new ItemStack(Items.ENDER_PEARL, 2), 75, coins).withName("Ender Pearls (x2)"), isEmergency, player, minigame));
                return items;
            }
        });

        // Category 3: Defenses & Tactical
        categories.add(new ShopCategory() {
            @Override
            public Text getName() {
                return Text.literal("Defenses & Tactical").formatted(Formatting.GOLD);
            }

            @Override
            public ItemStack getIcon() {
                return new ItemStack(Items.COBBLESTONE);
            }

            @Override
            public List<ShopItem> getItems(ServerPlayerEntity player) {
                List<ShopItem> items = new ArrayList<>();
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.COBBLESTONE, 32), 25, coins).withName("Cobblestone (x32)"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.OAK_PLANKS, 32), 20, coins).withName("Oak Planks (x32)"), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.OAK_FENCE, 16), 15, coins).withName("Oak Fences (x16)").withLore("Perimeter barricades to slow down mobs."), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.IRON_BARS, 16), 25, coins).withName("Iron Bars (x16)").withLore("Reinforced barricades resistant to miners."), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.COBWEB, 4), 30, coins).withName("Cobwebs (x4)").withLore("Slows down mob advances."), isEmergency, player, minigame));
                items.add(wrap(new SimpleShopItem(new ItemStack(Items.TORCH, 16), 10, coins).withName("Torches (x16)"), isEmergency, player, minigame));

                // Emergency Battery Pack item
                ItemStack batteryPack = new ItemStack(Items.REDSTONE_BLOCK);
                batteryPack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Emergency Battery Pack (+25% Fuel)").formatted(Formatting.GOLD, Formatting.BOLD));
                items.add(wrap(new ShopItem() {
                    @Override
                    public ItemStack getIcon(ServerPlayerEntity p) {
                        return batteryPack;
                    }

                    @Override
                    public int getCost(ServerPlayerEntity p) {
                        return 40;
                    }

                    @Override
                    public ShopCurrency getCurrency(ServerPlayerEntity p) {
                        return coins;
                    }

                    @Override
                    public boolean onPurchase(ServerPlayerEntity p) {
                        ItemStack batteryItem = new ItemStack(Items.REDSTONE_BLOCK);
                        batteryItem.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Emergency Battery Pack").formatted(Formatting.GOLD, Formatting.BOLD));
                        p.getInventory().offerOrDrop(batteryItem);
                        p.sendMessage(Text.literal("Acquired Battery Pack! Right-click the Transmitter Pod to add +25% Fuel.").formatted(Formatting.GREEN), false);
                        return true;
                    }
                }, isEmergency, player, minigame));

                if (!isEmergency && minigame.getSettings().emergencyFlaresEnabled()) {
                    items.add(wrap(new SimpleShopItem(EmergencyFlareItem.createStack(), 100, coins).withName("Emergency Merchant Flare").withLore("Summon a pocket vendor anywhere for 1 purchase."), isEmergency, player, minigame));
                }

                return items;
            }
        });

        return categories;
    }

    private static ShopItem wrap(ShopItem original, boolean isEmergency, ServerPlayerEntity player, HordeSurvivalMinigame minigame) {
        if (!isEmergency) return original;
        return new ShopItem() {
            @Override
            public ItemStack getIcon(ServerPlayerEntity p) {
                return original.getIcon(p);
            }

            @Override
            public int getCost(ServerPlayerEntity p) {
                return original.getCost(p);
            }

            @Override
            public ShopCurrency getCurrency(ServerPlayerEntity p) {
                return original.getCurrency(p);
            }

            @Override
            public boolean onPurchase(ServerPlayerEntity p) {
                boolean result = original.onPurchase(p);
                if (result) {
                    p.closeHandledScreen();
                    p.sendMessage(Text.literal("Emergency transaction complete! Merchant spirit vanished.").formatted(Formatting.YELLOW), true);
                }
                return result;
            }
        };
    }
}
