package io.azthera.ecocore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Parsed view of {@code prices.yml}: global and per-category price
 * bounds/elasticity used by the AI engine when computing new prices,
 * plus rounding and buy/sell spread behavior.
 */
public final class PricesConfig {

    /**
     * Per-category price bound overrides.
     *
     * @param elasticity        elasticity coefficient for this category
     * @param maxPriceMultiplier max price as a multiple of base price
     */
    public record CategoryOverride(double elasticity, double maxPriceMultiplier) {
    }

    private final double globalMinPriceMultiplier;
    private final double globalMaxPriceMultiplier;
    private final double baseElasticity;
    private final Map<String, CategoryOverride> categoryOverrides = new HashMap<>();
    private final String roundingMode;
    private final double roundingStep;
    private final double defaultSpreadPercent;
    private final double minSpreadPercent;
    private final double maxSpreadPercent;

    /**
     * Parses price bound configuration from the loaded {@code prices.yml}.
     *
     * @param config the loaded prices.yml
     */
    public PricesConfig(FileConfiguration config) {
        this.globalMinPriceMultiplier = config.getDouble("global.min-price-multiplier", 0.10);
        this.globalMaxPriceMultiplier = config.getDouble("global.max-price-multiplier", 5.00);
        this.baseElasticity = config.getDouble("global.base-elasticity", 1.0);

        ConfigurationSection overridesSection = config.getConfigurationSection("category-overrides");
        if (overridesSection != null) {
            for (String category : overridesSection.getKeys(false)) {
                ConfigurationSection section = overridesSection.getConfigurationSection(category);
                if (section == null) {
                    continue;
                }
                categoryOverrides.put(category, new CategoryOverride(
                        section.getDouble("elasticity", baseElasticity),
                        section.getDouble("max-price-multiplier", globalMaxPriceMultiplier)
                ));
            }
        }

        this.roundingMode = config.getString("rounding.mode", "NEAREST");
        this.roundingStep = config.getDouble("rounding.step", 0.01);

        this.defaultSpreadPercent = config.getDouble("buy-sell-spread.default-spread-percent", 12.0);
        this.minSpreadPercent = config.getDouble("buy-sell-spread.min-spread-percent", 5.0);
        this.maxSpreadPercent = config.getDouble("buy-sell-spread.max-spread-percent", 35.0);
    }

    public double getGlobalMinPriceMultiplier() {
        return globalMinPriceMultiplier;
    }

    public double getGlobalMaxPriceMultiplier() {
        return globalMaxPriceMultiplier;
    }

    public double getBaseElasticity() {
        return baseElasticity;
    }

    /**
     * Returns the elasticity to use for a given category, falling back to
     * {@link #getBaseElasticity()} if no override is configured.
     *
     * @param category the shop category id
     * @return the elasticity coefficient
     */
    public double getElasticityForCategory(String category) {
        CategoryOverride override = categoryOverrides.get(category);
        return override != null ? override.elasticity() : baseElasticity;
    }

    /**
     * Returns the max price multiplier to use for a given category,
     * falling back to {@link #getGlobalMaxPriceMultiplier()}.
     *
     * @param category the shop category id
     * @return the max price multiplier
     */
    public double getMaxPriceMultiplierForCategory(String category) {
        CategoryOverride override = categoryOverrides.get(category);
        return override != null ? override.maxPriceMultiplier() : globalMaxPriceMultiplier;
    }

    public String getRoundingMode() {
        return roundingMode;
    }

    public double getRoundingStep() {
        return roundingStep;
    }

    public double getDefaultSpreadPercent() {
        return defaultSpreadPercent;
    }

    public double getMinSpreadPercent() {
        return minSpreadPercent;
    }

    public double getMaxSpreadPercent() {
        return maxSpreadPercent;
    }
}