package io.azthera.ecocore.model;

import org.bukkit.inventory.ItemStack;

public final class MinionStorage {

    public static final int SLOTS_PER_PAGE = 54;

    private final int pageIndex;
    private final ItemStack[] contents;

    public MinionStorage(int pageIndex, ItemStack[] contents) {
        if (contents.length != SLOTS_PER_PAGE) {
            throw new IllegalArgumentException(
                    "MinionStorage page must have exactly " + SLOTS_PER_PAGE + " slots");
        }
        this.pageIndex = pageIndex;
        this.contents = contents;
    }

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

    public boolean hasSpaceFor(ItemStack toAdd) {
        int maxStackSize = toAdd.getMaxStackSize();
        for (ItemStack slot : contents) {
            if (slot == null) {
                return true;
            }
            if (slot.isSimilar(toAdd) && slot.getAmount() < maxStackSize) {
                return true;
            }
        }
        return false;
    }

    public boolean isCompletelyFull() {
        for (ItemStack slot : contents) {
            if (slot == null) {
                return false;
            }
            if (slot.getAmount() < slot.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }
}
