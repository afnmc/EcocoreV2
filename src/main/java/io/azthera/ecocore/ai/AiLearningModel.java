package io.azthera.ecocore.ai;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.azthera.ecocore.config.AiConfig;
import io.azthera.ecocore.database.dao.AiLearningDao;

import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A lightweight, fully local learning model for the AI economy engine.
 * It never calls any external AI service - it simply tracks how
 * volatile each item's price has been across recent AI cycles and
 * nudges per-item weight multipliers to keep price movement within a
 * healthy range: dampening items that have been running too hot, and
 * sharpening the responsiveness of items that have stayed too static.
 */
public final class AiLearningModel {

    private static final Type FEATURE_MAP_TYPE = new TypeToken<Map<String, Double>>() {}.getType();
    private static final Type WEIGHT_MAP_TYPE = new TypeToken<Map<String, Double>>() {}.getType();

    private final AiLearningDao aiLearningDao;
    private final AiConfig aiConfig;
    private final Gson gson = new Gson();

    private final Map<String, AiWeightProfile> cache = new HashMap<>();

    /**
     * Creates the learning model.
     *
     * @param aiLearningDao DAO used to persist samples and weight profiles
     * @param aiConfig      resolved ai.yml configuration
     */
    public AiLearningModel(AiLearningDao aiLearningDao, AiConfig aiConfig) {
        this.aiLearningDao = aiLearningDao;
        this.aiConfig = aiConfig;
    }

    /**
     * Loads (or lazily initializes) the current weight multiplier profile
     * for an item. Weight multipliers start at 1.0 for every feature and
     * are nudged over time by {@link #retrain(String)}.
     *
     * @param itemId the item id
     * @return the item's current weight profile
     * @throws SQLException if the underlying query fails
     */
    public AiWeightProfile loadProfile(String itemId) throws SQLException {
        AiWeightProfile cached = cache.get(itemId);
        if (cached != null) {
            return cached;
        }

        String json = aiLearningDao.findWeightProfile(itemId);
        AiWeightProfile profile;
        if (json == null || json.isBlank()) {
            profile = new AiWeightProfile();
        } else {
            Map<String, Double> weights = gson.fromJson(json, WEIGHT_MAP_TYPE);
            profile = new AiWeightProfile(weights != null ? weights : new HashMap<>());
        }
        cache.put(itemId, profile);
        return profile;
    }

    /**
     * Records a training sample: the feature vector observed for an item
     * in this AI cycle, paired with the price that was ultimately computed.
     * Samples accumulate in the database and are consumed by {@link #retrain(String)}.
     *
     * @param features       the feature vector for this cycle
     * @param resultingPrice the price computed from this feature vector
     * @throws SQLException if the insert fails
     */
    public void recordSample(AiFeatureVector features, double resultingPrice) throws SQLException {
        Map<String, Double> featureMap = new HashMap<>();
        featureMap.put("supply", features.supply());
        featureMap.put("demand", features.demand());
        featureMap.put("transaction-volume", features.transactionVolume());
        featureMap.put("player-count", features.playerCount());
        featureMap.put("items-in", features.itemsIn());
        featureMap.put("items-out", features.itemsOut());
        featureMap.put("inflation", features.inflation());
        featureMap.put("deflation", features.deflation());
        featureMap.put("storage-level", features.storageLevel());
        featureMap.put("sold-volume", features.soldVolume());
        featureMap.put("bought-volume", features.boughtVolume());
        featureMap.put("market-saturation", features.marketSaturation());

        String json = gson.toJson(featureMap, FEATURE_MAP_TYPE);
        aiLearningDao.insertSample(features.itemId(), json, resultingPrice);
    }

    /**
     * Retrains an item's weight profile from its most recent samples.
     * Uses a simple local heuristic (no external AI/model): averages
     * how "hot" each feature has been running across recent samples,
     * and nudges its weight multiplier down when it has been sitting
     * near the extremes (risking runaway price swings) or up slightly
     * when it has been sitting near neutral (likely under-responsive).
     *
     * @param itemId the item id to retrain
     * @throws SQLException if the underlying queries fail
     */
    public void retrain(String itemId) throws SQLException {
        List<String> recentFeatureJson = aiLearningDao.findRecentFeatureSamples(itemId, aiConfig.getHistorySamplesUsed());
        if (recentFeatureJson.size() < 2) {
            return;
        }

        AiWeightProfile profile = loadProfile(itemId);
        double learningRate = aiConfig.getLearningRate();

        Map<String, Double> totals = new HashMap<>();
        for (String json : recentFeatureJson) {
            Map<String, Double> sample = gson.fromJson(json, FEATURE_MAP_TYPE);
            if (sample == null) {
                continue;
            }
            for (Map.Entry<String, Double> entry : sample.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        int sampleCount = recentFeatureJson.size();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            double average = entry.getValue() / sampleCount;
            double currentWeight = profile.getWeight(entry.getKey(), 1.0);

            double extremity = Math.abs(average - 0.5) * 2.0;
            double adjustment = extremity > 0.7
                    ? -learningRate
                    : (extremity < 0.3 ? learningRate * 0.5 : 0.0);

            double newWeight = Math.max(0.25, Math.min(2.0, currentWeight + adjustment));
            profile.setWeight(entry.getKey(), newWeight);
        }

        aiLearningDao.upsertWeightProfile(itemId, gson.toJson(profile.asMap(), WEIGHT_MAP_TYPE));
        cache.put(itemId, profile);
    }

    /**
     * Clears the in-memory profile cache, forcing the next
     * {@link #loadProfile(String)} call to re-read from the database.
     * Called by {@code /ecocore reload} and {@code /ecocore ai}.
     */
    public void invalidateCache() {
        cache.clear();
    }
}