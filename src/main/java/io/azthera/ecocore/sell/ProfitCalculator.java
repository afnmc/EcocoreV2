package io.azthera.ecocore.sell;

import io.azthera.ecocore.config.PricesConfig;
import io.azthera.ecocore.model.ShopItemRecord;

/**
 * Computes the sell-side price for an item from its live buy price
 * and the configured buy/sell spread, so selling to the shop is
 * always somewhat less profitable than buying from it (preventing
 * risk-free buy/sell arbitrage loops).
 */
public final class ProfitCalculator {

    private final PricesConfig pricesConfig;

    /**
     * Creates a profit calculator.
     *
     * @param pricesConfig resolved prices.yml configuration (spread bounds)
     */
    public ProfitCalculator(PricesConfig pricesConfig) {
        this.pricesConfig = pricesConfig;
    }

    /**
     * Computes the current sell (shop buys from player) price for a
     * single unit of an item, derived from its live buy price minus
     * the configured spread.
     *
     * @param item the item to price
     * @return the per-unit sell price, never negative
     */
    public double computeUnitSellPrice(ShopItemRecord item) {
        double spreadPercent = resolveSpreadPercent(item);
        double sellPrice = item.getCurrentPrice() * (1.0 - (spreadPercent / 100.0));
        return Math.max(0.0, sellPrice);
    }

    /**
     * Computes the total amount a player would receive for selling a
     * given quantity of an item, and previews the resulting profit
     * relative to the item's base price (used by {@code SellConfirmGui}'s
     * "preview profit" feature).
     *
     * @param item   the item being sold
     * @param amount the quantity being sold
     * @return the total sell payout
     */
    public double computeTotalSellPrice(ShopItemRecord item, int amount) {
        return computeUnitSellPrice(item) * amount;
    }

    private double resolveSpreadPercent(ShopItemRecord item) {
        double spread = pricesConfig.getDefaultSpreadPercent();
        return Math.max(pricesConfig.getMinSpreadPercent(), Math.min(pricesConfig.getMaxSpreadPercent(), spread));
    }
}