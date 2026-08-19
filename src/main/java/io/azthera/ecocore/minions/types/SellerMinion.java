package io.azthera.ecocore.minions.types;

import io.azthera.ecocore.model.MinionType;

/**
 * Automatically sells whatever sellable items are already in its
 * storage directly to its owner's balance, using EcoCore's live
 * shop prices. Has no fixed target table - the controller resolves
 * sellability per storage item via {@code SellManager}.
 */
public final class SellerMinion extends AbstractMinionHandler {

    public SellerMinion() {
        super(MinionType.SELLER, MinionProcessingType.INTERNAL_SELL, 1,
                noMaterials(), noEntities(), null, null, noCatches());
    }
}