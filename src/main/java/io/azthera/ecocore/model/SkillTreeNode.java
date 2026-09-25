package io.azthera.ecocore.model;

/**
 * An immutable definition of a single unlockable node in a job's skill tree.
 *
 * @param id             unique node id within the job's tree
 * @param jobType        the job this node belongs to
 * @param requiredLevel  the job level required to unlock this node
 * @param bonusType      the type of bonus granted ("money", "drop", "xp", "speed", "fortune", "luck")
 * @param bonusValue     the magnitude of the bonus (interpretation depends on bonusType)
 * @param branch         which skill tree branch this node belongs to (0-based)
 */
public record SkillTreeNode(String id, JobType jobType, int requiredLevel,
                             String bonusType, double bonusValue, int branch) {
}