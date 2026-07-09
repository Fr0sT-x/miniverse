package dev.frost.miniverse.minigame.impl.bedwars.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

public class BedwarsFireballEntity extends FireballEntity {

    private final float customExplosionPower;

    public BedwarsFireballEntity(World world, LivingEntity owner, Vec3d velocity, float explosionPower) {
        super(world, owner, velocity, (int) explosionPower);
        this.customExplosionPower = explosionPower;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        if (!this.getWorld().isClient) {
            boolean bl = this.getWorld().getGameRules().getBoolean(net.minecraft.world.GameRules.DO_MOB_GRIEFING);
            this.getWorld().createExplosion(this, this.getX(), this.getY(), this.getZ(), this.customExplosionPower, bl, World.ExplosionSourceType.MOB);

            // Custom knockback logic for players
            double radius = this.customExplosionPower * 3.0; // Increase affect radius for knockback
            Box box = new Box(this.getX() - radius, this.getY() - radius, this.getZ() - radius,
                              this.getX() + radius, this.getY() + radius, this.getZ() + radius);
            List<ServerPlayerEntity> players = this.getWorld().getEntitiesByClass(ServerPlayerEntity.class, box, p -> true);

            for (ServerPlayerEntity p : players) {
                double dist = p.squaredDistanceTo(this.getX(), this.getY(), this.getZ());
                if (dist < radius * radius) {
                    Vec3d dir = p.getPos().subtract(this.getPos());
                    if (dir.lengthSquared() == 0) {
                        dir = new Vec3d(0, 1, 0);
                    } else {
                        dir = dir.normalize();
                    }
                    
                    double strength = 1.0 - (Math.sqrt(dist) / radius);
                    
                    // Boost based on typical Hypixel mechanics
                    double boostX = dir.x * strength * 2.5;
                    double boostY = (dir.y * strength * 1.5) + 0.8;
                    double boostZ = dir.z * strength * 2.5;
                    
                    p.addVelocity(boostX, boostY, boostZ);
                    p.velocityModified = true; // Sync to client immediately
                }
            }

            this.discard();
        }
    }
}
