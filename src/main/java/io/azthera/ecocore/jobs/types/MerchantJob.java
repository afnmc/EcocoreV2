package io.azthera.ecocore.jobs.types;

import io.azthera.ecocore.model.JobType;

import java.util.Map;

/**
 * Rewards trading with villagers and transacting through EcoCore's shop.
 */
public final class MerchantJob extends AbstractJobHandler {

    public MerchantJob() {
        super(JobType.MERCHANT, Map.ofEntries(
                Map.entry("TRADE_VILLAGER", 1.5),
                Map.entry("SELL_ITEM_SHOP", 0.3),
                Map.entry("BUY_ITEM_SHOP", 0.2)
        ));
    }
}