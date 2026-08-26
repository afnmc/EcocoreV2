// FILE: src/main/java/io/azthera/ecocore/model/MissionType.java
package io.azthera.ecocore.model;

/**
 * The kind of in-game action a job mission tracks (Revisi 18). A
 * mission's storage key encodes both this type and an optional
 * target (material/entity name, blank for types with no specific
 * target) as {@code "TYPE:TARGET"}, so no database schema change is
 * needed on top of the existing free-form {@code mission_key} column.
 */
public enum MissionType {

    BREAK_BLOCK,
    PLACE_BLOCK,
    HARVEST_CROP,
    PLANT_CROP,
    CHOP_TREE,
    COLLECT_ITEM,
    CRAFT_ITEM,
    SMELT_ITEM,
    FISH_ITEM,
    KILL_MOB,
    BREED_ANIMAL,
    SHEAR_SHEEP,
    MILK_COW,
    COLLECT_EGG,
    MINE_ORE,
    MINE_STONE,
    TRADE,
    ENCHANT,
    BREW_POTION,
    USE_MINION,
    DELIVER_ITEM,
    EARN_MONEY,
    SELL_ITEM,
    UPGRADE_MINION,
    BUY_SHOP,
    SELL_SHOP,
    NIGHT_MARKET_ACTION;

    public static MissionType fromConfigKey(String key) {
        if (key == null) {
            return null;
        }
        for (MissionType type : values()) {
            if (type.name().equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}