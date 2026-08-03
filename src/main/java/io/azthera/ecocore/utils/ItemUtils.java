package io.azthera.ecocore.utils;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.model.MinionType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Shared helpers for working with {@link ItemStack}s across EcoCore.
 */
public final class ItemUtils {

    private static final Logger LOGGER = Logger.getLogger("EcoCore");
    private static final String MINION_EGG_TYPE_KEY = "minion_egg_type";

    private ItemUtils() {
        // Utility class, not instantiable.
    }

    /**
     * Resolves a {@link Material} by name, falling back to STONE if the
     * name is invalid or unknown, so a bad config value never crashes a build.
     *
     * @param name the material name
     * @return the resolved material, or {@link Material#STONE} as a fallback
     */
    public static Material safeMaterial(String name) {
        if (name == null) {
            return Material.STONE;
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException exception) {
            LOGGER.warning("[EcoCore] Unknown material '" + name + "' in config - falling back to STONE.");
            return Material.STONE;
        }
    }

    /**
     * Builds a simple item stack with a colorized display name and lore.
     *
     * @param material the item's material
     * @param amount   the stack amount
     * @param name     colorized display name (using '&' codes)
     * @param lore     colorized lore lines (using '&' codes)
     * @return the built item stack
     */
    public static ItemStack named(Material material, int amount, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtils.colorize(name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream().map(ColorUtils::colorize).toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Builds the icon item used to represent a minion type in shop/
     * inventory GUIs (the "Beli Minion" screen and the owned-minions
     * list both use this exact same builder so the two never drift
     * out of sync visually). Falls back to a Villager Spawn Egg icon
     * with the raw type name if {@code minions.yml} has no entry for
     * this type, and logs a warning so a missing config entry is easy
     * to spot in console instead of silently showing a generic icon.
     *
     * @param type          the minion type to build an icon for
     * @param minionsConfig resolved minions.yml configuration
     * @return the built icon item stack (no lore - callers add their own)
     */
    public static ItemStack buildMinionTypeIcon(MinionType type, MinionsConfig minionsConfig) {
        MinionsConfig.MinionDefinition definition = minionsConfig.getDefinition(type);
        if (definition == null) {
            LOGGER.warning("[EcoCore] minions.yml has no entry for minion type '" + type.configKey()
                    + "' - showing a fallback icon. Add a 'minions." + type.configKey() + "' section to fix this.");
        }

        Material material = definition != null ? safeMaterial(definition.icon()) : Material.VILLAGER_SPAWN_EGG;
        String displayName = definition != null ? definition.displayName() : ("&f" + type.configKey());

        return named(material, 1, displayName, List.of());
    }

    /**
     * Gives an amount of a material to a player's inventory, splitting
     * into multiple stacks if it exceeds the material's max stack
     * size, and dropping any overflow on the ground at their feet if
     * their inventory doesn't have room.
     *
     * @param player   the player to give items to
     * @param material the material to give
     * @param amount   the total quantity to give
     */
    public static void giveOrDrop(Player player, Material material, int amount) {
        int remaining = amount;
        int maxStackSize = new ItemStack(material).getMaxStackSize();

        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack stack = new ItemStack(material, stackAmount);

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack leftoverStack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverStack);
            }

            remaining -= stackAmount;
        }
    }

    /**
     * Builds a "minion egg" item: what a player receives after buying
     * a minion (or removing a placed one), instead of the minion
     * being placed instantly. Right-clicking the ground with this
     * item consumes one and places the actual minion at that location.
     *
     * @param type          the minion type this egg will place
     * @param minionsConfig resolved minions.yml configuration (for display name)
     * @return the built egg item stack
     */
    public static ItemStack buildMinionEgg(MinionType type, MinionsConfig minionsConfig) {
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            MinionsConfig.MinionDefinition definition = minionsConfig.getDefinition(type);
            String rawName = definition != null ? definition.displayName() : type.configKey();

            meta.setDisplayName(ColorUtils.colorize("&a&lMinion Egg &7- " + ColorUtils.stripColor(rawName)));
            meta.setLore(List.of(
                    ColorUtils.colorize("&7Klik kanan ke tanah buat naruh minion ini."),
                    ColorUtils.colorize("&7Tipe: &f" + type.configKey())
            ));

            NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), MINION_EGG_TYPE_KEY);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.name());

            egg.setItemMeta(meta);
        }
        return egg;
    }

    /**
     * Reads the minion type tagged on an item stack by
     * {@link #buildMinionEgg}, if any.
     *
     * @param stack the item stack to check
     * @return the tagged minion type, or {@code null} if this isn't a minion egg
     */
    public static MinionType readMinionEggType(ItemStack stack) {
        if (stack == null || stack.getType() != Material.VILLAGER_SPAWN_EGG || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), MINION_EGG_TYPE_KEY);
        String raw = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return MinionType.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Serializes an array of item stacks into a Base64 string.
     *
     * @param logger the logger used to report serialization failures
     * @param items  the item stacks to serialize
     * @return the Base64-encoded serialized form, or {@code null} on failure
     */
    public static String serialize(Logger logger, ItemStack[] items) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataStream = new BukkitObjectOutputStream(byteStream)) {

            dataStream.writeInt(items.length);
            for (ItemStack item : items) {
                dataStream.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException exception) {
            logger.severe("[EcoCore] Failed to serialize item stacks: " + exception.getMessage());
            return null;
        }
    }

    /**
     * Deserializes a Base64 string produced by {@link #serialize} back
     * into an item stack array.
     *
     * @param logger the logger used to report deserialization failures
     * @param data   the Base64-encoded serialized form, may be {@code null}/blank
     * @param size   the expected array size if {@code data} is empty
     * @return the deserialized item stacks, or an all-{@code null} array of {@code size} on failure/empty input
     */
    public static ItemStack[] deserialize(Logger logger, String data, int size) {
        if (data == null || data.isBlank()) {
            return new ItemStack[size];
        }

        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream dataStream = new BukkitObjectInputStream(byteStream)) {

            int length = dataStream.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) dataStream.readObject();
            }
            return items;
        } catch (IOException | ClassNotFoundException exception) {
            logger.severe("[EcoCore] Failed to deserialize item stacks: " + exception.getMessage());
            return new ItemStack[size];
        }
    }

    /**
     * Attempts to fit a full stack into the first available slot(s) of
     * an inventory array (simple first-fit).
     *
     * @param storage the inventory array to insert into, modified in place
     * @param toAdd   the item stack to insert
     * @return the leftover amount that didn't fit, 0 if everything fit
     */
    public static int addToStorage(ItemStack[] storage, ItemStack toAdd) {
        int remaining = toAdd.getAmount();
        int maxStackSize = toAdd.getMaxStackSize();

        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack slot = storage[i];
            if (slot != null && slot.isSimilar(toAdd) && slot.getAmount() < maxStackSize) {
                int space = maxStackSize - slot.getAmount();
                int move = Math.min(space, remaining);
                slot.setAmount(slot.getAmount() + move);
                remaining -= move;
            }
        }

        for (int i = 0; i < storage.length && remaining > 0; i++) {
            if (storage[i] == null) {
                int move = Math.min(maxStackSize, remaining);
                ItemStack newStack = toAdd.clone();
                newStack.setAmount(move);
                storage[i] = newStack;
                remaining -= move;
            }
        }

        return remaining;
    }
                    }
