package dev.frost.miniverse.minigame.impl.pillarsoffortune;

import com.google.gson.JsonObject;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.loot.LootDropModule;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.loot.LootTable;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.modifier.GameModifier;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.modifier.ShuffleModifier;
import dev.frost.miniverse.minigame.impl.pillarsoffortune.modifier.SwapperModifier;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PillarsOfFortuneMinigame extends AbstractMinigame implements DeathAwareMinigame, SpawnPointAware, ServerTickAware, PersistentMinigame, dev.frost.miniverse.minigame.core.event.BlockBreakBypassAware {
    private PillarsOfFortuneSettings settings = PillarsOfFortuneSettings.defaults();
    private PillarsOfFortuneMapConfig mapConfig = new PillarsOfFortuneMapConfig(List.of());
    private DeathLifecycleManager deathLifecycleManager;
    private ScoreboardTemplate scoreboard;
    private ScoreboardLine playersAliveLine;
    private ScoreboardLine nextDropLine;
    
    private final Set<ServerPlayerEntity> aliveParticipants = ConcurrentHashMap.newKeySet();
    private LootDropModule lootDropModule;
    private GameModifier activeModifierModule;

    private int gameTicks;
    private int nextSpawnIndex = 0;
    private GameState state = GameState.WAITING_FOR_PLAYERS;

    public void applySettings(PillarsOfFortuneSettings settings, String preSerializedMapConfig) {
        this.settings = settings == null ? PillarsOfFortuneSettings.defaults() : settings;
        this.mapConfig = PillarsOfFortuneMapConfig.load(PillarsOfFortuneDefinition.ID, preSerializedMapConfig);
    }

    public boolean canStartMatch() {
        return this.context.roster().size() >= 2;
    }

    @Override
    public String getName() {
        return PillarsOfFortuneDefinition.DISPLAY_NAME;
    }

    public dev.frost.miniverse.minigame.core.MinigameContext getContext() {
        return this.context;
    }

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(net.minecraft.world.GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_IMMEDIATE_RESPAWN, true);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.FALL_DAMAGE, true);
        this.applyVanillaGameRule(net.minecraft.world.GameRules.DO_DAYLIGHT_CYCLE, false);
        
        this.deathLifecycleManager = new DeathLifecycleManager(new PillarsOfFortuneDeathConfig(this), SpectatorService.getInstance());
        this.nextSpawnIndex = 0;
    }

    @Override
    protected void onMatchStart() {
        this.aliveParticipants.clear();
        this.aliveParticipants.addAll(this.context.liveParticipants());
        
        List<MapPosition> spawns = new ArrayList<>(this.mapConfig.spawnPoints());
        Collections.shuffle(spawns);

        int i = 0;
        for (ServerPlayerEntity player : this.aliveParticipants) {
            player.getInventory().clear();
            player.changeGameMode(GameMode.SURVIVAL);
            
            if (i < spawns.size()) {
                MapPosition spawn = spawns.get(i++);
                player.teleport(player.getServerWorld(), spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
            }
        }

        this.lootDropModule = this.getOrRegisterModule(LootDropModule.class, () -> new LootDropModule(this, LootTable.createDefault(), this.settings.lootDropIntervalSeconds()));

        if (this.settings.activeModifier().equalsIgnoreCase(SwapperModifier.ID)) {
            this.activeModifierModule = this.getOrRegisterModule(SwapperModifier.class, SwapperModifier::new);
        } else if (this.settings.activeModifier().equalsIgnoreCase(ShuffleModifier.ID)) {
            this.activeModifierModule = this.getOrRegisterModule(ShuffleModifier.class, ShuffleModifier::new);
        }

        this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () -> new ScoreboardTemplate(PillarsOfFortuneDefinition.ID + "_obj", Text.literal("Pillars of Fortune").formatted(Formatting.AQUA, Formatting.BOLD)));
        this.playersAliveLine = this.scoreboard.addLine(Text.literal(""));
        this.nextDropLine = this.scoreboard.addLine(Text.literal(""));
        
        this.updateScoreboard();
        this.scoreboard.show(this.context.liveParticipants());
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        if (this.getState() != GameState.RUNNING) {
            return;
        }

        this.gameTicks++;
        if (this.gameTicks >= this.settings.timeLimitSeconds() * 20) {
            if (this.context.nullableServer() != null && MinigameManager.getInstance().getRuntime() != null) {
                dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                    MinigameManager.getInstance().getRuntime(),
                    MatchEndResult.winners(Set.of(), Text.literal("Time Limit Reached!")),
                    dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions.defaults(getName())
                );
            }
            return;
        }

        if (this.lootDropModule != null) {
            this.lootDropModule.tick();
        }
        if (this.activeModifierModule != null) {
            this.activeModifierModule.tick(this);
        }

        if (this.gameTicks % 20 == 0) {
            this.updateScoreboard();
        }
    }

    private void updateScoreboard() {
        if (this.playersAliveLine != null) {
            this.playersAliveLine.setText(Text.literal("Alive: " + this.aliveParticipants.size()).formatted(Formatting.WHITE));
            this.playersAliveLine.updateAll();
        }
        if (this.lootDropModule != null && this.nextDropLine != null) {
            this.nextDropLine.setText(Text.literal("Next Drop: " + this.lootDropModule.getSecondsUntilNextDrop() + "s").formatted(Formatting.YELLOW));
            this.nextDropLine.updateAll();
        }
    }

    public List<ServerPlayerEntity> getAliveParticipants() {
        return new ArrayList<>(this.aliveParticipants);
    }

    public void onPlayerEliminated(ServerPlayerEntity player) {
        this.aliveParticipants.remove(player);
        this.updateScoreboard();
        this.checkWinCondition();
    }

    public void checkWinCondition() {
        if (this.getState() != GameState.RUNNING) return;
        
        if (this.aliveParticipants.size() == 1) {
            ServerPlayerEntity winner = this.aliveParticipants.iterator().next();
            if (MinigameManager.getInstance().getRuntime() != null) {
                dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                    MinigameManager.getInstance().getRuntime(),
                    MatchEndResult.winner(winner),
                    dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions.defaults(getName())
                );
            }
        } else if (this.aliveParticipants.isEmpty()) {
            if (MinigameManager.getInstance().getRuntime() != null) {
                dev.frost.miniverse.minigame.core.MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                    MinigameManager.getInstance().getRuntime(),
                    MatchEndResult.winners(Set.of(), Text.literal("Everyone died!")),
                    dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions.defaults(getName())
                );
            }
        }
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        return true; 
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity instanceof ServerPlayerEntity player && this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleFatalDamage(player, source);
        }
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
    }

    @Override
    protected void onMatchEnd() {
        if (this.scoreboard != null && this.context.nullableServer() != null) {
            this.scoreboard.cleanup(this.context.nullableServer());
        }
        if (this.deathLifecycleManager != null) {
            this.deathLifecycleManager.handleMatchEnding(uuid -> this.getPlayerByUuid(uuid));
        }
    }

    public void broadcast(Text message) {
        GameMessenger.broadcast(this.getAliveParticipants(), message);
    }
    
    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        if (this.mapConfig == null || this.mapConfig.spawnPoints().isEmpty()) return;
        List<MapPosition> spawns = this.mapConfig.spawnPoints();
        MapPosition spawn = spawns.get((this.nextSpawnIndex++) % spawns.size());
        player.teleport(player.getServerWorld(), spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
    }

    @Override
    protected boolean isTeamBased() {
        return false;
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    @Override
    public void setState(GameState state) {
        this.state = state;
    }

    @Override
    public boolean canBuild() {
        return true;
    }

    @Override
    public boolean canBreakBlocks() {
        return true;
    }

    @Override
    public boolean canBypassProtection(ServerPlayerEntity player, net.minecraft.util.math.BlockPos pos) {
        return true;
    }
    
    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject state = new JsonObject();
        state.addProperty("gameTicks", this.gameTicks);
        if (this.lootDropModule != null) {
            state.addProperty("lootDropSeconds", this.lootDropModule.getSecondsUntilNextDrop());
        }
        if (this.activeModifierModule != null) {
            JsonObject modState = new JsonObject();
            this.activeModifierModule.saveState(modState);
            state.add("modifierState", modState);
        }
        return state;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        if (state.has("gameTicks")) {
            this.gameTicks = state.get("gameTicks").getAsInt();
        }
        if (state.has("lootDropSeconds") && this.lootDropModule != null) {
            this.lootDropModule.setSecondsUntilNextDrop(state.get("lootDropSeconds").getAsInt());
        }
        if (state.has("modifierState") && this.activeModifierModule != null) {
            this.activeModifierModule.loadState(state.getAsJsonObject("modifierState"));
        }
    }
}
