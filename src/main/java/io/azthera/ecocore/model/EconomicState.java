package io.azthera.ecocore.model;

/**
 * Represents the overall macro-economic state of the server,
 * as computed by the InflationEngine from money supply, trading
 * volume, and wealth distribution metrics.
 */
public enum EconomicState {
    ECONOMIC_CRISIS,
    RECESSION,
    STABLE,
    ECONOMIC_GROWTH,
    BOOM;

    /**
     * Returns a human-readable, colorized display label matched to
     * the keys used in {@code messages.yml} under the "inflation" section.
     *
     * @return the messages.yml key for this state
     */
    public String messageKey() {
        return switch (this) {
            case ECONOMIC_CRISIS -> "crisis";
            case RECESSION -> "recession";
            case ECONOMIC_GROWTH -> "growth";
            case BOOM -> "boom";
            case STABLE -> "stable";
        };
    }
}