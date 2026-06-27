package dev.frost.miniverse.map;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record MapDescriptor(
    MapMetadata metadata,
    Path folder,
    Path worldFolder,
    Path thumbnail,
    List<String> supportedGamemodes,
    long lastModifiedMillis
) {
    /** Returns true if the world template folder exists and is a directory. */
    public boolean hasWorld() {
        return Files.isDirectory(this.worldFolder);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = this.metadata.toNbt();
        nbt.putString("folder", this.folder.toString());
        nbt.putString("worldFolder", this.worldFolder.toString());
        nbt.putString("thumbnail", this.thumbnail.toString());
        nbt.putLong("lastModified", this.lastModifiedMillis);
        nbt.putBoolean("hasWorld", this.hasWorld());
        NbtList games = new NbtList();
        for (String game : this.supportedGamemodes) {
            games.add(NbtString.of(game));
        }
        nbt.put("gamemodes", games);
        return nbt;
    }

    public boolean supports(String gameId) {
        return this.supportedGamemodes.stream().anyMatch(id -> id.equalsIgnoreCase(gameId));
    }

    public Instant lastModified() {
        return Instant.ofEpochMilli(this.lastModifiedMillis);
    }
}
