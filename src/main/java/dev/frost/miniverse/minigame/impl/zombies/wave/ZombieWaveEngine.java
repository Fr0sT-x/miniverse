package dev.frost.miniverse.minigame.impl.zombies.wave;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficulty;
import dev.frost.miniverse.minigame.impl.zombies.ZombiesSettings;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesMapConfig;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesWindow;
import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieEntityManager;
import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ZombieWaveEngine {
    public static final int MAX_ROUNDS = 30;
    public static final int INTERMISSION_TICKS = 10 * 20; // 10 seconds
    public static final int MAX_ACTIVE_MOBS = 28;

    public enum WaveState {
        INTERMISSION,
        IN_ROUND,
        VICTORY,
        GAME_OVER
    }

    private final ServerWorld world;
    private final ZombiesMapConfig mapConfig;
    private final ZombieEntityManager mobManager;
    private final Random random = new Random();

    private int maxRounds = MAX_ROUNDS;
    private int intermissionTicks = INTERMISSION_TICKS;
    private ZombiesDifficulty difficulty = ZombiesDifficulty.EASY;
    private dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficultyConfig difficultyConfig = dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficultyConfig.defaults();
    private boolean endlessMode = false;

    private int currentRound = 0;
    private WaveState state = WaveState.INTERMISSION;
    private int timerTicks = 100; // 5 second intro before Round 1
    private int spawnCooldown = 0;
    private long roundStartTime = 0L;

    private final Queue<ZombieType> spawnQueue = new LinkedList<>();

    private Runnable onRoundClearListener;
    private Consumer<Integer> onRoundStartListener;
    private Runnable onVictoryListener;

    public ZombieWaveEngine(ServerWorld world, ZombiesMapConfig mapConfig, ZombieEntityManager mobManager) {
        this(world, mapConfig, mobManager, ZombiesSettings.defaults());
    }

    public ZombieWaveEngine(ServerWorld world, ZombiesMapConfig mapConfig, ZombieEntityManager mobManager, ZombiesSettings settings) {
        this.world = world;
        this.mapConfig = mapConfig;
        this.mobManager = mobManager;
        this.mobManager.setSpawnQueueEmptySupplier(() -> this.spawnQueue.isEmpty() && this.state == WaveState.IN_ROUND);
        if (settings != null) {
            this.maxRounds = settings.maxRounds();
            this.intermissionTicks = settings.intermissionSeconds() * 20;
            this.difficulty = settings.difficulty() != null ? settings.difficulty() : ZombiesDifficulty.EASY;
            this.endlessMode = settings.endlessMode();
            if (settings.difficultyConfig() != null) {
                this.difficultyConfig = settings.difficultyConfig();
            }
        }
    }

    public void setDifficultyConfig(dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficultyConfig difficultyConfig) {
        this.difficultyConfig = difficultyConfig != null ? difficultyConfig : dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficultyConfig.defaults();
    }

    public dev.frost.miniverse.minigame.impl.zombies.ZombiesDifficultyConfig getDifficultyConfig() {
        return this.difficultyConfig;
    }

    public int getMaxActiveMobs() {
        return this.difficultyConfig != null ? this.difficultyConfig.getMaxActiveMobs(this.difficulty) : this.difficulty.getMaxActiveMobs();
    }

    public int getSpawnCooldownMin() {
        return this.difficultyConfig != null ? this.difficultyConfig.getSpawnCooldownMin(this.difficulty) : this.difficulty.getSpawnCooldownMin();
    }

    public int getSpawnCooldownRandom() {
        return this.difficultyConfig != null ? this.difficultyConfig.getSpawnCooldownRandom(this.difficulty) : this.difficulty.getSpawnCooldownRandom();
    }

    public int getBaseWaveMobs() {
        return this.difficultyConfig != null ? this.difficultyConfig.getBaseWaveMobs(this.difficulty) : this.difficulty.getBaseWaveMobs();
    }

    public int getWaveMobsPerRound() {
        return this.difficultyConfig != null ? this.difficultyConfig.getWaveMobsPerRound(this.difficulty) : this.difficulty.getWaveMobsPerRound();
    }

    public int getCurrentRound() {
        return this.currentRound;
    }

    public boolean isEndlessMode() {
        return this.endlessMode;
    }

    public WaveState getState() {
        return this.state;
    }

    public int getRemainingInQueue() {
        return this.spawnQueue.size();
    }

    public int getIntermissionSeconds() {
        return Math.max(0, this.timerTicks / 20);
    }

    public void setOnRoundClearListener(Runnable listener) {
        this.onRoundClearListener = listener;
    }

    public void setOnRoundStartListener(Consumer<Integer> listener) {
        this.onRoundStartListener = listener;
    }

    public void setOnVictoryListener(Runnable listener) {
        this.onVictoryListener = listener;
    }

    public void tick(List<ServerPlayerEntity> survivors, Set<String> reachableAreas) {
        if (this.state == WaveState.VICTORY || this.state == WaveState.GAME_OVER) {
            return;
        }

        if (this.state == WaveState.INTERMISSION) {
            this.timerTicks--;
            if (this.timerTicks == 60 || this.timerTicks == 40 || this.timerTicks == 20) {
                int sec = this.timerTicks / 20;
                for (ServerPlayerEntity p : survivors) {
                    p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.8f, 1.0f);
                }
            } else if (this.timerTicks <= 0) {
                startNextRound(survivors);
            }
            return;
        }

        if (this.state == WaveState.IN_ROUND) {
            // Spawn queued mobs if under cap
            int baseMaxActive = getMaxActiveMobs();
            int maxActive = (this.endlessMode && this.currentRound > 30)
                ? Math.min(48, baseMaxActive + ((this.currentRound - 30) / 5) * 2)
                : baseMaxActive;
            if (!this.spawnQueue.isEmpty() && this.mobManager.getAliveMobCount() < maxActive) {
                this.spawnCooldown--;
                if (this.spawnCooldown <= 0) {
                    this.spawnCooldown = getSpawnCooldownMin() + this.random.nextInt(Math.max(1, getSpawnCooldownRandom()));
                    ZombieType nextType = this.spawnQueue.poll();
                    if (nextType != null) {
                        spawnMobAtWindow(nextType, reachableAreas);
                    }
                }
            }

            // Check round completion
            if (this.spawnQueue.isEmpty() && this.mobManager.getAliveMobCount() == 0) {
                completeRound(survivors);
            }
        }
    }

    public void devSkipRound(List<ServerPlayerEntity> survivors) {
        if (this.state == WaveState.VICTORY || this.state == WaveState.GAME_OVER) {
            return;
        }
        if (this.state == WaveState.INTERMISSION) {
            this.timerTicks = 0;
            startNextRound(survivors);
        } else if (this.state == WaveState.IN_ROUND) {
            this.spawnQueue.clear();
            this.mobManager.clearAll();
            this.mobManager.clearGlowingEffects();
            completeRound(survivors);
        }
    }

    public void startNextRound(List<ServerPlayerEntity> survivors) {
        this.currentRound++;
        if (!this.endlessMode && this.currentRound > this.maxRounds) {
            this.state = WaveState.VICTORY;
            if (this.onVictoryListener != null) {
                this.onVictoryListener.run();
            }
            return;
        }

        this.state = WaveState.IN_ROUND;
        this.spawnQueue.clear();
        buildRoundQueue(this.currentRound);
        this.roundStartTime = System.currentTimeMillis();
        this.mobManager.resetLastKillTime();

        // Notify survivors with Big Title and sound
        for (ServerPlayerEntity p : survivors) {
            p.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 0.9f, 1.2f);
        }

        Text subtitle = Text.empty();
        if (this.endlessMode && this.currentRound > 30) {
            if (this.currentRound % 5 == 0) {
                subtitle = Text.literal("⚠ BOSS SURGE ⚠").formatted(Formatting.DARK_RED, Formatting.BOLD);
            } else {
                int themeIdx = (this.currentRound - 31) % 4;
                subtitle = switch (themeIdx) {
                    case 0 -> Text.literal("Theme: The Swarm").formatted(Formatting.YELLOW);
                    case 1 -> Text.literal("Theme: Infernal Siege").formatted(Formatting.GOLD);
                    case 2 -> Text.literal("Theme: Laser Vanguard").formatted(Formatting.AQUA);
                    case 3 -> Text.literal("Theme: Apocalyptic Chaos").formatted(Formatting.LIGHT_PURPLE);
                    default -> Text.empty();
                };
            }

            if (this.currentRound == 31) {
                for (ServerPlayerEntity p : survivors) {
                    p.sendMessage(Text.literal("☠ ENDLESS MODE ACTIVATED! Survive as long as you can... ☠").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
                    p.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
                }
            }
        }

        dev.frost.miniverse.minigame.core.GameMessenger.showGameTitle(
            survivors,
            Text.literal("Round " + this.currentRound).formatted(Formatting.RED, Formatting.BOLD),
            subtitle
        );

        if (this.onRoundStartListener != null) {
            this.onRoundStartListener.accept(this.currentRound);
        }
    }

    private void completeRound(List<ServerPlayerEntity> survivors) {
        if (!this.endlessMode && this.currentRound >= this.maxRounds) {
            this.state = WaveState.VICTORY;
            if (this.onVictoryListener != null) {
                this.onVictoryListener.run();
            }
            return;
        }

        this.state = WaveState.INTERMISSION;
        this.timerTicks = this.intermissionTicks;
        this.mobManager.clearGlowingEffects();

        long durationMs = Math.max(0, System.currentTimeMillis() - this.roundStartTime);
        int sec = (int) (durationMs / 1000);
        String timeStr = String.format("%02d:%02d", sec / 60, sec % 60);

        for (ServerPlayerEntity p : survivors) {
            p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            p.sendMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GREEN), false);
            p.sendMessage(Text.literal(" You completed Round " + this.currentRound + " in " + timeStr + "!").formatted(Formatting.GREEN, Formatting.BOLD), false);
            p.sendMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GREEN), false);
        }

        if (this.onRoundClearListener != null) {
            this.onRoundClearListener.run();
        }
    }

    private void spawnMobAtWindow(ZombieType type, Set<String> reachableAreas) {
        List<ZombiesWindow> eligibleWindows = new ArrayList<>();
        for (ZombiesWindow w : this.mapConfig.windows()) {
            if (reachableAreas.contains(w.getArea())) {
                eligibleWindows.add(w);
            }
        }

        if (eligibleWindows.isEmpty()) {
            // Fallback to any window or player spawn
            if (!this.mapConfig.windows().isEmpty()) {
                eligibleWindows.addAll(this.mapConfig.windows());
            }
        }

        if (!eligibleWindows.isEmpty()) {
            ZombiesWindow chosen = eligibleWindows.get(this.random.nextInt(eligibleWindows.size()));
            this.mobManager.spawnMob(type, chosen.getSpawnPos(), chosen);
        } else if (!this.mapConfig.playerSpawns().isEmpty()) {
            net.minecraft.util.math.BlockPos bp = this.mapConfig.playerSpawns().get(0);
            MapPosition pSpawn = MapPosition.of(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
            this.mobManager.spawnMob(type, pSpawn, null);
        }
    }

    public void handleStuckMob(ZombieEntityManager.ActiveMob active, Set<String> reachableAreas) {
        if (active == null || active.entity == null || !active.entity.isAlive()) return;
        List<ZombiesWindow> candidates = this.mapConfig.windows().stream()
            .filter(w -> reachableAreas.contains(w.getArea()))
            .toList();
        if (!candidates.isEmpty()) {
            ZombiesWindow chosen = candidates.get(this.random.nextInt(candidates.size()));
            MapPosition sp = chosen.getSpawnPos();
            active.entity.teleport(this.world, sp.x(), sp.y(), sp.z(), Set.of(), sp.yaw(), sp.pitch());
            this.world.spawnParticles(ParticleTypes.POOF, sp.x(), sp.y() + 1.0, sp.z(), 15, 0.3, 0.4, 0.3, 0.05);
            this.world.playSound(null, sp.x(), sp.y(), sp.z(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.8f, 1.2f);
            active.entity.getNavigation().recalculatePath();
        } else {
            // Despawn and refund into queue
            this.spawnQueue.add(active.type);
            active.entity.discard();
        }
    }

    private void buildRoundQueue(int round) {
        if (round > 30) {
            int mobCount = getBaseWaveMobs() + (round * getWaveMobsPerRound()) + (round - 30) * 2;

            if (round % 5 == 0) {
                if (round == 35) {
                    this.spawnQueue.add(ZombieType.BOMBIE);
                    this.spawnQueue.add(ZombieType.INFERNO);
                } else if (round == 40) {
                    this.spawnQueue.add(ZombieType.BROODMOTHER);
                    this.spawnQueue.add(ZombieType.BOMBIE);
                } else if (round == 45) {
                    this.spawnQueue.add(ZombieType.BROODMOTHER);
                    this.spawnQueue.add(ZombieType.INFERNO);
                } else { // 50+
                    this.spawnQueue.add(ZombieType.BROODMOTHER);
                    this.spawnQueue.add(ZombieType.INFERNO);
                    this.spawnQueue.add(ZombieType.BOMBIE);
                }
            }

            int themeIdx = (round - 31) % 4;
            for (int i = 0; i < mobCount; i++) {
                this.spawnQueue.add(selectEndlessMobType(themeIdx));
            }
            return;
        }

        int mobCount = getBaseWaveMobs() + (round * getWaveMobsPerRound());

        if (round == 10) {
            this.spawnQueue.add(ZombieType.BOMBIE);
            mobCount = Math.round(20 * (getBaseWaveMobs() / 14.0f));
        } else if (round == 20) {
            this.spawnQueue.add(ZombieType.INFERNO);
            mobCount = Math.round(30 * (getBaseWaveMobs() / 14.0f));
        } else if (round == 30) {
            this.spawnQueue.add(ZombieType.BROODMOTHER);
            this.spawnQueue.add(ZombieType.INFERNO);
            this.spawnQueue.add(ZombieType.BOMBIE);
            mobCount = Math.round(40 * (getBaseWaveMobs() / 14.0f));
        }

        for (int i = 0; i < mobCount; i++) {
            this.spawnQueue.add(selectMobTypeForRound(round));
        }
    }

    private ZombieType selectEndlessMobType(int themeIdx) {
        float r = this.random.nextFloat();
        return switch (themeIdx) {
            case 0 -> { // The Swarm: Wolves 35%, Little Bombies 30%, Pig Zombies 20%, Normal Hard 15%
                if (r < 0.35f) yield ZombieType.ZOMBIE_WOLF;
                if (r < 0.65f) yield ZombieType.LITTLE_BOMBIE;
                if (r < 0.85f) yield ZombieType.PIG_ZOMBIE;
                yield ZombieType.NORMAL_HARD;
            }
            case 1 -> { // Infernal Siege: Fire Zombies 35%, Magma Zombies 30%, Magma Cubes 20%, Normal Hard 15%
                if (r < 0.35f) yield ZombieType.FIRE_ZOMBIE;
                if (r < 0.65f) yield ZombieType.MAGMA_ZOMBIE;
                if (r < 0.85f) yield ZombieType.MAGMA_CUBE;
                yield ZombieType.NORMAL_HARD;
            }
            case 2 -> { // Laser Vanguard: Guardian Zombies 35%, Normal Hard 35%, Pig Zombies 20%, Little Bombie 10%
                if (r < 0.35f) yield ZombieType.GUARDIAN_ZOMBIE;
                if (r < 0.70f) yield ZombieType.NORMAL_HARD;
                if (r < 0.90f) yield ZombieType.PIG_ZOMBIE;
                yield ZombieType.LITTLE_BOMBIE;
            }
            default -> { // 3: Apocalyptic Chaos: Equal mix of all elites
                if (r < 0.20f) yield ZombieType.GUARDIAN_ZOMBIE;
                if (r < 0.40f) yield ZombieType.MAGMA_ZOMBIE;
                if (r < 0.60f) yield ZombieType.FIRE_ZOMBIE;
                if (r < 0.80f) yield ZombieType.LITTLE_BOMBIE;
                yield ZombieType.NORMAL_HARD;
            }
        };
    }

    private ZombieType selectMobTypeForRound(int round) {
        float r = this.random.nextFloat();

        if (round <= 2) {
            return ZombieType.NORMAL_EASY;
        } else if (round <= 4) {
            return r < 0.65f ? ZombieType.NORMAL_EASY : ZombieType.NORMAL_MEDIUM;
        } else if (round <= 9) {
            if (r < 0.45f) return ZombieType.NORMAL_MEDIUM;
            if (r < 0.75f) return ZombieType.PIG_ZOMBIE;
            return ZombieType.MAGMA_CUBE;
        } else if (round == 10) {
            return r < 0.60f ? ZombieType.NORMAL_MEDIUM : ZombieType.PIG_ZOMBIE;
        } else if (round <= 14) {
            if (r < 0.35f) return ZombieType.NORMAL_MEDIUM;
            if (r < 0.65f) return ZombieType.NORMAL_HARD;
            if (r < 0.85f) return ZombieType.PIG_ZOMBIE;
            return ZombieType.FIRE_ZOMBIE;
        } else if (round <= 19) {
            if (r < 0.30f) return ZombieType.NORMAL_HARD;
            if (r < 0.55f) return ZombieType.LITTLE_BOMBIE;
            if (r < 0.80f) return ZombieType.FIRE_ZOMBIE;
            return ZombieType.ZOMBIE_WOLF;
        } else if (round == 20) {
            if (r < 0.50f) return ZombieType.MAGMA_ZOMBIE;
            if (r < 0.80f) return ZombieType.FIRE_ZOMBIE;
            return ZombieType.NORMAL_HARD;
        } else if (round <= 24) {
            if (r < 0.30f) return ZombieType.NORMAL_HARD;
            if (r < 0.60f) return ZombieType.GUARDIAN_ZOMBIE;
            if (r < 0.80f) return ZombieType.LITTLE_BOMBIE;
            return ZombieType.PIG_ZOMBIE;
        } else if (round <= 29) {
            if (r < 0.30f) return ZombieType.NORMAL_HARD;
            if (r < 0.60f) return ZombieType.GUARDIAN_ZOMBIE;
            if (r < 0.80f) return ZombieType.MAGMA_ZOMBIE;
            return ZombieType.LITTLE_BOMBIE;
        } else {
            // Round 30
            if (r < 0.50f) return ZombieType.NORMAL_HARD;
            return ZombieType.GUARDIAN_ZOMBIE;
        }
    }
}
