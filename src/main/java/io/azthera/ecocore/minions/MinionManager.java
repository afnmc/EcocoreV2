package io.azthera.ecocore.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.database.dao.MinionsDao;
import io.azthera.ecocore.minions.types.CollectorMinion;
import io.azthera.ecocore.minions.types.FarmerMinion;
import io.azthera.ecocore.minions.types.FishermanMinion;
import io.azthera.ecocore.minions.types.LumberjackMinion;
import io.azthera.ecocore.minions.types.MinerMinion;
import io.azthera.ecocore.minions.types.ChestMinion;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.minions.types.MobKillerMinion;
import io.azthera.ecocore.minions.types.QuarryMinion;
import io.azthera.ecocore.minions.types.SellMinion;
import io.azthera.ecocore.minions.types.SmelterMinion;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionStorage;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.model.MinionWorkMode;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class MinionManager {

    public static final int DEFAULT_MAX_MINIONS_PER_PLAYER = 20;
    private static final String MINION_ID_KEY = "minion_id";
    private static final int MAX_CATCH_UP_ACTIONS_PER_PASS = 50;
    private static final String PAGE_DELIMITER = "\u0000PAGE\u0000";

    private final Logger logger;
    private final MinionsDao minionsDao;
    private final MinionsConfig minionsConfig;
    private final MinionFactory minionFactory;
    private final MinionAiController aiController;
    private final MinionConnectorManager connectorManager;
    private final Map<MinionType, MinionHandler> handlers = new EnumMap<>(MinionType.class);

    /**
     * A read-only snapshot of a nearby owned minion, used by
     * {@code MinionAiController} for pulling/pushing between minions
     * without holding a direct reference to internal state.
     *
     * @param id the minion's database id
     * @param type the minion's type
     * @param pages the minion's live storage pages (index 0 first)
     */
    public record NearbyMinionView(long id, MinionType type, List<MinionStorage> pages) {
    }

    private static final class ActiveMinion {
        private final MinionData data;
        private List<MinionStorage> pages;
        private Entity entity;
        private long tickAccumulator;

        private ActiveMinion(MinionData data, List<MinionStorage> pages, Entity entity) {
            this.data = data;
            this.pages = pages;
            this.entity = entity;
        }
    }

    private final Map<Long, ActiveMinion> activeMinions = new ConcurrentHashMap<>();

    public MinionManager(Logger logger, MinionsDao minionsDao, MinionsConfig minionsConfig,
                          MinionFactory minionFactory, MinionAiController aiController,
                          MinionConnectorManager connectorManager) {
        this.logger = logger;
        this.minionsDao = minionsDao;
        this.minionsConfig = minionsConfig;
        this.minionFactory = minionFactory;
        this.aiController = aiController;
        this.connectorManager = connectorManager;
        registerHandlers();
    }

    private void registerHandlers() {
        handlers.put(MinionType.MINER, new MinerMinion());
        handlers.put(MinionType.LUMBERJACK, new LumberjackMinion());
        handlers.put(MinionType.FARMER, new FarmerMinion());
        handlers.put(MinionType.FISHERMAN, new FishermanMinion());
        handlers.put(MinionType.COLLECTOR, new CollectorMinion());
        handlers.put(MinionType.MOB_KILLER, new MobKillerMinion());
        handlers.put(MinionType.SMELTER, new SmelterMinion());
        handlers.put(MinionType.SELL, new SellMinion());
        handlers.put(MinionType.QUARRY, new QuarryMinion());
        handlers.put(MinionType.CHEST, new ChestMinion());
    }

    public MinionHandler getHandler(MinionType type) {
        return handlers.get(type);
    }

    public Map<MinionType, MinionHandler> getAllHandlers() {
        return handlers;
    }

    public void loadAll() {
        try {
            List<MinionData> allMinions = minionsDao.findAll();
            for (MinionData data : allMinions) {
                List<MinionStorage> pages = loadPagesForMinion(data);
                activeMinions.put(data.getId(), new ActiveMinion(data, pages, null));
            }
            logger.info("[EcoCore] Loaded " + activeMinions.size() + " minion records from database");
            int reattached = 0;
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    for (Entity entity : chunk.getEntities()) {
                        Long minionId = resolveMinionId(entity);
                        if (minionId != null && activeMinions.containsKey(minionId)) {
                            activeMinions.get(minionId).entity = entity;
                            reattached++;
                        }
                    }
                }
            }
            if (reattached > 0) {
                logger.info("[EcoCore] Reattached " + reattached + " minion entities from already-loaded chunks");
            }
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to load minions: " + exception.getMessage());
        }
    }

    /**
     * Loads a minion's storage pages, preferring the new multi-page
     * JSON format and falling back to migrating the legacy
     * single-page format forward (one-time, transparent) if the new
     * column is still empty for this row.
     */
    private List<MinionStorage> loadPagesForMinion(MinionData data) {
        String pagesJson;
        try {
            pagesJson = minionsDao.findStoragePagesJson(data.getId());
        } catch (SQLException exception) {
            pagesJson = null;
        }
        if (pagesJson != null && !pagesJson.isBlank()) {
            return deserializePages(data.getStoragePageCount(), pagesJson);
        }
        // Legacy fallback: migrate the old single flat storage array into page 0.
        List<MinionStorage> pages = new ArrayList<>();
        String legacyJson;
        try {
            legacyJson = minionsDao.findLegacyStorageJson(data.getId());
        } catch (SQLException exception) {
            legacyJson = null;
        }
        MinionStorage firstPage = MinionStorage.empty(0);
        if (legacyJson != null && !legacyJson.isBlank()) {
            ItemStack[] legacyContents = ItemUtils.deserialize(logger, legacyJson, MinionStorage.SLOTS_PER_PAGE);
            int copyLength = Math.min(legacyContents.length, MinionStorage.SLOTS_PER_PAGE);
            for (int i = 0; i < copyLength; i++) {
                firstPage.setSlot(i, legacyContents[i]);
            }
        }
        pages.add(firstPage);
        for (int i = 1; i < Math.max(1, data.getStoragePageCount()); i++) {
            pages.add(MinionStorage.empty(i));
        }
        return pages;
    }

    private List<MinionStorage> deserializePages(int pageCount, String pagesJson) {
        List<MinionStorage> pages = new ArrayList<>();
        String[] rawPages = pagesJson.split(PAGE_DELIMITER, -1);
        for (int i = 0; i < Math.max(1, pageCount); i++) {
            String rawPage = i < rawPages.length ? rawPages[i] : null;
            ItemStack[] contents = ItemUtils.deserialize(logger, rawPage, MinionStorage.SLOTS_PER_PAGE);
            if (contents.length != MinionStorage.SLOTS_PER_PAGE) {
                ItemStack[] resized = new ItemStack[MinionStorage.SLOTS_PER_PAGE];
                System.arraycopy(contents, 0, resized, 0, Math.min(contents.length, resized.length));
                contents = resized;
            }
            pages.add(new MinionStorage(i, contents));
        }
        return pages;
    }

    private String serializePages(List<MinionStorage> pages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            if (i > 0) {
                builder.append(PAGE_DELIMITER);
            }
            String serialized = ItemUtils.serialize(logger, pages.get(i).getContents());
            builder.append(serialized != null ? serialized : "");
        }
        return builder.toString();
    }

    private Entity spawnVisualEntity(Location location, MinionData data) {
        Villager villager = location.getWorld().spawn(location, Villager.class);
        villager.setBaby();
        villager.setAgeLock(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCanPickupItems(false);
        villager.setCustomNameVisible(true);
        villager.setCustomName(data.getType().configKey() + " Lv." + data.getLevel());
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        try {
            villager.setProfession(Villager.Profession.NONE);
        } catch (Throwable ignored) {
        }
        // Revisi 1: the entity's visual yaw always matches the minion's
        // stored facing, not free-look. This is set once at spawn and
        // never touched again - the AI controller must never rotate it.
        Location facingLocation = location.clone();
        facingLocation.setYaw(facingToYaw(data.getFacing()));
        facingLocation.setPitch(0f);
        villager.teleport(facingLocation);
        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), MINION_ID_KEY);
        villager.getPersistentDataContainer().set(key, PersistentDataType.LONG, data.getId());
        return villager;
    }

    /**
     * Converts a cardinal {@link BlockFace} into the yaw value that
     * makes an entity visually face that direction.
     *
     * @param facing a cardinal direction (NORTH/SOUTH/EAST/WEST)
     * @return the corresponding yaw
     */
    public static float facingToYaw(BlockFace facing) {
        return switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> 270f;
            default -> 0f;
        };
    }

    /**
     * Snaps a raw player yaw to the nearest cardinal {@link BlockFace}
     * (Revisi 1) - the same 4-way snap a piston or dispenser uses,
     * never a free 360-degree direction.
     *
     * @param yaw the raw yaw to snap
     * @return the nearest cardinal direction
     */
    public static BlockFace snapYawToCardinal(float yaw) {
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

    public Long resolveMinionId(Entity entity) {
        if (entity == null) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), MINION_ID_KEY);
        return entity.getPersistentDataContainer().get(key, PersistentDataType.LONG);
    }

    public void attachOrCleanupEntity(Entity entity) {
        Long minionId = resolveMinionId(entity);
        if (minionId == null) {
            return;
        }
        ActiveMinion active = activeMinions.get(minionId);
        if (active == null) {
            entity.remove();
            logger.info("[EcoCore] Removed an orphaned minion entity (database id " + minionId + " no longer exists).");
            return;
        }
        active.entity = entity;
    }

    private Entity resolveLiveEntity(ActiveMinion active) {
        if (active.entity != null && active.entity.isValid()) {
            return active.entity;
        }
        UUID entityUuid = active.data.getEntityUuid();
        if (entityUuid == null) {
            return null;
        }
        Entity resolved = Bukkit.getEntity(entityUuid);
        if (resolved != null && resolved.isValid()) {
            active.entity = resolved;
            return resolved;
        }
        return null;
    }

    public long countOwnedBy(UUID ownerUuid) {
        return activeMinions.values().stream()
                .filter(active -> active.data.getOwnerUuid().equals(ownerUuid))
                .count();
    }

    /**
     * Places a new minion, locking its facing to the nearest cardinal
     * direction from the placing player's look yaw (Revisi 1). The
     * minion never moves or re-orients after this point.
     *
     * @param player the placing player
     * @param type the minion type to place
     * @param location the placement location (its yaw is read and snapped, then discarded)
     * @return the newly placed minion's data, or {@code null} if placement failed
     */
    public MinionData placeMinion(Player player, MinionType type, Location location) {
        if (countOwnedBy(player.getUniqueId()) >= DEFAULT_MAX_MINIONS_PER_PLAYER) {
            return null;
        }
        BlockFace facing = snapYawToCardinal(location.getYaw());
        MinionData data = minionFactory.create(player.getUniqueId(), type, location, facing);
        try {
            List<MinionStorage> pages = new ArrayList<>();
            pages.add(MinionStorage.empty(0));
            String pagesJson = serializePages(pages);
            long id = minionsDao.insert(data, pagesJson);
            MinionData persisted = new MinionData(
                    id, data.getOwnerUuid(), data.getType(), data.getLevel(), data.getXp(),
                    data.getEnergy(), data.getFuelTicksRemaining(), data.getWorld(),
                    data.getX(), data.getY(), data.getZ(), data.getRadius(),
                    data.getSpeedTicks(), data.isAutoRepair(), data.getFacing(),
                    data.isUseArenaMode(), data.getStoragePageCount(),
                    data.getCreatedAt(), data.getUpdatedAt(), null
            );
            Entity entity = spawnVisualEntity(location, persisted);
            persisted.setEntityUuid(entity.getUniqueId());
            try {
                minionsDao.updateEntityUuid(id, entity.getUniqueId());
            } catch (SQLException exception) {
                logger.warning("[EcoCore] Failed to persist entity uuid for minion " + id + ": " + exception.getMessage());
            }
            activeMinions.put(id, new ActiveMinion(persisted, pages, entity));
            return persisted;
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to place minion for " + player.getUniqueId() + ": " + exception.getMessage());
            return null;
        }
    }

    public boolean removeAndRefund(long minionId, Player player) {
        ActiveMinion active = activeMinions.remove(minionId);
        if (active == null) {
            return false;
        }
        connectorManager.removeAllInvolving(minionId);
        Entity entity = resolveLiveEntity(active);
        if (entity != null) {
            entity.remove();
        }
        try {
            minionsDao.delete(minionId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to delete minion " + minionId + ": " + exception.getMessage());
        }
        for (MinionStorage page : active.pages) {
            for (ItemStack stack : page.getContents()) {
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
                for (ItemStack over : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), over);
                }
            }
        }
        ItemStack egg = ItemUtils.buildMinionEgg(active.data.getType(), minionsConfig);
        Map<Integer, ItemStack> eggLeftover = player.getInventory().addItem(egg);
        for (ItemStack over : eggLeftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), over);
        }
        return true;
    }

    public List<MinionData> getMinionsOwnedBy(UUID ownerUuid) {
        return activeMinions.values().stream()
                .filter(active -> active.data.getOwnerUuid().equals(ownerUuid))
                .map(active -> active.data)
                .collect(Collectors.toList());
    }

    public MinionData getMinion(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        return active != null ? active.data : null;
    }

    public void refreshMinionDisplay(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        if (active == null) {
            return;
        }
        Entity entity = resolveLiveEntity(active);
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.setCustomNameVisible(true);
            livingEntity.setCustomName(active.data.getType().configKey() + " Lv." + active.data.getLevel());
        }
    }

    /**
     * Returns the minion's storage pages (index 0 first). Never
     * returns {@code null} for a known minion - always at least one page.
     */
    public List<MinionStorage> getMinionPages(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        return active != null ? active.pages : null;
    }

    /**
     * Unlocks one additional storage page for a minion (Revisi 11),
     * up to the configured max. No-op if already at max.
     *
     * @param minionId the minion to grant a page to
     * @param maxPages the configured maximum page count
     * @return {@code true} if a new page was added
     */
    public boolean addStoragePage(long minionId, int maxPages) {
        ActiveMinion active = activeMinions.get(minionId);
        if (active == null || active.pages.size() >= maxPages) {
            return false;
        }
        active.pages.add(MinionStorage.empty(active.pages.size()));
        active.data.setStoragePageCount(active.pages.size());
        return true;
    }

    public Entity getMinionEntity(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        return active != null ? resolveLiveEntity(active) : null;
    }

    /**
     * Finds owned minions within radius of an origin point, excluding
     * one minion id (typically the caller itself).
     */
    public List<NearbyMinionView> getNearbyOwnedMinions(Location origin, double radius, UUID ownerUuid, long excludeId) {
        List<NearbyMinionView> results = new ArrayList<>();
        if (origin.getWorld() == null) {
            return results;
        }
        double radiusSq = radius * radius;
        String worldName = origin.getWorld().getName();
        for (ActiveMinion active : activeMinions.values()) {
            MinionData otherData = active.data;
            if (otherData.getId() == excludeId) {
                continue;
            }
            if (!otherData.getOwnerUuid().equals(ownerUuid)) {
                continue;
            }
            if (!otherData.getWorld().equals(worldName)) {
                continue;
            }
            double dx = otherData.getX() - origin.getX();
            double dy = otherData.getY() - origin.getY();
            double dz = otherData.getZ() - origin.getZ();
            double distSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (distSq <= radiusSq) {
                results.add(new NearbyMinionView(otherData.getId(), otherData.getType(), active.pages));
            }
        }
        return results;
    }

    public void tickAll(long elapsedGameTicks) {
        for (ActiveMinion active : activeMinions.values()) {
            MinionHandler handler = handlers.get(active.data.getType());
            if (handler == null) {
                continue;
            }
            Entity entity = resolveLiveEntity(active);
            if (entity == null) {
                continue;
            }
            int speedTicks = Math.max(1, active.data.getSpeedTicks());
            active.tickAccumulator += elapsedGameTicks;
            int actionsToRun = (int) Math.min(active.tickAccumulator / speedTicks, MAX_CATCH_UP_ACTIONS_PER_PASS);
            active.tickAccumulator %= speedTicks;
            for (int i = 0; i < actionsToRun; i++) {
                aiController.tick(active.data, handler, entity, active.pages);
            }
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setCustomName(active.data.getType().configKey() + " Lv." + active.data.getLevel());
            }
        }
    }

    public void saveAll() {
        for (ActiveMinion active : activeMinions.values()) {
            try {
                String pagesJson = serializePages(active.pages);
                minionsDao.update(active.data, pagesJson);
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to save minion " + active.data.getId() + ": " + exception.getMessage());
            }
        }
    }

    public MinionsConfig getMinionsConfig() {
        return minionsConfig;
    }
}
