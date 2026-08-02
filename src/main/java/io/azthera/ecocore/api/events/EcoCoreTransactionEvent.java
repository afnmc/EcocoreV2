package io.azthera.ecocore.api.events;

import io.azthera.ecocore.model.TransactionRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a completed buy or sell transaction (through the shop,
 * auto-sell, or minion auto-sell) has already been applied and
 * persisted. This is a notification-only event - it fires after the
 * fact and cannot be cancelled, since the trade has already settled
 * by the time it fires.
 */
public final class EcoCoreTransactionEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final TransactionRecord transaction;

    /**
     * Creates a transaction event.
     *
     * @param transaction the completed transaction record
     */
    public EcoCoreTransactionEvent(TransactionRecord transaction) {
        super(true);
        this.transaction = transaction;
    }

    public TransactionRecord getTransaction() {
        return transaction;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}