package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.sell.AutoSellManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Translates block-break events into job actions (Miner, Woodcutter,
 * Farmer, Excavator all key off this listener) and, if the breaking
 * player has auto-sell enabled, attempts to liquidate the drop
 * immediately instead of letting it land in their inventory.
 */
public final class BlockBreakListener implements Listener {

    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;
    private final AutoSellManager autoSellManager;

    /**
     * Creates the block break listener.
     *
     * @param jobsManager     shared jobs manager
     * @param inflationEngine shared inflation engine, used for the current job-bonus multiplier
     * @param inflationConfig resolved inflation.yml configuration
     * @param autoSellManager shared auto-sell manager
     */
    public BlockBreakListener(JobsManager jobsManager, InflationEngine inflationEngine,
                               InflationConfig inflationConfig, AutoSellManager autoSellManager) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
        this.autoSellManager = autoSellManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String actionKey = "BREAK_" + block.getType().name();

        double jobBonusMultiplier = inflationConfig
                .getStateEffect(inflationEngine.getCurrentState())
                .jobBonusMultiplier();

        jobsManager.processAction(event.getPlayer().getUniqueId(), actionKey, jobBonusMultiplier);

        if (autoSellManager.isEnabled(event.getPlayer().getUniqueId())) {
            for (ItemStack drop : block.getDrops(event.getPlayer().getInventory().getItemInMainHand())) {
                autoSellManager.attemptAutoSell(event.getPlayer().getUniqueId(), drop);
            }
        }
    }
}