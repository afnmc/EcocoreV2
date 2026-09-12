package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds per-item stock caps: introduces {@code max_stock} on
 * {@code shop_items} and a {@code stock_events} log table used by
 * {@code RestockScheduler} and {@code StockManager} to audit restocks.
 */
public final class V2__ShopStock implements Migration {

    @Override
    public int getVersion() {
        return 2;
    }

    @Override
    public String getDescription() {
        return "Add max_stock to shop_items and create stock_events table";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!columnExists(connection, "shop_items", "max_stock")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE shop_items ADD COLUMN max_stock INTEGER NOT NULL DEFAULT 6400");
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS stock_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        item_id TEXT NOT NULL,
                        event_type TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        stock_after INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_stock_events_item ON stock_events(item_id, created_at)");
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}