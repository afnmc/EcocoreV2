package io.azthera.ecocore.commands;

import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.model.InflationRecord;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * {@code /inflation} - prints the server's current macro-economic state.
 */
public final class InflationCommand implements CommandExecutor {

    private final InflationEngine inflationEngine;

    /**
     * Creates the inflation command.
     *
     * @param inflationEngine shared inflation engine
     */
    public InflationCommand(InflationEngine inflationEngine) {
        this.inflationEngine = inflationEngine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        InflationRecord record = inflationEngine.getLatestRecord();
        if (record == null) {
            sender.sendMessage(ChatColor.RED + "Data inflasi belum tersedia.");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "===== Status Ekonomi =====");
        sender.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.YELLOW + record.state());
        sender.sendMessage(ChatColor.GRAY + "Inflasi: " + ChatColor.WHITE
                + String.format("%.2f%%", record.inflationPercent()));
        sender.sendMessage(ChatColor.GRAY + "Deflasi: " + ChatColor.WHITE
                + String.format("%.2f%%", record.deflationPercent()));
        sender.sendMessage(ChatColor.GRAY + "Total Uang Beredar: " + ChatColor.WHITE
                + String.format("%.2f", record.totalMoney()));
        sender.sendMessage(ChatColor.GRAY + "Rata-rata Saldo: " + ChatColor.WHITE
                + String.format("%.2f", record.averageBalance()));
        return true;
    }
}