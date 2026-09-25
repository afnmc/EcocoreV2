package io.azthera.ecocore.ai;

import io.azthera.ecocore.database.dao.MarketHistoryDao;
import io.azthera.ecocore.model.MarketSnapshot;

import java.sql.SQLException;
import java.util.List;

/**
 * Analyzes historical market snapshots to compute price trends over
 * daily, weekly, and monthly windows, used by price graphs, the
 * {@code /prices} command, and Discord market embeds.
 */
public final class TrendAnalyzer {

    /**
     * The direction of a price trend.
     */
    public enum Direction {
        RISING,
        FALLING,
        STABLE
    }

    /**
     * A computed trend over a window of snapshots.
     *
     * @param direction     the overall trend direction
     * @param percentChange percent change from the oldest to the newest snapshot in the window
     * @param sampleCount   number of snapshots used in the analysis
     */
    public record Trend(Direction direction, double percentChange, int sampleCount) {
    }

    private final MarketHistoryDao marketHistoryDao;

    /**
     * Creates a trend analyzer.
     *
     * @param marketHistoryDao DAO used to read historical price snapshots
     */
    public TrendAnalyzer(MarketHistoryDao marketHistoryDao) {
        this.marketHistoryDao = marketHistoryDao;
    }

    /**
     * Computes the price trend for an item over the given window.
     *
     * @param itemId       the item id
     * @param windowMillis how far back to look, in milliseconds
     * @return the computed trend; {@link Direction#STABLE} with 0% change if too little data exists
     * @throws SQLException if the underlying query fails
     */
    public Trend computeTrend(String itemId, long windowMillis) throws SQLException {
        long since = System.currentTimeMillis() - windowMillis;
        List<MarketSnapshot> snapshots = marketHistoryDao.findSince(itemId, since);

        if (snapshots.size() < 2) {
            return new Trend(Direction.STABLE, 0.0, snapshots.size());
        }

        double oldest = snapshots.get(0).price();
        double newest = snapshots.get(snapshots.size() - 1).price();

        if (oldest <= 0) {
            return new Trend(Direction.STABLE, 0.0, snapshots.size());
        }

        double percentChange = ((newest - oldest) / oldest) * 100.0;

        Direction direction;
        if (percentChange > 1.0) {
            direction = Direction.RISING;
        } else if (percentChange < -1.0) {
            direction = Direction.FALLING;
        } else {
            direction = Direction.STABLE;
        }

        return new Trend(direction, percentChange, snapshots.size());
    }
}