package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.shop.ShopHistoryGui;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /history} - opens the player's transaction history GUI.
 */
public final class HistoryCommand implements CommandExecutor {

    private final ShopManager shopManager;
    private final GuiManager guiManager;

    /**
     * Creates the history command.
     *
     * @param shopManager shared shop manager
     * @param guiManager  shared GUI manager
     */
    public HistoryCommand(ShopManager shopManager, GuiManager guiManager) {
        this.shopManager = shopManager;
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCommand ini cuma bisa dipakai player.");
            return true;
        }

        ShopHistoryGui gui = new ShopHistoryGui(player, shopManager, guiManager);
        guiManager.register(player, gui);
        gui.open();
        return true;
    }
}