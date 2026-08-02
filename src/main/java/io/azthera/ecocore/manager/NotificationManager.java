package io.azthera.ecocore.manager;

import io.azthera.ecocore.config.ConfigManager;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.discord.DiscordBotManager;
import io.azthera.ecocore.discord.DiscordChannelRouter;
import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.discord.DiscordWebhookSender;
import io.azthera.ecocore.inflation.InflationEvent;
import io.azthera.ecocore.model.EconomicState;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.RestockScheduler;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.util.List;
import java.util.logging.Logger;

/**
 * Central fan-out point for every player-facing and Discord-facing
 * notification EcoCore produces: price changes, inflation state
 * changes, and restocks. Reads the enabled channels
 * (actionbar/bossbar/title/chat/discord/console) from {@code config.yml}
 * and dispatches to each enabled channel.
 */
public final class NotificationManager {

    private final Logger logger;
    private final ConfigManager configManager;
    private final MessagesConfig messagesConfig;
    private final DiscordBotManager discordBotManager;
    private final DiscordWebhookSender webhookSender;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates a notification manager.
     *
     * @param logger            plugin logger
     * @param configManager     resolved main config manager (notification toggles)
     * @param messagesConfig    resolved messages.yml configuration
     * @param discordBotManager Discord bot manager, used for embed sends
     * @param webhookSender     Discord webhook sender, used as a lighter alternative
     * @param embedBuilder      shared embed builder
     */
    public NotificationManager(Logger logger, ConfigManager configManager, MessagesConfig messagesConfig,
                                DiscordBotManager discordBotManager, DiscordWebhookSender webhookSender,
                                DiscordEmbedBuilder embedBuilder) {
        this.logger = logger;
        this.configManager = configManager;
        this.messagesConfig = messagesConfig;
        this.discordBotManager = discordBotManager;
        this.webhookSender = webhookSender;
        this.embedBuilder = embedBuilder;
    }

    /**
     * Announces a price change across every enabled channel.
     *
     * @param item          the item whose price changed
     * @param previousPrice the price before this change
     */
    public void announcePriceChange(ShopItemRecord item, double previousPrice) {
        if (!configManager.isBroadcastPriceChanges()) {
            return;
        }

        boolean up = item.getCurrentPrice() >= previousPrice;
        String messageKey = up ? "market.price-up" : "market.price-down";
        String message = messagesConfig.get(messageKey, "item", item.getId(), "price",
                String.format("%.2f", item.getCurrentPrice()));

        broadcastToPlayers(message, false);

        if (configManager.isUseDiscordNotifications()) {
            discordBotManager.sendEmbed(DiscordChannelRouter.CHANNEL_MARKET, "PRICE_CHANGE",
                    embedBuilder.priceChangeEmbed(item, previousPrice));
            webhookSender.sendMarketMessage(org.bukkit.ChatColor.stripColor(message));
        }

        if (configManager.isUseConsole()) {
            logger.info("[EcoCore] " + org.bukkit.ChatColor.stripColor(message));
        }
    }

    /**
     * Announces a restock across every enabled channel (if restock
     * broadcasting is enabled in config.yml).
     *
     * @param item   the restocked item
     * @param amount the amount restocked
     */
    public void announceRestock(ShopItemRecord item, int amount) {
        if (!configManager.isBroadcastRestock()) {
            return;
        }

        if (configManager.isUseDiscordNotifications()) {
            discordBotManager.sendEmbed(DiscordChannelRouter.CHANNEL_MARKET, "RESTOCK",
                    embedBuilder.restockEmbed(item, amount));
        }

        if (configManager.isUseConsole()) {
            logger.info("[EcoCore] Restocked " + item.getId() + " +" + amount);
        }
    }

    /**
     * Announces a batch of restocks from a single restock pass.
     *
     * @param outcomes the outcomes from {@code RestockScheduler.runRestockPass}
     * @param resolver used to resolve each outcome's item record for messaging
     */
    public void announceRestockBatch(List<RestockScheduler.RestockOutcome> outcomes,
                                      java.util.function.Function<String, ShopItemRecord> resolver) {
        for (RestockScheduler.RestockOutcome outcome : outcomes) {
            ShopItemRecord item = resolver.apply(outcome.itemId());
            if (item != null) {
                announceRestock(item, outcome.amount());
            }
        }
    }

    /**
     * Reacts to an inflation cycle completing, announcing a state
     * change across every enabled channel only when the state
     * actually changed this cycle.
     *
     * @param event the completed inflation cycle event
     */
    public void onInflationEvent(InflationEvent event) {
        if (!event.isStateChange() || !configManager.isBroadcastInflationChanges()) {
            return;
        }

        EconomicState state = event.newState();
        String messageKey = "inflation." + state.messageKey();
        String message = messagesConfig.getWithPrefix(messageKey);

        broadcastToPlayers(message, true);

        if (configManager.isUseDiscordNotifications()) {
            discordBotManager.sendEmbed(DiscordChannelRouter.CHANNEL_INFLATION, "STATE_CHANGE",
                    embedBuilder.inflationStateEmbed(event.record()));
        }

        if (configManager.isUseConsole()) {
            logger.info("[EcoCore] Economic state changed: " + event.previousState() + " -> " + state);
        }
    }

    private void broadcastToPlayers(String message, boolean isMajorEvent) {
        if (configManager.isUseChat()) {
            Bukkit.broadcastMessage(message);
        }

        if (configManager.isUseActionbar()) {
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(message);
            }
        }

        if (isMajorEvent && configManager.isUseTitle()) {
            for (var player : Bukkit.getOnlinePlayers()) {
                player.sendTitle("§eEkonomi Server", message, 10, 60, 10);
            }
        }

        if (isMajorEvent && configManager.isUseBossbar()) {
            BossBar bossBar = Bukkit.createBossBar(message, BarColor.YELLOW, BarStyle.SOLID);
            for (var player : Bukkit.getOnlinePlayers()) {
                bossBar.addPlayer(player);
            }
            Bukkit.getScheduler().runTaskLater(
                    Bukkit.getPluginManager().getPlugin("EcoCore"), bossBar::removeAll, 200L);
        }
    }
}