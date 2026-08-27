package io.azthera.ecocore.minions.types;

import org.bukkit.Material;

import java.util.List;

public record TreeSpeciesData(Material logMaterial, Material leavesMaterial, Material saplingMaterial,
                               double appleChance, double stickChance, boolean require2x2,
                               List<Material> extraDrops) {
}
