package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.ItemStack;

/**
 * Detects job actions that are not represented by the normal block/craft/fish
 * listeners: furnace extraction, villager trading, and anvil output.
 *
 * <p>FurnaceExtractEvent is intentionally used instead of FurnaceSmeltEvent:
 * it has the player who actually collected the output, which prevents passive
 * furnace automation from awarding a job to nobody.</p>
 */
public final class JobActionListener implements Listener {

    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;

    public JobActionListener(JobsManager jobsManager, InflationEngine inflationEngine,
                             InflationConfig inflationConfig) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        ItemStack result = new ItemStack(event.getItemType(), Math.max(1, event.getItemAmount()));
        if (result.getType().isAir()) {
            return;
        }

        String actionKey = "SMELT_" + result.getType().name();
        process(player, actionKey, result.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryType type = event.getView().getTopInventory().getType();
        int rawSlot = event.getRawSlot();

        // Vanilla merchant result slot. The selected recipe is only available
        // when the player has a real villager trade selected.
        if (type == InventoryType.MERCHANT && rawSlot == 2
                && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()
                && event.getView().getTopInventory() instanceof MerchantInventory merchant) {
            MerchantRecipe recipe = merchant.getSelectedRecipe();
            if (recipe != null) {
                int amount = Math.max(1, recipe.getResult().getAmount());
                process(player, "TRADE_VILLAGER_" + recipe.getResult().getType().name(), amount);
            }
            return;
        }

        // Vanilla anvil result slot.
        if (type == InventoryType.ANVIL && rawSlot == 2
                && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
            process(player, "ANVIL_" + event.getCurrentItem().getType().name(),
                    Math.max(1, event.getCurrentItem().getAmount()));
        }
    }

    private void process(Player player, String actionKey, int amount) {
        double multiplier = inflationConfig
                .getStateEffect(inflationEngine.getCurrentState())
                .jobBonusMultiplier();
        jobsManager.processAction(player.getUniqueId(), actionKey, multiplier, amount);
    }
}
