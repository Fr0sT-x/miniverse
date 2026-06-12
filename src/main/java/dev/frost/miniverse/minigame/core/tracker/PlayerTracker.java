package dev.frost.miniverse.minigame.core.tracker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import dev.frost.miniverse.minigame.core.item.TrackingItemNameFormatter;

public class PlayerTracker {

    private final Map<UUID, TrackingData> trackingData = new ConcurrentHashMap<>();

    /**
     * Updates the last known positions for a given iterable of targets.
     * Should be called on server tick for all players who might be tracked.
     */
    public void updatePositions(Iterable<ServerPlayerEntity> targets, boolean netherTrackingEnabled) {
        for (ServerPlayerEntity target : targets) {
            if (target.isDisconnected()) {
                continue;
            }
            TrackingData data = this.trackingData.computeIfAbsent(target.getUuid(), uuid -> new TrackingData());
            BlockPos position = target.getBlockPos();
            if (target.getEntityWorld().getRegistryKey().equals(World.OVERWORLD)) {
                data.lastOverworld = position;
            } else if (target.getEntityWorld().getRegistryKey().equals(World.NETHER)) {
                if (netherTrackingEnabled) {
                    data.lastNether = position;
                }
            } else if (target.getEntityWorld().getRegistryKey().equals(World.END)) {
                data.lastEnd = position;
                if (data.endEntryOverworld == null && data.lastOverworld != null) {
                    data.endEntryOverworld = data.lastOverworld;
                }
            }
        }
    }

    /**
     * Clear data for a player (e.g. when they leave)
     */
    public void remove(UUID playerId) {
        this.trackingData.remove(playerId);
    }

    public void clear() {
        this.trackingData.clear();
    }

    /**
     * Resolves the target tracking position for a hunter looking for a target.
     */
    public Optional<GlobalPos> resolveTrackingTarget(ServerPlayerEntity hunter, ServerPlayerEntity target, boolean netherTrackingEnabled) {
        TrackingData data = this.trackingData.computeIfAbsent(target.getUuid(), uuid -> new TrackingData());
        
        if (hunter.getEntityWorld().getRegistryKey().equals(World.NETHER)) {
            if (!netherTrackingEnabled || data.lastNether == null) {
                return Optional.empty();
            }
            return Optional.of(GlobalPos.create(World.NETHER, data.lastNether));
        }

        if (hunter.getEntityWorld().getRegistryKey().equals(World.END)) {
            BlockPos endPos = data.lastEnd != null ? data.lastEnd : target.getBlockPos();
            return Optional.of(GlobalPos.create(World.END, endPos));
        }

        BlockPos overworldPos = data.lastOverworld != null ? data.lastOverworld : target.getBlockPos();
        if (target.getEntityWorld().getRegistryKey().equals(World.END) && data.endEntryOverworld != null) {
            overworldPos = data.endEntryOverworld;
        }
        return Optional.of(GlobalPos.create(World.OVERWORLD, overworldPos));
    }
    
    /**
     * Updates the trackers in the hunter's inventory.
     * Handles setting the LodestoneTrackerComponent (if the item is a compass) 
     * and renaming the item with TrackingItemNameFormatter.
     */
    public void updateTrackerStacks(
            ServerPlayerEntity hunter, 
            ServerPlayerEntity targetPlayer, 
            Optional<GlobalPos> targetLocation, 
            Predicate<ItemStack> isTrackerPredicate, 
            boolean isCompassItemType) {
        
        LodestoneTrackerComponent tracker = null;
        if (isCompassItemType) {
            if (targetLocation.isPresent()) {
                tracker = new LodestoneTrackerComponent(targetLocation, false);
            } else {
                tracker = new LodestoneTrackerComponent(Optional.empty(), true); // "Mad" compass
            }
        }

        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            ItemStack stack = hunter.getInventory().getStack(slot);
            if (!isTrackerPredicate.test(stack)) {
                continue;
            }
            if (tracker != null) {
                stack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
            }
        }
        
        TrackingItemNameFormatter.applyTrackingName(hunter.getInventory(), isTrackerPredicate, targetPlayer.getDisplayName());
    }
    
    public TrackingData getData(UUID playerId) {
        return this.trackingData.computeIfAbsent(playerId, uuid -> new TrackingData());
    }

    public JsonArray writeTrackingData() {
        JsonArray array = new JsonArray();
        for (Map.Entry<UUID, TrackingData> entry : this.trackingData.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid", entry.getKey().toString());
            TrackingData data = entry.getValue();
            if (data.lastOverworld != null) obj.addProperty("lastOverworld", data.lastOverworld.asLong());
            if (data.lastNether != null) obj.addProperty("lastNether", data.lastNether.asLong());
            if (data.lastEnd != null) obj.addProperty("lastEnd", data.lastEnd.asLong());
            if (data.endEntryOverworld != null) obj.addProperty("endEntryOverworld", data.endEntryOverworld.asLong());
            array.add(obj);
        }
        return array;
    }

    public void readTrackingData(JsonArray array) {
        for (var element : array) {
            JsonObject obj = element.getAsJsonObject();
            UUID uuid = UUID.fromString(obj.get("uuid").getAsString());
            TrackingData data = new TrackingData();
            if (obj.has("lastOverworld")) data.lastOverworld = BlockPos.fromLong(obj.get("lastOverworld").getAsLong());
            if (obj.has("lastNether")) data.lastNether = BlockPos.fromLong(obj.get("lastNether").getAsLong());
            if (obj.has("lastEnd")) data.lastEnd = BlockPos.fromLong(obj.get("lastEnd").getAsLong());
            if (obj.has("endEntryOverworld")) data.endEntryOverworld = BlockPos.fromLong(obj.get("endEntryOverworld").getAsLong());
            this.trackingData.put(uuid, data);
        }
    }

    public static class TrackingData {
        @Nullable public BlockPos lastOverworld;
        @Nullable public BlockPos lastNether;
        @Nullable public BlockPos lastEnd;
        @Nullable public BlockPos endEntryOverworld;
    }
}
