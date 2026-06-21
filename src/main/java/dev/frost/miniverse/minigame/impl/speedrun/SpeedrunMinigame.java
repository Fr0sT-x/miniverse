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
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;

/**
 * MCSR-style speedrun session implementation.
 *
 * This is a lightweight server-side MVP: runners, a live timer,
 * and Ender Dragon defeat as the completion condition.
 */
public class SpeedrunMinigame extends AbstractMinigame implements ServerTickAware, EntityDeathAware, PlayerRespawnAware, PlayerLeaveAware, DynamicParticipantMinigame, PauseAwareMinigame {
    private static final String NAME = "Speedrun";
    private static final int TICKS_PER_SECOND = 20;
    private ScoreboardTemplate scoreboard;
    private ScoreboardLine playersLine;
    private ScoreboardLine runnerLine;
    private ScoreboardLine timeLine;
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("speedrun");

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private boolean paused = false;
    @Nullable
    private UUID runnerUuid;
    private int elapsedTicks;
    private int tickCounter;
    @Nullable
    private MinecraftServer server;

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
        this.setState(GameState.WAITING_FOR_PLAYERS);
        this.runnerUuid = null;
        this.elapsedTicks = 0;
        this.tickCounter = 0;
        this.server = null;
        this.paused = false;
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        return GlobalMatchRules.defaults(true, false);
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

        this.setState(GameState.RUNNING);
        this.setRuntimeState(GameState.RUNNING);
        this.elapsedTicks = 0;
        this.tickCounter = 0;

        this.prepareParticipantsForRun();

        this.broadcastMessage(Text.literal("✓ Speedrun started!").formatted(Formatting.GREEN));
        this.broadcastMessage(Text.literal("Runner: " + this.getRunner().getName().getString()).formatted(Formatting.YELLOW));
        this.rebuildScoreboard();
    }

    @Override
    protected void onMatchEnd() {
        this.setState(GameState.ENDING);
        this.setRuntimeState(GameState.ENDING);

        this.restoreParticipants();
        if (this.server != null && this.scoreboard != null) {
            this.scoreboard.cleanup(this.server);
        }
        this.clearVanillaTeams();

        this.runnerUuid = null;
        this.elapsedTicks = 0;
        this.tickCounter = 0;
        this.server = null;

        if (this.context != null) {
            this.context.roster().clear();
        }
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        // Speedrun finishes only when a team defeats the Ender Dragon.
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
        if (this.getState() == GameState.ENDING) {
            return;
        }

        if (this.getState() == GameState.RUNNING && !this.paused) {
            this.elapsedTicks++;
            this.tickCounter++;

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

        if (this.getState() == GameState.RUNNING) {
            this.prepareRunnerForRun(player);
        }
        this.syncVanillaTeams();

        this.rebuildScoreboard();
    }

    public void syncLateParticipant(ServerPlayerEntity player) {
        this.addParticipant(player);
        if (this.getState() == GameState.RUNNING) {
            this.prepareRunnerForRun(player);
        }
        this.syncVanillaTeams();
        this.rebuildScoreboard();
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
            if (this.getState() == GameState.RUNNING) {
                player.sendMessage(Text.literal("Joined Speedrun in progress as a runner.").formatted(Formatting.GREEN), false);
            }
            return;
        }
        this.syncLateParticipant(player);
        if (this.getState() == GameState.RUNNING) {
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

        if (this.getState() == GameState.RUNNING) {
            this.syncParticipantModes();
        }
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        this.removeParticipant(player);

        if (this.getRunner() == null && !this.getParticipants().isEmpty()) {
            this.runnerUuid = this.getParticipants().get(0).getUuid();
            this.rebuildScoreboard();
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
        if (this.getState() == GameState.RUNNING) {
            this.prepareRunnerForRun(player);
        }
        this.syncVanillaTeams();
        this.rebuildScoreboard();
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
        this.setState(GameState.ENDING);
        this.setRuntimeState(GameState.ENDING);

        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("🏆 SPEEDRUN COMPLETE! 🏆").formatted(Formatting.GOLD));
        this.broadcastMessage(reason.copy().formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("Time: " + this.getFormattedTime()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));

        ServerPlayerEntity runner = this.getRunner();
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getStandardEndSequence().start(NAME, runner == null
            ? new MatchEndResult(Set.of(), Text.literal("No winner"))
            : MatchEndResult.winner(runner));
        if (this.server != null && this.scoreboard != null) {
            this.scoreboard.cleanup(this.server);
        }
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
        if (this.server == null) {
            return;
        }
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("Speedrun").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD)));
            this.scoreboard.show(this.getParticipants());
        }

        this.syncVanillaTeams();
        this.scoreboard.clearLines();
        this.playersLine = this.scoreboard.addLine(Text.empty());
        this.runnerLine = this.scoreboard.addLine(Text.empty());
        this.scoreboard.addBlankLine();
        this.timeLine = this.scoreboard.addLine(Text.empty());
        this.scoreboard.resendStructure();
        this.updateScoreboardTick();
    }

    private void updateScoreboardTick() {
        if (this.playersLine != null) {
            this.playersLine.setText(Text.literal("Players: " + this.getParticipantCount()));
            this.playersLine.updateAll();
        }
        if (this.runnerLine != null) {
            this.runnerLine.setText(Text.literal("Runner: " + (this.getRunner() == null ? 0 : 1)));
            this.runnerLine.updateAll();
        }
        if (this.timeLine != null) {
            this.timeLine.setText(Text.literal("Time: " + (this.getState() == GameState.RUNNING ? this.elapsedTicks / 20 : 0)));
            this.timeLine.updateAll();
        }
    }



    @Override
    protected void syncVanillaTeams() {
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
