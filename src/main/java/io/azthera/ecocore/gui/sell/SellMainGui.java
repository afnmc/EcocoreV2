package io.azthera.ecocore.gui.sell;

import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/**
 * The {@code /sell} screen: a drag-and-drop deposit area (18 slots)
 * plus quick actions. Players place items into the deposit area
 * freely - clicks there are NOT cancelled, so items can be taken back
 * out normally like any chest. Clicking "Sell Deposited Items"
 * liquidates whatever's currently sitting in the deposit area.
 *
 * <p>If the player closes the screen with items still sitting in the
 * deposit area (accidentally or on purpose), those items are returned
 * to their inventory in {@link #handleClose}, dropping any overflow
 * on the ground if their inventory is full - nothing is ever lost
 * silently.
 *
 * <p>This screen no longer offers a player-level Auto Sell toggle:
 * automatic selling is now exclusively the Sell Minion's job (see
 * {@code SellerMinion}), so a separate global auto-sell-on-pickup
 * switch here would be redundant and confusing.
 */
public final class SellMainGui extends AbstractGui {

    private static final int INFO_SLOT = 4;
    private static final int DEPOSIT_START_SLOT = 9;
    private static final int DEPOSIT_END_SLOT = 26;
    private static final int SELL_INVENTORY_SLOT = 38;
    private static final int SELL_DEPOSITED_SLOT = 40;
    private static final int CLOSE_SLOT = 49;

    private final SellManager sellManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;

    /**
     * Creates the sell main screen.
     *
     * @param viewer         the viewing player
     * @param sellManager    shared sell manager
     * @param guiManager     shared GUI manager
     * @param messagesConfig resolved messages.yml configuration
     */
    public SellMainGui(Player viewer, SellManager sellManager,
                        GuiManager guiManager, MessagesConfig messagesConfig) {
        super(viewer);
        this.sellManager = sellManager;
        this.guiManager = guiManager;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, "§8Sell");
        render();
    }

    /**
     * Whether dragging is allowed at this raw slot: the deposit area
     * (free placement of any item, including drag-splitting a stack
     * across several deposit slots at once) plus the viewer's own
     * bottom inventory (handled by the default implementation).
     *
     * @param rawSlot the raw slot index touched by the drag
     * @return {@code true} if dragging into this slot is allowed
     */
    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        return (rawSlot >= DEPOSIT_START_SLOT && rawSlot <= DEPOSIT_END_SLOT) || super.isFreeDragSlot(rawSlot);
    }

    /**
     * Repopulates the control slots in place. Deliberately never
     * touches slots {@link #DEPOSIT_START_SLOT}-{@link #DEPOSIT_END_SLOT}
     * so items the player has placed there survive every re-render.
     */
    private void render() {
        ItemStack info = new ItemStack(Material.HOPPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§eCara Jual");
            infoMeta.setLore(List.of(
                    "§7Taruh barang di kotak besar di bawah ini.",
                    "§7Klik §a§lJUAL BARANG DI SINI §7buat jual semuanya.",
                    "§7Kalau nutup menu ini, barang yang belum",
                    "§7dijual otomatis balik ke inventory lu."
            ));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(INFO_SLOT, info);

        ItemStack sellInventory = new ItemStack(Material.CHEST);
        ItemMeta sellInvMeta = sellInventory.getItemMeta();
        if (sellInvMeta != null) {
            sellInvMeta.setDisplayName("§6Sell All (Seluruh Inventory)");
            sellInvMeta.setLore(List.of("§7Jual semua barang yang bisa", "§7dijual di inventory lu."));
            sellInventory.setItemMeta(sellInvMeta);
        }
        inventory.setItem(SELL_INVENTORY_SLOT, sellInventory);

        ItemStack sellDeposited = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sellDepMeta = sellDeposited.getItemMeta();
        if (sellDepMeta != null) {
            sellDepMeta.setDisplayName("§a§lJUAL BARANG DI SINI");
            sellDepMeta.setLore(List.of("§7Jual semua barang di kotak deposit."));
            sellDeposited.setItemMeta(sellDepMeta);
        }
        inventory.setItem(SELL_DEPOSITED_SLOT, sellDeposited);

        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot >= DEPOSIT_START_SLOT && slot <= DEPOSIT_END_SLOT) {
            // Allow completely free vanilla interaction (place, take back,
            // shift-click in) within the deposit area. Explicitly
            // un-cancel: without this, if anything else already flagged
            // the event as cancelled before this handler ran, simply
            // returning here would leave it cancelled and silently
            // block the player from placing items at all.
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);

        if (slot == SELL_DEPOSITED_SLOT) {
            sellDepositedItems();
            return;
        }

        if (slot == SELL_INVENTORY_SLOT) {
            SellConfirmGui confirmGui = new SellConfirmGui(
                    viewer, sellManager, guiManager, messagesConfig, SellConfirmGui.Mode.INVENTORY, null, this);
            guiManager.register(viewer, confirmGui);
            confirmGui.open();
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
        }
    }

    private void sellDepositedItems() {
        int totalAmount = 0;
        double totalPayout = 0;
        int skipped = 0;

        for (int slot = DEPOSIT_START_SLOT; slot <= DEPOSIT_END_SLOT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            SellManager.SellResult result = sellManager.sellSingle(viewer.getUniqueId(), stack);
            if (result.success()) {
                totalAmount += result.totalAmount();
                totalPayout += result.totalPayout();
                inventory.setItem(slot, null);
            } else {
                skipped++;
            }
        }

        if (totalAmount > 0) {
            viewer.sendMessage(messagesConfig.getWithPrefix("sell.sold",
                    "amount", String.valueOf(totalAmount),
                    "item", "barang",
                    "price", String.format("%.2f", totalPayout)));
            guiManager.playSound(viewer, "sell");
        } else {
            viewer.sendMessage(messagesConfig.getWithPrefix("sell.nothing-to-sell"));
            guiManager.playSound(viewer, "error");
        }

        if (skipped > 0) {
            viewer.sendMessage("§7(" + skipped + " item dilewati karena gak bisa dijual.)");
        }
    }

    /**
     * Returns whatever items are still sitting in the deposit area
     * back to the player's inventory when this screen closes, so
     * nothing is ever silently lost by walking away or pressing
     * escape mid-deposit. Drops overflow on the ground if their
     * inventory is full.
     *
     * @param event the triggering inventory close event
     */
    @Override
    public void handleClose(InventoryCloseEvent event) {
        for (int slot = DEPOSIT_START_SLOT; slot <= DEPOSIT_END_SLOT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }

            Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(stack);
            for (ItemStack over : leftover.values()) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), over);
            }

            inventory.setItem(slot, null);
        }
    }
}
