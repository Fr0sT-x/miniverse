package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class ColorRouletteRule implements MicroRule {
    private static final String[] COLOR_NAMES = {"RED", "BLUE", "LIME", "YELLOW"};

    private final Random random = new Random();
    private final Map<BlockPos, String> cornerColors = new HashMap<>();
    private final Set<UUID> doomedPlayers = new HashSet<>();
    private String doomedColor = "RED";
    private boolean struck = false;

    @Override
    public String id() {
        return "color_roulette";
    }

    @Override
    public String name() {
        return "Color Roulette";
    }

    @Override
    public String description() {
        return "Pick a colored corner! One random corner will be struck by lightning!";
    }

    @Override
    public Text title() {
        return Text.literal("COLOR ROULETTE!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Pick a corner! One will be struck!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 8;
    }

    @Override
    public double minDurationSeconds() {
        return 5.0;
    }

    private static BlockState getBlockForColor(String color) {
        return switch (color) {
            case "BLUE" -> Blocks.BLUE_CONCRETE.getDefaultState();
            case "LIME" -> Blocks.LIME_CONCRETE.getDefaultState();
            case "YELLOW" -> Blocks.YELLOW_CONCRETE.getDefaultState();
            default -> Blocks.RED_CONCRETE.getDefaultState();
        };
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.cornerColors.clear();
        this.doomedPlayers.clear();
        this.struck = false;
        this.doomedColor = COLOR_NAMES[random.nextInt(COLOR_NAMES.length)];

        ServerWorld world = game.getWorld();
        if (world == null) return;

        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        int surfaceY = floorY - 1;
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());

        int cx = (bounds.minX() + bounds.maxX()) / 2;
        int cz = (bounds.minZ() + bounds.maxZ()) / 2;

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                BlockPos surfacePos = new BlockPos(x, surfaceY, z);
                BlockPos abovePos = new BlockPos(x, floorY, z);

                if (world.getBlockState(surfacePos).isAir() || !world.getBlockState(abovePos).isAir()) {
                    continue;
                }

                String color;
                if (x < cx && z < cz) {
                    color = "RED";
                } else if (x >= cx && z < cz) {
                    color = "BLUE";
                } else if (x < cx && z >= cz) {
                    color = "LIME";
                } else {
                    color = "YELLOW";
                }

                BlockState state = getBlockForColor(color);
                game.getBlockManager().setTemporaryBlock(world, surfacePos, state);
                this.cornerColors.put(surfacePos.toImmutable(), color);
            }
        }
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        // Ticking audio cue during last 1.5 seconds
        if (remainingTicks <= 30 && remainingTicks > 1 && remainingTicks % 6 == 0) {
            float pitch = 1.0f + (30 - remainingTicks) * 0.04f;
            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.8f, pitch);
            }
        }

        // Final strike at remainingTicks == 1
        if (remainingTicks == 1 && !this.struck) {
            this.struck = true;

            int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());

            // Detonate / strike the doomed corner blocks
            for (Map.Entry<BlockPos, String> entry : this.cornerColors.entrySet()) {
                if (entry.getValue().equals(this.doomedColor)) {
                    BlockPos pos = entry.getKey();
                    if (random.nextInt(3) == 0) {
                        world.spawnParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
                        world.spawnParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.05);
                    }
                }
            }

            for (ServerPlayerEntity p : game.getLivingPlayers()) {
                p.playSoundToPlayer(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.0f, 1.2f);
                p.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 0.8f, 1.2f);
                p.networkHandler.sendPacket(new OverlayMessageS2CPacket(Text.literal("§c§l⚡ " + this.doomedColor + " WAS STRUCK!").formatted(Formatting.RED)));

                // Check if player is standing on the doomed corner
                BlockPos below = p.getBlockPos().down();
                BlockPos feet = p.getBlockPos();
                String col = this.cornerColors.get(below);
                if (col == null) col = this.cornerColors.get(feet);

                if (this.doomedColor.equals(col)) {
                    this.doomedPlayers.add(p.getUuid());
                    p.sendMessage(Text.literal("§c§l💥 STRUCK BY LIGHTNING!"), true);
                    p.setVelocity(new Vec3d(0, 0.6, 0));
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                }
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        if (this.doomedPlayers.contains(player.getUuid())) {
            return false;
        }

        BlockPos below = player.getBlockPos().down();
        BlockPos feet = player.getBlockPos();
        String col = this.cornerColors.get(below);
        if (col == null) col = this.cornerColors.get(feet);

        // Player must be standing in one of the safe corner colors
        return col != null && !col.equals(this.doomedColor);
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.cornerColors.clear();
        this.doomedPlayers.clear();
        this.struck = false;
    }
}
