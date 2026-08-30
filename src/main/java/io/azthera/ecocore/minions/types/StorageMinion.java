package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Pure storage minion (bug-fix round addition): the ONLY minion type
 * that unlocks additional whole 54-slot pages (up to 10) via
 * {@code MinionUpgradeManager.UpgradeType.STORAGE_PAGE}. Every other
 * minion type instead has a single fixed page whose usable slot
 * count is upgraded directly. Does no world action (workMode NONE).
 */
public final class StorageMinion extends AbstractMinionHandler {
    public StorageMinion() {
        super(MinionType.STORAGE, MinionProcessingType.NONE, noEntities(), null, null);
    }
}
