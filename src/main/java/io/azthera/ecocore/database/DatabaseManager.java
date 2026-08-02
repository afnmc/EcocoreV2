package io.azthera.ecocore.database;

import io.azthera.ecocore.config.DatabaseConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Top-level entry point for EcoCore's persistence layer.
 * Owns the {@link ConnectionPool} and runs schema migrations on startup
 * via {@link MigrationManager}. DAOs obtain connections through this class.
 */
public final class DatabaseManager {

    private final JavaPlugin plugin;
    private final DatabaseConfig config;
    private final ConnectionPool connectionPool;
    private final MigrationManager migrationManager;

    /**
     * Creates the database manager. Call {@link #initialize()} once
     * during plugin startup before any DAO is used.
     *
     * @param plugin the owning plugin instance
     * @param config the resolved database configuration
     */
    public DatabaseManager(JavaPlugin plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.connectionPool = new ConnectionPool(plugin, config);
        this.migrationManager = new MigrationManager(plugin.getLogger());
    }

    /**
     * Opens the connection pool and applies any pending migrations.
     * Safe to call once at startup; subsequent calls are a no-op if
     * already initialized.
     *
     * @throws SQLException if the pool cannot be opened or a migration fails
     */
    public void initialize() throws SQLException {
        if (connectionPool.isOpen()) {
            return;
        }
        connectionPool.open();

        if (config.isAutoMigrate()) {
            try (Connection connection = connectionPool.getConnection()) {
                migrationManager.migrate(connection);
            }
        }

        plugin.getLogger().info("[EcoCore] Database initialized (" + config.getFileName() + ")");
    }

    /**
     * Borrows a pooled connection. Callers must close it
     * (try-with-resources) to return it to the pool.
     *
     * @return an open JDBC connection
     * @throws SQLException if no connection is available
     */
    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }

    /**
     * Shuts down the connection pool. Should be called from
     * the plugin's {@code onDisable()}.
     */
    public void shutdown() {
        connectionPool.close();
        plugin.getLogger().info("[EcoCore] Database connection pool closed");
    }

    /**
     * Whether the database has been successfully initialized.
     *
     * @return {@code true} if the connection pool is open
     */
    public boolean isReady() {
        return connectionPool.isOpen();
    }
}