package io.azthera.ecocore.gui.minions;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.database.dao.MinionConnectionDao;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.minions.MinionConnectorManager;
import io.azthera.ecocore.minions.MinionManager;
import io.azthera.ecocore.model.MinionData;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Connect-list screen (bug-fix round: replaces the old click-source-
 * then-click-destination-in-world flow entirely per explicit user
 * request). Opened from a minion's {@link MinionUpgradeGui} - shows
 * every OTHER minion the player owns as a clickable entry; clicking
 * one immediately attempts to connect FROM the minion whose upgrade
 * screen was open TO the clicked entry, using the same {@link
 * MinionConnectorManager#resolveLink}/{@code connect} validation as
 * before (owner match, same world, direct-10-block or relay-via-
 * connector-entity range check) - just without the two-step
 * click-in-the-world UI.
 */
public final class MinionConnectListGui extends AbstractGui {

    private static final int[] LIST_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int CLOSE_SLOT = 49;

    private final MinionManager minionManager;
    private final MinionConnectorManager connectorManager;
    private final GuiManager guiManager;
    private final long sourceMinionId;
    private final AbstractGui previousGui;
    private List<Long> candidateIds = new ArrayList<>();

    public MinionConnectListGui(Player viewer, MinionManager minionManager, MinionConnectorManager connectorManager,
                                 GuiManager guiManager, long sourceMinionId, AbstractGui previousGui) {
        super(viewer);
        this.minionManager = minionManager;
        this.connectorManager = connectorManager;
        this.guiManager = guiManager;
        this.sourceMinionId = sourceMinionId;
        this.previousGui = previousGui;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, "\u00a78Connect Minion");
        render();
    }

    private void render() {
        inventory.clear();
        MinionData source = minionManager.getMinion(sourceMinionId);
        if (source == null) {
            inventory.setItem(22, namedItem(Material.BARRIER, "\u00a7cMinion sumber tidak ditemukan", List.of()));
            inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "\u00a7cTutup"));
            return;
        }

        candidateIds = new ArrayList<>();
        for (MinionData candidate : minionManager.getMinionsOwnedBy(source.getOwnerUuid())) {
            if (candidate.getId() != sourceMinionId) {
                candidateIds.add(candidate.getId());
            }
        }

        if (candidateIds.isEmpty()) {
            inventory.setItem(22, namedItem(Material.BARRIER, "\u00a7cGak ada minion lain buat di-connect",
                    List.of("\u00a77Beli minion lain dulu sebelum connect.")));
        }

        int slotIndex = 0;
        for (long candidateId : candidateIds) {
            if (slotIndex >= LIST_SLOTS.length) {
                break; // capped at 21 entries per screen - fine given the 20-minion-per-player cap
            }
            MinionData candidate = minionManager.getMinion(candidateId);
            if (candidate == null) {
                continue;
            }
            inventory.setItem(LIST_SLOTS[slotIndex], buildCandidateIcon(source, candidate));
            slotIndex++;
        }

        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", "\u00a7cTutup"));
    }

    private ItemStack buildCandidateIcon(MinionData source, MinionData candidate) {
        ItemStack icon = ItemUtils.buildMinionTypeIcon(candidate.getType(), minionManager.getMinionsConfig());
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            boolean alreadyConnected = connectorManager.isConnected(sourceMinionId, candidate.getId());
            meta.setDisplayName((alreadyConnected ? "\u00a77" : "\u00a7f")
                    + candidate.getType().configKey() + " \u00a78(#" + candidate.getId() + ")");
            List<String> lore = new ArrayList<>();
            if (alreadyConnected) {
                lore.add("\u00a7aSudah terhubung.");
            } else {
                MinionConnectorManager.ResolvedLink preview = connectorManager.resolveLink(source, candidate);
                if (preview.isValid()) {
                    String modeLabel = preview.linkMode() == MinionConnectionDao.LinkMode.RELAY
                            ? "\u00a7d(relay via Minion Connector)" : "\u00a7a(direct)";
                    lore.add("\u00a7eKlik buat connect " + modeLabel);
                } else {
                    lore.add("\u00a7c" + preview.rejectionReason());
                }
            }
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == CLOSE_SLOT) {
            getViewer().closeInventory();
            return;
        }

        int listIndex = -1;
        for (int i = 0; i < LIST_SLOTS.length; i++) {
            if (LIST_SLOTS[i] == slot) {
                listIndex = i;
                break;
            }
        }
        if (listIndex == -1 || listIndex >= candidateIds.size()) {
            return;
        }

        MinionData source = minionManager.getMinion(sourceMinionId);
        MinionData destination = minionManager.getMinion(candidateIds.get(listIndex));
        if (source == null || destination == null) {
            render();
            return;
        }

        if (connectorManager.isConnected(sourceMinionId, destination.getId())) {
            guiManager.playSound(getViewer(), "error");
            return;
        }

        MinionConnectorManager.ResolvedLink resolvedLink = connectorManager.resolveLink(source, destination);
        if (!resolvedLink.isValid()) {
            getViewer().sendMessage("\u00a7cGak bisa bikin koneksi ini: \u00a7f" + resolvedLink.rejectionReason());
            guiManager.playSound(getViewer(), "error");
            return;
        }

        boolean connected = connectorManager.connect(source.getOwnerUuid(), source.getId(), destination.getId(), resolvedLink);
        if (connected) {
            String modeLabel = resolvedLink.linkMode() == MinionConnectionDao.LinkMode.RELAY
                    ? "\u00a7d(via Minion Connector - relay)" : "\u00a7a(direct)";
            getViewer().sendMessage("\u00a7bBerhasil connect: \u00a7f" + source.getType().configKey() + " \u00a7b-> \u00a7f"
                    + destination.getType().configKey() + " " + modeLabel);
            guiManager.playSound(getViewer(), "level-up");
        } else {
            getViewer().sendMessage("\u00a7cGagal membuat koneksi.");
            guiManager.playSound(getViewer(), "error");
        }
        render();
    }
}
