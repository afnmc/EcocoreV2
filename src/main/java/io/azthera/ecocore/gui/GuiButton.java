package io.azthera.ecocore.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * A reusable clickable icon binding an {@link ItemStack} to a click
 * handler, used by GUI screens that build their button layout
 * dynamically rather than hardcoding slot-to-action switches.
 */
public final class GuiButton {

    private final ItemStack icon;
    private final Consumer<InventoryClickEvent> onClick;

    /**
     * Creates a button.
     *
     * @param icon    the item stack displayed for this button
     * @param onClick callback invoked when the button is clicked, may be {@code null} for a decorative/no-op button
     */
    public GuiButton(ItemStack icon, Consumer<InventoryClickEvent> onClick) {
        this.icon = icon;
        this.onClick = onClick;
    }

    public ItemStack getIcon() {
        return icon;
    }

    /**
     * Invokes this button's click handler, if any.
     *
     * @param event the triggering inventory click event
     */
    public void click(InventoryClickEvent event) {
        if (onClick != null) {
            onClick.accept(event);
        }
    }
}