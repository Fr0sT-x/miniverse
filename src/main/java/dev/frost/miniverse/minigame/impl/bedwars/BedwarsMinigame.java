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

public class BedwarsMinigame extends AbstractMinigame implements
    DeathAwareMinigame, TeamManagerProvider, BlockBreakAware, EntityInteractAware, 
    SpawnPointAware, RosterAware, PlayerLeaveAware, PersistentMinigame, 
    ServerTickAware, InventoryLayoutAware, ItemUseAware, ItemUseOnBlockAware, dev.frost.miniverse.minigame.core.event.BlockBreakBypassAware {

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private final TeamManager teamManager = new TeamManager();
    private final Map<String, BedTeamState> bedTeamStates = new ConcurrentHashMap<>();
    private final Set<UUID> permanentlyEliminated = ConcurrentHashMap.newKeySet();
    private DeathLifecycleManager deathLifecycleManager;
    private dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsGeneratorManager generatorManager;
    private dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsShopManager shopManager;
    private dev.frost.miniverse.minigame.impl.bedwars.upgrade.BedwarsTeamUpgradeManager upgradeManager;
    private dev.frost.miniverse.minigame.impl.bedwars.HologramManager hologramManager;
    private dev.frost.miniverse.minigame.impl.bedwars.BedwarsCountdownService countdownService;
    private dev.frost.miniverse.minigame.impl.bedwars.visibility.BedwarsVisibilityManager visibilityManager;
    private BedwarsSettings settings = BedwarsSettings.fromNbt(null);
    private BedwarsMapConfig mapConfig = new BedwarsMapConfig(Map.of(), List.of(), List.of(), List.of(), List.of(), null, 64);
    
    private final Map<UUID, ScoreboardTemplate> scoreboards = new ConcurrentHashMap<>();
    
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
        this.setState(GameState.WAITING_FOR_PLAYERS);

        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.registerGamemode(
            BedwarsDefinition.ID,
            Set.of("BEDWARS_SWORD", "BEDWARS_PICKAXE", "BEDWARS_AXE", "BEDWARS_BLOCKS")
        );
    }

    public void applySettings(BedwarsSettings settings, BedwarsMapConfig mapConfig) {
        this.settings = settings != null ? settings : BedwarsSettings.fromNbt(null);
        this.mapConfig = mapConfig != null ? mapConfig : new BedwarsMapConfig(Map.of(), List.of(), List.of(), List.of(), List.of(), null, 64);
    }

    public void ensureTeamAssignment(ServerPlayerEntity player, String team) {
        if (this.teamManager.teamId(player.getUuid()) == null) {
            BedwarsMapConfig.BedwarsTeamConfig cfg = this.mapConfig.teams().get(team);
            String label = cfg != null ? cfg.name : team;
            this.teamManager.assign(player, team, label, dev.frost.miniverse.team.TeamRole.MEMBER);
        }
    }

    public MapValidationResult startValidation() {
        return MapValidationResult.ok();
    }

    public boolean canStartMatch() {
        return true;
    }

    @Override
    public void onMatchStart() {

        // Remap generic session groups to the actual map teams
        java.util.List<dev.frost.miniverse.team.TeamSnapshot> activeSessionTeams = this.teamManager.snapshots();
        java.util.List<String> mapTeamIds = new java.util.ArrayList<>(this.mapConfig.teams().keySet());
        
        for (int i = 0; i < Math.min(activeSessionTeams.size(), mapTeamIds.size()); i++) {
            dev.frost.miniverse.team.TeamSnapshot sessionTeam = activeSessionTeams.get(i);
            String mapId = mapTeamIds.get(i);
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

        for (String teamId : this.mapConfig.teams().keySet()) {
            this.bedTeamStates.put(teamId, new BedTeamState());
        }
        
        this.hologramManager = new dev.frost.miniverse.minigame.impl.bedwars.HologramManager();
        this.generatorManager = new dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsGeneratorManager(this.mapConfig, this.settings, this.hologramManager);
        this.shopManager = new dev.frost.miniverse.minigame.impl.bedwars.shop.BedwarsShopManager(this.mapConfig, this.settings);
        this.upgradeManager = new dev.frost.miniverse.minigame.impl.bedwars.upgrade.BedwarsTeamUpgradeManager(this.mapConfig.teams().keySet(), this);
        this.visibilityManager = new dev.frost.miniverse.minigame.impl.bedwars.visibility.BedwarsVisibilityManager(this);
        this.countdownService = new dev.frost.miniverse.minigame.impl.bedwars.BedwarsCountdownService(this, this.generatorManager);
        
        // Setup players
        List<ServerPlayerEntity> participants = this.context.roster().onlinePlayers(this.context.nullableServer());
        this.shopManager.initPlayers(participants);
        ServerWorld instanceWorld = participants.isEmpty() ? this.context.nullableServer().getOverworld() : participants.get(0).getServerWorld();
        this.shopManager.spawnNpcs(instanceWorld, this.mapConfig.shopNpcs());
        this.upgradeManager.spawnNpcs(instanceWorld, this.mapConfig.upgradeNpcs());
        java.util.Iterator<String> teamIter = this.mapConfig.teams().keySet().iterator();
        
        for (ServerPlayerEntity player : participants) {
            if (!teamIter.hasNext()) teamIter = this.mapConfig.teams().keySet().iterator();
            if (teamIter.hasNext()) {
                String teamId = teamIter.next();
                this.ensureTeamAssignment(player, teamId);
            }
            this.teleportToSpawn(player);
            player.getInventory().clear();
            this.equipBaseArmor(player);
            player.getInventory().insertStack(new net.minecraft.item.ItemStack(net.minecraft.item.Items.WOODEN_SWORD));
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        }
        
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
        this.state = state;
    }

    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(SessionRoster roster) {
        if (roster.size() < 2) {
            return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(true, net.minecraft.text.Text.literal("Not enough players"), null);
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
            return true;
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

    public void checkWinCondition() {
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
            net.minecraft.entity.projectile.FireballEntity fireball = new net.minecraft.entity.projectile.FireballEntity(world, player, player.getRotationVector(), 1);
            fireball.setPosition(player.getX(), player.getEyeY() - 0.1, player.getZ());
            world.spawnEntity(fireball);
            
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
        String teamId = this.teamManager.teamId(player.getUuid());
        if (teamId != null) {
            BedwarsMapConfig.BedwarsTeamConfig config = this.mapConfig.teams().get(teamId);
            if (config != null && !config.spawns.isEmpty()) {
                dev.frost.miniverse.map.MapPosition pos = config.spawns.get(0);
                if (this.runtime != null && this.context.nullableServer() != null) {
                    player.teleport(player.getServerWorld(), pos.x() + 0.5, pos.y(), pos.z() + 0.5, java.util.Set.of(), pos.yaw(), pos.pitch());
                }
            }
        }
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
            this.generatorManager.tick(this.context.nullableServer().getOverworld());
        }
        if (this.countdownService != null && this.context.nullableServer() != null) {
            this.countdownService.tick(this.context.nullableServer());
        }
        if (this.visibilityManager != null && this.context.nullableServer() != null) {
            this.visibilityManager.sync(this.context.nullableServer());
        }
        if (server.getTicks() % 20 == 0) {
            this.rebuildScoreboard();
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
                String status = state.isBedAlive() ? "§a✔" : "§c☠";
                board.addLine(net.minecraft.text.Text.literal("● " + teamLabel + " " + status));
            }
            
            board.addBlankLine();
            board.addLine(net.minecraft.text.Text.literal("Iron: §f" + player.getInventory().count(dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.IRON.item())).formatted(net.minecraft.util.Formatting.GRAY));
            board.addLine(net.minecraft.text.Text.literal("Gold: §f" + player.getInventory().count(dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.GOLD.item())).formatted(net.minecraft.util.Formatting.GRAY));
            board.addLine(net.minecraft.text.Text.literal("Diamond: §f" + player.getInventory().count(dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.DIAMOND.item())).formatted(net.minecraft.util.Formatting.GRAY));
            board.addLine(net.minecraft.text.Text.literal("Emerald: §f" + player.getInventory().count(dev.frost.miniverse.minigame.impl.bedwars.economy.BedwarsCurrency.EMERALD.item())).formatted(net.minecraft.util.Formatting.GRAY));
            
            board.resendStructure();
        }
    }

    @Override
    public String inventoryLayoutGamemodeId() {
        return BedwarsDefinition.ID;
    }
}
