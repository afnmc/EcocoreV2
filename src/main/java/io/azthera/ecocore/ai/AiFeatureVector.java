package io.azthera.ecocore.ai;

/**
 * A single AI cycle's computed feature signals for one item, all
 * normalized to roughly the 0.0-1.0 range so they can be linearly
 * weighted and combined by {@link PriceCalculator}.
 *
 * @param itemId           the item this vector was computed for
 * @param supply           normalized supply signal (stock abundance + inflow)
 * @param demand           normalized demand signal (recent buy pressure)
 * @param transactionVolume normalized total transaction activity (buys + sells)
 * @param playerCount      normalized active player-count signal
 * @param itemsIn          normalized rate of items flowing into the shop (players selling)
 * @param itemsOut         normalized rate of items flowing out of the shop (players buying)
 * @param inflation        normalized current inflation pressure from the InflationEngine
 * @param deflation        normalized current deflation pressure from the InflationEngine
 * @param storageLevel     current stock as a fraction of max stock
 * @param soldVolume       normalized count of sell transactions (players selling to shop)
 * @param boughtVolume     normalized count of buy transactions (players buying from shop)
 * @param marketSaturation how saturated (over-supplied, under-demanded) this item's market is
 * @param velocityOfMoney  normalized velocity of money across the whole server economy
 */
public record AiFeatureVector(
        String itemId,
        double supply,
        double demand,
        double transactionVolume,
        double playerCount,
        double itemsIn,
        double itemsOut,
        double inflation,
        double deflation,
        double storageLevel,
        double soldVolume,
        double boughtVolume,
        double marketSaturation,
        double velocityOfMoney
) {
}