// FILE: src/main/java/io/azthera/ecocore/minions/types/FishRarityTier.java
package io.azthera.ecocore.minions.types;

import org.bukkit.Material;

import java.util.List;

/**
 * A single weighted rarity tier for the Fisherman minion (Revisi 8),
 * e.g. COMMON at 70% weight yielding COD or SALMON.
 *
 * @param name the tier's display/config name (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY)
 * @param weight the tier's relative selection weight
 * @param pool the possible catch materials within this tier
 */
public record FishRarityTier(String name, double weight, List pool) {
}