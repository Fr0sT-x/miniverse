package dev.frost.miniverse.minigame.impl.deathshuffle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.PauseAwareMinigame;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjective;
import dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;

public class DeathShuffleMinigame extends AbstractMinigame {
    private static final int TICKS_PER_SECOND = 20;

    private DeathShuffleSettings settings;
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private RoundState roundState = RoundState.INTERMISSION;
    
    private MinecraftServer server;
    private int elapsedTicks;
    private int roundTimerTicks;
    private int graceTimerTicks;
    
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Identifier> assignedObjectives = new HashMap<>();
    private final Set<UUID> completedThisRound = new HashSet<>();
    private final Set<Integer> timeWarningsShown = new HashSet<>();

    private ScoreboardTemplate baseScoreboard;
    private final Map<UUID, ScoreboardLine> timerLines = new HashMap<>();

    private enum RoundState {
        INTERMISSION,
        GRACE_PERIOD,
        ACTIVE
    }

    public DeathShuffleMinigame() {
        this.settings = DeathShuffleSettings.fromNbt(new net.minecraft.nbt.NbtCompound()); // Defaults
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        return new GlobalMatchRules(true, false, true, true, true, true, true, false);
    }

    @Override
    protected boolean isTeamBased() {
        return false;
    }

    public void applySettings(DeathShuffleSettings settings) {
        this.settings = settings;
    }

    @Override
    public void initialize() {
        this.setState(GameState.WAITING_FOR_PLAYERS);
        this.roundState = RoundState.INTERMISSION;
        this.server = null;
        this.elapsedTicks = 0;
        this.activePlayers.clear();
        this.points.clear();
        this.assignedObjectives.clear();
        this.completedThisRound.clear();
        this.timeWarningsShown.clear();
    }

    @Override
    protected void onMatchStart() {
        
        List<ServerPlayerEntity> participants = this.context.liveParticipants();
        if (participants.isEmpty()) {
            this.broadcast(Text.literal("Cannot start Death Shuffle: no participants.").formatted(Formatting.RED));
            return;
        }

        this.setState(GameState.IN_PROGRESS);
        if (this.context != null) {
            this.context.setState(GameState.IN_PROGRESS);
            this.activePlayers.addAll(participants.stream().map(ServerPlayerEntity::getUuid).collect(Collectors.toSet()));
        }
        
        for (UUID uuid : this.activePlayers) {
            this.points.put(uuid, 0);
        }
        
        for (ServerPlayerEntity player : participants) {
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        }

        this.broadcast(Text.literal("☠ Death Shuffle started! First to " + this.settings.pointsToWin() + " points wins.").formatted(Formatting.RED));
        this.rebuildScoreboards();
        this.startGracePeriod();
    }

    @Override
    protected void onMatchEnd() {
        this.setState(GameState.ENDING);
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
        }
        if (this.server != null && this.baseScoreboard != null) {
            this.baseScoreboard.cleanup(this.server);
        }
        this.activePlayers.clear();
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;
        if (this.getState() != GameState.IN_PROGRESS) return;

        if (this.activePlayers.isEmpty()) {
            if (!MatchLifecycleController.getInstance().isDisconnectGraceActive()) {
                this.stopGame();
            }
            return;
        }

        // Force keep inventory on all participants
        for (ServerPlayerEntity player : this.getAliveParticipants()) {
            player.getEntityWorld().getGameRules().get(net.minecraft.world.GameRules.KEEP_INVENTORY).set(true, server);
        }

        this.elapsedTicks++;
        this.updateScoreboardTimers();

        if (this.roundState == RoundState.INTERMISSION) {
            if (this.elapsedTicks >= 5 * TICKS_PER_SECOND) {
                this.startGracePeriod();
            } else {
                int secondsRemaining = 5 - (this.elapsedTicks / TICKS_PER_SECOND);
                if (this.elapsedTicks % TICKS_PER_SECOND == 0 && secondsRemaining > 0 && secondsRemaining <= 3) {
                    GameMessenger.showGameTitle(this.getAliveParticipants(), Text.literal("Next round in " + secondsRemaining).formatted(Formatting.YELLOW), Text.empty());
                }
            }
        } else if (this.roundState == RoundState.GRACE_PERIOD) {
            this.graceTimerTicks--;
            int secondsRemaining = (int) Math.ceil(this.graceTimerTicks / (float) TICKS_PER_SECOND);
            
            if (this.graceTimerTicks % TICKS_PER_SECOND == 0 && secondsRemaining > 0) {
                GameMessenger.showGameTitle(this.getAliveParticipants(), Text.literal(String.valueOf(secondsRemaining)).formatted(Formatting.GOLD), Text.literal("Get ready...").formatted(Formatting.GRAY));
            }
            
            if (this.graceTimerTicks <= 0) {
                GameMessenger.showGameTitle(this.getAliveParticipants(), Text.literal("GO!").formatted(Formatting.GREEN, Formatting.BOLD), Text.empty());
                this.startRound();
            }
        } else if (this.roundState == RoundState.ACTIVE) {
            this.roundTimerTicks--;
            this.checkTimeWarnings();

            if (this.roundTimerTicks <= 0) {
                this.endRound();
            } else {
                this.checkEarlyCompletions();
            }
        }
    }

    private void startGracePeriod() {
        this.roundState = RoundState.GRACE_PERIOD;
        this.graceTimerTicks = Math.max(1, this.settings.gracePeriodSeconds()) * TICKS_PER_SECOND;
        this.completedThisRound.clear();
        this.assignedObjectives.clear();
        
        DeathObjective sharedObjective = null;
        if (!this.settings.perPlayerObjectives()) {
            sharedObjective = DeathShuffleWeights.selectRandomObjective(this.server, this.settings, this.server.getOverworld().getRandom());
        }

        for (UUID uuid : this.activePlayers) {
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;

            DeathObjective objectiveAssigned = sharedObjective;
            if (objectiveAssigned == null) {
                objectiveAssigned = DeathShuffleWeights.selectRandomObjective(this.server, this.settings, player.getRandom());
            }
            
            if (objectiveAssigned != null) {
                Identifier id = dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager.getId(this.server, objectiveAssigned);
                this.assignedObjectives.put(uuid, id);
                
                player.sendMessage(Text.literal("New Objective: ").append(objectiveAssigned.displayName().copy().formatted(Formatting.RED, Formatting.BOLD)), false);
                Text titleSubtitle = Text.literal("Prepare to die!").formatted(Formatting.GRAY);
                if (objectiveAssigned.description().isPresent()) {
                    Text desc = objectiveAssigned.description().get();
                    player.sendMessage(Text.literal(" -> ").formatted(Formatting.GRAY).append(desc.copy().formatted(Formatting.ITALIC)), false);
                    titleSubtitle = desc.copy().formatted(Formatting.GRAY);
                }
                GameMessenger.showGameTitle(List.of(player), objectiveAssigned.displayName().copy().formatted(Formatting.RED), titleSubtitle);
            }
        }
        this.rebuildScoreboards();
    }

    private void startRound() {
        this.roundState = RoundState.ACTIVE;
        this.timeWarningsShown.clear();
        this.roundTimerTicks = Math.max(10, this.settings.roundDurationSeconds()) * TICKS_PER_SECOND;
        this.rebuildScoreboards();
    }

    private void checkTimeWarnings() {
        int secondsRemaining = this.roundTimerTicks / TICKS_PER_SECOND;

        if (secondsRemaining == 60 && !this.timeWarningsShown.contains(60)) {
            this.broadcast(Text.literal("⏰ 1 minute remaining!").formatted(Formatting.YELLOW));
            this.timeWarningsShown.add(60);
        } else if (secondsRemaining == 10 && !this.timeWarningsShown.contains(10)) {
            this.broadcast(Text.literal("⏰ 10 seconds remaining!").formatted(Formatting.RED));
            this.timeWarningsShown.add(10);
        } else if (secondsRemaining <= 5 && secondsRemaining > 0 && !this.timeWarningsShown.contains(secondsRemaining)) {
            GameMessenger.showGameTitle(this.getAliveParticipants(), Text.literal(String.valueOf(secondsRemaining)).formatted(Formatting.RED), Text.empty());
            this.timeWarningsShown.add(secondsRemaining);
        }
    }

    private void checkEarlyCompletions() {
        if (this.completedThisRound.containsAll(this.activePlayers) && !this.activePlayers.isEmpty()) {
            this.broadcast(Text.literal("Everyone completed their objectives early!").formatted(Formatting.GREEN));
            this.roundTimerTicks = 0;
            this.endRound();
        }
    }

    private void endRound() {
        this.broadcast(Text.literal("Round over!").formatted(Formatting.YELLOW));
        
        List<Text> results = new ArrayList<>();
        results.add(Text.literal("--- Round Results ---").formatted(Formatting.GOLD, Formatting.BOLD));
        
        for (UUID uuid : this.activePlayers) {
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(uuid);
            String name = player != null ? player.getName().getString() : "Unknown";
            
            if (this.completedThisRound.contains(uuid)) {
                results.add(Text.literal("✓ ").formatted(Formatting.GREEN).append(Text.literal(name).formatted(Formatting.WHITE)).append(Text.literal(" - " + this.points.getOrDefault(uuid, 0) + " pts").formatted(Formatting.GRAY)));
            } else {
                results.add(Text.literal("✗ ").formatted(Formatting.RED).append(Text.literal(name).formatted(Formatting.WHITE)).append(Text.literal(" - " + this.points.getOrDefault(uuid, 0) + " pts").formatted(Formatting.GRAY)));
            }
        }
        
        for (ServerPlayerEntity player : this.getAliveParticipants()) {
            for (Text line : results) {
                player.sendMessage(line, false);
            }
        }

        this.checkWinCondition();
        if (this.state == GameState.IN_PROGRESS) {
            this.startIntermission();
        } else {
            this.rebuildScoreboards();
        }
    }

    private void startIntermission() {
        this.roundState = RoundState.INTERMISSION;
        this.elapsedTicks = 0;
        GameMessenger.showGameTitle(this.getAliveParticipants(), Text.literal("Round Results").formatted(Formatting.GOLD), Text.literal("Check chat for details").formatted(Formatting.GRAY));
        this.rebuildScoreboards();
    }

    private void checkWinCondition() {
        List<UUID> winners = new ArrayList<>();
        int highestScore = 0;
        
        for (Map.Entry<UUID, Integer> entry : this.points.entrySet()) {
            if (!this.activePlayers.contains(entry.getKey())) continue;
            int score = entry.getValue();
            if (score > highestScore) {
                highestScore = score;
                winners.clear();
                winners.add(entry.getKey());
            } else if (score == highestScore) {
                winners.add(entry.getKey());
            }
        }

        if (highestScore >= this.settings.pointsToWin()) {
            this.declareWinners(winners);
        }
    }

    private void declareWinners(List<UUID> winners) {
        this.state = GameState.ENDING;
        this.context.setState(GameState.ENDING);
        
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null) return;

        if (winners.isEmpty()) {
            MatchLifecycleController.getInstance().endMatch(runtime, new MatchEndResult(Set.of(), Text.empty()), MatchLifecycleOptions.defaults(DeathShuffleDefinition.DISPLAY_NAME));
            return;
        }

        if (winners.size() == 1) {
            ServerPlayerEntity winner = this.server.getPlayerManager().getPlayer(winners.get(0));
            Text winnerName = winner != null ? winner.getName() : Text.literal("Unknown");
            this.broadcast(Text.literal("🎉 ").append(winnerName).append(" won the match!").formatted(Formatting.GREEN, Formatting.BOLD));
            MatchLifecycleController.getInstance().endMatch(runtime, new MatchEndResult(Set.of(winners.get(0)), winnerName), MatchLifecycleOptions.defaults(DeathShuffleDefinition.DISPLAY_NAME));
        } else {
            this.broadcast(Text.literal("🎉 It's a tie between multiple players!").formatted(Formatting.GREEN, Formatting.BOLD));
            Text label = Text.literal("Tie!");
            MatchLifecycleController.getInstance().endMatch(runtime, new MatchEndResult(new HashSet<>(winners), label), MatchLifecycleOptions.defaults(DeathShuffleDefinition.DISPLAY_NAME));
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (this.state != GameState.IN_PROGRESS || this.roundState != RoundState.ACTIVE) {
            // Instantly respawn if in minigame
            if (this.isParticipant(player.getUuid())) {
                this.instantRespawn(player);
            }
            return;
        }

        UUID uuid = player.getUuid();
        if (!this.activePlayers.contains(uuid)) return;

        // Force instant respawn manually
        this.instantRespawn(player);

        if (this.completedThisRound.contains(uuid)) {
            // Already completed this round
            return;
        }

        Identifier assignedId = this.assignedObjectives.get(uuid);
        if (assignedId == null) return;

        DeathObjective objective = dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager.get(this.server, assignedId);

        if (objective != null && objective.damageCondition().isPresent()) {
            if (objective.damageCondition().get().test(player, source, 1.0f, 1.0f, false)) {
                // Success!
                this.completedThisRound.add(uuid);
                this.points.put(uuid, this.points.getOrDefault(uuid, 0) + 1);
                
                player.sendMessage(Text.literal("Objective Complete! +1 Point!").formatted(Formatting.GREEN, Formatting.BOLD), false);
                GameMessenger.showGameTitle(List.of(player), Text.literal("Completed!").formatted(Formatting.GREEN), Text.literal("Wait for the next round...").formatted(Formatting.GRAY));
                this.rebuildScoreboards();
            } else {
                player.sendMessage(Text.literal("Wrong death cause! Try again.").formatted(Formatting.RED), true);
            }
        }
    }
    
    private void instantRespawn(ServerPlayerEntity player) {
        // Schedule a task for the next tick to respawn the player, since doing it inside the death event can cause issues
        MinecraftServer server = this.server;
        if (server != null) {
            server.execute(() -> {
                if (player.isDead()) {
                    server.getPlayerManager().respawnPlayer(player, false, Entity.RemovalReason.KILLED);
                }
            });
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.state == GameState.WAITING_FOR_PLAYERS) {
            if (this.context != null) this.context.roster().remove(player);
            this.activePlayers.remove(player.getUuid());
        } else {
            this.rebuildScoreboards();
        }
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        if (this.state == GameState.WAITING_FOR_PLAYERS) {
            if (this.context != null) this.context.roster().add(player);
            this.activePlayers.add(player.getUuid());
        } else if (this.state == GameState.IN_PROGRESS) {
            if (this.context != null) this.context.roster().add(player);
            this.activePlayers.add(player.getUuid());
            this.points.putIfAbsent(player.getUuid(), 0);
            player.sendMessage(Text.literal("You joined the game late. Wait for your next objective!").formatted(Formatting.YELLOW), false);
            this.rebuildScoreboards();
        }
    }

    @Override
    public void removeParticipantMidGame(ServerPlayerEntity player) {
        if (this.context != null) this.context.roster().remove(player);
        this.activePlayers.remove(player.getUuid());
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject stateJson = new JsonObject();
        stateJson.addProperty("elapsedTicks", this.elapsedTicks);
        stateJson.addProperty("roundTimerTicks", this.roundTimerTicks);
        stateJson.addProperty("graceTimerTicks", this.graceTimerTicks);
        stateJson.addProperty("roundState", this.roundState.name());
        
        JsonObject pointsJson = new JsonObject();
        for (Map.Entry<UUID, Integer> entry : this.points.entrySet()) {
            pointsJson.addProperty(entry.getKey().toString(), entry.getValue());
        }
        stateJson.add("points", pointsJson);

        JsonObject assignedJson = new JsonObject();
        for (Map.Entry<UUID, Identifier> entry : this.assignedObjectives.entrySet()) {
            assignedJson.addProperty(entry.getKey().toString(), entry.getValue().toString());
        }
        stateJson.add("assignedObjectives", assignedJson);
        
        JsonArray completedArray = new JsonArray();
        for (UUID uuid : this.completedThisRound) {
            completedArray.add(uuid.toString());
        }
        stateJson.add("completedThisRound", completedArray);

        return stateJson;
    }

    @Override
    public void loadRuntimeState(JsonObject stateJson) {
        if (stateJson.has("elapsedTicks")) this.elapsedTicks = stateJson.get("elapsedTicks").getAsInt();
        if (stateJson.has("roundTimerTicks")) this.roundTimerTicks = stateJson.get("roundTimerTicks").getAsInt();
        if (stateJson.has("graceTimerTicks")) this.graceTimerTicks = stateJson.get("graceTimerTicks").getAsInt();
        if (stateJson.has("roundState")) {
            try {
                this.roundState = RoundState.valueOf(stateJson.get("roundState").getAsString());
            } catch (Exception ignored) {}
        }

        if (stateJson.has("points")) {
            JsonObject pointsJson = stateJson.getAsJsonObject("points");
            for (String key : pointsJson.keySet()) {
                this.points.put(UUID.fromString(key), pointsJson.get(key).getAsInt());
            }
        }

        if (stateJson.has("assignedObjectives")) {
            JsonObject assignedJson = stateJson.getAsJsonObject("assignedObjectives");
            for (String key : assignedJson.keySet()) {
                this.assignedObjectives.put(UUID.fromString(key), Identifier.tryParse(assignedJson.get(key).getAsString()));
            }
        }
        
        if (stateJson.has("completedThisRound")) {
            JsonArray completedArray = stateJson.getAsJsonArray("completedThisRound");
            for (int i = 0; i < completedArray.size(); i++) {
                this.completedThisRound.add(UUID.fromString(completedArray.get(i).getAsString()));
            }
        }
    }

    public void setState(GameState state) {
        this.state = state;
    }

    @Override
    public String getName() {
        return DeathShuffleDefinition.DISPLAY_NAME;
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    private void broadcast(Text message) {
        if (this.context != null && this.context.nullableServer() != null) {
            for (ServerPlayerEntity player : this.getAliveParticipants()) {
                player.sendMessage(message, false);
            }
        }
    }

    private List<ServerPlayerEntity> getAliveParticipants() {
        if (this.server == null) return List.of();
        return this.activePlayers.stream()
            .map(this.server.getPlayerManager()::getPlayer)
            .filter(p -> p != null && !p.isDisconnected())
            .collect(Collectors.toList());
    }
    
    private boolean isParticipant(UUID uuid) {
        return this.activePlayers.contains(uuid);
    }

    private void rebuildScoreboards() {
        if (this.server == null) return;
        List<ServerPlayerEntity> participants = this.getAliveParticipants();
        if (participants.isEmpty()) return;

        if (this.baseScoreboard == null) {
            this.baseScoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("Death Shuffle").formatted(Formatting.RED, Formatting.BOLD)));
            this.baseScoreboard.show(participants);
        } else {
            for (ServerPlayerEntity player : participants) {
                if (!this.baseScoreboard.isViewing(player)) {
                    this.baseScoreboard.show(player);
                }
            }
        }

        this.baseScoreboard.rebuild(player -> {
            UUID uuid = player.getUuid();
            ScoreboardTemplate board = new ScoreboardTemplate(this.getName(), Text.literal("Death Shuffle").formatted(Formatting.RED, Formatting.BOLD));
            
            ScoreboardLine timerLine = board.addLine(Text.empty());
            this.timerLines.put(uuid, timerLine);
            
            board.addBlankLine();

            List<Map.Entry<UUID, Integer>> sortedScores = new ArrayList<>(this.points.entrySet());
            sortedScores.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            for (int i = 0; i < Math.min(sortedScores.size(), 10); i++) {
                Map.Entry<UUID, Integer> entry = sortedScores.get(i);
                ServerPlayerEntity p = this.server.getPlayerManager().getPlayer(entry.getKey());
                String name = p != null ? p.getName().getString() : "Unknown";
                
                Formatting nameColor = Formatting.GRAY;
                if (this.roundState == RoundState.ACTIVE) {
                    if (this.completedThisRound.contains(entry.getKey())) {
                        nameColor = Formatting.GREEN;
                    } else if (this.activePlayers.contains(entry.getKey())) {
                        nameColor = Formatting.WHITE;
                    }
                }

                board.addLine(Text.literal(name + ": ")
                    .formatted(nameColor)
                    .append(Text.literal(String.valueOf(entry.getValue())).formatted(Formatting.GOLD)));
            }

            if (this.roundState == RoundState.ACTIVE) {
                board.addBlankLine();
                board.addLine(Text.literal("Objective:").formatted(Formatting.RED));
                
                Identifier objId = this.assignedObjectives.get(this.settings.perPlayerObjectives() ? uuid : this.activePlayers.stream().findFirst().orElse(null));
                if (objId != null) {
                    DeathObjective obj = dev.frost.miniverse.minigame.impl.deathshuffle.objective.DeathObjectiveManager.get(this.server, objId);
                    if (obj != null) {
                        board.addLine(obj.displayName().copy().formatted(Formatting.WHITE));
                        if (obj.description().isPresent()) {
                            board.addLine(obj.description().get().copy().formatted(Formatting.GRAY, Formatting.ITALIC));
                        }
                    }
                }
            }
            return board;
        });

        this.updateScoreboardTimers();
    }

    private void updateScoreboardTimers() {
        for (ServerPlayerEntity player : this.getAliveParticipants()) {
            ScoreboardLine timerLine = this.timerLines.get(player.getUuid());
            if (timerLine != null) {
                if (this.roundState == RoundState.ACTIVE) {
                    int secondsRemaining = this.roundTimerTicks / TICKS_PER_SECOND;
                    int minutes = secondsRemaining / 60;
                    int seconds = secondsRemaining % 60;
                    timerLine.setText(Text.literal("Time: ").formatted(Formatting.WHITE)
                        .append(Text.literal(String.format("%02d:%02d", minutes, seconds)).formatted(Formatting.RED)));
                } else if (this.roundState == RoundState.GRACE_PERIOD) {
                    int secondsRemaining = (int) Math.ceil(this.graceTimerTicks / (float) TICKS_PER_SECOND);
                    timerLine.setText(Text.literal("Starting in: ").formatted(Formatting.WHITE)
                        .append(Text.literal(String.valueOf(secondsRemaining)).formatted(Formatting.GOLD)));
                } else {
                    int secondsRemaining = 5 - (this.elapsedTicks / TICKS_PER_SECOND);
                    timerLine.setText(Text.literal("Next Round: ").formatted(Formatting.WHITE)
                        .append(Text.literal(String.valueOf(secondsRemaining)).formatted(Formatting.YELLOW)));
                }
                timerLine.updateAll();
            }
        }
    }
}
