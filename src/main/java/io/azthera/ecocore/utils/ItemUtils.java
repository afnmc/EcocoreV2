package io.azthera.ecocore.utils;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.enchantments.Enchantment;
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

    /**
     * Builds a "10.00 -&gt; 15.00 (+50.0%)" style lore line describing
     * an item's live price move since the last AI pricing cycle
     * (Revisi 20), or {@code null} if there's no meaningful change yet
     * to show (brand new item, or change below 0.1%).
     *
     * @param item the shop item to describe
     * @return the formatted lore line, or {@code null} if there's nothing to show
     */
    public static String priceChangeLoreLine(io.azthera.ecocore.model.ShopItemRecord item) {
        double percent = item.getPriceChangePercent();
        if (Math.abs(percent) < 0.1) {
            return null;
        }
        String color = percent > 0 ? "§a" : "§c";
        String arrow = percent > 0 ? "▲" : "▼";
        return "§7" + String.format("%.2f", item.getPreviousPrice()) + " §8-> " + color
                + String.format("%.2f", item.getCurrentPrice()) + " §8(" + color + arrow + " "
                + String.format("%+.1f", percent) + "%§8)";
    }

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
        giveOrDrop(player, new ItemStack(material, 1), amount);
    }

    /** Gives a fully configured item (including PotionMeta/EnchantmentStorageMeta) to a player. */
    public static void giveOrDrop(Player player, ItemStack template, int amount) {
        int remaining = amount;
        int maxStackSize = Math.max(1, template.getMaxStackSize());

        while (remaining > 0) {
            int stackAmount = Math.min(maxStackSize, remaining);
            ItemStack stack = template.clone();
            stack.setAmount(stackAmount);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack leftoverStack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverStack);
            }
            remaining -= stackAmount;
        }
    }

    /** Builds the actual ItemStack represented by a shop catalog record, including potion and enchantment variants. */
    public static ItemStack createShopItem(io.azthera.ecocore.model.ShopItemRecord item, int amount) {
        Material material = safeMaterial(item.getMaterial());
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        String type = item.getVariantType();
        String value = item.getVariantValue();
        if (type == null || type.isBlank() || value == null || value.isBlank()) {
            return stack;
        }
        try {
            if (type.equalsIgnoreCase("potion")) {
                if (stack.getItemMeta() instanceof PotionMeta meta) {
                    org.bukkit.potion.PotionType potion = org.bukkit.potion.PotionType.valueOf(value.toUpperCase());
                    meta.setBasePotionType(potion);
                    stack.setItemMeta(meta);
                }
            } else if (type.equalsIgnoreCase("enchantment")) {
                if (stack.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta meta) {
                    String[] parts = value.split(":", 2);
                    String enchantName = parts[0].toUpperCase();
                    int level = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    Enchantment enchantment = resolveEnchantment(enchantName);
                    if (enchantment != null) {
                        meta.addStoredEnchant(enchantment, level, true);
                        stack.setItemMeta(meta);
                    }
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warning("[EcoCore] Failed to apply shop variant '" + type + ":" + value + "' for " + item.getId());
        }
        return stack;
    }

    private static Enchantment resolveEnchantment(String name) {
        return switch (name) {
            case "WATER_WORKER" -> Enchantment.getByName("AQUA_AFFINITY");
            case "DAMAGE_ALL" -> Enchantment.getByName("SHARPNESS");
            case "DAMAGE_UNDEAD" -> Enchantment.getByName("SMITE");
            case "DAMAGE_ARTHROPODS" -> Enchantment.getByName("BANE_OF_ARTHROPODS");
            case "DIG_SPEED" -> Enchantment.getByName("EFFICIENCY");
            case "DURABILITY" -> Enchantment.getByName("UNBREAKING");
            case "ARROW_DAMAGE" -> Enchantment.getByName("POWER");
            case "ARROW_FIRE" -> Enchantment.getByName("FLAME");
            case "ARROW_INFINITE" -> Enchantment.getByName("INFINITY");
            case "ARROW_KNOCKBACK" -> Enchantment.getByName("PUNCH");
            case "LOOT_BONUS_MOBS" -> Enchantment.getByName("LOOTING");
            case "LOOT_BONUS_BLOCKS" -> Enchantment.getByName("FORTUNE");
            case "PROTECTION_ENVIRONMENTAL" -> Enchantment.getByName("PROTECTION");
            case "PROTECTION_FIRE" -> Enchantment.getByName("FIRE_PROTECTION");
            case "PROTECTION_FALL" -> Enchantment.getByName("FEATHER_FALLING");
            case "PROTECTION_EXPLOSIONS" -> Enchantment.getByName("BLAST_PROTECTION");
            case "PROTECTION_PROJECTILE" -> Enchantment.getByName("PROJECTILE_PROTECTION");
            case "SWEEPING_EDGE" -> Enchantment.getByName("SWEEPING_EDGE");
            default -> Enchantment.getByName(name);
        };
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
}
