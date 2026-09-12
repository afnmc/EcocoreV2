package io.azthera.ecocore.jobs;

import io.azthera.ecocore.model.MissionType;

/**
 * Turns a mission's {@link MissionType}, optional target material/entity,
 * and rolled target amount into a single clear Indonesian instruction
 * line, e.g. "Tambang Diamond Ore sebanyak 12x" instead of the old
 * generic "Lakukan aksi kerja sebagai Miner sebanyak 12x" text that
 * didn't actually say what to do (Revisi 20).
 */
public final class MissionDescriber {

    private MissionDescriber() {
        // Utility class, not instantiable.
    }

    /**
     * Builds the clear instruction line for a mission.
     *
     * @param type   the mission's action type, may be {@code null} for legacy/unrecognized missions
     * @param target the specific material/entity/item involved, may be {@code null}/blank for a generic mission
     * @param amount the rolled target amount for this mission instance
     * @return a human-readable instruction, e.g. "Tambang Diamond Ore sebanyak 12x"
     */
    public static String describe(MissionType type, String target, int amount) {
        if (type == null) {
            return "Lakukan aksi kerja yang relevan sebanyak " + amount + "x";
        }

        String pretty = prettify(target);
        String amountSuffix = " sebanyak " + amount + "x";

        return switch (type) {
            case BREAK_BLOCK -> "Hancurkan " + fallback(pretty, "blok apa saja") + amountSuffix;
            case PLACE_BLOCK -> "Pasang " + fallback(pretty, "blok apa saja") + amountSuffix;
            case HARVEST_CROP -> "Panen " + fallback(pretty, "tanaman") + amountSuffix;
            case PLANT_CROP -> "Tanam " + fallback(pretty, "bibit") + amountSuffix;
            case CHOP_TREE -> "Tebang " + fallback(pretty, "pohon") + amountSuffix;
            case COLLECT_ITEM -> "Kumpulkan " + fallback(pretty, "item") + amountSuffix;
            case CRAFT_ITEM -> "Craft " + fallback(pretty, "item apa saja") + amountSuffix;
            case SMELT_ITEM -> "Smelt " + fallback(pretty, "item di furnace") + amountSuffix;
            case COOK_ITEM -> "Masak " + fallback(pretty, "item di furnace") + amountSuffix;
            case FISH_ITEM -> "Mancing " + fallback(pretty, "ikan apa saja") + amountSuffix;
            case KILL_MOB -> "Bunuh " + fallback(pretty, "mob") + amountSuffix;
            case BREED_ANIMAL -> "Kawinkan " + fallback(pretty, "hewan") + amountSuffix;
            case SHEAR_SHEEP -> "Cukur domba" + amountSuffix;
            case MILK_COW -> "Perah sapi pakai bucket" + amountSuffix;
            case COLLECT_EGG -> "Kumpulkan telur ayam" + amountSuffix;
            case MINE_ORE -> "Tambang " + fallback(pretty, "ore") + amountSuffix;
            case MINE_STONE -> "Gali " + fallback(pretty, "batu/tanah") + amountSuffix;
            case TRADE -> "Trading sama villager" + amountSuffix;
            case ENCHANT -> "Enchant item di enchanting table" + amountSuffix;
            case ANVIL_USE -> "Gunakan anvil pada " + fallback(pretty, "item") + amountSuffix;
            case BREW_POTION -> "Brewing potion" + amountSuffix;
            case DELIVER_ITEM -> "Antar/deliver " + fallback(pretty, "item") + amountSuffix;
            case EARN_MONEY -> "Kumpulkan total pendapatan sebesar $" + amount;
            case SELL_ITEM -> "Jual " + fallback(pretty, "item") + " ke shop" + amountSuffix;
            case BUY_SHOP -> "Beli " + fallback(pretty, "item") + " dari shop" + amountSuffix;
            case SELL_SHOP -> "Jual " + fallback(pretty, "item") + " lewat shop" + amountSuffix;
            case NIGHT_MARKET_ACTION -> "Transaksi di Night Market" + amountSuffix;
        };
    }

    private static String fallback(String pretty, String generic) {
        return (pretty == null || pretty.isBlank()) ? generic : pretty;
    }

    /**
     * Turns a raw material/entity config key like {@code DIAMOND_ORE}
     * into a display-friendly form like {@code Diamond Ore}.
     *
     * @param raw the raw material/entity name, may be {@code null}/blank
     * @return the prettified name, or {@code null} if {@code raw} was blank
     */
    public static String prettify(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? null : builder.toString();
    }
}
