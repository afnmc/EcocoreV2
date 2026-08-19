package io.azthera.ecocore.commands;

import io.azthera.ecocore.ai.AiEconomyEngine;
import io.azthera.ecocore.ai.AiLearningModel;
import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.InflationRecord;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.RestockScheduler;
import io.azthera.ecocore.shop.ShopManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;

/**
 * {@code /ecocore <subcommand>} - the admin command hub covering
 * reload, debug, restock, inflation, ai, save, backup, market, and graph.
 */
public final class EcoCoreCommand implements CommandExecutor {

    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final ShopManager shopManager;
    private final RestockScheduler restockScheduler;
    private final InflationEngine inflationEngine;
    private final AiEconomyEngine aiEconomyEngine;
    private final AiLearningModel aiLearningModel;
    private final EconomyEngine economyEngine;
    private final MinionManager minionManager;
    private final File dataFolder;

    /**
     * Creates the admin command hub.
     *
     * @param configManager    resolved main config manager
     * @param databaseManager  shared database manager
     * @param shopManager      shared shop manager
     * @param restockScheduler shared restock scheduler
     * @param inflationEngine  shared inflation engine
     * @param aiEconomyEngine  shared AI economy engine
     * @param aiLearningModel  shared AI learning model
     * @param economyEngine    shared economy engine
     * @param minionManager    shared minion manager
     * @param dataFolder       the plugin's data folder, used for backups
     */
    public EcoCoreCommand(ConfigManager configManager, DatabaseManager databaseManager, ShopManager shopManager,
                           RestockScheduler restockScheduler, InflationEngine inflationEngine,
                           AiEconomyEngine aiEconomyEngine, AiLearningModel aiLearningModel,
                           EconomyEngine economyEngine, MinionManager minionManager, File dataFolder) {
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.shopManager = shopManager;
        this.restockScheduler = restockScheduler;
        this.inflationEngine = inflationEngine;
        this.aiEconomyEngine = aiEconomyEngine;
        this.aiLearningModel = aiLearningModel;
        this.economyEngine = economyEngine;
        this.minionManager = minionManager;
        this.dataFolder = dataFolder;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecocore.admin")) {
            sender.sendMessage(ChatColor.RED + "Lu gak punya izin buat command ini.");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender);
            case "restock" -> handleRestock(sender);
            case "inflation" -> handleInflation(sender);
            case "ai" -> handleAi(sender);
            case "save" -> handleSave(sender);
            case "backup" -> handleBackup(sender);
            case "market" -> handleMarket(sender, args);
            case "graph" -> handleGraph(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/ecocore <reload|debug|restock|inflation|ai|save|backup|market|graph>");
    }

    private void handleReload(CommandSender sender) {
        configManager.reloadAll();
        shopManager.loadCatalog();
        sender.sendMessage(configManager.getMessagesConfig().getWithPrefix("general.reload-success"));
    }

    private void handleDebug(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== EcoCore Debug =====");
        sender.sendMessage(ChatColor.GRAY + "Database ready: " + ChatColor.WHITE + databaseManager.isReady());
        sender.sendMessage(ChatColor.GRAY + "Total shop items: " + ChatColor.WHITE + shopManager.getAllItems().size());
        sender.sendMessage(ChatColor.GRAY + "Economic state: " + ChatColor.WHITE + inflationEngine.getCurrentState());
        sender.sendMessage(ChatColor.GRAY + "Debug mode: " + ChatColor.WHITE + configManager.isDebug());
    }

    private void handleRestock(CommandSender sender) {
        var outcomes = restockScheduler.runRestockPass(false, false);
        sender.sendMessage(ChatColor.GREEN + "Restock manual selesai: " + outcomes.size() + " barang di-restock.");
    }

    private void handleInflation(CommandSender sender) {
        InflationRecord record = inflationEngine.getLatestRecord();
        if (record == null) {
            sender.sendMessage(ChatColor.RED + "Data inflasi belum tersedia.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Status ekonomi: " + ChatColor.YELLOW + record.state());
        sender.sendMessage(ChatColor.GRAY + "Inflasi: " + String.format("%.2f%%", record.inflationPercent()));
        sender.sendMessage(ChatColor.GRAY + "Deflasi: " + String.format("%.2f%%", record.deflationPercent()));
    }

    private void handleAi(CommandSender sender) {
        aiLearningModel.invalidateCache();
        sender.sendMessage(ChatColor.GREEN + "Menjalankan siklus AI secara manual...");
        aiEconomyEngine.runCycle();
        sender.sendMessage(ChatColor.GREEN + "Siklus AI selesai.");
    }

    private void handleSave(CommandSender sender) {
        economyEngine.saveAll();
        minionManager.saveAll();
        sender.sendMessage(ChatColor.GREEN + "Semua data berhasil disimpan.");
    }

    private void handleBackup(CommandSender sender) {
        try {
            File dbFile = new File(dataFolder, configManager.getDatabaseConfig().getFileName());
            File backupFolder = new File(dataFolder, configManager.getBackupFolderName());
            if (!backupFolder.exists()) {
                backupFolder.mkdirs();
            }

            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now());
            File backupFile = new File(backupFolder, "ecocore-" + timestamp + ".db");

            Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            sender.sendMessage(ChatColor.GREEN + "Backup berhasil dibuat: " + backupFile.getName());
        } catch (IOException exception) {
            sender.sendMessage(ChatColor.RED + "Backup gagal: " + exception.getMessage());
        }
    }

    private void handleMarket(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "/ecocore market <item> <set-stock|set-price> <value>");
            return;
        }

        String itemId = args[1];
        ShopItemRecord item = shopManager.getItem(itemId);
        if (item == null) {
            sender.sendMessage(ChatColor.RED + "Barang '" + itemId + "' tidak ditemukan.");
            return;
        }

        sender.sendMessage(ChatColor.GRAY + itemId + ": harga=" + item.getCurrentPrice()
                + " stock=" + item.getStock() + "/" + item.getMaxStock());
    }

    private void handleGraph(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/ecocore graph <item>");
            return;
        }
        sender.sendMessage(ChatColor.GRAY + "Gunakan Discord /item " + args[1] + " untuk melihat grafik harga.");
    }
}