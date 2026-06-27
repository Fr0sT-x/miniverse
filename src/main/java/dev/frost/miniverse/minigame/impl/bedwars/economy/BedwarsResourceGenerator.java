package dev.frost.miniverse.minigame.impl.bedwars.economy;

import dev.frost.miniverse.map.MapPosition;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class BedwarsResourceGenerator {
    private final MapPosition position;
    private final BedwarsCurrency currency;
    private final int maxStack;
    
    private int defaultInterval;
    private int currentInterval;
    private int ticksElapsed = 0;

    private final boolean isTeamGenerator;
    private final String teamId;
    private dev.frost.miniverse.minigame.impl.bedwars.HologramManager hologramManager;
    private net.minecraft.entity.decoration.ArmorStandEntity hologramEntity;

    public BedwarsResourceGenerator(MapPosition position, BedwarsCurrency currency, int defaultInterval, int maxStack, boolean isTeamGenerator, String teamId) {
        this.position = position;
        this.currency = currency;
        this.defaultInterval = defaultInterval;
        this.currentInterval = defaultInterval;
        this.maxStack = maxStack;
        this.isTeamGenerator = isTeamGenerator;
        this.teamId = teamId;
    }

    public String getTeamId() {
        return this.teamId;
    }

    public void setHologramManager(dev.frost.miniverse.minigame.impl.bedwars.HologramManager manager) {
        this.hologramManager = manager;
    }

    public BedwarsCurrency getCurrency() {
        return this.currency;
    }

    public void tick(ServerWorld world) {
        this.ticksElapsed++;
        
        if (this.hologramManager != null && !this.isTeamGenerator) {
            if (this.hologramEntity == null) {
                this.hologramEntity = this.hologramManager.createHologram(world, position.x() + 0.5, position.y() + 2.0, position.z() + 0.5, net.minecraft.text.Text.literal(""));
            }
            int secondsLeft = (this.currentInterval - this.ticksElapsed) / 20;
            String text = "Spawns in " + secondsLeft + "s";
            this.hologramEntity.setCustomName(net.minecraft.text.Text.literal(text).formatted(this.currency.formatting()));
        }
        
        if (this.ticksElapsed >= this.currentInterval) {
            this.ticksElapsed = 0;
            this.spawnResource(world);
        }
    }

    private void spawnResource(ServerWorld world) {
        Vec3d center = new Vec3d(position.x(), position.y(), position.z());
        Box box = new Box(center.subtract(1.5, 1.5, 1.5), center.add(1.5, 1.5, 1.5));
        
        if (isTeamGenerator) {
            List<ServerPlayerEntity> playersNear = world.getEntitiesByType(net.minecraft.entity.EntityType.PLAYER, box, p -> p.isAlive() && !p.isSpectator())
                .stream()
                .map(p -> (ServerPlayerEntity) p)
                .collect(java.util.stream.Collectors.toList());
                
            if (!playersNear.isEmpty()) {
                // Split resources among players
                for (ServerPlayerEntity p : playersNear) {
                    p.getInventory().offerOrDrop(new ItemStack(currency.item(), 1));
                }
                return;
            }
        }
        
        long count = world.getEntitiesByType(EntityType.ITEM, box, 
            itemEntity -> itemEntity.getStack().getItem() == currency.item())
            .stream()
            .mapToLong(itemEntity -> itemEntity.getStack().getCount())
            .sum();
            
        if (count >= maxStack) {
            return;
        }

        ItemStack stack = new ItemStack(currency.item(), 1);
        ItemEntity entity = new ItemEntity(world, position.x(), position.y(), position.z(), stack);
        entity.setVelocity(0, 0, 0);
        world.spawnEntity(entity);
    }

    public void setIntervalModifier(double multiplier) {
        this.currentInterval = Math.max(1, (int) (this.defaultInterval * multiplier));
    }
}
