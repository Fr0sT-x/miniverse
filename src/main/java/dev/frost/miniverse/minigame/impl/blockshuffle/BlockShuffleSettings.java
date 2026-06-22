package dev.frost.miniverse.minigame.impl.blockshuffle;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public record BlockShuffleSettings(
    int roundDurationSeconds,
    int pointsToWin,
    boolean perPlayerBlocks,
    Set<Identifier> blockPool,
    int respawnDelaySeconds
) {
    public static BlockShuffleSettings defaults() {
        return new BlockShuffleSettings(
            300,
            5,
            true,
            new HashSet<>(BlockShuffleWeights.STANDARD_POOL),
            5
        );
    }

    public static BlockShuffleSettings fromNbt(NbtCompound nbt) {
        if (nbt == null || nbt.isEmpty()) {
            return defaults();
        }

        int roundDurationSeconds = nbt.contains("roundDurationSeconds") ? nbt.getInt("roundDurationSeconds") : 300;
        int pointsToWin = nbt.contains("pointsToWin") ? nbt.getInt("pointsToWin") : 5;
        boolean perPlayerBlocks = nbt.contains("perPlayerBlocks") ? nbt.getBoolean("perPlayerBlocks") : true;
        
        Set<Identifier> blockPool = new HashSet<>();
        if (nbt.contains("blockPool", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("blockPool", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                blockPool.add(Identifier.of(list.getString(i)));
            }
        } else {
            blockPool.addAll(BlockShuffleWeights.STANDARD_POOL);
        }

        int respawnDelaySeconds = nbt.contains("respawnDelaySeconds") ? nbt.getInt("respawnDelaySeconds") : 5;

        return new BlockShuffleSettings(roundDurationSeconds, pointsToWin, perPlayerBlocks, blockPool, respawnDelaySeconds);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("roundDurationSeconds", this.roundDurationSeconds);
        nbt.putInt("pointsToWin", this.pointsToWin);
        nbt.putBoolean("perPlayerBlocks", this.perPlayerBlocks);
        nbt.putInt("respawnDelaySeconds", this.respawnDelaySeconds);

        NbtList list = new NbtList();
        for (Identifier id : this.blockPool) {
            list.add(NbtString.of(id.toString()));
        }
        nbt.put("blockPool", list);
        return nbt;
    }

    public void writeTo(Properties properties) {
        properties.setProperty("blockshuffle.roundDurationSeconds", String.valueOf(this.roundDurationSeconds));
        properties.setProperty("blockshuffle.pointsToWin", String.valueOf(this.pointsToWin));
        properties.setProperty("blockshuffle.perPlayerBlocks", String.valueOf(this.perPlayerBlocks));
        properties.setProperty("blockshuffle.respawnDelaySeconds", String.valueOf(this.respawnDelaySeconds));
    }

    public static BlockShuffleSettings fromProperties(Properties properties) {
        if (properties == null) {
            return defaults();
        }

        int roundDurationSeconds = parsePropertyInt(properties, "blockshuffle.roundDurationSeconds", 300);
        int pointsToWin = parsePropertyInt(properties, "blockshuffle.pointsToWin", 5);
        boolean perPlayerBlocks = Boolean.parseBoolean(properties.getProperty("blockshuffle.perPlayerBlocks", "true"));
        int respawnDelaySeconds = parsePropertyInt(properties, "blockshuffle.respawnDelaySeconds", 5);
        
        Set<Identifier> blockPool = new HashSet<>(BlockShuffleWeights.STANDARD_POOL);
        return new BlockShuffleSettings(roundDurationSeconds, pointsToWin, perPlayerBlocks, blockPool, respawnDelaySeconds);
    }

    private static int parsePropertyInt(Properties properties, String key, int defaultValue) {
        try {
            String value = properties.getProperty(key);
            return value == null ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
