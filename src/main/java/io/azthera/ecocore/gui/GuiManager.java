package io.azthera.ecocore.gui;

import io.azthera.ecocore.config.GuiConfig;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which {@link AbstractGui} screen each player currently has
 * open and routes Bukkit inventory events to it. The actual Bukkit
 * listener registration lives in {@code listener.InventoryClickListener},
 * which simply delegates to {@link #routeClick(InventoryClickEvent)},
 * {@link #routeDrag(InventoryDragEvent)}, and
 * {@link #routeClose(InventoryCloseEvent)}.
 *
 * <p>Also provides shared icon/sound helpers backed by {@code gui.yml}
 * so individual screens don't each duplicate material-lookup and
 * sound-lookup boilerplate.
 */
public final class GuiManager {

    private final GuiConfig guiConfig;
    private final Map<UUID, AbstractGui> openGuis = new ConcurrentHashMap<>();

    /**
     * Creates a GUI manager.
     *
     * @param guiConfig resolved gui.yml configuration
     */
    public GuiManager(GuiConfig guiConfig) {
        this.guiConfig = guiConfig;
    }

    /**
     * Registers the currently open screen for a player, overwriting
     * any previous registration. Screens should call this just before
     * {@link AbstractGui#open()}.
     *
     * @param player the viewing player
     * @param gui the screen now open for them
     */
    public void register(Player player, AbstractGui gui) {
        openGuis.put(player.getUniqueId(), gui);
    }

    /**
     * Removes a player's open-screen registration without notifying
     * the screen, used when a new screen is about to replace the old
     * one and no close-side-effects are desired.
     *
     * @param player the player to unregister
     */
    public void unregister(Player player) {
        openGuis.remove(player.getUniqueId());
    }

    /**
     * Returns the screen currently open for a player, if any.
     *
     * @param player the player to check
     * @return the open screen, or {@code null} if none is tracked
     */
    public AbstractGui getOpenGui(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    public boolean hasOpenGui(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }

    /**
     * Routes a Bukkit inventory click to the clicking player's
     * currently registered screen, if any.
     *
     * @param event the inventory click event to route
     */
    public void routeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        AbstractGui gui = openGuis.get(player.getUniqueId());

        if (gui != null) {
            gui.handleClick(event);
        }
    }

    /**
     * Routes a Bukkit inventory drag to the dragging player's
     * currently registered screen. Cancels the drag outright unless
     * EVERY raw slot it touches is a free-drag slot for that screen
     * (see {@link AbstractGui#isFreeDragSlot(int)}), so a drag that
     * partially overlaps a protected control area (icons, buttons,
     * etc.) never leaks an item into it.
     *
     * @param event the inventory drag event to route
     */
    public void routeDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        AbstractGui gui = openGuis.get(player.getUniqueId());

        if (gui == null) {
            event.setCancelled(true);
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (!gui.isFreeDragSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Routes a Bukkit inventory close to the closing player's
     * currently registered screen, then clears the registration.
     *
     * <p>Only acts if the inventory that is actually closing still
     * matches the screen currently tracked for this player. During
     * screen-to-screen navigation (e.g. a "Back" button), the new
     * screen is registered and opened <em>while the old screen's
     * inventory is still on-screen</em>; opening the new inventory
     * makes Bukkit implicitly fire a close event for the old one.
     * Without this check, that implicit close would blindly remove
     * whatever is currently mapped — which by then is already the
     * new screen — leaving the player's active screen unregistered
     * and its clicks no longer cancelled by {@code
     * InventoryClickListener}.
     *
     * @param event the inventory close event to route
     */
    public void routeClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        AbstractGui gui = openGuis.get(player.getUniqueId());

        if (gui == null || gui.getInventory() != event.getInventory()) {
            // Not a real close of the tracked screen — most likely an
            // implicit close fired because a new screen just replaced
            // it. Leave the (already-updated) registration alone.
            return;
        }

        openGuis.remove(player.getUniqueId());
        gui.handleClose(event);
    }

    /**
     * Plays a configured UI sound to a player, if sounds are enabled
     * and the given key resolves to a valid sound in {@code gui.yml}.
     *
     * @param player the player to play the sound to
     * @param key the sound key (e.g. "click", "buy", "sell", "error")
     */
    public void playSound(Player player, String key) {
        if (!guiConfig.isSoundsEnabled()) {
            return;
        }

        Sound sound = guiConfig.getSound(key);

        if (sound != null) {
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    /**
     * Builds a shared control icon (next-page, close, back, etc.) from
     * its {@code gui.yml} definition.
     *
     * @param key the button key from gui.yml
     * @param displayName colorized display name to apply to the built icon
     * @return the built item stack, falling back to plain stone if
     *         unconfigured/invalid
     */
    public ItemStack buildButtonIcon(String key, String displayName) {
        GuiConfig.ButtonIcon iconDef = guiConfig.getButtonIcon(key);
        Material material = iconDef != null ? safeMaterial(iconDef.material()) : Material.STONE;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(displayName);

            if (iconDef != null && iconDef.customModelData() != 0) {
                meta.setCustomModelData(iconDef.customModelData());
            }

            stack.setItemMeta(meta);
        }

        return stack;
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return Material.STONE;
        }
    }
    }
