package io.azthera.ecocore.ai;

import java.util.HashMap;
import java.util.Map;

/**
 * An item's learned per-feature weight multipliers. Every feature
 * starts at a neutral multiplier of 1.0 (meaning "use the base weight
 * from ai.yml as-is") and is nudged up or down over time by
 * {@link AiLearningModel#retrain(String)} based on how volatile the
 * item's price has been.
 */
public final class AiWeightProfile {

    private final Map<String, Double> weights;

    /**
     * Creates an empty profile; every feature defaults to a 1.0 multiplier.
     */
    public AiWeightProfile() {
        this.weights = new HashMap<>();
    }

    /**
     * Creates a profile pre-populated with the given weight multipliers.
     *
     * @param weights initial feature-key to multiplier map
     */
    public AiWeightProfile(Map<String, Double> weights) {
        this.weights = new HashMap<>(weights);
    }

    /**
     * Returns the learned multiplier for a feature key, or the given
     * fallback if this feature has never been adjusted.
     *
     * @param key      the feature key (e.g. "demand", "supply")
     * @param fallback value to return if unset, typically 1.0
     * @return the learned multiplier
     */
    public double getWeight(String key, double fallback) {
        return weights.getOrDefault(key, fallback);
    }

    /**
     * Sets the learned multiplier for a feature key.
     *
     * @param key   the feature key
     * @param value the new multiplier value
     */
    public void setWeight(String key, double value) {
        weights.put(key, value);
    }

    /**
     * Returns the raw underlying weight map, used for JSON serialization.
     *
     * @return the mutable backing map
     */
    public Map<String, Double> asMap() {
        return weights;
    }
}