// FILE: src/main/java/io/azthera/ecocore/database/migrations/V15__MinionStorageSlots.java
package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Migrates {@code minions_data} from a single flat {@code
 * storage_slots}/{@code storage_json} pair to the new multi-page
 * storage system (Revisi 11): adds {@code storage_page_count} and a
 * new {@code storage_pages_json} column holding a JSON array of
 * 54-slot pages. The old {@code storage_json}/{@code storage_slots}
 * columns are left in place (unused) rather than dropped, since
 * SQLite's ALTER TABLE cannot cheaply drop columns and the old data
 * is migrated forward by {@code MinionsDao} on first load rather
 * than by this migration directly.
 */
public final class V15__MinionStorageSlots implements Migration {

    @Override
    public int getVersion() {
        return 15;
    }

    @Override
    public String getDescription() {
        return "Add storage_page_count and storage_pages_json columns to minions_data";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!columnExists(connection, "minions_data", "storage_page_count")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE minions_data ADD COLUMN storage_page_count INTEGER DEFAULT 1");
            }
        }
        if (!columnExists(connection, "minions_data", "storage_pages_json")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE minions_data ADD COLUMN storage_pages_json TEXT");
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}