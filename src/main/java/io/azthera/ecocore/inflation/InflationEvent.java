package io.azthera.ecocore.inflation;

import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.InflationRecord;

/**
 * An internal notification fired by {@link InflationEngine} at the end
 * of every cycle, consumed by {@code NotificationManager} and the
 * Discord integration to broadcast inflation updates. Distinct from
 * {@code EcoCoreInflationChangeEvent} in the {@code api.events}
 * package, which is the public Bukkit event fired only on an actual
 * state change for third-party plugin integrations.
 *
 * @param previousState the economic state before this cycle
 * @param newState      the economic state computed this cycle
 * @param record        the full computed inflation record for this cycle
 */
public record InflationEvent(EconomicState previousState, EconomicState newState, InflationRecord record) {

    /**
     * Whether this cycle actually changed the server's economic state.
     *
     * @return {@code true} if {@link #previousState} differs from {@link #newState}
     */
    public boolean isStateChange() {
        return previousState != newState;
    }
}