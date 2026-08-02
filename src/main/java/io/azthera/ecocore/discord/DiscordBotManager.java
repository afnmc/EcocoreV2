package io.azthera.ecocore.discord;

import io.azthera.ecocore.config.DiscordConfig;
import io.azthera.ecocore.database.dao.DiscordLogDao;
import io.azthera.ecocore.discord.commands.SlashCommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;

import javax.security.auth.login.LoginException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Owns EcoCore's JDA bot connection: login, activity status, slash
 * command registration/routing, and outbound message sending to
 * configured channels. Every {@link SlashCommandHandler} is
 * registered here and every incoming slash command interaction is
 * routed to the matching handler by name.
 */
public final class DiscordBotManager extends ListenerAdapter {

    private final Logger logger;
    private final DiscordConfig discordConfig;
    private final DiscordChannelRouter channelRouter;
    private final DiscordLogDao discordLogDao;

    private final Map<String, SlashCommandHandler> commandHandlers = new HashMap<>();

    private JDA jda;

    /**
     * Creates the Discord bot manager. Call {@link #registerCommand}
     * for every available command handler, then {@link #start()} once
     * ready during plugin enable.
     *
     * @param logger        plugin logger
     * @param discordConfig resolved discord.yml configuration
     * @param channelRouter channel key resolver
     * @param discordLogDao DAO used to audit-log every message sent
     */
    public DiscordBotManager(Logger logger, DiscordConfig discordConfig,
                              DiscordChannelRouter channelRouter, DiscordLogDao discordLogDao) {
        this.logger = logger;
        this.discordConfig = discordConfig;
        this.channelRouter = channelRouter;
        this.discordLogDao = discordLogDao;
    }

    /**
     * Registers a slash command handler to be built and routed by this manager.
     *
     * @param handler the command handler to register
     */
    public void registerCommand(SlashCommandHandler handler) {
        commandHandlers.put(handler.getName(), handler);
    }

    /**
     * Logs in and connects the bot if {@code discord.yml} has a valid
     * token configured and the bot is enabled. Safe to call even when
     * disabled/unconfigured - it will simply no-op.
     */
    public void start() {
        if (!discordConfig.isReadyToStart()) {
            logger.info("[EcoCore] Discord bot is disabled or not configured - skipping startup");
            return;
        }

        try {
            JDABuilder builder = JDABuilder.createDefault(discordConfig.getToken())
                    .enableIntents(GatewayIntent.GUILD_MESSAGES)
                    .addEventListeners(this);

            if (!discordConfig.getActivityStatus().isBlank()) {
                builder.setActivity(Activity.playing(discordConfig.getActivityStatus()));
            }

            jda = builder.build();
            jda.awaitReady();

            if (discordConfig.isSlashCommandsEnabled()) {
                registerSlashCommands();
            }

            logger.info("[EcoCore] Discord bot connected as " + jda.getSelfUser().getAsTag());
        } catch (LoginException | InterruptedException exception) {
            logger.severe("[EcoCore] Failed to start Discord bot: " + exception.getMessage());
            jda = null;
        }
    }

    private void registerSlashCommands() {
        List<CommandData> commandDataList = discordConfig.getSlashCommands().stream()
                .map(commandHandlers::get)
                .filter(handler -> handler != null)
                .map(SlashCommandHandler::build)
                .toList();

        if (commandDataList.isEmpty()) {
            return;
        }

        if (discordConfig.isRegisterGuildOnly() && !discordConfig.getGuildId().isBlank()) {
            Guild guild = jda.getGuildById(discordConfig.getGuildId());
            if (guild != null) {
                guild.updateCommands().addCommands(commandDataList).queue();
                return;
            }
        }

        jda.updateCommands().addCommands(commandDataList).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommandHandler handler = commandHandlers.get(event.getName());
        if (handler == null) {
            event.reply("Command tidak dikenali.").setEphemeral(true).queue();
            return;
        }
        try {
            handler.execute(event);
        } catch (Exception exception) {
            logger.severe("[EcoCore] Slash command '" + event.getName() + "' failed: " + exception.getMessage());
            if (!event.isAcknowledged()) {
                event.reply("Terjadi error saat menjalankan command.").setEphemeral(true).queue();
            }
        }
    }

    /**
     * Sends an embed to a logical channel key, if the bot is connected
     * and the channel is configured, and logs the send to the audit trail.
     *
     * @param channelKey one of {@link DiscordChannelRouter}'s {@code CHANNEL_*} constants
     * @param logType    a short type tag for the audit log (e.g. "PRICE_CHANGE")
     * @param embed      the embed to send
     */
    public void sendEmbed(String channelKey, String logType, EmbedBuilder embed) {
        if (jda == null) {
            return;
        }
        MessageChannel channel = channelRouter.resolveChannel(jda, channelKey);
        if (channel == null) {
            return;
        }

        channel.sendMessageEmbeds(embed.build()).queue();
        logMessage(channelKey, logType, embed.build().getTitle());
    }

    /**
     * Sends an embed with an attached image (e.g. a rendered price
     * graph) to a logical channel key.
     *
     * @param channelKey one of {@link DiscordChannelRouter}'s {@code CHANNEL_*} constants
     * @param logType    a short type tag for the audit log
     * @param embed      the embed to send
     * @param imageBytes PNG image bytes to attach
     * @param fileName   the attachment's file name (e.g. "graph.png")
     */
    public void sendEmbedWithImage(String channelKey, String logType, EmbedBuilder embed,
                                    byte[] imageBytes, String fileName) {
        if (jda == null) {
            return;
        }
        MessageChannel channel = channelRouter.resolveChannel(jda, channelKey);
        if (channel == null || imageBytes == null) {
            return;
        }

        embed.setImage("attachment://" + fileName);
        channel.sendFiles(FileUpload.fromData(imageBytes, fileName))
                .setEmbeds(embed.build())
                .queue();
        logMessage(channelKey, logType, embed.build().getTitle());
    }

    private void logMessage(String channelKey, String logType, String summary) {
        try {
            discordLogDao.insert(channelKey, logType, summary != null ? summary : "");
        } catch (SQLException exception) {
            logger.warning("[EcoCore] Failed to write discord log entry: " + exception.getMessage());
        }
    }

    /**
     * Shuts down the JDA connection cleanly, called on plugin disable.
     */
    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    /**
     * Whether the bot is currently connected.
     *
     * @return {@code true} if JDA has an active session
     */
    public boolean isConnected() {
        return jda != null;
    }
}