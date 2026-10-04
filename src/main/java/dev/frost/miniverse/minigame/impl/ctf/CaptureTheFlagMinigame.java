package dev.frost.miniverse.minigame.impl.ctf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.frost.miniverse.chat.ChatRoutingAware;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.MapValidationResult;
import dev.frost.miniverse.minigame.arena.ArenaTracker;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.SessionRoster;
import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.event.BlockBreakAware;
import dev.frost.miniverse.minigame.core.event.BlockBreakBypassAware;
import dev.frost.miniverse.minigame.core.event.EntityInteractAware;
import dev.frost.miniverse.minigame.core.event.ItemUseAware;
import dev.frost.miniverse.minigame.core.event.ItemUseOnBlockAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.RosterAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;
import dev.frost.miniverse.minigame.core.layout.InventoryLayoutAware;
import dev.frost.miniverse.minigame.core.layout.InventoryLayoutFramework;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.visibility.GlowVisibilityAware;
import dev.frost.miniverse.minigame.impl.ctf.death.CtfDeathLifecycleConfig;
import dev.frost.miniverse.minigame.impl.ctf.economy.CtfEconomyManager;
import dev.frost.miniverse.minigame.impl.ctf.economy.CtfPlayerUpgradeState;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagInstance;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagManager;
import dev.frost.miniverse.minigame.impl.ctf.flag.CtfFlagState;
import dev.frost.miniverse.minigame.impl.ctf.powerup.CtfPowerupManager;
import dev.frost.miniverse.minigame.impl.ctf.shop.CtfShopManager;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamMembership;
import dev.frost.miniverse.team.TeamRole;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaptureTheFlagMinigame extends AbstractMinigame implements
    DeathAwareMinigame, TeamManagerProvider, BlockBreakAware, BlockBreakBypassAware,
    EntityInteractAware, SpawnPointAware, RosterAware, PlayerLeaveAware,
    PersistentMinigame, ServerTickAware, InventoryLayoutAware, ItemUseAware, ItemUseOnBlockAware,
    PlayerDamageAware, GlowVisibilityAware, ChatRoutingAware {

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private final TeamManager teamManager = new TeamManager();
    private final Set<String> activeTeamIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> permanentlyEliminated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastDamageTimes = new ConcurrentHashMap<>();

    private DeathLifecycleManager deathLifecycleManager;
    private CtfFlagManager flagManager;
    private final CtfEconomyManager economyManager = new CtfEconomyManager();
    private CtfShopManager shopManager;
    private final CtfPowerupManager powerupManager = new CtfPowerupManager();

    private CaptureTheFlagSettings settings = CaptureTheFlagSettings.defaults();
    private CaptureTheFlagMapConfig mapConfig = new CaptureTheFlagMapConfig(Map.of(), List.of(), null, null);

    private final Map<UUID, ScoreboardTemplate> scoreboards = new ConcurrentHashMap<>();
    private final Map<UUID, BridgeEggProjectile> bridgeEggProjectiles = new ConcurrentHashMap<>();
    private ServerWorld instanceWorld;
    private int matchTicksRemaining = 15 * 60 * 20;
    private boolean inOvertime = false;

    public CaptureTheFlagMinigame() {
    }

    public static ItemStack createCompass() {
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Flag Tracker").formatted(Formatting.GOLD, Formatting.BOLD));
        InventoryLayoutFramework.tagKitItem(compass, "CTF_COMPASS");
        return compass;
    }

    public boolean isInOvertime() {
        return this.inOvertime;
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(GameRules.DO_IMMEDIATE_RESPAWN, true);
        this.applyVanillaGameRule(GameRules.DO_MOB_SPAWNING, false);
        this.applyVanillaGameRule(GameRules.ANNOUNCE_ADVANCEMENTS, false);
        this.applyVanillaGameRule(GameRules.DO_FIRE_TICK, false);
        this.applyVanillaGameRule(GameRules.NATURAL_REGENERATION, false);
        this.setState(GameState.WAITING_FOR_PLAYERS);

        InventoryLayoutFramework.registerGamemode(
            CaptureTheFlagDefinition.ID,
            Set.of("CTF_SWORD", "CTF_BOW", "CTF_ARROWS", "CTF_SHEARS", "CTF_AXE", "CTF_BLOCKS", "CTF_COMPASS")
        );
    }

    public void applySettings(CaptureTheFlagSettings settings, CaptureTheFlagMapConfig mapConfig) {
        this.settings = settings != null ? settings : CaptureTheFlagSettings.defaults();
        this.mapConfig = mapConfig != null ? mapConfig : new CaptureTheFlagMapConfig(Map.of(), List.of(), null, null);
        this.matchTicksRemaining = this.settings.matchDurationMinutes() * 60 * 20;
        this.applyVanillaGameRule(GameRules.NATURAL_REGENERATION, false);
    }

    @Override
    public boolean isChatRoutingEnabled() {
        return this.settings.teamChatEnabled();
    }

    public CaptureTheFlagSettings getSettings() { return settings; }
    public CtfFlagManager getFlagManager() { return flagManager; }
    public CtfEconomyManager getEconomyManager() { return economyManager; }

    public void ensureTeamAssignment(ServerPlayerEntity player, String team) {
        String mapTeamId = this.resolveMapTeamId(team);
        if (mapTeamId != null) {
            this.assignToMapTeam(player, mapTeamId);
            return;
        }
        String fallback = team == null || team.isBlank() ? player.getName().getString() : team;
        this.teamManager.assign(player, fallback, fallback, TeamRole.MEMBER);
    }

    public boolean assignConfiguredMapTeam(ServerPlayerEntity player, String teamId) {
        if (player == null || teamId == null || teamId.isBlank() || !this.mapConfig.teams().containsKey(teamId)) {
            return false;
        }
        this.assignToMapTeam(player, teamId);
        return true;
    }

    private void mapPlayersToTeams(List<ServerPlayerEntity> participants) {
        List<String> mapTeamIds = new ArrayList<>(this.mapConfig.teams().keySet());
        Map<String, Integer> teamSizes = new HashMap<>();
        for (String id : mapTeamIds) {
            teamSizes.put(id, 0);
        }

        List<ServerPlayerEntity> unassigned = new ArrayList<>();
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
        CaptureTheFlagMapConfig.CtfTeamConfig cfg = this.mapConfig.teams().get(teamId);
        if (cfg == null) return;
        this.teamManager.assign(player, teamId, cfg.name, TeamRole.MEMBER);
        if (cfg.color != null) {
            this.teamManager.ensureTeam(teamId, cfg.name).setColor(cfg.color);
        }
    }

    private String resolvePlayerMapTeamId(ServerPlayerEntity player) {
        String currentTeamId = this.teamManager.teamId(player.getUuid());
        String resolved = this.resolveMapTeamId(currentTeamId);
        if (resolved != null) return resolved;
        return this.resolveMapTeamId(this.teamManager.teamLabel(player.getUuid(), ""));
    }

    private String resolveMapTeamId(String requestedTeam) {
        if (requestedTeam == null || requestedTeam.isBlank()) return null;
        if (this.mapConfig.teams().containsKey(requestedTeam)) return requestedTeam;

        String requestedAlias = teamAlias(requestedTeam);
        for (Map.Entry<String, CaptureTheFlagMapConfig.CtfTeamConfig> entry : this.mapConfig.teams().entrySet()) {
            CaptureTheFlagMapConfig.CtfTeamConfig config = entry.getValue();
            if (teamAlias(entry.getKey()).equals(requestedAlias)
                || teamAlias(config.name).equals(requestedAlias)
                || (config.color != null && teamAlias(config.color.getName()).equals(requestedAlias))) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String teamAlias(String value) {
        if (value == null) return "";
        String alias = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return alias.endsWith("team") ? alias.substring(0, alias.length() - 4) : alias;
    }

    public MapValidationResult startValidation() {
        MapValidationResult configResult = this.mapConfig.validate();
        if (!configResult.valid()) return configResult;

        long activeSessionTeams = this.teamManager.snapshots().stream().filter(t -> !t.members().isEmpty()).count();
        if (activeSessionTeams > this.mapConfig.teams().size()) {
            return MapValidationResult.builder().error("Too many teams configured! Map supports " + this.mapConfig.teams().size() + " teams.").build();
        }
        return MapValidationResult.ok();
    }

    public boolean canStartMatch() {
        return true;
    }

    @Override
    public void onMatchStart() {
        this.activeTeamIds.clear();
        this.permanentlyEliminated.clear();
        this.economyManager.clear();
        this.bridgeEggProjectiles.clear();
        this.lastDamageTimes.clear();
        this.matchTicksRemaining = this.settings.matchDurationMinutes() * 60 * 20;
        this.inOvertime = false;

        List<ServerPlayerEntity> participants = this.getOnlineParticipants();
        this.instanceWorld = participants.isEmpty() ? this.context.nullableServer().getOverworld() : participants.get(0).getServerWorld();

        this.mapPlayersToTeams(participants);

        this.flagManager = new CtfFlagManager(this, this.settings, this.mapConfig);
        this.flagManager.init(this.instanceWorld, this.activeTeamIds);

        this.shopManager = new CtfShopManager(this, this.economyManager);
        this.shopManager.spawnNpcs(this.instanceWorld, this.mapConfig);

        this.powerupManager.init(this.instanceWorld, this.mapConfig.powerupLocations());

        for (ServerPlayerEntity player : participants) {
            player.getInventory().clear();
            player.changeGameMode(GameMode.SURVIVAL);
            this.teleportToSpawn(player);

            // Initial loadout with layout preferences
            CtfPlayerUpgradeState upgradeState = this.economyManager.getUpgradeState(player.getUuid());
            List<ItemStack> initialItems = new ArrayList<>();

            ItemStack sword = upgradeState.buildSword(player);
            InventoryLayoutFramework.tagKitItem(sword, "CTF_SWORD");
            initialItems.add(sword);

            ItemStack bow = upgradeState.buildBow(player);
            InventoryLayoutFramework.tagKitItem(bow, "CTF_BOW");
            initialItems.add(bow);

            ItemStack arrows = new ItemStack(Items.ARROW, 12);
            InventoryLayoutFramework.tagKitItem(arrows, "CTF_ARROWS");
            initialItems.add(arrows);

            initialItems.add(createCompass());

            InventoryLayoutFramework.applyLayout(player, CaptureTheFlagDefinition.ID, initialItems);
            upgradeState.equipArmor(player, this.getPlayerTeamColor(player));
        }

        this.deathLifecycleManager = new DeathLifecycleManager(
            new CtfDeathLifecycleConfig(this, this.settings, this.mapConfig, SpectatorService.getInstance(), this.permanentlyEliminated),
            SpectatorService.getInstance()
        );

        this.syncVanillaTeams();
        this.rebuildScoreboard();
        this.setState(GameState.RUNNING);
    }

    @Override
    public void onMatchEnd() {
        if (this.instanceWorld != null) {
            if (this.flagManager != null) this.flagManager.clear(this.instanceWorld);
            if (this.shopManager != null) this.shopManager.clear(this.context.nullableServer());
            if (this.powerupManager != null) this.powerupManager.clear(this.context.nullableServer());
        }
        this.bridgeEggProjectiles.clear();
        if (this.context != null && this.context.nullableServer() != null) {
            for (ScoreboardTemplate board : this.scoreboards.values()) {
                board.cleanup(this.context.nullableServer());
            }
        }
        this.scoreboards.clear();
    }

    @Override
    public String getName() {
        return CaptureTheFlagDefinition.DISPLAY_NAME;
    }

    @Override
    public GameState getState() { return this.state; }

    @Override
    public void setState(GameState state) {
        GameState oldState = this.state;
        this.state = state;
        if (oldState != GameState.FROZEN && state == GameState.FROZEN) {
            List<ServerPlayerEntity> players = this.getOnlineParticipants();
            this.mapPlayersToTeams(players);
            for (ServerPlayerEntity player : players) {
                this.teleportToSpawn(player);
            }
        }
    }

    @Override
    public MatchProgressionValidator.ProgressionState checkProgression(SessionRoster roster) {
        long distinctTeams = roster.allParticipants().stream()
            .map(this.teamManager::teamId)
            .filter(Objects::nonNull)
            .distinct()
            .count();
        if (distinctTeams < 2) {
            return new MatchProgressionValidator.ProgressionState(true, Text.literal("Not enough teams with players"), null);
        }
        return MatchProgressionValidator.ProgressionState.valid();
    }

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() { return this.deathLifecycleManager; }

    @Override
    public TeamManager teamManager() { return this.teamManager; }

    public Formatting getPlayerTeamColor(ServerPlayerEntity player) {
        String teamId = this.teamManager.teamId(player.getUuid());
        return getTeamColor(teamId);
    }

    public Formatting getTeamColor(String teamId) {
        if (teamId == null) return Formatting.WHITE;
        CaptureTheFlagMapConfig.CtfTeamConfig cfg = this.mapConfig.teams().get(teamId);
        return cfg != null && cfg.color != null ? cfg.color : Formatting.WHITE;
    }

    public List<ServerPlayerEntity> getOnlineParticipants() {
        if (this.context == null || this.context.nullableServer() == null) return List.of();
        return this.context.roster().onlinePlayers(this.context.nullableServer());
    }

    public void broadcast(Text text) {
        if (this.context != null && this.context.nullableServer() != null) {
            this.context.nullableServer().getPlayerManager().broadcast(text, false);
        }
    }

    @Override
    public boolean canBypassProtection(ServerPlayerEntity player, BlockPos pos) {
        return false;
    }

    @Override
    public void onBlockBroken(ServerPlayerEntity breaker, ServerWorld world, BlockPos pos, BlockState state) {
    }

    @Override
    public ActionResult onEntityInteract(ServerPlayerEntity player, ServerWorld world, Hand hand, Entity entity) {
        if (this.shopManager != null && this.shopManager.handleInteract(player, entity)) {
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, net.minecraft.world.World world, Hand hand) {
        if (this.getState() != GameState.RUNNING) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.isOf(Items.COMPASS)) {
            CompassTarget target = this.resolveCompassTarget(player);
            if (target != null && target.pos() != null) {
                int dist = (int) Math.round(Math.sqrt(player.squaredDistanceTo(Vec3d.ofCenter(target.pos()))));
                player.sendMessage(Text.literal("✦ Tracking: ").formatted(Formatting.GOLD)
                    .append(Text.literal(target.label()).formatted(Formatting.YELLOW))
                    .append(Text.literal(" (" + dist + "m away)").formatted(Formatting.AQUA)), true);
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.4F);
            } else {
                player.sendMessage(Text.literal("✦ Tracking: No active objective found.").formatted(Formatting.GRAY), true);
            }
            return ActionResult.SUCCESS;
        }

        if (world instanceof ServerWorld serverWorld && stack.getItem() == Items.EGG) {
            net.minecraft.entity.projectile.thrown.EggEntity egg = new net.minecraft.entity.projectile.thrown.EggEntity(serverWorld, player);
            egg.setItem(stack.copyWithCount(1));
            egg.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 1.5F, 1.0F);
            serverWorld.spawnEntity(egg);
            this.bridgeEggProjectiles.put(egg.getUuid(), new BridgeEggProjectile(this.teamManager.teamId(player.getUuid()), player.getBlockPos()));

            if (!player.isCreative()) {
                stack.decrement(1);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseBlock(ServerPlayerEntity player, net.minecraft.world.World world, Hand hand, net.minecraft.util.hit.BlockHitResult hitResult) {
        if (this.getState() != GameState.RUNNING) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (stack.getItem() instanceof net.minecraft.item.BlockItem) {
            BlockPos hitPos = hitResult.getBlockPos();
            BlockState hitState = world.getBlockState(hitPos);
            BlockPos placePos = hitState.isReplaceable() ? hitPos : hitPos.offset(hitResult.getSide());
            if (this.isAboveHeightLimit(placePos)) {
                player.sendMessage(Text.literal("You cannot build above the height limit").formatted(Formatting.RED), false);
                return ActionResult.FAIL;
            }
        }
        return ActionResult.PASS;
    }

    public boolean isAboveHeightLimit(BlockPos pos) {
        if (this.mapConfig != null && this.mapConfig.heightLimitRef() != null) {
            return pos.getY() > this.mapConfig.heightLimitRef();
        }
        return false;
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.getState() != GameState.RUNNING) return true;
        Entity attacker = source.getAttacker();
        if (attacker instanceof ServerPlayerEntity attackingPlayer) {
            String pTeam = this.teamManager.teamId(player.getUuid());
            String aTeam = this.teamManager.teamId(attackingPlayer.getUuid());
            if (pTeam != null && pTeam.equals(aTeam)) {
                return false; // friendly fire disabled
            }
            this.lastDamageTimes.put(attackingPlayer.getUuid(), System.currentTimeMillis());
        }
        this.lastDamageTimes.put(player.getUuid(), System.currentTimeMillis());
        return true;
    }

    @Override
    public boolean canViewerSeeGlowing(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        // Flag carriers are revealed to ALL players (both teammates and enemies, even through invisibility)
        if (this.settings.carrierGlowing() && this.flagManager != null && this.flagManager.isCarrier(target.getUuid())) {
            return true;
        }
        // Teammates can see each other's outline
        String viewerTeam = this.teamManager.teamId(viewer.getUuid());
        String targetTeam = this.teamManager.teamId(target.getUuid());
        return viewerTeam != null && viewerTeam.equals(targetTeam);
    }

    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        String teamId = this.resolvePlayerMapTeamId(player);
        if (teamId == null) return;

        CaptureTheFlagMapConfig.CtfTeamConfig config = this.mapConfig.teams().get(teamId);
        if (config == null || config.spawns.isEmpty()) return;

        MapPosition pos = config.spawns.get(Math.floorMod(player.getUuid().hashCode(), config.spawns.size()));
        player.teleport(player.getServerWorld(), pos.x(), pos.y(), pos.z(), Set.of(), pos.yaw(), pos.pitch());
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
        if (this.flagManager != null) {
            this.flagManager.dropCarriedFlag(player, false);
        }
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject json = new JsonObject();
        JsonArray elim = new JsonArray();
        for (UUID u : this.permanentlyEliminated) {
            elim.add(u.toString());
        }
        json.add("eliminated", elim);
        json.addProperty("timeRemaining", this.matchTicksRemaining);
        return json;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        if (state.has("eliminated")) {
            for (JsonElement e : state.getAsJsonArray("eliminated")) {
                this.permanentlyEliminated.add(UUID.fromString(e.getAsString()));
            }
        }
        if (state.has("timeRemaining")) {
            this.matchTicksRemaining = state.get("timeRemaining").getAsInt();
        }
    }

    @Override
    public void onGameTick(MinecraftServer server) {
        if (this.getState() != GameState.RUNNING) return;

        this.matchTicksRemaining--;
        if (this.matchTicksRemaining <= 0) {
            this.handleTimeout();
            return;
        }

        if (this.instanceWorld == null) {
            this.instanceWorld = server.getOverworld();
        }

        if (this.flagManager != null) {
            this.flagManager.tick(this.instanceWorld);
        }

        if (this.powerupManager != null) {
            this.powerupManager.tick(this.instanceWorld, this.getOnlineParticipants(), this.economyManager);
        }

        this.tickBridgeEggs(server);

        // Void protection check
        if (this.mapConfig.voidLevelRef() != null) {
            int voidY = this.mapConfig.voidLevelRef();
            for (ServerPlayerEntity p : this.getOnlineParticipants()) {
                if (p.getY() <= voidY && p.isAlive()) {
                    if (this.flagManager != null) {
                        this.flagManager.dropCarriedFlag(p, true);
                    }
                    if (this.deathLifecycleManager != null) {
                        this.deathLifecycleManager.handleFatalDamage(p, p.getDamageSources().outOfWorld());
                    }
                }
            }
        }

        // Out-of-combat health regeneration & hunger management
        for (ServerPlayerEntity p : this.getOnlineParticipants()) {
            if (p.isAlive() && !p.isSpectator()) {
                p.getHungerManager().setFoodLevel(20);
                p.getHungerManager().setSaturationLevel(5.0F);

                if (this.settings.naturalRegeneration() && server.getTicks() % 10 == 0) {
                    long lastDamage = this.lastDamageTimes.getOrDefault(p.getUuid(), 0L);
                    if (System.currentTimeMillis() - lastDamage >= 5000L) { // 5s out of combat
                        if (p.getHealth() < p.getMaxHealth()) {
                            p.heal(1.0F);
                        }
                    }
                }
            }
        }

        if (server.getTicks() % 10 == 0) {
            this.updateTrackingCompasses(server);
        }

        if (server.getTicks() % 20 == 0) {
            this.rebuildScoreboard();
        }
    }

    public record CompassTarget(BlockPos pos, String label) {}

    public CompassTarget resolveCompassTarget(ServerPlayerEntity player) {
        if (this.flagManager == null || player == null) return null;

        String myTeamId = this.teamManager.teamId(player.getUuid());

        // 1. If player is carrying an enemy flag, point to friendly home base / dropoff pad
        CtfFlagInstance friendlyFlag = myTeamId != null ? this.flagManager.getFlag(myTeamId) : null;
        for (String teamId : this.activeTeamIds) {
            if (teamId.equals(myTeamId)) continue;
            CtfFlagInstance enemyFlag = this.flagManager.getFlag(teamId);
            if (enemyFlag != null && enemyFlag.state() == CtfFlagState.CARRIED && player.getUuid().equals(enemyFlag.carrierUuid())) {
                BlockPos base = friendlyFlag != null && friendlyFlag.basePos() != null ? BlockPos.ofFloored(friendlyFlag.basePos()) : null;
                if (base == null && myTeamId != null && this.mapConfig.teams().containsKey(myTeamId)) {
                    base = this.mapConfig.teams().get(myTeamId).flagPos;
                }
                return new CompassTarget(base, "Your Base (Deliver Flag!)");
            }
        }

        // 2. If friendly flag is stolen/carried by an enemy, point to that enemy carrier
        if (friendlyFlag != null && friendlyFlag.state() == CtfFlagState.CARRIED && friendlyFlag.carrierUuid() != null) {
            ServerPlayerEntity carrier = player.getServer().getPlayerManager().getPlayer(friendlyFlag.carrierUuid());
            if (carrier != null && carrier.isAlive()) {
                return new CompassTarget(carrier.getBlockPos(), carrier.getName().getString() + " (Enemy Carrier)");
            }
        }

        // 3. If friendly flag is dropped on the ground, point to it to return/defend it
        if (friendlyFlag != null && friendlyFlag.state() == CtfFlagState.DROPPED && friendlyFlag.currentPos() != null) {
            return new CompassTarget(BlockPos.ofFloored(friendlyFlag.currentPos()), "Your Dropped Flag");
        }

        // 4. Default: Find nearest active enemy flag objective
        CompassTarget bestTarget = null;
        double bestDistSq = Double.MAX_VALUE;

        for (String teamId : this.activeTeamIds) {
            if (teamId.equals(myTeamId)) continue;
            CtfFlagInstance enemyFlag = this.flagManager.getFlag(teamId);
            if (enemyFlag == null || enemyFlag.state() == CtfFlagState.CAPTURED) continue;

            BlockPos targetPos = null;
            String label = enemyFlag.teamName() + " Flag";

            if (enemyFlag.state() == CtfFlagState.CARRIED) {
                if (enemyFlag.carrierUuid() != null) {
                    ServerPlayerEntity carrier = player.getServer().getPlayerManager().getPlayer(enemyFlag.carrierUuid());
                    if (carrier != null && carrier.isAlive()) {
                        targetPos = carrier.getBlockPos();
                        label = enemyFlag.teamName() + " Flag (" + carrier.getName().getString() + ")";
                    }
                }
            } else if (enemyFlag.state() == CtfFlagState.DROPPED && enemyFlag.currentPos() != null) {
                targetPos = BlockPos.ofFloored(enemyFlag.currentPos());
                label = enemyFlag.teamName() + " Flag (Dropped)";
            } else if (enemyFlag.basePos() != null) { // AT_BASE
                targetPos = BlockPos.ofFloored(enemyFlag.basePos());
                label = enemyFlag.teamName() + " Flag (Base)";
            }

            if (targetPos != null) {
                double distSq = player.squaredDistanceTo(Vec3d.ofCenter(targetPos));
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestTarget = new CompassTarget(targetPos, label);
                }
            }
        }

        return bestTarget;
    }

    private void updateTrackingCompasses(MinecraftServer server) {
        for (ServerPlayerEntity p : this.getOnlineParticipants()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            CompassTarget target = this.resolveCompassTarget(p);
            if (target == null || target.pos() == null) continue;

            LodestoneTrackerComponent tracker = new LodestoneTrackerComponent(
                Optional.of(GlobalPos.create(p.getWorld().getRegistryKey(), target.pos())),
                false
            );

            for (int i = 0; i < p.getInventory().size(); i++) {
                ItemStack stack = p.getInventory().getStack(i);
                if (stack.isOf(Items.COMPASS)) {
                    LodestoneTrackerComponent existing = stack.get(DataComponentTypes.LODESTONE_TRACKER);
                    if (!Objects.equals(existing, tracker)) {
                        stack.set(DataComponentTypes.LODESTONE_TRACKER, tracker);
                    }
                }
            }
        }
    }

    public void notifyFlagDestroyed(String teamId) {
        CaptureTheFlagMapConfig.CtfTeamConfig config = this.mapConfig.teams().get(teamId);
        String name = config != null ? config.name : teamId;
        Formatting color = config != null ? config.color : Formatting.RED;

        this.broadcast(Text.literal("☠ " + name + "'s flag was captured! They will no longer respawn!")
            .formatted(color, Formatting.BOLD));

        for (ServerPlayerEntity p : this.getOnlineParticipants()) {
            if (teamId.equals(this.teamManager.teamId(p.getUuid()))) {
                p.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.0F);
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("FLAG DESTROYED!").formatted(Formatting.RED, Formatting.BOLD)));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("You will no longer respawn!").formatted(Formatting.GRAY)));
            }
        }
    }

    public void checkWinCondition() {
        if (this.getState() != GameState.RUNNING || this.flagManager == null) return;

        // In Overtime, a leading team wins if no flags are in play
        if (this.inOvertime) {
            String topTeam = null;
            int maxCaptures = -1;
            boolean tie = false;

            for (String teamId : this.activeTeamIds) {
                int caps = this.flagManager.getCaptures(teamId);
                if (caps > maxCaptures) {
                    maxCaptures = caps;
                    topTeam = teamId;
                    tie = false;
                } else if (caps == maxCaptures) {
                    tie = true;
                }
            }

            boolean anyFlagCarriedOrDropped = false;
            for (String teamId : this.activeTeamIds) {
                CtfFlagInstance flag = this.flagManager.getFlag(teamId);
                if (flag != null && (flag.state() == CtfFlagState.CARRIED || flag.state() == CtfFlagState.DROPPED)) {
                    anyFlagCarriedOrDropped = true;
                    break;
                }
            }

            if (topTeam != null && !tie && maxCaptures > 0 && !anyFlagCarriedOrDropped) {
                this.broadcast(Text.literal("★ SUDDEN DEATH VICTORY! ★").formatted(Formatting.GOLD, Formatting.BOLD));
                this.declareWinner(topTeam);
                return;
            }
        }

        // 1. Elimination Mode win check
        if (this.settings.eliminationMode()) {
            List<String> survivingTeams = new ArrayList<>();
            for (String teamId : this.activeTeamIds) {
                CtfFlagInstance flag = this.flagManager.getFlag(teamId);
                boolean flagSafe = flag != null && flag.state() != CtfFlagState.CAPTURED;
                boolean hasSurvivingPlayer = this.teamManager.ensureTeam(teamId, teamId).members().stream()
                    .map(TeamMembership::playerUuid)
                    .anyMatch(id -> !this.permanentlyEliminated.contains(id));

                if (flagSafe || hasSurvivingPlayer) {
                    survivingTeams.add(teamId);
                }
            }

            if (survivingTeams.size() == 1) {
                this.declareWinner(survivingTeams.get(0));
            } else if (survivingTeams.isEmpty()) {
                this.declareDraw();
            }
        } else {
            // 2. Standard Mode win check (target captures)
            for (String teamId : this.activeTeamIds) {
                int captures = this.flagManager.getCaptures(teamId);
                if (captures >= this.settings.targetCaptures()) {
                    this.declareWinner(teamId);
                    return;
                }
            }
        }
    }

    private void handleTimeout() {
        if (this.flagManager == null) {
            this.declareDraw();
            return;
        }

        if (!this.inOvertime && this.settings.suddenDeath()) {
            boolean anyFlagCarriedOrDropped = false;
            for (String teamId : this.activeTeamIds) {
                CtfFlagInstance flag = this.flagManager.getFlag(teamId);
                if (flag != null && (flag.state() == CtfFlagState.CARRIED || flag.state() == CtfFlagState.DROPPED)) {
                    anyFlagCarriedOrDropped = true;
                    break;
                }
            }

            int topCaptures = -1;
            boolean tiedTop = false;
            for (String teamId : this.activeTeamIds) {
                int caps = this.flagManager.getCaptures(teamId);
                if (caps > topCaptures) {
                    topCaptures = caps;
                    tiedTop = false;
                } else if (caps == topCaptures) {
                    tiedTop = true;
                }
            }

            // Trigger Sudden Death / Overtime if flags are contested OR scores are tied
            if (anyFlagCarriedOrDropped || tiedTop) {
                this.inOvertime = true;
                this.matchTicksRemaining = 3 * 60 * 20; // 3-minute overtime limit
                this.broadcast(Text.literal("⚡ SUDDEN DEATH OVERTIME! ⚡").formatted(Formatting.RED, Formatting.BOLD));
                this.broadcast(Text.literal("The match clock has expired! Next capture wins!").formatted(Formatting.YELLOW));
                for (ServerPlayerEntity p : this.getOnlineParticipants()) {
                    p.playSound(SoundEvents.EVENT_RAID_HORN.value(), 1.5F, 1.0F);
                    p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("SUDDEN DEATH!").formatted(Formatting.RED, Formatting.BOLD)));
                    p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Next capture wins!").formatted(Formatting.GOLD)));
                }
                this.rebuildScoreboard();
                return;
            }
        }

        String topTeam = null;
        int maxCaptures = -1;
        boolean tie = false;

        for (String teamId : this.activeTeamIds) {
            int caps = this.flagManager.getCaptures(teamId);
            if (caps > maxCaptures) {
                maxCaptures = caps;
                topTeam = teamId;
                tie = false;
            } else if (caps == maxCaptures) {
                tie = true;
            }
        }

        if (topTeam != null && !tie && maxCaptures > 0) {
            this.declareWinner(topTeam);
        } else {
            this.declareDraw();
        }
    }

    private void declareWinner(String winnerTeamId) {
        String winnerLabel = this.teamManager.ensureTeam(winnerTeamId, winnerTeamId).label();
        List<ServerPlayerEntity> winners = this.getOnlineParticipants().stream()
            .filter(p -> winnerTeamId.equals(this.teamManager.teamId(p.getUuid())))
            .toList();

        MinigameManager.getInstance().getMatchLifecycleController().endMatch(
            this.runtime,
            MatchEndResult.winners(winners, Text.literal(winnerLabel + " Wins!")),
            MatchLifecycleOptions.defaults(CaptureTheFlagDefinition.DISPLAY_NAME)
        );
    }

    private void declareDraw() {
        MinigameManager.getInstance().getMatchLifecycleController().endMatch(
            this.runtime,
            new MatchEndResult(Set.of(), Text.literal("Draw!")),
            MatchLifecycleOptions.defaults(CaptureTheFlagDefinition.DISPLAY_NAME)
        );
    }

    public void rebuildScoreboard() {
        if (this.context == null || this.context.nullableServer() == null || this.getState() != GameState.RUNNING) return;
        List<ServerPlayerEntity> participants = this.getOnlineParticipants();

        int secondsRemaining = Math.max(0, this.matchTicksRemaining / 20);
        int mins = secondsRemaining / 60;
        int secs = secondsRemaining % 60;
        String timeDisplay = this.inOvertime
            ? String.format("§c§lOVERTIME §f(%02d:%02d)", mins, secs)
            : String.format("§f%02d:%02d", mins, secs);

        for (ServerPlayerEntity player : participants) {
            ScoreboardTemplate board = this.scoreboards.computeIfAbsent(player.getUuid(), id -> {
                ScoreboardTemplate t = new ScoreboardTemplate(CaptureTheFlagDefinition.ID + "_" + id,
                    Text.literal("CAPTURE THE FLAG").formatted(Formatting.GOLD, Formatting.BOLD));
                t.show(player);
                return t;
            });

            board.clearLines();
            board.addLine(Text.literal("Time: " + timeDisplay));
            board.addBlankLine();

            for (String teamId : this.activeTeamIds) {
                CaptureTheFlagMapConfig.CtfTeamConfig cfg = this.mapConfig.teams().get(teamId);
                String teamLabel = cfg != null ? cfg.name : teamId;
                Formatting color = cfg != null ? cfg.color : Formatting.WHITE;

                CtfFlagInstance flag = this.flagManager != null ? this.flagManager.getFlag(teamId) : null;
                String flagStatus;
                if (flag == null) {
                    flagStatus = "§7-";
                } else {
                    flagStatus = switch (flag.state()) {
                        case AT_BASE -> "§a✔ Base";
                        case CARRIED -> {
                            ServerPlayerEntity c = player.getServer().getPlayerManager().getPlayer(flag.carrierUuid());
                            yield "§c⚑ " + (c != null ? c.getName().getString() : "Stolen");
                        }
                        case DROPPED -> "§e⚠ Dropped (" + (int) Math.ceil(flag.droppedTicksRemaining() / 20.0) + "s)";
                        case CAPTURED -> "§4☠ Lost";
                    };
                }

                int score = this.flagManager != null ? this.flagManager.getCaptures(teamId) : 0;
                board.addLine(Text.literal("● " + teamLabel + " ").formatted(color).append(Text.literal(flagStatus)));
                board.addLine(Text.literal("  Captures: §e" + score));
            }

            board.addBlankLine();
            board.addLine(Text.literal("Coins: §6" + this.economyManager.getCoins(player.getUuid())));
            board.addLine(Text.literal("Gems: §a" + this.economyManager.getGems(player.getUuid())));

            board.sendLineUpdates();
        }
    }

    private void tickBridgeEggs(MinecraftServer server) {
        for (Map.Entry<UUID, BridgeEggProjectile> entry : List.copyOf(this.bridgeEggProjectiles.entrySet())) {
            Entity entity = findEntity(server, entry.getKey());
            BridgeEggProjectile projectile = entry.getValue();
            if (entity == null || entity.isRemoved() || entity.age > 80 || projectile.blocksPlaced >= 24) {
                if (entity != null && !entity.isRemoved()) entity.discard();
                this.bridgeEggProjectiles.remove(entry.getKey());
                continue;
            }

            if (entity.getWorld() instanceof ServerWorld world) {
                BlockPos currentBridgePos = entity.getBlockPos().down();
                if (entity.squaredDistanceTo(Vec3d.ofCenter(projectile.throwOrigin)) > 4.0) {
                    projectile.blocksPlaced += this.placeBridgeLine(world, projectile.teamId, projectile.lastPos.down(), currentBridgePos);
                }
                projectile.lastPos = entity.getBlockPos();
            }
        }
    }

    private int placeBridgeLine(ServerWorld world, String teamId, BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))));
        int placed = 0;
        BlockPos previous = null;

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            BlockPos center = new BlockPos(
                from.getX() + (int) Math.round(dx * t),
                from.getY() + (int) Math.round(dy * t),
                from.getZ() + (int) Math.round(dz * t)
            );
            if (!center.equals(previous)) {
                if (this.isAboveHeightLimit(center)) {
                    continue;
                }
                BlockState state = this.teamWoolState(teamId);
                this.placeUtilityBlock(world, center, state);
                placed++;
                previous = center;
            }
        }
        return placed;
    }

    private void placeUtilityBlock(ServerWorld world, BlockPos pos, BlockState state) {
        if (!world.getBlockState(pos).isAir()) return;
        ArenaTracker.getManager(world).ifPresentOrElse(
            m -> m.setBlock(pos, state),
            () -> world.setBlockState(pos, state)
        );
        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            runtime.context().protectionTracker().addPlacedBlock(pos);
        }
    }

    private BlockState teamWoolState(String teamId) {
        Formatting color = this.getTeamColor(teamId);
        return switch (color) {
            case RED, DARK_RED -> Blocks.RED_WOOL.getDefaultState();
            case BLUE, DARK_BLUE -> Blocks.BLUE_WOOL.getDefaultState();
            case GREEN, DARK_GREEN -> Blocks.LIME_WOOL.getDefaultState();
            case YELLOW, GOLD -> Blocks.YELLOW_WOOL.getDefaultState();
            case AQUA, DARK_AQUA -> Blocks.LIGHT_BLUE_WOOL.getDefaultState();
            case LIGHT_PURPLE, DARK_PURPLE -> Blocks.MAGENTA_WOOL.getDefaultState();
            case GRAY, DARK_GRAY -> Blocks.GRAY_WOOL.getDefaultState();
            default -> Blocks.WHITE_WOOL.getDefaultState();
        };
    }

    private Entity findEntity(MinecraftServer server, UUID uuid) {
        if (uuid == null) return null;
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    @Override
    public String inventoryLayoutGamemodeId() {
        return CaptureTheFlagDefinition.ID;
    }

    @Override
    public boolean isTeamBased() {
        return false; // Handled dynamically via VanillaTeamAdapter in AbstractMinigame
    }

    public dev.frost.miniverse.minigame.core.MinigameContext getContext() {
        return this.context;
    }

    private static class BridgeEggProjectile {
        final String teamId;
        final BlockPos throwOrigin;
        BlockPos lastPos;
        int blocksPlaced;

        BridgeEggProjectile(String teamId, BlockPos throwOrigin) {
            this.teamId = teamId;
            this.throwOrigin = throwOrigin;
            this.lastPos = throwOrigin;
        }
    }
}
