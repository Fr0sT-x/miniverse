package dev.frost.miniverse.client.gui.selector.providers;

import dev.frost.miniverse.client.gui.selector.RegistryCategory;
import dev.frost.miniverse.client.gui.selector.RegistryContentProvider;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class BlockRegistryProvider implements RegistryContentProvider<Block> {

    @Override
    public net.minecraft.registry.Registry<Block> registry() {
        return Registries.BLOCK;
    }

    @Override
    public Collection<Block> getEntries() {
        return StreamSupport.stream(Registries.BLOCK.spliterator(), false)
            .filter(b -> b.asItem() != Items.AIR)
            .toList();
    }

    @Override
    public void renderIcon(DrawContext context, Block entry, int x, int y) {
        Item item = entry.asItem();
        if (item != Items.AIR) {
            context.drawItem(new ItemStack(item), x, y);
        }
    }

    @Override
    public Text getDisplayName(Block entry) {
        return entry.getName();
    }

    @Override
    public Identifier getId(Block entry) {
        return Registries.BLOCK.getId(entry);
    }

    @Override
    public List<Text> getTooltip(Block entry) {
        return List.of(
            entry.getName(),
            Text.literal(getId(entry).toString()).formatted(net.minecraft.util.Formatting.DARK_GRAY)
        );
    }

    @Override
    public Set<Identifier> getTags(Block entry) {
        return registry().getEntry(getId(entry)).stream()
            .flatMap(e -> e.streamTags())
            .map(TagKey::id)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<RegistryCategory> getCategories(Block entry) {
        // Implement default categorization logic here, or return empty
        return Set.of();
    }

    @Override
    public List<RegistryCategory> getAllCategories() {
        return List.of();
    }
}
