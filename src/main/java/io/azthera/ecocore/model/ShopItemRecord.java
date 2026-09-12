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
    private String variantType;
    private String variantValue;
    private double basePrice;
    private double currentPrice;
    private double previousPrice;
    private double minPrice;
    private double maxPrice;
    private int stock;
    private int maxStock;
    private double elasticity;
    private boolean tradeable;
    private long updatedAt;

    /**
     * Epoch millis of the most recent RESTOCK_* event applied to this
     * item (Revisi 17) - admin adjustments (EVENT_ADMIN) deliberately
     * do NOT update this, since manual admin restocks are meant to
     * bypass the cooldown entirely.
     */
    private long lastRestockAt;

    /** How many RESTOCK_* events have landed within the current {@link #restockDayEpoch} day-bucket. */
    private int restocksToday;

    /** Which day-bucket (epoch millis / 86400000) {@link #restocksToday} is currently counting for. */
    private long restockDayEpoch;

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
     * @param lastRestockAt epoch millis of the last non-admin restock, 0 if never restocked
     * @param restocksToday how many non-admin restocks landed in the current day bucket
     * @param restockDayEpoch which day bucket restocksToday belongs to
     */
    public ShopItemRecord(String id, String category, String material, String namespacedKey,
                           double basePrice, double currentPrice, double minPrice, double maxPrice,
                           int stock, int maxStock, double elasticity, boolean tradeable, long updatedAt,
                           long lastRestockAt, int restocksToday, long restockDayEpoch) {
        this.id = id;
        this.category = category;
        this.material = material;
        this.namespacedKey = namespacedKey;
        this.variantType = null;
        this.variantValue = null;
        this.basePrice = basePrice;
        this.currentPrice = currentPrice;
        this.previousPrice = currentPrice;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.stock = stock;
        this.maxStock = maxStock;
        this.elasticity = elasticity;
        this.tradeable = tradeable;
        this.updatedAt = updatedAt;
        this.lastRestockAt = lastRestockAt;
        this.restocksToday = restocksToday;
        this.restockDayEpoch = restockDayEpoch;
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

    /**
     * Returns the optional item variant type, such as POTION,
     * SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW, or ENCHANTED_BOOK.
     */
    public String getVariantType() {
        return variantType;
    }

    public void setVariantType(String variantType) {
        this.variantType = variantType;
    }

    /**
     * Returns the variant-specific value, for example a potion type
     * or enchantment name. May be null for normal items.
     */
    public String getVariantValue() {
        return variantValue;
    }

    public void setVariantValue(String variantValue) {
        this.variantValue = variantValue;
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

    /**
     * The item's price as of just before the most recent AI pricing
     * cycle, snapshotted by {@code AiEconomyEngine} every cycle
     * (Revisi 20) regardless of whether the move crossed the
     * notification threshold - lets the shop GUI show a live
     * "10 -> 15 (+50%)" style delta without needing its own history.
     *
     * @return the pre-cycle reference price
     */
    public double getPreviousPrice() {
        return previousPrice;
    }

    /**
     * Snapshots the reference price used by {@link #getPriceChangePercent()}.
     *
     * @param previousPrice the price to remember as "before this cycle"
     */
    public void setPreviousPrice(double previousPrice) {
        this.previousPrice = previousPrice;
    }

    /**
     * The signed percent change between {@link #getPreviousPrice()}
     * and {@link #getCurrentPrice()} - positive means the price went
     * up, negative means it went down, {@code 0} if there's no
     * reference price yet (e.g. brand new item, first cycle).
     *
     * @return the signed percent change since the last snapshot
     */
    public double getPriceChangePercent() {
        if (previousPrice <= 0) {
            return 0.0;
        }
        return ((currentPrice - previousPrice) / previousPrice) * 100.0;
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

    public boolean isSoldOut() {
        return stock <= 0;
    }

    public double stockPercent() {
        if (maxStock <= 0) {
            return 0.0;
        }
        return (stock / (double) maxStock) * 100.0;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getLastRestockAt() {
        return lastRestockAt;
    }

    public void setLastRestockAt(long lastRestockAt) {
        this.lastRestockAt = lastRestockAt;
    }

    public int getRestocksToday() {
        return restocksToday;
    }

    public void setRestocksToday(int restocksToday) {
        this.restocksToday = restocksToday;
    }

    public long getRestockDayEpoch() {
        return restockDayEpoch;
    }

    public void setRestockDayEpoch(long restockDayEpoch) {
        this.restockDayEpoch = restockDayEpoch;
    }

    /**
     * Records that a non-admin restock just happened (Revisi 17):
     * updates {@link #lastRestockAt} and increments {@link
     * #restocksToday}, automatically rolling the day-bucket counter
     * over to 0/1 if the current day differs from {@link #restockDayEpoch}.
     *
     * @param nowMillis the current time in epoch millis
     */
    public void recordRestock(long nowMillis) {
        long currentDayBucket = nowMillis / 86_400_000L;
        if (currentDayBucket != restockDayEpoch) {
            restockDayEpoch = currentDayBucket;
            restocksToday = 0;
        }
        restocksToday++;
        lastRestockAt = nowMillis;
    }
}
