package io.azthera.ecocore.minions;

import io.azthera.ecocore.config.GuiConfig;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;

/**
 * Plays lightweight visual/audio feedback for minion actions, gated
 * by the {@code general.animations-enabled} / {@code general.sounds-enabled}
 * flags in {@code gui.yml} so server owners can disable effects
 * entirely on performance-constrained servers.
 */
public final class MinionAnimationHandler {

    private final GuiConfig guiConfig;

    /**
     * Creates an animation handler.
     *
     * @param guiConfig resolved gui.yml configuration (animation/sound toggles)
     */
    public MinionAnimationHandler(GuiConfig guiConfig) {
        this.guiConfig = guiConfig;
    }

    /**
     * Plays a small particle burst representing a minion completing
     * an action (breaking a block, catching a fish, etc.).
     *
     * @param location the location to play the effect at
     */
    public void playActionEffect(Location location) {
        if (!guiConfig.isAnimationsEnabled() || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.CRIT, location.clone().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0.02);
    }

    /**
     * Plays a level-up particle/sound effect at a minion's location.
     *
     * @param location the location to play the effect at
     */
    public void playLevelUpEffect(Location location) {
        if (location.getWorld() == null) {
            return;
        }
        if (guiConfig.isAnimationsEnabled()) {
            location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location.clone().add(0.5, 1.0, 0.5), 20, 0.3, 0.3, 0.3, 0.1);
        }
        if (guiConfig.isSoundsEnabled()) {
            Sound sound = guiConfig.getSound("level-up");
            if (sound != null) {
                location.getWorld().playSound(location, sound, 1.0f, 1.2f);
            }
        }
    }

    /**
     * Plays a small "out of fuel" warning effect at a minion's location.
     *
     * @param location the location to play the effect at
     */
    public void playOutOfFuelEffect(Location location) {
        if (!guiConfig.isAnimationsEnabled() || location.getWorld() == null) {
            return;
        }
        location.getWorld().spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 6, 0.15, 0.15, 0.15, 0.01);
    }
}