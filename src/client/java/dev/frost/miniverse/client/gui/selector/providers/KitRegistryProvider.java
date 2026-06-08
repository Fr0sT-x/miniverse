package dev.frost.miniverse.client.gui.selector.providers;

import dev.frost.miniverse.client.gui.selector.RegistryCategory;
import dev.frost.miniverse.client.gui.selector.RegistryContentProvider;
import dev.frost.miniverse.minigame.core.kit.Kit;
import dev.frost.miniverse.minigame.core.kit.KitRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class KitRegistryProvider implements RegistryContentProvider<Kit> {

    @Override
    public Registry<Kit> registry() {
        return null; // KitRegistry isn't a vanilla Registry, we handle getEntries manually
    }

    @Override
    public Collection<Kit> getEntries() {
        return KitRegistry.getAll();
    }

    @Override
    public void renderIcon(DrawContext context, Kit entry, int x, int y) {
        // Render a placeholder or first item in inventory as an icon
        ItemStack icon = new ItemStack(Items.DIAMOND_SWORD);
        if (entry.getInventory().length > 0 && entry.getInventory()[0] != null) {
            icon = entry.getInventory()[0];
        }
        context.drawItem(icon, x, y);
    }

    @Override
    public Text getDisplayName(Kit entry) {
        return entry.getDisplayName();
    }

    @Override
    public Identifier getId(Kit entry) {
        return entry.getId();
    }

    @Override
    public List<Text> getTooltip(Kit entry) {
        return List.of(
            entry.getDisplayName().copy().formatted(Formatting.GOLD),
            Text.literal(String.join(", ", entry.getCategories())).formatted(Formatting.GRAY)
        );
    }

    @Override
    public Set<Identifier> getTags(Kit entry) {
        return Collections.emptySet();
    }

    @Override
    public Set<RegistryCategory> getCategories(Kit entry) {
        return entry.getCategories().stream()
            .map(c -> new RegistryCategory(c, Text.literal(c), "textures/gui/icons/category_" + c + ".png"))
            .collect(Collectors.toSet());
    }

    @Override
    public List<RegistryCategory> getAllCategories() {
        return KitRegistry.getAll().stream()
            .flatMap(k -> k.getCategories().stream())
            .distinct()
            .map(c -> new RegistryCategory(c, Text.literal(c), "textures/gui/icons/category_" + c + ".png"))
            .toList();
    }

    @Override
    public boolean supportsCreation() {
        return true;
    }

    @Override
    public void openCreator(net.minecraft.client.gui.screen.Screen parent, Kit editingEntry) {
        net.minecraft.client.MinecraftClient.getInstance().setScreen(new dev.frost.miniverse.client.gui.workspace.KitCreatorScreen(parent, editingEntry));
    }
}
