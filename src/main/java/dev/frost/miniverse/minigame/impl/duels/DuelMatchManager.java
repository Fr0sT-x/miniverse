package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.arena.Arena;
import dev.frost.miniverse.minigame.arena.ArenaManager;
import dev.frost.miniverse.minigame.core.kit.Kit;
import net.minecraft.server.network.ServerPlayerEntity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class DuelMatchManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DuelMatchManager.class);
    
    private final ArenaManager arenaManager;
    private final List<DuelMatch> activeMatches = new CopyOnWriteArrayList<>();

    public DuelMatchManager(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    public MatchCreationResult createMatch(Kit kit, MatchRules rules, List<ServerPlayerEntity> players) {
        if (kit == null) {
            String msg = "Kit missing from registry or not provided.";
            LOGGER.error("DuelMatch creation failed: " + msg);
            return MatchCreationResult.failure("Selected kit is unavailable.", msg);
        }

        if (players.size() < 2) {
            String msg = "Not enough players connected to start match. Expected 2, got " + players.size();
            LOGGER.error("DuelMatch creation failed: " + msg);
            return MatchCreationResult.failure("Player disconnected before match start.", msg);
        }

        // Find idle arena with matching tags
        Optional<Arena> compatibleArena = arenaManager.getArenas().stream()
            .filter(a -> a.getState() == dev.frost.miniverse.minigame.arena.ArenaState.IDLE)
            .filter(a -> a.getTags().containsAll(rules.requiredArenaTags()))
            .findFirst();

        if (compatibleArena.isEmpty()) {
            boolean hasArenas = arenaManager.getArenas().stream().anyMatch(a -> a.getTags().containsAll(rules.requiredArenaTags()));
            if (hasArenas) {
                String msg = "Arena pool exhausted. All compatible arenas are busy.";
                LOGGER.warn("DuelMatch creation failed: " + msg);
                return MatchCreationResult.failure("All arenas are currently full. Please wait.", msg);
            } else {
                String msg = "No compatible arena found for tags: " + rules.requiredArenaTags();
                LOGGER.error("DuelMatch creation failed: " + msg);
                return MatchCreationResult.failure("No compatible arena exists for this match type.", msg);
            }
        }

        UUID matchId = UUID.randomUUID();
        DuelMatchContext context = new DuelMatchContext(matchId, compatibleArena.get(), kit, rules, players);
        DuelMatch match = new DuelMatch(context);
        
        activeMatches.add(match);
        return MatchCreationResult.success(match);
    }

    public void tick() {
        for (DuelMatch match : activeMatches) {
            match.tick();
            
            if (match.getState() == DuelMatchState.ENDING && match.getContext().getArena().getState() == dev.frost.miniverse.minigame.arena.ArenaState.IDLE) {
                activeMatches.remove(match);
            }
        }
    }

    public Optional<DuelMatch> getMatchForPlayer(ServerPlayerEntity player) {
        return activeMatches.stream()
            .filter(m -> m.getContext().getPlayers().contains(player))
            .findFirst();
    }

    public void handleDisconnect(ServerPlayerEntity player) {
        getMatchForPlayer(player).ifPresent(match -> {
            if (match.getState() != DuelMatchState.ENDING) {
                // The disconnected player loses
                ServerPlayerEntity winner = match.getContext().getPlayers().stream()
                    .filter(p -> !p.equals(player))
                    .findFirst()
                    .orElse(null);
                match.endMatch(winner, player);
            }
        });
    }

    public void handleDeath(ServerPlayerEntity victim) {
        getMatchForPlayer(victim).ifPresent(match -> {
            if (match.getState() != DuelMatchState.ENDING) {
                ServerPlayerEntity winner = match.getContext().getPlayers().stream()
                    .filter(p -> !p.equals(victim))
                    .findFirst()
                    .orElse(null);
                match.endMatch(winner, victim);
            }
        });
    }
}
