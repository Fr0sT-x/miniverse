package dev.frost.miniverse.minigame.impl.blockshuffle;

import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorStopReason;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.PauseAwareMinigame;
import dev.frost.miniverse.minigame.core.PersistentMinigame;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;

public class BlockShuffleMinigame extends AbstractMinigame {
    private static final int TICKS_PER_SECOND = 20;
    private static final int INTERMISSION_SECONDS = 5;

    private BlockShuffleSettings settings;
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private RoundState roundState = RoundState.INTERMISSION;
    
    private MinecraftServer server;
    private int elapsedTicks;
    private int roundTimerTicks;
    
    private boolean suddenDeathMode = false;
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<UUID, Identifier> assignedBlocks = new HashMap<>();
    private final Set<Integer> timeWarningsShown = new HashSet<>();

    private ScoreboardTemplate scoreboard;
    private ScoreboardLine statusLine;

    private enum RoundState {
        INTERMISSION,
        ACTIVE
    }

    public BlockShuffleMinigame() {
        this.settings = BlockShuffleSettings.defaults();
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        return GlobalMatchRules.defaults();
    }

    @Override
    protected boolean isTeamBased() {
        return false;
    }

    public void applySettings(BlockShuffleSettings settings) {
        this.settings = settings == null ? BlockShuffleSettings.defaults() : settings;
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(net.minecraft.world.GameRules.KEEP_INVENTORY, false);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_IMMEDIATE_RESPAWN, false);
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.roundState = RoundState.INTERMISSION;
        this.server = null;
        this.elapsedTicks = 0;
        this.suddenDeathMode = false;
        this.activePlayers.clear();
        this.points.clear();
        this.assignedBlocks.clear();
        this.timeWarningsShown.clear();
    }

    @Override
    protected void onMatchStart() {
        
        List<ServerPlayerEntity> participants = this.context.liveParticipants();
        if (participants.isEmpty()) {
            this.broadcast(Text.literal("Cannot start Block Shuffle: no participants.").formatted(Formatting.RED));
            return;
        }

        this.setState(GameState.RUNNING);
        if (this.context != null) {
            this.context.setState(GameState.RUNNING);
            this.activePlayers.addAll(participants.stream().map(ServerPlayerEntity::getUuid).collect(Collectors.toSet()));
        }
        
        for (UUID uuid : this.activePlayers) {
            this.points.put(uuid, 0);
        }

        this.broadcast(Text.literal("🧊 Block Shuffle started! First to " + this.settings.pointsToWin() + " points wins.").formatted(Formatting.AQUA));
        this.startRound();
    }

    @Override
    protected void onMatchEnd() {
        this.setState(GameState.ENDING);
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
        }
        if (this.server != null) {
            SpectatorService.getInstance().clearAll();
        }
        if (this.scoreboard != null) {
            this.scoreboard.cleanup(this.server);
        }
        this.activePlayers.clear();
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;
        if (this.getState() != GameState.RUNNING) return;

        if (this.activePlayers.isEmpty()) {
            if (!this.checkProgression(this.context.roster()).blocked()) {
                this.stopGame();
            }
            return;
        }

        this.elapsedTicks++;
        this.updateScoreboardTick();

        if (this.roundState == RoundState.INTERMISSION) {
            if (this.elapsedTicks >= INTERMISSION_SECONDS * TICKS_PER_SECOND) {
                this.startRound();
            } else {
                int secondsRemaining = INTERMISSION_SECONDS - (this.elapsedTicks / TICKS_PER_SECOND);
                if (this.elapsedTicks % TICKS_PER_SECOND == 0 && secondsRemaining > 0 && secondsRemaining <= 3) {
                    GameMessenger.showGameTitle(this.getAliveParticipants(), Text.literal("Next round in " + secondsRemaining).formatted(Formatting.YELLOW), Text.empty());
                }
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

    private void startIntermission() {
        this.roundState = RoundState.INTERMISSION;
        this.elapsedTicks = 0;
        this.assignedBlocks.clear();
        this.rebuildScoreboard();
    }

    private void startRound() {
        this.roundState = RoundState.ACTIVE;
        this.timeWarningsShown.clear();
        
        int duration = this.suddenDeathMode ? this.settings.roundDurationSeconds() / 2 : this.settings.roundDurationSeconds();
        this.roundTimerTicks = Math.max(10, duration) * TICKS_PER_SECOND;

        Identifier sharedBlock = null;
        if (!this.settings.perPlayerBlocks()) {
            sharedBlock = this.suddenDeathMode 
                ? BlockShuffleWeights.pickHardRandomBlock(this.settings.blockPool()) 
                : BlockShuffleWeights.pickRandomBlock(this.settings.blockPool());
        }

        for (UUID uuid : this.activePlayers) {
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;

            Identifier blockAssigned = sharedBlock;
            if (blockAssigned == null) {
                blockAssigned = this.suddenDeathMode 
                    ? BlockShuffleWeights.pickHardRandomBlock(this.settings.blockPool()) 
                    : BlockShuffleWeights.pickRandomBlock(this.settings.blockPool());
            }
            this.assignedBlocks.put(uuid, blockAssigned);
            
            String blockName = Registries.BLOCK.get(blockAssigned).getName().getString();
            player.sendMessage(Text.literal("Your block is: ").append(Text.literal(blockName).formatted(Formatting.GOLD, Formatting.BOLD)), false);
            GameMessenger.showGameTitle(List.of(player), Text.literal(blockName).formatted(Formatting.GOLD), Text.literal("Find and stand on this block!").formatted(Formatting.AQUA));
        }
        this.rebuildScoreboard();
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
        boolean allComplete = true;
        for (UUID uuid : this.activePlayers) {
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;

            Identifier assigned = this.assignedBlocks.get(uuid);
            if (assigned == null) continue;
            
            if (!this.isStandingOnBlock(player, assigned)) {
                allComplete = false;
                break;
            }
        }

        if (allComplete && !this.activePlayers.isEmpty()) {
            this.broadcast(Text.literal("Everyone found their blocks early!").formatted(Formatting.GREEN));
            this.roundTimerTicks = 0;
            this.endRound();
        }
    }

    private void endRound() {
        this.broadcast(Text.literal("Round over!").formatted(Formatting.YELLOW));
        
        for (UUID uuid : this.activePlayers) {
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;

            Identifier assigned = this.assignedBlocks.get(uuid);
            if (assigned != null && this.isStandingOnBlock(player, assigned)) {
                this.points.put(uuid, this.points.getOrDefault(uuid, 0) + 1);
                player.sendMessage(Text.literal("+1 Point!").formatted(Formatting.GREEN, Formatting.BOLD), true);
            } else {
                player.sendMessage(Text.literal("You failed to find your block.").formatted(Formatting.RED), true);
            }
        }

        this.checkWinCondition();
        if (this.state == GameState.RUNNING) {
            this.startIntermission();
        }
    }

    private boolean isStandingOnBlock(ServerPlayerEntity player, Identifier blockId) {
        BlockPos pos = player.getBlockPos();
        BlockState feetState = player.getEntityWorld().getBlockState(pos);
        BlockState belowState = player.getEntityWorld().getBlockState(pos.down());
        
        return Registries.BLOCK.getId(feetState.getBlock()).equals(blockId) || Registries.BLOCK.getId(belowState.getBlock()).equals(blockId);
    }

    private void checkWinCondition() {
        if (this.activePlayers.size() <= 1) {
            if (!this.activePlayers.isEmpty()) {
                this.declareWinner(this.activePlayers.iterator().next());
            } else {
                this.stopGame();
            }
            return;
        }

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
            if (winners.size() == 1) {
                this.declareWinner(winners.getFirst());
            } else {
                this.triggerSuddenDeath(winners);
            }
        }
    }

    private void triggerSuddenDeath(List<UUID> tiedPlayers) {
        this.suddenDeathMode = true;
        this.broadcast(Text.literal("💀 SUDDEN DEATH TIE-BREAKER!").formatted(Formatting.DARK_RED, Formatting.BOLD));
        this.broadcast(Text.literal("The timer is faster and the blocks are harder!").formatted(Formatting.RED));
        GameMessenger.showGameTitle(this.getAliveParticipants(), Text.literal("SUDDEN DEATH").formatted(Formatting.DARK_RED, Formatting.BOLD), Text.empty());

        Set<UUID> eliminated = new HashSet<>(this.activePlayers);
        eliminated.removeAll(tiedPlayers);

        for (UUID uuid : eliminated) {
            this.activePlayers.remove(uuid);
            ServerPlayerEntity player = this.server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Text.literal("You did not reach the score limit. You are now spectating the tie-breaker.").formatted(Formatting.GRAY), false);
                SpectatorService.getInstance().startSpectating(
                    player,
                    SpectatorPolicies.unrestricted(),
                    SpectatorTargetProviders.roster(),
                    null, null, null, null
                );
            }
        }
    }

    private void declareWinner(UUID winnerId) {
        ServerPlayerEntity winner = this.server.getPlayerManager().getPlayer(winnerId);
        String name = winner != null ? winner.getName().getString() : "Unknown";
        
        this.broadcast(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcast(Text.literal("🏆 " + name + " won Block Shuffle! 🏆").formatted(Formatting.GOLD, Formatting.BOLD));
        this.broadcast(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));

        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                runtime,
                new MatchEndResult(Set.of(winnerId), Text.literal(name)),
                MatchLifecycleOptions.defaults("Block Shuffle")
            );
        }
        this.stopGame();
    }

    private void rebuildScoreboard() {
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("Block Shuffle").formatted(Formatting.AQUA, Formatting.BOLD)));
            this.scoreboard.show(this.context.liveParticipants());
        }

        this.scoreboard.clearLines();
        this.statusLine = this.scoreboard.addLine(Text.empty());
        this.scoreboard.addBlankLine();

        List<Map.Entry<UUID, Integer>> sorted = this.points.entrySet().stream()
            .filter(e -> this.activePlayers.contains(e.getKey()))
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .toList();

        for (Map.Entry<UUID, Integer> entry : sorted) {
            ServerPlayerEntity p = this.server != null ? this.server.getPlayerManager().getPlayer(entry.getKey()) : null;
            String name = p != null ? p.getName().getString() : "Offline";
            this.scoreboard.addLine(Text.literal(name + ": " + entry.getValue()));
        }

        this.scoreboard.addBlankLine();
        this.scoreboard.addLine(Text.literal("Goal: " + this.settings.pointsToWin()).formatted(Formatting.YELLOW));
        
        this.scoreboard.resendStructure();
    }

    private void updateScoreboardTick() {
        if (this.statusLine == null) return;

        if (this.roundState == RoundState.ACTIVE) {
            int seconds = this.roundTimerTicks / TICKS_PER_SECOND;
            this.statusLine.setText(Text.literal("Time Left: " + seconds));
        } else {
            int seconds = INTERMISSION_SECONDS - (this.elapsedTicks / TICKS_PER_SECOND);
            this.statusLine.setText(Text.literal("Intermission: " + seconds));
        }
        this.statusLine.updateAll();
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (!this.context.roster().contains(player)) return;
        
        this.context.roster().remove(player);
        this.activePlayers.remove(player.getUuid());
        if (this.scoreboard != null) {
            this.scoreboard.remove(player);
        }
        SpectatorService.getInstance().stopSpectating(player, SpectatorStopReason.MANUAL);

        if (this.state == GameState.RUNNING) {
            this.checkWinCondition();
        }
    }



    private void broadcast(Text text) {
        GameMessenger.broadcast(this.context.roster().livePlayers(this.context.nullableServer()), text);
    }

    private List<ServerPlayerEntity> getAliveParticipants() {
        return this.context.liveParticipants().stream()
            .filter(p -> this.activePlayers.contains(p.getUuid()))
            .collect(Collectors.toList());
    }

    @Override
    public String getName() {
        return BlockShuffleDefinition.DISPLAY_NAME;
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    @Override
    public void setState(GameState state) {
        this.state = state;
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject stateObj = new JsonObject();
        stateObj.addProperty("elapsedTicks", this.elapsedTicks);
        stateObj.addProperty("roundTimerTicks", this.roundTimerTicks);
        stateObj.addProperty("suddenDeathMode", this.suddenDeathMode);
        stateObj.addProperty("roundState", this.roundState.name());
        
        JsonArray activePlayersArray = new JsonArray();
        for (UUID uuid : this.activePlayers) {
            activePlayersArray.add(uuid.toString());
        }
        stateObj.add("activePlayers", activePlayersArray);
        
        JsonObject pointsObj = new JsonObject();
        for (Map.Entry<UUID, Integer> entry : this.points.entrySet()) {
            pointsObj.addProperty(entry.getKey().toString(), entry.getValue());
        }
        stateObj.add("points", pointsObj);
        
        JsonObject assignedBlocksObj = new JsonObject();
        for (Map.Entry<UUID, Identifier> entry : this.assignedBlocks.entrySet()) {
            assignedBlocksObj.addProperty(entry.getKey().toString(), entry.getValue().toString());
        }
        stateObj.add("assignedBlocks", assignedBlocksObj);
        
        JsonArray timeWarnings = new JsonArray();
        for (Integer warning : this.timeWarningsShown) {
            timeWarnings.add(warning);
        }
        stateObj.add("timeWarningsShown", timeWarnings);
        
        return stateObj;
    }

    @Override
    public void loadRuntimeState(JsonObject stateObj) {
        if (stateObj == null) return;
        
        this.elapsedTicks = stateObj.has("elapsedTicks") ? stateObj.get("elapsedTicks").getAsInt() : 0;
        this.roundTimerTicks = stateObj.has("roundTimerTicks") ? stateObj.get("roundTimerTicks").getAsInt() : 0;
        this.suddenDeathMode = stateObj.has("suddenDeathMode") && stateObj.get("suddenDeathMode").getAsBoolean();
        
        if (stateObj.has("roundState")) {
            try {
                this.roundState = RoundState.valueOf(stateObj.get("roundState").getAsString());
            } catch (IllegalArgumentException ignored) {}
        }
        
        this.activePlayers.clear();
        if (stateObj.has("activePlayers")) {
            for (var elem : stateObj.getAsJsonArray("activePlayers")) {
                this.activePlayers.add(UUID.fromString(elem.getAsString()));
            }
        }
        
        this.points.clear();
        if (stateObj.has("points")) {
            JsonObject pointsObj = stateObj.getAsJsonObject("points");
            for (String key : pointsObj.keySet()) {
                this.points.put(UUID.fromString(key), pointsObj.get(key).getAsInt());
            }
        }
        
        this.assignedBlocks.clear();
        if (stateObj.has("assignedBlocks")) {
            JsonObject assignedBlocksObj = stateObj.getAsJsonObject("assignedBlocks");
            for (String key : assignedBlocksObj.keySet()) {
                this.assignedBlocks.put(UUID.fromString(key), Identifier.of(assignedBlocksObj.get(key).getAsString()));
            }
        }
        
        this.timeWarningsShown.clear();
        if (stateObj.has("timeWarningsShown")) {
            for (var elem : stateObj.getAsJsonArray("timeWarningsShown")) {
                this.timeWarningsShown.add(elem.getAsInt());
            }
        }
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        if (this.state != GameState.RUNNING && this.state != GameState.PAUSED) {
            return;
        }
        UUID uuid = player.getUuid();
        if (this.activePlayers.contains(uuid)) {
            return;
        }
        
        this.context.roster().add(player);
        this.activePlayers.add(uuid);
        this.points.putIfAbsent(uuid, 0);
        
        if (this.roundState == RoundState.ACTIVE) {
            Identifier assigned = this.suddenDeathMode 
                ? BlockShuffleWeights.pickHardRandomBlock(this.settings.blockPool()) 
                : BlockShuffleWeights.pickRandomBlock(this.settings.blockPool());
            this.assignedBlocks.put(uuid, assigned);
            
            String blockName = Registries.BLOCK.get(assigned).getName().getString();
            player.sendMessage(Text.literal("You joined late! Your block is: ").append(Text.literal(blockName).formatted(Formatting.GOLD, Formatting.BOLD)), false);
            GameMessenger.showGameTitle(List.of(player), Text.literal(blockName).formatted(Formatting.GOLD), Text.literal("Find and stand on this block!").formatted(Formatting.AQUA));
        } else {
            player.sendMessage(Text.literal("You joined late! Waiting for the next round to start.").formatted(Formatting.YELLOW), false);
        }
        if (this.scoreboard != null) {
            this.scoreboard.show(player);
        }
        this.rebuildScoreboard();
    }

    @Override
    public void removeParticipantMidGame(ServerPlayerEntity player) {
        this.onPlayerLeave(player);
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
