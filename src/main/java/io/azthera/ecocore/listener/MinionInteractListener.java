package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.MinionsConfig;
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
 * MinionConnectorEntity}.
 *
 * <p>Bug-fix round: the click-source-then-click-destination-in-world
 * connect flow has been removed entirely per explicit user request -
 * connecting minions is now done purely through a GUI list opened
 * from {@link MinionUpgradeGui}'s Connector button (see {@link
 * io.azthera.ecocore.gui.minions.MinionConnectListGui}), so
 * right-clicking a minion never triggers a pending-connection
 * completion anymore. This listener now only opens the minion's
 * upgrade screen (or refuels it, if the player is holding fuel).
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