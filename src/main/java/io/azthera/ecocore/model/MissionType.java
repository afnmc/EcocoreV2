package io.azthera.ecocore.model;

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
