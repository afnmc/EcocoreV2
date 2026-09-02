package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Manages minion fuel: consuming configured fuel items from a
 * minion's own storage OR directly from a player's hand (via
 * right-click interaction) to keep {@link MinionData#getFuelTicksRemaining()}
 * topped up. Fuel gates whether a minion is powered on at all;
 * energy (managed separately by {@code MinionAiController}) gates
 * how many actions it can perform while powered.
 */
public final class MinionFuelManager {

    private static final Map<Material, Integer> FUEL_VALUES = Map.of(
            Material.COAL, 1600,
            Material.COAL_BLOCK, 14400,
            Material.LAVA_BUCKET, 20000
    );

    private final MinionsConfig minionsConfig;

    /**
     * Creates a fuel manager.
     *
     * @param minionsConfig resolved minions.yml configuration (configured fuel type names)
     */
    public MinionFuelManager(MinionsConfig minionsConfig) {
        this.minionsConfig = minionsConfig;
    }

    /**
     * Whether the minion currently has enough fuel to act.
     *
     * @param data the minion's persistent data
     * @return {@code true} if fuel ticks remain
     */
    public boolean isFueled(MinionData data) {
        return data.getFuelTicksRemaining() > 0;
    }

    /**
     * Decrements a minion's remaining fuel by one tick, called once
     * per scheduler pass regardless of whether the minion actually
     * acted this pass.
     *
     * @param data the minion's persistent data
     */
    public void consumeTick(MinionData data) {
        if (data.getFuelTicksRemaining() > 0) {
            data.setFuelTicksRemaining(data.getFuelTicksRemaining() - 1);
        }
    }

    /**
     * Attempts to refuel a minion by consuming one configured fuel
     * item from its storage, if it isn't already fueled. Called
     * automatically by {@code MinionAiController} every tick a
     * minion runs out of fuel.
     *
     * @param data    the minion's persistent data
     * @param storage the minion's storage contents, modified in place if refueling occurs
     * @return {@code true} if a fuel item was consumed and fuel was added
     */
    public boolean tryRefuel(MinionData data, ItemStack[] storage) {
        if (isFueled(data)) {
            return false;
        }

        for (int i = 0; i < storage.length; i++) {
            ItemStack slot = storage[i];
            if (slot == null) {
                continue;
            }

            Integer fuelValue = FUEL_VALUES.get(slot.getType());
            if (fuelValue == null || !minionsConfig.getFuelTypes().contains(slot.getType().name())) {
                continue;
            }

            slot.setAmount(slot.getAmount() - 1);
            if (slot.getAmount() <= 0) {
                storage[i] = null;
            }

            data.setFuelTicksRemaining(data.getFuelTicksRemaining() + fuelValue);
            return true;
        }

        return false;
    }

    /**
     * Multi-page overload of {@link #tryRefuel(MinionData, ItemStack[])}
     * for Revisi 11's storage-page model. Only ever consumes from
     * page 0's Zone A slots (the same slots used for seed/input on
     * dual-zone types), since fuel is conceptually an input resource.
     *
     * @param data the minion's persistent data
     * @param pages the minion's live storage pages
     * @return {@code true} if a fuel item was consumed and fuel was added
     */
    public boolean tryConsumeFuelFromStorage(MinionData data, java.util.List<io.azthera.ecocore.model.MinionStorage> pages) {
        if (isFueled(data) || pages.isEmpty()) {
            return false;
        }
        io.azthera.ecocore.model.MinionStorage firstPage = pages.get(0);
        ItemStack[] contents = firstPage.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack slot = contents[i];
            if (slot == null) {
                continue;
            }
            Integer fuelValue = FUEL_VALUES.get(slot.getType());
            if (fuelValue == null || !minionsConfig.getFuelTypes().contains(slot.getType().name())) {
                continue;
            }
            slot.setAmount(slot.getAmount() - 1);
            if (slot.getAmount() <= 0) {
                firstPage.setSlot(i, null);
            }
            data.setFuelTicksRemaining(data.getFuelTicksRemaining() + fuelValue);
            return true;
        }
        return false;
    }

    /**
     * Refuels a minion directly from a fuel item, used when a player
     * right-clicks the minion's entity in the world while holding
     * coal/coal block/lava bucket. Consumes one unit from the given
     * stack. Unlike {@link #tryRefuel}, this works even if the
     * minion already has fuel remaining (tops it up further), since
     * it's an intentional player action rather than an automatic check.
     *
     * @param data     the minion's persistent data
     * @param handItem the item currently in the player's hand, may be {@code null}
     * @return {@code true} if one fuel item was consumed and fuel was added
     */
    public boolean refuelFromHand(MinionData data, ItemStack handItem) {
        if (handItem == null || handItem.getType().isAir()) {
            return false;
        }

        Integer fuelValue = FUEL_VALUES.get(handItem.getType());
        if (fuelValue == null || !minionsConfig.getFuelTypes().contains(handItem.getType().name())) {
            return false;
        }

        handItem.setAmount(handItem.getAmount() - 1);
        data.setFuelTicksRemaining(data.getFuelTicksRemaining() + fuelValue);
        return true;
    }

    /**
     * Whether the given material is configured as valid minion fuel.
     *
     * @param material the material to check
     * @return {@code true} if it's a recognized, enabled fuel type
     */
    public boolean isFuelItem(Material material) {
        return FUEL_VALUES.containsKey(material) && minionsConfig.getFuelTypes().contains(material.name());
    }
}