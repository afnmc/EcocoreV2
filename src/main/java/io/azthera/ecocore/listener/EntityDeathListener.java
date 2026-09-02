package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Translates entity death events into Hunter job actions when killed
 * by a player.
 *
 * <p>No longer attempts player-level auto-sell on drops - automatic
 * selling is exclusively the Sell Minion's job now.
 */
public final class EntityDeathListener implements Listener {

    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;

    /**
     * Creates the entity death listener.
     *
     * @param jobsManager     shared jobs manager
     * @param inflationEngine shared inflation engine, used for the current job-bonus multiplier
     * @param inflationConfig resolved inflation.yml configuration
     */
    public EntityDeathListener(JobsManager jobsManager, InflationEngine inflationEngine,
                                InflationConfig inflationConfig) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
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
    }
}