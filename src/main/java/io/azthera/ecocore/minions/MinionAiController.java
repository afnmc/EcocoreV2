package io.azthera.ecocore.minions;

import io.azthera.ecocore.claim.ClaimManager;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.minions.types.MinionProcessingType;
import io.azthera.ecocore.minions.types.TreeSpeciesData;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionStorage;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import io.azthera.ecocore.model.ShopItemRecord;
import io.azthera.ecocore.sell.SellManager;
import org.bukkit.Location;
import org.bukkit.LootableInventory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/**
 * Executes a single work action for a stationary minion each time
 * it's ticked. Never moves, teleports, or pathfinds the minion in any
 * way (Revisi 1) - all targeting is done via
 * {@link MinionTargetSelector} against the minion's fixed placement
 * location, branching between a facing-only STRAIGHT LINE (bug-fix
 * round: narrowed from a wide slab to a strict 1-wide line) and a
 * full 360-degree arena based on the handler's {@link MinionWorkMode}
 * and, for BOTH-mode types, the player's per-minion toggle.
 *
 * <p>Bug-fix round changes summarized:
 * <ul>
 *   <li>Storage space checks now respect {@link MinionData#getActiveSlotCount()}
 *       for every type except {@link MinionType#STORAGE}, which alone
 *       uses the full multi-page model.</li>
 *   <li>Dual-zone types (Smelter/Lumberjack/Farmer) now strictly separate
 *       Zone A (input) and Zone B (output): output NEVER lands in Zone A,
 *       and {@link #pushAlongConnections} pulls from the SOURCE's Zone B
 *       (output) and delivers into the DESTINATION's Zone A (input) if it
 *       has one, else its general storage.</li>
 *   <li>Farmer planting spacing now checks for ANY crop already occupying
 *       a farmland tile, not just the handler's specific target material
 *       set, so wheat/carrot/potato/beetroot get real spacing too.</li>
 *   <li>Lumberjack tree height/leaf-clearing/multi-sapling planting reworked
 *       against real per-species Minecraft max heights, and choplog() now
 *       clears the whole canopy, not just one adjacent leaf block.</li>
 *   <li>Fisherman reworked to use the real vanilla fishing loot table
 *       instead of the old synthetic weighted-rarity system.</li>
 *   <li>Chest type now actively pushes its storage into an adjacent real
 *       chest every tick, not just detecting it once at placement.</li>
 * </ul>
 */
public final class MinionAiController {

    /** How many of a storage page's 54 slots are reserved as Zone A (seed/input) for dual-zone types. */
    public static final int ZONE_A_SLOTS = 9;

    private static final BlockFace[] ADJACENT_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    /**
     * Real per-species max tree height (trunk + canopy, from the
     * Minecraft wiki's growth ranges), used to size how tall a
     * clearance/canopy check needs to go instead of the old fixed
     * "+3" guess. Values are the upper end of each species' natural
     * range so a minion never gets blocked replanting a spot that a
     * real tree could grow into.
     */
    private static final Map<Material, Integer> TREE_MAX_HEIGHT = Map.ofEntries(
            Map.entry(Material.OAK_LOG, 14),
            Map.entry(Material.SPRUCE_LOG, 32),
            Map.entry(Material.BIRCH_LOG, 10),
            Map.entry(Material.JUNGLE_LOG, 32),
            Map.entry(Material.ACACIA_LOG, 10),
            Map.entry(Material.DARK_OAK_LOG, 20),
            Map.entry(Material.MANGROVE_LOG, 18),
            Map.entry(Material.CHERRY_LOG, 10)
    );

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
        if (handler.getWorkMode() != MinionWorkMode.NONE || handler.getProcessingType() == MinionProcessingType.SELL_ONLY) {
            if (!fuelManager.isFueled(data)) {
                fuelManager.tryConsumeFuelFromStorage(data, pages);
                if (!fuelManager.isFueled(data)) {
                    animationHandler.playOutOfFuelEffect(entity.getLocation());
                    return;
                }
            }
        }

        pushAlongConnections(data, handler, pages);

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
            case CHEST_DETECT -> handleChestPush(data, entity, pages);
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
        return targetSelector.findNearestBlockInFacingLine(origin, data.getFacing(), data.getRadius(), handler);
    }

    private Optional<LivingEntity> findTargetEntity(MinionData data, MinionHandler handler, Location origin) {
        if (isArenaActive(data, handler)) {
            return targetSelector.findBestEntityInArena(origin, data.getRadius(), handler);
        }
        return targetSelector.findBestEntityInFacingLine(origin, data.getFacing(), data.getRadius(), handler);
    }

    // ------------------------------------------------------------------
    // MINER / QUARRY
    // ------------------------------------------------------------------

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
        if (!hasSpaceForOutput(data, pages, drop)) {
            return;
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        target.setType(Material.AIR);
        addToOutputWithOverflow(data, pages, drop);
        animationHandler.playActionEffect(target.getLocation());
    }

    // ------------------------------------------------------------------
    // FARMER
    // ------------------------------------------------------------------

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
            for (int dz = arena ? -radius : (facing.getModZ() != 0 ? 0 : 0); dz <= (arena ? radius : radius); dz++) {
                int x = arena ? baseX + dx : baseX + facing.getModX() * dz;
                int z = arena ? baseZ + dz : baseZ + facing.getModZ() * dz;
                if (!arena && facing.getModX() == 0 && facing.getModZ() == 0) {
                    continue;
                }
                if (!arena && dx != 0) {
                    continue; // strict single line, no sideways spread
                }
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
        if (!hasSpaceForOutput(data, pages, produce)) {
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
        addToOutputWithOverflow(data, pages, produce);
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

    /**
     * Finds a farmland tile to plant into, respecting spacing (Revisi
     * 3). Bug fix: this used to only check for NEARBY blocks matching
     * this handler's own target-crop set, which meant a Farmer
     * planting wheat never noticed a carrot planted next to it (a
     * different crop material) and would plant right on top of
     * anything that wasn't specifically its own crop type. Now checks
     * for ANY crop occupying the farmland tile itself (so it never
     * double-plants on an occupied tile) plus spacing against every
     * common crop type nearby, not just its own.
     */
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
                int x;
                int z;
                if (arena) {
                    x = baseX + dx;
                    z = baseZ + dz;
                } else {
                    if (dx != 0) {
                        continue; // strict single line forward, no side spread
                    }
                    x = baseX + facing.getModX() * dz;
                    z = baseZ + facing.getModZ() * dz;
                }
                Block ground = origin.getWorld().getBlockAt(x, baseY, z);
                Block above = ground.getRelative(BlockFace.UP);
                if (ground.getType() != Material.FARMLAND || !above.getType().isAir()) {
                    continue;
                }
                if (!claimManager.isAllowed(data.getOwnerUuid(), above.getLocation())) {
                    continue;
                }
                if (isAnyCropSpacingViolated(above, spacing)) {
                    continue;
                }
                return Optional.of(above);
            }
        }
        return Optional.empty();
    }

    private static final Set<Material> ALL_CROP_MATERIALS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.PUMPKIN_STEM, Material.MELON_STEM,
            Material.PUMPKIN, Material.MELON, Material.COCOA
    );

    private boolean isAnyCropSpacingViolated(Block spot, int spacing) {
        if (spacing <= 0) {
            return false;
        }
        for (int dx = -spacing; dx <= spacing; dx++) {
            for (int dz = -spacing; dz <= spacing; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Block nearby = spot.getRelative(dx, 0, dz);
                if (ALL_CROP_MATERIALS.contains(nearby.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // LUMBERJACK
    // ------------------------------------------------------------------

    private void handleLumberChop(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        Location origin = entity.getLocation();
        Optional<Block> logTarget = findTargetBlock(data, handler, origin);
        if (logTarget.isPresent()) {
            choplog(data, handler, logTarget.get(), pages);
            return;
        }
        plantSaplingFromZoneA(data, handler, origin, pages);
    }

    /**
     * Chops a log and clears its ENTIRE leaf canopy (bug fix: used to
     * only ever touch a single adjacent leaf block on a sapling-chance
     * roll, leaving the rest of the tree's leaves floating). Walks
     * outward from the log through connected leaves up to the
     * species' real max height, breaking every leaf block found;
     * saplings/apples/sticks drop from the leaf-clearing pass with
     * the same species-configured chances applied per leaf cleared
     * rather than only once for the whole tree.
     */
    private void choplog(MinionData data, MinionHandler handler, Block log, List<MinionStorage> pages) {
        if (!claimManager.isAllowed(data.getOwnerUuid(), log.getLocation())) {
            return;
        }
        TreeSpeciesData species = handler.getTreeSpeciesData().get(log.getType());
        List<ItemStack> drops = new ArrayList<>();
        drops.add(new ItemStack(log.getType(), 1));

        List<Block> leavesToClear = species != null ? findConnectedLeaves(log, species) : List.of();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int extraSaplings = 0;
        if (species != null) {
            for (int i = 0; i < leavesToClear.size(); i++) {
                if (random.nextDouble() < species.appleChance()) {
                    drops.add(new ItemStack(Material.APPLE, 1));
                }
                if (random.nextDouble() < species.stickChance()) {
                    drops.add(new ItemStack(Material.STICK, 1));
                }
                if (random.nextDouble() < minionsConfig.getLumberjackSaplingHarvestChance()) {
                    extraSaplings++;
                }
            }
        }

        for (ItemStack drop : drops) {
            if (!hasSpaceForOutput(data, pages, drop)) {
                return;
            }
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }

        log.setType(Material.AIR);
        for (Block leaf : leavesToClear) {
            leaf.setType(Material.AIR);
        }
        for (ItemStack drop : drops) {
            addToOutputWithOverflow(data, pages, drop);
        }
        if (species != null && extraSaplings > 0) {
            for (int i = 0; i < extraSaplings; i++) {
                addToZoneA(pages, new ItemStack(species.saplingMaterial(), 1));
            }
        }
        animationHandler.playActionEffect(log.getLocation());
    }

    /**
     * Flood-fills outward from a chopped log through adjacent leaf
     * blocks of the matching species, capped at the species' real
     * Minecraft max height (Revisi wiki-sourced values in {@link
     * #TREE_MAX_HEIGHT}) so this never runs away scanning unrelated
     * builds far above the tree.
     */
    private List<Block> findConnectedLeaves(Block log, TreeSpeciesData species) {
        int maxHeight = TREE_MAX_HEIGHT.getOrDefault(species.logMaterial(), 16);
        List<Block> found = new ArrayList<>();
        java.util.Deque<Block> queue = new java.util.ArrayDeque<>();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        int baseY = log.getY();
        for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
            Block relative = log.getRelative(face);
            if (relative.getType() == species.leavesMaterial()) {
                queue.add(relative);
            }
        }
        int guard = 0;
        while (!queue.isEmpty() && guard < 4000) {
            guard++;
            Block current = queue.poll();
            long key = ((long) current.getX() << 40) ^ ((long) current.getY() << 20) ^ (current.getZ() & 0xFFFFF);
            if (!visited.add(key)) {
                continue;
            }
            if (Math.abs(current.getY() - baseY) > maxHeight) {
                continue;
            }
            if (current.getType() != species.leavesMaterial()) {
                continue;
            }
            found.add(current);
            for (BlockFace face : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.DOWN}) {
                Block relative = current.getRelative(face);
                if (relative.getType() == species.leavesMaterial() || relative.getType() == species.logMaterial()) {
                    queue.add(relative);
                }
            }
        }
        return found;
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

        if (species.require2x2()) {
            plant2x2Sapling(data, handler, origin, species, zoneAPage, saplingSlot, saplingType);
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
        consumeOneSapling(zoneAPage, saplingSlot);
        animationHandler.playActionEffect(spot.get().getLocation());
    }

    /**
     * Bug fix: species that {@code require2x2()} (dark oak) need
     * FOUR saplings placed simultaneously in a 2x2 grid to actually
     * grow in vanilla Minecraft - the old code only ever placed one
     * sapling even when this flag was set, which can never sprout.
     * This finds a valid 2x2 patch, requires 4 saplings of the right
     * type in Zone A, and plants all 4 in the same action.
     */
    private void plant2x2Sapling(MinionData data, MinionHandler handler, Location origin, TreeSpeciesData species,
                                  MinionStorage zoneAPage, int firstSlot, Material saplingType) {
        int available = 0;
        for (int i = 0; i < ZONE_A_SLOTS; i++) {
            ItemStack slot = zoneAPage.getSlot(i);
            if (slot != null && slot.getType() == saplingType) {
                available += slot.getAmount();
            }
        }
        if (available < 4) {
            return; // not enough saplings for a 2x2 planting yet
        }
        Optional<Block> corner = findSpacedTreeSpot(data, origin, species);
        if (corner.isEmpty()) {
            return;
        }
        Block base = corner.get();
        List<Block> grid = new ArrayList<>();
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                grid.add(base.getRelative(dx, 0, dz));
            }
        }
        for (Block spot : grid) {
            if (!claimManager.isAllowed(data.getOwnerUuid(), spot.getLocation()) || !spot.getType().isAir()) {
                return;
            }
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        for (Block spot : grid) {
            spot.setType(saplingType);
        }
        int remaining = 4;
        for (int i = 0; i < ZONE_A_SLOTS && remaining > 0; i++) {
            ItemStack slot = zoneAPage.getSlot(i);
            if (slot == null || slot.getType() != saplingType) {
                continue;
            }
            int take = Math.min(remaining, slot.getAmount());
            slot.setAmount(slot.getAmount() - take);
            remaining -= take;
            if (slot.getAmount() <= 0) {
                zoneAPage.setSlot(i, null);
            }
        }
        animationHandler.playActionEffect(base.getLocation());
    }

    private void consumeOneSapling(MinionStorage zoneAPage, int slot) {
        ItemStack saplingStack = zoneAPage.getSlot(slot);
        saplingStack.setAmount(saplingStack.getAmount() - 1);
        if (saplingStack.getAmount() <= 0) {
            zoneAPage.setSlot(slot, null);
        }
    }

    private Optional<Block> findSpacedTreeSpot(MinionData data, Location origin, TreeSpeciesData species) {
        int radius = data.getRadius();
        int spacing = minionsConfig.getTreeSpacingFor(species.logMaterial());
        int canopyClearance = TREE_MAX_HEIGHT.getOrDefault(species.logMaterial(),
                minionsConfig.getCanopyClearanceFor(species.logMaterial()) + 3);
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
        for (int dy = 1; dy <= canopyClearance; dy++) {
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

    // ------------------------------------------------------------------
    // FISHERMAN - real vanilla fishing loot table
    // ------------------------------------------------------------------

    /**
     * Bug fix: the old system was a synthetic weighted-rarity roll
     * that had no relationship to real Minecraft fishing and, more
     * importantly, added the entire returned pool as if it were one
     * catch (the "more than one item per catch" complaint). This now
     * pulls from the REAL vanilla {@code minecraft:gameplay/fishing}
     * loot table via {@link LootTables#FISHING}, generating exactly
     * one roll of that table per successful catch cycle - the same
     * mechanic and drop odds a player fishing with a plain rod gets,
     * with each resulting ItemStack from that single roll added
     * individually (a loot table entry can itself specify a stack of
     * more than 1 of an item, e.g. multiple raw fish, but the minion
     * never rolls the table more than once per catch).
     */
    private void handleFishing(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        LootTable fishingTable = Bukkit.getLootTable(LootTables.FISHING.getKey());
        if (fishingTable == null) {
            return;
        }
        LootContext context = new LootContext.Builder(entity.getLocation()).build();
        Set<ItemStack> catchResult;
        try {
            catchResult = fishingTable.populateLoot(ThreadLocalRandom.current(), context);
        } catch (IllegalStateException | IllegalArgumentException lootFailure) {
            return; // fail closed - no catch this cycle rather than crashing the tick
        }
        if (catchResult.isEmpty()) {
            return;
        }
        List<ItemStack> catches = new ArrayList<>(catchResult);
        for (ItemStack drop : catches) {
            if (!hasSpaceForOutput(data, pages, drop)) {
                return;
            }
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        for (ItemStack drop : catches) {
            addToOutputWithOverflow(data, pages, drop);
        }
        animationHandler.playActionEffect(entity.getLocation());
    }

    // ------------------------------------------------------------------
    // MOB_KILLER
    // ------------------------------------------------------------------

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
        if (!hasSpaceForOutput(data, pages, drop)) {
            return;
        }
        if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
            return;
        }
        target.damage(1000.0);
        addToOutputWithOverflow(data, pages, drop);
        animationHandler.playActionEffect(target.getLocation());
    }

    // ------------------------------------------------------------------
    // SMELTER - Zone A (input) -> Zone B (output), never the reverse
    // ------------------------------------------------------------------

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
            if (!hasSpaceForOutput(data, pages, output)) {
                return;
            }
            if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
                return;
            }
            input.setAmount(input.getAmount() - 1);
            if (input.getAmount() <= 0) {
                inputPage.setSlot(i, null);
            }
            addToOutputWithOverflow(data, pages, output);
            animationHandler.playActionEffect(entity.getLocation());
            return;
        }
    }

    // ------------------------------------------------------------------
    // COLLECTOR
    // ------------------------------------------------------------------

    private void handleItemCollect(MinionData data, MinionHandler handler, Entity entity, List<MinionStorage> pages) {
        Location origin = entity.getLocation();
        double radiusSq = (double) data.getRadius() * data.getRadius();
        for (org.bukkit.entity.Item groundItem : origin.getWorld().getEntitiesByClass(org.bukkit.entity.Item.class)) {
            if (groundItem.getLocation().distanceSquared(origin) > radiusSq) {
                continue;
            }
            ItemStack stack = groundItem.getItemStack();
            if (!hasSpaceForOutput(data, pages, stack)) {
                continue;
            }
            if (!data.consumeEnergy(handler.getEnergyCostPerAction())) {
                return;
            }
            addToOutputWithOverflow(data, pages, stack);
            groundItem.remove();
            animationHandler.playActionEffect(groundItem.getLocation());
            return;
        }
    }

    // ------------------------------------------------------------------
    // CHEST - actively pushes storage into an adjacent real chest every tick
    // ------------------------------------------------------------------

    /**
     * Bug fix: the CHEST type used to only detect an adjacent chest
     * once at placement and do nothing further. Now it actively
     * pushes everything in its own storage into an adjacent real
     * Container every tick, exactly matching the report - one item
     * moved per tick to keep it visually smooth and avoid a single
     * giant inventory operation.
     */
    private void handleChestPush(MinionData data, Entity entity, List<MinionStorage> pages) {
        Block center = entity.getLocation().getBlock();
        Container adjacentContainer = null;
        for (BlockFace face : ADJACENT_FACES) {
            Block adjacent = center.getRelative(face);
            if (adjacent.getState() instanceof Container container) {
                adjacentContainer = container;
                break;
            }
        }
        if (adjacentContainer == null) {
            return;
        }
        Inventory targetInventory = adjacentContainer.getInventory();
        for (MinionStorage page : pages) {
            ItemStack[] contents = page.getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                ItemStack toMove = stack.clone();
                toMove.setAmount(1);
                Map<Integer, ItemStack> leftover = targetInventory.addItem(toMove);
                if (!leftover.isEmpty()) {
                    return; // chest is full - stop trying this tick
                }
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    page.setSlot(i, null);
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // SELL
    // ------------------------------------------------------------------

    private void handleSellOnly(MinionData data, Entity entity, List<MinionStorage> pages) {
        pullSellableFromAdjacentContainer(data, entity, pages);

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

    private void pullSellableFromAdjacentContainer(MinionData data, Entity entity, List<MinionStorage> pages) {
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
                if (!hasSpaceForOutput(data, pages, slot)) {
                    continue;
                }
                ItemStack moved = slot.clone();
                addToOutputWithOverflow(data, pages, moved);
                sourceInventory.setItem(i, null);
            }
            return;
        }
    }

    // ------------------------------------------------------------------
    // Storage helpers - respect activeSlotCount for non-STORAGE types (bug fix)
    // ------------------------------------------------------------------

    /**
     * Whether an item can currently fit anywhere in this minion's
     * usable storage. Bug fix: {@link MinionType#STORAGE} alone uses
     * every unlocked page in full; every other type has exactly one
     * page and only its first {@link MinionData#getActiveSlotCount()}
     * slots are usable at all - slots beyond that are locked and
     * must never receive items even if technically empty.
     */
    private boolean hasSpaceInUsableSlots(MinionData data, List<MinionStorage> pages, ItemStack item) {
        if (data.getType() == MinionType.STORAGE) {
            for (MinionStorage page : pages) {
                if (page.hasSpaceFor(item)) {
                    return true;
                }
            }
            return false;
        }
        MinionStorage page = pages.get(0);
        int usable = Math.min(data.getActiveSlotCount(), MinionStorage.SLOTS_PER_PAGE);
        int maxStackSize = item.getMaxStackSize();
        for (int i = 0; i < usable; i++) {
            ItemStack slot = page.getSlot(i);
            if (slot == null) {
                return true;
            }
            if (slot.isSimilar(item) && slot.getAmount() < maxStackSize) {
                return true;
            }
        }
        return false;
    }

    /**
     * Same activeSlotCount-aware check as {@link #hasSpaceInUsableSlots}
     * but restricted to the OUTPUT range only (Zone B for dual-zone
     * types, or the whole usable range for single-zone types).
     */
    private boolean hasSpaceForOutput(MinionData data, List<MinionStorage> pages, ItemStack item) {
        if (data.getType() == MinionType.STORAGE) {
            return hasSpaceInUsableSlots(data, pages, item);
        }
        if (!isDualZoneType(data.getType())) {
            return hasSpaceInUsableSlots(data, pages, item);
        }
        MinionStorage firstPage = pages.get(0);
        int usable = Math.min(data.getActiveSlotCount(), MinionStorage.SLOTS_PER_PAGE);
        int maxStackSize = item.getMaxStackSize();
        for (int i = ZONE_A_SLOTS; i < usable; i++) {
            ItemStack slot = firstPage.getSlot(i);
            if (slot == null) {
                return true;
            }
            if (slot.isSimilar(item) && slot.getAmount() < maxStackSize) {
                return true;
            }
        }
        return false;
    }

    private boolean isDualZoneType(MinionType type) {
        return type == MinionType.SMELTER || type == MinionType.LUMBERJACK || type == MinionType.FARMER;
    }

    /**
     * Adds an item to output storage only, honoring activeSlotCount
     * and, for dual-zone types, restricting to Zone B (bug fix: this
     * used to write starting from slot 0 of page 0, which for
     * dual-zone types IS Zone A - meaning smelter/lumberjack/farmer
     * output was silently landing in the input zone instead of the
     * output zone).
     */
    private void addToOutputWithOverflow(MinionData data, List<MinionStorage> pages, ItemStack item) {
        if (data.getType() == MinionType.STORAGE) {
            addWithOverflowInRange(pages, item, 0, MinionStorage.SLOTS_PER_PAGE, true);
            return;
        }
        int usable = Math.min(data.getActiveSlotCount(), MinionStorage.SLOTS_PER_PAGE);
        int startSlot = isDualZoneType(data.getType()) ? ZONE_A_SLOTS : 0;
        if (startSlot >= usable) {
            return; // no unlocked output slots yet
        }
        addWithOverflowInRange(List.of(pages.get(0)), item, startSlot, usable, false);
    }

    private void addWithOverflowInRange(List<MinionStorage> pages, ItemStack item, int startSlot, int endSlotExclusive,
                                         boolean multiPage) {
        for (MinionStorage page : pages) {
            ItemStack[] contents = page.getContents();
            int limit = multiPage ? contents.length : Math.min(endSlotExclusive, contents.length);
            for (int i = startSlot; i < limit; i++) {
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
            for (int i = startSlot; i < limit; i++) {
                if (contents[i] == null) {
                    page.setSlot(i, item.clone());
                    return;
                }
            }
            startSlot = 0; // subsequent pages (STORAGE type only) start from slot 0
        }
    }

    // ------------------------------------------------------------------
    // Connector network - Zone B (output) -> Zone A (input), bug fix
    // ------------------------------------------------------------------

    /**
     * Pushes items from this minion's OUTPUT storage into any minion
     * it has an outgoing connection to. Bug fix: this used to read
     * from the raw start of page 0 (Zone A / input for dual-zone
     * types) and write into the raw start of the destination's page
     * 0 as well - meaning a Smelter connected onward was having its
     * INPUT ore pulled out instead of its finished OUTPUT ingots, and
     * a destination like a Lumberjack was receiving deliveries into
     * whatever slot 0 happened to be rather than its input zone. Now
     * strictly reads from the source's output range and writes into
     * the destination's input range (Zone A) if it's a dual-zone
     * type, or its general usable storage otherwise.
     */
    private void pushAlongConnections(MinionData data, MinionHandler handler, List<MinionStorage> pages) {
        if (minionManager == null) {
            return;
        }
        List<Long> destinations = connectorManager.getOutgoingIds(data.getId());
        if (destinations.isEmpty()) {
            return;
        }
        for (long destinationId : destinations) {
            List<MinionStorage> destinationPages = minionManager.getMinionPages(destinationId);
            MinionData destinationData = minionManager.getMinion(destinationId);
            if (destinationPages == null || destinationData == null) {
                continue;
            }
            transferOneStackOutputToInput(data, pages, destinationData, destinationPages);
        }
    }

    private void transferOneStackOutputToInput(MinionData sourceData, List<MinionStorage> sourcePages,
                                                MinionData destinationData, List<MinionStorage> destinationPages) {
        MinionStorage sourceFirstPage = sourcePages.get(0);
        int sourceStart = isDualZoneType(sourceData.getType()) ? ZONE_A_SLOTS : 0;
        int sourceEnd = sourceData.getType() == MinionType.STORAGE
                ? MinionStorage.SLOTS_PER_PAGE
                : Math.min(sourceData.getActiveSlotCount(), MinionStorage.SLOTS_PER_PAGE);

        List<MinionStorage> scanPages = sourceData.getType() == MinionType.STORAGE ? sourcePages : List.of(sourceFirstPage);
        for (MinionStorage page : scanPages) {
            ItemStack[] contents = page.getContents();
            int limit = sourceData.getType() == MinionType.STORAGE ? contents.length : sourceEnd;
            for (int i = sourceStart; i < limit; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getAmount() <= 0) {
                    continue;
                }
                if (!hasSpaceForInput(destinationData, destinationPages, stack)) {
                    continue;
                }
                ItemStack moved = stack.clone();
                moved.setAmount(1);
                addToInputWithOverflow(destinationData, destinationPages, moved);
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    page.setSlot(i, null);
                }
                return;
            }
            sourceStart = 0;
        }
    }

    private boolean hasSpaceForInput(MinionData data, List<MinionStorage> pages, ItemStack item) {
        if (data.getType() == MinionType.STORAGE) {
            return hasSpaceInUsableSlots(data, pages, item);
        }
        if (!isDualZoneType(data.getType())) {
            return hasSpaceInUsableSlots(data, pages, item);
        }
        MinionStorage firstPage = pages.get(0);
        int usable = Math.min(data.getActiveSlotCount(), ZONE_A_SLOTS);
        int maxStackSize = item.getMaxStackSize();
        for (int i = 0; i < usable; i++) {
            ItemStack slot = firstPage.getSlot(i);
            if (slot == null) {
                return true;
            }
            if (slot.isSimilar(item) && slot.getAmount() < maxStackSize) {
                return true;
            }
        }
        return false;
    }

    private void addToInputWithOverflow(MinionData data, List<MinionStorage> pages, ItemStack item) {
        if (data.getType() == MinionType.STORAGE) {
            addWithOverflowInRange(pages, item, 0, MinionStorage.SLOTS_PER_PAGE, true);
            return;
        }
        if (!isDualZoneType(data.getType())) {
            addToOutputWithOverflow(data, pages, item);
            return;
        }
        int usable = Math.min(data.getActiveSlotCount(), ZONE_A_SLOTS);
        addWithOverflowInRange(List.of(pages.get(0)), item, 0, usable, false);
    }
}
