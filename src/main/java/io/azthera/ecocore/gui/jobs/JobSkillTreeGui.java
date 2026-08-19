package io.azthera.ecocore.gui.jobs;

import io.azthera.ecocore.config.GuiConfig;
import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.jobs.JobsManager;
import io.azthera.ecocore.model.JobData;
import io.azthera.ecocore.model.JobType;
import io.azthera.ecocore.model.SkillTreeNode;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.List;

/**
 * Displays a job's full skill tree, with nodes visually distinguished
 * as unlocked (player's level meets the requirement) or locked.
 */
public final class JobSkillTreeGui extends AbstractGui {

    private static final int BACK_SLOT = 49;

    private final JobsManager jobsManager;
    private final JobsConfig jobsConfig;
    private final GuiManager guiManager;
    private final GuiConfig guiConfig;
    private final MessagesConfig messagesConfig;
    private final JobType jobType;
    private final AbstractGui previousGui;

    /**
     * Creates the skill tree screen.
     *
     * @param viewer         the viewing player
     * @param jobsManager    shared jobs manager
     * @param jobsConfig     resolved jobs.yml configuration
     * @param guiManager     shared GUI manager
     * @param guiConfig      resolved gui.yml configuration
     * @param messagesConfig resolved messages.yml configuration
     * @param jobType        the job type whose tree is being viewed
     * @param previousGui    the screen to return to
     */
    public JobSkillTreeGui(Player viewer, JobsManager jobsManager, JobsConfig jobsConfig, GuiManager guiManager,
                            GuiConfig guiConfig, MessagesConfig messagesConfig, JobType jobType, AbstractGui previousGui) {
        super(viewer);
        this.jobsManager = jobsManager;
        this.jobsConfig = jobsConfig;
        this.guiManager = guiManager;
        this.guiConfig = guiConfig;
        this.messagesConfig = messagesConfig;
        this.jobType = jobType;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, "§8Skill Tree: " + jobType.configKey());

        try {
            JobData data = jobsManager.getProgress(viewer.getUniqueId(), jobType);
            int playerLevel = data != null ? data.getLevel() : 0;

            List<SkillTreeNode> tree = jobsManager.getSkillTreeManager().generateTree(jobType);
            int maxBranches = jobsConfig.getSkillTreeMaxBranches();

            for (int i = 0; i < tree.size(); i++) {
                SkillTreeNode node = tree.get(i);
                int row = i / maxBranches;
                int col = node.branch();
                int slot = (row * 9) + col + 1;
                if (slot >= 45) {
                    continue;
                }

                boolean unlocked = playerLevel >= node.requiredLevel();
                inventory.setItem(slot, buildNodeIcon(node, unlocked));
            }
        } catch (SQLException exception) {
            viewer.sendMessage("§cGagal memuat skill tree.");
        }

        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
    }

    private ItemStack buildNodeIcon(SkillTreeNode node, boolean unlocked) {
        ItemStack icon = new ItemStack(unlocked ? Material.ENCHANTED_BOOK : Material.BOOK);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((unlocked ? "§a" : "§7") + "Bonus " + node.bonusType());
            meta.setLore(List.of(
                    "§7Butuh level: §f" + node.requiredLevel(),
                    "§7Bonus: §f+" + String.format("%.0f", node.bonusValue() * 100) + "%",
                    unlocked ? "§a§lTerbuka" : "§c§lTerkunci"
            ));
            icon.setItemMeta(meta);
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