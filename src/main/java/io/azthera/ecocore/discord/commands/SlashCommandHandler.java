package io.azthera.ecocore.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * A single Discord slash command's definition and execution logic.
 * Implementations are registered with the guild/global command set
 * by {@code DiscordBotManager} and routed incoming interactions by name.
 */
public interface SlashCommandHandler {

    /**
     * The command's name as typed in Discord (e.g. "market"), must
     * match one of the {@code slash-commands.commands} entries in
     * {@code discord.yml} to actually be registered.
     *
     * @return the command name
     */
    String getName();

    /**
     * Builds this command's registration definition (name, description, options).
     *
     * @return the JDA command data to register
     */
    CommandData build();

    /**
     * Handles an incoming interaction for this command. Implementations
     * are responsible for calling {@code event.reply(...)} or
     * {@code event.deferReply()} themselves.
     *
     * @param event the triggering slash command interaction
     */
    void execute(SlashCommandInteractionEvent event);
}