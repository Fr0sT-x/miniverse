package dev.frost.miniverse.minigame.impl.resourcesprint;

import dev.frost.miniverse.team.TeamColorPalette;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamRole;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PauseAwareMinigame;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;

public class ResourceSprintMinigame extends AbstractMinigame implements TeamManagerProvider, PauseAwareMinigame {
    private static final int TICKS_PER_SECOND = 20;
    private static final String NAME = "Resource Sprint";

    private GameState state;
    private ResourceSprintSettings settings;
    private MinecraftServer server;
    private String teamLabel = "Team";
    private int elapsedTicks;
    private boolean paused;
    private boolean suddenDeathActive;
    private final Set<String> suddenDeathTeams = new HashSet<>();
    private final List<ResourceSprintSettings.ObjectiveEntry> activeObjectives = new ArrayList<>();
    private final Map<String, TeamProgress> teamProgress = new LinkedHashMap<>();
    private final TeamManager teams = new TeamManager();
    // Scoreboard and time-warning helpers
    private ScoreboardTemplate baseScoreboard;
    private final Map<UUID, ScoreboardLine> timeLines = new HashMap<>();
    private final Set<Integer> timeWarningsShown = new HashSet<>();
    private final ResourceSprintEventMessenger eventMessenger = new ResourceSprintEventMessenger(this::getParticipants);
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("resourcesprint");

    private static final class TeamProgress {
        private int currentObjectiveIndex;
        private @Nullable UUID lastObjectiveClaimedBy;
        private final Map<UUID, Integer> objectiveScore = new HashMap<>();
        private final Map<UUID, Integer> lastCompletionTick = new HashMap<>();
        private final ResourceSprintMomentum momentum = new ResourceSprintMomentum();
        private final ResourceSprintStatistics statistics = new ResourceSprintStatistics();

        private void reset() {
            this.currentObjectiveIndex = 0;
            this.lastObjectiveClaimedBy = null;
            this.objectiveScore.clear();
            this.lastCompletionTick.clear();
            this.momentum.reset();
        }
    }

    public ResourceSprintMinigame() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.settings = ResourceSprintSettings.defaults();
        this.vanillaTeams.setFriendlyFireAllowed(false);
        this.vanillaTeams.setTeammateCollisionAllowed(false);
    }



    public void applySettings(ResourceSprintSettings settings) {
        this.settings = settings == null ? ResourceSprintSettings.defaults() : settings;
    }

    public void setVanillaFriendlyFireAllowed(boolean allowed) {
        this.vanillaTeams.setFriendlyFireAllowed(allowed);
        this.syncVanillaTeams();
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        return GlobalMatchRules.defaults(false, false);
    }

    @Override
    protected boolean isTeamBased() {
        return false;
    }

    public void setVanillaTeammateCollisionAllowed(boolean allowed) {
        this.vanillaTeams.setTeammateCollisionAllowed(allowed);
        this.syncVanillaTeams();
    }

    public void setTeamLabel(String teamLabel) {
        if (teamLabel != null && !teamLabel.isBlank()) {
            this.teamLabel = teamLabel.trim();
        }
    }

    public void setPlayerTeam(ServerPlayerEntity player, String teamLabel) {
        String resolvedTeam = this.normalizeTeamLabel(teamLabel);
        this.teams.assign(player, resolvedTeam, resolvedTeam, TeamRole.MEMBER);
        this.teamProgress.computeIfAbsent(resolvedTeam, ignored -> new TeamProgress());
        this.teamLabel = resolvedTeam;
        this.syncVanillaTeams();
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        if (!this.isParticipant(player)) {
            if (this.context != null) {
                this.context.roster().add(player);
            }
        }
        String resolvedTeam = this.normalizeTeamLabel(teamId);
        this.setPlayerTeam(player, resolvedTeam);
        if (this.state == GameState.RUNNING) {
            TeamProgress progress = this.teamProgress.computeIfAbsent(resolvedTeam, ignored -> new TeamProgress());
            player.sendMessage(Text.literal("Joined Resource Sprint in progress for " + resolvedTeam + ".").formatted(Formatting.GREEN), false);
            this.broadcastCurrentObjective(resolvedTeam, progress);
            this.rebuildScoreboard();
        }
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.settings = ResourceSprintSettings.defaults();
        this.server = null;
        this.elapsedTicks = 0;
        this.paused = false;
        this.suddenDeathActive = false;
        this.suddenDeathTeams.clear();
        this.timeWarningsShown.clear();
        this.activeObjectives.clear();
        this.teamProgress.clear();
        this.teams.clear();
    }

    @Override
    protected void onMatchStart() {
        if (this.getState() == GameState.RUNNING) {
            return;
        }

        if (!this.canStartMatch()) {
            this.broadcastMessage(Text.literal("Cannot start Resource Sprint: no valid objectives or participants are assigned.").formatted(Formatting.RED));
            return;
        }

        this.setState(GameState.RUNNING);
        this.setRuntimeState(GameState.RUNNING);
        this.elapsedTicks = 0;
        this.suddenDeathActive = false;
        this.suddenDeathTeams.clear();
        this.timeWarningsShown.clear();
        this.activeObjectives.clear();
        this.activeObjectives.addAll(this.resolveActiveObjectives());
        this.teamProgress.values().forEach(TeamProgress::reset);
        for (ServerPlayerEntity participant : this.getParticipants()) {
            this.teamProgress.computeIfAbsent(this.teamFor(participant), ignored -> new TeamProgress());
        }

        this.broadcastMessage(Text.literal("✓ Resource Sprint started!").formatted(Formatting.GREEN));
        this.broadcastMessage(Text.literal("Teams are sharing this world with isolated objective progress.").formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("Mode: " + this.describeMode() + " | Tie-break: " + this.describeTieBreak()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("Objectives complete: 0 / " + this.activeObjectives.size()).formatted(Formatting.AQUA));
        for (String team : this.teamProgress.keySet()) {
            this.broadcastCurrentObjective(team, this.teamProgress.get(team));
        }
        this.rebuildScoreboard();
    }

    @Override
    protected void onMatchEnd() {
        this.setState(GameState.ENDING);
        this.setRuntimeState(GameState.ENDING);
        this.elapsedTicks = 0;
        this.suddenDeathActive = false;
        this.suddenDeathTeams.clear();
        this.timeWarningsShown.clear();
        this.teamProgress.values().forEach(TeamProgress::reset);
        if (this.server != null && this.baseScoreboard != null) {
            this.baseScoreboard.cleanup(this.server);
        }
        this.clearVanillaTeams();
        this.server = null;
        if (this.context != null) {
            this.context.roster().clear();
        }
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        // Objective progress is item-driven, so deaths do not eliminate participants in the MVP.
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;
        if (this.getState() != GameState.RUNNING) {
            return;
        }

        if (this.getParticipants().isEmpty()) {
            if (this.checkProgression(this.context.roster()).blocked()) {
                return;
            }
            this.onMatchEnd();
            return;
        }

        if (this.paused) {
            return;
        }

        this.elapsedTicks++;
        // Check time warnings each tick
        this.checkTimeWarnings();
        // Update scoreboard every tick to keep it visible and update the time
        this.updateScoreboardTick();

        if (this.settings.mode() == ResourceSprintSettings.Mode.TIME_LIMITED && !this.suddenDeathActive) {
            int timeLimitTicks = Math.max(1, this.settings.timeLimitSeconds()) * TICKS_PER_SECOND;
            if (this.elapsedTicks >= timeLimitTicks) {
                this.resolveTimedWinner();
                return;
            }
        }

        for (Map.Entry<String, TeamProgress> entry : new ArrayList<>(this.teamProgress.entrySet())) {
            if (this.suddenDeathActive && !this.suddenDeathTeams.isEmpty() && !this.suddenDeathTeams.contains(entry.getKey())) {
                continue;
            }

            TeamProgress progress = entry.getValue();
            if (progress.currentObjectiveIndex >= this.activeObjectives.size()) {
                if (progress.lastObjectiveClaimedBy != null) {
                    this.finishWithWinner(entry.getKey(), progress.lastObjectiveClaimedBy, Text.literal("Completed the final objective chain.").formatted(Formatting.GOLD));
                    return;
                }
                continue;
            }

            this.checkCurrentObjective(entry.getKey(), progress);
            if (this.getState() != GameState.RUNNING) {
                return;
            }
        }
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (this.isParticipant(oldPlayer)) {
            this.handlePlayerRespawn(oldPlayer, newPlayer);
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.isParticipant(player)) {
            this.handlePlayerLeave(player);
        }
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (!this.isParticipant(oldPlayer)) {
            return;
        }

        this.replaceParticipant(oldPlayer, newPlayer);
        this.syncVanillaTeams();
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        boolean wasTracked = this.isParticipant(player);
        this.removeParticipant(player);
        this.teams.remove(player);
        this.syncVanillaTeams();

        if (wasTracked && this.getState() == GameState.RUNNING && this.getParticipants().isEmpty()) {
            this.onMatchEnd();
        }
    }

    public boolean canStartMatch() {
        return !this.getParticipants().isEmpty() && !this.settings.objectives().isEmpty();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public TeamManager teamManager() {
        return this.teams;
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    @Override
    public void setState(GameState state) {
        this.state = state;
    }

    private void checkCurrentObjective(String team, TeamProgress progress) {
        if (progress.currentObjectiveIndex < 0 || progress.currentObjectiveIndex >= this.activeObjectives.size()) {
            return;
        }

        ResourceSprintSettings.ObjectiveEntry objective = this.activeObjectives.get(progress.currentObjectiveIndex);
        Item objectiveItem = this.resolveObjectiveItem(objective.id());
        if (objectiveItem == null) {
            this.broadcastMessage(Text.literal("Skipping invalid objective id: " + objective.id()).formatted(Formatting.RED));
            progress.currentObjectiveIndex++;
            this.broadcastCurrentObjective(team, progress);
            return;
        }

        for (ServerPlayerEntity participant : this.getParticipants()) {
            if (!this.teamFor(participant).equals(team)) {
                continue;
            }
            if (this.playerHasObjectiveItem(participant, objectiveItem)) {
                this.onObjectiveClaimed(participant, team, progress);
                return;
            }
        }
    }

    private void onObjectiveClaimed(ServerPlayerEntity player, String team, TeamProgress progress) {
        UUID uuid = player.getUuid();
        progress.lastObjectiveClaimedBy = uuid;
        progress.objectiveScore.put(uuid, progress.objectiveScore.getOrDefault(uuid, 0) + 1);
        progress.lastCompletionTick.put(uuid, this.elapsedTicks);
        // Record statistics and momentum
        progress.statistics.recordClaim(uuid, this.elapsedTicks);
        int streak = progress.momentum.onClaim(uuid);
        progress.currentObjectiveIndex++;

        String objectiveName = progress.currentObjectiveIndex <= 0 ? "objective" : this.describeObjective(progress.currentObjectiveIndex - 1);
        // Contextual/emoji-enhanced claim message
        String difficultyEmoji = "";
        int claimedIndex = Math.max(0, progress.currentObjectiveIndex - 1);
        if (claimedIndex < this.activeObjectives.size()) {
            difficultyEmoji = switch (this.activeObjectives.get(claimedIndex).difficulty()) {
                case EASY -> "☘️";
                case MEDIUM -> "🔨";
                case HARD -> "💎";
            };
        }

        this.broadcastMessage(Text.literal("⚡ " + player.getName().getString() + " claimed " + difficultyEmoji + " " + objectiveName + " for " + team + " (" + progress.currentObjectiveIndex + "/" + this.activeObjectives.size() + ") - " + this.getFormattedTime()).formatted(Formatting.AQUA));
        this.broadcastMessage(Text.literal(team + " progress: " + progress.currentObjectiveIndex + " / " + this.activeObjectives.size() + " objectives complete.").formatted(Formatting.YELLOW));

        if (this.suddenDeathActive && this.suddenDeathTeams.contains(team)) {
            this.finishWithWinner(team, uuid, Text.literal("Won sudden death by claiming the next objective.").formatted(Formatting.GOLD));
            return;
        }

        // Momentum/streak messages
        if (streak >= 2) {
            if (streak == 2) {
                eventMessenger.send(ResourceSprintEventMessenger.Severity.MILESTONE,
                    Text.literal("⚡ " + player.getName().getString() + " is on a 2x STREAK!").formatted(Formatting.GOLD),
                    Text.literal("").formatted(Formatting.RESET));
            } else if (streak == 3) {
                eventMessenger.send(ResourceSprintEventMessenger.Severity.MILESTONE,
                    Text.literal("🌪️ " + player.getName().getString() + " is UNSTOPPABLE! 3 in a row!").formatted(Formatting.GOLD),
                    Text.literal("").formatted(Formatting.RESET));
            } else if (streak >= 5) {
                eventMessenger.send(ResourceSprintEventMessenger.Severity.MILESTONE,
                    Text.literal("🔥 " + player.getName().getString() + " ON FIRE! " + streak + " consecutive!").formatted(Formatting.GOLD),
                    Text.literal("").formatted(Formatting.RESET));
            }
        }

        if (progress.currentObjectiveIndex >= this.activeObjectives.size()) {
            this.finishWithWinner(team, uuid, Text.literal("Completed all resource objectives.").formatted(Formatting.GOLD));
            return;
        }

        this.broadcastCurrentObjective(team, progress);
        this.rebuildScoreboard();

        // Check for dynamic difficulty triggers (non-invasive: announce only)
        this.applyDynamicDifficultyIfNeeded(team, progress);
    }

    private void broadcastCurrentObjective(String team, TeamProgress progress) {
        if (progress.currentObjectiveIndex >= this.activeObjectives.size()) {
            return;
        }

        ResourceSprintSettings.ObjectiveEntry entry = this.activeObjectives.get(progress.currentObjectiveIndex);
        String difficultyEmoji = switch (entry.difficulty()) {
            case EASY -> "☘️";
            case MEDIUM -> "🔨";
            case HARD -> "💎";
        };
        String objective = this.describeObjective(progress.currentObjectiveIndex);
        String category = this.getObjectiveCategory(this.activeObjectives.get(progress.currentObjectiveIndex));
        String prefix = this.suddenDeathActive && this.suddenDeathTeams.contains(team) ? "Sudden death objective" : "Objective";
        this.broadcastMessage(Text.literal(team + " " + prefix + " " + (progress.currentObjectiveIndex + 1) + "/" + this.activeObjectives.size() + ": " + difficultyEmoji + " " + objective + (category.isBlank() ? "" : " [" + category + "]")).formatted(Formatting.YELLOW));
    }

    private void rebuildScoreboard() {
        if (this.server == null) return;
        List<ServerPlayerEntity> participants = this.getParticipants();
        if (participants.isEmpty()) return;

        if (this.baseScoreboard == null) {
            this.baseScoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("Resource Sprint").formatted(Formatting.AQUA, Formatting.BOLD)));
            this.baseScoreboard.show(participants);
        } else {
            for (ServerPlayerEntity p : participants) {
                if (!this.baseScoreboard.isViewing(p)) this.baseScoreboard.show(p);
            }
        }

        this.syncVanillaTeams();

        this.baseScoreboard.rebuild(player -> {
            UUID uuid = player.getUuid();
            ScoreboardTemplate board = new ScoreboardTemplate(this.getName(), Text.literal("Resource Sprint").formatted(Formatting.AQUA, Formatting.BOLD));
            
            ScoreboardLine timeLine = board.addLine(Text.empty());
            this.timeLines.put(uuid, timeLine);
            
            board.addLine(Text.literal("──────────────"));
            
            for (Map.Entry<String, TeamProgress> entry : this.teamProgress.entrySet()) {
                TeamProgress p = entry.getValue();
                board.addLine(Text.literal(entry.getKey() + ": " + p.currentObjectiveIndex + "/" + this.activeObjectives.size()));
            }

            board.addLine(Text.literal("──────────────"));
            
            String playerTeam = this.teamFor(player);
            TeamProgress ownProgress = this.teamProgress.get(playerTeam);
            if (ownProgress != null && ownProgress.currentObjectiveIndex < this.activeObjectives.size()) {
                board.addLine(Text.literal("Next: " + this.getObjectiveItemName(ownProgress.currentObjectiveIndex)));
            }
            return board;
        });
        
        this.updateScoreboardTick();
    }

    private void updateScoreboardTick() {
        for (ServerPlayerEntity player : this.getParticipants()) {
            ScoreboardLine timeLine = this.timeLines.get(player.getUuid());
            if (timeLine != null) {
                timeLine.setText(Text.literal("⏱ Time: " + this.getFormattedTime()));
                timeLine.updateAll();
            }
        }
    }


    private void checkTimeWarnings() {
        if (this.settings.mode() != ResourceSprintSettings.Mode.TIME_LIMITED) return;
        if (this.server == null) return;

        int timeLimitTicks = Math.max(1, this.settings.timeLimitSeconds()) * TICKS_PER_SECOND;
        int timeRemaining = Math.max(0, timeLimitTicks - this.elapsedTicks);
        int secondsRemaining = timeRemaining / TICKS_PER_SECOND;

        if (secondsRemaining == 300 && !this.timeWarningsShown.contains(300)) {
            this.showTimeWarning("5 minutes remaining!", Formatting.YELLOW);
            this.timeWarningsShown.add(300);
        } else if (secondsRemaining == 60 && !this.timeWarningsShown.contains(60)) {
            this.showTimeWarning("1 minute remaining!", Formatting.RED);
            this.timeWarningsShown.add(60);
        } else if (secondsRemaining == 30 && !this.timeWarningsShown.contains(30)) {
            this.showTimeWarning("30 SECONDS REMAINING!", Formatting.DARK_RED);
            this.timeWarningsShown.add(30);
        } else if (secondsRemaining <= 10 && secondsRemaining > 0 && !this.timeWarningsShown.contains(secondsRemaining)) {
            // final 10 seconds announce every second if desired (mark each shown to avoid repeats)
            this.showTimeWarning(secondsRemaining + " seconds remaining", Formatting.DARK_RED);
            this.timeWarningsShown.add(secondsRemaining);
        }
    }

    private void showTimeWarning(String message, Formatting color) {
        GameMessenger.showGameTitle(this.getParticipants(),
            Text.literal("⏰ " + message).formatted(color),
            Text.literal("").formatted(Formatting.RESET));
        this.broadcastMessage(Text.literal("⏰ " + message).formatted(color));
    }

    private void resolveTimedWinner() {
        if (this.settings.mode() != ResourceSprintSettings.Mode.TIME_LIMITED) {
            return;
        }

        List<String> leaders = this.findLeadingTeams();
        if (leaders.isEmpty()) {
            this.broadcastMessage(Text.literal("Time limit reached, but no one completed an objective.").formatted(Formatting.RED));
            this.onMatchEnd();
            return;
        }

        if (leaders.size() == 1) {
            String team = leaders.getFirst();
            this.finishWithWinner(team, this.teamProgress.get(team).lastObjectiveClaimedBy, Text.literal("Time limit reached. Highest progress wins.").formatted(Formatting.GOLD));
            return;
        }

        if (this.settings.tieBreakRule() == ResourceSprintSettings.TieBreakRule.FASTEST_TOTAL_TIME) {
            String winner = leaders.stream()
                .min(Comparator.comparingInt(team -> this.lastTeamCompletionTick(this.teamProgress.get(team))))
                .orElse(leaders.getFirst());
            this.finishWithWinner(winner, this.teamProgress.get(winner).lastObjectiveClaimedBy, Text.literal("Time limit reached. Fastest total completion time wins the tie.").formatted(Formatting.GOLD));
            return;
        }

        this.suddenDeathActive = true;
        this.suddenDeathTeams.clear();
        this.suddenDeathTeams.addAll(leaders);
        // Major event: sudden death
        eventMessenger.send(ResourceSprintEventMessenger.Severity.MAJOR,
            Text.literal("💀 SUDDEN DEATH").formatted(Formatting.DARK_RED),
            Text.literal("Next objective wins the match!").formatted(Formatting.RED));
        for (String team : leaders) {
            this.broadcastCurrentObjective(team, this.teamProgress.get(team));
        }
    }

    private List<String> findLeadingTeams() {
        int bestScore = this.teamProgress.values().stream().mapToInt(progress -> progress.currentObjectiveIndex).max().orElse(0);
        if (bestScore <= 0) {
            return List.of();
        }

        List<String> leaders = new ArrayList<>();
        for (Map.Entry<String, TeamProgress> entry : this.teamProgress.entrySet()) {
            if (entry.getValue().currentObjectiveIndex == bestScore) {
                leaders.add(entry.getKey());
            }
        }
        return leaders;
    }

    private void finishWithWinner(String winningTeam, @Nullable UUID winner, Text reason) {
        TeamProgress progress = this.teamProgress.getOrDefault(winningTeam, new TeamProgress());
        ServerPlayerEntity player = winner == null ? null : this.findParticipant(winner);
        String winnerName = player == null ? "Unknown player" : player.getName().getString();
        this.setState(GameState.ENDING);
        this.setRuntimeState(GameState.ENDING);
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("🏁 Resource Sprint Complete! 🏁").formatted(Formatting.GOLD));
        this.broadcastMessage(reason.copy().formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("Winner: " + winningTeam + " (" + winnerName + ")").formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("Time: " + this.getFormattedTime()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));

        // Detailed end-game statistics & awards
        if (this.server != null) {
            Text[] report = progress.statistics.generateReport(this.server, progress.objectiveScore, this.activeObjectives.size());
            for (Text line : report) {
                this.broadcastMessage(line);
            }
        }

        this.startStandardEndSequence(winningTeam);
        this.onMatchEnd();
    }

    private void startStandardEndSequence(String winningTeam) {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null) {
            return;
        }

        Set<UUID> winners = this.getParticipants().stream()
            .filter(player -> this.teamFor(player).equals(winningTeam))
            .map(ServerPlayerEntity::getUuid)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
            runtime,
            new MatchEndResult(winners, Text.literal(winningTeam)),
            MatchLifecycleOptions.defaults(NAME)
        );
    }

    private String describeMode() {
        return this.settings.mode() == ResourceSprintSettings.Mode.FIRST_TO_COMPLETE ? "First to Complete" : "Time Limited";
    }

    private String describeTieBreak() {
        return this.settings.tieBreakRule() == ResourceSprintSettings.TieBreakRule.SUDDEN_DEATH ? "Sudden Death" : "Fastest Total Time";
    }

    private String modeStrategyHint() {
        if (this.settings.mode() == ResourceSprintSettings.Mode.FIRST_TO_COMPLETE) {
            return "First to Complete - Speed is key!";
        }
        return "Time Limited - Consistency matters!";
    }

    private void applyDynamicDifficultyIfNeeded(String team, TeamProgress progress) {
        // Non-invasive: detect large leader gaps and announce difficulty scaling to players
        if (progress.objectiveScore.isEmpty() || this.server == null) return;
        int best = progress.objectiveScore.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int second = progress.objectiveScore.values().stream().mapToInt(Integer::intValue).boxed().sorted((a,b)->b-a).skip(1).findFirst().orElse(0);
        if (best - second >= 3) {
            // Major milestone: leader significantly ahead
            UUID leader = progress.objectiveScore.entrySet().stream().max((a,b)->Integer.compare(a.getValue(), b.getValue())).map(Map.Entry::getKey).orElse(null);
            String leaderName = leader == null ? "Leader" : (this.findParticipant(leader) == null ? leader.toString() : this.findParticipant(leader).getName().getString());
            eventMessenger.send(ResourceSprintEventMessenger.Severity.MILESTONE,
                Text.literal("Difficulty scaling activated for " + team + " / " + leaderName).formatted(Formatting.RED),
                Text.literal("Leader is " + (best - second) + " ahead — optional scaling may apply").formatted(Formatting.YELLOW));
        }
    }

    private String describeObjective(int index) {
        if (index < 0 || index >= this.activeObjectives.size()) {
            return "<invalid>";
        }

        ResourceSprintSettings.ObjectiveEntry entry = this.activeObjectives.get(index);
        Item item = this.resolveObjectiveItem(entry.id());
        if (item == null) {
            return entry.id() + " [" + entry.difficulty().name().toLowerCase() + "]";
        }
        return item.getName().getString() + " (" + entry.id() + ", " + entry.difficulty().name().toLowerCase() + ")";
    }

    private String getObjectiveItemName(int index) {
        if (index < 0 || index >= this.activeObjectives.size()) {
            return "<invalid>";
        }

        ResourceSprintSettings.ObjectiveEntry entry = this.activeObjectives.get(index);
        Item item = this.resolveObjectiveItem(entry.id());
        if (item == null) {
            return entry.id();
        }
        return item.getName().getString();
    }

    private String getObjectiveCategory(ResourceSprintSettings.ObjectiveEntry entry) {
        if (entry == null || entry.id() == null) return "";
        String id = entry.id().toLowerCase();
        if (id.contains("ore") || id.contains("diamond") || id.contains("iron") || id.contains("coal")) return "Mining";
        if (id.contains("craft") || id.contains("table") || id.contains("crafting")) return "Crafting";
        if (id.contains("log") || id.contains("sapling") || id.contains("leaf")) return "Exploration";
        if (id.contains("sword") || id.contains("bow") || id.contains("axe")) return "Combat";
        return "General";
    }

    private List<ResourceSprintSettings.ObjectiveEntry> resolveActiveObjectives() {
        List<ResourceSprintSettings.ObjectiveEntry> configured = this.settings.objectives();
        if (configured.isEmpty()) {
            return List.of();
        }

        if (this.settings.objectiveDistributionMode() == ResourceSprintSettings.ObjectiveDistributionMode.SHARED) {
            return List.copyOf(configured);
        }

        List<ResourceSprintSettings.ObjectiveEntry> resolved = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (ResourceSprintSettings.ObjectiveEntry entry : configured) {
            double probability = this.clampProbability(entry.probability());
            if (random.nextDouble() <= probability) {
                resolved.add(entry);
            }
        }

        if (resolved.isEmpty()) {
            ResourceSprintSettings.ObjectiveEntry fallback = configured.stream()
                .max(Comparator.comparingDouble(ResourceSprintSettings.ObjectiveEntry::probability))
                .orElse(configured.getFirst());
            resolved.add(fallback);
        }

        return List.copyOf(resolved);
    }

    private double clampProbability(double value) {
        if (value > 0.0 && value <= 1.0) {
            return value;
        }
        return 1.0;
    }

    private String teamFor(ServerPlayerEntity player) {
        String team = this.teams.teamLabel(player.getUuid(), "");
        if (team == null || team.isBlank()) {
            team = this.normalizeTeamLabel(this.teamLabel);
            this.teams.assign(player, team, team, TeamRole.MEMBER);
            this.teamProgress.computeIfAbsent(team, ignored -> new TeamProgress());
        }
        return team;
    }

    private String normalizeTeamLabel(String teamLabel) {
        return teamLabel == null || teamLabel.isBlank() ? "Team" : teamLabel.trim();
    }

    @Override
    protected void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }

        this.vanillaTeams.syncSnapshots(this.server, this.teams.snapshots(this.teamProgress.keySet()), snapshot -> {
            Formatting color = this.vanillaTeams.colorFor(snapshot.id());
            return VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(Text.literal("[" + TeamColorPalette.labelFor(snapshot.id()) + "] ").formatted(color))
                .withFriendlyFireAllowed(false)
                .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
        });
    }

    @Override
    protected void clearVanillaTeams() {
        if (this.server != null) {
            this.vanillaTeams.clear(this.server);
        }
    }

    private int lastTeamCompletionTick(TeamProgress progress) {
        if (progress == null || progress.lastCompletionTick.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return progress.lastCompletionTick.values().stream().mapToInt(Integer::intValue).max().orElse(Integer.MAX_VALUE);
    }

    private @Nullable ServerPlayerEntity findParticipant(UUID uuid) {
        for (ServerPlayerEntity participant : this.getParticipants()) {
            if (participant.getUuid().equals(uuid)) {
                return participant;
            }
        }
        return this.server == null ? null : this.server.getPlayerManager().getPlayer(uuid);
    }

    private boolean playerHasObjectiveItem(ServerPlayerEntity player, Item objectiveItem) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(objectiveItem) && stack.getCount() > 0) {
                return true;
            }
        }
        return false;
    }

    private @Nullable Item resolveObjectiveItem(String id) {
        try {
            Identifier identifier = Identifier.of(id);
            Item item = Registries.ITEM.get(identifier);
            return item == Items.AIR ? null : item;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(this.getParticipants(), message);
    }

    private List<ServerPlayerEntity> getParticipants() {
        return this.context != null ? this.context.liveParticipants() : List.of();
    }

    private boolean isParticipant(ServerPlayerEntity player) {
        return this.context != null && this.context.roster().contains(player);
    }

    private void removeParticipant(ServerPlayerEntity player) {
        if (this.context != null) this.context.roster().remove(player);
    }

    private void replaceParticipant(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (this.context != null) this.context.roster().add(newPlayer);
    }

    private void setRuntimeState(GameState state) {
        if (this.context != null) this.context.setState(state);
    }

    public String getFormattedTime() {
        int totalSeconds = this.elapsedTicks / TICKS_PER_SECOND;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public void onPause(GameState previousState) {
        this.paused = true;
    }

    @Override
    public void onResume(GameState resumedState) {
        this.paused = false;
    }

    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        int onlineCount = roster.onlinePlayers(this.context != null ? this.context.nullableServer() : null).size();
        if (onlineCount < 1) {
            return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(true, null, net.minecraft.text.Text.literal("Match paused! Waiting for a player to reconnect...").formatted(net.minecraft.util.Formatting.RED));
        }
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }
}
