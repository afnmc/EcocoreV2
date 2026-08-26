package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Buffer between a Collector and a normal chest: receives items
 * pushed into it (adjacent placement, or a Connector Network link
 * from a Collector) and drains its storage into an adjacent real
 * chest/barrel/container block every tick. Has no target
 * materials/entities of its own - all of its behavior is the
 * generic {@link MinionProcessingType#CHEST_BUFFER} drain handled by
 * {@code MinionAiController}.
 */
public final class MinionChestMinion extends AbstractMinionHandler {

    public MinionChestMinion() {
        super(MinionType.MINION_CHEST, MinionProcessingType.CHEST_BUFFER, 0,
                noMaterials(), noEntities(), null, null, noCatches());
    }
}
