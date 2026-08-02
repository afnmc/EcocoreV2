package io.azthera.ecocore.inflation;

import io.azthera.ecocore.config.InflationConfig;

/**
 * Computes the weighted "economic pressure" score that drives inflation
 * and deflation percentages, by blending total money supply growth,
 * wealth concentration, average balance drift, trading volume, money
 * flow, and market activity according to the weights configured in
 * {@code inflation.yml}.
 */
public final class InflationCalculator {

    private final InflationConfig inflationConfig;

    /**
     * Creates an inflation calculator.
     *
     * @param inflationConfig resolved inflation.yml configuration
     */
    public InflationCalculator(InflationConfig inflationConfig) {
        this.inflationConfig = inflationConfig;
    }

    /**
     * Raw economic indicators gathered for a single InflationEngine cycle.
     *
     * @param totalMoney          current total money supply across all accounts
     * @param averageBalance      current average account balance
     * @param tradingVolume       total buy+sell transactions in the sampling window
     * @param moneyFlow           net signed money flow in the sampling window
     * @param marketActivity      composite market activity score, 0-100
     * @param wealthConcentration current wealth concentration, 0.0-1.0 (from {@link WealthDistributionTracker})
     */
    public record Metrics(double totalMoney, double averageBalance, long tradingVolume,
                           double moneyFlow, double marketActivity, double wealthConcentration) {
    }

    /**
     * Computes the composite economic pressure/growth score for this cycle,
     * roughly expressed as a percentage. Positive values indicate the
     * economy is expanding (inflationary pressure); negative values
     * indicate it is contracting (deflationary pressure).
     *
     * @param current              this cycle's raw metrics
     * @param previousTotalMoney   the total money supply from the previous cycle, or {@code null} on the very first cycle
     * @return the composite growth/pressure score, as a percentage
     */
    public double computeGrowthPercent(Metrics current, Double previousTotalMoney) {
        double baseline = (previousTotalMoney != null && previousTotalMoney > 0)
                ? previousTotalMoney
                : inflationConfig.getInitialMoneySupply();

        double totalMoneyGrowth = baseline > 0
                ? ((current.totalMoney() - baseline) / baseline) * 100.0
                : 0.0;

        double averageBalanceSignal = deviationFromTargetPercent(
                current.averageBalance(), inflationConfig.getTargetAverageBalance());

        double tradingVolumeSignal = normalizeVolume(current.tradingVolume());
        double moneyFlowSignal = current.totalMoney() > 0
                ? (current.moneyFlow() / current.totalMoney()) * 100.0
                : 0.0;
        double wealthSignal = (current.wealthConcentration() - 0.5) * 100.0;
        double marketActivitySignal = current.marketActivity() - 50.0;

        return (totalMoneyGrowth * inflationConfig.getTotalMoneyWeight())
                + (wealthSignal * inflationConfig.getPlayerWealthWeight())
                + (averageBalanceSignal * inflationConfig.getAverageBalanceWeight())
                + (tradingVolumeSignal * inflationConfig.getTradingVolumeWeight())
                + (moneyFlowSignal * inflationConfig.getMoneyFlowWeight())
                + (marketActivitySignal * inflationConfig.getMarketActivityWeight());
    }

    /**
     * Derives the inflation percentage from a growth score: the positive
     * portion of growth, floored at zero.
     *
     * @param growthPercent the composite growth score from {@link #computeGrowthPercent}
     * @return the inflation percentage, always >= 0
     */
    public double computeInflationPercent(double growthPercent) {
        return Math.max(0.0, growthPercent);
    }

    /**
     * Derives the deflation percentage from a growth score: the magnitude
     * of the negative portion of growth, floored at zero.
     *
     * @param growthPercent the composite growth score from {@link #computeGrowthPercent}
     * @return the deflation percentage, always >= 0
     */
    public double computeDeflationPercent(double growthPercent) {
        return Math.max(0.0, -growthPercent);
    }

    private double deviationFromTargetPercent(double actual, double target) {
        if (target <= 0) {
            return 0.0;
        }
        return ((actual - target) / target) * 100.0;
    }

    private double normalizeVolume(long tradingVolume) {
        // Squash raw transaction counts to a roughly -50..+50 signal
        // centered on a "moderate activity" baseline of 100 transactions.
        double normalized = tradingVolume / (double) (tradingVolume + 100.0);
        return (normalized - 0.5) * 100.0;
    }
}