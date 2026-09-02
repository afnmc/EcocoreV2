package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class V16__ShopItemRestockCooldown implements Migration {

    @Override
    public int getVersion() {
        return 16;
    }

    @Override
    public String getDescription() {
        return "Add restock cooldown tracking columns to shop_items";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "last_restock_at", "INTEGER DEFAULT 0");
        addColumnIfMissing(connection, "restocks_today", "INTEGER DEFAULT 0");
        addColumnIfMissing(connection, "restock_day_epoch", "INTEGER DEFAULT 0");
    }

    private void addColumnIfMissing(Connection connection, String column, String definition) throws SQLException {
        if (!columnExists(connection, column)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE shop_items ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private boolean columnExists(Connection connection, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, "shop_items", column)) {
            return columns.next();
        }
    }
}