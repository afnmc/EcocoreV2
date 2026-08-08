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
import org.bukkit.block.data.Ageable;
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

    /**
     * How many of a Farmer's or Lumberjack's storage slots (starting
     * from index 0) are reserved as "Storage A" - seeds/saplings kept
     * for replanting - versus "Storage B" (the rest of the array),
     * which holds normal harvested output. See
     * {@link #zoneBStart(MinionType)}. Collectors are only ever
     * allowed to pull from Storage B (see {@link #pullFromNearbyMinions})
     * and the Connector Network only ever routes out of Storage B
     * (see {@link #pushAlongConnections}), so a Farmer's seed stash
     * never accidentally gets vacuumed away by its own logistics.
     */
    private static final int ZONE_A_SLOTS = 4;

    /** Crop block -> the seed/planting material that grows it, used by {@link #handleFarmCycle}. */
    private static final Map<Material, Material> CROP_SEEDS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART
    );

    /** Log block -> the sapling that regrows it, used by the Lumberjack's auto-replant in {@link #handleBlockBreak}. */
    private static final Map<Material, Material> LOG_SAPLINGS = Map.ofEntries(
            Map.entry(Material.OAK_LOG, Material.OAK_SAPLING),
            Map.entry(Material.BIRCH_LOG, Material.BIRCH_SAPLING),
            Map.entry(Material.SPRUCE_LOG, Material.SPRUCE_SAPLING),
            Map.entry(Material.JUNGLE_LOG, Material.JUNGLE_SAPLING),
            Map.entry(Material.ACACIA_LOG, Material.ACACIA_SAPLING),
            Map.entry(Material.DARK_OAK_LOG, Material.DARK_OAK_SAPLING),
            Map.entry(Material.MANGROVE_LOG, Material.MANGROVE_PROPAGULE),
            Map.entry(Material.CHERRY_LOG, Material.CHERRY_SAPLING)
    );

    private final Logger logger;
    private final MinionsConfig minionsConfig;
    private final MinionFuelManager fuelManager;
    private final MinionTargetSelector targetSelector;
    private final MinionAnimationHandler animationHandler;
    private final SellManager sellManager;
    private final EconomyEngine economyEngine;
    private final MinionConnectorManager connectorManager;

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
     * @param connectorManager shared Connector Network manager, used by Collectors/Minion Chests/Sell Minions to route items along drawn connections
     */
    public MinionAiController(Logger logger, MinionsConfig minionsConfig, MinionFuelManager fuelManager,
                               MinionTargetSelector targetSelector, MinionAnimationHandler animationHandler,
                               SellManager sellManager, EconomyEngine economyEngine,
                               MinionConnectorManager connectorManager) {
        this.logger = logger;
        this.minionsConfig = minionsConfig;
        this.fuelManager = fuelManager;
        this.targetSelector = targetSelector;
        this.animationHandler = animationHandler;
        this.sellManager = sellManager;
        this.economyEngine = economyEngine;
        this.connectorManager = connectorManager;
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
            case INTERNAL_SELL -> handleInternalSell(data, entity, storage);
            case CHEST_BUFFER -> handleChestBuffer(data, entity, storage);
            case FARM_CYCLE -> handleFarmCycle(data, entity, storage);
            case PASSIVE -> {
                // No action; storage-only minion type.
            }
        }

        // Universal Connector Network step: ANY minion type with outgoing
        // connections drawn from it (see MinionConnectorManager) pushes
        // whatever's left in its storage along every one of those
        // connections after doing its own type-specific work above. This
        // is what lets a Miner connect straight to a Sell Minion, or a
        // Collector branch into two Minion Chests, without needing every
        // producer type to special-case connector-pushing itself.
        pushAlongConnections(data, storage);
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
            boolean isBaseOfTrunk = data.getType() == MinionType.LUMBERJACK
                    && LOG_SAPLINGS.containsKey(block.getType())
                    && !LOG_SAPLINGS.containsKey(block.getRelative(BlockFace.DOWN).getType());
            Material sapling = LOG_SAPLINGS.get(block.getType());

            block.setType(Material.AIR);
            animationHandler.playActionEffect(block.getLocation());

            if (isBaseOfTrunk && sapling != null && consumeOneInRange(storage, 0, ZONE_A_SLOTS, sapling)) {
                // Sapling/propagule items share the same Material constant as
                // their placed-block form in modern versions, so this is a direct set.
                block.setType(sapling);
            }
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
     * Whether a minion type keeps a dedicated seed/sapling reserve
     * (see {@link #ZONE_A_SLOTS}) separate from its normal output.
     *
     * @param type the minion type to check
     * @return {@code true} for Farmer and Lumberjack
     */
    private boolean hasZonedStorage(MinionType type) {
        return type == MinionType.FARMER || type == MinionType.LUMBERJACK;
    }

    /**
     * The first storage index that's fair game for a Collector to
     * pull from, or for the Connector Network to route out of - index
     * 0 for every ordinary minion, or {@link #ZONE_A_SLOTS} for a
     * Farmer/Lumberjack whose first few slots are its protected seed/
     * sapling reserve.
     *
     * @param type the source minion's type
     * @return the first index outside that minion's protected zone
     */
    private int zoneBStart(MinionType type) {
        return hasZonedStorage(type) ? ZONE_A_SLOTS : 0;
    }

    /**
     * Farmer behavior (also covers what used to be the separate
     * Planter and Harvester types): each tick, prefers harvesting a
     * fully-grown crop over planting a new one.
     * <ol>
     *   <li>Scans for a mature crop/produce block in radius. A crop
     *       counts as mature only when its actual growth stage is
     *       maxed out ({@link Ageable#getAge()} ==
     *       {@link Ageable#getMaximumAge()}) - an immature crop is
     *       never touched, no matter how long it's been in radius.</li>
     *   <li>If found: harvests it into Storage B, then immediately
     *       replants by consuming one matching seed from Storage A -
     *       if no seed is available, the plot is simply left empty
     *       (still counts as a successful harvest either way).</li>
     *   <li>Otherwise, scans for empty farmland (or, for nether wart,
     *       empty soul sand) in radius and plants a seed from Storage
     *       A onto it, if one's available.</li>
     * </ol>
     *
     * @param data    the Farmer's persistent data
     * @param entity  the Farmer's visual entity
     * @param storage the Farmer's live storage array (Storage A: indices
     *                0-{@link #ZONE_A_SLOTS}-1, Storage B: the rest), mutated in place
     */
    private void handleFarmCycle(MinionData data, Entity entity, ItemStack[] storage) {
        Location origin = entity.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        int radius = effectiveRadius(data);

        Block matureCrop = findMatureCrop(origin, radius, world);
        if (matureCrop != null) {
            Material harvestResult = CROP_SEEDS.containsKey(matureCrop.getType())
                    ? switch (matureCrop.getType()) {
                        case WHEAT -> Material.WHEAT;
                        case CARROTS -> Material.CARROT;
                        case POTATOES -> Material.POTATO;
                        case BEETROOTS -> Material.BEETROOT;
                        case NETHER_WART -> Material.NETHER_WART;
                        default -> matureCrop.getType();
                    }
                    : (matureCrop.getType() == Material.PUMPKIN ? Material.PUMPKIN : Material.MELON_SLICE);

            int leftover = addToStorageRange(storage, ZONE_A_SLOTS, storage.length, new ItemStack(harvestResult, 1));
            if (leftover > 0) {
                return; // Storage B full - leave the crop standing rather than losing the harvest.
            }

            animationHandler.playActionEffect(matureCrop.getLocation());

            Material seed = CROP_SEEDS.get(matureCrop.getType());
            if (seed != null) {
                // Ageable crop: consuming a seed from Storage A instantly replants it (age reset to 0).
                if (consumeOneInRange(storage, 0, ZONE_A_SLOTS, seed)) {
                    Ageable ageable = (Ageable) matureCrop.getBlockData();
                    ageable.setAge(0);
                    matureCrop.setBlockData(ageable);
                } else {
                    matureCrop.setType(Material.AIR);
                }
            } else {
                // Pumpkin/melon: the stem regrows a new one on its own, nothing to replant.
                matureCrop.setType(Material.AIR);
            }
            return;
        }

        Block emptyPlot = findEmptyFarmPlot(origin, radius, world);
        if (emptyPlot == null) {
            return;
        }

        Material seedToPlant = emptyPlot.getType() == Material.SOUL_SAND ? Material.NETHER_WART : Material.WHEAT_SEEDS;
        // Only Wheat Seeds are auto-selected for open farmland (matching vanilla's own
        // default crop); Carrots/Potatoes/Beetroot Seeds already in Storage A still get
        // planted wherever a Farmer harvests and replants one of THEIR OWN plots above -
        // this just decides what to do with a plot nobody has claimed yet.
        if (!consumeOneInRange(storage, 0, ZONE_A_SLOTS, seedToPlant)) {
            return;
        }

        if (seedToPlant == Material.NETHER_WART) {
            emptyPlot.getRelative(BlockFace.UP).setType(Material.NETHER_WART);
        } else {
            emptyPlot.getRelative(BlockFace.UP).setType(cropForSeed(seedToPlant));
        }
    }

    private Material cropForSeed(Material seed) {
        return switch (seed) {
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            default -> Material.WHEAT;
        };
    }

    private Block findMatureCrop(Location origin, int radius, World world) {
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();

        Block nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -Math.min(radius, 4); dy <= Math.min(radius, 4); dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    Material type = block.getType();

                    boolean matches;
                    if (CROP_SEEDS.containsKey(type)) {
                        matches = block.getBlockData() instanceof Ageable ageable
                                && ageable.getAge() >= ageable.getMaximumAge();
                    } else {
                        matches = type == Material.PUMPKIN || type == Material.MELON;
                    }
                    if (!matches) {
                        continue;
                    }

                    double distanceSq = block.getLocation().distanceSquared(origin);
                    if (distanceSq < nearestDistanceSq) {
                        nearestDistanceSq = distanceSq;
                        nearest = block;
                    }
                }
            }
        }
        return nearest;
    }

    private Block findEmptyFarmPlot(Location origin, int radius, World world) {
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();

        Block nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -Math.min(radius, 4); dy <= Math.min(radius, 4); dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    boolean isFarmland = block.getType() == Material.FARMLAND
                            && block.getRelative(BlockFace.UP).getType() == Material.AIR;
                    boolean isSoulSand = block.getType() == Material.SOUL_SAND
                            && block.getRelative(BlockFace.UP).getType() == Material.AIR;
                    if (!isFarmland && !isSoulSand) {
                        continue;
                    }

                    double distanceSq = block.getLocation().distanceSquared(origin);
                    if (distanceSq < nearestDistanceSq) {
                        nearestDistanceSq = distanceSq;
                        nearest = block;
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Like {@link ItemUtils#addToStorage}, but confined to a slot
     * range - used to keep output confined to Storage B without
     * ever touching a Farmer's/Lumberjack's Storage A reserve.
     *
     * @param storage    the storage array
     * @param fromIndex  first index in range (inclusive)
     * @param toIndex    last index in range (exclusive)
     * @param toAdd      the item to add
     * @return the leftover amount that didn't fit
     */
    private int addToStorageRange(ItemStack[] storage, int fromIndex, int toIndex, ItemStack toAdd) {
        int remaining = toAdd.getAmount();
        int maxStackSize = toAdd.getMaxStackSize();

        for (int i = fromIndex; i < toIndex && i < storage.length && remaining > 0; i++) {
            ItemStack slot = storage[i];
            if (slot != null && slot.isSimilar(toAdd) && slot.getAmount() < maxStackSize) {
                int space = maxStackSize - slot.getAmount();
                int move = Math.min(space, remaining);
                slot.setAmount(slot.getAmount() + move);
                remaining -= move;
            }
        }
        for (int i = fromIndex; i < toIndex && i < storage.length && remaining > 0; i++) {
            if (storage[i] == null) {
                int move = Math.min(maxStackSize, remaining);
                ItemStack newStack = toAdd.clone();
                newStack.setAmount(move);
                storage[i] = newStack;
                remaining -= move;
            }
        }
        return remaining;
    }

    /**
     * Like {@link #consumeOne}, but confined to a slot range - used
     * to only ever consume seeds/saplings from Storage A.
     *
     * @param storage   the storage array
     * @param fromIndex first index in range (inclusive)
     * @param toIndex   last index in range (exclusive)
     * @param material  the material to consume one of
     * @return {@code true} if one was found and consumed
     */
    private boolean consumeOneInRange(ItemStack[] storage, int fromIndex, int toIndex, Material material) {
        for (int i = fromIndex; i < toIndex && i < storage.length; i++) {
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

    /**
     * Collector behavior:
     * <ol>
     *   <li>Sucks up dropped item entities within radius (unchanged from before).</li>
     *   <li>Pulls items directly out of every OTHER minion belonging to
     *       the same owner within radius - so placing a collector next
     *       to a cluster of miners/farmers centralizes their loot
     *       automatically instead of each one filling up independently.</li>
     *   <li>If another Collector, a Minion Chest, or a Sell Minion
     *       belonging to the same owner is directly adjacent, pushes
     *       into it - this is what lets Collectors chain into each
     *       other physically (Collector -> Collector -> Chest) without
     *       needing an explicit Connector Network link for the simple
     *       side-by-side case.</li>
     *   <li>Pushes whatever's left after that into any nearby Seller
     *       Minion belonging to the same owner within radius.</li>
     * </ol>
     * Anything still left in storage after all of the above flows out
     * through the Connector Network in {@link #tick} (see
     * {@link #pushAlongConnections}), so a Collector no longer ever
     * pushes directly into a real chest block - it always goes through
     * a Minion Chest (adjacent or connected) first.
     */
    private void handleItemPickup(MinionData data, Entity entity, ItemStack[] storage) {
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
        pushToAdjacentMinion(data, entity, storage);
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
            int startIndex = zoneBStart(other.type());
            for (int i = startIndex; i < otherStorage.length; i++) {
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

    /**
     * Pushes an owner-matching minion's storage into another owned
     * minion (Collector, Minion Chest, or Sell Minion) standing
     * directly next to it, checked as an ENTITY within 1.5 blocks
     * rather than a block face - minions are entities, not blocks, so
     * a block-adjacency check would never find one. This is the
     * "just stand them next to each other" chaining path; the
     * Connector Network (see {@link #pushAlongConnections}) is the
     * general path for non-adjacent or branching routes.
     *
     * @param data    the pushing minion's persistent data
     * @param entity  the pushing minion's visual entity
     * @param storage the pushing minion's live storage array, mutated in place
     */
    private void pushToAdjacentMinion(MinionData data, Entity entity, ItemStack[] storage) {
        if (minionManager == null) {
            return;
        }

        for (Entity nearby : entity.getNearbyEntities(1.5, 1.5, 1.5)) {
            Long otherId = minionManager.resolveMinionId(nearby);
            if (otherId == null || otherId == data.getId()) {
                continue;
            }

            MinionData otherData = minionManager.getMinion(otherId);
            if (otherData == null || !otherData.getOwnerUuid().equals(data.getOwnerUuid())) {
                continue;
            }
            if (otherData.getType() != MinionType.MINION_CHEST
                    && otherData.getType() != MinionType.COLLECTOR
                    && otherData.getType() != MinionType.SELLER) {
                continue;
            }

            ItemStack[] otherStorage = minionManager.getMinionStorage(otherId);
            if (otherStorage == null) {
                continue;
            }

            for (int i = 0; i < storage.length; i++) {
                ItemStack slot = storage[i];
                if (slot == null) {
                    continue;
                }

                int leftover = ItemUtils.addToStorage(otherStorage, slot);
                if (leftover <= 0) {
                    storage[i] = null;
                } else if (leftover < slot.getAmount()) {
                    slot.setAmount(leftover);
                }
            }
            return; // Only push into the first adjacent match found.
        }
    }

    /**
     * Pushes every outgoing Connector Network destination's storage
     * full of whatever's left in a minion's own storage. Runs for
     * EVERY minion type once per tick (see {@link #tick}), not just
     * Collectors - a Miner with a connection drawn straight to a Sell
     * Minion works exactly the same way as a Collector feeding a
     * Minion Chest. Destinations whose chunk isn't currently loaded
     * are skipped for this tick (their storage array is unavailable
     * until then) rather than dropping the items.
     *
     * @param data    the pushing minion's persistent data
     * @param storage the pushing minion's live storage array, mutated in place
     */
    private void pushAlongConnections(MinionData data, ItemStack[] storage) {
        if (minionManager == null || connectorManager == null) {
            return;
        }

        List<Long> destinations = connectorManager.getOutgoing(data.getId());
        if (destinations.isEmpty()) {
            return;
        }
        int startIndex = zoneBStart(data.getType());

        for (long destinationId : destinations) {
            ItemStack[] destinationStorage = minionManager.getMinionStorage(destinationId);
            if (destinationStorage == null) {
                continue;
            }

            for (int i = startIndex; i < storage.length; i++) {
                ItemStack slot = storage[i];
                if (slot == null) {
                    continue;
                }

                int leftover = ItemUtils.addToStorage(destinationStorage, slot);
                if (leftover <= 0) {
                    storage[i] = null;
                } else if (leftover < slot.getAmount()) {
                    slot.setAmount(leftover);
                }
            }
        }
    }

    /**
     * Pushes whatever the Collector is currently holding into any
     * nearby placed Seller Minion belonging to the same owner, so a
     * Collector sitting near a Seller Minion automatically feeds it
     * for auto-liquidation - mirroring {@link #pullFromNearbyMinions}
     * but in the opposite direction and targeting Seller Minions
     * specifically.
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

    /**
     * Minion Chest behavior: acts as a buffer between a Collector and
     * a real chest. Its own storage gets filled by an adjacent/
     * connected Collector's push (see {@link #pushToAdjacentMinion}/
     * {@link #pushAlongConnections}); every tick it then drains
     * whatever it's holding into an adjacent real chest/barrel/other
     * container block, exactly like the old direct Collector-to-chest
     * push used to work - just one hop later, so the Collector itself
     * never touches a real chest directly anymore.
     *
     * @param data    the Minion Chest's persistent data
     * @param entity  the Minion Chest's visual entity
     * @param storage the Minion Chest's live storage array, mutated in place
     */
    private void handleChestBuffer(MinionData data, Entity entity, ItemStack[] storage) {
        pushToAdjacentContainer(entity, storage);
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

    /**
     * Sell Minion behavior: first pulls in any sellable items sitting
     * in an adjacent real chest/barrel/container block (Method 1:
     * Adjacent Chest Block), then sells everything sellable currently
     * in its own storage - which by then also includes anything
     * routed in via the Connector Network (Method 2, handled
     * generically by {@link #pushAlongConnections} in {@link #tick}).
     *
     * @param data    the Sell Minion's persistent data
     * @param entity  the Sell Minion's visual entity
     * @param storage the Sell Minion's live storage array, mutated in place
     */
    private void handleInternalSell(MinionData data, Entity entity, ItemStack[] storage) {
        pullSellableFromAdjacentContainer(entity, storage);

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

    /**
     * Pulls sellable items out of an adjacent real chest/barrel/other
     * container block straight into a Sell Minion's own storage,
     * leaving anything not currently sellable untouched in the chest -
     * this is the Sell Minion's "Method 1: Adjacent Chest Block"
     * input, letting a player just place a normal chest next to a
     * Sell Minion and have it liquidated automatically.
     *
     * @param entity  the Sell Minion's visual entity
     * @param storage the Sell Minion's live storage array, mutated in place
     */
    private void pullSellableFromAdjacentContainer(Entity entity, ItemStack[] storage) {
        Block center = entity.getLocation().getBlock();

        for (BlockFace face : ADJACENT_FACES) {
            Block adjacent = center.getRelative(face);
            if (!(adjacent.getState() instanceof Container container)) {
                continue;
            }

            Inventory sourceInventory = container.getInventory();
            for (int i = 0; i < sourceInventory.getSize(); i++) {
                ItemStack slot = sourceInventory.getItem(i);
                if (slot == null || slot.getType().isAir() || !sellManager.isSellable(slot)) {
                    continue;
                }

                int leftover = ItemUtils.addToStorage(storage, slot.clone());
                if (leftover <= 0) {
                    sourceInventory.setItem(i, null);
                } else if (leftover < slot.getAmount()) {
                    slot.setAmount(leftover);
                    sourceInventory.setItem(i, slot);
                }
            }
            return; // Only pull from the first container found.
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
