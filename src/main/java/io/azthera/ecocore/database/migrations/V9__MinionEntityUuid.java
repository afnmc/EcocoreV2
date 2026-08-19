package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds an {@code entity_uuid} column to {@code minions_data}, used to
 * reliably re-resolve a minion's live world entity by uuid instead of
 * trusting a long-held Java object reference that can go stale across
 * chunk unload/reload cycles.
 */
public final class V9__MinionEntityUuid implements Migration {

    @Override
    public int getVersion() {
        return 9;
    }

    @Override
    public String getDescription() {
        return "Add entity_uuid to minions_data";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!columnExists(connection, "minions_data", "entity_uuid")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE minions_data ADD COLUMN entity_uuid TEXT");
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
