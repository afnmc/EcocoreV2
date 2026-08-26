package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionConnectorManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ConnectorConfirmGui extends AbstractGui {

    private static final int SOURCE_SLOT = 11;
    private static final int ARROW_SLOT = 13;
    private static final int DESTINATION_SLOT = 15;
    private static final int CONFIRM_SLOT = 21;
    private static final int CANCEL_SLOT = 23;

    private final MinionManager minionManager;
    private final MinionConnectorManager connectorManager;
    private final GuiManager guiManager;
    private final MinionData source;
    private final MinionData destination;

    public ConnectorConfirmGui(Player viewer, MinionManager minionManager, MinionConnectorManager connectorManager,
                                GuiManager guiManager, MinionData source, MinionData destination) {
        super(viewer);
        this.minionManager = minionManager;
        this.connectorManager = connectorManager;
        this.guiManager = guiManager;
        this.source = source;
        this.destination = destination;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 27, "§8Simpan Koneksi Ini?");

        inventory.setItem(SOURCE_SLOT, buildMinionIcon(source, "§bSumber (Source)"));
        inventory.setItem(ARROW_SLOT, buildArrowIcon());
        inventory.setItem(DESTINATION_SLOT, buildMinionIcon(destination, "§aTujuan (Destination)"));

        ItemStack confirm = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirm.getItemMeta();
        if (confirmMeta != null) {
            confirmMeta.setDisplayName("§a§lSimpan Koneksi");
            confirmMeta.setLore(List.of("§7Item dari sumber bakal ngalir ke tujuan tiap tick."));
            confirm.setItemMeta(confirmMeta);
        }
        inventory.setItem(CONFIRM_SLOT, confirm);

        ItemStack cancel = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName("§c§lBatal");
            cancel.setItemMeta(cancelMeta);
        }
        inventory.setItem(CANCEL_SLOT, cancel);
    }

    private ItemStack buildMinionIcon(MinionData data, String label) {
        ItemStack icon = new ItemStack(Material.HOPPER);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(label);
            meta.setLore(List.of(
                    "§7Tipe: §f" + data.getType().configKey(),
                    "§7ID: §f#" + data.getId()
            ));
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack buildArrowIcon() {
        ItemStack icon = new ItemStack(Material.ARROW);
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7➜");
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == CONFIRM_SLOT) {
            String rejectionReason = connectorManager.validateConnection(source, destination);
            if (rejectionReason != null) {
                viewer.sendMessage("§cGak bisa bikin koneksi ini: §f" + rejectionReason);
                guiManager.playSound(viewer, "error");
                viewer.closeInventory();
                return;
            }

            boolean saved = connectorManager.connect(viewer.getUniqueId(), source.getId(), destination.getId());
            if (saved) {
                viewer.sendMessage("§aKoneksi disimpan: §f" + source.getType().configKey() + " #" + source.getId()
                        + " §a➜ §f" + destination.getType().configKey() + " #" + destination.getId());
                guiManager.playSound(viewer, "click");
            } else {
                viewer.sendMessage("§cGagal menyimpan koneksi.");
                guiManager.playSound(viewer, "error");
            }
            viewer.closeInventory();
            return;
        }

        if (slot == CANCEL_SLOT) {
            viewer.sendMessage("§7Koneksi dibatalkan.");
            viewer.closeInventory();
        }
    }
                                }
