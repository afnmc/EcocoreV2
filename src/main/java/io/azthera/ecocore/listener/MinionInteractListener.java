package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.minions.MinionStorageGui;
import io.azthera.ecocore.gui.minions.MinionUpgradeGui;
import io.azthera.ecocore.minions.MinionFuelManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles right-clicking a minion's visual entity in the world:
 * holding a valid fuel item (coal, coal block, lava bucket) refuels
 * it directly; otherwise it opens the owner's upgrade screen, or
 * their storage screen if sneaking. This is the primary way players
 * are meant to interact with placed minions - walk up to it and
 * right-click it, just like a villager.
 */
public final class MinionInteractListener implements Listener {

    private final MinionManager minionManager;
    private final MinionFuelManager fuelManager;
    private final MinionsConfig minionsConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;

    /**
     * Creates the minion interact listener.
     *
     * @param minionManager shared minion manager
     * @param fuelManager   shared fuel manager
     * @param minionsConfig resolved minions.yml configuration
     * @param guiManager    shared GUI manager
     * @param guiConfig     resolved gui.yml configuration
     */
    public MinionInteractListener(MinionManager minionManager, MinionFuelManager fuelManager,
                                   MinionsConfig minionsConfig, GuiManager guiManager, GuiConfig guiConfig) {
        this.minionManager = minionManager;
        this.fuelManager = fuelManager;
        this.minionsConfig = minionsConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Long minionId = minionManager.resolveMinionId(event.getRightClicked());
        if (minionId == null) {
            return;
        }
        event.setCancelled(true);

        MinionData data = minionManager.getMinion(minionId);
        if (data == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!data.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("ecocore.admin")) {
            player.sendMessage("§cIni minion punya orang lain.");
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (fuelManager.isFuelItem(handItem.getType())) {
            if (fuelManager.refuelFromHand(data, handItem)) {
                player.sendMessage("§aBerhasil isi fuel! Sisa fuel: §f"
                        + (data.getFuelTicksRemaining() / 20) + " detik.");
            }
            return;
        }

        if (player.isSneaking()) {
            MinionStorageGui storageGui = new MinionStorageGui(player, minionManager, guiManager, minionId, null);
            guiManager.register(player, storageGui);
            storageGui.open();
            return;
        }

        MinionUpgradeGui upgradeGui = new MinionUpgradeGui(
                player, minionManager, minionsConfig, guiManager, guiConfig, minionId, null);
        guiManager.register(player, upgradeGui);
        upgradeGui.open();
    }
}
