package io.azthera.ecocore.minions;

import io.azthera.ecocore.claim.ClaimManager;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.minions.types.FishRarityTier;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.minions.types.TreeSpeciesData;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionStorage;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Executes a single work action for a stationary minion each time
 * it's ticked. Never moves, teleports, or pathfinds the minion in any
 * way (Revisi 1) - all targeting is done via
 * {@link MinionTargetSelector} against the minion's fixed placement
 * location, branching between a facing-only directional slab and a
 * full 360-degree arena based on the handler's {@link MinionWorkMode}
 * and, for BOTH-mode types, the player's per-minion toggle.
 */
public final class MinionAiController {

    /** How many of a storage page's 54 slots are reserved as Zone A (seed/input) for dual-zone types. */
    public static final int ZONE_A_SLOTS = 9;

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final Logger logger;
    private final MinionsConfig minionsConfig;
    private final MinionTargetSelector targetSelector;
    private final MinionFuelManager fuelManager;
    private final MinionAnimationHandler animationHandler;
    private final MinionConnectorManager connectorManager;
    private final ClaimManager claimManager;
    private final SellManager sellManager;
    private final EconomyEngine economyEngine;
    private MinionManager minionManager;

    public MinionAiController(Logger logger, MinionsConfig minionsConfig, MinionTargetSelector targetSelector,
                               MinionFuelManager fuelManager, MinionAnimationHandler animationHandler,
                               MinionConnectorManager connectorManager, ClaimManager claimManager,
                               SellManager sellManager, EconomyEngine economyEngine) {
        this.logger = logger;
        this.minionsConfig = minionsConfig;
        this.targetSelector = targetSelector;
        this.fuelManager = fuelManager;
        this.animationHandler = animationHandler;
        this.connectorManager = connectorManager;
        this.claimManager = claimManager;
        this.sellManager = sellManager;
        this.economyEngine = economyEngine;
    }

    public void setMinionManager(MinionManager minionManager) {
        this.minionManager = minionManager;
    }

    public void tick(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        if (handler.getWorkMode() != MinionWorkMode.NONE || handler.getProcessingType() == io.azthera.ecocore.minions.types.MinionProcessingType.SELL_ONLY) {
            if (!fuelManager.isFueled(data)) {
                fuelManager.tryConsumeFuelFromStorage(data, pages);
                if (!fuelManager.isFueled(data)) {
                    animationHandler.playOutOfFuelEffect(entity.getLocation());
                    return;
                }
            }
        }

        pushAlongConnections(data, pages);

        switch (handler.getProcessingType()) {
            case BLOCK_BREAK -> handleBlockBreak(data, handler, entity, pages);
            case FARM_CYCLE -> {
                if (handler.getType() == MinionType.LUMBERJACK) {
                    handleLumberChop(data, handler, entity, pages);
                } else {
                    handleFarmCycle(data, handler, entity, pages);
                }
            }
            case FISHING -> handleFishing(data, handler, entity, pages);
            case ENTITY_INTERACT -> handleEntityInteract(data, handler, entity, pages);
            case INTERNAL_SMELT -> handleInternalSmelt(data, handler, entity, pages);
            case ITEM_COLLECT -> handleItemCollect(data, handler, entity, pages);
            case CHEST_DETECT -> { /* detection happens at placement, nothing per-tick */ }
            case SELL_ONLY -> handleSellOnly(data, entity, pages);
            case NONE -> { /* pure storage type, nothing to do */ }
        }

        if (handler.getWorkMode() != MinionWorkMode.NONE) {
            fuelManager.consumeTick(data);
        }
    }

    private boolean isArenaActive(MinionData data, MinionHandler handler) {
        return switch (handler.getWorkMode()) {
            case ARENA_ONLY -> true;
            case FACING_ONLY -> false;
            case BOTH -> data.isUseArenaMode();
            case NONE -> false;
        };
    }

    private Optional<Block> findTargetBlock(MinionData data, MinionHandler handler, Location origin) {
        if (isArenaActive(data, handler)) {
            return targetSelector.findNearestBlockInArena(origin, data.getRadius(), handler);
        }
        return targetSelector.findNearestBlockInFacingSlab(origin, data.getFacing(), data.getRadius(), handler);
    }

    private Optional<LivingEntity> findTargetEntity(MinionData data, MinionHandler handler, Location origin) {
        if (isArenaActive(data, handler)) {
            return targetSelector.findBestEntityInArena(origin, data.getRadius(), handler);
        }
        return targetSelector.findBestEntityInFacingSlab(origin, data.getFacing(), data.getRadius(), handler);
    }

    private void handleBlockBreak(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        Optional<Block> targetOpt = findTargetBlock(data, handler, entity.getLocation());
        if (targetOpt.isEmpty()) {
            return;
        }
        Block target = targetOpt.get();
        if (!claimManager.isAllowed(data.getOwnerUuid(), target.getLocation())) {
            return;
        }
        Material resultMaterial = handler.resultFor(target.getType());
        ItemStack drop = new ItemStack(resultMaterial != null ? resultMaterial : target.getType());
        if (!hasSpaceInAnyPage(pages, drop)) {
            return;
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        target.setType(Material.AIR);
        addToPagesWithOverflow(pages, drop);
        animationHandler.playActionEffect(target.getLocation());
    }

    private void handleFarmCycle(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        Location origin = entity.getLocation();
        Optional<Block> matureCrop = findMatureCropInRange(data, handler, origin);
        if (matureCrop.isPresent()) {
            harvestCrop(data, handler, matureCrop.get(), pages);
            return;
        }
        plantFromSeedZone(data, handler, origin, pages);
    }

    private Optional<Block> findMatureCropInRange(MinionData data, MinionHandler handler, Location origin) {
        boolean arena = isArenaActive(data, handler);
        int radius = data.getRadius();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        BlockFace facing = data.getFacing();
        Block best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = arena ? -radius : 0; dx <= radius; dx++) {
            for (int dz = arena ? -radius : 0; dz <= radius; dz++) {
                int x = arena ? baseX + dx : baseX + facing.getModX() * dz + (facing.getModX() == 0 ? dx : 0);
                int z = arena ? baseZ + dz : baseZ + facing.getModZ() * dz + (facing.getModZ() == 0 ? dx : 0);
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = origin.getWorld().getBlockAt(x, baseY + dy, z);
                    if (!handler.getTargetMaterials().contains(block.getType())) {
                        continue;
                    }
                    if (!isCropMature(block)) {
                        continue;
                    }
                    double distSq = block.getLocation().distanceSquared(origin);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = block;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private boolean isCropMature(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return block.getType() == Material.PUMPKIN || block.getType() == Material.MELON;
    }

    private void harvestCrop(MinionData data, MinionHandler handler, Block crop, List<MinionStorage> pages) {
        if (!claimManager.isAllowed(data.getOwnerUuid(), crop.getLocation())) {
            return;
        }
        Material produceMaterial = handler.resultFor(crop.getType());
        ItemStack produce = new ItemStack(produceMaterial != null ? produceMaterial : crop.getType());
        if (!hasSpaceInAnyPage(pages, produce)) {
            return;
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        boolean isStemFruit = crop.getType() == Material.PUMPKIN || crop.getType() == Material.MELON;
        if (isStemFruit) {
            crop.setType(Material.AIR);
        } else if (crop.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
            ageable.setAge(0);
            crop.setBlockData(ageable);
        }
        addToPagesWithOverflow(pages, produce);
        animationHandler.playActionEffect(crop.getLocation());
    }

    private void plantFromSeedZone(MinionData data, MinionHandler handler, Location origin, List<MinionStorage> pages) {
        if (handler.getSeedItem() == null) {
            return;
        }
        MinionStorage zoneAPage = pages.get(0);
        int seedSlot = -1;
        for (int i = 0; i < ZONE_A_SLOTS; i++) {
            ItemStack slot = zoneAPage.getSlot(i);
            if (slot != null && slot.getType() == handler.getSeedItem() && slot.getAmount() > 0) {
                seedSlot = i;
                break;
            }
        }
        if (seedSlot == -1) {
            return;
        }
        Optional<Block> plantSpot = findSpacedPlantingSpot(data, handler, origin);
        if (plantSpot.isEmpty()) {
            return;
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        Block spot = plantSpot.get();
        Material cropMaterial = resolveCropBlockForSeed(handler.getSeedItem());
        if (cropMaterial != null) {
            spot.setType(cropMaterial);
        }
        ItemStack seedStack = zoneAPage.getSlot(seedSlot);
        seedStack.setAmount(seedStack.getAmount() - 1);
        if (seedStack.getAmount() <= 0) {
            zoneAPage.setSlot(seedSlot, null);
        }
        animationHandler.playActionEffect(spot.getLocation());
    }

    private Material resolveCropBlockForSeed(Material seed) {
        return switch (seed) {
            case WHEAT_SEEDS -> Material.WHEAT;
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT_SEEDS -> Material.BEETROOTS;
            case PUMPKIN_SEEDS -> Material.PUMPKIN_STEM;
            case MELON_SEEDS -> Material.MELON_STEM;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA_BEANS -> Material.COCOA;
            default -> null;
        };
    }

    private Optional<Block> findSpacedPlantingSpot(MinionData data, MinionHandler handler, Location origin) {
        boolean arena = isArenaActive(data, handler);
        int radius = data.getRadius();
        int spacing = minionsConfig.getCropSpacing();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY() - 1;
        int baseZ = origin.getBlockZ();
        BlockFace facing = data.getFacing();
        for (int dx = arena ? -radius : 0; dx <= radius; dx++) {
            for (int dz = arena ? -radius : 0; dz <= radius; dz++) {
                int x = arena ? baseX + dx : baseX + facing.getModX() * dz + (facing.getModX() == 0 ? dx : 0);
                int z = arena ? baseZ + dz : baseZ + facing.getModZ() * dz + (facing.getModZ() == 0 ? dx : 0);
                Block ground = origin.getWorld().getBlockAt(x, baseY, z);
                Block above = ground.getRelative(BlockFace.UP);
                if (ground.getType() != Material.FARMLAND || !above.getType().isAir()) {
                    continue;
                }
                if (!claimManager.isAllowed(data.getOwnerUuid(), above.getLocation())) {
                    continue;
                }
                if (hasNearbyMatchingCrop(above, handler.getTargetMaterials(), spacing)) {
                    continue;
                }
                return Optional.of(above);
            }
        }
        return Optional.empty();
    }

    private boolean hasNearbyMatchingCrop(Block spot, java.util.Set<Material> cropMaterials, int spacing) {
        if (spacing <= 0) {
            return false;
        }
        for (int dx = -spacing; dx <= spacing; dx++) {
            for (int dz = -spacing; dz <= spacing; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block nearby = spot.getRelative(dx, 0, dz);
                if (cropMaterials.contains(nearby.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleLumberChop(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        Location origin = entity.getLocation();
        Optional<Block> logTarget = findTargetBlock(data, handler, origin);
        if (logTarget.isPresent()) {
            choplog(data, handler, logTarget.get(), pages);
            return;
        }
        plantSaplingFromZoneA(data, handler, origin, pages);
    }

    private void choplog(MinionData data, MinionHandler handler, Block log, List<MinionStorage> pages) {
        if (!claimManager.isAllowed(data.getOwnerUuid(), log.getLocation())) {
            return;
        }
        TreeSpeciesData species = handler.getTreeSpeciesData().get(log.getType());
        java.util.List<ItemStack> drops = new java.util.ArrayList<>();
        drops.add(new ItemStack(log.getType(), 1));
        if (species != null) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (random.nextDouble() < species.appleChance()) {
                drops.add(new ItemStack(Material.APPLE, 1));
            }
            if (random.nextDouble() < species.stickChance()) {
                drops.add(new ItemStack(Material.STICK, 1));
            }
        }
        for (ItemStack drop : drops) {
            if (!hasSpaceInAnyPage(pages, drop)) {
                return;
            }
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        log.setType(Material.AIR);
        for (ItemStack drop : drops) {
            addToPagesWithOverflow(pages, drop);
        }
        if (species != null) {
            Block leaf = findAdjacentLeaf(log, species.leavesMaterial());
            if (leaf != null && ThreadLocalRandom.current().nextDouble() < minionsConfig.getLumberjackSaplingHarvestChance()) {
                ItemStack sapling = new ItemStack(species.saplingMaterial(), 1);
                addToZoneA(pages, sapling);
                leaf.setType(Material.AIR);
            }
        }
        animationHandler.playActionEffect(log.getLocation());
    }

    private Block findAdjacentLeaf(Block log, Material leavesMaterial) {
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block relative = log.getRelative(face);
            if (relative.getType() == leavesMaterial) {
                return relative;
            }
        }
        return null;
    }

    private void plantSaplingFromZoneA(MinionData data, MinionHandler handler, Location origin, List<MinionStorage> pages) {
        MinionStorage zoneAPage = pages.get(0);
        int saplingSlot = -1;
        Material saplingType = null;
        for (int i = 0; i < ZONE_A_SLOTS; i++) {
            ItemStack slot = zoneAPage.getSlot(i);
            if (slot != null && slot.getAmount() > 0) {
                boolean isKnownSapling = handler.getTreeSpeciesData().values().stream()
                        .anyMatch(species -> species.saplingMaterial() == slot.getType());
                if (isKnownSapling) {
                    saplingSlot = i;
                    saplingType = slot.getType();
                    break;
                }
            }
        }
        if (saplingSlot == -1) {
            return;
        }
        TreeSpeciesData species = handler.getTreeSpeciesData().values().stream()
                .filter(candidate -> candidate.saplingMaterial() == saplingType)
                .findFirst().orElse(null);
        if (species == null) {
            return;
        }
        Optional<Block> spot = findSpacedTreeSpot(data, origin, species);
        if (spot.isEmpty()) {
            return;
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        spot.get().setType(saplingType);
        ItemStack saplingStack = zoneAPage.getSlot(saplingSlot);
        saplingStack.setAmount(saplingStack.getAmount() - 1);
        if (saplingStack.getAmount() <= 0) {
            zoneAPage.setSlot(saplingSlot, null);
        }
        animationHandler.playActionEffect(spot.get().getLocation());
    }

    private Optional<Block> findSpacedTreeSpot(MinionData data, Location origin, TreeSpeciesData species) {
        int radius = data.getRadius();
        int spacing = minionsConfig.getTreeSpacingFor(species.logMaterial());
        int canopyClearance = minionsConfig.getCanopyClearanceFor(species.logMaterial());
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY() - 1;
        int baseZ = origin.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block ground = origin.getWorld().getBlockAt(baseX + dx, baseY, baseZ + dz);
                Block above = ground.getRelative(BlockFace.UP);
                if (!isSuitableSoil(ground.getType()) || !above.getType().isAir()) {
                    continue;
                }
                if (!claimManager.isAllowed(data.getOwnerUuid(), above.getLocation())) {
                    continue;
                }
                if (species.require2x2() && !has2x2SpaceAvailable(above)) {
                    continue;
                }
                if (hasNearbySapling(above, species.saplingMaterial(), spacing)) {
                    continue;
                }
                if (!hasCanopyClearance(above, canopyClearance)) {
                    continue;
                }
                return Optional.of(above);
            }
        }
        return Optional.empty();
    }

    private boolean isSuitableSoil(Material material) {
        return material == Material.GRASS_BLOCK || material == Material.DIRT || material == Material.PODZOL
                || material == Material.ROOTED_DIRT || material == Material.MYCELIUM
                || material == Material.MUD || material == Material.MANGROVE_ROOTS;
    }

    private boolean has2x2SpaceAvailable(Block spot) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                Block candidate = spot.getRelative(dx, 0, dz);
                Block ground = candidate.getRelative(BlockFace.DOWN);
                if (!isSuitableSoil(ground.getType()) || !candidate.getType().isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasNearbySapling(Block spot, Material saplingType, int spacing) {
        if (spacing <= 0) {
            return false;
        }
        for (int dx = -spacing; dx <= spacing; dx++) {
            for (int dz = -spacing; dz <= spacing; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block nearby = spot.getRelative(dx, 0, dz);
                if (nearby.getType() == saplingType) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCanopyClearance(Block spot, int canopyClearance) {
        for (int dy = 1; dy <= canopyClearance + 3; dy++) {
            if (!spot.getRelative(0, dy, 0).getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private void addToZoneA(List<MinionStorage> pages, ItemStack item) {
        MinionStorage zoneAPage = pages.get(0);
        for (int i = 0; i < ZONE_A_SLOTS; i++) {
            ItemStack slot = zoneAPage.getSlot(i);
            if (slot == null) {
                zoneAPage.setSlot(i, item);
                return;
            }
            if (slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) {
                slot.setAmount(slot.getAmount() + item.getAmount());
                return;
            }
        }
    }

    private void handleFishing(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        List<FishRarityTier> tiers = handler.getRarityTiers();
        if (tiers.isEmpty()) {
            return;
        }
        Material caught = rollRarityCatch(tiers);
        if (caught == null) {
            return;
        }
        ItemStack drop = new ItemStack(caught, 1);
        if (!hasSpaceInAnyPage(pages, drop)) {
            return;
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        addToPagesWithOverflow(pages, drop);
        animationHandler.playActionEffect(entity.getLocation());
    }

    private Material rollRarityCatch(List<FishRarityTier> tiers) {
        double totalWeight = tiers.stream().mapToDouble(FishRarityTier::weight).sum();
        if (totalWeight <= 0) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (FishRarityTier tier : tiers) {
            cumulative += tier.weight();
            if (roll <= cumulative && !tier.pool().isEmpty()) {
                return tier.pool().get(ThreadLocalRandom.current().nextInt(tier.pool().size()));
            }
        }
        List<Material> lastPool = tiers.get(tiers.size() - 1).pool();
        return lastPool.isEmpty() ? null : lastPool.get(0);
    }

    private void handleEntityInteract(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        Optional<LivingEntity> targetOpt = findTargetEntity(data, handler, entity.getLocation());
        if (targetOpt.isEmpty()) {
            return;
        }
        LivingEntity target = targetOpt.get();
        if (!claimManager.isAllowed(data.getOwnerUuid(), target.getLocation())) {
            return;
        }
        Material resultMaterial = handler.resultForEntity(target.getType());
        if (resultMaterial == null) {
            return;
        }
        ItemStack drop = new ItemStack(resultMaterial, 1);
        if (!hasSpaceInAnyPage(pages, drop)) {
            return;
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        target.damage(1000.0);
        addToPagesWithOverflow(pages, drop);
        animationHandler.playActionEffect(target.getLocation());
    }

    private void handleInternalSmelt(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        MinionStorage inputPage = pages.get(0);
        Map<Material, Material> recipes = handler.getSmeltingRecipes();
        for (int i = 0; i < ZONE_A_SLOTS; i++) {
            ItemStack input = inputPage.getSlot(i);
            if (input == null || input.getAmount() <= 0) {
                continue;
            }
            Material outputMaterial = recipes.get(input.getType());
            if (outputMaterial == null) {
                continue;
            }
            ItemStack output = new ItemStack(outputMaterial, 1);
            if (!hasSpaceInAnyPage(pages, output)) {
                return;
            }
            if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
                return;
            }
            input.setAmount(input.getAmount() - 1);
            if (input.getAmount() <= 0) {
                inputPage.setSlot(i, null);
            }
            addToPagesWithOverflow(pages, output);
            animationHandler.playActionEffect(entity.getLocation());
            return;
        }
    }

    private void handleItemCollect(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        Location origin = entity.getLocation();
        double radiusSq = (double) data.getRadius() * data.getRadius();
        for (org.bukkit.entity.Item groundItem : origin.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class)) {
            if (groundItem.getLocation().distanceSquared(origin) > radiusSq) {
                continue;
            }
            ItemStack stack = groundItem.getItemStack();
            if (!hasSpaceInAnyPage(pages, stack)) {
                continue;
            }
            if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
                return;
            }
            addToPagesWithOverflow(pages, stack);
            groundItem.remove();
            animationHandler.playActionEffect(groundItem.getLocation());
            return;
        }
    }

    /**
     * SELL type: pulls sellable items from an adjacent real chest/
     * container into its own storage (preserving the baseline's
     * behavior), then sells everything sellable currently in its
     * storage directly to the owner's balance via {@link SellManager}.
     */
    private void handleSellOnly(MinionData data, Entity entity, List<MinionStorage> pages) {
        pullSellableFromAdjacentContainer(entity, pages);

        double totalPayout = 0.0;
        for (MinionStorage page : pages) {
            ItemStack[] contents = page.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack slot = contents[i];
                if (slot == null || !sellManager.isSellable(slot)) {
                    continue;
                }
                ShopItemRecord catalogItem = sellManager.resolveCatalogItem(slot);
                if (catalogItem == null) {
                    continue;
                }
                double unitPrice = sellManager.getProfitCalculator().computeUnitSellPrice(catalogItem);
                totalPayout += unitPrice * slot.getAmount();
                page.setSlot(i, null);
            }
        }
        if (totalPayout > 0) {
            economyEngine.deposit(data.getOwnerUuid(), totalPayout, TransactionLogger.REASON_MINION_AUTOSELL);
        }
    }

    private void pullSellableFromAdjacentContainer(Entity entity, List<MinionStorage> pages) {
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
                if (!hasSpaceInAnyPage(pages, slot)) {
                    continue;
                }
                ItemStack moved = slot.clone();
                addToPagesWithOverflow(pages, moved);
                sourceInventory.setItem(i, null);
            }
            return;
        }
    }

    private boolean hasSpaceInAnyPage(List<MinionStorage> pages, ItemStack item) {
        for (MinionStorage page : pages) {
            if (page.hasSpaceFor(item)) {
                return true;
            }
        }
        return false;
    }

    private void addToPagesWithOverflow(List<MinionStorage> pages, ItemStack item) {
        for (MinionStorage page : pages) {
            ItemStack[] contents = page.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack slot = contents[i];
                if (slot != null && slot.isSimilar(item) && slot.getAmount() < slot.getMaxStackSize()) {
                    int space = slot.getMaxStackSize() - slot.getAmount();
                    int toAdd = Math.min(space, item.getAmount());
                    slot.setAmount(slot.getAmount() + toAdd);
                    item.setAmount(item.getAmount() - toAdd);
                    if (item.getAmount() <= 0) {
                        return;
                    }
                }
            }
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] == null) {
                    page.setSlot(i, item.clone());
                    return;
                }
            }
        }
    }

    private void pushAlongConnections(MinionData data, List<MinionStorage> pages) {
        if (minionManager == null) {
            return;
        }
        List<Long> destinations = connectorManager.getOutgoingIds(data.getId());
        if (destinations.isEmpty()) {
            return;
        }
        for (long destinationId : destinations) {
            List<MinionStorage> destinationPages = minionManager.getMinionPages(destinationId);
            if (destinationPages == null) {
                continue;
            }
            transferOneStack(pages, destinationPages);
        }
    }

    private void transferOneStack(List<MinionStorage> fromPages, List<MinionStorage> toPages) {
        for (MinionStorage fromPage : fromPages) {
            ItemStack[] contents = fromPage.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                if (!hasSpaceInAnyPage(toPages, stack)) {
                    continue;
                }
                ItemStack moved = stack.clone();
                moved.setAmount(1);
                addToPagesWithOverflow(toPages, moved);
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    fromPage.setSlot(i, null);
                }
                return;
            }
        }
    }
}
