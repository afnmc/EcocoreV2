package io.azthera.ecocore.placeholder;

import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.InflationRecord;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

/**
 * PlaceholderAPI expansion exposing EcoCore's economy, market, jobs,
 * and minions data as {@code %ecocore_*%} placeholders. Registered
 * once during plugin enable via {@link #register()}, if
 * PlaceholderAPI is present and {@code config.yml modules.placeholderapi-enabled}
 * is true.
 *
 * <p>Supported placeholders:
 * <ul>
 *   <li>{@code %ecocore_money%} - the player's current balance</li>
 *   <li>{@code %ecocore_money_formatted%} - the player's balance with currency symbol</li>
 *   <li>{@code %ecocore_inflation%} - current inflation percentage</li>
 *   <li>{@code %ecocore_deflation%} - current deflation percentage</li>
 *   <li>{@code %ecocore_economic_state%} - current macro-economic state</li>
 *   <li>{@code %ecocore_stock_<item>%} - an item's current stock</li>
 *   <li>{@code %ecocore_price_<item>%} - an item's current live price</li>
 *   <li>{@code %ecocore_job_<jobtype>_level%} - the player's level in a job</li>
 *   <li>{@code %ecocore_job_<jobtype>_xp%} - the player's xp in a job</li>
 *   <li>{@code %ecocore_job_<jobtype>_prestige%} - the player's prestige in a job</li>
 *   <li>{@code %ecocore_minion_count%} - the player's total placed minion count</li>
 * </ul>
 */
public final class EcoCorePlaceholderExpansion extends PlaceholderExpansion {

    private final EconomyEngine economyEngine;
    private final ShopManager shopManager;
    private final InflationEngine inflationEngine;
    private final JobsManager jobsManager;
    private final MinionManager minionManager;

    /**
     * Creates the placeholder expansion.
     *
     * @param economyEngine   shared economy engine
     * @param shopManager     shared shop manager
     * @param inflationEngine shared inflation engine
     * @param jobsManager     shared jobs manager
     * @param minionManager   shared minion manager
     */
    public EcoCorePlaceholderExpansion(EconomyEngine economyEngine, ShopManager shopManager,
                                        InflationEngine inflationEngine, JobsManager jobsManager,
                                        MinionManager minionManager) {
        this.economyEngine = economyEngine;
        this.shopManager = shopManager;
        this.inflationEngine = inflationEngine;
        this.jobsManager = jobsManager;
        this.minionManager = minionManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ecocore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Afn";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        if (params.equals("money")) {
            return String.valueOf(economyEngine.getBalance(player.getUniqueId()));
        }
        if (params.equals("money_formatted")) {
            return economyEngine.format(economyEngine.getBalance(player.getUniqueId()));
        }

        if (params.equals("inflation")) {
            InflationRecord record = inflationEngine.getLatestRecord();
            return record != null ? String.format("%.2f", record.inflationPercent()) : "0.00";
        }
        if (params.equals("deflation")) {
            InflationRecord record = inflationEngine.getLatestRecord();
            return record != null ? String.format("%.2f", record.deflationPercent()) : "0.00";
        }
        if (params.equals("economic_state")) {
            return inflationEngine.getCurrentState().name();
        }

        if (params.startsWith("stock_")) {
            String itemId = params.substring("stock_".length());
            ShopItemRecord item = shopManager.getItem(itemId);
            return item != null ? String.valueOf(item.getStock()) : "0";
        }

        if (params.startsWith("price_")) {
            String itemId = params.substring("price_".length());
            ShopItemRecord item = shopManager.getItem(itemId);
            return item != null ? String.format("%.2f", item.getCurrentPrice()) : "0.00";
        }

        if (params.startsWith("job_") && params.endsWith("_level")) {
            return resolveJobField(player, params, "_level", JobFieldType.LEVEL);
        }
        if (params.startsWith("job_") && params.endsWith("_xp")) {
            return resolveJobField(player, params, "_xp", JobFieldType.XP);
        }
        if (params.startsWith("job_") && params.endsWith("_prestige")) {
            return resolveJobField(player, params, "_prestige", JobFieldType.PRESTIGE);
        }

        if (params.equals("minion_count")) {
            return String.valueOf(minionManager.getMinionsOwnedBy(player.getUniqueId()).size());
        }

        return null;
    }

    private enum JobFieldType {
        LEVEL, XP, PRESTIGE
    }

    private String resolveJobField(OfflinePlayer player, String params, String suffix, JobFieldType fieldType) {
        String jobKey = params.substring("job_".length(), params.length() - suffix.length());
        JobType type = JobType.fromConfigKey(jobKey);
        if (type == null) {
            return "0";
        }

        try {
            JobData data = jobsManager.getProgress(player.getUniqueId(), type);
            if (data == null) {
                return "0";
            }
            return switch (fieldType) {
                case LEVEL -> String.valueOf(data.getLevel());
                case XP -> String.valueOf(data.getXp());
                case PRESTIGE -> String.valueOf(data.getPrestige());
            };
        } catch (SQLException exception) {
            return "0";
        }
    }
}