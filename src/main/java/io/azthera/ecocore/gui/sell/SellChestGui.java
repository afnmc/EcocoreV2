package io.azthera.ecocore.gui.sell;

import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * A thin wrapper around an already-open container inventory (chest,
 * barrel, shulker box) that lets the player sell its full contents
 * via {@code /sell chest}. Unlike other EcoCore screens, this one
 * does not build its own inventory - it attaches to the container
 * the player already has open so the sale reflects exactly what's
 * inside it at confirm time.
 */
public final class SellChestGui extends AbstractGui {

    private final SellManager sellManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;
    private final Inventory containerInventory;

    /**
     * Creates a sell-chest wrapper around an already-open container.
     *
     * @param viewer             the viewing player
     * @param sellManager        shared sell manager
     * @param guiManager         shared GUI manager
     * @param messagesConfig     resolved messages.yml configuration
     * @param containerInventory the container inventory currently open for the player
     */
    public SellChestGui(Player viewer, SellManager sellManager, GuiManager guiManager,
                         MessagesConfig messagesConfig, Inventory containerInventory) {
        super(viewer);
        this.sellManager = sellManager;
        this.guiManager = guiManager;
        this.messagesConfig = messagesConfig;
        this.containerInventory = containerInventory;
    }

    @Override
    public void build() {
        // No-op: this screen attaches to an inventory the player already has open
        // rather than building a new one. See executeSale().
        this.inventory = containerInventory;
    }

    @Override
    public void open() {
        // Overridden: do not call viewer.openInventory() since the container
        // is already open. Simply confirm and execute the sale immediately.
        build();
        executeSale();
    }

    private void executeSale() {
        SellManager.SellResult result = sellManager.sellChest(viewer.getUniqueId(), containerInventory);

        if (result.success()) {
            viewer.sendMessage(messagesConfig.getWithPrefix("sell.sold",
                    "amount", String.valueOf(result.totalAmount()),
                    "item", "barang",
                    "price", String.format("%.2f", result.totalPayout())));
            guiManager.playSound(viewer, "sell");
        } else {
            viewer.sendMessage(messagesConfig.getWithPrefix("sell.nothing-to-sell"));
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        // This screen never stays open for interaction; sale executes immediately in open().
    }
}