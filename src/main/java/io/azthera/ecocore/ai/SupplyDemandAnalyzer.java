package io.azthera.ecocore.ai;

import io.azthera.ecocore.database.dao.BuyHistoryDao;
import io.azthera.ecocore.database.dao.SellHistoryDao;
import io.azthera.ecocore.model.ShopItemRecord;

import java.sql.SQLException;

/**
 * Computes normalized supply and demand signals for a single shop item,
 * derived from its current stock level and recent buy/sell transaction
 * volume. All signals are normalized to roughly the 0.0-1.0 range so
 * they can be linearly combined by {@link PriceCalculator}.
 */
public final class SupplyDemandAnalyzer {

    /** Smoothing constant used to squash raw transaction counts into a 0-1 range. */
    private static final double COUNT_SMOOTHING = 10.0;

    private final BuyHistoryDao buyHistoryDao;
    private final SellHistoryDao sellHistoryDao;

    /**
     * Creates a supply/demand analyzer.
     *
     * @param buyHistoryDao  DAO used to count recent buy transactions
     * @param sellHistoryDao DAO used to count recent sell transactions
     */
    public SupplyDemandAnalyzer(BuyHistoryDao buyHistoryDao, SellHistoryDao sellHistoryDao) {
        this.buyHistoryDao = buyHistoryDao;
        this.sellHistoryDao = sellHistoryDao;
    }

    /**
     * Result of a supply/demand analysis for a single item.
     *
     * @param supply       normalized supply signal, 0.0 (scarce) to 1.0 (abundant)
     * @param demand       normalized demand signal, 0.0 (no demand) to 1.0 (very high demand)
     * @param boughtVolume raw count of buy transactions in the sampling window (players buying from the shop)
     * @param soldVolume   raw count of sell transactions in the sampling window (players selling into the shop)
     */
    public record Result(double supply, double demand, int boughtVolume, int soldVolume) {
    }

    /**
     * Analyzes an item's supply and demand over the given time window.
     *
     * @param item        the shop item to analyze
     * @param sinceMillis inclusive lower bound epoch millis for the sampling window
     * @return the computed supply/demand result
     * @throws SQLException if the underlying queries fail
     */
    public Result analyze(ShopItemRecord item, long sinceMillis) throws SQLException {
        int boughtVolume = buyHistoryDao.countSince(item.getId(), sinceMillis);
        int soldVolume = sellHistoryDao.countSince(item.getId(), sinceMillis);

        double stockSignal = item.getMaxStock() > 0
                ? item.getStock() / (double) item.getMaxStock()
                : 0.0;
        double inflowSignal = normalizeCount(soldVolume);

        // Supply blends current stock level with how fast items are flowing
        // back in; a fuller shop that's also being restocked quickly reads
        // as more abundant than raw stock percentage alone would suggest.
        double supply = clamp((stockSignal * 0.7) + (inflowSignal * 0.3));
        double demand = normalizeCount(boughtVolume);

        return new Result(supply, demand, boughtVolume, soldVolume);
    }

    private double normalizeCount(int count) {
        return count / (count + COUNT_SMOOTHING);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}