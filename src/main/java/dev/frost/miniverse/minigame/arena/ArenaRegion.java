package dev.frost.miniverse.minigame.arena;

import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;

/**
 * Represents the configuration properties of an arena region before it is instantiated.
 */
public record ArenaRegion(String id, Vec3d min, Vec3d max, Map<String, Vec3d> spawns, List<String> tags) {
}
