package dev.frost.miniverse.minigame.impl.deathswap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.countdown.CountdownService;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.respawn.RespawnMode;
import dev.frost.miniverse.minigame.core.respawn.RespawnPolicyController;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.swap.DerangementAssignment;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamRole;
import dev.frost.miniverse.team.TeamColorPalette;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.team.TeamManagerProvider;

public class DeathSwapMinigame extends AbstractMinigame implements PersistentMinigame, PlayerDamageAware, PlayerRespawnAware, TeamManagerProvider {
    private static final String NAME = "Death Swap";
    private static final String SCOREBOARD_OBJECTIVE = "deathswap_display";
    private static final int DEATH_ATTRIBUTION_SECONDS = 90;
    private static final int SWAP_WARNING_SECONDS = 10;
    private static final int RECENT_TARGET_LIMIT = 3;
    private ScoreboardTemplate scoreboard;
    private ScoreboardLine nextSwapLine;

    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("deathswap");
    private final CountdownService visibleCountdowns = new CountdownService();
    private final DerangementAssignment<UUID> assignmentBuilder = new DerangementAssignment<>();
    private final SpectatorService spectators = SpectatorService.getInstance();
    private final TeamManager teams = new TeamManager();
    private final Set<UUID> aliveParticipants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> points = new ConcurrentHashMap<>();
    private final Map<UUID, ArrayDeque<UUID>> recentTargets = new ConcurrentHashMap<>();
    private final Map<UUID, SwapAttribution> deathAttributions = new ConcurrentHashMap<>();
    private Map<UUID, UUID> pendingAssignment = Map.of();

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private DeathSwapSettings settings = DeathSwapSettings.defaults();
    private RespawnPolicyController respawns = new RespawnPolicyController(RespawnMode.POINTS, this.spectators);

    @Nullable
    private MinecraftServer server;
    private long gameTicks;
    private int secondAccumulator;
    private int swapTicksRemaining;
    private int swapCount;
    private boolean warnedCurrentSwap;

    @Override
    protected GlobalMatchRules configureGameRules() {
        return new GlobalMatchRules(true, false, true, true, true, true, true, false);
    }

    @Override
    protected boolean isTeamBased() {
        return false;
    }

    public void applySettings(DeathSwapSettings settings) {
        this.settings = settings == null ? DeathSwapSettings.defaults() : settings;
        this.respawns = new RespawnPolicyController(this.settings.respawnMode(), this.spectators);
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.server = null;
        this.gameTicks = 0L;
        this.secondAccumulator = 0;
        this.swapTicksRemaining = 0;
        this.swapCount = 0;
        this.warnedCurrentSwap = false;
        this.pendingAssignment = Map.of();
        this.visibleCountdowns.reset();
        this.teams.clear();
        this.spectators.clearAll();
        this.aliveParticipants.clear();
        this.points.clear();
        this.recentTargets.clear();
        this.deathAttributions.clear();
    }

    @Override
    protected void onMatchStart() {
        List<ServerPlayerEntity> participants = this.getParticipants();
        if (participants.size() < 2) {
            this.broadcast(Text.literal("Need at least two players to start Death Swap.").formatted(Formatting.RED));
            return;
        }

        this.setState(GameState.RUNNING);
        if (this.context != null) {
            this.context.setState(GameState.RUNNING);
        }
        this.gameTicks = 0L;
        this.secondAccumulator = 0;
        this.swapCount = 0;
        this.swapTicksRemaining = Math.max(1, this.settings.swapIntervalSeconds()) * 20;
        this.warnedCurrentSwap = false;
        this.pendingAssignment = Map.of();
        this.visibleCountdowns.reset();
        this.aliveParticipants.clear();
        this.aliveParticipants.addAll(participants.stream().map(ServerPlayerEntity::getUuid).toList());
        this.points.clear();
        this.deathAttributions.clear();
        if (this.server != null) {
            this.vanillaTeams.pruneNamespaceTeams(this.server);
        }
        this.rebuildSoloTeams(participants);
        this.prepareMatch(participants);
        this.rebuildScoreboard();
        this.broadcast(Text.literal("Swap in " + formatDuration(this.settings.swapIntervalSeconds())).formatted(Formatting.AQUA));
    }

    @Override
    protected void onMatchEnd() {
        this.setState(GameState.ENDING);
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
        }
        for (ServerPlayerEntity participant : this.getParticipants()) {
            participant.changeGameMode(GameMode.SURVIVAL);
        }
        if (this.server != null) {
            if (this.scoreboard != null) {
                this.scoreboard.cleanup(this.server);
            }
            this.vanillaTeams.clear(this.server);
        }
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;
        if (this.getState() != GameState.RUNNING) {
            return;
        }

        this.gameTicks++;
        this.secondAccumulator++;
        this.expireAttributions();
        if (this.secondAccumulator < 20) {
            return;
        }
        this.secondAccumulator = 0;
        this.tickSwapTimer();
        this.updateScoreboardTick();
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (!this.isParticipant(player)) {
            return true;
        }
        if (!this.gameRules.pvpEnabled() && this.isParticipantAttacker(source)) {
            return false;
        }
        return true;
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayerEntity player && this.isParticipant(player)) {
            this.handleParticipantDeath(player);
        }
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (this.isParticipant(player)) {
            this.handleParticipantDeath(player);
        }
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (!this.context().roster().contains(oldPlayer.getUuid())) {
            return;
        }
        this.context().roster().add(newPlayer);
        this.respawns.handleRespawn(newPlayer);
        this.syncVanillaTeams();
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (!this.isParticipant(player)) {
            return;
        }
        UUID playerId = player.getUuid();
        this.context().roster().remove(playerId);
        this.aliveParticipants.remove(playerId);
        this.deathAttributions.remove(playerId);
        if (this.state == GameState.RUNNING) {
            this.checkEliminationEnd();
        }
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

    public boolean canStartMatch() {
        return this.context().roster().size() >= 2;
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        if (!this.isParticipant(player)) {
            this.context().roster().add(player);
        }
        if (this.state == GameState.RUNNING) {
            this.aliveParticipants.add(player.getUuid());
            this.points.putIfAbsent(player.getUuid(), 0);
            player.changeGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20.0F);
            this.rebuildSoloTeams(this.getParticipants());
            this.rebuildScoreboard();
            player.sendMessage(Text.literal("Joined Death Swap in progress. You will be included in the next swap cycle.").formatted(Formatting.GREEN), false);
        }
    }

    public int getAliveCount() {
        return this.aliveParticipants.size();
    }

    public int getSwapCount() {
        return this.swapCount;
    }

    private void prepareMatch(List<ServerPlayerEntity> participants) {
        if (this.server == null) {
            return;
        }
        this.gameRules.apply(this.server);
        for (ServerPlayerEntity participant : participants) {
            participant.changeGameMode(GameMode.SURVIVAL);
            participant.setHealth(participant.getMaxHealth());
            participant.getHungerManager().setFoodLevel(20);
            participant.getHungerManager().setSaturationLevel(20.0F);
            participant.clearStatusEffects();
            participant.extinguish();
            participant.fallDistance = 0.0F;
        }
        this.syncVanillaTeams();
    }

    private void tickSwapTimer() {
        this.refreshAliveParticipants();
        if (this.settings.respawnMode() == RespawnMode.ELIMINATION && this.aliveParticipants.size() <= 1) {
            if (this.checkProgression(this.context.roster()).blocked()) {
                return;
            }
            this.endMatch(this.aliveParticipants);
            return;
        }

        this.swapTicksRemaining = Math.max(0, this.swapTicksRemaining - 20);
        int secondsRemaining = Math.max(0, (this.swapTicksRemaining + 19) / 20);
        if (secondsRemaining == SWAP_WARNING_SECONDS && !this.warnedCurrentSwap) {
            this.warnedCurrentSwap = true;
            this.pendingAssignment = this.buildAssignment();
            this.notifyPrivateTargets(this.pendingAssignment);
        }

        this.visibleCountdowns.announceVisibleCountdown(
            this.getParticipants(),
            secondsRemaining,
            SWAP_WARNING_SECONDS,
            Text.literal("Swap"),
            SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()
        );

        if (this.swapTicksRemaining <= 0) {
            this.executeSwap();
        }
    }

    private Map<UUID, UUID> buildAssignment() {
        Set<UUID> onlineAlive = this.aliveParticipants.stream()
            .filter(uuid -> {
                ServerPlayerEntity p = this.getPlayerByUuid(uuid);
                return p != null && !p.isDisconnected();
            })
            .collect(java.util.stream.Collectors.toSet());
        return this.assignmentBuilder.assign(onlineAlive, this.recentTargets);
    }

    private void executeSwap() {
        this.refreshAliveParticipants();
        if (this.aliveParticipants.size() < 2) {
            if (this.checkProgression(this.context.roster()).blocked()) {
                this.resetSwapTimer();
                return;
            }
            this.checkEliminationEnd();
            this.resetSwapTimer();
            return;
        }

        Map<UUID, UUID> assignment = this.pendingAssignment.isEmpty() ? this.buildAssignment() : this.pendingAssignment;
        Map<UUID, PositionSnapshot> snapshots = this.captureSnapshots(assignment.values());
        int teleported = 0;
        int nextSwapId = this.swapCount + 1;

        for (Map.Entry<UUID, UUID> entry : assignment.entrySet()) {
            ServerPlayerEntity source = this.getPlayerByUuid(entry.getKey());
            PositionSnapshot target = snapshots.get(entry.getValue());
            if (source == null || target == null) {
                continue;
            }
            source.teleport(target.world(), target.x(), target.y(), target.z(), Set.<PositionFlag>of(), target.yaw(), target.pitch());
            source.fallDistance = 0.0F;
            source.setVelocity(this.settings.preserveVelocity() ? target.velocity() : Vec3d.ZERO);
            source.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
            this.rememberTarget(entry.getKey(), entry.getValue());
            this.deathAttributions.put(entry.getKey(), new SwapAttribution(entry.getValue(), this.gameTicks + DEATH_ATTRIBUTION_SECONDS * 20L, nextSwapId));
            teleported++;
        }

        this.swapCount = nextSwapId;
        this.broadcast(Text.literal("Swapped " + teleported + " player" + (teleported == 1 ? "" : "s") + ".").formatted(Formatting.AQUA));
        this.resetSwapTimer();
        this.rebuildScoreboard();
    }

    private void resetSwapTimer() {
        this.swapTicksRemaining = Math.max(1, this.settings.swapIntervalSeconds()) * 20;
        this.warnedCurrentSwap = false;
        this.pendingAssignment = Map.of();
        this.visibleCountdowns.reset();
    }

    private void handleParticipantDeath(ServerPlayerEntity player) {
        if (this.state != GameState.RUNNING) {
            return;
        }

        UUID playerId = player.getUuid();
        this.awardSwapPoint(playerId);

        if (this.settings.respawnMode() == RespawnMode.ELIMINATION) {
            if (!this.aliveParticipants.remove(playerId)) {
                return;
            }
            this.respawns.handleDeath(player, Text.literal("Eliminated from Death Swap."));
            this.broadcast(Text.literal(player.getName().getString() + " was eliminated.").formatted(Formatting.RED));
            this.checkEliminationEnd();
        }
        this.rebuildScoreboard();
    }

    private void awardSwapPoint(UUID deadPlayerId) {
        SwapAttribution attribution = this.deathAttributions.remove(deadPlayerId);
        if (attribution == null || attribution.expiresAtTick() < this.gameTicks || attribution.swapId() != this.swapCount) {
            return;
        }
        UUID scorerId = attribution.scorerId();
        if (scorerId.equals(deadPlayerId) || !this.context().roster().contains(scorerId)) {
            return;
        }
        int score = this.points.merge(scorerId, 1, Integer::sum);
        ServerPlayerEntity scorer = this.getPlayerByUuid(scorerId);
        String scorerName = scorer == null ? "A player" : scorer.getName().getString();
        this.broadcast(Text.literal(scorerName + " scored from a swap death. (" + score + "/" + this.settings.pointsToWin() + ")").formatted(Formatting.GOLD));
        if (this.settings.respawnMode() == RespawnMode.POINTS && score >= this.settings.pointsToWin()) {
            this.endMatch(Set.of(scorerId));
        }
    }

    private void checkEliminationEnd() {
        if (this.settings.respawnMode() != RespawnMode.ELIMINATION) {
            return;
        }
        this.refreshAliveParticipants();
        if (this.aliveParticipants.size() <= 1) {
            if (this.checkProgression(this.context.roster()).blocked()) {
                return;
            }
            this.endMatch(this.aliveParticipants);
        }
    }

    private void endMatch(Collection<UUID> winners) {
        if (this.state == GameState.ENDING || this.state == GameState.STOPPED || this.state == GameState.STOPPED) {
            return;
        }
        this.state = GameState.ENDING;
        this.context().setState(GameState.ENDING);
        if (this.server != null && this.scoreboard != null) {
            this.scoreboard.cleanup(this.server);
        }

        Set<UUID> winnerSet = new LinkedHashSet<>(winners == null ? Set.of() : winners);
        Text winnerLabel = Text.literal(this.winnerLabel(winnerSet));
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                runtime,
                new MatchEndResult(winnerSet, winnerLabel),
                MatchLifecycleOptions.defaults(NAME).withReturnSeconds(10)
            );
        }
    }

    private String winnerLabel(Set<UUID> winners) {
        if (winners.isEmpty()) {
            return "No winner";
        }
        if (winners.size() == 1) {
            ServerPlayerEntity winner = this.getPlayerByUuid(winners.iterator().next());
            return winner == null ? "Winner" : winner.getName().getString();
        }
        List<String> names = new ArrayList<>();
        for (UUID winnerId : winners) {
            ServerPlayerEntity winner = this.getPlayerByUuid(winnerId);
            names.add(winner == null ? winnerId.toString() : winner.getName().getString());
        }
        return String.join(", ", names);
    }

    private void notifyPrivateTargets(Map<UUID, UUID> assignment) {
        for (Map.Entry<UUID, UUID> entry : assignment.entrySet()) {
            ServerPlayerEntity player = this.getPlayerByUuid(entry.getKey());
            ServerPlayerEntity target = this.getPlayerByUuid(entry.getValue());
            if (player == null || target == null) {
                continue;
            }
            player.sendMessage(Text.literal("Your swap target: ").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)
                .append(Text.literal(target.getName().getString()).formatted(Formatting.WHITE)), false);
        }
    }


    private Map<UUID, PositionSnapshot> captureSnapshots(Collection<UUID> playerIds) {
        Map<UUID, PositionSnapshot> snapshots = new LinkedHashMap<>();
        for (UUID playerId : playerIds) {
            ServerPlayerEntity player = this.getPlayerByUuid(playerId);
            if (player == null || !(player.getEntityWorld() instanceof ServerWorld world)) {
                continue;
            }
            snapshots.put(playerId, new PositionSnapshot(world, player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch(), player.getVelocity()));
        }
        return snapshots;
    }

    private void rebuildSoloTeams(List<ServerPlayerEntity> participants) {
        this.teams.clear();
        for (ServerPlayerEntity player : participants) {
            String id = "solo_" + player.getUuid().toString().replace("-", "");
            this.teams.assign(player, id, player.getName().getString(), TeamRole.MEMBER);
        }
        this.syncVanillaTeams();
    }

    @Override
    protected void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }
        this.vanillaTeams.syncSnapshots(this.server, this.teams.snapshots(), snapshot -> {
            Formatting color = this.vanillaTeams.colorFor(snapshot.id());
            return VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(Text.literal("[" + TeamColorPalette.labelFor(snapshot.id()) + "] ").formatted(color))
                .withFriendlyFireAllowed(this.gameRules.pvpEnabled())
                .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
        });
    }

    private void rebuildScoreboard() {
        if (this.server == null) {
            return;
        }
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("Death Swap").formatted(Formatting.GOLD, Formatting.BOLD)));
            this.scoreboard.show(this.getParticipants());
        }

        this.syncVanillaTeams();
        this.scoreboard.clearLines();
        this.scoreboard.addLine(Text.literal("Alive: " + this.aliveParticipants.size()));
        this.scoreboard.addLine(Text.literal("Swaps: " + this.swapCount));
        this.nextSwapLine = this.scoreboard.addLine(Text.empty());

        if (this.settings.respawnMode() == RespawnMode.POINTS) {
            this.scoreboard.addBlankLine();
            this.scoreboard.addLine(Text.literal("Goal: " + this.settings.pointsToWin()).formatted(Formatting.YELLOW));
            for (UUID playerId : this.context().participantIds()) {
                ServerPlayerEntity player = this.getPlayerByUuid(playerId);
                String label = player == null ? playerId.toString().substring(0, 8) : player.getName().getString();
                this.scoreboard.addLine(Text.literal(label + ": " + this.points.getOrDefault(playerId, 0)));
            }
        }

        this.scoreboard.resendStructure();
        this.updateScoreboardTick();
    }

    private void updateScoreboardTick() {
        if (this.nextSwapLine != null) {
            this.nextSwapLine.setText(Text.literal("Next Swap: " + Math.max(0, this.swapTicksRemaining / 20)));
            this.nextSwapLine.updateAll();
        }
    }



    private void refreshAliveParticipants() {
        this.aliveParticipants.removeIf(playerId -> {
            ServerPlayerEntity player = this.getPlayerByUuid(playerId);
            if ((player == null || player.isDisconnected()) && this.checkProgression(this.context.roster()).blocked()) {
                return false;
            }
            return player == null || player.isDisconnected() || (this.settings.respawnMode() == RespawnMode.ELIMINATION && player.isSpectator());
        });
    }

    private void expireAttributions() {
        this.deathAttributions.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() < this.gameTicks);
    }

    private void rememberTarget(UUID source, UUID target) {
        ArrayDeque<UUID> recent = this.recentTargets.computeIfAbsent(source, ignored -> new ArrayDeque<>());
        recent.remove(target);
        recent.addFirst(target);
        while (recent.size() > RECENT_TARGET_LIMIT) {
            recent.removeLast();
        }
    }

    private boolean isParticipantAttacker(DamageSource source) {
        Entity attacker = source.getAttacker();
        return attacker instanceof ServerPlayerEntity player && this.isParticipant(player);
    }

    private boolean isParticipant(ServerPlayerEntity player) {
        return this.context().roster().contains(player);
    }

    private List<ServerPlayerEntity> getParticipants() {
        return this.context().liveParticipants();
    }

    private void broadcast(Text message) {
        GameMessenger.broadcast(this.getParticipants(), message);
    }



    private MinigameContext context() {
        if (this.context == null) {
            throw new IllegalStateException("Death Swap runtime context is not attached.");
        }
        return this.context;
    }

    private static String formatDuration(int seconds) {
        if (seconds >= 60) {
            int minutes = seconds / 60;
            int remainder = seconds % 60;
            return remainder == 0 ? minutes + "m" : minutes + "m " + remainder + "s";
        }
        return seconds + "s";
    }

    private record PositionSnapshot(ServerWorld world, double x, double y, double z, float yaw, float pitch, Vec3d velocity) {
    }

    private record SwapAttribution(UUID scorerId, long expiresAtTick, int swapId) {
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject root = new JsonObject();
        root.addProperty("state", this.state.name());
        root.addProperty("gameTicks", this.gameTicks);
        root.addProperty("secondAccumulator", this.secondAccumulator);
        root.addProperty("swapTicksRemaining", this.swapTicksRemaining);
        root.addProperty("swapCount", this.swapCount);
        root.addProperty("warnedCurrentSwap", this.warnedCurrentSwap);
        root.add("settings", this.writeSettings());
        root.add("participants", writeUuidArray(this.context == null ? Set.of() : this.context.participantIds()));
        root.add("aliveParticipants", writeUuidArray(this.aliveParticipants));
        root.add("points", writeUuidIntMap(this.points));
        root.add("recentTargets", this.writeRecentTargets());
        root.add("deathAttributions", this.writeDeathAttributions());
        root.add("pendingAssignment", writeUuidUuidMap(this.pendingAssignment));
        return root;
    }

    @Override
    public void loadRuntimeState(JsonObject root) {
        if (root == null) {
            return;
        }
        this.visibleCountdowns.reset();
        this.teams.clear();
        this.aliveParticipants.clear();
        this.points.clear();
        this.recentTargets.clear();
        this.deathAttributions.clear();
        this.pendingAssignment = Map.of();

        if (root.has("settings") && root.get("settings").isJsonObject()) {
            this.settings = readSettings(root.getAsJsonObject("settings"), this.settings);
            this.respawns = new RespawnPolicyController(this.settings.respawnMode(), this.spectators);
        }
        this.state = parseState(stringValue(root, "state", GameState.WAITING_FOR_PLAYERS.name()));
        this.context().setState(this.state);
        this.gameTicks = longValue(root, "gameTicks", 0L);
        this.secondAccumulator = intValue(root, "secondAccumulator", 0);
        this.swapTicksRemaining = intValue(root, "swapTicksRemaining", Math.max(1, this.settings.swapIntervalSeconds()) * 20);
        this.swapCount = intValue(root, "swapCount", 0);
        this.warnedCurrentSwap = booleanValue(root, "warnedCurrentSwap", false);
        for (UUID playerId : readUuidArray(root, "participants")) {
            this.context().roster().add(playerId);
        }
        this.aliveParticipants.addAll(readUuidArray(root, "aliveParticipants"));
        this.points.putAll(readUuidIntMap(root, "points"));
        this.readRecentTargets(root);
        this.readDeathAttributions(root);
        this.pendingAssignment = readUuidUuidMap(root, "pendingAssignment");
        this.rebuildScoreboard();
    }

    private JsonObject writeSettings() {
        JsonObject object = new JsonObject();
        object.addProperty("swapIntervalSeconds", this.settings.swapIntervalSeconds());
        object.addProperty("initialGracePeriodSeconds", this.settings.initialGracePeriodSeconds());
        object.addProperty("borderSize", this.settings.borderSize());
        object.addProperty("seedMode", this.settings.seedMode().name());
        object.addProperty("seed", this.settings.seed());
        object.addProperty("respawnMode", this.settings.respawnMode().name());
        object.addProperty("pointsToWin", this.settings.pointsToWin());
        object.addProperty("preserveVelocity", this.settings.preserveVelocity());
        return object;
    }

    private static DeathSwapSettings readSettings(JsonObject root, DeathSwapSettings fallback) {
        DeathSwapSettings base = fallback == null ? DeathSwapSettings.defaults() : fallback;
        DeathSwapSettings.SeedMode seedMode;
        try {
            seedMode = DeathSwapSettings.SeedMode.valueOf(stringValue(root, "seedMode", base.seedMode().name()));
        } catch (IllegalArgumentException ignored) {
            seedMode = base.seedMode();
        }
        RespawnMode respawnMode = RespawnMode.parse(stringValue(root, "respawnMode", base.respawnMode().configValue()), base.respawnMode());
        return new DeathSwapSettings(
            intValue(root, "swapIntervalSeconds", base.swapIntervalSeconds()),
            intValue(root, "initialGracePeriodSeconds", base.initialGracePeriodSeconds()),
            intValue(root, "borderSize", base.borderSize()),
            seedMode,
            longValue(root, "seed", base.seed()),
            respawnMode,
            intValue(root, "pointsToWin", base.pointsToWin()),
            booleanValue(root, "preserveVelocity", base.preserveVelocity()),
            base.teams()
        );
    }

    private JsonArray writeRecentTargets() {
        JsonArray array = new JsonArray();
        for (Map.Entry<UUID, ArrayDeque<UUID>> entry : this.recentTargets.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", entry.getKey().toString());
            object.add("targets", writeUuidArray(entry.getValue()));
            array.add(object);
        }
        return array;
    }

    private void readRecentTargets(JsonObject root) {
        if (!root.has("recentTargets") || !root.get("recentTargets").isJsonArray()) {
            return;
        }
        for (var element : root.getAsJsonArray("recentTargets")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            UUID uuid = uuidValue(object, "uuid");
            if (uuid != null) {
                this.recentTargets.put(uuid, new ArrayDeque<>(readUuidArray(object, "targets")));
            }
        }
    }

    private JsonArray writeDeathAttributions() {
        JsonArray array = new JsonArray();
        for (Map.Entry<UUID, SwapAttribution> entry : this.deathAttributions.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("uuid", entry.getKey().toString());
            object.addProperty("scorerId", entry.getValue().scorerId().toString());
            object.addProperty("expiresAtTick", entry.getValue().expiresAtTick());
            object.addProperty("swapId", entry.getValue().swapId());
            array.add(object);
        }
        return array;
    }

    private void readDeathAttributions(JsonObject root) {
        if (!root.has("deathAttributions") || !root.get("deathAttributions").isJsonArray()) {
            return;
        }
        for (var element : root.getAsJsonArray("deathAttributions")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            UUID uuid = uuidValue(object, "uuid");
            UUID scorerId = uuidValue(object, "scorerId");
            if (uuid != null && scorerId != null) {
                this.deathAttributions.put(uuid, new SwapAttribution(
                    scorerId,
                    longValue(object, "expiresAtTick", 0L),
                    intValue(object, "swapId", 0)
                ));
            }
        }
    }

    private static JsonArray writeUuidArray(Collection<UUID> uuids) {
        JsonArray array = new JsonArray();
        for (UUID uuid : uuids) {
            array.add(uuid.toString());
        }
        return array;
    }

    private static List<UUID> readUuidArray(JsonObject root, String key) {
        List<UUID> uuids = new ArrayList<>();
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return uuids;
        }
        for (var element : root.getAsJsonArray(key)) {
            try {
                uuids.add(UUID.fromString(element.getAsString()));
            } catch (RuntimeException ignored) {
            }
        }
        return uuids;
    }

    private static JsonObject writeUuidUuidMap(Map<UUID, UUID> map) {
        JsonObject object = new JsonObject();
        map.forEach((key, value) -> object.addProperty(key.toString(), value.toString()));
        return object;
    }

    private static Map<UUID, UUID> readUuidUuidMap(JsonObject root, String key) {
        Map<UUID, UUID> map = new LinkedHashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return map;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
            try {
                map.put(UUID.fromString(entry.getKey()), UUID.fromString(entry.getValue().getAsString()));
            } catch (RuntimeException ignored) {
            }
        }
        return map;
    }

    private static JsonObject writeUuidIntMap(Map<UUID, Integer> map) {
        JsonObject object = new JsonObject();
        map.forEach((key, value) -> object.addProperty(key.toString(), value));
        return object;
    }

    private static Map<UUID, Integer> readUuidIntMap(JsonObject root, String key) {
        Map<UUID, Integer> map = new ConcurrentHashMap<>();
        if (!root.has(key) || !root.get(key).isJsonObject()) {
            return map;
        }
        for (Map.Entry<String, com.google.gson.JsonElement> entry : root.getAsJsonObject(key).entrySet()) {
            try {
                map.put(UUID.fromString(entry.getKey()), entry.getValue().getAsInt());
            } catch (RuntimeException ignored) {
            }
        }
        return map;
    }

    private static GameState parseState(String value) {
        try {
            return GameState.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return GameState.WAITING_FOR_PLAYERS;
        }
    }

    @Nullable
    private static UUID uuidValue(JsonObject object, String key) {
        String value = stringValue(object, key, "");
        if (value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String stringValue(JsonObject object, String key, String fallback) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int intValue(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsLong() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        int onlineCount = roster.onlinePlayers(this.context != null ? this.context.nullableServer() : null).size();
        if (onlineCount < 2) {
            return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(true, null, net.minecraft.text.Text.literal("Waiting for more players to reconnect to resume swapping...").formatted(net.minecraft.util.Formatting.RED));
        }
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }
}
