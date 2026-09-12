package io.azthera.ecocore.gui.sell;

import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.model.InflationRecord;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The {@code /sell} screen (Revisi 15): total size matches a player
 * inventory screen convention - 54 slots split into a 27-slot deposit
 * area (rows 1-3, slots 0-26) and a control/preview area in rows 4-6.
 * The deposit area accepts completely free interaction (regular
 * click, shift-click, number-key swap, drag-and-drop spread,
 * double-click collect, and offhand swap all work exactly like a
 * normal chest).
 *
 * <p>A live preview of the deposit area's total sell value is shown
 * and refreshed on every click, along with the current economic
 * state's effect on sell prices (Revisi 16).
 *
 * <p>If the player closes the screen with items still sitting in the
 * deposit area, those items are returned to their inventory in
 * {@link #handleClose}, dropping any overflow on the ground if their
 * inventory is full - nothing is ever lost silently.
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

    public SellMainGui(Player viewer, SellManager sellManager,
                        GuiManager guiManager, MessagesConfig messagesConfig) {
        super(viewer);
        this.sellManager = sellManager;
        this.guiManager = guiManager;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, messagesConfig.get("ui.sell.title"));
        render();
    }

    @Override
    public boolean isFreeDragSlot(int rawSlot) {
        return (rawSlot >= DEPOSIT_START_SLOT && rawSlot <= DEPOSIT_END_SLOT) || super.isFreeDragSlot(rawSlot);
    }

    @Override
    public void handleDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        // Refresh the live price preview once Bukkit has applied the
        // drag - matches the same 1-tick-later pattern used for clicks.
        org.bukkit.Bukkit.getScheduler().runTask(io.azthera.ecocore.EcoCorePlugin.getInstance(), () -> {
            if (inventory != null) {
                inventory.setItem(PREVIEW_SLOT, buildPreviewIcon());
            }
        });
    }

    private void render() {
        ItemStack info = new ItemStack(Material.HOPPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(messagesConfig.get("ui.sell.info-title"));
            infoMeta.setLore(List.of(
                    messagesConfig.get("ui.sell.info-1"),
                    messagesConfig.get("ui.sell.info-2"),
                    messagesConfig.get("ui.sell.info-3"),
                    messagesConfig.get("ui.sell.info-4")
            ));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(INFO_SLOT, info);

        inventory.setItem(PREVIEW_SLOT, buildPreviewIcon());

        ItemStack sellDeposited = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta sellDepMeta = sellDeposited.getItemMeta();
        if (sellDepMeta != null) {
            sellDepMeta.setDisplayName(messagesConfig.get("ui.sell.deposit-title"));
            sellDepMeta.setLore(List.of(messagesConfig.get("ui.sell.deposit-lore")));
            sellDeposited.setItemMeta(sellDepMeta);
        }
        inventory.setItem(SELL_DEPOSITED_SLOT, sellDeposited);

        ItemStack sellInventory = new ItemStack(Material.CHEST);
        ItemMeta sellInvMeta = sellInventory.getItemMeta();
        if (sellInvMeta != null) {
            sellInvMeta.setDisplayName(messagesConfig.get("ui.sell.inventory-title"));
            sellInvMeta.setLore(List.of(messagesConfig.get("ui.sell.inventory-lore-1"), messagesConfig.get("ui.sell.inventory-lore-2")));
            sellInventory.setItemMeta(sellInvMeta);
        }
        inventory.setItem(SELL_INVENTORY_SLOT, sellInventory);

        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    /**
     * Computes and displays the live total sell value of everything
     * currently sitting in the deposit area (Revisi 15), plus a
     * context line about the current economic state's effect on sell
     * prices (Revisi 16).
     */
    private ItemStack buildPreviewIcon() {
        double total = 0;
        int itemCount = 0;
        if (inventory != null) {
            for (int slot = DEPOSIT_START_SLOT; slot <= DEPOSIT_END_SLOT; slot++) {
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
        List<String> lore = new ArrayList<>();
        lore.add("§7Jumlah item: §f" + itemCount);
        lore.add("§7Estimasi total: §a" + String.format("%.2f", total));
        InflationRecord latestInflation = io.azthera.ecocore.EcoCorePlugin.getInstance().getInflationEngine().getLatestRecord();
        if (latestInflation != null) {
            boolean isInflation = latestInflation.inflationPercent() >= latestInflation.deflationPercent();
            double percent = isInflation ? latestInflation.inflationPercent() : latestInflation.deflationPercent();
            if (percent >= 0.01) {
                lore.add(isInflation
                        ? "§7Harga jual turun §c" + String.format("%.1f", percent) + "%§7 karena inflasi"
                        : "§7Harga jual naik §a" + String.format("%.1f", percent) + "%§7 karena deflasi");
            }
        }
        ItemStack icon = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messagesConfig.get("ui.sell.preview-title"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // The player's own inventory is intentionally left fully interactive.
        // This is important for shift-clicking items into the deposit area and
        // for normal inventory management while /sell is open.
        if (slot >= inventory.getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack source = event.getCurrentItem();
                if (source != null && !source.getType().isAir()) {
                    ItemStack moving = source.clone();
                    ItemStack remaining = depositStack(moving);
                    if (remaining.getAmount() <= 0) {
                        event.setCurrentItem(null);
                    } else {
                        event.setCurrentItem(remaining);
                    }
                    Bukkit.getScheduler().runTask(io.azthera.ecocore.EcoCorePlugin.getInstance(), () -> {
                        if (inventory != null) inventory.setItem(PREVIEW_SLOT, buildPreviewIcon());
                    });
                }
            } else {
                event.setCancelled(false);
                Bukkit.getScheduler().runTask(io.azthera.ecocore.EcoCorePlugin.getInstance(), () -> {
                    if (inventory != null) inventory.setItem(PREVIEW_SLOT, buildPreviewIcon());
                });
            }
            return;
        }

        if (slot >= DEPOSIT_START_SLOT && slot <= DEPOSIT_END_SLOT) {
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
                    viewer, sellManager, guiManager, messagesConfig, SellConfirmGui.Mode.INVENTORY, null, this);
            guiManager.register(viewer, confirmGui);
            confirmGui.open();
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
        }
    }

    private ItemStack depositStack(ItemStack source) {
        ItemStack remaining = source.clone();
        for (int slot = DEPOSIT_START_SLOT; slot <= DEPOSIT_END_SLOT && remaining.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType().isAir()) continue;
            if (!existing.isSimilar(remaining)) continue;
            int room = existing.getMaxStackSize() - existing.getAmount();
            if (room <= 0) continue;
            int moved = Math.min(room, remaining.getAmount());
            existing.setAmount(existing.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
            inventory.setItem(slot, existing);
        }
        for (int slot = DEPOSIT_START_SLOT; slot <= DEPOSIT_END_SLOT && remaining.getAmount() > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) continue;
            int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
            ItemStack placed = remaining.clone();
            placed.setAmount(moved);
            inventory.setItem(slot, placed);
            remaining.setAmount(remaining.getAmount() - moved);
        }
        return remaining;
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
            viewer.sendMessage(messagesConfig.get("ui.sell.skipped", "count", String.valueOf(skipped)));
        }

        inventory.setItem(PREVIEW_SLOT, buildPreviewIcon());
    }

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
