package io.azthera.ecocore.gui.jobs;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * The root {@code /jobs} screen: one icon per job type, showing the
 * player's current level if joined, or a "join" prompt otherwise.
 */
public final class JobsMainGui extends AbstractGui {

    private static final int CLOSE_SLOT = 22;

    private final JobsManager jobsManager;
    private final JobsConfig jobsConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MessagesConfig messagesConfig;

    private final Map<Integer, JobType> slotToJob = new java.util.HashMap<>();

    /**
     * Creates the jobs main screen.
     *
     * @param viewer         the viewing player
     * @param jobsManager    shared jobs manager
     * @param jobsConfig     resolved jobs.yml configuration
     * @param guiManager     shared GUI manager
     * @param guiConfig      resolved gui.yml configuration
     * @param messagesConfig resolved messages.yml configuration
     */
    public JobsMainGui(Player viewer, JobsManager jobsManager, JobsConfig jobsConfig,
                        GuiManager guiManager, GuiConfig guiConfig, MessagesConfig messagesConfig) {
        super(viewer);
        this.jobsManager = jobsManager;
        this.jobsConfig = jobsConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, guiConfig.getJobsMainRows() * 9, "§8Jobs");
        render();
    }

    /**
     * Repopulates the already-created {@link #inventory} in place.
     * Always call this (not {@link #build()}) after a state change
     * like joining a job, so the window the player is actively
     * looking at gets updated instead of an orphaned new inventory object.
     */
    private void render() {
        slotToJob.clear();

        JobType[] types = JobType.values();
        for (int i = 0; i < types.length; i++) {
            int slot = i;
            slotToJob.put(slot, types[i]);
            inventory.setItem(slot, buildJobIcon(types[i]));
        }

        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildJobIcon(JobType type) {
        JobsConfig.JobDefinition definition = jobsConfig.getDefinition(type);
        Material material = definition != null ? safeMaterial(definition.icon()) : Material.STONE;
        String displayName = definition != null ? definition.displayName() : type.name();

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(displayName));

            try {
                JobData progress = jobsManager.getProgress(viewer.getUniqueId(), type);
                if (progress != null) {
                    meta.setLore(List.of(
                            "§7Level: §f" + progress.getLevel() + "/" + jobsConfig.getMaxLevel(),
                            "§7Prestige: §f" + progress.getPrestige(),
                            "§7XP: §f" + progress.getXp(),
                            "§eKlik buat buka detail, skill tree & leaderboard"
                    ));
                } else {
                    meta.setLore(List.of("§7Belum bergabung.", "§eKlik untuk join!"));
                }
            } catch (SQLException exception) {
                meta.setLore(List.of("§cGagal memuat data."));
            }

            icon.setItemMeta(meta);
        }
        return icon;
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return Material.STONE;
        }
    }

    private String colorize(String input) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', input);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        JobType type = slotToJob.get(slot);
        if (type == null) {
            return;
        }

        try {
            if (!jobsManager.hasJoined(viewer.getUniqueId(), type)) {
                jobsManager.join(viewer.getUniqueId(), type);
                viewer.sendMessage(messagesConfig.getWithPrefix("jobs.joined", "job", type.configKey()));
                guiManager.playSound(viewer, "click");
                render();
                return;
            }
        } catch (SQLException exception) {
            viewer.sendMessage("§cGagal join job.");
            return;
        }

        JobDetailGui detailGui = new JobDetailGui(
                viewer, jobsManager, jobsConfig, guiManager, guiConfig, messagesConfig, type);
        guiManager.register(viewer, detailGui);
        detailGui.open();
    }
                        }