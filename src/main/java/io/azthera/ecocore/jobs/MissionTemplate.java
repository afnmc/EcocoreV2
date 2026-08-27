package io.azthera.ecocore.jobs;

import io.azthera.ecocore.model.MissionType;

public record MissionTemplate(MissionType type, String target, int minAmount, int maxAmount,
                                double moneyPerUnit, double xpPerUnit, double weight,
                                boolean dailyEligible, boolean weeklyEligible) {

    public String toStorageKey() {
        return type.name() + ":" + (target != null ? target : "");
    }

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

    public record DecodedKey(MissionType type, String target) {
    }
}
