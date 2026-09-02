package io.azthera.ecocore.shop;

import io.azthera.ecocore.model.ShopItemRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Performs case-insensitive, multi-field search over a shop catalog.
 *
 * <p>Every item is matched against three normalized fields: its raw
 * config id (e.g. {@code raw_iron}), its Bukkit material name (e.g.
 * {@code RAW_IRON}), and a human-readable display name derived from
 * the id (e.g. {@code Raw Iron}) - the same text players see on the
 * item's icon. A query matches if every one of its words is found in
 * at least one of those fields, so multi-word queries like "raw iron"
 * or out-of-order queries like "iron raw" both work, not just a
 * single exact substring.
 */
public final class ShopSearchEngine {

    /**
     * Searches the given items for matches against a free-text query.
     *
     * @param items the catalog (or category subset) to search within
     * @param query the search text, may contain spaces/underscores
     * @return matching items, in their original relative order
     */
    public List<ShopItemRecord> search(Collection<ShopItemRecord> items, String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(items);
        }

        String[] queryWords = normalize(query).split("\\s+");
        List<ShopItemRecord> results = new ArrayList<>();
        for (ShopItemRecord item : items) {
            if (matches(item, queryWords)) {
                results.add(item);
            }
        }
        return results;
    }

    private boolean matches(ShopItemRecord item, String[] queryWords) {
        String haystack = normalize(item.getId())
                + " " + normalize(item.getMaterial())
                + " " + normalize(displayName(item));

        for (String word : queryWords) {
            if (!word.isBlank() && !haystack.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Derives the human-readable display name shown to players for an
     * item (title-cased, spaces instead of underscores) from its id,
     * e.g. {@code raw_iron} -> {@code Raw Iron}. This mirrors what
     * screens like {@code ShopSearchGui}/{@code ShopItemPreviewGui}
     * show as the item's name, so searching by what's on-screen works.
     *
     * @param item the item to derive a display name for
     * @return the derived display name
     */
    public String displayName(ShopItemRecord item) {
        String raw = item.getId() != null ? item.getId() : item.getMaterial();
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] words = raw.replace("_", " ").trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.toString();
    }

    /**
     * Normalizes text for matching: lowercase, underscores collapsed
     * to spaces, and anything that isn't a letter/digit/space stripped
     * out, so punctuation differences never block an otherwise-good match.
     *
     * @param input the raw text to normalize
     * @return the normalized text
     */
    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        String replaced = input.toLowerCase(Locale.ROOT).replace("_", " ");
        StringBuilder builder = new StringBuilder(replaced.length());
        for (char c : replaced.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == ' ') {
                builder.append(c);
            }
        }
        return builder.toString().trim().replaceAll("\\s+", " ");
    }
}