package dev.frost.miniverse.session.plan;

import net.minecraft.nbt.NbtCompound;

import java.util.Optional;
import java.util.UUID;

public record PlayerRef(UUID uuid, String name) {
    public static Optional<PlayerRef> fromNbt(NbtCompound nbt) {
        String uuidString = nbt.getString("uuid", "");
        if (uuidString.isBlank()) {
            return Optional.empty();
        }

        try {
            UUID uuid = UUID.fromString(uuidString);
            String name = nbt.getString("name", uuidString);
            return Optional.of(new PlayerRef(uuid, name));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("uuid", this.uuid.toString());
        nbt.putString("name", this.name);
        return nbt;
    }
}
