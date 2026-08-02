package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.database.dao.MinionsDao;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.minions.types.AnimalFarmerMinion;
import io.azthera.ecocore.minions.types.BreederMinion;
import io.azthera.ecocore.minions.types.CollectorMinion;
import io.azthera.ecocore.minions.types.CrafterMinion;
import io.azthera.ecocore.minions.types.FarmerMinion;
import io.azthera.ecocore.minions.types.FishingMinion;
import io.azthera.ecocore.minions.types.HarvesterMinion;
import io.azthera.ecocore.minions.types.LumberjackMinion;
import io.azthera.ecocore.minions.types.MinerMinion;
import io.azthera.ecocore.minions.types.MinionHandler;
import io.azthera.ecocore.minions.types.MobKillerMinion;
import io.azthera.ecocore.minions.types.PlanterMinion;
import io.azthera.ecocore.minions.types.QuarryMinion;
import io.azthera.ecocore.minions.types.SellerMinion;
import io.azthera.ecocore.minions.types.SmelterMinion;
import io.azthera.ecocore.minions.types.StorageMinion;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.sell.SellManager;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
 */
public final class MinionManager {

    /** Default cap on how many minions a single player may place; server owners can raise this via a future permission-based override. */
    public static final int DEFAULT_MAX_MINIONS_PER_PLAYER = 20;

    private final Logger logger;
    private final MinionsDao minionsDao;
    private final MinionsConfig minionsConfig;
    private final MinionFactory minionFactory;
    private final MinionAiController aiController;

    private final Map<MinionType, MinionHandler> handlers = new EnumMap<>(MinionType.class);

    /**
     * One runtime entry per active minion: its persistent data, live
     * storage contents, and spawned visual entity.
     *
     * @param data    the minion's persistent data
     * @param storage the minion's live (in-memory) storage contents
     * @param entity  the minion's spawned visual entity in the world
     */
    private record ActiveMinion(MinionData data, ItemStack[] storage, Entity entity) {
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
     */
    public MinionManager(Logger logger, MinionsDao minionsDao, MinionsConfig minionsConfig,
                          MinionFactory minionFactory, MinionAiController aiController) {
        this.logger = logger;
        this.minionsDao = minionsDao;
        this.minionsConfig = minionsConfig;
        this.minionFactory = minionFactory;
        this.aiController = aiController;

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
        handlers.put(MinionType.CRAFTER, new CrafterMinion());
        handlers.put(MinionType.STORAGE, new StorageMinion());
        handlers.put(MinionType.SELLER, new SellerMinion());
        handlers.put(MinionType.HARVESTER, new HarvesterMinion());
        handlers.put(MinionType.PLANTER, new PlanterMinion());
        handlers.put(MinionType.BREEDER, new BreederMinion());
        handlers.put(MinionType.QUARRY, new QuarryMinion());
    }

    public MinionHandler getHandler(MinionType type) {
        return handlers.get(type);
    }

    /**
     * Loads every persisted minion from the database and spawns their
     * visual entities, called once during plugin enable.
     */
    public void loadAll() {
        try {
            List<MinionData> allMinions = minionsDao.findAll();
            for (MinionData data : allMinions) {
                spawnRuntime(data);
            }
            logger.info("[EcoCore] Loaded " + activeMinions.size() + " active minions");
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to load minions: " + exception.getMessage());
        }
    }

    private void spawnRuntime(MinionData data) {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(data.getWorld());
        if (world == null) {
            return;
        }
        Location location = new Location(world, data.getX(), data.getY(), data.getZ());

        String storageJson;
        try {
            storageJson = minionsDao.findStorageJson(data.getId());
        } catch (SQLException exception) {
            storageJson = null;
        }
        ItemStack[] storage = ItemUtils.deserialize(logger, storageJson, data.getStorageSlots());

        Entity entity = spawnVisualEntity(location, data);
        activeMinions.put(data.getId(), new ActiveMinion(data, storage, entity));
    }

    private Entity spawnVisualEntity(Location location, MinionData data) {
        org.bukkit.entity.ArmorStand standEntity = location.getWorld().spawn(location, org.bukkit.entity.ArmorStand.class);
        standEntity.setInvisible(false);
        standEntity.setGravity(false);
        standEntity.setSmall(true);
        standEntity.setCustomNameVisible(true);
        standEntity.setCustomName(data.getType().configKey() + " Lv." + data.getLevel());
        standEntity.setBasePlate(false);
        standEntity.setMarker(false);
        return standEntity;
    }

    /**
     * Returns the number of minions a player currently owns.
     *
     * @param ownerUuid the player's uuid
     * @return the count of that player's active minions
     */
    public long countOwnedBy(UUID ownerUuid) {
        return activeMinions.values().stream()
                .filter(active -> active.data().getOwnerUuid().equals(ownerUuid))
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
                    data.getCreatedAt(), data.getUpdatedAt()
            );

            Entity entity = spawnVisualEntity(location, persisted);
            ItemStack[] storage = new ItemStack[persisted.getStorageSlots()];
            activeMinions.put(id, new ActiveMinion(persisted, storage, entity));
            return persisted;
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to place minion for " + player.getUniqueId() + ": " + exception.getMessage());
            return null;
        }
    }

    /**
     * Removes a minion permanently: despawns its visual entity, drops
     * or returns its remaining storage contents is left to the
     * caller (the returned array), and deletes it from the database.
     *
     * @param minionId the minion's database id
     * @return the minion's remaining storage contents at time of removal, or {@code null} if it wasn't active
     */
    public ItemStack[] removeMinion(long minionId) {
        ActiveMinion active = activeMinions.remove(minionId);
        if (active == null) {
            return null;
        }

        active.entity().remove();
        try {
            minionsDao.delete(minionId);
        } catch (SQLException exception) {
            logger.severe("[EcoCore] Failed to delete minion " + minionId + ": " + exception.getMessage());
        }
        return active.storage();
    }

    /**
     * Returns every active minion owned by a player.
     *
     * @param ownerUuid the player's uuid
     * @return that player's active minion data
     */
    public List<MinionData> getMinionsOwnedBy(UUID ownerUuid) {
        return activeMinions.values().stream()
                .filter(active -> active.data().getOwnerUuid().equals(ownerUuid))
                .map(ActiveMinion::data)
                .collect(Collectors.toList());
    }

    /**
     * Returns a single active minion's data by id.
     *
     * @param minionId the minion's database id
     * @return the minion's data, or {@code null} if not active
     */
    public MinionData getMinion(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        return active != null ? active.data() : null;
    }

    /**
     * Returns a single active minion's live storage array by id.
     *
     * @param minionId the minion's database id
     * @return the storage array, or {@code null} if not active
     */
    public ItemStack[] getMinionStorage(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        return active != null ? active.storage() : null;
    }

    /**
     * Ticks every active minion once, called by {@code MinionTickScheduler}.
     */
    public void tickAll() {
        for (ActiveMinion active : activeMinions.values()) {
            MinionHandler handler = handlers.get(active.data().getType());
            if (handler == null || active.entity() == null || !active.entity().isValid()) {
                continue;
            }
            boolean ownerOnline = org.bukkit.Bukkit.getPlayer(active.data().getOwnerUuid()) != null;
            aiController.tick(active.data(), handler, active.entity(), active.storage(), ownerOnline);
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
                String storageJson = ItemUtils.serialize(logger, active.storage());
                minionsDao.update(active.data(), storageJson);
            } catch (SQLException exception) {
                logger.severe("[EcoCore] Failed to save minion " + active.data().getId() + ": " + exception.getMessage());
            }
        }
    }

    public MinionsConfig getMinionsConfig() {
        return minionsConfig;
    }
}