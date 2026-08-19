package io.azthera.ecocore.inflation;

import io.azthera.ecocore.database.dao.PlayerDao;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Tracks how concentrated wealth is across the server's player base,
 * expressed as a Gini-coefficient-style score from 0.0 (perfectly
 * equal balances) to 1.0 (maximal inequality). Feeds the
 * "player wealth" indicator in the InflationEngine's weighted
 * economic pressure calculation.
 */
public final class WealthDistributionTracker {

    private final PlayerDao playerDao;

    /**
     * Creates a wealth distribution tracker.
     *
     * @param playerDao DAO used to read every account's balance
     */
    public WealthDistributionTracker(PlayerDao playerDao) {
        this.playerDao = playerDao;
    }

    /**
     * Computes the current wealth concentration across all known accounts.
     *
     * @return a Gini-coefficient-style score, 0.0 (equal) to 1.0 (unequal);
     *         0.0 if fewer than two accounts exist
     * @throws SQLException if the underlying query fails
     */
    public double computeWealthConcentration() throws SQLException {
        List<Double> balances = playerDao.findAllBalances();
        if (balances.size() < 2) {
            return 0.0;
        }

        List<Double> sorted = balances.stream().sorted().toList();
        int n = sorted.size();

        double sum = 0.0;
        for (double balance : sorted) {
            sum += balance;
        }
        if (sum <= 0) {
            return 0.0;
        }

        double weightedSum = 0.0;
        for (int i = 0; i < n; i++) {
            // Standard Gini formula using rank-weighted balances on a sorted list.
            weightedSum += ((2.0 * (i + 1)) - n - 1) * sorted.get(i);
        }

        double gini = weightedSum / (n * sum);
        return clamp(gini);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}