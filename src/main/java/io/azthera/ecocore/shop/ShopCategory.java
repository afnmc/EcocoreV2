package io.azthera.ecocore.shop;

import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.model.ShopItemRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * A runtime view of a single shop category: its static display
 * definition from {@code shop.yml} paired with the live list of
 * items currently assigned to it.
 */
public final class ShopCategory {

    private final ShopConfig.CategoryDefinition definition;
    private final List<ShopItemRecord> items = new ArrayList<>();

    /**
     * Creates a category wrapper around a static category definition.
     *
     * @param definition the category's display definition from shop.yml
     */
    public ShopCategory(ShopConfig.CategoryDefinition definition) {
        this.definition = definition;
    }

    public String getId() {
        return definition.id();
    }

    public String getDisplayName() {
        return definition.displayName();
    }

    public String getIcon() {
        return definition.icon();
    }

    public int getSlot() {
        return definition.slot();
    }

    /**
     * Returns the live items currently assigned to this category.
     * Backed by a mutable list refreshed by {@code ShopCatalogLoader}
     * on reload; callers should not retain long-lived references to it.
     *
     * @return the category's items
     */
    public List<ShopItemRecord> getItems() {
        return items;
    }

    /**
     * Replaces this category's item list wholesale, called by
     * {@code ShopCatalogLoader} whenever the catalog is (re)loaded.
     *
     * @param newItems the new items belonging to this category
     */
    public void setItems(List<ShopItemRecord> newItems) {
        items.clear();
        items.addAll(newItems);
    }
}