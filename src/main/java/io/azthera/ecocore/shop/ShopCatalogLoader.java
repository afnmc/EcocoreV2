package io.azthera.ecocore.shop;

import io.azthera.ecocore.config.PricesConfig;
import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.config.ShopItemsConfig;
import io.azthera.ecocore.database.dao.ShopItemDao;
import io.azthera.ecocore.model.ShopItemRecord;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads the shop's item catalog from the database into memory, and
 * fully mirrors it against {@code shop-items.yml} on every load:
 * <ul>
 *   <li>new item ids in the YAML are inserted</li>
 *   <li>existing ones have their static fields (category, material,
 *       base price, bound overrides) refreshed WITHOUT resetting the
 *       item's live AI-driven current price or current stock</li>
 *   <li>any database row whose id is NOT present in
 *       {@code shop-items.yml} is deleted</li>
 * </ul>
 * That last point matters: earlier plugin versions bootstrapped a
 * default catalog directly into the database using a different id
 * scheme (e.g. {@code blocks_stone}). Without the delete step here,
 * those old rows would sit alongside the new {@code shop-items.yml}
 * ids (e.g. {@code stone}) forever, showing up as duplicate-looking
 * entries for the same material. Treating {@code shop-items.yml} as
 * the single full source of truth keeps the catalog clean.
 */
public final class ShopCatalogLoader {

    private final Logger logger;
    private final ShopItemDao shopItemDao;
    private final ShopConfig shopConfig;
    private final PricesConfig pricesConfig;
    private final ShopItemsConfig shopItemsConfig;

    /**
     * Creates a catalog loader.
     *
     * @param logger          plugin logger for sync/load summaries
     * @param shopItemDao     DAO used to read/write the item catalog
     * @param shopConfig      resolved shop.yml configuration (default stock fallback)
     * @param pricesConfig    resolved prices.yml configuration (price bound fallbacks)
     * @param shopItemsConfig resolved shop-items.yml configuration (the catalog's source of truth)
     */
    public ShopCatalogLoader(Logger logger, ShopItemDao shopItemDao, ShopConfig shopConfig,
                              PricesConfig pricesConfig, ShopItemsConfig shopItemsConfig) {
        this.logger = logger;
        this.shopItemDao = shopItemDao;
        this.shopConfig = shopConfig;
        this.pricesConfig = pricesConfig;
        this.shopItemsConfig = shopItemsConfig;
    }

    /**
     * Syncs the database from {@code shop-items.yml}, then loads the
     * full resulting catalog.
     *
     * @return every item in the catalog, keyed by item id
     * @throws SQLException if the underlying queries fail
     */
    public Map<String, ShopItemRecord> loadCatalog() throws SQLException {
        syncFromConfig();

        List<ShopItemRecord> allItems = shopItemDao.findAll();
        Map<String, ShopItemRecord> catalog = new LinkedHashMap<>();
        for (ShopItemRecord item : allItems) {
            catalog.put(item.getId(), item);
        }

        logger.info("[EcoCore] Loaded " + catalog.size() + " shop items into the catalog");
        return catalog;
    }

    private void syncFromConfig() throws SQLException {
        int created = 0;
        int updated = 0;
        Set<String> configuredIds = new HashSet<>();

        for (ShopItemsConfig.ItemDefinition def : shopItemsConfig.getItems()) {
            configuredIds.add(def.id());
            ShopItemRecord existing = shopItemDao.findById(def.id());

            double minPrice = def.minPrice() > 0
                    ? def.minPrice() : def.basePrice() * pricesConfig.getGlobalMinPriceMultiplier();
            double maxPrice = def.maxPrice() > 0
                    ? def.maxPrice() : def.basePrice() * pricesConfig.getMaxPriceMultiplierForCategory(def.category());
            double elasticity = def.elasticity() > 0
                    ? def.elasticity() : pricesConfig.getElasticityForCategory(def.category());
            int maxStock = def.maxStock() > 0 ? def.maxStock() : shopConfig.getDefaultMaxStock();

            if (existing == null) {
                ShopItemRecord record = new ShopItemRecord(
                        def.id(), def.category(), def.material(), null,
                        def.basePrice(), def.basePrice(), minPrice, maxPrice,
                        maxStock, maxStock, elasticity, def.tradeable(), System.currentTimeMillis(),
                        0L, 0, 0L
                );
                shopItemDao.upsert(record);
                created++;
            } else {
                existing.setCategory(def.category());
                existing.setMaterial(def.material());
                existing.setBasePrice(def.basePrice());
                existing.setMinPrice(minPrice);
                existing.setMaxPrice(maxPrice);
                existing.setElasticity(elasticity);
                existing.setMaxStock(maxStock);
                existing.setTradeable(def.tradeable());
                shopItemDao.upsert(existing);
                updated++;
            }
        }

        int removed = 0;
        for (String existingId : shopItemDao.findAllIds()) {
            if (!configuredIds.contains(existingId)) {
                shopItemDao.deleteById(existingId);
                removed++;
            }
        }

        if (created > 0 || updated > 0 || removed > 0) {
            logger.info("[EcoCore] shop-items.yml sync: " + created + " created, " + updated
                    + " updated, " + removed + " removed (not in shop-items.yml)");
        }
    }
                    }
