package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMapConfig;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class HotPotatoRule implements MicroRule {
    private final Random random = new Random();
    private UUID currentHolderUuid = null;

    @Override
    public String id() {
        return "hot_potato";
    }

    @Override
    public String name() {
        return "Hot Potato";
    }

    @Override
    public String description() {
        return "Punch someone to pass the burning TNT before it detonates.";
    }

    @Override
    public Text title() {
        return Text.literal("HOT POTATO!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Punch someone to pass the TNT before it explodes!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public int getDurationTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(8 * 20 * factor);
        return Math.max(100, standardTicks); // Clamped to at least 5.0 seconds (100 ticks)
    }

    @Override
    public boolean isApplicable(MicroPartyMapConfig mapConfig) {
        return true;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.currentHolderUuid = null;

        List<ServerPlayerEntity> living = game.getLivingPlayers();
        if (living.size() < 2) {
            return;
        }

        ServerPlayerEntity holder = living.get(random.nextInt(living.size()));
        this.currentHolderUuid = holder.getUuid();

        holder.equipStack(EquipmentSlot.HEAD, new ItemStack(Blocks.TNT));
        // Give holder speed boost to chase players effectively
        holder.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1, false, false, false));
        holder.playSoundToPlayer(SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 1.0f, 1.0f);
        holder.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§c§l💥 YOU HAVE THE HOT POTATO!")));
        holder.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§ePunch someone to pass it!")));

        GameMessenger.broadcast(living, Text.literal("§e" + holder.getName().getString() + " §7has the §c§lHot Potato§7! Run!"));
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        if (this.currentHolderUuid == null) return;

        ServerPlayerEntity holder = server.getPlayerManager().getPlayer(this.currentHolderUuid);
        if (holder != null && holder.isAlive()) {
            holder.getServerWorld().spawnParticles(ParticleTypes.FLAME, holder.getX(), holder.getY() + 2.0, holder.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
            holder.getServerWorld().spawnParticles(ParticleTypes.SMOKE, holder.getX(), holder.getY() + 2.0, holder.getZ(), 2, 0.2, 0.1, 0.2, 0.01);

            if (remainingTicks % 8 == 0) {
                float pitch = 1.0f + (float) (160 - remainingTicks) / 100.0f;
                holder.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.8f, pitch);
            }
        }
    }

    @Override
    public void onPlayerAttack(ServerPlayerEntity attacker, Entity target, MicroPartyMinigame game) {
        if (this.currentHolderUuid == null) return;

        if (attacker.getUuid().equals(this.currentHolderUuid) && target instanceof ServerPlayerEntity victim && game.getLivingPlayers().contains(victim)) {
            attacker.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
            attacker.removeStatusEffect(StatusEffects.SPEED);
            this.currentHolderUuid = victim.getUuid();
            victim.equipStack(EquipmentSlot.HEAD, new ItemStack(Blocks.TNT));
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 1, false, false, false));

            attacker.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
            attacker.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("§a✔ Potato Passed!").formatted(Formatting.GREEN)));

            victim.playSoundToPlayer(SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 1.0f, 1.2f);
            victim.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("§c§l💥 HOT POTATO!")));
            victim.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("§ePunch someone quick!")));

            GameMessenger.broadcast(game.getLivingPlayers(), Text.literal("§c" + victim.getName().getString() + " §7now has the §c§lHot Potato§7!"));
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        if (this.currentHolderUuid == null) {
            return true;
        }
        return !player.getUuid().equals(this.currentHolderUuid);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        if (this.currentHolderUuid != null) {
            ServerPlayerEntity holder = server.getPlayerManager().getPlayer(this.currentHolderUuid);
            if (holder != null && holder.isAlive()) {
                holder.getServerWorld().spawnParticles(ParticleTypes.EXPLOSION, holder.getX(), holder.getY() + 1.0, holder.getZ(), 1, 0, 0, 0, 0);
                holder.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 1.2f);
                holder.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
                holder.removeStatusEffect(StatusEffects.SPEED);
            }
        }

        // Safety clear on all living players
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (p.getEquippedStack(EquipmentSlot.HEAD).isOf(Blocks.TNT.asItem())) {
                p.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
        }

        this.currentHolderUuid = null;
    }
}
