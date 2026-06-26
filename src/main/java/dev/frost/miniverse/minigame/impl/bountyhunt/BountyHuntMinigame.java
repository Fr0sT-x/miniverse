package dev.frost.miniverse.minigame.impl.bountyhunt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.ItemUseAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerJoinAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.item.ProtectedItemRule;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTypes;
import dev.frost.miniverse.minigame.core.item.TrackingItemNameFormatter;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlayRenderMode;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlaySender;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlayPresets;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlaySettings;
import dev.frost.miniverse.minigame.core.protection.ProtectionOverlayStyle;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.team.TeamMembership;
import dev.frost.miniverse.team.TeamRole;
import dev.frost.miniverse.team.TeamSnapshot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.sound.SoundEvents;
import dev.frost.miniverse.minigame.core.tracker.PlayerTracker;
import static dev.frost.miniverse.minigame.core.util.MinigameSerializationUtil.*;
import java.util.concurrent.ThreadLocalRandom;

import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.impl.bountyhunt.death.BountyHuntDeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.death.ImmediateRespawnNotifier;

public class BountyHuntMinigame extends AbstractMinigame implements dev.frost.miniverse.minigame.core.event.RosterAware, DeathAwareMinigame, ImmediateRespawnNotifier, dev.frost.miniverse.team.TeamManagerProvider {
    private static final String NAME = "Bounty Hunt";
    private static final String TRACKER_TYPE = ProtectedItemTypes.TRACKER_COMPASS;
    private static final Identifier GRACE_PROTECTION_OVERLAY = ProtectionOverlayPresets.GRACE_PERIOD.overlayId();
    private static final int BOUNTY_PROTECTION_COLOR = 0xE6FFFFFF;
    private static final ProtectionOverlaySettings BOUNTY_PROTECTION_OVERLAY = ProtectionOverlaySettings.DEFAULT
        .withStyle(ProtectionOverlayStyle.VANILLA_GLOW)
        .withRenderMode(ProtectionOverlayRenderMode.DEPTH_TESTED)
        .withGlowColor(0xFFFFFFFF)
        .withOutlineColor(0xFFFFFFFF)
        .withAlpha(0.82F)
        .withIntensity(1.0F);

    private static final Identifier LEADER_OVERLAY = Identifier.of("miniverse", "bountyhunt_leader");
    private static final int LEADER_COLOR = 0xFFFFD700;
    private static final ProtectionOverlaySettings LEADER_OVERLAY_SETTINGS = ProtectionOverlaySettings.DEFAULT
        .withStyle(ProtectionOverlayStyle.VANILLA_GLOW)
        .withRenderMode(ProtectionOverlayRenderMode.DEPTH_TESTED)
        .withGlowColor(0xFFFFD700)
        .withOutlineColor(0xFFFFD700)
        .withAlpha(0.82F)
        .withIntensity(1.0F);

    private ScoreboardTemplate scoreboard;
    private ScoreboardLine graceLine;

    private final dev.frost.miniverse.team.TeamManager teamManager = new dev.frost.miniverse.team.TeamManager();

    private final Map<UUID, UUID> targetAssignments = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
    private final Map<UUID, Long> compassCooldownUntilTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> killStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> targetBountyValues = new ConcurrentHashMap<>();
    private final PlayerTracker playerTracker = new PlayerTracker();
    private final dev.frost.miniverse.minigame.core.countdown.CountdownService graceCountdowns = new dev.frost.miniverse.minigame.core.countdown.CountdownService();

    private GameState state;
    private BountyHuntSettings settings;
    private MinecraftServer server;
    // Death Lifecycle Framework — see ARCHITECTURE.md F05
    private DeathLifecycleManager deathLifecycleManager;
    private long gameTicks;
    private int tickCounter;
    private int graceTicksRemaining;
    private int targetSwapTicksRemaining;

    public BountyHuntMinigame() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.applySettings(BountyHuntSettings.defaults());
    }

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }

    @Override
    public dev.frost.miniverse.team.TeamManager teamManager() {
        return this.teamManager;
    }



    // Context is attached in AbstractMinigame

    public void setVanillaFriendlyFireAllowed(boolean allowed) {
        if (this.getVanillaTeams() != null) {
            this.getVanillaTeams().setFriendlyFireAllowed(allowed);
            this.syncVanillaTeams();
        }
    }

    public dev.frost.miniverse.minigame.core.MinigameContext getContext() {
        return this.context;
    }

    public void setVanillaTeammateCollisionAllowed(boolean allowed) {
        if (this.getVanillaTeams() != null) {
            this.getVanillaTeams().setTeammateCollisionAllowed(allowed);
            this.syncVanillaTeams();
        }
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(net.minecraft.world.GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_IMMEDIATE_RESPAWN, true);
        this.setState(GameState.WAITING_FOR_PLAYERS);
        this.targetAssignments.clear();
        this.scores.clear();
        this.compassCooldownUntilTicks.clear();
        this.killStreaks.clear();
        this.targetBountyValues.clear();
        this.killStreaks.clear();
        this.targetBountyValues.clear();
        this.playerTracker.clear();
        this.graceCountdowns.reset();
        this.gameTicks = 0L;
        this.tickCounter = 0;
        this.graceTicksRemaining = 0;
        this.targetSwapTicksRemaining = 0;
    }

    @Override
    protected void onMatchStart() {
        this.setState(GameState.STARTING);
        this.setRuntimeState(GameState.STARTING);
        this.registerProtectedItems();
        this.targetAssignments.clear();
        this.scores.clear();
        this.compassCooldownUntilTicks.clear();
        this.killStreaks.clear();
        this.targetBountyValues.clear();
        this.killStreaks.clear();
        this.targetBountyValues.clear();
        this.playerTracker.clear();
        this.graceCountdowns.reset();
        this.gameTicks = 0L;
        this.tickCounter = 0;
        this.graceTicksRemaining = this.settings.gracePeriodSeconds() * 20;
        this.targetSwapTicksRemaining = this.settings.targetSwapIntervalSeconds() * 20;

        this.deathLifecycleManager = new DeathLifecycleManager(new dev.frost.miniverse.minigame.impl.bountyhunt.death.BountyHuntDeathLifecycleConfig(this), dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getSpectatorService());

        this.rebuildSoloTeams(this.getParticipants());
        this.assignInitialTargets();
        this.grantTrackersToParticipants();

        this.broadcastMessage(Text.literal("✓ Bounty Hunt started!").formatted(Formatting.GREEN));
        this.broadcastMessage(Text.literal("First player to " + this.settings.scoreToWin() + " points wins.").formatted(Formatting.AQUA));
        this.broadcastMessage(Text.literal("Only kills on your assigned target give points.").formatted(Formatting.AQUA));
        this.broadcastMessage(Text.literal("Your tracker points to your current target.").formatted(Formatting.AQUA));
        if (this.graceTicksRemaining > 0) {
            this.broadcastGraceProtectionState(this.graceTicksRemaining, true);
            this.broadcastMessage(Text.literal("Grace period active: PvP enabled in " + this.formatDuration(this.settings.gracePeriodSeconds()) + ".")
                .formatted(Formatting.YELLOW));
        } else {
            this.beginHunt();
        }
        this.rebuildScoreboard();
    }

    @Override
    protected void onMatchEnd() {
        this.setState(GameState.ENDING);
        this.targetAssignments.clear();
        this.scores.clear();
        this.clearVanillaTeams();
        this.compassCooldownUntilTicks.clear();
        this.killStreaks.clear();
        this.targetBountyValues.clear();
        this.playerTracker.clear();
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleMatchEnding(this::getPlayerByUuid);
        }
        this.clearScoreboard();
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        // Handled by DeathLifecycleManager via allowDamage()
    }

    public Text getDeathTitle(ServerPlayerEntity victim, net.minecraft.entity.damage.DamageSource source) {
        String killerName = source != null && source.getAttacker() != null ? source.getAttacker().getName().getString() : "Unknown";
        return Text.literal("You were killed by " + killerName + "!").formatted(net.minecraft.util.Formatting.RED);
    }

    @Override
    public Text getDeathSubtitle(ServerPlayerEntity victim, net.minecraft.entity.damage.DamageSource source, int ticksRemaining) {
        int seconds = (int) Math.ceil(ticksRemaining / 20.0);
        return Text.literal("Respawning in ").formatted(net.minecraft.util.Formatting.GRAY)
            .append(Text.literal(String.valueOf(seconds)).formatted(net.minecraft.util.Formatting.GREEN))
            .append(Text.literal(" seconds...").formatted(net.minecraft.util.Formatting.GRAY));
    }

    public void processPlayerDeath(ServerPlayerEntity player, @Nullable DamageSource source) {
        if (!this.isParticipant(player)) {
            return;
        }

        this.killStreaks.put(player.getUuid(), 0);

        ServerPlayerEntity killer = source != null && source.getAttacker() instanceof ServerPlayerEntity attacker
            ? attacker
            : null;

        boolean scored = false;
        if (killer != null && this.isParticipant(killer)) {
            UUID expectedTarget = this.targetAssignments.get(killer.getUuid());
            if (expectedTarget != null && expectedTarget.equals(player.getUuid())) {
                int bountyValue = this.targetBountyValues.getOrDefault(player.getUuid(), 0);
                int pointsEarned = 1 + bountyValue;
                int newScore = this.scores.merge(killer.getUuid(), pointsEarned, Integer::sum);
                scored = true;
                this.targetBountyValues.put(player.getUuid(), 0);
                
                int currentStreak = this.killStreaks.merge(killer.getUuid(), 1, Integer::sum);
                if (currentStreak >= 2) {
                    this.broadcastMessage(Text.literal(killer.getName().getString() + " is on a " + currentStreak + "-kill streak!").formatted(Formatting.GOLD));
                }

                killer.sendMessage(Text.literal("Target eliminated! +" + pointsEarned + " point(s) (" + newScore + "/" + this.settings.scoreToWin() + ")")
                    .formatted(Formatting.GOLD), true);
                killer.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                player.sendMessage(Text.literal("You were eliminated by your hunter, " + killer.getName().getString() + "!").formatted(Formatting.RED), false);
                if (newScore >= this.settings.scoreToWin()) {
                    this.endGameWithWinner(killer);
                    return;
                }
            } else {
                killer.sendMessage(Text.literal("No point: that player was not your assigned target.")
                    .formatted(Formatting.RED), true);
            }
            
            if (this.settings.highValueTargetEnabled() && killer != player) {
                List<ServerPlayerEntity> leaders = this.getCurrentLeaders();
                if (leaders.size() == 1 && leaders.get(0).getUuid().equals(player.getUuid())) {
                    int newScore = this.scores.merge(killer.getUuid(), 1, Integer::sum);
                    this.broadcastMessage(Text.literal(killer.getName().getString() + " assassinated the High Value Target! +1 bonus point.").formatted(Formatting.YELLOW));
                    if (newScore >= this.settings.scoreToWin()) {
                        this.endGameWithWinner(killer);
                        return;
                    }
                }
            }
        }

        if (killer != null && this.isParticipant(killer)) {
            if (this.settings.revengeAssignmentEnabled() && !player.getUuid().equals(this.targetAssignments.get(killer.getUuid()))) {
                this.targetAssignments.put(player.getUuid(), killer.getUuid());
                player.sendMessage(Text.literal("Revenge Assignment: Your new target is " + killer.getName().getString()).formatted(Formatting.AQUA), true);
            } else {
                this.assignNewTarget(player, false);
            }
        }
        this.grantTracker(player);

        if (scored && killer != null) {
            this.assignNewTarget(killer, true);
            this.syncTrackerTarget(killer, true);
        }

        this.rebuildScoreboard();
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;
        if (this.getState() == GameState.ENDING) {
            return;
        }

        this.gameTicks++;
        this.tickCounter++;
        if (this.tickCounter >= 20) {
            this.tickCounter = 0;
            this.playerTracker.updatePositions(this.getParticipants(), this.settings.netherTrackingEnabled());
            this.tickGracePeriod();
            this.tickTargetSwap();
            if (this.state == GameState.RUNNING && this.settings.trackerEnabled()) {
                this.updateTrackers();
            }
            this.updateScoreboardTick();
            this.updateHighValueTargetOverlay();
        }
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand) {
        if (!this.isParticipant(player)) {
            return ActionResult.PASS;
        }

        if (!this.isTrackerItem(player.getStackInHand(hand))) {
            return ActionResult.PASS;
        }

        this.cycleTrackerCooldown(player);
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (!this.isParticipant(player)) {
            return true;
        }

        return !this.shouldCancelDamage(player, source);
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.isParticipant(player)) {
            this.removeParticipant(player);
            this.handlePlayerLeave(player);
        }
    }

    @Override
    protected void onPlayerJoinGame(ServerPlayerEntity player, MinecraftServer server) {
        this.sendGraceProtectionStatesTo(player);
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        if (!this.isParticipant(player) && this.context != null) {
            this.context.roster().add(player);
        }
        this.scores.putIfAbsent(player.getUuid(), 0);
        if (this.state == GameState.RUNNING || this.state == GameState.STARTING) {
            this.registerProtectedItems();
            this.grantTracker(player);
            this.assignInitialTargets();
            this.sendGraceProtectionStatesTo(player);
            this.sendGraceProtectionStatesTo(player);
            player.sendMessage(Text.literal("Joined Bounty Hunt in progress.").formatted(Formatting.GREEN), false);
        }
        this.rebuildSoloTeams(this.getParticipants());
        this.rebuildScoreboard();
    }

    public void processPlayerRespawn(ServerPlayerEntity newPlayer) {
        this.grantTracker(newPlayer);
        this.syncTrackerTarget(newPlayer, false);
        this.syncVanillaTeams();
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleDisconnect(player);
        }
        UUID playerUuid = player.getUuid();
        this.targetAssignments.remove(playerUuid);
        this.compassCooldownUntilTicks.remove(playerUuid);
        this.playerTracker.remove(playerUuid);
        this.scores.remove(playerUuid);
        ProtectionOverlaySender.broadcastClearOverlay(this.server, playerUuid, LEADER_OVERLAY);
        if (this.state == GameState.STARTING && this.graceTicksRemaining > 0) {
            ProtectionOverlaySender.broadcastClearOverlay(this.server, playerUuid, GRACE_PROTECTION_OVERLAY);
        }

        if (this.getParticipants().size() <= 1 && this.state != GameState.ENDING) {
            if (this.checkProgression(this.context.roster()).blocked()) {
                ServerPlayerEntity remaining = this.getParticipants().stream().findFirst().orElse(null);
                if (remaining != null) {
                    this.endGameWithWinner(remaining);
                }
            }
        }
        this.rebuildSoloTeams(this.getParticipants());
        this.rebuildScoreboard();
    }

    void handleDisconnectGraceExpired(List<UUID> pendingPlayers) {
        if (!this.state.isActive()) {
            return;
        }
        if (this.getParticipants().size() > 1) {
            return;
        }
        ServerPlayerEntity remaining = this.getParticipants().stream().findFirst().orElse(null);
        if (remaining != null) {
            this.broadcastMessage(Text.literal("Disconnect grace expired. Ending match.").formatted(Formatting.YELLOW));
            this.endGameWithWinner(remaining);
        }
    }

    int getActiveParticipantCount() {
        return this.getParticipants().size();
    }

    boolean isActiveParticipant(ServerPlayerEntity player) {
        return this.context != null && this.context.roster().contains(player);
    }

    public boolean shouldCancelDamage(ServerPlayerEntity player, DamageSource source) {
        if (!this.isParticipant(player)) {
            return false;
        }

        if (this.state == GameState.STARTING) {
            if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
                return this.isParticipant(attacker);
            }
        }

        return false;
    }

    public boolean canStartMatch() {
        return this.getParticipants().size() >= 2;
    }

    public void applySettings(BountyHuntSettings settings) {
        this.settings = settings == null ? BountyHuntSettings.defaults() : settings;
    }

    public BountyHuntSettings getSettings() {
        return this.settings;
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

    @Nullable
    public ServerPlayerEntity getTarget(ServerPlayerEntity player) {
        UUID target = this.targetAssignments.get(player.getUuid());
        return target == null ? null : this.getPlayerByUuid(target);
    }

    public void syncTrackerTarget(ServerPlayerEntity player, boolean announce) {
        if (!this.settings.trackerEnabled()) {
            return;
        }

        ServerPlayerEntity target = this.getTarget(player);
        if (target == null) {
            if (announce) {
                player.sendMessage(Text.literal("No valid target right now."), true);
            }
            return;
        }

        Optional<GlobalPos> trackingTarget = this.resolveTrackingTarget(player, target);
        if (trackingTarget.isEmpty()) {
            this.setTrackerMad(player, target);
            if (announce) {
                player.sendMessage(Text.literal("Target not traceable in this dimension."), true);
            }
            return;
        }

        this.updateTrackerStacks(player, trackingTarget.get(), target);
        if (announce) {
            player.sendMessage(Text.literal("Tracking: " + target.getName().getString()).formatted(Formatting.AQUA), true);
        }
    }

    public void cycleTrackerCooldown(ServerPlayerEntity player) {
        if (!this.settings.trackerEnabled()) {
            return;
        }

        long cooldownUntil = this.compassCooldownUntilTicks.getOrDefault(player.getUuid(), 0L);
        if (cooldownUntil > this.gameTicks) {
            long seconds = Math.max(1L, (cooldownUntil - this.gameTicks + 19L) / 20L);
            player.sendMessage(Text.literal("Tracker cooldown: " + seconds + "s").formatted(Formatting.YELLOW), true);
            return;
        }

        if (this.settings.compassCooldownSeconds() > 0) {
            this.compassCooldownUntilTicks.put(player.getUuid(), this.gameTicks + this.settings.compassCooldownSeconds() * 20L);
        }
        this.syncTrackerTarget(player, true);
    }

    private void beginHunt() {
        if (this.state == GameState.RUNNING) {
            return;
        }
        this.state = GameState.RUNNING;
        this.setRuntimeState(GameState.RUNNING);
        this.broadcastMessage(Text.literal("Hunt is live!").formatted(Formatting.GREEN));
    }

    private void tickGracePeriod() {
        if (this.state != GameState.STARTING) {
            return;
        }

        this.graceTicksRemaining = Math.max(0, this.graceTicksRemaining - 20);
        int secondsLeft = this.graceTicksRemaining / 20;
        this.maybeAnnounceGraceCountdown(secondsLeft);
        if (this.graceTicksRemaining <= 0) {
            this.broadcastGraceProtectionState(0, false);
            this.beginHunt();
        }
    }

    private void tickTargetSwap() {
        if (this.settings.targetSwapIntervalSeconds() <= 0 || this.state != GameState.RUNNING) {
            return;
        }

        this.targetSwapTicksRemaining = Math.max(0, this.targetSwapTicksRemaining - 20);
        if (this.targetSwapTicksRemaining > 0) {
            return;
        }

        this.targetSwapTicksRemaining = this.settings.targetSwapIntervalSeconds() * 20;
        
        for (UUID currentTarget : this.targetAssignments.values()) {
            int current = this.targetBountyValues.getOrDefault(currentTarget, 0);
            if (current < 3) {
                this.targetBountyValues.put(currentTarget, current + 1);
            }
        }
        
        this.assignInitialTargets();
        this.broadcastMessage(Text.literal("Target assignments refreshed! Check your tracker.").formatted(Formatting.YELLOW));
    }

    private void assignInitialTargets() {
        List<ServerPlayerEntity> participants = this.getParticipants();
        if (participants.size() < 2) {
            return;
        }

        ServerPlayerEntity[] original = participants.toArray(new ServerPlayerEntity[0]);
        ServerPlayerEntity[] shuffled = participants.toArray(new ServerPlayerEntity[0]);
        
        for (int i = shuffled.length - 1; i > 0; i--) {
            int j = ThreadLocalRandom.current().nextInt(i);
            ServerPlayerEntity temp = shuffled[i];
            shuffled[i] = shuffled[j];
            shuffled[j] = temp;
        }

        for (int i = 0; i < original.length; i++) {
            ServerPlayerEntity hunter = original[i];
            ServerPlayerEntity target = shuffled[i];
            this.targetAssignments.put(hunter.getUuid(), target.getUuid());
            this.scores.putIfAbsent(hunter.getUuid(), 0);
            hunter.sendMessage(Text.literal("Your target is: " + target.getName().getString()).formatted(Formatting.AQUA), false);
        }
    }

    private void assignNewTarget(ServerPlayerEntity hunter, boolean scored) {
        UUID oldTarget = this.targetAssignments.get(hunter.getUuid());
        if (!scored && oldTarget != null) {
            int current = this.targetBountyValues.getOrDefault(oldTarget, 0);
            if (current < 3) {
                this.targetBountyValues.put(oldTarget, current + 1);
            }
        }

        List<ServerPlayerEntity> candidates = this.getParticipants().stream()
            .filter(entry -> !entry.getUuid().equals(hunter.getUuid()))
            .toList();

        if (candidates.isEmpty()) {
            candidates = this.getParticipants().stream()
                .filter(entry -> !entry.getUuid().equals(hunter.getUuid()))
                .toList();
        }

        if (candidates.isEmpty()) {
            this.targetAssignments.remove(hunter.getUuid());
            hunter.sendMessage(Text.literal("No valid targets remaining.").formatted(Formatting.YELLOW), true);
            return;
        }

        Map<UUID, Integer> hunterCounts = new java.util.HashMap<>();
        for (ServerPlayerEntity candidate : candidates) {
            hunterCounts.put(candidate.getUuid(), 0);
        }
        for (UUID targetId : this.targetAssignments.values()) {
            hunterCounts.computeIfPresent(targetId, (k, v) -> v + 1);
        }

        int minHunters = hunterCounts.values().stream().min(Integer::compareTo).orElse(0);
        List<ServerPlayerEntity> bestCandidates = candidates.stream()
            .filter(p -> hunterCounts.get(p.getUuid()) == minHunters)
            .toList();

        ServerPlayerEntity target = bestCandidates.get(ThreadLocalRandom.current().nextInt(bestCandidates.size()));
        this.targetAssignments.put(hunter.getUuid(), target.getUuid());
        hunter.sendMessage(Text.literal("Your target is: " + target.getName().getString()).formatted(Formatting.AQUA), true);
        this.syncTrackerTarget(hunter, true);
        hunter.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
    }

    private void grantTrackersToParticipants() {
        for (ServerPlayerEntity player : this.getParticipants()) {
            this.grantTracker(player);
            this.syncTrackerTarget(player, false);
        }
    }

    private void grantTracker(ServerPlayerEntity player) {
        if (!this.settings.trackerEnabled()) {
            return;
        }

        if (this.playerHasTracker(player)) {
            return;
        }

        Item trackerItem = this.resolveTrackerItem();
        ItemStack stack = trackerItem.getDefaultStack();
        this.markTracker(stack);
        player.getInventory().insertStack(stack);
    }

    private Item resolveTrackerItem() {
        try {
            Identifier id = Identifier.of(this.settings.trackerItemId());
            Item item = Registries.ITEM.get(id);
            return item == null || item == Items.AIR ? Items.COMPASS : item;
        } catch (IllegalArgumentException ignored) {
            return Items.COMPASS;
        }
    }

    private boolean playerHasTracker(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (this.isTrackerItem(player.getInventory().getStack(slot))) {
                return true;
            }
        }
        return false;
    }

    public boolean isTrackerItem(ItemStack stack) {
        Item trackerItem = this.resolveTrackerItem();
        if (!stack.isOf(trackerItem)) {
            return false;
        }
        return ProtectedItemTags.hasType(stack, TRACKER_TYPE);
    }

    private void markTracker(ItemStack stack) {
        ProtectedItemTags.mark(stack, TRACKER_TYPE);
    }

    private void updateTrackers() {
        for (ServerPlayerEntity player : this.getParticipants()) {
            if (player.isDisconnected()) {
                continue;
            }
            this.syncTrackerTarget(player, false);
        }
    }

    private Optional<GlobalPos> resolveTrackingTarget(ServerPlayerEntity hunter, ServerPlayerEntity target) {
        return this.playerTracker.resolveTrackingTarget(hunter, target, this.settings.netherTrackingEnabled());
    }

    private void updateTrackerStacks(ServerPlayerEntity hunter, GlobalPos target, ServerPlayerEntity trackedPlayer) {
        boolean isCompass = this.resolveTrackerItem().equals(Items.COMPASS);
        this.playerTracker.updateTrackerStacks(hunter, trackedPlayer, Optional.of(target), this::isTrackerItem, isCompass);
    }

    private void setTrackerMad(ServerPlayerEntity hunter, ServerPlayerEntity trackedPlayer) {
        boolean isCompass = this.resolveTrackerItem().equals(Items.COMPASS);
        this.playerTracker.updateTrackerStacks(hunter, trackedPlayer, Optional.empty(), this::isTrackerItem, isCompass);
    }

    private void registerProtectedItems() {
        ProtectedItemService service = ProtectedItemService.getInstance();
        service.clearRules();
        service.registerRule(ProtectedItemRule.builder(TRACKER_TYPE)
            .preventDrop()
            .preventExternalStorage()
            .preventDeletion()
            .preventDeathLoss()
            .preventDuplication()
            .autoRestore()
            .allowRearrange(true)
            .allowOffhandSwap(true)
            .maxStacks(1)
            .cleanupWorldDrops()
            .canHold(player -> this.settings.trackerEnabled() && this.isParticipant(player))
            .shouldHave(player -> this.settings.trackerEnabled() && this.isParticipant(player))
            .restoreAction(this::grantTracker)
            .build());
    }

    private void maybeAnnounceGraceCountdown(int secondsLeft) {
        if (secondsLeft <= 0) {
            return;
        }

        boolean milestone = secondsLeft == 60
            || secondsLeft == 30
            || secondsLeft == 10
            || secondsLeft == 5
            || secondsLeft == 4
            || secondsLeft == 3
            || secondsLeft == 2
            || secondsLeft == 1;
        if (!milestone) {
            return;
        }

        if (this.graceCountdowns.announceOnce(this.getParticipants(), secondsLeft, Text.empty())) {
            this.broadcastMessage(Text.literal("Grace period active: PvP enabled in " + this.formatDuration(secondsLeft) + ".")
                .formatted(Formatting.YELLOW));
        }
    }

    private String formatDuration(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private List<ServerPlayerEntity> getParticipants() {
        return this.context != null ? this.context.liveParticipants() : List.of();
    }

    private boolean isParticipant(ServerPlayerEntity player) {
        return this.context != null && this.context.roster().contains(player);
    }

    private void removeParticipant(ServerPlayerEntity player) {
        if (this.context != null) this.context.roster().remove(player);
    }

    private void replaceParticipant(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (this.context != null) {
            this.context.roster().remove(oldPlayer);
            this.context.roster().add(newPlayer);
        }
    }

    private void setRuntimeState(GameState state) {
        if (this.context != null) this.context.setState(state);
    }



    @Nullable
    @Override
    protected ServerPlayerEntity getPlayerByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (ServerPlayerEntity participant : this.getParticipants()) {
            if (participant.getUuid().equals(uuid)) {
                return participant;
            }
        }
        return this.server == null ? null : this.server.getPlayerManager().getPlayer(uuid);
    }

    private void rebuildScoreboard() {
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("Bounty Hunt").formatted(Formatting.GOLD, Formatting.BOLD)));
            this.scoreboard.show(this.getParticipants());
        }

        this.scoreboard.clearLines();
        this.graceLine = this.scoreboard.addLine(Text.empty());
        this.scoreboard.addBlankLine();

        List<ServerPlayerEntity> participants = this.getParticipants();
        List<ServerPlayerEntity> sorted = participants.stream()
            .sorted((p1, p2) -> Integer.compare(
                this.scores.getOrDefault(p2.getUuid(), 0),
                this.scores.getOrDefault(p1.getUuid(), 0)))
            .toList();

        for (ServerPlayerEntity player : sorted) {
            int score = this.scores.getOrDefault(player.getUuid(), 0);
            int bounty = this.targetBountyValues.getOrDefault(player.getUuid(), 0);
            String bountyDisplay = bounty > 0 ? " [bounty:" + bounty + "]" : "";
            this.scoreboard.addLine(Text.literal(player.getName().getString() + ": " + score + bountyDisplay));
        }

        this.scoreboard.addBlankLine();
        this.scoreboard.addLine(Text.literal("Goal: " + this.settings.scoreToWin()).formatted(Formatting.YELLOW));
        
        this.scoreboard.resendStructure();
        this.updateScoreboardTick();
    }

    private void updateScoreboardTick() {
        if (this.graceLine == null) return;
        int graceSeconds = this.state == GameState.STARTING ? Math.max(0, this.graceTicksRemaining / 20) : 0;
        if (graceSeconds > 0) {
            this.graceLine.setText(Text.literal("Grace: " + graceSeconds));
        } else {
            this.graceLine.setText(Text.literal("PvP Enabled").formatted(Formatting.RED));
        }
        this.graceLine.updateAll();
    }

    private void clearScoreboard() {
        if (this.server == null) {
            return;
        }
        if (this.scoreboard != null) {
            this.scoreboard.cleanup(this.server);
        }
        this.clearVanillaTeams();
    }

    private void rebuildSoloTeams(List<ServerPlayerEntity> participants) {
        this.teamManager.clear();
        for (ServerPlayerEntity player : participants) {
            String teamId = "solo_" + player.getUuidAsString();
            this.teamManager.assign(player, teamId, player.getName().getString(), dev.frost.miniverse.team.TeamRole.MEMBER);
        }
        this.syncVanillaTeams();
    }

    @Override
    protected void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }

        VanillaTeamAdapter adapter = this.getVanillaTeams();
        if (adapter == null) return;
        adapter.syncSnapshots(this.server, this.teamManager.snapshots(), snapshot -> {
            Formatting color = adapter.colorFor(snapshot.id());
            return VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(Text.literal("[" + dev.frost.miniverse.team.TeamColorPalette.labelFor(snapshot.id()) + "] ").formatted(color))
                .withFriendlyFireAllowed(true)
                .withCollisionRule(AbstractTeam.CollisionRule.ALWAYS);
        });
    }

    @Override
    protected void clearVanillaTeams() {
        VanillaTeamAdapter adapter = this.getVanillaTeams();
        if (this.server != null && adapter != null) {
            adapter.clear(this.server);
        }
    }

    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(this.getParticipants(), message);
    }

    private void sendGraceProtectionStatesTo(ServerPlayerEntity recipient) {
        if (this.state != GameState.STARTING || this.graceTicksRemaining <= 0) {
            return;
        }

        for (ServerPlayerEntity participant : this.getParticipants()) {
            if (participant.isDisconnected()) {
                continue;
            }
            ProtectionOverlaySender.send(
                recipient,
                participant.getUuid(),
                GRACE_PROTECTION_OVERLAY,
                this.graceTicksRemaining,
                true,
                BOUNTY_PROTECTION_COLOR,
                BOUNTY_PROTECTION_OVERLAY
            );
        }
    }

    private void broadcastGraceProtectionState(int remainingTicks, boolean active) {
        if (this.server == null) {
            return;
        }
        for (ServerPlayerEntity protectedParticipant : this.getParticipants()) {
            if (protectedParticipant.isDisconnected()) {
                continue;
            }
            if (active) {
                ProtectionOverlaySender.broadcast(
                    this.server,
                    protectedParticipant.getUuid(),
                    GRACE_PROTECTION_OVERLAY,
                    remainingTicks,
                    true,
                    BOUNTY_PROTECTION_COLOR,
                    BOUNTY_PROTECTION_OVERLAY
                );
            } else {
                ProtectionOverlaySender.broadcastClearOverlay(this.server, protectedParticipant.getUuid(), GRACE_PROTECTION_OVERLAY);
            }
        }
    }
    private void clearGraceProtectionStates() {
        if (this.state != GameState.STARTING && this.graceTicksRemaining <= 0) {
            return;
        }
        broadcastGraceProtectionState(0, false);
    }

    private void endGameWithWinner(ServerPlayerEntity winner) {
        this.state = GameState.ENDING;
        this.setRuntimeState(GameState.ENDING);

        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("🏆 " + winner.getName().getString() + " wins! 🏆").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.startStandardEndSequence(MatchEndResult.winner(winner));
        this.clearScoreboard();
    }

    private void startStandardEndSequence(MatchEndResult result) {
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(runtime, result, MatchLifecycleOptions.defaults(NAME));
        }
    }



    @Override
    public JsonObject saveRuntimeState() {
        JsonObject root = new JsonObject();
        root.addProperty("state", this.state.name());
        root.addProperty("gameTicks", this.gameTicks);
        root.addProperty("tickCounter", this.tickCounter);
        root.addProperty("graceTicksRemaining", this.graceTicksRemaining);
        root.addProperty("targetSwapTicksRemaining", this.targetSwapTicksRemaining);
        root.add("settings", this.writeSettings());
        root.add("participants", writeUuidArray(this.context == null ? Set.of() : this.context.participantIds()));
        root.add("targetAssignments", writeUuidUuidMap(this.targetAssignments));
        root.add("scores", writeUuidIntMap(this.scores));
        root.add("killStreaks", writeUuidIntMap(this.killStreaks));
        root.add("targetBountyValues", writeUuidIntMap(this.targetBountyValues));
        root.add("compassCooldownUntilTicks", writeUuidLongMap(this.compassCooldownUntilTicks));
        root.add("trackingData", this.playerTracker.writeTrackingData());
        return root;
    }

    @Override
    public void loadRuntimeState(JsonObject root) {
        if (root == null) {
            return;
        }
        this.targetAssignments.clear();
        this.scores.clear();
        this.killStreaks.clear();
        this.targetBountyValues.clear();
        this.compassCooldownUntilTicks.clear();
        this.playerTracker.clear();
        this.graceCountdowns.reset();

        if (root.has("settings") && root.get("settings").isJsonObject()) {
            this.settings = readSettings(root.getAsJsonObject("settings"), this.settings);
        }
        this.state = parseState(stringValue(root, "state", GameState.WAITING_FOR_PLAYERS.name()));
        this.setRuntimeState(this.state);
        this.gameTicks = longValue(root, "gameTicks", 0L);
        this.tickCounter = intValue(root, "tickCounter", 0);
        this.graceTicksRemaining = intValue(root, "graceTicksRemaining", 0);
        this.targetSwapTicksRemaining = intValue(root, "targetSwapTicksRemaining", this.settings.targetSwapIntervalSeconds() * 20);
        if (this.context != null) {
            for (UUID playerId : readUuidArray(root, "participants")) {
                this.context.roster().add(playerId);
            }
        }
        this.targetAssignments.putAll(readUuidUuidMap(root, "targetAssignments"));
        this.scores.putAll(readUuidIntMap(root, "scores"));
        this.killStreaks.putAll(readUuidIntMap(root, "killStreaks"));
        this.targetBountyValues.putAll(readUuidIntMap(root, "targetBountyValues"));
        this.compassCooldownUntilTicks.putAll(readUuidLongMap(root, "compassCooldownUntilTicks"));
        if (root.has("trackingData") && root.get("trackingData").isJsonArray()) {
            this.playerTracker.readTrackingData(root.getAsJsonArray("trackingData"));
        }
        this.registerProtectedItems();
        this.syncVanillaTeams();
        this.rebuildScoreboard();
    }

    private List<ServerPlayerEntity> getCurrentLeaders() {
        if (this.scores.isEmpty()) return List.of();
        int maxScore = this.scores.values().stream().max(Integer::compareTo).orElse(0);
        return this.getParticipants().stream()
            .filter(p -> this.scores.getOrDefault(p.getUuid(), 0) == maxScore)
            .toList();
    }

    private void updateHighValueTargetOverlay() {
        if (!this.settings.highValueTargetEnabled() || this.server == null) {
            return;
        }
        List<ServerPlayerEntity> leaders = this.getCurrentLeaders();
        Set<UUID> leaderUuids = leaders.size() == 1 ? Set.of(leaders.get(0).getUuid()) : Set.of();
        
        for (ServerPlayerEntity p : this.getParticipants()) {
            if (leaderUuids.contains(p.getUuid())) {
                ProtectionOverlaySender.broadcast(this.server, p.getUuid(), LEADER_OVERLAY, 20, true, LEADER_COLOR, LEADER_OVERLAY_SETTINGS);
            } else {
                ProtectionOverlaySender.broadcastClearOverlay(this.server, p.getUuid(), LEADER_OVERLAY);
            }
        }
    }

    private JsonObject writeSettings() {
        JsonObject settingsJson = new JsonObject();
        settingsJson.addProperty("gracePeriodSeconds", this.settings.gracePeriodSeconds());
        settingsJson.addProperty("scoreToWin", this.settings.scoreToWin());
        settingsJson.addProperty("targetSwapIntervalSeconds", this.settings.targetSwapIntervalSeconds());
        settingsJson.addProperty("trackerEnabled", this.settings.trackerEnabled());
        settingsJson.addProperty("netherTrackingEnabled", this.settings.netherTrackingEnabled());
        settingsJson.addProperty("compassCooldownSeconds", this.settings.compassCooldownSeconds());
        settingsJson.addProperty("trackerItemId", this.settings.trackerItemId());
        settingsJson.addProperty("disconnectGraceSeconds", this.settings.disconnectGraceSeconds());
        settingsJson.addProperty("highValueTargetEnabled", this.settings.highValueTargetEnabled());
        settingsJson.addProperty("revengeAssignmentEnabled", this.settings.revengeAssignmentEnabled());
        settingsJson.addProperty("respawnDelaySeconds", this.settings.respawnDelaySeconds());
        return settingsJson;
    }

    private static BountyHuntSettings readSettings(JsonObject root, BountyHuntSettings fallback) {
        BountyHuntSettings base = fallback == null ? BountyHuntSettings.defaults() : fallback;
        return new BountyHuntSettings(
            intValue(root, "gracePeriodSeconds", base.gracePeriodSeconds()),
            intValue(root, "scoreToWin", base.scoreToWin()),
            intValue(root, "targetSwapIntervalSeconds", base.targetSwapIntervalSeconds()),
            booleanValue(root, "trackerEnabled", base.trackerEnabled()),
            booleanValue(root, "netherTrackingEnabled", base.netherTrackingEnabled()),
            intValue(root, "compassCooldownSeconds", base.compassCooldownSeconds()),
            stringValue(root, "trackerItemId", base.trackerItemId()),
            intValue(root, "disconnectGraceSeconds", base.disconnectGraceSeconds()),
            booleanValue(root, "highValueTargetEnabled", base.highValueTargetEnabled()),
            booleanValue(root, "revengeAssignmentEnabled", base.revengeAssignmentEnabled()),
            intValue(root, "respawnDelaySeconds", base.respawnDelaySeconds())
        );
    }





    private static void putBlockPos(JsonObject object, String key, @Nullable BlockPos pos) {
        if (pos == null) {
            return;
        }
        JsonObject value = new JsonObject();
        value.addProperty("x", pos.getX());
        value.addProperty("y", pos.getY());
        value.addProperty("z", pos.getZ());
        object.add(key, value);
    }

    @Nullable
    private static BlockPos readBlockPos(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) {
            return null;
        }
        JsonObject value = object.getAsJsonObject(key);
        return new BlockPos(intValue(value, "x", 0), intValue(value, "y", 0), intValue(value, "z", 0));
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
            return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(true, null, net.minecraft.text.Text.literal("Match paused! Not enough players.").formatted(net.minecraft.util.Formatting.RED));
        }
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }

    @Override
    public void onRosterChanged(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        if (this.state != GameState.RUNNING && this.state != GameState.STARTING) {
            return;
        }
        List<ServerPlayerEntity> online = roster.onlinePlayers(this.context != null ? this.context.nullableServer() : null);
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(this.targetAssignments.entrySet())) {
            ServerPlayerEntity target = this.getPlayerByUuid(entry.getValue());
            if (target == null || target.isDisconnected() || !online.contains(target)) {
                ServerPlayerEntity hunter = this.getPlayerByUuid(entry.getKey());
                if (hunter != null && !hunter.isDisconnected()) {
                    this.assignNewTarget(hunter, false);
                    this.syncTrackerTarget(hunter, false);
                }
            }
        }
    }
}
