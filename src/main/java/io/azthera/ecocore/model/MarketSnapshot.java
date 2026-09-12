package io.azthera.ecocore.model;

/**
 * An immutable point-in-time snapshot of a single item's market state,
 * stored periodically for trend analysis and price-graph rendering.
 *
 * @param itemId          the shop item id this snapshot belongs to
 * @param price           the item's price at snapshot time
 * @param stock           the item's stock at snapshot time
 * @param transactionsIn  number of buy transactions since the previous snapshot
 * @param transactionsOut number of sell transactions since the previous snapshot
 * @param timestamp       epoch millis when this snapshot was taken
 */
public record MarketSnapshot(String itemId, double price, int stock,
                              int transactionsIn, int transactionsOut, long timestamp) {
}