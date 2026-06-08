package dev.frost.miniverse.client.gui.selector.providers;

import dev.frost.miniverse.client.gui.selector.RegistryCategory;
import dev.frost.miniverse.client.gui.selector.RegistryContentProvider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ItemRegistryProvider implements RegistryContentProvider<Item> {

    @Override
    public net.minecraft.registry.Registry<Item> registry() {
        return Registries.ITEM;
    }

    @Override
    public Collection<Item> getEntries() {
        return StreamSupport.stream(Registries.ITEM.spliterator(), false).toList();
    }

    @Override
    public void renderIcon(DrawContext context, Item entry, int x, int y) {
        context.drawItem(new ItemStack(entry), x, y);
    }

    @Override
    public Text getDisplayName(Item entry) {
        return entry.getName();
    }

    @Override
    public Identifier getId(Item entry) {
        return Registries.ITEM.getId(entry);
    }

    @Override
    public List<Text> getTooltip(Item entry) {
        return List.of(
            entry.getName(),
            Text.literal(getId(entry).toString()).formatted(net.minecraft.util.Formatting.DARK_GRAY)
        );
    }

    @Override
    public Set<Identifier> getTags(Item entry) {
        return registry().getEntry(getId(entry)).stream()
            .flatMap(e -> e.streamTags())
            .map(TagKey::id)
            .collect(Collectors.toSet());
    }

    @Override
    public Set<RegistryCategory> getCategories(Item entry) {
        return Set.of();
    }

    @Override
    public List<RegistryCategory> getAllCategories() {
        return List.of();
    }
}
