package io.azthera.ecocore.ai;

import io.azthera.ecocore.config.AiConfig;
import io.azthera.ecocore.model.ShopItemRecord;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides when and how much to restock a shop item.
 *
 * <p>Revisi 17: every non-NONE decision is now gated by two
 * independent throttles before it's returned, fixing the old bug
 * where an item sitting below the trigger threshold restocked on
 * every scheduler pass (as often as the check interval, which could
 * be every minute): a minimum cooldown since the item's last restock,
 * and a hard daily cap. Neither throttle applies to manual admin
 * restocks, which go through a completely separate code path that
 * never calls this class.
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

    public RestockDecision evaluate(ShopItemRecord item, boolean isDailyTick, boolean isWeeklyTick) {
        RestockDecision decision = evaluateRawTrigger(item, isDailyTick, isWeeklyTick);
        if (decision == RestockDecision.NONE) {
            return RestockDecision.NONE;
        }
        // Revisi 17: EMERGENCY restocks bypass the cooldown (a shop
        // that's actually out of stock shouldn't stay empty just
        // because the cooldown window hasn't elapsed) but the daily
        // cap still applies even to emergencies.
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
     * Whether enough time has passed since this item's last restock
     * (Revisi 17). Uses the stricter of {@code
     * min-restock-interval-hours} and {@code restock-cooldown-hours}.
     */
    private boolean cooldownElapsed(ShopItemRecord item) {
        if (item.getLastRestockAt() <= 0) {
            return true;
        }
        double effectiveCooldownHours = Math.max(
                aiConfig.getMinRestockIntervalHours(), aiConfig.getRestockCooldownHours());
        long cooldownMillis = (long) (effectiveCooldownHours * 3_600_000L);
        return (System.currentTimeMillis() - item.getLastRestockAt()) >= cooldownMillis;
    }

    /**
     * Whether this item has already hit its configured daily restock
     * cap (Revisi 17).
     */
    private boolean dailyCapReached(ShopItemRecord item) {
        long currentDayBucket = System.currentTimeMillis() / 86_400_000L;
        if (currentDayBucket != item.getRestockDayEpoch()) {
            return false;
        }
        return item.getRestocksToday() >= aiConfig.getMaxRestockPerItemPerDay();
    }

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
