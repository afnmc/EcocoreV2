package io.azthera.ecocore.gui;

import java.util.Collections;
import java.util.List;

/**
 * Generic pagination helper shared by every list-style GUI screen
 * (shop categories, search results, jobs, minions). Slices a backing
 * list into fixed-size pages and tracks the current page index.
 *
 * @param <T> the element type being paginated
 */
public final class GuiPage<T> {

    private final List<T> allItems;
    private final int pageSize;
    private int currentPage;

    /**
     * Creates a paginator over the given items.
     *
     * @param allItems the full backing list, not modified by this class
     * @param pageSize maximum items per page, coerced to at least 1
     */
    public GuiPage(List<T> allItems, int pageSize) {
        this.allItems = allItems;
        this.pageSize = Math.max(1, pageSize);
        this.currentPage = 0;
    }

    /**
     * Returns the slice of items belonging to the current page.
     *
     * @return the current page's items, possibly empty
     */
    public List<T> getCurrentPageItems() {
        int from = currentPage * pageSize;
        if (from >= allItems.size()) {
            return Collections.emptyList();
        }
        int to = Math.min(from + pageSize, allItems.size());
        return allItems.subList(from, to);
    }

    /**
     * Returns the total number of pages, always at least 1.
     *
     * @return the total page count
     */
    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil(allItems.size() / (double) pageSize));
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public boolean hasNextPage() {
        return currentPage < getTotalPages() - 1;
    }

    public boolean hasPreviousPage() {
        return currentPage > 0;
    }

    public void nextPage() {
        if (hasNextPage()) {
            currentPage++;
        }
    }

    public void previousPage() {
        if (hasPreviousPage()) {
            currentPage--;
        }
    }

    /**
     * Jumps directly to a page index, clamped to the valid range.
     *
     * @param page the target page index (0-based)
     */
    public void setPage(int page) {
        currentPage = Math.max(0, Math.min(page, getTotalPages() - 1));
    }
}