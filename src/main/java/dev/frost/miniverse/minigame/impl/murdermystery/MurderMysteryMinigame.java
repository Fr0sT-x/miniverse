package dev.frost.miniverse.minigame.impl.murdermystery;

import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.Minigame;
import dev.frost.miniverse.minigame.core.MinigameContext;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.MinigameRuntime;
import dev.frost.miniverse.minigame.core.PauseAwareMinigame;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.RuntimeContextAware;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.corpse.CorpseManager;
import dev.frost.miniverse.minigame.core.event.ItemUseAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerJoinAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.role.RoleManager;
import dev.frost.miniverse.minigame.core.spectator.SpectatorMode;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders;
import dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies;
import dev.frost.miniverse.minigame.core.visibility.VisibilityManager;
import dev.frost.miniverse.minigame.impl.murdermystery.role.DetectiveRole;
import dev.frost.miniverse.minigame.impl.murdermystery.role.InnocentRole;
import dev.frost.miniverse.minigame.impl.murdermystery.role.MurdererRole;
import dev.frost.miniverse.minigame.impl.murdermystery.role.SpectatorRole;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.rules.GlobalMatchRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.death.config.DeathLifecycleConfig;
import dev.frost.miniverse.minigame.core.death.policy.impl.SpectateForeverPolicy;
import dev.frost.miniverse.minigame.impl.murdermystery.death.MurderMysteryDeathCallbacks;
import dev.frost.miniverse.minigame.impl.murdermystery.death.MurderMysteryDeathPolicy;
import dev.frost.miniverse.minigame.impl.murdermystery.death.MurderMysteryRespawnStrategy;
import dev.frost.miniverse.minigame.impl.murdermystery.death.MurderMysterySpectatorPolicy;

public class MurderMysteryMinigame extends AbstractMinigame implements DeathAwareMinigame, SpawnPointAware {

    private static final String NAME = MurderMysteryDefinition.ID;
    private ScoreboardTemplate scoreboard;
    private ScoreboardLine timeLine;
    private ScoreboardLine innocentsLine;
    
    private DeathLifecycleManager deathLifecycleManager;

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }
    
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private boolean paused = false;
    
    private MurderMysterySettings settings;
    private MurderMysteryMapConfig mapConfig;
    
    private final RoleManager roleManager = new RoleManager();
    private final VisibilityManager visibilityManager = new VisibilityManager("murdermystery", roleManager);
    private final CorpseManager corpseManager = new CorpseManager();
    
    private final VirtualEconomyManager economyManager = new VirtualEconomyManager();
    private final MurderMysteryWeaponManager weaponManager = new MurderMysteryWeaponManager();
    private final MurderMysteryWinConditionManager winConditionManager = new MurderMysteryWinConditionManager(roleManager);
    private CoinManager coinManager;
    private ShopManager shopManager;

    private int elapsedTicks = 0;
    private MinecraftServer server;
    private int nextSpawnIndex = 0;


    public void applySettings(MurderMysterySettings settings) {
        applySettings(settings, null);
    }

    public void applySettings(MurderMysterySettings settings, String preSerializedMapConfig) {
        this.settings = settings;
        this.mapConfig = MurderMysteryMapConfig.load(settings.mapId(), preSerializedMapConfig);
        this.coinManager = new CoinManager(economyManager, mapConfig.coinSpawns(), settings.coinSpawnIntervalTicks());
        this.shopManager = new ShopManager(economyManager, weaponManager, roleManager, settings);
    }

    @Override
    public void initialize() {
        if (this.context != null) this.context.setState(GameState.WAITING_FOR_PLAYERS);
        this.paused = false;
        this.elapsedTicks = 0;
        if (server != null) {
            roleManager.cleanup(server);
            visibilityManager.clear(server);
        }
        corpseManager.cleanup(server);
        economyManager.clear();
        weaponManager.clear();
        if (coinManager != null) coinManager.clear();
        if (shopManager != null) shopManager.clear();
        nextSpawnIndex = 0;
        
        DeathLifecycleConfig config = new DeathLifecycleConfig() {
            @Override
            public dev.frost.miniverse.minigame.core.death.policy.DeathPolicy getDeathPolicy() {
                return new MurderMysteryDeathPolicy();
            }

            @Override
            public dev.frost.miniverse.minigame.core.death.policy.DeathSpectatorPolicy getSpectatorPolicy() {
                return new MurderMysterySpectatorPolicy(SpectatorService.getInstance());
            }

            @Override
            public dev.frost.miniverse.minigame.core.death.policy.PostDeathPolicy createPostDeathPolicy() {
                return new SpectateForeverPolicy();
            }

            @Override
            public dev.frost.miniverse.minigame.core.death.policy.RespawnStrategy getRespawnStrategy() {
                return new MurderMysteryRespawnStrategy(MurderMysteryMinigame.this);
            }

            @Override
            public GameMode resolveRespawnGameMode() {
                return GameMode.SPECTATOR;
            }

            @Override
            public dev.frost.miniverse.minigame.core.death.config.DeathLifecycleCallbacks getCallbacks() {
                return new MurderMysteryDeathCallbacks(MurderMysteryMinigame.this);
            }

            @Override
            public String resolveTeamId(java.util.UUID playerId) {
                return null;
            }

            @Override
            public String resolveMatchIdentifier() {
                return getName();
            }
        };
        this.deathLifecycleManager = new DeathLifecycleManager(config, SpectatorService.getInstance());
    }

    @Override
    protected GlobalMatchRules configureGameRules() {
        // doImmediateRespawn=false required: framework calls changeGameMode(SPECTATOR) on fatal damage; 
        // client must not auto-respawn before the framework transition completes. See DECISIONS.md D04.
        return GlobalMatchRules.defaults(true, false);
    }

    @Override
    protected boolean isTeamBased() {
        return false;
    }

    @Override
    protected void onMatchStart() {
        if (this.getState() == GameState.RUNNING) return;
        if (this.context != null) {
            this.context.setState(GameState.RUNNING);
            if (this.server == null) {
                this.server = this.context.nullableServer();
            }
        } else {
            return;
        }
        
        List<ServerPlayerEntity> players = new ArrayList<>(this.context.liveParticipants());
        Collections.shuffle(players);
        
        if (players.size() >= 1) {
            roleManager.assignRole(players.get(0), new MurdererRole());
            weaponManager.giveMurdererWeapon(players.get(0));
            players.get(0).sendMessage(Text.literal("You are the Murderer!").formatted(Formatting.RED), false);
            GameMessenger.showGameTitle(Collections.singleton(players.get(0)), Text.literal("MURDERER").formatted(Formatting.RED), Text.literal("Kill everyone!").formatted(Formatting.GRAY));
        }
        
        int detectiveCount = Math.min(settings.detectiveCount(), Math.max(0, players.size() - 1));
        for (int i = 1; i <= detectiveCount; i++) {
            roleManager.assignRole(players.get(i), new DetectiveRole());
            weaponManager.giveDetectiveWeapon(players.get(i));
            players.get(i).sendMessage(Text.literal("You are the Detective!").formatted(Formatting.BLUE), false);
            GameMessenger.showGameTitle(Collections.singleton(players.get(i)), Text.literal("DETECTIVE").formatted(Formatting.BLUE), Text.literal("Find and kill the Murderer!").formatted(Formatting.GRAY));
        }
        
        for (int i = detectiveCount + 1; i < players.size(); i++) {
            roleManager.assignRole(players.get(i), new InnocentRole());
            players.get(i).sendMessage(Text.literal("You are Innocent!").formatted(Formatting.GREEN), false);
            GameMessenger.showGameTitle(Collections.singleton(players.get(i)), Text.literal("INNOCENT").formatted(Formatting.GREEN), Text.literal("Stay alive as long as you can!").formatted(Formatting.GRAY));
        }
        
        for (ServerPlayerEntity p : players) {
            p.changeGameMode(GameMode.ADVENTURE);
        }

        if (!players.isEmpty() && server != null && shopManager != null) {
            shopManager.spawnNpcs(players.get(0).getServerWorld(), mapConfig.shopNpcs());
        }

        visibilityManager.sync(server);
        rebuildScoreboard();
    }

    @Override
    protected void onMatchEnd() {
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleMatchEnding(this::getPlayerByUuid);
        }
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
        }
        initialize();
        if (server != null && this.scoreboard != null) {
            this.scoreboard.cleanup(server);
        }
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        this.server = server;
        if (this.getState() != GameState.RUNNING || paused) return;

        this.elapsedTicks++;
        List<ServerPlayerEntity> activePlayers = new ArrayList<>();
        for (ServerPlayerEntity p : context.liveParticipants()) {
            if (!roleManager.hasRole(p, SpectatorRole.class)) {
                activePlayers.add(p);
            }
        }
        
        if (!activePlayers.isEmpty() && coinManager != null) {
            coinManager.tick(activePlayers.get(0).getServerWorld(), activePlayers);
        }
        
        ServerPlayerEntity picker = weaponManager.checkBowPickup(activePlayers);
        if (picker != null && !roleManager.hasRole(picker, MurdererRole.class)) {
            weaponManager.giveDetectiveWeapon(picker);
            GameMessenger.broadcast(context.liveParticipants(), Text.literal("A player has picked up the Detective's bow!").formatted(Formatting.AQUA));
        }

        if (this.elapsedTicks >= settings.roundDurationTicks()) {
            endMatch(MurderMysteryWinConditionManager.WinResult.INNOCENT_WIN);
        }

        if (this.elapsedTicks % 20 == 0) {
            updateScoreboardTick();
        }

        for (ServerPlayerEntity p : activePlayers) {
            if (p.getY() < -64) {
                if (this.deathLifecycleManager != null) {
                    this.deathLifecycleManager.handleFatalDamage(p, p.getDamageSources().outOfWorld());
                }
                teleportToRandomSpawn(p);
            }
            int coins = economyManager.getBalance(p);
            p.sendMessage(Text.literal("Coins: ").formatted(Formatting.YELLOW).append(Text.literal(String.valueOf(coins)).formatted(Formatting.GOLD)), true);
        }
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.getState() != GameState.RUNNING) {
            return false;
        }
        if (!context.liveParticipants().contains(player)) {
            return false;
        }
        if (roleManager.hasRole(player, SpectatorRole.class)) {
            return false;
        }

        if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
            if (roleManager.hasRole(attacker, SpectatorRole.class)) {
                return false;
            }
            if (source.getSource() instanceof ArrowEntity arrow) {
                arrow.discard();
                if (!roleManager.hasRole(player, MurdererRole.class)) {
                    attacker.sendMessage(Text.literal("You shot an innocent!").formatted(Formatting.RED), false);
                    if (this.deathLifecycleManager != null) {
                        this.deathLifecycleManager.handleFatalDamage(attacker, source); // Accidentally killing innocent kills the attacker
                    }
                }
                if (this.deathLifecycleManager != null) {
                    this.deathLifecycleManager.handleFatalDamage(player, source);
                }
                return false;
            } else if (roleManager.hasRole(attacker, MurdererRole.class)) {
                player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, net.minecraft.sound.SoundCategory.PLAYERS, 0.65f, 1.0f);
                player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_ARROW_HIT_PLAYER, net.minecraft.sound.SoundCategory.PLAYERS, 0.65f, 0.7f);
                if (this.deathLifecycleManager != null) {
                    this.deathLifecycleManager.handleFatalDamage(player, source);
                }
                return false;
            }
        }
        return false;
    }

    // TODO: Migrate EntityDeathAware.onEntityDeath override if needed
    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        // Handled by DeathLifecycleManager via allowDamage()
    }

    private void teleportToMapSpawns(List<ServerPlayerEntity> players) {
        if (mapConfig == null || mapConfig.spawnPoints().isEmpty()) return;
        List<dev.frost.miniverse.map.MapPosition> spawns = mapConfig.spawnPoints();
        for (int i = 0; i < players.size(); i++) {
            teleport(players.get(i), spawns.get(i % spawns.size()));
        }
    }

    private void teleportToRandomSpawn(ServerPlayerEntity player) {
        if (mapConfig == null || mapConfig.spawnPoints().isEmpty()) return;
        List<dev.frost.miniverse.map.MapPosition> spawns = mapConfig.spawnPoints();
        teleport(player, spawns.get((nextSpawnIndex++) % spawns.size()));
    }

    /** SpawnPointAware — called by the framework after applySettings() completes during player join. */
    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        GameState state = this.getState();
        if (state == GameState.WAITING_FOR_PLAYERS || state == GameState.FROZEN || state == GameState.STARTING) {
            teleportToRandomSpawn(player);
        }
    }

    private void teleport(ServerPlayerEntity player, dev.frost.miniverse.map.MapPosition spawn) {
        ServerWorld world = player.getServerWorld();
        player.teleport(world, spawn.x(), spawn.y(), spawn.z(), java.util.Set.of(), spawn.yaw(), spawn.pitch());
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleDisconnect(player);
        }
        if (roleManager.hasRole(player, DetectiveRole.class)) {
            weaponManager.dropDetectiveWeapon(player.getServerWorld(), player.getPos());
            GameMessenger.broadcast(context.liveParticipants(), Text.literal("Detective disconnected! The Detective's Bow has dropped.").formatted(Formatting.RED));
        }
        roleManager.removeRole(player);
        context.roster().remove(player);
        visibilityManager.sync(server);
        checkWinConditions();
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        context.roster().add(player);
        if (this.getState() == GameState.RUNNING || this.getState() == GameState.ENDING) {
            roleManager.assignRole(player, new SpectatorRole());
            SpectatorService.getInstance().startSpectating(
                player, 
                SpectatorPolicies.unrestricted(), 
                SpectatorTargetProviders.roster(), 
                dev.frost.miniverse.minigame.core.spectator.SpectatorMode.STANDARD, 
                null, 
                null, 
                Text.literal("You joined as spectator. Right-click to cycle targets, sneak to free-fly.").formatted(Formatting.GRAY)
            );
            teleportToRandomSpawn(player);
        } else {
            roleManager.assignRole(player, new InnocentRole());
            player.changeGameMode(GameMode.ADVENTURE);
            teleportToRandomSpawn(player);
        }
        visibilityManager.sync(server);
    }

    @Override
    protected void onPlayerJoinGame(ServerPlayerEntity player, MinecraftServer server) {
        // Spawn teleport is handled by MurderMysterySessionBootstrap.Handler.onPlayerJoin,
        // which fires after applySettings (guaranteeing mapConfig is populated).
        // Do not attempt teleport here — mapConfig may not yet be set.
    }

    public void checkWinConditions() {
        if (this.getState() != GameState.RUNNING) return;
        List<ServerPlayerEntity> active = new ArrayList<>();
        for (ServerPlayerEntity p : context.liveParticipants()) {
            if (!roleManager.hasRole(p, SpectatorRole.class)) active.add(p);
        }
        
        MurderMysteryWinConditionManager.WinResult result = winConditionManager.checkWinCondition(active, false);
        if (result != MurderMysteryWinConditionManager.WinResult.NONE) {
            endMatch(result);
        }
    }

    private void endMatch(MurderMysteryWinConditionManager.WinResult result) {
        if (this.getState() == GameState.ENDING || this.getState() == GameState.STOPPED) return;
        this.context.setState(GameState.ENDING);
        
        Set<ServerPlayerEntity> winners = new java.util.HashSet<>();
        Text message = Text.literal("Game Over!");
        
        if (result == MurderMysteryWinConditionManager.WinResult.MURDERER_WIN) {
            message = Text.literal("The Murderer wins!").formatted(Formatting.RED);
            for (ServerPlayerEntity p : context.liveParticipants()) {
                if (roleManager.hasRole(p, MurdererRole.class)) winners.add(p);
            }
        } else if (result == MurderMysteryWinConditionManager.WinResult.INNOCENT_WIN) {
            message = Text.literal("The Innocents survive!").formatted(Formatting.GREEN);
            for (ServerPlayerEntity p : context.liveParticipants()) {
                if (roleManager.hasRole(p, InnocentRole.class) || roleManager.hasRole(p, DetectiveRole.class)) winners.add(p);
            }
        }
        
        GameMessenger.broadcast(context.liveParticipants(), message);

        MinigameRuntime runtime = MinigameManager.getInstance().getRuntime();
        if (runtime != null) {
            dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                runtime, 
                dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult.winners(winners, Text.literal("Winners")), 
                dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions.defaults(NAME)
            );
        }
    }

    private void rebuildScoreboard() {
        if (this.server == null) return;
        if (this.scoreboard == null) {
            this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(this.getName(), Text.literal("Murder Mystery").formatted(Formatting.RED, Formatting.BOLD)));
            this.scoreboard.show(this.context.liveParticipants());
        }

        this.scoreboard.clearLines();
        this.scoreboard.addBlankLine();
        this.timeLine = this.scoreboard.addLine(Text.empty());
        this.innocentsLine = this.scoreboard.addLine(Text.empty());
        this.scoreboard.resendStructure();
        this.updateScoreboardTick();
    }

    public void updateScoreboardTick() {
        if (server == null) return;
        int timeRemaining = Math.max(0, (settings.roundDurationTicks() - elapsedTicks) / 20);
        
        int innocents = 0;
        for (ServerPlayerEntity p : context.liveParticipants()) {
            if (roleManager.hasRole(p, InnocentRole.class) || roleManager.hasRole(p, DetectiveRole.class)) innocents++;
        }
        
        if (this.timeLine != null) {
            this.timeLine.setText(Text.literal("Time: " + timeRemaining));
            this.timeLine.updateAll();
        }
        if (this.innocentsLine != null) {
            this.innocentsLine.setText(Text.literal("Innocents Alive: " + innocents));
            this.innocentsLine.updateAll();
        }
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
    public JsonObject saveRuntimeState() {
        JsonObject json = new JsonObject();
        json.addProperty("elapsedTicks", elapsedTicks);
        json.addProperty("state", this.getState().name());
        return json;
    }

    @Override
    public void loadRuntimeState(JsonObject json) {
        if (json.has("elapsedTicks")) elapsedTicks = json.get("elapsedTicks").getAsInt();
        if (json.has("state") && this.context != null) this.context.setState(GameState.valueOf(json.get("state").getAsString()));
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public GameState getState() {
        return state;
    }

    @Override
    public void setState(GameState state) {
        this.state = state;
    }

    @Override
    public boolean canBuild() {
        return false;
    }

    @Override
    public boolean canBreakBlocks() {
        return false;
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand) {
        if (this.getState() != GameState.RUNNING) return ActionResult.PASS;
        if (roleManager.hasRole(player, SpectatorRole.class)) {
            if (SpectatorService.getInstance().cycleTarget(player, true)) {
                ServerPlayerEntity target = null;
                dev.frost.miniverse.minigame.core.spectator.SpectatorSession session = SpectatorService.getInstance().session(player.getUuid());
                if (session != null && session.targetId() != null) {
                    target = server.getPlayerManager().getPlayer(session.targetId());
                }
                if (target != null && target != player) {
                    player.sendMessage(Text.literal("Spectating: " + target.getName().getString() + " (Sneak to free-fly)").formatted(Formatting.AQUA), true);
                }
            } else {
                player.sendMessage(Text.literal("No players to spectate.").formatted(Formatting.YELLOW), true);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public RoleManager getRoleManager() { return roleManager; }
    public MurderMysteryWeaponManager getWeaponManager() { return weaponManager; }
    public MinigameContext getContext() { return context; }
    public CorpseManager getCorpseManager() { return corpseManager; }
    public VisibilityManager getVisibilityManager() { return visibilityManager; }
    
    @Override
    public ServerPlayerEntity getPlayerByUuid(java.util.UUID uuid) {
        return super.getPlayerByUuid(uuid);
    }
    
    public dev.frost.miniverse.map.MapPosition getNextRandomSpawn() {
        if (mapConfig == null || mapConfig.spawnPoints().isEmpty()) return null;
        List<dev.frost.miniverse.map.MapPosition> spawns = mapConfig.spawnPoints();
        return spawns.get((nextSpawnIndex++) % spawns.size());
    }

    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        net.minecraft.server.MinecraftServer server = this.context != null ? this.context.nullableServer() : null;
        if (server == null) return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
        
        boolean hasOnlineMurderer = false;
        boolean hasOnlineTarget = false;
        
        for (net.minecraft.server.network.ServerPlayerEntity player : roster.onlinePlayers(server)) {
            if (this.roleManager.hasRole(player, dev.frost.miniverse.minigame.impl.murdermystery.role.MurdererRole.class)) {
                hasOnlineMurderer = true;
            } else if (this.roleManager.hasRole(player, dev.frost.miniverse.minigame.impl.murdermystery.role.InnocentRole.class) || 
                       this.roleManager.hasRole(player, dev.frost.miniverse.minigame.impl.murdermystery.role.DetectiveRole.class)) {
                hasOnlineTarget = true;
            }
        }
        
        if (!hasOnlineMurderer) {
            return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(true, null, net.minecraft.text.Text.literal("Waiting for the Murderer to reconnect...").formatted(net.minecraft.util.Formatting.RED));
        }
        if (!hasOnlineTarget) {
            return new dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState(true, null, net.minecraft.text.Text.literal("Waiting for targets to reconnect...").formatted(net.minecraft.util.Formatting.RED));
        }
        
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }
}
