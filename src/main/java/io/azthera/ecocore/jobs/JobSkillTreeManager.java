package io.azthera.ecocore.jobs;

import io.azthera.ecocore.config.JobsConfig;
import io.azthera.ecocore.model.JobType;
import io.azthera.ecocore.model.SkillTreeNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates and evaluates each job's skill tree. Nodes are derived
 * purely from {@code jobs.yml} (perk unlock levels x skill tree
 * branches) rather than stored per-player: a node is considered
 * unlocked automatically once the player's job level reaches its
 * required level, so there is no separate "spend a point" choice to
 * persist - the tree simply reflects progress.
 */
public final class JobSkillTreeManager {

    private final JobsConfig jobsConfig;

    /**
     * Creates a skill tree manager.
     *
     * @param jobsConfig resolved jobs.yml configuration
     */
    public JobSkillTreeManager(JobsConfig jobsConfig) {
        this.jobsConfig = jobsConfig;
    }

    /**
     * Generates the full skill tree for a job type: one node per
     * configured perk-unlock level, per branch.
     *
     * @param type the job type
     * @return the full set of skill tree nodes for this job
     */
    public List<SkillTreeNode> generateTree(JobType type) {
        List<SkillTreeNode> nodes = new ArrayList<>();
        List<Integer> unlockLevels = jobsConfig.getPerkUnlockLevels();
        List<String> bonusTypes = jobsConfig.getPerkBonusTypes();
        int maxBranches = jobsConfig.getSkillTreeMaxBranches();

        int nodeIndex = 0;
        for (int level : unlockLevels) {
            for (int branch = 0; branch < maxBranches; branch++) {
                String bonusType = bonusTypes.isEmpty() ? "money" : bonusTypes.get(nodeIndex % bonusTypes.size());
                double bonusValue = 0.02 * (branch + 1);
                String nodeId = type.configKey() + "_lvl" + level + "_b" + branch;

                nodes.add(new SkillTreeNode(nodeId, type, level, bonusType, bonusValue, branch));
                nodeIndex++;
            }
        }
        return nodes;
    }

    /**
     * Returns the subset of a job's skill tree that is unlocked at a given level.
     *
     * @param type  the job type
     * @param level the player's current level in this job
     * @return the unlocked nodes
     */
    public List<SkillTreeNode> getUnlockedNodes(JobType type, int level) {
        return generateTree(type).stream()
                .filter(node -> node.requiredLevel() <= level)
                .toList();
    }

    /**
     * Sums the bonus value of every unlocked node of a given bonus type
     * (e.g. total "money" bonus percentage from all unlocked nodes).
     *
     * @param type      the job type
     * @param level     the player's current level in this job
     * @param bonusType the bonus type to sum (e.g. "money", "xp", "speed")
     * @return the summed bonus value
     */
    public double getTotalBonus(JobType type, int level, String bonusType) {
        return getUnlockedNodes(type, level).stream()
                .filter(node -> node.bonusType().equals(bonusType))
                .mapToDouble(SkillTreeNode::bonusValue)
                .sum();
    }
}