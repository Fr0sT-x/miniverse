package dev.frost.miniverse.minigame.core.persistence;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public record PlayerStateSnapshot(
    UUID playerId,
    String playerName,
    String dimension,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    String gameMode,
    float health,
    int food,
    float saturation,
    int totalExperience,
    int experienceLevel,
    float experienceProgress,
    int fireTicks,
    float fallDistance,
    String inventorySnbt,
    JsonArray effects
) {
    public static PlayerStateSnapshot capture(ServerPlayerEntity player) {
        NbtList inventory = new NbtList();
        player.getInventory().writeNbt(inventory);
        NbtCompound inventoryRoot = new NbtCompound();
        inventoryRoot.put("items", inventory);

        JsonArray effects = new JsonArray();
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            Identifier id = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
            if (id == null) {
                continue;
            }
            JsonObject object = new JsonObject();
            object.addProperty("id", id.toString());
            object.addProperty("duration", effect.getDuration());
            object.addProperty("amplifier", effect.getAmplifier());
            object.addProperty("ambient", effect.isAmbient());
            object.addProperty("showParticles", effect.shouldShowParticles());
            object.addProperty("showIcon", effect.shouldShowIcon());
            effects.add(object);
        }

        return new PlayerStateSnapshot(
            player.getUuid(),
            player.getName().getString(),
            player.getEntityWorld().getRegistryKey().getValue().toString(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYaw(),
            player.getPitch(),
            player.interactionManager.getGameMode().getName(),
            player.getHealth(),
            player.getHungerManager().getFoodLevel(),
            player.getHungerManager().getSaturationLevel(),
            player.totalExperience,
            player.experienceLevel,
            player.experienceProgress,
            player.getFireTicks(),
            player.fallDistance,
            inventoryRoot.toString(),
            effects
        );
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("uuid", this.playerId.toString());
        object.addProperty("name", this.playerName);
        object.addProperty("dimension", this.dimension);
        object.addProperty("x", this.x);
        object.addProperty("y", this.y);
        object.addProperty("z", this.z);
        object.addProperty("yaw", this.yaw);
        object.addProperty("pitch", this.pitch);
        object.addProperty("gameMode", this.gameMode);
        object.addProperty("health", this.health);
        object.addProperty("food", this.food);
        object.addProperty("saturation", this.saturation);
        object.addProperty("totalExperience", this.totalExperience);
        object.addProperty("experienceLevel", this.experienceLevel);
        object.addProperty("experienceProgress", this.experienceProgress);
        object.addProperty("fireTicks", this.fireTicks);
        object.addProperty("fallDistance", this.fallDistance);
        object.addProperty("inventory", this.inventorySnbt);
        object.add("effects", this.effects == null ? new JsonArray() : this.effects.deepCopy());
        return object;
    }

    @Nullable
    public static PlayerStateSnapshot fromJson(JsonObject object) {
        UUID uuid = uuidValue(object, "uuid");
        if (uuid == null) {
            return null;
        }
        return new PlayerStateSnapshot(
            uuid,
            stringValue(object, "name", uuid.toString()),
            stringValue(object, "dimension", World.OVERWORLD.getValue().toString()),
            doubleValue(object, "x", 0.0D),
            doubleValue(object, "y", 64.0D),
            doubleValue(object, "z", 0.0D),
            floatValue(object, "yaw", 0.0F),
            floatValue(object, "pitch", 0.0F),
            stringValue(object, "gameMode", GameMode.SURVIVAL.getName()),
            floatValue(object, "health", 20.0F),
            intValue(object, "food", 20),
            floatValue(object, "saturation", 5.0F),
            intValue(object, "totalExperience", 0),
            intValue(object, "experienceLevel", 0),
            floatValue(object, "experienceProgress", 0.0F),
            intValue(object, "fireTicks", 0),
            floatValue(object, "fallDistance", 0.0F),
            stringValue(object, "inventory", "{items:[]}"),
            object.has("effects") && object.get("effects").isJsonArray() ? object.getAsJsonArray("effects") : new JsonArray()
        );
    }

    public void restore(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(this.dimension)));
        if (world == null) {
            world = server.getOverworld();
        }
        player.teleport(world, this.x, this.y, this.z, Set.of(), this.yaw, this.pitch);
        player.changeGameMode(GameMode.byName(this.gameMode, GameMode.SURVIVAL));
        player.setHealth(Math.clamp(this.health, 1.0F, player.getMaxHealth()));
        player.getHungerManager().setFoodLevel(Math.clamp(this.food, 0, 20));
        player.getHungerManager().setSaturationLevel(Math.clamp(this.saturation, 0.0F, 20.0F));
        player.totalExperience = Math.max(0, this.totalExperience);
        player.experienceLevel = Math.max(0, this.experienceLevel);
        player.experienceProgress = Math.clamp(this.experienceProgress, 0.0F, 1.0F);
        player.setFireTicks(Math.max(0, this.fireTicks));
        player.fallDistance = Math.max(0.0F, this.fallDistance);
        restoreInventory(player);
        restoreEffects(player);
    }

    private void restoreInventory(ServerPlayerEntity player) {
        try {
            NbtCompound root = net.minecraft.nbt.StringNbtReader.parse(this.inventorySnbt);
            NbtList inventory = root.getList("items", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            player.getInventory().readNbt(inventory);
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
        } catch (Exception ignored) {
        }
    }

    private void restoreEffects(ServerPlayerEntity player) {
        player.clearStatusEffects();
        if (this.effects == null) {
            return;
        }
        for (var element : this.effects) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            Identifier id = Identifier.tryParse(stringValue(object, "id", ""));
            if (id == null) {
                continue;
            }
            StatusEffect effect = Registries.STATUS_EFFECT.get(id);
            if (effect == null) {
                continue;
            }
            player.addStatusEffect(new StatusEffectInstance(
                Registries.STATUS_EFFECT.getEntry(effect),
                intValue(object, "duration", 0),
                intValue(object, "amplifier", 0),
                booleanValue(object, "ambient", false),
                booleanValue(object, "showParticles", true),
                booleanValue(object, "showIcon", true)
            ));
        }
    }

    private static UUID uuidValue(JsonObject object, String key) {
        try {
            return UUID.fromString(stringValue(object, key, ""));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double doubleValue(JsonObject object, String key, double fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsDouble() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float floatValue(JsonObject object, String key, float fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsFloat() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
