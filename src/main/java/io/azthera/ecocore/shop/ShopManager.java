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
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Top-level facade for EcoCore's shop system, tying together the
 * live item catalog, search/sort/favorite/history helpers, and buy
 * transactions. This is the class GUI screens and the {@code /shop}
 * command interact with directly.
 */
public final class ShopManager {

    private final Logger logger;
    private final ShopItemDao shopItemDao;
    private final BuyHistoryDao buyHistoryDao;
    private final ConfigManager configManager;
    private final EconomyEngine economyEngine;

    private final Map<String, ShopItemRecord> catalog = new ConcurrentHashMap<>();
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

    private final ShopCatalogLoader catalogLoader;
    private final ShopSearchEngine searchEngine = new ShopSearchEngine();
    private final ShopSortEngine sortEngine = new ShopSortEngine();
    private final ShopFavoriteManager favoriteManager = new ShopFavoriteManager();
    private final ShopHistoryManager historyManager;
    private final StockManager stockManager;

    /**
     * The outcome of an attempted purchase.
     *
     * @param success    whether the purchase went through
     * @param message    a human-readable outcome reason (only meaningful when {@code success} is false)
     * @param amount     the quantity actually purchased (0 if failed)
     * @param totalPrice the total price charged (0 if failed)
     */
    public record BuyResult(boolean success, String message, int amount, double totalPrice) {
    }

    /**
     * Creates the shop manager and its sub-components. The catalog
     * loader is built here using {@code configManager.getShopItemsConfig()}
     * so {@code shop-items.yml} drives the catalog on every reload.
     *
     * @param logger              plugin logger
     * @param shopItemDao         DAO for the item catalog
     * @param buyHistoryDao       DAO for buy transaction history
     * @param sellHistoryDaoParam DAO for sell transaction history (used only by {@link ShopHistoryManager})
     * @param stockEventDao       DAO for stock change audit events
     * @param shopConfig          resolved shop.yml configuration
     * @param pricesConfig        resolved prices.yml configuration
     * @param configManager       resolved main config manager
     * @param economyEngine       the economy engine used to charge players on purchase
     */
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

    /**
     * Loads (or reloads) the entire item catalog: first syncs
     * definitions from {@code shop-items.yml} into the database
     * (adding new items, updating static fields on existing ones
     * without resetting their live AI-driven price/stock), then loads
     * everything from the database and rebuilds each category's item
     * list. Call once on plugin enable and again whenever
     * {@code /ecocore reload} runs.
     */
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

    public ShopItemRecord getItem(String itemId) {
        return catalog.get(itemId);
    }

    public Map<String, ShopCategory> getCategories() {
        return categories;
    }

    public List<ShopItemRecord> getAllItems() {
        return List.copyOf(catalog.values());
    }

    /**
     * Returns the live, mutable catalog map backing this manager,
     * shared with {@link StockManager} and used by
     * {@code RestockScheduler} to iterate items without allocating a
     * defensive copy every restock pass. Callers should not put/remove
     * entries directly - go through {@link #loadCatalog()} instead.
     *
     * @return the live catalog map
     */
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
     * stock, GIVES the purchased items to the player's inventory
     * (dropping any overflow on the ground if their inventory is
     * full), and records the transaction.
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

            if (!stockManager.consumeForBuy(itemId, actualAmount)) {
                return new BuyResult(false, "stock-update-failed", 0, 0);
            }

            boolean charged = economyEngine.withdraw(playerUuid, totalPrice, TransactionLogger.REASON_SHOP_BUY);
            if (!charged) {
                stockManager.restock(itemId, actualAmount, StockManager.EVENT_ADMIN);
                return new BuyResult(false, "insufficient-funds", 0, 0);
            }

            giveItemToPlayer(playerUuid, item, actualAmount);

            try {
                buyHistoryDao.insert(playerUuid, itemId, actualAmount, item.getCurrentPrice(), totalPrice);
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to record buy history for "
                        + playerUuid + "/" + itemId + ": " + exception.getMessage());
            }

            return new BuyResult(true, "ok", actualAmount, totalPrice);
        }
    }

    /**
     * Hands purchased items to the buying player's inventory,
     * splitting into multiple stacks if the amount exceeds the
     * material's max stack size, and dropping any overflow on the
     * ground at their feet if their inventory doesn't have room.
     *
     * <p>If the player is currently offline (e.g. a future
     * console/API-triggered purchase), the items are forfeited - this
     * method is only ever called synchronously from an online
     * player's own GUI click today, so this path is a defensive
     * no-op rather than an active mailbox system.
     *
     * @param playerUuid the buying player's uuid
     * @param item       the purchased item record
     * @param amount     total quantity purchased
     */
    private void giveItemToPlayer(UUID playerUuid, ShopItemRecord item, int amount) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null) {
            logger.warning("[EcoCore] Player " + playerUuid + " went offline mid-purchase; "
                    + amount + "x " + item.getId() + " was paid for but not delivered.");
            return;
        }

        Material material = ItemUtils.safeMaterial(item.getMaterial());
        int remaining = amount;
        int maxStackSize = new ItemStack(material).getMaxStackSize();

        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack stack = new ItemStack(material, stackAmount);

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack leftoverStack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverStack);
            }

            remaining -= stackAmount;
        }
    }

    public StockManager getStockManager() {
        return stockManager;
    }

    public ShopSortEngine getSortEngine() {
        return sortEngine;
    }
            }
