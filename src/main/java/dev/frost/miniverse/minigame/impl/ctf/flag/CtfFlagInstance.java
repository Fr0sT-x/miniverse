package dev.frost.miniverse.minigame.impl.ctf.flag;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

public class CtfFlagInstance {
    private final String teamId;
    private final String teamName;
    private final Formatting teamColor;
    private final BlockPos baseBlockPos;
    private final Vec3d basePos;
    private final ItemStack bannerTemplate;

    private CtfFlagState state = CtfFlagState.AT_BASE;
    private UUID carrierUuid = null;
    private Vec3d currentPos;
    private int droppedTicksRemaining = 0;
    private int maxDroppedTicks = 300;

    private UUID pedestalStandUuid = null;
    private UUID hologramStandUuid = null;
    private UUID droppedStandUuid = null;

    public CtfFlagInstance(String teamId, String teamName, Formatting teamColor, BlockPos baseBlockPos) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.teamColor = teamColor != null ? teamColor : Formatting.WHITE;
        this.baseBlockPos = baseBlockPos;
        this.basePos = new Vec3d(baseBlockPos.getX() + 0.5, baseBlockPos.getY(), baseBlockPos.getZ() + 0.5);
        this.currentPos = this.basePos;
        this.bannerTemplate = createBannerItem(this.teamColor, this.teamName);
    }

    public String teamId() { return teamId; }
    public String teamName() { return teamName; }
    public Formatting teamColor() { return teamColor; }
    public BlockPos baseBlockPos() { return baseBlockPos; }
    public Vec3d basePos() { return basePos; }
    public Vec3d currentPos() { return currentPos; }
    public CtfFlagState state() { return state; }
    public @Nullable UUID carrierUuid() { return carrierUuid; }
    public int droppedTicksRemaining() { return droppedTicksRemaining; }
    public ItemStack bannerTemplate() { return bannerTemplate.copy(); }

    public void spawnAtBase(ServerWorld world) {
        this.clearStands(world);
        this.state = CtfFlagState.AT_BASE;
        this.carrierUuid = null;
        this.currentPos = this.basePos;
        this.droppedTicksRemaining = 0;

        // 1. Pedestal Armor Stand holding Banner on Head (lowered by 1 block so banner sits on pedestal)
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.setPosition(this.basePos.x, this.basePos.y - 1.0, this.basePos.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.equipStack(EquipmentSlot.HEAD, this.bannerTemplate.copy());
        world.spawnEntity(stand);
        this.pedestalStandUuid = stand.getUuid();

        // 2. Hologram Title above flag
        ArmorStandEntity holo = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        holo.setPosition(this.basePos.x, this.basePos.y + 1.2, this.basePos.z);
        holo.setInvisible(true);
        holo.setNoGravity(true);
        holo.setInvulnerable(true);
        holo.setCustomName(Text.literal("● " + this.teamName + " Flag").formatted(this.teamColor, Formatting.BOLD));
        holo.setCustomNameVisible(true);
        world.spawnEntity(holo);
        this.hologramStandUuid = holo.getUuid();
    }

    public void attachToCarrier(ServerPlayerEntity carrier) {
        if (carrier.getWorld() instanceof ServerWorld serverWorld) {
            this.clearStands(serverWorld);
        }
        this.state = CtfFlagState.CARRIED;
        this.carrierUuid = carrier.getUuid();
        this.droppedTicksRemaining = 0;

        ItemStack flagHelmet = this.bannerTemplate.copy();
        var registry = carrier.getWorld().getRegistryManager().get(RegistryKeys.ENCHANTMENT);
        var curseEntry = registry.getEntry(Enchantments.BINDING_CURSE);
        curseEntry.ifPresent(enchantmentRegistryEntry -> flagHelmet.addEnchantment(enchantmentRegistryEntry, 1));

        carrier.equipStack(EquipmentSlot.HEAD, flagHelmet);
    }

    public void dropOnGround(ServerWorld world, Vec3d pos, int returnSeconds) {
        this.clearStands(world);
        this.state = CtfFlagState.DROPPED;
        this.carrierUuid = null;
        this.currentPos = pos;
        this.maxDroppedTicks = Math.max(20, returnSeconds * 20);
        this.droppedTicksRemaining = this.maxDroppedTicks;

        // 1. Stand holding dropped banner
        ArmorStandEntity stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        stand.setPosition(pos.x, pos.y - 0.8, pos.z);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.equipStack(EquipmentSlot.HEAD, this.bannerTemplate.copy());
        world.spawnEntity(stand);
        this.droppedStandUuid = stand.getUuid();

        // 2. Countdown Hologram
        ArmorStandEntity holo = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
        holo.setPosition(pos.x, pos.y + 1.4, pos.z);
        holo.setInvisible(true);
        holo.setNoGravity(true);
        holo.setInvulnerable(true);
        holo.setCustomName(this.getDroppedHologramText());
        holo.setCustomNameVisible(true);
        world.spawnEntity(holo);
        this.hologramStandUuid = holo.getUuid();
    }

    public void returnToBase(ServerWorld world) {
        this.spawnAtBase(world);
    }

    public void markCaptured(ServerWorld world, boolean respawnAtBase) {
        this.clearStands(world);
        if (respawnAtBase) {
            this.spawnAtBase(world);
        } else {
            this.state = CtfFlagState.CAPTURED;
            this.carrierUuid = null;
            this.currentPos = this.basePos;
        }
    }

    public boolean tick(ServerWorld world) {
        if (this.state == CtfFlagState.AT_BASE) {
            // Ambient particle aura
            if (world.getServer().getTicks() % 5 == 0) {
                Vector3f colorVec = parseRgbVector(this.teamColor);
                world.spawnParticles(new DustParticleEffect(colorVec, 1.2F),
                    this.basePos.x, this.basePos.y + 1.0, this.basePos.z,
                    4, 0.35, 0.4, 0.35, 0.0);
            }
        } else if (this.state == CtfFlagState.CARRIED) {
            if (this.carrierUuid != null) {
                ServerPlayerEntity carrier = world.getServer().getPlayerManager().getPlayer(this.carrierUuid);
                if (carrier != null && carrier.isAlive() && !carrier.isSpectator()) {
                    this.currentPos = carrier.getPos();
                    if (world.getServer().getTicks() % 4 == 0) {
                        Vector3f colorVec = parseRgbVector(this.teamColor);
                        world.spawnParticles(new DustParticleEffect(colorVec, 1.5F),
                            carrier.getX(), carrier.getY() + 2.2, carrier.getZ(),
                            3, 0.2, 0.2, 0.2, 0.0);
                    }
                }
            }
        } else if (this.state == CtfFlagState.DROPPED) {
            this.droppedTicksRemaining--;

            // Update floating hologram every second
            if (this.droppedTicksRemaining % 20 == 0 && this.hologramStandUuid != null) {
                var holo = world.getEntity(this.hologramStandUuid);
                if (holo instanceof ArmorStandEntity armorStand) {
                    armorStand.setCustomName(this.getDroppedHologramText());
                }
            }

            // Particle pulse at dropped location
            if (world.getServer().getTicks() % 10 == 0) {
                world.spawnParticles(ParticleTypes.END_ROD,
                    this.currentPos.x, this.currentPos.y + 0.5, this.currentPos.z,
                    2, 0.2, 0.3, 0.2, 0.02);
            }

            if (this.droppedTicksRemaining <= 0) {
                this.returnToBase(world);
                return true; // Indicates timer expired and flag auto-returned
            }
        }
        return false;
    }

    private Text getDroppedHologramText() {
        int seconds = (int) Math.ceil(this.droppedTicksRemaining / 20.0);
        return Text.literal("⚠ " + this.teamName + " Flag Dropped [" + seconds + "s]").formatted(Formatting.YELLOW, Formatting.BOLD);
    }

    public void clearStands(ServerWorld world) {
        if (this.pedestalStandUuid != null) {
            var e = world.getEntity(this.pedestalStandUuid);
            if (e != null && !e.isRemoved()) e.discard();
            this.pedestalStandUuid = null;
        }
        if (this.hologramStandUuid != null) {
            var e = world.getEntity(this.hologramStandUuid);
            if (e != null && !e.isRemoved()) e.discard();
            this.hologramStandUuid = null;
        }
        if (this.droppedStandUuid != null) {
            var e = world.getEntity(this.droppedStandUuid);
            if (e != null && !e.isRemoved()) e.discard();
            this.droppedStandUuid = null;
        }
    }

    public static ItemStack createBannerItem(Formatting color, String teamName) {
        Item bannerItem = switch (color) {
            case RED, DARK_RED -> Items.RED_BANNER;
            case BLUE, DARK_BLUE -> Items.BLUE_BANNER;
            case GREEN, DARK_GREEN -> Items.GREEN_BANNER;
            case YELLOW, GOLD -> Items.YELLOW_BANNER;
            case AQUA, DARK_AQUA -> Items.CYAN_BANNER;
            case LIGHT_PURPLE, DARK_PURPLE -> Items.MAGENTA_BANNER;
            case GRAY, DARK_GRAY -> Items.GRAY_BANNER;
            default -> Items.WHITE_BANNER;
        };

        ItemStack stack = new ItemStack(bannerItem);
        stack.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
            Text.literal(teamName + " Banner").formatted(color, Formatting.BOLD));
        return stack;
    }

    public static Vector3f parseRgbVector(Formatting formatting) {
        Integer colorValue = formatting.getColorValue();
        if (colorValue == null) {
            return new Vector3f(1.0F, 1.0F, 1.0F);
        }
        float r = ((colorValue >> 16) & 0xFF) / 255.0F;
        float g = ((colorValue >> 8) & 0xFF) / 255.0F;
        float b = (colorValue & 0xFF) / 255.0F;
        return new Vector3f(r, g, b);
    }
}
