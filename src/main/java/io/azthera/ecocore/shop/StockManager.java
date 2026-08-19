package io.azthera.ecocore.shop;

import io.azthera.ecocore.database.dao.ShopItemDao;
import io.azthera.ecocore.database.dao.StockEventDao;
import io.azthera.ecocore.model.ShopItemRecord;

import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Applies and persists all stock-level changes to shop items: buy
 * consumption and restocks. Every change is written through to the
 * database immediately and logged to {@code stock_events} for audit.
 *
 * <p>Selling items back to the shop does not directly change an
 * item's {@code stock} field - it instead feeds the AI engine's
 * supply/demand signals (see {@code SupplyDemandAnalyzer}), keeping
 * "how much the shop has to sell" and "how much players are selling
 * to the shop" as independently tracked signals.
 */
public final class StockManager {

    public static final String EVENT_BUY = "BUY";
    public static final String EVENT_RESTOCK_SCHEDULED = "RESTOCK_SCHEDULED";
    public static final String EVENT_RESTOCK_EMERGENCY = "RESTOCK_EMERGENCY";
    public static final String EVENT_RESTOCK_RANDOM = "RESTOCK_RANDOM";
    public static final String EVENT_ADMIN = "ADMIN";

    private final Logger logger;
    private final ShopItemDao shopItemDao;
    private final StockEventDao stockEventDao;
    private final Map<String, ShopItemRecord> catalog;

    /**
     * Creates a stock manager.
     *
     * @param logger        plugin logger for error reporting
     * @param shopItemDao   DAO used to persist stock changes
     * @param stockEventDao DAO used to log stock change audit events
     * @param catalog       the live in-memory catalog shared with {@code ShopManager}
     */
    public StockManager(Logger logger, ShopItemDao shopItemDao, StockEventDao stockEventDao,
                         Map<String, ShopItemRecord> catalog) {
        this.logger = logger;
        this.shopItemDao = shopItemDao;
        this.stockEventDao = stockEventDao;
        this.catalog = catalog;
    }

    /**
     * Consumes stock for a completed purchase.
     *
     * @param itemId the item that was bought
     * @param amount the quantity purchased, must be positive
     * @return {@code true} if stock was successfully consumed
     */
    public boolean consumeForBuy(String itemId, int amount) {
        return applyChange(itemId, -amount, EVENT_BUY);
    }

    /**
     * Applies a restock, adding units back to an item's stock.
     *
     * @param itemId     the item to restock
     * @param amount     the quantity to add, must be positive
     * @param eventType  one of the {@code EVENT_RESTOCK_*} constants
     * @return {@code true} if the restock was applied
     */
    public boolean restock(String itemId, int amount, String eventType) {
        return applyChange(itemId, amount, eventType);
    }

    /**
     * Applies a manual admin stock adjustment (e.g. from {@code /ecocore market}).
     *
     * @param itemId the item to adjust
     * @param delta  the signed amount to change stock by
     * @return {@code true} if the adjustment was applied
     */
    public boolean adminAdjust(String itemId, int delta) {
        return applyChange(itemId, delta, EVENT_ADMIN);
    }

    private boolean applyChange(String itemId, int delta, String eventType) {
        ShopItemRecord item = catalog.get(itemId);
        if (item == null) {
            return false;
        }

        synchronized (item) {
            int before = item.getStock();
            item.adjustStock(delta);
            int after = item.getStock();

            if (before == after && delta != 0) {
                // Hit a bound (0 or max stock) without moving at all.
                return false;
            }

            try {
                shopItemDao.updateStock(itemId, after, item.getUpdatedAt());
                stockEventDao.insert(itemId, eventType, after - before, after);
                return true;
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to persist stock change for " + itemId + ": " + exception.getMessage());
                item.setStock(before);
                return false;
            }
        }
    }
}