package io.azthera.ecocore.sell;

import io.azthera.ecocore.config.PricesConfig;
import io.azthera.ecocore.database.dao.SellHistoryDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.hook.ItemIdentityResolver;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
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
 * enforces blacklist/whitelist rules, and executes the sale (charging
 * the shop's stock inflow and paying the player).
 *
 * <p>Selling does not consume shop stock directly - it instead feeds
 * the AI engine's supply signal (see {@code SupplyDemandAnalyzer}),
 * modeling "players selling to the shop" as increasing available
 * supply for future AI pricing cycles, without artificially inflating
 * the shop's purchasable stock count.
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

    /**
     * Creates the sell manager.
     *
     * @param logger            plugin logger
     * @param shopManager       shared shop manager, used to resolve catalog items and material->id lookups
     * @param economyEngine     economy engine used to pay players
     * @param sellHistoryDao    DAO for recording sell transactions
     * @param pricesConfig      resolved prices.yml configuration
     * @param blacklistManager  blacklist enforcement for sellable items
     * @param whitelistManager  optional whitelist enforcement for sellable items
     * @param identityResolver  resolves an ItemStack's identity across vanilla and custom-item plugins
     */
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

    /**
     * Attempts to resolve a catalog {@link ShopItemRecord} matching the
     * given item stack's material, ignoring blacklist/whitelist checks.
     *
     * @param stack the item stack to resolve
     * @return the matching catalog item, or {@code null} if none is sellable for this material
     */
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

    /**
     * Checks whether a single item stack is currently sellable
     * (not blacklisted, whitelist-permitted if enabled, and has a
     * matching tradeable catalog entry).
     *
     * @param stack the item stack to check
     * @return {@code true} if it can be sold right now
     */
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

    /**
     * Sells a single item stack in full on behalf of a player.
     *
     * @param playerUuid the selling player's uuid
     * @param stack      the item stack to sell (its amount is fully sold)
     * @return the sell result
     */
    public SellResult sellSingle(UUID playerUuid, ItemStack stack) {
        if (!isSellable(stack)) {
            return new SellResult(false, 0, 0.0, 1);
        }

        ShopItemRecord catalogItem = resolveCatalogItem(stack);
        int amount = stack.getAmount();
        double payout = profitCalculator.computeTotalSellPrice(catalogItem, amount);

        economyEngine.deposit(playerUuid, payout, TransactionLogger.REASON_SHOP_SELL);
        recordSale(playerUuid, catalogItem, amount, payout);

        return new SellResult(true, amount, payout, 0);
    }

    /**
     * Sells every sellable item in a player's inventory, replacing sold
     * stacks with air.
     *
     * @param playerUuid the selling player's uuid
     * @param inventory  the inventory to sweep (usually the player's own)
     * @return the aggregated sell result across all sold stacks
     */
    public SellResult sellAll(UUID playerUuid, Inventory inventory) {
        return sellMatching(playerUuid, inventory, stack -> true);
    }

    /**
     * Sells every sellable item currently in a container inventory
     * (used for the "Sell Chest" feature), replacing sold stacks with air.
     *
     * @param playerUuid the selling player's uuid
     * @param container  the container inventory to sweep
     * @return the aggregated sell result across all sold stacks
     */
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
            recordSale(playerUuid, itemById.get(itemId), amount, unitPrice * amount);
        }

        return new SellResult(true, totalAmount, totalPayout, skipped);
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

    public SellBlacklistManager getBlacklistManager() {
        return blacklistManager;
    }

    public SellWhitelistManager getWhitelistManager() {
        return whitelistManager;
    }
}