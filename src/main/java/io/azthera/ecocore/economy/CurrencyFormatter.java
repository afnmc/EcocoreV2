package io.azthera.ecocore.economy;

import io.azthera.ecocore.config.ConfigManager;

import java.text.DecimalFormat;

/**
 * Formats raw balance/price doubles into display strings using the
 * currency symbol, singular/plural names, and decimal format
 * configured in {@code config.yml}.
 */
public final class CurrencyFormatter {

    private final ConfigManager configManager;
    private final DecimalFormat decimalFormat;

    /**
     * Creates a currency formatter.
     *
     * @param configManager resolved config manager (read for currency symbol/names/format)
     */
    public CurrencyFormatter(ConfigManager configManager) {
        this.configManager = configManager;
        this.decimalFormat = new DecimalFormat(configManager.getDecimalFormat());
    }

    /**
     * Formats an amount with the configured currency symbol, e.g. "$1,234.56".
     *
     * @param amount the amount to format
     * @return the formatted string
     */
    public String format(double amount) {
        return configManager.getCurrencySymbol() + decimalFormat.format(amount);
    }

    /**
     * Formats an amount with the configured currency name (singular/plural
     * chosen automatically), e.g. "1,234.56 Dollars".
     *
     * @param amount the amount to format
     * @return the formatted string with a spelled-out currency name
     */
    public String formatWithName(double amount) {
        String name = Math.abs(amount - 1.0) < 0.0001
                ? configManager.getCurrencyNameSingular()
                : configManager.getCurrencyNamePlural();
        return decimalFormat.format(amount) + " " + name;
    }
}