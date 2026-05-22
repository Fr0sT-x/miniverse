package dev.frost.miniverse.session.plan;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.Optional;
import java.util.UUID;

public record PlayerRole(UUID playerUuid, String role) {
    public static Optional<PlayerRole> fromNbt(NbtCompound nbt) {
        String uuidString = nbt.contains("uuid", NbtElement.STRING_TYPE) ? nbt.getString("uuid") : "";
        String role = nbt.contains("role", NbtElement.STRING_TYPE) ? nbt.getString("role") : "";
        if (uuidString.isBlank() || role.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new PlayerRole(UUID.fromString(uuidString), role));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("uuid", this.playerUuid.toString());
        nbt.putString("role", this.role);
        return nbt;
    }
}
