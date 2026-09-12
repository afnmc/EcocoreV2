package io.azthera.ecocore.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Helpers for building player-head icons, used by leaderboard GUIs
 * and any future "show a player" GUI element.
 */
public final class SkullUtils {

    private SkullUtils() {
        // Utility class, not instantiable.
    }

    /**
     * Builds a player-head item stack for the given player, with a
     * colorized display name.
     *
     * @param player      the player whose head to build
     * @param displayName the colorized display name to apply
     * @return the built player head item stack
     */
    public static ItemStack buildPlayerHead(OfflinePlayer player, String displayName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(ColorUtils.colorize(displayName));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    /**
     * Resolves a player's current display name, falling back to their
     * uuid string if the name is unknown (never played / cache miss).
     *
     * @param player the offline player to resolve
     * @return the player's name, or their uuid as a string if unavailable
     */
    public static String resolveName(OfflinePlayer player) {
        String name = player.getName();
        return name != null ? name : player.getUniqueId().toString();
    }
}