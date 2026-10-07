package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.entity.ItemEntity;
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

import java.util.Random;

public class DropItemRule implements MicroRule {
    private final Random random = new Random();

    @Override
    public String id() {
        return "drop_item";
    }

    @Override
    public String name() {
        return "Drop It";
    }

    @Override
    public String description() {
        return "Open your inventory (E) and drop your diamond immediately.";
    }

    @Override
    public Text title() {
        return Text.literal("DROP IT!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Open inventory (E) and drop the diamond!").formatted(Formatting.YELLOW);
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
        player.sendMessage(Text.literal("§eOpen inventory (E) and drop the diamond!"), true);
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();

            // Hotbar (slots 0-8) is kept completely empty!
            // Diamond placed randomly in main inventory (slots 9-35)
            int targetSlot = 9 + random.nextInt(27);
            p.getInventory().setStack(targetSlot, new ItemStack(Items.DIAMOND));
            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();

            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    private boolean playerHoldsOrContainsDiamond(ServerPlayerEntity p) {
        if (p.currentScreenHandler.getCursorStack().isOf(Items.DIAMOND)) {
            return true;
        }
        for (int i = 0; i < p.getInventory().size(); i++) {
            if (p.getInventory().getStack(i).isOf(Items.DIAMOND)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                if (!playerHoldsOrContainsDiamond(p)) {
                    game.getTracker().setPassedCurrentRound(p.getUuid(), true);
                    p.sendMessage(Text.literal("§a§l✔ Diamond Dropped!"), true);
                    p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                    ServerWorld world = p.getServerWorld();
                    world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid()) || !playerHoldsOrContainsDiamond(player);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }

        // Clean up dropped diamond entities from the arena floor
        if (game.getWorld() != null) {
            MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
            int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
            Box arenaBox = new Box(bounds.minX() - 2, floorY - 2, bounds.minZ() - 2, bounds.maxX() + 2, floorY + 5, bounds.maxZ() + 2);
            game.getWorld().getEntitiesByClass(ItemEntity.class, arenaBox, item -> item.getStack().isOf(Items.DIAMOND)).forEach(ItemEntity::discard);
        }
    }
}
