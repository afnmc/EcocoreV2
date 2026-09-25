package io.azthera.ecocore.shop;

import io.azthera.ecocore.model.ShopItemRecord;

import java.util.Comparator;
import java.util.List;

/**
 * Provides the sort orders available in the shop GUI's sort button.
 */
public final class ShopSortEngine {

    /**
     * The available sort modes, cycled through by the GUI's sort button.
     */
    public enum SortMode {
        PRICE_ASC,
        PRICE_DESC,
        STOCK_ASC,
        STOCK_DESC,
        NAME_ASC,
        NAME_DESC
    }

    /**
     * Sorts the given item list in place according to the given mode.
     *
     * @param items the items to sort
     * @param mode  the sort mode to apply
     * @return the same list instance, sorted, for convenient chaining
     */
    public List<ShopItemRecord> sort(List<ShopItemRecord> items, SortMode mode) {
        Comparator<ShopItemRecord> comparator = switch (mode) {
            case PRICE_ASC -> Comparator.comparingDouble(ShopItemRecord::getCurrentPrice);
            case PRICE_DESC -> Comparator.comparingDouble(ShopItemRecord::getCurrentPrice).reversed();
            case STOCK_ASC -> Comparator.comparingInt(ShopItemRecord::getStock);
            case STOCK_DESC -> Comparator.comparingInt(ShopItemRecord::getStock).reversed();
            case NAME_ASC -> Comparator.comparing(ShopItemRecord::getMaterial);
            case NAME_DESC -> Comparator.comparing(ShopItemRecord::getMaterial).reversed();
        };
        items.sort(comparator);
        return items;
    }

    /**
     * Returns the sort mode that should follow the given one when a
     * player repeatedly clicks the sort button, cycling back to the
     * first mode after the last.
     *
     * @param current the currently active sort mode
     * @return the next sort mode in the cycle
     */
    public SortMode next(SortMode current) {
        SortMode[] values = SortMode.values();
        int nextIndex = (current.ordinal() + 1) % values.length;
        return values[nextIndex];
    }
}