package dev.frost.miniverse.minigame.arena;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Arena {

    private final String id;
    private final ServerWorld world;
    private final Box bounds;
    private final Map<String, Vec3d> spawns;
    private final List<String> tags;

    private volatile ArenaState state = ArenaState.IDLE;
    private volatile UUID activeMatchId = null;

    private final Map<BlockPos, TrackedBlock> trackedBlocks = new ConcurrentHashMap<>();

    public record TrackedBlock(BlockState state, NbtCompound nbt) {}

    public Arena(ServerWorld world, ArenaRegion region) {
        this.id = region.id();
        this.world = world;
        this.bounds = new Box(region.min(), region.max());
        this.spawns = Map.copyOf(region.spawns());
        this.tags = List.copyOf(region.tags());
    }

    public String getId() {
        return id;
    }

    public ServerWorld getWorld() {
        return world;
    }

    public ArenaState getState() {
        return state;
    }

    public void setState(ArenaState state) {
        this.state = state;
    }

    public UUID getActiveMatchId() {
        return activeMatchId;
    }

    public void setActiveMatchId(UUID activeMatchId) {
        this.activeMatchId = activeMatchId;
    }

    public Vec3d getSpawn(String key) {
        return spawns.get(key);
    }

    public Map<String, Vec3d> getSpawns() {
        return spawns;
    }

    public List<String> getTags() {
        return tags;
    }

    public boolean contains(BlockPos pos) {
        return bounds.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }
    
    public boolean contains(Vec3d pos) {
        return bounds.contains(pos);
    }

    public void trackBlockChange(BlockPos pos, BlockState originalState, BlockEntity originalEntity) {
        if (state == ArenaState.RUNNING) {
            trackedBlocks.computeIfAbsent(pos.toImmutable(), k -> {
                NbtCompound nbt = null;
                if (originalEntity != null) {
                    nbt = originalEntity.createNbt(world.getRegistryManager());
                }
                return new TrackedBlock(originalState, nbt);
            });
        }
    }

    public void startReset() {
        if (state != ArenaState.RESETTING) {
            setState(ArenaState.RESETTING);
        }

        // Restore blocks
        for (Map.Entry<BlockPos, TrackedBlock> entry : trackedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            TrackedBlock tracked = entry.getValue();
            
            // Flags: NO_REDRAW | FORCE_STATE | SKIP_DROPS
            world.setBlockState(pos, tracked.state(), net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE | net.minecraft.block.Block.SKIP_DROPS);
            
            if (tracked.nbt() != null) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be != null) {
                    be.read(tracked.nbt(), world.getRegistryManager());
                }
            }
        }
        trackedBlocks.clear();

        // Clean up entities
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof PlayerEntity) continue;
            if (bounds.contains(entity.getPos())) {
                if (entity instanceof ItemEntity || entity instanceof ProjectileEntity) {
                    entity.discard();
                } else {
                    entity.discard();
                }
            }
        }

        if (validateResetCompletion()) {
            setActiveMatchId(null);
            setState(ArenaState.IDLE);
        } else {
            System.err.println("Arena " + id + " failed validation, forcing IDLE anyway but warning administrators.");
            setActiveMatchId(null);
            setState(ArenaState.IDLE);
        }
    }

    private boolean validateResetCompletion() {
        for (Entity entity : world.iterateEntities()) {
            if (bounds.contains(entity.getPos())) {
                if (entity instanceof PlayerEntity) {
                    System.err.println("Validation failed for Arena " + id + ": Player " + entity.getUuid() + " still in arena.");
                    return false;
                }
                if (!entity.isRemoved()) {
                    System.err.println("Validation failed for Arena " + id + ": Entity " + entity.getUuid() + " still in arena.");
                    return false;
                }
            }
        }
        return true;
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("state", state.name());
        if (activeMatchId != null) {
            json.addProperty("activeMatchId", activeMatchId.toString());
        }
        com.google.gson.JsonArray blocksArray = new com.google.gson.JsonArray();
        for (Map.Entry<BlockPos, TrackedBlock> entry : trackedBlocks.entrySet()) {
            com.google.gson.JsonObject blockJson = new com.google.gson.JsonObject();
            BlockPos pos = entry.getKey();
            blockJson.addProperty("x", pos.getX());
            blockJson.addProperty("y", pos.getY());
            blockJson.addProperty("z", pos.getZ());
            
            NbtCompound stateNbt = net.minecraft.nbt.NbtHelper.fromBlockState(entry.getValue().state());
            blockJson.addProperty("state", stateNbt.toString());
            
            if (entry.getValue().nbt() != null) {
                blockJson.addProperty("nbt", entry.getValue().nbt().toString());
            }
            blocksArray.add(blockJson);
        }
        json.add("trackedBlocks", blocksArray);
        return json;
    }

    public void loadRuntimeState(com.google.gson.JsonObject json) {
        if (json.has("state")) {
            this.state = ArenaState.valueOf(json.get("state").getAsString());
        }
        if (json.has("activeMatchId")) {
            this.activeMatchId = UUID.fromString(json.get("activeMatchId").getAsString());
        }
        if (json.has("trackedBlocks")) {
            com.google.gson.JsonArray blocksArray = json.getAsJsonArray("trackedBlocks");
            for (com.google.gson.JsonElement el : blocksArray) {
                com.google.gson.JsonObject obj = el.getAsJsonObject();
                BlockPos pos = new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
                try {
                    NbtCompound stateNbt = net.minecraft.nbt.StringNbtReader.parse(obj.get("state").getAsString());
                    net.minecraft.registry.RegistryEntryLookup<net.minecraft.block.Block> blockLookup = world.getRegistryManager().getWrapperOrThrow(net.minecraft.registry.RegistryKeys.BLOCK);
                    BlockState state = net.minecraft.nbt.NbtHelper.toBlockState(blockLookup, stateNbt);
                    
                    NbtCompound nbt = null;
                    if (obj.has("nbt")) {
                        nbt = net.minecraft.nbt.StringNbtReader.parse(obj.get("nbt").getAsString());
                    }
                    trackedBlocks.put(pos, new TrackedBlock(state, nbt));
                } catch (Exception e) {
                    System.err.println("Failed to parse NBT for tracked block at " + pos + " in arena " + id);
                }
            }
        }
    }
}
