package io.azthera.ecocore.shop;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.config.ShopConfig;
import io.azthera.ecocore.database.dao.BuyHistoryDao;
import io.azthera.ecocore.database.dao.ShopItemDao;
import io.azthera.ecocore.database.dao.StockEventDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.azthera.ecocore.jobs.JobsManager;
import java.util.logging.Logger;

/**
 * Top-level facade for EcoCore's shop system, tying together the
 * live item catalog, search/sort/favorite/history helpers, and buy
 * transactions.
 */
public final class ShopManager {

    private final Logger logger;
    private final ShopItemDao shopItemDao;
    private final BuyHistoryDao buyHistoryDao;
    private final ConfigManager configManager;
    private final EconomyEngine economyEngine;

    private final Map<String, ShopItemRecord> catalog = new ConcurrentHashMap<>();
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private volatile JobsManager jobsManager;

    private final ShopCatalogLoader catalogLoader;
    private final ShopSearchEngine searchEngine = new ShopSearchEngine();
    private final ShopSortEngine sortEngine = new ShopSortEngine();
    private final ShopFavoriteManager favoriteManager = new ShopFavoriteManager();
    private final ShopHistoryManager historyManager;
    private final StockManager stockManager;

    public record BuyResult(boolean success, String message, int amount, double totalPrice) {
    }

    public ShopManager(Logger logger, ShopItemDao shopItemDao, BuyHistoryDao buyHistoryDao,
                        io.azthera.ecocore.database.dao.SellHistoryDao sellHistoryDaoParam,
                        StockEventDao stockEventDao, ShopConfig shopConfig,
                        io.azthera.ecocore.config.PricesConfig pricesConfig,
                        ConfigManager configManager, EconomyEngine economyEngine) {
        this.logger = logger;
        this.shopItemDao = shopItemDao;
        this.buyHistoryDao = buyHistoryDao;
        this.configManager = configManager;
        this.economyEngine = economyEngine;

        this.catalogLoader = new ShopCatalogLoader(logger, shopItemDao, shopConfig, pricesConfig,
                configManager.getShopItemsConfig());
        this.historyManager = new ShopHistoryManager(buyHistoryDao, sellHistoryDaoParam);
        this.stockManager = new StockManager(logger, shopItemDao, stockEventDao, catalog);

        for (ShopConfig.CategoryDefinition definition : shopConfig.getCategories()) {
            categories.put(definition.id(), new ShopCategory(definition));
        }
    }

    public void loadCatalog() {
        try {
            catalog.clear();
            catalog.putAll(catalogLoader.loadCatalog());
            rebuildCategoryIndex();
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to load shop catalog: " + exception.getMessage());
        }
    }

    private void rebuildCategoryIndex() {
        Map<String, List<ShopItemRecord>> byCategory = new LinkedHashMap<>();
        for (ShopItemRecord item : catalog.values()) {
            byCategory.computeIfAbsent(item.getCategory(), key -> new java.util.ArrayList<>()).add(item);
        }
        for (ShopCategory category : categories.values()) {
            category.setItems(byCategory.getOrDefault(category.getId(), List.of()));
        }
    }

    public void setJobsManager(JobsManager jobsManager) {
        this.jobsManager = jobsManager;
    }

    public ShopItemRecord getItem(String itemId) {
        return catalog.get(itemId);
    }

    public Map<String, ShopCategory> getCategories() {
        return categories;
    }

    public List<ShopItemRecord> getAllItems() {
        return List.copyOf(catalog.values());
    }

    public Map<String, ShopItemRecord> getLiveCatalog() {
        return catalog;
    }

    public List<ShopItemRecord> search(String query) {
        return searchEngine.search(catalog.values(), query);
    }

    public List<ShopItemRecord> sort(List<ShopItemRecord> items, ShopSortEngine.SortMode mode) {
        return sortEngine.sort(items, mode);
    }

    public boolean toggleFavorite(UUID playerUuid, String itemId) {
        return favoriteManager.toggle(playerUuid, itemId);
    }

    public boolean isFavorite(UUID playerUuid, String itemId) {
        return favoriteManager.isFavorite(playerUuid, itemId);
    }

    public ShopFavoriteManager getFavoriteManager() {
        return favoriteManager;
    }

    public List<io.azthera.ecocore.model.TransactionRecord> getHistory(UUID playerUuid, int limit) throws SQLException {
        return historyManager.getRecentHistory(playerUuid, limit);
    }

    /**
     * Attempts to purchase an item on behalf of a player: validates
     * tradeability/stock, charges the player's balance, consumes
     * stock, gives the purchased items to the player's inventory
     * (dropping overflow if full), and records the transaction.
     *
     * <p>All mutating steps are coordinated so that a failure at any
     * stage triggers a full rollback (stock is restored and money is
     * refunded) — a player can never be charged without receiving items.
     *
     * @param playerUuid the buying player's uuid
     * @param itemId     the item id to purchase
     * @param amount     the quantity requested, must be positive
     * @return the outcome of the purchase attempt
     */
    public BuyResult buy(UUID playerUuid, String itemId, int amount) {
        if (amount <= 0) {
            return new BuyResult(false, "invalid-amount", 0, 0);
        }

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            return new BuyResult(false, "player-offline", 0, 0);
        }

        ShopItemRecord item = catalog.get(itemId);
        if (item == null || !item.isTradeable()) {
            return new BuyResult(false, "not-tradeable", 0, 0);
        }

        synchronized (item) {
            if (item.isSoldOut()) {
                return new BuyResult(false, "sold-out", 0, 0);
            }

            int actualAmount = Math.min(amount, item.getStock());
            double totalPrice = item.getCurrentPrice() * actualAmount;

            if (!economyEngine.has(playerUuid, totalPrice)) {
                return new BuyResult(false, "insufficient-funds", 0, 0);
            }

            // Step 1: consume stock (DB + memory).
            if (!stockManager.consumeForBuy(itemId, actualAmount)) {
                return new BuyResult(false, "stock-update-failed", 0, 0);
            }

            // Step 2: withdraw money. If this fails, restore the stock we just consumed.
            boolean charged = economyEngine.withdraw(playerUuid, totalPrice, TransactionLogger.REASON_SHOP_BUY);
            if (!charged) {
                stockManager.restock(itemId, actualAmount, StockManager.EVENT_ADMIN);
                return new BuyResult(false, "insufficient-funds", 0, 0);
            }

            // Step 3: record history in database BEFORE giving items to inventory.
            // If history fails, rollback money and stock so player loses neither money nor items.
            try {
                buyHistoryDao.insert(playerUuid, itemId, actualAmount, item.getCurrentPrice(), totalPrice);
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to record buy history for "
                        + playerUuid + "/" + itemId + ": " + exception.getMessage());
                economyEngine.deposit(playerUuid, totalPrice, "shop_buy_refund");
                stockManager.restock(itemId, actualAmount, StockManager.EVENT_ADMIN);
                return new BuyResult(false, "database-error", 0, 0);
            }

            // Step 4: verify player is still online before delivery
            player = Bukkit.getPlayer(playerUuid);
            if (player == null || !player.isOnline()) {
                logger.warning("[EcoCore] Player " + playerUuid + " went offline mid-purchase; "
                        + "refunding " + totalPrice + " and restoring " + actualAmount + "x " + itemId + ".");
                economyEngine.deposit(playerUuid, totalPrice, "shop_buy_refund");
                stockManager.restock(itemId, actualAmount, StockManager.EVENT_ADMIN);
                return new BuyResult(false, "player-offline", 0, 0);
            }

            // Step 5: deliver items now that payment, stock and history are secured
            // giveOrDrop() either inserts the item or drops overflow at the player;
            // it does not throw for ordinary inventory-full conditions. Therefore
            // delivery is the terminal step and must not trigger a refund after
            // items may already have been delivered.
            ItemUtils.giveOrDrop(player, ItemUtils.createShopItem(item, 1), actualAmount);

            JobsManager jobs = jobsManager;
            if (jobs != null) {
                jobs.processAction(playerUuid, "BUY_ITEM_" + item.getMaterial().toUpperCase(),
                        1.0, actualAmount);
            }

            return new BuyResult(true, "ok", actualAmount, totalPrice);
        }
    }

    /** Updates the live current price and persists it for admin price edits. */
    public boolean setCurrentPrice(String itemId, double price) {
        if (!Double.isFinite(price) || price <= 0) return false;
        ShopItemRecord item = catalog.get(itemId);
        if (item == null) return false;
        synchronized (item) {
            item.setCurrentPrice(price);
            try {
                shopItemDao.updatePrice(itemId, item.getCurrentPrice(), item.getUpdatedAt());
                return true;
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to persist admin price change for " + itemId + ": " + exception.getMessage());
                return false;
            }
        }
    }

    public StockManager getStockManager() {
        return stockManager;
    }

    public ShopSortEngine getSortEngine() {
        return sortEngine;
    }
}
