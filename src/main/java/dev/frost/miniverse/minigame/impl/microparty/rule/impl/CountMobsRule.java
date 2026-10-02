package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyArenaHelper;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;

import java.util.*;

public class CountMobsRule implements MicroRule {
    private final Set<UUID> passedPlayers = new HashSet<>();
    private final List<SheepEntity> spawnedSheep = new ArrayList<>();
    private final Random random = new Random();

    private int targetCount = 5;
    private boolean sheepDespawned = false;
    private int despawnAtTick = 40;

    @Override
    public String id() {
        return "count_mobs";
    }

    @Override
    public String name() {
        return "Count the Sheep";
    }

    @Override
    public String description() {
        return "Count how many glowing sheep appear in the arena and type the number in chat!";
    }

    @Override
    public Text title() {
        return Text.literal("COUNT THE SHEEP!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Type the exact number in chat!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public int getDurationTicks(MicroFrenzyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(7 * 20 * factor);
        return Math.max(70, standardTicks); // Clamped to at least 3.5 seconds
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
        this.spawnedSheep.clear();
        this.sheepDespawned = false;

        ServerWorld world = game.getWorld();
        if (world == null) return;

        this.targetCount = 3 + random.nextInt(5); // 3 to 7 sheep
        int totalTicks = getDurationTicks(game);
        this.despawnAtTick = Math.max(25, totalTicks / 2);

        int floorY = MicroFrenzyArenaHelper.getFloorY(game.getMapConfig());
        MicroFrenzyArenaHelper.ArenaBounds2D bounds = MicroFrenzyArenaHelper.getBounds2D(game.getMapConfig());
        int margin = Math.max(1, bounds.width() / 6);

        for (int i = 0; i < this.targetCount; i++) {
            int x = bounds.minX() + margin + random.nextInt(Math.max(1, bounds.width() - 2 * margin));
            int z = bounds.minZ() + margin + random.nextInt(Math.max(1, bounds.depth() - 2 * margin));

            SheepEntity sheep = new SheepEntity(EntityType.SHEEP, world);
            sheep.refreshPositionAndAngles(x + 0.5, floorY, z + 0.5, random.nextFloat() * 360f, 0f);
            sheep.setColor(DyeColor.PINK);
            sheep.setGlowing(true);
            sheep.setInvulnerable(true);
            sheep.setAiDisabled(true);

            world.spawnEntity(sheep);
            this.spawnedSheep.add(sheep);
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        if (!this.sheepDespawned && remainingTicks <= this.despawnAtTick) {
            this.sheepDespawned = true;
            for (SheepEntity sheep : this.spawnedSheep) {
                if (sheep.isAlive()) {
                    world.spawnParticles(ParticleTypes.POOF, sheep.getX(), sheep.getY() + 0.5, sheep.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                    sheep.discard();
                }
            }
        }
    }

    @Override
    public boolean onChatMessage(ServerPlayerEntity player, String message, MicroFrenzyMinigame game) {
        if (player == null || message == null || game.isEliminated(player.getUuid()) || !game.getTracker().isAlive(player.getUuid())) {
            return false;
        }

        if (this.passedPlayers.contains(player.getUuid())) {
            return true; // Already passed, consume chat quietly
        }

        String trimmed = message.trim();
        if (trimmed.startsWith("!")) {
            trimmed = trimmed.substring(1).trim();
        }

        int guess;
        try {
            guess = Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return false; // Not a number, allow standard chat
        }

        if (guess == this.targetCount) {
            this.passedPlayers.add(player.getUuid());
            player.sendMessage(Text.literal("§a§l✔ Correct! (" + this.targetCount + ")"), true);
            player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
            return true; // SUPPRESS chat message silently!
        } else {
            player.sendMessage(Text.literal("§c§l✖ Wrong count!"), true);
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 1.0f, 0.6f);
            return false; // ALLOW chat message to be broadcast publicly for everyone to see!
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return this.passedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        for (SheepEntity sheep : this.spawnedSheep) {
            if (sheep.isAlive()) {
                sheep.discard();
            }
        }
        this.spawnedSheep.clear();
        this.passedPlayers.clear();
        this.sheepDespawned = false;
    }
}
