package io.azthera.ecocore.discord;

import io.azthera.ecocore.config.DiscordConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Resolves EcoCore's logical channel keys ("market", "inflation",
 * "trade-log", "sell-log", "buy-log", "admin-log", "crash-log") to
 * their configured Discord channel ids from {@code discord.yml}, and
 * fetches the live JDA channel object for sending.
 */
public final class DiscordChannelRouter {

    public static final String CHANNEL_MARKET = "market";
    public static final String CHANNEL_INFLATION = "inflation";
    public static final String CHANNEL_TRADE_LOG = "trade-log";
    public static final String CHANNEL_SELL_LOG = "sell-log";
    public static final String CHANNEL_BUY_LOG = "buy-log";
    public static final String CHANNEL_ADMIN_LOG = "admin-log";
    public static final String CHANNEL_CRASH_LOG = "crash-log";

    private final DiscordConfig discordConfig;

    /**
     * Creates a channel router.
     *
     * @param discordConfig resolved discord.yml configuration
     */
    public DiscordChannelRouter(DiscordConfig discordConfig) {
        this.discordConfig = discordConfig;
    }

    /**
     * Resolves the configured channel id for a logical channel key.
     *
     * @param channelKey one of the {@code CHANNEL_*} constants
     * @return the configured Discord channel id, or blank if unset/unrecognized
     */
    public String resolveChannelId(String channelKey) {
        return switch (channelKey) {
            case CHANNEL_MARKET -> discordConfig.getMarketChannelId();
            case CHANNEL_INFLATION -> discordConfig.getInflationChannelId();
            case CHANNEL_TRADE_LOG -> discordConfig.getTradeLogChannelId();
            case CHANNEL_SELL_LOG -> discordConfig.getSellLogChannelId();
            case CHANNEL_BUY_LOG -> discordConfig.getBuyLogChannelId();
            case CHANNEL_ADMIN_LOG -> discordConfig.getAdminLogChannelId();
            case CHANNEL_CRASH_LOG -> discordConfig.getCrashLogChannelId();
            default -> "";
        };
    }

    /**
     * Fetches the live JDA message channel for a logical channel key.
     *
     * @param jda        the active JDA instance
     * @param channelKey one of the {@code CHANNEL_*} constants
     * @return the resolved channel, or {@code null} if unconfigured or not found
     */
    public MessageChannel resolveChannel(JDA jda, String channelKey) {
        String channelId = resolveChannelId(channelKey);
        if (channelId == null || channelId.isBlank() || channelId.equals("REPLACE_ME")) {
            return null;
        }
        return jda.getTextChannelById(channelId);
    }
}