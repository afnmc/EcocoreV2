package io.azthera.ecocore.config;

import io.azthera.ecocore.model.EconomicState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Parsed view of {@code inflation.yml}: baseline economic targets,
 * state transition thresholds, indicator weighting, and the
 * price/job-bonus effects applied per {@link EconomicState}.
 */
public final class InflationConfig {

    /**
     * The gameplay effects applied while the economy is in a given state.
     *
     * @param priceMultiplier    multiplier applied to AI-computed prices
     * @param jobBonusMultiplier multiplier applied to job money/xp rewards
     */
    public record StateEffect(double priceMultiplier, double jobBonusMultiplier) {
    }

    private final double initialMoneySupply;
    private final double targetInflationPercent;
    private final double targetAverageBalance;

    private final double boomGrowthPercent;
    private final double recessionDeclinePercent;
    private final double crisisDeclinePercent;
    private final double recoveryGrowthPercent;

    private final double totalMoneyWeight;
    private final double playerWealthWeight;
    private final double averageBalanceWeight;
    private final double tradingVolumeWeight;
    private final double moneyFlowWeight;
    private final double marketActivityWeight;

    private final Map<EconomicState, StateEffect> stateEffects = new EnumMap<>(EconomicState.class);

    private final int snapshotIntervalSeconds;
    private final int keepDays;

    /**
     * Parses inflation engine configuration from the loaded {@code inflation.yml}.
     *
     * @param config the loaded inflation.yml
     */
    public InflationConfig(FileConfiguration config) {
        this.initialMoneySupply = config.getDouble("economy-baseline.initial-money-supply", 0.0);
        this.targetInflationPercent = config.getDouble("economy-baseline.target-inflation-percent", 2.0);
        this.targetAverageBalance = config.getDouble("economy-baseline.target-average-balance", 5000.0);

        this.boomGrowthPercent = config.getDouble("thresholds.boom-growth-percent", 8.0);
        this.recessionDeclinePercent = config.getDouble("thresholds.recession-decline-percent", -5.0);
        this.crisisDeclinePercent = config.getDouble("thresholds.crisis-decline-percent", -15.0);
        this.recoveryGrowthPercent = config.getDouble("thresholds.recovery-growth-percent", 3.0);

        this.totalMoneyWeight = config.getDouble("weighting.total-money-weight", 0.25);
        this.playerWealthWeight = config.getDouble("weighting.player-wealth-weight", 0.20);
        this.averageBalanceWeight = config.getDouble("weighting.average-balance-weight", 0.15);
        this.tradingVolumeWeight = config.getDouble("weighting.trading-volume-weight", 0.20);
        this.moneyFlowWeight = config.getDouble("weighting.money-flow-weight", 0.10);
        this.marketActivityWeight = config.getDouble("weighting.market-activity-weight", 0.10);

        stateEffects.put(EconomicState.BOOM, readEffect(config, "boom"));
        stateEffects.put(EconomicState.RECESSION, readEffect(config, "recession"));
        stateEffects.put(EconomicState.ECONOMIC_CRISIS, readEffect(config, "economic-crisis"));
        stateEffects.put(EconomicState.ECONOMIC_GROWTH, readEffect(config, "economic-growth"));
        stateEffects.put(EconomicState.STABLE, new StateEffect(1.0, 1.0));

        this.snapshotIntervalSeconds = config.getInt("history.snapshot-interval-seconds", 900);
        this.keepDays = config.getInt("history.keep-days", 90);
    }

    private StateEffect readEffect(FileConfiguration config, String key) {
        ConfigurationSection section = config.getConfigurationSection("state-effects." + key);
        if (section == null) {
            return new StateEffect(1.0, 1.0);
        }
        return new StateEffect(
                section.getDouble("price-multiplier", 1.0),
                section.getDouble("job-bonus-multiplier", 1.0)
        );
    }

    public double getInitialMoneySupply() {
        return initialMoneySupply;
    }

    public double getTargetInflationPercent() {
        return targetInflationPercent;
    }

    public double getTargetAverageBalance() {
        return targetAverageBalance;
    }

    public double getBoomGrowthPercent() {
        return boomGrowthPercent;
    }

    public double getRecessionDeclinePercent() {
        return recessionDeclinePercent;
    }

    public double getCrisisDeclinePercent() {
        return crisisDeclinePercent;
    }

    public double getRecoveryGrowthPercent() {
        return recoveryGrowthPercent;
    }

    public double getTotalMoneyWeight() {
        return totalMoneyWeight;
    }

    public double getPlayerWealthWeight() {
        return playerWealthWeight;
    }

    public double getAverageBalanceWeight() {
        return averageBalanceWeight;
    }

    public double getTradingVolumeWeight() {
        return tradingVolumeWeight;
    }

    public double getMoneyFlowWeight() {
        return moneyFlowWeight;
    }

    public double getMarketActivityWeight() {
        return marketActivityWeight;
    }

    /**
     * Returns the gameplay effect configured for a given economic state.
     *
     * @param state the economic state
     * @return the state's effect, defaulting to a neutral 1.0/1.0 effect if unconfigured
     */
    public StateEffect getStateEffect(EconomicState state) {
        return stateEffects.getOrDefault(state, new StateEffect(1.0, 1.0));
    }

    public int getSnapshotIntervalSeconds() {
        return snapshotIntervalSeconds;
    }

    public int getKeepDays() {
        return keepDays;
    }
}