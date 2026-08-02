package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.sell.AutoSellManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Translates entity death events into Hunter job actions when killed
 * by a player, and auto-sells the resulting drops if the killer has
 * auto-sell enabled.
 */
public final class EntityDeathListener implements Listener {

    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;
    private final AutoSellManager autoSellManager;

    /**
     * Creates the entity death listener.
     *
     * @param jobsManager     shared jobs manager
     * @param inflationEngine shared inflation engine, used for the current job-bonus multiplier
     * @param inflationConfig resolved inflation.yml configuration
     * @param autoSellManager shared auto-sell manager
     */
    public EntityDeathListener(JobsManager jobsManager, InflationEngine inflationEngine,
                                InflationConfig inflationConfig, AutoSellManager autoSellManager) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
        this.autoSellManager = autoSellManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player killer)) {
            return;
        }

        String actionKey = "KILL_" + event.getEntityType().name();
        double jobBonusMultiplier = inflationConfig
                .getStateEffect(inflationEngine.getCurrentState())
                .jobBonusMultiplier();

        jobsManager.processAction(killer.getUniqueId(), actionKey, jobBonusMultiplier);

        if (autoSellManager.isEnabled(killer.getUniqueId())) {
            for (ItemStack drop : event.getDrops()) {
                autoSellManager.attemptAutoSell(killer.getUniqueId(), drop);
            }
        }
    }
}