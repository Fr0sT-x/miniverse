package dev.frost.miniverse.minigame.impl.skywars;

import dev.frost.miniverse.chat.ChatRoutingAware;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.death.DeathAwareMinigame;
import dev.frost.miniverse.minigame.core.death.DeathLifecycleManager;
import dev.frost.miniverse.minigame.core.event.BlockBreakBypassAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.loot.ChestLootTable;
import dev.frost.miniverse.minigame.core.loot.ChestRefillModule;
import dev.frost.miniverse.minigame.core.loot.SkywarsLootTables;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.spectator.SpectatorService;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamRole;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkywarsMinigame extends AbstractMinigame implements
    DeathAwareMinigame,
    SpawnPointAware,
    ServerTickAware,
    PersistentMinigame,
    BlockBreakBypassAware,
    TeamManagerProvider,
    ChatRoutingAware,
    DynamicParticipantMinigame {

    private SkywarsSettings settings = SkywarsSettings.defaults();
    private SkywarsMapConfig mapConfig = SkywarsMapConfig.empty();
    private DeathLifecycleManager deathLifecycleManager;

    private final TeamManager teamManager = new TeamManager();

    private ScoreboardTemplate scoreboard;
    private ScoreboardLine modeLine;
    private ScoreboardLine teamsAliveLine;
    private ScoreboardLine playersAliveLine;
    private ScoreboardLine refillLine;
    private ScoreboardLine killsLine;

    private final Set<ServerPlayerEntity> aliveParticipants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> playerKills = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastDamager = new ConcurrentHashMap<>();
    private final Set<BlockPos> cageBlocks = new HashSet<>();
    private final Map<String, MapPosition> teamSpawns = new ConcurrentHashMap<>();

    private ChestRefillModule chestRefillModule;
    private boolean cagesActive = false;
    private int cageTicksRemaining = 0;
    private int fallImmunityTicks = 0;
    private int gameTicks = 0;
    private GameState state = GameState.WAITING_FOR_PLAYERS;

    public void applySettings(SkywarsSettings settings, String preSerializedMapConfig) {
        this.settings = settings == null ? SkywarsSettings.defaults() : settings;
        this.mapConfig = SkywarsMapConfig.load(SkywarsDefinition.ID, preSerializedMapConfig);
    }

    public boolean canStartMatch() {
        return this.context.roster().size() >= 2;
    }

    @Override
    public String getName() {
        return SkywarsDefinition.DISPLAY_NAME;
    }

    public dev.frost.miniverse.minigame.core.MinigameContext getContext() {
        return this.context;
    }

    @Override
    public DeathLifecycleManager getDeathLifecycleManager() {
        return this.deathLifecycleManager;
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
    public boolean isTeamBased() {
        return true;
    }

    @Override
    public TeamManager teamManager() {
        return this.teamManager;
    }

    @Override
    public boolean isChatRoutingEnabled() {
        return this.settings.teamChatEnabled();
    }

    public void ensureTeamAssignment(ServerPlayerEntity player, String team) {
        String teamId = team.toLowerCase().replaceAll("[^a-z0-9]", "_");
        this.teamManager.assign(player, teamId, team, TeamRole.MEMBER);
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String team, String role) {
        if (team != null && !team.isBlank()) {
            ensureTeamAssignment(player, team);
        }
    }

    @Override
    public boolean canBuild() {
        return !this.cagesActive && this.state == GameState.RUNNING;
    }

    @Override
    public boolean canBreakBlocks() {
        return !this.cagesActive && this.state == GameState.RUNNING;
    }

    @Override
    public boolean canBypassProtection(ServerPlayerEntity player, BlockPos pos) {
        // Disallow breaking map blocks; player-placed blocks are allowed via MapProtectionManager
        return false;
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand) {
        if (this.cagesActive) {
            return ActionResult.FAIL;
        }
        return ActionResult.PASS;
    }

    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }

    private int nextSpawnIndex = 0;

    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        if (this.mapConfig.waitingLobby().isPresent()) {
            MapPosition lobby = this.mapConfig.waitingLobby().get();
            player.teleport(player.getServerWorld(), lobby.x(), lobby.y(), lobby.z(), Set.of(), lobby.yaw(), lobby.pitch());
            return;
        }

        String teamId = this.teamManager.teamId(player.getUuid());
        if (teamId != null && this.teamSpawns.containsKey(teamId)) {
            MapPosition pos = this.teamSpawns.get(teamId);
            player.teleport(player.getServerWorld(), pos.x(), pos.y(), pos.z(), Set.of(), pos.yaw(), pos.pitch());
            return;
        }

        List<MapPosition> spawns = this.mapConfig.islandSpawns();
        if (spawns.isEmpty()) return;
        MapPosition spawn = spawns.get((this.nextSpawnIndex++) % spawns.size());
        player.teleport(player.getServerWorld(), spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(GameRules.KEEP_INVENTORY, false);
        this.applyVanillaGameRule(GameRules.DO_IMMEDIATE_RESPAWN, true);
        this.applyVanillaGameRule(GameRules.FALL_DAMAGE, true);
        this.applyVanillaGameRule(GameRules.DO_DAYLIGHT_CYCLE, false);
        this.applyVanillaGameRule(GameRules.NATURAL_REGENERATION, true);

        this.deathLifecycleManager = new DeathLifecycleManager(new SkywarsDeathConfig(this), SpectatorService.getInstance());
        this.playerKills.clear();
        this.lastDamager.clear();
        this.cageBlocks.clear();
        this.teamSpawns.clear();
        this.fallImmunityTicks = 0;
        this.gameTicks = 0;
    }

    @Override
    protected void onMatchStart() {
        this.aliveParticipants.clear();
        this.aliveParticipants.addAll(this.context.liveParticipants());
        this.playerKills.clear();
        this.lastDamager.clear();
        this.cageBlocks.clear();
        this.teamSpawns.clear();
        this.fallImmunityTicks = 0;
        this.gameTicks = 0;

        if (this.getVanillaTeams() != null) {
            this.getVanillaTeams().setFriendlyFireAllowed(false);
            this.syncVanillaTeams();
        }

        ServerWorld world = this.context.nullableServer() != null ? this.context.nullableServer().getOverworld() : null;

        List<MapPosition> availableSpawns = new ArrayList<>(this.mapConfig.islandSpawns());
        if (availableSpawns.isEmpty()) {
            availableSpawns.add(MapPosition.of(0.0, 100.0, 0.0));
        }
        Collections.shuffle(availableSpawns);

        // Group alive participants by team so teammates always spawn on the same island
        Map<String, List<ServerPlayerEntity>> teamMembers = new LinkedHashMap<>();
        for (ServerPlayerEntity player : this.aliveParticipants) {
            String teamId = this.teamManager.teamId(player.getUuid());
            if (teamId == null || teamId.isBlank()) {
                teamId = "solo_" + player.getUuid();
            }
            teamMembers.computeIfAbsent(teamId, k -> new ArrayList<>()).add(player);
        }

        int spawnIndex = 0;
        for (Map.Entry<String, List<ServerPlayerEntity>> entry : teamMembers.entrySet()) {
            String teamId = entry.getKey();
            List<ServerPlayerEntity> members = entry.getValue();

            MapPosition teamSpawn = availableSpawns.get(spawnIndex % availableSpawns.size());
            spawnIndex++;
            this.teamSpawns.put(teamId, teamSpawn);

            BlockPos baseOrigin = BlockPos.ofFloored(teamSpawn.x(), teamSpawn.y(), teamSpawn.z());

            for (int i = 0; i < members.size(); i++) {
                ServerPlayerEntity player = members.get(i);
                player.getInventory().clear();
                player.changeGameMode(GameMode.SURVIVAL);
                player.setHealth(20.0F);
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0F);

                BlockPos cageOrigin = getTeammateOffset(baseOrigin, i);
                player.teleport(player.getServerWorld(), cageOrigin.getX() + 0.5, cageOrigin.getY(), cageOrigin.getZ() + 0.5, Set.of(), teamSpawn.yaw(), teamSpawn.pitch());

                if (world != null) {
                    buildCage(world, cageOrigin);
                }
            }
        }

        // Also track any pre-built cages at unassigned spawns so they open as well
        if (world != null) {
            for (MapPosition spawn : availableSpawns) {
                if (!this.teamSpawns.containsValue(spawn)) {
                    BlockPos unassignedOrigin = BlockPos.ofFloored(spawn.x(), spawn.y(), spawn.z());
                    for (int m = 0; m < 4; m++) {
                        cleanPreBuiltCage(world, getTeammateOffset(unassignedOrigin, m));
                    }
                }
            }
        }

        this.cagesActive = true;
        this.cageTicksRemaining = this.settings.cageTimerSeconds() * 20;

        // Initialize loot tables and refill module
        boolean insane = this.settings.isInsaneMode();
        ChestLootTable islandTable = SkywarsLootTables.island(insane);
        ChestLootTable midTable = SkywarsLootTables.mid(insane);

        this.chestRefillModule = this.getOrRegisterModule(ChestRefillModule.class, () ->
            new ChestRefillModule(
                world,
                this.mapConfig.islandChests(),
                this.mapConfig.midChests(),
                islandTable,
                midTable,
                () -> this.aliveParticipants,
                this.settings.refillIntervalSeconds(),
                5
            )
        );

        // Pre-fill all chests immediately
        this.chestRefillModule.populateAll(true);

        // Scoreboard initialization
        this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () ->
            new ScoreboardTemplate(SkywarsDefinition.ID + "_obj", Text.literal("SKYWARS").formatted(Formatting.GOLD, Formatting.BOLD))
        );
        this.modeLine = this.scoreboard.addLine(Text.literal("Mode: ").formatted(Formatting.GRAY).append(Text.literal(insane ? "Insane" : "Normal").formatted(insane ? Formatting.RED : Formatting.GREEN)));
        this.teamsAliveLine = this.scoreboard.addLine(Text.literal("Teams Alive: " + getAliveTeamCount()).formatted(Formatting.WHITE));
        this.playersAliveLine = this.scoreboard.addLine(Text.literal("Players Alive: " + this.aliveParticipants.size()).formatted(Formatting.WHITE));
        this.refillLine = this.scoreboard.addLine(Text.literal("Next Refill: ").formatted(Formatting.GRAY).append(Text.literal(formatTime(this.settings.refillIntervalSeconds())).formatted(Formatting.YELLOW)));
        this.killsLine = this.scoreboard.addLine(Text.literal("Your Kills: 0").formatted(Formatting.WHITE));

        this.scoreboard.show(this.context.liveParticipants());
    }

    private BlockPos getTeammateOffset(BlockPos base, int memberIndex) {
        return switch (memberIndex) {
            case 0 -> base;
            case 1 -> base.add(2, 0, 0);
            case 2 -> base.add(-2, 0, 0);
            case 3 -> base.add(0, 0, 2);
            default -> base.add(0, 0, -2 * (memberIndex - 3));
        };
    }

    private void buildCage(ServerWorld world, BlockPos origin) {
        // Floor block directly beneath the player's feet
        BlockPos floor = origin.down();
        if (world.getBlockState(floor).isAir()) {
            world.setBlockState(floor, Blocks.GLASS.getDefaultState());
        }
        this.cageBlocks.add(floor);

        // Ensure origin (feet) and origin.up() (head) are clear of obstruction
        if (!world.getBlockState(origin).isAir()) {
            this.cageBlocks.add(origin);
            world.setBlockState(origin, Blocks.AIR.getDefaultState());
        }
        if (!world.getBlockState(origin.up()).isAir()) {
            this.cageBlocks.add(origin.up());
            world.setBlockState(origin.up(), Blocks.AIR.getDefaultState());
        }

        // Roof
        BlockPos roof = origin.up(2);
        if (world.getBlockState(roof).isAir()) {
            world.setBlockState(roof, Blocks.GLASS.getDefaultState());
        }
        this.cageBlocks.add(roof);

        // Cage walls (cardinals and diagonals for full enclosure)
        BlockPos[] wallOffsets = {
            origin.north(), origin.south(), origin.east(), origin.west(),
            origin.north().east(), origin.north().west(), origin.south().east(), origin.south().west(),
            origin.up().north(), origin.up().south(), origin.up().east(), origin.up().west(),
            origin.up().north().east(), origin.up().north().west(), origin.up().south().east(), origin.up().south().west()
        };

        for (BlockPos wallPos : wallOffsets) {
            if (world.getBlockState(wallPos).isAir()) {
                world.setBlockState(wallPos, Blocks.GLASS.getDefaultState());
            }
            this.cageBlocks.add(wallPos);
        }
    }

    private void cleanPreBuiltCage(ServerWorld world, BlockPos origin) {
        if (world == null) return;
        BlockPos floor = origin.down();
        BlockPos roof = origin.up(2);
        BlockPos[] cagePositions = {
            floor, origin, origin.up(), roof,
            origin.north(), origin.south(), origin.east(), origin.west(),
            origin.north().east(), origin.north().west(), origin.south().east(), origin.south().west(),
            origin.up().north(), origin.up().south(), origin.up().east(), origin.up().west(),
            origin.up().north().east(), origin.up().north().west(), origin.up().south().east(), origin.up().south().west()
        };

        for (BlockPos pos : cagePositions) {
            net.minecraft.block.BlockState state = world.getBlockState(pos);
            if (isCageBlock(state)) {
                this.cageBlocks.add(pos);
            }
        }
    }

    private boolean isCageBlock(net.minecraft.block.BlockState state) {
        if (state.isAir()) return false;
        net.minecraft.block.Block block = state.getBlock();
        if (state.isOf(Blocks.BARRIER)) return true;
        if (block instanceof net.minecraft.block.PaneBlock) return true;
        return block instanceof net.minecraft.block.TransparentBlock && !state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES);
    }

    private void removeCages(MinecraftServer server) {
        this.cagesActive = false;
        this.fallImmunityTicks = 40; // 2 seconds of fall damage immunity
        ServerWorld world = server.getOverworld();
        for (BlockPos pos : this.cageBlocks) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            world.spawnParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.05);
        }
        this.cageBlocks.clear();

        for (ServerPlayerEntity player : this.aliveParticipants) {
            player.fallDistance = 0.0F;
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("CAGES OPENED!").formatted(Formatting.GREEN, Formatting.BOLD)));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Fight to the death!").formatted(Formatting.YELLOW)));
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 0.7F, 1.2F);
        }
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        if (this.getState() != GameState.RUNNING) {
            return;
        }

        // Handle pre-match cage countdown
        if (this.cagesActive) {
            for (ServerPlayerEntity player : this.aliveParticipants) {
                player.getHungerManager().setFoodLevel(20);
                player.getHungerManager().setSaturationLevel(20.0F);
            }

            this.cageTicksRemaining--;
            int secondsLeft = this.cageTicksRemaining / 20;

            if (this.cageTicksRemaining > 0 && this.cageTicksRemaining % 20 == 0) {
                if (secondsLeft == 10 || (secondsLeft <= 5 && secondsLeft > 0)) {
                    Formatting color = secondsLeft <= 3 ? Formatting.RED : Formatting.YELLOW;
                    float pitch = 0.5F + (10 - secondsLeft) * 0.1F;

                    for (ServerPlayerEntity player : this.aliveParticipants) {
                        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(String.valueOf(secondsLeft)).formatted(color, Formatting.BOLD)));
                        player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Cages opening in " + secondsLeft + "s...").formatted(Formatting.GRAY)));
                        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0F, pitch);
                    }
                }
            }

            if (this.cageTicksRemaining <= 0) {
                this.removeCages(server);
            }
            return;
        }

        if (this.fallImmunityTicks > 0) {
            this.fallImmunityTicks--;
            for (ServerPlayerEntity player : this.aliveParticipants) {
                player.fallDistance = 0.0F;
            }
        }

        this.gameTicks++;

        // Time limit check
        if (this.gameTicks >= this.settings.timeLimitSeconds() * 20) {
            if (this.context.nullableServer() != null && MinigameManager.getInstance().getRuntime() != null) {
                MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                    MinigameManager.getInstance().getRuntime(),
                    MatchEndResult.winners(Set.of(), Text.literal("Time Limit Reached!")),
                    MatchLifecycleOptions.defaults(getName())
                );
            }
            return;
        }

        // Void elimination check
        double voidY = this.mapConfig.voidLevel();
        List<ServerPlayerEntity> voidCasualties = new ArrayList<>();
        for (ServerPlayerEntity player : this.aliveParticipants) {
            if (player.getY() <= voidY) {
                voidCasualties.add(player);
            }
        }
        for (ServerPlayerEntity player : voidCasualties) {
            this.eliminateVoidPlayer(player);
        }

        // Chest refill module tick
        if (this.chestRefillModule != null) {
            this.chestRefillModule.tick();
        }

        // Update scoreboard display
        if (this.gameTicks % 20 == 0) {
            this.updateScoreboard();
        }
    }

    private void eliminateVoidPlayer(ServerPlayerEntity player) {
        if (!this.aliveParticipants.contains(player)) {
            return;
        }

        UUID killerId = this.lastDamager.get(player.getUuid());
        ServerPlayerEntity killer = killerId != null && this.context.nullableServer() != null
            ? this.context.nullableServer().getPlayerManager().getPlayer(killerId)
            : null;

        if (killer != null && this.aliveParticipants.contains(killer) && !killer.equals(player)) {
            int kills = this.playerKills.merge(killer.getUuid(), 1, Integer::sum);
            this.broadcast(Text.literal("☠ ").formatted(Formatting.RED)
                .append(player.getName().copy().formatted(Formatting.WHITE))
                .append(Text.literal(" was knocked into the void by ").formatted(Formatting.GRAY))
                .append(killer.getName().copy().formatted(Formatting.WHITE))
                .append(Text.literal(". (" + kills + " kills)").formatted(Formatting.GOLD)));
        } else {
            this.broadcast(Text.literal("☠ ").formatted(Formatting.RED)
                .append(player.getName().copy().formatted(Formatting.WHITE))
                .append(Text.literal(" fell into the void.").formatted(Formatting.GRAY)));
        }

        this.onPlayerEliminated(player, true);
    }

    public void onPlayerEliminated(ServerPlayerEntity player, boolean fromVoid) {
        this.aliveParticipants.remove(player);
        this.lastDamager.remove(player.getUuid());

        ServerWorld world = this.context.nullableServer() != null ? this.context.nullableServer().getOverworld() : null;
        if (world != null) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 0.6F, 1.4F);
        }

        if (fromVoid) {
            player.getInventory().clear();
            SpectatorService.getInstance().startSpectating(
                player,
                dev.frost.miniverse.minigame.core.spectator.policies.SpectatorPolicies.unrestricted(),
                dev.frost.miniverse.minigame.core.spectator.SpectatorTargetProviders.roster(),
                dev.frost.miniverse.minigame.core.spectator.SpectatorMode.STANDARD,
                null,
                null,
                Text.literal("You fell into the void! Now spectating.").formatted(Formatting.GRAY)
            );
            Vec3d specPos = getSpectatorSpawnPos();
            player.teleport(world, specPos.x, specPos.y, specPos.z, Set.of(), 0, 0);
        }

        this.updateScoreboard();
        this.checkWinCondition();
    }

    public void checkWinCondition() {
        if (this.getState() != GameState.RUNNING) return;

        // Collect all distinct teams among remaining alive participants
        Set<String> aliveTeamIds = new HashSet<>();
        for (ServerPlayerEntity p : this.aliveParticipants) {
            String tid = this.teamManager.teamId(p.getUuid());
            aliveTeamIds.add(tid != null && !tid.isBlank() ? tid : ("solo_" + p.getUuid()));
        }

        if (aliveTeamIds.size() == 1) {
            String winningTeamId = aliveTeamIds.iterator().next();
            String teamLabel = this.teamManager.snapshots().stream()
                .filter(s -> s.id().equals(winningTeamId))
                .map(dev.frost.miniverse.team.TeamSnapshot::label)
                .findFirst()
                .orElse(winningTeamId);

            // All live participants of the winning team are winners
            Set<ServerPlayerEntity> teamWinners = new HashSet<>();
            for (ServerPlayerEntity p : this.context.liveParticipants()) {
                String tid = this.teamManager.teamId(p.getUuid());
                if (winningTeamId.equals(tid) || winningTeamId.equals("solo_" + p.getUuid())) {
                    teamWinners.add(p);
                }
            }

            int teamKills = 0;
            for (ServerPlayerEntity p : teamWinners) {
                teamKills += this.playerKills.getOrDefault(p.getUuid(), 0);
            }

            Text endSubtitle = Text.literal(teamLabel).formatted(Formatting.YELLOW)
                .append(Text.literal(" won the match!").formatted(Formatting.GOLD));

            this.broadcast(Text.literal("═══════════════════════════════════════").formatted(Formatting.GOLD));
            this.broadcast(Text.literal("               SKYWARS                 ").formatted(Formatting.YELLOW, Formatting.BOLD));
            this.broadcast(Text.literal("  Winner: ").formatted(Formatting.GRAY)
                .append(Text.literal(teamLabel).formatted(Formatting.YELLOW, Formatting.BOLD)));
            this.broadcast(Text.literal("  Team Kills: ").formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(teamKills)).formatted(Formatting.GREEN)));
            this.broadcast(Text.literal("═══════════════════════════════════════").formatted(Formatting.GOLD));

            MatchLifecycleOptions options = MatchLifecycleOptions.defaults(getName())
                .withEndTitles(
                    Text.literal("VICTORY!").formatted(Formatting.GOLD, Formatting.BOLD),
                    Text.literal("GAME OVER").formatted(Formatting.RED, Formatting.BOLD)
                );

            if (MinigameManager.getInstance().getRuntime() != null) {
                MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                    MinigameManager.getInstance().getRuntime(),
                    MatchEndResult.winners(teamWinners, endSubtitle),
                    options
                );
            }
        } else if (this.aliveParticipants.isEmpty() || aliveTeamIds.isEmpty()) {
            if (MinigameManager.getInstance().getRuntime() != null) {
                MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                    MinigameManager.getInstance().getRuntime(),
                    MatchEndResult.winners(Set.of(), Text.literal("Nobody survived the void!")),
                    MatchLifecycleOptions.defaults(getName())
                );
            }
        }
    }

    private void updateScoreboard() {
        if (this.teamsAliveLine != null) {
            this.teamsAliveLine.setText(Text.literal("Teams Alive: " + getAliveTeamCount()).formatted(Formatting.WHITE));
            this.teamsAliveLine.updateAll();
        }

        if (this.playersAliveLine != null) {
            this.playersAliveLine.setText(Text.literal("Players Alive: " + this.aliveParticipants.size()).formatted(Formatting.WHITE));
            this.playersAliveLine.updateAll();
        }

        if (this.refillLine != null && this.chestRefillModule != null) {
            int refillSec = this.chestRefillModule.getSecondsUntilNextRefill();
            this.refillLine.setText(Text.literal("Next Refill: ").formatted(Formatting.GRAY)
                .append(Text.literal(formatTime(refillSec)).formatted(Formatting.YELLOW)));
            this.refillLine.updateAll();
        }
    }

    private int getAliveTeamCount() {
        Set<String> aliveTeamIds = new HashSet<>();
        for (ServerPlayerEntity p : this.aliveParticipants) {
            String tid = this.teamManager.teamId(p.getUuid());
            aliveTeamIds.add(tid != null && !tid.isBlank() ? tid : ("solo_" + p.getUuid()));
        }
        return aliveTeamIds.size();
    }

    private void broadcast(Text message) {
        if (this.context.nullableServer() != null) {
            for (ServerPlayerEntity p : this.context.nullableServer().getPlayerManager().getPlayerList()) {
                p.sendMessage(message, false);
            }
        }
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public Vec3d getSpectatorSpawnPos() {
        if (this.mapConfig.waitingLobby().isPresent()) {
            MapPosition lobby = this.mapConfig.waitingLobby().get();
            return new Vec3d(lobby.x(), lobby.y(), lobby.z());
        }

        List<MapPosition> spawns = this.mapConfig.islandSpawns();
        if (spawns.isEmpty()) {
            return new Vec3d(0.0, 100.0, 0.0);
        }

        double sumX = 0;
        double sumZ = 0;
        for (MapPosition spawn : spawns) {
            sumX += spawn.x();
            sumZ += spawn.z();
        }
        return new Vec3d(sumX / spawns.size(), 100.0, sumZ / spawns.size());
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.cagesActive) {
            return false;
        }
        if (this.fallImmunityTicks > 0 && source.isOf(DamageTypes.FALL)) {
            return false;
        }
        if (source.getAttacker() instanceof ServerPlayerEntity attacker && !attacker.equals(player)) {
            String playerTeam = this.teamManager.teamId(player.getUuid());
            String attackerTeam = this.teamManager.teamId(attacker.getUuid());
            if (playerTeam != null && playerTeam.equals(attackerTeam)) {
                return false;
            }
            this.lastDamager.put(player.getUuid(), attacker.getUuid());
        }
        return true;
    }

    @Override
    protected void onMatchEnd() {
        super.onMatchEnd();
        if (this.context != null && this.context.nullableServer() != null && !this.cageBlocks.isEmpty()) {
            ServerWorld world = this.context.nullableServer().getOverworld();
            for (BlockPos pos : this.cageBlocks) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
            this.cageBlocks.clear();
        }
        this.teamSpawns.clear();
    }
}
