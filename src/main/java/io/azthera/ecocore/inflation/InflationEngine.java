package io.azthera.ecocore.inflation;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.database.dao.BuyHistoryDao;
import io.azthera.ecocore.database.dao.InflationHistoryDao;
import io.azthera.ecocore.database.dao.MoneyDao;
import io.azthera.ecocore.database.dao.PlayerDao;
import io.azthera.ecocore.database.dao.SellHistoryDao;
import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.InflationRecord;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Top-level orchestrator for EcoCore's macro-economic inflation system.
 * Once per configured interval, {@code InflationTaskScheduler} calls
 * {@link #runCycle()}, which gathers server-wide economic indicators,
 * computes a composite growth/pressure score via
 * {@link InflationCalculator}, resolves the resulting
 * {@link EconomicState} via {@link EconomicCycleManager}, persists an
 * {@link InflationRecord}, and notifies registered listeners
 * (notifications, Discord) via {@link InflationEvent}.
 *
 * <p>The most recently computed record is also exposed via
 * {@link #getLatestRecord()}, which {@code AiEconomyEngine} consumes
 * each pricing cycle to apply the current economic state's price
 * multiplier.
 */
public final class InflationEngine {

    private final Logger logger;
    private final PlayerDao playerDao;
    private final MoneyDao moneyDao;
    private final BuyHistoryDao buyHistoryDao;
    private final SellHistoryDao sellHistoryDao;
    private final InflationHistoryDao inflationHistoryDao;

    private final InflationCalculator calculator;
    private final EconomicCycleManager cycleManager;
    private final WealthDistributionTracker wealthTracker;

    private final List<Consumer<InflationEvent>> listeners = new ArrayList<>();

    private volatile InflationRecord latestRecord;
    private volatile EconomicState currentState = EconomicState.STABLE;
    private long lastCycleTime;

    /**
     * Creates the inflation engine.
     *
     * @param logger              plugin logger for cycle summaries
     * @param playerDao           DAO for total money supply, average balance, and account counts
     * @param moneyDao            DAO for net money flow over the sampling window
     * @param buyHistoryDao       DAO for global buy transaction counts
     * @param sellHistoryDao      DAO for global sell transaction counts
     * @param inflationHistoryDao DAO for persisting computed inflation records
     * @param inflationConfig     resolved inflation.yml configuration
     */
    public InflationEngine(Logger logger, PlayerDao playerDao, MoneyDao moneyDao, BuyHistoryDao buyHistoryDao,
                            SellHistoryDao sellHistoryDao, InflationHistoryDao inflationHistoryDao,
                            InflationConfig inflationConfig) {
        this.logger = logger;
        this.playerDao = playerDao;
        this.moneyDao = moneyDao;
        this.buyHistoryDao = buyHistoryDao;
        this.sellHistoryDao = sellHistoryDao;
        this.inflationHistoryDao = inflationHistoryDao;

        this.calculator = new InflationCalculator(inflationConfig);
        this.cycleManager = new EconomicCycleManager(inflationConfig);
        this.wealthTracker = new WealthDistributionTracker(playerDao);

        this.lastCycleTime = System.currentTimeMillis();
    }

    /**
     * Loads the most recently persisted inflation record on startup,
     * so the server doesn't start "blind" (defaulting to STABLE) after
     * a restart. Safe to call once during plugin enable.
     *
     * @throws SQLException if the underlying query fails
     */
    public void loadLastKnownState() throws SQLException {
        InflationRecord record = inflationHistoryDao.findLatest();
        if (record != null) {
            this.latestRecord = record;
            this.currentState = record.state();
        }
    }

    /**
     * Registers a listener to be notified after every completed cycle.
     * Used by {@code NotificationManager} and the Discord integration.
     *
     * @param listener callback invoked with this cycle's {@link InflationEvent}
     */
    public void addListener(Consumer<InflationEvent> listener) {
        listeners.add(listener);
    }

    /**
     * Runs one full inflation calculation cycle: gathers indicators,
     * computes the new economic state, persists a record, and notifies
     * listeners. Safe to call from an async scheduler task.
     */
    public void runCycle() {
        long now = System.currentTimeMillis();
        long since = lastCycleTime;

        try {
            double totalMoney = playerDao.sumTotalMoney();
            double averageBalance = playerDao.averageBalance();
            double moneyFlow = moneyDao.netMoneyFlowSince(since);
            long tradingVolume = buyHistoryDao.countAllSince(since) + sellHistoryDao.countAllSince(since);
            double wealthConcentration = wealthTracker.computeWealthConcentration();
            double marketActivity = computeMarketActivity(tradingVolume, playerDao.countAccounts());

            InflationCalculator.Metrics metrics = new InflationCalculator.Metrics(
                    totalMoney, averageBalance, tradingVolume, moneyFlow, marketActivity, wealthConcentration);

            Double previousTotalMoney = latestRecord != null ? latestRecord.totalMoney() : null;
            double growthPercent = calculator.computeGrowthPercent(metrics, previousTotalMoney);

            EconomicState previousState = currentState;
            EconomicState newState = cycleManager.determineState(growthPercent, previousState);

            double inflationPercent = calculator.computeInflationPercent(growthPercent);
            double deflationPercent = calculator.computeDeflationPercent(growthPercent);
            double recoveryPercent = cycleManager.computeRecoveryPercent(growthPercent, previousState);

            InflationRecord record = new InflationRecord(
                    totalMoney, averageBalance, tradingVolume, moneyFlow, marketActivity,
                    inflationPercent, deflationPercent, recoveryPercent, newState, now
            );

            inflationHistoryDao.insert(record);

            this.latestRecord = record;
            this.currentState = newState;

            InflationEvent event = new InflationEvent(previousState, newState, record);
            for (Consumer<InflationEvent> listener : listeners) {
                listener.accept(event);
            }

            logger.info("[EcoCore] Inflation cycle complete: state=" + newState
                    + " inflation=" + String.format("%.2f", inflationPercent) + "%"
                    + " deflation=" + String.format("%.2f", deflationPercent) + "%");
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Inflation cycle failed: " + exception.getMessage());
        } finally {
            lastCycleTime = now;
        }
    }

    private double computeMarketActivity(long tradingVolume, int playerCount) {
        if (playerCount <= 0) {
            return 0.0;
        }
        double perPlayerActivity = tradingVolume / (double) playerCount;
        double normalized = perPlayerActivity / (perPlayerActivity + 5.0);
        return Math.max(0.0, Math.min(100.0, normalized * 100.0));
    }

    /**
     * Returns the most recently computed inflation record.
     * Used as the {@code Supplier<InflationRecord>} passed into
     * {@code AiEconomyEngine} so pricing decisions reflect the
     * current macro-economic state.
     *
     * @return the latest record, or {@code null} if no cycle has run yet
     */
    public InflationRecord getLatestRecord() {
        return latestRecord;
    }

    /**
     * Returns the current resolved economic state.
     *
     * @return the current {@link EconomicState}
     */
    public EconomicState getCurrentState() {
        return currentState;
    }
}