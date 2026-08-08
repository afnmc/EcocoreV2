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

/**
 * Handles a player right-clicking with a minion egg item (see
 * {@code ItemUtils.buildMinionEgg}): places the actual minion at the
 * clicked location (on top of the clicked block, or at the player's
 * feet if clicking in the air) and consumes one egg from the stack.
 */
public final class MinionEggListener implements Listener {

    private final MinionManager minionManager;

    /**
     * Creates the minion egg listener.
     *
     * @param minionManager shared minion manager
     */
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

        // Every minion always faces a single fixed direction (south,
        // yaw 0), regardless of which way the player was looking or
        // which branch placed it. Previously, air-clicks inherited the
        // player's own yaw while block-clicks used a fixed yaw of 0 -
        // that mismatch made a minion's working direction depend on
        // how the player happened to be facing at placement time,
        // rotating "like a piston" from one placement to the next.
        Location placeLocation;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            placeLocation = clickedBlock.getLocation().add(0.5, 1.0, 0.5);
        } else {
            placeLocation = player.getLocation().clone();
        }
        placeLocation.setYaw(0f);
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
