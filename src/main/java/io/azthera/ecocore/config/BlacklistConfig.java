package io.azthera.ecocore.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parsed view of {@code blacklist.yml}: rules describing which items
 * can never be traded, checked by {@code ItemIdentityResolver} and
 * the various hook classes before any buy/sell action is allowed.
 */
public final class BlacklistConfig {

    private final boolean eventItem;
    private final boolean questItem;
    private final boolean bossDrop;
    private final boolean artifact;
    private final boolean relic;
    private final boolean unique;
    private final boolean legendary;
    private final boolean adminItem;
    private final boolean creativeOnly;
    private final boolean bedrockItem;
    private final boolean commandItem;
    private final boolean museumItem;

    private final String persistentDataKey;
    private final String namespace;

    private final Set<String> materials;
    private final Set<String> namespacedKeys;
    private final Set<Integer> customModelData;
    private final Set<String> nbtKeys;

    private final boolean itemsAdderHookEnabled;
    private final Set<String> itemsAdderBlacklistedIds;
    private final boolean oraxenHookEnabled;
    private final Set<String> oraxenBlacklistedIds;
    private final boolean mmoItemsHookEnabled;
    private final Set<String> mmoItemsBlacklistedIds;
    private final boolean slimefunHookEnabled;
    private final Set<String> slimefunBlacklistedIds;
    private final boolean ecoItemsHookEnabled;
    private final Set<String> ecoItemsBlacklistedIds;
    private final boolean executableItemsHookEnabled;
    private final Set<String> executableItemsBlacklistedIds;

    /**
     * Parses blacklist configuration from the loaded {@code blacklist.yml}.
     *
     * @param config the loaded blacklist.yml
     */
    public BlacklistConfig(FileConfiguration config) {
        this.eventItem = config.getBoolean("category-flags.event-item", true);
        this.questItem = config.getBoolean("category-flags.quest-item", true);
        this.bossDrop = config.getBoolean("category-flags.boss-drop", true);
        this.artifact = config.getBoolean("category-flags.artifact", true);
        this.relic = config.getBoolean("category-flags.relic", true);
        this.unique = config.getBoolean("category-flags.unique", true);
        this.legendary = config.getBoolean("category-flags.legendary", true);
        this.adminItem = config.getBoolean("category-flags.admin-item", true);
        this.creativeOnly = config.getBoolean("category-flags.creative-only", true);
        this.bedrockItem = config.getBoolean("category-flags.bedrock-item", true);
        this.commandItem = config.getBoolean("category-flags.command-item", true);
        this.museumItem = config.getBoolean("category-flags.museum-item", true);

        this.persistentDataKey = config.getString("detection.persistent-data-key", "ecocore:untradeable");
        this.namespace = config.getString("detection.namespace", "ecocore");

        this.materials = new HashSet<>(config.getStringList("materials"));
        this.namespacedKeys = new HashSet<>(config.getStringList("namespaced-keys"));
        this.customModelData = new HashSet<>(config.getIntegerList("custom-model-data"));
        this.nbtKeys = new HashSet<>(config.getStringList("nbt-keys"));

        this.itemsAdderHookEnabled = config.getBoolean("hooks.itemsadder.enabled", true);
        this.itemsAdderBlacklistedIds = readIdSet(config, "hooks.itemsadder.blacklisted-ids");
        this.oraxenHookEnabled = config.getBoolean("hooks.oraxen.enabled", true);
        this.oraxenBlacklistedIds = readIdSet(config, "hooks.oraxen.blacklisted-ids");
        this.mmoItemsHookEnabled = config.getBoolean("hooks.mmoitems.enabled", true);
        this.mmoItemsBlacklistedIds = readIdSet(config, "hooks.mmoitems.blacklisted-ids");
        this.slimefunHookEnabled = config.getBoolean("hooks.slimefun.enabled", true);
        this.slimefunBlacklistedIds = readIdSet(config, "hooks.slimefun.blacklisted-ids");
        this.ecoItemsHookEnabled = config.getBoolean("hooks.ecoitems.enabled", true);
        this.ecoItemsBlacklistedIds = readIdSet(config, "hooks.ecoitems.blacklisted-ids");
        this.executableItemsHookEnabled = config.getBoolean("hooks.executableitems.enabled", true);
        this.executableItemsBlacklistedIds = readIdSet(config, "hooks.executableitems.blacklisted-ids");
    }

    private Set<String> readIdSet(FileConfiguration config, String path) {
        List<String> list = config.getStringList(path);
        return new HashSet<>(list);
    }

    public boolean isEventItem() {
        return eventItem;
    }

    public boolean isQuestItem() {
        return questItem;
    }

    public boolean isBossDrop() {
        return bossDrop;
    }

    public boolean isArtifact() {
        return artifact;
    }

    public boolean isRelic() {
        return relic;
    }

    public boolean isUnique() {
        return unique;
    }

    public boolean isLegendary() {
        return legendary;
    }

    public boolean isAdminItem() {
        return adminItem;
    }

    public boolean isCreativeOnly() {
        return creativeOnly;
    }

    public boolean isBedrockItem() {
        return bedrockItem;
    }

    public boolean isCommandItem() {
        return commandItem;
    }

    public boolean isMuseumItem() {
        return museumItem;
    }

    public String getPersistentDataKey() {
        return persistentDataKey;
    }

    public String getNamespace() {
        return namespace;
    }

    public Set<String> getMaterials() {
        return materials;
    }

    public Set<String> getNamespacedKeys() {
        return namespacedKeys;
    }

    public Set<Integer> getCustomModelData() {
        return customModelData;
    }

    public Set<String> getNbtKeys() {
        return nbtKeys;
    }

    public boolean isItemsAdderHookEnabled() {
        return itemsAdderHookEnabled;
    }

    public Set<String> getItemsAdderBlacklistedIds() {
        return itemsAdderBlacklistedIds;
    }

    public boolean isOraxenHookEnabled() {
        return oraxenHookEnabled;
    }

    public Set<String> getOraxenBlacklistedIds() {
        return oraxenBlacklistedIds;
    }

    public boolean isMmoItemsHookEnabled() {
        return mmoItemsHookEnabled;
    }

    public Set<String> getMmoItemsBlacklistedIds() {
        return mmoItemsBlacklistedIds;
    }

    public boolean isSlimefunHookEnabled() {
        return slimefunHookEnabled;
    }

    public Set<String> getSlimefunBlacklistedIds() {
        return slimefunBlacklistedIds;
    }

    public boolean isEcoItemsHookEnabled() {
        return ecoItemsHookEnabled;
    }

    public Set<String> getEcoItemsBlacklistedIds() {
        return ecoItemsBlacklistedIds;
    }

    public boolean isExecutableItemsHookEnabled() {
        return executableItemsHookEnabled;
    }

    public Set<String> getExecutableItemsBlacklistedIds() {
        return executableItemsBlacklistedIds;
    }
}