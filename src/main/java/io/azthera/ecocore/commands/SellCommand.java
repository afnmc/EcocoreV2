package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.sell.SellChestGui;
import io.azthera.ecocore.gui.sell.SellMainGui;
import io.azthera.ecocore.sell.AutoSellManager;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/**
 * {@code /sell} - opens the sell GUI, or {@code /sell chest} to
 * immediately sell the contents of a currently-open container.
 */
public final class SellCommand implements CommandExecutor {

    private final SellManager sellManager;
    private final AutoSellManager autoSellManager;
    private final ConfigManager configManager;
    private final GuiManager guiManager;

    /**
     * Creates the sell command.
     *
     * @param sellManager     shared sell manager
     * @param autoSellManager shared auto-sell manager
     * @param configManager   resolved main config manager
     * @param guiManager      shared GUI manager
     */
    public SellCommand(SellManager sellManager, AutoSellManager autoSellManager,
                        ConfigManager configManager, GuiManager guiManager) {
        this.sellManager = sellManager;
        this.autoSellManager = autoSellManager;
        this.configManager = configManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini cuma bisa dipakai player.");
            return true;
        }

        if (!configManager.isModuleEnabled("sell-enabled")) {
            player.sendMessage("§cFitur sell sedang dinonaktifkan.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("chest")) {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof InventoryHolder)
                    || player.getOpenInventory().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
                player.sendMessage("§cBuka chest yang mau dijual dulu, baru ketik command ini.");
                return true;
            }

            SellChestGui chestGui = new SellChestGui(player, sellManager, guiManager,
                    configManager.getMessagesConfig(), player.getOpenInventory().getTopInventory());
            chestGui.open();
            return true;
        }

        SellMainGui gui = new SellMainGui(player, sellManager, autoSellManager, guiManager, configManager.getMessagesConfig());
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}