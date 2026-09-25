package io.azthera.ecocore.shop;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.EcoCorePlugin;
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
                configManager.getShopItemsConfig(), configManager.getBlacklistConfig());
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
            refreshCategories();
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

    /** Rebuilds the category index after an admin category/item edit. */
    public synchronized void refreshCategories() {
        categories.clear();
        for (ShopConfig.CategoryDefinition definition : configManager.getShopConfig().getCategories()) {
            categories.put(definition.id(), new ShopCategory(definition));
        }
        rebuildCategoryIndex();
    }

    /** Adds a category to shop.yml and reloads the live category index. */
    public synchronized boolean addCategory(String id, String displayName, String iconMaterial, String iconData) {
        if (id == null || displayName == null || iconMaterial == null || id.isBlank() || displayName.isBlank()) return false;
        String normalized = id.toLowerCase().replaceAll("[^a-z0-9_-]", "-").replaceAll("-{2,}", "-");
        if (normalized.isBlank() || categories.containsKey(normalized)) return false;
        try {
            java.io.File file = new java.io.File(EcoCorePlugin.getInstance().getDataFolder(), "shop.yml");
            org.bukkit.configuration.file.YamlConfiguration yaml =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            java.util.List<java.util.Map<?, ?>> raw = new java.util.ArrayList<>(yaml.getMapList("categories"));
            java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", normalized);
            entry.put("display-name", displayName);
            entry.put("icon", iconMaterial.toUpperCase());
            if (iconData != null && !iconData.isBlank()) entry.put("icon-data", iconData);
            int slot = 10;
            java.util.Set<Integer> used = new java.util.HashSet<>();
            for (java.util.Map<?, ?> m : raw) if (m.get("slot") instanceof Number n) used.add(n.intValue());
            while (used.contains(slot) && slot < 54) slot++;
            entry.put("slot", slot);
            raw.add(entry);
            yaml.set("categories", raw);
            yaml.save(file);
            configManager.reloadShopConfigOnly();
            refreshCategories();
            return true;
        } catch (Exception exception) {
            logger.severe("[EcoCore] Failed to add shop category: " + exception.getMessage());
            return false;
        }
    }

    /** Adds the exact ItemStack held by an admin to a category at the requested price. */
    public synchronized String addItemFromHand(String categoryId, org.bukkit.inventory.ItemStack held, double price) {
        if (held == null || held.getType().isAir() || price <= 0 || !Double.isFinite(price)
                || !categories.containsKey(categoryId)) return null;
        String material = held.getType().name();
        if (configManager.getBlacklistConfig().getMaterials().contains(material)) return null;

        String base = material.toLowerCase();
        String id = base;
        int suffix = 2;
        while (true) {
            boolean idExists = catalog.containsKey(id);
            if (!idExists) {
                for (io.azthera.ecocore.config.ShopItemsConfig.ItemDefinition existing
                        : configManager.getShopItemsConfig().getItems()) {
                    if (existing.id().equals(id)) {
                        idExists = true;
                        break;
                    }
                }
            }
            if (!idExists) break;
            id = base + "-" + suffix++;
        }

        double minPrice = price * configManager.getPricesConfig().getGlobalMinPriceMultiplier();
        double maxPrice = price * configManager.getPricesConfig().getMaxPriceMultiplierForCategory(categoryId);
        int maxStock = configManager.getShopConfig().getDefaultMaxStock();
        String serialized = io.azthera.ecocore.utils.ItemUtils.serialize(logger, new org.bukkit.inventory.ItemStack[]{held.clone()});

        io.azthera.ecocore.config.ShopItemsConfig.ItemDefinition def =
                new io.azthera.ecocore.config.ShopItemsConfig.ItemDefinition(
                        id, categoryId, material, price, -1.0, minPrice, maxPrice, maxStock,
                        configManager.getPricesConfig().getElasticityForCategory(categoryId), true, "", "", serialized);
        configManager.getShopItemsConfig().addItem(def);
        loadCatalog();
        return id;
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

            // Step 5: deliver items now that payment, stock and history are secured.
            // giveOrDrop() either inserts the item or drops overflow at the player;
            // it does not throw for ordinary inventory-full conditions. Therefore
            // delivery is the terminal step and must not trigger a refund after
            // items may already have been delivered.
            try {
                ItemUtils.giveOrDrop(player, ItemUtils.createShopItem(item, 1), actualAmount);
            } catch (Exception deliveryException) {
                // Critical: item creation or delivery threw unexpectedly.
                // Refund the player immediately to maintain atomicity.
                logger.severe("[EcoCore] Item delivery failed for " + playerUuid + "/" + itemId
                        + ": " + deliveryException.getMessage() + " — refunding.");
                economyEngine.deposit(playerUuid, totalPrice, "shop_buy_refund");
                stockManager.restock(itemId, actualAmount, StockManager.EVENT_ADMIN);
                return new BuyResult(false, "delivery-failed", 0, 0);
            }

            // Notify the player if their inventory was probably full
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(configManager.getMessagesConfig()
                        .getWithPrefix("shop.overflow-dropped"));
            }

            JobsManager jobs = jobsManager;
            if (jobs != null) {
                jobs.processAction(playerUuid, "BUY_ITEM_" + item.getMaterial().toUpperCase(),
                        1.0, actualAmount);
            }

            return new BuyResult(true, "ok", actualAmount, totalPrice);
        }
    }

    /** Updates the live current price and persists it to BOTH database AND YAML. */
    public boolean setCurrentPrice(String itemId, double price) {
        if (!Double.isFinite(price) || price <= 0) return false;
        ShopItemRecord item = catalog.get(itemId);
        if (item == null) return false;
        synchronized (item) {
            item.setCurrentPrice(price);
            try {
                shopItemDao.updatePrice(itemId, item.getCurrentPrice(), item.getUpdatedAt());
                // Also persist to YAML to prevent desync on restart
                catalogLoader.updatePriceInYaml(itemId, price);
                return true;
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to persist admin price change for " + itemId + ": " + exception.getMessage());
                return false;
            }
        }
    }

    /** Updates the sell price for an item and persists it. */
    public boolean setSellPrice(String itemId, double price) {
        if (!Double.isFinite(price) || price < 0) return false;
        ShopItemRecord item = catalog.get(itemId);
        if (item == null) return false;
        synchronized (item) {
            item.setSellPrice(price);
            catalogLoader.updateSellPriceInYaml(itemId, price);
            return true;
        }
    }

    /** Updates item category and persists it. */
    public boolean setCategory(String itemId, String category) {
        ShopItemRecord item = catalog.get(itemId);
        if (item == null) return false;
        synchronized (item) {
            item.setCategory(category);
            try {
                shopItemDao.updateCategory(itemId, category);
                catalogLoader.updateCategoryInYaml(itemId, category);
                rebuildCategoryIndex();
                return true;
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to persist category change for " + itemId + ": " + exception.getMessage());
                return false;
            }
        }
    }

    /** Toggles the tradeable status for an item and persists it. */
    public boolean setTradeable(String itemId, boolean tradeable) {
        ShopItemRecord item = catalog.get(itemId);
        if (item == null) return false;
        synchronized (item) {
            item.setTradeable(tradeable);
            try {
                shopItemDao.updateTradeable(itemId, tradeable);
                catalogLoader.updateTradeableInYaml(itemId, tradeable);
                return true;
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to persist tradeable change for " + itemId + ": " + exception.getMessage());
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

    public ShopCatalogLoader getCatalogLoader() {
        return catalogLoader;
    }
}
