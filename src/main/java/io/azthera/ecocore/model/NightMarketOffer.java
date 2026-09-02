package io.azthera.ecocore.model;

/**
 * A single item currently on offer in the night market's active
 * rotation. Unlike {@link ShopItemRecord}, this does NOT auto-restock
 * - once {@link #getStock()} hits zero it stays sold out until
 * {@code NightMarketManager.rotate()} replaces the entire offer list.
 */
public final class NightMarketOffer {

    private final String id;
    private final String material;
    private final double price;
    private int stock;
    private final int maxStock;

    /**
     * Creates a night market offer.
     *
     * @param id       the offer id (matches a key in night-market.yml's pool)
     * @param material the Bukkit Material backing this offer
     * @param price    the fixed price for this rotation
     * @param stock    current remaining stock
     * @param maxStock the stock this offer started this rotation with
     */
    public NightMarketOffer(String id, String material, double price, int stock, int maxStock) {
        this.id = id;
        this.material = material;
        this.price = price;
        this.stock = stock;
        this.maxStock = maxStock;
    }

    public String getId() {
        return id;
    }

    public String getMaterial() {
        return material;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public int getMaxStock() {
        return maxStock;
    }

    public boolean isSoldOut() {
        return stock <= 0;
    }

    /**
     * Consumes stock for a purchase.
     *
     * @param amount the amount to consume
     * @return {@code true} if there was enough stock
     */
    public boolean consume(int amount) {
        if (stock < amount) {
            return false;
        }
        stock -= amount;
        return true;
    }

    /**
     * Returns stock to the offer, used to roll back a purchase that
     * was consumed but then failed to charge. Capped at max stock.
     *
     * @param amount the amount to return
     */
    public void refund(int amount) {
        stock = Math.min(maxStock, stock + amount);
    }
}