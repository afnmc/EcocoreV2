package io.azthera.ecocore.model;

import org.bukkit.block.BlockFace;

import java.util.UUID;

/**
 * Tracks a single placed minion's persistent state.
 * Mirrors a row in the {@code minions_data} table.
 */
public final class MinionData {

    private final long id;
    private final UUID ownerUuid;
    private final MinionType type;
    private int level;
    private long xp;
    private int energy;
    private int fuelTicksRemaining;
    private String world;
    private double x;
    private double y;
    private double z;
    private int radius;
    private int speedTicks;
    private boolean autoRepair;
    private long createdAt;
    private long updatedAt;

    /**
     * The cardinal direction this minion was locked to when placed
     * (Revisi 1). Never NORTH/SOUTH/EAST/WEST-adjacent diagonals -
     * always snapped to one of the four cardinal faces. Never changed
     * by the AI after placement; only re-placing the minion changes it.
     */
    private BlockFace facing;

    /**
     * For minion types whose {@link MinionWorkMode} is BOTH, whether
     * the player has toggled this specific minion into arena mode
     * (360-degree radius) instead of the default facing-only mode.
     * Meaningless for types locked to FACING_ONLY/ARENA_ONLY/NONE.
     */
    private boolean useArenaMode;

    /**
     * How many {@link MinionStorage} pages (54 slots each) this
     * minion currently has unlocked, from 1 up to
     * {@code minions.yml global.max-storage-pages} (Revisi 11).
     */
    private int storagePageCount;

    /**
     * The world-persistent Bukkit entity UUID this minion's visual
     * villager entity is tagged with. Never trust a cached
     * {@code Entity} Java object reference across time (it can go
     * stale when its chunk unloads/reloads) - always re-resolve the
     * live entity via {@code Bukkit.getEntity(entityUuid)} when
     * acting on it. {@code null} until the minion's entity has
     * actually been spawned at least once.
     */
    private UUID entityUuid;

    /**
     * Creates minion persistent data.
     *
     * @param id database row id, or -1 if not yet persisted
     * @param ownerUuid the owning player's uuid
     * @param type the minion type
     * @param level current minion level
     * @param xp current experience
     * @param energy current stored energy
     * @param fuelTicksRemaining remaining fuel burn time in ticks
     * @param world world name the minion is placed in
     * @param x x coordinate
     * @param y y coordinate
     * @param z z coordinate
     * @param radius current work radius in blocks
     * @param speedTicks ticks between minion actions (lower is faster)
     * @param autoRepair whether auto-repair is enabled
     * @param facing the cardinal direction locked in at placement
     * @param useArenaMode whether a BOTH-mode minion is currently set to arena mode
     * @param storagePageCount how many 54-slot storage pages are unlocked
     * @param createdAt epoch millis when placed
     * @param updatedAt epoch millis of the last update
     * @param entityUuid the minion's tagged visual entity uuid, or {@code null} if never spawned
     */
    public MinionData(long id, UUID ownerUuid, MinionType type, int level, long xp, int energy,
                       int fuelTicksRemaining, String world, double x, double y, double z,
                       int radius, int speedTicks, boolean autoRepair,
                       BlockFace facing, boolean useArenaMode, int storagePageCount,
                       long createdAt, long updatedAt, UUID entityUuid) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.type = type;
        this.level = level;
        this.xp = xp;
        this.energy = energy;
        this.fuelTicksRemaining = fuelTicksRemaining;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.speedTicks = speedTicks;
        this.autoRepair = autoRepair;
        this.facing = facing != null ? facing : BlockFace.SOUTH;
        this.useArenaMode = useArenaMode;
        this.storagePageCount = Math.max(1, storagePageCount);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.entityUuid = entityUuid;
    }

    public long getId() {
        return id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public MinionType getType() {
        return type;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        touch();
    }

    public long getXp() {
        return xp;
    }

    public void addXp(long amount) {
        this.xp += amount;
        touch();
    }

    public int getEnergy() {
        return energy;
    }

    public boolean consumeEnergy(int amount) {
        if (energy < amount) {
            return false;
        }
        energy -= amount;
        touch();
        return true;
    }

    public void refillEnergy(int amount) {
        this.energy += amount;
        touch();
    }

    public int getFuelTicksRemaining() {
        return fuelTicksRemaining;
    }

    public void setFuelTicksRemaining(int fuelTicksRemaining) {
        this.fuelTicksRemaining = fuelTicksRemaining;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setLocation(String world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        touch();
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
        touch();
    }

    public int getSpeedTicks() {
        return speedTicks;
    }

    public void setSpeedTicks(int speedTicks) {
        this.speedTicks = speedTicks;
        touch();
    }

    public boolean isAutoRepair() {
        return autoRepair;
    }

    public void setAutoRepair(boolean autoRepair) {
        this.autoRepair = autoRepair;
    }

    public BlockFace getFacing() {
        return facing;
    }

    public void setFacing(BlockFace facing) {
        this.facing = facing;
        touch();
    }

    public boolean isUseArenaMode() {
        return useArenaMode;
    }

    public void setUseArenaMode(boolean useArenaMode) {
        this.useArenaMode = useArenaMode;
        touch();
    }

    public int getStoragePageCount() {
        return storagePageCount;
    }

    public void setStoragePageCount(int storagePageCount) {
        this.storagePageCount = storagePageCount;
        touch();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public void setEntityUuid(UUID entityUuid) {
        this.entityUuid = entityUuid;
        touch();
    }

    private void touch() {
        this.updatedAt = System.currentTimeMillis();
    }
}
