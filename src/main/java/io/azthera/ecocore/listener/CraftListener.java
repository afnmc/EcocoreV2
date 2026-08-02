package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Translates crafting-table results into Crafter job actions,
 * categorizing the crafted result as a tool, armor piece, or generic
 * block/item for reward weighting.
 */
public final class CraftListener implements Listener {

    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;

    /**
     * Creates the craft listener.
     *
     * @param jobsManager     shared jobs manager
     * @param inflationEngine shared inflation engine, used for the current job-bonus multiplier
     * @param inflationConfig resolved inflation.yml configuration
     */
    public CraftListener(JobsManager jobsManager, InflationEngine inflationEngine, InflationConfig inflationConfig) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) {
            return;
        }

        String actionKey = classifyResult(result);
        double jobBonusMultiplier = inflationConfig
                .getStateEffect(inflationEngine.getCurrentState())
                .jobBonusMultiplier();

        jobsManager.processAction(player.getUniqueId(), actionKey, jobBonusMultiplier);
    }

    private String classifyResult(ItemStack result) {
        String name = result.getType().name();
        if (name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return "CRAFT_TOOL";
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            return "CRAFT_ARMOR";
        }
        if (result.getType().isBlock()) {
            return "CRAFT_BLOCK";
        }
        return "CRAFT_ITEM";
    }
}