package io.azthera.ecocore.minions.types;

import org.bukkit.Material;

import java.util.List;

public record FishRarityTier(String name, double weight, List<Material> pool) {
}