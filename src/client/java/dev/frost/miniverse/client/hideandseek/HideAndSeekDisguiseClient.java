package dev.frost.miniverse.client.hideandseek;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks client-side disguise states for players in Hide and Seek.
 * Guaranteed to be inert unless active Hide and Seek gamemode is in progress.
 */
public final class HideAndSeekDisguiseClient {
    private static volatile boolean active = false;
    private static final Map<UUID, BlockState> DISGUISES = new ConcurrentHashMap<>();
    private static final Set<UUID> SOLIDIFIED = ConcurrentHashMap.newKeySet();

    private HideAndSeekDisguiseClient() {}

    public static void setActive(boolean isActive) {
        active = isActive;
        if (!active) {
            clear();
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static void setDisguise(UUID uuid, String blockId) {
        if (uuid == null) return;
        if (blockId == null || blockId.isBlank()) {
            removeDisguise(uuid);
            return;
        }

        active = true;

        Identifier id = Identifier.tryParse(blockId);
        if (id != null) {
            Block block = Registries.BLOCK.get(id);
            if (block != null && block != Blocks.AIR) {
                DISGUISES.put(uuid, block.getDefaultState());
                recalculatePlayerDimensions(uuid);
                return;
            }
        }
        DISGUISES.put(uuid, Blocks.CRAFTING_TABLE.getDefaultState());
        recalculatePlayerDimensions(uuid);
    }

    public static void removeDisguise(UUID uuid) {
        if (uuid == null) return;
        DISGUISES.remove(uuid);
        SOLIDIFIED.remove(uuid);
        if (DISGUISES.isEmpty()) {
            active = false;
        }
        recalculatePlayerDimensions(uuid);
    }

    public static BlockState getDisguise(UUID uuid) {
        if (!active || uuid == null) return null;
        return DISGUISES.get(uuid);
    }

    public static boolean isDisguised(UUID uuid) {
        return active && uuid != null && DISGUISES.containsKey(uuid);
    }

    public static void setSolidified(UUID uuid, boolean solidified) {
        if (uuid == null) return;
        if (solidified) {
            SOLIDIFIED.add(uuid);
        } else {
            SOLIDIFIED.remove(uuid);
        }
        recalculatePlayerDimensions(uuid);
    }

    public static boolean isSolidified(UUID uuid) {
        return active && uuid != null && SOLIDIFIED.contains(uuid);
    }

    public static void clear() {
        active = false;
        DISGUISES.clear();
        SOLIDIFIED.clear();
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null) {
                if (client.player != null) {
                    client.player.calculateDimensions();
                }
                if (client.world != null) {
                    for (net.minecraft.entity.player.PlayerEntity p : client.world.getPlayers()) {
                        p.calculateDimensions();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void recalculatePlayerDimensions(UUID uuid) {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                net.minecraft.entity.player.PlayerEntity p = client.world.getPlayerByUuid(uuid);
                if (p != null) {
                    p.calculateDimensions();
                }
            }
        } catch (Throwable ignored) {}
    }
}
