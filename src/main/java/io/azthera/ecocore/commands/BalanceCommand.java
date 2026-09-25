package io.azthera.ecocore.commands;

import io.azthera.ecocore.economy.EconomyEngine;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /balance} - prints the sender's (or a target player's)
 * balance read directly from EcoCore's own economy ledger, bypassing
 * Vault entirely. Useful for confirming whether EcoCore itself is
 * crediting/debiting correctly when a Vault-facing display (another
 * plugin's /balance, a scoreboard, etc.) doesn't seem to update -
 * that usually means a different plugin is registered as the active
 * Vault economy provider instead of EcoCore.
 */
public final class BalanceCommand implements CommandExecutor {

    private final EconomyEngine economyEngine;

    /**
     * Creates the balance command.
     *
     * @param economyEngine shared economy engine
     */
    public BalanceCommand(EconomyEngine economyEngine) {
        this.economyEngine = economyEngine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && sender.hasPermission("ecocore.admin")) {
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer target = org.bukkit.Bukkit.getOfflinePlayer(args[0]);
            double balance = economyEngine.getBalance(target.getUniqueId());
            sender.sendMessage(ChatColor.GRAY + "Saldo EcoCore " + args[0] + ": "
                    + ChatColor.GREEN + economyEngine.format(balance));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Gunakan: /balance <player>");
            return true;
        }

        double balance = economyEngine.getBalance(player.getUniqueId());
        sender.sendMessage(ChatColor.GRAY + "Saldo EcoCore lu: " + ChatColor.GREEN + economyEngine.format(balance));
        sender.sendMessage(ChatColor.DARK_GRAY + "(Ini dibaca langsung dari database EcoCore, bukan lewat Vault.)");
        return true;
    }
}
