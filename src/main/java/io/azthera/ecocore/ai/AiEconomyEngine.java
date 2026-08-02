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
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Top-level orchestrator for EcoCore's local AI economy engine.
 * Once per configured interval, {@code AiCalculationScheduler} calls
 * {@link #runCycle()}, which for every tradeable item: gathers this
 * cycle's feature signals, consults the item's learned weight
 * profile, computes a new price, persists it, records a market
 * snapshot, and feeds the outcome back into the learning model.
 *
 * <p>This engine runs entirely on local data (supply, demand, stock,
 * transaction volume, player count, money velocity, and the current
 * macro-economic state from the InflationEngine). It never contacts
 * any external AI service and requires no internet connection.
 */
public final class AiEconomyEngine {

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

    private long lastCycleTime;
    private long lastRetrainTime;

    /**
     * Creates the AI economy engine.
     *
     * @param logger                  plugin logger for cycle summaries
     * @param shopItemDao             DAO for reading/writing item prices and stock
     * @param marketHistoryDao        DAO for persisting per-cycle market snapshots
     * @param buyHistoryDao           DAO for buy transaction counts
     * @param sellHistoryDao          DAO for sell transaction counts
     * @param playerDao               DAO for total money supply and player counts
     * @param moneyDao                DAO for net money flow, used by velocity tracking
     * @param aiConfig                resolved ai.yml configuration
     * @param pricesConfig            resolved prices.yml configuration
     * @param inflationConfig         resolved inflation.yml configuration
     * @param learningModel           the shared AI learning model
     * @param latestInflationSupplier supplies the most recently computed {@link InflationRecord}, backed by the InflationEngine
     */
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
     * Runs one full AI pricing cycle across every tradeable item in the
     * catalog. Safe to call from an async scheduler task; all database
     * access here is expected to already be off the main server thread.
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
        double newPrice = priceCalculator.computeNewPrice(item, features, profile, economicMultiplier);

        item.setCurrentPrice(newPrice);
        shopItemDao.updatePrice(item.getId(), newPrice, now);

        marketHistoryDao.insert(new MarketSnapshot(
                item.getId(), newPrice, item.getStock(),
                supplyDemand.boughtVolume(), supplyDemand.soldVolume(), now
        ));

        learningModel.recordSample(features, newPrice);
    }

    private double normalizeCount(int count) {
        return count / (double) (count + 10.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}