package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
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
import net.minecraft.util.math.Box;

import java.util.*;

public class FishingHookRule implements MicroRule {
    private final Set<UUID> passedPlayers = new HashSet<>();
    private ArmorStandEntity targetDummy;

    @Override
    public String id() {
        return "fishing_hook";
    }

    @Override
    public String name() {
        return "Reel 'Em In";
    }

    @Override
    public String description() {
        return "Hook another player (or the target dummy) with your fishing rod!";
    }

    @Override
    public Text title() {
        return Text.literal("REEL 'EM IN!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Hook a player with your rod!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.0;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
        this.targetDummy = null;

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        double cx = (bounds.minX() + bounds.maxX()) / 2.0;
        double cz = (bounds.minZ() + bounds.maxZ()) / 2.0;

        // Give every player a fishing rod
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.getInventory().setStack(0, new ItemStack(Items.FISHING_ROD));
            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();
        }

        // Only spawn a dummy in center for solo testing / 1 player
        if (game.getLivingPlayers().size() <= 1) {
            ArmorStandEntity dummy = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
            dummy.refreshPositionAndAngles(cx, floorY, cz, 0f, 0f);
            dummy.setCustomName(Text.literal("§e§lHOOK ME!").formatted(Formatting.YELLOW));
            dummy.setCustomNameVisible(true);
            dummy.setGlowing(true);
            dummy.setInvulnerable(true);
            dummy.setNoGravity(true);
            world.spawnEntity(dummy);
            this.targetDummy = dummy;
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        Box arenaBox = new Box(bounds.minX(), floorY - 2, bounds.minZ(), bounds.maxX() + 1, floorY + 12, bounds.maxZ() + 1);

        List<FishingBobberEntity> bobbers = world.getEntitiesByClass(FishingBobberEntity.class, arenaBox, b -> b.isAlive());
        for (FishingBobberEntity bobber : bobbers) {
            if (bobber.getOwner() instanceof ServerPlayerEntity owner && !game.isEliminated(owner.getUuid()) && game.getTracker().isAlive(owner.getUuid())) {
                if (this.passedPlayers.contains(owner.getUuid())) continue;

                var hooked = bobber.getHookedEntity();
                if (hooked != null && hooked != owner) {
                    if (hooked instanceof ServerPlayerEntity || (this.targetDummy != null && hooked == this.targetDummy)) {
                        this.passedPlayers.add(owner.getUuid());
                        owner.sendMessage(Text.literal("§a§l✔ Hooked Target!"), true);
                        owner.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                        world.spawnParticles(ParticleTypes.SPLASH, hooked.getX(), hooked.getY() + 0.5, hooked.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                    }
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return this.passedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }
        if (this.targetDummy != null && this.targetDummy.isAlive()) {
            this.targetDummy.discard();
            this.targetDummy = null;
        }
        this.passedPlayers.clear();
    }
}
