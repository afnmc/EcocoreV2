package io.azthera.ecocore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger("EcoCore");

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
                                  double sellPrice, double minPrice, double maxPrice, int maxStock,
                                  double elasticity, boolean tradeable, String variantType, String variantValue, String serializedItem) {
        public ItemDefinition(String id, String category, String material, double basePrice,
                              double minPrice, double maxPrice, int maxStock,
                              double elasticity, boolean tradeable, String variantType, String variantValue, String serializedItem) {
            this(id, category, material, basePrice, -1.0, minPrice, maxPrice, maxStock, elasticity, tradeable, variantType, variantValue, null);
        }
    }

    private final List<ItemDefinition> items = new ArrayList<>();
    private final FileConfiguration config;
    private File file;

    /**
     * Parses item definitions from the loaded {@code shop-items.yml}.
     *
     * @param config the loaded shop-items.yml
     */
    public ShopItemsConfig(FileConfiguration config) {
        this.config = config;
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
                    section.getDouble("sell-price", -1.0),
                    section.getDouble("min-price", 0.0),
                    section.getDouble("max-price", 0.0),
                    section.getInt("max-stock", 0),
                    section.getDouble("elasticity", 0.0),
                    section.getBoolean("tradeable", true),
                    section.getString("variant-type", ""),
                    section.getString("variant-value", ""),
                    section.getString("item-data", "")
            ));
        }
    }

    /** Sets the file handle for save operations. */
    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Returns every configured item definition, in file order.
     *
     * @return the configured item definitions
     */
    public List<ItemDefinition> getItems() {
        return items;
    }

    /**
     * Updates a single field for an item in shop-items.yml and saves to disk.
     * Used by admin GUI to persist price/tradeable changes.
     *
     * @param itemId the item id
     * @param field  the YAML field name (e.g. "base-price", "sell-price", "tradeable")
     * @param value  the new value
     */
    /**
     * Adds a complete admin-created shop item definition and persists it.
     */
    public synchronized void addItem(ItemDefinition definition) {
        items.add(definition);
        String path = "items." + definition.id();
        config.set(path + ".category", definition.category());
        config.set(path + ".material", definition.material());
        config.set(path + ".base-price", definition.basePrice());
        config.set(path + ".sell-price", definition.sellPrice());
        config.set(path + ".min-price", definition.minPrice());
        config.set(path + ".max-price", definition.maxPrice());
        config.set(path + ".max-stock", definition.maxStock());
        config.set(path + ".elasticity", definition.elasticity());
        config.set(path + ".tradeable", definition.tradeable());
        if (definition.variantType() != null && !definition.variantType().isBlank()) {
            config.set(path + ".variant-type", definition.variantType());
        }
        if (definition.variantValue() != null && !definition.variantValue().isBlank()) {
            config.set(path + ".variant-value", definition.variantValue());
        }
        if (definition.serializedItem() != null && !definition.serializedItem().isBlank()) {
            config.set(path + ".item-data", definition.serializedItem());
        }
        save();
    }

    private void save() {
        if (file != null) {
            try {
                config.save(file);
            } catch (IOException exception) {
                LOGGER.warning("[EcoCore] Failed to save shop-items.yml: " + exception.getMessage());
            }
        }
    }

    public synchronized void updateField(String itemId, String field, Object value) {
        String path = "items." + itemId + "." + field;
        config.set(path, value);
        if (file != null) {
            try {
                config.save(file);
            } catch (IOException exception) {
                LOGGER.warning("[EcoCore] Failed to save shop-items.yml after updating " + path + ": " + exception.getMessage());
            }
        }
    }
}
