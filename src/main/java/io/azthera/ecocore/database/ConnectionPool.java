package io.azthera.ecocore.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.azthera.ecocore.config.DatabaseConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wraps a HikariCP-backed connection pool targeting a local SQLite
 * database file inside the plugin's data folder.
 */
public final class ConnectionPool {

    private final JavaPlugin plugin;
    private final DatabaseConfig config;
    private HikariDataSource dataSource;

    /**
     * Creates a connection pool wrapper. Call {@link #open()} to actually
     * initialize the underlying HikariCP data source.
     *
     * @param plugin the owning plugin instance
     * @param config the resolved database configuration
     */
    public ConnectionPool(JavaPlugin plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Initializes the HikariCP data source pointing at the SQLite file
     * defined in {@code database.yml}. Creates the plugin data folder
     * and database file's parent directory if missing.
     */
    public void open() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File dbFile = new File(dataFolder, config.getFileName());

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
        hikariConfig.setLeakDetectionThreshold(config.getLeakDetectionThresholdMs());
        hikariConfig.setPoolName("EcoCore-Pool");

        // SQLite is single-writer; keep connections cooperative under WAL mode.
        hikariConfig.addDataSourceProperty("journal_mode", "WAL");
        hikariConfig.addDataSourceProperty("foreign_keys", "ON");

        this.dataSource = new HikariDataSource(hikariConfig);
    }

    /**
     * Borrows a connection from the pool. Callers are responsible for
     * closing it (try-with-resources is recommended) so it returns to the pool.
     *
     * @return a pooled JDBC connection
     * @throws SQLException if a connection could not be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException("ConnectionPool has not been opened yet");
        }
        return dataSource.getConnection();
    }

    /**
     * Shuts down the pool and releases all held connections.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Whether the pool is currently open and accepting connection requests.
     *
     * @return {@code true} if open
     */
    public boolean isOpen() {
        return dataSource != null && !dataSource.isClosed();
    }
}