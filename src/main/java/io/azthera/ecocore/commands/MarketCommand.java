package io.azthera.ecocore.commands;

import io.azthera.ecocore.economy.CurrencyFormatter;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;

/**
 * {@code /market} - prints a quick chat summary of overall market
 * state, including the top 8 most expensive/rare items. When run by
 * a player, each item line is clickable and opens the buy-preview
 * screen for it directly (via {@code /ecoitem}) - chat can't open a
 * GUI on its own, so this is what makes rare market items purchasable
 * straight from the summary instead of needing to dig through {@code /shop}.
 */
public final class MarketCommand implements CommandExecutor {

    private final ShopManager shopManager;
    private final CurrencyFormatter currencyFormatter;

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
                .limit(8)
                .toList();

        sender.sendMessage(ChatColor.GRAY + "Barang termahal / paling langka:");

        boolean isPlayer = sender instanceof Player;
        for (ShopItemRecord item : topExpensive) {
            String priceText = currencyFormatter.format(item.getCurrentPrice());

            if (isPlayer) {
                String label2 = ChatColor.YELLOW + "  " + item.getId() + ChatColor.GRAY + " - "
                        + ChatColor.GREEN + priceText
                        + (item.isSoldOut() ? ChatColor.RED + " (SELL OUT)" : "");

                ComponentBuilder builder = new ComponentBuilder(label2)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ecoitem " + item.getId()))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                new Text(ChatColor.YELLOW + "Klik buat buka & beli " + item.getId())));
                sender.spigot().sendMessage(builder.create());
            } else {
                sender.sendMessage(ChatColor.YELLOW + "  " + item.getId() + ChatColor.GRAY + " - "
                        + ChatColor.GREEN + priceText);
            }
        }

        if (isPlayer && !topExpensive.isEmpty()) {
            sender.sendMessage(ChatColor.DARK_GRAY + "Klik salah satu barang di atas buat langsung buka & beli.");
        }

        return true;
    }
                               }
