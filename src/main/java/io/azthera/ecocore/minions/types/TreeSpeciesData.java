// FILE: src/main/java/io/azthera/ecocore/minions/types/TreeSpeciesData.java
package io.azthera.ecocore.minions.types;

import org.bukkit.Material;

import java.util.List;

/**
 * Per-tree-species drop and structure data for the Lumberjack minion
 * (Revisi 7): what log/leaves/sapling a species uses, its drop table
 * (with apple/stick chances), and whether it requires a 2x2 planting
 * footprint (dark oak).
 *
 * @param logMaterial the species' log block material
 * @param leavesMaterial the species' leaves block material
 * @param saplingMaterial the sapling/propagule item used to replant this species
 * @param appleChance chance (0.0-1.0) of an apple drop when chopping leaves of this species
 * @param stickChance chance (0.0-1.0) of a stick drop when chopping leaves of this species
 * @param require2x2 whether this species needs a 2x2 sapling grid to grow (dark oak)
 * @param extraDrops additional guaranteed-pool drops beyond log/sapling/apple/stick
 */
public record TreeSpeciesData(Material logMaterial, Material leavesMaterial, Material saplingMaterial,
                               double appleChance, double stickChance, boolean require2x2,
                               List extraDrops) {
}