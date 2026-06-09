package dev.frost.miniverse.client.gui.selector;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RegistryContentProvider<T> {
    Registry<T> registry();
    
    Collection<T> getEntries();
    
    void renderIcon(DrawContext context, T entry, int x, int y);
    
    Text getDisplayName(T entry);
    
    Identifier getId(T entry);
    
    List<Text> getTooltip(T entry);
    
    Set<Identifier> getTags(T entry);
    
    Set<RegistryCategory> getCategories(T entry);
    
    List<RegistryCategory> getAllCategories();
    
    default boolean supportsCreation() { return false; }
    
    default boolean hasCollapsibleCategories() { return false; }
    
    default void openCreator(Screen parent, T editingEntry) {}

    default boolean isListView() { return false; }

    default String getPrimaryCategory(T entry) { return ""; }

    default boolean renderCustomEntry(DrawContext context, T entry, int index, int x, int y, int width, int height, boolean isHovered, boolean isSelected, double mouseX, double mouseY) { return false; }

    default boolean handleCustomClick(T entry, double mouseX, double mouseY, int button, int x, int y, int width, int height) { return false; }
}
