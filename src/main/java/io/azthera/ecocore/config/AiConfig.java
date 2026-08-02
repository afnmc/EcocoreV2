package io.azthera.ecocore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Parsed view of {@code ai.yml}: tuning parameters for the local
 * AI economy engine's pricing cycle, restock decisions, trend
 * analysis windows, and learning model.
 */
public final class AiConfig {

    private final int calculationIntervalSeconds;
    private final double smoothingFactor;
    private final double learningRate;
    private final double maxPriceChangeUpPercent;
    private final double maxPriceChangeDownPercent;

    private final Map<String, Double> weights = new HashMap<>();

    private final double stockEmptyThresholdPercent;
    private final double restockTriggerPercent;
    private final double emergencyRestockTriggerPercent;
    private final double randomRestockChancePercent;
    private final boolean dailyRestockEnabled;
    private final boolean weeklyRestockEnabled;

    private final int dailyWindowHours;
    private final int weeklyWindowHours;
    private final int monthlyWindowHours;

    private final boolean learningModelEnabled;
    private final int historySamplesUsed;
    private final int retrainIntervalSeconds;

    /**
     * Parses AI engine configuration from the loaded {@code ai.yml}.
     *
     * @param config the loaded ai.yml
     */
    public AiConfig(FileConfiguration config) {
        this.calculationIntervalSeconds = config.getInt("engine.calculation-interval-seconds", 300);
        this.smoothingFactor = config.getDouble("engine.smoothing-factor", 0.25);
        this.learningRate = config.getDouble("engine.learning-rate", 0.05);
        this.maxPriceChangeUpPercent = config.getDouble("engine.max-price-change-up-percent", 8.0);
        this.maxPriceChangeDownPercent = config.getDouble("engine.max-price-change-down-percent", 8.0);

        ConfigurationSection weightsSection = config.getConfigurationSection("weights");
        if (weightsSection != null) {
            for (String key : weightsSection.getKeys(false)) {
                weights.put(key, weightsSection.getDouble(key));
            }
        }

        this.stockEmptyThresholdPercent = config.getDouble("restock-decision.stock-empty-threshold-percent", 0.0);
        this.restockTriggerPercent = config.getDouble("restock-decision.restock-trigger-percent", 15.0);
        this.emergencyRestockTriggerPercent = config.getDouble("restock-decision.emergency-restock-trigger-percent", 3.0);
        this.randomRestockChancePercent = config.getDouble("restock-decision.random-restock-chance-percent", 5.0);
        this.dailyRestockEnabled = config.getBoolean("restock-decision.daily-restock-enabled", true);
        this.weeklyRestockEnabled = config.getBoolean("restock-decision.weekly-restock-enabled", true);

        this.dailyWindowHours = config.getInt("trend-analysis.daily-window-hours", 24);
        this.weeklyWindowHours = config.getInt("trend-analysis.weekly-window-hours", 168);
        this.monthlyWindowHours = config.getInt("trend-analysis.monthly-window-hours", 720);

        this.learningModelEnabled = config.getBoolean("learning-model.enabled", true);
        this.historySamplesUsed = config.getInt("learning-model.history-samples-used", 200);
        this.retrainIntervalSeconds = config.getInt("learning-model.retrain-interval-seconds", 3600);
    }

    public int getCalculationIntervalSeconds() {
        return calculationIntervalSeconds;
    }

    public double getSmoothingFactor() {
        return smoothingFactor;
    }

    public double getLearningRate() {
        return learningRate;
    }

    public double getMaxPriceChangeUpPercent() {
        return maxPriceChangeUpPercent;
    }

    public double getMaxPriceChangeDownPercent() {
        return maxPriceChangeDownPercent;
    }

    /**
     * Returns the configured weight for a given AI feature key
     * (e.g. "supply", "demand", "market-saturation").
     *
     * @param key the feature key
     * @return the configured weight, or 0.0 if not configured
     */
    public double getWeight(String key) {
        return weights.getOrDefault(key, 0.0);
    }

    public Map<String, Double> getAllWeights() {
        return weights;
    }

    public double getStockEmptyThresholdPercent() {
        return stockEmptyThresholdPercent;
    }

    public double getRestockTriggerPercent() {
        return restockTriggerPercent;
    }

    public double getEmergencyRestockTriggerPercent() {
        return emergencyRestockTriggerPercent;
    }

    public double getRandomRestockChancePercent() {
        return randomRestockChancePercent;
    }

    public boolean isDailyRestockEnabled() {
        return dailyRestockEnabled;
    }

    public boolean isWeeklyRestockEnabled() {
        return weeklyRestockEnabled;
    }

    public int getDailyWindowHours() {
        return dailyWindowHours;
    }

    public int getWeeklyWindowHours() {
        return weeklyWindowHours;
    }

    public int getMonthlyWindowHours() {
        return monthlyWindowHours;
    }

    public boolean isLearningModelEnabled() {
        return learningModelEnabled;
    }

    public int getHistorySamplesUsed() {
        return historySamplesUsed;
    }

    public int getRetrainIntervalSeconds() {
        return retrainIntervalSeconds;
    }
}