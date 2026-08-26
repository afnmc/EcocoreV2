// FILE: src/main/java/io/azthera/ecocore/model/MinionStorage.java
package io.azthera.ecocore.model;

import org.bukkit.inventory.ItemStack;

/**
 * A single 54-slot storage page belonging to a minion. Minions start
 * with exactly one page (index 0) and may unlock additional pages
 * (up to {@code minions.yml global.max-storage-pages}, default 10)
 * via {@code MinionUpgradeManager}. Each page is a full independent
 * 54-slot inventory, not a slice of a larger array - this keeps the
 * existing zoned-storage convention (Zone A = seed/input slots,
 * Zone B = output slots) intact within page 0 for types that use it
 * (Farmer, Lumberjack, Smelter), while pages 1+ are always plain
 * uniform output overflow.
 */
public final class MinionStorage {

    public static final int SLOTS_PER_PAGE = 54;

    private final int pageIndex;
    private final ItemStack[] contents;

    /**
     * Creates a storage page.
     *
     * @param pageIndex the zero-based page index (0 is always the
     *                   first/primary page)
     * @param contents   the page's slot contents, must be exactly
     *                   {@link #SLOTS_PER_PAGE} long
     */
    public MinionStorage(int pageIndex, ItemStack[] contents) {
        if (contents.length != SLOTS_PER_PAGE) {
            throw new IllegalArgumentException(
                    "MinionStorage page must have exactly " + SLOTS_PER_PAGE + " slots");
        }
        this.pageIndex = pageIndex;
        this.contents = contents;
    }

    /**
     * Creates a new, empty storage page.
     *
     * @param pageIndex the zero-based page index
     * @return a fresh page with all slots empty
     */
    public static MinionStorage empty(int pageIndex) {
        return new MinionStorage(pageIndex, new ItemStack[SLOTS_PER_PAGE]);
    }

    public int getPageIndex() {
        return pageIndex;
    }

    public ItemStack[] getContents() {
        return contents;
    }

    public ItemStack getSlot(int index) {
        return contents[index];
    }

    public void setSlot(int index, ItemStack stack) {
        contents[index] = stack;
    }

    /**
     * Whether this page has room for at least one more item (either
     * an empty slot, or an existing similar stack under max size).
     *
     * @return {@code true} if this page is not completely full
     */
    public boolean hasSpaceFor(ItemStack toAdd) {
        int maxStackSize = toAdd.getMaxStackSize();
        for (ItemStack slot : contents) {
            if (slot == null) {
                return true;
            }
            if (slot.isSimilar(toAdd) && slot.getAmount() ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether every slot in this page is completely full (either
     * occupied by a max-size stack or a non-stackable item).
     *
     * @return {@code true} if this page has zero remaining capacity
     */
    public boolean isCompletelyFull() {
        for (ItemStack slot : contents) {
            if (slot == null) {
                return false;
            }
            if (slot.getAmount() .getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }
}