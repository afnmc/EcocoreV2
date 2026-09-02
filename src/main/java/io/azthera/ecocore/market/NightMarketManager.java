package io.azthera.ecocore.market;

import io.azthera.ecocore.config.NightMarketConfig;
import io.azthera.ecocore.database.dao.NightMarketDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.model.NightMarketOffer;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Owns EcoCore's "night market": a separate, slow-rotating shop of
 * expensive and rare items, distinct from the main {@code /shop}
 * catalog. Unlike the main shop, offers here do NOT restock during
 * their rotation window - once an offer sells out, it stays sold out
 * until the next full rotation swaps in an entirely new set of items.
 */
public final class NightMarketManager {

    private final Logger logger;
    private final NightMarketDao nightMarketDao;
    private final NightMarketConfig nightMarketConfig;
    private final EconomyEngine economyEngine;

    private final List<NightMarketOffer> offers = new CopyOnWriteArrayList<>();
    private volatile long rotationStartedAt;
    private final List<Consumer<List<NightMarketOffer>>> rotationListeners = new CopyOnWriteArrayList<>();

    /**
     * The outcome of an attempted night market purchase.
     *
     * @param success    whether the purchase went through
     * @param message    a human-readable outcome reason
     * @param amount     quantity actually purchased
     * @param totalPrice total price charged
     */
    public record BuyResult(boolean success, String message, int amount, double totalPrice) {
    }

    /**
     * Creates the night market manager.
     *
     * @param logger            plugin logger
     * @param nightMarketDao    DAO for rotation persistence
     * @param nightMarketConfig resolved night-market.yml configuration
     * @param economyEngine     economy engine used to charge purchases
     */
    public NightMarketManager(Logger logger, NightMarketDao nightMarketDao, NightMarketConfig nightMarketConfig,
                               EconomyEngine economyEngine) {
        this.logger = logger;
        this.nightMarketDao = nightMarketDao;
        this.nightMarketConfig = nightMarketConfig;
        this.economyEngine = economyEngine;
    }

    /**
     * Loads the current rotation from the database on startup, or
     * triggers a fresh rotation if none exists yet, or the loaded one
     * has already expired.
     */
    public void loadOrRotate() {
        try {
            List<NightMarketOffer> loaded = nightMarketDao.findAll();
            long startedAt = nightMarketDao.findRotationStartedAt();
            long intervalMillis = nightMarketConfig.getRotationIntervalHours() * 3_600_000L;

            if (loaded.isEmpty() || (System.currentTimeMillis() - startedAt) >= intervalMillis) {
                rotate();
            } else {
                offers.clear();
                offers.addAll(loaded);
                rotationStartedAt = startedAt;
                logger.info("[EcoCore] Loaded existing night market rotation (" + offers.size() + " offers)");
            }
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to load night market state: " + exception.getMessage());
            rotate();
        }
    }

    /**
     * Picks a fresh random set of offers from the configured pool and
     * persists them, replacing whatever rotation was active before.
     * Safe to call from any thread.
     */
    public void rotate() {
        List<NightMarketConfig.PoolEntry> pool = new ArrayList<>(nightMarketConfig.getPool());
        Collections.shuffle(pool, new Random());

        int slotCount = Math.min(nightMarketConfig.getSlots(), pool.size());
        List<NightMarketOffer> newOffers = new ArrayList<>();

        for (int i = 0; i < slotCount; i++) {
            NightMarketConfig.PoolEntry entry = pool.get(i);
            double price = entry.basePrice() * nightMarketConfig.getPriceMultiplier();
            int stock = nightMarketConfig.getStockPerItem();
            newOffers.add(new NightMarketOffer(entry.id(), entry.material(), price, stock, stock));
        }

        long now = System.currentTimeMillis();

        try {
            nightMarketDao.clearAll();
            for (NightMarketOffer offer : newOffers) {
                nightMarketDao.insert(offer, now);
            }
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to persist night market rotation: " + exception.getMessage());
        }

        offers.clear();
        offers.addAll(newOffers);
        rotationStartedAt = now;

        logger.info("[EcoCore] Night market rotated: " + newOffers.size() + " new offers");

        List<NightMarketOffer> snapshot = List.copyOf(offers);
        for (Consumer<List<NightMarketOffer>> listener : rotationListeners) {
            listener.accept(snapshot);
        }
    }

    /**
     * Registers a listener notified with the new offer list every
     * time a rotation occurs. NOTE: {@link #rotate()} may run on an
     * async scheduler thread, so listeners that touch Bukkit API must
     * hop back to the main thread themselves before doing so.
     *
     * @param listener the callback to register
     */
    public void addRotationListener(Consumer<List<NightMarketOffer>> listener) {
        rotationListeners.add(listener);
    }

    public List<NightMarketOffer> getOffers() {
        return List.copyOf(offers);
    }

    public NightMarketOffer getOffer(String id) {
        for (NightMarketOffer offer : offers) {
            if (offer.getId().equals(id)) {
                return offer;
            }
        }
        return null;
    }

    /**
     * Milliseconds remaining until the next automatic rotation.
     *
     * @return remaining time in millis, 0 if already due
     */
    public long millisUntilNextRotation() {
        long intervalMillis = nightMarketConfig.getRotationIntervalHours() * 3_600_000L;
        long elapsed = System.currentTimeMillis() - rotationStartedAt;
        return Math.max(0, intervalMillis - elapsed);
    }

    /**
     * Attempts to purchase from a night market offer. A sold-out
     * offer stays sold out - there is no restock until the next full
     * rotation.
     *
     * @param player  the buying player
     * @param offerId the offer id to purchase
     * @param amount  the requested quantity
     * @return the outcome of the purchase attempt
     */
    public BuyResult buy(Player player, String offerId, int amount) {
        if (amount <= 0) {
            return new BuyResult(false, "invalid-amount", 0, 0);
        }

        NightMarketOffer offer = getOffer(offerId);
        if (offer == null) {
            return new BuyResult(false, "not-found", 0, 0);
        }

        synchronized (offer) {
            if (offer.isSoldOut()) {
                return new BuyResult(false, "sold-out", 0, 0);
            }

            int actualAmount = Math.min(amount, offer.getStock());
            double totalPrice = offer.getPrice() * actualAmount;

            if (!economyEngine.has(player.getUniqueId(), totalPrice)) {
                return new BuyResult(false, "insufficient-funds", 0, 0);
            }

            if (!offer.consume(actualAmount)) {
                return new BuyResult(false, "stock-update-failed", 0, 0);
            }

            boolean charged = economyEngine.withdraw(player.getUniqueId(), totalPrice, TransactionLogger.REASON_SHOP_BUY);
            if (!charged) {
                offer.refund(actualAmount);
                return new BuyResult(false, "insufficient-funds", 0, 0);
            }

            ItemUtils.giveOrDrop(player, ItemUtils.safeMaterial(offer.getMaterial()), actualAmount);

            try {
                nightMarketDao.updateStock(offer.getId(), offer.getStock());
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to persist night market stock for " + offerId + ": " + exception.getMessage());
            }

            return new BuyResult(true, "ok", actualAmount, totalPrice);
        }
    }
                                               }