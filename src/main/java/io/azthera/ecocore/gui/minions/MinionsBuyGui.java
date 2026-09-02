package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.config.MinionsConfig;
import io.azthera.ecocore.economy.EconomyEngine;
import io.azthera.ecocore.economy.TransactionLogger;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionType;
import io.azthera.ecocore.utils.ItemUtils;
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
 * Lets a player purchase a "minion egg" item for any minion type.
 * Purchasing does NOT place the minion immediately - it charges the
 * configured price and gives an egg item, which the player then
 * right-clicks onto the ground to actually place it.
 */
public final class MinionsBuyGui extends AbstractGui {

    private static final int BACK_SLOT = 49;
    private static final int INFO_SLOT = 40;

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
        inventory = Bukkit.createInventory(this, 54, "§8Beli Minion");
        render();
    }

    private void render() {
        slotToType.clear();

        MinionType[] types = MinionType.values();
        for (int i = 0; i < types.length; i++) {
            inventory.setItem(i, buildIcon(types[i]));
            slotToType.put(i, types[i]);
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§eCara pakai minion");
            infoMeta.setLore(List.of(
                    "§7Beli minion -> lu dapet item §fMinion Egg§7.",
                    "§7Klik kanan tanah pakai Minion Egg buat naruh minion-nya.",
                    "§7Minion diam di tempat, gak bisa jalan/pindah.",
                    "§7Klik kanan minion buat buka menu upgrade.",
                    "§7Shift + klik kanan minion buat buka storage-nya.",
                    "§7Pegang §fCoal/Coal Block/Lava Bucket §7lalu",
                    "§7klik kanan minion buat isi ulang fuel."
            ));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(INFO_SLOT, info);

        inventory.setItem(BACK_SLOT, guiManager.buildButtonIcon("back", "§eKembali"));
    }

    private ItemStack buildIcon(MinionType type) {
        ItemStack icon = ItemUtils.buildMinionTypeIcon(type, minionsConfig);
        double price = minionsConfig.getPurchasePrice(type);

        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(
                    "§7Harga: §a" + String.format("%.2f", price),
                    "§7Lu bakal dapet item Minion Egg.",
                    "§eKlik buat beli"
            ));
            icon.setItemMeta(meta);
        }
        return icon;
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

        if (minionManager.countOwnedBy(viewer.getUniqueId()) >= minionManager.getMinionsConfig().getMaxMinionsPerPlayer()) {
            viewer.sendMessage("§cLu udah mencapai batas maksimal minion ("
                    + minionManager.getMinionsConfig().getMaxMinionsPerPlayer() + ").");
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

        ItemStack egg = ItemUtils.buildMinionEgg(type, minionsConfig);
        Map<Integer, ItemStack> leftover = viewer.getInventory().addItem(egg);
        for (ItemStack over : leftover.values()) {
            viewer.getWorld().dropItemNaturally(viewer.getLocation(), over);
        }

        viewer.sendMessage("§aBerhasil beli Minion Egg §f" + type.configKey()
                + "§a! Klik kanan ke tanah buat naruh minion-nya.");
        guiManager.playSound(viewer, "buy");
        render();
    }
        }