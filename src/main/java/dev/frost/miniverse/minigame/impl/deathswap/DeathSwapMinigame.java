package dev.frost.miniverse.minigame.impl.deathswap;

import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathSwapMinigame implements Minigame {
    private static final String NAME = "Death Swap";
    private static final String SCOREBOARD_OBJECTIVE = "deathswap_display";
    private static final ScoreboardController SCOREBOARD = new ScoreboardController(SCOREBOARD_OBJECTIVE, Text.literal("Death Swap"));
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("deathswap");

    private final DeathSwapManager manager = new DeathSwapManager();
    private final Map<UUID, Long> damageImmunityUntilTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> swapsSurvived = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> eliminations = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();
    private final Set<UUID> aliveParticipants = ConcurrentHashMap.newKeySet();
    private final Set<Integer> announcedCountdowns = ConcurrentHashMap.newKeySet();

    private GameState state;
    private DeathSwapSettings settings;
    @Nullable
    private MinecraftServer server;
    private long gameTicks;
    private int tickCounter;
    private int graceTicksRemaining;
    private int swapTicksRemaining;
    private int swapCount;

    public DeathSwapMinigame() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.settings = DeathSwapSettings.defaults();
        this.vanillaTeams.setFriendlyFireAllowed(true);
        this.vanillaTeams.setTeammateCollisionAllowed(false);
    }

    public void applySettings(DeathSwapSettings settings) {
        this.settings = settings == null ? DeathSwapSettings.defaults() : settings;
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
        this.server = null;
        this.gameTicks = 0L;
        this.tickCounter = 0;
        this.graceTicksRemaining = 0;
        this.swapTicksRemaining = 0;
        this.swapCount = 0;
        this.damageImmunityUntilTicks.clear();
        this.swapsSurvived.clear();
        this.eliminations.clear();
        this.playerTeams.clear();
        this.aliveParticipants.clear();
        this.announcedCountdowns.clear();
    }

    @Override
    public void startGame() {
        if (this.state == GameState.IN_PROGRESS || this.state == GameState.ENDING) {
            return;
        }

        List<ServerPlayerEntity> participants = new ArrayList<>(MinigameManager.getInstance().getParticipants());
        if (participants.size() < 2) {
            this.broadcastMessage(Text.literal("Need at least two players to start Death Swap.").formatted(Formatting.RED));
            return;
        }

        this.state = GameState.STARTING;
        MinigameManager.getInstance().setCurrentState(GameState.STARTING);
        this.gameTicks = 0L;
        this.tickCounter = 0;
        this.swapCount = 0;
        this.graceTicksRemaining = this.settings.initialGracePeriodSeconds() * 20;
        this.swapTicksRemaining = Math.max(1, this.settings.swapIntervalSeconds()) * 20;
        this.damageImmunityUntilTicks.clear();
        this.swapsSurvived.clear();
        this.eliminations.clear();
        this.aliveParticipants.clear();
        this.aliveParticipants.addAll(participants.stream().map(ServerPlayerEntity::getUuid).toList());
        this.announcedCountdowns.clear();
        this.rebuildTeams(participants);
        this.syncVanillaTeams();
        this.prepareWorld();
        this.prepareParticipants(participants);

        this.broadcastMessage(Text.literal("✓ Death Swap started!").formatted(Formatting.GREEN));
        this.broadcastMessage(Text.literal("Swap interval: " + this.settings.swapIntervalSeconds() + "s | Grace: " + this.settings.initialGracePeriodSeconds() + "s").formatted(Formatting.AQUA));
        this.broadcastMessage(Text.literal("Last surviving player/team wins.").formatted(Formatting.AQUA));

        if (this.graceTicksRemaining <= 0) {
            this.beginInProgress();
        } else {
            this.updateScoreboard();
        }
    }

    @Override
    public void stopGame() {
        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);

        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            participant.changeGameMode(GameMode.SURVIVAL);
        }

        this.clearScoreboard();
        this.clearVanillaTeams();
        MinigameManager.getInstance().clearParticipants();
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (this.state != GameState.STARTING && this.state != GameState.IN_PROGRESS) {
            return;
        }
        if (!MinigameManager.getInstance().isParticipant(player) || !this.aliveParticipants.contains(player.getUuid())) {
            return;
        }

        this.eliminatePlayer(player, Text.literal(player.getName().getString() + " died.").formatted(Formatting.RED));
    }

    public void onServerTick(MinecraftServer server) {
        this.server = server;
        if (this.state == GameState.ENDING) {
            return;
        }

        this.gameTicks++;
        this.tickCounter++;
        if (this.tickCounter < 20) {
            return;
        }
        this.tickCounter = 0;

        if (this.state == GameState.STARTING) {
            this.tickGracePeriod();
            return;
        }

        if (this.state == GameState.IN_PROGRESS) {
            this.tickSwapTimer();
            this.updateScoreboard();
        }
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (!MinigameManager.getInstance().isParticipant(oldPlayer)) {
            return;
        }

        MinigameManager.getInstance().replaceParticipant(oldPlayer, newPlayer);
        UUID uuid = newPlayer.getUuid();
        if (this.aliveParticipants.contains(uuid)) {
            newPlayer.changeGameMode(GameMode.SURVIVAL);
        } else {
            newPlayer.changeGameMode(GameMode.SPECTATOR);
        }
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        MinigameManager.getInstance().removeParticipant(player);
        this.aliveParticipants.remove(playerUuid);
        this.damageImmunityUntilTicks.remove(playerUuid);

        if (this.state == GameState.IN_PROGRESS && this.resolveWinnerLabel().isBlank()) {
            this.endGameIfRequired();
        }
    }

    public boolean shouldCancelDamage(ServerPlayerEntity player) {
        if (!this.aliveParticipants.contains(player.getUuid())) {
            return false;
        }

        long immuneUntil = this.damageImmunityUntilTicks.getOrDefault(player.getUuid(), 0L);
        return this.state == GameState.IN_PROGRESS && immuneUntil > this.gameTicks;
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

    public boolean canStartMatch() {
        return MinigameManager.getInstance().getParticipantCount() >= 2;
    }

    public int getAliveCount() {
        return this.aliveParticipants.size();
    }

    public int getSwapCount() {
        return this.swapCount;
    }

    private void tickGracePeriod() {
        this.graceTicksRemaining = Math.max(0, this.graceTicksRemaining - 20);
        this.updateCountdownAnnouncements(this.graceTicksRemaining, "First swap");
        this.updateScoreboard();

        if (this.graceTicksRemaining <= 0) {
            this.beginInProgress();
        }
    }

    private void tickSwapTimer() {
        this.refreshAliveParticipants();
        if (this.aliveParticipants.size() <= 1) {
            this.endMatch();
            return;
        }

        this.swapTicksRemaining = Math.max(0, this.swapTicksRemaining - 20);
        this.updateCountdownAnnouncements(this.swapTicksRemaining, "Swap");

        if (this.swapTicksRemaining <= 0) {
            this.executeSwap();
        }
    }

    private void beginInProgress() {
        this.state = GameState.IN_PROGRESS;
        MinigameManager.getInstance().setCurrentState(GameState.IN_PROGRESS);
        this.swapTicksRemaining = Math.max(1, this.settings.swapIntervalSeconds()) * 20;
        this.announcedCountdowns.clear();
        this.broadcastMessage(Text.literal("The first swap is coming!").formatted(Formatting.YELLOW));
        this.updateScoreboard();
    }

    private void executeSwap() {
        this.refreshAliveParticipants();
        List<ServerPlayerEntity> alivePlayers = this.getAlivePlayers();
        if (alivePlayers.size() <= 1) {
            this.endMatch();
            return;
        }

        List<ServerPlayerEntity> order = this.manager.buildSwapOrder(alivePlayers, this.settings.trioRotationEnabled());
        Map<UUID, PositionSnapshot> snapshots = this.captureSnapshots(alivePlayers);
        List<String> teamLabelsBeforeSwap = this.snapshotAliveTeamLabels(alivePlayers);

        for (int i = 0; i < alivePlayers.size(); i++) {
            ServerPlayerEntity source = alivePlayers.get(i);
            ServerPlayerEntity destination = order.get(i);
            PositionSnapshot snapshot = snapshots.get(destination.getUuid());
            if (snapshot == null) {
                continue;
            }

            source.teleport(snapshot.world(), snapshot.x(), snapshot.y(), snapshot.z(), Set.of(), snapshot.yaw(), snapshot.pitch(), true);
            source.setVelocity(this.settings.preserveVelocity() ? snapshot.velocity() : Vec3d.ZERO);
            source.fallDistance = 0.0F;
            source.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        }

        this.swapCount++;
        this.announcedCountdowns.clear();
        this.swapTicksRemaining = Math.max(1, this.settings.swapIntervalSeconds()) * 20;

        if (this.settings.damageImmunityAfterSwapSeconds() > 0) {
            long until = this.gameTicks + (long) this.settings.damageImmunityAfterSwapSeconds() * 20L;
            for (ServerPlayerEntity player : alivePlayers) {
                this.damageImmunityUntilTicks.put(player.getUuid(), until);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, this.settings.damageImmunityAfterSwapSeconds() * 20, 4, true, false, true));
                this.swapsSurvived.merge(player.getUuid(), 1, Integer::sum);
            }
        } else {
            for (ServerPlayerEntity player : alivePlayers) {
                this.swapsSurvived.merge(player.getUuid(), 1, Integer::sum);
            }
        }

        this.broadcastMessage(Text.literal("↔ Swapped " + alivePlayers.size() + " player" + (alivePlayers.size() == 1 ? "" : "s") + ".").formatted(Formatting.AQUA));
        if (!teamLabelsBeforeSwap.isEmpty()) {
            this.broadcastMessage(Text.literal("Teams alive: " + String.join(", ", teamLabelsBeforeSwap)).formatted(Formatting.GRAY));
        }
        this.updateScoreboard();
        this.endGameIfRequired();
    }

    private void endGameIfRequired() {
        this.refreshAliveParticipants();
        if (this.aliveParticipants.size() > 1 && this.resolveWinnerLabel().isBlank()) {
            return;
        }

        this.endMatch();
    }

    private void endMatch() {
        if (this.state == GameState.ENDING) {
            return;
        }

        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);

        String winnerLabel = this.resolveWinnerLabel();
        if (winnerLabel.isBlank()) {
            this.broadcastMessage(Text.literal("No one survived Death Swap.").formatted(Formatting.RED));
        } else {
            this.broadcastMessage(Text.literal("🏆 " + winnerLabel + " wins Death Swap! 🏆").formatted(Formatting.GOLD));
        }
        this.broadcastMessage(Text.literal("Swaps completed: " + this.swapCount).formatted(Formatting.YELLOW));
        this.broadcastMessage(Text.literal("Survivors: " + this.aliveParticipants.size()).formatted(Formatting.YELLOW));

        this.showGameOverTitle(Text.literal(winnerLabel.isBlank() ? "Death Swap Over" : winnerLabel + " wins"));
        this.clearScoreboard();
    }

    private void prepareWorld() {
        if (this.server == null) {
            return;
        }

        for (ServerWorld world : this.server.getWorlds()) {
            world.getWorldBorder().setCenter(0.5D, 0.5D);
            world.getWorldBorder().setSize(this.settings.borderSize());
        }
    }

    private void prepareParticipants(List<ServerPlayerEntity> participants) {
        if (this.server == null) {
            return;
        }

        ServerWorld world = this.server.getOverworld();
        BlockPos spawnPos = new BlockPos(0, 80, 0);
        for (int i = 0; i < participants.size(); i++) {
            ServerPlayerEntity participant = participants.get(i);
            double angle = (Math.PI * 2.0D * i) / Math.max(1, participants.size());
            double offsetX = Math.cos(angle) * 2.5D;
            double offsetZ = Math.sin(angle) * 2.5D;
            participant.changeGameMode(GameMode.SURVIVAL);
            participant.setHealth(participant.getMaxHealth());
            participant.getHungerManager().setFoodLevel(20);
            participant.getHungerManager().setSaturationLevel(20.0F);
            participant.extinguish();
            participant.clearStatusEffects();
            participant.fallDistance = 0.0F;
            participant.teleport(world, spawnPos.getX() + 0.5D + offsetX, spawnPos.getY(), spawnPos.getZ() + 0.5D + offsetZ, Set.of(), participant.getYaw(), participant.getPitch(), true);
            participant.setVelocity(Vec3d.ZERO);
        }
    }

    private void rebuildTeams(List<ServerPlayerEntity> participants) {
        this.playerTeams.clear();
        List<UUID> assignedMembers = new ArrayList<>();
        int fallbackIndex = 1;

        for (DeathSwapSettings.TeamConfig team : this.settings.teams()) {
            String label = this.uniqueTeamLabel(team.label(), fallbackIndex++);
            for (DeathSwapSettings.TeamMember member : team.members()) {
                if (participants.stream().noneMatch(player -> player.getUuid().equals(member.uuid()))) {
                    continue;
                }
                this.playerTeams.put(member.uuid(), label);
                assignedMembers.add(member.uuid());
            }
        }

        for (ServerPlayerEntity participant : participants) {
            if (this.playerTeams.containsKey(participant.getUuid())) {
                continue;
            }
            this.playerTeams.put(participant.getUuid(), this.uniqueTeamLabel(participant.getName().getString(), fallbackIndex++));
        }
    }

    private String uniqueTeamLabel(String label, int fallbackIndex) {
        String base = label == null || label.isBlank() ? "Team " + fallbackIndex : label.trim();
        String candidate = base;
        int suffix = 2;
        while (this.playerTeams.containsValue(candidate)) {
            candidate = base + " #" + suffix++;
        }
        return candidate;
    }

    private void eliminatePlayer(ServerPlayerEntity player, Text message) {
        UUID uuid = player.getUuid();
        if (!this.aliveParticipants.remove(uuid)) {
            return;
        }

        this.eliminations.merge(uuid, 1, Integer::sum);
        player.changeGameMode(GameMode.SPECTATOR);
        this.broadcastMessage(message);
        this.updateScoreboard();
        this.endGameIfRequired();
    }

    private void refreshAliveParticipants() {
        List<UUID> toRemove = new ArrayList<>();
        for (UUID uuid : this.aliveParticipants) {
            ServerPlayerEntity player = this.getPlayerByUuid(uuid);
            if (player == null || player.isDisconnected() || player.isSpectator()) {
                toRemove.add(uuid);
            }
        }
        this.aliveParticipants.removeAll(toRemove);
    }

    private List<ServerPlayerEntity> getAlivePlayers() {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (UUID uuid : this.aliveParticipants) {
            ServerPlayerEntity player = this.getPlayerByUuid(uuid);
            if (player != null && !player.isDisconnected() && !player.isSpectator()) {
                players.add(player);
            }
        }
        return players;
    }

    private Map<UUID, PositionSnapshot> captureSnapshots(List<ServerPlayerEntity> players) {
        Map<UUID, PositionSnapshot> snapshots = new LinkedHashMap<>();
        for (ServerPlayerEntity player : players) {
            snapshots.put(player.getUuid(), new PositionSnapshot(
                (ServerWorld) player.getEntityWorld(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYaw(),
                player.getPitch(),
                player.getVelocity()
            ));
        }
        return snapshots;
    }

    private List<String> snapshotAliveTeamLabels(List<ServerPlayerEntity> players) {
        Set<String> labels = new LinkedHashSet<>();
        for (ServerPlayerEntity player : players) {
            String label = this.playerTeams.get(player.getUuid());
            if (label == null || label.isBlank()) {
                label = player.getName().getString();
            }
            labels.add(label);
        }
        return new ArrayList<>(labels);
    }

    private void updateCountdownAnnouncements(int ticksRemaining, String phase) {
        int secondsRemaining = Math.max(0, (ticksRemaining + 19) / 20);
        int[] milestones = {10, 5, 3, 2, 1};
        for (int milestone : milestones) {
            if (secondsRemaining != milestone || this.announcedCountdowns.contains(milestone)) {
                continue;
            }
            this.announcedCountdowns.add(milestone);
            this.broadcastMessage(Text.literal(phase + " in " + milestone + "s.").formatted(Formatting.YELLOW));
        }
    }

    private void updateScoreboard() {
        if (this.server == null) {
            return;
        }
        this.syncVanillaTeams();

        SCOREBOARD.setScore(this.server, "Alive Players", this.aliveParticipants.size());
        SCOREBOARD.setScore(this.server, "Alive Teams", this.manager.countAliveTeams(this.aliveParticipants, this.playerTeams));
        SCOREBOARD.setScore(this.server, "Swaps", this.swapCount);
        SCOREBOARD.setScore(this.server, "Grace", Math.max(0, this.graceTicksRemaining / 20));
        SCOREBOARD.setScore(this.server, "Next Swap", Math.max(0, this.swapTicksRemaining / 20));
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

        Map<String, List<ServerPlayerEntity>> membersByTeam = new LinkedHashMap<>();
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            String team = this.playerTeams.getOrDefault(participant.getUuid(), participant.getName().getString());
            membersByTeam.computeIfAbsent(team, ignored -> new ArrayList<>()).add(participant);
        }

        List<VanillaTeamDescriptor> descriptors = new ArrayList<>();
        for (Map.Entry<String, List<ServerPlayerEntity>> entry : membersByTeam.entrySet()) {
            Formatting color = this.vanillaTeams.colorFor(entry.getKey());
            VanillaTeamOptions options = VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(Text.literal("[" + entry.getKey() + "] ").formatted(color))
                .withFriendlyFireAllowed(true)
                .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
            descriptors.add(new VanillaTeamDescriptor(entry.getKey(), Text.literal(entry.getKey()), entry.getValue(), options));
        }
        this.vanillaTeams.sync(this.server, descriptors);
    }

    private void clearVanillaTeams() {
        if (this.server != null) {
            this.vanillaTeams.clear(this.server);
        }
    }

    private void showGameOverTitle(Text title) {
        GameMessenger.showGameOverTitle(MinigameManager.getInstance().getParticipants(), title);
    }

    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(MinigameManager.getInstance().getParticipants(), message);
    }

    @Nullable
    private ServerPlayerEntity getPlayerByUuid(UUID uuid) {
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
            if (participant.getUuid().equals(uuid)) {
                return participant;
            }
        }
        if (this.server != null) {
            return this.server.getPlayerManager().getPlayer(uuid);
        }
        return null;
    }

    private String resolveWinnerLabel() {
        String label = this.manager.resolveWinningLabel(this.aliveParticipants, this.playerTeams);
        if (!label.isBlank()) {
            return label;
        }
        if (this.aliveParticipants.size() == 1) {
            UUID winnerUuid = this.aliveParticipants.iterator().next();
            ServerPlayerEntity player = this.getPlayerByUuid(winnerUuid);
            return player == null ? "" : player.getName().getString();
        }
        return "";
    }

    private record PositionSnapshot(ServerWorld world, double x, double y, double z, float yaw, float pitch, Vec3d velocity) {
    }
}



