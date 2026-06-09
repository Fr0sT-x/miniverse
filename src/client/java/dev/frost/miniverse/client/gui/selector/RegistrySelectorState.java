package dev.frost.miniverse.client.gui.selector;

import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

public class RegistrySelectorState {
    private String searchQuery = "";
    private final Set<String> activeCategories = new HashSet<>();
    private double scrollPosition = 0;
    private boolean sidebarExpanded = true;
    private boolean selectedFilterActive = false;
    private final Set<Identifier> favorites = new HashSet<>();
    private final Set<String> collapsedCategories = new HashSet<>();

    public Set<String> getCollapsedCategories() {
        return this.collapsedCategories;
    }

    public void toggleCollapsedCategory(String category) {
        if (!this.collapsedCategories.remove(category)) {
            this.collapsedCategories.add(category);
        }
    }

    public String getSearchQuery() {
        return this.searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery;
    }

    public Set<String> getActiveCategories() {
        return this.activeCategories;
    }

    public void toggleCategory(String categoryId) {
        if (!this.activeCategories.remove(categoryId)) {
            this.activeCategories.add(categoryId);
        }
    }

    public void clearCategories() {
        this.activeCategories.clear();
    }

    public double getScrollPosition() {
        return this.scrollPosition;
    }

    public void setScrollPosition(double scrollPosition) {
        this.scrollPosition = scrollPosition;
    }

    public boolean isSidebarExpanded() {
        return this.sidebarExpanded;
    }

    public void setSidebarExpanded(boolean sidebarExpanded) {
        this.sidebarExpanded = sidebarExpanded;
    }

    public boolean isSelectedFilterActive() {
        return this.selectedFilterActive;
    }

    public void setSelectedFilterActive(boolean selectedFilterActive) {
        this.selectedFilterActive = selectedFilterActive;
    }

    public Set<Identifier> getFavorites() {
        return this.favorites;
    }

    public void toggleFavorite(Identifier id) {
        if (!this.favorites.remove(id)) {
            this.favorites.add(id);
        }
    }
}
