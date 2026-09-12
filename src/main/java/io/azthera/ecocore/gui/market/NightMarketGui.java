package io.azthera.ecocore.gui.market;

import io.azthera.ecocore.config.MessagesConfig;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.market.NightMarketManager;
import io.azthera.ecocore.model.NightMarketOffer;
import io.azthera.ecocore.utils.ItemUtils;
import io.azthera.ecocore.utils.TimeUtils;
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
 * The {@code /market} screen: EcoCore's night market, a small
 * rotating selection of expensive/rare items that does NOT restock
 * between rotations.
 */
public final class NightMarketGui extends AbstractGui {

    private static final int TIMER_SLOT = 4;
    private static final int CLOSE_SLOT = 49;
    private static final int FIRST_OFFER_SLOT = 19;
    private static final int OFFER_SLOT_STEP = 2;
    private static final int LAST_OFFER_SLOT = 33;

    private final NightMarketManager nightMarketManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;

    private final Map<Integer, String> slotToOfferId = new HashMap<>();

    /**
     * Creates the night market screen.
     *
     * @param viewer             the viewing player
     * @param nightMarketManager shared night market manager
     * @param guiManager         shared GUI manager
     * @param messagesConfig     resolved messages.yml configuration
     */
    public NightMarketGui(Player viewer, NightMarketManager nightMarketManager, GuiManager guiManager,
                           MessagesConfig messagesConfig) {
        super(viewer);
        this.nightMarketManager = nightMarketManager;
        this.guiManager = guiManager;
        this.messagesConfig = messagesConfig;
    }

    @Override
    public void build() {
        inventory = Bukkit.createInventory(this, 54, messagesConfig.get("ui.market.title"));
        render();
    }

    /**
     * Repopulates the already-created {@link #inventory} in place,
     * used both on first build and to refresh after a purchase.
     */
    private void render() {
        slotToOfferId.clear();

        ItemStack timerIcon = new ItemStack(Material.CLOCK);
        ItemMeta timerMeta = timerIcon.getItemMeta();
        if (timerMeta != null) {
            timerMeta.setDisplayName(messagesConfig.get("ui.market.timer-title"));
            timerMeta.setLore(List.of(
                    messagesConfig.get("ui.market.timer-lore-1"),
                    messagesConfig.get("ui.market.timer-lore-2", "time", TimeUtils.formatSecondsAsDuration(nightMarketManager.millisUntilNextRotation() / 1000)),
                    messagesConfig.get("ui.market.timer-lore-3")
            ));
            timerIcon.setItemMeta(timerMeta);
        }
        inventory.setItem(TIMER_SLOT, timerIcon);

        List<NightMarketOffer> offers = nightMarketManager.getOffers();
        int slot = FIRST_OFFER_SLOT;
        for (NightMarketOffer offer : offers) {
            if (slot > LAST_OFFER_SLOT) {
                break;
            }
            inventory.setItem(slot, buildOfferIcon(offer));
            slotToOfferId.put(slot, offer.getId());
            slot += OFFER_SLOT_STEP;
        }

        inventory.setItem(CLOSE_SLOT, guiManager.buildButtonIcon("close", messagesConfig.get("ui.common.close")));
    }

    private ItemStack buildOfferIcon(NightMarketOffer offer) {
        ItemStack icon = new ItemStack(ItemUtils.safeMaterial(offer.getMaterial()));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setDisplayName((offer.isSoldOut() ? "§c" : "§d") + offer.getId());
            List<String> lore = new java.util.ArrayList<>();
            lore.add(messagesConfig.get("ui.common.price", "price", String.format("%.2f", offer.getPrice())));
            // Revisi 16: night market prices are fixed for the rotation
            // (no live restock), so instead of a delta-from-base line,
            // show the current server-wide economic state as context.
            io.azthera.ecocore.model.InflationRecord latestInflation =
                    io.azthera.ecocore.EcoCorePlugin.getInstance().getInflationEngine().getLatestRecord();
            if (latestInflation != null) {
                boolean isInflation = latestInflation.inflationPercent() >= latestInflation.deflationPercent();
                double percent = isInflation ? latestInflation.inflationPercent() : latestInflation.deflationPercent();
                if (percent >= 0.01) {
                    lore.add(isInflation
                            ? messagesConfig.get("ui.market.inflation", "percent", String.format("%.1f", percent))
                            : messagesConfig.get("ui.market.deflation", "percent", String.format("%.1f", percent)));
                }
            }
            lore.add(messagesConfig.get("ui.common.stock", "stock", String.valueOf(offer.getStock()), "max", String.valueOf(offer.getMaxStock())));
            lore.add(offer.isSoldOut() ? messagesConfig.get("ui.market.sellout") : messagesConfig.get("ui.market.click-buy"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
            return;
        }

        String offerId = slotToOfferId.get(slot);
        if (offerId == null) {
            return;
        }

        NightMarketOffer offer = nightMarketManager.getOffer(offerId);
        if (offer == null || offer.isSoldOut()) {
            guiManager.playSound(viewer, "error");
            return;
        }

        NightMarketBuyConfirmGui confirmGui = new NightMarketBuyConfirmGui(
                viewer, nightMarketManager, guiManager, messagesConfig, offerId, this);
        guiManager.register(viewer, confirmGui);
        confirmGui.open();
    }
                                       }
