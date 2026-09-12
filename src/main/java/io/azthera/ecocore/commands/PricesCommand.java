package io.azthera.ecocore.commands;

import io.azthera.ecocore.economy.CurrencyFormatter;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * {@code /prices <item>} - prints an item's current live price and stock.
 */
public final class PricesCommand implements CommandExecutor {

    private final ShopManager shopManager;
    private final CurrencyFormatter currencyFormatter;

    /**
     * Creates the prices command.
     *
     * @param shopManager       shared shop manager
     * @param currencyFormatter shared currency formatter
     */
    public PricesCommand(ShopManager shopManager, CurrencyFormatter currencyFormatter) {
        this.shopManager = shopManager;
        this.currencyFormatter = currencyFormatter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "Gunakan: /prices <item id>");
            return true;
        }

        ShopItemRecord item = shopManager.getItem(args[0]);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "Barang '" + args[0] + "' tidak ditemukan.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + item.getId());
        sender.sendMessage(ChatColor.GRAY + "Harga: " + ChatColor.GREEN + currencyFormatter.format(item.getCurrentPrice()));
        sender.sendMessage(ChatColor.GRAY + "Stock: " + ChatColor.WHITE + item.getStock() + "/" + item.getMaxStock());
        sender.sendMessage(ChatColor.GRAY + "Status: "
                + (item.isSoldOut() ? ChatColor.RED + "Sell Out" : ChatColor.GREEN + "Tersedia"));
        return true;
    }
}