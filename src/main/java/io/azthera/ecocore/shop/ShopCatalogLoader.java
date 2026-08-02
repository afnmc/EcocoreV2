package io.azthera.ecocore.shop;

import io.azthera.ecocore.config.PricesConfig;
import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.database.dao.ShopItemDao;
import io.azthera.ecocore.model.ShopItemRecord;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads the shop's item catalog from the database into memory, and
 * bootstraps a sensible default catalog on first install (when the
 * {@code shop_items} table is still empty) using the categories
 * defined in {@code shop.yml} and the price bounds from {@code prices.yml}.
 *
 * <p>Server owners are expected to curate their live catalog afterward
 * via {@code /ecocore market} and direct database edits; the bootstrap
 * set only exists so a freshly installed server isn't an empty shop.
 */
public final class ShopCatalogLoader {

    /**
     * Default starter items per category: material name paired with a
     * reasonable base price. Only used to seed a brand-new install.
     */
    private static final Map<String, List<Map.Entry<String, Double>>> DEFAULT_CATALOG = buildDefaultCatalog();

    private final Logger logger;
    private final ShopItemDao shopItemDao;
    private final ShopConfig shopConfig;
    private final PricesConfig pricesConfig;

    /**
     * Creates a catalog loader.
     *
     * @param logger       plugin logger for bootstrap/load summaries
     * @param shopItemDao  DAO used to read/write the item catalog
     * @param shopConfig   resolved shop.yml configuration (categories, default stock)
     * @param pricesConfig resolved prices.yml configuration (price bound multipliers)
     */
    public ShopCatalogLoader(Logger logger, ShopItemDao shopItemDao, ShopConfig shopConfig, PricesConfig pricesConfig) {
        this.logger = logger;
        this.shopItemDao = shopItemDao;
        this.shopConfig = shopConfig;
        this.pricesConfig = pricesConfig;
    }

    /**
     * Loads the full catalog from the database, bootstrapping default
     * starter items first if the catalog is currently empty.
     *
     * @return every item in the catalog, keyed by item id
     * @throws SQLException if the underlying queries fail
     */
    public Map<String, ShopItemRecord> loadCatalog() throws SQLException {
        List<ShopItemRecord> existing = shopItemDao.findAll();
        if (existing.isEmpty()) {
            bootstrapDefaults();
            existing = shopItemDao.findAll();
        }

        Map<String, ShopItemRecord> catalog = new LinkedHashMap<>();
        for (ShopItemRecord item : existing) {
            catalog.put(item.getId(), item);
        }

        logger.info("[EcoCore] Loaded " + catalog.size() + " shop items into the catalog");
        return catalog;
    }

    private void bootstrapDefaults() throws SQLException {
        logger.info("[EcoCore] shop_items table is empty - seeding default starter catalog");

        for (ShopConfig.CategoryDefinition category : shopConfig.getCategories()) {
            List<Map.Entry<String, Double>> defaults = DEFAULT_CATALOG.get(category.id());
            if (defaults == null) {
                continue;
            }

            for (Map.Entry<String, Double> entry : defaults) {
                String material = entry.getKey();
                double basePrice = entry.getValue();
                String itemId = category.id() + "_" + material.toLowerCase();

                double elasticity = pricesConfig.getElasticityForCategory(category.id());
                double minPrice = basePrice * pricesConfig.getGlobalMinPriceMultiplier();
                double maxPrice = basePrice * pricesConfig.getMaxPriceMultiplierForCategory(category.id());
                int maxStock = shopConfig.getDefaultMaxStock();

                ShopItemRecord record = new ShopItemRecord(
                        itemId, category.id(), material, null,
                        basePrice, basePrice, minPrice, maxPrice,
                        maxStock, maxStock, elasticity, true, System.currentTimeMillis()
                );
                shopItemDao.upsert(record);
            }
        }
    }

    private static Map<String, List<Map.Entry<String, Double>>> buildDefaultCatalog() {
        Map<String, List<Map.Entry<String, Double>>> map = new LinkedHashMap<>();

        map.put("blocks", List.of(
                Map.entry("STONE", 1.0), Map.entry("COBBLESTONE", 0.5),
                Map.entry("DIRT", 0.2), Map.entry("SAND", 0.3)
        ));
        map.put("ores", List.of(
                Map.entry("COAL", 3.0), Map.entry("IRON_INGOT", 8.0),
                Map.entry("GOLD_INGOT", 15.0), Map.entry("DIAMOND", 60.0),
                Map.entry("EMERALD", 50.0), Map.entry("LAPIS_LAZULI", 4.0)
        ));
        map.put("food", List.of(
                Map.entry("BREAD", 2.0), Map.entry("COOKED_BEEF", 4.0),
                Map.entry("COOKED_CHICKEN", 3.0), Map.entry("APPLE", 1.5)
        ));
        map.put("farming", List.of(
                Map.entry("WHEAT", 1.0), Map.entry("POTATO", 0.8),
                Map.entry("BEETROOT", 0.8), Map.entry("PUMPKIN", 2.0),
                Map.entry("MELON_SLICE", 0.5)
        ));
        map.put("mob-drops", List.of(
                Map.entry("ROTTEN_FLESH", 0.1), Map.entry("BONE", 0.5),
                Map.entry("STRING", 0.5), Map.entry("SPIDER_EYE", 1.0),
                Map.entry("GUNPOWDER", 2.0), Map.entry("ENDER_PEARL", 20.0)
        ));
        map.put("fishing", List.of(
                Map.entry("COD", 2.0), Map.entry("SALMON", 2.5),
                Map.entry("PUFFERFISH", 3.0), Map.entry("TROPICAL_FISH", 3.5)
        ));
        map.put("wood", List.of(
                Map.entry("OAK_LOG", 1.5), Map.entry("BIRCH_LOG", 1.5),
                Map.entry("SPRUCE_LOG", 1.5), Map.entry("JUNGLE_LOG", 1.8)
        ));
        map.put("redstone", List.of(
                Map.entry("REDSTONE", 2.0), Map.entry("REPEATER", 5.0),
                Map.entry("COMPARATOR", 6.0), Map.entry("PISTON", 4.0)
        ));
        map.put("misc", List.of(
                Map.entry("BOOK", 3.0), Map.entry("PAPER", 0.5),
                Map.entry("LEATHER", 2.0), Map.entry("FEATHER", 0.5)
        ));

        return map;
    }
}