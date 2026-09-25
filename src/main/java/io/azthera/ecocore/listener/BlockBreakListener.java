package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Translates block-break events into job actions (Miner, Woodcutter,
 * Farmer, Excavator all key off this listener).
 *
 * <p>No longer attempts player-level auto-sell on drops directly;
 * that is handled through {@code sell.AutoSellManager} when enabled.
 */
public final class BlockBreakListener implements Listener {

    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;

    /**
     * Creates the block break listener.
     *
     * @param jobsManager     shared jobs manager
     * @param inflationEngine shared inflation engine, used for the current job-bonus multiplier
     * @param inflationConfig resolved inflation.yml configuration
     */
    public BlockBreakListener(JobsManager jobsManager, InflationEngine inflationEngine,
                               InflationConfig inflationConfig) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String matName = block.getType().name();
        String actionKey = "BREAK_" + matName;

        double jobBonusMultiplier = inflationConfig
                .getStateEffect(inflationEngine.getCurrentState())
                .jobBonusMultiplier();

        java.util.UUID playerUuid = event.getPlayer().getUniqueId();
        jobsManager.processAction(playerUuid, actionKey, jobBonusMultiplier);

        // Detect crop harvest (mature ageable crops or full-block plants)
        if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
            if (ageable.getAge() >= ageable.getMaximumAge()) {
                jobsManager.processAction(playerUuid, "HARVEST_" + matName, jobBonusMultiplier);
                if (matName.equals("CARROTS")) {
                    jobsManager.processAction(playerUuid, "HARVEST_CARROT", jobBonusMultiplier);
                } else if (matName.equals("POTATOES")) {
                    jobsManager.processAction(playerUuid, "HARVEST_POTATO", jobBonusMultiplier);
                } else if (matName.equals("BEETROOTS")) {
                    jobsManager.processAction(playerUuid, "HARVEST_BEETROOT", jobBonusMultiplier);
                }
            }
        } else if (matName.equals("PUMPKIN") || matName.equals("MELON") || matName.equals("SUGAR_CANE")
                || matName.equals("CACTUS") || matName.equals("BAMBOO") || matName.equals("COCOA")) {
            jobsManager.processAction(playerUuid, "HARVEST_" + matName, jobBonusMultiplier);
        }
    }
}
