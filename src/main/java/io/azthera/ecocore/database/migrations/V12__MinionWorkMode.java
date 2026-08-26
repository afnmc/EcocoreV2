// FILE: src/main/java/io/azthera/ecocore/database/migrations/V12__MinionWorkMode.java
package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds a {@code use_arena_mode} column to {@code minions_data} for
 * minion types whose work mode is BOTH (Revisi 2), letting the
 * player toggle between facing-only and 360-degree arena work.
 */
public final class V12__MinionWorkMode implements Migration {

    @Override
    public int getVersion() {
        return 12;
    }

    @Override
    public String getDescription() {
        return "Add use_arena_mode column to minions_data";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!columnExists(connection, "minions_data", "use_arena_mode")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE minions_data ADD COLUMN use_arena_mode INTEGER DEFAULT 0");
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