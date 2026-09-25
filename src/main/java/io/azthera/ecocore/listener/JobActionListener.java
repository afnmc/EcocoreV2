package io.azthera.ecocore.listener;

import io.azthera.ecocore.config.InflationConfig;
import io.azthera.ecocore.inflation.InflationEngine;
import io.azthera.ecocore.jobs.JobsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.ItemStack;

/**
 * Detects job actions that are not represented by the normal block/craft/fish
 * listeners: furnace extraction, villager trading, and anvil output.
 *
 * <p>FurnaceExtractEvent is intentionally used instead of FurnaceSmeltEvent:
 * it has the player who actually collected the output, which prevents passive
 * furnace automation from awarding a job to nobody.</p>
 */
public final class JobActionListener implements Listener {

    private final JobsManager jobsManager;
    private final InflationEngine inflationEngine;
    private final InflationConfig inflationConfig;

    private final java.util.Map<java.util.UUID, org.bukkit.Location> lastPositions = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Double> accumulatedDistance = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, String> lastBiomes = new java.util.concurrent.ConcurrentHashMap<>();

    public JobActionListener(JobsManager jobsManager, InflationEngine inflationEngine,
                             InflationConfig inflationConfig) {
        this.jobsManager = jobsManager;
        this.inflationEngine = inflationEngine;
        this.inflationConfig = inflationConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        ItemStack result = new ItemStack(event.getItemType(), Math.max(1, event.getItemAmount()));
        if (result.getType().isAir()) {
            return;
        }

        String actionKey = "SMELT_" + result.getType().name();
        process(player, actionKey, result.getAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryType type = event.getView().getTopInventory().getType();
        int rawSlot = event.getRawSlot();

        // 1. Vanilla merchant result slot.
        if (type == InventoryType.MERCHANT && rawSlot == 2
                && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()
                && event.getView().getTopInventory() instanceof MerchantInventory merchant) {
            MerchantRecipe recipe = merchant.getSelectedRecipe();
            if (recipe != null) {
                int amount = Math.max(1, recipe.getResult().getAmount());
                process(player, "TRADE_VILLAGER_" + recipe.getResult().getType().name(), amount);
                process(player, "TRADE", amount);
            }
            return;
        }

        // 2. Vanilla anvil result slot.
        if (type == InventoryType.ANVIL && rawSlot == 2
                && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
            process(player, "ANVIL_" + event.getCurrentItem().getType().name(),
                    Math.max(1, event.getCurrentItem().getAmount()));
            return;
        }

        // 3. Brewing stand output slots (0, 1, 2).
        if (type == InventoryType.BREWING && (rawSlot >= 0 && rawSlot <= 2)
                && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
            String mat = event.getCurrentItem().getType().name();
            int amt = Math.max(1, event.getCurrentItem().getAmount());
            if (mat.equals("POTION")) {
                process(player, "BREW_POTION", amt);
            } else if (mat.equals("SPLASH_POTION")) {
                process(player, "BREW_SPLASH_POTION", amt);
            } else if (mat.equals("LINGERING_POTION")) {
                process(player, "BREW_LINGERING_POTION", amt);
            }
            return;
        }

        // 4. Smithing table result slot (slot 3).
        if (type == InventoryType.SMITHING && rawSlot == 3
                && event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
            process(player, "SMITH_" + event.getCurrentItem().getType().name(),
                    Math.max(1, event.getCurrentItem().getAmount()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchantItem(org.bukkit.event.enchantment.EnchantItemEvent event) {
        process(event.getEnchanter(), "ENCHANT_ITEM", 1);
        process(event.getEnchanter(), "USE_ENCHANTING_TABLE", 1);
        process(event.getEnchanter(), "ENCHANT", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        String matName = event.getBlockPlaced().getType().name();
        process(player, "PLACE_BLOCK", 1);
        if (matName.endsWith("_STAIRS")) {
            process(player, "PLACE_STAIRS", 1);
        } else if (matName.endsWith("_SLAB")) {
            process(player, "PLACE_SLAB", 1);
        } else if (matName.endsWith("_WALL")) {
            process(player, "PLACE_WALL", 1);
        } else if (matName.endsWith("_FENCE") || matName.endsWith("_FENCE_GATE")) {
            process(player, "PLACE_FENCE", 1);
        }
        process(player, "PLACE_" + matName, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(org.bukkit.event.entity.EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            process(player, "BREED_" + event.getEntityType().name(), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getBlockY() == to.getBlockY())) {
            return;
        }

        Player player = event.getPlayer();
        java.util.UUID uuid = player.getUniqueId();

        // 1. Biome change detection
        String currentBiome = to.getBlock().getBiome().getKey().getKey().toUpperCase();
        String previousBiome = lastBiomes.put(uuid, currentBiome);
        if (previousBiome != null && !previousBiome.equals(currentBiome)) {
            process(player, "DISCOVER_BIOME", 1);
            process(player, "DISCOVER_BIOME_" + currentBiome, 1);
        }

        // 2. Cave exploration detection
        if (to.getBlockY() < 45 && to.getBlock().getLightFromSky() == 0) {
            if (Math.random() < 0.005) {
                process(player, "ENTER_CAVE", 1);
            }
        }

        // 3. Distance traveled accumulator (50 blocks threshold)
        double delta = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
        if (delta > 0 && delta < 30) {
            double total = accumulatedDistance.merge(uuid, delta, Double::sum);
            if (total >= 50.0) {
                accumulatedDistance.put(uuid, 0.0);
                process(player, "TRAVEL_DISTANCE", 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpenContainer(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getHolder() instanceof org.bukkit.block.Chest chest) {
            if (chest.getLootTable() != null) {
                process(player, "ENTER_STRUCTURE", 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (stack == null || stack.getType().isAir()) return;
        process(player, "COLLECT_" + stack.getType().name(), Math.max(1, stack.getAmount()));
        if (stack.getType() == org.bukkit.Material.EGG) {
            process(player, "COLLECT_EGG", Math.max(1, stack.getAmount()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(org.bukkit.event.player.PlayerShearEntityEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Sheep sheep) {
            process(event.getPlayer(), "SHEAR_SHEEP", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof org.bukkit.entity.Cow
                && event.getPlayer().getInventory().getItemInMainHand().getType() == org.bukkit.Material.BUCKET) {
            process(event.getPlayer(), "MILK_COW", 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        lastPositions.remove(uuid);
        accumulatedDistance.remove(uuid);
        lastBiomes.remove(uuid);
    }

    private void process(Player player, String actionKey, int amount) {
        double multiplier = inflationConfig
                .getStateEffect(inflationEngine.getCurrentState())
                .jobBonusMultiplier();
        jobsManager.processAction(player.getUniqueId(), actionKey, multiplier, amount);
    }
}
