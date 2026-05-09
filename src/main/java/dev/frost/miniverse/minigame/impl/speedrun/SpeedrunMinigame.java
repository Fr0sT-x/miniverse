package dev.frost.miniverse.minigame.impl.speedrun;

import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.ScoreboardController;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamDescriptor;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
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
import java.util.UUID;

/**
 * MCSR-style speedrun session implementation.
 *
 * This is a lightweight server-side MVP: one runner, a live timer,
 * and end conditions for runner death or Ender Dragon defeat.
 */
public class SpeedrunMinigame implements Minigame {
    private static final String NAME = "Speedrun";
    private static final String SCOREBOARD_OBJECTIVE = "speedrun_display";
    private static final int TICKS_PER_SECOND = 20;
    private static final ScoreboardController SCOREBOARD = new ScoreboardController(SCOREBOARD_OBJECTIVE, Text.literal("Speedrun"));
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("speedrun");

    private GameState state;
    @Nullable
    private UUID runnerUuid;
    private int elapsedTicks;
    private int tickCounter;
    @Nullable
    private MinecraftServer server;

    public SpeedrunMinigame() {
        this.state = GameState.WAITING_FOR_PLAYERS;
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
            List<ServerPlayerEntity> participants = MinigameManager.getInstance().getParticipants();
            if (!participants.isEmpty()) {
                this.runnerUuid = participants.get(0).getUuid();
            }
        }

        if (this.getRunner() == null) {
            this.broadcastMessage(Text.literal("Cannot start Speedrun: no runner is assigned.").formatted(Formatting.RED));
            return;
        }

        this.state = GameState.IN_PROGRESS;
        MinigameManager.getInstance().setCurrentState(GameState.IN_PROGRESS);
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
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);

        this.restoreParticipants();
        this.clearScoreboard();
        this.clearVanillaTeams();

        this.runnerUuid = null;
        this.elapsedTicks = 0;
        this.tickCounter = 0;
        this.server = null;

        MinigameManager.getInstance().clearParticipants();
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (this.state != GameState.IN_PROGRESS || !MinigameManager.getInstance().isParticipant(player)) {
            return;
        }

        if (!this.isRunner(player)) {
            return;
        }

        this.endGameWithFailure(Text.literal(player.getName().getString() + " died.").formatted(Formatting.RED));
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

            if (this.runnerUuid != null && !this.isRunnerConnected()) {
                this.endGameWithFailure(Text.literal("Runner disconnected.").formatted(Formatting.RED));
                return;
            }

            if (this.tickCounter >= TICKS_PER_SECOND) {
                this.tickCounter = 0;
                this.updateScoreboard();
            }
        }
    }

    public void setRunner(ServerPlayerEntity player) {
        MinigameManager manager = MinigameManager.getInstance();
        manager.addParticipant(player);
        this.runnerUuid = player.getUuid();

        if (this.state == GameState.IN_PROGRESS) {
            this.syncParticipantModes();
        }
        this.syncVanillaTeams();

        this.updateScoreboard();
    }

    @Nullable
    public ServerPlayerEntity getRunner() {
        if (this.runnerUuid == null) {
            return null;
        }

        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
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
        return this.getRunner() != null && !MinigameManager.getInstance().getParticipants().isEmpty();
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (!MinigameManager.getInstance().isParticipant(oldPlayer)) {
            return;
        }

        MinigameManager.getInstance().replaceParticipant(oldPlayer, newPlayer);
        if (this.runnerUuid != null && this.runnerUuid.equals(oldPlayer.getUuid())) {
            this.runnerUuid = newPlayer.getUuid();
        }

        if (this.state == GameState.IN_PROGRESS) {
            this.syncParticipantModes();
        }
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        boolean wasRunner = this.isRunner(player);
        MinigameManager.getInstance().removeParticipant(player);

        if (wasRunner && this.state == GameState.IN_PROGRESS) {
            this.endGameWithFailure(Text.literal(player.getName().getString() + " left the run.").formatted(Formatting.RED));
            return;
        }

        if (this.getRunner() == null && !MinigameManager.getInstance().getParticipants().isEmpty()) {
            this.runnerUuid = MinigameManager.getInstance().getParticipants().get(0).getUuid();
            this.updateScoreboard();
        }
        this.syncVanillaTeams();
    }

    private void prepareParticipantsForRun() {
        ServerPlayerEntity runner = this.getRunner();
        if (runner == null) {
            return;
        }

        List<ServerPlayerEntity> participants = MinigameManager.getInstance().getParticipants();
        for (ServerPlayerEntity participant : participants) {
            if (participant.getUuid().equals(runner.getUuid())) {
                participant.getInventory().clear();
                participant.changeGameMode(GameMode.SURVIVAL);
                participant.getHungerManager().setFoodLevel(20);
                participant.getHungerManager().setSaturationLevel(20.0F);
                participant.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 40, 0, true, false, false));
            } else {
                participant.changeGameMode(GameMode.SPECTATOR);
            }
        }
    }

    private void syncParticipantModes() {
        ServerPlayerEntity runner = this.getRunner();
        if (runner == null) {
            return;
        }

        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            if (participant.getUuid().equals(runner.getUuid())) {
                participant.changeGameMode(GameMode.SURVIVAL);
            } else {
                participant.changeGameMode(GameMode.SPECTATOR);
            }
        }
    }

    private void restoreParticipants() {
        for (ServerPlayerEntity participant : new ArrayList<>(MinigameManager.getInstance().getParticipants())) {
            participant.changeGameMode(GameMode.SURVIVAL);
        }
    }

    private boolean isRunner(ServerPlayerEntity player) {
        return this.runnerUuid != null && this.runnerUuid.equals(player.getUuid());
    }

    private boolean isRunnerConnected() {
        return this.getRunner() != null;
    }

    private void endGameWithVictory(Text reason) {
        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);

        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("🏆 SPEEDRUN COMPLETE! 🏆").formatted(Formatting.GOLD));
        this.broadcastMessage(reason.copy().formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("Time: " + this.getFormattedTime()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));

        this.showGameOverTitle(Text.literal("Speedrun Complete"));
        this.clearScoreboard();
    }

    private void endGameWithFailure(Text reason) {
        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);

        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("🏁 SPEEDRUN FAILED 🏁").formatted(Formatting.RED));
        this.broadcastMessage(reason.copy().formatted(Formatting.RED));
        this.broadcastMessage(Text.literal("Time: " + this.getFormattedTime()).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));

        this.showGameOverTitle(Text.literal("Speedrun Failed"));
        this.clearScoreboard();
    }

    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(MinigameManager.getInstance().getParticipants(), message);
    }

    private void updateScoreboard() {
        if (this.server == null) {
            return;
        }
        this.syncVanillaTeams();

        SCOREBOARD.setScore(this.server, "Players", MinigameManager.getInstance().getParticipantCount());
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

        ServerPlayerEntity runner = this.getRunner();
        List<ServerPlayerEntity> runners = runner == null ? List.of() : List.of(runner);
        List<ServerPlayerEntity> spectators = MinigameManager.getInstance().getParticipants().stream()
            .filter(player -> runner == null || !runner.getUuid().equals(player.getUuid()))
            .toList();

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

        this.vanillaTeams.sync(this.server, List.of(
            new VanillaTeamDescriptor("runner", Text.literal("Runner"), runners, runnerOptions),
            new VanillaTeamDescriptor("spectators", Text.literal("Spectators"), spectators, spectatorOptions)
        ));
    }

    private void clearVanillaTeams() {
        if (this.server != null) {
            this.vanillaTeams.clear(this.server);
        }
    }

    private void showGameOverTitle(Text title) {
        GameMessenger.showGameOverTitle(MinigameManager.getInstance().getParticipants(), title);
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


