// FILE: src/main/java/io/azthera/ecocore/database/migrations/V11__MinionFacing.java
package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds a {@code facing} column to {@code minions_data}, storing the
 * cardinal direction (NORTH/SOUTH/EAST/WEST) locked in when the
 * minion was placed (Revisi 1). Defaults existing rows to SOUTH.
 */
public final class V11__MinionFacing implements Migration {

    @Override
    public int getVersion() {
        return 11;
    }

    @Override
    public String getDescription() {
        return "Add facing column to minions_data";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!columnExists(connection, "minions_data", "facing")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE minions_data ADD COLUMN facing TEXT DEFAULT 'SOUTH'");
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