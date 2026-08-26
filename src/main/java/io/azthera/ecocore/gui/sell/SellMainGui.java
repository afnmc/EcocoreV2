// FILE: src/main/java/io/azthera/ecocore/gui/sell/SellMainGui.java
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
 * The {@code /sell} screen (Revisi 15): total size matches a player
 * inventory screen convention - 54 slots split into a 27-slot deposit
 * area (rows 1-3) and a 27-slot control/preview area (rows 4-6). The
 * deposit area accepts completely free interaction (regular click,
 * shift-click, number-key swap, drag-and-drop spread, double-click
 * collect, and offhand swap all work exactly like a normal chest) -
 * every one of those Bukkit interaction paths routes through {@link
 * InventoryClickEvent}/{@code InventoryDragEvent} and both are
 * explicitly un-cancelled for deposit slots here.
 *
 * A live preview of the deposit area's total sell value is shown
 * and refreshed on every click. If the player closes the screen with
 * items still sitting in the deposit area, those items are returned
 * to their inventory in {@link #handleClose} (dropping overflow on
 * the ground if full) - nothing is ever silently lost.
 */
public final class SellMainGui extends AbstractGui {

    private static final int DEPOSIT_START_SLOT = 0;
    private static final int DEPOSIT_END_SLOT = 26;
    private static final int INFO_SLOT = 31;
    private static final int PREVIEW_SLOT = 33;
    private static final int SELL_DEPOSITED_SLOT = 40;
    private static final int SELL_INVENTORY_SLOT = 42;
    private static final int CLOSE_SLOT = 49;

    private final SellManager sellManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;

    public SellMainGui(Player viewer, SellManager sellManager, GuiManager guiManager, MessagesConfig messagesConfig) {
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
     * Revisi 15: deposit slots accept every standard interaction path
     * (click/shift-click/number-key/double-click/offhand are all
     * covered by not cancelling {@link InventoryClickEvent} there;
     * drag-and-drop spread across multiple deposit slots is covered
     * by this returning {@code true} for the whole deposit range).
     *
     * @param rawSlot the raw slot index touched by the drag
     * @return {@code true} if dragging into this slot is allowed
     */
    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        return (rawSlot >= DEPOSIT_START_SLOT && rawSlot ) || super.isFreeDragSlot(rawSlot);
    }

    /**
     * Repopulates the control/preview slots in place. Deliberately
     * never touches the deposit range so items placed there survive
     * every re-render.
     */
    private void render() {
        inventory.setItem(INFO_SLOT, namedItem(Material.HOPPER, "§eCara Jual", List.of(
                "§7Taruh barang di kotak deposit (baris atas).",
                "§7Bisa drag & drop, shift-click, atau taruh manual.",
                "§7Klik §a§lJUAL BARANG DI SINI §7buat jual semuanya.",
                "§7Kalau nutup menu ini, barang yang belum",
                "§7dijual otomatis balik ke inventory lu."
        )));
        inventory.setItem(PREVIEW_SLOT, buildPreviewIcon());
        inventory.setItem(SELL_DEPOSITED_SLOT, namedItem(Material.EMERALD_BLOCK, "§a§lJUAL BARANG DI SINI",
                List.of("§7Jual semua barang di kotak deposit.")));
        inventory.setItem(SELL_INVENTORY_SLOT, namedItem(Material.CHEST, "§6Sell All (Seluruh Inventory)",
                List.of("§7Jual semua barang yang bisa", "§7dijual di inventory lu.")));
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    /**
     * Computes and displays the live total sell value of everything
     * currently sitting in the deposit area (Revisi 15/16: real-time
     * preview, refreshed on every click so it always reflects the
     * current deposit contents including current inflation-adjusted prices).
     */
    private ItemStack buildPreviewIcon() {
        double total = 0;
        int itemCount = 0;
        if (inventory != null) {
            for (int slot = DEPOSIT_START_SLOT; slot ; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                double preview = sellManager.previewSellValue(stack);
                if (preview > 0) {
                    total += preview;
                    itemCount += stack.getAmount();
                }
            }
        }
        return namedItem(Material.GOLD_NUGGET, "§6Total Preview Harga", List.of(
                "§7Jumlah item: §f" + itemCount,
                "§7Estimasi total: §a" + String.format("%.2f", total)
        ));
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= DEPOSIT_START_SLOT && slot ) {
            // Revisi 15: fully free vanilla interaction in the deposit area -
            // explicitly un-cancel in case an earlier handler already flagged
            // this event, then refresh the live price preview.
            event.setCancelled(false);
            Bukkit.getScheduler().runTask(io.azthera.ecocore.EcoCorePlugin.getInstance(), () -> {
                if (inventory != null) {
                    inventory.setItem(PREVIEW_SLOT, buildPreviewIcon());
                }
            });
            return;
        }
        event.setCancelled(true);
        if (slot == SELL_DEPOSITED_SLOT) {
            sellDepositedItems();
            return;
        }
        if (slot == SELL_INVENTORY_SLOT) {
            SellConfirmGui confirmGui = new SellConfirmGui(
                    getViewer(), sellManager, guiManager, messagesConfig, SellConfirmGui.Mode.INVENTORY, null, this);
            guiManager.register(getViewer(), confirmGui);
            confirmGui.open();
            return;
        }
        if (slot == CLOSE_SLOT) {
            getViewer().closeInventory();
        }
    }

    private void sellDepositedItems() {
        int totalAmount = 0;
        double totalPayout = 0;
        int skipped = 0;
        for (int slot = DEPOSIT_START_SLOT; slot ; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            SellManager.SellResult result = sellManager.sellSingle(getViewer().getUniqueId(), stack);
            if (result.success()) {
                totalAmount += result.totalAmount();
                totalPayout += result.totalPayout();
                inventory.setItem(slot, null);
            } else {
                skipped++;
            }
        }
        if (totalAmount > 0) {
            getViewer().sendMessage(messagesConfig.getWithPrefix("sell.sold",
                    "amount", String.valueOf(totalAmount),
                    "item", "barang",
                    "price", String.format("%.2f", totalPayout)));
            guiManager.playSound(getViewer(), "sell");
        } else {
            getViewer().sendMessage(messagesConfig.getWithPrefix("sell.nothing-to-sell"));
            guiManager.playSound(getViewer(), "error");
        }
        if (skipped > 0) {
            getViewer().sendMessage("§7(" + skipped + " item dilewati karena gak bisa dijual.)");
        }
        inventory.setItem(PREVIEW_SLOT, buildPreviewIcon());
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        for (int slot = DEPOSIT_START_SLOT; slot ; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftover = getViewer().getInventory().addItem(stack);
            for (ItemStack over : leftover.values()) {
                getViewer().getWorld().dropItemNaturally(getViewer().getLocation(), over);
            }
            inventory.setItem(slot, null);
        }
    }
}