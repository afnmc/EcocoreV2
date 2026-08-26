// FILE: src/main/java/io/azthera/ecocore/ai/RestockDecisionEngine.java
package io.azthera.ecocore.ai;

import io.azthera.ecocore.config.AiConfig;
import io.azthera.ecocore.model.ShopItemRecord;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides whether and how much to restock a single shop item on each
 * scheduler pass.
 *
 * Revisi 17: every non-NONE decision is now gated by two
 * independent throttles before it's returned, fixing the old bug
 * where an item sitting below the trigger threshold restocked on
 * literally every scheduler pass (as often as the check interval,
 * which could be every minute): a minimum cooldown since the item's
 * last restock ({@code restock-decision.min-restock-interval-hours}
 * / {@code restock-cooldown-hours}), and a hard daily cap ({@code
 * restock-decision.max-restock-per-item-per-day}). Neither throttle
 * applies to manual admin restocks (those go through {@code
 * StockManager.adminAdjust}, a completely separate code path that
 * never calls this class at all).
 */
public final class RestockDecisionEngine {

    private final AiConfig aiConfig;

    public RestockDecisionEngine(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    public enum RestockDecision {
        NONE,
        SCHEDULED,
        EMERGENCY,
        RANDOM
    }

    /**
     * Evaluates whether an item should be restocked right now,
     * applying both the underlying trigger logic and the Revisi 17
     * cooldown/daily-cap throttles.
     *
     * @param item the item to evaluate
     * @param isDailyTick whether this evaluation coincides with the daily restock tick
     * @param isWeeklyTick whether this evaluation coincides with the weekly restock tick
     * @return the restock decision for this item
     */
    public RestockDecision evaluate(ShopItemRecord item, boolean isDailyTick, boolean isWeeklyTick) {
        RestockDecision decision = evaluateRawTrigger(item, isDailyTick, isWeeklyTick);
        if (decision == RestockDecision.NONE) {
            return RestockDecision.NONE;
        }
        // Revisi 17: EMERGENCY restocks (stock is critically low or fully
        // empty) are allowed to bypass the cooldown - a shop that's
        // actually out of stock shouldn't stay empty just because the
        // cooldown window hasn't elapsed - but the daily cap still applies
        // even to emergencies, to keep a pathologically fast-selling item
        // from restocking unboundedly many times in one day.
        if (decision != RestockDecision.EMERGENCY && !cooldownElapsed(item)) {
            return RestockDecision.NONE;
        }
        if (dailyCapReached(item)) {
            return RestockDecision.NONE;
        }
        return decision;
    }

    private RestockDecision evaluateRawTrigger(ShopItemRecord item, boolean isDailyTick, boolean isWeeklyTick) {
        double stockPercent = item.stockPercent();
        if (stockPercent .getEmergencyRestockTriggerPercent()) {
            return RestockDecision.EMERGENCY;
        }
        if (stockPercent .getRestockTriggerPercent()) {
            return RestockDecision.SCHEDULED;
        }
        if (isDailyTick && aiConfig.isDailyRestockEnabled()) {
            return RestockDecision.SCHEDULED;
        }
        if (isWeeklyTick && aiConfig.isWeeklyRestockEnabled()) {
            return RestockDecision.SCHEDULED;
        }
        double roll = ThreadLocalRandom.current().nextDouble(0, 100);
        if (roll .getRandomRestockChancePercent()) {
            return RestockDecision.RANDOM;
        }
        return RestockDecision.NONE;
    }

    /**
     * Whether enough time has passed since this item's last restock
     * (Revisi 17). Uses the larger of {@code
     * min-restock-interval-hours} and {@code restock-cooldown-hours}
     * as the effective wait, since the two config keys from the
     * spec overlap in purpose and taking the stricter of the two
     * avoids ambiguity about which one "wins".
     */
    private boolean cooldownElapsed(ShopItemRecord item) {
        if (item.getLastRestockAt() 0) {
            return true; // never restocked before - nothing to wait on
        }
        double effectiveCooldownHours = Math.max(
                aiConfig.getMinRestockIntervalHours(), aiConfig.getRestockCooldownHours());
        long cooldownMillis = (long) (effectiveCooldownHours * 3_600_000L);
        return (System.currentTimeMillis() - item.getLastRestockAt()) >= cooldownMillis;
    }

    /**
     * Whether this item has already hit its configured daily restock
     * cap (Revisi 17). The day-bucket rollover itself is handled by
     * {@code ShopItemRecord.recordRestock}, so this only needs to
     * compare against whatever the record currently reports for
     * "today".
     */
    private boolean dailyCapReached(ShopItemRecord item) {
        long currentDayBucket = System.currentTimeMillis() / 86_400_000L;
        if (currentDayBucket != item.getRestockDayEpoch()) {
            return false; // a new day hasn't been recorded yet - counter would reset on the next restock
        }
        return item.getRestocksToday() >= aiConfig.getMaxRestockPerItemPerDay();
    }

    /**
     * Computes how many units to restock for a given decision.
     * Emergency restocks fill closer to full; scheduled/random
     * restocks add a smaller, more organic amount.
     *
     * @param item the item being restocked
     * @param decision the decision returned by {@link #evaluate}
     * @return the number of units to add to stock, never exceeding the item's max stock
     */
    public int computeRestockAmount(ShopItemRecord item, RestockDecision decision) {
        int missing = item.getMaxStock() - item.getStock();
        if (missing 0 || decision == RestockDecision.NONE) {
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