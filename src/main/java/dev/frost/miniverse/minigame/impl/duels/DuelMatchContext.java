package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.arena.Arena;
import dev.frost.miniverse.minigame.core.kit.Kit;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class DuelMatchContext {
    private final UUID matchId;
    private final Arena arena;
    private final Kit kit;
    private final MatchRules matchRules;
    private final List<ServerPlayerEntity> team1;
    private final List<ServerPlayerEntity> team2;
    private final List<ServerPlayerEntity> players;
    private final Instant startTime;
    private final NbtCompound metadata;

    public DuelMatchContext(UUID matchId, Arena arena, Kit kit, MatchRules matchRules, List<ServerPlayerEntity> team1, List<ServerPlayerEntity> team2) {
        this.matchId = matchId;
        this.arena = arena;
        this.kit = kit;
        this.matchRules = matchRules;
        this.team1 = new java.util.ArrayList<>(team1);
        this.team2 = new java.util.ArrayList<>(team2);
        
        List<ServerPlayerEntity> all = new java.util.ArrayList<>();
        all.addAll(this.team1);
        all.addAll(this.team2);
        this.players = new java.util.ArrayList<>(all);
        
        this.startTime = Instant.now();
        this.metadata = new NbtCompound();
    }

    public UUID getMatchId() {
        return matchId;
    }

    public Arena getArena() {
        return arena;
    }

    public Kit getKit() {
        return kit;
    }

    public MatchRules getMatchRules() {
        return matchRules;
    }

    public List<ServerPlayerEntity> getTeam1() {
        return team1;
    }

    public List<ServerPlayerEntity> getTeam2() {
        return team2;
    }

    public List<ServerPlayerEntity> getPlayers() {
        return players;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public NbtCompound getMetadata() {
        return metadata;
    }

    public void updatePlayerReference(ServerPlayerEntity newPlayer) {
        for (int i = 0; i < team1.size(); i++) {
            if (team1.get(i) != null && team1.get(i).getUuid().equals(newPlayer.getUuid())) {
                team1.set(i, newPlayer);
            }
        }
        for (int i = 0; i < team2.size(); i++) {
            if (team2.get(i) != null && team2.get(i).getUuid().equals(newPlayer.getUuid())) {
                team2.set(i, newPlayer);
            }
        }
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i) != null && players.get(i).getUuid().equals(newPlayer.getUuid())) {
                players.set(i, newPlayer);
            }
        }
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("matchId", matchId.toString());
        if (arena != null) {
            json.addProperty("arenaId", arena.getId());
        }
        if (kit != null) {
            json.addProperty("kitId", kit.getId().toString());
        }
        json.add("matchRules", matchRules.saveRuntimeState());
        
        com.google.gson.JsonArray t1 = new com.google.gson.JsonArray();
        for (ServerPlayerEntity p : team1) t1.add(p.getUuidAsString());
        json.add("team1", t1);
        
        com.google.gson.JsonArray t2 = new com.google.gson.JsonArray();
        for (ServerPlayerEntity p : team2) t2.add(p.getUuidAsString());
        json.add("team2", t2);
        
        json.addProperty("startTime", startTime.toEpochMilli());
        json.addProperty("metadata", metadata.toString());
        
        return json;
    }

    public static DuelMatchContext loadRuntimeState(com.google.gson.JsonObject json, dev.frost.miniverse.minigame.arena.ArenaManager arenaManager, net.minecraft.server.MinecraftServer server) {
        UUID matchId = UUID.fromString(json.get("matchId").getAsString());
        dev.frost.miniverse.minigame.arena.Arena arena = null;
        if (json.has("arenaId")) {
            String arenaId = json.get("arenaId").getAsString();
            arena = arenaManager.getArenas().stream().filter(a -> a.getId().equals(arenaId)).findFirst().orElse(null);
        }
        
        dev.frost.miniverse.minigame.core.kit.Kit kit = null;
        if (json.has("kitId")) {
            net.minecraft.util.Identifier kitId = net.minecraft.util.Identifier.tryParse(json.get("kitId").getAsString());
            if (kitId != null) {
                kit = dev.frost.miniverse.minigame.core.kit.KitRegistry.get(kitId).orElse(null);
            }
        }
        
        MatchRules rules = MatchRules.loadRuntimeState(json.getAsJsonObject("matchRules"));
        
        java.util.List<ServerPlayerEntity> team1 = new java.util.ArrayList<>();
        if (json.has("team1")) {
            for (com.google.gson.JsonElement el : json.getAsJsonArray("team1")) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(UUID.fromString(el.getAsString()));
                if (p != null) team1.add(p);
            }
        }
        
        java.util.List<ServerPlayerEntity> team2 = new java.util.ArrayList<>();
        if (json.has("team2")) {
            for (com.google.gson.JsonElement el : json.getAsJsonArray("team2")) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(UUID.fromString(el.getAsString()));
                if (p != null) team2.add(p);
            }
        }
        
        DuelMatchContext ctx = new DuelMatchContext(matchId, arena, kit, rules, team1, team2);
        
        if (json.has("metadata")) {
            try {
                net.minecraft.nbt.NbtCompound loadedMetadata = net.minecraft.nbt.StringNbtReader.parse(json.get("metadata").getAsString());
                for (String key : loadedMetadata.getKeys()) {
                    ctx.getMetadata().put(key, loadedMetadata.get(key));
                }
            } catch (Exception e) {}
        }
        
        return ctx;
    }
}
