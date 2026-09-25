package io.azthera.ecocore.ai;

import io.azthera.ecocore.database.dao.MoneyDao;
import io.azthera.ecocore.database.dao.PlayerDao;

import java.sql.SQLException;

/**
 * Tracks the velocity of money in the server economy: how quickly
 * money changes hands relative to the total money supply. High
 * velocity indicates an active, liquid economy; low velocity
 * indicates money is being hoarded rather than spent.
 */
public final class VelocityOfMoneyTracker {

    private final PlayerDao playerDao;
    private final MoneyDao moneyDao;

    /**
     * Creates a velocity tracker.
     *
     * @param playerDao DAO used to read total money supply
     * @param moneyDao  DAO used to read net money flow over a window
     */
    public VelocityOfMoneyTracker(PlayerDao playerDao, MoneyDao moneyDao) {
        this.playerDao = playerDao;
        this.moneyDao = moneyDao;
    }

    /**
     * Computes the velocity of money over a sampling window: the absolute
     * net money flow in the window divided by the total money supply.
     * Returns 0.0 if there is currently no money in circulation.
     *
     * @param sinceMillis inclusive lower bound epoch millis for the sampling window
     * @return the velocity ratio, typically in the 0.0-1.0+ range
     * @throws SQLException if the underlying queries fail
     */
    public double computeVelocity(long sinceMillis) throws SQLException {
        double totalMoney = playerDao.sumTotalMoney();
        if (totalMoney <= 0) {
            return 0.0;
        }
        double flow = Math.abs(moneyDao.netMoneyFlowSince(sinceMillis));
        return Math.min(1.0, flow / totalMoney);
    }
}