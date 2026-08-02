package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.model.MinionType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets a player purchase and place a new minion at their current
 * location. Charges the configured price from {@code minions.yml
 * purchase.prices} and enforces {@link MinionManager}'s per-player
 * minion cap.
 */
public final class MinionsBuyGui extends AbstractGui {

    private static final int BACK_SLOT = 49;

    private final MinionManager minionManager;
    private final MinionsConfig minionsConfig;
    private final GuiManager guiManager;
    private final AbstractGui previousGui;

    private final Map<Integer, MinionType> slotToType = new HashMap<>();

    /**
     * Creates the minion shop screen.
     *
     * @param viewer        the viewing player
     * @param minionManager shared minion manager
     * @param minionsConfig resolved minions.yml configuration
     * @param guiManager    shared GUI manager
     * @param previousGui   the screen to return to
     */
    public MinionsBuyGui(Player viewer, MinionManager minionManager, MinionsConfig minionsConfig,
                          GuiManager guiManager, AbstractGui previousGui) {
        super(viewer);
        this.minionManager = minionManager;
        this.minionsConfig = minionsConfig;
        this.guiManager = guiManager;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 45, "§8Beli Minion");
        slotToType.clear();

        MinionType[] types = MinionType.values();
        for (int i = 0; i < types.length; i++) {
            inventory.setItem(i, buildIcon(types[i]));
            slotToType.put(i, types[i]);
        }

        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
    }

    private ItemStack buildIcon(MinionType type) {
        MinionsConfig.MinionDefinition definition = minionsConfig.getDefinition(type);
        Material material = definition != null ? safeMaterial(definition.icon()) : Material.VILLAGER_SPAWN_EGG;
        String displayName = definition != null ? definition.displayName() : type.configKey();
        double price = minionsConfig.getPurchasePrice(type);

        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', displayName));
            meta.setLore(List.of(
                    "§7Harga: §a" + String.format("%.2f", price),
                    "§7Minion muncul persis di lokasi lu.",
                    "§eKlik buat beli"
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private Material safeMaterial(String name) {
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return Material.VILLAGER_SPAWN_EGG;
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == BACK_SLOT) {
            if (previousGui != null) {
                guiManager.register(viewer, previousGui);
                previousGui.open();
            } else {
                viewer.closeInventory();
            }
            return;
        }

        MinionType type = slotToType.get(slot);
        if (type == null) {
            return;
        }

        if (minionManager.countOwnedBy(viewer.getUniqueId()) >= MinionManager.DEFAULT_MAX_MINIONS_PER_PLAYER) {
            viewer.sendMessage("§cLu udah mencapai batas maksimal minion ("
                    + MinionManager.DEFAULT_MAX_MINIONS_PER_PLAYER + ").");
            guiManager.playSound(viewer, "error");
            return;
        }

        double price = minionsConfig.getPurchasePrice(type);
        EconomyEngine economyEngine = EcoCorePlugin.getInstance().getEconomyEngine();

        if (!economyEngine.has(viewer.getUniqueId(), price)) {
            viewer.sendMessage("§cSaldo lu gak cukup buat beli minion ini.");
            guiManager.playSound(viewer, "error");
            return;
        }

        boolean charged = economyEngine.withdraw(viewer.getUniqueId(), price, TransactionLogger.REASON_ADMIN_ADJUST);
        if (!charged) {
            viewer.sendMessage("§cGagal memproses pembayaran.");
            return;
        }

        MinionData placed = minionManager.placeMinion(viewer, type, viewer.getLocation());
        if (placed == null) {
            // Refund - placement failed after payment (e.g. hit the cap in a race, or persistence failed).
            economyEngine.deposit(viewer.getUniqueId(), price, TransactionLogger.REASON_ADMIN_ADJUST);
            viewer.sendMessage("§cGagal menempatkan minion, saldo lu dikembalikan.");
            return;
        }

        viewer.sendMessage("§aBerhasil beli minion §f" + type.configKey() + "§a! Cek lewat §f/minions§a.");
        guiManager.playSound(viewer, "buy");
        build();
    }
          }
