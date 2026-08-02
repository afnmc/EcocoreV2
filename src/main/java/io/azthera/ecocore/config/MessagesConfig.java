package io.azthera.ecocore.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

/**
 * Parsed view of {@code messages.yml}, with color-code translation
 * and simple {@code {placeholder}} substitution built in.
 */
public final class MessagesConfig {

    private final FileConfiguration config;
    private final String prefix;

    /**
     * Parses messages configuration from the loaded {@code messages.yml}.
     *
     * @param config the loaded messages.yml
     */
    public MessagesConfig(FileConfiguration config) {
        this.config = config;
        this.prefix = translate(config.getString("prefix", ""));
    }

    /**
     * Returns the shared message prefix, color-translated.
     *
     * @return the prefix string
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Looks up a message by its dotted path (e.g. "shop.bought"),
     * color-translates it, and substitutes any {@code {key}} placeholders.
     *
     * @param path         dotted path within messages.yml
     * @param placeholders alternating key/value pairs, e.g. "item", "Diamond", "price", "100"
     * @return the resolved, colorized message, or the path itself if missing
     */
    public String get(String path, String... placeholders) {
        String raw = config.getString(path, path);
        String translated = translate(raw);
        return applyPlaceholders(translated, placeholders);
    }

    /**
     * Looks up a message by path and prepends the shared prefix.
     *
     * @param path         dotted path within messages.yml
     * @param placeholders alternating key/value pairs
     * @return the prefixed, resolved message
     */
    public String getWithPrefix(String path, String... placeholders) {
        return prefix + get(path, placeholders);
    }

    /**
     * Looks up a message by path and substitutes placeholders from a map,
     * useful when the number of placeholders is dynamic.
     *
     * @param path         dotted path within messages.yml
     * @param placeholders a map of placeholder name to value
     * @return the resolved, colorized message
     */
    public String get(String path, Map<String, String> placeholders) {
        String result = translate(config.getString(path, path));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String applyPlaceholders(String input, String... placeholders) {
        String result = input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return result;
    }

    private String translate(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}