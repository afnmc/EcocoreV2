package io.azthera.ecocore.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired whenever an item is restocked, whether by the AI's scheduled/
 * emergency/random restock decisions or by an admin's manual adjustment.
 */
public final class EcoCoreRestockEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String itemId;
    private final int amountAdded;
    private final int stockAfter;
    private final String reason;

    /**
     * Creates a restock event.
     *
     * @param itemId      the item that was restocked
     * @param amountAdded the number of units added
     * @param stockAfter  the resulting stock value
     * @param reason      the restock's event type tag (e.g. "RESTOCK_SCHEDULED", "ADMIN")
     */
    public EcoCoreRestockEvent(String itemId, int amountAdded, int stockAfter, String reason) {
        super(true);
        this.itemId = itemId;
        this.amountAdded = amountAdded;
        this.stockAfter = stockAfter;
        this.reason = reason;
    }

    public String getItemId() {
        return itemId;
    }

    public int getAmountAdded() {
        return amountAdded;
    }

    public int getStockAfter() {
        return stockAfter;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}