package dev.frost.miniverse.minigame.impl.hideandseek.combat;

import dev.frost.miniverse.minigame.impl.hideandseek.HideAndSeekSettings;
import dev.frost.miniverse.minigame.impl.hideandseek.disguise.BlockDisguiseManager;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public class HideAndSeekCombatManager {
    private final ServerWorld world;
    private final BlockDisguiseManager disguiseManager;
    private final HideAndSeekSettings settings;
    private final Predicate<UUID> isSeeker;
    private final Predicate<UUID> isHider;
    private final BiPredicate<ServerPlayerEntity, BlockPos> decoyHandler;
    private final Map<UUID, Long> lastBlockAttackTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMissTimes = new ConcurrentHashMap<>();

    public HideAndSeekCombatManager(
        ServerWorld world,
        BlockDisguiseManager disguiseManager,
        HideAndSeekSettings settings,
        Predicate<UUID> isSeeker,
        Predicate<UUID> isHider,
        BiPredicate<ServerPlayerEntity, BlockPos> decoyHandler
    ) {
        this.world = world;
        this.disguiseManager = disguiseManager;
        this.settings = settings;
        this.isSeeker = isSeeker;
        this.isHider = isHider;
        this.decoyHandler = decoyHandler;
    }

    public HideAndSeekCombatManager(
        ServerWorld world,
        BlockDisguiseManager disguiseManager,
        HideAndSeekSettings settings,
        Predicate<UUID> isSeeker,
        Predicate<UUID> isHider
    ) {
        this(world, disguiseManager, settings, isSeeker, isHider, null);
    }

    public ActionResult onAttackBlock(ServerPlayerEntity seeker, BlockPos pos, boolean inGracePeriod, Map<UUID, ServerPlayerEntity> livePlayers) {
        if (!this.isSeeker.test(seeker.getUuid())) {
            return ActionResult.FAIL;
        }

        if (inGracePeriod) {
            return ActionResult.FAIL;
        }

        long now = System.currentTimeMillis();
        Long lastTime = this.lastBlockAttackTimes.get(seeker.getUuid());
        if (lastTime != null && now - lastTime < 300) {
            return ActionResult.FAIL; // Debounce
        }
        this.lastBlockAttackTimes.put(seeker.getUuid(), now);

        // Exhaustion check: If health is critical (<= 2.0 HP / 1 heart), Seeker CANNOT strike blocks or reveal hiders!
        float currentHealth = seeker.getHealth();
        if (currentHealth <= 2.0F) {
            seeker.sendMessage(Text.literal("§c⚡ Exhausted! Catch your breath (3s to recover)...").formatted(Formatting.RED, Formatting.BOLD), true);
            seeker.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5F, 1.2F);
            return ActionResult.FAIL;
        }

        if (this.decoyHandler != null && this.decoyHandler.test(seeker, pos)) {
            this.lastMissTimes.put(seeker.getUuid(), now);
            return ActionResult.FAIL;
        }

        Optional<ServerPlayerEntity> foundHider = this.disguiseManager.findHiderAt(pos, livePlayers);

        if (foundHider.isPresent()) {
            ServerPlayerEntity hider = foundHider.get();
            if (this.isHider.test(hider.getUuid())) {
                // Strike the hider!
                float damage = (float) seeker.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                hider.damage(this.world.getDamageSources().playerAttack(seeker), damage > 0.0F ? damage : 9.0F);
                this.disguiseManager.forceUnlock(hider.getUuid());

                // Visual & sound feedback
                this.world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 1.4F);
                this.world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                this.world.spawnParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.15);

                // Give hider brief sprint burst
                hider.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, false, false, true));

                // Seeker regains full health (10 hearts / 20 HP)
                seeker.setHealth(seeker.getMaxHealth());

                seeker.sendMessage(Text.literal("§a✔ FOUND HIDER: §e" + hider.getName().getString() + "§a! (Full Health Restored!)").formatted(Formatting.GREEN, Formatting.BOLD), true);
                return ActionResult.FAIL;
            }
        }

        // Wrong block: Apply miss penalty to seeker and record miss time to pause regen
        this.lastMissTimes.put(seeker.getUuid(), now);
        seeker.timeUntilRegen = 0;
        seeker.damage(this.world.getDamageSources().generic(), this.settings.seekerMissPenalty());
        seeker.setHealth(Math.max(1.0F, currentHealth - this.settings.seekerMissPenalty()));
        seeker.sendMessage(Text.literal("§c-0.5♥ (Wrong block!)").formatted(Formatting.RED), true);
        seeker.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.6F, 1.2F);

        return ActionResult.FAIL;
    }

    public void tickPassiveRegen(java.util.List<ServerPlayerEntity> seekers) {
        long now = System.currentTimeMillis();
        float cap = this.settings != null ? this.settings.seekerPassiveRegenCap() : HideAndSeekSettings.DEFAULT_PASSIVE_REGEN_CAP;
        for (ServerPlayerEntity seeker : seekers) {
            if (seeker == null || seeker.isDisconnected() || seeker.isSpectator()) continue;
            float targetCap = Math.min(seeker.getMaxHealth(), cap);
            float health = seeker.getHealth();
            if (health >= targetCap) continue;

            Long lastMiss = this.lastMissTimes.get(seeker.getUuid());
            // If 3 seconds (3000ms) have passed without hitting a wrong block, slowly recover stamina
            if (lastMiss == null || now - lastMiss >= 3000) {
                float newHealth = Math.min(targetCap, health + 1.0F);
                seeker.setHealth(newHealth);

                if (health <= 2.0F && newHealth > 2.0F) {
                    seeker.sendMessage(Text.literal("§a⚡ Energy recovered! Ready to strike.").formatted(Formatting.GREEN, Formatting.BOLD), true);
                    seeker.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7F, 1.6F);
                }
            }
        }
    }

    public void clear() {
        this.lastBlockAttackTimes.clear();
        this.lastMissTimes.clear();
    }
}
