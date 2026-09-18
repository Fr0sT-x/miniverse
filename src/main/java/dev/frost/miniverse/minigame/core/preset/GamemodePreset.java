package dev.frost.miniverse.minigame.core.preset;

import net.minecraft.nbt.NbtCompound;

public record GamemodePreset(
    String gameId,
    String name,
    long createdAt,
    long updatedAt,
    NbtCompound settings
) {
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("gameId", this.gameId);
        nbt.putString("name", this.name);
        nbt.putLong("createdAt", this.createdAt);
        nbt.putLong("updatedAt", this.updatedAt);
        nbt.put("settings", this.settings != null ? this.settings.copy() : new NbtCompound());
        return nbt;
    }

    public static GamemodePreset fromNbt(NbtCompound nbt) {
        String gameId = nbt.getString("gameId");
        String name = nbt.getString("name");
        long createdAt = nbt.getLong("createdAt");
        long updatedAt = nbt.getLong("updatedAt");
        NbtCompound settings = nbt.getCompound("settings");
        return new GamemodePreset(gameId, name, createdAt, updatedAt, settings);
    }
}
