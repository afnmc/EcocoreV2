package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Translates successful fishing catches into Fisherman job actions,
 * distinguishing ordinary fish catches from treasure/junk outcomes.
 *
 * <p>No longer attempts player-level auto-sell on the catch -
 * automatic selling is exclusively the Sell Minion's job now.
 */
public final class FishListener implements Listener {

    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;

    /**
     * Creates the fish listener.
     *
     * @param jobsManager     shared jobs manager
     * @param inflationEngine shared inflation engine, used for the current job-bonus multiplier
     * @param inflationConfig resolved inflation.yml configuration
     */
    public FishListener(JobsManager jobsManager, InflationEngine inflationEngine,
                         InflationConfig inflationConfig) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caughtItem)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack caught = caughtItem.getItemStack();

        String actionKey = switch (caught.getType()) {
            case COD -> "CATCH_COD";
            case SALMON -> "CATCH_SALMON";
            case PUFFERFISH -> "CATCH_PUFFERFISH";
            case TROPICAL_FISH -> "CATCH_TROPICAL_FISH";
            case BOW, ENCHANTED_BOOK, NAME_TAG, SADDLE, NAUTILUS_SHELL -> "CATCH_TREASURE";
            default -> "CATCH_JUNK";
        };

        double jobBonusMultiplier = inflationConfig
                .getStateEffect(inflationEngine.getCurrentState())
                .jobBonusMultiplier();

        jobsManager.processAction(player.getUniqueId(), actionKey, jobBonusMultiplier);
    }
}