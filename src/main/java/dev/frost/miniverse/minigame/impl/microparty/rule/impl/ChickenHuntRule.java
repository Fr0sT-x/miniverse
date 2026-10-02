package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyArenaHelper;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class ChickenHuntRule implements MicroRule {
    private final Set<UUID> passedPlayers = new HashSet<>();
    private final List<ChickenEntity> spawnedChickens = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public String id() {
        return "chicken_hunt";
    }

    @Override
    public String name() {
        return "Punch a Chicken";
    }

    @Override
    public String description() {
        return "Chase and punch a running chicken inside the arena!";
    }

    @Override
    public Text title() {
        return Text.literal("PUNCH A CHICKEN!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Punch a running chicken!").formatted(Formatting.YELLOW);
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
        this.spawnedChickens.clear();

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroFrenzyArenaHelper.getFloorY(game.getMapConfig());
        MicroFrenzyArenaHelper.ArenaBounds2D bounds = MicroFrenzyArenaHelper.getBounds2D(game.getMapConfig());

        int count = Math.max(6, game.getLivingPlayers().size() * 2);
        int margin = Math.max(1, bounds.width() / 8);

        for (int i = 0; i < count; i++) {
            int x = bounds.minX() + margin + random.nextInt(Math.max(1, bounds.width() - 2 * margin));
            int z = bounds.minZ() + margin + random.nextInt(Math.max(1, bounds.depth() - 2 * margin));

            ChickenEntity chicken = new ChickenEntity(EntityType.CHICKEN, world);
            chicken.refreshPositionAndAngles(x + 0.5, floorY, z + 0.5, random.nextFloat() * 360f, 0f);
            chicken.setCustomName(Text.literal("§e§lPUNCH ME!").formatted(Formatting.YELLOW));
            chicken.setCustomNameVisible(true);
            chicken.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 1, false, false));

            world.spawnEntity(chicken);
            this.spawnedChickens.add(chicken);
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null || this.spawnedChickens.isEmpty()) return;

        // Proximity swing fallback
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (this.passedPlayers.contains(p.getUuid())) continue;

            if (p.handSwinging) {
                Iterator<ChickenEntity> it = this.spawnedChickens.iterator();
                while (it.hasNext()) {
                    ChickenEntity chicken = it.next();
                    if (chicken.isAlive() && p.squaredDistanceTo(chicken) <= 4.0) {
                        this.passedPlayers.add(p.getUuid());
                        p.sendMessage(Text.literal("§a§l✔ Chicken Punched!"), true);
                        p.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                        p.playSoundToPlayer(SoundEvents.ENTITY_CHICKEN_HURT, SoundCategory.PLAYERS, 1.0f, 1.2f);
                        world.spawnParticles(ParticleTypes.POOF, chicken.getX(), chicken.getY() + 0.5, chicken.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                        chicken.discard();
                        it.remove();
                        break;
                    }
                }
            }
        }
    }

    @Override
    public boolean onEntityDamage(LivingEntity entity, DamageSource source, float amount, MicroFrenzyMinigame game) {
        if (entity instanceof ChickenEntity chicken && this.spawnedChickens.contains(chicken)) {
            if (source.getAttacker() instanceof ServerPlayerEntity player && game.getLivingPlayers().contains(player)) {
                if (!this.passedPlayers.contains(player.getUuid())) {
                    this.passedPlayers.add(player.getUuid());
                    player.sendMessage(Text.literal("§a§l✔ Chicken Punched!"), true);
                    player.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.5f);
                    player.playSoundToPlayer(SoundEvents.ENTITY_CHICKEN_HURT, SoundCategory.PLAYERS, 1.0f, 1.2f);
                }
                ServerWorld world = game.getWorld();
                if (world != null) {
                    world.spawnParticles(ParticleTypes.POOF, chicken.getX(), chicken.getY() + 0.5, chicken.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
                }
                chicken.discard();
                this.spawnedChickens.remove(chicken);
            }
            return false; // prevent drop loot
        }
        return true;
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        return this.passedPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
        for (ChickenEntity chicken : this.spawnedChickens) {
            if (chicken.isAlive()) {
                chicken.discard();
            }
        }
        this.spawnedChickens.clear();
        this.passedPlayers.clear();
    }
}
