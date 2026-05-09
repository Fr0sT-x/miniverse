package dev.frost.miniverse.minigame.impl.bountyhunt;

import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.ScoreboardController;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamDescriptor;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
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

public class BountyHuntMinigame implements Minigame {
    private static final String NAME = "Bounty Hunt";
    private static final String TRACKER_TAG = "BountyHunt_tracker";
    private static final String SCOREBOARD_OBJECTIVE = "bountyhunt_display";
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
        MinigameManager.getInstance().setCurrentState(GameState.STARTING);
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
        this.invincibleUntilTicks.clear();
        this.compassCooldownUntilTicks.clear();
        this.trackingData.clear();
        this.clearScoreboard();
        this.clearVanillaTeams();
        MinigameManager.getInstance().clearParticipants();
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        this.handlePlayerDeath(player, null);
    }

    public void handlePlayerDeath(ServerPlayerEntity player, @Nullable DamageSource source) {
        if (!MinigameManager.getInstance().isParticipant(player)) {
            return;
        }

        ServerPlayerEntity killer = source != null && source.getAttacker() instanceof ServerPlayerEntity attacker
            ? attacker
            : null;

        boolean scored = false;
        if (killer != null && MinigameManager.getInstance().isParticipant(killer)) {
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
        this.applyRespawnInvincibility(player);

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
                this.cleanupDroppedTrackers();
            }
            this.updateScoreboard();
        }
    }

    public void handlePlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        this.grantTracker(newPlayer);
        this.syncTrackerTarget(newPlayer, false);
        this.syncVanillaTeams();
    }

    public void handlePlayerLeave(ServerPlayerEntity player) {
        UUID playerUuid = player.getUuid();
        this.targetAssignments.remove(playerUuid);
        this.scores.remove(playerUuid);
        this.invincibleUntilTicks.remove(playerUuid);
        this.compassCooldownUntilTicks.remove(playerUuid);
        this.trackingData.remove(playerUuid);

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
            ServerPlayerEntity remaining = this.getParticipants().stream().findFirst().orElse(null);
            if (remaining != null) {
                this.endGameWithWinner(remaining);
            }
        }
        this.syncVanillaTeams();
    }

    public boolean shouldCancelDamage(ServerPlayerEntity player, DamageSource source) {
        if (!MinigameManager.getInstance().isParticipant(player)) {
            return false;
        }

        if (this.isInvincible(player)) {
            return true;
        }

        if (this.state == GameState.STARTING && source.getAttacker() instanceof ServerPlayerEntity attacker) {
            return MinigameManager.getInstance().isParticipant(attacker);
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
            this.setTrackerMad(player);
            if (announce) {
                player.sendMessage(Text.literal("Target not traceable in this dimension."), true);
            }
            return;
        }

        this.updateTrackerStacks(player, trackingTarget.get());
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
        MinigameManager.getInstance().setCurrentState(GameState.IN_PROGRESS);
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

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return false;
        }

        return customData.copyNbt().getBoolean(TRACKER_TAG, false);
    }

    private void markTracker(ItemStack stack) {
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> nbt.putBoolean(TRACKER_TAG, true));
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

    private void updateTrackerStacks(ServerPlayerEntity hunter, GlobalPos target) {
        if (!this.resolveTrackerItem().equals(Items.COMPASS)) {
            return;
        }

        LodestoneTrackerComponent tracker = new LodestoneTrackerComponent(Optional.of(target), false);
        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            ItemStack stack = hunter.getInventory().getStack(slot);
            if (!this.isTrackerItem(stack)) {
                continue;
            }
            stack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
        }
    }

    private void setTrackerMad(ServerPlayerEntity hunter) {
        if (!this.resolveTrackerItem().equals(Items.COMPASS)) {
            return;
        }

        LodestoneTrackerComponent tracker = new LodestoneTrackerComponent(Optional.empty(), true);
        for (int slot = 0; slot < hunter.getInventory().size(); slot++) {
            ItemStack stack = hunter.getInventory().getStack(slot);
            if (!this.isTrackerItem(stack)) {
                continue;
            }
            stack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
        }
    }

    private void cleanupDroppedTrackers() {
        if (this.server == null) {
            return;
        }

        for (ServerWorld world : this.server.getWorlds()) {
            List<? extends ItemEntity> items = world.getEntitiesByType(TypeFilter.instanceOf(ItemEntity.class),
                entity -> this.isTrackerItem(entity.getStack()));
            for (ItemEntity item : items) {
                item.discard();
            }
        }
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

        this.invincibleUntilTicks.put(player.getUuid(),
            this.gameTicks + (long) this.settings.respawnInvincibilitySeconds() * 20L);
        player.sendMessage(Text.literal("You are invincible for " + this.formatDuration(this.settings.respawnInvincibilitySeconds()) + " after respawn.")
            .formatted(Formatting.YELLOW), true);
    }

    private void tickInvincibilityWindows() {
        for (Map.Entry<UUID, Long> entry : new ArrayList<>(this.invincibleUntilTicks.entrySet())) {
            if (entry.getValue() > this.gameTicks) {
                continue;
            }

            this.invincibleUntilTicks.remove(entry.getKey());
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
        return MinigameManager.getInstance().getParticipants();
    }

    @Nullable
    private ServerPlayerEntity getPlayerByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (ServerPlayerEntity participant : MinigameManager.getInstance().getParticipants()) {
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

        List<VanillaTeamDescriptor> descriptors = new ArrayList<>();
        for (ServerPlayerEntity player : this.getParticipants()) {
            boolean isCurrentTarget = this.targetAssignments.containsValue(player.getUuid());
            Formatting color = isCurrentTarget ? Formatting.YELLOW : this.vanillaTeams.colorFor(player.getName().getString());
            String prefix = isCurrentTarget ? "[TARGET] " : "[HUNTER] ";
            VanillaTeamOptions options = VanillaTeamOptions.defaults()
                .withColor(color)
                .withPrefix(Text.literal(prefix).formatted(color))
                .withFriendlyFireAllowed(true)
                .withCollisionRule(AbstractTeam.CollisionRule.NEVER);
            descriptors.add(new VanillaTeamDescriptor("player_" + player.getUuidAsString(), player.getName(), List.of(player), options));
        }
        this.vanillaTeams.sync(this.server, descriptors);
    }

    private void clearVanillaTeams() {
        if (this.server != null) {
            this.vanillaTeams.clear(this.server);
        }
    }

    private void broadcastMessage(Text message) {
        GameMessenger.broadcast(MinigameManager.getInstance().getParticipants(), message);
    }

    private void endGameWithWinner(ServerPlayerEntity winner) {
        this.state = GameState.ENDING;
        MinigameManager.getInstance().setCurrentState(GameState.ENDING);

        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("🏆 " + winner.getName().getString() + " wins! 🏆").formatted(Formatting.GOLD));
        this.broadcastMessage(Text.literal("═══════════════════════════════════").formatted(Formatting.GOLD));
        GameMessenger.showGameOverTitle(MinigameManager.getInstance().getParticipants(), Text.literal("Bounty Hunt Winner"));
        this.clearScoreboard();
    }

    private static final class TrackingData {
        private BlockPos lastOverworld;
        private BlockPos lastNether;
        private BlockPos lastEnd;
        private BlockPos endEntryOverworld;
    }
}



