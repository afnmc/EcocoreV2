package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards villager trades and successful EcoCore shop transactions.
 */
public final class MerchantJob extends AbstractJobHandler {

    public MerchantJob() {
        super(JobType.MERCHANT, Map.of(
                "TRADE_VILLAGER", 1.5,
                "SELL_ITEM", 0.3,
                "BUY_ITEM", 0.2
        ));
    }

    @Override
    public boolean appliesTo(String actionKey) {
        return "TRADE_VILLAGER".equals(actionKey)
                || (actionKey != null && (actionKey.startsWith("SELL_ITEM_")
                || actionKey.startsWith("BUY_ITEM_")));
    }

    @Override
    public double getRewardMultiplier(String actionKey) {
        if ("TRADE_VILLAGER".equals(actionKey)) {
            return 1.5;
        }
        if (actionKey != null && actionKey.startsWith("SELL_ITEM_")) {
            return 0.3;
        }
        if (actionKey != null && actionKey.startsWith("BUY_ITEM_")) {
            return 0.2;
        }
        return 0.0;
    }
}
