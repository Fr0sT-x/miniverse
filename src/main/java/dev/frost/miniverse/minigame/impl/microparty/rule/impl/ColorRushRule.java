package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.map.MapPosition;
import dev.frost.miniverse.map.editor.RegionPart;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMapConfig;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ColorRushRule implements MicroRule {
    private final Random random = new Random();
    private MicroPartyMapConfig.ColorZone targetZone;
    private final List<MicroPartyMapConfig.ColorZone> activeZones = new ArrayList<>();

    private static final String[] COLOR_NAMES = {"RED", "BLUE", "GREEN", "YELLOW"};

    private static BlockState[] getColorBlocks() {
        return new BlockState[] {
            Blocks.RED_CONCRETE.getDefaultState(),
            Blocks.BLUE_CONCRETE.getDefaultState(),
            Blocks.LIME_CONCRETE.getDefaultState(),
            Blocks.YELLOW_CONCRETE.getDefaultState()
        };
    }

    @Override
    public String id() {
        return "color_rush";
    }

    @Override
    public String name() {
        return "Color Rush";
    }

    @Override
    public String description() {
        return "Rush to the designated colored zone on the arena floor.";
    }

    @Override
    public Text title() {
        String colorName = targetZone != null ? targetZone.color() : "COLOR";
        Formatting fmt = formatForColor(colorName);
        return Text.literal("RUN TO " + colorName + "!").formatted(fmt, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        String colorName = targetZone != null ? targetZone.color() : "the designated zone";
        return Text.literal("Stand inside the " + colorName + " area!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.0;
    }

    @Override
    public boolean isApplicable(MicroPartyMapConfig mapConfig) {
        return true;
    }

    @Override
    public void onPrepare(MicroPartyMinigame game, MinecraftServer server) {
        this.activeZones.clear();
        this.targetZone = null;

        List<MicroPartyMapConfig.ColorZone> staticZones = game.getMapConfig().colorZones();
        if (!staticZones.isEmpty()) {
            this.activeZones.addAll(staticZones);
            this.targetZone = this.activeZones.get(random.nextInt(this.activeZones.size()));
            return;
        }

        // Procedural floor generation for flat arena platforms
        ServerWorld world = game.getWorld();
        if (world == null) {
            return;
        }

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        int surfaceY = floorY - 1;
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());

        int cx = bounds.centerX();
        int cz = bounds.centerZ();

        boolean fullQuadrantDanceFloor = random.nextInt(100) < 30; // 30% chance for full dance-floor grid

        if (fullQuadrantDanceFloor) {
            this.generateQuadrantDanceFloor(game, world, bounds, surfaceY, floorY, cx, cz);
        } else {
            this.generateDistinctColorPads(game, world, bounds, surfaceY, floorY, cx, cz);
        }

        if (!this.activeZones.isEmpty()) {
            this.targetZone = this.activeZones.get(random.nextInt(this.activeZones.size()));
        }
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        if (this.targetZone == null) {
            onPrepare(game, server);
        }
    }

    private void generateDistinctColorPads(MicroPartyMinigame game, ServerWorld world, MicroPartyArenaHelper.ArenaBounds2D bounds, int surfaceY, int floorY, int cx, int cz) {
        int halfW = Math.max(4, bounds.width() / 4);
        int halfD = Math.max(4, bounds.depth() / 4);

        int[][] quadrantOffsets = {
            {-halfW, -halfD}, // NW -> RED
            { halfW, -halfD}, // NE -> BLUE
            {-halfW,  halfD}, // SW -> GREEN
            { halfW,  halfD}  // SE -> YELLOW
        };

        BlockState[] colorBlocks = getColorBlocks();
        for (int i = 0; i < 4; i++) {
            String color = COLOR_NAMES[i];
            BlockState block = colorBlocks[i];

            int padCx = cx + quadrantOffsets[i][0] + (random.nextInt(3) - 1);
            int padCz = cz + quadrantOffsets[i][1] + (random.nextInt(3) - 1);

            // Differing pad size each time (e.g. 3x3 to 6x6)
            int radX = 1 + random.nextInt(3); // width 3 to 5
            int radZ = 1 + random.nextInt(3); // depth 3 to 5

            int pMinX = Math.max(bounds.minX() + 1, padCx - radX);
            int pMaxX = Math.min(bounds.maxX() - 1, padCx + radX);
            int pMinZ = Math.max(bounds.minZ() + 1, padCz - radZ);
            int pMaxZ = Math.min(bounds.maxZ() - 1, padCz + radZ);

            boolean placedAny = false;
            for (int x = pMinX; x <= pMaxX; x++) {
                for (int z = pMinZ; z <= pMaxZ; z++) {
                    BlockPos surfacePos = new BlockPos(x, surfaceY, z);
                    BlockPos airPos = new BlockPos(x, floorY, z);
                    if (!world.getBlockState(surfacePos).isAir() && world.getBlockState(airPos).isAir()) {
                        game.getBlockManager().setTemporaryBlock(world, surfacePos, block);
                        placedAny = true;
                    }
                }
            }

            if (placedAny) {
                RegionPart region = new RegionPart(
                    new MapPosition(pMinX, surfaceY, pMinZ, 0, 0),
                    new MapPosition(pMaxX, floorY + 2, pMaxZ, 0, 0)
                );
                this.activeZones.add(new MicroPartyMapConfig.ColorZone("auto_" + color.toLowerCase(), color, List.of(region)));
            }
        }
    }

    private void generateQuadrantDanceFloor(MicroPartyMinigame game, ServerWorld world, MicroPartyArenaHelper.ArenaBounds2D bounds, int surfaceY, int floorY, int cx, int cz) {
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                BlockPos surfacePos = new BlockPos(x, surfaceY, z);
                BlockPos airPos = new BlockPos(x, floorY, z);
                if (world.getBlockState(surfacePos).isAir() || !world.getBlockState(airPos).isAir()) {
                    continue;
                }

                int colorIdx;
                if (x < cx && z < cz) {
                    colorIdx = 0; // NW -> RED
                } else if (x >= cx && z < cz) {
                    colorIdx = 1; // NE -> BLUE
                } else if (x < cx && z >= cz) {
                    colorIdx = 2; // SW -> GREEN
                } else {
                    colorIdx = 3; // SE -> YELLOW
                }

                BlockState[] colorBlocks = getColorBlocks();
                game.getBlockManager().setTemporaryBlock(world, surfacePos, colorBlocks[colorIdx]);
            }
        }

        // Add 4 quadrant regions
        this.activeZones.add(new MicroPartyMapConfig.ColorZone("quad_red", "RED", List.of(new RegionPart(
            new MapPosition(bounds.minX(), surfaceY, bounds.minZ(), 0, 0),
            new MapPosition(cx - 1, floorY + 2, cz - 1, 0, 0)
        ))));
        this.activeZones.add(new MicroPartyMapConfig.ColorZone("quad_blue", "BLUE", List.of(new RegionPart(
            new MapPosition(cx, surfaceY, bounds.minZ(), 0, 0),
            new MapPosition(bounds.maxX(), floorY + 2, cz - 1, 0, 0)
        ))));
        this.activeZones.add(new MicroPartyMapConfig.ColorZone("quad_green", "GREEN", List.of(new RegionPart(
            new MapPosition(bounds.minX(), surfaceY, cz, 0, 0),
            new MapPosition(cx - 1, floorY + 2, bounds.maxZ(), 0, 0)
        ))));
        this.activeZones.add(new MicroPartyMapConfig.ColorZone("quad_yellow", "YELLOW", List.of(new RegionPart(
            new MapPosition(cx, surfaceY, cz, 0, 0),
            new MapPosition(bounds.maxX(), floorY + 2, bounds.maxZ(), 0, 0)
        ))));
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        if (this.targetZone == null || remainingTicks % 5 != 0) {
            return;
        }

        ServerWorld world = game.getWorld();
        if (world == null) return;

        // Visual cue particles around the target zone
        for (RegionPart r : this.targetZone.regions()) {
            double midX = (r.min().x() + r.max().x()) / 2.0 + 0.5;
            double midZ = (r.min().z() + r.max().z()) / 2.0 + 0.5;
            double y = r.min().y() + 1.2;

            Formatting fmt = formatForColor(this.targetZone.color());
            if (fmt == Formatting.RED) {
                world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.1f, 0.1f), 1.2f), midX, y, midZ, 4, 1.5, 0.2, 1.5, 0.0);
            } else if (fmt == Formatting.BLUE) {
                world.spawnParticles(new DustParticleEffect(new Vector3f(0.1f, 0.4f, 1.0f), 1.2f), midX, y, midZ, 4, 1.5, 0.2, 1.5, 0.0);
            } else if (fmt == Formatting.GREEN) {
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, midX, y, midZ, 4, 1.5, 0.2, 1.5, 0.0);
            } else {
                world.spawnParticles(new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.1f), 1.2f), midX, y, midZ, 4, 1.5, 0.2, 1.5, 0.0);
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        if (targetZone == null) {
            return true;
        }

        Vec3d pos = player.getPos();
        for (RegionPart region : targetZone.regions()) {
            if (regionContains(region, pos)) {
                return true;
            }
        }

        // Also check block directly under feet
        ServerWorld world = game.getWorld();
        if (world != null) {
            BlockPos underFeet = BlockPos.ofFloored(pos.x, pos.y - 0.2, pos.z);
            BlockState underState = world.getBlockState(underFeet);
            BlockState[] colorBlocks = getColorBlocks();
            for (int i = 0; i < COLOR_NAMES.length; i++) {
                if (COLOR_NAMES[i].equalsIgnoreCase(targetZone.color()) && underState.isOf(colorBlocks[i].getBlock())) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        targetZone = null;
        activeZones.clear();
        if (game.getWorld() != null) {
            game.getBlockManager().restoreAll(game.getWorld());
        }
    }

    private static boolean regionContains(RegionPart r, Vec3d pos) {
        double minX = Math.min(r.min().x(), r.max().x());
        double maxX = Math.max(r.min().x(), r.max().x()) + 1.0;
        double minY = Math.min(r.min().y(), r.max().y()) - 0.5;
        double maxY = Math.max(r.min().y(), r.max().y()) + 2.5;
        double minZ = Math.min(r.min().z(), r.max().z());
        double maxZ = Math.max(r.min().z(), r.max().z()) + 1.0;
        return pos.x >= minX && pos.x <= maxX &&
               pos.y >= minY && pos.y <= maxY &&
               pos.z >= minZ && pos.z <= maxZ;
    }

    private static Formatting formatForColor(String color) {
        return switch (color.toUpperCase()) {
            case "RED" -> Formatting.RED;
            case "BLUE" -> Formatting.BLUE;
            case "GREEN" -> Formatting.GREEN;
            case "YELLOW" -> Formatting.YELLOW;
            case "PURPLE" -> Formatting.LIGHT_PURPLE;
            case "ORANGE" -> Formatting.GOLD;
            case "AQUA", "CYAN" -> Formatting.AQUA;
            default -> Formatting.WHITE;
        };
    }
}
