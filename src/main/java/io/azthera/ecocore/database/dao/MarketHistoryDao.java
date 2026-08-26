package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.MarketSnapshot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code market_history} table, used to
 * back price graphs and trend analysis over 24h/7d/30d/90d windows.
 */
public final class MarketHistoryDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a market history DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public MarketHistoryDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Inserts a new market snapshot.
     *
     * @param snapshot the snapshot to persist
     * @throws SQLException if the insert fails
     */
    public void insert(MarketSnapshot snapshot) throws SQLException {
        String sql = """
                INSERT INTO market_history (item_id, price, stock, transactions_in, transactions_out, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, snapshot.itemId());
            statement.setDouble(2, snapshot.price());
            statement.setInt(3, snapshot.stock());
            statement.setInt(4, snapshot.transactionsIn());
            statement.setInt(5, snapshot.transactionsOut());
            statement.setLong(6, snapshot.timestamp());
            statement.executeUpdate();
        }
    }

    /**
     * Finds all snapshots for an item since a given time, ordered oldest first,
     * suitable for feeding directly into {@code PriceGraphRenderer}.
     *
     * @param itemId      the item id
     * @param sinceMillis inclusive lower bound epoch millis
     * @return the matching snapshots, oldest first
     * @throws SQLException if the query fails
     */
    public List<MarketSnapshot> findSince(String itemId, long sinceMillis) throws SQLException {
        String sql = """
                SELECT item_id, price, stock, transactions_in, transactions_out, created_at
                FROM market_history
                WHERE item_id = ? AND created_at >= ?
                ORDER BY created_at ASC
                """;
        List<MarketSnapshot> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            statement.setLong(2, sinceMillis);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        }
        return results;
    }

    /**
     * Deletes snapshots older than the given cutoff, used for periodic
     * housekeeping per the {@code keep-days} setting in inflation.yml-style configs.
     *
     * @param beforeMillis exclusive upper bound epoch millis
     * @throws SQLException if the delete fails
     */
    public void pruneOlderThan(long beforeMillis) throws SQLException {
        String sql = "DELETE FROM market_history WHERE created_at < ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, beforeMillis);
            statement.executeUpdate();
        }
    }

    private MarketSnapshot mapRow(ResultSet resultSet) throws SQLException {
        return new MarketSnapshot(
                resultSet.getString("item_id"),
                resultSet.getDouble("price"),
                resultSet.getInt("stock"),
                resultSet.getInt("transactions_in"),
                resultSet.getInt("transactions_out"),
                resultSet.getLong("created_at")
        );
    }
}