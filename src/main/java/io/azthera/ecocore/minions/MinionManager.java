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

public final class MinionManager {

    public static final int DEFAULT_MAX_MINIONS_PER_PLAYER = 20;

    private static final String MINION_ID_KEY = "minion_id";
    private static final String FACING_MODE_KEY = "minion_facing_mode";

    private static final int MAX_CATCH_UP_ACTIONS_PER_PASS = 50;

    private final Logger logger;
    private final MinionsDao minionsDao;
    private final MinionsConfig minionsConfig;
    private final MinionFactory minionFactory;
    private final MinionAiController aiController;
    private final MinionConnectorManager connectorManager;

    private final Map<MinionType, MinionHandler> handlers = new EnumMap<>(MinionType.class);

    public record NearbyMinionView(long id, MinionType type, ItemStack[] storage) {
    }

    private static final class ActiveMinion {
        private final MinionData data;
        private ItemStack[] storage;
        private Entity entity;
        private long tickAccumulator;

        private ActiveMinion(MinionData data, ItemStack[] storage, Entity entity) {
            this.data = data;
            this.storage = storage;
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
        handlers.put(MinionType.FISHING, new FishingMinion());
        handlers.put(MinionType.COLLECTOR, new CollectorMinion());
        handlers.put(MinionType.MOB_KILLER, new MobKillerMinion());
        handlers.put(MinionType.ANIMAL_FARMER, new AnimalFarmerMinion());
        handlers.put(MinionType.SMELTER, new SmelterMinion());
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

        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), MINION_ID_KEY);
        villager.getPersistentDataContainer().set(key, PersistentDataType.LONG, data.getId());

        return villager;
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
            logger.info("[EcoCore] Removed an orphaned minion entity (database id "
                    + minionId + " no longer exists).");
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

    public void resizeStorage(long minionId, int newSize) {
        ActiveMinion active = activeMinions.get(minionId);
        if (active == null || newSize <= active.storage.length) {
            return;
        }

        ItemStack[] resized = new ItemStack[newSize];
        System.arraycopy(active.storage, 0, resized, 0, active.storage.length);
        active.storage = resized;
    }

    public Entity getMinionEntity(long minionId) {
        ActiveMinion active = activeMinions.get(minionId);
        return active != null ? resolveLiveEntity(active) : null;
    }

    public boolean isFacingModeEnabled(long minionId) {
        Entity entity = getMinionEntity(minionId);
        if (entity == null) {
            return false;
        }

        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), FACING_MODE_KEY);
        Byte value = entity.getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public void setFacingModeEnabled(long minionId, boolean enabled) {
        Entity entity = getMinionEntity(minionId);
        if (entity == null) {
            return;
        }

        NamespacedKey key = new NamespacedKey(EcoCorePlugin.getInstance(), FACING_MODE_KEY);
        entity.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

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
                aiController.tick(active.data, handler, entity, active.storage);
            }

            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setCustomName(active.data.getType().configKey() + " Lv." + active.data.getLevel());
            }
        }
    }

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
