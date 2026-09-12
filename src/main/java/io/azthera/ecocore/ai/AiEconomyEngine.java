package io.azthera.ecocore.ai;

import io.azthera.ecocore.config.AiConfig;
import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.config.PricesConfig;
import io.azthera.ecocore.database.dao.BuyHistoryDao;
import io.azthera.ecocore.database.dao.MarketHistoryDao;
import io.azthera.ecocore.database.dao.MoneyDao;
import io.azthera.ecocore.database.dao.PlayerDao;
import io.azthera.ecocore.database.dao.SellHistoryDao;
import io.azthera.ecocore.database.dao.ShopItemDao;
import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.InflationRecord;
import io.azthera.ecocore.model.MarketSnapshot;
import io.azthera.ecocore.model.ShopItemRecord;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Top-level orchestrator for EcoCore's local AI economy engine.
 * Once per configured interval, {@code AiCalculationScheduler} calls
 * {@link #runCycle()}, which for every tradeable item: gathers this
 * cycle's feature signals, consults the item's learned weight
 * profile, computes a new price, persists it, records a market
 * snapshot, feeds the outcome back into the learning model, and
 * notifies any registered {@link PriceCycleSummary} listeners once
 * per cycle, aggregating every significant move instead of firing
 * once per item.
 *
 * <p>This engine runs entirely on local data and requires no
 * internet connection. It also runs on an ASYNC scheduler thread
 * (see {@code AiCalculationScheduler}), so any listener registered
 * via {@link #addCycleSummaryListener} that touches Bukkit API must
 * hop back to the main thread itself before doing so.
 */
public final class AiEconomyEngine {

    /**
     * Notification payload for a single item's price move.
     *
     * @param item          the item whose price changed (already carries the new price)
     * @param previousPrice the price before this cycle's change
     */
    public record PriceChangeNotice(ShopItemRecord item, double previousPrice) {
    }

    /**
     * Aggregated summary of every significant price move from a
     * single AI pricing cycle (Revisi 20) - replaces broadcasting one
     * chat message per item, which spammed chat on every cycle for
     * servers with a large catalog.
     *
     * @param itemsChanged        how many items moved by at least the notify threshold this cycle
     * @param averagePercentChange the signed average percent change across those items
     * @param topMover            the single item that moved the most (by absolute percent), or {@code null} if none moved
     * @param topMoverPercent     the signed percent change of {@code topMover}
     */
    public record PriceCycleSummary(int itemsChanged, double averagePercentChange,
                                     ShopItemRecord topMover, double topMoverPercent) {
    }

    private final Logger logger;
    private final ShopItemDao shopItemDao;
    private final MarketHistoryDao marketHistoryDao;
    private final PlayerDao playerDao;

    private final AiConfig aiConfig;
    private final InflationConfig inflationConfig;

    private final SupplyDemandAnalyzer supplyDemandAnalyzer;
    private final MarketSaturationAnalyzer marketSaturationAnalyzer;
    private final VelocityOfMoneyTracker velocityOfMoneyTracker;
    private final PriceCalculator priceCalculator;
    private final AiLearningModel learningModel;

    private final Supplier<InflationRecord> latestInflationSupplier;
    private final List<Consumer<PriceCycleSummary>> cycleSummaryListeners = new CopyOnWriteArrayList<>();

    /**
     * Supplies the item list to reprice each cycle. Defaults to a
     * fresh DB read, but {@link #useLiveCatalog} lets the plugin wire
     * this to {@code ShopManager}'s own in-memory catalog instead
     * (Revisi 20 fix) so a price this engine computes is visible to
     * the shop GUI and every other reader of that catalog immediately,
     * rather than only after the next {@code /ecocore reload} or
     * server restart re-reads the database.
     */
    private Supplier<List<ShopItemRecord>> itemSource;

    private long lastCycleTime;
    private long lastRetrainTime;

    public AiEconomyEngine(Logger logger, ShopItemDao shopItemDao, MarketHistoryDao marketHistoryDao,
                            BuyHistoryDao buyHistoryDao, SellHistoryDao sellHistoryDao, PlayerDao playerDao,
                            MoneyDao moneyDao, AiConfig aiConfig, PricesConfig pricesConfig,
                            InflationConfig inflationConfig, AiLearningModel learningModel,
                            Supplier<InflationRecord> latestInflationSupplier) {
        this.logger = logger;
        this.shopItemDao = shopItemDao;
        this.marketHistoryDao = marketHistoryDao;
        this.playerDao = playerDao;

        this.aiConfig = aiConfig;
        this.inflationConfig = inflationConfig;

        this.supplyDemandAnalyzer = new SupplyDemandAnalyzer(buyHistoryDao, sellHistoryDao);
        this.marketSaturationAnalyzer = new MarketSaturationAnalyzer(shopItemDao);
        this.velocityOfMoneyTracker = new VelocityOfMoneyTracker(playerDao, moneyDao);
        this.priceCalculator = new PriceCalculator(aiConfig, pricesConfig);
        this.learningModel = learningModel;

        this.latestInflationSupplier = latestInflationSupplier;
        this.itemSource = () -> {
            try {
                return shopItemDao.findAll();
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to load shop items for AI cycle: " + exception.getMessage());
                return List.of();
            }
        };

        long now = System.currentTimeMillis();
        this.lastCycleTime = now - (aiConfig.getCalculationIntervalSeconds() * 1000L);
        this.lastRetrainTime = now;
    }

    /**
     * Wires this engine to read/mutate the exact {@link ShopItemRecord}
     * instances a live catalog (normally {@code ShopManager}'s) holds,
     * instead of independently re-reading the database each cycle.
     * Call this once, after the catalog is loaded - without it, this
     * engine still works but price changes only become visible to the
     * shop GUI after the next catalog reload (Revisi 20 fix).
     *
     * @param liveCatalogSupplier supplies the current live item list each cycle
     */
    public void useLiveCatalog(Supplier<List<ShopItemRecord>> liveCatalogSupplier) {
        this.itemSource = liveCatalogSupplier;
    }

    /**
     * Registers a listener notified once per cycle with an aggregated
     * summary of every item that moved by at least the configured
     * threshold, instead of once per item (Revisi 20). Runs on
     * whatever thread {@link #runCycle()} was called from (an async
     * scheduler thread) - listeners touching Bukkit API must schedule
     * back to the main thread themselves.
     *
     * @param listener the callback to register
     */
    public void addCycleSummaryListener(Consumer<PriceCycleSummary> listener) {
        cycleSummaryListeners.add(listener);
    }

    /**
     * Runs one full AI pricing cycle across every tradeable item in the
     * catalog. Safe to call from an async scheduler task.
     */
    public void runCycle() {
        long now = System.currentTimeMillis();
        long since = lastCycleTime;

        try {
            List<ShopItemRecord> items = itemSource.get();

            InflationRecord latestInflation = latestInflationSupplier.get();
            EconomicState state = latestInflation != null ? latestInflation.state() : EconomicState.STABLE;
            InflationConfig.StateEffect stateEffect = inflationConfig.getStateEffect(state);

            double inflationSignal = latestInflation != null
                    ? clamp(latestInflation.inflationPercent() / 20.0) : 0.5;
            double deflationSignal = latestInflation != null
                    ? clamp(latestInflation.deflationPercent() / 20.0) : 0.5;

            double velocity = velocityOfMoneyTracker.computeVelocity(since);
            int totalPlayers = playerDao.countAccounts();

            boolean shouldRetrain = aiConfig.isLearningModelEnabled()
                    && (now - lastRetrainTime) >= (aiConfig.getRetrainIntervalSeconds() * 1000L);

            List<PriceChangeNotice> significantMoves = new java.util.ArrayList<>();

            int processed = 0;
            for (ShopItemRecord item : items) {
                if (!item.isTradeable()) {
                    continue;
                }
                PriceChangeNotice notice = processItem(item, since, now, stateEffect.priceMultiplier(),
                        inflationSignal, deflationSignal, velocity, totalPlayers);
                if (notice != null) {
                    significantMoves.add(notice);
                }
                if (shouldRetrain) {
                    learningModel.retrain(item.getId());
                }
                processed++;
            }

            if (shouldRetrain) {
                lastRetrainTime = now;
            }

            publishCycleSummary(significantMoves);

            logger.info("[EcoCore] AI cycle complete: " + processed + " items repriced (state=" + state + ")");
        } catch (SQLException exception) {
            logger.severe("[EcoCore] AI cycle failed: " + exception.getMessage());
        } finally {
            lastCycleTime = now;
        }
    }

    private PriceChangeNotice processItem(ShopItemRecord item, long since, long now, double economicMultiplier,
                                           double inflationSignal, double deflationSignal, double velocity,
                                           int totalPlayers) throws SQLException {

        SupplyDemandAnalyzer.Result supplyDemand = supplyDemandAnalyzer.analyze(item, since);
        double saturation = marketSaturationAnalyzer.itemSaturation(supplyDemand.supply(), supplyDemand.demand());

        double transactionVolume = normalizeCount(supplyDemand.boughtVolume() + supplyDemand.soldVolume());
        double playerCountSignal = normalizeCount(totalPlayers);
        double storageLevel = item.getMaxStock() > 0 ? item.getStock() / (double) item.getMaxStock() : 0.0;

        AiFeatureVector features = new AiFeatureVector(
                item.getId(),
                supplyDemand.supply(),
                supplyDemand.demand(),
                transactionVolume,
                playerCountSignal,
                normalizeCount(supplyDemand.soldVolume()),
                normalizeCount(supplyDemand.boughtVolume()),
                inflationSignal,
                deflationSignal,
                storageLevel,
                normalizeCount(supplyDemand.soldVolume()),
                normalizeCount(supplyDemand.boughtVolume()),
                saturation,
                velocity
        );

        AiWeightProfile profile = learningModel.loadProfile(item.getId());
        double previousPrice = item.getCurrentPrice();
        double newPrice = priceCalculator.computeNewPrice(item, features, profile, economicMultiplier);

        // Snapshot the pre-cycle price on the item itself every cycle
        // (not just on significant moves) so the shop GUI can always
        // show a live "before -> after" delta (Revisi 20).
        item.setPreviousPrice(previousPrice);
        item.setCurrentPrice(newPrice);
        try {
            shopItemDao.updatePrice(item.getId(), newPrice, now);

            marketHistoryDao.insert(new MarketSnapshot(
                    item.getId(), newPrice, item.getStock(),
                    supplyDemand.boughtVolume(), supplyDemand.soldVolume(), now
            ));

            learningModel.recordSample(features, newPrice);
        } catch (SQLException exception) {
            item.setCurrentPrice(previousPrice);
            throw exception;
        }

        PriceChangeNotice notice = significantChangeOf(item, previousPrice);
        return notice;
    }

    private PriceChangeNotice significantChangeOf(ShopItemRecord item, double previousPrice) {
        if (previousPrice <= 0) {
            return null;
        }
        double percentChange = Math.abs((item.getCurrentPrice() - previousPrice) / previousPrice) * 100.0;
        if (percentChange < aiConfig.getNotifyPriceChangeThresholdPercent()) {
            return null;
        }
        return new PriceChangeNotice(item, previousPrice);
    }

    /**
     * Rolls every significant move from this cycle into a single
     * {@link PriceCycleSummary} and fires it to listeners once,
     * instead of once per item (Revisi 20).
     *
     * @param significantMoves every item that crossed the notify threshold this cycle
     */
    private void publishCycleSummary(List<PriceChangeNotice> significantMoves) {
        if (significantMoves.isEmpty() || cycleSummaryListeners.isEmpty()) {
            return;
        }

        double percentSum = 0.0;
        ShopItemRecord topMover = null;
        double topMoverPercent = 0.0;

        for (PriceChangeNotice notice : significantMoves) {
            double signedPercent = ((notice.item().getCurrentPrice() - notice.previousPrice())
                    / notice.previousPrice()) * 100.0;
            percentSum += signedPercent;
            if (topMover == null || Math.abs(signedPercent) > Math.abs(topMoverPercent)) {
                topMover = notice.item();
                topMoverPercent = signedPercent;
            }
        }

        double averagePercent = percentSum / significantMoves.size();
        PriceCycleSummary summary = new PriceCycleSummary(
                significantMoves.size(), averagePercent, topMover, topMoverPercent);

        for (Consumer<PriceCycleSummary> listener : cycleSummaryListeners) {
            listener.accept(summary);
        }
    }

    private double normalizeCount(int count) {
        return count / (double) (count + 10.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
