package io.azthera.ecocore.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Central owner of every EcoCore configuration file. Loads, caches, and
 * exposes each config's parsed object, and supports a full reload
 * triggered by {@code /ecocore reload}.
 */
public final class ConfigManager {

    private final JavaPlugin plugin;

    private FileConfiguration mainConfig;
    private ShopConfig shopConfig;
    private ShopItemsConfig shopItemsConfig;
    private PricesConfig pricesConfig;
    private InflationConfig inflationConfig;
    private JobsConfig jobsConfig;
    private MinionsConfig minionsConfig;
    private DiscordConfig discordConfig;
    private BlacklistConfig blacklistConfig;
    private DatabaseConfig databaseConfig;
    private MessagesConfig messagesConfig;
    private GuiConfig guiConfig;
    private AiConfig aiConfig;
    private NightMarketConfig nightMarketConfig;

    /**
     * Creates a config manager bound to the given plugin instance.
     * Call {@link #loadAll()} once during {@code onEnable()} before
     * reading any config values.
     *
     * @param plugin the owning plugin instance
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) every EcoCore configuration file from disk,
     * copying default resources on first run.
     */
    public void loadAll() {
        mainConfig = loadYaml("config.yml");
        shopConfig = new ShopConfig(loadYaml("shop.yml"));
        shopItemsConfig = new ShopItemsConfig(loadYaml("shop-items.yml"));
        pricesConfig = new PricesConfig(loadYaml("prices.yml"));
        inflationConfig = new InflationConfig(loadYaml("inflation.yml"));
        jobsConfig = new JobsConfig(loadYaml("jobs.yml"));
        minionsConfig = new MinionsConfig(plugin.getLogger(), loadYaml("minions.yml"));
        discordConfig = new DiscordConfig(loadYaml("discord.yml"));
        blacklistConfig = new BlacklistConfig(loadYaml("blacklist.yml"));
        databaseConfig = new DatabaseConfig(loadYaml("database.yml"));
        messagesConfig = new MessagesConfig(loadYaml("messages.yml"));
        guiConfig = new GuiConfig(loadYaml("gui.yml"));
        aiConfig = new AiConfig(loadYaml("ai.yml"));
        nightMarketConfig = new NightMarketConfig(loadYaml("night-market.yml"));

        plugin.getLogger().info("[EcoCore] All configuration files loaded");
    }

    /**
     * Reloads every configuration file from disk. Equivalent to
     * calling {@link #loadAll()} again.
     */
    public void reloadAll() {
        loadAll();
    }

    private FileConfiguration loadYaml(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        try (var stream = plugin.getResource(fileName)) {
            if (stream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
                config.save(file);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "[EcoCore] Failed to merge defaults for " + fileName, exception);
        }

        return config;
    }

    // ---- general (config.yml) getters ----

    public boolean isDebug() {
        return mainConfig.getBoolean("debug", false);
    }

    public boolean isClaimProtectionEnabled() {
        return mainConfig.getBoolean("claim-protection-enabled", true);
    }

    public String getLocale() {
        return mainConfig.getString("locale", "id_ID");
    }

    public String getCurrencySymbol() {
        return mainConfig.getString("general.currency-symbol", "$");
    }

    public String getCurrencyNameSingular() {
        return mainConfig.getString("general.currency-name-singular", "Dollar");
    }

    public String getCurrencyNamePlural() {
        return mainConfig.getString("general.currency-name-plural", "Dollars");
    }

    public double getStartingBalance() {
        return mainConfig.getDouble("general.starting-balance", 500.0);
    }

    public String getDecimalFormat() {
        return mainConfig.getString("general.decimal-format", "#,##0.00");
    }

    public int getAutosaveIntervalMinutes() {
        return mainConfig.getInt("general.autosave-interval-minutes", 5);
    }

    public boolean isModuleEnabled(String moduleKey) {
        return mainConfig.getBoolean("modules." + moduleKey, true);
    }

    public int getAiCalculationIntervalSeconds() {
        return mainConfig.getInt("intervals.ai-calculation-seconds", 300);
    }

    public int getRestockCheckIntervalSeconds() {
        return mainConfig.getInt("intervals.restock-check-seconds", 60);
    }

    public int getInflationCalculationIntervalSeconds() {
        return mainConfig.getInt("intervals.inflation-calculation-seconds", 900);
    }

    public int getMinionTickIntervalSeconds() {
        return mainConfig.getInt("intervals.minion-tick-seconds", 5);
    }

    public int getMarketSnapshotIntervalSeconds() {
        return mainConfig.getInt("intervals.market-snapshot-seconds", 3600);
    }

    public boolean isBroadcastPriceChanges() {
        return mainConfig.getBoolean("notifications.broadcast-price-changes", true);
    }

    public boolean isBroadcastInflationChanges() {
        return mainConfig.getBoolean("notifications.broadcast-inflation-changes", true);
    }

    public boolean isBroadcastRestock() {
        return mainConfig.getBoolean("notifications.broadcast-restock", false);
    }

    public boolean isUseActionbar() {
        return mainConfig.getBoolean("notifications.use-actionbar", true);
    }

    public boolean isUseBossbar() {
        return mainConfig.getBoolean("notifications.use-bossbar", false);
    }

    public boolean isUseTitle() {
        return mainConfig.getBoolean("notifications.use-title", false);
    }

    public boolean isUseChat() {
        return mainConfig.getBoolean("notifications.use-chat", true);
    }

    public boolean isUseDiscordNotifications() {
        return mainConfig.getBoolean("notifications.use-discord", true);
    }

    public boolean isUseConsole() {
        return mainConfig.getBoolean("notifications.use-console", true);
    }

    public String getDataFolderName() {
        return mainConfig.getString("storage.data-folder", "data");
    }

    public String getBackupFolderName() {
        return mainConfig.getString("storage.backup-folder", "backups");
    }

    // ---- parsed config accessors ----

    public ShopConfig getShopConfig() {
        return shopConfig;
    }

    public ShopItemsConfig getShopItemsConfig() {
        return shopItemsConfig;
    }

    public PricesConfig getPricesConfig() {
        return pricesConfig;
    }

    public InflationConfig getInflationConfig() {
        return inflationConfig;
    }

    public JobsConfig getJobsConfig() {
        return jobsConfig;
    }

    public MinionsConfig getMinionsConfig() {
        return minionsConfig;
    }

    public DiscordConfig getDiscordConfig() {
        return discordConfig;
    }

    public BlacklistConfig getBlacklistConfig() {
        return blacklistConfig;
    }

    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
    }

    public AiConfig getAiConfig() {
        return aiConfig;
    }

    public NightMarketConfig getNightMarketConfig() {
        return nightMarketConfig;
    }
    }