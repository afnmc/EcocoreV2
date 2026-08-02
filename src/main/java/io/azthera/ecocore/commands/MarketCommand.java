package io.azthera.ecocore.commands;

import io.azthera.ecocore.economy.CurrencyFormatter;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /market} - prints a quick chat summary of overall market
 * state: total items, sell-out count, and the top 5 most expensive items.
 */
public final class MarketCommand implements CommandExecutor {

    private final ShopManager shopManager;
    private final CurrencyFormatter currencyFormatter;

    /**
     * Creates the market command.
     *
     * @param shopManager       shared shop manager
     * @param currencyFormatter shared currency formatter
     */
    public MarketCommand(ShopManager shopManager, CurrencyFormatter currencyFormatter) {
        this.shopManager = shopManager;
        this.currencyFormatter = currencyFormatter;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<ShopItemRecord> all = shopManager.getAllItems();
        long soldOut = all.stream().filter(ShopItemRecord::isSoldOut).count();

        sender.sendMessage(ChatColor.GOLD + "===== EcoCore Market =====");
        sender.sendMessage(ChatColor.GRAY + "Total barang: " + ChatColor.WHITE + all.size());
        sender.sendMessage(ChatColor.GRAY + "Sedang sell out: " + ChatColor.WHITE + soldOut);

        List<ShopItemRecord> topExpensive = all.stream()
                .sorted(Comparator.comparingDouble(ShopItemRecord::getCurrentPrice).reversed())
                .limit(5)
                .toList();

        sender.sendMessage(ChatColor.GRAY + "Barang termahal:");
        for (ShopItemRecord item : topExpensive) {
            sender.sendMessage(ChatColor.YELLOW + "  " + item.getId() + ChatColor.GRAY + " - "
                    + ChatColor.GREEN + currencyFormatter.format(item.getCurrentPrice()));
        }

        return true;
    }
}