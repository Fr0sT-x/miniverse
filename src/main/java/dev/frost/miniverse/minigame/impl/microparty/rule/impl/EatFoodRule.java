package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
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

public class EatFoodRule implements MicroRule {
    public record FoodOption(Item item, String displayName) {}

    public static List<FoodOption> getFoodPool() {
        return List.of(
            new FoodOption(Items.APPLE, "Apple"),
            new FoodOption(Items.CARROT, "Carrot"),
            new FoodOption(Items.BREAD, "Bread"),
            new FoodOption(Items.BAKED_POTATO, "Baked Potato"),
            new FoodOption(Items.COOKIE, "Cookie"),
            new FoodOption(Items.MELON_SLICE, "Melon Slice"),
            new FoodOption(Items.SWEET_BERRIES, "Sweet Berries"),
            new FoodOption(Items.GOLDEN_CARROT, "Golden Carrot")
        );
    }

    private final Random random = new Random();
    private FoodOption targetFood = null;
    private final Set<UUID> passedPlayers = new HashSet<>();
    private final Set<UUID> failedPlayers = new HashSet<>();

    @Override
    public String id() {
        return "eat_food";
    }

    @Override
    public String name() {
        return "Feast";
    }

    @Override
    public String description() {
        return "Find the single target food hidden among decoy fruits in your inventory and eat it. Eating decoys fails!";
    }

    @Override
    public Text title() {
        String name = this.targetFood != null ? this.targetFood.displayName().toUpperCase() : "TARGET FOOD";
        return Text.literal("FEAST: EAT " + name + "!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        String name = this.targetFood != null ? this.targetFood.displayName() : "target food";
        return Text.literal("Find the 1 " + name + " in inventory & eat it! Decoys FAIL!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.5;
    }

    @Override
    public void sendInitialActionBar(MicroPartyMinigame game, ServerPlayerEntity player) {
        String name = this.targetFood != null ? this.targetFood.displayName() : "target food";
        player.sendMessage(Text.literal("§eFind the 1 " + name + " in your inventory (E) & eat it!"), true);
    }

    @Override
    public void onPrepare(MicroPartyMinigame game, MinecraftServer server) {
        List<FoodOption> pool = getFoodPool();
        this.targetFood = pool.get(random.nextInt(pool.size()));
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        if (this.targetFood == null) {
            onPrepare(game, server);
        }
        this.passedPlayers.clear();
        this.failedPlayers.clear();

        List<FoodOption> decoys = new ArrayList<>(getFoodPool());
        decoys.removeIf(f -> f.item() == this.targetFood.item());

        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.getHungerManager().setFoodLevel(6); // Lower hunger to permit eating immediately
            p.getHungerManager().setSaturationLevel(0.0f);

            // Hotbar (slots 0-8) is kept completely empty!
            // Main inventory slots are 9-35 (27 slots)
            int targetSlot = 9 + random.nextInt(27);

            for (int slot = 9; slot < 36; slot++) {
                if (slot == targetSlot) {
                    p.getInventory().setStack(slot, new ItemStack(this.targetFood.item(), 1));
                } else {
                    FoodOption decoy = decoys.get(random.nextInt(decoys.size()));
                    int count = 2 + random.nextInt(15);
                    p.getInventory().setStack(slot, new ItemStack(decoy.item(), count));
                }
            }

            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();

            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    private int countTargetItem(ServerPlayerEntity p) {
        if (this.targetFood == null) return 0;
        int count = 0;
        if (p.currentScreenHandler.getCursorStack().isOf(this.targetFood.item())) {
            count += p.currentScreenHandler.getCursorStack().getCount();
        }
        for (int i = 0; i < p.getInventory().size(); i++) {
            if (p.getInventory().getStack(i).isOf(this.targetFood.item())) {
                count += p.getInventory().getStack(i).getCount();
            }
        }
        return count;
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            UUID uuid = p.getUuid();
            if (this.passedPlayers.contains(uuid) || this.failedPlayers.contains(uuid)) {
                continue;
            }

            int foodLevel = p.getHungerManager().getFoodLevel();
            if (foodLevel > 6) { // Food was consumed!
                int targetCount = countTargetItem(p);
                if (targetCount == 0) {
                    // Successfully found and ate the single target food
                    this.passedPlayers.add(uuid);
                    game.getTracker().setPassedCurrentRound(uuid, true);
                    p.sendMessage(Text.literal("§a§l✔ Delicious! Ate the " + this.targetFood.displayName() + "!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                } else {
                    // Ate a decoy food!
                    this.failedPlayers.add(uuid);
                    game.getTracker().setPassedCurrentRound(uuid, false);
                    p.sendMessage(Text.literal("§c§l❌ ATE THE WRONG FOOD! (FAILED)"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.PLAYERS, 1.0f, 0.8f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, p.getX(), p.getY() + 1.2, p.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                    p.getInventory().clear();
                    p.currentScreenHandler.sendContentUpdates();
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return this.passedPlayers.contains(player.getUuid()) && !this.failedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.passedPlayers.clear();
        this.failedPlayers.clear();
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.getHungerManager().setFoodLevel(20);
        }

        // Clean up dropped food items from arena floor
        if (game.getWorld() != null) {
            MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
            int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
            Box arenaBox = new Box(bounds.minX() - 2, floorY - 2, bounds.minZ() - 2, bounds.maxX() + 2, floorY + 5, bounds.maxZ() + 2);
            game.getWorld().getEntitiesByClass(ItemEntity.class, arenaBox, e -> true).forEach(ItemEntity::discard);
        }
    }
}
