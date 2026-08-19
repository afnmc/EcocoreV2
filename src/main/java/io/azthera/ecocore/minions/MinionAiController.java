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

public final class MinionAiController {

    private static final double ENERGY_REGEN_PER_TICK_FRACTION = 0.02;
    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private static final int ZONE_A_SLOTS = 4;

    private static final Map<Material, Material> CROP_SEEDS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.NETHER_WART, Material.NETHER_WART
    );

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

    private MinionManager minionManager;

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

    public void setMinionManager(MinionManager minionManager) {
        this.minionManager = minionManager;
    }

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
                block.setType(sapling);
            }
        }
    }

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

    private boolean hasZonedStorage(MinionType type) {
        return type == MinionType.FARMER || type == MinionType.LUMBERJACK;
    }

    private int zoneBStart(MinionType type) {
        return hasZonedStorage(type) ? ZONE_A_SLOTS : 0;
    }

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
                return;
            }

            animationHandler.playActionEffect(matureCrop.getLocation());

            Material seed = CROP_SEEDS.get(matureCrop.getType());
            if (seed != null) {
                if (consumeOneInRange(storage, 0, ZONE_A_SLOTS, seed)) {
                    Ageable ageable = (Ageable) matureCrop.getBlockData();
                    ageable.setAge(0);
                    matureCrop.setBlockData(ageable);
                } else {
                    matureCrop.setType(Material.AIR);
                }
            } else {
                matureCrop.setType(Material.AIR);
            }
            return;
        }

        Block emptyPlot = findEmptyFarmPlot(origin, radius, world);
        if (emptyPlot == null) {
            return;
        }

        Material seedToPlant = emptyPlot.getType() == Material.SOUL_SAND ? Material.NETHER_WART : Material.WHEAT_SEEDS;
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
            return;
        }
    }

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
            return;
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
            return;
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
