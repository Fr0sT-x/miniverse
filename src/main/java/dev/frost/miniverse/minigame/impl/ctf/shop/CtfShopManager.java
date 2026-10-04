package dev.frost.miniverse.minigame.impl.ctf.shop;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.shop.ShopCategory;
import dev.frost.miniverse.minigame.core.shop.ShopCurrency;
import dev.frost.miniverse.minigame.core.shop.ShopGui;
import dev.frost.miniverse.minigame.core.shop.ShopItem;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMapConfig;
import dev.frost.miniverse.minigame.impl.ctf.CaptureTheFlagMinigame;
import dev.frost.miniverse.minigame.impl.ctf.economy.CtfCurrency;
import dev.frost.miniverse.minigame.impl.ctf.economy.CtfEconomyManager;
import dev.frost.miniverse.minigame.impl.ctf.economy.CtfPlayerUpgradeState;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CtfShopManager {
    private final CaptureTheFlagMinigame minigame;
    private final CtfEconomyManager economyManager;
    private final List<UUID> npcIds = new ArrayList<>();
    private final List<ShopCategory> categories;

    public CtfShopManager(CaptureTheFlagMinigame minigame, CtfEconomyManager economyManager) {
        this.minigame = minigame;
        this.economyManager = economyManager;
        this.categories = buildCategories();
    }

    public void spawnNpcs(ServerWorld world, CaptureTheFlagMapConfig mapConfig) {
        this.clear(world.getServer());
        for (CaptureTheFlagMapConfig.CtfTeamConfig team : mapConfig.teams().values()) {
            for (MapPosition pos : team.shopNpcs) {
                VillagerEntity npc = new VillagerEntity(EntityType.VILLAGER, world);
                npc.setPosition(pos.x(), pos.y(), pos.z());
                npc.setYaw(pos.yaw());
                npc.setHeadYaw(pos.yaw());
                npc.setBodyYaw(pos.yaw());
                npc.setCustomName(Text.literal("CTF Shop").formatted(Formatting.GOLD, Formatting.BOLD));
                npc.setCustomNameVisible(true);
                npc.setAiDisabled(true);
                npc.setInvulnerable(true);
                npc.setSilent(true);
                world.spawnEntity(npc);
                this.npcIds.add(npc.getUuid());
            }
        }
    }

    public boolean handleInteract(ServerPlayerEntity player, Entity entity) {
        if (this.npcIds.contains(entity.getUuid())) {
            ShopGui.open(player, Text.literal("CTF Shop").formatted(Formatting.GOLD, Formatting.BOLD), this.categories);
            return true;
        }
        return false;
    }

    public void clear(MinecraftServer server) {
        if (server != null) {
            for (UUID uuid : this.npcIds) {
                for (ServerWorld world : server.getWorlds()) {
                    Entity e = world.getEntity(uuid);
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                }
            }
        }
        this.npcIds.clear();
    }

    private List<ShopCategory> buildCategories() {
        ShopCurrency coins = CtfCurrency.coins(this.economyManager);
        ShopCurrency gems = CtfCurrency.gems(this.economyManager);

        List<ShopCategory> list = new ArrayList<>();

        // 1. Blocks Category (Coins)
        list.add(new ShopCategory() {
            @Override
            public Text getName() { return Text.literal("Blocks"); }
            @Override
            public ItemStack getIcon() { return new ItemStack(Items.RED_WOOL); }
            @Override
            public List<ShopItem> getItems(ServerPlayerEntity player) {
                return List.of(
                    new SimpleItem(new ItemStack(Items.WHITE_WOOL, 16), 5, coins),
                    new SimpleItem(new ItemStack(Items.OAK_PLANKS, 16), 10, coins),
                    new SimpleItem(new ItemStack(Items.END_STONE, 12), 24, coins)
                );
            }
        });

        // 2. Combat & Consumables (Coins)
        list.add(new ShopCategory() {
            @Override
            public Text getName() { return Text.literal("Combat"); }
            @Override
            public ItemStack getIcon() { return new ItemStack(Items.GOLDEN_APPLE); }
            @Override
            public List<ShopItem> getItems(ServerPlayerEntity player) {
                ItemStack speedPot = new ItemStack(Items.POTION);
                speedPot.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.SWIFTNESS));

                ItemStack invisPot = new ItemStack(Items.POTION);
                invisPot.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.INVISIBILITY));

                ItemStack leapPot = new ItemStack(Items.POTION);
                leapPot.set(net.minecraft.component.DataComponentTypes.POTION_CONTENTS, new PotionContentsComponent(Potions.LEAPING));

                List<ShopItem> items = new ArrayList<>();
                items.add(new SimpleItem(new ItemStack(Items.GOLDEN_APPLE), 20, coins));
                items.add(new SimpleItem(new ItemStack(Items.ARROW, 8), 10, coins));
                items.add(new SimpleItem(speedPot, 40, coins));
                items.add(new SimpleItem(leapPot, 30, coins));
                if (minigame.getSettings().allowInvisibilityPotion()) {
                    items.add(new SimpleItem(invisPot, 70, coins));
                }
                return items;
            }
        });

        // 3. Tools & Utility (Coins)
        list.add(new ShopCategory() {
            @Override
            public Text getName() { return Text.literal("Tools & Utility"); }
            @Override
            public ItemStack getIcon() { return new ItemStack(Items.SHEARS); }
            @Override
            public List<ShopItem> getItems(ServerPlayerEntity player) {
                return List.of(
                    new ToolPurchaseItem(Items.SHEARS, "Shears", 15, coins, state -> state.setHasShears(true), CtfPlayerUpgradeState::hasShears),
                    new ToolPurchaseItem(Items.STONE_AXE, "Stone Axe", 30, coins, state -> state.setHasAxe(true), CtfPlayerUpgradeState::hasAxe),
                    new SimpleItem(new ItemStack(Items.TNT), 30, coins),
                    new SimpleItem(new ItemStack(Items.WIND_CHARGE, 2), 25, coins)
                );
            }
        });

        // 4. Permanent Upgrades (Gems)
        list.add(new ShopCategory() {
            @Override
            public Text getName() { return Text.literal("Upgrades"); }
            @Override
            public ItemStack getIcon() { return new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE); }
            @Override
            public List<ShopItem> getItems(ServerPlayerEntity player) {
                CtfPlayerUpgradeState state = economyManager.getUpgradeState(player.getUuid());
                return List.of(
                    new UpgradeItem("Armor Tier",
                        new Item[]{Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE},
                        new int[]{10, 20, 40, 80}, new String[]{"Chainmail", "Iron", "Diamond", "Netherite"},
                        state.getArmorTier(), gems, () -> {
                            state.upgradeArmor();
                            state.equipArmor(player, minigame.getPlayerTeamColor(player));
                        }),
                    new UpgradeItem("Sword Tier",
                        new Item[]{Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD},
                        new int[]{15, 25, 40, 60}, new String[]{"Stone", "Iron", "Diamond", "Netherite"},
                        state.getSwordTier(), gems, () -> {
                            state.upgradeSword();
                            player.getInventory().insertStack(state.buildSword(player));
                        }),
                    new UpgradeItem("Protection Enchant", Items.ENCHANTED_BOOK,
                        new int[]{10, 30, 60}, new String[]{"Protection I", "Protection II", "Protection III"},
                        state.getProtectionTier(), gems, () -> {
                            state.upgradeProtection();
                            state.equipArmor(player, minigame.getPlayerTeamColor(player));
                        }),
                    new UpgradeItem("Sharpness Enchant", Items.ENCHANTED_BOOK,
                        new int[]{30, 60}, new String[]{"Sharpness I", "Sharpness II"},
                        state.getSharpnessTier(), gems, state::upgradeSharpness),
                    new UpgradeItem("Power Enchant", Items.BOW,
                        new int[]{40}, new String[]{"Power I"},
                        state.getPowerTier(), gems, state::upgradePower)
                );
            }
        });

        return list;
    }

    private static class SimpleItem implements ShopItem {
        private final ItemStack stack;
        private final int cost;
        private final ShopCurrency currency;

        SimpleItem(ItemStack stack, int cost, ShopCurrency currency) {
            this.stack = stack;
            this.cost = cost;
            this.currency = currency;
        }

        @Override public ItemStack getIcon(ServerPlayerEntity player) { return stack.copy(); }
        @Override public int getCost(ServerPlayerEntity player) { return cost; }
        @Override public ShopCurrency getCurrency(ServerPlayerEntity player) { return currency; }
        @Override public boolean onPurchase(ServerPlayerEntity player) {
            ItemStack copy = stack.copy();
            player.getInventory().insertStack(copy);
            if (!copy.isEmpty()) {
                player.dropItem(copy, false);
            }
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
            return true;
        }
    }

    private class ToolPurchaseItem implements ShopItem {
        private final Item item;
        private final String name;
        private final int cost;
        private final ShopCurrency currency;
        private final java.util.function.Consumer<CtfPlayerUpgradeState> onApply;
        private final java.util.function.Predicate<CtfPlayerUpgradeState> alreadyHas;

        ToolPurchaseItem(Item item, String name, int cost, ShopCurrency currency,
                         java.util.function.Consumer<CtfPlayerUpgradeState> onApply,
                         java.util.function.Predicate<CtfPlayerUpgradeState> alreadyHas) {
            this.item = item;
            this.name = name;
            this.cost = cost;
            this.currency = currency;
            this.onApply = onApply;
            this.alreadyHas = alreadyHas;
        }

        @Override public ItemStack getIcon(ServerPlayerEntity player) {
            ItemStack s = new ItemStack(item);
            s.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            return s;
        }
        @Override public int getCost(ServerPlayerEntity player) { return cost; }
        @Override public ShopCurrency getCurrency(ServerPlayerEntity player) { return currency; }
        @Override public boolean onPurchase(ServerPlayerEntity player) {
            CtfPlayerUpgradeState state = economyManager.getUpgradeState(player.getUuid());
            if (alreadyHas.test(state)) {
                player.sendMessage(Text.literal("You already own this tool!").formatted(Formatting.RED), true);
                return false;
            }
            onApply.accept(state);
            player.getInventory().insertStack(new ItemStack(item));
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
            return true;
        }
    }

    private static class UpgradeItem implements ShopItem {
        private final String name;
        private final Item[] tierIcons;
        private final int[] costs;
        private final String[] tierNames;
        private final int currentTier;
        private final ShopCurrency currency;
        private final Runnable onUpgrade;

        UpgradeItem(String name, Item icon, int[] costs, String[] tierNames, int currentTier, ShopCurrency currency, Runnable onUpgrade) {
            this(name, new Item[]{icon}, costs, tierNames, currentTier, currency, onUpgrade);
        }

        UpgradeItem(String name, Item[] tierIcons, int[] costs, String[] tierNames, int currentTier, ShopCurrency currency, Runnable onUpgrade) {
            this.name = name;
            this.tierIcons = tierIcons;
            this.costs = costs;
            this.tierNames = tierNames;
            this.currentTier = currentTier;
            this.currency = currency;
            this.onUpgrade = onUpgrade;
        }

        @Override
        public ItemStack getIcon(ServerPlayerEntity player) {
            Item iconItem = tierIcons[Math.min(currentTier, tierIcons.length - 1)];
            ItemStack s = new ItemStack(iconItem);
            String title = currentTier < tierNames.length
                ? name + " -> " + tierNames[currentTier]
                : name + " (MAXED)";
            s.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(title).formatted(Formatting.AQUA));
            return s;
        }

        @Override
        public int getCost(ServerPlayerEntity player) {
            return currentTier < costs.length ? costs[currentTier] : 9999;
        }

        @Override
        public ShopCurrency getCurrency(ServerPlayerEntity player) {
            return currency;
        }

        @Override
        public boolean onPurchase(ServerPlayerEntity player) {
            if (currentTier >= costs.length) {
                player.sendMessage(Text.literal("Already at max tier!").formatted(Formatting.RED), true);
                return false;
            }
            onUpgrade.run();
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
            return true;
        }
    }
}
