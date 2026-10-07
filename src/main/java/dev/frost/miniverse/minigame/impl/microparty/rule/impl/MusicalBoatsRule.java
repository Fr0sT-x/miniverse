package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class MusicalBoatsRule implements MicroRule {
    private final List<BoatEntity> spawnedBoats = new ArrayList<>();
    private final List<ArmorStandEntity> spawnedDummies = new ArrayList<>();
    private final Set<UUID> acknowledgedSeated = new HashSet<>();
    private final Random random = new Random();

    @Override
    public String id() {
        return "musical_boats";
    }

    @Override
    public String name() {
        return "Musical Boats";
    }

    @Override
    public String description() {
        return "Right-click to get inside an empty boat seat before time expires!";
    }

    @Override
    public Text title() {
        return Text.literal("MUSICAL BOATS!").formatted(Formatting.BLUE, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Get inside an empty boat seat!").formatted(Formatting.YELLOW);
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
        this.spawnedBoats.clear();
        this.spawnedDummies.clear();
        this.acknowledgedSeated.clear();

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int livingCount = game.getLivingPlayers().size();
        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int margin = Math.max(1, bounds.width() / 6);

        // Calculate boats and seats so exactly (livingCount - 1) seats are available for players
        int seatsNeeded = Math.max(1, livingCount <= 1 ? 1 : livingCount - 1);
        int boatsToSpawn = Math.max(1, (seatsNeeded + 1) / 2);
        int extraSeatsToBlock = (boatsToSpawn * 2) - seatsNeeded;

        for (int i = 0; i < boatsToSpawn; i++) {
            double bx = bounds.minX() + margin + random.nextDouble() * Math.max(1, bounds.width() - 2 * margin);
            double bz = bounds.minZ() + margin + random.nextDouble() * Math.max(1, bounds.depth() - 2 * margin);

            BoatEntity boat = new BoatEntity(EntityType.BOAT, world);
            boat.refreshPositionAndAngles(bx, floorY, bz, random.nextFloat() * 360f, 0f);
            world.spawnEntity(boat);
            this.spawnedBoats.add(boat);

            // Block extra seats with a dummy passenger if needed
            if (extraSeatsToBlock > 0) {
                ArmorStandEntity dummy = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
                dummy.refreshPositionAndAngles(bx, floorY, bz, 0f, 0f);
                dummy.setCustomName(Text.literal("§c§lSEAT TAKEN"));
                dummy.setCustomNameVisible(true);
                dummy.setInvulnerable(true);
                dummy.setNoGravity(true);
                dummy.setInvisible(false);
                world.spawnEntity(dummy);
                dummy.startRiding(boat, true);

                this.spawnedDummies.add(dummy);
                extraSeatsToBlock--;
            }
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (p.getVehicle() instanceof BoatEntity boat && this.spawnedBoats.contains(boat)) {
                if (this.acknowledgedSeated.add(p.getUuid())) {
                    p.sendMessage(Text.literal("§a§l✔ Seated in Boat!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return player.getVehicle() instanceof BoatEntity boat && this.spawnedBoats.contains(boat);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.stopRiding();
        }
        for (ArmorStandEntity dummy : this.spawnedDummies) {
            dummy.discard();
        }
        for (BoatEntity boat : this.spawnedBoats) {
            boat.discard();
        }
        this.spawnedDummies.clear();
        this.spawnedBoats.clear();
        this.acknowledgedSeated.clear();
    }
}
