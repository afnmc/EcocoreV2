package io.azthera.ecocore.model;

/**
 * An immutable snapshot of the server's macro-economic indicators,
 * computed periodically by the InflationEngine and stored in the
 * {@code inflation_history} table.
 *
 * @param totalMoney       total money currently in circulation across all accounts
 * @param averageBalance   mean balance across all known player accounts
 * @param tradingVolume    total transaction count in the sampling window
 * @param moneyFlow        net money flow (money entering minus money leaving the economy)
 * @param marketActivity   composite market activity score, 0-100
 * @param inflationPercent computed inflation percentage for this period
 * @param deflationPercent computed deflation percentage for this period
 * @param recoveryPercent  computed recovery percentage, relevant when transitioning out of a crisis
 * @param state            the resulting macro-economic state for this period
 * @param timestamp        epoch millis when this record was computed
 */
public record InflationRecord(double totalMoney, double averageBalance, long tradingVolume,
                               double moneyFlow, double marketActivity, double inflationPercent,
                               double deflationPercent, double recoveryPercent,
                               EconomicState state, long timestamp) {
}