package dev.frost.miniverse.minigame.impl.zombies.station;

import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponData;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponItemHelper;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class ZombiesUltimateMachine {
    public static final int UPGRADE_COST = 1000;
    private final String id;
    private final BlockPos pos;

    public ZombiesUltimateMachine(BlockPos pos) {
        this("ultimate_machine", pos);
    }

    public ZombiesUltimateMachine(String id, BlockPos pos) {
        this.id = id != null && !id.isBlank() ? id : "ultimate_machine";
        this.pos = pos;
    }

    public String getId() {
        return this.id;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public boolean handleInteract(
        ServerPlayerEntity player,
        BlockPos clickedPos,
        boolean powerActive,
        BiPredicate<ServerPlayerEntity, Integer> goldSpender,
        Consumer<Text> broadcastCallback
    ) {
        boolean matches = clickedPos.equals(this.pos)
            || clickedPos.isWithinDistance(this.pos, 2.0)
            || clickedPos.equals(this.pos.north())
            || clickedPos.equals(this.pos.south())
            || clickedPos.equals(this.pos.east())
            || clickedPos.equals(this.pos.west());

        if (!matches) return false;

        if (!powerActive) {
            player.sendMessage(Text.literal("⚡ The power must be turned on first!").formatted(Formatting.YELLOW), true);
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
            return true;
        }

        ItemStack held = player.getMainHandStack();
        WeaponType current = WeaponItemHelper.getWeaponType(held);

        if (current == null || current.getData().isMelee()) {
            player.sendMessage(Text.literal("Hold a firearm in your main hand to upgrade it!").formatted(Formatting.RED), true);
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
            return true;
        }

        if (current.isUpgraded()) {
            player.sendMessage(Text.literal("This weapon is already upgraded!").formatted(Formatting.YELLOW), true);
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
            return true;
        }

        WeaponType upgraded = current.getUpgradedVersion();
        if (upgraded == null) {
            player.sendMessage(Text.literal("This weapon cannot be upgraded!").formatted(Formatting.RED), true);
            return true;
        }

        if (!goldSpender.test(player, UPGRADE_COST)) {
            player.sendMessage(Text.literal("Not enough gold! (" + UPGRADE_COST + "g required)").formatted(Formatting.RED), true);
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
            return true;
        }

        // Replace held weapon with upgraded variant
        ItemStack upgradedStack = WeaponItemHelper.createWeaponStack(upgraded);
        player.setStackInHand(player.getActiveHand(), upgradedStack);

        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.2f, 1.2f);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.4f);
        world.spawnParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.5, 0.5, 0.5, 0.1);

        // Spec sheet in chat
        WeaponData d = upgraded.getData();
        player.sendMessage(Text.literal("═════════════════════════════════").formatted(Formatting.LIGHT_PURPLE), false);
        player.sendMessage(Text.literal("★ WEAPON UPGRADED TO " + d.displayName().toUpperCase() + "! ★").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        player.sendMessage(Text.literal("  Damage: ").formatted(Formatting.GRAY).append(Text.literal(String.format("%.1f HP", d.damage())).formatted(Formatting.WHITE)), false);
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
        if (upgraded == WeaponType.NUKE_LAUNCHER) {
            player.sendMessage(Text.literal("  Special: ").formatted(Formatting.GRAY).append(Text.literal("Splash Damage (massive explosive blast radius)").formatted(Formatting.GOLD)), false);
        }
        player.sendMessage(Text.literal("═════════════════════════════════").formatted(Formatting.LIGHT_PURPLE), false);

        broadcastCallback.accept(Text.literal("★ " + player.getName().getString() + " upgraded their " + current.getData().displayName() + " into the " + d.displayName() + "!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        return true;
    }
}
