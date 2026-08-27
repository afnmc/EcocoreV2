package io.azthera.ecocore.database;

import io.azthera.ecocore.database.migrations.Migration;
import io.azthera.ecocore.database.migrations.V1__InitialSchema;
import io.azthera.ecocore.database.migrations.V2__ShopStock;
import io.azthera.ecocore.database.migrations.V3__InflationHistory;
import io.azthera.ecocore.database.migrations.V4__JobsData;
import io.azthera.ecocore.database.migrations.V5__MinionsData;
import io.azthera.ecocore.database.migrations.V6__AiLearning;
import io.azthera.ecocore.database.migrations.V7__DiscordLogs;
import io.azthera.ecocore.database.migrations.V8__NightMarket;
import io.azthera.ecocore.database.migrations.V9__MinionEntityUuid;
import io.azthera.ecocore.database.migrations.V10__MinionConnectors;
import io.azthera.ecocore.database.migrations.V11__MinionFacing;
import io.azthera.ecocore.database.migrations.V12__MinionWorkMode;
import io.azthera.ecocore.database.migrations.V13__MinionConnectorEntities;
import io.azthera.ecocore.database.migrations.V14__MinionConnections;
import io.azthera.ecocore.database.migrations.V15__MinionStorageSlots;
import io.azthera.ecocore.database.migrations.V16__ShopItemRestockCooldown;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Discovers, orders, and applies all registered {@link Migration}s
 * against the plugin's SQLite database, tracking progress in a
 * {@code schema_version} table so each migration runs exactly once.
 */
public final class MigrationManager {

    private final Logger logger;
    private final List<Migration> migrations = new ArrayList<>();

    /**
     * Creates a migration manager with all built-in EcoCore migrations
     * pre-registered in version order.
     *
     * @param logger the plugin logger to report progress on
     */
    public MigrationManager(Logger logger) {
        this.logger = logger;
        register(new V1__InitialSchema());
        register(new V2__ShopStock());
        register(new V3__InflationHistory());
        register(new V4__JobsData());
        register(new V5__MinionsData());
        register(new V6__AiLearning());
        register(new V7__DiscordLogs());
        register(new V8__NightMarket());
        register(new V9__MinionEntityUuid());
        register(new V10__MinionConnectors());
        register(new V11__MinionFacing());
        register(new V12__MinionWorkMode());
        register(new V13__MinionConnectorEntities());
        register(new V14__MinionConnections());
        register(new V15__MinionStorageSlots());
        register(new V16__ShopItemRestockCooldown());
    }

    /**
     * Registers an additional migration.
     *
     * @param migration the migration to register
     */
    public void register(Migration migration) {
        migrations.add(migration);
        migrations.sort(Comparator.comparingInt(Migration::getVersion));
    }

    /**
     * Ensures the bookkeeping table exists, then applies every
     * migration whose version is greater than the currently stored
     * schema version, in ascending order.
     *
     * @param connection an open JDBC connection
     * @throws SQLException if bookkeeping or any migration fails
     */
    public void migrate(Connection connection) throws SQLException {
        ensureBookkeepingTable(connection);
        int currentVersion = readCurrentVersion(connection);

        for (Migration migration : migrations) {
            if (migration.getVersion() <= currentVersion) {
                continue;
            }
            logger.info("[EcoCore] Applying migration V" + migration.getVersion()
                    + " - " + migration.getDescription());
            migration.apply(connection);
            writeVersion(connection, migration.getVersion());
            currentVersion = migration.getVersion();
        }
    }

    private void ensureBookkeepingTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        version INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT OR IGNORE INTO schema_version (id, version) VALUES (1, 0)
                    """);
        }
    }

    private int readCurrentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM schema_version WHERE id = 1")) {
            if (resultSet.next()) {
                return resultSet.getInt("version");
            }
            return 0;
        }
    }

    private void writeVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("UPDATE schema_version SET version = ? WHERE id = 1")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }
}
