package io.azthera.ecocore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed view of {@code night-market.yml}: rotation timing/sizing
 * rules and the pool of possible expensive/rare items a rotation can
 * draw from.
 */
public final class NightMarketConfig {

    /**
     * A single item available in the night market's item pool.
     *
     * @param id        the pool entry id, also used as the offer id once picked
     * @param material  the Bukkit Material backing this item
     * @param basePrice the base price before the rotation price multiplier
     */
    public record PoolEntry(String id, String material, double basePrice) {
    }

    private final int rotationIntervalHours;
    private final int slots;
    private final int stockPerItem;
    private final double priceMultiplier;
    private final List<PoolEntry> pool = new ArrayList<>();

    /**
     * Parses night market configuration from the loaded {@code night-market.yml}.
     *
     * @param config the loaded night-market.yml
     */
    public NightMarketConfig(FileConfiguration config) {
        this.rotationIntervalHours = config.getInt("rotation.interval-hours", 6);
        this.slots = config.getInt("rotation.slots", 5);
        this.stockPerItem = config.getInt("rotation.stock-per-item", 5);
        this.priceMultiplier = config.getDouble("rotation.price-multiplier", 1.4);

        ConfigurationSection poolSection = config.getConfigurationSection("pool");
        if (poolSection != null) {
            for (String id : poolSection.getKeys(false)) {
                ConfigurationSection section = poolSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                pool.add(new PoolEntry(
                        id,
                        section.getString("material", "STONE"),
                        section.getDouble("base-price", 50.0)
                ));
            }
        }
    }

    public int getRotationIntervalHours() {
        return rotationIntervalHours;
    }

    public int getSlots() {
        return slots;
    }

    public int getStockPerItem() {
        return stockPerItem;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }

    public List<PoolEntry> getPool() {
        return pool;
    }
}