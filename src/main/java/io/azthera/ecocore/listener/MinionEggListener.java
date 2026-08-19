package io.azthera.ecocore.listener;

import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class MinionEggListener implements Listener {

    private final MinionManager minionManager;

    public MinionEggListener(MinionManager minionManager) {
        this.minionManager = minionManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        ItemStack handItem = event.getItem();
        MinionType type = ItemUtils.readMinionEggType(handItem);
        if (type == null) {
            return;
        }

        event.setCancelled(true);
        var player = event.getPlayer();

        if (minionManager.countOwnedBy(player.getUniqueId()) >= MinionManager.DEFAULT_MAX_MINIONS_PER_PLAYER) {
            player.sendMessage("§cLu udah mencapai batas maksimal minion ("
                    + MinionManager.DEFAULT_MAX_MINIONS_PER_PLAYER + ").");
            return;
        }

        // Every minion faces the direction the player was looking when
        // placed, the same way a piston/dispenser orients itself based
        // on the placing player's direction - not a fixed direction
        // regardless of how the player was standing.
        Location placeLocation;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            placeLocation = clickedBlock.getLocation().add(0.5, 1.0, 0.5);
        } else {
            placeLocation = player.getLocation().clone();
        }
        placeLocation.setYaw(player.getLocation().getYaw());
        placeLocation.setPitch(0f);

        MinionData placed = minionManager.placeMinion(player, type, placeLocation);
        if (placed == null) {
            player.sendMessage("§cGagal menempatkan minion di sini.");
            return;
        }

        handItem.setAmount(handItem.getAmount() - 1);
        player.sendMessage("§aMinion §f" + type.configKey() + " §aberhasil ditempatkan!");
    }
}
