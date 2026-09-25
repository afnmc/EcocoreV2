package io.azthera.ecocore.model;

import java.util.UUID;

/**
 * An immutable record of a single buy or sell transaction,
 * stored in the {@code buy_history} or {@code sell_history} tables.
 *
 * @param id         database row id, or -1 if not yet persisted
 * @param playerUuid the player who performed the transaction
 * @param itemId     the shop item id involved
 * @param type       {@link TransactionType#BUY} or {@link TransactionType#SELL}
 * @param amount     quantity of items traded
 * @param unitPrice  price per single item at the time of trade
 * @param totalPrice total price paid or received
 * @param timestamp  epoch millis when the transaction occurred
 */
public record TransactionRecord(long id, UUID playerUuid, String itemId, TransactionType type,
                                 int amount, double unitPrice, double totalPrice, long timestamp) {

    /**
     * The direction of a transaction.
     */
    public enum TransactionType {
        BUY,
        SELL
    }
}