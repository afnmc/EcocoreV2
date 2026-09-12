package io.azthera.ecocore;

import io.azthera.ecocore.ai.AiEconomyEngine;
import io.azthera.ecocore.ai.AiLearningModel;
import io.azthera.ecocore.ai.RestockDecisionEngine;
import io.azthera.ecocore.ai.TrendAnalyzer;
import io.azthera.ecocore.api.EcoCoreAPI;
import io.azthera.ecocore.api.EcoCoreAPIImpl;
import io.azthera.ecocore.commands.BalanceCommand;
import io.azthera.ecocore.commands.EcoCoreCommand;
import io.azthera.ecocore.commands.HistoryCommand;
import io.azthera.ecocore.commands.InflationCommand;
import io.azthera.ecocore.commands.ItemViewCommand;
import io.azthera.ecocore.commands.JobCommand;
import io.azthera.ecocore.commands.JobsCommand;
import io.azthera.ecocore.commands.MarketCommand;
import io.azthera.ecocore.commands.PricesCommand;
import io.azthera.ecocore.commands.SellCommand;
import io.azthera.ecocore.commands.ShopCommand;
import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.database.DatabaseManager;
import io.azthera.ecocore.database.dao.AiLearningDao;
import io.azthera.ecocore.database.dao.BuyHistoryDao;
import io.azthera.ecocore.database.dao.DiscordLogDao;
import io.azthera.ecocore.database.dao.InflationHistoryDao;
import io.azthera.ecocore.database.dao.JobMissionDao;
import io.azthera.ecocore.database.dao.JobsDao;
import io.azthera.ecocore.database.dao.MarketHistoryDao;
import io.azthera.ecocore.database.dao.MoneyDao;
import io.azthera.ecocore.database.dao.NightMarketDao;
import io.azthera.ecocore.database.dao.PlayerDao;
import io.azthera.ecocore.database.dao.SellHistoryDao;
import io.azthera.ecocore.database.dao.ShopItemDao;
import io.azthera.ecocore.database.dao.StockEventDao;
import io.azthera.ecocore.discord.DiscordBotManager;
import io.azthera.ecocore.discord.DiscordChannelRouter;
import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.discord.DiscordWebhookSender;
import io.azthera.ecocore.discord.commands.HistorySlashCommand;
import io.azthera.ecocore.discord.commands.InflationSlashCommand;
import io.azthera.ecocore.discord.commands.ItemSlashCommand;
import io.azthera.ecocore.discord.commands.JobsSlashCommand;
import io.azthera.ecocore.discord.commands.MarketSlashCommand;
import io.azthera.ecocore.discord.commands.PlayerSlashCommand;
import io.azthera.ecocore.discord.commands.RestockSlashCommand;
import io.azthera.ecocore.discord.commands.TopMarketSlashCommand;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.VaultEconomyHook;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.graph.PriceGraphRenderer;
import io.azthera.ecocore.hook.ItemIdentityResolver;
import io.azthera.ecocore.hook.ItemsAdderHook;
import io.azthera.ecocore.hook.MMOItemsHook;
import io.azthera.ecocore.hook.OraxenHook;
import io.azthera.ecocore.hook.SlimefunHook;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobLeaderboardManager;
import io.azthera.ecocore.jobs.JobMissionManager;
import io.azthera.ecocore.jobs.JobPrestigeManager;
import io.azthera.ecocore.jobs.JobProgressTracker;
import io.azthera.ecocore.jobs.JobSkillTreeManager;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.input.PrivateChatInputManager;
import io.azthera.ecocore.listener.BlockBreakListener;
import io.azthera.ecocore.listener.CraftListener;
import io.azthera.ecocore.listener.EntityDeathListener;
import io.azthera.ecocore.listener.FishListener;
import io.azthera.ecocore.listener.InventoryClickListener;
import io.azthera.ecocore.listener.JobActionListener;
import io.azthera.ecocore.listener.PlayerJoinListener;
import io.azthera.ecocore.listener.PlayerQuitListener;
import io.azthera.ecocore.manager.NotificationManager;
import io.azthera.ecocore.manager.PlayerDataManager;
import io.azthera.ecocore.market.NightMarketManager;
import io.azthera.ecocore.placeholder.EcoCorePlaceholderExpansion;

import io.azthera.ecocore.scheduler.AiCalculationScheduler;
import io.azthera.ecocore.scheduler.AutoSaveScheduler;
import io.azthera.ecocore.scheduler.InflationTaskScheduler;
import io.azthera.ecocore.scheduler.NightMarketRotationScheduler;
import io.azthera.ecocore.scheduler.RestockTaskScheduler;
import io.azthera.ecocore.sell.SellBlacklistManager;
import io.azthera.ecocore.sell.SellManager;
import io.azthera.ecocore.sell.SellWhitelistManager;
import io.azthera.ecocore.shop.RestockScheduler;
import io.azthera.ecocore.shop.ShopManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.List;

/**
 * EcoCore's main plugin class: wires every module together in
 * dependency order on {@link #onEnable()} and tears everything down
 * cleanly on {@link #onDisable()}.
 */
public final class EcoCorePlugin extends JavaPlugin {

    private static EcoCorePlugin instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;

    private PlayerDao playerDao;
    private MoneyDao moneyDao;
    private ShopItemDao shopItemDao;
    private StockEventDao stockEventDao;
    private MarketHistoryDao marketHistoryDao;
    private BuyHistoryDao buyHistoryDao;
    private SellHistoryDao sellHistoryDao;
    private InflationHistoryDao inflationHistoryDao;
    private JobsDao jobsDao;
    private JobMissionDao jobMissionDao;
    private AiLearningDao aiLearningDao;
    private DiscordLogDao discordLogDao;
    private NightMarketDao nightMarketDao;

    private EconomyEngine economyEngine;
    private AiLearningModel aiLearningModel;
    private AiEconomyEngine aiEconomyEngine;
    private InflationEngine inflationEngine;

    private ShopManager shopManager;
    private RestockScheduler restockScheduler;
    private SellManager sellManager;

    private NightMarketManager nightMarketManager;

    private GuiManager guiManager;
    private PrivateChatInputManager privateChatInputManager;

    private JobsManager jobsManager;

    private ItemIdentityResolver itemIdentityResolver;

    private DiscordChannelRouter discordChannelRouter;
    private DiscordEmbedBuilder discordEmbedBuilder;
    private DiscordWebhookSender discordWebhookSender;
    private DiscordBotManager discordBotManager;

    private PlayerDataManager playerDataManager;
    private NotificationManager notificationManager;

    private AiCalculationScheduler aiCalculationScheduler;
    private InflationTaskScheduler inflationTaskScheduler;
    private RestockTaskScheduler restockTaskScheduler;
    private AutoSaveScheduler autoSaveScheduler;
    private NightMarketRotationScheduler nightMarketRotationScheduler;

    private EcoCoreAPI api;

    public static EcoCorePlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        setupConfig();
        setupDatabase();
        setupDao();
        setupEconomy();
        setupAiAndInflation();
        setupShopAndSell();
        setupNightMarket();
        setupGui();
        setupJobs();
        setupHooks();
        setupDiscord();
        setupManagers();
        setupPlaceholderApi();
        setupApi();
        registerListeners();
        registerCommands();
        startSchedulers();

        getLogger().info("[EcoCore] EcoCore enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (aiCalculationScheduler != null) aiCalculationScheduler.stop();
        if (inflationTaskScheduler != null) inflationTaskScheduler.stop();
        if (restockTaskScheduler != null) restockTaskScheduler.stop();
        if (autoSaveScheduler != null) autoSaveScheduler.stop();
        if (nightMarketRotationScheduler != null) nightMarketRotationScheduler.stop();

        if (discordBotManager != null) {
            discordBotManager.shutdown();
        }

        if (economyEngine != null) {
            economyEngine.saveAll();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("[EcoCore] EcoCore disabled.");
    }

    private void setupConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        configManager = new ConfigManager(this);
        configManager.loadAll();
    }

    private void setupDatabase() {
        databaseManager = new DatabaseManager(this, configManager.getDatabaseConfig());
        try {
            databaseManager.initialize();
        } catch (SQLException exception) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    "[EcoCore] Failed to initialize database; refusing to continue startup.", exception);
            throw new IllegalStateException("EcoCore database initialization failed", exception);
        }
    }

    private void setupDao() {
        playerDao = new PlayerDao(databaseManager);
        moneyDao = new MoneyDao(databaseManager);
        shopItemDao = new ShopItemDao(databaseManager);
        stockEventDao = new StockEventDao(databaseManager);
        marketHistoryDao = new MarketHistoryDao(databaseManager);
        buyHistoryDao = new BuyHistoryDao(databaseManager);
        sellHistoryDao = new SellHistoryDao(databaseManager);
        inflationHistoryDao = new InflationHistoryDao(databaseManager);
        jobsDao = new JobsDao(databaseManager);
        jobMissionDao = new JobMissionDao(databaseManager);
        aiLearningDao = new AiLearningDao(databaseManager);
        discordLogDao = new DiscordLogDao(databaseManager);
        nightMarketDao = new NightMarketDao(databaseManager);
    }

    private void setupEconomy() {
        economyEngine = new EconomyEngine(getLogger(), playerDao, moneyDao, configManager);

        if (configManager.isModuleEnabled("vault-hook-enabled")
                && getServer().getPluginManager().getPlugin("Vault") != null) {
            getServer().getServicesManager().register(
                    Economy.class, new VaultEconomyHook(economyEngine), this, ServicePriority.Highest);
            getLogger().info("[EcoCore] Registered Vault economy hook.");
        }
    }

    private void setupAiAndInflation() {
        aiLearningModel = new AiLearningModel(aiLearningDao, configManager.getAiConfig());

        inflationEngine = new InflationEngine(getLogger(), playerDao, moneyDao, buyHistoryDao,
                sellHistoryDao, inflationHistoryDao, configManager.getInflationConfig());
        try {
            inflationEngine.loadLastKnownState();
        } catch (SQLException exception) {
            getLogger().warning("[EcoCore] Failed to load last known inflation state: " + exception.getMessage());
        }

        aiEconomyEngine = new AiEconomyEngine(getLogger(), shopItemDao, marketHistoryDao, buyHistoryDao,
                sellHistoryDao, playerDao, moneyDao, configManager.getAiConfig(), configManager.getPricesConfig(),
                configManager.getInflationConfig(), aiLearningModel, inflationEngine::getLatestRecord);
    }

    private void setupShopAndSell() {
        shopManager = new ShopManager(getLogger(), shopItemDao, buyHistoryDao, sellHistoryDao, stockEventDao,
                configManager.getShopConfig(), configManager.getPricesConfig(), configManager, economyEngine);
        shopManager.loadCatalog();

        // Revisi 20 fix: make the AI engine reprice the exact objects
        // ShopManager's catalog holds (instead of independently
        // re-reading the DB) so a computed price is visible to the
        // shop GUI/commands the instant the cycle runs, not just after
        // the next catalog reload.
        aiEconomyEngine.useLiveCatalog(() -> List.copyOf(shopManager.getLiveCatalog().values()));

        RestockDecisionEngine restockDecisionEngine = new RestockDecisionEngine(configManager.getAiConfig());
        restockScheduler = new RestockScheduler(getLogger(), shopManager.getLiveCatalog(),
                restockDecisionEngine, shopManager.getStockManager());

        itemIdentityResolver = new ItemIdentityResolver(
                new ItemsAdderHook(), new OraxenHook(), new MMOItemsHook(), new SlimefunHook());

        SellBlacklistManager sellBlacklistManager = new SellBlacklistManager(
                configManager.getBlacklistConfig(), itemIdentityResolver);
        SellWhitelistManager sellWhitelistManager = new SellWhitelistManager();

        sellManager = new SellManager(getLogger(), shopManager, economyEngine, sellHistoryDao,
                configManager.getPricesConfig(), sellBlacklistManager, sellWhitelistManager, itemIdentityResolver);
    }

    private void setupNightMarket() {
        nightMarketManager = new NightMarketManager(getLogger(), nightMarketDao,
                configManager.getNightMarketConfig(), economyEngine);
        nightMarketManager.loadOrRotate();
    }

    private void setupGui() {
        guiManager = new GuiManager(configManager.getGuiConfig());
        privateChatInputManager = new PrivateChatInputManager(this);
    }

    private void setupJobs() {
        JobProgressTracker progressTracker = new JobProgressTracker(jobsDao, configManager.getJobsConfig(), economyEngine);
        JobSkillTreeManager skillTreeManager = new JobSkillTreeManager(configManager.getJobsConfig());
        JobMissionManager missionManager = new JobMissionManager(jobMissionDao, configManager.getJobsConfig(),
                economyEngine, configManager.getMessagesConfig());
        JobPrestigeManager prestigeManager = new JobPrestigeManager(jobsDao, configManager.getJobsConfig());
        JobLeaderboardManager leaderboardManager = new JobLeaderboardManager(jobsDao, configManager.getJobsConfig());

        jobsManager = new JobsManager(getLogger(), this, jobsDao, configManager.getJobsConfig(),
                progressTracker, skillTreeManager, missionManager, prestigeManager, leaderboardManager,
                configManager.getMessagesConfig());

        // Wire cross-module job actions after all managers exist. This avoids
        // constructor cycles while allowing Shop/Sell/Economy to emit actions.
        shopManager.setJobsManager(jobsManager);
        sellManager.setJobsManager(jobsManager);
        economyEngine.setMoneyEarnedListener((uuid, amount) ->
                jobsManager.processAction(uuid, "EARN_MONEY", 1.0,
                        Math.max(1, (int) Math.ceil(amount))));
    }

    private void setupHooks() {
        // Item-identity hooks are already constructed in setupShopAndSell() via ItemIdentityResolver.
    }

    private void setupDiscord() {
        discordChannelRouter = new DiscordChannelRouter(configManager.getDiscordConfig());
        discordEmbedBuilder = new DiscordEmbedBuilder(configManager.getDiscordConfig());
        discordWebhookSender = new DiscordWebhookSender(getLogger(), configManager.getDiscordConfig());
        discordBotManager = new DiscordBotManager(getLogger(), configManager.getDiscordConfig(),
                discordChannelRouter, discordLogDao);

        if (configManager.isModuleEnabled("discord-enabled")) {
            TrendAnalyzer trendAnalyzer = new TrendAnalyzer(marketHistoryDao);
            PriceGraphRenderer graphRenderer = new PriceGraphRenderer(getLogger());

            discordBotManager.registerCommand(new MarketSlashCommand(shopManager, discordEmbedBuilder));
            discordBotManager.registerCommand(new InflationSlashCommand(inflationEngine, discordEmbedBuilder));
            discordBotManager.registerCommand(new TopMarketSlashCommand(shopManager, trendAnalyzer, discordEmbedBuilder));
            discordBotManager.registerCommand(new HistorySlashCommand(shopManager, discordEmbedBuilder));
            discordBotManager.registerCommand(new RestockSlashCommand(restockScheduler, discordEmbedBuilder));
            discordBotManager.registerCommand(new ItemSlashCommand(shopManager, marketHistoryDao, graphRenderer, discordEmbedBuilder));
            discordBotManager.registerCommand(new PlayerSlashCommand(economyEngine, discordEmbedBuilder));
            discordBotManager.registerCommand(new JobsSlashCommand(jobsManager, discordEmbedBuilder));

            discordBotManager.start();
        }
    }

    private void setupManagers() {
        playerDataManager = new PlayerDataManager(getLogger(), this, economyEngine, shopManager.getFavoriteManager(),
                jobsManager.getMissionManager(), jobsDao, jobsManager);

        notificationManager = new NotificationManager(getLogger(), configManager, configManager.getMessagesConfig(),
                discordBotManager, discordWebhookSender, discordEmbedBuilder);

        inflationEngine.addListener(event ->
                Bukkit.getScheduler().runTask(this, () -> notificationManager.onInflationEvent(event)));

        aiEconomyEngine.addCycleSummaryListener(summary ->
                Bukkit.getScheduler().runTask(this, () ->
                        notificationManager.announcePriceCycleSummary(summary)));

        nightMarketManager.addRotationListener(newOffers ->
                Bukkit.getScheduler().runTask(this, () ->
                        Bukkit.broadcastMessage(configManager.getMessagesConfig().getWithPrefix("market.rotated"))));
    }

    private void setupPlaceholderApi() {
        if (configManager.isModuleEnabled("placeholderapi-enabled")
                && getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EcoCorePlaceholderExpansion(economyEngine, shopManager, inflationEngine, jobsManager)
                    .register();
            getLogger().info("[EcoCore] Registered PlaceholderAPI expansion.");
        }
    }

    private void setupApi() {
        api = new EcoCoreAPIImpl(economyEngine, shopManager, inflationEngine, jobsManager);
    }

    private void registerListeners() {
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerJoinListener(playerDataManager), this);
        pluginManager.registerEvents(new PlayerQuitListener(playerDataManager), this);
        pluginManager.registerEvents(new InventoryClickListener(guiManager), this);
        pluginManager.registerEvents(privateChatInputManager, this);

        var inflationConfig = configManager.getInflationConfig();
        pluginManager.registerEvents(new BlockBreakListener(jobsManager, inflationEngine, inflationConfig), this);
        pluginManager.registerEvents(new EntityDeathListener(jobsManager, inflationEngine, inflationConfig), this);
        pluginManager.registerEvents(new FishListener(jobsManager, inflationEngine, inflationConfig), this);
        pluginManager.registerEvents(new CraftListener(jobsManager, inflationEngine, inflationConfig), this);
        pluginManager.registerEvents(new JobActionListener(jobsManager, inflationEngine, inflationConfig), this);
    }

    private void registerCommands() {
        var guiConfig = configManager.getGuiConfig();

        setExecutor("shop", new ShopCommand(shopManager, configManager, guiManager, guiConfig));
        setExecutor("sell", new SellCommand(sellManager, configManager, guiManager));
        setExecutor("jobs", new JobsCommand(jobsManager, configManager, guiManager, guiConfig));
        setExecutor("job", new JobCommand(jobsManager, configManager, guiManager, guiConfig));
        setExecutor("market", new MarketCommand(nightMarketManager, guiManager, configManager));
        setExecutor("prices", new PricesCommand(shopManager, economyEngine.getFormatter()));
        setExecutor("inflation", new InflationCommand(inflationEngine, configManager.getInflationConfig(),
                playerDao, configManager.getMessagesConfig()));
        setExecutor("history", new HistoryCommand(shopManager, guiManager));
        setExecutor("balance", new BalanceCommand(economyEngine));
        setExecutor("ecoitem", new ItemViewCommand(shopManager, configManager, guiManager, guiConfig));
        setExecutor("ecocore", new EcoCoreCommand(configManager, databaseManager, shopManager, restockScheduler,
                inflationEngine, aiEconomyEngine, aiLearningModel, economyEngine, getDataFolder()));
    }

    private void setExecutor(String name, org.bukkit.command.CommandExecutor executor) {
        var command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            getLogger().warning("[EcoCore] Command '" + name + "' not found in plugin.yml - skipping registration.");
        }
    }

    private void startSchedulers() {
        aiCalculationScheduler = new AiCalculationScheduler(this, aiEconomyEngine,
                configManager.getAiCalculationIntervalSeconds());
        inflationTaskScheduler = new InflationTaskScheduler(this, inflationEngine,
                configManager.getInflationCalculationIntervalSeconds());
        restockTaskScheduler = new RestockTaskScheduler(this, restockScheduler, shopManager,
                notificationManager, configManager.getRestockCheckIntervalSeconds());
        autoSaveScheduler = new AutoSaveScheduler(this, getLogger(), economyEngine,
                configManager.getAutosaveIntervalMinutes());
        nightMarketRotationScheduler = new NightMarketRotationScheduler(this, nightMarketManager);

        if (configManager.isModuleEnabled("ai-economy-enabled")) {
            aiCalculationScheduler.start();
        }
        if (configManager.isModuleEnabled("inflation-enabled")) {
            inflationTaskScheduler.start();
        }
        if (configManager.isModuleEnabled("shop-enabled")) {
            restockTaskScheduler.start();
        }
        if (configManager.isModuleEnabled("night-market-enabled")) {
            nightMarketRotationScheduler.start();
        }
        autoSaveScheduler.start();
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Exposes the active locale-aware message configuration to GUI/API components.
     * Keeping this delegated through ConfigManager ensures all player-facing text
     * uses the selected YAML locale.
     */
    public io.azthera.ecocore.config.MessagesConfig getMessagesConfig() {
        return configManager.getMessagesConfig();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyEngine getEconomyEngine() {
        return economyEngine;
    }

    public ShopManager getShopManager() {
        return shopManager;
    }

    public SellManager getSellManager() {
        return sellManager;
    }

    public NightMarketManager getNightMarketManager() {
        return nightMarketManager;
    }

    public InflationEngine getInflationEngine() {
        return inflationEngine;
    }

    public AiEconomyEngine getAiEconomyEngine() {
        return aiEconomyEngine;
    }

    public JobsManager getJobsManager() {
        return jobsManager;
    }

    public GuiManager getGuiManager() {
        return guiManager;
    }

    public PrivateChatInputManager getPrivateChatInputManager() {
        return privateChatInputManager;
    }

    public io.azthera.ecocore.ai.AiLearningModel getAiLearningModel() {
        return aiLearningModel;
    }

    public io.azthera.ecocore.shop.RestockScheduler getRestockScheduler() {
        return restockScheduler;
    }

    public DiscordBotManager getDiscordBotManager() {
        return discordBotManager;
    }

    public EcoCoreAPI getApi() {
        return api;
    }
    }
