package io.azthera.ecocore.database.migrations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initial schema: player accounts, the money ledger, the shop item
 * catalog (without max_stock, added in {@link V2__ShopStock}), and
 * the buy/sell/market history tables.
 */
public final class V1__InitialSchema implements Migration {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Initial schema: player accounts, money ledger, shop items, trade history";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_accounts (
                        uuid TEXT PRIMARY KEY,
                        last_known_name TEXT,
                        balance REAL NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS money_ledger (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        change_amount REAL NOT NULL,
                        balance_after REAL NOT NULL,
                        reason TEXT,
                        created_at INTEGER NOT NULL,
                        FOREIGN KEY (player_uuid) REFERENCES player_accounts(uuid)
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS shop_items (
                        id TEXT PRIMARY KEY,
                        category TEXT NOT NULL,
                        material TEXT NOT NULL,
                        namespaced_key TEXT,
                        base_price REAL NOT NULL,
                        current_price REAL NOT NULL,
                        min_price REAL NOT NULL,
                        max_price REAL NOT NULL,
                        stock INTEGER NOT NULL,
                        elasticity REAL NOT NULL DEFAULT 1.0,
                        tradeable INTEGER NOT NULL DEFAULT 1,
                        updated_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS market_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        item_id TEXT NOT NULL,
                        price REAL NOT NULL,
                        stock INTEGER NOT NULL,
                        transactions_in INTEGER NOT NULL DEFAULT 0,
                        transactions_out INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS buy_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        unit_price REAL NOT NULL,
                        total_price REAL NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sell_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        unit_price REAL NOT NULL,
                        total_price REAL NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);

            statement.execute("CREATE INDEX IF NOT EXISTS idx_market_history_item ON market_history(item_id, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_buy_history_player ON buy_history(player_uuid, created_at)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_sell_history_player ON sell_history(player_uuid, created_at)");
        }
    }
}