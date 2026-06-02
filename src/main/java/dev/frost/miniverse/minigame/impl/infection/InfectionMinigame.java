package dev.frost.miniverse.minigame.impl.infection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.ScoreboardController;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamRole;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InfectionMinigame implements Minigame, RuntimeContextAware, ServerTickAware, EntityDeathAware, PlayerRespawnAware, PlayerDamageAware, PlayerLeaveAware, TeamManagerProvider, PersistentMinigame {
    private static final String NAME = "Infection";
    private static final String SURVIVOR_TEAM = "survivor";
    private static final String INFECTED_TEAM = "infected";
    private static final ScoreboardController SCOREBOARD = new ScoreboardController("infection_display", Text.literal("Infection"));

    private final TeamManager teams = new TeamManager();
    private final Set<UUID> survivors = ConcurrentHashMap.newKeySet();
    private final Set<UUID> infected = ConcurrentHashMap.newKeySet();
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("infection");

    private InfectionSettings settings = InfectionSettings.defaults();
    private InfectionMapConfig mapConfig = new InfectionMapConfig(List.of());
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    @Nullable
    private MinigameContext context;
    @Nullable
    private MinecraftServer server;
    private int ticksRemaining;
    private int secondAccumulator;

    @Override
    public void attachContext(MinigameContext context) {
        this.context = context;
    }

    public void applySettings(InfectionSettings settings, InfectionMapConfig mapConfig) {
        this.settings = settings == null ? InfectionSettings.defaults() : settings;
        this.mapConfig = mapConfig == null ? new InfectionMapConfig(List.of()) : mapConfig;
    }

    public boolean canStartMatch() {
        return this.context != null && this.context.participants().size() >= 2 && this.mapConfig.validate().valid();
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.server = null;
        this.survivors.clear();
        this.infected.clear();
        this.teams.clear();
        this.ticksRemaining = 0;
        this.secondAccumulator = 0;
    }

    @Override
    public void startGame() {
        List<ServerPlayerEntity> participants = this.participants();
        if (participants.size() < 2) {
            this.broadcast(Text.literal("Need at least two players to start Infection.").formatted(Formatting.RED));
            return;
        }
        if (!this.mapConfig.validate().valid()) {
            this.broadcast(Text.literal("Selected map is not valid for Infection.").formatted(Formatting.RED));
            return;
        }

        this.state = GameState.RUNNING;
        this.context().setState(GameState.RUNNING);
        this.ticksRemaining = Math.max(1, this.settings.matchDurationSeconds()) * 20;
        this.secondAccumulator = 0;
        this.survivors.clear();
        this.infected.clear();
        this.teams.clear();

        Collections.shuffle(participants);
        int infectedCount = Math.min(Math.max(1, this.settings.startingInfectedCount()), participants.size() - 1);
        for (int i = 0; i < participants.size(); i++) {
            ServerPlayerEntity player = participants.get(i);
            player.changeGameMode(GameMode.SURVIVAL);
            if (i < infectedCount) {
                this.markInfected(player);
            } else {
                this.markSurvivor(player);
            }
        }

        this.teleportToMapSpawns(participants);
        this.syncVanillaTeams();
        this.updateScoreboard();
        this.broadcast(Text.literal("Infection has begun. Survivors must last " + formatTime(this.settings.matchDurationSeconds()) + ".").formatted(Formatting.GOLD));
    }

    @Override
    public void stopGame() {
        this.state = GameState.ENDING;
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
            this.context.participants().clear();
        }
        if (this.server != null) {
            SCOREBOARD.clear(this.server);
            this.vanillaTeams.clear(this.server);
        }
        this.survivors.clear();
        this.infected.clear();
        this.teams.clear();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        this.server = server;
        if (this.state != GameState.RUNNING) {
            return;
        }
        this.ticksRemaining = Math.max(0, this.ticksRemaining - 1);
        this.secondAccumulator++;
        if (this.secondAccumulator >= 20) {
            this.secondAccumulator = 0;
            this.updateScoreboard();
            if (this.ticksRemaining <= 0) {
                this.endSurvivorWin();
            }
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity victim) || !this.isParticipant(victim)) {
            return;
        }
        ServerPlayerEntity attacker = source.getAttacker() instanceof ServerPlayerEntity player ? player : null;
        if (attacker != null && this.infected.contains(attacker.getUuid()) && this.survivors.contains(victim.getUuid())) {
            this.infect(victim);
        }
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (this.isParticipant(player) && this.survivors.contains(player.getUuid())) {
            this.infect(player);
        }
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (!this.context().participants().contains(oldPlayer.getUuid())) {
            return;
        }
        this.context().participants().remove(oldPlayer.getUuid());
        this.context().participants().add(newPlayer);
        if (this.infected.contains(oldPlayer.getUuid())) {
            this.infected.remove(oldPlayer.getUuid());
            this.markInfected(newPlayer);
        } else if (this.survivors.contains(oldPlayer.getUuid())) {
            this.survivors.remove(oldPlayer.getUuid());
            this.markSurvivor(newPlayer);
        }
        this.teleportToRandomSpawn(newPlayer);
        this.syncVanillaTeams();
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (!this.isParticipant(player) || this.settings.allowFriendlyFire()) {
            return true;
        }
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker) || !this.isParticipant(attacker)) {
            return true;
        }
        return !(this.infected.contains(player.getUuid()) && this.infected.contains(attacker.getUuid()))
            && !(this.survivors.contains(player.getUuid()) && this.survivors.contains(attacker.getUuid()));
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (!this.isParticipant(player)) {
            return;
        }
        this.survivors.remove(player.getUuid());
        this.infected.remove(player.getUuid());
        if (this.state == GameState.RUNNING) {
            this.checkInfectedWin();
        }
    }

    @Override
    public TeamManager teamManager() {
        return this.teams;
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject json = new JsonObject();
        json.addProperty("state", this.state.name());
        json.addProperty("ticksRemaining", this.ticksRemaining);
        JsonArray survivorArray = new JsonArray();
        for (UUID survivor : this.survivors) {
            survivorArray.add(survivor.toString());
        }
        JsonArray infectedArray = new JsonArray();
        for (UUID infectedPlayer : this.infected) {
            infectedArray.add(infectedPlayer.toString());
        }
        json.add("survivors", survivorArray);
        json.add("infected", infectedArray);
        return json;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        this.state = GameState.valueOf(state.has("state") ? state.get("state").getAsString() : GameState.WAITING_FOR_PLAYERS.name());
        this.ticksRemaining = state.has("ticksRemaining") ? state.get("ticksRemaining").getAsInt() : 0;
        this.survivors.clear();
        this.infected.clear();
        if (state.has("survivors") && state.get("survivors").isJsonArray()) {
            state.getAsJsonArray("survivors").forEach(element -> this.survivors.add(UUID.fromString(element.getAsString())));
        }
        if (state.has("infected") && state.get("infected").isJsonArray()) {
            state.getAsJsonArray("infected").forEach(element -> this.infected.add(UUID.fromString(element.getAsString())));
        }
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
        this.state = state == null ? GameState.WAITING_FOR_PLAYERS : state;
    }

    private void infect(ServerPlayerEntity player) {
        if (!this.survivors.remove(player.getUuid())) {
            return;
        }
        this.markInfected(player);
        this.syncVanillaTeams();
        this.updateScoreboard();
        this.broadcast(Text.literal(player.getName().getString() + " was infected.").formatted(Formatting.RED));
        this.checkInfectedWin();
    }

    private void checkInfectedWin() {
        if (this.state == GameState.RUNNING && this.survivors.isEmpty()) {
            this.endInfectedWin();
        }
    }

    private void markSurvivor(ServerPlayerEntity player) {
        this.infected.remove(player.getUuid());
        this.survivors.add(player.getUuid());
        this.teams.assign(player, SURVIVOR_TEAM, "Survivors", TeamRole.MEMBER);
        player.sendMessage(Text.literal("You are a Survivor. Stay alive until time expires.").formatted(Formatting.GREEN), false);
    }

    private void markInfected(ServerPlayerEntity player) {
        this.survivors.remove(player.getUuid());
        this.infected.add(player.getUuid());
        this.teams.assign(player, INFECTED_TEAM, "Infected", TeamRole.MEMBER);
        player.sendMessage(Text.literal("You are Infected. Convert every survivor.").formatted(Formatting.RED), false);
    }

    private void endSurvivorWin() {
        this.startEndSequence(this.livePlayers(this.survivors), Text.literal("Survivors").formatted(Formatting.GREEN));
    }

    private void endInfectedWin() {
        this.startEndSequence(this.livePlayers(this.infected), Text.literal("Infected").formatted(Formatting.RED));
    }

    private void startEndSequence(List<ServerPlayerEntity> winners, Text winnerLabel) {
        this.state = GameState.ENDING;
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null) {
            return;
        }
        MatchLifecycleController.getInstance().endMatch(
            runtime,
            MatchEndResult.winners(winners, winnerLabel),
            MatchLifecycleOptions.defaults(NAME)
        );
    }

    private void teleportToMapSpawns(List<ServerPlayerEntity> players) {
        List<MapPosition> spawns = this.mapConfig.spawnPoints();
        if (spawns.isEmpty()) {
            return;
        }
        for (int i = 0; i < players.size(); i++) {
            this.teleport(players.get(i), spawns.get(i % spawns.size()));
        }
    }

    private void teleportToRandomSpawn(ServerPlayerEntity player) {
        List<MapPosition> spawns = this.mapConfig.spawnPoints();
        if (spawns.isEmpty()) {
            return;
        }
        this.teleport(player, spawns.get(Math.floorMod(player.getUuid().hashCode(), spawns.size())));
    }

    private void teleport(ServerPlayerEntity player, MapPosition spawn) {
        ServerWorld world = player.getServerWorld();
        player.teleport(world, spawn.x(), spawn.y(), spawn.z(), Set.<PositionFlag>of(), spawn.yaw(), spawn.pitch());
    }

    private void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }
        this.vanillaTeams.syncSnapshots(this.server, this.teams.snapshots(), snapshot -> {
            Formatting color = INFECTED_TEAM.equals(snapshot.id()) ? Formatting.RED : Formatting.GREEN;
            return VanillaTeamOptions.defaults()
                .withColor(color)
                .withFriendlyFireAllowed(this.settings.allowFriendlyFire());
        });
    }

    private void updateScoreboard() {
        if (this.server == null) {
            return;
        }
        SCOREBOARD.setScore(this.server, "Survivors", this.survivors.size());
        SCOREBOARD.setScore(this.server, "Infected", this.infected.size());
        SCOREBOARD.setScore(this.server, "Time", Math.max(0, this.ticksRemaining / 20));
    }

    private List<ServerPlayerEntity> participants() {
        return this.context().liveParticipants();
    }

    private List<ServerPlayerEntity> livePlayers(Set<UUID> ids) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            this.context().resolvePlayer(id).ifPresent(players::add);
        }
        return players;
    }

    private boolean isParticipant(ServerPlayerEntity player) {
        return this.context != null && this.context.participants().contains(player);
    }

    private void broadcast(Text message) {
        for (ServerPlayerEntity player : this.participants()) {
            player.sendMessage(message, false);
        }
    }

    private MinigameContext context() {
        if (this.context == null) {
            throw new IllegalStateException("Infection context is not attached.");
        }
        return this.context;
    }

    private static String formatTime(int seconds) {
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
