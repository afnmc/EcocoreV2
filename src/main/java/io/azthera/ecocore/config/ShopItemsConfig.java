package io.azthera.ecocore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed view of {@code shop-items.yml}: the admin-editable source of
 * truth for the shop catalog's static fields (category, material,
 * base price, and optional overrides). Consumed by
 * {@code ShopCatalogLoader} on every {@code loadCatalog()} call so
 * server owners can add/adjust items just by editing this file and
 * running {@code /ecocore reload} - no database editing required.
 *
 * <p>Live/runtime fields (current AI-computed price, current stock)
 * are NOT read from this file - those are only ever set here for a
 * brand-new item's initial values, and are otherwise fully owned by
 * the AI economy engine and stock system once an item exists in the
 * database.
 */
public final class ShopItemsConfig {

    /**
     * A single configured item definition.
     *
     * @param id         the item id (the map key in shop-items.yml), used as the catalog's primary key
     * @param category   the shop category id this item belongs to (must match a category in shop.yml)
     * @param material   the Bukkit Material name backing this item
     * @param basePrice  the reference price used for a brand-new item's initial current price
     * @param minPrice   optional price floor override, 0 to use the category default from prices.yml
     * @param maxPrice   optional price ceiling override, 0 to use the category default from prices.yml
     * @param maxStock   optional max stock override, 0 to use shop.yml's default-max-stock
     * @param elasticity optional elasticity override, 0 to use the category default from prices.yml
     * @param tradeable  whether this item should currently be tradeable
     */
    public record ItemDefinition(String id, String category, String material, double basePrice,
                                  double minPrice, double maxPrice, int maxStock,
                                  double elasticity, boolean tradeable) {
    }

    private final List<ItemDefinition> items = new ArrayList<>();

    /**
     * Parses item definitions from the loaded {@code shop-items.yml}.
     *
     * @param config the loaded shop-items.yml
     */
    public ShopItemsConfig(FileConfiguration config) {
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }

        for (String id : itemsSection.getKeys(false)) {
            ConfigurationSection section = itemsSection.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            items.add(new ItemDefinition(
                    id,
                    section.getString("category", "misc"),
                    section.getString("material", "STONE"),
                    section.getDouble("base-price", 1.0),
                    section.getDouble("min-price", 0.0),
                    section.getDouble("max-price", 0.0),
                    section.getInt("max-stock", 0),
                    section.getDouble("elasticity", 0.0),
                    section.getBoolean("tradeable", true)
            ));
        }
    }

    /**
     * Returns every configured item definition, in file order.
     *
     * @return the configured item definitions
     */
    public List<ItemDefinition> getItems() {
        return items;
    }
}
