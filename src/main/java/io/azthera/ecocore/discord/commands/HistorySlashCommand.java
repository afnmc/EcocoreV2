package io.azthera.ecocore.discord.commands;

import io.azthera.ecocore.discord.DiscordEmbedBuilder;
import io.azthera.ecocore.model.TransactionRecord;
import io.azthera.ecocore.shop.ShopManager;
import net.dv8tion.jda.api.entities.OfflineUser;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

/**
 * {@code /history} - shows a player's recent buy/sell transactions.
 * Player identity is resolved by Minecraft username, not Discord
 * account, since EcoCore has no Discord-to-Minecraft account linking.
 */
public final class HistorySlashCommand implements SlashCommandHandler {

    private static final int LIMIT = 10;

    private final ShopManager shopManager;
    private final DiscordEmbedBuilder embedBuilder;

    /**
     * Creates the history command handler.
     *
     * @param shopManager  shared shop manager
     * @param embedBuilder shared embed builder
     */
    public HistorySlashCommand(ShopManager shopManager, DiscordEmbedBuilder embedBuilder) {
        this.shopManager = shopManager;
        this.embedBuilder = embedBuilder;
    }

    @Override
    public String getName() {
        return "history";
    }

    @Override
    public CommandData build() {
        return Commands.slash("history", "Lihat riwayat transaksi seorang player")
                .addOptions(new OptionData(OptionType.STRING, "player", "Nama player Minecraft", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("player").getAsString();

        @SuppressWarnings("deprecation")
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = offlinePlayer.getUniqueId();

        try {
            List<TransactionRecord> history = shopManager.getHistory(uuid, LIMIT);
            if (history.isEmpty()) {
                event.reply("Tidak ada riwayat transaksi untuk `" + playerName + "`.").setEphemeral(true).queue();
                return;
            }

            StringBuilder description = new StringBuilder();
            for (TransactionRecord record : history) {
                boolean isBuy = record.type() == TransactionRecord.TransactionType.BUY;
                description.append(isBuy ? "\uD83D\uDFE2 Beli " : "\uD83D\uDD34 Jual ")
                        .append(record.amount()).append("x ").append(record.itemId())
                        .append(" - ").append(String.format("%.2f", record.totalPrice())).append("\n");
            }

            event.replyEmbeds(embedBuilder.infoEmbed(
                    "Riwayat: " + playerName, description.toString()).build()).queue();
        } catch (java.sql.SQLException exception) {
            event.reply("Gagal memuat riwayat transaksi.").setEphemeral(true).queue();
        }
    }
}