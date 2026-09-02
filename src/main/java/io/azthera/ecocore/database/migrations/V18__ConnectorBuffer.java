package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class V18__ConnectorBuffer implements Migration {
    @Override
    public int getVersion() { return 18; }

    @Override
    public String getDescription() { return "Add buffer_json to minion_connector_entities"; }

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!columnExists(connection, "minion_connector_entities", "buffer_json")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE minion_connector_entities ADD COLUMN buffer_json TEXT");
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
