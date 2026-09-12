package io.azthera.ecocore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed view of {@code shop.yml}: GUI layout, category definitions,
 * feature toggles, buying rules, and default stock settings.
 */
public final class ShopConfig {

    /**
     * Static definition of a single shop category tab.
     *
     * @param id          category id, referenced by {@code ShopItemRecord.getCategory()}
     * @param displayName colorized display name
     * @param icon        Bukkit Material name used as the tab icon
     * @param slot        GUI slot the category tab is placed in
     */
    public record CategoryDefinition(String id, String displayName, String icon, int slot) {
    }

    private final int guiRows;
    private final String guiTitle;
    private final int itemsPerPage;

    private final List<CategoryDefinition> categories = new ArrayList<>();

    private final boolean searchEnabled;
    private final boolean paginationEnabled;
    private final boolean sortingEnabled;
    private final boolean favoriteEnabled;
    private final boolean historyEnabled;
    private final int historyMaxEntries;
    private final boolean itemPreviewEnabled;
    private final boolean stockIndicatorEnabled;
    private final boolean inflationIndicatorEnabled;
    private final boolean priceGraphEnabled;
    private final List<String> priceGraphPeriods;

    private final double requireConfirmationAbove;
    private final boolean buyMaxEnabled;
    private final int buyMaxCap;
    private final boolean sellOutBlockPurchase;

    private final int defaultMaxStock;
    private final int lowStockThresholdPercent;
    private final int criticalStockThresholdPercent;

    /**
     * Parses shop configuration from the loaded {@code shop.yml}.
     *
     * @param config the loaded shop.yml
     */
    public ShopConfig(FileConfiguration config) {
        this.guiRows = config.getInt("gui.rows", 6);
        this.guiTitle = config.getString("gui.title", "&8Shop");
        this.itemsPerPage = config.getInt("gui.items-per-page", 45);

        for (var raw : config.getMapList("categories")) {
            String id = String.valueOf(raw.get("id"));
            String displayName = String.valueOf(raw.get("display-name"));
            String icon = String.valueOf(raw.get("icon"));
            int slot = raw.get("slot") instanceof Number number ? number.intValue() : 0;
            categories.add(new CategoryDefinition(id, displayName, icon, slot));
        }

        this.searchEnabled = config.getBoolean("features.search-enabled", true);
        this.paginationEnabled = config.getBoolean("features.pagination-enabled", true);
        this.sortingEnabled = config.getBoolean("features.sorting-enabled", true);
        this.favoriteEnabled = config.getBoolean("features.favorite-enabled", true);
        this.historyEnabled = config.getBoolean("features.history-enabled", true);
        this.historyMaxEntries = config.getInt("features.history-max-entries", 50);
        this.itemPreviewEnabled = config.getBoolean("features.item-preview-enabled", true);
        this.stockIndicatorEnabled = config.getBoolean("features.stock-indicator-enabled", true);
        this.inflationIndicatorEnabled = config.getBoolean("features.inflation-indicator-enabled", true);
        this.priceGraphEnabled = config.getBoolean("features.price-graph-enabled", true);
        this.priceGraphPeriods = config.getStringList("features.price-graph-periods");

        this.requireConfirmationAbove = config.getDouble("buying.require-confirmation-above", 1000.0);
        this.buyMaxEnabled = config.getBoolean("buying.buy-max-enabled", true);
        this.buyMaxCap = config.getInt("buying.buy-max-cap", 6400);
        this.sellOutBlockPurchase = config.getBoolean("buying.sell-out-block-purchase", true);

        this.defaultMaxStock = config.getInt("stock.default-max-stock", 6400);
        this.lowStockThresholdPercent = config.getInt("stock.low-stock-threshold-percent", 20);
        this.criticalStockThresholdPercent = config.getInt("stock.critical-stock-threshold-percent", 5);
    }

    public int getGuiRows() {
        return guiRows;
    }

    public String getGuiTitle() {
        return guiTitle;
    }

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public List<CategoryDefinition> getCategories() {
        return categories;
    }

    public boolean isSearchEnabled() {
        return searchEnabled;
    }

    public boolean isPaginationEnabled() {
        return paginationEnabled;
    }

    public boolean isSortingEnabled() {
        return sortingEnabled;
    }

    public boolean isFavoriteEnabled() {
        return favoriteEnabled;
    }

    public boolean isHistoryEnabled() {
        return historyEnabled;
    }

    public int getHistoryMaxEntries() {
        return historyMaxEntries;
    }

    public boolean isItemPreviewEnabled() {
        return itemPreviewEnabled;
    }

    public boolean isStockIndicatorEnabled() {
        return stockIndicatorEnabled;
    }

    public boolean isInflationIndicatorEnabled() {
        return inflationIndicatorEnabled;
    }

    public boolean isPriceGraphEnabled() {
        return priceGraphEnabled;
    }

    public List<String> getPriceGraphPeriods() {
        return priceGraphPeriods;
    }

    public double getRequireConfirmationAbove() {
        return requireConfirmationAbove;
    }

    public boolean isBuyMaxEnabled() {
        return buyMaxEnabled;
    }

    public int getBuyMaxCap() {
        return buyMaxCap;
    }

    public boolean isSellOutBlockPurchase() {
        return sellOutBlockPurchase;
    }

    public int getDefaultMaxStock() {
        return defaultMaxStock;
    }

    public int getLowStockThresholdPercent() {
        return lowStockThresholdPercent;
    }

    public int getCriticalStockThresholdPercent() {
        return criticalStockThresholdPercent;
    }
}