package dev.frost.miniverse.minigame.impl.microfrenzy.rule.impl;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.RegionPart;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMapConfig;
import dev.frost.miniverse.minigame.impl.microfrenzy.MicroFrenzyMinigame;
import dev.frost.miniverse.minigame.impl.microfrenzy.rule.MicroRule;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

public class CenterStageRule implements MicroRule {
    @Override
    public String id() {
        return "center_stage";
    }

    @Override
    public String name() {
        return "Center Stage";
    }

    @Override
    public String description() {
        return "Rush to the exact center of the arena platform.";
    }

    @Override
    public Text title() {
        return Text.literal("CENTER STAGE!").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Rush to the exact center of the arena!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    public static double getMaxDistance(MicroFrenzyMinigame game) {
        if (game == null) return 3.5;
        float factor = game.getSpeedFactor();
        if (factor >= 0.7f) return 3.5;
        if (factor >= 0.5f) return 4.5;
        return 5.5; // Wider capture radius at frenzy speed
    }

    @Override
    public boolean isApplicable(MicroFrenzyMapConfig mapConfig) {
        return mapConfig != null && (mapConfig.arenaCenter() != null || !mapConfig.arenaBounds().isEmpty() || !mapConfig.playerSpawns().isEmpty());
    }

    private MapPosition getEffectiveCenter(MicroFrenzyMapConfig config) {
        if (config == null) return MapPosition.of(0, 100, 0);
        if (config.arenaCenter() != null) return config.arenaCenter();
        if (!config.arenaBounds().isEmpty()) {
            RegionPart first = config.arenaBounds().get(0);
            double midX = (first.min().x() + first.max().x()) / 2.0;
            double midZ = (first.min().z() + first.max().z()) / 2.0;
            double y = !config.playerSpawns().isEmpty() ? config.playerSpawns().get(0).y() : first.min().y() + 1.0;
            return new MapPosition(midX, y, midZ, 0, 0);
        }
        if (!config.playerSpawns().isEmpty()) return config.playerSpawns().get(0);
        return MapPosition.of(0, 100, 0);
    }

    @Override
    public void onStart(MicroFrenzyMinigame game, MinecraftServer server) {
        MapPosition center = getEffectiveCenter(game.getMapConfig());
        if (game.getWorld() != null) {
            ServerWorld world = game.getWorld();
            world.spawnParticles(ParticleTypes.FLAME, center.x() + 0.5, center.y() + 0.2, center.z() + 0.5, 30, 0.5, 0.1, 0.5, 0.05);
        }
    }

    @Override
    public void onTick(MicroFrenzyMinigame game, MinecraftServer server, int remainingTicks) {
        MapPosition center = getEffectiveCenter(game.getMapConfig());
        if (game.getWorld() != null) {
            ServerWorld world = game.getWorld();
            world.spawnParticles(ParticleTypes.END_ROD, center.x() + 0.5, center.y() + 0.1, center.z() + 0.5, 4, 0.4, 0.1, 0.4, 0.01);
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroFrenzyMinigame game) {
        MapPosition center = getEffectiveCenter(game.getMapConfig());
        Vec3d target = new Vec3d(center.x() + 0.5, center.y(), center.z() + 0.5);
        double maxDist = getMaxDistance(game);
        return player.getPos().squaredDistanceTo(target) <= (maxDist * maxDist);
    }

    @Override
    public void onEnd(MicroFrenzyMinigame game, MinecraftServer server) {
    }
}
