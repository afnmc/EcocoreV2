package io.azthera.ecocore.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Locale-aware, fully YAML-backed message and UI text resolver. */
public final class MessagesConfig {
    private final FileConfiguration fallback;
    private final FileConfiguration localeConfig;
    private final String locale;
    private final String prefix;

    public MessagesConfig(JavaPlugin plugin, String locale, FileConfiguration fallback) {
        this.fallback = fallback;
        this.locale = normalizeLocale(locale);
        this.localeConfig = loadLocale(plugin, this.locale);
        this.prefix = translate(resolveRaw("prefix", fallback.getString("prefix", "")));
    }

    /** Backward-compatible constructor; uses the legacy messages.yml. */
    public MessagesConfig(FileConfiguration config) {
        this.fallback = config;
        this.localeConfig = config;
        this.locale = "id_ID";
        this.prefix = translate(config.getString("prefix", ""));
    }

    public String getLocale() { return locale; }
    public String getPrefix() { return prefix; }

    public String get(String path, String... placeholders) {
        return applyPlaceholders(translate(resolveRaw(path, path)), placeholders);
    }

    public String getWithPrefix(String path, String... placeholders) {
        return prefix + get(path, placeholders);
    }

    public String get(String path, Map<String, String> placeholders) {
        String result = translate(resolveRaw(path, path));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /** Resolves a YAML list of strings, useful for GUI lore. */
    public List<String> getList(String path, String... placeholders) {
        Object value = resolveValue(path);
        if (!(value instanceof List<?> list)) return List.of(get(path, placeholders));
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(applyPlaceholders(translate(String.valueOf(item)), placeholders));
        }
        return result;
    }

    private Object resolveValue(String path) {
        if (localeConfig.contains(path)) return localeConfig.get(path);
        if (fallback.contains(path)) return fallback.get(path);
        return null;
    }

    private String resolveRaw(String path, String defaultValue) {
        Object value = resolveValue(path);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String applyPlaceholders(String input, String... placeholders) {
        String result = input == null ? "" : input;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return result;
    }

    private String translate(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) return "id_ID";
        String value = locale.trim().replace('-', '_');
        if (value.equalsIgnoreCase("id") || value.equalsIgnoreCase("id_id")) return "id_ID";
        if (value.equalsIgnoreCase("en") || value.equalsIgnoreCase("en_us") || value.equalsIgnoreCase("en_us")) return "en_US";
        return value;
    }

    private static FileConfiguration loadLocale(JavaPlugin plugin, String locale) {
        File file = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            String resource = "lang/" + locale + ".yml";
            try (var stream = plugin.getResource(resource)) {
                if (stream != null) {
                    java.nio.file.Files.copy(stream, file.toPath());
                }
            } catch (Exception ignored) { }
        }
        if (file.exists()) return YamlConfiguration.loadConfiguration(file);
        return new YamlConfiguration();
    }
}
