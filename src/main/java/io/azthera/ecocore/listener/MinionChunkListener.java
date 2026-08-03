package io.azthera.ecocore.listener;

import io.azthera.ecocore.minions.MinionManager;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Re-attaches a minion's live entity reference whenever its chunk
 * loads (including the very first load after a server restart).
 * Minion entities are spawned with {@code persistent=true}, so
 * Minecraft itself saves and reloads them as real world data - this
 * listener is what lets {@code MinionManager} pick that reloaded
 * entity back up (by its stored uuid) instead of ever spawning a
 * duplicate, and cleans up any orphaned entity whose database row no
 * longer exists.
 */
public final class MinionChunkListener implements Listener {

    private final MinionManager minionManager;

    /**
     * Creates the chunk listener.
     *
     * @param minionManager shared minion manager
     */
    public MinionChunkListener(MinionManager minionManager) {
        this.minionManager = minionManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            minionManager.attachOrCleanupEntity(entity);
        }
    }
}
