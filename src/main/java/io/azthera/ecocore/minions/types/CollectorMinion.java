// FILE: src/main/java/io/azthera/ecocore/minions/types/CollectorMinion.java
package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Picks up ground item drops within its work radius (Revisi 9: no
 * longer relays items between other minions - that's now the
 * Connector Network's job entirely).
 */
public final class CollectorMinion extends AbstractMinionHandler {

    public CollectorMinion() {
        super(MinionType.COLLECTOR, MinionProcessingType.ITEM_COLLECT, noEntities(), null, null);
    }
}