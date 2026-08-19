package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the AI economy engine's learning data:
 * raw feature samples ({@code ai_learning_samples}) and the current
 * learned per-item weight profile ({@code ai_weight_profile}).
 * Feature vectors and weight profiles are stored as opaque JSON
 * strings, serialized/deserialized by {@code AiLearningModel}.
 */
public final class AiLearningDao {

    private final DatabaseManager databaseManager;

    /**
     * Creates an AI learning DAO.
     *
     * @param databaseManager the initialized database manager to pull connections from
     */
    public AiLearningDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Records a single training sample: the feature vector that was
     * observed for an item and the price that resulted from it.
     *
     * @param itemId         the item id this sample belongs to
     * @param featuresJson   serialized feature vector (supply, demand, velocity, etc.)
     * @param resultingPrice the price computed from these features
     * @throws SQLException if the insert fails
     */
    public void insertSample(String itemId, String featuresJson, double resultingPrice) throws SQLException {
        String sql = """
                INSERT INTO ai_learning_samples (item_id, features_json, resulting_price, created_at)
                VALUES (?, ?, ?, ?)
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            statement.setString(2, featuresJson);
            statement.setDouble(3, resultingPrice);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * Finds the most recent training samples for an item, newest first,
     * used by {@code AiLearningModel} to retrain its weight profile.
     *
     * @param itemId the item id
     * @param limit  maximum number of samples to return
     * @return the samples as raw feature JSON strings, newest first
     * @throws SQLException if the query fails
     */
    public List<String> findRecentFeatureSamples(String itemId, int limit) throws SQLException {
        String sql = """
                SELECT features_json FROM ai_learning_samples
                WHERE item_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<String> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(resultSet.getString("features_json"));
                }
            }
        }
        return results;
    }

    /**
     * Inserts or replaces the learned weight profile for an item.
     *
     * @param itemId      the item id
     * @param weightsJson serialized weight profile
     * @throws SQLException if the operation fails
     */
    public void upsertWeightProfile(String itemId, String weightsJson) throws SQLException {
        String sql = """
                INSERT INTO ai_weight_profile (item_id, weights_json, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT(item_id) DO UPDATE SET
                    weights_json = excluded.weights_json,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            statement.setString(2, weightsJson);
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    /**
     * Finds the currently learned weight profile for an item.
     *
     * @param itemId the item id
     * @return the serialized weight profile, or {@code null} if none learned yet
     * @throws SQLException if the query fails
     */
    public String findWeightProfile(String itemId) throws SQLException {
        String sql = "SELECT weights_json FROM ai_weight_profile WHERE item_id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, itemId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("weights_json") : null;
            }
        }
    }

    /**
     * Deletes learning samples older than the given cutoff.
     *
     * @param beforeMillis exclusive upper bound epoch millis
     * @throws SQLException if the delete fails
     */
    public void pruneOlderThan(long beforeMillis) throws SQLException {
        String sql = "DELETE FROM ai_learning_samples WHERE created_at < ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, beforeMillis);
            statement.executeUpdate();
        }
    }
}