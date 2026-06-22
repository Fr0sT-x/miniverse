package dev.frost.miniverse.minigame.impl.deathshuffle;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public record DeathShuffleSettings(
    int roundDurationSeconds,
    int pointsToWin,
    int gracePeriodSeconds,
    boolean perPlayerObjectives,
    List<Identifier> activeObjectivePool,
    int respawnDelaySeconds
) {
    public static DeathShuffleSettings fromNbt(NbtCompound nbt) {
        int roundDurationSeconds = 300;
        if (nbt.contains("roundDurationSeconds", NbtElement.INT_TYPE)) {
            roundDurationSeconds = nbt.getInt("roundDurationSeconds");
        }
        
        int pointsToWin = 5;
        if (nbt.contains("pointsToWin", NbtElement.INT_TYPE)) {
            pointsToWin = nbt.getInt("pointsToWin");
        }
        
        int gracePeriodSeconds = 3;
        if (nbt.contains("gracePeriodSeconds", NbtElement.INT_TYPE)) {
            gracePeriodSeconds = nbt.getInt("gracePeriodSeconds");
        }

        boolean perPlayerObjectives = true;
        if (nbt.contains("perPlayerObjectives", NbtElement.BYTE_TYPE)) {
            perPlayerObjectives = nbt.getBoolean("perPlayerObjectives");
        }

        List<Identifier> pool = new ArrayList<>();
        if (nbt.contains("activeObjectivePool", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("activeObjectivePool", NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) {
                pool.add(Identifier.tryParse(list.getString(i)));
            }
        }

        int respawnDelaySeconds = 5;
        if (nbt.contains("respawnDelaySeconds", NbtElement.INT_TYPE)) {
            respawnDelaySeconds = nbt.getInt("respawnDelaySeconds");
        }

        return new DeathShuffleSettings(roundDurationSeconds, pointsToWin, gracePeriodSeconds, perPlayerObjectives, pool, respawnDelaySeconds);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("roundDurationSeconds", this.roundDurationSeconds);
        nbt.putInt("pointsToWin", this.pointsToWin);
        nbt.putInt("gracePeriodSeconds", this.gracePeriodSeconds);
        nbt.putBoolean("perPlayerObjectives", this.perPlayerObjectives);
        nbt.putInt("respawnDelaySeconds", this.respawnDelaySeconds);

        NbtList list = new NbtList();
        for (Identifier id : this.activeObjectivePool) {
            list.add(NbtString.of(id.toString()));
        }
        nbt.put("activeObjectivePool", list);
        return nbt;
    }

    public void writeTo(Properties properties) {
        properties.setProperty("death_shuffle.roundDurationSeconds", String.valueOf(this.roundDurationSeconds));
        properties.setProperty("death_shuffle.pointsToWin", String.valueOf(this.pointsToWin));
        properties.setProperty("death_shuffle.gracePeriodSeconds", String.valueOf(this.gracePeriodSeconds));
        properties.setProperty("death_shuffle.perPlayerObjectives", String.valueOf(this.perPlayerObjectives));
        properties.setProperty("death_shuffle.respawnDelaySeconds", String.valueOf(this.respawnDelaySeconds));
        
        String poolStr = String.join(",", this.activeObjectivePool.stream().map(Identifier::toString).toList());
        properties.setProperty("death_shuffle.activeObjectivePool", poolStr);
    }

    public static DeathShuffleSettings fromProperties(Properties properties) {
        int roundDurationSeconds = 300;
        try {
            roundDurationSeconds = Integer.parseInt(properties.getProperty("death_shuffle.roundDurationSeconds", "300"));
        } catch (NumberFormatException ignored) {}

        int pointsToWin = 5;
        try {
            pointsToWin = Integer.parseInt(properties.getProperty("death_shuffle.pointsToWin", "5"));
        } catch (NumberFormatException ignored) {}

        int gracePeriodSeconds = 3;
        try {
            gracePeriodSeconds = Integer.parseInt(properties.getProperty("death_shuffle.gracePeriodSeconds", "3"));
        } catch (NumberFormatException ignored) {}

        boolean perPlayerObjectives = Boolean.parseBoolean(properties.getProperty("death_shuffle.perPlayerObjectives", "true"));

        List<Identifier> pool = new ArrayList<>();
        String poolStr = properties.getProperty("death_shuffle.activeObjectivePool", "");
        if (!poolStr.isBlank()) {
            for (String s : poolStr.split(",")) {
                Identifier id = Identifier.tryParse(s.trim());
                if (id != null) {
                    pool.add(id);
                }
            }
        }

        int respawnDelaySeconds = 5;
        try {
            respawnDelaySeconds = Integer.parseInt(properties.getProperty("death_shuffle.respawnDelaySeconds", "5"));
        } catch (NumberFormatException ignored) {}

        return new DeathShuffleSettings(roundDurationSeconds, pointsToWin, gracePeriodSeconds, perPlayerObjectives, pool, respawnDelaySeconds);
    }
}
