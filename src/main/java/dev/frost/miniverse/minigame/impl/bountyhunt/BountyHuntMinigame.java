package dev.frost.miniverse.minigame.impl.bountyhunt;

import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.ScoreboardController;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.sound.SoundEvents;

public class BountyHuntMinigame implements Minigame, RuntimeContextAware, ServerTickAware, ItemUseAware, PlayerDamageAware, EntityDeathAware, PlayerRespawnAware, PlayerLeaveAware, PlayerJoinAware {
    private static final String NAME = "Bounty Hunt";
    private static final String TRACKER_TYPE = ProtectedItemTypes.TRACKER_COMPASS;
    private static final String SCOREBOARD_OBJECTIVE = "bountyhunt_display";
    private static final Identifier RESPAWN_PROTECTION_OVERLAY = ProtectionOverlayPresets.RESPAWN_PROTECTION.overlayId();
    private static final Identifier GRACE_PROTECTION_OVERLAY = ProtectionOverlayPresets.GRACE_PERIOD.overlayId();
    private static final int BOUNTY_PROTECTION_COLOR = 0xE6FFFFFF;
    private static final ProtectionOverlaySettings BOUNTY_PROTECTION_OVERLAY = ProtectionOverlaySettings.DEFAULT
        .withStyle(ProtectionOverlayStyle.VANILLA_GLOW)
        .withRenderMode(ProtectionOverlayRenderMode.DEPTH_TESTED)
        .withGlowColor(0xFFFFFFFF)
        .withOutlineColor(0xFFFFFFFF)
        .withAlpha(0.82F)
        .withIntensity(1.0F);
    private static final ScoreboardController SCOREBOARD = new ScoreboardController(SCOREBOARD_OBJECTIVE, Text.literal("Bounty Hunt"));
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("bountyhunt");

    private final Map<UUID, UUID> targetAssignments = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
    private final Map<UUID, Long> invincibleUntilTicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> compassCooldownUntilTicks = new ConcurrentHashMap<>();
    private final Map<UUID, TrackingData> trackingData = new ConcurrentHashMap<>();
    private final Set<Integer> announcedGraceThresholds = ConcurrentHashMap.newKeySet();

    private GameState state;
    private BountyHuntSettings settings;
    private MinigameContext context;
    private MinecraftServer server;
    private long gameTicks;
    private int tickCounter;
    private int graceTicksRemaining;
    private int targetSwapTicksRemaining;

    public BountyHuntMinigame() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.applySettings(BountyHuntSettings.defaults());
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
        this.targetAssignments.clear();
        this.scores.clear();
        this.invincibleUntilTicks.clear();
        this.compassCooldownUntilTicks.clear();
        this.trackingData.clear();
        this.announcedGraceThresholds.clear();
        this.gameTicks = 0L;
        this.tickCounter = 0;
        this.graceTicksRemaining = 0;
        this.targetSwapTicksRemaining = 0;
    }

    @Override
    public void startGame() {
        this.state = GameState.STARTING;
        this.setRuntimeState(GameState.STARTING);
        this.registerProtectedItems();
        this.targetAssignments.clear();
        this.scores.clear();
        this.invincibleUntilTicks.clear();
        this.compassCooldownUntilTicks.clear();
        this.trackingData.clear();
        this.announcedGraceThresholds.clear();
        this.gameTicks = 0L;
        this.tickCounter = 0;
        this.graceTicksRemaining = this.settings.gracePeriodSeconds() * 20;
        this.targetSwapTicksRemaining = this.settings.targetSwapIntervalSeconds() * 20;

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
        this.updateScoreboard();
    }

    @Override
    public void stopGame() {
        this.state = GameState.ENDING;
        this.targetAssignments.clear();
        this.scores.clear();
        this.clearInvincibilityStates();
        this.clearGraceProtectionStates();
        this.compassCooldownUntilTicks.clear();
        this.trackingData.clear();
        this.clearScoreboard();
        this.clearVanillaTeams();
        this.clearParticipants();
        ProtectedItemService.getInstance().clearRules();
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        this.handlePlayerDeath(player, null);
    }

    public void handlePlayerDeath(ServerPlayerEntity player, @Nullable DamageSource source) {
        if (!this.isParticipant(player)) {
            return;
        }

        ServerPlayerEntity killer = source != null && source.getAttacker() instanceof ServerPlayerEntity attacker
            ? attacker
            : null;

        boolean scored = false;
        if (killer != null && this.isParticipant(killer)) {
            UUID expectedTarget = this.targetAssignments.get(killer.getUuid());
            if (expectedTarget != null && expectedTarget.equals(player.getUuid())) {
                int newScore = this.scores.merge(killer.getUuid(), 1, Integer::sum);
                scored = true;
                killer.sendMessage(Text.literal("Target eliminated! +1 point (" + newScore + "/" + this.settings.scoreToWin() + ")")
                    .formatted(Formatting.GOLD), true);
                if (newScore >= this.settings.scoreToWin()) {
                    this.endGameWithWinner(killer);
                    return;
                }
            } else {
                killer.sendMessage(Text.literal("No point: that player was not your assigned target.")
                    .formatted(Formatting.RED), true);
            }
        }

        this.assignNewTarget(player);
        this.grantTracker(player);

        if (scored && killer != null) {
            this.assignNewTarget(killer);
            this.syncTrackerTarget(killer, true);
        }

        this.updateScoreboard();
    }

    public void onServerTick(MinecraftServer server) {
        this.server = server;
        if (this.state == GameState.ENDING) {
            return;
        }

        this.gameTicks++;
        this.tickCounter++;
        if (this.tickCounter >= 20) {
            this.tickCounter = 0;
            this.updateTrackingData();
            this.tickGracePeriod();
            this.tickTargetSwap();
            this.tickInvincibilityWindows();
            if (this.state == GameState.IN_PROGRESS && this.settings.trackerEnabled()) {
                this.updateTrackers();
            }
            this.updateScoreboard();
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
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayerEntity player && this.isParticipant(player)) {
            this.handlePlayerDeath(player, source);
        }
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (this.isParticipant(oldPlayer)) {
            this.replaceParticipant(oldPlayer, newPlayer);
            this.handlePlayerRespawn(oldPlayer, newPlayer);
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.isParticipant(player)) {
            this.removeParticipant(player);
            this.handlePlayerLeave(player);
        }
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        this.sendInvincibilityStatesTo(player);
        this.sendGraceProtectionStatesTo(player);
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        this.grantTracker(newPlayer);
        this.syncTrackerTarget(newPlayer, false);
        this.applyRespawnInvincibility(newPlayer);
        this.syncVanillaTeams();
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        this.targetAssignments.remove(playerUuid);
        this.scores.remove(playerUuid);
        this.invincibleUntilTicks.remove(playerUuid);
        this.compassCooldownUntilTicks.remove(playerUuid);
        this.trackingData.remove(playerUuid);
        ProtectionOverlaySender.broadcastClearOverlay(this.server, playerUuid, RESPAWN_PROTECTION_OVERLAY);
        if (this.state == GameState.STARTING && this.graceTicksRemaining > 0) {
            ProtectionOverlaySender.broadcastClearOverlay(this.server, playerUuid, GRACE_PROTECTION_OVERLAY);
        }

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(this.targetAssignments.entrySet())) {
            if (playerUuid.equals(entry.getValue())) {
                ServerPlayerEntity hunter = this.getPlayerByUuid(entry.getKey());
                if (hunter != null) {
                    this.assignNewTarget(hunter);
                    this.syncTrackerTarget(hunter, false);
                }
            }
        }

        if (this.getParticipants().size() <= 1 && this.state != GameState.ENDING) {
            if (!MatchLifecycleController.getInstance().isDisconnectGraceActiveFor(player.getUuid())) {
                ServerPlayerEntity remaining = this.getParticipants().stream().findFirst().orElse(null);
                if (remaining != null) {
                    this.endGameWithWinner(remaining);
                }
            }
        }
        this.syncVanillaTeams();
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
        return this.context != null && this.context.participants().contains(player);
    }

    public boolean shouldCancelDamage(ServerPlayerEntity player, DamageSource source) {
        if (!this.isParticipant(player)) {
            return false;
        }

        if (this.isInvincible(player)) {
            this.notifyInvincibleHit(source);
            return true;
        }

        if (this.state == GameState.STARTING && source.getAttacker() instanceof ServerPlayerEntity attacker) {
            return this.isParticipant(attacker);
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
            this.compassCooldownUntilTicks.put(player.getUuid(), this.gameTicks + (long) this.settings.compassCooldownSeconds() * 20L);
        }
        this.syncTrackerTarget(player, true);
    }

    private void beginHunt() {
        if (this.state == GameState.IN_PROGRESS) {
            return;
        }
        this.state = GameState.IN_PROGRESS;
        this.setRuntimeState(GameState.IN_PROGRESS);
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
        if (this.settings.targetSwapIntervalSeconds() <= 0 || this.state != GameState.IN_PROGRESS) {
            return;
        }

        this.targetSwapTicksRemaining = Math.max(0, this.targetSwapTicksRemaining - 20);
        if (this.targetSwapTicksRemaining > 0) {
            return;
        }

        this.targetSwapTicksRemaining = this.settings.targetSwapIntervalSeconds() * 20;
        this.assignInitialTargets();
        this.broadcastMessage(Text.literal("Target assignments refreshed! Check your tracker.").formatted(Formatting.YELLOW));
    }

    private void assignInitialTargets() {
        List<ServerPlayerEntity> participants = this.getParticipants();
        if (participants.size() < 2) {
            return;
        }

        List<ServerPlayerEntity> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled);
        for (int i = 0; i < shuffled.size(); i++) {
            ServerPlayerEntity hunter = shuffled.get(i);
            ServerPlayerEntity target = shuffled.get((i + 1) % shuffled.size());
            this.targetAssignments.put(hunter.getUuid(), target.getUuid());
            this.scores.putIfAbsent(hunter.getUuid(), 0);
            hunter.sendMessage(Text.literal("Your target is: " + target.getName().getString()).formatted(Formatting.AQUA), false);
        }
    }

    private void assignNewTarget(ServerPlayerEntity hunter) {
        List<ServerPlayerEntity> candidates = this.getParticipants().stream()
            .filter(entry -> !entry.getUuid().equals(hunter.getUuid()))
            .toList();
        if (candidates.isEmpty()) {
            return;
        }

        ServerPlayerEntity target = candidates.get((int) (Math.random() * candidates.size()));
        this.targetAssignments.put(hunter.getUuid(), target.getUuid());
        hunter.sendMessage(Text.literal("Your target is: " + target.getName().getString()).formatted(Formatting.AQUA), true);
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
        TrackingData data = this.trackingData.computeIfAbsent(target.getUuid(), uuid -> new TrackingData());
        if (hunter.getEntityWorld().getRegistryKey().equals(World.NETHER)) {
            if (!this.settings.netherTrackingEnabled()) {
                return Optional.empty();
            }
            if (data.lastNether == null) {
                return Optional.empty();
            }
            return Optional.of(GlobalPos.create(World.NETHER, data.lastNether));
        }

        if (hunter.getEntityWorld().getRegistryKey().equals(World.END)) {
            BlockPos endPos = data.lastEnd != null ? data.lastEnd : target.getBlockPos();
            return Optional.of(GlobalPos.create(World.END, endPos));
        }

        BlockPos overworldPos = data.lastOverworld != null ? data.lastOverworld : target.getBlockPos();
        if (target.getEntityWorld().getRegistryKey().equals(World.END) && data.endEntryOverworld != null) {
            overworldPos = data.endEntryOverworld;
        }
        return Optional.of(GlobalPos.create(World.OVERWORLD, overworldPos));
    }

    private void updateTrackerStacks(ServerPlayerEntity hunter, GlobalPos target, ServerPlayerEntity trackedPlayer) {
        boolean isCompass = this.resolveTrackerItem().equals(Items.COMPASS);
        LodestoneTrackerComponent tracker = isCompass
            ? new LodestoneTrackerComponent(Optional.of(target), false)
            : null;
        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            ItemStack stack = hunter.getInventory().getStack(slot);
            if (!this.isTrackerItem(stack)) {
                continue;
            }
            if (tracker != null) {
                stack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
            }
        }
        TrackingItemNameFormatter.applyTrackingName(hunter.getInventory(), this::isTrackerItem, trackedPlayer.getDisplayName());
    }

    private void setTrackerMad(ServerPlayerEntity hunter, ServerPlayerEntity trackedPlayer) {
        boolean isCompass = this.resolveTrackerItem().equals(Items.COMPASS);
        LodestoneTrackerComponent tracker = isCompass
            ? new LodestoneTrackerComponent(Optional.empty(), true)
            : null;
        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            ItemStack stack = hunter.getInventory().getStack(slot);
            if (!this.isTrackerItem(stack)) {
                continue;
            }
            if (tracker != null) {
                stack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
            }
        }
        TrackingItemNameFormatter.applyTrackingName(hunter.getInventory(), this::isTrackerItem, trackedPlayer.getDisplayName());
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

    private void updateTrackingData() {
        for (ServerPlayerEntity player : this.getParticipants()) {
            if (player.isDisconnected()) {
                continue;
            }

            TrackingData data = this.trackingData.computeIfAbsent(player.getUuid(), uuid -> new TrackingData());
            BlockPos position = player.getBlockPos();
            if (player.getEntityWorld().getRegistryKey().equals(World.OVERWORLD)) {
                data.lastOverworld = position;
            } else if (player.getEntityWorld().getRegistryKey().equals(World.NETHER)) {
                if (this.settings.netherTrackingEnabled()) {
                    data.lastNether = position;
                }
            } else if (player.getEntityWorld().getRegistryKey().equals(World.END)) {
                data.lastEnd = position;
                if (data.endEntryOverworld == null && data.lastOverworld != null) {
                    data.endEntryOverworld = data.lastOverworld;
                }
            }
        }
    }

    private void applyRespawnInvincibility(ServerPlayerEntity player) {
        if (this.settings.respawnInvincibilitySeconds() <= 0) {
            this.invincibleUntilTicks.remove(player.getUuid());
            return;
        }

        int durationTicks = this.settings.respawnInvincibilitySeconds() * 20;
        this.invincibleUntilTicks.put(player.getUuid(), this.gameTicks + durationTicks);
        ProtectionOverlaySender.broadcast(
            this.server,
            player.getUuid(),
            RESPAWN_PROTECTION_OVERLAY,
            durationTicks,
            true,
            BOUNTY_PROTECTION_COLOR,
            BOUNTY_PROTECTION_OVERLAY
        );
        player.sendMessage(Text.literal("You are invincible for " + this.formatDuration(this.settings.respawnInvincibilitySeconds()) + " after respawn.")
            .formatted(Formatting.YELLOW), true);
    }

    private void tickInvincibilityWindows() {
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(this.invincibleUntilTicks.entrySet())) {
            long remainingTicks = entry.getValue() - this.gameTicks;
            if (remainingTicks > 0) {
                int secondsRemaining = (int) Math.max(1L, (remainingTicks + 19L) / 20L);
                ServerPlayerEntity player = this.getPlayerByUuid(entry.getKey());
                if (player != null && !player.isDisconnected()) {
                    player.sendMessage(Text.literal("Invincible: " + this.formatDuration(secondsRemaining))
                        .formatted(Formatting.GOLD), true);
                }
                continue;
            }

            this.invincibleUntilTicks.remove(entry.getKey());
            ProtectionOverlaySender.broadcastClearOverlay(this.server, entry.getKey(), RESPAWN_PROTECTION_OVERLAY);
            ServerPlayerEntity player = this.getPlayerByUuid(entry.getKey());
            if (player != null && !player.isDisconnected()) {
                player.sendMessage(Text.literal("Invincibility ended. You are vulnerable again.")
                    .formatted(Formatting.RED), true);
            }
        }
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

        if (this.announcedGraceThresholds.add(secondsLeft)) {
            this.broadcastMessage(Text.literal("Grace period active: PvP enabled in " + this.formatDuration(secondsLeft) + ".")
                .formatted(Formatting.YELLOW));
        }
    }

    private String formatDuration(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private boolean isInvincible(ServerPlayerEntity player) {
        return this.invincibleUntilTicks.getOrDefault(player.getUuid(), 0L) > this.gameTicks;
    }

    private List<ServerPlayerEntity> getParticipants() {
        return this.context().liveParticipants();
    }

    private boolean isParticipant(ServerPlayerEntity player) {
        return this.context().participants().contains(player);
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
            throw new IllegalStateException("Bounty Hunt runtime context is not attached.");
        }
        return this.context;
    }

    @Nullable
    private ServerPlayerEntity getPlayerByUuid(UUID uuid) {
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

    private void updateScoreboard() {
        if (this.server == null) {
            return;
        }
        this.syncVanillaTeams();

        SCOREBOARD.setScore(this.server, "Score To Win", this.settings.scoreToWin());
        int graceSeconds = this.state == GameState.STARTING ? Math.max(0, this.graceTicksRemaining / 20) : 0;
        SCOREBOARD.setScore(this.server, "Grace", graceSeconds);

        for (ServerPlayerEntity player : this.getParticipants()) {
            int score = this.scores.getOrDefault(player.getUuid(), 0);
            SCOREBOARD.setScore(this.server, player.getName().getString(), score);
        }
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

        List<TeamSnapshot> snapshots = new ArrayList<>();
        for (ServerPlayerEntity player : this.getParticipants()) {
            TeamRole role = this.targetAssignments.containsValue(player.getUuid()) ? TeamRole.TARGET : TeamRole.HUNTER;
            snapshots.add(new TeamSnapshot("player_" + player.getUuidAsString(), player.getName().getString(), List.of(TeamMembership.of(player, role))));
        }
        this.vanillaTeams.syncSnapshots(this.server, snapshots, snapshot -> {
            TeamRole role = snapshot.members().isEmpty() ? TeamRole.MEMBER : snapshot.members().get(0).role();
            Formatting color = role == TeamRole.TARGET ? Formatting.YELLOW : this.vanillaTeams.colorFor(snapshot.id());
            String prefix = role == TeamRole.TARGET ? "[TARGET] " : "[HUNTER] ";
            return VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(Text.literal(prefix).formatted(color))
                .withFriendlyFireAllowed(true)
                .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
        });
    }

    private void clearVanillaTeams() {
        if (this.server != null) {
            this.vanillaTeams.clear(this.server);
        }
    }

    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(this.getParticipants(), message);
    }

    private void notifyInvincibleHit(DamageSource source) {
        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker)) {
            return;
        }
        if (!this.isParticipant(attacker)) {
            return;
        }
        attacker.sendMessage(Text.literal("This target is invincible").formatted(Formatting.YELLOW), false);
        attacker.playSound(SoundEvents.ITEM_SHIELD_BLOCK, 1.0F, 1.0F);
    }

    private void sendInvincibilityStatesTo(ServerPlayerEntity player) {
        for (Map.Entry<UUID, Long> entry : this.invincibleUntilTicks.entrySet()) {
            long remainingTicks = entry.getValue() - this.gameTicks;
            if (remainingTicks <= 0) {
                continue;
            }
            ProtectionOverlaySender.send(
                player,
                entry.getKey(),
                RESPAWN_PROTECTION_OVERLAY,
                (int) remainingTicks,
                true,
                BOUNTY_PROTECTION_COLOR,
                BOUNTY_PROTECTION_OVERLAY
            );
        }
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

    private void clearInvincibilityStates() {
        for (UUID playerId : new ArrayList<>(this.invincibleUntilTicks.keySet())) {
            ProtectionOverlaySender.broadcastClearOverlay(this.server, playerId, RESPAWN_PROTECTION_OVERLAY);
        }
        this.invincibleUntilTicks.clear();
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
            MatchLifecycleController.getInstance().endMatch(runtime, result, MatchLifecycleOptions.defaults(NAME));
        }
    }

    private static final class TrackingData {
        private BlockPos lastOverworld;
        private BlockPos lastNether;
        private BlockPos lastEnd;
        private BlockPos endEntryOverworld;
    }
}
