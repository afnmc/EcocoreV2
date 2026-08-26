// FILE: src/main/java/io/azthera/ecocore/commands/InflationCommand.java
package io.azthera.ecocore.commands;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.database.dao.PlayerDao;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.inflation.PriceDisplayHelper;
import io.azthera.ecocore.model.InflationRecord;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.List;

/**
 * {@code /inflation} - prints the server's current macro-economic
 * state (Revisi 16): status, inflation/deflation percent, the effect
 * on buy/sell prices, total money supply, and average balance. Every
 * line goes through {@code messages.yml} where a translation exists;
 * the dynamic economy summary block itself is built via {@link
 * PriceDisplayHelper#buildEconomySummaryLines} since its content
 * (which direction, which percent) is inherently data-driven rather
 * than a fixed template.
 */
public final class InflationCommand implements CommandExecutor {

    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;
    private final PlayerDao playerDao;
    private final MessagesConfig messagesConfig;

    public InflationCommand(InflationEngine inflationEngine, InflationConfig inflationConfig,
                             PlayerDao playerDao, MessagesConfig messagesConfig) {
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
        this.playerDao = playerDao;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        InflationRecord record = inflationEngine.getLatestRecord();
        if (record == null) {
            sender.sendMessage(messagesConfig.getWithPrefix("inflation.not-available"));
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "===== Status Ekonomi Server =====");
        sender.sendMessage(ChatColor.GRAY + "Status: " + ChatColor.YELLOW + record.state());

        for (String line : PriceDisplayHelper.buildEconomySummaryLines(record)) {
            sender.sendMessage(line);
        }

        InflationConfig.StateEffect effect = inflationConfig.getStateEffect(record.state());
        sender.sendMessage(ChatColor.GRAY + "Efek harga beli: " + ChatColor.WHITE
                + String.format("x%.2f", effect.priceMultiplier()));
        sender.sendMessage(ChatColor.GRAY + "Efek bonus job: " + ChatColor.WHITE
                + String.format("x%.2f", effect.jobBonusMultiplier()));
        sender.sendMessage(ChatColor.GRAY + "Total Uang Beredar: " + ChatColor.WHITE
                + String.format("%.2f", record.totalMoney()));

        double liveAverageBalance;
        try {
            liveAverageBalance = playerDao.averageBalance();
        } catch (SQLException exception) {
            liveAverageBalance = record.averageBalance();
        }
        sender.sendMessage(ChatColor.GRAY + "Rata-rata Saldo: " + ChatColor.WHITE
                + String.format("%.2f", liveAverageBalance));

        return true;
    }
}