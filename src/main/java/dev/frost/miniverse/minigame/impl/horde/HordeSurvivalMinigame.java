package dev.frost.miniverse.minigame.impl.horde;

import com.google.gson.JsonObject;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.GameMessenger;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.SessionRoster;
import dev.frost.miniverse.minigame.core.countdown.CountdownService;
import dev.frost.miniverse.minigame.core.event.EntityInteractAware;
import dev.frost.miniverse.minigame.core.event.ItemUseOnBlockAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.shop.ShopGui;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamAdapter;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.minigame.core.protection.BlockProtectionProvider;
import dev.frost.miniverse.minigame.impl.horde.bounty.HordeBountyManager;
import dev.frost.miniverse.minigame.impl.horde.transmitter.TransmitterPod;
import dev.frost.miniverse.minigame.impl.horde.transmitter.TransmitterPodManager;
import dev.frost.miniverse.minigame.impl.horde.revive.DownedPlayerManager;
import dev.frost.miniverse.minigame.impl.horde.shop.EmergencyFlareItem;
import dev.frost.miniverse.minigame.impl.horde.shop.HordeShopProvider;
import dev.frost.miniverse.minigame.impl.horde.shop.VirtualCoinCurrency;
import dev.frost.miniverse.minigame.impl.horde.wave.WaveDefinition;
import dev.frost.miniverse.minigame.impl.horde.wave.WaveEngine;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LodestoneTrackerComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HordeSurvivalMinigame extends AbstractMinigame implements TeamManagerProvider, EntityInteractAware, ItemUseOnBlockAware, BlockProtectionProvider {
    public static final String TEAM_SURVIVORS = "survivors";

    private final VanillaTeamAdapter vanillaTeams = new VanillaTeamAdapter("horde");
    private final TeamManager teamManager = new TeamManager();
    private final SpectatorService spectators = SpectatorService.getInstance();
    private final CountdownService countdownService = new CountdownService();

    private HordeSurvivalSettings settings = HordeSurvivalSettings.defaults();
    private VirtualCoinCurrency currency;
    private final TransmitterPodManager podManager;
    private final WaveEngine waveEngine;
    private final DownedPlayerManager downedManager;
    private final HordeBountyManager bountyManager;

    private final Map<UUID, Integer> coins = new ConcurrentHashMap<>();
    private final Set<UUID> aliveParticipants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ScoreboardTemplate> scoreboards = new ConcurrentHashMap<>();

    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private Phase phase = Phase.PRE_GAME;
    private int currentWave = 0;
    private int phaseTicksRemaining = 0;
    private int tickCounter = 0;
    private BlockPos extractionPos = null;
    private int extractionHoldTicks = 0;

    public enum Phase {
        PRE_GAME,
        WAVE_ACTIVE,
        INTERMISSION,
        EXTRACTION
    }

    public HordeSurvivalMinigame() {
        this.currency = new VirtualCoinCurrency(this);
        this.downedManager = new DownedPlayerManager(this);
        this.bountyManager = new HordeBountyManager(this);
        this.podManager = new TransmitterPodManager(this);
        this.waveEngine = new WaveEngine(this);
    }

    public void applySettings(HordeSurvivalSettings settings) {
        this.settings = settings != null ? settings : HordeSurvivalSettings.defaults();
    }

    public HordeSurvivalSettings getSettings() {
        return settings;
    }

    public VirtualCoinCurrency getCurrency() {
        return currency;
    }

    public DownedPlayerManager getDownedManager() {
        return downedManager;
    }

    public HordeBountyManager getBountyManager() {
        return bountyManager;
    }

    public TransmitterPodManager getPodManager() {
        return podManager;
    }

    public TransmitterPod getCurrentPod() {
        return podManager.getCurrentPod();
    }

    public BlockPos getExtractionPos() {
        return extractionPos;
    }

    public BlockPos getCurrentCampfirePos() {
        if (this.podManager != null && this.podManager.getCurrentPod() != null) {
            return this.podManager.getCurrentPod().getCampfirePos();
        }
        return this.extractionPos;
    }

    public int getCoins(UUID uuid) {
        return this.coins.getOrDefault(uuid, 0);
    }

    public void addCoins(UUID uuid, int amount) {
        this.coins.merge(uuid, amount, Integer::sum);
    }

    public boolean deductCoins(UUID uuid, int amount) {
        int current = getCoins(uuid);
        if (current < amount) return false;
        this.coins.put(uuid, current - amount);
        return true;
    }

    @Override
    public String getName() {
        return "Horde Survival";
    }

    @Override
    public GameState getState() {
        return this.state;
    }

    @Override
    public void setState(GameState state) {
        this.state = state;
    }

    public boolean canStartMatch() {
        return this.context != null && !this.context.roster().isEmpty();
    }

    public int getCurrentWave() {
        return this.currentWave;
    }

    public MinecraftServer getServer() {
        return this.context != null ? this.context.nullableServer() : null;
    }

    public String getGameId() {
        return HordeSurvivalDefinition.ID;
    }

    public List<ServerPlayerEntity> getParticipants() {
        if (this.context == null) {
            return List.of();
        }
        return this.context.liveParticipants();
    }

    public void broadcast(Text message) {
        GameMessenger.broadcast(this.getParticipants(), message);
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(GameRules.DO_IMMEDIATE_RESPAWN, true);
        this.applyVanillaGameRule(GameRules.DO_DAYLIGHT_CYCLE, false);
        this.applyVanillaGameRule(GameRules.DO_WEATHER_CYCLE, false);
        this.applyVanillaGameRule(GameRules.DO_MOB_SPAWNING, false);
        this.state = GameState.WAITING_FOR_PLAYERS;

        this.coins.clear();
        this.aliveParticipants.clear();
        this.scoreboards.clear();
        this.teamManager.clear();
        this.phase = Phase.PRE_GAME;
        this.currentWave = 0;
        this.phaseTicksRemaining = 0;
        this.tickCounter = 0;
        this.extractionPos = null;
        this.extractionHoldTicks = 0;
    }

    @Override
    protected void onMatchStart() {
        List<ServerPlayerEntity> participants = this.getParticipants();
        if (participants.isEmpty()) {
            this.broadcast(Text.literal("No players selected to start Horde Survival!").formatted(Formatting.RED));
            return;
        }

        this.state = GameState.RUNNING;
        if (this.context != null) {
            this.context.setState(GameState.RUNNING);
        }

        ServerWorld world = participants.get(0).getServerWorld();
        world.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false, world.getServer());
        world.setTimeOfDay(18000); // Midnight
        world.setWeather(0, 6000, true, true); // Storm

        this.aliveParticipants.clear();
        for (ServerPlayerEntity p : participants) {
            this.aliveParticipants.add(p.getUuid());
            this.teamManager.assign(p, TEAM_SURVIVORS, "Survivors");
            this.giveStarterKit(p);
            this.coins.putIfAbsent(p.getUuid(), 50); // 50 starting coins
        }
        this.syncVanillaTeams();

        // Start Wave 1 (spawns first Transmitter Pod)
        this.startWave(1);
    }

    @Override
    protected void syncVanillaTeams() {
        MinecraftServer srv = this.context != null ? this.context.nullableServer() : null;
        if (srv == null) return;

        this.vanillaTeams.syncSnapshots(srv, this.teamManager.snapshots(), snapshot -> {
            return VanillaTeamOptions.defaults()
                .withColor(Formatting.GREEN)
                .withPrefix(Text.literal("[Survivor] ").formatted(Formatting.GREEN))
                .withFriendlyFireAllowed(false)
                .withCollisionRule(AbstractTeam.CollisionRule.ALWAYS);
        });
    }

    private void giveStarterKit(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.getInventory().offerOrDrop(new ItemStack(Items.STONE_SWORD));
        player.getInventory().offerOrDrop(new ItemStack(Items.SHIELD));
        player.getInventory().offerOrDrop(new ItemStack(Items.COOKED_BEEF, 8));
        player.getInventory().offerOrDrop(new ItemStack(Items.TORCH, 16));

        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Pod Tracker Compass").formatted(Formatting.GOLD));
        if (this.podManager != null && this.podManager.getCurrentPod() != null) {
            compass.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(
                Optional.of(GlobalPos.create(player.getWorld().getRegistryKey(), this.podManager.getCurrentPod().getPos())),
                false
            ));
        }
        player.getInventory().offerOrDrop(compass);
    }

    public void startWave(int wave) {
        this.currentWave = wave;
        this.phase = Phase.WAVE_ACTIVE;
        this.phaseTicksRemaining = this.settings.uplinkDurationSeconds() * 20;

        List<ServerPlayerEntity> participants = this.getParticipants();
        if (!participants.isEmpty()) {
            ServerWorld world = participants.get(0).getServerWorld();
            BlockPos center = participants.get(0).getBlockPos();
            this.podManager.spawnPodForWave(
                world,
                center,
                wave,
                this.settings.uplinkDurationSeconds(),
                this.settings.initialPodFuel(),
                this.settings.harvestRadius(),
                this.settings.fuelDrainPerSecond()
            );

            if (this.podManager.getCurrentPod() != null) {
                for (ServerPlayerEntity p : participants) {
                    updateCompassTarget(p, this.podManager.getCurrentPod().getPos());
                }
            }
        }

        this.waveEngine.startWave(wave, participants.size());
        this.bountyManager.assignBountiesForWave(wave, this.waveEngine.getCurrentDef(), participants);

        WaveDefinition.MilestoneType milestone = this.waveEngine.getCurrentDef() != null ? this.waveEngine.getCurrentDef().milestone() : WaveDefinition.MilestoneType.NONE;
        if (milestone == WaveDefinition.MilestoneType.BLOOD_MOON) {
            this.broadcast(Text.literal("☠ BLOOD MOON RISES! (WAVE 5) ☠").formatted(Formatting.DARK_RED, Formatting.BOLD));
            this.broadcast(Text.literal("Supercharged Creepers & Double Coin Drops!").formatted(Formatting.RED));
            for (ServerPlayerEntity p : participants) {
                p.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
            }
        } else if (milestone == WaveDefinition.MilestoneType.SHADOW_PLAGUE) {
            this.broadcast(Text.literal("☠ SHADOW PLAGUE DESCENDS! (WAVE 10) ☠").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            this.broadcast(Text.literal("Toxic Beasts & Double Coin Drops!").formatted(Formatting.LIGHT_PURPLE));
            for (ServerPlayerEntity p : participants) {
                p.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 1.0f, 1.2f);
            }
        } else {
            this.broadcast(Text.literal("☠ WAVE " + wave + " / " + this.settings.totalWaves() + " HAS BEGUN!").formatted(Formatting.RED, Formatting.BOLD));
            for (ServerPlayerEntity p : participants) {
                p.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
            }
        }
    }

    public void startIntermission() {
        this.phase = Phase.INTERMISSION;
        this.phaseTicksRemaining = this.settings.intermissionSeconds() * 20;

        List<ServerPlayerEntity> participants = this.getParticipants();
        this.broadcast(Text.literal("✔ WAVE " + this.currentWave + " SURVIVED! Intermission: " + this.settings.intermissionSeconds() + "s").formatted(Formatting.GREEN, Formatting.BOLD));
        for (ServerPlayerEntity p : participants) {
            p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            if (!this.aliveParticipants.contains(p.getUuid())) {
                respawnSurvivor(p);
            }
        }

        if (participants.isEmpty()) return;
        ServerWorld world = participants.get(0).getServerWorld();

        // Spawn Nomad merchant at the completed pod during intermission
        if (this.podManager.getCurrentPod() != null) {
            this.podManager.spawnNomadMerchant(world, this.podManager.getCurrentPod().getPos());
        }
    }

    public void startExtraction() {
        this.phase = Phase.EXTRACTION;
        this.phaseTicksRemaining = 180 * 20; // 3 minutes to extract
        List<ServerPlayerEntity> participants = this.getParticipants();
        if (participants.isEmpty()) return;

        ServerWorld world = participants.get(0).getServerWorld();
        this.podManager.cleanup(world);

        // Pick extraction LZ 200-300m away
        BlockPos center = participants.get(0).getBlockPos();
        this.extractionPos = TransmitterPodManager.findBestFlatSurface(world, center, 200, 300);

        this.broadcast(Text.literal("🚨 DISTRESS BEACON ACTIVE! REACH EXTRACTION POINT IN 3 MINUTES!").formatted(Formatting.GOLD, Formatting.BOLD));
        for (ServerPlayerEntity p : participants) {
            p.playSound(SoundEvents.EVENT_RAID_HORN.value(), 2.0f, 1.0f);
        }

        this.waveEngine.startWave(this.currentWave + 1, participants.size() * 2);
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        if (this.state != GameState.RUNNING) return;
        this.tickCounter++;

        List<ServerPlayerEntity> participants = this.getParticipants();
        if (participants.isEmpty()) return;
        ServerWorld world = participants.get(0).getServerWorld();

        this.podManager.tick(world);

        // Update compass target
        if (this.tickCounter % 20 == 0) {
            BlockPos targetPos = this.phase == Phase.EXTRACTION
                ? this.extractionPos
                : (this.podManager.getCurrentPod() != null ? this.podManager.getCurrentPod().getPos() : null);
            if (targetPos != null) {
                for (ServerPlayerEntity p : participants) {
                    updateCompassTarget(p, targetPos);
                }
            }
        }

        // Tick downed players
        List<ServerPlayerEntity> aliveList = participants.stream().filter(p -> this.aliveParticipants.contains(p.getUuid())).toList();
        this.downedManager.tick(world, aliveList);
        if (this.downedManager.allSurvivorsDowned(aliveList)) {
            loseMatch("All survivors have fallen!");
            return;
        }

        // Phase specific logic
        if (this.phase == Phase.WAVE_ACTIVE) {
            this.phaseTicksRemaining--;

            TransmitterPod pod = this.podManager.getCurrentPod();
            if (pod != null && pod.isCompleted()) {
                // Uplink completed! EMP Shockwave and unlock supply crate
                pod.detonateEmp(world, this.currentWave);
                this.waveEngine.purgeWaveMobs(world);

                for (ServerPlayerEntity p : participants) {
                    addCoins(p.getUuid(), 50);
                    p.sendMessage(Text.literal("✔ DATA UPLINK COMPLETE! +50 Coins awarded!").formatted(Formatting.GREEN, Formatting.BOLD), false);
                }

                if (this.currentWave >= this.settings.totalWaves()) {
                    startExtraction();
                } else {
                    startIntermission();
                }
                return;
            }

            this.waveEngine.tick(world, aliveList);
        } else if (this.phase == Phase.INTERMISSION) {
            this.phaseTicksRemaining--;
            if (this.phaseTicksRemaining <= 0) {
                startWave(this.currentWave + 1);
            }
        } else if (this.phase == Phase.EXTRACTION) {
            this.phaseTicksRemaining--;
            this.waveEngine.tick(world, participants.stream().filter(p -> this.aliveParticipants.contains(p.getUuid())).toList());

            if (this.extractionPos != null) {
                if (this.tickCounter % 5 == 0) {
                    for (int y = this.extractionPos.getY(); y < this.extractionPos.getY() + 50; y += 3) {
                        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, this.extractionPos.getX() + 0.5, y, this.extractionPos.getZ() + 0.5, 1, 0.1, 0.2, 0.1, 0.01);
                    }
                }

                boolean survivorAtLZ = false;
                for (ServerPlayerEntity p : participants) {
                    if (this.aliveParticipants.contains(p.getUuid()) && p.squaredDistanceTo(this.extractionPos.toCenterPos()) <= 64.0) {
                        survivorAtLZ = true;
                        break;
                    }
                }

                if (survivorAtLZ) {
                    this.extractionHoldTicks++;
                    if (this.extractionHoldTicks % 20 == 0) {
                        int remainingSecs = 10 - (this.extractionHoldTicks / 20);
                        this.broadcast(Text.literal("Securing LZ... " + remainingSecs + "s").formatted(Formatting.AQUA));
                    }
                    if (this.extractionHoldTicks >= 200) {
                        winMatch();
                        return;
                    }
                } else {
                    this.extractionHoldTicks = Math.max(0, this.extractionHoldTicks - 1);
                }

                if (this.phaseTicksRemaining <= 0) {
                    loseMatch("Time expired! The rescue airship departed without you.");
                    return;
                }
            }
        }

        // Scoreboard updates
        if (this.tickCounter % 20 == 0) {
            this.updateScoreboards();
        }
    }

    private void updateCompassTarget(ServerPlayerEntity player, BlockPos target) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.COMPASS)) {
                stack.set(DataComponentTypes.LODESTONE_TRACKER, new LodestoneTrackerComponent(
                    Optional.of(GlobalPos.create(player.getWorld().getRegistryKey(), target)),
                    false
                ));
            }
        }
    }

    private void updateScoreboards() {
        for (ServerPlayerEntity player : this.getParticipants()) {
            ScoreboardTemplate board = this.scoreboards.computeIfAbsent(player.getUuid(), id -> {
                ScoreboardTemplate t = new ScoreboardTemplate(id.toString(), Text.literal("HORDE SURVIVAL").formatted(Formatting.GOLD, Formatting.BOLD));
                t.show(player);
                return t;
            });

            board.clearLines();
            board.addBlankLine();

            if (this.phase == Phase.WAVE_ACTIVE) {
                TransmitterPod pod = this.podManager.getCurrentPod();
                board.addLine(Text.literal("Wave: §e" + this.currentWave + "§7/§e" + this.settings.totalWaves() + " §c[ACTIVE]"));
                if (pod != null) {
                    if (pod.getState() == TransmitterPod.State.AWAITING_ACTIVATION) {
                        int dist = (int) Math.sqrt(player.squaredDistanceTo(pod.getPos().toCenterPos()));
                        String dir = TransmitterPodManager.getCardinalAbbreviation(pod.getPos().getX() - player.getBlockX(), pod.getPos().getZ() - player.getBlockZ());
                        board.addLine(Text.literal("Pod: §b" + dist + "m (" + dir + ") §6[BOOT]"));
                    } else if (pod.getState() == TransmitterPod.State.PAUSED_NO_FUEL) {
                        board.addLine(Text.literal("Uplink: §c" + pod.getSecondsRemaining() + "s §4[0% FUEL]"));
                        board.addLine(Text.literal("Fuel: §c[░░░░░░░░░░] 0%"));
                    } else {
                        int sec = pod.getSecondsRemaining();
                        int fuel = (int) pod.getFuelPercent();
                        String fuelColor = fuel > 30 ? "§e" : "§c";
                        board.addLine(Text.literal("Uplink: §f" + String.format("%02d:%02d", sec / 60, sec % 60)));
                        board.addLine(Text.literal("Fuel: " + fuelColor + fuel + "% §7(" + (int) pod.getHarvestRadius() + "m Ring)"));
                    }
                }
                board.addLine(Text.literal("Active Mobs: §c" + this.waveEngine.getActiveMobCount()));
            } else if (this.phase == Phase.INTERMISSION) {
                int sec = Math.max(0, this.phaseTicksRemaining / 20);
                board.addLine(Text.literal("Wave: §a" + this.currentWave + "§7/§a" + this.settings.totalWaves() + " §7(Cleared)"));
                board.addLine(Text.literal("Next Wave: §e" + sec + "s"));
                if (this.podManager.getCurrentPod() != null) {
                    int pDist = (int) Math.sqrt(player.squaredDistanceTo(this.podManager.getCurrentPod().getPos().toCenterPos()));
                    board.addLine(Text.literal("Pod: §a" + pDist + "m (Nomad Shop)"));
                }
            } else if (this.phase == Phase.EXTRACTION) {
                int sec = Math.max(0, this.phaseTicksRemaining / 20);
                board.addLine(Text.literal("§c🚨 EXTRACTION ACTIVE!"));
                board.addLine(Text.literal("Time Left: §e" + String.format("%02d:%02d", sec / 60, sec % 60)));
                board.addLine(Text.literal("LZ Hold: §b" + (this.extractionHoldTicks / 20) + "§7/§b10s"));
                if (this.extractionPos != null) {
                    int dist = (int) Math.sqrt(player.squaredDistanceTo(this.extractionPos.toCenterPos()));
                    board.addLine(Text.literal("Extraction LZ: §b" + dist + "m"));
                }
            }

            board.addBlankLine();
            board.addLine(Text.literal("Bounty: §e" + this.bountyManager.getBountySummary(player.getUuid())));
            board.addLine(Text.literal("Alive: §f" + this.aliveParticipants.size() + "§7/§f" + this.getParticipants().size()));
            board.addLine(Text.literal("Coins: §6" + this.getCoins(player.getUuid()) + " ⛃"));

            board.sendLineUpdates();
        }
    }

    public void openEmergencyShop(ServerPlayerEntity player) {
        ShopGui.open(player, Text.literal("Emergency Spirit Vendor").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), HordeShopProvider.createCategories(this, true));
    }

    public void respawnSurvivor(ServerPlayerEntity player) {
        this.aliveParticipants.add(player.getUuid());
        player.changeGameMode(GameMode.SURVIVAL);
        if (this.podManager.getCurrentPod() != null) {
            BlockPos pPos = this.podManager.getCurrentPod().getPos();
            player.teleport(player.getServerWorld(), pPos.getX() + 0.5, pPos.getY() + 1, pPos.getZ() + 0.5, player.getYaw(), player.getPitch());
        }
        this.giveStarterKit(player);
        player.sendMessage(Text.literal("You have rejoined the fight!").formatted(Formatting.GREEN), false);
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (source.getAttacker() instanceof ServerPlayerEntity) {
            return false; // No friendly fire
        }
        if (this.downedManager.isDowned(player.getUuid())) {
            return false; // Immune to normal mob damage while downed
        }
        if (amount >= player.getHealth()) {
            this.downedManager.downPlayer(player);
            return false;
        }
        return true;
    }

    public void onSurvivorDied(ServerPlayerEntity victim) {
        handlePlayerElimination(victim);
    }

    @Override
    public void onPlayerDeath(ServerPlayerEntity player) {
        handlePlayerElimination(player);
    }

    private void handlePlayerElimination(ServerPlayerEntity victim) {
        this.aliveParticipants.remove(victim.getUuid());
        victim.changeGameMode(GameMode.SPECTATOR);

        this.broadcast(Text.literal("☠ " + victim.getName().getString() + " was overwhelmed!").formatted(Formatting.RED));

        if (this.aliveParticipants.isEmpty()) {
            loseMatch("All survivors have fallen!");
        }
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (!this.aliveParticipants.contains(newPlayer.getUuid())) {
            newPlayer.changeGameMode(GameMode.SPECTATOR);
        }
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String teamId, String role) {
        if (this.context != null && !this.context.roster().contains(player)) {
            this.context.roster().add(player);
        }
        if (this.state == GameState.RUNNING) {
            this.teamManager.assign(player, TEAM_SURVIVORS, "Survivors");
            this.syncVanillaTeams();
            this.respawnSurvivor(player);
        }
    }

    public void winMatch() {
        this.broadcast(Text.literal("🎉 VICTORY! The survivors successfully evacuated at dawn!").formatted(Formatting.GOLD, Formatting.BOLD));
        for (ServerPlayerEntity p : this.getParticipants()) {
            p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
        }
        MinigameManager.getInstance().getMatchLifecycleController().endMatch(
            this.runtime,
            new MatchEndResult(Set.copyOf(this.aliveParticipants), Text.literal("Survivors Evacuated!")),
            MatchLifecycleOptions.defaults("Horde Survival")
                .withEndTitles(
                    Text.literal("VICTORY").formatted(Formatting.GOLD, Formatting.BOLD),
                    Text.literal("DEFEAT").formatted(Formatting.DARK_RED, Formatting.BOLD)
                )
        );
    }

    public void loseMatch(String reason) {
        this.broadcast(Text.literal("☠ DEFEAT: " + reason).formatted(Formatting.DARK_RED, Formatting.BOLD));
        for (ServerPlayerEntity p : this.getParticipants()) {
            p.playSound(SoundEvents.ENTITY_WITHER_DEATH, 1.0f, 0.8f);
        }
        MinigameManager.getInstance().getMatchLifecycleController().endMatch(
            this.runtime,
            new MatchEndResult(Set.of(), Text.literal("Defeat: " + reason).formatted(Formatting.RED)),
            MatchLifecycleOptions.defaults("Horde Survival")
                .withEndTitles(
                    Text.literal("VICTORY").formatted(Formatting.GOLD, Formatting.BOLD),
                    Text.literal("DEFEAT").formatted(Formatting.DARK_RED, Formatting.BOLD)
                )
        );
    }

    @Override
    protected void onMatchEnd() {
        this.state = GameState.ENDING;
        if (this.context != null) {
            this.context.setState(GameState.ENDING);
        }

        List<ServerPlayerEntity> participants = this.getParticipants();
        if (!participants.isEmpty()) {
            ServerWorld world = participants.get(0).getServerWorld();
            this.waveEngine.cleanup(world);
            this.podManager.cleanup(world);
            this.downedManager.cleanup(world);
            this.bountyManager.cleanup();
        }

        for (ScoreboardTemplate board : this.scoreboards.values()) {
            board.cleanup(this.context != null ? this.context.nullableServer() : null);
        }
        this.scoreboards.clear();
        this.spectators.clearAll();
    }

    @Override
    protected boolean isTeamBased() {
        return true;
    }

    @Override
    public TeamManager teamManager() {
        return this.teamManager;
    }

    @Override
    public MatchProgressionValidator.ProgressionState checkProgression(SessionRoster roster) {
        int onlineCount = roster.onlinePlayers(this.context != null ? this.context.nullableServer() : null).size();
        if (onlineCount < 1) {
            return new MatchProgressionValidator.ProgressionState(true, null, Text.literal("Waiting for survivors to reconnect...").formatted(Formatting.RED));
        }
        return MatchProgressionValidator.ProgressionState.valid();
    }

    @Override
    public JsonObject saveRuntimeState() {
        JsonObject json = new JsonObject();
        json.addProperty("wave", this.currentWave);
        json.addProperty("phase", this.phase.name());
        return json;
    }

    @Override
    public void loadRuntimeState(JsonObject state) {
        if (state.has("wave")) {
            this.currentWave = state.get("wave").getAsInt();
        }
        if (state.has("phase")) {
            try {
                this.phase = Phase.valueOf(state.get("phase").getAsString());
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onEntityDeath(LivingEntity entity, DamageSource source) {
        ServerPlayerEntity killer = source.getAttacker() instanceof ServerPlayerEntity p ? p : null;
        this.waveEngine.handleMobDeath(entity.getUuid(), killer);

        // Check if mob was killed inside the Transmitter Pod perimeter ring!
        TransmitterPod pod = this.podManager.getCurrentPod();
        if (pod != null && (pod.getState() == TransmitterPod.State.TRANSMITTING || pod.getState() == TransmitterPod.State.PAUSED_NO_FUEL)) {
            double distSq = entity.squaredDistanceTo(pod.getPos().toCenterPos());
            double radius = pod.getHarvestRadius();
            if (distSq <= radius * radius) {
                if (entity.getWorld() instanceof ServerWorld serverWorld) {
                    pod.addFuel(2.0f, entity.getPos(), serverWorld);
                }
                if (killer != null && this.bountyManager != null) {
                    this.bountyManager.onFuelHarvested(killer.getUuid(), 2);
                }
            }
        }
    }

    @Override
    public ActionResult onEntityInteract(ServerPlayerEntity player, ServerWorld world, Hand hand, Entity entity) {
        if (this.podManager.handleEntityInteraction(player, entity)) {
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseBlock(ServerPlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (this.podManager.handleBlockInteraction(player, hitResult.getBlockPos())) {
            return ActionResult.SUCCESS;
        }

        TransmitterPod pod = this.podManager.getCurrentPod();
        if (pod != null && (hitResult.getBlockPos().equals(pod.getPos()) || hitResult.getBlockPos().equals(pod.getCampfirePos()))) {
            ItemStack stack = player.getStackInHand(hand);
            if (isBatteryPack(stack)) {
                if (pod.getFuelPercent() >= 100.0f) {
                    player.sendMessage(Text.literal("The Transmitter Pod is already fully charged!").formatted(Formatting.YELLOW), true);
                    return ActionResult.CONSUME;
                }
                stack.decrement(1);
                if (world instanceof ServerWorld serverWorld) {
                    pod.addFuel(25.0f, player.getPos(), serverWorld);
                }
                player.sendMessage(Text.literal("⚡ Installed Emergency Battery! +25% Fuel (" + (int) pod.getFuelPercent() + "%)").formatted(Formatting.GREEN, Formatting.BOLD), true);
                if (this.bountyManager != null) {
                    this.bountyManager.onFuelHarvested(player.getUuid(), 25);
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.FAIL;
        }

        return ActionResult.PASS;
    }

    private static boolean isBatteryPack(ItemStack stack) {
        return stack != null && stack.isOf(Items.REDSTONE_BLOCK) && stack.getName().getString().contains("Battery");
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (EmergencyFlareItem.isFlare(stack)) {
            stack.decrement(1);
            EmergencyFlareItem.use(player, this);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public boolean isBlockProtected(ServerWorld world, BlockPos pos) {
        if (pos == null) return false;
        TransmitterPod pod = this.podManager.getCurrentPod();
        if (pod != null) {
            BlockPos basePos = pod.getPos();
            if (basePos != null) {
                // Protect Lodestone base and Campfire transmitter
                if (pos.equals(basePos) || pos.equals(pod.getCampfirePos())) {
                    return true;
                }
                // Protect Supply Crate
                if (pod.getCratePos() != null && pos.equals(pod.getCratePos())) {
                    return true;
                }
                // Protect 3x3 foundation platform directly beneath the lodestone
                if (pos.getY() == basePos.getY() - 1
                    && Math.abs(pos.getX() - basePos.getX()) <= 1
                    && Math.abs(pos.getZ() - basePos.getZ()) <= 1) {
                    return true;
                }
            }
        }
        // Protect any campfire or lodestone during Horde Survival from explosions
        if (world != null) {
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (state.isOf(Blocks.LODESTONE)
                || state.isOf(Blocks.CAMPFIRE)
                || state.isOf(Blocks.SOUL_CAMPFIRE)) {
                return true;
            }
        }
        return false;
    }
}
