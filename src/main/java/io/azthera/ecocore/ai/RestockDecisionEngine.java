package io.azthera.ecocore.ai;

import io.azthera.ecocore.config.AiConfig;
import io.azthera.ecocore.model.ShopItemRecord;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides when and how much to restock a shop item, based on its
 * current stock percentage and the thresholds configured in
 * {@code ai.yml}. Actual restock quantities and persistence are
 * applied by {@code RestockScheduler} / {@code StockManager}; this
 * class only makes the decision.
 */
public final class RestockDecisionEngine {

    private final AiConfig aiConfig;

    /**
     * Creates a restock decision engine.
     *
     * @param aiConfig resolved ai.yml configuration
     */
    public RestockDecisionEngine(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    /**
     * The kind of restock decision made for an item this check.
     */
    public enum RestockDecision {
        NONE,
        SCHEDULED,
        EMERGENCY,
        RANDOM
    }

    /**
     * Evaluates whether an item should be restocked right now.
     *
     * @param item         the item to evaluate
     * @param isDailyTick  whether this evaluation coincides with the daily restock tick
     * @param isWeeklyTick whether this evaluation coincides with the weekly restock tick
     * @return the restock decision for this item
     */
    public RestockDecision evaluate(ShopItemRecord item, boolean isDailyTick, boolean isWeeklyTick) {
        double stockPercent = item.stockPercent();

        if (stockPercent <= aiConfig.getEmergencyRestockTriggerPercent()) {
            return RestockDecision.EMERGENCY;
        }

        if (stockPercent <= aiConfig.getRestockTriggerPercent()) {
            return RestockDecision.SCHEDULED;
        }

        if (isDailyTick && aiConfig.isDailyRestockEnabled()) {
            return RestockDecision.SCHEDULED;
        }

        if (isWeeklyTick && aiConfig.isWeeklyRestockEnabled()) {
            return RestockDecision.SCHEDULED;
        }

        double roll = ThreadLocalRandom.current().nextDouble(0, 100);
        if (roll < aiConfig.getRandomRestockChancePercent()) {
            return RestockDecision.RANDOM;
        }

        return RestockDecision.NONE;
    }

    /**
     * Computes how many units to restock for a given decision.
     * Emergency restocks fill closer to full; scheduled/random
     * restocks add a smaller, more organic amount.
     *
     * @param item     the item being restocked
     * @param decision the decision returned by {@link #evaluate}
     * @return the number of units to add to stock, never exceeding the item's max stock
     */
    public int computeRestockAmount(ShopItemRecord item, RestockDecision decision) {
        int missing = item.getMaxStock() - item.getStock();
        if (missing <= 0 || decision == RestockDecision.NONE) {
            return 0;
        }

        double fraction = switch (decision) {
            case EMERGENCY -> 0.90;
            case SCHEDULED -> 0.50;
            case RANDOM -> 0.25;
            case NONE -> 0.0;
        };

        return (int) Math.round(missing * fraction);
    }
}