package io.azthera.ecocore.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

/**
 * Shared helpers for working with {@link ItemStack}s across EcoCore:
 * safe material lookup, quick named-item construction, and
 * Base64 serialization of item arrays for storage in SQLite text columns.
 */
public final class ItemUtils {

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
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore.stream()
                        .map(line -> org.bukkit.ChatColor.translateAlternateColorCodes('&', line))
                        .toList());
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Serializes an array of item stacks (a minion's or container's
     * inventory contents) into a Base64 string suitable for storing in
     * a SQLite text column. {@code null} entries are preserved as empty slots.
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
     * @param data   the Base64-encoded serialized form, may be {@code null}/blank for an empty result
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
     * an inventory array (simple first-fit, no partial-stack merging
     * across multiple slots beyond direct same-material top-ups).
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