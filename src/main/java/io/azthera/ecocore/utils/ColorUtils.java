package io.azthera.ecocore.utils;

import org.bukkit.ChatColor;

/**
 * Shared color-code translation helpers used across configs, GUIs,
 * and Discord-adjacent text formatting that still needs Minecraft
 * color codes stripped for plain-text contexts.
 */
public final class ColorUtils {

    private ColorUtils() {
        // Utility class, not instantiable.
    }

    /**
     * Translates '&' color codes into Minecraft's section-sign color codes.
     *
     * @param input the raw string containing '&'-coded colors
     * @return the translated string, or an empty string if input is {@code null}
     */
    public static String colorize(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    /**
     * Strips all Minecraft color codes from a string, producing plain
     * text suitable for logs, Discord messages, or PlaceholderAPI
     * output that shouldn't carry formatting codes.
     *
     * @param input the colorized string
     * @return the plain-text string with color codes removed
     */
    public static String stripColor(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.stripColor(colorize(input));
    }
}