package dev.frost.miniverse.minigame.impl.hideandseek.disguise;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DisguiseType {
    public static final DisguiseType BOOKSHELF = new DisguiseType("bookshelf", "Bookshelf", Blocks.BOOKSHELF.getDefaultState(), new ItemStack(Items.BOOKSHELF));
    public static final DisguiseType CRAFTING_TABLE = new DisguiseType("crafting_table", "Crafting Table", Blocks.CRAFTING_TABLE.getDefaultState(), new ItemStack(Items.CRAFTING_TABLE));
    public static final DisguiseType FURNACE = new DisguiseType("furnace", "Furnace", Blocks.FURNACE.getDefaultState(), new ItemStack(Items.FURNACE));
    public static final DisguiseType CHEST = new DisguiseType("chest", "Chest", Blocks.CHEST.getDefaultState(), new ItemStack(Items.CHEST));
    public static final DisguiseType ANVIL = new DisguiseType("anvil", "Anvil", Blocks.ANVIL.getDefaultState(), new ItemStack(Items.ANVIL));
    public static final DisguiseType HAY_BLOCK = new DisguiseType("hay_block", "Hay Bale", Blocks.HAY_BLOCK.getDefaultState(), new ItemStack(Items.HAY_BLOCK));
    public static final DisguiseType MELON = new DisguiseType("melon", "Melon", Blocks.MELON.getDefaultState(), new ItemStack(Items.MELON));
    public static final DisguiseType TNT = new DisguiseType("tnt", "TNT", Blocks.TNT.getDefaultState(), new ItemStack(Items.TNT));
    public static final DisguiseType CAULDRON = new DisguiseType("cauldron", "Cauldron", Blocks.CAULDRON.getDefaultState(), new ItemStack(Items.CAULDRON));
    public static final DisguiseType STONECUTTER = new DisguiseType("stonecutter", "Stonecutter", Blocks.STONECUTTER.getDefaultState(), new ItemStack(Items.STONECUTTER));
    public static final DisguiseType FLOWER_POT = new DisguiseType("flower_pot", "Flower Pot", Blocks.FLOWER_POT.getDefaultState(), new ItemStack(Items.FLOWER_POT));
    public static final DisguiseType BARREL = new DisguiseType("barrel", "Barrel", Blocks.BARREL.getDefaultState(), new ItemStack(Items.BARREL));

    public static final List<DisguiseType> ALL = List.of(
        BOOKSHELF, CRAFTING_TABLE, FURNACE, CHEST, ANVIL, HAY_BLOCK,
        MELON, TNT, CAULDRON, STONECUTTER, FLOWER_POT, BARREL
    );

    private static final Map<String, DisguiseType> DEFAULTS_MAP = new LinkedHashMap<>();
    static {
        for (DisguiseType d : ALL) {
            DEFAULTS_MAP.put(d.id.toLowerCase(), d);
            DEFAULTS_MAP.put("minecraft:" + d.id.toLowerCase(), d);
        }
    }

    private final String id;
    private final String displayName;
    private final BlockState state;
    private final ItemStack icon;

    public DisguiseType(String id, String displayName, BlockState state, ItemStack icon) {
        this.id = id;
        this.displayName = displayName;
        this.state = state;
        this.icon = icon;
    }

    public String id() {
        return this.id;
    }

    public String displayName() {
        return this.displayName;
    }

    public BlockState blockState() {
        return this.state;
    }

    public ItemStack icon() {
        return this.icon.copy();
    }

    public static DisguiseType fromBlock(Block block) {
        if (block == null || block == Blocks.AIR) {
            return CRAFTING_TABLE;
        }
        Identifier id = Registries.BLOCK.getId(block);
        String idStr = id.toString();
        DisguiseType existing = DEFAULTS_MAP.get(idStr.toLowerCase());
        if (existing != null) {
            return existing;
        }
        String name = block.getName().getString();
        ItemStack icon = new ItemStack(block.asItem());
        if (icon.isEmpty()) {
            icon = new ItemStack(Items.STONE);
        }
        return new DisguiseType(idStr, name, block.getDefaultState(), icon);
    }

    public static DisguiseType fromId(String idStr) {
        if (idStr == null || idStr.isBlank()) {
            return CRAFTING_TABLE;
        }
        String clean = idStr.trim().toLowerCase();
        DisguiseType existing = DEFAULTS_MAP.get(clean);
        if (existing != null) {
            return existing;
        }
        Identifier id = Identifier.tryParse(clean.contains(":") ? clean : "minecraft:" + clean);
        if (id != null && Registries.BLOCK.containsId(id)) {
            Block block = Registries.BLOCK.get(id);
            if (block != Blocks.AIR) {
                return fromBlock(block);
            }
        }
        return CRAFTING_TABLE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DisguiseType that = (DisguiseType) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
