package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Performs no active work; exists to provide extra shared storage
 * capacity to a player's minion setup.
 */
public final class StorageMinion extends AbstractMinionHandler {

    public StorageMinion() {
        super(MinionType.STORAGE, MinionProcessingType.PASSIVE, 0,
                noMaterials(), noEntities(), null, null, noCatches());
    }
}