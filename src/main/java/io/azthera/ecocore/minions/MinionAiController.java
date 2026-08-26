// FILE: src/main/java/io/azthera/ecocore/minions/MinionAiController.java
package io.azthera.ecocore.minions;

import io.azthera.ecocore.claim.ClaimManager;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.minions.types.FishRarityTier;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.minions.types.MinionProcessingType;
import io.azthera.ecocore.minions.types.TreeSpeciesData;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionStorage;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.ThreadLocalRandom;

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

    private final MinionsConfig minionsConfig;
    private final MinionTargetSelector targetSelector;
    private final MinionFuelManager fuelManager;
    private final MinionAnimationHandler animationHandler;
    private final MinionConnectorManager connectorManager;
    private final ClaimManager claimManager;
    private MinionManager minionManager;

    public MinionAiController(MinionsConfig minionsConfig, MinionTargetSelector targetSelector,
                               MinionFuelManager fuelManager, MinionAnimationHandler animationHandler,
                               MinionConnectorManager connectorManager, ClaimManager claimManager) {
        this.minionsConfig = minionsConfig;
        this.targetSelector = targetSelector;
        this.fuelManager = fuelManager;
        this.animationHandler = animationHandler;
        this.connectorManager = connectorManager;
        this.claimManager = claimManager;
    }

    /**
     * Late-binds the owning {@link MinionManager}, since the two
     * classes are mutually dependent (the manager ticks the
     * controller, the controller pushes items to other minions via
     * the manager). Called once during plugin startup wiring.
     *
     * @param minionManager the minion manager instance
     */
    public void setMinionManager(MinionManager minionManager) {
        this.minionManager = minionManager;
    }

    /**
     * Runs one work action for a minion, if it currently has one
     * available (fuel, energy, a valid target, and free storage all
     * permitting). Called by {@code MinionManager.tickAll} at a rate
     * controlled by the minion's configured speed.
     *
     * @param data the minion's persistent state
     * @param handler the minion type's behavior definition
     * @param entity the minion's live visual entity (never moved)
     * @param pages the minion's live storage pages
     */
    public void tick(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
        if (handler.getWorkMode() != MinionWorkMode.NONE) {
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
            case FARM_CYCLE -> handleFarmCycle(data, handler, entity, pages);
            case FISHING -> handleFishing(data, handler, entity, pages);
            case ENTITY_INTERACT -> handleEntityInteract(data, handler, entity, pages);
            case INTERNAL_SMELT -> handleInternalSmelt(data, handler, entity, pages);
            case ITEM_COLLECT -> handleItemCollect(data, handler, entity, pages);
            case CHEST_DETECT -> handleChestDetect(data, handler, entity);
            case SELL_ONLY -> handleSellOnly(data, handler, entity, pages);
            case NONE -> { /* pure storage type, nothing to do */ }
        }

        if (handler.getWorkMode() != MinionWorkMode.NONE) {
            fuelManager.consumeTick(data);
        }
    }

    // ------------------------------------------------------------------
    // Targeting helpers - facing vs arena branching (Revisi 1/2)
    // ------------------------------------------------------------------

    /**
     * Whether this specific placed minion is currently operating in
     * arena (360-degree) mode rather than facing-only, resolving the
     * handler's work mode together with the minion's per-instance
     * toggle for BOTH-mode types.
     */
    private boolean isArenaActive(MinionData data, MinionHandler handler) {
        return switch (handler.getWorkMode()) {
            case ARENA_ONLY -> true;
            case FACING_ONLY -> false;
            case BOTH -> data.isUseArenaMode();
            case NONE -> false;
        };
    }

    private OptionalBlock> findTargetBlock(MinionData data, MinionHandler handler, Location origin) {
        if (isArenaActive(data, handler)) {
            return targetSelector.findNearestBlockInArena(origin, data.getRadius(), handler);
        }
        return targetSelector.findNearestBlockInFacingSlab(origin, data.getFacing(), data.getRadius(), handler);
    }

    private OptionalLivingEntity> findTargetEntity(MinionData data, MinionHandler handler, Location origin) {
        if (isArenaActive(data, handler)) {
            return targetSelector.findBestEntityInArena(origin, data.getRadius(), handler);
        }
        return targetSelector.findBestEntityInFacingSlab(origin, data.getFacing(), data.getRadius(), handler);
    }

    // ------------------------------------------------------------------
    // MINER / QUARRY - block breaking (Revisi 12: reads target set live from handler/config)
    // ------------------------------------------------------------------

    private void handleBlockBreak(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
        OptionalBlock> targetOpt = findTargetBlock(data, handler, entity.getLocation());
        if (targetOpt.isEmpty()) {
            return;
        }
        Block target = targetOpt.get();
        // Revisi 19: cek claim land sebelum minion melakukan break.
        if (!claimManager.isAllowed(data.getOwnerUuid(), target.getLocation())) {
            return;
        }
        Material resultMaterial = handler.resultFor(target.getType());
        ItemStack drop = new ItemStack(resultMaterial != null ? resultMaterial : target.getType());
        if (!hasSpaceInAnyPage(pages, drop)) {
            return; // Revisi 11: semua storage penuh -> minion idle, jangan duplikasi item.
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        target.setType(Material.AIR);
        addToPagesWithOverflow(pages, drop);
        animationHandler.playActionEffect(target.getLocation());
    }

    // ------------------------------------------------------------------
    // FARMER - dual zone plant/harvest/replant with spacing (Revisi 3/4/12)
    // ------------------------------------------------------------------

    private void handleFarmCycle(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
        Location origin = entity.getLocation();
        // First priority: harvest anything mature within range.
        OptionalBlock> matureCrop = findMatureCropInRange(data, handler, origin);
        if (matureCrop.isPresent()) {
            harvestCrop(data, handler, matureCrop.get(), pages);
            return;
        }
        // Second priority: plant a seed from Zone A storage into a valid, correctly-spaced spot.
        plantFromSeedZone(data, handler, origin, pages);
    }

    private OptionalBlock> findMatureCropInRange(MinionData data, MinionHandler handler, Location origin) {
        boolean arena = isArenaActive(data, handler);
        int radius = data.getRadius();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY();
        int baseZ = origin.getBlockZ();
        BlockFace facing = data.getFacing();
        Block best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = arena ? -radius : 0; dx ; dx++) {
            for (int dz = arena ? -radius : 0; dz ; dz++) {
                int x = arena ? baseX + dx : baseX + facing.getModX() * dz + (facing.getModX() == 0 ? dx : 0);
                int z = arena ? baseZ + dz : baseZ + facing.getModZ() * dz + (facing.getModZ() == 0 ? dx : 0);
                for (int dy = -2; dy 2; dy++) {
                    Block block = origin.getWorld().getBlockAt(x, baseY + dy, z);
                    if (!handler.getTargetMaterials().contains(block.getType())) {
                        continue;
                    }
                    if (!isCropMature(block)) {
                        continue;
                    }
                    double distSq = block.getLocation().distanceSquared(origin);
                    if (distSq ) {
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
        // Pumpkin/melon stems produce adjacent fruit blocks directly, which count as always "mature" once formed.
        return block.getType() == Material.PUMPKIN || block.getType() == Material.MELON;
    }

    private void harvestCrop(MinionData data, MinionHandler handler, Block crop, ListMinionStorage> pages) {
        // Revisi 19: cek claim land sebelum minion melakukan break/harvest.
        if (!claimManager.isAllowed(data.getOwnerUuid(), crop.getLocation())) {
            return;
        }
        Material produceMaterial = handler.resultFor(crop.getType());
        ItemStack produce = new ItemStack(produceMaterial != null ? produceMaterial : crop.getType());
        ListMinionStorage> outputPages = zoneOutputPages(pages);
        if (!hasSpaceInAnyPage(outputPages, produce)) {
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
        addToPagesWithOverflow(outputPages, produce);
        animationHandler.playActionEffect(crop.getLocation());
    }

    /**
     * Attempts to plant one seed from the minion's Zone A (seed)
     * storage into a valid, sufficiently-spaced location (Revisi 3):
     * if no safely-spaced spot exists nearby, the minion idles rather
     * than planting anyway.
     */
    private void plantFromSeedZone(MinionData data, MinionHandler handler, Location origin, ListMinionStorage> pages) {
        if (handler.getSeedItem() == null) {
            return;
        }
        MinionStorage zoneAPage = pages.get(0);
        int seedSlot = -1;
        for (int i = 0; i ; i++) {
            ItemStack slot = zoneAPage.getSlot(i);
            if (slot != null && slot.getType() == handler.getSeedItem() && slot.getAmount() > 0) {
                seedSlot = i;
                break;
            }
        }
        if (seedSlot == -1) {
            return; // Revisi 4: seed habis -> idle.
        }
        OptionalBlock> plantSpot = findSpacedPlantingSpot(data, handler, origin);
        if (plantSpot.isEmpty()) {
            return; // Revisi 3: tidak ada lokasi valid -> idle, jangan memaksa.
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
        if (seedStack.getAmount() 0) {
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

    /**
     * Finds a nearby farmland/valid spot for planting that respects
     * the configured crop spacing (Revisi 3) - won't plant directly
     * adjacent to another crop of the same kind if spacing requires
     * distance between them.
     */
    private OptionalBlock> findSpacedPlantingSpot(MinionData data, MinionHandler handler, Location origin) {
        boolean arena = isArenaActive(data, handler);
        int radius = data.getRadius();
        int spacing = minionsConfig.getCropSpacing();
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY() - 1;
        int baseZ = origin.getBlockZ();
        BlockFace facing = data.getFacing();
        for (int dx = arena ? -radius : 0; dx ; dx++) {
            for (int dz = arena ? -radius : 0; dz ; dz++) {
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

    private boolean hasNearbyMatchingCrop(Block spot, java.util.SetMaterial> cropMaterials, int spacing) {
        if (spacing 0) {
            return false;
        }
        for (int dx = -spacing; dx ; dx++) {
            for (int dz = -spacing; dz ; dz++) {
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

    /** Zone B (output) pages: the rest of page 0's slots beyond Zone A, plus every overflow page. */
    private ListMinionStorage> zoneOutputPages(ListMinionStorage> pages) {
        return pages; // addToPagesWithOverflow already skips Zone A slots on page 0 for dual-zone types via zone-aware overload
    }

    // ------------------------------------------------------------------
    // LUMBERJACK - tree chopping with spacing + per-species drops (Revisi 3/7/12)
    // ------------------------------------------------------------------
    // Lumberjack reuses BLOCK_BREAK generically for chopping logs, but with
    // richer per-species drop resolution and a replant step using its own
    // Zone A (sapling) storage - both handled by dedicated methods called
    // from the type-specific branch below rather than the generic path,
    // since AbstractMinionHandler flags Lumberjack as FARM_CYCLE-equivalent
    // for planting purposes. See handleLumberChop, invoked from handleFarmCycle
    // when handler.getType() == LUMBERJACK.

    private void handleLumberChop(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
        Location origin = entity.getLocation();
        OptionalBlock> logTarget = findTargetBlock(data, handler, origin);
        if (logTarget.isPresent()) {
            choplog(data, handler, logTarget.get(), pages);
            return;
        }
        plantSaplingFromZoneA(data, handler, origin, pages);
    }

    private void choplog(MinionData data, MinionHandler handler, Block log, ListMinionStorage> pages) {
        if (!claimManager.isAllowed(data.getOwnerUuid(), log.getLocation())) {
            return;
        }
        TreeSpeciesData species = handler.getTreeSpeciesData().get(log.getType());
        ListItemStack> drops = new java.util.ArrayList<>();
        drops.add(new ItemStack(log.getType(), 1));
        if (species != null) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (random.nextDouble() .appleChance()) {
                drops.add(new ItemStack(Material.APPLE, 1));
            }
            if (random.nextDouble() .stickChance()) {
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
        // Chance to also chop an adjacent leaf block into a sapling for the Zone A replant stock.
        if (species != null) {
            Block leaf = findAdjacentLeaf(log, species.leavesMaterial());
            if (leaf != null && ThreadLocalRandom.current().nextDouble() 0.15) {
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

    private void plantSaplingFromZoneA(MinionData data, MinionHandler handler, Location origin, ListMinionStorage> pages) {
        MinionStorage zoneAPage = pages.get(0);
        int saplingSlot = -1;
        Material saplingType = null;
        for (int i = 0; i ; i++) {
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
        OptionalBlock> spot = findSpacedTreeSpot(data, origin, species);
        if (spot.isEmpty()) {
            return; // Revisi 3: tidak ada lokasi valid dan aman -> idle.
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        spot.get().setType(saplingType);
        ItemStack saplingStack = zoneAPage.getSlot(saplingSlot);
        saplingStack.setAmount(saplingStack.getAmount() - 1);
        if (saplingStack.getAmount() 0) {
            zoneAPage.setSlot(saplingSlot, null);
        }
        animationHandler.playActionEffect(spot.get().getLocation());
    }

    private OptionalBlock> findSpacedTreeSpot(MinionData data, Location origin, TreeSpeciesData species) {
        boolean arena = isArenaActive(data, null) || true; // lumberjack always allowed arena search for replanting
        int radius = data.getRadius();
        int spacing = minionsConfig.getTreeSpacingFor(species.logMaterial());
        int canopyClearance = minionsConfig.getCanopyClearanceFor(species.logMaterial());
        int baseX = origin.getBlockX();
        int baseY = origin.getBlockY() - 1;
        int baseZ = origin.getBlockZ();
        for (int dx = -radius; dx ; dx++) {
            for (int dz = -radius; dz ; dz++) {
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
        for (int dx = 0; dx 1; dx++) {
            for (int dz = 0; dz 1; dz++) {
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
        if (spacing 0) {
            return false;
        }
        for (int dx = -spacing; dx ; dx++) {
            for (int dz = -spacing; dz ; dz++) {
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
        for (int dy = 1; dy 3; dy++) {
            if (!spot.getRelative(0, dy, 0).getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private void addToZoneA(ListMinionStorage> pages, ItemStack item) {
        MinionStorage zoneAPage = pages.get(0);
        for (int i = 0; i ; i++) {
            ItemStack slot = zoneAPage.getSlot(i);
            if (slot == null) {
                zoneAPage.setSlot(i, item);
                return;
            }
            if (slot.isSimilar(item) && slot.getAmount() .getMaxStackSize()) {
                slot.setAmount(slot.getAmount() + item.getAmount());
                return;
            }
        }
        // Zone A full: silently drop the extra sapling rather than duplicating or crashing.
    }

    // ------------------------------------------------------------------
    // FISHERMAN - weighted rarity catches (Revisi 8)
    // ------------------------------------------------------------------

    private void handleFishing(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
        ListFishRarityTier> tiers = handler.getRarityTiers();
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

    private Material rollRarityCatch(ListFishRarityTier> tiers) {
        double totalWeight = tiers.stream().mapToDouble(FishRarityTier::weight).sum();
        if (totalWeight 0) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double cumulative = 0;
        for (FishRarityTier tier : tiers) {
            cumulative += tier.weight();
            if (roll .pool().isEmpty()) {
                return tier.pool().get(ThreadLocalRandom.current().nextInt(tier.pool().size()));
            }
        }
        ListMaterial> lastPool = tiers.get(tiers.size() - 1).pool();
        return lastPool.isEmpty() ? null : lastPool.get(0);
    }

    // ------------------------------------------------------------------
    // MOB_KILLER - entity interact
    // ------------------------------------------------------------------

    private void handleEntityInteract(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
        OptionalLivingEntity> targetOpt = findTargetEntity(data, handler, entity.getLocation());
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

    // ------------------------------------------------------------------
    // SMELTER - raw_iron -> iron_ingot style recipes (Revisi 5), input/output zones
    // ------------------------------------------------------------------

    private void handleInternalSmelt(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
        MinionStorage inputPage = pages.get(0);
        java.util.MapMaterial, Material> recipes = handler.getSmeltingRecipes();
        for (int i = 0; i ; i++) {
            ItemStack input = inputPage.getSlot(i);
            if (input == null || input.getAmount() 0) {
                continue;
            }
            Material outputMaterial = recipes.get(input.getType());
            if (outputMaterial == null) {
                continue;
            }
            ItemStack output = new ItemStack(outputMaterial, 1);
            ListMinionStorage> outputPages = pages;
            if (!hasSpaceInAnyPage(outputPages, output)) {
                return; // Revisi 11: output penuh -> idle, jangan duplikasi.
            }
            if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
                return;
            }
            input.setAmount(input.getAmount() - 1);
            if (input.getAmount() 0) {
                inputPage.setSlot(i, null);
            }
            addToPagesWithOverflow(outputPages, output);
            animationHandler.playActionEffect(entity.getLocation());
            return;
        }
    }

    // ------------------------------------------------------------------
    // COLLECTOR - picks up ground item drops only (Revisi 9: no longer relays between minions)
    // ------------------------------------------------------------------

    private void handleItemCollect(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
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

    // ------------------------------------------------------------------
    // CHEST - detects adjacent single/double chest (Revisi 2)
    // ------------------------------------------------------------------

    private void handleChestDetect(MinionData data, MinionHandler handler, Entity entity) {
        // Detection happens once at placement time (see MinionEggListener);
        // nothing to do per-tick for this type beyond staying idle.
    }

    // ------------------------------------------------------------------
    // SELL - handled by SellManager elsewhere; nothing per-tick beyond idling
    // ------------------------------------------------------------------

    private void handleSellOnly(MinionData data, MinionHandler handler, Entity entity, ListMinionStorage> pages) {
        // Auto-sell timing/economy interaction lives in SellManager, invoked
        // separately by a scheduled task rather than the per-tick AI pass.
    }

    // ------------------------------------------------------------------
    // Storage helpers - multi-page overflow (Revisi 11)
    // ------------------------------------------------------------------

    private boolean hasSpaceInAnyPage(ListMinionStorage> pages, ItemStack item) {
        for (MinionStorage page : pages) {
            if (page.hasSpaceFor(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an item to the first page with room, checking pages in
     * order (Storage 1 -> 2 -> ... -> N) so overflow always fills the
     * lowest-numbered available page first (Revisi 11).
     */
    private void addToPagesWithOverflow(ListMinionStorage> pages, ItemStack item) {
        for (MinionStorage page : pages) {
            ItemStack[] contents = page.getContents();
            for (int i = 0; i .length; i++) {
                ItemStack slot = contents[i];
                if (slot != null && slot.isSimilar(item) && slot.getAmount() .getMaxStackSize()) {
                    int space = slot.getMaxStackSize() - slot.getAmount();
                    int toAdd = Math.min(space, item.getAmount());
                    slot.setAmount(slot.getAmount() + toAdd);
                    item.setAmount(item.getAmount() - toAdd);
                    if (item.getAmount() 0) {
                        return;
                    }
                }
            }
            for (int i = 0; i .length; i++) {
                if (contents[i] == null) {
                    page.setSlot(i, item.clone());
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Connector network - pushes items along outgoing connections (Revisi 9)
    // ------------------------------------------------------------------

    /**
     * Pushes items from this minion's output storage into any minion
     * it has an outgoing connection to (DIRECT or RELAY, both behave
     * identically for transfer purposes once the link is validated -
     * the mode only affects max distance at connect-time).
     */
    private void pushAlongConnections(MinionData data, ListMinionStorage> pages) {
        if (minionManager == null) {
            return;
        }
        ListLong> destinations = connectorManager.getOutgoingIds(data.getId());
        if (destinations.isEmpty()) {
            return;
        }
        for (long destinationId : destinations) {
            ListMinionStorage> destinationPages = minionManager.getMinionPages(destinationId);
            if (destinationPages == null) {
                continue;
            }
            transferOneStack(pages, destinationPages);
        }
    }

    private void transferOneStack(ListMinionStorage> fromPages, ListMinionStorage> toPages) {
        for (MinionStorage fromPage : fromPages) {
            ItemStack[] contents = fromPage.getContents();
            for (int i = 0; i .length; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getAmount() 0) {
                    continue;
                }
                if (!hasSpaceInAnyPage(toPages, stack)) {
                    continue;
                }
                ItemStack moved = stack.clone();
                moved.setAmount(1);
                addToPagesWithOverflow(toPages, moved);
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() 0) {
                    fromPage.setSlot(i, null);
                }
                return;
            }
        }
    }
}