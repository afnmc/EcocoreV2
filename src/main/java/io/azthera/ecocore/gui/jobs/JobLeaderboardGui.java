package io.azthera.ecocore.gui.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.SQLException;
import java.util.List;

/**
 * Displays the top-ranked players for a single job, using
 * {@code JobLeaderboardManager}'s cached results.
 */
public final class JobLeaderboardGui extends AbstractGui {

    private static final int BACK_SLOT = 49;

    private final JobsManager jobsManager;
    private final JobsConfig jobsConfig;
    private final GuiManager guiManager;
    private final JobType jobType;
    private final AbstractGui previousGui;

    /**
     * Creates the leaderboard screen.
     *
     * @param viewer      the viewing player
     * @param jobsManager shared jobs manager
     * @param jobsConfig  resolved jobs.yml configuration
     * @param guiManager  shared GUI manager
     * @param jobType     the job type whose leaderboard is being viewed
     * @param previousGui the screen to return to
     */
    public JobLeaderboardGui(Player viewer, JobsManager jobsManager, JobsConfig jobsConfig,
                              GuiManager guiManager, JobType jobType, AbstractGui previousGui) {
        super(viewer);
        this.jobsManager = jobsManager;
        this.jobsConfig = jobsConfig;
        this.guiManager = guiManager;
        this.jobType = jobType;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 45, "§8Leaderboard: " + jobType.configKey());

        try {
            List<JobData> top = jobsManager.getLeaderboardManager().getLeaderboard(jobType);
            for (int i = 0; i < top.size() && i < 36; i++) {
                inventory.setItem(i, buildRankIcon(i + 1, top.get(i)));
            }
        } catch (SQLException exception) {
            viewer.sendMessage("§cGagal memuat leaderboard.");
        }

        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
    }

    private ItemStack buildRankIcon(int rank, JobData data) {
        ItemStack icon = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = icon.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(data.getPlayerUuid());
            skullMeta.setOwningPlayer(offlinePlayer);
            skullMeta.setDisplayName("§e#" + rank + " §f"
                    + (offlinePlayer.getName() != null ? offlinePlayer.getName() : data.getPlayerUuid()));
            skullMeta.setLore(List.of(
                    "§7Level: §f" + data.getLevel() + "/" + jobsConfig.getMaxLevel(),
                    "§7Prestige: §f" + data.getPrestige()
            ));
            icon.setItemMeta(skullMeta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getRawSlot() == BACK_SLOT && previousGui != null) {
            guiManager.register(viewer, previousGui);
            previousGui.open();
        }
    }
}