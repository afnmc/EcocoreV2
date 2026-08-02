package io.azthera.ecocore.shop;

import io.azthera.ecocore.model.ShopItemRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Performs simple case-insensitive substring search over a shop
 * catalog, matching against item id and material name.
 */
public final class ShopSearchEngine {

    /**
     * Searches the given items for matches against a free-text query.
     *
     * @param items the catalog (or category subset) to search within
     * @param query the search text, may contain spaces/underscores
     * @return matching items, in their original relative order
     */
    public List<ShopItemRecord> search(Collection<ShopItemRecord> items, String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(items);
        }

        String normalizedQuery = normalize(query);
        List<ShopItemRecord> results = new ArrayList<>();
        for (ShopItemRecord item : items) {
            if (normalize(item.getId()).contains(normalizedQuery)
                    || normalize(item.getMaterial()).contains(normalizedQuery)) {
                results.add(item);
            }
        }
        return results;
    }

    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT).replace("_", " ").trim();
    }
}