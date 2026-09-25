package io.azthera.ecocore.api;

import io.azthera.ecocore.economy.EconomyAPI;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link EcoCoreAPI}, thinly wiring
 * together the plugin's already-constructed internal managers.
 */
public final class EcoCoreAPIImpl implements EcoCoreAPI {

    private final EconomyAPI economyApi;
    private final ShopManager shopManager;
    private final InflationEngine inflationEngine;
    private final JobsManager jobsManager;

    /**
     * Creates the API implementation.
     *
     * @param economyApi      the economy sub-API to expose
     * @param shopManager     shared shop manager
     * @param inflationEngine shared inflation engine
     * @param jobsManager     shared jobs manager
     */
    public EcoCoreAPIImpl(EconomyAPI economyApi, ShopManager shopManager, InflationEngine inflationEngine,
                           JobsManager jobsManager) {
        this.economyApi = economyApi;
        this.shopManager = shopManager;
        this.inflationEngine = inflationEngine;
        this.jobsManager = jobsManager;
    }

    @Override
    public EconomyAPI economy() {
        return economyApi;
    }

    @Override
    public ShopItemRecord getShopItem(String itemId) {
        return shopManager.getItem(itemId);
    }

    @Override
    public List<ShopItemRecord> getAllShopItems() {
        return shopManager.getAllItems();
    }

    @Override
    public EconomicState getEconomicState() {
        return inflationEngine.getCurrentState();
    }

    @Override
    public JobData getJobProgress(UUID playerUuid, JobType jobType) throws SQLException {
        return jobsManager.getProgress(playerUuid, jobType);
    }
}
