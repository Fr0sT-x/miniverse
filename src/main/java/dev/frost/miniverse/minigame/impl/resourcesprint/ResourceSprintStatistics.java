package dev.frost.miniverse.minigame.impl.resourcesprint;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Collects lightweight statistics during a Resource Sprint match and formats end-game report.
 */
public final class ResourceSprintStatistics {
    private final Map<UUID, List<Integer>> claimTicks = new HashMap<>();

    public void recordClaim(UUID player, int tick) {
        claimTicks.computeIfAbsent(player, k -> new ArrayList<>()).add(tick);
    }

    public Text[] generateReport(MinecraftServer server, Map<UUID, Integer> objectiveScore, int totalObjectives) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal("📊 FINAL SCORES:").formatted(Formatting.YELLOW));

        // per-player lines
        for (Map.Entry<UUID, Integer> entry : objectiveScore.entrySet()) {
            ServerPlayerEntity p = server == null ? null : server.getPlayerManager().getPlayer(entry.getKey());
            String name = p != null ? p.getName().getString() : entry.getKey().toString();
            int count = entry.getValue();
            int percentage = totalObjectives <= 0 ? 0 : (count * 100) / totalObjectives;
            lines.add(Text.literal("  " + name + ": " + count + "/" + totalObjectives + " (" + percentage + "%)").formatted(Formatting.AQUA));
        }

        // awards
        UUID mostObjectives = null;
        int best = -1;
        for (Map.Entry<UUID, Integer> entry : objectiveScore.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                mostObjectives = entry.getKey();
            }
        }

        UUID speedDemon = null;
        int fastest = Integer.MAX_VALUE;
        for (Map.Entry<UUID, List<Integer>> e : claimTicks.entrySet()) {
            List<Integer> ticks = e.getValue();
            if (ticks.size() < 1) continue;
            // estimate fastest single claim as the minimal delta between consecutive claims (heuristic)
            int localFastest = Integer.MAX_VALUE;
            int prev = -1;
            for (int t : ticks) {
                if (prev >= 0) {
                    localFastest = Math.min(localFastest, t - prev);
                }
                prev = t;
            }
            if (localFastest < fastest) {
                fastest = localFastest;
                speedDemon = e.getKey();
            }
        }

        lines.add(Text.literal(""));
        lines.add(Text.literal("🎯 MVP AWARDS:").formatted(Formatting.GOLD));
        if (mostObjectives != null) {
            ServerPlayerEntity p = server == null ? null : server.getPlayerManager().getPlayer(mostObjectives);
            String name = p != null ? p.getName().getString() : mostObjectives.toString();
            lines.add(Text.literal("  ⭐ Most Objectives: " + name + " (" + best + ")").formatted(Formatting.YELLOW));
        }

        if (speedDemon != null && fastest < Integer.MAX_VALUE) {
            ServerPlayerEntity p = server == null ? null : server.getPlayerManager().getPlayer(speedDemon);
            String name = p != null ? p.getName().getString() : speedDemon.toString();
            String time = formatTicksAsTime(fastest);
            lines.add(Text.literal("  ⚡ Speed Demon: " + name + " (fastest: " + time + ")").formatted(Formatting.YELLOW));
        }

        return lines.toArray(new Text[0]);
    }

    private String formatTicksAsTime(int ticks) {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int secs = seconds % 60;
        if (minutes > 0) return String.format("%d:%02d", minutes, secs);
        return String.format("%d sec", secs);
    }

    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        com.google.gson.JsonObject claimTicksObj = new com.google.gson.JsonObject();
        claimTicks.forEach((uuid, ticks) -> {
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (int tick : ticks) {
                arr.add(tick);
            }
            claimTicksObj.add(uuid.toString(), arr);
        });
        json.add("claimTicks", claimTicksObj);
        return json;
    }

    public void loadRuntimeState(com.google.gson.JsonObject json) {
        claimTicks.clear();
        if (json.has("claimTicks")) {
            com.google.gson.JsonObject claimTicksObj = json.getAsJsonObject("claimTicks");
            for (String key : claimTicksObj.keySet()) {
                com.google.gson.JsonArray arr = claimTicksObj.getAsJsonArray(key);
                List<Integer> ticks = new ArrayList<>();
                for (com.google.gson.JsonElement el : arr) {
                    ticks.add(el.getAsInt());
                }
                claimTicks.put(UUID.fromString(key), ticks);
            }
        }
    }
}

