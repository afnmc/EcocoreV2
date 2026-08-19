package io.azthera.ecocore.economy;

import java.util.UUID;

/**
 * Public-facing contract for EcoCore's economy layer. Kept separate
 * from {@link EconomyEngine} so other modules (jobs, minions, shop,
 * Discord) and third-party plugins consuming {@code EcoCoreAPI} can
 * depend on this narrow interface rather than the full engine.
 */
public interface EconomyAPI {

    /**
     * Returns a player's current balance, loading their account if needed.
     *
     * @param uuid the player's uuid
     * @return the current balance, or 0.0 if the account could not be loaded
     */
    double getBalance(UUID uuid);

    /**
     * Checks whether a player has at least the given amount.
     *
     * @param uuid   the player's uuid
     * @param amount the amount to check for
     * @return {@code true} if the player's balance is at least {@code amount}
     */
    boolean has(UUID uuid, double amount);

    /**
     * Attempts to withdraw an amount from a player's balance.
     *
     * @param uuid   the player's uuid
     * @param amount the amount to withdraw, must be non-negative
     * @param reason a standardized reason code, see {@link TransactionLogger}
     * @return {@code true} if the withdrawal succeeded, {@code false} if insufficient funds
     */
    boolean withdraw(UUID uuid, double amount, String reason);

    /**
     * Deposits an amount into a player's balance.
     *
     * @param uuid   the player's uuid
     * @param amount the amount to deposit, must be non-negative
     * @param reason a standardized reason code, see {@link TransactionLogger}
     */
    void deposit(UUID uuid, double amount, String reason);

    /**
     * Formats an amount using the configured currency symbol.
     *
     * @param amount the amount to format
     * @return the formatted display string
     */
    String format(double amount);
}