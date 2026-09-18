package dev.frost.miniverse.minigame.impl.zombies.weapon;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public final class WeaponItemHelper {
    public static final String KEY_WEAPON_TYPE = "zombies_weapon";
    public static final String KEY_CLIP_AMMO = "zombies_clip";
    public static final String KEY_RESERVE_AMMO = "zombies_reserve";

    private WeaponItemHelper() {}

    public static ItemStack createWeaponStack(WeaponType type) {
        ItemStack stack = new ItemStack(type.getData().item());
        WeaponData data = type.getData();

        stack.set(DataComponentTypes.MAX_STACK_SIZE, 99);

        if (!data.isMelee()) {
            stack.setCount(Math.max(1, data.clipSize()));
        }

        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putString(KEY_WEAPON_TYPE, type.name());
            nbt.putInt(KEY_CLIP_AMMO, data.clipSize());
            nbt.putInt(KEY_RESERVE_AMMO, data.maxReserve());
        });

        updateStackLore(stack, type, data.clipSize(), data.maxReserve(), false);
        dev.frost.miniverse.minigame.core.item.ProtectedItemTags.mark(
            stack,
            data.isMelee() ? dev.frost.miniverse.minigame.core.item.ProtectedItemTypes.ZOMBIES_KNIFE : dev.frost.miniverse.minigame.core.item.ProtectedItemTypes.ZOMBIES_WEAPON
        );
        return stack;
    }

    public static WeaponType getWeaponType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null || !comp.contains(KEY_WEAPON_TYPE)) return null;
        String name = comp.copyNbt().getString(KEY_WEAPON_TYPE);
        return WeaponType.fromString(name);
    }

    public static int getClipAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null || !comp.contains(KEY_CLIP_AMMO)) return 0;
        return comp.copyNbt().getInt(KEY_CLIP_AMMO);
    }

    public static int getReserveAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null || !comp.contains(KEY_RESERVE_AMMO)) return 0;
        return comp.copyNbt().getInt(KEY_RESERVE_AMMO);
    }

    public static void setAmmo(ItemStack stack, WeaponType type, int clip, int reserve, boolean reloading) {
        if (type != null && !type.getData().isMelee()) {
            stack.setCount(Math.max(1, clip));
        }
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
            nbt.putString(KEY_WEAPON_TYPE, type.name());
            nbt.putInt(KEY_CLIP_AMMO, clip);
            nbt.putInt(KEY_RESERVE_AMMO, reserve);
        });
        updateStackLore(stack, type, clip, reserve, reloading);
        if (type != null) {
            dev.frost.miniverse.minigame.core.item.ProtectedItemTags.mark(
                stack,
                type.getData().isMelee() ? dev.frost.miniverse.minigame.core.item.ProtectedItemTypes.ZOMBIES_KNIFE : dev.frost.miniverse.minigame.core.item.ProtectedItemTypes.ZOMBIES_WEAPON
            );
        }
    }

    public static void refillAmmo(ItemStack stack, WeaponType type) {
        setAmmo(stack, type, type.getData().clipSize(), type.getData().maxReserve(), false);
    }

    public static void updateStackLore(ItemStack stack, WeaponType type, int clip, int reserve, boolean reloading) {
        WeaponData data = type.getData();
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(data.displayName()).formatted(Formatting.GOLD, Formatting.BOLD));

        List<Text> lore = new ArrayList<>();
        lore.add(Text.literal("Damage: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(data.damage())).formatted(Formatting.RED)));

        if (data.isMelee()) {
            lore.add(Text.literal("Type: ").formatted(Formatting.GRAY)
                .append(Text.literal("Melee Weapon").formatted(Formatting.YELLOW)));
        } else {
            if (reloading) {
                lore.add(Text.literal("Ammo: ").formatted(Formatting.GRAY)
                    .append(Text.literal("[RELOADING...]").formatted(Formatting.RED, Formatting.ITALIC)));
            } else {
                lore.add(Text.literal("Ammo: ").formatted(Formatting.GRAY)
                    .append(Text.literal("[" + clip + " / " + reserve + "]").formatted(Formatting.GREEN)));
            }
            if (data.isPiercing()) {
                lore.add(Text.literal("✦ Piercing Bullets").formatted(Formatting.AQUA));
            }
        }

        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
    }

    public static void sendWeaponSpecSheet(net.minecraft.server.network.ServerPlayerEntity player, WeaponType targetType, String titleText) {
        if (player == null || targetType == null) return;
        WeaponData d = targetType.getData();
        player.sendMessage(Text.literal("═════════════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal(titleText).formatted(Formatting.GREEN, Formatting.BOLD), false);
        player.sendMessage(Text.literal("  Damage: ").formatted(Formatting.GRAY).append(Text.literal(String.format("%.1f HP", d.damage())).formatted(Formatting.WHITE)), false);
        if (!d.isMelee()) {
            player.sendMessage(Text.literal("  Total ammo: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(d.clipSize() + d.maxReserve())).formatted(Formatting.WHITE)), false);
            player.sendMessage(Text.literal("  Magazine ammo: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(d.clipSize())).formatted(Formatting.WHITE)), false);
            player.sendMessage(Text.literal("  Fire Rate: ").formatted(Formatting.GRAY).append(Text.literal(String.format("%.2fs", d.delayTicks() / 20.0f)).formatted(Formatting.WHITE)), false);
            player.sendMessage(Text.literal("  Reload: ").formatted(Formatting.GRAY).append(Text.literal(String.format("%.2fs", d.reloadTicks() / 20.0f)).formatted(Formatting.WHITE)), false);
            if (d.bulletsPerShot() > 1) {
                player.sendMessage(Text.literal("  Pellets: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(d.bulletsPerShot())).formatted(Formatting.WHITE)), false);
            }
            if (d.isPiercing()) {
                player.sendMessage(Text.literal("  Piercing: ").formatted(Formatting.GRAY).append(Text.literal("Up to " + d.pierceLimit() + " mobs").formatted(Formatting.AQUA)), false);
            }
            if (targetType == WeaponType.ROCKET_LAUNCHER || targetType == WeaponType.NUKE_LAUNCHER) {
                player.sendMessage(Text.literal("  Special: ").formatted(Formatting.GRAY).append(Text.literal("Splash Damage (explosive blast radius)").formatted(Formatting.GOLD)), false);
            }
            if (targetType == WeaponType.GOLD_DIGGER) {
                player.sendMessage(Text.literal("  Special: ").formatted(Formatting.GRAY).append(Text.literal("Bonus Gold (+15g per hit)").formatted(Formatting.YELLOW)), false);
            }
        }
        player.sendMessage(Text.literal("═════════════════════════════════").formatted(Formatting.GOLD), false);
    }
}
