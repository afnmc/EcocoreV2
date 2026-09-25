package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Data access object for the {@code money_ledger} table, an append-only
 * audit trail of every balance change applied to a player account.
 */
public final class MoneyDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a money ledger DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public MoneyDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Appends a ledger entry recording a balance change.
     *
     * @param playerUuid   the player whose balance changed
     * @param changeAmount the signed change amount (positive for deposit, negative for withdrawal)
     * @param balanceAfter the resulting balance after the change
     * @param reason       a short machine-readable reason code (e.g. "shop_buy", "job_reward")
     * @throws SQLException if the insert fails
     */
    public void logChange(UUID playerUuid, double changeAmount, double balanceAfter, String reason) throws SQLException {
        String sql = """
                INSERT INTO money_ledger (player_uuid, change_amount, balance_after, reason, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setDouble(2, changeAmount);
            statement.setDouble(3, balanceAfter);
            statement.setString(4, reason);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * Computes net money flow (sum of all change amounts) within a time window,
     * used by the InflationEngine's money-flow indicator.
     *
     * @param sinceMillis inclusive lower bound epoch millis
     * @return the net signed money flow across all players in the window
     * @throws SQLException if the query fails
     */
    public double netMoneyFlowSince(long sinceMillis) throws SQLException {
        String sql = "SELECT COALESCE(SUM(change_amount), 0) AS flow FROM money_ledger WHERE created_at >= ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sinceMillis);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble("flow") : 0.0;
            }
        }
    }

    /**
     * Deletes ledger entries older than the given cutoff, used for periodic
     * housekeeping so the ledger doesn't grow unbounded.
     *
     * @param beforeMillis exclusive upper bound epoch millis; entries older than this are removed
     * @throws SQLException if the delete fails
     */
    public void pruneOlderThan(long beforeMillis) throws SQLException {
        String sql = "DELETE FROM money_ledger WHERE created_at < ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, beforeMillis);
            statement.executeUpdate();
        }
    }
}