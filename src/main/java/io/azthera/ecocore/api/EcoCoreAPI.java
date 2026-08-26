package io.azthera.ecocore.api;

import io.azthera.ecocore.economy.EconomyAPI;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Public-facing API surface for third-party plugins integrating with
 * EcoCore. Obtain an instance via {@code EcoCorePlugin.getInstance().getApi()}.
 * Wraps the narrower {@link EconomyAPI} together with read access to
 * market, jobs, minions, and inflation state, so external plugins
 * depend on this single stable interface rather than EcoCore's
 * internal manager classes directly.
 */
public interface EcoCoreAPI {

    /**
     * Returns the economy sub-API for balance operations.
     *
     * @return the economy API
     */
    EconomyAPI economy();

    /**
     * Returns a live shop item by id.
     *
     * @param itemId the item id
     * @return the item, or {@code null} if not in the catalog
     */
    ShopItemRecord getShopItem(String itemId);

    /**
     * Returns every item currently in the shop catalog.
     *
     * @return all catalog items
     */
    List<ShopItemRecord> getAllShopItems();

    /**
     * Returns the server's current macro-economic state.
     *
     * @return the current economic state
     */
    EconomicState getEconomicState();

    /**
     * Returns a player's progress in a single job.
     *
     * @param playerUuid the player's uuid
     * @param jobType    the job type
     * @return the job data, or {@code null} if not joined
     * @throws SQLException if the underlying query fails
     */
    JobData getJobProgress(UUID playerUuid, JobType jobType) throws SQLException;

    /**
     * Returns every minion currently owned by a player.
     *
     * @param playerUuid the player's uuid
     * @return that player's active minions
     */
    List<MinionData> getMinionsOwnedBy(UUID playerUuid);
}