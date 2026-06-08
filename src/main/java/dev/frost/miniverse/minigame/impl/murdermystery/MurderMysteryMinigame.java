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
import dev.frost.miniverse.minigame.core.ScoreboardController;
import dev.frost.miniverse.minigame.core.corpse.CorpseManager;
import dev.frost.miniverse.minigame.core.event.ItemUseAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.event.PlayerJoinAware;
import dev.frost.miniverse.minigame.core.event.PlayerLeaveAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MurderMysteryMinigame implements Minigame, RuntimeContextAware, ServerTickAware, PauseAwareMinigame, PersistentMinigame, DynamicParticipantMinigame, PlayerDamageAware, PlayerLeaveAware, ItemUseAware, PlayerJoinAware {

    private static final String NAME = MurderMysteryDefinition.ID;
    private static final ScoreboardController SCOREBOARD = new ScoreboardController("mm_display", Text.literal("Murder Mystery"));
    
    private MinigameContext context;
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private boolean paused = false;
    
    private MurderMysterySettings settings;
    private MurderMysteryMapConfig mapConfig;
    
    private RoleManager roleManager;
    private VisibilityManager visibilityManager;
    private CorpseManager corpseManager;
    
    private VirtualEconomyManager economyManager;
    private MurderMysteryWeaponManager weaponManager;
    private MurderMysteryWinConditionManager winConditionManager;
    private CoinManager coinManager;
    private ShopManager shopManager;

    private int elapsedTicks = 0;
    private MinecraftServer server;
    private int nextSpawnIndex = 0;

    @Override
    public void attachContext(MinigameContext context) {
        this.context = context;
        this.roleManager = new RoleManager();
        this.visibilityManager = new VisibilityManager("murdermystery", roleManager);
        this.corpseManager = new CorpseManager();
        this.economyManager = new VirtualEconomyManager();
        this.weaponManager = new MurderMysteryWeaponManager();
        this.winConditionManager = new MurderMysteryWinConditionManager(roleManager);
    }
    
    public void applySettings(MurderMysterySettings settings) {
        this.settings = settings;
        this.mapConfig = MurderMysteryMapConfig.load(settings.mapId());
        this.coinManager = new CoinManager(economyManager, mapConfig.coinSpawns(), settings.coinSpawnIntervalTicks());
        this.shopManager = new ShopManager(economyManager, weaponManager, roleManager, settings);
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.paused = false;
        this.elapsedTicks = 0;
        if (server != null) {
            roleManager.clear(server);
            visibilityManager.clear(server);
        }
        corpseManager.clear();
        economyManager.clear();
        weaponManager.clear();
        if (coinManager != null) coinManager.clear();
        if (shopManager != null) shopManager.clear();
        nextSpawnIndex = 0;
    }

    @Override
    public void startGame() {
        if (this.state == GameState.IN_PROGRESS) return;
        this.state = GameState.IN_PROGRESS;
        this.context.setState(GameState.IN_PROGRESS);
        
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
        updateScoreboard();
    }

    @Override
    public void stopGame() {
        this.state = GameState.ENDING;
        this.context.setState(GameState.ENDING);
        initialize();
        if (server != null) {
            SCOREBOARD.clear(server);
        }
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        this.server = server;
        if (this.state != GameState.IN_PROGRESS || paused) return;

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
            updateScoreboard();
        }

        for (ServerPlayerEntity p : activePlayers) {
            if (p.getY() < -64) {
                onPlayerDeath(p);
                teleportToRandomSpawn(p);
            }
            int coins = economyManager.getBalance(p);
            p.sendMessage(Text.literal("Coins: ").formatted(Formatting.YELLOW).append(Text.literal(String.valueOf(coins)).formatted(Formatting.GOLD)), true);
        }
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (state != GameState.IN_PROGRESS) {
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
                    onPlayerDeath(attacker);
                }
                onPlayerDeath(player);
                return false;
            } else if (roleManager.hasRole(attacker, MurdererRole.class)) {
                player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, net.minecraft.sound.SoundCategory.PLAYERS, 0.65f, 1.0f);
                player.getServerWorld().playSound(null, player.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_ARROW_HIT_PLAYER, net.minecraft.sound.SoundCategory.PLAYERS, 0.65f, 0.7f);
                onPlayerDeath(player);
                return false;
            }
        }
        return false;
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        if (roleManager.hasRole(player, SpectatorRole.class)) return;

        player.setHealth(20.0f);
        player.clearStatusEffects();
        player.getInventory().clear();
        player.changeGameMode(GameMode.SPECTATOR);

        if (roleManager.hasRole(player, DetectiveRole.class)) {
            weaponManager.dropDetectiveWeapon(player.getServerWorld(), player.getPos());
            GameMessenger.broadcast(context.liveParticipants(), Text.literal("Detective eliminated! The Detective's Bow has dropped.").formatted(Formatting.RED));
        }

        corpseManager.spawnCorpse(player);
        roleManager.assignRole(player, new SpectatorRole());
        
        SpectatorService.getInstance().startSpectating(
            player, 
            SpectatorPolicies.unrestricted(), 
            SpectatorTargetProviders.participants(), 
            SpectatorMode.ELIMINATED, 
            null, 
            null, 
            Text.literal("You died. Right-click to cycle targets, sneak to free-fly.").formatted(Formatting.GRAY)
        );
        visibilityManager.sync(server);
        updateScoreboard();

        checkWinConditions();
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

    private void teleport(ServerPlayerEntity player, dev.frost.miniverse.map.MapPosition spawn) {
        ServerWorld world = player.getServerWorld();
        player.teleport(world, spawn.x(), spawn.y(), spawn.z(), java.util.Set.of(), spawn.yaw(), spawn.pitch());
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (roleManager.hasRole(player, DetectiveRole.class)) {
            weaponManager.dropDetectiveWeapon(player.getServerWorld(), player.getPos());
            GameMessenger.broadcast(context.liveParticipants(), Text.literal("Detective disconnected! The Detective's Bow has dropped.").formatted(Formatting.RED));
        }
        roleManager.removeRole(player);
        context.participants().remove(player);
        visibilityManager.sync(server);
        checkWinConditions();
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        context.participants().add(player);
        if (state == GameState.IN_PROGRESS || state == GameState.ENDING) {
            roleManager.assignRole(player, new SpectatorRole());
            SpectatorService.getInstance().startSpectating(
                player, 
                SpectatorPolicies.unrestricted(), 
                SpectatorTargetProviders.participants(), 
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
    public void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        if (state == GameState.WAITING_FOR_PLAYERS || state == GameState.FROZEN || state == GameState.STARTING) {
            teleportToRandomSpawn(player);
        }
    }

    private void checkWinConditions() {
        if (state != GameState.IN_PROGRESS) return;
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
        if (state == GameState.ENDING || state == GameState.FINISHED) return;
        state = GameState.ENDING;
        context.setState(GameState.ENDING);
        
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
            dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleController.getInstance().endMatch(
                runtime, 
                dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult.winners(winners, Text.literal("Winners")), 
                dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions.defaults(NAME)
            );
        }
    }

    private void updateScoreboard() {
        if (server == null) return;
        int timeRemaining = Math.max(0, (settings.roundDurationTicks() - elapsedTicks) / 20);
        
        int innocents = 0;
        for (ServerPlayerEntity p : context.liveParticipants()) {
            if (roleManager.hasRole(p, InnocentRole.class) || roleManager.hasRole(p, DetectiveRole.class)) innocents++;
        }
        
        SCOREBOARD.setScore(server, "Time", timeRemaining);
        SCOREBOARD.setScore(server, "Innocents Alive", innocents);
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
        json.addProperty("state", state.name());
        return json;
    }

    @Override
    public void loadRuntimeState(JsonObject json) {
        if (json.has("elapsedTicks")) elapsedTicks = json.get("elapsedTicks").getAsInt();
        if (json.has("state")) state = GameState.valueOf(json.get("state").getAsString());
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
        if (state != GameState.IN_PROGRESS) return ActionResult.PASS;
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
}
