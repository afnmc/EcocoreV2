package io.azthera.ecocore.model;

import java.util.UUID;

/**
 * Represents a single player's economy account.
 * This is a mutable, in-memory view of a row in the
 * {@code player_accounts} table, kept in sync by {@code PlayerDao}.
 */
public final class PlayerAccount {

    private final UUID uuid;
    private String lastKnownName;
    private double balance;
    private long createdAt;
    private long updatedAt;

    /**
     * Creates a new player account.
     *
     * @param uuid           the player's unique id
     * @param lastKnownName  the player's last known username
     * @param balance        the current balance
     * @param createdAt      epoch millis when the account was first created
     * @param updatedAt      epoch millis of the last balance update
     */
    public PlayerAccount(UUID uuid, String lastKnownName, double balance, long createdAt, long updatedAt) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.balance = balance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public double getBalance() {
        return balance;
    }

    /**
     * Sets the account balance directly. Callers should generally prefer
     * {@link #deposit(double)} or {@link #withdraw(double)} so the
     * updated-at timestamp stays consistent.
     *
     * @param balance the new balance
     */
    public void setBalance(double balance) {
        this.balance = balance;
    }

    /**
     * Adds the given amount to the balance.
     *
     * @param amount amount to add, must be non-negative
     * @throws IllegalArgumentException if amount is negative
     */
    public void deposit(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        }
        this.balance += amount;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Removes the given amount from the balance if sufficient funds exist.
     *
     * @param amount amount to remove, must be non-negative
     * @return {@code true} if the withdrawal succeeded, {@code false} if insufficient funds
     */
    public boolean withdraw(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Withdraw amount cannot be negative");
        }
        if (this.balance < amount) {
            return false;
        }
        this.balance -= amount;
        this.updatedAt = System.currentTimeMillis();
        return true;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }
}