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
    private static final int ACTION_WIDTH = 54;
    private static final int ACTION_GAP = 6;
    private static final int ACTION_AREA_WIDTH = ACTION_WIDTH * 3 + ACTION_GAP * 2;

    private final java.util.Map<Identifier, EntryBounds> lastRenderBounds = new java.util.HashMap<>();

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

    @Override
    public boolean isListView() {
        return true;
    }

    @Override
    public boolean renderCustomEntry(DrawContext context, Kit entry, int x, int y, int width, int height, boolean isHovered, boolean isSelected, double mouseX, double mouseY) {
        lastRenderBounds.put(entry.getId(), new EntryBounds(x, y, width, height));

        // Draw background
        context.fill(x, y, x + width, y + height, isSelected ? 0x8033AA33 : (isHovered ? 0x60FFFFFF : 0x40000000));
        
        int startX = x + 10;
        int hotbarY = y + (height - 22) / 2;
        
        // Draw 9 hotbar slots
        for (int i = 0; i < 9; i++) {
            context.fill(startX + i * 20, hotbarY, startX + i * 20 + 18, hotbarY + 18, 0x80000000);
            if (entry.getInventory() != null && i < entry.getInventory().length) {
                ItemStack stack = entry.getInventory()[i];
                if (stack != null && !stack.isEmpty()) {
                    context.drawItem(stack, startX + i * 20 + 1, hotbarY + 1);
                    context.drawItemInSlot(net.minecraft.client.MinecraftClient.getInstance().textRenderer, stack, startX + i * 20 + 1, hotbarY + 1);
                }
            }
        }
        
        // Kit Name
        int textX = startX + 9 * 20 + 10;
        context.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, entry.getDisplayName(), textX, y + (height - 8) / 2, 0xFFFFFF, false);
        
        // Actions
        int actionX = x + width - ACTION_AREA_WIDTH - 8;
        int actionY = y + (height - 18) / 2;
        this.drawAction(context, actionX, actionY, "Rename", 0xFFAAAAAA);
        this.drawAction(context, actionX + ACTION_WIDTH + ACTION_GAP, actionY, "Delete", 0xFFFF5555);
        this.drawAction(context, actionX + (ACTION_WIDTH + ACTION_GAP) * 2, actionY, "Give", 0xFF55FF55);
        
        return true;
    }

    @Override
    public boolean handleCustomClick(Kit entry, double mouseX, double mouseY, int button, int x, int y, int width, int height) {
        EntryBounds bounds = lastRenderBounds.get(entry.getId());
        if (bounds == null) return false;

        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        int actionX = bounds.x + bounds.width - ACTION_AREA_WIDTH - 8;
        int actionY = bounds.y + (bounds.height - 18) / 2;

        if (inside(mouseX, mouseY, actionX, actionY)) {
            this.openCreator(client.currentScreen, entry);
            return true;
        }
        if (inside(mouseX, mouseY, actionX + ACTION_WIDTH + ACTION_GAP, actionY)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.DeleteKitPayload(entry.getId().toString()));
            KitRegistry.delete(entry.getId());
            if (client.currentScreen instanceof dev.frost.miniverse.client.gui.selector.RegistrySelectorScreen<?> selectorScreen) {
                selectorScreen.refreshEntries();
            }
            return true;
        }
        if (inside(mouseX, mouseY, actionX + (ACTION_WIDTH + ACTION_GAP) * 2, actionY)) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.frost.miniverse.common.NetworkConstants.GiveKitPayload(entry.getId().toString()));
            return true;
        }

        return false;
    }

    private void drawAction(DrawContext context, int x, int y, String label, int color) {
        context.fill(x, y, x + ACTION_WIDTH, y + 18, 0x66000000);
        int labelX = x + (ACTION_WIDTH - net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(label)) / 2;
        context.drawText(net.minecraft.client.MinecraftClient.getInstance().textRenderer, Text.literal(label), labelX, y + 5, color, false);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + ACTION_WIDTH && mouseY >= y && mouseY < y + 18;
    }

    private record EntryBounds(int x, int y, int width, int height) {
    }
}
