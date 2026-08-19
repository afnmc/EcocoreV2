package io.azthera.ecocore.config;

import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Parsed view of {@code gui.yml}: global GUI behavior, sounds, and
 * button icon definitions shared across every GUI screen.
 */
public final class GuiConfig {

    /**
     * An icon definition for a shared GUI button (material + optional
     * custom model data for resource-pack driven textures).
     *
     * @param material        Bukkit Material name
     * @param customModelData custom model data value, or 0 if unused
     */
    public record ButtonIcon(String material, int customModelData) {
    }

    private final boolean animationsEnabled;
    private final boolean soundsEnabled;
    private final boolean resourcePackSupport;
    private final Map<String, Sound> sounds = new HashMap<>();
    private final Map<String, ButtonIcon> buttons = new HashMap<>();
    private final int shopMainRows;
    private final int sellMainRows;
    private final int jobsMainRows;
    private final int minionsMainRows;

    /**
     * Parses GUI configuration from the loaded {@code gui.yml}.
     *
     * @param config the loaded gui.yml
     */
    public GuiConfig(FileConfiguration config) {
        Logger logger = Logger.getLogger("EcoCore");

        this.animationsEnabled = config.getBoolean("general.animations-enabled", true);
        this.soundsEnabled = config.getBoolean("general.sounds-enabled", true);
        this.resourcePackSupport = config.getBoolean("general.resource-pack-support", true);

        ConfigurationSection soundsSection = config.getConfigurationSection("sounds");
        if (soundsSection != null) {
            for (String key : soundsSection.getKeys(false)) {
                String soundName = soundsSection.getString(key);
                try {
                    sounds.put(key, Sound.valueOf(soundName));
                } catch (IllegalArgumentException exception) {
                    logger.warning("[EcoCore] Unknown sound '" + soundName + "' for gui.sounds." + key);
                }
            }
        }

        ConfigurationSection buttonsSection = config.getConfigurationSection("buttons");
        if (buttonsSection != null) {
            for (String key : buttonsSection.getKeys(false)) {
                ConfigurationSection buttonSection = buttonsSection.getConfigurationSection(key);
                if (buttonSection == null) {
                    continue;
                }
                buttons.put(key, new ButtonIcon(
                        buttonSection.getString("material", "STONE"),
                        buttonSection.getInt("custom-model-data", 0)
                ));
            }
        }

        this.shopMainRows = config.getInt("layouts.shop-main-rows", 6);
        this.sellMainRows = config.getInt("layouts.sell-main-rows", 6);
        this.jobsMainRows = config.getInt("layouts.jobs-main-rows", 3);
        this.minionsMainRows = config.getInt("layouts.minions-main-rows", 4);
    }

    public boolean isAnimationsEnabled() {
        return animationsEnabled;
    }

    public boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public boolean isResourcePackSupport() {
        return resourcePackSupport;
    }

    /**
     * Looks up a configured sound by its key (e.g. "click", "buy", "sell").
     *
     * @param key the sound key from gui.yml
     * @return the resolved Sound, or {@code null} if not configured/invalid
     */
    public Sound getSound(String key) {
        return sounds.get(key);
    }

    /**
     * Looks up a configured button icon by its key (e.g. "next-page", "close").
     *
     * @param key the button key from gui.yml
     * @return the icon definition, or {@code null} if not configured
     */
    public ButtonIcon getButtonIcon(String key) {
        return buttons.get(key);
    }

    public int getShopMainRows() {
        return shopMainRows;
    }

    public int getSellMainRows() {
        return sellMainRows;
    }

    public int getJobsMainRows() {
        return jobsMainRows;
    }

    public int getMinionsMainRows() {
        return minionsMainRows;
    }
}