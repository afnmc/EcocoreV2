package io.azthera.ecocore.api.events;

import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.InflationRecord;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired whenever the server's macro-economic state changes (e.g.
 * transitioning from Stable to Boom, or Recession to Economic Crisis).
 * Not fired on cycles where the state stays the same, even though a
 * new {@link InflationRecord} is still computed and persisted every cycle.
 */
public final class EcoCoreInflationChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final EconomicState previousState;
    private final EconomicState newState;
    private final InflationRecord record;

    /**
     * Creates an inflation state change event.
     *
     * @param previousState the state before this cycle
     * @param newState      the state computed this cycle
     * @param record        the full computed inflation record for this cycle
     */
    public EcoCoreInflationChangeEvent(EconomicState previousState, EconomicState newState, InflationRecord record) {
        super(true);
        this.previousState = previousState;
        this.newState = newState;
        this.record = record;
    }

    public EconomicState getPreviousState() {
        return previousState;
    }

    public EconomicState getNewState() {
        return newState;
    }

    public InflationRecord getRecord() {
        return record;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}