package io.azthera.ecocore.hook;

import io.azthera.ecocore.config.BlacklistConfig;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;

/**
 * Soft integration with the MMOItems plugin, resolved via reflection.
 * Falls back to reading MMOItems' well-known persistent data keys
 * directly if the reflective API call path changes between MMOItems
 * versions, since MMOItems tags every item with PDC type/id keys.
 */
public final class MMOItemsHook {

    private final boolean available;

    /**
     * Creates the hook, detecting at construction time whether
     * MMOItems is present on the server.
     */
    public MMOItemsHook() {
        this.available = Bukkit.getPluginManager().getPlugin("MMOItems") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Resolves the MMOItems "TYPE:ID" identifier for an item stack, if any.
     *
     * @param stack the item stack to identify
     * @return the MMOItems id in "TYPE:ID" form, or {@code null} if not an MMOItems item or unavailable
     */
    public String resolveId(ItemStack stack) {
        if (!available || stack == null) {
            return null;
        }
        try {
            Class<?> nbtItemClass = Class.forName("net.Indyuce.mmoitems.api.item.NBTItem");
            Method getMethod = nbtItemClass.getMethod("get", ItemStack.class);
            Object nbtItem = getMethod.invoke(null, stack);

            Method getStringMethod = nbtItemClass.getMethod("getString", String.class);
            String type = (String) getStringMethod.invoke(nbtItem, "MMOITEMS_ITEM_TYPE");
            String id = (String) getStringMethod.invoke(nbtItem, "MMOITEMS_ITEM_ID");

            if (type == null || id == null || type.isBlank() || id.isBlank()) {
                return null;
            }
            return type + ":" + id;
        } catch (ReflectiveOperationException exception) {
            return resolveViaPdc(stack);
        }
    }

    private String resolveViaPdc(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        org.bukkit.NamespacedKey typeKey = new org.bukkit.NamespacedKey("mmoitems", "type");
        org.bukkit.NamespacedKey idKey = new org.bukkit.NamespacedKey("mmoitems", "id");

        String type = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        String id = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);

        if (type == null || id == null) {
            return null;
        }
        return type + ":" + id;
    }

    /**
     * Checks whether an item stack resolves to a blacklisted MMOItems id.
     *
     * @param stack           the item stack to check
     * @param blacklistConfig resolved blacklist.yml configuration
     * @return {@code true} if blacklisted
     */
    public boolean isBlacklisted(ItemStack stack, BlacklistConfig blacklistConfig) {
        String id = resolveId(stack);
        return id != null && blacklistConfig.getMmoItemsBlacklistedIds().contains(id);
    }
}