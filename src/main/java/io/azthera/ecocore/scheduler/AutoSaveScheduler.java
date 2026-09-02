package io.azthera.ecocore.scheduler;

import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.minions.MinionConnectorEntityManager;
import io.azthera.ecocore.minions.MinionManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class AutoSaveScheduler {
    private final JavaPlugin plugin;
    private final Logger logger;
    private final EconomyEngine economyEngine;
    private final MinionManager minionManager;
    private final MinionConnectorEntityManager connectorEntityManager;
    private final int intervalMinutes;
    private BukkitTask task;

    public AutoSaveScheduler(JavaPlugin plugin, Logger logger, EconomyEngine economyEngine,
                              MinionManager minionManager, MinionConnectorEntityManager connectorEntityManager,
                              int intervalMinutes) {
        this.plugin = plugin;
        this.logger = logger;
        this.economyEngine = economyEngine;
        this.minionManager = minionManager;
        this.connectorEntityManager = connectorEntityManager;
        this.intervalMinutes = Math.max(1, intervalMinutes);
    }

    public void start() {
        stop();
        long periodTicks = intervalMinutes * 60L * 20L;
        // Run on MAIN thread to capture snapshots safely
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runSave, periodTicks, periodTicks);
    }

    private void runSave() {
        // 1. Capture Snapshots (Main Thread)
        List<MinionManager.SaveSnapshot> minionSnapshots = minionManager.collectSaveSnapshots();
        Map<Long, String> bufferSnapshots = connectorEntityManager.collectBufferSnapshots();
        
        // 2. Persist Async (Worker Thread)
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            economyEngine.saveAll();
            minionManager.persistSnapshots(minionSnapshots);
            connectorEntityManager.persistBufferSnapshots(bufferSnapshots);
            logger.info("[EcoCore] Autosave complete");
        });
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}