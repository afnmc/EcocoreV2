package io.azthera.ecocore.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Parsed view of {@code discord.yml}: bot credentials, channel routing,
 * webhook settings, slash command registration, and embed styling.
 */
public final class DiscordConfig {

    private final boolean botEnabled;
    private final String token;
    private final String guildId;
    private final String activityStatus;

    private final String marketChannelId;
    private final String inflationChannelId;
    private final String tradeLogChannelId;
    private final String sellLogChannelId;
    private final String buyLogChannelId;
    private final String adminLogChannelId;
    private final String crashLogChannelId;

    private final boolean webhooksEnabled;
    private final String marketWebhookUrl;
    private final String tradeWebhookUrl;

    private final boolean slashCommandsEnabled;
    private final boolean registerGuildOnly;
    private final List<String> slashCommands;

    private final String colorPriceUp;
    private final String colorPriceDown;
    private final String colorNeutral;
    private final String colorCrash;
    private final String footerText;

    /**
     * Parses Discord configuration from the loaded {@code discord.yml}.
     *
     * @param config the loaded discord.yml
     */
    public DiscordConfig(FileConfiguration config) {
        this.botEnabled = config.getBoolean("bot.enabled", true);
        this.token = config.getString("bot.token", "");
        this.guildId = config.getString("bot.guild-id", "");
        this.activityStatus = config.getString("bot.activity-status", "");

        this.marketChannelId = config.getString("channels.market-channel-id", "");
        this.inflationChannelId = config.getString("channels.inflation-channel-id", "");
        this.tradeLogChannelId = config.getString("channels.trade-log-channel-id", "");
        this.sellLogChannelId = config.getString("channels.sell-log-channel-id", "");
        this.buyLogChannelId = config.getString("channels.buy-log-channel-id", "");
        this.adminLogChannelId = config.getString("channels.admin-log-channel-id", "");
        this.crashLogChannelId = config.getString("channels.crash-log-channel-id", "");

        this.webhooksEnabled = config.getBoolean("webhooks.enabled", false);
        this.marketWebhookUrl = config.getString("webhooks.market-webhook-url", "");
        this.tradeWebhookUrl = config.getString("webhooks.trade-webhook-url", "");

        this.slashCommandsEnabled = config.getBoolean("slash-commands.enabled", true);
        this.registerGuildOnly = config.getBoolean("slash-commands.register-guild-only", true);
        this.slashCommands = config.getStringList("slash-commands.commands");

        this.colorPriceUp = config.getString("embeds.color-price-up", "#43B581");
        this.colorPriceDown = config.getString("embeds.color-price-down", "#F04747");
        this.colorNeutral = config.getString("embeds.color-neutral", "#7289DA");
        this.colorCrash = config.getString("embeds.color-crash", "#992D22");
        this.footerText = config.getString("embeds.footer-text", "EcoCore");
    }

    public boolean isBotEnabled() {
        return botEnabled;
    }

    public String getToken() {
        return token;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getActivityStatus() {
        return activityStatus;
    }

    public String getMarketChannelId() {
        return marketChannelId;
    }

    public String getInflationChannelId() {
        return inflationChannelId;
    }

    public String getTradeLogChannelId() {
        return tradeLogChannelId;
    }

    public String getSellLogChannelId() {
        return sellLogChannelId;
    }

    public String getBuyLogChannelId() {
        return buyLogChannelId;
    }

    public String getAdminLogChannelId() {
        return adminLogChannelId;
    }

    public String getCrashLogChannelId() {
        return crashLogChannelId;
    }

    public boolean isWebhooksEnabled() {
        return webhooksEnabled;
    }

    public String getMarketWebhookUrl() {
        return marketWebhookUrl;
    }

    public String getTradeWebhookUrl() {
        return tradeWebhookUrl;
    }

    public boolean isSlashCommandsEnabled() {
        return slashCommandsEnabled;
    }

    public boolean isRegisterGuildOnly() {
        return registerGuildOnly;
    }

    public List<String> getSlashCommands() {
        return slashCommands;
    }

    public String getColorPriceUp() {
        return colorPriceUp;
    }

    public String getColorPriceDown() {
        return colorPriceDown;
    }

    public String getColorNeutral() {
        return colorNeutral;
    }

    public String getColorCrash() {
        return colorCrash;
    }

    public String getFooterText() {
        return footerText;
    }

    /**
     * Whether the bot has enough configuration to actually attempt login
     * (enabled, with a non-blank token).
     *
     * @return {@code true} if the bot should attempt to start
     */
    public boolean isReadyToStart() {
        return botEnabled && token != null && !token.isBlank() && !token.equals("REPLACE_WITH_BOT_TOKEN");
    }
}