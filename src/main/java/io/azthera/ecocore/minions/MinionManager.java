package io.azthera.ecocore.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.database.dao.MinionsDao;
import io.azthera.ecocore.minions.types.AnimalFarmerMinion;
import io.azthera.ecocore.minions.types.BreederMinion;
import io.azthera.ecocore.minions.types.CollectorMinion;
import io.azthera.ecocore.minions.types.FarmerMinion;
import io.azthera.ecocore.minions.types.FishingMinion;
import io.azthera.ecocore.minions.types.LumberjackMinion;
import io.azthera.ecocore.minions.types.MinerMinion;
import io.azthera.ecocore.minions.types.MinionChestMinion;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.minions.types.MobKillerMinion;
import io.azthera.ecocore.minions.types.QuarryMinion;
import io.azthera.ecocore.minions.types.SellerMinion;
import io.azthera.ecocore.minions.types.SmelterMinion;
import io.azthera.ecocore.minions.types.StorageMinion;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
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

/**
 * Top-level facade for EcoCore's Minions system: owns every active
 * placed minion's runtime state (persistent data, visual entity,
 * live storage array), every {@link MinionHandler}, and the
 * placement/removal lifecycle. {@code MinionTickScheduler} calls
 * {@link #tickAll()} once per configured tick interval.
 *
 * <p>Every placed minion is a real, visible, interactable baby
 * {@link Villager} entity in the world, tagged with its database id
 * via persistent data. Its Bukkit entity uuid is also stored in
 * {@link MinionData}, because a long-held Java {@code Entity}
 * reference can go stale the moment its chunk unloads and reloads -
 * {@link #resolveLiveEntity} always re-checks validity and, if
 * stale, re-resolves the CURRENT live entity via
 * {@code Bukkit.getEntity(uuid)} rather than trusting the cached
 * reference. {@link #loadAll()} therefore never eagerly spawns a
 * brand-new entity for a previously-placed minion (that would create
 * a visible duplicate standing next to the one Minecraft already
 * reloaded from the chunk's own save data) - it only loads the data
 * and waits for {@link #attachOrCleanupEntity} (driven by
 * {@code MinionChunkListener}) to pick the real entity back up once
 * its chunk becomes loaded.
 */
public final class MinionManager {

    /** Default cap on how many minions a single player may place. */
    public static final int DEFAULT_MAX_MINIONS_PER_PLAYER = 20;

    private static final String MINION_ID_KEY = "minion_id";

    /**
     * Persistent-data key tagging whether a block-break minion is in
     * "facing mode" (mines in a straight line in the direction its
     * entity is facing) rather than the default "arena mode" (mines
     * the nearest match anywhere within its radius). Stored directly
     * on the minion's visual entity rather than in the database, so
     * no schema migration is needed and it survives world saves the
     * same way the entity's own position/rotation does.
     */
    private static final String FACING_MODE_KEY = "minion_facing_mode";

    private final Logger logger;
    private final MinionsDao minionsDao;
    private final MinionsConfig minionsConfig;
    private final MinionFactory minionFactory;
    private final MinionAiController aiController;
    private final MinionConnectorManager connectorManager;

    private final Map<MinionType, MinionHandler> handlers = new EnumMap<>(MinionType.class);

    /**
     * A view of a nearby minion exposed to other minions' AI (used by
     * the Collector's cross-minion pulling behavior), carrying just
     * enough to identify it and reach into its live storage array.
     *
     * @param id      the minion's database id
     * @param type    the minion's type
     * @param storage the minion's live storage array (mutating this affects the real minion)
     */
    public record NearbyMinionView(long id, MinionType type, ItemStack[] storage) {
    }

    /**
     * One runtime entry per active minion: its persistent data, live
     * storage contents, and currently-known visual entity (may be
     * {@code null} or stale - always resolve via {@link #resolveLiveEntity}).
     */
    private static final class ActiveMinion {
        private final MinionData data;
        private final ItemStack[] storage;
        private Entity entity;

        private ActiveMinion(MinionData data, ItemStack[] storage, Entity entity) {
            this.data = data;
            this.storage = storage;
            this.entity = entity;
        }
    }

    private final Map<Long, ActiveMinion> activeMinions = new ConcurrentHashMap<>();

    /**
     * Creates the minions manager and every minion-type handler.
     *
     * @param logger        plugin logger
     * @param minionsDao    DAO for minion persistence
     * @param minionsConfig resolved minions.yml configuration
     * @param minionFactory factory for new minion data
     * @param aiController  shared AI controller ticking every active minion
     * @param connectorManager shared Connector Network manager, used to clean up connections on removal
     */
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
        handlers.put(MinionType.FISHING, new FishingMinion());
        handlers.put(MinionType.COLLECTOR, new CollectorMinion());
        handlers.put(MinionType.MOB_KILLER, new MobKillerMinion());
        handlers.put(MinionType.ANIMAL_FARMER, new AnimalFarmerMinion());
        handlers.put(MinionType.SMELTER, new SmelterMinion());
        handlers.put(MinionType.STORAGE, new StorageMinion());
        handlers.put(MinionType.SELLER, new SellerMinion());
        handlers.put(MinionType.BREEDER, new BreederMinion());
        handlers.put(MinionType.QUARRY, new QuarryMinion());
        handlers.put(MinionType.MINION_CHEST, new MinionChestMinion());
    }

    public MinionHandler getHandler(MinionType type) {
        return handlers.get(type);
    }

    public Map<MinionType, MinionHandler> getAllHandlers() {
        return handlers;
    }

    /**
     * Loads every persisted minion's data (NOT its entity) from the
     * database, then does one pass over every currently-loaded chunk
     * to catch entities whose {@code ChunkLoadEvent} may have already
     * fired before this plugin's listener registered (e.g. spawn
     * chunks present at boot). Any minion whose chunk isn't loaded
     * yet simply waits for {@link #attachOrCleanupEntity} to pick it
     * up naturally once a player wanders close enough.
     */
    public void loadAll() {
        try {
            List<MinionData> allMinions = minionsDao.findAll();
            for (MinionData data : allMinions) {
                String storageJson;
                try {
                    storageJson = minionsDao.findStorageJson(data.getId());
                } catch (SQLException exception) {
                    storageJson = null;
                }
                ItemStack[] storage = ItemUtils.deserialize(logger, storageJson, data.getStorageSlots());
                activeMinions.put(data.getId(), new ActiveMinion(data, storage, null));
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
     * Spawns a minion's visual entity: a permanent baby villager,
     * with AI disabled and age-locked so it never moves or grows up.
     * Tagged with the minion's database id via persistent data.
     *
     * @param location the location to spawn at
     * @param data     the minion's data (must already have a real database id)
     * @return the spawned entity
     */
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
            // Some server builds restrict profession changes on freshly spawned babies; safe to ignore.
        }

        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), MINION_ID_KEY);
        villager.getPersistentDataContainer().set(key, PersistentDataType.LONG, data.getId());

        return villager;
    }

    /**
     * Resolves the minion database id tagged on an entity.
     *
     * @param entity the entity to check
     * @return the minion's database id, or {@code null} if this entity isn't a minion
     */
    public Long resolveMinionId(Entity entity) {
        if (entity == null) {
            return null;
        }
        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), MINION_ID_KEY);
        return entity.getPersistentDataContainer().get(key, PersistentDataType.LONG);
    }

    /**
     * Called by {@code MinionChunkListener} for every entity in a
     * freshly loaded chunk. If the entity is tagged as a minion whose
     * database record still exists, re-attaches it as that minion's
     * current live entity. If the tagged id has NO matching record
     * (the minion was deleted some other way, e.g. direct database
     * editing), the entity is an orphan and gets removed - this
     * self-heals any ghost entities left behind by earlier bugs too.
     *
     * @param entity the entity to check and possibly attach/clean up
     */
    public void attachOrCleanupEntity(Entity entity) {
        Long minionId = resolveMinionId(entity);
        if (minionId == null) {
            return;
        }

        ActiveMinion active = activeMinions.get(minionId);
        if (active == null) {
            entity.remove();
            logger.info("[EcoCore] Removed an orphaned minion entity (database id "
                    + minionId + " no longer exists).");
            return;
        }

        active.entity = entity;
    }

    /**
     * Resolves the current live entity for an active minion, healing
     * a stale cached reference by re-looking it up via uuid if needed.
     *
     * @param active the minion runtime entry to resolve
     * @return the live entity, or {@code null} if its chunk isn't currently loaded
     */
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

    /**
     * Returns the number of minions a player currently owns.
     *
     * @param ownerUuid the player's uuid
     * @return the count of that player's active minions
     */
    public long countOwnedBy(UUID ownerUuid) {
        return activeMinions.values().stream()
                .filter(active -> active.data.getOwnerUuid().equals(ownerUuid))
                .count();
    }

    /**
     * Places a new minion for a player at the given location, if they
     * are under their minion limit.
     *
     * @param player   the placing player
     * @param type     the minion type to place
     * @param location the placement location
     * @return the newly placed minion's data, or {@code null} if the player is at their limit or persistence failed
     */
    public MinionData placeMinion(Player player, MinionType type, Location location) {
        if (countOwnedBy(player.getUniqueId()) >= DEFAULT_MAX_MINIONS_PER_PLAYER) {
            return null;
        }

        MinionData data = minionFactory.create(player.getUniqueId(), type, location);
        try {
            long id = minionsDao.insert(data, null);

            MinionData persisted = new MinionData(
                    id, data.getOwnerUuid(), data.getType(), data.getLevel(), data.getXp(),
                    data.getEnergy(), data.getFuelTicksRemaining(), data.getWorld(),
                    data.getX(), data.getY(), data.getZ(), data.getStorageSlots(), data.getRadius(),
                    data.getSpeedTicks(), data.isAutoRepair(), data.isAutoSell(), data.isAutoSmelt(),
                    data.getCreatedAt(), data.getUpdatedAt(), null
            );

            Entity entity = spawnVisualEntity(location, persisted);
            persisted.setEntityUuid(entity.getUniqueId());

            try {
                minionsDao.updateEntityUuid(id, entity.getUniqueId());
            } catch (SQLException exception) {
                logger.warning("[EcoCore] Failed to persist entity uuid for minion " + id + ": " + exception.getMessage());
            }

            ItemStack[] storage = new ItemStack[persisted.getStorageSlots()];
            activeMinions.put(id, new ActiveMinion(persisted, storage, entity));
            return persisted;
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to place minion for " + player.getUniqueId() + ": " + exception.getMessage());
            return null;
        }
    }

    /**
     * Removes a minion permanently: despawns its live entity (found
     * fresh via its stored uuid, not a possibly-stale cached
     * reference), deletes its database row, returns any leftover
     * storage contents to the player (dropping overflow on the
     * ground if their inventory is full), and gives them back a
     * matching Minion Egg item so they can place it again elsewhere.
     *
     * @param minionId the minion's database id
     * @param player   the player performing the removal, who receives the egg and any leftover storage
     * @return {@code true} if a minion was found and removed
     */
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

        for (ItemStack stack : active.storage) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack over : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), over);
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

    /**
     * Immediately re-applies a minion's custom-name/level display to
     * its live entity, instead of waiting for the next scheduled
     * {@link #tickAll} pass. Call this right after any change that
     * should be visible on the minion right away (e.g. an upgrade
     * purchase) - without it, the entity's nametag only refreshes on
     * its next tick, which for a minion with a long speed interval can
     * look like it's "stuck" showing stale info even though the GUI
     * (which reads the data object directly) already shows the change.
     *
     * @param minionId the minion whose display should be refreshed
     */
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

    public ItemStack[] getMinionStorage(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        return active != null ? active.storage : null;
    }

    /**
     * Resolves the current live entity for an active minion by id,
     * for callers outside this class (e.g. GUIs reading/writing the
     * facing-mode flag). Re-uses the same stale-reference healing as
     * {@link #resolveLiveEntity}.
     *
     * @param minionId the minion's database id
     * @return the live entity, or {@code null} if the minion isn't
     *         active or its chunk isn't currently loaded
     */
    public Entity getMinionEntity(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        return active != null ? resolveLiveEntity(active) : null;
    }

    /**
     * Whether a block-break minion currently has "facing mode"
     * enabled (mines in a straight line in the direction it's facing,
     * instead of the nearest match anywhere in its radius).
     *
     * @param minionId the minion's database id
     * @return {@code true} if facing mode is on; {@code false} if
     *         off, unset, or the minion's entity isn't currently
     *         loaded (defaults to arena mode, its previous-only behavior)
     */
    public boolean isFacingModeEnabled(long minionId) {
        Entity entity = getMinionEntity(minionId);
        if (entity == null) {
            return false;
        }

        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), FACING_MODE_KEY);
        Byte value = entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    /**
     * Toggles a block-break minion's facing-mode flag. No-ops
     * silently if the minion's entity isn't currently loaded (the
     * player would need to be standing near an unloaded minion's GUI
     * anyway, which shouldn't normally happen).
     *
     * @param minionId the minion's database id
     * @param enabled  the new facing-mode value
     */
    public void setFacingModeEnabled(long minionId, boolean enabled) {
        Entity entity = getMinionEntity(minionId);
        if (entity == null) {
            return;
        }

        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), FACING_MODE_KEY);
        entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    /**
     * Returns every other minion belonging to the same owner within a
     * radius of a location, for the Collector's cross-minion pulling
     * behavior. Distance is computed from each minion's stored
     * coordinates directly (not its live entity location), so this
     * works correctly even for minions whose chunk isn't currently loaded.
     *
     * @param origin    the searching minion's location
     * @param radius    the search radius in blocks
     * @param ownerUuid the owner to match
     * @param excludeId the searching minion's own id, excluded from results
     * @return the matching nearby minions
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
                results.add(new NearbyMinionView(otherData.getId(), otherData.getType(), active.storage));
            }
        }

        return results;
    }

    /**
     * Ticks every active minion once, called by {@code MinionTickScheduler}.
     * Minions whose chunk isn't currently loaded are skipped for this tick.
     */
    public void tickAll() {
        for (ActiveMinion active : activeMinions.values()) {
            MinionHandler handler = handlers.get(active.data.getType());
            if (handler == null) {
                continue;
            }

            Entity entity = resolveLiveEntity(active);
            if (entity == null) {
                continue;
            }

            aiController.tick(active.data, handler, entity, active.storage);

            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setCustomName(active.data.getType().configKey() + " Lv." + active.data.getLevel());
            }
        }
    }

    /**
     * Persists every active minion's current state and storage
     * contents back to the database, called periodically by
     * {@code AutoSaveScheduler} and on plugin disable.
     */
    public void saveAll() {
        for (ActiveMinion active : activeMinions.values()) {
            try {
                String storageJson = ItemUtils.serialize(logger, active.storage);
                minionsDao.update(active.data, storageJson);
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to save minion " + active.data.getId() + ": " + exception.getMessage());
            }
        }
    }

    public MinionsConfig getMinionsConfig() {
        return minionsConfig;
    }
    }
