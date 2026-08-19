package io.azthera.ecocore.discord;

import com.google.gson.Gson;
import io.azthera.ecocore.config.DiscordConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Sends plain-text messages to Discord via incoming webhooks, used as
 * a lighter-weight alternative to the full bot connection for
 * servers that only want simple market/trade announcements without
 * running a bot user. Independent of {@code DiscordBotManager} - both
 * can be enabled simultaneously.
 */
public final class DiscordWebhookSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Logger logger;
    private final DiscordConfig discordConfig;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    /**
     * Creates a webhook sender.
     *
     * @param logger        plugin logger for send failures
     * @param discordConfig resolved discord.yml configuration (webhook toggles/urls)
     */
    public DiscordWebhookSender(Logger logger, DiscordConfig discordConfig) {
        this.logger = logger;
        this.discordConfig = discordConfig;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    /**
     * Sends a plain-text message to the configured market webhook, if enabled.
     *
     * @param content the message content
     */
    public void sendMarketMessage(String content) {
        send(discordConfig.getMarketWebhookUrl(), content);
    }

    /**
     * Sends a plain-text message to the configured trade webhook, if enabled.
     *
     * @param content the message content
     */
    public void sendTradeMessage(String content) {
        send(discordConfig.getTradeWebhookUrl(), content);
    }

    private void send(String webhookUrl, String content) {
        if (!discordConfig.isWebhooksEnabled() || webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        try {
            String body = gson.toJson(Map.of("content", content));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(throwable -> {
                        logger.warning("[EcoCore] Discord webhook send failed: " + throwable.getMessage());
                        return null;
                    });
        } catch (Exception exception) {
            logger.warning("[EcoCore] Discord webhook send failed: " + exception.getMessage());
        }
    }
}