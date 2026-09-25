package io.azthera.ecocore.gui.admin;

import io.azthera.ecocore.EcoCorePlugin;
import io.azthera.ecocore.gui.AbstractGui;
import io.azthera.ecocore.gui.GuiManager;
import io.azthera.ecocore.gui.GuiPage;
import io.azthera.ecocore.input.PrivateChatInputManager;
import io.azthera.ecocore.market.NightMarketManager;
import io.azthera.ecocore.model.NightMarketOffer;
import io.azthera.ecocore.utils.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Admin-only editor for the active Night Market rotation. */
public final class AdminNightMarketGui extends AbstractGui {
    private final GuiManager guiManager;
    private final PrivateChatInputManager inputManager;
    private GuiPage<NightMarketOffer> page;
    private List<NightMarketOffer> offers = List.of();

    public AdminNightMarketGui(Player viewer, GuiManager guiManager, PrivateChatInputManager inputManager) {
        super(viewer);
        this.guiManager = guiManager;
        this.inputManager = inputManager;
    }

    @Override public void build() {
        inventory = Bukkit.createInventory(this, 54, color("&8&lEcoCore &7» &dNight Market Editor"));
        reloadPage();
    }

    private void reloadPage() {
        offers = new ArrayList<>(EcoCorePlugin.getInstance().getNightMarketManager().getOffers());
        offers.sort(Comparator.comparing(NightMarketOffer::getId, String.CASE_INSENSITIVE_ORDER));
        page = new GuiPage<>(offers, 45);
        render();
    }

    private void render() {
        for (int i=0;i<45;i++) inventory.setItem(i,null);
        List<NightMarketOffer> current = page.getCurrentPageItems();
        for (int i=0;i<current.size();i++) inventory.setItem(i, icon(current.get(i)));
        inventory.setItem(45, page.hasPreviousPage()?button(Material.ARROW,"&eSebelumnya"):null);
        inventory.setItem(48, button(Material.NETHER_STAR,"&dRotasi Sekarang","&7Ganti seluruh rotasi"));
        inventory.setItem(49, button(Material.BARRIER,"&cTutup"));
        inventory.setItem(50, button(Material.ARROW,"&eKembali"));
        inventory.setItem(53, page.hasNextPage()?button(Material.ARROW,"&eBerikutnya"):null);
    }

    private ItemStack icon(NightMarketOffer offer) {
        ItemStack item = new ItemStack(ItemUtils.safeMaterial(offer.getMaterial()));
        ItemMeta meta=item.getItemMeta();
        if(meta!=null){
            meta.setDisplayName(color("&f"+offer.getId()));
            meta.setLore(List.of(color("&7Harga: &a$"+String.format("%.2f",offer.getPrice())), color("&7Stok: &f"+offer.getStock()+"/"+offer.getMaxStock()), color("&eKlik: edit harga"), color("&bShift-klik: edit stok")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material m,String n,String... l){ ItemStack i=new ItemStack(m); ItemMeta meta=i.getItemMeta(); if(meta!=null){meta.setDisplayName(color(n)); meta.setLore(java.util.Arrays.stream(l).map(this::color).toList()); i.setItemMeta(meta);} return i; }
    private String color(String s){return ChatColor.translateAlternateColorCodes('&',s);}

    @Override public void handleClick(InventoryClickEvent event){
        event.setCancelled(true); int slot=event.getRawSlot();
        if(slot==49){viewer.closeInventory();return;}
        if(slot==50){AdminMainGui g=new AdminMainGui(viewer,guiManager,inputManager);guiManager.register(viewer,g);g.open();return;}
        if(slot==48){EcoCorePlugin.getInstance().getNightMarketManager().rotate();reloadPage();viewer.sendMessage(color("&a[EcoCore] Night Market dirotasi."));return;}
        if(slot==45&&page.hasPreviousPage()){page.previousPage();render();return;}
        if(slot==53&&page.hasNextPage()){page.nextPage();render();return;}
        if(slot>=0&&slot<45){List<NightMarketOffer> current=page.getCurrentPageItems();if(slot>=current.size())return; NightMarketOffer offer=current.get(slot); requestEdit(offer,event.isShiftClick());}
    }

    private void requestEdit(NightMarketOffer offer, boolean stock){
        viewer.closeInventory();
        viewer.sendMessage(color("&d[EcoCore] &f"+(stock?"Stok":"Harga")+" saat ini: &a"+(stock?String.valueOf(offer.getStock()):String.format("$%.2f",offer.getPrice()))));
        viewer.sendMessage(color("&fKetik nilai baru atau &ccancel&f."));
        inputManager.request(viewer,input->{
            try{
                if(stock){int v=Integer.parseInt(input.trim()); if(!EcoCorePlugin.getInstance().getNightMarketManager().setOfferStock(offer.getId(),v)) throw new NumberFormatException();}
                else{double v=Double.parseDouble(input.replace(",","").trim()); if(!EcoCorePlugin.getInstance().getNightMarketManager().setOfferPrice(offer.getId(),v)) throw new NumberFormatException();}
                viewer.sendMessage(color("&a[EcoCore] Perubahan Night Market tersimpan."));
            }catch(NumberFormatException ex){viewer.sendMessage(color("&c[EcoCore] Nilai tidak valid."));}
            guiManager.register(viewer,this); reloadPage(); viewer.openInventory(inventory);
        },()->{guiManager.register(viewer,this);reloadPage();viewer.openInventory(inventory);});
    }
}
