package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.minions.ConnectorConfirmGui;
import io.azthera.ecocore.gui.minions.MinionStorageGui;
import io.azthera.ecocore.gui.minions.MinionUpgradeGui;
import io.azthera.ecocore.minions.MinionConnectorManager;
import io.azthera.ecocore.minions.MinionFuelManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.utils.ItemUtils;
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
    private final MinionConnectorManager connectorManager;

    /**
     * Creates the minion interact listener.
     *
     * @param minionManager    shared minion manager
     * @param fuelManager      shared fuel manager
     * @param minionsConfig    resolved minions.yml configuration
     * @param guiManager       shared GUI manager
     * @param guiConfig        resolved gui.yml configuration
     * @param connectorManager shared Connector Network manager, used when the player is holding the Connector Tool
     */
    public MinionInteractListener(MinionManager minionManager, MinionFuelManager fuelManager,
                                   MinionsConfig minionsConfig, GuiManager guiManager, GuiConfig guiConfig,
                                   MinionConnectorManager connectorManager) {
        this.minionManager = minionManager;
        this.fuelManager = fuelManager;
        this.minionsConfig = minionsConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.connectorManager = connectorManager;
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

        if (ItemUtils.isConnectorTool(handItem)) {
            handleConnectorToolClick(player, data);
            return;
        }

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

    /**
     * Handles a Connector Tool right-click on a minion: the first
     * click on any of the player's own minions selects it as the
     * source and remembers it; a second click on a DIFFERENT owned
     * minion selects it as the destination and opens a confirmation
     * screen before the connection is actually saved. Clicking the
     * same minion again (or a minion belonging to someone else)
     * re-selects/rejects rather than silently doing nothing, so the
     * tool always gives clear feedback.
     *
     * @param player the player using the tool
     * @param data   the clicked minion's persistent data (ownership already verified by the caller)
     */
    private void handleConnectorToolClick(Player player, MinionData data) {
        Long pendingSourceId = connectorManager.getPendingSource(player.getUniqueId());

        if (pendingSourceId == null) {
            connectorManager.beginSelection(player.getUniqueId(), data.getId());
            player.sendMessage("§bSumber (source) dipilih: §f" + data.getType().configKey()
                    + " §b(#" + data.getId() + "). Klik kanan minion tujuan sekarang.");
            return;
        }

        if (pendingSourceId == data.getId()) {
            player.sendMessage("§eMinion ini udah jadi source. Klik minion lain buat jadi tujuan,"
                    + " atau klik minion lain dulu buat ganti source.");
            return;
        }

        MinionData sourceData = minionManager.getMinion(pendingSourceId);
        if (sourceData == null) {
            // Source minion was removed while pending - just restart with this click as the new source.
            connectorManager.beginSelection(player.getUniqueId(), data.getId());
            player.sendMessage("§cSource sebelumnya udah gak ada. §bSumber baru dipilih: §f"
                    + data.getType().configKey() + " §b(#" + data.getId() + ").");
            return;
        }

        connectorManager.clearSelection(player.getUniqueId());

        ConnectorConfirmGui confirmGui = new ConnectorConfirmGui(
                player, minionManager, connectorManager, guiManager, sourceData, data);
        guiManager.register(player, confirmGui);
        confirmGui.open();
    }
}
