package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMapConfig;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class CollectCoinRule implements MicroRule {
    private final Random random = new Random();
    private final List<ItemEntity> spawnedCoins = new ArrayList<>();
    private final Map<UUID, Integer> collectedCoins = new HashMap<>();

    @Override
    public String id() {
        return "collect_coin";
    }

    @Override
    public String name() {
        return "Collect Coins";
    }

    @Override
    public String description() {
        return "Run around and grab floating gold coins before time runs out.";
    }

    @Override
    public Text title() {
        return Text.literal("COLLECT COINS!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return instruction(null);
    }

    @Override
    public Text instruction(MicroPartyMinigame game) {
        int req = getRequiredCoins(game);
        if (req > 1) {
            return Text.literal("Collect " + req + " floating gold coins!").formatted(Formatting.YELLOW);
        } else {
            return Text.literal("Grab a floating gold coin!").formatted(Formatting.YELLOW);
        }
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public int getDurationTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(8 * 20 * factor);
        return Math.max(70, standardTicks); // Clamped to at least 3.5 seconds (70 ticks)
    }

    public static int getRequiredCoins(MicroPartyMinigame game) {
        if (game == null) return 2;
        return game.getSpeedFactor() >= 0.9f ? 2 : 1;
    }

    @Override
    public boolean isApplicable(MicroPartyMapConfig mapConfig) {
        return true;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.spawnedCoins.clear();
        this.collectedCoins.clear();

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());

        int req = getRequiredCoins(game);
        int livingCount = Math.max(1, game.getLivingPlayers().size());
        int toSpawn = Math.max(6, (livingCount * req) + 4);

        List<BlockPos> walkableSurface = MicroPartyArenaHelper.getWalkableFloorSurface(world, game.getMapConfig());

        if (!walkableSurface.isEmpty()) {
            List<BlockPos> shuffled = new ArrayList<>(walkableSurface);
            Collections.shuffle(shuffled, random);
            int count = Math.min(toSpawn, shuffled.size());
            for (int i = 0; i < count; i++) {
                BlockPos p = shuffled.get(i);
                spawnCoinAt(world, p.getX() + 0.5, floorY + 0.4, p.getZ() + 0.5);
            }
        } else {
            // Fallback random bounds positions
            int margin = Math.max(1, bounds.width() / 6);
            for (int i = 0; i < toSpawn; i++) {
                double cx = bounds.minX() + margin + random.nextDouble() * Math.max(1, bounds.width() - 2 * margin);
                double cz = bounds.minZ() + margin + random.nextDouble() * Math.max(1, bounds.depth() - 2 * margin);
                spawnCoinAt(world, cx, floorY + 0.4, cz);
            }
        }
    }

    private void spawnCoinAt(ServerWorld world, double x, double y, double z) {
        ItemEntity item = new ItemEntity(world, x, y, z, new ItemStack(Items.GOLD_INGOT));
        item.setNeverDespawn();
        item.setPickupDelay(32767); // Can't be collected by vanilla inventory pickup
        item.setVelocity(0, 0, 0);
        item.setNoGravity(true);
        world.spawnEntity(item);
        this.spawnedCoins.add(item);
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null || this.spawnedCoins.isEmpty()) return;

        // Sparkle particles
        if (remainingTicks % 6 == 0) {
            for (ItemEntity coin : this.spawnedCoins) {
                if (coin.isAlive()) {
                    world.spawnParticles(ParticleTypes.WAX_ON, coin.getX(), coin.getY() + 0.2, coin.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
                }
            }
        }

        int required = getRequiredCoins(game);

        // Proximity collection
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            int current = this.collectedCoins.getOrDefault(p.getUuid(), 0);
            if (current >= required) {
                continue;
            }

            Iterator<ItemEntity> it = this.spawnedCoins.iterator();
            while (it.hasNext()) {
                ItemEntity coin = it.next();
                if (coin.isAlive() && p.squaredDistanceTo(coin) < 2.25) { // 1.5 blocks radius
                    int updated = this.collectedCoins.merge(p.getUuid(), 1, Integer::sum);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.4f + (updated * 0.2f));
                    if (updated >= required) {
                        p.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("§e§l🪙 COINS COMPLETE!").formatted(Formatting.GOLD)));
                    } else {
                        p.sendMessage(Text.literal("§eCoins: §a" + updated + "§7/§e" + required), true);
                    }
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, coin.getX(), coin.getY() + 0.3, coin.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                    coin.discard();
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return this.collectedCoins.getOrDefault(player.getUuid(), 0) >= getRequiredCoins(game);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ItemEntity coin : this.spawnedCoins) {
            if (coin != null && coin.isAlive()) {
                coin.discard();
            }
        }
        this.spawnedCoins.clear();
        this.collectedCoins.clear();
    }
}
