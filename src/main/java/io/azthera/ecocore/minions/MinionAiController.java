package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.minions.types.QuarryMinion;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.sell.SellManager;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Runs a single tick of AI behavior for one placed minion: energy
 * regen, fuel consumption, target selection, and executing whichever
 * {@link io.azthera.ecocore.minions.types.MinionProcessingType}
 * behavior its handler declares.
 *
 * <p>Minions are STATIONARY - they never move from their placement
 * spot. Every action here acts directly on whatever's within the
 * minion's configured radius, the same way a hopper or dispenser
 * works on its immediate surroundings without walking anywhere.
 *
 * <p>Called once per configured tick interval per minion by
 * {@code MinionManager}, which owns the minion's visual entity and
 * live storage array.
 */
public final class MinionAiController {

    private static final double ENERGY_REGEN_PER_TICK_FRACTION = 0.02;
    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final Logger logger;
    private final MinionsConfig minionsConfig;
    private final MinionFuelManager fuelManager;
    private final MinionTargetSelector targetSelector;
    private final MinionAnimationHandler animationHandler;
    private final SellManager sellManager;
    private final EconomyEngine economyEngine;

    /**
     * Late-bound after construction to break the constructor cycle
     * with {@link MinionManager} (which itself owns this controller).
     * Set once from {@code EcoCorePlugin.setupMinions()} right after
     * both objects are built. Used by the Collector's "pull from
     * nearby minions" behavior.
     */
    private MinionManager minionManager;

    /**
     * Creates the AI controller.
     *
     * @param logger           plugin logger
     * @param minionsConfig    resolved minions.yml configuration
     * @param fuelManager      fuel consumption/refuel manager
     * @param targetSelector   block/entity target selection
     * @param animationHandler visual/audio feedback helper
     * @param sellManager      used by INTERNAL_SELL minions to liquidate storage
     * @param economyEngine    used to pay the owner for INTERNAL_SELL results
     */
    public MinionAiController(Logger logger, MinionsConfig minionsConfig, MinionFuelManager fuelManager,
                               MinionTargetSelector targetSelector, MinionAnimationHandler animationHandler,
                               SellManager sellManager, EconomyEngine economyEngine) {
        this.logger = logger;
        this.minionsConfig = minionsConfig;
        this.fuelManager = fuelManager;
        this.targetSelector = targetSelector;
        this.animationHandler = animationHandler;
        this.sellManager = sellManager;
        this.economyEngine = economyEngine;
    }

    /**
     * Wires in the minion manager after both objects exist. Must be
     * called once before {@link #tick} is ever invoked, or the
     * Collector's cross-minion pulling silently no-ops.
     *
     * @param minionManager the shared minion manager
     */
    public void setMinionManager(MinionManager minionManager) {
        this.minionManager = minionManager;
    }

    /**
     * Runs one tick of behavior for a single minion.
     *
     * @param data    the minion's persistent data, mutated in place
     * @param handler the minion's type handler
     * @param entity  the minion's visual entity in the world (its fixed location)
     * @param storage the minion's live storage contents array, mutated in place
     */
    public void tick(MinionData data, MinionHandler handler, Entity entity, ItemStack[] storage) {
        regenerateEnergy(data);
        fuelManager.consumeTick(data);

        if (!fuelManager.isFueled(data)) {
            fuelManager.tryRefuel(data, storage);
            if (!fuelManager.isFueled(data)) {
                if (ThreadLocalRandom.current().nextInt(200) == 0) {
                    animationHandler.playOutOfFuelEffect(entity.getLocation());
                }
                return;
            }
        }

        int energyCost = handler.getEnergyCostPerAction();
        if (energyCost > 0 && !data.consumeEnergy(energyCost)) {
            return;
        }

        switch (handler.getProcessingType()) {
            case BLOCK_BREAK -> handleBlockBreak(data, handler, entity, storage);
            case BLOCK_PLACE -> handleBlockPlace(handler, storage);
            case ENTITY_INTERACT -> handleEntityInteract(data, handler, entity, storage);
            case ITEM_PICKUP -> handleItemPickup(data, entity, storage);
            case FISHING -> handleFishing(handler, entity, storage);
            case INTERNAL_SMELT -> handleInternalConversion(handler, storage);
            case INTERNAL_SELL -> handleInternalSell(data, storage);
            case PASSIVE -> {
                // No action; storage-only minion type.
            }
        }
    }

    private void regenerateEnergy(MinionData data) {
        int baseEnergy = minionsConfig.getBaseEnergy();
        if (data.getEnergy() >= baseEnergy) {
            return;
        }
        int regen = Math.max(1, (int) Math.round(baseEnergy * ENERGY_REGEN_PER_TICK_FRACTION));
        data.refillEnergy(Math.min(regen, baseEnergy - data.getEnergy()));
    }

    private int effectiveRadius(MinionData data) {
        if (data.getType() == MinionType.QUARRY) {
            return (int) Math.round(data.getRadius() * QuarryMinion.RADIUS_MULTIPLIER);
        }
        return data.getRadius();
    }

    /**
     * Breaks a matching block within radius INSTANTLY. The minion
     * never moves - it simply reaches into its surroundings, so
     * there's no walk-time delay and no line-of-sight requirement
     * (which would otherwise make buried ore unreachable by definition).
     *
     * <p>Uses "arena mode" (nearest match anywhere in radius) unless
     * the minion has "facing mode" enabled, in which case it instead
     * mines in a straight line in the direction its entity is facing
     * - see {@link MinionManager#isFacingModeEnabled(long)}.
     */
    private void handleBlockBreak(MinionData data, MinionHandler handler, Entity entity, ItemStack[] storage) {
        Location origin = entity.getLocation();
        int radius = effectiveRadius(data);

        boolean facingMode = minionManager != null && minionManager.isFacingModeEnabled(data.getId());
        Optional<Block> targetBlock = facingMode
                ? targetSelector.findBlockInFacingDirection(origin, yawToBlockFace(origin.getYaw()), radius, handler)
                : targetSelector.findNearestBlock(origin, radius, handler);

        if (targetBlock.isEmpty()) {
            return;
        }

        Block block = targetBlock.get();

        if (!canBreakAt(data, block)) {
            return;
        }

        Material resultMaterial = handler.resultFor(block.getType());
        int leftover = ItemUtils.addToStorage(storage, new ItemStack(resultMaterial, 1));
        if (leftover == 0) {
            block.setType(Material.AIR);
            animationHandler.playActionEffect(block.getLocation());
        }
    }

    /**
     * Converts a yaw angle to the horizontal direction it most
     * closely faces, used to pick a mining direction for "facing
     * mode" minions. Follows standard Minecraft yaw convention
     * (0=south, 90=west, 180=north, 270=east).
     *
     * @param yaw the entity's yaw in degrees
     * @return the closest cardinal {@link BlockFace}
     */
    private static BlockFace yawToBlockFace(float yaw) {
        float normalizedYaw = yaw % 360;
        if (normalizedYaw < 0) {
            normalizedYaw += 360;
        }

        if (normalizedYaw >= 315 || normalizedYaw < 45) {
            return BlockFace.SOUTH;
        }
        if (normalizedYaw < 135) {
            return BlockFace.WEST;
        }
        if (normalizedYaw < 225) {
            return BlockFace.NORTH;
        }
        return BlockFace.EAST;
    }

    /**
     * Checks whether the minion's owner is currently allowed to break
     * a block at the given location, by firing a synthetic
     * {@link BlockBreakEvent} with the owner as the breaker so that
     * WorldGuard, GriefPrevention, Towny, or any other protection
     * plugin listening to that event gets a chance to cancel it -
     * exactly as it would for a real player-caused break. This lets
     * minions respect land claims (only breaking within land the
     * owner is actually permitted to build in) without EcoCore
     * needing a hard dependency on any specific protection plugin.
     *
     * <p><b>Limitation:</b> this check can only run while the
     * minion's owner is online, since {@code BlockBreakEvent}
     * requires a live {@link Player} instance. If the owner is
     * offline, this defaults to allowing the break, matching
     * EcoCore's previous (unconditional) behavior, rather than
     * silently starving an offline owner's minions.
     *
     * @param data  the minion's persistent data (used to resolve the owner)
     * @param block the block the minion wants to break
     * @return {@code true} if the break should proceed
     */
    private boolean canBreakAt(MinionData data, Block block) {
        Player owner = Bukkit.getPlayer(data.getOwnerUuid());
        if (owner == null) {
            return true;
        }

        BlockBreakEvent event = new BlockBreakEvent(block, owner);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    private void handleBlockPlace(MinionHandler handler, ItemStack[] storage) {
        Material seed = handler.getSeedItem();
        Material plantResult = handler.getPlantResult();
        if (seed == null || plantResult == null) {
            return;
        }
        if (!consumeOne(storage, seed)) {
            return;
        }
        ItemUtils.addToStorage(storage, new ItemStack(plantResult, 1));
    }

    private void handleEntityInteract(MinionData data, MinionHandler handler, Entity entity, ItemStack[] storage) {
        Location origin = entity.getLocation();
        Optional<LivingEntity> targetEntity = targetSelector.findBestEntity(origin, effectiveRadius(data), handler);
        if (targetEntity.isEmpty()) {
            return;
        }

        LivingEntity target = targetEntity.get();

        Material seed = handler.getSeedItem();
        if (seed != null && !consumeOne(storage, seed)) {
            return;
        }

        Material result = handler.resultForEntity(target.getType());
        if (result == null) {
            return;
        }

        int leftover = ItemUtils.addToStorage(storage, new ItemStack(result, 1));
        if (leftover == 0) {
            animationHandler.playActionEffect(target.getLocation());
            if (data.getType() == MinionType.MOB_KILLER && target.isValid()) {
                target.damage(target.getHealth() + 1, entity);
            }
        }
    }

    /**
     * Collector behavior, four parts:
     * <ol>
     *   <li>Sucks up dropped item entities within radius (unchanged from before).</li>
     *   <li>Pulls items directly out of every OTHER minion belonging to
     *       the same owner within radius - so placing a collector next
     *       to a cluster of miners/farmers centralizes their loot
     *       automatically instead of each one filling up independently.</li>
     *   <li>If a hopper, chest, barrel, or any other container block is
     *       directly adjacent (one of the 6 neighboring blocks) to the
     *       collector, pushes whatever it's currently holding into it.</li>
     *   <li>Pushes whatever's left after that into any nearby Seller
     *       Minion belonging to the same owner, so a Collector feeding
     *       a Seller Minion auto-liquidates instead of just piling up
     *       storage with nowhere to go.</li>
     * </ol>
     */
    private void handleItemPickup(MinionData data, Entity entity, ItemStack[] storage) {
        World world = entity.getWorld();
        int radius = effectiveRadius(data);

        entity.getNearbyEntities(radius, radius, radius).stream()
                .filter(nearby -> nearby instanceof org.bukkit.entity.Item)
                .map(nearby -> (org.bukkit.entity.Item) nearby)
                .findFirst()
                .ifPresent(itemEntity -> {
                    ItemStack drop = itemEntity.getItemStack();
                    int leftover = ItemUtils.addToStorage(storage, drop);
                    if (leftover == 0) {
                        itemEntity.remove();
                        animationHandler.playActionEffect(itemEntity.getLocation());
                    } else {
                        drop.setAmount(leftover);
                        itemEntity.setItemStack(drop);
                    }
                });

        pullFromNearbyMinions(data, entity, storage, radius);
        pushToAdjacentContainer(entity, storage);
        pushToNearbySellerMinions(data, entity, storage, radius);
    }

    private void pullFromNearbyMinions(MinionData data, Entity entity, ItemStack[] storage, int radius) {
        if (minionManager == null) {
            return;
        }

        List<MinionManager.NearbyMinionView> nearby = minionManager.getNearbyOwnedMinions(
                entity.getLocation(), radius, data.getOwnerUuid(), data.getId());

        for (MinionManager.NearbyMinionView other : nearby) {
            ItemStack[] otherStorage = other.storage();
            for (int i = 0; i < otherStorage.length; i++) {
                ItemStack slot = otherStorage[i];
                if (slot == null) {
                    continue;
                }

                int leftover = ItemUtils.addToStorage(storage, slot);
                if (leftover <= 0) {
                    otherStorage[i] = null;
                } else if (leftover < slot.getAmount()) {
                    slot.setAmount(leftover);
                }
            }
        }
    }

    private void pushToAdjacentContainer(Entity entity, ItemStack[] storage) {
        Block center = entity.getLocation().getBlock();

        for (BlockFace face : ADJACENT_FACES) {
            Block adjacent = center.getRelative(face);
            if (!(adjacent.getState() instanceof Container container)) {
                continue;
            }

            Inventory targetInventory = container.getInventory();
            for (int i = 0; i < storage.length; i++) {
                ItemStack slot = storage[i];
                if (slot == null) {
                    continue;
                }

                Map<Integer, ItemStack> leftover = targetInventory.addItem(slot.clone());
                if (leftover.isEmpty()) {
                    storage[i] = null;
                } else {
                    storage[i] = leftover.values().iterator().next();
                }
            }
            return; // Only push into the first container found.
        }
    }

    /**
     * Pushes whatever the Collector is currently holding into any
     * nearby placed Seller Minion belonging to the same owner, so a
     * Collector sitting near a Seller Minion automatically feeds it
     * for auto-liquidation - mirroring {@link #pullFromNearbyMinions}
     * but in the opposite direction and targeting Seller Minions
     * specifically. Runs AFTER {@link #pushToAdjacentContainer}, so a
     * directly-adjacent physical chest still takes priority; only
     * whatever's left afterward flows to a Seller Minion.
     *
     * @param data    the Collector's persistent data
     * @param entity  the Collector's visual entity
     * @param storage the Collector's live storage array, mutated in place
     * @param radius  the Collector's effective work radius
     */
    private void pushToNearbySellerMinions(MinionData data, Entity entity, ItemStack[] storage, int radius) {
        if (minionManager == null) {
            return;
        }

        List<MinionManager.NearbyMinionView> nearby = minionManager.getNearbyOwnedMinions(
                entity.getLocation(), radius, data.getOwnerUuid(), data.getId());

        for (MinionManager.NearbyMinionView other : nearby) {
            if (other.type() != MinionType.SELLER) {
                continue;
            }

            ItemStack[] sellerStorage = other.storage();
            for (int i = 0; i < storage.length; i++) {
                ItemStack slot = storage[i];
                if (slot == null) {
                    continue;
                }

                int leftover = ItemUtils.addToStorage(sellerStorage, slot);
                if (leftover <= 0) {
                    storage[i] = null;
                } else if (leftover < slot.getAmount()) {
                    slot.setAmount(leftover);
                }
            }
        }
    }

    private void handleFishing(MinionHandler handler, Entity entity, ItemStack[] storage) {
        if (handler.getPossibleCatches().isEmpty()) {
            return;
        }
        Random random = ThreadLocalRandom.current();
        Material catchMaterial = handler.getPossibleCatches().get(random.nextInt(handler.getPossibleCatches().size()));

        int leftover = ItemUtils.addToStorage(storage, new ItemStack(catchMaterial, 1));
        if (leftover == 0) {
            animationHandler.playActionEffect(entity.getLocation());
        }
    }

    private void handleInternalConversion(MinionHandler handler, ItemStack[] storage) {
        for (Material input : handler.getTargetMaterials()) {
            if (consumeOne(storage, input)) {
                Material output = handler.resultFor(input);
                ItemUtils.addToStorage(storage, new ItemStack(output, 1));
                return;
            }
        }
    }

    private void handleInternalSell(MinionData data, ItemStack[] storage) {
        double totalPayout = 0.0;

        for (int i = 0; i < storage.length; i++) {
            ItemStack slot = storage[i];
            if (slot == null || !sellManager.isSellable(slot)) {
                continue;
            }

            var catalogItem = sellManager.resolveCatalogItem(slot);
            if (catalogItem == null) {
                continue;
            }

            double unitPrice = sellManager.getProfitCalculator().computeUnitSellPrice(catalogItem);
            totalPayout += unitPrice * slot.getAmount();
            storage[i] = null;
        }

        if (totalPayout > 0) {
            economyEngine.deposit(data.getOwnerUuid(), totalPayout, TransactionLogger.REASON_MINION_AUTOSELL);
        }
    }

    private boolean consumeOne(ItemStack[] storage, Material material) {
        for (int i = 0; i < storage.length; i++) {
            ItemStack slot = storage[i];
            if (slot != null && slot.getType() == material) {
                slot.setAmount(slot.getAmount() - 1);
                if (slot.getAmount() <= 0) {
                    storage[i] = null;
                }
                return true;
            }
        }
        return false;
    }
        }
