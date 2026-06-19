package dev.frost.miniverse.minigame.impl.duels;

import dev.frost.miniverse.minigame.arena.ArenaState;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

public class DuelMatch {
    private static final int FIGHT_COUNTDOWN_SECONDS = 3;
    private static final int BETWEEN_ROUNDS_SECONDS = 5;

    private final DuelMatchContext context;
    private DuelMatchState state;
    private int countdownSeconds;
    private long lastTickTime;

    // Round tracking
    private final int totalRounds;
    private int currentRound = 0;
    private int team1RoundWins = 0;
    private int team2RoundWins = 0;
    private int betweenRoundsSeconds;

    public DuelMatch(DuelMatchContext context) {
        this.context = context;
        this.state = DuelMatchState.RESERVED;
        this.lastTickTime = System.currentTimeMillis();
        this.totalRounds = Math.max(1, context.getMatchRules().totalRounds());

        context.getArena().setActiveMatchId(context.getMatchId());
        context.getArena().setState(ArenaState.RESERVED);
    }

    public DuelMatchContext getContext() {
        return context;
    }

    public DuelMatchState getState() {
        return state;
    }

    public int getTeam1RoundWins() {
        return team1RoundWins;
    }

    public int getTeam2RoundWins() {
        return team2RoundWins;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    // -------------------------------------------------------------------------
    // Startup
    // -------------------------------------------------------------------------

    public void start() {
        // Create Scoreboard teams (once for the whole match)
        net.minecraft.scoreboard.ServerScoreboard scoreboard = context.getArena().getWorld().getServer().getScoreboard();
        String matchPrefix = "duel_" + context.getMatchId().toString().substring(0, 8);

        net.minecraft.scoreboard.Team t1 = scoreboard.getTeam(matchPrefix + "_1");
        if (t1 == null) t1 = scoreboard.addTeam(matchPrefix + "_1");
        t1.setColor(Formatting.RED);
        t1.setFriendlyFireAllowed(false);
        for (ServerPlayerEntity p : context.getTeam1()) {
            scoreboard.addScoreHolderToTeam(p.getName().getString(), t1);
        }

        net.minecraft.scoreboard.Team t2 = scoreboard.getTeam(matchPrefix + "_2");
        if (t2 == null) t2 = scoreboard.addTeam(matchPrefix + "_2");
        t2.setColor(Formatting.BLUE);
        t2.setFriendlyFireAllowed(false);
        for (ServerPlayerEntity p : context.getTeam2()) {
            scoreboard.addScoreHolderToTeam(p.getName().getString(), t2);
        }

        startRound();
    }

    // -------------------------------------------------------------------------
    // Round lifecycle
    // -------------------------------------------------------------------------

    /** Teleports all players to their spawn, gives fresh kits, and begins the fight countdown. */
    private void startRound() {
        currentRound++;
        clearRoundDeaths();

        Vec3d p1Spawn = context.getArena().getSpawn("player1");
        Vec3d p2Spawn = context.getArena().getSpawn("player2");

        for (ServerPlayerEntity p : context.getTeam1()) {
            p.changeGameMode(GameMode.SURVIVAL);
            p.heal(p.getMaxHealth());
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(20.0F);
            context.getKit().apply(p);
            if (p1Spawn != null) {
                p.teleport(context.getArena().getWorld(), p1Spawn.x, p1Spawn.y, p1Spawn.z, p.getYaw(), p.getPitch());
            }
        }

        for (ServerPlayerEntity p : context.getTeam2()) {
            p.changeGameMode(GameMode.SURVIVAL);
            p.heal(p.getMaxHealth());
            p.getHungerManager().setFoodLevel(20);
            p.getHungerManager().setSaturationLevel(20.0F);
            context.getKit().apply(p);
            if (p2Spawn != null) {
                p.teleport(context.getArena().getWorld(), p2Spawn.x, p2Spawn.y, p2Spawn.z, p.getYaw(), p.getPitch());
            }
        }

        this.state = DuelMatchState.TELEPORTED;
        this.lastTickTime = System.currentTimeMillis();
    }

    /**
     * Called by DuelMatchManager when all members of a team are dead.
     * @param winningTeamIndex 1 or 2, or 0 for a draw
     */
    public void endRound(int winningTeamIndex) {
        if (this.state == DuelMatchState.ENDING || this.state == DuelMatchState.BETWEEN_ROUNDS) {
            return;
        }

        int roundsToWin = context.getMatchRules().roundsToWin();

        if (winningTeamIndex == 1) {
            team1RoundWins++;
        } else if (winningTeamIndex == 2) {
            team2RoundWins++;
        }
        // Draw: no points awarded, round will replay

        // Check if someone has won enough rounds
        if (team1RoundWins >= roundsToWin) {
            endMatch(1);
            return;
        }
        if (team2RoundWins >= roundsToWin) {
            endMatch(2);
            return;
        }

        // Not decided yet — start the inter-round pause
        if (winningTeamIndex == 0) {
            broadcastTitle(
                Text.literal("Draw!").formatted(Formatting.GRAY),
                Text.literal("Round replaying…").formatted(Formatting.YELLOW)
            );
            broadcastMessage(Text.literal("Round draw — replaying round " + currentRound + ".").formatted(Formatting.GRAY));
            // We decrement currentRound so startRound() re-increments back to the same number
            currentRound--;
        } else {
            String winnerLabel = winningTeamIndex == 1 ? "Red Team" : "Blue Team";
            Formatting winnerColor = winningTeamIndex == 1 ? Formatting.RED : Formatting.BLUE;
            String scoreText = "§c" + team1RoundWins + " §7- §9" + team2RoundWins;
            broadcastTitle(
                Text.literal(winnerLabel + " wins round " + currentRound + "!").formatted(winnerColor),
                Text.literal(scoreText)
            );
            broadcastMessage(
                Text.literal(winnerLabel + " wins round " + currentRound + "! Score: " + team1RoundWins + " - " + team2RoundWins
                    + "  (First to " + roundsToWin + " wins)").formatted(winnerColor)
            );
        }

        this.state = DuelMatchState.BETWEEN_ROUNDS;
        this.betweenRoundsSeconds = BETWEEN_ROUNDS_SECONDS;
        this.lastTickTime = System.currentTimeMillis();

        // Arena reset will run now to restore any placed blocks from this round
        context.getArena().startReset();
    }

    // -------------------------------------------------------------------------
    // Overall match end
    // -------------------------------------------------------------------------

    public void endMatch(int winningTeamIndex) {
        if (this.state == DuelMatchState.ENDING) return;
        this.state = DuelMatchState.ENDING;

        String winningName = winningTeamIndex == 1 ? "Red Team" : "Blue Team";
        Formatting color = winningTeamIndex == 1 ? Formatting.RED : Formatting.BLUE;
        String scoreText = "§c" + team1RoundWins + " §7- §9" + team2RoundWins;

        if (totalRounds > 1) {
            broadcastTitle(
                Text.literal(winningName + " wins the match!").formatted(color, Formatting.BOLD),
                Text.literal(scoreText)
            );
            broadcastMessage(Text.literal(winningName + " wins the match! Final score: "
                + team1RoundWins + " - " + team2RoundWins).formatted(color));
        } else {
            broadcastMessage(Text.literal(winningName + " has won the duel!").formatted(color));
        }

        cleanup();
    }

    public void endMatchDraw() {
        if (this.state == DuelMatchState.ENDING) return;
        this.state = DuelMatchState.ENDING;
        broadcastMessage(Text.literal("The duel ended in a draw.").formatted(Formatting.GRAY));
        cleanup();
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    public void tick() {
        long now = System.currentTimeMillis();

        if (state == DuelMatchState.TELEPORTED) {
            // One tick grace after teleport, then start the fight countdown
            this.state = DuelMatchState.COUNTDOWN;
            this.lastTickTime = now;
            this.countdownSeconds = FIGHT_COUNTDOWN_SECONDS;
            context.getArena().setState(ArenaState.RUNNING);

        } else if (state == DuelMatchState.COUNTDOWN) {
            if (now - lastTickTime >= 1000) {
                lastTickTime = now;
                if (countdownSeconds > 0) {
                    broadcastTitle(Text.literal(String.valueOf(countdownSeconds)).formatted(Formatting.YELLOW));
                    countdownSeconds--;
                } else {
                    broadcastTitle(Text.literal("FIGHT!").formatted(Formatting.RED));
                    this.state = DuelMatchState.ACTIVE;
                }
            }

        } else if (state == DuelMatchState.BETWEEN_ROUNDS) {
            if (now - lastTickTime >= 1000) {
                lastTickTime = now;
                betweenRoundsSeconds--;
                if (betweenRoundsSeconds > 0) {
                    String roundLabel = totalRounds > 1
                        ? "Round " + (currentRound + 1) + " in " + betweenRoundsSeconds + "s"
                        : "Next round in " + betweenRoundsSeconds + "s";
                    broadcastActionBar(Text.literal(roundLabel).formatted(Formatting.YELLOW));
                } else {
                    // Start the next round
                    startRound();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Clears the "dead" metadata flag for every player so the new round starts fresh. */
    public void clearRoundDeaths() {
        for (ServerPlayerEntity p : context.getPlayers()) {
            context.getMetadata().remove("dead_" + p.getUuidAsString());
        }
    }

    private void cleanup() {
        net.minecraft.scoreboard.ServerScoreboard scoreboard = context.getArena().getWorld().getServer().getScoreboard();
        String matchPrefix = "duel_" + context.getMatchId().toString().substring(0, 8);
        net.minecraft.scoreboard.Team t1 = scoreboard.getTeam(matchPrefix + "_1");
        if (t1 != null) scoreboard.removeTeam(t1);
        net.minecraft.scoreboard.Team t2 = scoreboard.getTeam(matchPrefix + "_2");
        if (t2 != null) scoreboard.removeTeam(t2);

        // Teleport players to their spawn points before resetting the arena,
        // so Arena.validateResetCompletion() does not warn about players still inside.
        Vec3d p1Spawn = context.getArena().getSpawn("player1");
        Vec3d p2Spawn = context.getArena().getSpawn("player2");
        // Use spectator spawn as a safe "exit" point if individual spawns are not available
        Vec3d specSpawn = context.getArena().getSpawn("spectator");
        Vec3d exitPoint = specSpawn != null ? specSpawn : (p1Spawn != null ? p1Spawn : p2Spawn);
        if (exitPoint != null) {
            for (ServerPlayerEntity p : context.getPlayers()) {
                p.teleport(context.getArena().getWorld(), exitPoint.x, exitPoint.y + 5, exitPoint.z, p.getYaw(), p.getPitch());
            }
        }

        context.getArena().startReset();
    }

    private void broadcastTitle(Text title) {
        TitleS2CPacket packet = new TitleS2CPacket(title);
        for (ServerPlayerEntity player : context.getPlayers()) {
            player.networkHandler.sendPacket(packet);
        }
    }

    private void broadcastTitle(Text title, Text subtitle) {
        TitleS2CPacket titlePacket = new TitleS2CPacket(title);
        SubtitleS2CPacket subtitlePacket = new SubtitleS2CPacket(subtitle);
        for (ServerPlayerEntity player : context.getPlayers()) {
            player.networkHandler.sendPacket(subtitlePacket);
            player.networkHandler.sendPacket(titlePacket);
        }
    }

    private void broadcastActionBar(Text text) {
        for (ServerPlayerEntity player : context.getPlayers()) {
            player.sendMessage(text, true);
        }
    }

    private void broadcastMessage(Text text) {
        for (ServerPlayerEntity player : context.getPlayers()) {
            player.sendMessage(text);
        }
    }
}
