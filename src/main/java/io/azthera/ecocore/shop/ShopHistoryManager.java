package io.azthera.ecocore.shop;

import io.azthera.ecocore.database.dao.BuyHistoryDao;
import io.azthera.ecocore.database.dao.SellHistoryDao;
import io.azthera.ecocore.model.TransactionRecord;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Combines a player's recent buy and sell transactions into a single
 * chronological history feed, used by {@code ShopHistoryGui}.
 */
public final class ShopHistoryManager {

    private final BuyHistoryDao buyHistoryDao;
    private final SellHistoryDao sellHistoryDao;

    /**
     * Creates a shop history manager.
     *
     * @param buyHistoryDao  DAO for reading buy transaction history
     * @param sellHistoryDao DAO for reading sell transaction history
     */
    public ShopHistoryManager(BuyHistoryDao buyHistoryDao, SellHistoryDao sellHistoryDao) {
        this.buyHistoryDao = buyHistoryDao;
        this.sellHistoryDao = sellHistoryDao;
    }

    /**
     * Returns a player's most recent buy+sell transactions combined,
     * newest first.
     *
     * @param playerUuid the player's uuid
     * @param limit      maximum number of combined entries to return
     * @return the combined history, newest first
     * @throws SQLException if either underlying query fails
     */
    public List<TransactionRecord> getRecentHistory(UUID playerUuid, int limit) throws SQLException {
        List<TransactionRecord> combined = new ArrayList<>();
        combined.addAll(buyHistoryDao.findRecentByPlayer(playerUuid, limit));
        combined.addAll(sellHistoryDao.findRecentByPlayer(playerUuid, limit));

        combined.sort(Comparator.comparingLong(TransactionRecord::timestamp).reversed());

        return combined.size() > limit ? combined.subList(0, limit) : combined;
    }
}