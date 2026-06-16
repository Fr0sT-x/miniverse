package dev.frost.miniverse.minigame.core.death;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record DeathContext(
    UUID victimId,
    String victimName,
    @Nullable Entity killer,
    DamageSource damageSource,
    RegistryKey<World> dimension,
    Vec3d location,
    float yawAtDeath,
    float pitchAtDeath,
    long timestamp,
    @Nullable String victimTeamId,
    @Nullable String matchIdentifier,
    @Nullable UUID spectatorTargetAtDeath
) {
}
