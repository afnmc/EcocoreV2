package io.azthera.ecocore.inflation;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.model.EconomicState;

/**
 * Determines the server's macro-economic state transitions based on
 * the composite growth score computed by {@link InflationCalculator},
 * using the thresholds configured in {@code inflation.yml}.
 */
public final class EconomicCycleManager {

    private final InflationConfig inflationConfig;

    /**
     * Creates an economic cycle manager.
     *
     * @param inflationConfig resolved inflation.yml configuration
     */
    public EconomicCycleManager(InflationConfig inflationConfig) {
        this.inflationConfig = inflationConfig;
    }

    /**
     * Determines this cycle's economic state from the growth score and
     * the previous state, so a recovery out of crisis/recession can be
     * distinguished from ordinary growth.
     *
     * @param growthPercent the composite growth score for this cycle
     * @param previousState the state computed in the previous cycle
     * @return the resolved economic state for this cycle
     */
    public EconomicState determineState(double growthPercent, EconomicState previousState) {
        if (growthPercent >= inflationConfig.getBoomGrowthPercent()) {
            return EconomicState.BOOM;
        }
        if (growthPercent <= inflationConfig.getCrisisDeclinePercent()) {
            return EconomicState.ECONOMIC_CRISIS;
        }
        if (growthPercent <= inflationConfig.getRecessionDeclinePercent()) {
            return EconomicState.RECESSION;
        }

        boolean recoveringFromDownturn = previousState == EconomicState.ECONOMIC_CRISIS
                || previousState == EconomicState.RECESSION;

        if (recoveringFromDownturn && growthPercent >= inflationConfig.getRecoveryGrowthPercent()) {
            return EconomicState.ECONOMIC_GROWTH;
        }

        if (growthPercent > 0.5) {
            return EconomicState.ECONOMIC_GROWTH;
        }

        return EconomicState.STABLE;
    }

    /**
     * Computes a 0-100 recovery percentage, only meaningful when the
     * previous state was a downturn (recession or crisis) and the
     * economy is trending back toward its recovery threshold.
     *
     * @param growthPercent the composite growth score for this cycle
     * @param previousState the state computed in the previous cycle
     * @return the recovery percentage, 0 if not currently recovering from a downturn
     */
    public double computeRecoveryPercent(double growthPercent, EconomicState previousState) {
        boolean wasInDownturn = previousState == EconomicState.ECONOMIC_CRISIS
                || previousState == EconomicState.RECESSION;

        if (!wasInDownturn) {
            return 0.0;
        }

        double target = inflationConfig.getRecoveryGrowthPercent();
        if (target <= 0) {
            return 0.0;
        }

        double progress = (growthPercent / target) * 100.0;
        return Math.max(0.0, Math.min(100.0, progress));
    }
}