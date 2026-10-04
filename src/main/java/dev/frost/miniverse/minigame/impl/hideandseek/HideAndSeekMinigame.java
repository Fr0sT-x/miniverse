package dev.frost.miniverse.minigame.impl.hideandseek;

import dev.frost.miniverse.chat.ChatRoutingAware;
import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.minigame.core.AbstractMinigame;
import dev.frost.miniverse.minigame.core.DynamicParticipantMinigame;
import dev.frost.miniverse.minigame.core.GameState;
import dev.frost.miniverse.minigame.core.MinigameManager;
import dev.frost.miniverse.minigame.core.PersistentMinigame;
import dev.frost.miniverse.minigame.core.event.BlockAttackAware;
import dev.frost.miniverse.minigame.core.event.BlockBreakBypassAware;
import dev.frost.miniverse.minigame.core.event.ItemUseAware;
import dev.frost.miniverse.minigame.core.event.ItemUseOnBlockAware;
import dev.frost.miniverse.minigame.core.event.ServerTickAware;
import dev.frost.miniverse.minigame.core.event.SpawnPointAware;
import dev.frost.miniverse.minigame.core.lifecycle.MatchEndResult;
import dev.frost.miniverse.minigame.core.lifecycle.MatchLifecycleOptions;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardLine;
import dev.frost.miniverse.minigame.core.scoreboard.ScoreboardTemplate;
import dev.frost.miniverse.minigame.core.freeze.FreezeReason;
import dev.frost.miniverse.minigame.core.freeze.FreezeService;
import dev.frost.miniverse.minigame.core.item.ProtectedItemRule;
import dev.frost.miniverse.minigame.core.item.ProtectedItemService;
import dev.frost.miniverse.minigame.core.item.ProtectedItemTags;
import dev.frost.miniverse.minigame.impl.hideandseek.audio.PassiveSoundEmitter;
import dev.frost.miniverse.minigame.impl.hideandseek.combat.HideAndSeekCombatManager;
import dev.frost.miniverse.minigame.impl.hideandseek.combat.HideAndSeekSonarManager;
import dev.frost.miniverse.minigame.impl.hideandseek.combat.HideAndSeekTauntManager;
import dev.frost.miniverse.minigame.impl.hideandseek.disguise.BlockDisguiseManager;
import dev.frost.miniverse.minigame.impl.hideandseek.disguise.DisguiseSelectGui;
import dev.frost.miniverse.minigame.impl.hideandseek.disguise.DisguiseType;
import dev.frost.miniverse.minigame.impl.hideandseek.tracker.SeekerDisguiseTracker;
import dev.frost.miniverse.team.TeamManager;
import dev.frost.miniverse.team.TeamManagerProvider;
import dev.frost.miniverse.team.TeamRole;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamDescriptor;
import dev.frost.miniverse.minigame.core.vanilla.VanillaTeamOptions;
import dev.frost.miniverse.minigame.core.protection.BlockProtectionProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HideAndSeekMinigame extends AbstractMinigame implements
    TeamManagerProvider,
    BlockBreakBypassAware,
    BlockAttackAware,
    ItemUseAware,
    ItemUseOnBlockAware,
    ServerTickAware,
    SpawnPointAware,
    DynamicParticipantMinigame,
    PersistentMinigame,
    ChatRoutingAware,
    BlockProtectionProvider {

    public static final String TEAM_HIDERS = "hiders";
    public static final String TEAM_SEEKERS = "seekers";
    public static final String TAG_SEEKER_STICK = "hideandseek:seeker_stick";
    public static final String TAG_HIDER_DECOY = "hideandseek:decoy";

    public record DecoyBlock(UUID ownerUuid, BlockPos pos, BlockState replacedState, long placedTime) {}

    private HideAndSeekSettings settings = HideAndSeekSettings.defaults();
    private HideAndSeekMapConfig mapConfig = HideAndSeekMapConfig.empty();

    private final TeamManager teamManager = new TeamManager();
    private BlockDisguiseManager disguiseManager;
    private HideAndSeekCombatManager combatManager;
    private HideAndSeekTauntManager tauntManager;
    private final HideAndSeekSonarManager sonarManager = new HideAndSeekSonarManager();
    private SeekerDisguiseTracker disguiseTracker;
    private PassiveSoundEmitter soundEmitter;

    private ScoreboardTemplate scoreboard;
    private ScoreboardLine phaseLine;
    private ScoreboardLine hidersLine;
    private ScoreboardLine seekersLine;
    private ScoreboardLine pointsLine;

    private final Set<ServerPlayerEntity> aliveParticipants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ServerPlayerEntity> playerMap = new ConcurrentHashMap<>();
    private final Map<BlockPos, DecoyBlock> activeDecoys = new ConcurrentHashMap<>();
    private final Set<UUID> recordedHiders = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> frozenSeekerTicks = new ConcurrentHashMap<>();

    private boolean inGracePeriod = true;
    private int gracePeriodTicksRemaining = 0;
    private int gameTicks = 0;
    private boolean sonarDistributed = false;
    private GameState state = GameState.WAITING_FOR_PLAYERS;
    private List<DisguiseType> activeDisguises = DisguiseType.ALL;

    public void applySettings(HideAndSeekSettings settings, String preSerializedMapConfig) {
        this.applySettings(settings, preSerializedMapConfig, "");
    }

    public void applySettings(HideAndSeekSettings settings, String preSerializedMapConfig, String customDisguiseBlocks) {
        this.settings = settings == null ? HideAndSeekSettings.defaults() : settings;
        this.mapConfig = HideAndSeekMapConfig.load(HideAndSeekDefinition.ID, preSerializedMapConfig);

        List<String> blockList = new ArrayList<>();
        if (customDisguiseBlocks != null && !customDisguiseBlocks.isBlank()) {
            for (String part : customDisguiseBlocks.split(",")) {
                if (!part.isBlank()) blockList.add(part.trim());
            }
        }
        if (blockList.isEmpty() && this.mapConfig.disguiseBlocks() != null && !this.mapConfig.disguiseBlocks().isEmpty()) {
            blockList.addAll(this.mapConfig.disguiseBlocks());
        }

        if (!blockList.isEmpty()) {
            List<DisguiseType> parsed = new ArrayList<>();
            for (String blockStr : blockList) {
                parsed.add(DisguiseType.fromId(blockStr));
            }
            this.activeDisguises = parsed;
        } else {
            this.activeDisguises = DisguiseType.ALL;
        }
    }

    @Override
    public String getName() {
        return HideAndSeekDefinition.DISPLAY_NAME;
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
        return false;
    }

    public void ensureTeamAssignment(ServerPlayerEntity player, String team) {
        String teamId = team.equalsIgnoreCase(TEAM_SEEKERS) ? TEAM_SEEKERS : TEAM_HIDERS;
        String label = teamId.equals(TEAM_SEEKERS) ? "Seekers" : "Hiders";
        this.teamManager.assign(player, teamId, label, TeamRole.MEMBER);
    }

    @Override
    public void addParticipantMidGame(ServerPlayerEntity player, String team, String role) {
        // Late joiners become Seekers
        this.ensureTeamAssignment(player, TEAM_SEEKERS);
        if (this.state == GameState.RUNNING) {
            this.aliveParticipants.add(player);
            this.playerMap.put(player.getUuid(), player);
            player.changeGameMode(GameMode.SURVIVAL);
            equipSeeker(player);
            if (this.inGracePeriod) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, this.gracePeriodTicksRemaining + 40, 1, false, false, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, this.gracePeriodTicksRemaining + 40, 4, false, false, false));
                teleportToSeekerSpawn(player);
            } else {
                teleportToHiderSpawn(player);
            }
            if (this.disguiseTracker != null) {
                this.disguiseTracker.updateAll(getAliveSeekers(), getAliveHiders());
            }
        }
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
    public boolean canBypassProtection(ServerPlayerEntity player, BlockPos pos) {
        return false;
    }

    @Override
    public dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState checkProgression(dev.frost.miniverse.minigame.core.SessionRoster roster) {
        return dev.frost.miniverse.minigame.core.lifecycle.MatchProgressionValidator.ProgressionState.valid();
    }

    @Override
    public void teleportToSpawn(ServerPlayerEntity player) {
        if (this.mapConfig.waitingLobby().isPresent()) {
            MapPosition lobby = this.mapConfig.waitingLobby().get();
            player.teleport(player.getServerWorld(), lobby.x(), lobby.y(), lobby.z(), Set.of(), lobby.yaw(), lobby.pitch());
            return;
        }
        teleportToHiderSpawn(player);
    }

    private void teleportToHiderSpawn(ServerPlayerEntity player) {
        List<MapPosition> spawns = this.mapConfig.hiderSpawns();
        if (spawns.isEmpty()) return;
        MapPosition spawn = spawns.get(Math.abs(player.getUuid().hashCode()) % spawns.size());
        player.teleport(player.getServerWorld(), spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
    }

    private void teleportToSeekerSpawn(ServerPlayerEntity player) {
        List<MapPosition> spawns = this.mapConfig.seekerSpawns();
        if (spawns.isEmpty()) {
            teleportToHiderSpawn(player);
            return;
        }
        MapPosition spawn = spawns.get(Math.abs(player.getUuid().hashCode()) % spawns.size());
        player.teleport(player.getServerWorld(), spawn.x(), spawn.y(), spawn.z(), Set.of(), spawn.yaw(), spawn.pitch());
    }

    @Override
    public void initialize() {
        this.applyVanillaGameRule(GameRules.KEEP_INVENTORY, true);
        this.applyVanillaGameRule(GameRules.DO_IMMEDIATE_RESPAWN, true);
        this.applyVanillaGameRule(GameRules.FALL_DAMAGE, false);
        this.applyVanillaGameRule(GameRules.DO_DAYLIGHT_CYCLE, false);
        this.applyVanillaGameRule(GameRules.NATURAL_REGENERATION, false);

        this.aliveParticipants.clear();
        this.playerMap.clear();
        this.activeDecoys.clear();
        this.recordedHiders.clear();
        this.frozenSeekerTicks.clear();
        this.sonarManager.clear();
        this.gameTicks = 0;
        this.inGracePeriod = true;
        this.sonarDistributed = false;
    }

    @Override
    protected void onMatchStart() {
        this.aliveParticipants.clear();
        this.aliveParticipants.addAll(this.context.liveParticipants());
        this.playerMap.clear();
        for (ServerPlayerEntity p : this.aliveParticipants) {
            this.playerMap.put(p.getUuid(), p);
        }

        ServerWorld world = this.context.nullableServer() != null ? this.context.nullableServer().getOverworld() : null;
        if (world == null) return;
        world.getGameRules().get(GameRules.FALL_DAMAGE).set(false, world.getServer());

        this.disguiseManager = new BlockDisguiseManager(world, this.settings.solidifyDelaySeconds());
        this.disguiseManager.setAvailableDisguises(this.activeDisguises);
        this.combatManager = new HideAndSeekCombatManager(
            world,
            this.disguiseManager,
            this.settings,
            uuid -> TEAM_SEEKERS.equals(this.teamManager.teamId(uuid)),
            uuid -> TEAM_HIDERS.equals(this.teamManager.teamId(uuid)),
            this::checkAndBreakDecoy
        );
        this.tauntManager = new HideAndSeekTauntManager(world, this.settings.tauntCooldownSeconds());
        this.disguiseTracker = new SeekerDisguiseTracker(this.disguiseManager, this.settings.hotbarCycleSeconds());
        this.disguiseTracker.initialize();
        this.sonarManager.initialize();
        this.soundEmitter = new PassiveSoundEmitter(world, this.settings.passiveSoundIntervalSeconds(), this.settings.passiveSoundLateGameSeconds());

        ProtectedItemService.getInstance().registerRule(
            ProtectedItemRule.builder(TAG_SEEKER_STICK)
                .preventDrop()
                .preventExternalStorage()
                .allowOffhandSwap(false)
                .allowRearrange(true)
                .build()
        );
        ProtectedItemService.getInstance().registerRule(
            ProtectedItemRule.builder(TAG_HIDER_DECOY)
                .preventDrop()
                .preventExternalStorage()
                .allowOffhandSwap(false)
                .allowRearrange(true)
                .build()
        );

        // Setup Vanilla Teams with friendly fire disabled
        if (this.getVanillaTeams() != null) {
            this.getVanillaTeams().setFriendlyFireAllowed(false);
        }

        // Assign Seekers & Hiders if not assigned
        List<ServerPlayerEntity> players = new ArrayList<>(this.aliveParticipants);
        Collections.shuffle(players);

        int seekersNeeded = Math.max(1, this.settings.seekerCount());
        int seekersAssigned = 0;

        for (ServerPlayerEntity p : players) {
            String tid = this.teamManager.teamId(p.getUuid());
            if (TEAM_SEEKERS.equalsIgnoreCase(tid)) {
                seekersAssigned++;
            }
        }

        if (seekersAssigned == 0) {
            for (int i = 0; i < seekersNeeded && i < players.size(); i++) {
                this.ensureTeamAssignment(players.get(i), TEAM_SEEKERS);
            }
            for (int i = seekersNeeded; i < players.size(); i++) {
                this.ensureTeamAssignment(players.get(i), TEAM_HIDERS);
            }
        }

        this.syncVanillaTeams();

        // Initialize players
        this.inGracePeriod = true;
        this.gracePeriodTicksRemaining = this.settings.hidingTimeSeconds() * 20;

        for (ServerPlayerEntity player : this.aliveParticipants) {
            player.getInventory().clear();
            player.changeGameMode(GameMode.SURVIVAL);
            player.setHealth(20.0F);
            player.getHungerManager().setFoodLevel(20);
            player.getHungerManager().setSaturationLevel(20.0F);
            player.getHungerManager().setExhaustion(0.0F);

            if (isSeeker(player)) {
                // Seekers: Blindness + Slowness in holding area
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, this.gracePeriodTicksRemaining + 40, 1, false, false, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, this.gracePeriodTicksRemaining + 40, 4, false, false, false));
                teleportToSeekerSpawn(player);
                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("YOU ARE A SEEKER").formatted(Formatting.RED, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Wait for hiders to scatter...").formatted(Formatting.YELLOW)));
            } else {
                // Hiders: Speed II + Disguise Item + Decoy Item + Taunt Item
                this.recordedHiders.add(player.getUuid());
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, this.gracePeriodTicksRemaining, 1, false, false, false));
                teleportToHiderSpawn(player);

                // Give Disguise Selector in slot 0, Baton in slot 1, Decoy in slot 2, Taunt in slot 8
                ItemStack selector = new ItemStack(Items.ENCHANTED_BOOK);
                selector.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Choose Disguise §7(Right-Click)").formatted(Formatting.GREEN, Formatting.BOLD));
                player.getInventory().setStack(0, selector);

                ItemStack baton = new ItemStack(Items.WOODEN_SHOVEL);
                baton.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Hider's Baton §7(Knockback)").formatted(Formatting.YELLOW, Formatting.BOLD));
                player.getInventory().setStack(1, baton);

                player.getInventory().setStack(2, createDecoyItem());
                player.getInventory().setStack(8, HideAndSeekTauntManager.createTauntItem());

                // Default disguise & open selection GUI immediately
                this.disguiseManager.applyDisguise(player, this.disguiseManager.getDefaultDisguise());
                DisguiseSelectGui.open(player, this.disguiseManager);

                player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("YOU ARE A HIDER").formatted(Formatting.GREEN, Formatting.BOLD)));
                player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Find a hiding spot!").formatted(Formatting.YELLOW)));
            }
        }

        // Initialize Scoreboard
        this.scoreboard = this.getOrRegisterModule(ScoreboardTemplate.class, () ->
            new ScoreboardTemplate(HideAndSeekDefinition.ID + "_obj", Text.literal("HIDE AND SEEK").formatted(Formatting.YELLOW, Formatting.BOLD))
        );
        this.phaseLine = this.scoreboard.addLine(Text.literal("Hiding Time: " + this.settings.hidingTimeSeconds() + "s").formatted(Formatting.GOLD));
        this.hidersLine = this.scoreboard.addLine(Text.literal("Hiders Left: " + getHiderCount()).formatted(Formatting.GREEN));
        this.seekersLine = this.scoreboard.addLine(Text.literal("Seekers: " + getSeekerCount()).formatted(Formatting.RED));
        this.pointsLine = this.scoreboard.addLine(Text.literal("Your Points: 0").formatted(Formatting.WHITE));

        this.scoreboard.show(this.context.liveParticipants());
    }

    public static ItemStack createSeekerStick() {
        ItemStack stick = new ItemStack(Items.STICK);
        stick.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Seeker's Stick").formatted(Formatting.RED, Formatting.BOLD));
        stick.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Whack blocks to discover hidden hiders!").formatted(Formatting.GRAY),
            Text.literal("Deals 9 Attack Damage (Iron Axe equivalent)").formatted(Formatting.DARK_RED)
        )));

        AttributeModifiersComponent modifiers = AttributeModifiersComponent.builder()
            .add(
                EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(
                    Identifier.of("miniverse", "seeker_stick_damage"),
                    8.0,
                    EntityAttributeModifier.Operation.ADD_VALUE
                ),
                AttributeModifierSlot.MAINHAND
            )
            .build();
        stick.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers);
        ProtectedItemTags.mark(stick, TAG_SEEKER_STICK, false, true, true);
        return stick;
    }

    public static ItemStack createDecoyItem() {
        ItemStack decoy = new ItemStack(Items.ARMOR_STAND);
        decoy.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Block Decoy §7(Right-Click)").formatted(Formatting.AQUA, Formatting.BOLD));
        decoy.set(DataComponentTypes.LORE, new LoreComponent(List.of(
            Text.literal("Places a stationary clone of your disguised block").formatted(Formatting.GRAY),
            Text.literal("at your feet and grants Speed II to slip away.").formatted(Formatting.GRAY),
            Text.literal("Tricks seekers who strike it! §e(+25 Pts)").formatted(Formatting.YELLOW),
            Text.literal("Single-use only").formatted(Formatting.RED)
        )));
        ProtectedItemTags.mark(decoy, TAG_HIDER_DECOY, false, true, true);
        return decoy;
    }

    private boolean triggerDecoy(ServerPlayerEntity player) {
        if (!isHider(player)) {
            return false;
        }

        if (this.inGracePeriod) {
            player.sendMessage(Text.literal("§cWait for seekers to be released before using your decoy!"), true);
            player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5F, 1.2F);
            return false;
        }

        ServerWorld world = player.getServerWorld();
        BlockPos playerPos = player.getBlockPos();

        // If the hider is currently solidified in the world, unsolidify them first
        if (this.disguiseManager != null && this.disguiseManager.isSolidified(player.getUuid())) {
            this.disguiseManager.forceUnlock(player.getUuid());
        }

        // Target placement block position
        BlockPos targetPos = playerPos;
        BlockState currentState = world.getBlockState(targetPos);
        if (!currentState.isAir() && !currentState.isReplaceable()) {
            if (world.getBlockState(targetPos.up()).isAir() || world.getBlockState(targetPos.up()).isReplaceable()) {
                targetPos = targetPos.up();
                currentState = world.getBlockState(targetPos);
            }
        }

        DisguiseType disguise = this.disguiseManager != null ? this.disguiseManager.getDisguise(player.getUuid()) : DisguiseType.CRAFTING_TABLE;
        BlockState decoyState = disguise.blockState();

        // Save decoy and place physical block in world
        this.activeDecoys.put(targetPos, new DecoyBlock(player.getUuid(), targetPos, currentState, System.currentTimeMillis()));
        world.setBlockState(targetPos, decoyState, Block.NOTIFY_ALL);

        // Visual and auditory feedback
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.2F, 1.2F);
        world.playSound(null, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1.0F, 0.8F);
        world.spawnParticles(ParticleTypes.POOF, targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5, 20, 0.35, 0.35, 0.35, 0.05);

        // Escape buffs: Speed II (3s) + Invisibility (1.5s)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 1, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 30, 0, false, false, true));

        // Consume the decoy item from main inventory
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (ProtectedItemTags.hasType(s, TAG_HIDER_DECOY)) {
                s.decrement(1);
                break;
            }
        }

        player.sendMessage(Text.literal("✦ DECOY DEPLOYED! Speed boost active — flee!").formatted(Formatting.AQUA, Formatting.BOLD), true);
        return true;
    }

    private boolean checkAndBreakDecoy(ServerPlayerEntity seeker, BlockPos pos) {
        DecoyBlock decoy = this.activeDecoys.remove(pos);
        if (decoy == null) {
            return false;
        }

        ServerWorld world = seeker.getServerWorld();
        BlockState originalState = decoy.replacedState() != null ? decoy.replacedState() : Blocks.AIR.getDefaultState();
        world.setBlockState(pos, originalState, Block.NOTIFY_ALL);

        // Poof sound and particles
        world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2F, 1.4F);
        world.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1.0F, 0.8F);
        world.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 25, 0.4, 0.4, 0.4, 0.08);
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.04);

        seeker.sendMessage(Text.literal("✦ That was a FAKE DECOY! You got tricked!").formatted(Formatting.GOLD, Formatting.BOLD), true);
        seeker.playSoundToPlayer(SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 0.8F, 1.6F);

        // Award points to hider who placed it
        if (this.tauntManager != null) {
            this.tauntManager.addPoints(decoy.ownerUuid(), 25);
        }
        ServerPlayerEntity owner = this.playerMap.get(decoy.ownerUuid());
        if (owner != null && !owner.isDisconnected()) {
            owner.sendMessage(Text.literal("✦ A Seeker whacked your Decoy! (+25 Points)").formatted(Formatting.GREEN, Formatting.BOLD), false);
            owner.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8F, 1.4F);
        }

        return true;
    }

    private void equipSeeker(ServerPlayerEntity player) {
        player.getInventory().clear();
        player.changeGameMode(GameMode.SURVIVAL);
        player.removeStatusEffect(StatusEffects.SLOWNESS);

        player.getInventory().setStack(0, createSeekerStick());

        // Dyed red leather armor
        net.minecraft.component.type.DyedColorComponent redDye = new net.minecraft.component.type.DyedColorComponent(0xCC2222, false);
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        helmet.set(DataComponentTypes.DYED_COLOR, redDye);
        player.getInventory().setStack(39, helmet);

        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
        chestplate.set(DataComponentTypes.DYED_COLOR, redDye);
        player.getInventory().setStack(38, chestplate);

        ItemStack leggings = new ItemStack(Items.LEATHER_LEGGINGS);
        leggings.set(DataComponentTypes.DYED_COLOR, redDye);
        player.getInventory().setStack(37, leggings);

        ItemStack boots = new ItemStack(Items.LEATHER_BOOTS);
        boots.set(DataComponentTypes.DYED_COLOR, redDye);
        player.getInventory().setStack(36, boots);

        if (this.sonarDistributed) {
            player.getInventory().setStack(7, HideAndSeekSonarManager.createSonarShard());
        }

        player.playSoundToPlayer(SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    protected void onGameTick(MinecraftServer server) {
        if (this.getState() != GameState.RUNNING) {
            return;
        }

        // Lock hunger bar to full for all participants (hunger bar never depletes, natural regen is off)
        if (server.getTicks() % 10 == 0) {
            for (ServerPlayerEntity p : this.aliveParticipants) {
                p.getHungerManager().setFoodLevel(20);
                p.getHungerManager().setSaturationLevel(20.0F);
                p.getHungerManager().setExhaustion(0.0F);
            }
        }

        // Tick Disguise Manager every tick (solidification, sneak timer, movement check)
        if (this.disguiseManager != null) {
            this.disguiseManager.tick(this.playerMap);
        }

        // Tick Seeker Respawn Freeze countdown (3 seconds)
        if (!this.frozenSeekerTicks.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = this.frozenSeekerTicks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                int remaining = entry.getValue() - 1;
                ServerPlayerEntity p = this.playerMap.get(entry.getKey());
                if (remaining <= 0) {
                    it.remove();
                    if (p != null && !p.isDisconnected()) {
                        FreezeService.getInstance().unfreeze(p, FreezeReason.SEEKER_RESPAWN);
                        p.sendMessage(Text.literal("⚔ Seek and destroy all hiders!").formatted(Formatting.RED, Formatting.BOLD), true);
                        p.playSoundToPlayer(SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 1.0F, 1.2F);
                        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("GO!").formatted(Formatting.GREEN, Formatting.BOLD)));
                        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Hunt down remaining hiders!").formatted(Formatting.YELLOW)));
                    }
                } else {
                    entry.setValue(remaining);
                    if (p != null && !p.isDisconnected()) {
                        if (remaining == 40 || remaining == 20) {
                            int sec = remaining / 20;
                            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Release in " + sec + "s...").formatted(Formatting.YELLOW)));
                            p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8F, 1.0F + (3 - sec) * 0.2F);
                        }
                    }
                }
            }
        }

        // Handle Grace Period Countdown
        if (this.inGracePeriod) {
            this.gracePeriodTicksRemaining--;
            int secondsLeft = this.gracePeriodTicksRemaining / 20;

            if (this.gracePeriodTicksRemaining > 0 && this.gracePeriodTicksRemaining % 20 == 0) {
                if (secondsLeft == 30 || secondsLeft == 15 || secondsLeft <= 5) {
                    Formatting color = secondsLeft <= 3 ? Formatting.RED : Formatting.YELLOW;
                    for (ServerPlayerEntity p : this.aliveParticipants) {
                        if (isSeeker(p)) {
                            p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(String.valueOf(secondsLeft)).formatted(color, Formatting.BOLD)));
                            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Release in " + secondsLeft + "s...").formatted(Formatting.GRAY)));
                        } else {
                            p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Seekers release in " + secondsLeft + "s!").formatted(color)));
                        }
                        p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0F, 0.8F + (30 - secondsLeft) * 0.02F);
                    }
                }
            }

            if (this.gracePeriodTicksRemaining <= 0) {
                this.releaseSeekers();
            }
            return;
        }

        this.gameTicks++;

        // Passive Seeker stamina/health regeneration (every 30 ticks / 1.5 seconds)
        if (this.gameTicks % 30 == 0 && this.combatManager != null && !this.inGracePeriod) {
            this.combatManager.tickPassiveRegen(getAliveSeekers());
        }

        // Periodic passive points for surviving hiders (every 15 seconds)
        if (this.gameTicks % 300 == 0 && this.tauntManager != null) {
            for (ServerPlayerEntity hider : getAliveHiders()) {
                this.tauntManager.addPoints(hider.getUuid(), 10);
                hider.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.4F, 1.4F);
            }
        }

        // Proximity heartbeat for hiders when seekers are near (The Hive signature feature)
        if (!this.inGracePeriod && this.gameTicks % 4 == 0) {
            List<ServerPlayerEntity> seekers = getAliveSeekers();
            if (!seekers.isEmpty()) {
                for (ServerPlayerEntity hider : getAliveHiders()) {
                    double minDistanceSq = Double.MAX_VALUE;
                    for (ServerPlayerEntity seeker : seekers) {
                        double distSq = hider.squaredDistanceTo(seeker);
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                        }
                    }
                    double minDistance = Math.sqrt(minDistanceSq);
                    if (minDistance <= 8.0) {
                        int interval = minDistance <= 3.0 ? 8 : (minDistance <= 5.5 ? 16 : 24);
                        if (this.gameTicks % interval == 0) {
                            float pitch = 1.0F + (float) ((8.0 - minDistance) / 8.0) * 0.4F;
                            hider.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.3F, pitch);
                        }
                    }
                }
            }
        }

        // Sonar Radar unlock during final minute
        int totalDurationTicks = this.settings.gameDurationSeconds() * 20;
        int ticksRemaining = totalDurationTicks - this.gameTicks;
        int secondsRemaining = Math.max(0, ticksRemaining / 20);

        if (secondsRemaining <= this.settings.sonarUnlockSeconds() && !this.sonarDistributed) {
            this.distributeSonar();
        }

        // Tick Seeker Disguise Tracker (inventory roster & slot 8 cycling)
        if (this.disguiseTracker != null && !this.inGracePeriod) {
            this.disguiseTracker.tick(this.gameTicks, getAliveSeekers(), getAliveHiders());
        }

        // Tick Passive Sound Emitter (locational audio clues & action-bar alerts)
        if (this.soundEmitter != null) {
            this.soundEmitter.tick(this.gameTicks, secondsRemaining, getAliveHiders(), this.inGracePeriod);
        }

        // Time limit reached -> Hiders win!
        if (this.gameTicks >= totalDurationTicks) {
            this.endMatchWithWinners(TEAM_HIDERS);
            return;
        }

        // Update Scoreboard every second
        if (this.gameTicks % 20 == 0) {
            this.updateScoreboard(secondsRemaining);
        }
    }

    private void releaseSeekers() {
        this.inGracePeriod = false;

        ServerWorld world = this.context.nullableServer() != null ? this.context.nullableServer().getOverworld() : null;

        for (ServerPlayerEntity p : this.aliveParticipants) {
            if (isSeeker(p)) {
                equipSeeker(p);
                p.removeStatusEffect(StatusEffects.BLINDNESS);
                p.removeStatusEffect(StatusEffects.SLOWNESS);
                teleportToHiderSpawn(p);
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("THE HUNT BEGINS!").formatted(Formatting.RED, Formatting.BOLD)));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Find and eliminate all hiders!").formatted(Formatting.YELLOW)));
            } else {
                // Hiders: remove selector book from slot 0
                ItemStack slot0 = p.getInventory().getStack(0);
                if (slot0.isOf(Items.ENCHANTED_BOOK)) {
                    p.getInventory().setStack(0, ItemStack.EMPTY);
                }
                p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("SEEKERS RELEASED!").formatted(Formatting.RED, Formatting.BOLD)));
                p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Blend into the map and stay hidden!").formatted(Formatting.YELLOW)));
            }

            if (world != null) {
                world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
        }

        if (this.disguiseTracker != null) {
            this.disguiseTracker.updateAll(getAliveSeekers(), getAliveHiders());
        }
    }

    private void distributeSonar() {
        this.sonarDistributed = true;
        this.broadcast(Text.literal("⚡ SEEKERS RECEIVED ECHO SHARD! Right-click to listen for hider resonance!").formatted(Formatting.AQUA, Formatting.BOLD));

        for (ServerPlayerEntity seeker : getAliveSeekers()) {
            seeker.getInventory().setStack(7, HideAndSeekSonarManager.createSonarShard());
            seeker.playSoundToPlayer(SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.PLAYERS, 1.0F, 1.2F);
        }
    }

    @Override
    public ActionResult onAttackBlock(ServerPlayerEntity player, World world, Hand hand, BlockPos pos, Direction direction) {
        if (this.combatManager != null) {
            return this.combatManager.onAttackBlock(player, pos, this.inGracePeriod, this.playerMap);
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseBlock(ServerPlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        ItemStack stack = player.getStackInHand(hand);
        if (ProtectedItemTags.hasType(stack, TAG_HIDER_DECOY) && isHider(player)) {
            triggerDecoy(player);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, World world, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        // Hider Block Decoy
        if (ProtectedItemTags.hasType(stack, TAG_HIDER_DECOY) && isHider(player)) {
            triggerDecoy(player);
            return ActionResult.SUCCESS;
        }

        // Disguise selector book
        if (stack.isOf(Items.ENCHANTED_BOOK) && this.inGracePeriod && isHider(player)) {
            if (this.disguiseManager != null) {
                DisguiseSelectGui.open(player, this.disguiseManager);
            }
            return ActionResult.SUCCESS;
        }

        // Taunt Note Block
        if (stack.isOf(Items.NOTE_BLOCK) && !this.inGracePeriod && isHider(player)) {
            if (this.tauntManager != null) {
                this.tauntManager.tryTaunt(player);
            }
            return ActionResult.SUCCESS;
        }

        // Disguise Radar Hotbar Slot 8 (Manual cycle on right-click)
        if (ProtectedItemTags.hasType(stack, SeekerDisguiseTracker.TAG_DISGUISE_RADAR) && isSeeker(player)) {
            if (this.disguiseTracker != null) {
                this.disguiseTracker.handleManualCycle(player, getAliveHiders());
            }
            return ActionResult.SUCCESS;
        }

        // Echo Resonance Shard
        if ((stack.isOf(Items.ECHO_SHARD) || ProtectedItemTags.hasType(stack, HideAndSeekSonarManager.TAG_SONAR_SHARD)) && this.sonarDistributed && isSeeker(player)) {
            this.sonarManager.ping(player, getAliveHiders());
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    public boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (this.inGracePeriod) {
            return false;
        }

        // Prevent fall damage and kinetic wall crash damage for all players (neither hiders nor seekers)
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.FALL) || source.isOf(net.minecraft.entity.damage.DamageTypes.FLY_INTO_WALL)) {
            return false;
        }

        // Prevent suffocation/in-wall damage while hiding inside solidified blocks
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.IN_WALL)) {
            return false;
        }

        if (source.getAttacker() instanceof ServerPlayerEntity attacker) {
            boolean attackerIsSeeker = isSeeker(attacker);
            boolean victimIsSeeker = isSeeker(player);

            if (attackerIsSeeker && victimIsSeeker) {
                return false; // Friendly fire off
            }

            if (!attackerIsSeeker && victimIsSeeker) {
                // Hider whacking a seeker with their defensive baton — knockback only
                net.minecraft.util.math.Vec3d knockbackDir = player.getPos().subtract(attacker.getPos()).normalize();
                player.takeKnockback(0.9, -knockbackDir.x, -knockbackDir.z);
                player.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0F, 1.2F);
                attacker.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0F, 1.2F);
                return false;
            }

            if (!attackerIsSeeker) {
                return false;
            }

            // Exhaustion check: If Seeker is at critical health (<= 2.0 HP / 1 heart), they are too tired to strike
            if (attacker.getHealth() <= 2.0F) {
                attacker.sendMessage(Text.literal("§c⚡ Exhausted! Catch your breath (3s to recover)...").formatted(Formatting.RED, Formatting.BOLD), true);
                attacker.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.5F, 1.2F);
                return false;
            }

            // Seeker attacking hider entity — ray-cast hit the entity instead of the block.
            // Apply the same visual/audio feedback as the block-attack path so it never feels silent.
            if (this.disguiseManager != null && this.disguiseManager.isSolidified(player.getUuid())) {
                ServerWorld hitWorld = player.getServerWorld();
                hitWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 1.0F, 1.4F);
                hitWorld.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                hitWorld.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.3, 0.3, 0.3, 0.15);
                attacker.sendMessage(Text.literal("§a✔ FOUND HIDER: §e" + player.getName().getString() + "§a! (Full Health Restored!)").formatted(Formatting.GREEN, Formatting.BOLD), true);
            } else {
                attacker.sendMessage(Text.literal("§a✔ HIT HIDER: §e" + player.getName().getString() + "§a! (Full Health Restored!)").formatted(Formatting.GREEN, Formatting.BOLD), true);
            }
            if (this.disguiseManager != null) {
                this.disguiseManager.forceUnlock(player.getUuid());
            }

            // Seeker regains full health (10 hearts / 20 HP) when finding/hitting a hider
            attacker.setHealth(attacker.getMaxHealth());

            // Check lethal blow
            if (player.getHealth() - amount <= 0.0F) {
                this.handleHiderCaught(player, attacker);
                return false; // Handled custom conversion
            }
        }

        return true;
    }

    private void handleHiderCaught(ServerPlayerEntity hider, ServerPlayerEntity seeker) {
        boolean wasLastHider = getHiderCount() <= 1;
        if (wasLastHider) {
            // Keep the caught hider at the place they were found. Removing the
            // disguise restores the map block under them before spectator mode
            // is applied, and no seeker-spawn teleport is performed.
            if (this.disguiseManager != null) {
                this.disguiseManager.removeDisguise(hider);
            }
            ServerWorld world = hider.getServerWorld();
            world.playSound(null, hider.getX(), hider.getY(), hider.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 0.7F, 1.2F);
            this.broadcast(Text.literal("☠ ").formatted(Formatting.RED)
                .append(Text.literal(hider.getName().getString()).formatted(Formatting.YELLOW))
                .append(Text.literal(" was caught by ").formatted(Formatting.GRAY))
                .append(Text.literal(seeker != null ? seeker.getName().getString() : "a Seeker").formatted(Formatting.RED))
                .append(Text.literal("! The Seekers win!").formatted(Formatting.YELLOW)));
            this.ensureTeamAssignment(hider, TEAM_SEEKERS);
            this.syncVanillaTeams();
            hider.changeGameMode(GameMode.SPECTATOR);
            this.endMatchWithWinners(TEAM_SEEKERS);
            return;
        }

        if (this.disguiseManager != null) {
            this.disguiseManager.removeDisguise(hider);
        }

        // Convert Hider to Seeker
        this.ensureTeamAssignment(hider, TEAM_SEEKERS);
        this.syncVanillaTeams();

        ServerWorld world = hider.getServerWorld();
        world.playSound(null, hider.getX(), hider.getY(), hider.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 0.7F, 1.2F);

        this.broadcast(Text.literal("☠ ").formatted(Formatting.RED)
            .append(Text.literal(hider.getName().getString()).formatted(Formatting.YELLOW))
            .append(Text.literal(" was caught by ").formatted(Formatting.GRAY))
            .append(Text.literal(seeker != null ? seeker.getName().getString() : "a Seeker").formatted(Formatting.RED))
            .append(Text.literal(" and joined the Seekers!").formatted(Formatting.YELLOW)));

        hider.setHealth(20.0F);
        equipSeeker(hider);

        // Give blindness for 3s (60 ticks) before spawning at hider spawn so they cannot see immediately
        hider.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 1, false, false, false));
        hider.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 4, false, false, false));

        // Freeze new seeker for 3s using FreezeService
        FreezeService.getInstance().freeze(hider, FreezeReason.SEEKER_RESPAWN);
        this.frozenSeekerTicks.put(hider.getUuid(), 60);

        hider.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("YOU ARE NOW A SEEKER!").formatted(Formatting.RED, Formatting.BOLD)));
        hider.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("Release in 3s...").formatted(Formatting.YELLOW)));

        // Teleport to Hider spawn point (not the enclosed seeker holding area)
        teleportToHiderSpawn(hider);

        if (this.disguiseTracker != null) {
            this.disguiseTracker.updateAll(getAliveSeekers(), getAliveHiders());
        }

    }

    private void endMatchWithWinners(String winningTeamId) {
        boolean hidersWon = TEAM_HIDERS.equals(winningTeamId);
        String label = hidersWon ? "Hiders" : "Seekers";

        if (this.disguiseManager != null) {
            this.disguiseManager.unsolidifyAll();
        }
        for (ServerPlayerEntity hider : getAliveHiders()) {
            hider.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 300, 0, false, false, false));
            ServerWorld world = hider.getServerWorld();
            world.spawnParticles(ParticleTypes.GLOW, hider.getX(), hider.getY() + 0.8, hider.getZ(), 25, 0.4, 0.4, 0.4, 0.05);
        }

        // Restore any remaining active decoy blocks to clean the world
        ServerWorld overworld = this.context.nullableServer() != null ? this.context.nullableServer().getOverworld() : null;
        if (overworld != null) {
            for (DecoyBlock decoy : this.activeDecoys.values()) {
                BlockState original = decoy.replacedState() != null ? decoy.replacedState() : Blocks.AIR.getDefaultState();
                overworld.setBlockState(decoy.pos(), original, Block.NOTIFY_ALL);
            }
        }
        this.activeDecoys.clear();

        Set<ServerPlayerEntity> winners = new HashSet<>();
        for (ServerPlayerEntity p : this.aliveParticipants) {
            if (winningTeamId.equals(this.teamManager.teamId(p.getUuid()))) {
                winners.add(p);
            }
        }

        Text endSubtitle = Text.literal(label).formatted(hidersWon ? Formatting.GREEN : Formatting.RED)
            .append(Text.literal(" won the match!").formatted(Formatting.GOLD));

        // Compile Hider Leaderboard (Highest points to Lowest points)
        record HiderScore(UUID uuid, String name, int points, boolean survived) {}
        List<HiderScore> leaderboard = new ArrayList<>();

        Set<UUID> allHiderUuids = new HashSet<>(this.recordedHiders);
        if (this.tauntManager != null) {
            allHiderUuids.addAll(this.tauntManager.getAllPoints().keySet());
        }

        for (UUID uuid : allHiderUuids) {
            ServerPlayerEntity p = this.playerMap.get(uuid);
            String name = (p != null) ? p.getName().getString() : (this.context.nullableServer() != null && this.context.nullableServer().getUserCache() != null ? this.context.nullableServer().getUserCache().getByUuid(uuid).map(com.mojang.authlib.GameProfile::getName).orElse("Hider") : "Hider");
            int pts = this.tauntManager != null ? this.tauntManager.getPoints(uuid) : 0;
            boolean survived = isHider(uuid);
            leaderboard.add(new HiderScore(uuid, name, pts, survived));
        }

        leaderboard.sort((a, b) -> Integer.compare(b.points(), a.points()));

        this.broadcast(Text.literal("═══════════════════════════════════════").formatted(Formatting.GOLD));
        this.broadcast(Text.literal("             HIDE AND SEEK             ").formatted(Formatting.YELLOW, Formatting.BOLD));
        this.broadcast(Text.literal("  Winner: ").formatted(Formatting.GRAY)
            .append(Text.literal(label).formatted(hidersWon ? Formatting.GREEN : Formatting.RED, Formatting.BOLD)));
        this.broadcast(Text.literal("  Remaining Hiders: ").formatted(Formatting.GRAY)
            .append(Text.literal(String.valueOf(getHiderCount())).formatted(Formatting.YELLOW)));

        if (!leaderboard.isEmpty()) {
            this.broadcast(Text.empty());
            this.broadcast(Text.literal("  ★ Hider Leaderboard (Points):").formatted(Formatting.AQUA, Formatting.BOLD));
            for (int i = 0; i < leaderboard.size(); i++) {
                HiderScore score = leaderboard.get(i);
                Formatting rankColor = switch (i) {
                    case 0 -> Formatting.GOLD;
                    case 1 -> Formatting.WHITE;
                    case 2 -> Formatting.YELLOW;
                    default -> Formatting.GRAY;
                };
                Text rankPrefix = Text.literal("   #" + (i + 1) + ". ").formatted(rankColor, Formatting.BOLD);
                Text nameText = Text.literal(score.name()).formatted(Formatting.WHITE);
                Text pointsText = Text.literal(" - " + score.points() + " pts").formatted(Formatting.GOLD);
                Text statusBadge = score.survived()
                    ? Text.literal(" [SURVIVOR]").formatted(Formatting.GREEN, Formatting.BOLD)
                    : Text.literal(" [CAUGHT]").formatted(Formatting.DARK_GRAY);

                this.broadcast(rankPrefix.copy().append(nameText).append(pointsText).append(statusBadge));
            }
        }
        this.broadcast(Text.literal("═══════════════════════════════════════").formatted(Formatting.GOLD));

        MatchLifecycleOptions options = MatchLifecycleOptions.defaults(getName())
            .withEndTitles(
                Text.literal("VICTORY!").formatted(Formatting.GOLD, Formatting.BOLD),
                Text.literal("GAME OVER").formatted(Formatting.RED, Formatting.BOLD)
            );

        if (MinigameManager.getInstance().getRuntime() != null) {
            MinigameManager.getInstance().getMatchLifecycleController().endMatch(
                MinigameManager.getInstance().getRuntime(),
                MatchEndResult.winners(winners, endSubtitle),
                options
            );
        }
    }

    private void updateScoreboard(int secondsRemaining) {
        if (this.phaseLine != null) {
            String timeText = formatTime(secondsRemaining);
            this.phaseLine.setText(Text.literal("Time Left: ").formatted(Formatting.GRAY).append(Text.literal(timeText).formatted(Formatting.YELLOW)));
            this.phaseLine.updateAll();
        }

        if (this.hidersLine != null) {
            this.hidersLine.setText(Text.literal("Hiders Left: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(getHiderCount())).formatted(Formatting.GREEN)));
            this.hidersLine.updateAll();
        }

        if (this.seekersLine != null) {
            this.seekersLine.setText(Text.literal("Seekers: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(getSeekerCount())).formatted(Formatting.RED)));
            this.seekersLine.updateAll();
        }

        // Per-player points — each participant sees their own total
        if (this.pointsLine != null && this.tauntManager != null) {
            for (ServerPlayerEntity p : this.aliveParticipants) {
                int pts = this.tauntManager.getPoints(p.getUuid());
                this.pointsLine.setText(Text.literal("Your Points: ").formatted(Formatting.GRAY).append(Text.literal(String.valueOf(pts)).formatted(Formatting.GOLD)));
                this.pointsLine.update(p);
            }
        }
    }

    private boolean isSeeker(ServerPlayerEntity player) {
        return TEAM_SEEKERS.equals(this.teamManager.teamId(player.getUuid()));
    }

    private boolean isHider(ServerPlayerEntity player) {
        return TEAM_HIDERS.equals(this.teamManager.teamId(player.getUuid()));
    }

    private boolean isHider(UUID uuid) {
        return TEAM_HIDERS.equals(this.teamManager.teamId(uuid));
    }

    private int getHiderCount() {
        int count = 0;
        for (ServerPlayerEntity p : this.aliveParticipants) {
            if (isHider(p)) count++;
        }
        return count;
    }

    private int getSeekerCount() {
        int count = 0;
        for (ServerPlayerEntity p : this.aliveParticipants) {
            if (isSeeker(p)) count++;
        }
        return count;
    }

    private List<ServerPlayerEntity> getAliveHiders() {
        List<ServerPlayerEntity> hiders = new ArrayList<>();
        for (ServerPlayerEntity p : this.aliveParticipants) {
            if (isHider(p)) hiders.add(p);
        }
        return hiders;
    }

    private List<ServerPlayerEntity> getAliveSeekers() {
        List<ServerPlayerEntity> seekers = new ArrayList<>();
        for (ServerPlayerEntity p : this.aliveParticipants) {
            if (isSeeker(p)) seekers.add(p);
        }
        return seekers;
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

    @Override
    protected void onMatchEnd() {
        super.onMatchEnd();
        if (this.disguiseManager != null) {
            this.disguiseManager.clearAll();
        }
        if (this.disguiseTracker != null) {
            this.disguiseTracker.clear(getAliveSeekers());
        }
        if (this.soundEmitter != null) {
            this.soundEmitter.clear();
        }
        if (this.tauntManager != null) {
            this.tauntManager.clear();
        }
        this.sonarManager.clear();
        if (this.combatManager != null) {
            this.combatManager.clear();
        }
        for (UUID uuid : this.frozenSeekerTicks.keySet()) {
            ServerPlayerEntity p = this.playerMap.get(uuid);
            if (p != null) {
                FreezeService.getInstance().unfreeze(p, FreezeReason.SEEKER_RESPAWN);
            }
        }
        this.frozenSeekerTicks.clear();
        this.playerMap.clear();
        this.recordedHiders.clear();
        ProtectedItemService.getInstance().removeRule(TAG_SEEKER_STICK);
        ProtectedItemService.getInstance().removeRule(TAG_HIDER_DECOY);

        ServerWorld overworld = this.context.nullableServer() != null ? this.context.nullableServer().getOverworld() : null;
        if (overworld != null) {
            for (DecoyBlock decoy : this.activeDecoys.values()) {
                BlockState original = decoy.replacedState() != null ? decoy.replacedState() : Blocks.AIR.getDefaultState();
                overworld.setBlockState(decoy.pos(), original, Block.NOTIFY_ALL);
            }
        }
        this.activeDecoys.clear();

        for (ServerPlayerEntity p : this.aliveParticipants) {
            p.removeStatusEffect(StatusEffects.INVISIBILITY);
            p.removeStatusEffect(StatusEffects.BLINDNESS);
            p.removeStatusEffect(StatusEffects.SLOWNESS);
            p.removeStatusEffect(StatusEffects.GLOWING);
            p.calculateDimensions();
        }
    }

    @Override
    public void onPlayerLeave(ServerPlayerEntity player) {
        super.onPlayerLeave(player);
        this.aliveParticipants.remove(player);
        this.playerMap.remove(player.getUuid());
        this.frozenSeekerTicks.remove(player.getUuid());
        FreezeService.getInstance().unfreeze(player, FreezeReason.SEEKER_RESPAWN);
        if (this.disguiseManager != null) {
            this.disguiseManager.removeDisguise(player);
        }
        if (this.disguiseTracker != null) {
            this.disguiseTracker.updateAll(getAliveSeekers(), getAliveHiders());
        }
    }

    public boolean canStartMatch() {
        return this.context != null && this.context.roster().size() >= 2 && this.mapConfig.validate().valid();
    }

    protected void syncVanillaTeams() {
        if (this.context == null || this.context.nullableServer() == null || this.getVanillaTeams() == null) {
            return;
        }

        MinecraftServer server = this.context.nullableServer();
        List<ServerPlayerEntity> hiders = new ArrayList<>();
        List<ServerPlayerEntity> seekers = new ArrayList<>();

        for (ServerPlayerEntity p : this.aliveParticipants) {
            String tid = this.teamManager.teamId(p.getUuid());
            if (TEAM_SEEKERS.equalsIgnoreCase(tid)) {
                seekers.add(p);
            } else {
                hiders.add(p);
            }
        }

        List<VanillaTeamDescriptor> descriptors = new ArrayList<>();

        VanillaTeamOptions hiderOptions = VanillaTeamOptions.defaults()
            .withColor(Formatting.GREEN)
            .withPrefix(Text.literal("[HIDER] ").formatted(Formatting.GREEN))
            .withNameTagVisibility(AbstractTeam.VisibilityRule.NEVER)
            .withShowFriendlyInvisibles(false)
            .withFriendlyFireAllowed(false);
        descriptors.add(new VanillaTeamDescriptor(TEAM_HIDERS, Text.literal("Hiders"), hiders, hiderOptions));

        VanillaTeamOptions seekerOptions = VanillaTeamOptions.defaults()
            .withColor(Formatting.RED)
            .withPrefix(Text.literal("[SEEKER] ").formatted(Formatting.RED))
            .withNameTagVisibility(AbstractTeam.VisibilityRule.ALWAYS)
            .withShowFriendlyInvisibles(false)
            .withFriendlyFireAllowed(false);
        descriptors.add(new VanillaTeamDescriptor(TEAM_SEEKERS, Text.literal("Seekers"), seekers, seekerOptions));

        this.getVanillaTeams().sync(server, descriptors);
    }

    @Override
    public boolean isBlockProtected(ServerWorld world, BlockPos pos) {
        return true;
    }
}
