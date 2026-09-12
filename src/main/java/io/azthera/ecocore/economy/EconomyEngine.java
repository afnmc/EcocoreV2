package io.azthera.ecocore.economy;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.database.dao.MoneyDao;
import io.azthera.ecocore.database.dao.JobMissionDao;
import io.azthera.ecocore.database.dao.PlayerDao;
import io.azthera.ecocore.model.PlayerAccount;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Concrete implementation of {@link EconomyAPI} backed by
 * {@link PlayerDao} and {@link MoneyDao}. Keeps an in-memory cache of
 * currently-loaded accounts so repeated balance checks within a single
 * tick (e.g. rendering a shop GUI) don't each hit the database, while
 * every mutating operation writes straight through to SQLite so a
 * server crash never loses a committed transaction.
 */
public final class EconomyEngine implements EconomyAPI {

    private final Logger logger;
    private final PlayerDao playerDao;
    private final MoneyDao moneyDao;
    private final ConfigManager configManager;
    private final CurrencyFormatter formatter;

    private final Map<UUID, PlayerAccount> cache = new ConcurrentHashMap<>();
    private volatile BiConsumer<UUID, Double> moneyEarnedListener;

    /**
     * Creates the economy engine.
     *
     * @param logger        plugin logger for error reporting
     * @param playerDao     DAO for player account persistence
     * @param moneyDao      DAO for the money ledger audit trail
     * @param configManager resolved config manager (starting balance, currency formatting)
     */
    public EconomyEngine(Logger logger, PlayerDao playerDao, MoneyDao moneyDao, ConfigManager configManager) {
        this.logger = logger;
        this.playerDao = playerDao;
        this.moneyDao = moneyDao;
        this.configManager = configManager;
        this.formatter = new CurrencyFormatter(configManager);
    }

    /**
     * Registers an optional callback for externally earned money. Internal job
     * and mission rewards are excluded so EARN_MONEY missions cannot recursively
     * reward themselves.
     */
    public void setMoneyEarnedListener(BiConsumer<UUID, Double> listener) {
        this.moneyEarnedListener = listener;
    }

    private boolean shouldTrackEarnedMoney(String reason) {
        if (reason == null) {
            return true;
        }
        String normalized = reason.toLowerCase(java.util.Locale.ROOT);
        return !normalized.contains("job_reward")
                && !normalized.contains("mission_reward")
                && !normalized.contains("shop_buy_refund");
    }

    /**
     * Loads a player's account into the cache (creating it with the
     * configured starting balance if this is their first time), called
     * on player join.
     *
     * @param uuid the player's uuid
     * @param name the player's current username
     * @return the loaded account
     */
    public PlayerAccount loadAccount(UUID uuid, String name) {
        try {
            PlayerAccount account = playerDao.findOrCreate(uuid, name, configManager.getStartingBalance());
            cache.put(uuid, account);
            return account;
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to load account for " + uuid + ": " + exception.getMessage());
            PlayerAccount fallback = new PlayerAccount(uuid, name, 0.0, System.currentTimeMillis(), System.currentTimeMillis());
            cache.put(uuid, fallback);
            return fallback;
        }
    }

    /**
     * Saves and removes a player's account from the cache, called on
     * player quit so memory doesn't grow unbounded across sessions.
     *
     * @param uuid the player's uuid
     */
    public void unloadAccount(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            return;
        }
        PlayerAccount account = cache.get(uuid);
        if (account == null) {
            return;
        }
        try {
            playerDao.save(account);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to save account for " + uuid + " on unload: " + exception.getMessage());
        }
        player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            cache.remove(uuid, account);
        }
    }

    /**
     * Forces every currently cached account to be saved, called
     * periodically by {@code AutoSaveScheduler} and on plugin disable.
     */
    public void saveAll() {
        for (PlayerAccount account : cache.values()) {
            try {
                playerDao.save(account);
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to autosave account for "
                        + account.getUuid() + ": " + exception.getMessage());
            }
        }
    }

    private PlayerAccount resolve(UUID uuid) {
        PlayerAccount cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }
        try {
            PlayerAccount loaded = playerDao.findByUuid(uuid);
            if (loaded == null) {
                loaded = playerDao.findOrCreate(uuid, uuid.toString(), configManager.getStartingBalance());
            }
            cache.put(uuid, loaded);
            return loaded;
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to resolve account for " + uuid + ": " + exception.getMessage());
            return null;
        }
    }

    @Override
    public double getBalance(UUID uuid) {
        PlayerAccount account = resolve(uuid);
        return account != null ? account.getBalance() : 0.0;
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    @Override
    public boolean withdraw(UUID uuid, double amount, String reason) {
        if (amount < 0) {
            throw new IllegalArgumentException("Withdraw amount cannot be negative");
        }
        PlayerAccount account = resolve(uuid);
        if (account == null) {
            return false;
        }

        synchronized (account) {
            double prevBalance = account.getBalance();
            long prevUpdatedAt = account.getUpdatedAt();
            if (!account.withdraw(amount)) {
                return false;
            }
            try {
                persist(account, -amount, reason);
                return true;
            } catch (SQLException exception) {
                account.setBalance(prevBalance);
                account.setUpdatedAt(prevUpdatedAt);
                logger.severe("[EcoCore] Failed to persist withdraw for "
                        + account.getUuid() + ": " + exception.getMessage());
                return false;
            }
        }
    }

    @Override
    public boolean deposit(UUID uuid, double amount, String reason) {
        if (amount < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        }
        PlayerAccount account = resolve(uuid);
        if (account == null) {
            return false;
        }

        synchronized (account) {
            double prevBalance = account.getBalance();
            long prevUpdatedAt = account.getUpdatedAt();
            account.deposit(amount);
            try {
                persist(account, amount, reason);
                if (amount > 0 && shouldTrackEarnedMoney(reason)) {
                    BiConsumer<UUID, Double> listener = moneyEarnedListener;
                    if (listener != null) {
                        listener.accept(uuid, amount);
                    }
                }
                return true;
            } catch (SQLException exception) {
                account.setBalance(prevBalance);
                account.setUpdatedAt(prevUpdatedAt);
                logger.severe("[EcoCore] Failed to persist deposit for "
                        + account.getUuid() + ": " + exception.getMessage());
                return false;
            }
        }
    }

    /**
     * Atomically persists a balance change: updates {@code player_accounts}
     * and appends a {@code money_ledger} entry within a single SQLite
     * transaction on one shared connection, so a mid-write crash can
     * never leave the ledger out of sync with the stored balance.
     *
     * @param account the account to save
     * @param signedChange the change amount
     * @param reason the transaction reason
     * @throws SQLException if persistence fails
     */
    public void persist(PlayerAccount account, double signedChange, String reason) throws SQLException {
        playerDao.saveAndLogAtomic(account, signedChange, reason, moneyDao);
    }

    /**
     * Completes a job mission and credits its reward while holding the same
     * per-account monitor used by normal deposits/withdrawals. The DAO commits
     * mission state, account balance and ledger entry as one transaction, then
     * the cache is updated from the committed balance.
     */
    public boolean completeMissionRewardAtomically(JobMissionDao missionDao, UUID uuid, long missionId,
                                                    int progress, double reward, String reason) throws SQLException {
        PlayerAccount account = resolve(uuid);
        if (account == null) {
            return false;
        }
        synchronized (account) {
            JobMissionDao.AtomicRewardResult result = missionDao.completeAndRewardAtomic(
                    uuid, missionId, progress, reward, reason);
            if (!result.completed()) {
                return false;
            }
            account.setBalance(result.newBalance());
            account.setUpdatedAt(System.currentTimeMillis());
            return true;
        }
    }

    @Override
    public String format(double amount) {
        return formatter.format(amount);
    }

    /**
     * Returns the shared currency formatter, used by GUI and command
     * classes that need richer formatting than {@link #format(double)} alone.
     *
     * @return the currency formatter
     */
    public CurrencyFormatter getFormatter() {
        return formatter;
    }
}