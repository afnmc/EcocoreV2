package io.azthera.ecocore.sell;

import io.azthera.ecocore.config.PricesConfig;
import io.azthera.ecocore.database.dao.SellHistoryDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.hook.ItemIdentityResolver;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import io.azthera.ecocore.shop.StockManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Top-level facade for EcoCore's sell system: matches raw inventory
 * item stacks to catalog items, prices them via {@link ProfitCalculator},
 * enforces blacklist/whitelist rules, and executes the sale (paying
 * the player and restocking the shop with what was sold).
 *
 * <p>Selling an item now DOES add units back to that item's live shop
 * stock (capped at its max stock, same as any other restock) - a
 * player selling diamonds increases how many diamonds the shop has
 * available to sell back out. This also naturally feeds the AI
 * engine's supply signal via the resulting higher stock level.
 */
public final class SellManager {

    private final Logger logger;
    private final ShopManager shopManager;
    private final EconomyEngine economyEngine;
    private final SellHistoryDao sellHistoryDao;
    private final ProfitCalculator profitCalculator;
    private final SellBlacklistManager blacklistManager;
    private final SellWhitelistManager whitelistManager;
    private final ItemIdentityResolver identityResolver;

    /**
     * The outcome of a sell operation, which may cover multiple item stacks at once.
     *
     * @param success      whether at least one item was sold
     * @param totalAmount  total number of individual items sold across all stacks
     * @param totalPayout  total money paid out to the player
     * @param itemsSkipped number of item stacks that could not be sold (blacklisted/unmatched/no price)
     */
    public record SellResult(boolean success, int totalAmount, double totalPayout, int itemsSkipped) {
    }

    public SellManager(Logger logger, ShopManager shopManager, EconomyEngine economyEngine,
                        SellHistoryDao sellHistoryDao, PricesConfig pricesConfig,
                        SellBlacklistManager blacklistManager, SellWhitelistManager whitelistManager,
                        ItemIdentityResolver identityResolver) {
        this.logger = logger;
        this.shopManager = shopManager;
        this.economyEngine = economyEngine;
        this.sellHistoryDao = sellHistoryDao;
        this.profitCalculator = new ProfitCalculator(pricesConfig);
        this.blacklistManager = blacklistManager;
        this.whitelistManager = whitelistManager;
        this.identityResolver = identityResolver;
    }

    public ShopItemRecord resolveCatalogItem(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        String material = stack.getType().name();
        for (ShopItemRecord item : shopManager.getAllItems()) {
            if (item.getMaterial().equalsIgnoreCase(material) && item.isTradeable()) {
                return item;
            }
        }
        return null;
    }

    public boolean isSellable(ItemStack stack) {
        if (blacklistManager.isBlacklisted(stack)) {
            return false;
        }
        ShopItemRecord catalogItem = resolveCatalogItem(stack);
        if (catalogItem == null) {
            return false;
        }
        return whitelistManager.isAllowed(catalogItem.getId());
    }

    public SellResult sellSingle(UUID playerUuid, ItemStack stack) {
        if (!isSellable(stack)) {
            return new SellResult(false, 0, 0.0, 1);
        }

        ShopItemRecord catalogItem = resolveCatalogItem(stack);
        int amount = stack.getAmount();
        double payout = profitCalculator.computeTotalSellPrice(catalogItem, amount);

        economyEngine.deposit(playerUuid, payout, TransactionLogger.REASON_SHOP_SELL);
        restockFromSale(catalogItem.getId(), amount);
        recordSale(playerUuid, catalogItem, amount, payout);

        return new SellResult(true, amount, payout, 0);
    }

    public SellResult sellAll(UUID playerUuid, Inventory inventory) {
        return sellMatching(playerUuid, inventory, stack -> true);
    }

    public SellResult sellChest(UUID playerUuid, Inventory container) {
        return sellMatching(playerUuid, container, stack -> true);
    }

    private SellResult sellMatching(UUID playerUuid, Inventory inventory, java.util.function.Predicate<ItemStack> filter) {
        Map<String, Double> unitPriceByItemId = new HashMap<>();
        Map<String, Integer> amountByItemId = new HashMap<>();
        Map<String, ShopItemRecord> itemById = new HashMap<>();

        int skipped = 0;
        double totalPayout = 0.0;
        int totalAmount = 0;

        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() == Material.AIR || !filter.test(stack)) {
                continue;
            }

            if (!isSellable(stack)) {
                skipped++;
                continue;
            }

            ShopItemRecord catalogItem = resolveCatalogItem(stack);
            double unitPrice = profitCalculator.computeUnitSellPrice(catalogItem);
            double stackPayout = unitPrice * stack.getAmount();

            totalPayout += stackPayout;
            totalAmount += stack.getAmount();

            unitPriceByItemId.put(catalogItem.getId(), unitPrice);
            amountByItemId.merge(catalogItem.getId(), stack.getAmount(), Integer::sum);
            itemById.put(catalogItem.getId(), catalogItem);

            inventory.setItem(slot, null);
        }

        if (totalAmount == 0) {
            return new SellResult(false, 0, 0.0, skipped);
        }

        economyEngine.deposit(playerUuid, totalPayout, TransactionLogger.REASON_SHOP_SELL);

        for (Map.Entry<String, Integer> entry : amountByItemId.entrySet()) {
            String itemId = entry.getKey();
            int amount = entry.getValue();
            double unitPrice = unitPriceByItemId.get(itemId);
            restockFromSale(itemId, amount);
            recordSale(playerUuid, itemById.get(itemId), amount, unitPrice * amount);
        }

        return new SellResult(true, totalAmount, totalPayout, skipped);
    }

    /**
     * Adds the sold quantity back to the item's live shop stock,
     * capped at its max stock like any other restock. This is what
     * makes selling to the shop actually replenish what's available
     * to buy.
     *
     * @param itemId the item id that was sold
     * @param amount the quantity sold
     */
    private void restockFromSale(String itemId, int amount) {
        shopManager.getStockManager().restock(itemId, amount, "PLAYER_SELL");
    }

    private void recordSale(UUID playerUuid, ShopItemRecord item, int amount, double totalPrice) {
        try {
            double unitPrice = amount > 0 ? totalPrice / amount : 0.0;
            sellHistoryDao.insert(playerUuid, item.getId(), amount, unitPrice, totalPrice);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to record sell history for "
                    + playerUuid + "/" + item.getId() + ": " + exception.getMessage());
        }
    }

    public ProfitCalculator getProfitCalculator() {
        return profitCalculator;
    }

    /**
     * Computes the total sell value a stack would currently fetch,
     * without actually selling it - used by the Sell GUI (Revisi 15)
     * to show a live running total of the deposit area's contents.
     *
     * @param stack the stack to preview, may be {@code null}
     * @return the total payout this stack would currently yield, or 0 if unsellable
     */
    public double previewSellValue(ItemStack stack) {
        if (stack == null || !isSellable(stack)) {
            return 0.0;
        }
        ShopItemRecord catalogItem = resolveCatalogItem(stack);
        if (catalogItem == null) {
            return 0.0;
        }
        double unitPrice = profitCalculator.computeUnitSellPrice(catalogItem);
        return unitPrice * stack.getAmount();
    }

    public SellBlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    public SellWhitelistManager getWhitelistManager() {
        return whitelistManager;
    }
    }