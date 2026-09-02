package io.azthera.ecocore.gui.jobs;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobMissionRecord;
import io.azthera.ecocore.model.JobType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Detail screen for a single joined job: level/xp/prestige summary,
 * active missions (with clear descriptions and a visual progress
 * bar), and navigation into the skill tree and leaderboard.
 */
public final class JobDetailGui extends AbstractGui {

    private static final int SUMMARY_SLOT = 4;
    private static final int SKILL_TREE_SLOT = 20;
    private static final int LEADERBOARD_SLOT = 22;
    private static final int PRESTIGE_SLOT = 24;
    private static final int MISSIONS_START_SLOT = 27;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 49;

    private final JobsManager jobsManager;
    private final JobsConfig jobsConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MessagesConfig messagesConfig;
    private final JobType jobType;

    /**
     * Creates the job detail screen.
     *
     * @param viewer         the viewing player
     * @param jobsManager    shared jobs manager
     * @param jobsConfig     resolved jobs.yml configuration
     * @param guiManager     shared GUI manager
     * @param guiConfig      resolved gui.yml configuration
     * @param messagesConfig resolved messages.yml configuration
     * @param jobType        the job type being viewed
     */
    public JobDetailGui(Player viewer, JobsManager jobsManager, JobsConfig jobsConfig, GuiManager guiManager,
                         GuiConfig guiConfig, MessagesConfig messagesConfig, JobType jobType) {
        super(viewer);
        this.jobsManager = jobsManager;
        this.jobsConfig = jobsConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.messagesConfig = messagesConfig;
        this.jobType = jobType;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, "§8Job: " + jobType.configKey());
        render();
    }

    private void render() {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }

        try {
            JobData data = jobsManager.getProgress(viewer.getUniqueId(), jobType);
            if (data == null) {
                viewer.closeInventory();
                return;
            }

            inventory.setItem(SUMMARY_SLOT, buildSummaryIcon(data));
            inventory.setItem(SKILL_TREE_SLOT, buildNavIcon(Material.BOOKSHELF, "§dSkill Tree",
                    "§7Lihat perk yang terbuka di level lu."));
            inventory.setItem(LEADERBOARD_SLOT, buildNavIcon(Material.GOLD_INGOT, "§6Leaderboard",
                    "§7Lihat peringkat top player job ini."));

            boolean canPrestige = jobsManager.getPrestigeManager().canPrestige(data);
            inventory.setItem(PRESTIGE_SLOT, buildPrestigeIcon(data, canPrestige));

            ItemStack missionHeader = new ItemStack(Material.WRITTEN_BOOK);
            ItemMeta headerMeta = missionHeader.getItemMeta();
            if (headerMeta != null) {
                headerMeta.setDisplayName("§bMisi Aktif");
                headerMeta.setLore(List.of("§7Selesaikan misi buat dapet bonus reward."));
                missionHeader.setItemMeta(headerMeta);
            }
            inventory.setItem(MISSIONS_START_SLOT - 9, missionHeader);

            List<JobMissionRecord> missions = jobsManager.getMissionManager()
                    .getActiveMissions(viewer.getUniqueId()).stream()
                    .filter(mission -> mission.jobType() == jobType)
                    .toList();

            for (int i = 0; i < missions.size() && i < 9; i++) {
                inventory.setItem(MISSIONS_START_SLOT + i, buildMissionIcon(missions.get(i)));
            }

            if (missions.isEmpty()) {
                ItemStack noneIcon = new ItemStack(Material.BARRIER);
                ItemMeta noneMeta = noneIcon.getItemMeta();
                if (noneMeta != null) {
                    noneMeta.setDisplayName("§7Belum ada misi aktif");
                    noneMeta.setLore(List.of("§7Misi baru muncul otomatis setiap hari/minggu."));
                    noneIcon.setItemMeta(noneMeta);
                }
                inventory.setItem(MISSIONS_START_SLOT, noneIcon);
            }
        } catch (SQLException exception) {
            viewer.sendMessage("§cGagal memuat data job.");
        }

        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "§cTutup"));
    }

    private ItemStack buildSummaryIcon(JobData data) {
        JobsConfig.JobDefinition definition = jobsConfig.getDefinition(jobType);
        ItemStack icon = new ItemStack(definition != null ? safeMaterial(definition.icon()) : Material.STONE);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + jobType.configKey());
            long xpForNext = jobsConfig.xpRequiredForLevel(data.getLevel() + 1);
            meta.setLore(List.of(
                    "§7Level: §f" + data.getLevel() + "/" + jobsConfig.getMaxLevel(),
                    "§7XP: §f" + data.getXp() + " / " + xpForNext,
                    "§7Prestige: §f" + data.getPrestige()
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildNavIcon(Material material, String name, String lore) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildPrestigeIcon(JobData data, boolean canPrestige) {
        ItemStack icon = new ItemStack(canPrestige ? Material.NETHER_STAR : Material.GRAY_DYE);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(canPrestige ? "§d§lPrestige Tersedia!" : "§7Prestige");
            meta.setLore(List.of(canPrestige
                    ? "§7Klik untuk prestige ke tier " + (data.getPrestige() + 1) + "."
                    : "§7Capai level max untuk prestige."));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    /**
     * Builds a mission icon with a plain-language description, a
     * bracket progress bar, and the numeric progress, instead of just
     * a bare number.
     *
     * @param mission the mission to render
     * @return the built icon
     */
    private ItemStack buildMissionIcon(JobMissionRecord mission) {
        ItemStack icon = new ItemStack(mission.isComplete() ? Material.LIME_DYE : Material.PAPER);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            String periodLabel = mission.period().equals("DAILY") ? "§bMisi Harian" : "§5Misi Mingguan";
            meta.setDisplayName(periodLabel);

            List<String> lore = new ArrayList<>();
            lore.add("§7Tugas: §fLakukan aksi kerja sebagai " + jobType.configKey() + " sebanyak " + mission.target() + "x");
            lore.add("§7(misal: mining/nebang/farming/dll sesuai job ini)");
            lore.add("");
            lore.add(buildProgressBar(mission.progress(), mission.target()));
            lore.add("§7Progress: §f" + mission.progress() + " / " + mission.target());
            lore.add(mission.isComplete() ? "§a§lSelesai - reward udah masuk!" : "§e§lSedang berjalan");
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private String buildProgressBar(int progress, int target) {
        int totalBars = 20;
        int filled = target > 0 ? (int) Math.round((progress / (double) target) * totalBars) : 0;
        filled = Math.max(0, Math.min(totalBars, filled));

        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < totalBars; i++) {
            if (i == filled) {
                bar.append("§7");
            }
            bar.append("■");
        }
        int percent = target > 0 ? (int) Math.round((progress / (double) target) * 100) : 0;
        return bar + " §f" + percent + "%";
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return Material.STONE;
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            JobsMainGui mainGui = new JobsMainGui(viewer, jobsManager, jobsConfig, guiManager, guiConfig, messagesConfig);
            guiManager.register(viewer, mainGui);
            mainGui.open();
            return;
        }

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        if (slot == SKILL_TREE_SLOT) {
            JobSkillTreeGui skillTreeGui = new JobSkillTreeGui(
                    viewer, jobsManager, jobsConfig, guiManager, guiConfig, messagesConfig, jobType, this);
            guiManager.register(viewer, skillTreeGui);
            skillTreeGui.open();
            return;
        }

        if (slot == LEADERBOARD_SLOT) {
            JobLeaderboardGui leaderboardGui = new JobLeaderboardGui(
                    viewer, jobsManager, jobsConfig, guiManager, jobType, this);
            guiManager.register(viewer, leaderboardGui);
            leaderboardGui.open();
            return;
        }

        if (slot == PRESTIGE_SLOT) {
            try {
                JobData data = jobsManager.getProgress(viewer.getUniqueId(), jobType);
                if (data != null && jobsManager.getPrestigeManager().prestige(data)) {
                    viewer.sendMessage("§d§lPrestige berhasil! §7Sekarang tier " + data.getPrestige() + ".");
                    guiManager.playSound(viewer, "level-up");
                    render();
                }
            } catch (SQLException exception) {
                viewer.sendMessage("§cGagal prestige.");
            }
        }
    }
                                   }