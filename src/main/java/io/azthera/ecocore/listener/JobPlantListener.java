package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/** Emits explicit crop-planting job actions for seeds and plantable crops. */
public final class JobPlantListener implements Listener {
    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;

    public JobPlantListener(JobsManager jobsManager, InflationEngine inflationEngine, InflationConfig inflationConfig) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        Material material = hand.getType();
        String target = switch (material) {
            case WHEAT_SEEDS -> "WHEAT";
            case CARROT -> "CARROTS";
            case POTATO -> "POTATOES";
            case BEETROOT_SEEDS -> "BEETROOTS";
            case NETHER_WART -> "NETHER_WART";
            case MELON_SEEDS -> "MELON";
            case PUMPKIN_SEEDS -> "PUMPKIN";
            case TORCHFLOWER_SEEDS -> "TORCHFLOWER";
            case PITCHER_POD -> "PITCHER_PLANT";
            case COCOA_BEANS -> "COCOA";
            default -> null;
        };
        if (target == null) return;
        double multiplier = inflationConfig.getStateEffect(inflationEngine.getCurrentState()).jobBonusMultiplier();
        jobsManager.processAction(player.getUniqueId(), "PLANT_" + target, multiplier, 1);
    }
}
