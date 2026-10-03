package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class DropItemRule implements MicroRule {
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
        return "Press 'Q' to drop your diamond immediately.";
    }

    @Override
    public Text title() {
        return Text.literal("DROP IT!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Press 'Q' to drop your diamond!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            p.getInventory().setStack(0, new ItemStack(Items.DIAMOND));
            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();
            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (!game.getTracker().hasPassedCurrentRound(p.getUuid())) {
                if (!p.getInventory().contains(new ItemStack(Items.DIAMOND))) {
                    game.getTracker().setPassedCurrentRound(p.getUuid(), true);
                    p.sendMessage(Text.literal("§a§l✔ Dropped!"), true);
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid()) || !player.getInventory().contains(new ItemStack(Items.DIAMOND));
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }

        // Clean up dropped diamond entities from the arena floor
        if (game.getWorld() != null) {
            dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper.ArenaBounds2D bounds = dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
            int floorY = dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper.getFloorY(game.getMapConfig());
            net.minecraft.util.math.Box arenaBox = new net.minecraft.util.math.Box(bounds.minX() - 2, floorY - 2, bounds.minZ() - 2, bounds.maxX() + 2, floorY + 5, bounds.maxZ() + 2);
            game.getWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, arenaBox, item -> item.getStack().isOf(Items.DIAMOND)).forEach(net.minecraft.entity.ItemEntity::discard);
        }
    }
}
