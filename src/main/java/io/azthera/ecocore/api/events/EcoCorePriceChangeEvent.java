package io.azthera.ecocore.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired whenever the AI economy engine changes an item's live price.
 * Third-party plugins can listen to this to react to market movement
 * (e.g. driving external dashboards or cross-plugin economy bridges).
 */
public final class EcoCorePriceChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String itemId;
    private final double previousPrice;
    private final double newPrice;

    /**
     * Creates a price change event.
     *
     * @param itemId        the item whose price changed
     * @param previousPrice the price before this change
     * @param newPrice      the newly computed price
     */
    public EcoCorePriceChangeEvent(String itemId, double previousPrice, double newPrice) {
        super(true);
        this.itemId = itemId;
        this.previousPrice = previousPrice;
        this.newPrice = newPrice;
    }

    public String getItemId() {
        return itemId;
    }

    public double getPreviousPrice() {
        return previousPrice;
    }

    public double getNewPrice() {
        return newPrice;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}