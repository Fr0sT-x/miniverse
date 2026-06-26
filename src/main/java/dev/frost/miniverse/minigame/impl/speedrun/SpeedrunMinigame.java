package dev.frost.miniverse.minigame.impl.speedrun;

import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.PauseAwareMinigame;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.minigame.core.util.StandardEndSequence;
import dev.frost.miniverse.team.TeamMembership;
import dev.frost.miniverse.team.TeamRole;
import dev.frost.miniverse.team.TeamSnapshot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;
import dev.frost.miniverse.minigame.impl.speedrun.death.SpeedrunDeathLifecycleConfig;

/**
 * MCSR-style speedrun session implementation.
 *
 * This is a lightweight server-side MVP: runners, a live timer,
 * and Ender Dragon defeat as the completion condition.
 */
public class SpeedrunMinigame extends AbstractMinigame implements ServerTickAware, EntityDeathAware, PlayerRespawnAware, PlayerLeaveAware, DynamicParticipantMinigame, PauseAwareMinigame, DeathAwareMinigame, dev.frost.miniverse.minigame.core.event.RosterAware {
    private static final String NAME = "Speedrun";
    private static final int TICKS_PER_SECOND = 20;
    private ScoreboardTemplate scoreboard;
    private ScoreboardLine playersLine;
    private ScoreboardLine timeLine;
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("speedrun");

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private boolean paused = false;
    private int elapsedTicks;
    private int tickCounter;
    private int timeLimitTicks = 0;
    @Nullable
    private MinecraftServer server;
    private DeathLifecycleManager deathLifecycleManager;
    private final Set<UUID> initializedPlayers = new java.util.HashSet<>();
    private final java.util.Map<UUID, ServerPlayerEntity> knownRoster = new java.util.HashMap<>();
    private boolean needsScoreboardRebuild = false;

    public void setTimeLimitTicks(int ticks) {
        this.timeLimitTicks = ticks;
    }

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }

    public SpeedrunMinigame() {
        this.vanillaTeams.setFriendlyFireAllowed(true);
        this.vanillaTeams.setTeammateCollisionAllowed(false);
    }

    public void setVanillaFriendlyFireAllowed(boolean allowed) {
        this.vanillaTeams.setFriendlyFireAllowed(allowed);
        this.syncVanillaTeams();
    }

    public void setVanillaTeammateCollisionAllowed(boolean allowed) {
        this.vanillaTeams.setTeammateCollisionAllowed(allowed);
        this.syncVanillaTeams();
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(net.minecraft.world.GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_IMMEDIATE_RESPAWN, false);
        this.setState(GameState.WAITING_FOR_PLAYERS);
        this.elapsedTicks = 0;
        this.tickCounter = 0;
        this.timeLimitTicks = 0;
        this.server = null;
        this.paused = false;
        this.initializedPlayers.clear();
        this.knownRoster.clear();
        this.needsScoreboardRebuild = false;
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        return GlobalMatchRules.defaults();
    }

    @Override
    protected boolean isTeamBased() {
        return false;
    }

    @Override
    protected void onMatchStart() {
        if (this.getState() == GameState.RUNNING) {
            return;
        }

        if (this.getParticipants().isEmpty()) {
            this.broadcastMessage(Text.literal("Cannot start Speedrun: no participants.").formatted(Formatting.RED));
            return;
        }

        this.deathLifecycleManager = new DeathLifecycleManager(
            new SpeedrunDeathLifecycleConfig(),
            dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance()
        );

        this.setState(GameState.RUNNING);
        this.setRuntimeState(GameState.RUNNING);
        this.elapsedTicks = 0;
        this.tickCounter = 0;

        for (ServerPlayerEntity p : this.getParticipants()) {
            this.knownRoster.put(p.getUuid(), p);
        }

        this.prepareParticipantsForRun();

        this.broadcastMessage(Text.literal("✓ Speedrun started!").formatted(Formatting.GREEN));
        this.rebuildScoreboard();
    }

    @Override
    protected void onMatchEnd() {
        this.deathLifecycleManager.handleMatchEnding(this::getPlayerByUuid);
        this.setState(GameState.ENDING);
        this.setRuntimeState(GameState.ENDING);

        this.restoreParticipants();
        this.clearVanillaTeams();

        this.deathLifecycleManager = null;
        this.elapsedTicks = 0;
        this.tickCounter = 0;
        this.server = null;
        this.initializedPlayers.clear();
        this.knownRoster.clear();

        if (this.context != null) {
            this.context.roster().clear();
        }
    }

    public void handleDragonDeath() {
        if (this.getState() != GameState.RUNNING) {
            return;
        }

        this.endGameWithVictory(Text.literal("The Ender Dragon has been defeated!").formatted(Formatting.GOLD));
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;
        
        if (this.needsScoreboardRebuild) {
            this.rebuildScoreboard();
            this.needsScoreboardRebuild = false;
        }

        if (this.getState() == GameState.ENDING) {
            return;
        }

        if (this.getState() == GameState.RUNNING && !this.paused) {
            this.elapsedTicks++;
            this.tickCounter++;

            if (this.timeLimitTicks > 0 && this.elapsedTicks >= this.timeLimitTicks) {
                this.endGameWithVictory(Text.literal("Time's Up!").formatted(Formatting.RED));
                return;
            }

            if (this.tickCounter >= TICKS_PER_SECOND) {
                this.tickCounter = 0;
                this.updateScoreboardTick();
            }
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof EnderDragonEntity) {
            this.handleDragonDeath();
            return;
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
        // Disconnect cleanup is handled in onRosterChanged
    }

    @Override
    public void onRosterChanged(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        if (this.server == null) {
            return;
        }

        boolean rosterChanged = false;

        List<UUID> disconnected = new ArrayList<>();
        for (java.util.Map.Entry<UUID, ServerPlayerEntity> entry : this.knownRoster.entrySet()) {
            if (!roster.contains(entry.getKey())) {
                disconnected.add(entry.getKey());
            }
        }
        
        for (UUID uuid : disconnected) {
            ServerPlayerEntity p = this.knownRoster.remove(uuid);
            if (p != null && this.deathLifecycleManager != null) {
                this.deathLifecycleManager.handleDisconnect(p);
            }
            rosterChanged = true;
        }

        for (ServerPlayerEntity p : roster.onlinePlayers(this.server)) {
            if (!this.knownRoster.containsKey(p.getUuid())) {
                this.knownRoster.put(p.getUuid(), p);
                if (this.getState() == GameState.RUNNING) {
                    this.prepareRunnerForRun(p);
                }
                rosterChanged = true;
            }
        }

        this.syncVanillaTeams();
        if (rosterChanged && this.scoreboard != null && this.getState() != GameState.WAITING_FOR_PLAYERS) {
            this.rebuildScoreboard();
        } else if (this.scoreboard != null) {
            this.updateScoreboardTick();
        }
    }

    public void syncLateParticipant(ServerPlayerEntity player) {
        if (dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance().isSpectating(player.getUuid())) {
            dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance().stopSpectating(
                player, dev.frost.miniverse.minigame.core.spectator.SpectatorStopReason.MANUAL
            );
        }
        // Instead of manually managing roster here, we let the framework's addParticipant 
        // trigger onRosterChanged which handles everything gracefully!
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().addParticipant(player);
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        this.syncLateParticipant(player);
        if (this.getState() == GameState.RUNNING) {
            player.sendMessage(Text.literal("Joined Speedrun in progress.").formatted(Formatting.GREEN), false);
        }
    }

    public int getElapsedTicks() {
        return this.elapsedTicks;
    }

    public String getFormattedTime() {
        int totalSeconds = this.elapsedTicks / TICKS_PER_SECOND;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public boolean canStartRun() {
        return !this.getParticipants().isEmpty();
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (!this.isParticipant(oldPlayer)) {
            return;
        }

        this.replaceParticipant(oldPlayer, newPlayer);
        this.knownRoster.remove(oldPlayer.getUuid());
        this.knownRoster.put(newPlayer.getUuid(), newPlayer);

        if (this.getState() == GameState.RUNNING) {
            this.syncParticipantModes();
        }
    }

    private void prepareParticipantsForRun() {
        List<ServerPlayerEntity> participants = this.getParticipants();
        for (ServerPlayerEntity participant : participants) {
            this.prepareRunnerForRun(participant);
        }
    }

    private void syncParticipantModes() {
        for (ServerPlayerEntity participant : this.getParticipants()) {
            participant.changeGameMode(GameMode.SURVIVAL);
        }
    }

    private void restoreParticipants() {
        for (ServerPlayerEntity participant : new ArrayList<>(this.getParticipants())) {
            participant.changeGameMode(GameMode.SURVIVAL);
        }
    }



    private void prepareRunnerForRun(ServerPlayerEntity player) {
        if (this.initializedPlayers.add(player.getUuid())) {
            player.changeGameMode(GameMode.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20.0F);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 40, 0, true, false, false));
        }
    }

    private void endGameWithVictory(Text reason) {
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("🏆 SPEEDRUN COMPLETE! 🏆").formatted(Formatting.GOLD));
        this.broadcastMessage(reason.copy().formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("Time: " + this.getFormattedTime()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));

        List<ServerPlayerEntity> runners = this.getParticipants();
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getStandardEndSequence().start(NAME, runners.isEmpty()
            ? new MatchEndResult(Set.of(), Text.literal("No winner"))
            : MatchEndResult.winners(runners, Text.literal("Runners")));
    }



    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(this.getParticipants(), message);
    }

    private List<ServerPlayerEntity> getParticipants() {
        return this.context != null ? this.context.liveParticipants() : List.of();
    }

    private int getParticipantCount() {
        return this.context != null ? this.context.roster().size() : 0;
    }

    private boolean isParticipant(ServerPlayerEntity player) {
        return this.context != null && this.context.roster().contains(player);
    }

    private void addParticipant(ServerPlayerEntity player) {
        if (this.context != null) this.context.roster().add(player);
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

    private void rebuildScoreboard() {
        if (this.server == null || this.getState() == GameState.WAITING_FOR_PLAYERS) {
            return;
        }
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("Speedrun").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)));
        }

        for (ServerPlayerEntity p : this.getParticipants()) {
            if (!this.scoreboard.isViewing(p)) {
                this.scoreboard.show(p);
            }
        }

        this.syncVanillaTeams();
        this.scoreboard.clearLines();
        
        List<ServerPlayerEntity> participants = this.getParticipants();
        this.scoreboard.addLine(Text.literal("Players: " + participants.size()));
        
        for (ServerPlayerEntity p : participants) {
            this.scoreboard.addLine(Text.literal(" - " + p.getName().getString()).formatted(Formatting.GRAY));
        }
        
        this.scoreboard.addBlankLine();
        this.timeLine = this.scoreboard.addLine(Text.empty());
        this.scoreboard.resendStructure();
        this.updateScoreboardTick();
    }

    private void updateScoreboardTick() {
        if (this.timeLine != null) {
            this.timeLine.setText(Text.literal("Time: " + (this.getState() == GameState.RUNNING ? this.getFormattedTime() : "00:00:00")));
            this.timeLine.updateAll();
        }
    }



    @Override
    protected void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }

        List<ServerPlayerEntity> runners = this.getParticipants();

        VanillaTeamOptions runnerOptions = VanillaTeamOptions.defaults()
            .withFriendlyFireAllowed(true)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);

        List<TeamSnapshot> snapshots = List.of(
            new TeamSnapshot("runner", "Runner", runners.stream().map(player -> TeamMembership.of(player, TeamRole.RUNNER)).toList())
        );
        this.vanillaTeams.syncSnapshots(this.server, snapshots, snapshot -> switch (snapshot.id()) {
            case "runner" -> runnerOptions;
            default -> VanillaTeamOptions.defaults();
        });
    }

    @Override
    protected void clearVanillaTeams() {
        if (this.server != null) {
            this.vanillaTeams.clear(this.server);
        }
    }

    private void showGameOverTitle(Text title) {
        GameMessenger.showGameOverTitle(this.getParticipants(), title);
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
        if (state == GameState.FROZEN) {
            this.rebuildScoreboard();
        }
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
    public com.google.gson.JsonObject saveRuntimeState() {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("state", this.state.name());
        json.addProperty("paused", this.paused);
        json.addProperty("elapsedTicks", this.elapsedTicks);
        json.addProperty("timeLimitTicks", this.timeLimitTicks);
        com.google.gson.JsonArray initializedArray = new com.google.gson.JsonArray();
        for (UUID uuid : this.initializedPlayers) {
            initializedArray.add(uuid.toString());
        }
        json.add("initializedPlayers", initializedArray);
        return json;
    }

    @Override
    public void loadRuntimeState(com.google.gson.JsonObject json) {
        if (json == null) return;
        if (json.has("state")) {
            try {
                this.state = GameState.valueOf(json.get("state").getAsString());
            } catch (Exception ignored) {}
        }
        if (json.has("paused")) {
            this.paused = json.get("paused").getAsBoolean();
        }
        if (json.has("elapsedTicks")) {
            this.elapsedTicks = json.get("elapsedTicks").getAsInt();
        }
        if (json.has("timeLimitTicks")) {
            this.timeLimitTicks = json.get("timeLimitTicks").getAsInt();
        }
        if (json.has("initializedPlayers")) {
            this.initializedPlayers.clear();
            for (com.google.gson.JsonElement el : json.getAsJsonArray("initializedPlayers")) {
                this.initializedPlayers.add(UUID.fromString(el.getAsString()));
            }
        }
        this.needsScoreboardRebuild = true;
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
