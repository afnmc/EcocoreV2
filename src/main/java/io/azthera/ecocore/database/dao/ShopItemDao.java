package io.azthera.ecocore.database.dao;

import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.model.ShopItemRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access object for the {@code shop_items} table, the catalog of
 * every tradeable item tracked by the shop and AI economy engine.
 */
public final class ShopItemDao {

    private final DatabaseManager databaseManager;

    public ShopItemDao(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public ShopItemRecord findById(String id) throws SQLException {
        String sql = "SELECT * FROM shop_items WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRow(resultSet) : null;
            }
        }
    }

    public List<ShopItemRecord> findAll() throws SQLException {
        String sql = "SELECT * FROM shop_items";
        List<ShopItemRecord> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(mapRow(resultSet));
            }
        }
        return results;
    }

    /**
     * Returns every item id currently in the catalog, used by
     * {@code ShopCatalogLoader} to figure out which rows are no
     * longer present in {@code shop-items.yml} and should be removed
     * (keeping the database from accumulating stale/duplicate entries
     * across catalog format changes).
     *
     * @return every item id in the catalog
     * @throws SQLException if the query fails
     */
    public List<String> findAllIds() throws SQLException {
        String sql = "SELECT id FROM shop_items";
        List<String> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(resultSet.getString("id"));
            }
        }
        return results;
    }

    public List<ShopItemRecord> findByCategory(String category) throws SQLException {
        String sql = "SELECT * FROM shop_items WHERE category = ?";
        List<ShopItemRecord> results = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(mapRow(resultSet));
                }
            }
        }
        return results;
    }

    public void upsert(ShopItemRecord item) throws SQLException {
        String sql = """
                INSERT INTO shop_items
                    (id, category, material, namespaced_key, base_price, current_price,
                     min_price, max_price, stock, max_stock, elasticity, tradeable, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    category = excluded.category,
                    material = excluded.material,
                    namespaced_key = excluded.namespaced_key,
                    base_price = excluded.base_price,
                    current_price = excluded.current_price,
                    min_price = excluded.min_price,
                    max_price = excluded.max_price,
                    stock = excluded.stock,
                    max_stock = excluded.max_stock,
                    elasticity = excluded.elasticity,
                    tradeable = excluded.tradeable,
                    updated_at = excluded.updated_at
                """;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getId());
            statement.setString(2, item.getCategory());
            statement.setString(3, item.getMaterial());
            statement.setString(4, item.getNamespacedKey());
            statement.setDouble(5, item.getBasePrice());
            statement.setDouble(6, item.getCurrentPrice());
            statement.setDouble(7, item.getMinPrice());
            statement.setDouble(8, item.getMaxPrice());
            statement.setInt(9, item.getStock());
            statement.setInt(10, item.getMaxStock());
            statement.setDouble(11, item.getElasticity());
            statement.setBoolean(12, item.isTradeable());
            statement.setLong(13, item.getUpdatedAt());
            statement.executeUpdate();
        }
    }

    public void updatePrice(String id, double currentPrice, long updatedAt) throws SQLException {
        String sql = "UPDATE shop_items SET current_price = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, currentPrice);
            statement.setLong(2, updatedAt);
            statement.setString(3, id);
            statement.executeUpdate();
        }
    }

    public void updateStock(String id, int stock, long updatedAt) throws SQLException {
        String sql = "UPDATE shop_items SET stock = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, stock);
            statement.setLong(2, updatedAt);
            statement.setString(3, id);
            statement.executeUpdate();
        }
    }

    /**
     * Permanently deletes an item from the catalog, used by
     * {@code ShopCatalogLoader} to remove entries that no longer
     * exist in {@code shop-items.yml}.
     *
     * @param id the item id to delete
     * @throws SQLException if the delete fails
     */
    public void deleteById(String id) throws SQLException {
        String sql = "DELETE FROM shop_items WHERE id = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    private ShopItemRecord mapRow(ResultSet resultSet) throws SQLException {
        return new ShopItemRecord(
                resultSet.getString("id"),
                resultSet.getString("category"),
                resultSet.getString("material"),
                resultSet.getString("namespaced_key"),
                resultSet.getDouble("base_price"),
                resultSet.getDouble("current_price"),
                resultSet.getDouble("min_price"),
                resultSet.getDouble("max_price"),
                resultSet.getInt("stock"),
                resultSet.getInt("max_stock"),
                resultSet.getDouble("elasticity"),
                resultSet.getBoolean("tradeable"),
                resultSet.getLong("updated_at")
        );
    }
                }
