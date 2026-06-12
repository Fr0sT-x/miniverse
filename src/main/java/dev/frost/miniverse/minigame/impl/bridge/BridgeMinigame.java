package dev.frost.miniverse.minigame.impl.bridge;

import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.map.editor.MapMarker;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.event.EntityDeathAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerJoinAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.PlayerRegionAware;
import dev.frost.miniverse.minigame.core.event.PlayerRespawnAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.layout.InventoryLayoutAware;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamRole;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.List;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;

public final class BridgeMinigame extends AbstractMinigame implements PlayerDamageAware, PlayerRegionAware, TeamManagerProvider, PersistentMinigame, InventoryLayoutAware {

    public static final String RED_TEAM = "red";
    public static final String BLUE_TEAM = "blue";
    private ScoreboardTemplate scoreboard;
    private ScoreboardLine redScoreLine;
    private ScoreboardLine blueScoreLine;
    private ScoreboardLine timeLine;

    private final TeamManager teams = new TeamManager();
    private final Set<UUID> redPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> bluePlayers = ConcurrentHashMap.newKeySet();
    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("bridge");

    private BridgeSettings settings = BridgeSettings.fromNbt(null);
    private BridgeMapConfig mapConfig = new BridgeMapConfig(List.of(), List.of(), null, null);
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    @Nullable
    private MinecraftServer server;

    private int redScore = 0;
    private int blueScore = 0;
    private int ticksRemaining;
    private int stateTicks = 0;
    private boolean acceptingGoals = true;
    private String lastScorerName = "";
    private Formatting lastScorerColor = Formatting.WHITE;
    private UUID lastScorerUuid;
    private String lastScorerTeamId;
    private final Map<UUID, Integer> arrowTimers = new java.util.HashMap<>();

    @Override
    protected void onPlayerJoinGame(ServerPlayerEntity player, MinecraftServer server) {
        if (this.getTeamId(player) != null && !this.mapConfig.redSpawns().isEmpty()) {
            this.teleportToTeamSpawn(player);
        }
    }

    public void applySettings(BridgeSettings settings, BridgeMapConfig mapConfig) {
        this.settings = settings == null ? BridgeSettings.fromNbt(null) : settings;
        this.mapConfig = mapConfig == null ? new BridgeMapConfig(List.of(), List.of(), null, null) : mapConfig;
    }

    @Override
    public String inventoryLayoutGamemodeId() {
        return BridgeDefinition.ID;
    }

    public boolean canStartMatch() {
        if (this.participants().size() < 2) return false;
        MapValidationResult validation = this.mapConfig.validate();
        if (!validation.valid()) {
            dev.frost.miniverse.Miniverse.LOGGER.warn("Bridge canStartMatch failed validation: {}", validation.errors());
            return false;
        }
        return true;
    }

    public MapValidationResult startValidation() {
        MapValidationResult.Builder builder = MapValidationResult.builder();
        if (this.participants().size() < 2) {
            builder.error("Need at least two players to start The Bridge.");
        }

        MapValidationResult mapValidation = this.mapConfig.validate();
        for (String error : mapValidation.errors()) {
            builder.error(error);
        }
        for (String warning : mapValidation.warnings()) {
            builder.warning(warning);
        }
        return builder.build();
    }

    private final Map<UUID, Integer> respawnTimers = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
        this.setState(GameState.WAITING_FOR_PLAYERS);
        
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.registerGamemode(
            BridgeDefinition.ID, 
            Set.of("BRIDGE_SWORD", "BRIDGE_PICKAXE", "BRIDGE_BLOCKS", "BRIDGE_BOW", "BRIDGE_GAPPLES", "BRIDGE_ARROW")
        );

        this.server = null;
        this.redPlayers.clear();
        this.bluePlayers.clear();
        this.teams.clear();
        this.redScore = 0;
        this.blueScore = 0;
        this.ticksRemaining = 0;
        this.stateTicks = 0;
        this.acceptingGoals = false;
        this.respawnTimers.clear();
        this.arrowTimers.clear();

        java.util.Optional<com.google.gson.JsonObject> sessionJsonOpt = dev.frost.miniverse.session.SessionRuntimeConfig.getSessionJson();
        if (sessionJsonOpt.isPresent()) {
            com.google.gson.JsonObject sessionJson = sessionJsonOpt.get();
            if (sessionJson.has("teams") && sessionJson.get("teams").isJsonArray()) {
                com.google.gson.JsonArray teamsArray = sessionJson.getAsJsonArray("teams");
                java.util.List<UUID> unassigned = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement elem : teamsArray) {
                    if (elem.isJsonObject()) {
                        com.google.gson.JsonObject teamObj = elem.getAsJsonObject();
                        String label = dev.frost.miniverse.session.SessionConfigJson.string(teamObj, "label", "");
                        boolean isRed = RED_TEAM.equalsIgnoreCase(label);
                        boolean isBlue = BLUE_TEAM.equalsIgnoreCase(label);
                        if (teamObj.has("members") && teamObj.get("members").isJsonArray()) {
                            for (com.google.gson.JsonElement memElem : teamObj.getAsJsonArray("members")) {
                                if (memElem.isJsonObject()) {
                                    String uuidStr = dev.frost.miniverse.session.SessionConfigJson.string(memElem.getAsJsonObject(), "uuid", "");
                                    if (!uuidStr.isBlank()) {
                                        try {
                                            UUID u = UUID.fromString(uuidStr);
                                            if (isRed) this.redPlayers.add(u);
                                            else if (isBlue) this.bluePlayers.add(u);
                                            else unassigned.add(u);
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Balance unassigned players immediately
                for (UUID u : unassigned) {
                    if (!this.redPlayers.contains(u) && !this.bluePlayers.contains(u)) {
                        if (this.redPlayers.size() <= this.bluePlayers.size()) {
                            this.redPlayers.add(u);
                        } else {
                            this.bluePlayers.add(u);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        return new GlobalMatchRules(true, true, true, true, true, true, true);
    }

    @Override
    protected boolean isTeamBased() {
        return true;
    }

    @Override
    protected void onMatchStart() {
        try {
            this.doStartGame();
            
            dev.frost.miniverse.common.NetworkConstants.LayoutSupportPayload payload = new dev.frost.miniverse.common.NetworkConstants.LayoutSupportPayload(BridgeDefinition.ID, "default");
            for (ServerPlayerEntity player : this.participants()) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        } catch (Exception e) {
            dev.frost.miniverse.Miniverse.LOGGER.error("Failed to start Bridge game", e);
            this.broadcast(Text.literal("Error starting The Bridge. Match cancelled.").formatted(Formatting.RED));
        }
    }

    private void doStartGame() {
        List<ServerPlayerEntity> participants = this.participants();
        if (participants.size() < 2) {
            this.broadcast(Text.literal("Need at least two players to start The Bridge.").formatted(Formatting.RED));
            return;
        }

        // Assign unassigned players to balance teams and set team roles
        for (ServerPlayerEntity player : participants) {
            if (!this.redPlayers.contains(player.getUuid()) && !this.bluePlayers.contains(player.getUuid())) {
                if (this.redPlayers.size() <= this.bluePlayers.size()) {
                    this.redPlayers.add(player.getUuid());
                } else {
                    this.bluePlayers.add(player.getUuid());
                }
            }
            if (this.redPlayers.contains(player.getUuid())) {
                this.teams.assign(player, RED_TEAM, "Red", TeamRole.MEMBER);
            } else if (this.bluePlayers.contains(player.getUuid())) {
                this.teams.assign(player, BLUE_TEAM, "Blue", TeamRole.MEMBER);
            }
            if (this.getState() != GameState.FROZEN) {
                this.teleportToTeamSpawnSafe(player);
            }
            player.changeGameMode(GameMode.SURVIVAL);
        }

        this.startRound();
        this.syncVanillaTeams();
        
        this.ticksRemaining = 15 * 60 * 20; // 15 minutes match timer
        this.setState(GameState.PLAYING);
        this.broadcast(Text.literal("The Bridge has started! First to " + this.settings.targetScore() + " wins.").formatted(Formatting.YELLOW));
    }

    private void startRound() {
        this.setState(GameState.PLAYING);
        this.setRuntimeState(GameState.PLAYING);
        this.acceptingGoals = true;
        this.stateTicks = 0;

        for (ServerPlayerEntity player : this.participants()) {
            player.getInventory().clear();
            this.applyKit(player);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 3 * 20, 255, false, false, true));
        }

        this.rebuildScoreboard();
    }

    @Override
    protected void onMatchEnd() {
        dev.frost.miniverse.common.NetworkConstants.LayoutSupportPayload clearPayload = new dev.frost.miniverse.common.NetworkConstants.LayoutSupportPayload("", "");
        for (ServerPlayerEntity player : this.participants()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, clearPayload);
        }

        this.setState(GameState.ENDING);
        this.setRuntimeState(GameState.ENDING);

        if (this.server != null) {
            if (this.scoreboard != null) {
                this.scoreboard.cleanup(this.server);
            }
            this.clearVanillaTeams();
        }
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;

        if (this.getState() == GameState.ROUND_RESET) {
            this.stateTicks++;
            int elapsed = this.stateTicks;
            int remainingTicks = 100 - elapsed;
            int secondsLeft = (int) Math.ceil(remainingTicks / 20.0);
            
            if ((elapsed == 1 || elapsed % 20 == 0) && secondsLeft > 0) {
                Text subtitle = Text.literal("Round Starts In " + secondsLeft).formatted(Formatting.YELLOW);
                Text title = Text.empty()
                    .append(Text.literal(this.lastScorerName != null ? this.lastScorerName : "Someone").formatted(this.lastScorerColor))
                    .append(Text.literal(" Scored!").formatted(Formatting.WHITE));
                
                for (ServerPlayerEntity p : this.participants()) {
                    p.networkHandler.sendPacket(new TitleS2CPacket(title));
                    p.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
                    p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
                }
            }

            if (elapsed >= 100) {
                for (ServerPlayerEntity p : this.participants()) {
                    FreezeService.getInstance().unfreeze(p, FreezeReason.ROUND_RESET);
                    p.networkHandler.sendPacket(new TitleS2CPacket(Text.empty()));
                    p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.empty()));
                }
                this.startRound();
            }
            return;
        }

        if (this.getState() != GameState.PLAYING) {
            return;
        }

        for (ServerPlayerEntity p : this.participants()) {
            if (p.getInventory().count(Items.ARROW) > 0) {
                this.arrowTimers.remove(p.getUuid());
                p.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(0.0f, 0, 0));
            } else {
                int ticks = this.arrowTimers.getOrDefault(p.getUuid(), 0);
                ticks++;
                if (ticks >= 70) {
                    ItemStack arrow = new ItemStack(Items.ARROW);
                    dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(arrow, "BRIDGE_ARROW");
                    
                    com.google.gson.JsonObject profile = dev.frost.miniverse.player.PlayerDataStore.getProfile(p.getUuid());
                    int slot = -1;
                    if (profile.has("layouts") && profile.getAsJsonObject("layouts").has(BridgeDefinition.ID)) {
                        com.google.gson.JsonObject layout = profile.getAsJsonObject("layouts").getAsJsonObject(BridgeDefinition.ID);
                        if (layout.has("BRIDGE_ARROW")) {
                            slot = layout.get("BRIDGE_ARROW").getAsInt();
                        }
                    }
                    if (slot >= 0 && slot < 9 && p.getInventory().getStack(slot).isEmpty()) {
                        p.getInventory().setStack(slot, arrow);
                    } else {
                        p.getInventory().insertStack(arrow);
                    }
                    
                    this.arrowTimers.remove(p.getUuid());
                    p.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(0.0f, 0, 0));
                } else {
                    this.arrowTimers.put(p.getUuid(), ticks);
                    float progress = ticks / 70.0f;
                    int secondsToRegen = (int) Math.ceil((70 - ticks) / 20.0);
                    p.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(progress, 0, secondsToRegen));
                }
            }
        }

        for (ServerPlayerEntity p : this.participants()) {
            net.minecraft.util.math.Box bounds = p.getBoundingBox().expand(20.0);
            for (net.minecraft.entity.ItemEntity itemEntity : p.getServerWorld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class, bounds, e -> true)) {
                ItemStack stack = itemEntity.getStack();
                if (stack.isOf(Items.RED_TERRACOTTA) || stack.isOf(Items.BLUE_TERRACOTTA) || stack.isOf(Items.WHITE_TERRACOTTA)) {
                    if (dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.getKitItemId(stack) == null) {
                        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(stack, "BRIDGE_BLOCKS");
                        itemEntity.setStack(stack);
                    }
                }
            }
        }

        this.ticksRemaining = Math.max(0, this.ticksRemaining - 1);
        if (this.ticksRemaining % 20 == 0) {
            this.updateScoreboardTick();
            if (this.ticksRemaining <= 0) {
                this.endMatchOnTimer();
            }
        }

        if (this.mapConfig.voidLevelRef() != null) {
            int voidY = this.mapConfig.voidLevelRef() - this.settings.voidDeathOffset();
            for (ServerPlayerEntity p : this.participants()) {
                if (p.getY() <= voidY && p.getHealth() > 0 && !this.respawnTimers.containsKey(p.getUuid())) {
                    this.dieToVoid(p);
                }
            }
        }

        if (!this.respawnTimers.isEmpty()) {
            for (Map.Entry<UUID, Integer> entry : this.respawnTimers.entrySet()) {
                int remaining = entry.getValue() - 1;
                if (remaining <= 0) {
                    this.respawnTimers.remove(entry.getKey());
                    ServerPlayerEntity p = this.server.getPlayerManager().getPlayer(entry.getKey());
                    if (p != null && this.isParticipant(p) && this.getState() == GameState.PLAYING) {
                        p.changeGameMode(GameMode.SURVIVAL);
                        this.applyKit(p);
                        this.teleportToTeamSpawn(p);
                        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 3 * 20, 255, false, false, true));
                    }
                } else {
                    this.respawnTimers.put(entry.getKey(), remaining);
                }
            }
        }
    }

    @Override
    public void onPlayerEnterRegion(ServerPlayerEntity player, MapMarker region) {
        if (!this.acceptingGoals || this.getState() != GameState.PLAYING) {
            return;
        }
        if (!this.isParticipant(player) || player.isDead()) {
            return;
        }

        String teamId = this.getTeamId(player);
        if (teamId == null) {
            return;
        }

        if (BridgeDefinition.RED_TEAM_GOAL.equals(region.definitionKey())) {
            if (BLUE_TEAM.equals(teamId)) {
                this.scoreGoal(BLUE_TEAM, player, region);
            }
        } else if (BridgeDefinition.BLUE_TEAM_GOAL.equals(region.definitionKey())) {
            if (RED_TEAM.equals(teamId)) {
                this.scoreGoal(RED_TEAM, player, region);
            }
        }
    }

    private void scoreGoal(String scoringTeamId, ServerPlayerEntity scorer, MapMarker region) {
        this.acceptingGoals = false;
        this.setState(GameState.ROUND_RESET);
        this.setRuntimeState(GameState.ROUND_RESET);
        this.stateTicks = 0;
        this.arrowTimers.clear();

        this.lastScorerName = scorer.getName().getString();
        this.lastScorerColor = RED_TEAM.equals(scoringTeamId) ? Formatting.RED : Formatting.BLUE;
        this.lastScorerUuid = scorer.getUuid();
        this.lastScorerTeamId = scoringTeamId;

        if (RED_TEAM.equals(scoringTeamId)) {
            this.redScore++;
        } else {
            this.blueScore++;
        }
        
        BridgeStatsEvents.GOAL_SCORED.invoker().onGoalScored(scorer, scoringTeamId, 1);
        this.updateScoreboardTick();

        // Spawn firework 35 blocks above Goal Region Center
        double cx = scorer.getX();
        double cy = scorer.getY();
        double cz = scorer.getZ();
        if (region != null && region.points().size() >= 2) {
            dev.frost.miniverse.map.MapPosition min = region.points().get(0);
            dev.frost.miniverse.map.MapPosition max = region.points().get(1);
            cx = (min.x() + max.x()) / 2.0;
            cy = (min.y() + max.y()) / 2.0;
            cz = (min.z() + max.z()) / 2.0;
        }

        int colorInt = RED_TEAM.equals(scoringTeamId) ? 0xFF0000 : 0x0000FF;
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
        List<FireworkExplosionComponent> explosions = List.of(new FireworkExplosionComponent(
            FireworkExplosionComponent.Type.LARGE_BALL,
            it.unimi.dsi.fastutil.ints.IntList.of(colorInt),
            it.unimi.dsi.fastutil.ints.IntList.of(),
            false,
            false
        ));
        firework.set(DataComponentTypes.FIREWORKS, new FireworksComponent(0, explosions));
        FireworkRocketEntity entity = new FireworkRocketEntity(scorer.getServerWorld(), cx, cy + 5, cz, firework);
        scorer.getServerWorld().spawnEntity(entity);

        for (ServerPlayerEntity p : this.participants()) {
            this.teleportToTeamSpawnSafe(p);
            
            p.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
            p.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 1.0f);
            p.playSound(SoundEvents.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
            
            p.getInventory().clear();
            this.applyKit(p);
            FreezeService.getInstance().freeze(p, FreezeReason.ROUND_RESET);
            p.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(0.0f, 0, 0));
        }

        if (this.redScore >= this.settings.targetScore()) {
            this.endMatch(RED_TEAM);
        } else if (this.blueScore >= this.settings.targetScore()) {
            this.endMatch(BLUE_TEAM);
        }
    }

    private void dieToVoid(ServerPlayerEntity player) {
        player.setHealth(20.0F);
        player.clearStatusEffects();
        player.getInventory().clear();
        player.fallDistance = 0.0f;
        player.setVelocity(0, 0, 0);
        player.velocityModified = true;
        this.teleportToTeamSpawnSafe(player);
        this.broadcast(Text.literal(player.getName().getString() + " fell into the void.").formatted(Formatting.GRAY));
        player.changeGameMode(GameMode.SURVIVAL);
        this.applyKit(player);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 3 * 20, 255, false, false, true));
    }

    private void endMatchOnTimer() {
        if (this.redScore > this.blueScore) {
            this.endMatch(RED_TEAM);
        } else if (this.blueScore > this.redScore) {
            this.endMatch(BLUE_TEAM);
        } else {
            BridgeStatsEvents.MATCH_DRAW.invoker().onMatchDraw(this.redScore);
            this.startEndSequence(this.participants(), Text.literal("Draw").formatted(Formatting.YELLOW));
        }
    }

    private void endMatch(String winningTeamId) {
        BridgeStatsEvents.MATCH_WON.invoker().onMatchWon(winningTeamId, this.redScore, this.blueScore);
        if (RED_TEAM.equals(winningTeamId)) {
            this.startEndSequence(this.livePlayers(this.redPlayers), Text.literal("Red Team").formatted(Formatting.RED));
        } else {
            this.startEndSequence(this.livePlayers(this.bluePlayers), Text.literal("Blue Team").formatted(Formatting.BLUE));
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (!this.isParticipant(oldPlayer)) {
            return;
        }
        this.replaceParticipant(oldPlayer, newPlayer);
        
        if (this.redPlayers.contains(oldPlayer.getUuid())) {
            this.redPlayers.remove(oldPlayer.getUuid());
            this.redPlayers.add(newPlayer.getUuid());
            this.teams.assign(newPlayer, RED_TEAM, "Red", TeamRole.MEMBER);
        } else if (this.bluePlayers.contains(oldPlayer.getUuid())) {
            this.bluePlayers.remove(oldPlayer.getUuid());
            this.bluePlayers.add(newPlayer.getUuid());
            this.teams.assign(newPlayer, BLUE_TEAM, "Blue", TeamRole.MEMBER);
        }

        // Always teleport to team spawn on respawn (handles freeze, playing, etc.)
        this.teleportToTeamSpawn(newPlayer);
        if (this.getState() == GameState.PLAYING) {
            this.applyKit(newPlayer);
            newPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 3 * 20, 255, false, false, true));
        }
        this.syncVanillaTeams();
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (!this.isParticipant(player)) {
            return true;
        }

        // Ignore damage if player is already "dead" and waiting to respawn
        if (this.respawnTimers.containsKey(player.getUuid())) {
            return false;
        }

        // During non-playing states (FROZEN, WAITING, STARTING), block ALL damage
        // This prevents void deaths during the freeze countdown on void maps
        if (this.getState() != GameState.PLAYING && this.getState() != GameState.ROUND_RESET) {
            // If player is somehow falling into the void during freeze, teleport them back
            if (player.getY() < -64) {
                this.teleportToTeamSpawn(player);
                player.setHealth(20.0F);
            }
            return false;
        }

        // During PLAYING: intercept lethal damage to bypass death screen
        if (player.getHealth() - amount <= 0.0F) {
            player.setHealth(20.0F);
            player.changeGameMode(GameMode.SPECTATOR);
            player.clearStatusEffects();
            player.getInventory().clear();
            // Teleport to team spawn immediately so they don't fall forever as spectator
            this.teleportToTeamSpawnSafe(player);
            this.broadcast(source.getDeathMessage(player).copy().formatted(Formatting.GRAY));
            this.respawnTimers.put(player.getUuid(), 3 * 20);
            return false;
        }

        if (!(source.getAttacker() instanceof ServerPlayerEntity attacker) || !this.isParticipant(attacker)) {
            return true;
        }
        String victimTeam = this.getTeamId(player);
        String attackerTeam = this.getTeamId(attacker);
        return victimTeam == null || attackerTeam == null || !victimTeam.equals(attackerTeam);
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (!this.isParticipant(player)) {
            return;
        }
        this.redPlayers.remove(player.getUuid());
        this.bluePlayers.remove(player.getUuid());
    }

    @Override
    public TeamManager teamManager() {
        return this.teams;
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject json = new JsonObject();
        json.addProperty("state", this.getState().name());
        json.addProperty("ticksRemaining", this.ticksRemaining);
        json.addProperty("redScore", this.redScore);
        json.addProperty("blueScore", this.blueScore);
        return json;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        this.setState(GameState.valueOf(state.has("state") ? state.get("state").getAsString() : GameState.WAITING_FOR_PLAYERS.name()));
        this.ticksRemaining = state.has("ticksRemaining") ? state.get("ticksRemaining").getAsInt() : 0;
        this.redScore = state.has("redScore") ? state.get("redScore").getAsInt() : 0;
        this.blueScore = state.has("blueScore") ? state.get("blueScore").getAsInt() : 0;
    }

    @Override
    public String getName() {
        return BridgeDefinition.DISPLAY_NAME;
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    @Override
    public void setState(GameState state) {
        GameState oldState = this.getState();
        this.state = state == null ? GameState.WAITING_FOR_PLAYERS : state;
        
        if (oldState != GameState.FROZEN && this.getState() == GameState.FROZEN) {
            for (ServerPlayerEntity player : this.participants()) {
                this.teleportToTeamSpawn(player);
                player.changeGameMode(GameMode.SURVIVAL);
            }
        }
    }

    @Override
    public boolean canBuild() {
        if (this.getState() == GameState.ROUND_RESET) return false;
        return this.settings.allowBuilding();
    }

    @Override
    public boolean canBreakBlocks() {
        if (this.getState() == GameState.ROUND_RESET) return false;
        return this.settings.allowBlockBreaking();
    }

    public boolean isAboveHeightLimit(net.minecraft.util.math.BlockPos pos) {
        if (this.mapConfig.heightLimitRef() != null) {
            int limitY = this.mapConfig.heightLimitRef() + this.settings.heightLimitOffset();
            return pos.getY() > limitY;
        }
        return false;
    }

    private void applyKit(ServerPlayerEntity player) {
        List<ItemStack> kit = new ArrayList<>();
        
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(sword, "BRIDGE_SWORD");
        kit.add(sword);
        
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        RegistryWrapper.WrapperLookup registries = player.getServerWorld().getRegistryManager();
        RegistryWrapper.Impl<Enchantment> enchantmentRegistry = registries.getWrapperOrThrow(RegistryKeys.ENCHANTMENT);
        RegistryEntry<Enchantment> efficiency = enchantmentRegistry.getOrThrow(Enchantments.EFFICIENCY);
        pickaxe.addEnchantment(efficiency, 2);
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(pickaxe, "BRIDGE_PICKAXE");
        kit.add(pickaxe);
        
        String teamId = this.getTeamId(player);
        int colorInt = 0xFFFFFF;
        ItemStack blocks1;
        ItemStack blocks2;
        if (RED_TEAM.equals(teamId)) {
            blocks1 = new ItemStack(Items.RED_TERRACOTTA, 64);
            blocks2 = new ItemStack(Items.RED_TERRACOTTA, 64);
            colorInt = 0xFF0000;
        } else if (BLUE_TEAM.equals(teamId)) {
            blocks1 = new ItemStack(Items.BLUE_TERRACOTTA, 64);
            blocks2 = new ItemStack(Items.BLUE_TERRACOTTA, 64);
            colorInt = 0x0000FF;
        } else {
            blocks1 = new ItemStack(Items.WHITE_TERRACOTTA, 64);
            blocks2 = new ItemStack(Items.WHITE_TERRACOTTA, 64);
        }
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(blocks1, "BRIDGE_BLOCKS");
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(blocks2, "BRIDGE_BLOCKS");
        kit.add(blocks1);
        kit.add(blocks2);
        
        ItemStack bow = new ItemStack(Items.BOW);
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(bow, "BRIDGE_BOW");
        kit.add(bow);
        
        ItemStack apples = new ItemStack(Items.GOLDEN_APPLE, 8);
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(apples, "BRIDGE_GAPPLES");
        kit.add(apples);
        
        ItemStack arrow = new ItemStack(Items.ARROW, 1);
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.tagKitItem(arrow, "BRIDGE_ARROW");
        kit.add(arrow);
        
        dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework.applyLayout(player, BridgeDefinition.ID, kit);
        
        dev.frost.miniverse.common.NetworkConstants.LayoutSupportPayload payload = new dev.frost.miniverse.common.NetworkConstants.LayoutSupportPayload(BridgeDefinition.ID, "default");
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        
        ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
        ItemStack legs = new ItemStack(Items.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        
        chest.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(colorInt, false));
        legs.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(colorInt, false));
        boots.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(colorInt, false));
        
        player.getInventory().armor.set(2, chest);
        player.getInventory().armor.set(1, legs);
        player.getInventory().armor.set(0, boots);
    }

    private void startEndSequence(List<ServerPlayerEntity> winners, Text winnerLabel) {
        this.setState(GameState.MATCH_OVER);
        this.setRuntimeState(GameState.MATCH_OVER);
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime == null) {
            return;
        }
        MatchLifecycleController.getInstance().endMatch(
            runtime,
            MatchEndResult.winners(winners, winnerLabel),
            MatchLifecycleOptions.defaults(BridgeDefinition.DISPLAY_NAME)
        );
    }

    private void teleportToTeamSpawn(ServerPlayerEntity player) {
        String teamId = this.getTeamId(player);
        List<MapPosition> spawns = RED_TEAM.equals(teamId) ? this.mapConfig.redSpawns() : this.mapConfig.blueSpawns();
        if (spawns.isEmpty()) {
            return;
        }
        MapPosition spawn = spawns.get(Math.floorMod(player.getUuid().hashCode(), spawns.size()));
        ServerWorld world = player.getServerWorld();
        player.teleport(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
    }

    public void ensureTeamAssignment(ServerPlayerEntity player, String teamId) {
        UUID uuid = player.getUuid();
        if (this.redPlayers.contains(uuid) || this.bluePlayers.contains(uuid)) {
            return;
        }
        if (RED_TEAM.equalsIgnoreCase(teamId)) {
            this.redPlayers.add(uuid);
            this.teams.assign(player, RED_TEAM, "Red", TeamRole.MEMBER);
        } else if (BLUE_TEAM.equalsIgnoreCase(teamId)) {
            this.bluePlayers.add(uuid);
            this.teams.assign(player, BLUE_TEAM, "Blue", TeamRole.MEMBER);
        }
    }

    public void teleportToTeamSpawnSafe(ServerPlayerEntity player) {
        String teamId = this.getTeamId(player);
        List<MapPosition> spawns = RED_TEAM.equals(teamId) ? this.mapConfig.redSpawns()
            : BLUE_TEAM.equals(teamId) ? this.mapConfig.blueSpawns()
            : List.of();
        if (spawns.isEmpty()) {
            return;
        }
        MapPosition spawn = spawns.get(Math.floorMod(player.getUuid().hashCode(), spawns.size()));
        ServerWorld world = player.getServerWorld();
        player.teleport(world, spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
    }

    @Override
    protected void syncVanillaTeams() {
        if (this.server == null) {
            return;
        }
        this.vanillaTeams.syncSnapshots(this.server, this.teams.snapshots(), snapshot -> {
            Formatting color = RED_TEAM.equals(snapshot.id()) ? Formatting.RED : Formatting.BLUE;
            return dev.frost.miniverse.minigame.core.util.VanillaTeamSync.aliveOptionsTemplate()
                .withColor(color)
                .withFriendlyFireAllowed(false);
        });
    }

    private void rebuildScoreboard() {
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("The Bridge").formatted(Formatting.AQUA, Formatting.BOLD)));
            this.scoreboard.show(this.participants());
        }

        this.scoreboard.clearLines();
        this.redScoreLine = this.scoreboard.addLine(Text.literal("Red: " + this.redScore).formatted(Formatting.RED));
        this.blueScoreLine = this.scoreboard.addLine(Text.literal("Blue: " + this.blueScore).formatted(Formatting.BLUE));
        this.scoreboard.addBlankLine();
        this.scoreboard.addLine(Text.literal("Target: " + this.settings.targetScore()));
        this.scoreboard.addBlankLine();
        this.timeLine = this.scoreboard.addLine(Text.literal("Time: " + Math.max(0, this.ticksRemaining / 20)));
        this.scoreboard.resendStructure();
    }

    private void updateScoreboardTick() {
        if (this.redScoreLine != null) {
            this.redScoreLine.setText(Text.literal("Red: " + this.redScore).formatted(Formatting.RED));
            this.redScoreLine.updateAll();
        }
        if (this.blueScoreLine != null) {
            this.blueScoreLine.setText(Text.literal("Blue: " + this.blueScore).formatted(Formatting.BLUE));
            this.blueScoreLine.updateAll();
        }
        if (this.timeLine != null) {
            this.timeLine.setText(Text.literal("Time: " + Math.max(0, this.ticksRemaining / 20)));
            this.timeLine.updateAll();
        }
    }



    private List<ServerPlayerEntity> livePlayers(Set<UUID> ids) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            ServerPlayerEntity p = this.findParticipant(id);
            if (p != null) {
                players.add(p);
            }
        }
        return players;
    }

    // isParticipant is inherited from AbstractMinigame

    private String getTeamId(ServerPlayerEntity player) {
        if (this.redPlayers.contains(player.getUuid())) return RED_TEAM;
        if (this.bluePlayers.contains(player.getUuid())) return BLUE_TEAM;
        return null;
    }

    protected List<ServerPlayerEntity> participants() {
        return this.context != null ? this.context.liveParticipants() : List.of();
    }

    protected boolean isParticipant(ServerPlayerEntity player) {
        return this.context != null && this.context.participants().contains(player);
    }

    protected void replaceParticipant(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer) {
        if (this.context != null) {
            this.context.participants().remove(oldPlayer);
            this.context.participants().add(newPlayer);
        }
    }

    protected void setRuntimeState(GameState state) {
        if (this.context != null) this.context.setState(state);
    }

    protected ServerPlayerEntity findParticipant(UUID uuid) {
        if (this.context != null) {
            return this.context.resolvePlayer(uuid).orElse(null);
        }
        return null;
    }

    private void broadcast(Text message) {
        for (ServerPlayerEntity player : this.participants()) {
            player.sendMessage(message, false);
        }
    }
}
