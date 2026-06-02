package dev.frost.miniverse.map;

import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;

public record MapPosition(double x, double y, double z, float yaw, float pitch) {
    public static MapPosition of(double x, double y, double z) {
        return new MapPosition(x, y, z, 0.0F, 0.0F);
    }

    public static MapPosition fromJson(JsonObject json, MapPosition fallback) {
        if (json == null) {
            return fallback;
        }
        return new MapPosition(
            number(json, "x", fallback.x()),
            number(json, "y", fallback.y()),
            number(json, "z", fallback.z()),
            (float) number(json, "yaw", fallback.yaw()),
            (float) number(json, "pitch", fallback.pitch())
        );
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("x", this.x);
        json.addProperty("y", this.y);
        json.addProperty("z", this.z);
        json.addProperty("yaw", this.yaw);
        json.addProperty("pitch", this.pitch);
        return json;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putDouble("x", this.x);
        nbt.putDouble("y", this.y);
        nbt.putDouble("z", this.z);
        nbt.putFloat("yaw", this.yaw);
        nbt.putFloat("pitch", this.pitch);
        return nbt;
    }

    public Vec3d vec3d() {
        return new Vec3d(this.x, this.y, this.z);
    }

    private static double number(JsonObject json, String key, double fallback) {
        try {
            return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : fallback;
        } catch (NumberFormatException | IllegalStateException ignored) {
            return fallback;
        }
    }
}
