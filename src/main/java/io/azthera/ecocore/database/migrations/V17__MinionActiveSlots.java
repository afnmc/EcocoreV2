package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds an {@code active_slot_count} column to {@code minions_data}
 * for the STORAGE-type-only multi-page split: non-STORAGE minions
 * now have exactly one storage page, and this column tracks how many
 * of that single page's 54 slots are currently usable (starts small,
 * upgraded up to 54). Meaningless for STORAGE-type minions, which
 * keep using {@code storage_page_count} instead.
 */
public final class V17__MinionActiveSlots implements Migration {

    @Override
    public int getVersion() {
        return 17;
    }

    @Override
    public String getDescription() {
        return "Add active_slot_count column to minions_data";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!columnExists(connection, "minions_data", "active_slot_count")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE minions_data ADD COLUMN active_slot_count INTEGER DEFAULT 9");
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