package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SnowballFightRule implements MicroRule {
    @Override
    public String id() {
        return "snowball_fight";
    }

    @Override
    public String name() {
        return "Snowball Tag";
    }

    @Override
    public String description() {
        return "Hit another player with a snowball! You only have 3 shots.";
    }

    @Override
    public Text title() {
        return Text.literal("SNOWBALL FIGHT!").formatted(Formatting.AQUA, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Hit another player! (3 snowballs)").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public int getDurationTicks(MicroFrenzyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(8 * 20 * factor);
        return Math.max(80, standardTicks); // Clamped to at least 4.0 seconds
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            // Exactly 3 snowballs as requested
            p.getInventory().setStack(0, new ItemStack(Items.SNOWBALL, 3));
            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();

            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    @Override
    public boolean onPlayerDamage(ServerPlayerEntity victim, DamageSource source, float amount, MicroFrenzyMinigame game) {
        if (source.getSource() instanceof SnowballEntity snowball) {
            if (snowball.getOwner() instanceof ServerPlayerEntity attacker && !attacker.getUuid().equals(victim.getUuid())) {
                if (!game.getTracker().hasPassedCurrentRound(attacker.getUuid())) {
                    game.getTracker().setPassedCurrentRound(attacker.getUuid(), true);
                    attacker.sendMessage(Text.literal("§a§l✔ Bullseye! Snowball landed!"), true);
                    attacker.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                    victim.getServerWorld().spawnParticles(ParticleTypes.ITEM_SNOWBALL, victim.getX(), victim.getY() + 1.0, victim.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
                    victim.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.7f, 1.2f);
                }
            }
            return false; // Prevent damage/knockback glitch
        }
        return false;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }
    }
}
