package dev.frost.miniverse.minigame.impl.ctf.powerup;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.ctf.economy.CtfEconomyManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class CtfPowerupManager {
    private final List<PowerupPad> pads = new ArrayList<>();
    private final Random random = new Random();

    public void init(ServerWorld world, List<MapPosition> locations) {
        this.clear(world.getServer());
        for (MapPosition loc : locations) {
            PowerupPad pad = new PowerupPad(loc);
            this.pads.add(pad);
            this.spawnPowerup(world, pad);
        }
    }

    public void tick(ServerWorld world, Collection<ServerPlayerEntity> players, CtfEconomyManager economyManager) {
        for (PowerupPad pad : this.pads) {
            if (pad.activeType == null) {
                pad.respawnTicksRemaining--;
                if (pad.respawnTicksRemaining <= 0) {
                    this.spawnPowerup(world, pad);
                }
                continue;
            }

            // Visual effects
            if (world.getServer().getTicks() % 8 == 0) {
                world.spawnParticles(ParticleTypes.ENCHANTED_HIT,
                    pad.pos.x(), pad.pos.y() + 1.0, pad.pos.z(),
                    3, 0.2, 0.2, 0.2, 0.05);
            }

            // Player collection detection
            for (ServerPlayerEntity player : players) {
                if (!player.isAlive() || player.isSpectator()) continue;
                double distSq = player.squaredDistanceTo(pad.pos.x(), pad.pos.y(), pad.pos.z());
                if (distSq <= 2.25) { // 1.5 blocks
                    this.collectPowerup(world, player, pad, economyManager);
                    break;
                }
            }
        }
    }

    private void collectPowerup(ServerWorld world, ServerPlayerEntity player, PowerupPad pad, CtfEconomyManager economyManager) {
        CtfPowerupType type = pad.activeType;
        if (type == null) return;

        pad.activeType = null;
        pad.respawnTicksRemaining = 60 * 20; // 60s respawn
        this.clearPadEntities(world, pad);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.2F, 1.4F);

        player.sendMessage(Text.literal("✦ Collected ").append(type.getTitleText()).append("!"), true);

        switch (type) {
            case SPEED -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 30, 1));
            case STRENGTH -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 20, 0));
            case ABSORPTION -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 60, 1));
            case HEAL -> {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH, 1, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 8, 1));
            }
            case JUMP_BOOST -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20 * 30, 1));
            case COINS -> economyManager.addCoins(player, 30);
            case GEMS -> economyManager.addGems(player, 10);
            case BRIDGE_BALL -> {
                ItemStack eggs = new ItemStack(Items.EGG, 2);
                eggs.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
                    Text.literal("Bridge Egg").formatted(Formatting.AQUA, Formatting.BOLD));
                player.getInventory().insertStack(eggs);
            }
        }
    }

    private void spawnPowerup(ServerWorld world, PowerupPad pad) {
        this.clearPadEntities(world, pad);
        CtfPowerupType[] values = CtfPowerupType.values();
        CtfPowerupType type = values[random.nextInt(values.length)];
        pad.activeType = type;

        // Floating display armor stand
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.setPosition(pad.pos.x(), pad.pos.y(), pad.pos.z());
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.equipStack(EquipmentSlot.HEAD, type.createIconStack());
        world.spawnEntity(stand);
        pad.standUuid = stand.getUuid();

        // Hologram Title
        ArmorStandEntity holo = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        holo.setPosition(pad.pos.x(), pad.pos.y() + 1.2, pad.pos.z());
        holo.setInvisible(true);
        holo.setNoGravity(true);
        holo.setInvulnerable(true);
        holo.setCustomName(type.getTitleText());
        holo.setCustomNameVisible(true);
        world.spawnEntity(holo);
        pad.holoUuid = holo.getUuid();
    }

    private void clearPadEntities(ServerWorld world, PowerupPad pad) {
        if (pad.standUuid != null) {
            var e = world.getEntity(pad.standUuid);
            if (e != null && !e.isRemoved()) e.discard();
            pad.standUuid = null;
        }
        if (pad.holoUuid != null) {
            var e = world.getEntity(pad.holoUuid);
            if (e != null && !e.isRemoved()) e.discard();
            pad.holoUuid = null;
        }
    }

    public void clear(MinecraftServer server) {
        if (server != null) {
            for (PowerupPad pad : this.pads) {
                for (ServerWorld world : server.getWorlds()) {
                    clearPadEntities(world, pad);
                }
            }
        }
        this.pads.clear();
    }

    private static class PowerupPad {
        final MapPosition pos;
        CtfPowerupType activeType;
        int respawnTicksRemaining = 0;
        UUID standUuid;
        UUID holoUuid;

        PowerupPad(MapPosition pos) {
            this.pos = pos;
        }
    }
}
