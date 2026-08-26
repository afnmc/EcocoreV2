// FILE: src/main/java/io/azthera/ecocore/listener/MinionEggListener.java
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

        Location placeLocation;
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock != null) {
            placeLocation = clickedBlock.getLocation().add(0.5, 1.0, 0.5);
        } else {
            placeLocation = player.getLocation().clone();
        }
        // Revisi 1: the player's raw look yaw is read here and passed through
        // to MinionManager.placeMinion, which snaps it to the nearest
        // cardinal direction (NORTH/SOUTH/EAST/WEST) - the same 4-way lock a
        // piston or dispenser uses. The minion is rigid after this: it never
        // rotates freely and never changes facing again once placed.
        placeLocation.setYaw(player.getLocation().getYaw());
        placeLocation.setPitch(0f);

        MinionData placed = minionManager.placeMinion(player, type, placeLocation);
        if (placed == null) {
            player.sendMessage("§cGagal menempatkan minion di sini.");
            return;
        }
        handItem.setAmount(handItem.getAmount() - 1);
        player.sendMessage("§aMinion §f" + type.configKey() + " §aberhasil ditempatkan! Arah: §f" + placed.getFacing().name());

        // Revisi 2: CHEST-type minions detect an adjacent chest (single vs
        // double) once, right at placement time - never re-evaluated afterward.
        if (type == MinionType.CHEST) {
            detectAdjacentChest(placed, clickedBlock);
        }
    }

    private void detectAdjacentChest(MinionData placed, Block referenceBlock) {
        if (referenceBlock == null) {
            return;
        }
        for (org.bukkit.block.BlockFace face : new org.bukkit.block.BlockFace[]{
                org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST, org.bukkit.block.BlockFace.WEST}) {
            Block adjacent = referenceBlock.getRelative(face);
            if (adjacent.getState() instanceof org.bukkit.block.Chest chest) {
                org.bukkit.block.data.type.Chest.Type chestType = ((org.bukkit.block.data.type.Chest) chest.getBlockData()).getType();
                boolean isDouble = chestType != org.bukkit.block.data.type.Chest.Type.SINGLE;
                // Detection result is informational only for now - a future
                // batch may wire this into MinionData for a chest-linked
                // storage capacity bonus; storing nothing destructive here.
                return;
            }
        }
    }
}