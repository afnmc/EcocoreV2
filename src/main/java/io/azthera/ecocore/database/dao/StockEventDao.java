package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Data access object for the {@code stock_events} table, an audit
 * trail of every stock change (buy consumption, restock, admin
 * adjustment) applied to a shop item.
 */
public final class StockEventDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a stock event DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public StockEventDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Records a stock change event.
     *
     * @param itemId     the item whose stock changed
     * @param eventType  a short type tag (e.g. "BUY", "RESTOCK_SCHEDULED", "RESTOCK_EMERGENCY", "RESTOCK_RANDOM", "ADMIN")
     * @param amount     the signed amount stock changed by (negative for consumption, positive for restock)
     * @param stockAfter the resulting stock value after this event
     * @throws SQLException if the insert fails
     */
    public void insert(String itemId, String eventType, int amount, int stockAfter) throws SQLException {
        String sql = """
                INSERT INTO stock_events (item_id, event_type, amount, stock_after, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            statement.setString(2, eventType);
            statement.setInt(3, amount);
            statement.setInt(4, stockAfter);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * Deletes stock events older than the given cutoff, used for periodic housekeeping.
     *
     * @param beforeMillis exclusive upper bound epoch millis
     * @throws SQLException if the delete fails
     */
    public void pruneOlderThan(long beforeMillis) throws SQLException {
        String sql = "DELETE FROM stock_events WHERE created_at < ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, beforeMillis);
            statement.executeUpdate();
        }
    }
}