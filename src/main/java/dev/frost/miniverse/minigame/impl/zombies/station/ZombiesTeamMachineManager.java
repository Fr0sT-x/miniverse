package dev.frost.miniverse.minigame.impl.zombies.station;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieEntityManager;
import dev.frost.miniverse.minigame.impl.zombies.revive.ZombiesReviveManager;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponItemHelper;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ZombiesTeamMachineManager {
    public static final int COST_AMMO_REFILL = 500;
    public static final int COST_TEAM_REVIVE = 1500;
    public static final int COST_DRAGONS_WRATH = 2000;

    private final ServerWorld world;
    private final ZombieEntityManager mobManager;
    private final ZombiesReviveManager reviveManager;
    private final Supplier<List<ServerPlayerEntity>> survivorsSupplier;
    private final BiPredicate<ServerPlayerEntity, Integer> goldSpender;
    private final Consumer<Text> broadcastCallback;

    public ZombiesTeamMachineManager(
        ServerWorld world,
        ZombieEntityManager mobManager,
        ZombiesReviveManager reviveManager,
        Supplier<List<ServerPlayerEntity>> survivorsSupplier,
        BiPredicate<ServerPlayerEntity, Integer> goldSpender,
        Consumer<Text> broadcastCallback
    ) {
        this.world = world;
        this.mobManager = mobManager;
        this.reviveManager = reviveManager;
        this.survivorsSupplier = survivorsSupplier;
        this.goldSpender = goldSpender;
        this.broadcastCallback = broadcastCallback;
    }

    public void openTeamMachine(ServerPlayerEntity player, int playerGold) {
        SimpleInventory inv = new SimpleInventory(9);

        // Slot 2: Team Ammo
        ItemStack ammo = new ItemStack(Items.CHEST);
        ammo.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Team Ammo Refill").formatted(Formatting.AQUA, Formatting.BOLD));
        ammo.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Refills reserve ammo for all survivors.").formatted(Formatting.GRAY),
            Text.literal("Cost: ").formatted(Formatting.YELLOW).append(Text.literal(COST_AMMO_REFILL + " Gold").formatted(Formatting.GOLD))
        )));
        inv.setStack(2, ammo);

        // Slot 4: Team Revive
        ItemStack revive = new ItemStack(Items.GOLDEN_APPLE);
        revive.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Team Revive").formatted(Formatting.GREEN, Formatting.BOLD));
        revive.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Revives all downed & spectator teammates!").formatted(Formatting.GRAY),
            Text.literal("Cost: ").formatted(Formatting.YELLOW).append(Text.literal(COST_TEAM_REVIVE + " Gold").formatted(Formatting.GOLD))
        )));
        inv.setStack(4, revive);

        // Slot 6: Dragon's Wrath
        ItemStack wrath = new ItemStack(Items.BLAZE_POWDER);
        wrath.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Dragon's Wrath").formatted(Formatting.RED, Formatting.BOLD));
        wrath.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Strikes lightning and deals heavy damage to all zombies!").formatted(Formatting.GRAY),
            Text.literal("Cost: ").formatted(Formatting.YELLOW).append(Text.literal(COST_DRAGONS_WRATH + " Gold").formatted(Formatting.GOLD))
        )));
        inv.setStack(6, wrath);

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X1, syncId, playerInv, inv, 1) {
                @Override
                public void onSlotClick(int slotIndex, int button, SlotActionType actionType, net.minecraft.entity.player.PlayerEntity p2) {
                    if (slotIndex >= 0 && slotIndex < 9) {
                        if (actionType == SlotActionType.PICKUP) {
                            handlePurchase((ServerPlayerEntity) p2, slotIndex);
                        }
                        this.sendContentUpdates();
                    } else if (actionType == SlotActionType.QUICK_MOVE || actionType == SlotActionType.SWAP) {
                        this.sendContentUpdates();
                    } else {
                        super.onSlotClick(slotIndex, button, actionType, p2);
                    }
                }
            },
            Text.literal("Team Machine (Gold: " + playerGold + ")")
        ));
    }

    private void handlePurchase(ServerPlayerEntity player, int slotIndex) {
        if (slotIndex == 2) {
            // Team Ammo
            if (!this.goldSpender.test(player, COST_AMMO_REFILL)) {
                player.sendMessage(Text.literal("Not enough gold! (" + COST_AMMO_REFILL + "g required)").formatted(Formatting.RED), true);
                return;
            }
            player.closeHandledScreen();
            this.broadcastCallback.accept(Text.literal("⚡ " + player.getName().getString() + " bought Team Ammo Refill!").formatted(Formatting.AQUA, Formatting.BOLD));
            for (ServerPlayerEntity p : this.survivorsSupplier.get()) {
                for (int i = 0; i < p.getInventory().size(); i++) {
                    ItemStack stack = p.getInventory().getStack(i);
                    WeaponType wt = WeaponItemHelper.getWeaponType(stack);
                    if (wt != null && !wt.getData().isMelee()) {
                        WeaponItemHelper.refillAmmo(stack, wt);
                    }
                }
                p.playSound(SoundEvents.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            }
        } else if (slotIndex == 4) {
            // Team Revive
            if (!this.goldSpender.test(player, COST_TEAM_REVIVE)) {
                player.sendMessage(Text.literal("Not enough gold! (" + COST_TEAM_REVIVE + "g required)").formatted(Formatting.RED), true);
                return;
            }
            player.closeHandledScreen();
            this.broadcastCallback.accept(Text.literal("❤ " + player.getName().getString() + " bought Team Revive!").formatted(Formatting.GREEN, Formatting.BOLD));
            this.reviveManager.respawnAllAtRoundEnd(this.survivorsSupplier.get(), null);
        } else if (slotIndex == 6) {
            // Dragon's Wrath
            if (!this.goldSpender.test(player, COST_DRAGONS_WRATH)) {
                player.sendMessage(Text.literal("Not enough gold! (" + COST_DRAGONS_WRATH + "g required)").formatted(Formatting.RED), true);
                return;
            }
            player.closeHandledScreen();
            this.broadcastCallback.accept(Text.literal("☄ " + player.getName().getString() + " unleashed Dragon's Wrath!").formatted(Formatting.RED, Formatting.BOLD));

            for (ZombieEntityManager.ActiveMob mob : this.mobManager.getActiveMobs()) {
                LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, this.world);
                bolt.setPosition(mob.entity.getX(), mob.entity.getY(), mob.entity.getZ());
                this.world.spawnEntity(bolt);
                mob.entity.damage(this.world.getDamageSources().lightningBolt(), 50.0f);
            }
        }
    }
}
