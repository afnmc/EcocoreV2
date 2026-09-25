package io.azthera.ecocore.shop;

import io.azthera.ecocore.ai.RestockDecisionEngine;
import io.azthera.ecocore.model.ShopItemRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Business logic for one restock evaluation pass over the entire
 * catalog: for every tradeable item, consults {@link RestockDecisionEngine}
 * and applies any resulting restock via {@link StockManager}.
 *
 * <p>This class does not itself run on a timer - {@code
 * scheduler.RestockTaskScheduler} owns the periodic Bukkit task and
 * calls {@link #runRestockPass(boolean, boolean)} once per check
 * interval defined in {@code config.yml}.
 */
public final class RestockScheduler {

    private final Logger logger;
    private final Map<String, ShopItemRecord> catalog;
    private final RestockDecisionEngine decisionEngine;
    private final StockManager stockManager;

    /**
     * A single item's restock outcome from a completed pass, used by
     * callers that want to broadcast/notify about restocks.
     *
     * @param itemId the item that was restocked
     * @param amount the number of units added
     * @param reason the kind of restock that triggered it
     */
    public record RestockOutcome(String itemId, int amount, RestockDecisionEngine.RestockDecision reason) {
    }

    /**
     * Creates a restock scheduler.
     *
     * @param logger         plugin logger for pass summaries
     * @param catalog        the live in-memory catalog shared with {@code ShopManager}
     * @param decisionEngine the AI restock decision engine
     * @param stockManager   the stock manager used to apply restocks
     */
    public RestockScheduler(Logger logger, Map<String, ShopItemRecord> catalog,
                             RestockDecisionEngine decisionEngine, StockManager stockManager) {
        this.logger = logger;
        this.catalog = catalog;
        this.decisionEngine = decisionEngine;
        this.stockManager = stockManager;
    }

    /**
     * Evaluates and applies restocks for every tradeable item in the catalog.
     *
     * @param isDailyTick  whether this pass coincides with the daily restock tick
     * @param isWeeklyTick whether this pass coincides with the weekly restock tick
     * @return the list of items that were actually restocked this pass
     */
    public List<RestockOutcome> runRestockPass(boolean isDailyTick, boolean isWeeklyTick) {
        List<RestockOutcome> outcomes = new ArrayList<>();

        for (ShopItemRecord item : catalog.values()) {
            if (!item.isTradeable()) {
                continue;
            }

            RestockDecisionEngine.RestockDecision decision = decisionEngine.evaluate(item, isDailyTick, isWeeklyTick);
            if (decision == RestockDecisionEngine.RestockDecision.NONE) {
                continue;
            }

            int amount = decisionEngine.computeRestockAmount(item, decision);
            if (amount <= 0) {
                continue;
            }

            String eventType = switch (decision) {
                case EMERGENCY -> StockManager.EVENT_RESTOCK_EMERGENCY;
                case SCHEDULED -> StockManager.EVENT_RESTOCK_SCHEDULED;
                case RANDOM -> StockManager.EVENT_RESTOCK_RANDOM;
                case NONE -> StockManager.EVENT_ADMIN;
            };

            if (stockManager.restock(item.getId(), amount, eventType)) {
                outcomes.add(new RestockOutcome(item.getId(), amount, decision));
            }
        }

        if (!outcomes.isEmpty()) {
            logger.info("[EcoCore] Restock pass complete: " + outcomes.size() + " item(s) restocked");
        }

        return outcomes;
    }
}