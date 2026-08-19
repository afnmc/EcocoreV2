package io.azthera.ecocore.model;

/**
 * Represents a single tradeable item tracked by EcoCore's shop and
 * AI economy engine. Mirrors a row in the {@code shop_items} table.
 */
public final class ShopItemRecord {

    private final String id;
    private String category;
    private String material;
    private String namespacedKey;
    private double basePrice;
    private double currentPrice;
    private double minPrice;
    private double maxPrice;
    private int stock;
    private int maxStock;
    private double elasticity;
    private boolean tradeable;
    private long updatedAt;

    /**
     * Creates a new shop item record.
     *
     * @param id            unique item id (usually matches config key)
     * @param category      shop category id from shop.yml
     * @param material      Bukkit Material name backing this item
     * @param namespacedKey optional namespaced key for custom items, may be {@code null}
     * @param basePrice     the reference price used when no market data exists yet
     * @param currentPrice  the current AI-computed live price
     * @param minPrice      the minimum allowed price (price floor)
     * @param maxPrice      the maximum allowed price (price ceiling)
     * @param stock         current stock count
     * @param maxStock      maximum stock capacity
     * @param elasticity    price elasticity coefficient used by the AI engine
     * @param tradeable     whether this item is currently allowed to be traded
     * @param updatedAt     epoch millis of the last price/stock update
     */
    public ShopItemRecord(String id, String category, String material, String namespacedKey,
                           double basePrice, double currentPrice, double minPrice, double maxPrice,
                           int stock, int maxStock, double elasticity, boolean tradeable, long updatedAt) {
        this.id = id;
        this.category = category;
        this.material = material;
        this.namespacedKey = namespacedKey;
        this.basePrice = basePrice;
        this.currentPrice = currentPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.stock = stock;
        this.maxStock = maxStock;
        this.elasticity = elasticity;
        this.tradeable = tradeable;
        this.updatedAt = updatedAt;
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getNamespacedKey() {
        return namespacedKey;
    }

    public void setNamespacedKey(String namespacedKey) {
        this.namespacedKey = namespacedKey;
    }

    public double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(double basePrice) {
        this.basePrice = basePrice;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    /**
     * Updates the current live price, clamped between {@link #getMinPrice()}
     * and {@link #getMaxPrice()}, and refreshes the updated-at timestamp.
     *
     * @param newPrice the newly computed price from the AI engine
     */
    public void setCurrentPrice(double newPrice) {
        this.currentPrice = Math.max(minPrice, Math.min(maxPrice, newPrice));
        this.updatedAt = System.currentTimeMillis();
    }

    public double getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(double minPrice) {
        this.minPrice = minPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(double maxPrice) {
        this.maxPrice = maxPrice;
    }

    public int getStock() {
        return stock;
    }

    /**
     * Adjusts stock by a delta (positive to restock, negative to consume).
     * Clamped between 0 and {@link #getMaxStock()}.
     *
     * @param delta the amount to add (or subtract if negative)
     */
    public void adjustStock(int delta) {
        this.stock = Math.max(0, Math.min(maxStock, this.stock + delta));
        this.updatedAt = System.currentTimeMillis();
    }

    public void setStock(int stock) {
        this.stock = Math.max(0, Math.min(maxStock, stock));
    }

    public int getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(int maxStock) {
        this.maxStock = maxStock;
        if (this.stock > maxStock) {
            this.stock = maxStock;
        }
    }

    public double getElasticity() {
        return elasticity;
    }

    public void setElasticity(double elasticity) {
        this.elasticity = elasticity;
    }

    public boolean isTradeable() {
        return tradeable;
    }

    public void setTradeable(boolean tradeable) {
        this.tradeable = tradeable;
    }

    /**
     * Whether this item is currently sold out and cannot be bought
     * (players may still sell into it, per EcoCore's stock rules).
     *
     * @return {@code true} if stock is zero
     */
    public boolean isSoldOut() {
        return stock <= 0;
    }

    /**
     * Returns the current stock as a percentage of max stock, 0-100.
     *
     * @return stock percentage
     */
    public double stockPercent() {
        if (maxStock <= 0) {
            return 0.0;
        }
        return (stock / (double) maxStock) * 100.0;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}