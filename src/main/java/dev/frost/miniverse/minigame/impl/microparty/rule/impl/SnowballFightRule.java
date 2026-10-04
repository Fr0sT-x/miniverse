package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
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
    public int getDurationTicks(MicroPartyMinigame game) {
        float factor = game != null ? game.getSpeedFactor() : 1.0f;
        int standardTicks = Math.round(8 * 20 * factor);
        return Math.max(80, standardTicks); // Clamped to at least 4.0 seconds
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
            // Exactly 3 snowballs as requested
            p.getInventory().setStack(0, new ItemStack(Items.SNOWBALL, 3));
            p.getInventory().selectedSlot = 0;
            p.currentScreenHandler.sendContentUpdates();

            game.getTracker().setPassedCurrentRound(p.getUuid(), false);
        }
    }

    private void recordHit(ServerPlayerEntity attacker, ServerPlayerEntity victim, MicroPartyMinigame game) {
        if (attacker.getUuid().equals(victim.getUuid())) {
            return;
        }
        if (!game.getTracker().hasPassedCurrentRound(attacker.getUuid())) {
            game.getTracker().setPassedCurrentRound(attacker.getUuid(), true);
            attacker.sendMessage(Text.literal("§a§l✔ Bullseye! Snowball landed!"), true);
            attacker.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
            victim.getServerWorld().spawnParticles(ParticleTypes.ITEM_SNOWBALL, victim.getX(), victim.getY() + 1.0, victim.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
            victim.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.7f, 1.2f);
        }
    }

    @Override
    public void onPlayerAttack(ServerPlayerEntity attacker, net.minecraft.entity.Entity target, MicroPartyMinigame game) {
        if (target instanceof ServerPlayerEntity victim && !victim.getUuid().equals(attacker.getUuid())) {
            if (attacker.getMainHandStack().isOf(Items.SNOWBALL) || attacker.getOffHandStack().isOf(Items.SNOWBALL)) {
                recordHit(attacker, victim, game);
            }
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        net.minecraft.server.world.ServerWorld world = game.getWorld();
        if (world == null) return;

        var bounds = dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int floorY = dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        net.minecraft.util.math.Box arenaBox = new net.minecraft.util.math.Box(
            bounds.minX() - 2, floorY - 2, bounds.minZ() - 2,
            bounds.maxX() + 2, floorY + 15, bounds.maxZ() + 2
        );

        java.util.List<SnowballEntity> snowballs = world.getEntitiesByClass(SnowballEntity.class, arenaBox, net.minecraft.entity.Entity::isAlive);
        for (SnowballEntity snowball : snowballs) {
            ServerPlayerEntity owner = null;
            if (snowball.getOwner() instanceof ServerPlayerEntity p) {
                owner = p;
            }
            if (owner == null) continue;

            for (ServerPlayerEntity victim : game.getLivingPlayers()) {
                if (victim.getUuid().equals(owner.getUuid())) continue;
                if (victim.getBoundingBox().expand(0.35).intersects(snowball.getBoundingBox())) {
                    recordHit(owner, victim, game);
                    snowball.discard();
                    break;
                }
            }
        }
    }

    @Override
    public boolean onPlayerDamage(ServerPlayerEntity victim, DamageSource source, float amount, MicroPartyMinigame game) {
        // 1. Thrown projectile hit
        if (source.getSource() instanceof SnowballEntity snowball || source.isOf(net.minecraft.entity.damage.DamageTypes.THROWN)) {
            ServerPlayerEntity attacker = null;
            if (source.getAttacker() instanceof ServerPlayerEntity a) {
                attacker = a;
            } else if (source.getSource() instanceof SnowballEntity sb && sb.getOwner() instanceof ServerPlayerEntity a) {
                attacker = a;
            }
            if (attacker != null) {
                recordHit(attacker, victim, game);
            }
            return false; // Prevent damage/knockback glitch
        }

        // 2. Melee hit with snowball in hand
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.PLAYER_ATTACK)) {
            if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
                if (attacker.getMainHandStack().isOf(Items.SNOWBALL) || attacker.getOffHandStack().isOf(Items.SNOWBALL)) {
                    recordHit(attacker, victim, game);
                }
            }
            return false;
        }

        return false;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        if (game != null && game.getLivingPlayers().size() <= 1) {
            return true;
        }
        return game.getTracker().hasPassedCurrentRound(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            p.getInventory().clear();
        }
    }
}
