package io.azthera.ecocore.economy;

/**
 * Standardized reason codes used when writing to the money ledger,
 * so every balance change across the plugin is tagged consistently
 * for later auditing via {@code /ecocore debug} or the money ledger table.
 */
public final class TransactionLogger {

    public static final String REASON_SHOP_BUY = "shop_buy";
    public static final String REASON_SHOP_SELL = "shop_sell";
    public static final String REASON_JOB_REWARD = "job_reward";
    public static final String REASON_MINION_AUTOSELL = "minion_autosell";
    public static final String REASON_ADMIN_ADJUST = "admin_adjust";
    public static final String REASON_STARTING_BALANCE = "starting_balance";
    public static final String REASON_MISSION_REWARD = "mission_reward";

    private TransactionLogger() {
        // Constants holder, not instantiable.
    }
}