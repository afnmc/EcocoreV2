package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.NightMarketOffer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code night_market_offers} table.
 */
public final class NightMarketDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates a night market DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public NightMarketDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Loads every currently persisted offer.
     *
     * @return the persisted offers
     * @throws SQLException if the query fails
     */
    public List<NightMarketOffer> findAll() throws SQLException {
        String sql = "SELECT * FROM night_market_offers";
        List<NightMarketOffer> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(new NightMarketOffer(
                        resultSet.getString("id"),
                        resultSet.getString("material"),
                        resultSet.getDouble("price"),
                        resultSet.getInt("stock"),
                        resultSet.getInt("max_stock")
                ));
            }
        }
        return results;
    }

    /**
     * Returns when the currently persisted rotation started.
     *
     * @return epoch millis of the rotation start, 0 if no offers exist
     * @throws SQLException if the query fails
     */
    public long findRotationStartedAt() throws SQLException {
        String sql = "SELECT rotation_started_at FROM night_market_offers LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong("rotation_started_at") : 0L;
        }
    }

    /**
     * Deletes every persisted offer, called before writing a fresh rotation.
     *
     * @throws SQLException if the delete fails
     */
    public void clearAll() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM night_market_offers");
        }
    }

    /**
     * Inserts a single offer for the current rotation.
     *
     * @param offer             the offer to persist
     * @param rotationStartedAt epoch millis this rotation started
     * @throws SQLException if the insert fails
     */
    public void insert(NightMarketOffer offer, long rotationStartedAt) throws SQLException {
        String sql = """
                INSERT INTO night_market_offers (id, material, price, stock, max_stock, rotation_started_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, offer.getId());
            statement.setString(2, offer.getMaterial());
            statement.setDouble(3, offer.getPrice());
            statement.setInt(4, offer.getStock());
            statement.setInt(5, offer.getMaxStock());
            statement.setLong(6, rotationStartedAt);
            statement.executeUpdate();
        }
    }

    /**
     * Updates an offer's remaining stock.
     *
     * @param id    the offer id
     * @param stock the new stock value
     * @throws SQLException if the update fails
     */
    public void updateStock(String id, int stock) throws SQLException {
        String sql = "UPDATE night_market_offers SET stock = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, stock);
            statement.setString(2, id);
            statement.executeUpdate();
        }
    }
              }