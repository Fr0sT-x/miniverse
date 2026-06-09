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

    public MatchCreationResult createMatch(Kit kit, MatchRules rules, List<ServerPlayerEntity> team1, List<ServerPlayerEntity> team2) {
        if (kit == null) {
            String msg = "Kit missing from registry or not provided.";
            LOGGER.error("DuelMatch creation failed: " + msg);
            return MatchCreationResult.failure("Selected kit is unavailable.", msg);
        }

        if (team1.isEmpty() || team2.isEmpty()) {
            String msg = "Not enough players to start match. Expected at least 1 per team.";
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
        DuelMatchContext context = new DuelMatchContext(matchId, compatibleArena.get(), kit, rules, team1, team2);
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
                match.getContext().getMetadata().putBoolean("dead_" + player.getUuidAsString(), true);
                checkWinCondition(match);
            }
        });
    }

    public void handleDeath(ServerPlayerEntity victim) {
        getMatchForPlayer(victim).ifPresent(match -> {
            if (match.getState() != DuelMatchState.ENDING) {
                match.getContext().getMetadata().putBoolean("dead_" + victim.getUuidAsString(), true);
                checkWinCondition(match);
            }
        });
    }

    private void checkWinCondition(DuelMatch match) {
        boolean t1Alive = false;
        for (ServerPlayerEntity p : match.getContext().getTeam1()) {
            if (!match.getContext().getMetadata().getBoolean("dead_" + p.getUuidAsString())) {
                t1Alive = true;
                break;
            }
        }
        boolean t2Alive = false;
        for (ServerPlayerEntity p : match.getContext().getTeam2()) {
            if (!match.getContext().getMetadata().getBoolean("dead_" + p.getUuidAsString())) {
                t2Alive = true;
                break;
            }
        }

        if (!t1Alive && !t2Alive) {
            match.endMatchDraw();
        } else if (!t1Alive) {
            match.endMatch(2);
        } else if (!t2Alive) {
            match.endMatch(1);
        }
    }
}
