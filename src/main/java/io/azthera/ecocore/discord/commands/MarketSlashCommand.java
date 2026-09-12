package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.shop.ShopManager;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * {@code /market} - shows an overview of the market, or a specific
 * item's live price/stock if an item id is provided.
 */
public final class MarketSlashCommand implements SlashCommandHandler {

    private final ShopManager shopManager;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the market command handler.
     *
     * @param shopManager  shared shop manager
     * @param embedBuilder shared embed builder
     */
    public MarketSlashCommand(ShopManager shopManager, DiscordEmbedBuilder embedBuilder) {
        this.shopManager = shopManager;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "market";
    }

    @Override
    public CommandData build() {
        return Commands.slash("market", "Lihat kondisi market EcoCore")
                .addOptions(new OptionData(OptionType.STRING, "item", "ID barang tertentu (opsional)", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String itemId = event.getOption("item") != null ? event.getOption("item").getAsString() : null;

        if (itemId != null) {
            ShopItemRecord item = shopManager.getItem(itemId);
            if (item == null) {
                event.reply("Barang `" + itemId + "` tidak ditemukan.").setEphemeral(true).queue();
                return;
            }
            var embed = embedBuilder.infoEmbed(item.getId(),
                    "Kategori: " + item.getCategory()
                            + "\nHarga: " + String.format("%.2f", item.getCurrentPrice())
                            + "\nStock: " + item.getStock() + "/" + item.getMaxStock());
            event.replyEmbeds(embed.build()).queue();
            return;
        }

        int totalItems = shopManager.getAllItems().size();
        long soldOut = shopManager.getAllItems().stream().filter(ShopItemRecord::isSoldOut).count();

        var embed = embedBuilder.infoEmbed("Market Overview",
                "Total barang: " + totalItems + "\nSedang sell out: " + soldOut);
        event.replyEmbeds(embed.build()).queue();
    }
}