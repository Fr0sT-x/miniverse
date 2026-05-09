package dev.frost.miniverse.minigame.impl.resourcesprint;

import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamDescriptor;
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

public class ResourceSprintMinigame implements Minigame {
    private static final int TICKS_PER_SECOND = 20;
    private static final String NAME = "Resource Sprint";

    private GameState state;
    private ResourceSprintSettings settings;
    private MinecraftServer server;
    private String teamLabel = "Team";
    private int elapsedTicks;
    private boolean suddenDeathActive;
    private final Set<String> suddenDeathTeams = new HashSet<>();
    private final List<ResourceSprintSettings.ObjectiveEntry> activeObjectives = new ArrayList<>();
    private final Map<String, TeamProgress> teamProgress = new LinkedHashMap<>();
    private final Map<UUID, String> playerTeams = new HashMap<>();
    // Scoreboard and time-warning helpers
    private static final String SCOREBOARD_OBJECTIVE = "resourcesprint_display";
    private static final dev.frost.miniverse.minigame.core.ScoreboardController SCOREBOARD =
        new dev.frost.miniverse.minigame.core.ScoreboardController(SCOREBOARD_OBJECTIVE, Text.literal("Resource Sprint"));
    private final Set<Integer> timeWarningsShown = new HashSet<>();
    private final ResourceSprintEventMessenger eventMessenger = new ResourceSprintEventMessenger();
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
        this.playerTeams.put(player.getUuid(), resolvedTeam);
        this.teamProgress.computeIfAbsent(resolvedTeam, ignored -> new TeamProgress());
        this.teamLabel = resolvedTeam;
        this.syncVanillaTeams();
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.settings = ResourceSprintSettings.defaults();
        this.server = null;
        this.elapsedTicks = 0;
        this.suddenDeathActive = false;
        this.suddenDeathTeams.clear();
        this.timeWarningsShown.clear();
        this.activeObjectives.clear();
        this.teamProgress.clear();
        this.playerTeams.clear();
    }

    @Override
    public void startGame() {
        if (this.state == GameState.IN_PROGRESS) {
            return;
        }

        if (!this.canStartMatch()) {
            this.broadcastMessage(Text.literal("Cannot start Resource Sprint: no valid objectives or participants are assigned.").formatted(Formatting.RED));
            return;
        }

        this.state = GameState.IN_PROGRESS;
        MinigameManager.getInstance().setCurrentState(GameState.IN_PROGRESS);
        this.elapsedTicks = 0;
        this.suddenDeathActive = false;
        this.suddenDeathTeams.clear();
        this.timeWarningsShown.clear();
        this.activeObjectives.clear();
        this.activeObjectives.addAll(this.resolveActiveObjectives());
        this.teamProgress.values().forEach(TeamProgress::reset);
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            this.teamProgress.computeIfAbsent(this.teamFor(participant), ignored -> new TeamProgress());
        }

        this.broadcastMessage(Text.literal("✓ Resource Sprint started!").formatted(Formatting.GREEN));
        // Major event: game start (title + subtitle)
        eventMessenger.send(ResourceSprintEventMessenger.Severity.MAJOR,
            Text.literal("🏃 RESOURCE SPRINT BEGINS!").formatted(Formatting.GOLD),
            Text.literal("Mode: " + this.describeMode() + " — " + modeStrategyHint()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("Teams are sharing this world with isolated objective progress.").formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("Mode: " + this.describeMode() + " | Tie-break: " + this.describeTieBreak()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("Objectives complete: 0 / " + this.activeObjectives.size()).formatted(Formatting.AQUA));
        for (String team : this.teamProgress.keySet()) {
            this.broadcastCurrentObjective(team, this.teamProgress.get(team));
        }
        this.updateScoreboard();
    }

    @Override
    public void stopGame() {
        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);
        this.elapsedTicks = 0;
        this.suddenDeathActive = false;
        this.suddenDeathTeams.clear();
        this.timeWarningsShown.clear();
        this.teamProgress.values().forEach(TeamProgress::reset);
        if (this.server != null) {
            SCOREBOARD.clear(this.server);
        }
        this.clearVanillaTeams();
        this.server = null;
        MinigameManager.getInstance().clearParticipants();
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        // Objective progress is item-driven, so deaths do not eliminate participants in the MVP.
    }

    public void onServerTick(MinecraftServer server) {
        this.server = server;
        if (this.state != GameState.IN_PROGRESS) {
            return;
        }

        if (MinigameManager.getInstance().getParticipants().isEmpty()) {
            this.stopGame();
            return;
        }

        this.elapsedTicks++;
        // Check time warnings each tick
        this.checkTimeWarnings();
        // Update scoreboard every tick to keep it visible and update the time
        this.updateScoreboard();

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
            if (this.state != GameState.IN_PROGRESS) {
                return;
            }
        }
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (!MinigameManager.getInstance().isParticipant(oldPlayer)) {
            return;
        }

        MinigameManager.getInstance().replaceParticipant(oldPlayer, newPlayer);
        this.syncVanillaTeams();
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        boolean wasTracked = MinigameManager.getInstance().isParticipant(player);
        MinigameManager.getInstance().removeParticipant(player);
        this.playerTeams.remove(player.getUuid());
        this.syncVanillaTeams();

        if (wasTracked && this.state == GameState.IN_PROGRESS && MinigameManager.getInstance().getParticipants().isEmpty()) {
            this.stopGame();
        }
    }

    public boolean canStartMatch() {
        return !MinigameManager.getInstance().getParticipants().isEmpty() && !this.settings.objectives().isEmpty();
    }

    @Override
    public String getName() {
        return NAME;
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

        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
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
        this.updateScoreboard();

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

    private void updateScoreboard() {
        if (this.server == null) return;
        this.syncVanillaTeams();
        SCOREBOARD.clear(this.server);

        int order = 100;
        SCOREBOARD.setScore(this.server, "⏱ Time: " + this.getFormattedTime(), order--);
        SCOREBOARD.setScore(this.server, "──────────────", order--);
        for (Map.Entry<String, TeamProgress> entry : this.teamProgress.entrySet()) {
            TeamProgress progress = entry.getValue();
            SCOREBOARD.setScore(this.server, entry.getKey() + ": " + progress.currentObjectiveIndex + "/" + this.activeObjectives.size(), order--);
        }

        SCOREBOARD.setScore(this.server, "──────────────", order--);
        TeamProgress ownProgress = this.teamProgress.get(this.teamLabel);
        if (ownProgress != null && ownProgress.currentObjectiveIndex < this.activeObjectives.size()) {
            SCOREBOARD.setScore(this.server, "Next: " + this.getObjectiveItemName(ownProgress.currentObjectiveIndex), order--);
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
        GameMessenger.showGameTitle(MinigameManager.getInstance().getParticipants(),
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
            this.stopGame();
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
        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);
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

        GameMessenger.showGameOverTitle(MinigameManager.getInstance().getParticipants(), Text.literal(winningTeam + " wins Resource Sprint"));
        this.stopGame();
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
        String team = this.playerTeams.get(player.getUuid());
        if (team == null || team.isBlank()) {
            team = this.normalizeTeamLabel(this.teamLabel);
            this.playerTeams.put(player.getUuid(), team);
            this.teamProgress.computeIfAbsent(team, ignored -> new TeamProgress());
        }
        return team;
    }

    private String normalizeTeamLabel(String teamLabel) {
        return teamLabel == null || teamLabel.isBlank() ? "Team" : teamLabel.trim();
    }

    private void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }

        List<VanillaTeamDescriptor> descriptors = new ArrayList<>();
        for (String team : this.teamProgress.keySet()) {
            List<ServerPlayerEntity> members = MinigameManager.getInstance().getParticipants().stream()
                .filter(player -> this.teamFor(player).equals(team))
                .toList();
            Formatting color = this.vanillaTeams.colorFor(team);
            VanillaTeamOptions options = VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(Text.literal("[" + team + "] ").formatted(color))
                .withFriendlyFireAllowed(false)
                .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
            descriptors.add(new VanillaTeamDescriptor(team, Text.literal(team), members, options));
        }
        this.vanillaTeams.sync(this.server, descriptors);
    }

    private void clearVanillaTeams() {
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
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
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
        GameMessenger.broadcast(MinigameManager.getInstance().getParticipants(), message);
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
}




