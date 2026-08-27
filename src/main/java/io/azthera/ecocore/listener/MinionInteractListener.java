package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.database.dao.MinionConnectionDao;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.minions.MinionConnectorEntityGui;
import io.azthera.ecocore.gui.minions.MinionUpgradeGui;
import io.azthera.ecocore.minions.MinionConnectorEntityManager;
import io.azthera.ecocore.minions.MinionConnectorManager;
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
 * Handles right-clicking a placed minion or a {@code
 * MinionConnectorEntity}. Revisi 9 replaced the old dedicated
 * "Connector Tool" item entirely: the connect-flow now starts from
 * the "Connector" button inside {@link MinionUpgradeGui}, which puts
 * the player into a pending-selection state via {@code
 * MinionConnectorManager.beginSelection}; the very next minion they
 * right-click (no special tool needed) becomes the destination and
 * the link mode (direct vs relay) is auto-detected and created
 * automatically. Cara connect ini sama untuk semua minion.
 */
public final class MinionInteractListener implements Listener {

    private final MinionManager minionManager;
    private final MinionFuelManager fuelManager;
    private final MinionsConfig minionsConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MinionConnectorManager connectorManager;
    private final MinionConnectorEntityManager connectorEntityManager;

    public MinionInteractListener(MinionManager minionManager, MinionFuelManager fuelManager,
                                   MinionsConfig minionsConfig, GuiManager guiManager, GuiConfig guiConfig,
                                   MinionConnectorManager connectorManager,
                                   MinionConnectorEntityManager connectorEntityManager) {
        this.minionManager = minionManager;
        this.fuelManager = fuelManager;
        this.minionsConfig = minionsConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.connectorManager = connectorManager;
        this.connectorEntityManager = connectorEntityManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();

        Long connectorId = connectorEntityManager.resolveConnectorId(event.getRightClicked());
        if (connectorId != null) {
            event.setCancelled(true);
            handleConnectorEntityClick(player, connectorId);
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

        Long pendingSourceId = connectorManager.getPendingSource(player.getUniqueId());
        if (pendingSourceId != null) {
            handlePendingConnectionClick(player, pendingSourceId, data);
            return;
        }

        if (!data.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("ecocore.admin")) {
            player.sendMessage("\u00a7cIni minion punya orang lain.");
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (fuelManager.isFuelItem(handItem.getType())) {
            if (fuelManager.refuelFromHand(data, handItem)) {
                player.sendMessage("\u00a7aBerhasil isi fuel! Sisa fuel: \u00a7f"
                        + (data.getFuelTicksRemaining() / 20) + " detik.");
            }
            return;
        }

        MinionUpgradeGui upgradeGui = new MinionUpgradeGui(
                player, minionManager, minionsConfig, guiManager, guiConfig, minionId, null);
        guiManager.register(player, upgradeGui);
        upgradeGui.open();
    }

    private void handlePendingConnectionClick(Player player, long pendingSourceId, MinionData destinationData) {
        connectorManager.clearSelection(player.getUniqueId());
        if (pendingSourceId == destinationData.getId()) {
            player.sendMessage("\u00a7cGak bisa connect minion ke dirinya sendiri.");
            return;
        }
        MinionData sourceData = minionManager.getMinion(pendingSourceId);
        if (sourceData == null) {
            player.sendMessage("\u00a7cSource sebelumnya udah gak ada. Coba mulai ulang dari tombol Connector.");
            return;
        }
        MinionConnectorManager.ResolvedLink resolvedLink = connectorManager.resolveLink(sourceData, destinationData);
        if (!resolvedLink.isValid()) {
            player.sendMessage("\u00a7cGak bisa bikin koneksi ini: \u00a7f" + resolvedLink.rejectionReason());
            return;
        }
        boolean connected = connectorManager.connect(player.getUniqueId(), sourceData.getId(),
                destinationData.getId(), resolvedLink);
        if (connected) {
            String modeLabel = resolvedLink.linkMode() == MinionConnectionDao.LinkMode.RELAY
                    ? "\u00a7d(via Minion Connector - relay)" : "\u00a7a(direct)";
            player.sendMessage("\u00a7bBerhasil connect: \u00a7f" + sourceData.getType().configKey() + " \u00a7b-> \u00a7f"
                    + destinationData.getType().configKey() + " " + modeLabel);
            guiManager.playSound(player, "click");
        } else {
            player.sendMessage("\u00a7cGagal membuat koneksi.");
        }
    }

    private void handleConnectorEntityClick(Player player, long connectorId) {
        MinionConnectorEntityManager.ActiveConnector connector = connectorEntityManager.getConnector(connectorId);
        if (connector == null) {
            return;
        }
        if (!connector.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("ecocore.admin")) {
            player.sendMessage("\u00a7cIni Minion Connector punya orang lain.");
            return;
        }
        MinionConnectorEntityGui gui = new MinionConnectorEntityGui(player, connectorEntityManager, guiManager, connectorId);
        guiManager.register(player, gui);
        gui.open();
    }
}
