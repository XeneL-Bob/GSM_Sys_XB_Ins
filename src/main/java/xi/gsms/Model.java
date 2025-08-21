package src.main.java.xi.gsms;

import java.util.*;

/** Data model classes and utilities. */
final class Model {

    static final class StockBatch {
        int qty;
        double cost;
        StockBatch(int qty, double cost) { this.qty = qty; this.cost = cost; }
    }

    static final class SaleSegment {
        int qty;
        double cost;
        SaleSegment(int qty, double cost) { this.qty = qty; this.cost = cost; }
    }

    /** A single ORDER captured for later RETURNs. */
    static final class SaleRecord {
        final String baseSellKey;       // "2.00"
        final double effectiveSell;     // after discount applied at ORDER time
        int qtyRemaining;               // remaining cancellable quantity
        final Deque<SaleSegment> segmentsFIFO = new ArrayDeque<>(); // FIFO across costs

        SaleRecord(String baseSellKey, double effectiveSell, int qtyRemaining) {
            this.baseSellKey = baseSellKey;
            this.effectiveSell = effectiveSell;
            this.qtyRemaining = qtyRemaining;
        }
    }

    /** Per-item state. */
    static final class ItemState {
        final Deque<StockBatch> stockFIFO = new ArrayDeque<>();
        final Deque<Integer> discountStack = new ArrayDeque<>();  // percentages, LIFO
        final Map<String, Deque<SaleRecord>> salesByBaseSell = new HashMap<>();
        int totalQtyInStock = 0;
    }

    /** Inventory for all items; LinkedHashMap preserves insertion order for CHECK printing. */
    static final class Inventory {
        final Map<String, ItemState> items = new LinkedHashMap<>();
        double profit = 0.0;
        boolean invalid = false;

        ItemState getItem(String name) {
            return items.computeIfAbsent(name, k -> new ItemState());
        }
    }

    // -------- utilities --------

    static String priceKey(double p) {
        return String.format(java.util.Locale.US, "%.2f", p);
    }

    static double applyDiscount(double base, int percent) {
        return base * (1.0 - percent / 100.0);
    }

    static int parsePositiveInt(String s) throws NumberFormatException {
        int v = Integer.parseInt(s);
        if (v < 0) throw new NumberFormatException("negative qty not allowed");
        return v;
    }

    static double parseDouble(String s) throws NumberFormatException {
        return Double.parseDouble(s);
    }

    static String money(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }
}
