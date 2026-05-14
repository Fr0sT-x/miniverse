package dev.frost.miniverse.minigame.impl.deathswap;

import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.ScoreboardController;
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

public class DeathSwapMinigame implements Minigame, RuntimeContextAware, ServerTickAware, PlayerDamageAware, EntityDeathAware, PlayerRespawnAware, PlayerLeaveAware, TeamManagerProvider {
    private static final String NAME = "Death Swap";
    private static final String SCOREBOARD_OBJECTIVE = "deathswap_display";
    private static final int DEATH_ATTRIBUTION_SECONDS = 90;
    private static final int SWAP_WARNING_SECONDS = 10;
    private static final int RECENT_TARGET_LIMIT = 3;
    private static final ScoreboardController SCOREBOARD = new ScoreboardController(SCOREBOARD_OBJECTIVE, Text.literal("Death Swap"));

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
    private MinigameContext context;
    @Nullable
    private MinecraftServer server;
    private long gameTicks;
    private int secondAccumulator;
    private int swapTicksRemaining;
    private int swapCount;
    private boolean warnedCurrentSwap;

    @Override
    public void attachContext(MinigameContext context) {
        this.context = context;
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
    public void startGame() {
        List<ServerPlayerEntity> participants = this.getParticipants();
        if (participants.size() < 2) {
            this.broadcast(Text.literal("Need at least two players to start Death Swap.").formatted(Formatting.RED));
            return;
        }

        this.state = GameState.RUNNING;
        this.context().setState(GameState.RUNNING);
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
        this.updateScoreboard();
        this.broadcast(Text.literal("Swap in " + formatDuration(this.settings.swapIntervalSeconds())).formatted(Formatting.AQUA));
    }

    @Override
    public void stopGame() {
        this.state = GameState.ENDING;
        this.context().setState(GameState.ENDING);
        for (ServerPlayerEntity participant : this.getParticipants()) {
            participant.changeGameMode(GameMode.SURVIVAL);
        }
        this.clearScoreboard();
        if (this.server != null) {
            this.vanillaTeams.clear(this.server);
        }
        this.context().participants().clear();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        this.server = server;
        if (this.state != GameState.RUNNING) {
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
        this.updateScoreboard();
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (!this.isParticipant(player)) {
            return true;
        }
        if (!this.settings.pvpEnabled() && this.isParticipantAttacker(source)) {
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
        if (!this.context().participants().contains(oldPlayer.getUuid())) {
            return;
        }
        this.context().participants().add(newPlayer);
        this.respawns.handleRespawn(newPlayer);
        this.syncVanillaTeams();
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (!this.isParticipant(player)) {
            return;
        }
        UUID playerId = player.getUuid();
        this.context().participants().remove(playerId);
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
        return this.context().participants().size() >= 2;
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
        new GlobalMatchRules(this.settings.keepInventory(), this.settings.pvpEnabled()).apply(this.server);
        for (ServerWorld world : this.server.getWorlds()) {
            world.getWorldBorder().setCenter(0.5D, 0.5D);
            world.getWorldBorder().setSize(this.settings.borderSize());
        }
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
        return this.assignmentBuilder.assign(this.aliveParticipants, this.recentTargets);
    }

    private void executeSwap() {
        this.refreshAliveParticipants();
        if (this.aliveParticipants.size() < 2) {
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
            source.teleport(target.world(), target.x(), target.y(), target.z(), Set.of(), target.yaw(), target.pitch(), true);
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
        this.updateScoreboard();
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
        this.updateScoreboard();
    }

    private void awardSwapPoint(UUID deadPlayerId) {
        SwapAttribution attribution = this.deathAttributions.remove(deadPlayerId);
        if (attribution == null || attribution.expiresAtTick() < this.gameTicks || attribution.swapId() != this.swapCount) {
            return;
        }
        UUID scorerId = attribution.scorerId();
        if (scorerId.equals(deadPlayerId) || !this.context().participants().contains(scorerId)) {
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
            this.endMatch(this.aliveParticipants);
        }
    }

    private void endMatch(Collection<UUID> winners) {
        if (this.state == GameState.ENDING || this.state == GameState.RETURNING || this.state == GameState.FINISHED) {
            return;
        }
        this.state = GameState.ENDING;
        this.context().setState(GameState.ENDING);
        this.clearScoreboard();

        Set<UUID> winnerSet = new LinkedHashSet<>(winners == null ? Set.of() : winners);
        Text winnerLabel = Text.literal(this.winnerLabel(winnerSet));
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            MatchLifecycleController.getInstance().endMatch(
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

    private void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }
        this.vanillaTeams.syncSnapshots(this.server, this.teams.snapshots(), snapshot -> {
            Formatting color = this.vanillaTeams.colorFor(snapshot.id());
            return VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(Text.literal("[" + TeamColorPalette.labelFor(snapshot.id()) + "] ").formatted(color))
                .withFriendlyFireAllowed(this.settings.pvpEnabled())
                .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
        });
    }

    private void updateScoreboard() {
        if (this.server == null) {
            return;
        }
        this.syncVanillaTeams();
        SCOREBOARD.setScore(this.server, "Alive", this.aliveParticipants.size());
        SCOREBOARD.setScore(this.server, "Swaps", this.swapCount);
        SCOREBOARD.setScore(this.server, "Next Swap", Math.max(0, this.swapTicksRemaining / 20));
        if (this.settings.respawnMode() == RespawnMode.POINTS) {
            SCOREBOARD.setScore(this.server, "Points To Win", this.settings.pointsToWin());
            for (UUID playerId : this.context().participantIds()) {
                ServerPlayerEntity player = this.getPlayerByUuid(playerId);
                String label = player == null ? playerId.toString().substring(0, 8) : player.getName().getString();
                SCOREBOARD.setScore(this.server, label, this.points.getOrDefault(playerId, 0));
            }
        }
    }

    private void clearScoreboard() {
        if (this.server != null) {
            SCOREBOARD.clear(this.server);
            this.vanillaTeams.clear(this.server);
        }
    }

    private void refreshAliveParticipants() {
        this.aliveParticipants.removeIf(playerId -> {
            ServerPlayerEntity player = this.getPlayerByUuid(playerId);
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
        return this.context().participants().contains(player);
    }

    private List<ServerPlayerEntity> getParticipants() {
        return this.context().liveParticipants();
    }

    private void broadcast(Text message) {
        GameMessenger.broadcast(this.getParticipants(), message);
    }

    @Nullable
    private ServerPlayerEntity getPlayerByUuid(UUID playerId) {
        Optional<ServerPlayerEntity> participant = this.context().resolvePlayer(playerId);
        if (participant.isPresent()) {
            return participant.get();
        }
        return this.server == null ? null : this.server.getPlayerManager().getPlayer(playerId);
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
}
