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
 * notifies any registered {@link PriceChangeNotice} listeners when
 * the move exceeds {@code ai.yml}'s configured threshold.
 *
 * <p>This engine runs entirely on local data and requires no
 * internet connection. It also runs on an ASYNC scheduler thread
 * (see {@code AiCalculationScheduler}), so any listener registered
 * via {@link #addPriceChangeListener} that touches Bukkit API must
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
    private final List<Consumer<PriceChangeNotice>> priceChangeListeners = new CopyOnWriteArrayList<>();

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

        long now = System.currentTimeMillis();
        this.lastCycleTime = now - (aiConfig.getCalculationIntervalSeconds() * 1000L);
        this.lastRetrainTime = now;
    }

    /**
     * Registers a listener notified whenever a single item's price
     * moves by at least {@code ai.yml notifications.price-change-threshold-percent}
     * in one cycle. Runs on whatever thread {@link #runCycle()} was
     * called from (an async scheduler thread) - listeners touching
     * Bukkit API must schedule back to the main thread themselves.
     *
     * @param listener the callback to register
     */
    public void addPriceChangeListener(Consumer<PriceChangeNotice> listener) {
        priceChangeListeners.add(listener);
    }

    /**
     * Runs one full AI pricing cycle across every tradeable item in the
     * catalog. Safe to call from an async scheduler task.
     */
    public void runCycle() {
        long now = System.currentTimeMillis();
        long since = lastCycleTime;

        try {
            List<ShopItemRecord> items = shopItemDao.findAll();

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

            int processed = 0;
            for (ShopItemRecord item : items) {
                if (!item.isTradeable()) {
                    continue;
                }
                processItem(item, since, now, stateEffect.priceMultiplier(),
                        inflationSignal, deflationSignal, velocity, totalPlayers);
                if (shouldRetrain) {
                    learningModel.retrain(item.getId());
                }
                processed++;
            }

            if (shouldRetrain) {
                lastRetrainTime = now;
            }

            logger.info("[EcoCore] AI cycle complete: " + processed + " items repriced (state=" + state + ")");
        } catch (SQLException exception) {
            logger.severe("[EcoCore] AI cycle failed: " + exception.getMessage());
        } finally {
            lastCycleTime = now;
        }
    }

    private void processItem(ShopItemRecord item, long since, long now, double economicMultiplier,
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

        item.setCurrentPrice(newPrice);
        shopItemDao.updatePrice(item.getId(), newPrice, now);

        notifyIfSignificantChange(item, previousPrice);

        marketHistoryDao.insert(new MarketSnapshot(
                item.getId(), newPrice, item.getStock(),
                supplyDemand.boughtVolume(), supplyDemand.soldVolume(), now
        ));

        learningModel.recordSample(features, newPrice);
    }

    private void notifyIfSignificantChange(ShopItemRecord item, double previousPrice) {
        if (previousPrice <= 0 || priceChangeListeners.isEmpty()) {
            return;
        }

        double percentChange = Math.abs((item.getCurrentPrice() - previousPrice) / previousPrice) * 100.0;
        if (percentChange < aiConfig.getNotifyPriceChangeThresholdPercent()) {
            return;
        }

        PriceChangeNotice notice = new PriceChangeNotice(item, previousPrice);
        for (Consumer<PriceChangeNotice> listener : priceChangeListeners) {
            listener.accept(notice);
        }
    }

    private double normalizeCount(int count) {
        return count / (double) (count + 10.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
                }