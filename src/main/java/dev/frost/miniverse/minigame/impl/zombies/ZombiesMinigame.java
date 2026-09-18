package dev.frost.miniverse.minigame.impl.zombies;

import dev.frost.miniverse.common.NetworkConstants;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.freeze.DownedPlayerTracker;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.event.BlockAttackAware;
import dev.frost.miniverse.minigame.core.event.ItemUseAware;
import dev.frost.miniverse.minigame.core.event.ItemUseOnBlockAware;
import dev.frost.miniverse.minigame.core.event.PlayerDamageAware;
import dev.frost.miniverse.minigame.core.SessionRoster;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator;
import dev.frost.miniverse.minigame.core.protection.BlockProtectionProvider;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.item.ProtectedItemRule;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTypes;
import dev.frost.miniverse.minigame.impl.zombies.item.ZombiesHotbarManager;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesArmorShop;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesDoor;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesMapConfig;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesPerkMachine;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesPowerSwitch;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesWeaponShop;
import dev.frost.miniverse.minigame.impl.zombies.map.ZombiesWindow;
import dev.frost.miniverse.minigame.impl.zombies.mob.ZombieEntityManager;
import dev.frost.miniverse.minigame.impl.zombies.perk.GlobalPerk;
import dev.frost.miniverse.minigame.impl.zombies.perk.PerkDropManager;
import dev.frost.miniverse.minigame.impl.zombies.perk.PlayerPerk;
import dev.frost.miniverse.minigame.impl.zombies.revive.ZombiesReviveManager;
import dev.frost.miniverse.minigame.impl.zombies.station.ZombiesHologramManager;
import dev.frost.miniverse.minigame.impl.zombies.station.ZombiesLuckyChestManager;
import dev.frost.miniverse.minigame.impl.zombies.station.ZombiesTeamMachineManager;
import dev.frost.miniverse.minigame.impl.zombies.station.ZombiesUltimateMachine;
import dev.frost.miniverse.minigame.impl.zombies.wave.ZombieWaveEngine;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponData;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponGunManager;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponItemHelper;
import dev.frost.miniverse.minigame.impl.zombies.weapon.WeaponType;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZombiesMinigame extends AbstractMinigame implements
    TeamManagerProvider,
    ServerTickAware,
    ItemUseAware,
    ItemUseOnBlockAware,
    BlockAttackAware,
    PlayerDamageAware,
    BlockProtectionProvider {

    public static final String TEAM_SURVIVORS = "survivors";

    private final TeamManager teamManager = new TeamManager();
    private ZombiesSettings settings = ZombiesSettings.defaults();

    private ZombiesMapConfig mapConfig;
    private ZombieEntityManager mobManager;
    private ZombieWaveEngine waveEngine;
    private WeaponGunManager gunManager;
    private PerkDropManager dropManager;
    private ZombiesReviveManager reviveManager;
    private ZombiesLuckyChestManager luckyChestManager;
    private ZombiesTeamMachineManager teamMachineManager;
    private ZombiesHologramManager hologramManager;
    private ZombiesUltimateMachine ultimateMachine;

    private final Map<UUID, Integer> goldMap = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerRevives = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerGoldSpent = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<PlayerPerk>> playerPerks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastShopInteractTimes = new ConcurrentHashMap<>();
    private final Set<String> reachableAreas = ConcurrentHashMap.newKeySet();
    private boolean powerActive = false;

    private final Map<UUID, ScoreboardTemplate> scoreboards = new ConcurrentHashMap<>();
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private int tickCounter = 0;

    private enum ActionBarPrompt {
        NONE,
        WINDOW_HOLD_SNEAK,
        WINDOW_REPAIRING,
        WINDOW_REPAIR_COMPLETE,
        WINDOW_UNDER_ATTACK,
        DOOR_PROMPT
    }
    private final Map<UUID, ActionBarPrompt> activeActionBarPrompts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> repairCompleteTicks = new ConcurrentHashMap<>();

    public ZombiesMinigame() {
    }

    public void applySettings(ZombiesSettings settings) {
        this.applySettings(settings, null);
    }

    public void applySettings(ZombiesSettings settings, ZombiesMapConfig mapConfig) {
        this.settings = settings != null ? settings : ZombiesSettings.defaults();
        this.mapConfig = mapConfig != null ? mapConfig : ZombiesMapConfig.loadDefaultTemplate();
    }

    public ZombiesSettings getSettings() {
        return this.settings;
    }

    @Override
    public void initialize() {
        this.state = GameState.WAITING_FOR_PLAYERS;
        this.goldMap.clear();
        this.playerKills.clear();
        this.playerRevives.clear();
        this.playerGoldSpent.clear();
        this.lastDamageTimes.clear();
        this.playerPerks.clear();
        this.scoreboards.clear();
        this.activeActionBarPrompts.clear();
        this.repairCompleteTicks.clear();
        this.teamManager.clear();
        this.tickCounter = 0;
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
    public MatchProgressionValidator.ProgressionState checkProgression(SessionRoster roster) {
        if (roster == null || roster.onlinePlayers(this.context != null ? this.context.nullableServer() : null).isEmpty()) {
            return new MatchProgressionValidator.ProgressionState(true, null, Text.literal("Waiting for survivors to reconnect...").formatted(Formatting.RED));
        }
        return MatchProgressionValidator.ProgressionState.valid();
    }

    @Override
    public String getName() {
        return ZombiesDefinition.DISPLAY_NAME;
    }

    public String getDescription() {
        return "Survive 30 waves of zombies.";
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
    public TeamManager teamManager() {
        return this.teamManager;
    }

    public List<ServerPlayerEntity> getParticipants() {
        if (this.context == null) return List.of();
        return this.context.liveParticipants();
    }

    public void broadcast(Text text) {
        for (ServerPlayerEntity p : getParticipants()) {
            p.sendMessage(text, false);
        }
    }

    public int getGold(ServerPlayerEntity player) {
        return this.goldMap.getOrDefault(player.getUuid(), 0);
    }

    public void addGold(ServerPlayerEntity player, int amount) {
        this.goldMap.merge(player.getUuid(), amount, Integer::sum);
        player.sendMessage(Text.literal("+" + amount + " Gold").formatted(Formatting.GOLD), false);
    }

    public void devSkipRound() {
        if (this.state == GameState.RUNNING && this.waveEngine != null) {
            this.waveEngine.devSkipRound(getParticipants());
        }
    }

    public void triggerNuke(ServerPlayerEntity player) {
        if (this.state == GameState.RUNNING && this.dropManager != null) {
            this.dropManager.triggerPerk(player, GlobalPerk.NUKE, getParticipants());
        }
    }

    public static ItemStack createNukeItem() {
        ItemStack stack = new ItemStack(Items.TNT);
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal("Tactical Nuke").formatted(Formatting.RED, Formatting.BOLD)
                .append(Text.literal(" (Right Click)").formatted(Formatting.YELLOW)));
        return stack;
    }

    public void recordKill(ServerPlayerEntity player) {
        this.playerKills.merge(player.getUuid(), 1, Integer::sum);
    }

    public int getKills(ServerPlayerEntity player) {
        return this.playerKills.getOrDefault(player.getUuid(), 0);
    }

    public boolean spendGold(ServerPlayerEntity player, int amount) {
        int current = getGold(player);
        if (current >= amount) {
            this.goldMap.put(player.getUuid(), current - amount);
            this.playerGoldSpent.merge(player.getUuid(), amount, Integer::sum);
            return true;
        }
        return false;
    }

    public boolean hasPerk(ServerPlayerEntity player, PlayerPerk perk) {
        Set<PlayerPerk> perks = this.playerPerks.get(player.getUuid());
        return perks != null && perks.contains(perk);
    }

    @Override
    public boolean isBlockProtected(ServerWorld world, BlockPos pos) {
        return true;
    }

    @Override
    protected void onMatchStart() {
        List<ServerPlayerEntity> participants = getParticipants();
        if (participants.isEmpty()) return;

        this.state = GameState.RUNNING;
        if (this.context != null) {
            this.context.setState(GameState.RUNNING);
        }

        ServerWorld world = participants.get(0).getServerWorld();

        // Game rules
        this.applyVanillaGameRule(GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(GameRules.DO_IMMEDIATE_RESPAWN, true);
        this.applyVanillaGameRule(GameRules.DO_MOB_SPAWNING, false);
        this.applyVanillaGameRule(GameRules.DO_DAYLIGHT_CYCLE, false);
        this.applyVanillaGameRule(GameRules.DO_WEATHER_CYCLE, false);
        this.applyVanillaGameRule(GameRules.DO_MOB_LOOT, false);
        this.applyVanillaGameRule(GameRules.ANNOUNCE_ADVANCEMENTS, false);
        this.applyVanillaGameRule(GameRules.NATURAL_REGENERATION, false);
        world.setTimeOfDay(18000); // Midnight atmosphere

        // Load map config: fallback to bundled dead_end.json if not provided
        if (this.mapConfig == null) {
            this.mapConfig = ZombiesMapConfig.loadDefaultTemplate();
        }

        // Initialize Managers
        this.mobManager = new ZombieEntityManager(world, this.settings.difficulty());
        this.mobManager.setWindows(this.mapConfig.windows());
        this.reviveManager = new ZombiesReviveManager(world, (p, g) -> {
            this.addGold(p, g);
            this.playerRevives.merge(p.getUuid(), 1, Integer::sum);
        }, this::hasPerk, this::broadcast);
        this.reviveManager.setBleedoutSeconds(this.settings.bleedoutSeconds());
        this.dropManager = new PerkDropManager(world, this.mapConfig, this.mobManager, this::addGold);
        this.gunManager = new WeaponGunManager(
            world,
            this.mobManager,
            this::addGold,
            this.dropManager::isInstantKillActive,
            this.dropManager::isDoubleGoldActive,
            this::hasPerk
        );
        this.gunManager.setKillTracker(this::recordKill);

        // Ultimate Machine & Holograms
        this.ultimateMachine = this.mapConfig.ultimateMachine() != null
            ? this.mapConfig.ultimateMachine()
            : new ZombiesUltimateMachine(this.mapConfig.getEffectiveUltimateMachinePos());
        this.hologramManager = new ZombiesHologramManager(world);
        this.hologramManager.spawnAll(this.mapConfig, this.ultimateMachine.getPos());

        // Protected item rules
        ProtectedItemService pis = ProtectedItemService.getInstance();
        for (String type : List.of(
            ProtectedItemTypes.ZOMBIES_KNIFE,
            ProtectedItemTypes.ZOMBIES_WEAPON,
            ProtectedItemTypes.ZOMBIES_ARMOR,
            ProtectedItemTypes.ZOMBIES_PLACEHOLDER,
            ProtectedItemTypes.ZOMBIES_PERK
        )) {
            pis.registerRule(ProtectedItemRule.builder(type)
                .preventDrop()
                .preventExternalStorage()
                .preventDeletion()
                .allowRearrange(false)
                .allowOffhandSwap(false)
                .build());
        }

        this.reviveManager.setOnReviveOrRespawnCallback(this::clearPlayerPerks);
        this.luckyChestManager = new ZombiesLuckyChestManager(world, this.mapConfig.luckyChests(), this::spendGold, this::broadcast, this.hologramManager, this::hasPerk);
        this.luckyChestManager.initHologram();
        this.teamMachineManager = new ZombiesTeamMachineManager(world, this.mobManager, this.reviveManager, this::getParticipants, this::spendGold, this::broadcast);

        this.waveEngine = new ZombieWaveEngine(world, this.mapConfig, this.mobManager, this.settings);
        this.mobManager.setStuckMobHandler(active -> this.waveEngine.handleStuckMob(active, this.reachableAreas));
        this.waveEngine.setOnRoundClearListener(() -> {
            // Respawn downed/spectating players
            BlockPos p0 = !this.mapConfig.playerSpawns().isEmpty() ? this.mapConfig.playerSpawns().get(0) : new BlockPos(0, 70, 0);
            MapPosition pSpawn = MapPosition.of(p0.getX() + 0.5, p0.getY(), p0.getZ() + 0.5);
            this.reviveManager.respawnAllAtRoundEnd(getParticipants(), pSpawn);
            // Award round clear gold
            int bonus = 100 + (this.waveEngine.getCurrentRound() * 25);
            for (ServerPlayerEntity p : getParticipants()) {
                addGold(p, bonus);
                p.sendMessage(Text.literal("✔ +" + bonus + "g Round Bonus!").formatted(Formatting.YELLOW), true);
            }
        });
        this.waveEngine.setOnVictoryListener(this::winMatch);

        this.mobManager.setOnMobKilledCallback(mob -> {
            // Little Bombie should not spawn power-ups (explodes immediately causing accidental pickups)
            if (mob.type != dev.frost.miniverse.minigame.impl.zombies.mob.ZombieType.LITTLE_BOMBIE) {
                this.dropManager.trySpawnDrop(mob.entity.getPos());
            }
        });

        // Reset Doors & Windows
        this.reachableAreas.clear();
        this.reachableAreas.add(this.mapConfig.startArea());
        for (ZombiesDoor door : this.mapConfig.doors()) {
            door.closeDoor(world);
        }
        for (ZombiesWindow window : this.mapConfig.windows()) {
            window.reset(world);
        }
        this.powerActive = false;

        // Initialize players
        BlockPos pStart = !this.mapConfig.playerSpawns().isEmpty() ? this.mapConfig.playerSpawns().get(0) : new BlockPos(0, 70, 0);
        MapPosition spawnPos = MapPosition.of(pStart.getX() + 0.5, pStart.getY(), pStart.getZ() + 0.5);

        DownedPlayerTracker.clear();
        for (ServerPlayerEntity p : participants) {
            DownedPlayerTracker.setDowned(p.getUuid(), false);
            p.setPose(EntityPose.STANDING);
            ServerPlayNetworking.send(p, new NetworkConstants.DownedStatePayload(p.getUuid(), false));
            this.teamManager.assign(p, TEAM_SURVIVORS, "Survivors");
            this.goldMap.put(p.getUuid(), this.settings.startGold());
            this.playerPerks.put(p.getUuid(), ConcurrentHashMap.newKeySet());

            p.changeGameMode(GameMode.ADVENTURE);
            p.teleport(world, spawnPos.x(), spawnPos.y(), spawnPos.z(), spawnPos.yaw(), spawnPos.pitch());

            // Starter hotbar: Knife, Pistol, Placeholders
            ZombiesHotbarManager.setupInitialHotbar(p);

            // Survivor clothes
            ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
            dev.frost.miniverse.minigame.core.item.ProtectedItemTags.mark(chest, ProtectedItemTypes.ZOMBIES_ARMOR);
            p.equipStack(EquipmentSlot.CHEST, chest);

            ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
            dev.frost.miniverse.minigame.core.item.ProtectedItemTags.mark(boots, ProtectedItemTypes.ZOMBIES_ARMOR);
            p.equipStack(EquipmentSlot.FEET, boots);

            p.setHealth(20.0f);
            p.getHungerManager().setFoodLevel(20);
        }
        this.syncVanillaTeams();
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        if (this.state != GameState.RUNNING) return;

        this.tickCounter++;
        List<ServerPlayerEntity> survivors = getParticipants();
        if (survivors.isEmpty()) return;

        ServerWorld world = survivors.get(0).getServerWorld();

        // 1. Tick Mobs
        this.mobManager.tick(survivors, this.tickCounter);

        // 2. Tick Wave Engine
        this.waveEngine.tick(survivors, this.reachableAreas);

        // 3. Tick Weapons & Reloads
        this.gunManager.tickReloads();
        this.gunManager.tickHUD(survivors);

        // 4. Tick Perk Drops
        this.dropManager.tick(survivors);

        // 5. Tick Downed & Revive
        this.reviveManager.tick(survivors);

        // 6. Tick Mystery Box
        this.luckyChestManager.tick();

        // 7. Window Sneak Barricade Repair (every 20 ticks - 1.0s)
        if (this.tickCounter % 20 == 0) {
            for (ServerPlayerEntity p : survivors) {
                if (p.isSneaking() && !this.reviveManager.isDowned(p.getUuid()) && !p.isSpectator()) {
                    for (ZombiesWindow window : this.mapConfig.windows()) {
                        if (window.isNearWindow(p.getX(), p.getY(), p.getZ(), 6.25) && !window.isFullyRepaired(world)) {
                            if (window.isUnderAttack()) {
                                p.sendMessage(Text.literal("Cannot repair while barricade is under attack!").formatted(Formatting.RED), true);
                                continue;
                            }
                            if (window.repairOneSlab(world)) {
                                int goldGain = this.dropManager.isDoubleGoldActive() ? 20 : 10;
                                addGold(p, goldGain);
                                if (window.isFullyRepaired(world)) {
                                    p.sendMessage(Text.literal("Repair complete!").formatted(Formatting.GREEN), true);
                                    this.activeActionBarPrompts.put(p.getUuid(), ActionBarPrompt.WINDOW_REPAIR_COMPLETE);
                                    this.repairCompleteTicks.put(p.getUuid(), 25);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 8. Combat Health Regeneration (every 20 ticks - 1.0s)
        if (this.tickCounter % 20 == 0) {
            long nowMs = System.currentTimeMillis();
            for (ServerPlayerEntity p : survivors) {
                if (p.isAlive() && !p.isSpectator() && !this.reviveManager.isDowned(p.getUuid())) {
                    if (p.getHungerManager().getFoodLevel() < 20) {
                        p.getHungerManager().setFoodLevel(20);
                    }
                    Long lastDmg = this.lastDamageTimes.get(p.getUuid());
                    if (lastDmg == null || (nowMs - lastDmg) >= 5000L) {
                        float maxHp = p.getMaxHealth();
                        float currentHp = p.getHealth();
                        if (currentHp < maxHp) {
                            p.setHealth(Math.min(maxHp, currentHp + 2.0f));
                        }
                    }
                }
            }
        }

        // 9. Proximity Action Bar Prompts & Window Repair Status (every 2 ticks)
        if (this.tickCounter % 2 == 0) {
            for (ServerPlayerEntity p : survivors) {
                if (!p.isAlive() || p.isSpectator() || this.reviveManager.isDowned(p.getUuid())) {
                    if (this.activeActionBarPrompts.getOrDefault(p.getUuid(), ActionBarPrompt.NONE) != ActionBarPrompt.NONE) {
                        p.sendMessage(Text.empty(), true);
                        this.activeActionBarPrompts.put(p.getUuid(), ActionBarPrompt.NONE);
                        this.repairCompleteTicks.remove(p.getUuid());
                    }
                    continue;
                }
                if (this.gunManager.isReloading(p.getUuid())) continue;

                // Handle active repair complete countdown
                int completeTimer = this.repairCompleteTicks.getOrDefault(p.getUuid(), 0);
                if (completeTimer > 0) {
                    boolean nearAnyWindow = false;
                    for (ZombiesWindow w : this.mapConfig.windows()) {
                        if (w.isNearWindow(p.getX(), p.getY(), p.getZ(), 6.25)) {
                            nearAnyWindow = true;
                            break;
                        }
                    }
                    if (!nearAnyWindow) {
                        p.sendMessage(Text.empty(), true);
                        this.activeActionBarPrompts.put(p.getUuid(), ActionBarPrompt.NONE);
                        this.repairCompleteTicks.remove(p.getUuid());
                    } else {
                        int nextTimer = completeTimer - 2;
                        if (nextTimer <= 0) {
                            p.sendMessage(Text.empty(), true);
                            this.activeActionBarPrompts.put(p.getUuid(), ActionBarPrompt.NONE);
                            this.repairCompleteTicks.remove(p.getUuid());
                        } else {
                            this.repairCompleteTicks.put(p.getUuid(), nextTimer);
                        }
                    }
                    continue;
                }

                ActionBarPrompt targetPrompt = ActionBarPrompt.NONE;
                Text promptMessage = null;

                // Check window proximity
                for (ZombiesWindow window : this.mapConfig.windows()) {
                    if (window.isNearWindow(p.getX(), p.getY(), p.getZ(), 6.25) && !window.isFullyRepaired(world)) {
                        if (window.isUnderAttack()) {
                            targetPrompt = ActionBarPrompt.WINDOW_UNDER_ATTACK;
                            if (p.isSneaking()) {
                                promptMessage = Text.literal("Repairing cancelled due to zombies!").formatted(Formatting.RED, Formatting.BOLD);
                            } else {
                                promptMessage = Text.literal("Cannot repair - barricade is under attack!").formatted(Formatting.RED);
                            }
                        } else if (p.isSneaking()) {
                            targetPrompt = ActionBarPrompt.WINDOW_REPAIRING;
                            promptMessage = Text.literal("Repairing window...").formatted(Formatting.YELLOW);
                        } else {
                            targetPrompt = ActionBarPrompt.WINDOW_HOLD_SNEAK;
                            promptMessage = Text.literal("Hold SNEAK to repair").formatted(Formatting.YELLOW);
                        }
                        break;
                    }
                }

                // Check door proximity if not near window
                if (targetPrompt == ActionBarPrompt.NONE) {
                    for (ZombiesDoor door : this.mapConfig.doors()) {
                        if (!door.isOpen() && door.isNearDoor(p.getX(), p.getY(), p.getZ(), 3.5)) {
                            targetPrompt = ActionBarPrompt.DOOR_PROMPT;
                            promptMessage = Text.literal("Right-click with empty hand to open door").formatted(Formatting.YELLOW);
                            break;
                        }
                    }
                }

                ActionBarPrompt current = this.activeActionBarPrompts.getOrDefault(p.getUuid(), ActionBarPrompt.NONE);
                if (targetPrompt == ActionBarPrompt.NONE) {
                    if (current != ActionBarPrompt.NONE) {
                        p.sendMessage(Text.empty(), true);
                        this.activeActionBarPrompts.put(p.getUuid(), ActionBarPrompt.NONE);
                    }
                } else {
                    if (current != targetPrompt || this.tickCounter % 20 == 0) {
                        p.sendMessage(promptMessage, true);
                        this.activeActionBarPrompts.put(p.getUuid(), targetPrompt);
                    }
                }
            }
        }

        // 10. Update Scoreboards (every 20 ticks)
        if (this.tickCounter % 20 == 0) {
            updateScoreboards();
        }

        // 11. Loss condition: all survivors downed or spectating
        if (this.reviveManager.isAllDeadOrDowned(survivors)) {
            loseMatch("All survivors were defeated!");
        }
    }

    public void handleReloadKey(ServerPlayerEntity player) {
        if (this.state != GameState.RUNNING || player.isSpectator() || !player.isAlive()) return;
        if (this.reviveManager.isDowned(player.getUuid())) return;

        ItemStack stack = player.getMainHandStack();
        WeaponType type = WeaponItemHelper.getWeaponType(stack);
        if (type != null && !type.getData().isMelee()) {
            this.gunManager.triggerReload(player, stack, type);
        }
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand) {
        if (this.state != GameState.RUNNING || hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (this.reviveManager.isDowned(player.getUuid())) return ActionResult.FAIL;

        int selectedSlot = player.getInventory().selectedSlot;
        ItemStack stack = player.getMainHandStack();
        if (ZombiesHotbarManager.isPerkSlot(selectedSlot)
            || ZombiesHotbarManager.isPerkItem(stack)
            || ZombiesHotbarManager.isPlaceholder(stack)) {
            return ActionResult.FAIL;
        }

        if (!stack.isEmpty() && stack.isOf(Items.TNT)) {
            Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
            if (name != null && name.getString().contains("Nuke")) {
                stack.decrement(1);
                triggerNuke(player);
                return ActionResult.SUCCESS;
            }
        }

        if (this.gunManager.handleWeaponFire(player, stack)) {
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseBlock(ServerPlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (this.state != GameState.RUNNING || hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (this.reviveManager.isDowned(player.getUuid())) return ActionResult.FAIL;

        ItemStack held = player.getMainHandStack();
        if (!held.isEmpty() && held.isOf(Items.TNT)) {
            Text name = held.get(DataComponentTypes.CUSTOM_NAME);
            if (name != null && name.getString().contains("Nuke")) {
                held.decrement(1);
                triggerNuke(player);
                return ActionResult.SUCCESS;
            }
        }

        BlockPos pos = hitResult.getBlockPos();

        // Shop & Station Interaction Debounce (500ms)
        long now = System.currentTimeMillis();
        Long lastShopInteract = this.lastShopInteractTimes.get(player.getUuid());
        boolean isShopInteract = (this.mapConfig.teamMachine() != null && pos.equals(this.mapConfig.teamMachine().getPos()))
            || (this.mapConfig.powerSwitch() != null && pos.equals(this.mapConfig.powerSwitch().getPos()))
            || (this.ultimateMachine != null && (pos.equals(this.ultimateMachine.getPos()) || pos.isWithinDistance(this.ultimateMachine.getPos(), 2.0)))
            || this.mapConfig.weaponShops().stream().anyMatch(ws -> pos.equals(ws.getPos()))
            || this.mapConfig.armorShops().stream().anyMatch(as -> pos.equals(as.getPos()))
            || this.mapConfig.perkMachines().stream().anyMatch(pm -> pos.equals(pm.getPos()));

        if (isShopInteract) {
            if (lastShopInteract != null && (now - lastShopInteract) < 500L) {
                return ActionResult.SUCCESS;
            }
            this.lastShopInteractTimes.put(player.getUuid(), now);
        }

        // 1. Lucky Chest / Chest blocks (cancel container opening)
        if (this.luckyChestManager.handleInteract(player, pos)) {
            return ActionResult.SUCCESS;
        }
        if (world.getBlockState(pos).getBlock() instanceof net.minecraft.block.ChestBlock) {
            player.sendMessage(Text.literal("This chest is empty.").formatted(Formatting.GRAY), true);
            player.playSound(SoundEvents.BLOCK_CHEST_LOCKED, 0.8f, 1.0f);
            return ActionResult.SUCCESS;
        }

        // 2. Team Machine
        if (this.mapConfig.teamMachine() != null && pos.equals(this.mapConfig.teamMachine().getPos())) {
            this.teamMachineManager.openTeamMachine(player, getGold(player));
            return ActionResult.SUCCESS;
        }

        // 3. Power Switch
        if (this.mapConfig.powerSwitch() != null && pos.equals(this.mapConfig.powerSwitch().getPos())) {
            handlePowerSwitch(player);
            return ActionResult.SUCCESS;
        }

        // 4. Ultimate Machine
        if (this.ultimateMachine != null) {
            if (this.ultimateMachine.handleInteract(player, pos, this.powerActive, this::spendGold, this::broadcast)) {
                return ActionResult.SUCCESS;
            }
        }

        // 4. Weapon Shops
        for (ZombiesWeaponShop ws : this.mapConfig.weaponShops()) {
            if (pos.equals(ws.getPos())) {
                handleWeaponShop(player, ws);
                return ActionResult.SUCCESS;
            }
        }

        // 5. Armor Shops
        for (ZombiesArmorShop as : this.mapConfig.armorShops()) {
            if (pos.equals(as.getPos())) {
                handleArmorShop(player, as);
                return ActionResult.SUCCESS;
            }
        }

        // 6. Perk Machines
        for (ZombiesPerkMachine pm : this.mapConfig.perkMachines()) {
            if (pos.equals(pm.getPos())) {
                handlePerkMachine(player, pm);
                return ActionResult.SUCCESS;
            }
        }

        // 7. Doors (only unlockable with empty hand)
        if (player.getMainHandStack().isEmpty()) {
            for (ZombiesDoor door : this.mapConfig.doors()) {
                if (!door.isOpen() && door.contains(pos)) {
                    handleDoor(player, door);
                    return ActionResult.SUCCESS;
                }
            }
        }

        // Holding weapon: fire gun
        ItemStack stack = player.getMainHandStack();
        if (WeaponItemHelper.getWeaponType(stack) != null) {
            if (this.gunManager.handleWeaponFire(player, stack)) {
                return ActionResult.SUCCESS;
            }
        }

        int selectedSlot = player.getInventory().selectedSlot;
        boolean isPerkOrPlaceholder = ZombiesHotbarManager.isPerkSlot(selectedSlot)
            || ZombiesHotbarManager.isPerkItem(stack)
            || ZombiesHotbarManager.isPlaceholder(stack);

        if (stack.getItem() instanceof net.minecraft.item.BlockItem) {
            player.sendMessage(Text.literal("Building is disabled in this match.").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        }

        if (isPerkOrPlaceholder) {
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    @Override
    public ActionResult onAttackBlock(ServerPlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        // Block all block breaking on the map
        return ActionResult.FAIL;
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.state != GameState.RUNNING) return true;

        // Downed players and freshly revived players (2s invulnerability) are immune to damage
        if (this.reviveManager.isDowned(player.getUuid()) || this.reviveManager.isInvulnerable(player.getUuid())) {
            return false;
        }

        this.lastDamageTimes.put(player.getUuid(), System.currentTimeMillis());

        // Intercept lethal damage to trigger Downed state
        if (player.getHealth() - amount <= 0) {
            this.reviveManager.downPlayer(player);
            if (this.reviveManager.isAllDeadOrDowned(getParticipants())) {
                loseMatch("All survivors were defeated!");
            }
            return false;
        }

        return true;
    }

    private void handlePowerSwitch(ServerPlayerEntity player) {
        if (this.powerActive) {
            player.sendMessage(Text.literal("The power is already active!").formatted(Formatting.YELLOW), true);
            return;
        }

        int cost = this.mapConfig.powerSwitch() != null && this.mapConfig.powerSwitch().getGold() > 0
            ? this.mapConfig.powerSwitch().getGold()
            : 1000;

        if (!spendGold(player, cost)) {
            player.sendMessage(Text.literal("You need " + cost + " Gold to activate the Power Switch!").formatted(Formatting.RED), true);
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
            return;
        }

        this.powerActive = true;
        if (this.hologramManager != null) {
            this.hologramManager.onPowerActivated();
        }
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.BLOCKS, 1.2f, 1.0f);

        Text powerTitle = Text.literal(player.getName().getString() + " activated Power!").formatted(Formatting.GOLD, Formatting.BOLD);
        Text powerSubtitle = Text.literal("Perk machines and traps are online!").formatted(Formatting.YELLOW);
        dev.frost.miniverse.minigame.core.GameMessenger.showGameTitle(getParticipants(), powerTitle, powerSubtitle);

        for (ServerPlayerEntity p : getParticipants()) {
            p.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
        }

        broadcast(Text.literal("⚡ POWER HAS BEEN RESTORED by " + player.getName().getString() + "! Perk machines are now operational!").formatted(Formatting.GOLD, Formatting.BOLD));
    }

    private void handleWeaponShop(ServerPlayerEntity player, ZombiesWeaponShop shop) {
        WeaponType targetType = shop.getWeaponType();
        boolean ownsWeapon = false;
        ItemStack ownedStack = null;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (WeaponItemHelper.getWeaponType(s) == targetType) {
                ownsWeapon = true;
                ownedStack = s;
                break;
            }
        }

        if (ownsWeapon && ownedStack != null) {
            // Ammo refill
            int cost = shop.getRefillPrice();
            if (!spendGold(player, cost)) {
                player.sendMessage(Text.literal("Not enough gold to refill ammo! (" + cost + "g required)").formatted(Formatting.RED), true);
                return;
            }
            WeaponItemHelper.refillAmmo(ownedStack, targetType);
            player.playSound(SoundEvents.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
            player.sendMessage(Text.literal("✔ Refilled " + targetType.getData().displayName() + " ammo! (-" + cost + "g)").formatted(Formatting.GREEN), true);
        } else {
            // Purchase weapon
            boolean hasExtraWeapon = hasPerk(player, PlayerPerk.EXTRA_WEAPON);
            int targetSlot = -1;
            int openSlot = ZombiesHotbarManager.getFirstOpenWeaponSlot(player, hasExtraWeapon);
            if (openSlot != -1) {
                targetSlot = openSlot;
            } else {
                int heldSlot = player.getInventory().selectedSlot;
                if (!ZombiesHotbarManager.isWeaponSlot(heldSlot, hasExtraWeapon)) {
                    String slotRange = hasExtraWeapon ? "2–4" : "2–3";
                    player.sendMessage(Text.literal("Weapon slots full! Select a weapon slot (" + slotRange + ") to replace.").formatted(Formatting.RED), true);
                    player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
                    return;
                }
                targetSlot = heldSlot;
            }

            int cost = shop.getPurchasePrice();
            if (!spendGold(player, cost)) {
                player.sendMessage(Text.literal("Not enough gold to purchase " + targetType.getData().displayName() + "! (" + cost + "g required)").formatted(Formatting.RED), true);
                return;
            }
            ItemStack newWeapon = WeaponItemHelper.createWeaponStack(targetType);
            player.getInventory().setStack(targetSlot, newWeapon);
            player.playerScreenHandler.sendContentUpdates();
            player.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

            // Spec sheet in chat
            WeaponItemHelper.sendWeaponSpecSheet(player, targetType, "You purchased " + targetType.getData().displayName() + "!");
        }
    }

    private void handleArmorShop(ServerPlayerEntity player, ZombiesArmorShop shop) {
        int cost = shop.getPrice();
        if (!spendGold(player, cost)) {
            player.sendMessage(Text.literal("Not enough gold! (" + cost + "g required)").formatted(Formatting.RED), true);
            return;
        }

        ZombiesArmorShop.ArmorQuality q = shop.getQuality();
        if (shop.getPart() == ZombiesArmorShop.ArmorPart.UPPER_BODY) {
            ItemStack helm = new ItemStack(q.getHelmet());
            dev.frost.miniverse.minigame.core.item.ProtectedItemTags.mark(helm, ProtectedItemTypes.ZOMBIES_ARMOR);
            player.equipStack(EquipmentSlot.HEAD, helm);

            ItemStack chest = new ItemStack(q.getChestplate());
            dev.frost.miniverse.minigame.core.item.ProtectedItemTags.mark(chest, ProtectedItemTypes.ZOMBIES_ARMOR);
            player.equipStack(EquipmentSlot.CHEST, chest);
        } else {
            ItemStack legs = new ItemStack(q.getLeggings());
            dev.frost.miniverse.minigame.core.item.ProtectedItemTags.mark(legs, ProtectedItemTypes.ZOMBIES_ARMOR);
            player.equipStack(EquipmentSlot.LEGS, legs);

            ItemStack boots = new ItemStack(q.getBoots());
            dev.frost.miniverse.minigame.core.item.ProtectedItemTags.mark(boots, ProtectedItemTypes.ZOMBIES_ARMOR);
            player.equipStack(EquipmentSlot.FEET, boots);
        }
        player.playerScreenHandler.sendContentUpdates();

        player.playSound(SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND.value(), 1.0f, 1.0f);
        player.sendMessage(Text.literal("✔ Equipped " + q.name() + " Armor! (-" + cost + "g)").formatted(Formatting.GREEN), true);
    }

    private void handlePerkMachine(ServerPlayerEntity player, ZombiesPerkMachine machine) {
        if (!this.powerActive) {
            player.sendMessage(Text.literal("⚡ The power must be turned on first!").formatted(Formatting.YELLOW), true);
            player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
            return;
        }

        PlayerPerk perk = machine.getPerk();
        Set<PlayerPerk> perks = this.playerPerks.computeIfAbsent(player.getUuid(), id -> ConcurrentHashMap.newKeySet());

        if (perks.contains(perk)) {
            player.sendMessage(Text.literal("You already have " + perk.getDisplayName() + "!").formatted(Formatting.YELLOW), true);
            return;
        }

        int targetSlot = -1;
        int openSlot = ZombiesHotbarManager.getFirstOpenPerkSlot(player);
        if (openSlot != -1) {
            targetSlot = openSlot;
        } else {
            int heldSlot = player.getInventory().selectedSlot;
            if (!ZombiesHotbarManager.isPerkSlot(heldSlot)) {
                player.sendMessage(Text.literal("Perk slots full! Select a perk slot (7–9) to replace.").formatted(Formatting.RED), true);
                player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f, 0.5f);
                return;
            }
            targetSlot = heldSlot;
            PlayerPerk oldPerk = ZombiesHotbarManager.getPerkAtSlot(player, targetSlot);
            if (oldPerk != null) {
                perks.remove(oldPerk);
                if (oldPerk == PlayerPerk.EXTRA_HEALTH) {
                    var hp = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                    if (hp != null) hp.setBaseValue(20.0);
                    if (player.getHealth() > 20.0f) player.setHealth(20.0f);
                } else if (oldPerk == PlayerPerk.SPEED) {
                    var spd = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
                    if (spd != null) spd.setBaseValue(0.1);
                } else if (oldPerk == PlayerPerk.EXTRA_WEAPON) {
                    ZombiesHotbarManager.lockExtraWeaponSlot(player);
                }
            }
        }

        int cost = machine.getGold();
        if (!spendGold(player, cost)) {
            player.sendMessage(Text.literal("Not enough gold! (" + cost + "g required)").formatted(Formatting.RED), true);
            return;
        }

        player.getInventory().setStack(targetSlot, perk.createItemStack());
        player.playerScreenHandler.sendContentUpdates();
        perks.add(perk);

        // Apply stat modifications
        if (perk == PlayerPerk.EXTRA_HEALTH) {
            var hp = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
            if (hp != null) hp.setBaseValue(30.0);
            player.setHealth(30.0f);
        } else if (perk == PlayerPerk.SPEED) {
            var spd = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.125);
        } else if (perk == PlayerPerk.EXTRA_WEAPON) {
            ZombiesHotbarManager.unlockExtraWeaponSlot(player);
        }

        player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        broadcast(Text.literal("✦ " + player.getName().getString() + " purchased ").append(perk.toFormattedText()).append(Text.literal("!")));
    }

    private boolean handleDoor(ServerPlayerEntity player, ZombiesDoor door) {
        int cost = door.getGold();
        if (!spendGold(player, cost)) {
            player.sendMessage(Text.literal("Not enough gold to open door! (" + cost + "g required)").formatted(Formatting.RED), true);
            return false;
        }

        door.openDoor(player.getServerWorld());
        this.reachableAreas.add(door.getArea1());
        this.reachableAreas.add(door.getArea2());
        if (this.hologramManager != null) {
            this.hologramManager.removeDoorHologram(door.getId());
        }

        Formatting areaColor = getAreaFormatting(door.getArea2());
        Text title = Text.literal(player.getName().getString() + " opened ").formatted(Formatting.WHITE)
            .append(Text.literal(door.getArea2() + "!").formatted(areaColor, Formatting.BOLD));
        Text subtitle = Text.literal("Door unlocked for " + cost + "g").formatted(Formatting.GRAY, Formatting.ITALIC);
        dev.frost.miniverse.minigame.core.GameMessenger.showGameTitle(getParticipants(), title, subtitle);

        for (ServerPlayerEntity p : getParticipants()) {
            p.playSound(SoundEvents.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.0f);
        }

        broadcast(Text.literal("🚪 " + player.getName().getString() + " opened the door to " + door.getArea2() + "! (-" + cost + "g)").formatted(Formatting.GOLD));
        return true;
    }

    private Formatting getAreaFormatting(String areaName) {
        if (areaName == null) return Formatting.GOLD;
        String lower = areaName.toLowerCase();
        if (lower.contains("alley")) return Formatting.YELLOW;
        if (lower.contains("office")) return Formatting.AQUA;
        if (lower.contains("hotel")) return Formatting.GOLD;
        if (lower.contains("apartment")) return Formatting.GREEN;
        if (lower.contains("rooftop")) return Formatting.LIGHT_PURPLE;
        if (lower.contains("power")) return Formatting.RED;
        if (lower.contains("garden")) return Formatting.DARK_GREEN;
        return Formatting.GOLD;
    }

    private void updateScoreboards() {
        List<ServerPlayerEntity> participants = getParticipants();
        int totalSeconds = this.tickCounter / 20;
        String timeStr = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);

        for (ServerPlayerEntity player : participants) {
            ScoreboardTemplate board = this.scoreboards.computeIfAbsent(player.getUuid(), id -> {
                ScoreboardTemplate t = new ScoreboardTemplate(id.toString(), Text.literal("ZOMBIES").formatted(Formatting.RED, Formatting.BOLD));
                t.show(player);
                return t;
            });

            board.clearLines();
            board.addBlankLine();

            int round = this.waveEngine.getCurrentRound();
            String roundLabel = "Round " + round + (this.settings.endlessMode() && round > 30 ? " (Endless)" : "");
            if (this.waveEngine.getState() == ZombieWaveEngine.WaveState.IN_ROUND) {
                board.addLine(Text.literal(roundLabel).formatted(Formatting.WHITE));
                board.addLine(Text.literal("Zombies left: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(this.mobManager.getAliveMobCount() + this.waveEngine.getRemainingInQueue())).formatted(Formatting.RED)));
            } else if (this.waveEngine.getState() == ZombieWaveEngine.WaveState.INTERMISSION) {
                board.addLine(Text.literal(roundLabel).formatted(Formatting.WHITE).append(Text.literal(" (Next: " + this.waveEngine.getIntermissionSeconds() + "s)").formatted(Formatting.YELLOW)));
                board.addLine(Text.literal("Zombies left: 0").formatted(Formatting.GRAY));
            } else {
                board.addLine(Text.literal("Round: -").formatted(Formatting.GRAY));
            }
            board.addLine(Text.literal("Difficulty: ").formatted(Formatting.GRAY).append(Text.literal(this.settings.difficulty().getDisplayName()).formatted(this.settings.difficulty().getColor())));

            board.addBlankLine();

            // 1. Viewer is always pinned at the top of the gold list
            board.addLine(Text.literal(player.getName().getString() + " (You): ").formatted(Formatting.GOLD).append(Text.literal(String.valueOf(getGold(player))).formatted(Formatting.WHITE)));

            // 2. Other players with 5-second rotation
            List<ServerPlayerEntity> otherPlayers = participants.stream()
                .filter(p -> !p.getUuid().equals(player.getUuid()))
                .toList();

            if (!otherPlayers.isEmpty()) {
                if (otherPlayers.size() <= 3) {
                    for (ServerPlayerEntity other : otherPlayers) {
                        board.addLine(Text.literal(other.getName().getString() + ": ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(getGold(other))).formatted(Formatting.WHITE)));
                    }
                } else {
                    int totalOthers = otherPlayers.size();
                    int totalPages = (totalOthers + 2) / 3;
                    int page = (this.tickCounter / 100) % totalPages; // 5 seconds per page (100 ticks)
                    int startIdx = page * 3;
                    int endIdx = Math.min(startIdx + 3, totalOthers);

                    for (int i = startIdx; i < endIdx; i++) {
                        ServerPlayerEntity other = otherPlayers.get(i);
                        board.addLine(Text.literal(other.getName().getString() + ": ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(getGold(other))).formatted(Formatting.WHITE)));
                    }
                    if (totalPages > 1) {
                        board.addLine(Text.literal("(Page " + (page + 1) + "/" + totalPages + ")").formatted(Formatting.DARK_GRAY));
                    }
                }
            }

            board.addBlankLine();
            board.addLine(Text.literal("Kills: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(getKills(player))).formatted(Formatting.GREEN)));
            board.addLine(Text.literal("Time: ").formatted(Formatting.GRAY).append(Text.literal(timeStr).formatted(Formatting.YELLOW)));

            board.sendLineUpdates();
        }
    }

    private void broadcastPostGameStats(boolean victory, String reason) {
        int totalSeconds = this.tickCounter / 20;
        String timeStr = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
        int rounds = this.waveEngine != null ? this.waveEngine.getCurrentRound() : 0;

        broadcast(Text.literal("═══════════════════════════════════").formatted(victory ? Formatting.GOLD : Formatting.RED));
        broadcast(Text.literal("           ZOMBIES").formatted(Formatting.WHITE, Formatting.BOLD));
        if (victory) {
            broadcast(Text.literal("            ★ VICTORY! ★").formatted(Formatting.GOLD, Formatting.BOLD));
        } else {
            broadcast(Text.literal("          ☠ GAME OVER ☠").formatted(Formatting.DARK_RED, Formatting.BOLD));
            if (reason != null && !reason.isBlank()) {
                broadcast(Text.literal("        " + reason).formatted(Formatting.RED));
            }
        }
        broadcast(Text.literal("═══════════════════════════════════").formatted(victory ? Formatting.GOLD : Formatting.RED));
        broadcast(Text.literal(" Rounds Survived: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(rounds)).formatted(Formatting.YELLOW, Formatting.BOLD)));
        broadcast(Text.literal(" Time Elapsed: ").formatted(Formatting.GRAY).append(Text.literal(timeStr).formatted(Formatting.WHITE)));
        broadcast(Text.empty());
        broadcast(Text.literal(" Survivor Performance:").formatted(Formatting.YELLOW, Formatting.BOLD));

        for (ServerPlayerEntity p : getParticipants()) {
            int kills = getKills(p);
            int revives = this.playerRevives.getOrDefault(p.getUuid(), 0);
            int spent = this.playerGoldSpent.getOrDefault(p.getUuid(), 0);
            broadcast(Text.literal(" • ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(p.getName().getString()).formatted(Formatting.WHITE, Formatting.BOLD))
                .append(Text.literal(": " + kills + " Kills | " + revives + " Revives | " + spent + "g Spent").formatted(Formatting.GRAY))
            );
        }
        broadcast(Text.literal("═══════════════════════════════════").formatted(victory ? Formatting.GOLD : Formatting.RED));
    }

    public void winMatch() {
        broadcastPostGameStats(true, null);
        for (ServerPlayerEntity p : getParticipants()) {
            p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
        }

        Set<UUID> winners = new HashSet<>();
        for (ServerPlayerEntity p : getParticipants()) winners.add(p.getUuid());

        MinigameManager.getInstance().getMatchLifecycleController().endMatch(
            this.runtime,
            new MatchEndResult(winners, Text.literal("Zombies Victory!")),
            MatchLifecycleOptions.defaults("Zombies")
                .withEndTitles(
                    Text.literal("VICTORY").formatted(Formatting.GOLD, Formatting.BOLD),
                    Text.literal("DEFEAT").formatted(Formatting.DARK_RED, Formatting.BOLD)
                )
        );
    }

    public void loseMatch(String reason) {
        broadcastPostGameStats(false, reason);
        for (ServerPlayerEntity p : getParticipants()) {
            p.playSound(SoundEvents.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
        }

        MinigameManager.getInstance().getMatchLifecycleController().endMatch(
            this.runtime,
            new MatchEndResult(Set.of(), Text.literal("Defeat: " + reason).formatted(Formatting.RED)),
            MatchLifecycleOptions.defaults("Zombies")
                .withEndTitles(
                    Text.literal("VICTORY").formatted(Formatting.GOLD, Formatting.BOLD),
                    Text.literal("GAME OVER").formatted(Formatting.DARK_RED, Formatting.BOLD)
                )
        );
    }

    public void clearPlayerPerks(ServerPlayerEntity player) {
        Set<PlayerPerk> perks = this.playerPerks.remove(player.getUuid());
        if (perks != null) {
            perks.clear();
        }
        var hp = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(20.0);
        }
        var spd = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(0.1);
        }
        ZombiesHotbarManager.resetPerkSlots(player);
        ZombiesHotbarManager.lockExtraWeaponSlot(player);
    }

    @Override
    protected void onMatchEnd() {
        this.state = GameState.ENDING;
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
        }

        ProtectedItemService pis = ProtectedItemService.getInstance();
        pis.removeRule(ProtectedItemTypes.ZOMBIES_KNIFE);
        pis.removeRule(ProtectedItemTypes.ZOMBIES_WEAPON);
        pis.removeRule(ProtectedItemTypes.ZOMBIES_ARMOR);
        pis.removeRule(ProtectedItemTypes.ZOMBIES_PLACEHOLDER);
        pis.removeRule(ProtectedItemTypes.ZOMBIES_PERK);

        if (this.hologramManager != null) this.hologramManager.clear();
        if (this.mobManager != null) this.mobManager.clearAll();
        if (this.dropManager != null) this.dropManager.cleanup();
        if (this.reviveManager != null) this.reviveManager.cleanup();
        for (ServerPlayerEntity p : getParticipants()) {
            DownedPlayerTracker.setDowned(p.getUuid(), false);
            p.setPose(EntityPose.STANDING);
            ServerPlayNetworking.send(p, new NetworkConstants.DownedStatePayload(p.getUuid(), false));
        }
        DownedPlayerTracker.clear();
        if (this.luckyChestManager != null) this.luckyChestManager.cleanup();

        for (ScoreboardTemplate board : this.scoreboards.values()) {
            board.cleanup(this.context != null ? this.context.nullableServer() : null);
        }
        this.scoreboards.clear();
        this.activeActionBarPrompts.clear();
        this.repairCompleteTicks.clear();
    }
}
