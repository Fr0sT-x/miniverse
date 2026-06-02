package dev.frost.miniverse.minigame.impl.speedrun;

import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.ScoreboardController;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
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

/**
 * MCSR-style speedrun session implementation.
 *
 * This is a lightweight server-side MVP: runners, a live timer,
 * and Ender Dragon defeat as the completion condition.
 */
public class SpeedrunMinigame implements Minigame, RuntimeContextAware, ServerTickAware, EntityDeathAware, PlayerRespawnAware, PlayerLeaveAware, DynamicParticipantMinigame {
    private static final String NAME = "Speedrun";
    private static final String SCOREBOARD_OBJECTIVE = "speedrun_display";
    private static final int TICKS_PER_SECOND = 20;
    private static final ScoreboardController SCOREBOARD = new ScoreboardController(SCOREBOARD_OBJECTIVE, Text.literal("Speedrun"));
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("speedrun");

    private GameState state;
    @Nullable
    private UUID runnerUuid;
    private MinigameContext context;
    private int elapsedTicks;
    private int tickCounter;
    @Nullable
    private MinecraftServer server;

    public SpeedrunMinigame() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.vanillaTeams.setFriendlyFireAllowed(true);
        this.vanillaTeams.setTeammateCollisionAllowed(false);
    }

    @Override
    public void attachContext(MinigameContext context) {
        this.context = context;
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
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.runnerUuid = null;
        this.elapsedTicks = 0;
        this.tickCounter = 0;
        this.server = null;
    }

    @Override
    public void startGame() {
        if (this.state == GameState.IN_PROGRESS) {
            return;
        }

        if (this.runnerUuid == null) {
            List<ServerPlayerEntity> participants = this.getParticipants();
            if (!participants.isEmpty()) {
                this.runnerUuid = participants.get(0).getUuid();
            }
        }

        if (this.getRunner() == null) {
            this.broadcastMessage(Text.literal("Cannot start Speedrun: no runner is assigned.").formatted(Formatting.RED));
            return;
        }

        this.state = GameState.IN_PROGRESS;
        this.setRuntimeState(GameState.IN_PROGRESS);
        this.elapsedTicks = 0;
        this.tickCounter = 0;

        this.prepareParticipantsForRun();

        this.broadcastMessage(Text.literal("✓ Speedrun started!").formatted(Formatting.GREEN));
        this.broadcastMessage(Text.literal("Runner: " + this.getRunner().getName().getString()).formatted(Formatting.YELLOW));
        this.updateScoreboard();
    }

    @Override
    public void stopGame() {
        this.state = GameState.ENDING;
        this.setRuntimeState(GameState.ENDING);

        this.restoreParticipants();
        this.clearScoreboard();
        this.clearVanillaTeams();

        this.runnerUuid = null;
        this.elapsedTicks = 0;
        this.tickCounter = 0;
        this.server = null;

        this.clearParticipants();
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        // Speedrun finishes only when a team defeats the Ender Dragon.
    }

    public void handleDragonDeath() {
        if (this.state != GameState.IN_PROGRESS) {
            return;
        }

        this.endGameWithVictory(Text.literal("The Ender Dragon has been defeated!").formatted(Formatting.GOLD));
    }

    public void onServerTick(MinecraftServer server) {
        this.server = server;
        if (this.state == GameState.ENDING) {
            return;
        }

        if (this.state == GameState.IN_PROGRESS) {
            this.elapsedTicks++;
            this.tickCounter++;

            if (this.tickCounter >= TICKS_PER_SECOND) {
                this.tickCounter = 0;
                this.updateScoreboard();
            }
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof EnderDragonEntity) {
            this.handleDragonDeath();
            return;
        }

        if (entity instanceof ServerPlayerEntity player && this.isParticipant(player)) {
            this.onPlayerDeath(player);
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

    public void setRunner(ServerPlayerEntity player) {
        this.addParticipant(player);
        this.runnerUuid = player.getUuid();

        if (this.state == GameState.IN_PROGRESS) {
            this.prepareRunnerForRun(player);
        }
        this.syncVanillaTeams();

        this.updateScoreboard();
    }

    public void syncLateParticipant(ServerPlayerEntity player) {
        this.addParticipant(player);
        if (this.state == GameState.IN_PROGRESS) {
            this.prepareRunnerForRun(player);
        }
        this.syncVanillaTeams();
        this.updateScoreboard();
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        String normalizedRole = role == null ? "" : role.trim().toLowerCase();
        if (this.getRunner() == null) {
            this.setRunner(player);
            return;
        }
        if (normalizedRole.equals("runner") || normalizedRole.equals("speedrunner")) {
            this.addRunnerParticipant(player);
            if (this.state == GameState.IN_PROGRESS) {
                player.sendMessage(Text.literal("Joined Speedrun in progress as a runner.").formatted(Formatting.GREEN), false);
            }
            return;
        }
        this.syncLateParticipant(player);
        if (this.state == GameState.IN_PROGRESS) {
            player.sendMessage(Text.literal("Joined Speedrun in progress as a runner.").formatted(Formatting.GREEN), false);
        }
    }

    @Nullable
    public ServerPlayerEntity getRunner() {
        if (this.runnerUuid == null) {
            return null;
        }

        for (ServerPlayerEntity participant : this.getParticipants()) {
            if (participant.getUuid().equals(this.runnerUuid)) {
                return participant;
            }
        }

        if (this.server != null) {
            return this.server.getPlayerManager().getPlayer(this.runnerUuid);
        }

        return null;
    }

    public int getElapsedTicks() {
        return this.elapsedTicks;
    }

    public String getFormattedTime() {
        int totalSeconds = this.elapsedTicks / TICKS_PER_SECOND;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        int hundredths = (this.elapsedTicks % TICKS_PER_SECOND) * 5;
        return String.format("%d:%02d.%02d", minutes, seconds, hundredths);
    }

    public boolean canStartRun() {
        return this.getRunner() != null && !this.getParticipants().isEmpty();
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (!this.isParticipant(oldPlayer)) {
            return;
        }

        this.replaceParticipant(oldPlayer, newPlayer);
        if (this.runnerUuid != null && this.runnerUuid.equals(oldPlayer.getUuid())) {
            this.runnerUuid = newPlayer.getUuid();
        }

        if (this.state == GameState.IN_PROGRESS) {
            this.syncParticipantModes();
        }
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        this.removeParticipant(player);

        if (this.getRunner() == null && !this.getParticipants().isEmpty()) {
            this.runnerUuid = this.getParticipants().get(0).getUuid();
            this.updateScoreboard();
        }
        this.syncVanillaTeams();
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

    private boolean isRunner(ServerPlayerEntity player) {
        return this.runnerUuid != null && this.runnerUuid.equals(player.getUuid());
    }

    private boolean isRunnerConnected() {
        return this.getRunner() != null;
    }

    private void addRunnerParticipant(ServerPlayerEntity player) {
        this.addParticipant(player);
        if (this.state == GameState.IN_PROGRESS) {
            this.prepareRunnerForRun(player);
        }
        this.syncVanillaTeams();
        this.updateScoreboard();
    }

    private void prepareRunnerForRun(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.changeGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0F);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 40, 0, true, false, false));
    }

    private void endGameWithVictory(Text reason) {
        this.state = GameState.ENDING;
        this.setRuntimeState(GameState.ENDING);

        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("🏆 SPEEDRUN COMPLETE! 🏆").formatted(Formatting.GOLD));
        this.broadcastMessage(reason.copy().formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("Time: " + this.getFormattedTime()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));

        ServerPlayerEntity runner = this.getRunner();
        this.startStandardEndSequence(runner == null
            ? new MatchEndResult(Set.of(), Text.literal("No winner"))
            : MatchEndResult.winner(runner));
        this.clearScoreboard();
    }

    private void startStandardEndSequence(MatchEndResult result) {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            MatchLifecycleController.getInstance().endMatch(runtime, result, MatchLifecycleOptions.defaults(NAME));
        }
    }

    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(this.getParticipants(), message);
    }

    private List<ServerPlayerEntity> getParticipants() {
        return this.context().liveParticipants();
    }

    private int getParticipantCount() {
        return this.context().participants().size();
    }

    private boolean isParticipant(ServerPlayerEntity player) {
        return this.context().participants().contains(player);
    }

    private void addParticipant(ServerPlayerEntity player) {
        this.context().participants().add(player);
    }

    private void removeParticipant(ServerPlayerEntity player) {
        this.context().participants().remove(player);
    }

    private void replaceParticipant(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        this.context().participants().add(newPlayer);
    }

    private void clearParticipants() {
        this.context().participants().clear();
    }

    private void setRuntimeState(GameState state) {
        this.context().setState(state);
    }

    private MinigameContext context() {
        if (this.context == null) {
            throw new IllegalStateException("Speedrun runtime context is not attached.");
        }
        return this.context;
    }

    private void updateScoreboard() {
        if (this.server == null) {
            return;
        }
        this.syncVanillaTeams();

        SCOREBOARD.setScore(this.server, "Players", this.getParticipantCount());
        SCOREBOARD.setScore(this.server, "Runner", this.getRunner() == null ? 0 : 1);
        SCOREBOARD.setScore(this.server, "Time", this.state == GameState.IN_PROGRESS ? this.elapsedTicks / TICKS_PER_SECOND : 0);
    }

    private void clearScoreboard() {
        if (this.server == null) {
            return;
        }

        SCOREBOARD.clear(this.server);
        this.clearVanillaTeams();
    }

    private void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }

        List<ServerPlayerEntity> runners = this.getParticipants();
        List<ServerPlayerEntity> spectators = List.of();

        VanillaTeamOptions runnerOptions = VanillaTeamOptions.defaults()
            .withColor(Formatting.GREEN)
            .withPrefix(Text.literal("[RUNNER] ").formatted(Formatting.GREEN))
            .withFriendlyFireAllowed(true)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
        VanillaTeamOptions spectatorOptions = VanillaTeamOptions.defaults()
            .withColor(Formatting.DARK_GRAY)
            .withPrefix(Text.literal("[SPEC] ").formatted(Formatting.DARK_GRAY))
            .withFriendlyFireAllowed(false)
            .withCollisionRule(AbstractTeam.CollisionRule.NEVER);

        List<TeamSnapshot> snapshots = List.of(
            new TeamSnapshot("runner", "Runner", runners.stream().map(player -> TeamMembership.of(player, TeamRole.RUNNER)).toList()),
            new TeamSnapshot("spectators", "Spectators", spectators.stream().map(player -> TeamMembership.of(player, TeamRole.SPECTATOR)).toList())
        );
        this.vanillaTeams.syncSnapshots(this.server, snapshots, snapshot -> switch (snapshot.id()) {
            case "runner" -> runnerOptions;
            case "spectators" -> spectatorOptions;
            default -> VanillaTeamOptions.defaults();
        });
    }

    private void clearVanillaTeams() {
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
    }
}


