package dev.frost.miniverse.minigame.impl.bedwars;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.SessionRoster;
import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.event.*;
import dev.frost.miniverse.minigame.core.layout.InventoryLayoutAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.team.GameTeam;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamMembership;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.List;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;

import dev.frost.miniverse.minigame.core.event.EntityDamageAware;

public class BedwarsMinigame extends AbstractMinigame implements
    DeathAwareMinigame, TeamManagerProvider, BlockBreakAware, EntityInteractAware, 
    SpawnPointAware, RosterAware, PlayerLeaveAware, PersistentMinigame, 
    ServerTickAware, InventoryLayoutAware, ItemUseAware, ItemUseOnBlockAware, dev.frost.miniverse.minigame.core.event.BlockBreakBypassAware, EntityDamageAware, BlockAttackAware {

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private final TeamManager teamManager = new TeamManager();
    private final Map<String, BedTeamState> bedTeamStates = new ConcurrentHashMap<>();
    private final Set<String> activeTeamIds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> golemHits = new ConcurrentHashMap<>();
    private final Set<UUID> permanentlyEliminated = ConcurrentHashMap.newKeySet();
    private DeathLifecycleManager deathLifecycleManager;
    private dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsGeneratorManager generatorManager;
    private dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsShopManager shopManager;
    private dev.frost.miniverse.minigame.impl.bedwars.upgrade.BedwarsTeamUpgradeManager upgradeManager;
    private dev.frost.miniverse.minigame.impl.bedwars.HologramManager hologramManager;
    private dev.frost.miniverse.minigame.impl.bedwars.BedwarsCountdownService countdownService;
    private dev.frost.miniverse.minigame.impl.bedwars.visibility.BedwarsVisibilityManager visibilityManager;
    private final Map<UUID, String> defenderTeams = new ConcurrentHashMap<>();
    private final Map<UUID, BedBugProjectile> bedBugProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, BridgeEggProjectile> bridgeEggProjectiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new ConcurrentHashMap<>();

    /** Returns true if the given UUID belongs to an active bridge egg projectile. */
    public boolean isBridgeEgg(UUID uuid) {
        return this.bridgeEggProjectiles.containsKey(uuid);
    }
    private BedwarsSettings settings = BedwarsSettings.fromNbt(null);
    private BedwarsMapConfig mapConfig = new BedwarsMapConfig(Map.of(), List.of(), List.of(), List.of(), List.of(), null);
    
    private final Map<UUID, ScoreboardTemplate> scoreboards = new ConcurrentHashMap<>();
    
    private ServerWorld instanceWorld;

    // For F05 integration later:
    // private BedwarsDeathLifecycleConfig deathConfig;
    
    public BedwarsMinigame() {
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(net.minecraft.world.GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_IMMEDIATE_RESPAWN, true);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_MOB_SPAWNING, false);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.ANNOUNCE_ADVANCEMENTS, false);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_FIRE_TICK, false);
        this.setState(GameState.WAITING_FOR_PLAYERS);

        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.registerGamemode(
            BedwarsDefinition.ID,
            Set.of("BEDWARS_SWORD", "BEDWARS_PICKAXE", "BEDWARS_AXE", "BEDWARS_BLOCKS", "BEDWARS_STICK")
        );
    }

    public void applySettings(BedwarsSettings settings, BedwarsMapConfig mapConfig) {
        this.settings = settings != null ? settings : BedwarsSettings.fromNbt(null);
        this.mapConfig = mapConfig != null ? mapConfig : new BedwarsMapConfig(Map.of(), List.of(), List.of(), List.of(), List.of(), null);
    }

    public void ensureTeamAssignment(ServerPlayerEntity player, String team) {
        String mapTeamId = this.resolveMapTeamId(team);
        if (mapTeamId != null) {
            this.assignToMapTeam(player, mapTeamId);
            return;
        }
        String fallback = team == null || team.isBlank() ? player.getName().getString() : team;
        this.teamManager.assign(player, fallback, fallback, dev.frost.miniverse.team.TeamRole.MEMBER);
    }

    public boolean assignConfiguredMapTeam(ServerPlayerEntity player, String teamId) {
        if (player == null || teamId == null || teamId.isBlank() || !this.mapConfig.teams().containsKey(teamId)) {
            return false;
        }
        this.assignToMapTeam(player, teamId);
        return true;
    }

    private void mapSessionTeams() {
        java.util.List<dev.frost.miniverse.team.TeamSnapshot> activeSessionTeams = this.teamManager.snapshots();
        java.util.List<String> mapTeamIds = new java.util.ArrayList<>(this.mapConfig.teams().keySet());
        java.util.Set<String> assignedMapTeams = new java.util.LinkedHashSet<>();
        int fallbackIndex = 0;
        
        for (int i = 0; i < activeSessionTeams.size() && !mapTeamIds.isEmpty(); i++) {
            dev.frost.miniverse.team.TeamSnapshot sessionTeam = activeSessionTeams.get(i);
            String mapId = this.resolveMapTeamId(sessionTeam.id());
            if (mapId == null) {
                mapId = this.resolveMapTeamId(sessionTeam.label());
            }
            if (mapId == null || assignedMapTeams.contains(mapId)) {
                while (fallbackIndex < mapTeamIds.size() && assignedMapTeams.contains(mapTeamIds.get(fallbackIndex))) {
                    fallbackIndex++;
                }
                if (fallbackIndex >= mapTeamIds.size()) {
                    break;
                }
                mapId = mapTeamIds.get(fallbackIndex++);
            }
            assignedMapTeams.add(mapId);
            BedwarsMapConfig.BedwarsTeamConfig mapTeamConfig = this.mapConfig.teams().get(mapId);
            this.teamManager.ensureTeam(mapId, mapTeamConfig.name);
            if (mapTeamConfig.color != null) {
                this.teamManager.ensureTeam(mapId, mapTeamConfig.name).setColor(mapTeamConfig.color);
            }
            
            for (dev.frost.miniverse.team.TeamMembership member : sessionTeam.members()) {
                net.minecraft.server.network.ServerPlayerEntity player = this.context.nullableServer().getPlayerManager().getPlayer(member.playerUuid());
                if (player != null) {
                    this.teamManager.assign(player, mapId, mapTeamConfig.name, dev.frost.miniverse.team.TeamRole.MEMBER);
                } else {
                    this.teamManager.assign(member.playerUuid(), member.playerName(), mapId, mapTeamConfig.name, dev.frost.miniverse.team.TeamRole.MEMBER);
                }
            }
        }
    }

    private void mapPlayersToBedwarsTeams(List<ServerPlayerEntity> participants) {
        java.util.List<String> mapTeamIds = new java.util.ArrayList<>(this.mapConfig.teams().keySet());
        
        java.util.Map<String, Integer> teamSizes = new java.util.HashMap<>();
        for (String id : mapTeamIds) {
            teamSizes.put(id, 0);
        }

        java.util.List<ServerPlayerEntity> unassigned = new java.util.ArrayList<>();
        for (ServerPlayerEntity player : participants) {
            String teamId = this.resolvePlayerMapTeamId(player);
            if (teamId != null && mapTeamIds.contains(teamId)) {
                this.assignToMapTeam(player, teamId);
                this.activeTeamIds.add(teamId);
                teamSizes.put(teamId, teamSizes.get(teamId) + 1);
            } else {
                unassigned.add(player);
            }
        }

        for (ServerPlayerEntity player : unassigned) {
            if (mapTeamIds.isEmpty()) break;
            
            String smallestTeam = mapTeamIds.get(0);
            int minSize = teamSizes.get(smallestTeam);
            for (String id : mapTeamIds) {
                if (teamSizes.get(id) < minSize) {
                    smallestTeam = id;
                    minSize = teamSizes.get(id);
                }
            }
            
            this.assignToMapTeam(player, smallestTeam);
            this.activeTeamIds.add(smallestTeam);
            teamSizes.put(smallestTeam, minSize + 1);
        }
    }

    private void assignToMapTeam(ServerPlayerEntity player, String teamId) {
        BedwarsMapConfig.BedwarsTeamConfig cfg = this.mapConfig.teams().get(teamId);
        if (cfg == null) {
            return;
        }
        this.teamManager.assign(player, teamId, cfg.name, dev.frost.miniverse.team.TeamRole.MEMBER);
        if (cfg.color != null) {
            this.teamManager.ensureTeam(teamId, cfg.name).setColor(cfg.color);
        }
    }

    private String resolvePlayerMapTeamId(ServerPlayerEntity player) {
        String currentTeamId = this.teamManager.teamId(player.getUuid());
        String resolved = this.resolveMapTeamId(currentTeamId);
        if (resolved != null) {
            return resolved;
        }
        return this.resolveMapTeamId(this.teamManager.teamLabel(player.getUuid(), ""));
    }

    private String resolveMapTeamId(String requestedTeam) {
        if (requestedTeam == null || requestedTeam.isBlank()) {
            return null;
        }
        if (this.mapConfig.teams().containsKey(requestedTeam)) {
            return requestedTeam;
        }

        String requestedAlias = teamAlias(requestedTeam);
        for (Map.Entry<String, BedwarsMapConfig.BedwarsTeamConfig> entry : this.mapConfig.teams().entrySet()) {
            BedwarsMapConfig.BedwarsTeamConfig config = entry.getValue();
            if (teamAlias(entry.getKey()).equals(requestedAlias)
                    || teamAlias(config.name).equals(requestedAlias)
                    || (config.color != null && teamAlias(config.color.getName()).equals(requestedAlias))) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String teamAlias(String value) {
        if (value == null) {
            return "";
        }
        String alias = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return alias.endsWith("team") ? alias.substring(0, alias.length() - 4) : alias;
    }

    public MapValidationResult startValidation() {
        MapValidationResult configResult = this.mapConfig.validate();
        if (!configResult.valid()) return configResult;

        long activeSessionTeams = this.teamManager.snapshots().stream().filter(t -> !t.members().isEmpty()).count();
        if (activeSessionTeams > this.mapConfig.teams().size()) {
             return MapValidationResult.builder().error("Too many teams configured! This map only supports " + this.mapConfig.teams().size() + " teams.").build();
        }
        return MapValidationResult.ok();
    }

    public boolean canStartMatch() {
        return true;
    }

    @Override
    public void onMatchStart() {
        this.applyVanillaGameRule(net.minecraft.world.GameRules.NATURAL_REGENERATION, false);
        this.bedTeamStates.clear();
        this.activeTeamIds.clear();
        this.lastDamageTimes.clear();
        this.defenderTeams.clear();
        this.bedBugProjectiles.clear();
        this.bridgeEggProjectiles.clear();
        
        this.hologramManager = new dev.frost.miniverse.minigame.impl.bedwars.HologramManager();
        this.generatorManager = new dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsGeneratorManager(this.mapConfig, this.settings, this.hologramManager);
        this.shopManager = new dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsShopManager(this.mapConfig, this.settings);
        this.visibilityManager = new dev.frost.miniverse.minigame.impl.bedwars.visibility.BedwarsVisibilityManager(this);
        this.countdownService = new dev.frost.miniverse.minigame.impl.bedwars.BedwarsCountdownService(this, this.generatorManager);
        
        // Setup players
        List<ServerPlayerEntity> participants = this.context.roster().onlinePlayers(this.context.nullableServer());
        this.shopManager.initPlayers(participants);
        this.instanceWorld = participants.isEmpty() ? this.context.nullableServer().getOverworld() : participants.get(0).getServerWorld();
        this.shopManager.spawnNpcs(this.instanceWorld, this.mapConfig.shopNpcs());
        this.mapPlayersToBedwarsTeams(participants);
        
        for (ServerPlayerEntity player : participants) {
            player.getInventory().clear();
            this.equipBaseArmor(player);
            player.getInventory().insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.WOODEN_SWORD));
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            this.teleportToSpawn(player);
        }

        for (String teamId : this.activeTeamIds) {
            this.bedTeamStates.put(teamId, new BedTeamState());
        }
        this.upgradeManager = new dev.frost.miniverse.minigame.impl.bedwars.upgrade.BedwarsTeamUpgradeManager(this.activeTeamIds, this);
        this.upgradeManager.spawnNpcs(this.instanceWorld, this.mapConfig.upgradeNpcs());
        
        if (this.context.nullableServer() != null) {
            this.context.nullableServer().setPvpEnabled(true);
        }
        
        this.deathLifecycleManager = new dev.frost.miniverse.minigame.core.death.DeathLifecycleManager(
            new dev.frost.miniverse.minigame.impl.bedwars.death.BedwarsDeathLifecycleConfig(this, this.bedTeamStates, this.settings, this.mapConfig, dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance(), this.permanentlyEliminated),
            dev.frost.miniverse.minigame.core.spectator.SpectatorService.getInstance()
        );

        this.syncVanillaTeams();
        this.rebuildScoreboard();
        this.setState(GameState.RUNNING);
    }
    
    @Override
    public void onMatchEnd() {
        if (this.shopManager != null && this.context != null) {
            this.shopManager.clear(this.context.nullableServer());
        }
        if (this.upgradeManager != null && this.context != null) {
            this.upgradeManager.clear(this.context.nullableServer());
        }
        if (this.hologramManager != null && this.context != null) {
            this.hologramManager.clear(this.context.nullableServer());
        }
        if (this.visibilityManager != null && this.context != null) {
            this.visibilityManager.clear(this.context.nullableServer());
        }
        this.defenderTeams.clear();
        this.bedBugProjectiles.clear();
        this.bridgeEggProjectiles.clear();
        if (this.context != null && this.context.nullableServer() != null) {
            for (ScoreboardTemplate board : this.scoreboards.values()) {
                board.cleanup(this.context.nullableServer());
            }
        }
    }
    
    @Override
    public String getName() {
        return BedwarsDefinition.DISPLAY_NAME;
    }

    public dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsShopManager getShopManager() {
        return this.shopManager;
    }

    public dev.frost.miniverse.minigame.impl.bedwars.upgrade.BedwarsTeamUpgradeManager getUpgradeManager() {
        return this.upgradeManager;
    }

    @Override
    public dev.frost.miniverse.minigame.core.GameState getState() {
        return this.state;
    }

    @Override
    public void setState(dev.frost.miniverse.minigame.core.GameState state) {
        dev.frost.miniverse.minigame.core.GameState oldState = this.getState();
        this.state = state;

        if (oldState != dev.frost.miniverse.minigame.core.GameState.FROZEN && this.getState() == dev.frost.miniverse.minigame.core.GameState.FROZEN) {
            if (this.context != null && this.context.nullableServer() != null) {
                List<ServerPlayerEntity> players = this.context.roster().onlinePlayers(this.context.nullableServer());
                this.mapPlayersToBedwarsTeams(players);
                for (net.minecraft.server.network.ServerPlayerEntity player : players) {
                    this.teleportToSpawn(player);
                }
            }
        }
    }

    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(SessionRoster roster) {
        long distinctTeams = roster.allParticipants().stream()
            .map(this.teamManager::teamId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .count();
        if (distinctTeams < 2) {
            return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(true, net.minecraft.text.Text.literal("Not enough teams with players"), null);
        }
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }

    public dev.frost.miniverse.minigame.core.MinigameContext getContext() {
        return this.context;
    }

    @Override
    public TeamManager teamManager() {
        return this.teamManager;
    }

    public dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsGeneratorManager getGeneratorManager() {
        return this.generatorManager;
    }

    public void broadcast(net.minecraft.text.Text text) {
        if (this.context != null && this.context.nullableServer() != null) {
            this.context.nullableServer().getPlayerManager().broadcast(text, false);
        }
    }

    @Override
    public boolean canBypassProtection(ServerPlayerEntity player, BlockPos pos) {
        if (this.getState() != GameState.RUNNING) return false;
        String playerTeamId = this.teamManager.teamId(player.getUuid());
        if (playerTeamId == null) return false;

        net.minecraft.block.BlockState state = player.getServerWorld().getBlockState(pos);
        if (!(state.getBlock() instanceof net.minecraft.block.BedBlock)) return false;

        String bedTeamId = this.mapConfig.findBedTeam(pos);
        if (bedTeamId == null) {
            net.minecraft.util.math.Direction dir = state.get(net.minecraft.block.BedBlock.FACING);
            net.minecraft.util.math.BlockPos otherHalf = state.get(net.minecraft.block.BedBlock.PART) == net.minecraft.block.enums.BedPart.FOOT 
                ? pos.offset(dir) 
                : pos.offset(dir.getOpposite());
            bedTeamId = this.mapConfig.findBedTeam(otherHalf);
        }

        if (bedTeamId != null) {
            if (bedTeamId.equals(playerTeamId)) {
                player.sendMessage(net.minecraft.text.Text.literal("You cannot break your own bed!").formatted(net.minecraft.util.Formatting.RED), false);
                return false;
            }
            // Manually break the block here to avoid item drops, and return false to cancel vanilla break
            net.minecraft.server.world.ServerWorld world = player.getServerWorld();
            
            net.minecraft.util.math.Direction dir = state.get(net.minecraft.block.BedBlock.FACING);
            net.minecraft.util.math.BlockPos otherHalf = state.get(net.minecraft.block.BedBlock.PART) == net.minecraft.block.enums.BedPart.FOOT 
                ? pos.offset(dir) 
                : pos.offset(dir.getOpposite());
                
            // Use flag 2 (NOTIFY_LISTENERS) | 32 (SKIP_DROPS) = 34 to avoid neighbor updates which cause the other half to break and drop an item
            world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState(), 34);
            world.setBlockState(otherHalf, net.minecraft.block.Blocks.AIR.getDefaultState(), 34);
            world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_WOOD_BREAK, net.minecraft.sound.SoundCategory.BLOCKS, 1.0f, 1.0f);
            
            this.onBlockBroken(player, world, pos, state);
            return false;
        }

        return false;
    }

    @Override
    public void onBlockBroken(ServerPlayerEntity breaker, ServerWorld world, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof net.minecraft.block.BedBlock)) return;
        
        String teamId = this.mapConfig.findBedTeam(pos);
        if (teamId == null) {
            net.minecraft.util.math.Direction dir = state.get(net.minecraft.block.BedBlock.FACING);
            net.minecraft.util.math.BlockPos otherHalf = state.get(net.minecraft.block.BedBlock.PART) == net.minecraft.block.enums.BedPart.FOOT 
                ? pos.offset(dir) 
                : pos.offset(dir.getOpposite());
            teamId = this.mapConfig.findBedTeam(otherHalf);
        }
        
        if (teamId == null) return;
        
        BedTeamState teamState = this.bedTeamStates.get(teamId);
        if (teamState == null || !teamState.isBedAlive()) return;
        
        teamState.destroyBed();
        
        String breakerTeamId = this.teamManager.teamId(breaker.getUuid());
        if (breakerTeamId == null) breakerTeamId = "";
        
        notifyBedDestroyed(teamId, breaker.getNameForScoreboard());
        
        this.rebuildScoreboard();
        this.checkWinCondition();
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, net.minecraft.entity.damage.DamageSource source, float amount) {
        if (this.getState() != GameState.RUNNING) {
            return false;
        }
        Entity attacker = source.getAttacker();
        if (attacker != null) {
            String defenderTeamId = this.defenderTeams.get(attacker.getUuid());
            if (this.isSameTeam(defenderTeamId, player)) {
                return false;
            }
        }
        this.lastDamageTimes.put(player.getUuid(), System.currentTimeMillis());
        return true;
    }

    @Override
    public ActionResult onAttackBlock(ServerPlayerEntity player, net.minecraft.world.World world, Hand hand, BlockPos pos, net.minecraft.util.math.Direction direction) {
        if (this.getState() != GameState.RUNNING) return ActionResult.PASS;

        if (!isResource(player.getMainHandStack())) {
            return ActionResult.PASS;
        }

        BlockState state = world.getBlockState(pos);
        boolean isEnderChest = state.isOf(Blocks.ENDER_CHEST);
        boolean isChest = state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL);

        if (isEnderChest || isChest) {
            net.minecraft.inventory.Inventory targetInventory = null;

            if (isEnderChest) {
                targetInventory = player.getEnderChestInventory();
            } else {
                net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof net.minecraft.inventory.Inventory inv) {
                    targetInventory = inv;
                }
            }

            if (targetInventory != null) {
                boolean depositedAny = depositResources(player.getInventory(), targetInventory);
                if (depositedAny) {
                    player.sendMessage(net.minecraft.text.Text.literal("Resources deposited!").formatted(net.minecraft.util.Formatting.GREEN), true);
                    player.playSound(net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP, 1.0F, 1.0F);
                }
                return ActionResult.SUCCESS;
            }
        }
        return ActionResult.PASS;
    }

    private boolean depositResources(net.minecraft.entity.player.PlayerInventory playerInv, net.minecraft.inventory.Inventory targetInv) {
        boolean deposited = false;
        for (int i = 0; i < playerInv.size(); i++) {
            net.minecraft.item.ItemStack stack = playerInv.getStack(i);
            if (!stack.isEmpty() && isResource(stack)) {
                net.minecraft.item.ItemStack remaining = insertIntoInventory(targetInv, stack);
                if (remaining.getCount() != stack.getCount()) {
                    deposited = true;
                    playerInv.setStack(i, remaining);
                }
            }
        }
        return deposited;
    }

    private net.minecraft.item.ItemStack insertIntoInventory(net.minecraft.inventory.Inventory target, net.minecraft.item.ItemStack stack) {
        net.minecraft.item.ItemStack remaining = stack.copy();

        for (int i = 0; i < target.size(); i++) {
            net.minecraft.item.ItemStack current = target.getStack(i);
            if (!current.isEmpty() && net.minecraft.item.ItemStack.areItemsAndComponentsEqual(current, remaining)) {
                int space = current.getMaxCount() - current.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, remaining.getCount());
                    current.increment(transfer);
                    remaining.decrement(transfer);
                    target.markDirty();
                    if (remaining.isEmpty()) {
                        return net.minecraft.item.ItemStack.EMPTY;
                    }
                }
            }
        }

        for (int i = 0; i < target.size(); i++) {
            net.minecraft.item.ItemStack current = target.getStack(i);
            if (current.isEmpty()) {
                target.setStack(i, remaining.copy());
                target.markDirty();
                return net.minecraft.item.ItemStack.EMPTY;
            }
        }

        return remaining;
    }

    private boolean isResource(net.minecraft.item.ItemStack stack) {
        net.minecraft.item.Item item = stack.getItem();
        return item == Items.IRON_INGOT
            || item == Items.GOLD_INGOT
            || item == Items.DIAMOND
            || item == Items.EMERALD;
    }

    public void checkWinCondition() {
        if (this.getState() != GameState.RUNNING || this.bedTeamStates.isEmpty()) return;
        List<String> alive = this.bedTeamStates.entrySet().stream()
            .filter(e -> isTeamAlive(e.getKey(), e.getValue()))
            .map(Map.Entry::getKey)
            .toList();

        if (alive.size() == 1) {
            String winnerId = alive.get(0);
            String winnerLabel = this.teamManager.ensureTeam(winnerId, winnerId).label();
            List<ServerPlayerEntity> winners = this.context.nullableServer().getPlayerManager().getPlayerList().stream()
                .filter(p -> winnerId.equals(this.teamManager.teamId(p.getUuid())))
                .toList();
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                this.runtime, 
                dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult.winners(winners, net.minecraft.text.Text.literal(winnerLabel + " Wins!")), 
                dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions.defaults(BedwarsDefinition.DISPLAY_NAME)
            );
        } else if (alive.isEmpty()) {
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                this.runtime, 
                new dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult(Set.of(), net.minecraft.text.Text.literal("Draw")), 
                dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions.defaults(BedwarsDefinition.DISPLAY_NAME)
            );
        }
    }

    private boolean isTeamAlive(String teamId, BedTeamState state) {
        if (state.isBedAlive()) return true;
        return this.teamManager.ensureTeam(teamId, teamId).members().stream()
            .map(TeamMembership::playerUuid)
            .anyMatch(id -> !this.permanentlyEliminated.contains(id));
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, net.minecraft.world.World world, Hand hand) {
        if (this.getState() != GameState.RUNNING) return ActionResult.PASS;
        
        net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() == net.minecraft.item.Items.FIRE_CHARGE) {
            dev.frost.miniverse.minigame.impl.bedwars.entity.BedwarsFireballEntity fireball = new dev.frost.miniverse.minigame.impl.bedwars.entity.BedwarsFireballEntity(world, player, player.getRotationVector(), 2.0f);
            fireball.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
            world.spawnEntity(fireball);
            
            if (!player.isCreative()) {
                stack.decrement(1);
            }
            return net.minecraft.util.ActionResult.SUCCESS;
        }

        if (world instanceof ServerWorld serverWorld && stack.getItem() == net.minecraft.item.Items.SNOWBALL) {
            net.minecraft.entity.projectile.thrown.SnowballEntity snowball = new net.minecraft.entity.projectile.thrown.SnowballEntity(serverWorld, player);
            snowball.setItem(stack.copyWithCount(1));
            snowball.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 1.5F, 1.0F);
            serverWorld.spawnEntity(snowball);
            this.bedBugProjectiles.put(snowball.getUuid(), new BedBugProjectile(this.teamManager.teamId(player.getUuid()), snowball.getBlockPos()));

            if (!player.isCreative()) {
                stack.decrement(1);
            }
            return net.minecraft.util.ActionResult.SUCCESS;
        }

        if (world instanceof ServerWorld serverWorld && stack.getItem() == net.minecraft.item.Items.EGG) {
            net.minecraft.entity.projectile.thrown.EggEntity egg = new net.minecraft.entity.projectile.thrown.EggEntity(serverWorld, player);
            egg.setItem(stack.copyWithCount(1));
            egg.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 1.5F, 1.0F);
            serverWorld.spawnEntity(egg);
            this.bridgeEggProjectiles.put(egg.getUuid(), new BridgeEggProjectile(this.teamManager.teamId(player.getUuid()), player.getBlockPos()));

            if (!player.isCreative()) {
                stack.decrement(1);
            }
            return net.minecraft.util.ActionResult.SUCCESS;
        }
        
        return net.minecraft.util.ActionResult.PASS;
    }

    @Override
    public net.minecraft.util.ActionResult onUseBlock(ServerPlayerEntity player, net.minecraft.world.World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (this.getState() != GameState.RUNNING) return net.minecraft.util.ActionResult.PASS;

        net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
        
        if (stack.getItem() == net.minecraft.item.Items.FIRE_CHARGE) {
            return this.onUseItem(player, world, hand);
        }

        if (stack.getItem() == net.minecraft.item.Items.TNT) {
            net.minecraft.util.math.BlockPos targetPos = hitResult.getBlockPos().offset(hitResult.getSide());
            net.minecraft.entity.TntEntity tnt = new net.minecraft.entity.TntEntity(net.minecraft.entity.EntityType.TNT, world);
            tnt.setPosition(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            tnt.setFuse(60); // 3 seconds
            world.spawnEntity(tnt);
            world.playSound(null, tnt.getX(), tnt.getY(), tnt.getZ(), net.minecraft.sound.SoundEvents.ENTITY_TNT_PRIMED, net.minecraft.sound.SoundCategory.BLOCKS, 1.0F, 1.0F);

            if (!player.isCreative()) {
                stack.decrement(1);
            }
            return net.minecraft.util.ActionResult.SUCCESS;
        }

        if (world instanceof ServerWorld serverWorld && stack.getItem() == net.minecraft.item.Items.IRON_GOLEM_SPAWN_EGG) {
            net.minecraft.util.math.BlockPos targetPos = hitResult.getBlockPos().offset(hitResult.getSide());
            net.minecraft.entity.passive.IronGolemEntity golem = new net.minecraft.entity.passive.IronGolemEntity(net.minecraft.entity.EntityType.IRON_GOLEM, serverWorld);
            golem.setPlayerCreated(true);
            golem.setPersistent();
            golem.setPosition(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            golem.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(7.5); // Half damage
            golem.setCustomName(net.minecraft.text.Text.literal("10 Hits"));
            golem.setCustomNameVisible(true);
            this.golemHits.put(golem.getUuid(), 10);
            serverWorld.spawnEntity(golem);
            this.defenderTeams.put(golem.getUuid(), this.teamManager.teamId(player.getUuid()));

            if (!player.isCreative()) {
                stack.decrement(1);
            }
            return net.minecraft.util.ActionResult.SUCCESS;
        }

        if (stack.getItem() == net.minecraft.item.Items.CHEST) {
            net.minecraft.util.math.BlockPos targetPos = hitResult.getBlockPos().offset(hitResult.getSide());
            this.buildCompactPopUpTower(player, world, targetPos);

            if (!player.isCreative()) {
                stack.decrement(1);
            }
            return net.minecraft.util.ActionResult.SUCCESS;
        }

        return net.minecraft.util.ActionResult.PASS;
    }

    @Override
    public net.minecraft.util.ActionResult onEntityInteract(ServerPlayerEntity player, ServerWorld world, Hand hand, Entity entity) {
        if (this.shopManager != null && this.shopManager.handleInteract(player, entity)) {
            return ActionResult.SUCCESS;
        }
        if (this.upgradeManager != null && this.upgradeManager.handleInteract(player, entity)) {
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        String rawTeamId = this.teamManager.teamId(player.getUuid());
        String teamId = this.resolvePlayerMapTeamId(player);
        if (teamId != null && !teamId.equals(rawTeamId)) {
            this.assignToMapTeam(player, teamId);
        }
        if (teamId == null) {
            dev.frost.miniverse.Miniverse.LOGGER.warn("Bedwars spawn skipped for {}: no map team resolved from teamId='{}' label='{}'. Map teams={}",
                player.getName().getString(), rawTeamId, this.teamManager.teamLabel(player.getUuid(), ""), this.mapConfig.teams().keySet());
            return;
        }

        BedwarsMapConfig.BedwarsTeamConfig config = this.mapConfig.teams().get(teamId);
        if (config == null) {
            dev.frost.miniverse.Miniverse.LOGGER.warn("Bedwars spawn skipped for {}: resolved team '{}' has no config. Map teams={}",
                player.getName().getString(), teamId, this.mapConfig.teams().keySet());
            return;
        }
        if (config.spawns.isEmpty()) {
            dev.frost.miniverse.Miniverse.LOGGER.warn("Bedwars spawn skipped for {}: team '{}' ({}) has no teamSpawns. Team definition point is not used as spawn.",
                player.getName().getString(), teamId, config.name);
            return;
        }

        dev.frost.miniverse.map.MapPosition pos = config.spawns.get(Math.floorMod(player.getUuid().hashCode(), config.spawns.size()));
        player.teleport(player.getServerWorld(), pos.x(), pos.y(), pos.z(), java.util.Set.of(), pos.yaw(), pos.pitch());
        dev.frost.miniverse.Miniverse.LOGGER.info("Bedwars spawned {} on team '{}' ({}) at team_spawn {},{},{}.",
            player.getName().getString(), teamId, config.name, pos.x(), pos.y(), pos.z());
    }

    public void equipBaseArmor(ServerPlayerEntity player) {
        String teamId = this.teamManager.teamId(player.getUuid());
        if (teamId != null) {
            BedwarsMapConfig.BedwarsTeamConfig config = this.mapConfig.teams().get(teamId);
            if (config != null && config.color != null && config.color.getColorValue() != null) {
                int rgb = config.color.getColorValue();
                net.minecraft.item.ItemStack helmet = new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEATHER_HELMET);
                helmet.set(net.minecraft.component.DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(rgb, false));
                net.minecraft.item.ItemStack chestplate = new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEATHER_CHESTPLATE);
                chestplate.set(net.minecraft.component.DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(rgb, false));
                
                player.equipStack(net.minecraft.entity.EquipmentSlot.HEAD, helmet);
                player.equipStack(net.minecraft.entity.EquipmentSlot.CHEST, chestplate);
                
                dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsPlayerToolState toolState = this.shopManager != null ? this.shopManager.getToolState(player.getUuid()) : null;
                int tier = toolState != null ? toolState.getArmorTier() : 0;
                
                if (tier == 0) {
                    net.minecraft.item.ItemStack leggings = new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEATHER_LEGGINGS);
                    leggings.set(net.minecraft.component.DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(rgb, false));
                    net.minecraft.item.ItemStack boots = new net.minecraft.item.ItemStack(net.minecraft.item.Items.LEATHER_BOOTS);
                    boots.set(net.minecraft.component.DataComponentTypes.DYED_COLOR, new net.minecraft.component.type.DyedColorComponent(rgb, false));
                    
                    player.equipStack(net.minecraft.entity.EquipmentSlot.LEGS, leggings);
                    player.equipStack(net.minecraft.entity.EquipmentSlot.FEET, boots);
                }
            }
        }
    }

    @Override
    public void onRosterChanged(SessionRoster roster) {
        if (this.context == null || this.context.nullableServer() == null) return;
        MinecraftServer server = this.context.nullableServer();
        for (UUID id : roster.offlinePlayers(server)) {
            if (this.deathLifecycleManager != null) {
                this.deathLifecycleManager.handleDisconnect(server.getPlayerManager().getPlayer(id));
            }
        }
        this.checkWinCondition();
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject state = new JsonObject();
        JsonObject teamsJson = new JsonObject();
        for (Map.Entry<String, BedTeamState> entry : this.bedTeamStates.entrySet()) {
            JsonObject teamState = new JsonObject();
            teamState.addProperty("bedAlive", entry.getValue().isBedAlive());
            teamsJson.add(entry.getKey(), teamState);
        }
        state.add("bedTeamStates", teamsJson);
        
        com.google.gson.JsonArray eliminatedArray = new com.google.gson.JsonArray();
        for (UUID uuid : this.permanentlyEliminated) {
            eliminatedArray.add(uuid.toString());
        }
        state.add("permanentlyEliminated", eliminatedArray);
        
        return state;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        if (state.has("bedTeamStates")) {
            JsonObject teamsJson = state.getAsJsonObject("bedTeamStates");
            for (Map.Entry<String, com.google.gson.JsonElement> entry : teamsJson.entrySet()) {
                BedTeamState teamState = this.bedTeamStates.computeIfAbsent(entry.getKey(), k -> new BedTeamState());
                JsonObject teamJson = entry.getValue().getAsJsonObject();
                if (teamJson.has("bedAlive") && !teamJson.get("bedAlive").getAsBoolean()) {
                    teamState.destroyBed();
                }
            }
        }
        
        if (state.has("permanentlyEliminated")) {
            for (com.google.gson.JsonElement element : state.getAsJsonArray("permanentlyEliminated")) {
                this.permanentlyEliminated.add(UUID.fromString(element.getAsString()));
            }
        }
    }

    @Override
    public void onGameTick(MinecraftServer server) {
        if (this.generatorManager != null && this.context.nullableServer() != null) {
            this.generatorManager.tick(this.instanceWorld != null ? this.instanceWorld : this.context.nullableServer().getOverworld());
        }
        if (this.countdownService != null && this.context.nullableServer() != null) {
            this.countdownService.tick(this.context.nullableServer());
        }
        if (this.visibilityManager != null && this.context.nullableServer() != null) {
            this.visibilityManager.sync(this.context.nullableServer());
        }
        this.tickUtilityItems(server);
        if (server.getTicks() % 20 == 0) {
            this.rebuildScoreboard();
        }

        if (this.context != null && this.context.roster() != null) {
            for (net.minecraft.server.network.ServerPlayerEntity p : this.context.roster().onlinePlayers(server)) {
                if (p.isAlive() && !p.isSpectator()) {
                    p.getHungerManager().setFoodLevel(20);
                    p.getHungerManager().setSaturationLevel(5.0F);

                    if (server.getTicks() % 10 == 0) {
                        long lastDamage = this.lastDamageTimes.getOrDefault(p.getUuid(), 0L);
                        if (System.currentTimeMillis() - lastDamage > 10000) {
                            if (p.getHealth() < p.getMaxHealth()) {
                                p.heal(1.0F);
                            }
                        }
                    }
                }
            }
        }

        if (this.getState() == GameState.RUNNING && this.mapConfig.voidLevelRef() != null && this.context != null && this.context.roster() != null) {
            int voidY = this.mapConfig.voidLevelRef();
            for (net.minecraft.server.network.ServerPlayerEntity p : this.context.roster().onlinePlayers(server)) {
                if (p.getY() <= voidY && p.getHealth() > 0 && (this.deathLifecycleManager == null || this.deathLifecycleManager.getContext(p.getUuid()) == null)) {
                    if (this.deathLifecycleManager != null) {
                        this.deathLifecycleManager.handleFatalDamage(p, p.getDamageSources().outOfWorld());
                    }
                }
            }
        }
    }

    public void destroyAllBeds() {
        for (Map.Entry<String, BedTeamState> entry : this.bedTeamStates.entrySet()) {
            if (entry.getValue().isBedAlive()) {
                entry.getValue().destroyBed();
                notifyBedDestroyed(entry.getKey(), "bed destruction phase");
            }
        }
        this.rebuildScoreboard();
        this.checkWinCondition();
    }

    private void notifyBedDestroyed(String teamId, String breakerName) {
        BedwarsMapConfig.BedwarsTeamConfig config = this.mapConfig.teams().get(teamId);
        String teamLabel = config != null ? config.name : teamId;
        this.broadcast(net.minecraft.text.Text.literal("Bed destroyed! Team " + teamLabel + " lost their bed to " + breakerName).formatted(net.minecraft.util.Formatting.RED));
        
        if (this.getContext() != null && this.getContext().nullableServer() != null) {
            for (ServerPlayerEntity p : this.getContext().roster().onlinePlayers(this.getContext().nullableServer())) {
                if (teamId.equals(this.teamManager.teamId(p.getUuid()))) {
                    p.playSound(net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
                    p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(net.minecraft.text.Text.literal("BED DESTROYED!").formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD)));
                    p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(net.minecraft.text.Text.literal("You will no longer respawn!").formatted(net.minecraft.util.Formatting.GRAY)));
                } else {
                    p.playSound(net.minecraft.sound.SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.0f);
                }
            }
        }
    }

    public void endMatchInDraw() {
        dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
            this.runtime, 
            new dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult(java.util.Set.of(), net.minecraft.text.Text.literal("Draw!")), 
            dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions.defaults(BedwarsDefinition.DISPLAY_NAME)
        );
    }

    private void rebuildScoreboard() {
        if (this.context == null || this.context.nullableServer() == null || this.getState() != GameState.RUNNING) return;
        List<ServerPlayerEntity> participants = this.context.roster().onlinePlayers(this.context.nullableServer());
        
        for (ServerPlayerEntity player : participants) {
            ScoreboardTemplate board = this.scoreboards.computeIfAbsent(player.getUuid(), id -> {
                ScoreboardTemplate t = new ScoreboardTemplate(BedwarsDefinition.ID + "_" + id.toString(), net.minecraft.text.Text.literal("BED WARS").formatted(net.minecraft.util.Formatting.GOLD, net.minecraft.util.Formatting.BOLD));
                t.show(player);
                return t;
            });
            
            board.clearLines();
            
            if (this.countdownService != null) {
                board.addLine(net.minecraft.text.Text.literal(this.countdownService.getCurrentPhaseDisplay()));
                board.addBlankLine();
            }
            
            for (Map.Entry<String, BedTeamState> entry : this.bedTeamStates.entrySet()) {
                String teamId = entry.getKey();
                BedTeamState state = entry.getValue();
                BedwarsMapConfig.BedwarsTeamConfig config = this.mapConfig.teams().get(teamId);
                String teamLabel = config != null ? config.name : teamId;
                
                String status;
                if (state.isBedAlive()) {
                    status = "§a✔";
                } else {
                    int aliveCount = 0;
                    for (ServerPlayerEntity p : participants) {
                        if (teamId.equals(this.teamManager.teamId(p.getUuid())) && !p.isSpectator()) {
                            aliveCount++;
                        }
                    }
                    if (aliveCount > 0) {
                        status = "§a" + aliveCount;
                    } else {
                        status = "§c☠";
                    }
                }
                
                board.addLine(net.minecraft.text.Text.literal("● " + teamLabel + " " + status));
            }
            
            board.addBlankLine();
            board.addLine(net.minecraft.text.Text.literal("Iron: §f" + player.getInventory().count(dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.IRON.item())).formatted(net.minecraft.util.Formatting.GRAY));
            board.addLine(net.minecraft.text.Text.literal("Gold: §f" + player.getInventory().count(dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.GOLD.item())).formatted(net.minecraft.util.Formatting.GRAY));
            board.addLine(net.minecraft.text.Text.literal("Diamond: §f" + player.getInventory().count(dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.DIAMOND.item())).formatted(net.minecraft.util.Formatting.GRAY));
            board.addLine(net.minecraft.text.Text.literal("Emerald: §f" + player.getInventory().count(dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.EMERALD.item())).formatted(net.minecraft.util.Formatting.GRAY));
            
            board.sendLineUpdates();
        }
    }

    @Override
    public String inventoryLayoutGamemodeId() {
        return BedwarsDefinition.ID;
    }

    @Override
    public boolean isTeamBased() {
        return false;
    }

    private void tickUtilityItems(MinecraftServer server) {
        this.removePotionBottles(server);
        this.tickBedBugs(server);
        this.tickBridgeEggs(server);
        this.tickDefenders(server);
    }

    private void removePotionBottles(MinecraftServer server) {
        if (this.context == null || this.context.roster() == null) {
            return;
        }
        for (ServerPlayerEntity player : this.context.roster().onlinePlayers(server)) {
            player.getInventory().remove(stack -> stack.getItem() == Items.GLASS_BOTTLE, Integer.MAX_VALUE, player.playerScreenHandler.getCraftingInput());
        }
    }

    private void tickBedBugs(MinecraftServer server) {
        for (Map.Entry<UUID, BedBugProjectile> entry : List.copyOf(this.bedBugProjectiles.entrySet())) {
            Entity entity = findEntity(server, entry.getKey());
            BedBugProjectile projectile = entry.getValue();
            if (entity != null && !entity.isRemoved()) {
                projectile.lastPos = entity.getBlockPos();
                if (!entity.horizontalCollision && !entity.verticalCollision && !entity.groundCollision && entity.age <= 100) {
                    continue;
                }
            }

            ServerWorld world = entity instanceof net.minecraft.entity.projectile.thrown.SnowballEntity snowball && snowball.getWorld() instanceof ServerWorld sw
                ? sw
                : this.instanceWorld;
            if (world != null) {
                this.spawnBedBug(world, projectile.teamId, projectile.lastPos);
            }
            if (entity != null && !entity.isRemoved()) {
                entity.discard();
            }
            this.bedBugProjectiles.remove(entry.getKey());
        }
    }

    private void tickBridgeEggs(MinecraftServer server) {
        for (Map.Entry<UUID, BridgeEggProjectile> entry : List.copyOf(this.bridgeEggProjectiles.entrySet())) {
            Entity entity = findEntity(server, entry.getKey());
            BridgeEggProjectile projectile = entry.getValue();
            if (entity == null || entity.isRemoved() || entity.age > 80 || projectile.blocksPlaced >= 24) {
                if (entity != null && !entity.isRemoved()) {
                    entity.discard();
                }
                this.bridgeEggProjectiles.remove(entry.getKey());
                continue;
            }

            if (entity.getWorld() instanceof ServerWorld world) {
                BlockPos currentBridgePos = entity.getBlockPos().down();
                // Skip placing blocks if the egg hasn't traveled at least 2 blocks from the thrower
                if (entity.squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(projectile.throwOrigin)) > 4.0) {
                    projectile.blocksPlaced += this.placeBridgeLine(world, projectile.teamId, projectile.lastPos.down(), currentBridgePos);
                }
                projectile.lastPos = entity.getBlockPos();
            }
        }
    }

    private void tickDefenders(MinecraftServer server) {
        for (Map.Entry<UUID, String> entry : List.copyOf(this.defenderTeams.entrySet())) {
            Entity entity = findEntity(server, entry.getKey());
            if (!(entity instanceof net.minecraft.entity.mob.MobEntity mob) || !mob.isAlive()) {
                this.defenderTeams.remove(entry.getKey());
                if (entity instanceof net.minecraft.entity.passive.IronGolemEntity) {
                    this.golemHits.remove(entry.getKey());
                }
                continue;
            }

            if (mob instanceof net.minecraft.entity.passive.IronGolemEntity) {
                if (mob.age > 4 * 60 * 20) {
                    mob.discard();
                    this.defenderTeams.remove(entry.getKey());
                    this.golemHits.remove(entry.getKey());
                    continue;
                }
            } else if (mob instanceof net.minecraft.entity.mob.SilverfishEntity) {
                if (mob.age > 15 * 20) {
                    mob.discard();
                    this.defenderTeams.remove(entry.getKey());
                    continue;
                }
            }

            if (this.isFriendlyDefenderTarget(entry.getValue(), mob.getTarget())) {
                mob.setTarget(null);
            }

            ServerPlayerEntity enemy = this.findNearestEnemy(mob, entry.getValue(), 16.0);
            if (enemy != null) {
                mob.setTarget(enemy);
            }
        }
    }

    private void spawnBedBug(ServerWorld world, String teamId, BlockPos pos) {
        if (pos == null) {
            return;
        }
        net.minecraft.entity.mob.SilverfishEntity silverfish = new net.minecraft.entity.mob.SilverfishEntity(net.minecraft.entity.EntityType.SILVERFISH, world);
        silverfish.setPersistent();
        silverfish.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        world.spawnEntity(silverfish);
        this.defenderTeams.put(silverfish.getUuid(), teamId);
    }

    private ServerPlayerEntity findNearestEnemy(net.minecraft.entity.mob.MobEntity mob, String ownerTeamId, double range) {
        if (!(mob.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        net.minecraft.util.math.Box box = mob.getBoundingBox().expand(range);
        return world.getEntitiesByType(net.minecraft.entity.EntityType.PLAYER, box, player ->
                player instanceof ServerPlayerEntity serverPlayer
                    && serverPlayer.isAlive()
                    && !serverPlayer.isSpectator()
                    && !this.isSameTeam(ownerTeamId, serverPlayer))
            .stream()
            .map(ServerPlayerEntity.class::cast)
            .min(java.util.Comparator.comparingDouble(mob::squaredDistanceTo))
            .orElse(null);
    }

    private boolean isSameTeam(String teamId, ServerPlayerEntity player) {
        return teamId != null && teamId.equals(this.teamManager.teamId(player.getUuid()));
    }

    private boolean isFriendlyDefenderTarget(String teamId, net.minecraft.entity.LivingEntity target) {
        if (target == null || teamId == null) {
            return false;
        }
        if (target instanceof ServerPlayerEntity player) {
            return this.isSameTeam(teamId, player);
        }
        return teamId.equals(this.defenderTeams.get(target.getUuid()));
    }

    private Entity findEntity(MinecraftServer server, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private void buildCompactPopUpTower(ServerPlayerEntity player, net.minecraft.world.World world, BlockPos origin) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        BlockState wool = this.teamWoolState(player);
        // LadderBlock.FACING is the direction the ladder faces outward (the side players climb from).
        // This must be the direction back toward the player, i.e. opposite of where they are looking.
        net.minecraft.util.math.Direction ladderFacing = player.getHorizontalFacing().getOpposite();
        BlockState ladder = Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, ladderFacing);

        for (int y = 0; y <= 4; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) {
                        continue;
                    }
                    // Leave a 2-block tall entry gap on the player-facing side (y=1 and y=2)
                    // so the player can actually walk inside to reach the ladder.
                    boolean isFrontFace = (x * ladderFacing.getOffsetX() + z * ladderFacing.getOffsetZ()) == 1;
                    if (isFrontFace && y >= 1 && y <= 2) {
                        continue;
                    }
                    if (Math.abs(x) == 1 || Math.abs(z) == 1 || y == 0) {
                        this.placeUtilityBlock(serverWorld, origin.add(x, y, z), wool);
                    }
                }
            }
        }

        for (int y = 1; y <= 5; y++) {
            this.placeUtilityBlock(serverWorld, origin.add(0, y, 0), ladder);
        }

        // 5×5 solid roof
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                this.placeUtilityBlock(serverWorld, origin.add(x, 5, z), wool);
            }
        }

        // Battlements on the 5×5 roof perimeter
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean perimeter = x == -2 || x == 2 || z == -2 || z == 2;
                boolean corner = (x == -2 || x == 2) && (z == -2 || z == 2);
                boolean alternating = Math.floorMod(x + z, 2) == 0;
                if (perimeter && (corner || alternating)) {
                    this.placeUtilityBlock(serverWorld, origin.add(x, 6, z), wool);
                }
            }
        }
    }

    private int placeBridgeLine(ServerWorld world, String teamId, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));
        int placedSections = 0;
        BlockPos previous = null;

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos center = new BlockPos(
                from.getX() + (int) Math.round(dx * t),
                from.getY() + (int) Math.round(dy * t),
                from.getZ() + (int) Math.round(dz * t)
            );
            if (!center.equals(previous)) {
                this.placeBridgeBlocks(world, teamId, center);
                placedSections++;
                previous = center;
            }
        }

        return placedSections;
    }

    private void placeBridgeBlocks(ServerWorld world, String teamId, BlockPos center) {
        BlockState state = this.teamWoolState(teamId);
        this.placeUtilityBlock(world, center, state);
        this.placeUtilityBlock(world, center.offset(net.minecraft.util.math.Direction.EAST), state);
        this.placeUtilityBlock(world, center.offset(net.minecraft.util.math.Direction.WEST), state);
    }

    private void placeUtilityBlock(ServerWorld world, BlockPos pos, BlockState state) {
        if (!world.getBlockState(pos).isAir()) {
            return;
        }
        dev.frost.miniverse.minigame.arena.ArenaTracker.getManager(world)
            .ifPresentOrElse(
                manager -> manager.setBlock(pos, state),
                () -> world.setBlockState(pos, state)
            );
        // Register as a player-placed block so map protection treats it as breakable.
        dev.frost.miniverse.minigame.core.MinigameRuntime runtime =
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            runtime.context().protectionTracker().addPlacedBlock(pos);
        }
    }

    private BlockState teamWoolState(ServerPlayerEntity player) {
        return this.teamWoolState(this.teamManager.teamId(player.getUuid()));
    }

    private BlockState teamWoolState(String teamId) {
        BedwarsMapConfig.BedwarsTeamConfig config = teamId != null ? this.mapConfig.teams().get(teamId) : null;
        if (config == null || config.color == null) {
            return Blocks.WHITE_WOOL.getDefaultState();
        }

        return switch (config.color) {
            case BLACK -> Blocks.BLACK_WOOL.getDefaultState();
            case DARK_BLUE -> Blocks.BLUE_WOOL.getDefaultState();
            case DARK_GREEN -> Blocks.GREEN_WOOL.getDefaultState();
            case DARK_AQUA -> Blocks.CYAN_WOOL.getDefaultState();
            case DARK_RED -> Blocks.RED_WOOL.getDefaultState();
            case DARK_PURPLE -> Blocks.PURPLE_WOOL.getDefaultState();
            case GOLD -> Blocks.ORANGE_WOOL.getDefaultState();
            case GRAY -> Blocks.LIGHT_GRAY_WOOL.getDefaultState();
            case DARK_GRAY -> Blocks.GRAY_WOOL.getDefaultState();
            case BLUE -> Blocks.LIGHT_BLUE_WOOL.getDefaultState();
            case GREEN -> Blocks.LIME_WOOL.getDefaultState();
            case AQUA -> Blocks.LIGHT_BLUE_WOOL.getDefaultState();
            case RED -> Blocks.RED_WOOL.getDefaultState();
            case LIGHT_PURPLE -> Blocks.MAGENTA_WOOL.getDefaultState();
            case YELLOW -> Blocks.YELLOW_WOOL.getDefaultState();
            case WHITE -> Blocks.WHITE_WOOL.getDefaultState();
            default -> Blocks.WHITE_WOOL.getDefaultState();
        };
    }

    private static class BedBugProjectile {
        private final String teamId;
        private BlockPos lastPos;

        private BedBugProjectile(String teamId, BlockPos lastPos) {
            this.teamId = teamId;
            this.lastPos = lastPos;
        }
    }

    private static class BridgeEggProjectile {
        private final String teamId;
        private final BlockPos throwOrigin; // player's block pos at throw time; used to skip placing blocks at the launch column
        private BlockPos lastPos;
        private int blocksPlaced;

        private BridgeEggProjectile(String teamId, BlockPos throwOrigin) {
            this.teamId = teamId;
            this.throwOrigin = throwOrigin;
            this.lastPos = throwOrigin;
        }
    }

    @Override
    public boolean allowEntityDamage(net.minecraft.entity.LivingEntity entity, net.minecraft.entity.damage.DamageSource source, float amount) {
        if (entity instanceof net.minecraft.entity.passive.IronGolemEntity golem && this.golemHits.containsKey(golem.getUuid())) {
            if (golem.isInvulnerableTo(source)) return false;
            
            int hits = this.golemHits.get(golem.getUuid());
            hits--;
            if (hits <= 0) {
                golem.kill();
                this.golemHits.remove(golem.getUuid());
                return false;
            }
            this.golemHits.put(golem.getUuid(), hits);
            golem.setCustomName(net.minecraft.text.Text.literal(hits + " Hits"));
            
            // Play hurt animation and sound manually so we don't actually lose health
            golem.getWorld().sendEntityStatus(golem, (byte) 2);
            golem.playSound(net.minecraft.sound.SoundEvents.ENTITY_IRON_GOLEM_HURT, 1.0f, 1.0f);
            return false; // cancel actual health damage
        }
        return true;
    }
}
