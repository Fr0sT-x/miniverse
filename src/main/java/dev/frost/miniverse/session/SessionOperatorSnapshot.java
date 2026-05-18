package dev.frost.miniverse.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.server.OperatorEntry;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SessionOperatorSnapshot(List<Entry> entries) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public SessionOperatorSnapshot {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static SessionOperatorSnapshot empty() {
        return new SessionOperatorSnapshot(List.of());
    }

    public static SessionOperatorSnapshot capture(MinecraftServer server, Collection<SessionGroup> groups) {
        if (server == null || groups == null || groups.isEmpty()) {
            return empty();
        }

        Map<UUID, Entry> entries = new LinkedHashMap<>();
        for (SessionGroup group : groups) {
            for (SessionMembership member : group.getPlannedTeam().members()) {
                captureMember(server, member, entries);
            }
        }
        return new SessionOperatorSnapshot(List.copyOf(entries.values()));
    }

    public SessionOperatorSnapshot forGroups(Collection<SessionGroup> groups) {
        if (groups == null || groups.isEmpty() || this.entries.isEmpty()) {
            return empty();
        }

        Map<UUID, Entry> byUuid = new LinkedHashMap<>();
        for (Entry entry : this.entries) {
            byUuid.put(entry.uuid(), entry);
        }

        Map<UUID, Entry> filtered = new LinkedHashMap<>();
        for (SessionGroup group : groups) {
            for (SessionMembership member : group.getPlannedTeam().members()) {
                Entry entry = byUuid.get(member.playerUuid());
                if (entry != null) {
                    filtered.put(entry.uuid(), entry);
                }
            }
        }
        return new SessionOperatorSnapshot(List.copyOf(filtered.values()));
    }

    public void writeOpsJson(Path workingDirectory) throws IOException {
        if (this.entries.isEmpty()) {
            return;
        }

        JsonArray operators = new JsonArray();
        for (Entry entry : this.entries) {
            JsonObject operator = new JsonObject();
            operator.addProperty("uuid", entry.uuid().toString());
            operator.addProperty("name", entry.name());
            operator.addProperty("level", entry.level());
            operator.addProperty("bypassesPlayerLimit", entry.bypassesPlayerLimit());
            operators.add(operator);
        }

        Files.writeString(
            workingDirectory.resolve("ops.json"),
            GSON.toJson(operators) + System.lineSeparator(),
            java.nio.charset.StandardCharsets.UTF_8
        );
    }

    private static void captureMember(MinecraftServer server, SessionMembership member, Map<UUID, Entry> entries) {
        PlayerConfigEntry playerEntry = new PlayerConfigEntry(member.playerUuid(), member.playerName());
        OperatorEntry operatorEntry = findOperatorEntry(server, playerEntry);
        if (operatorEntry != null) {
            entries.put(member.playerUuid(), Entry.from(operatorEntry, member));
            return;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(member.playerUuid());
        if (player == null || !server.getPlayerManager().isOperator(new PlayerConfigEntry(player.getGameProfile()))) {
            return;
        }

        int level = player.getPermissions() instanceof LeveledPermissionPredicate leveled
            ? leveled.getLevel().getLevel()
            : 4;
        entries.put(member.playerUuid(), new Entry(member.playerUuid(), player.getName().getString(), normalizeLevel(level), false));
    }

    private static OperatorEntry findOperatorEntry(MinecraftServer server, PlayerConfigEntry playerEntry) {
        OperatorEntry exact = server.getPlayerManager().getOpList().get(playerEntry);
        if (exact != null) {
            return exact;
        }

        for (OperatorEntry entry : server.getPlayerManager().getOpList().values()) {
            PlayerConfigEntry key = entry.getKey();
            if (key != null && key.id().equals(playerEntry.id())) {
                return entry;
            }
        }
        return null;
    }

    private static int normalizeLevel(int level) {
        return Math.clamp(level, 1, 4);
    }

    public record Entry(UUID uuid, String name, int level, boolean bypassesPlayerLimit) {
        public Entry {
            if (uuid == null) {
                throw new IllegalArgumentException("Operator UUID cannot be null.");
            }
            name = name == null || name.isBlank() ? uuid.toString() : name.trim();
            level = normalizeLevel(level);
        }

        private static Entry from(OperatorEntry operatorEntry, SessionMembership member) {
            PlayerConfigEntry key = operatorEntry.getKey();
            UUID uuid = key == null ? member.playerUuid() : key.id();
            String name = key == null || key.name() == null || key.name().isBlank() ? member.playerName() : key.name();
            return new Entry(
                uuid,
                name,
                operatorEntry.getLevel().getLevel().getLevel(),
                operatorEntry.canBypassPlayerLimit()
            );
        }
    }
}
