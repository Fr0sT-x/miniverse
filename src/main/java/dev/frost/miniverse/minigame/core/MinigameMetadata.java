package dev.frost.miniverse.minigame.core;

import dev.frost.miniverse.session.SessionTopology;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.List;

public record MinigameMetadata(
    String id,
    String displayName,
    String description,
    String icon,
    SessionTopology topology,
    SetupKind setupKind,
    boolean enabled,
    List<SetupField> fields
) {
    public MinigameMetadata {
        id = id == null ? "" : id.trim();
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        description = description == null ? "" : description.trim();
        icon = icon == null || icon.isBlank() ? "?" : icon.trim();
        topology = topology == null ? SessionTopology.SHARED_WORLD : topology;
        setupKind = setupKind == null ? SetupKind.GENERIC : setupKind;
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static MinigameMetadata custom(String id, String displayName, String description, String icon, SessionTopology topology) {
        return new MinigameMetadata(id, displayName, description, icon, topology, SetupKind.CUSTOM, true, List.of());
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", this.id);
        nbt.putString("displayName", this.displayName);
        nbt.putString("description", this.description);
        nbt.putString("icon", this.icon);
        nbt.putString("topology", this.topology.name());
        nbt.putString("setupKind", this.setupKind.name().toLowerCase(java.util.Locale.ROOT));
        nbt.putBoolean("enabled", this.enabled);

        NbtList fieldList = new NbtList();
        for (SetupField field : this.fields) {
            fieldList.add(field.toNbt());
        }
        nbt.put("fields", fieldList);
        return nbt;
    }

    public enum SetupKind {
        CUSTOM,
        GENERIC
    }

    public record SetupField(
        String key,
        String label,
        FieldType type,
        String defaultValue,
        boolean required,
        int min,
        int max
    ) {
        public SetupField {
            key = key == null ? "" : key.trim();
            label = label == null || label.isBlank() ? key : label.trim();
            type = type == null ? FieldType.STRING : type;
            defaultValue = defaultValue == null ? "" : defaultValue;
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("key", this.key);
            nbt.putString("label", this.label);
            nbt.putString("type", this.type.name().toLowerCase(java.util.Locale.ROOT));
            nbt.putString("default", this.defaultValue);
            nbt.putBoolean("required", this.required);
            nbt.putInt("min", this.min);
            nbt.putInt("max", this.max);
            return nbt;
        }
    }

    public enum FieldType {
        STRING,
        INTEGER,
        BOOLEAN
    }
}
