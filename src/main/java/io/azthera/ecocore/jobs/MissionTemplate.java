// FILE: src/main/java/io/azthera/ecocore/jobs/MissionTemplate.java
package io.azthera.ecocore.jobs;

import io.azthera.ecocore.model.MissionType;

/**
 * A single configured mission template (Revisi 18): a mission type
 * with an optional specific target (a material or entity name, e.g.
 * "DIAMOND_ORE" for a MINE_ORE mission targeting diamonds
 * specifically, or blank for a type that isn't target-specific like
 * TRADE or EARN_MONEY), a target-amount range, reward scaling, and a
 * selection weight for the random pool draw.
 *
 * @param type the mission's action type
 * @param target the specific material/entity/item this mission targets, or blank if type-only
 * @param minAmount the minimum random target amount to assign
 * @param maxAmount the maximum random target amount to assign
 * @param moneyPerUnit money reward per unit of target amount
 * @param xpPerUnit xp reward per unit of target amount
 * @param weight relative selection weight when picking from the pool
 * @param dailyEligible whether this template can be assigned as a daily mission
 * @param weeklyEligible whether this template can be assigned as a weekly mission
 */
public record MissionTemplate(MissionType type, String target, int minAmount, int maxAmount,
                                double moneyPerUnit, double xpPerUnit, double weight,
                                boolean dailyEligible, boolean weeklyEligible) {

    /**
     * Encodes this template's type+target into the flat storage key
     * format used by the {@code mission_key} database column.
     *
     * @return the encoded key, e.g. "MINE_ORE:DIAMOND_ORE" or "TRADE:"
     */
    public String toStorageKey() {
        return type.name() + ":" + (target != null ? target : "");
    }

    /**
     * Decodes a stored mission key back into its type and target parts.
     *
     * @param storageKey a key previously produced by {@link #toStorageKey()}
     * @return the decoded type/target pair, or {@code null} if the key isn't in the expected format
     */
    public static DecodedKey decode(String storageKey) {
        if (storageKey == null || !storageKey.contains(":")) {
            return null;
        }
        int separatorIndex = storageKey.indexOf(':');
        String typeName = storageKey.substring(0, separatorIndex);
        String target = storageKey.substring(separatorIndex + 1);
        MissionType type = MissionType.fromConfigKey(typeName);
        if (type == null) {
            return null;
        }
        return new DecodedKey(type, target.isBlank() ? null : target);
    }

    /**
     * A decoded mission key's parts.
     *
     * @param type the mission type
     * @param target the specific target, or {@code null} if type-only
     */
    public record DecodedKey(MissionType type, String target) {
    }
}