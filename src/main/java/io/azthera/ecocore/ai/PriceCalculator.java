package io.azthera.ecocore.ai;

import io.azthera.ecocore.config.AiConfig;
import io.azthera.ecocore.config.PricesConfig;
import io.azthera.ecocore.model.ShopItemRecord;

/**
 * Combines a feature vector, configured base weights, an item's
 * learned weight profile, elasticity, and the current macro-economic
 * multiplier into a new AI-computed price for a single item.
 */
public final class PriceCalculator {

    private final AiConfig aiConfig;
    private final PricesConfig pricesConfig;

    /**
     * Creates a price calculator.
     *
     * @param aiConfig     resolved ai.yml configuration (base weights, change bounds, smoothing)
     * @param pricesConfig resolved prices.yml configuration (elasticity/multiplier bounds, rounding)
     */
    public PriceCalculator(AiConfig aiConfig, PricesConfig pricesConfig) {
        this.aiConfig = aiConfig;
        this.pricesConfig = pricesConfig;
    }

    /**
     * Computes the new price for an item this cycle.
     *
     * @param item                the item's current record (used for current price and elasticity bounds)
     * @param features            this cycle's computed feature vector
     * @param learnedProfile      the item's learned per-feature weight multipliers
     * @param economicMultiplier  the current macro-economic price multiplier (from {@code InflationConfig.StateEffect})
     * @return the new price, clamped to the item's min/max price and this cycle's max-change bounds
     */
    public double computeNewPrice(ShopItemRecord item, AiFeatureVector features,
                                   AiWeightProfile learnedProfile, double economicMultiplier) {

        double pressure = 0.0;
        pressure += signed(features.demand()) * weighted("demand", learnedProfile);
        pressure += signed(1.0 - features.supply()) * weighted("supply", learnedProfile);
        pressure += signed(features.transactionVolume()) * weighted("transaction-volume", learnedProfile);
        pressure += signed(features.playerCount()) * weighted("player-count", learnedProfile);
        pressure += signed(features.itemsOut()) * weighted("items-out", learnedProfile);
        pressure += signed(1.0 - features.itemsIn()) * weighted("items-in", learnedProfile);
        pressure += signed(features.inflation()) * weighted("inflation", learnedProfile);
        pressure += signed(1.0 - features.deflation()) * weighted("deflation", learnedProfile);
        pressure += signed(1.0 - features.storageLevel()) * weighted("storage-level", learnedProfile);
        pressure += signed(1.0 - features.soldVolume()) * weighted("sold-volume", learnedProfile);
        pressure += signed(features.boughtVolume()) * weighted("bought-volume", learnedProfile);
        pressure += signed(1.0 - features.marketSaturation()) * weighted("market-saturation", learnedProfile);

        // pressure is roughly in [-1, 1]: positive pushes price up, negative pushes it down.
        double elasticity = item.getElasticity() > 0 ? item.getElasticity() : pricesConfig.getBaseElasticity();
        double rawChangePercent = pressure * elasticity * 10.0;

        double maxUp = aiConfig.getMaxPriceChangeUpPercent();
        double maxDown = -aiConfig.getMaxPriceChangeDownPercent();
        double clampedChangePercent = Math.max(maxDown, Math.min(maxUp, rawChangePercent));

        double basePrice = item.getCurrentPrice() > 0 ? item.getCurrentPrice() : item.getBasePrice();
        double target = basePrice * (1.0 + (clampedChangePercent / 100.0)) * economicMultiplier;

        // Smooth toward the target rather than jumping straight to it, so
        // prices trend rather than snap between AI cycles.
        double smoothing = aiConfig.getSmoothingFactor();
        double smoothedPrice = basePrice + ((target - basePrice) * smoothing);

        double minPrice = item.getMinPrice() > 0
                ? item.getMinPrice()
                : item.getBasePrice() * pricesConfig.getGlobalMinPriceMultiplier();
        double maxPrice = item.getMaxPrice() > 0
                ? item.getMaxPrice()
                : item.getBasePrice() * pricesConfig.getMaxPriceMultiplierForCategory(item.getCategory());

        double clamped = Math.max(minPrice, Math.min(maxPrice, smoothedPrice));
        return roundToStep(clamped);
    }

    private double weighted(String key, AiWeightProfile profile) {
        double baseWeight = aiConfig.getWeight(key);
        double learnedMultiplier = profile.getWeight(key, 1.0);
        return baseWeight * learnedMultiplier;
    }

    /**
     * Converts a 0.0-1.0 signal into a roughly -1.0 to +1.0 signed
     * signal centered on 0.5, so a "neutral" input contributes no pressure.
     *
     * @param normalizedSignal a value expected to be in the 0.0-1.0 range
     * @return the signed signal
     */
    private double signed(double normalizedSignal) {
        return (normalizedSignal - 0.5) * 2.0;
    }

    private double roundToStep(double price) {
        double step = pricesConfig.getRoundingStep();
        if (step <= 0) {
            return price;
        }
        return Math.round(price / step) * step;
    }
}