package io.azthera.ecocore.ai;

import io.azthera.ecocore.database.dao.ShopItemDao;
import io.azthera.ecocore.model.ShopItemRecord;

import java.sql.SQLException;
import java.util.List;

/**
 * Computes how saturated the market is for a given item or category:
 * a high-stock, low-demand item is "saturated" and should trend toward
 * lower prices; a low-stock, high-demand item is the opposite.
 */
public final class MarketSaturationAnalyzer {

    private final ShopItemDao shopItemDao;

    /**
     * Creates a market saturation analyzer.
     *
     * @param shopItemDao DAO used to read the current item catalog
     */
    public MarketSaturationAnalyzer(ShopItemDao shopItemDao) {
        this.shopItemDao = shopItemDao;
    }

    /**
     * Computes a single item's saturation score, 0.0 (scarce/high demand)
     * to 1.0 (fully saturated/low demand).
     *
     * @param supplySignal normalized supply signal from {@link SupplyDemandAnalyzer}
     * @param demandSignal normalized demand signal from {@link SupplyDemandAnalyzer}
     * @return the saturation score
     */
    public double itemSaturation(double supplySignal, double demandSignal) {
        return clamp(supplySignal * (1.0 - demandSignal));
    }

    /**
     * Computes the average saturation across every tradeable item in a
     * category, used for category-wide price pressure decisions and
     * Discord market summaries.
     *
     * @param category the shop category id
     * @return the average saturation across that category's items, or 0.0 if empty
     * @throws SQLException if the underlying query fails
     */
    public double categorySaturation(String category) throws SQLException {
        List<ShopItemRecord> items = shopItemDao.findByCategory(category);
        if (items.isEmpty()) {
            return 0.0;
        }

        double total = 0.0;
        int counted = 0;
        for (ShopItemRecord item : items) {
            if (!item.isTradeable() || item.getMaxStock() <= 0) {
                continue;
            }
            total += item.getStock() / (double) item.getMaxStock();
            counted++;
        }
        return counted == 0 ? 0.0 : clamp(total / counted);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}