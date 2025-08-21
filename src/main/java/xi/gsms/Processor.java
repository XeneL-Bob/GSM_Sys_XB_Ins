package src.main.java.xi.gsms;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static xi.gsms.Model.*;   // <-- update this static import too
import xi.gsms.Model.Inventory;
import xi.gsms.Model.ItemState;
import xi.gsms.Model.SaleRecord;
import xi.gsms.Model.SaleSegment;
import xi.gsms.Model.StockBatch;

/** Command processor: applies operations, prints CHECK and PROFIT. */
final class Processor {

    void processFile(String pathStr) throws IOException {
        Inventory inv = new Inventory();
        Path path = Paths.get(pathStr);

        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                // fields are separated by a single space per spec
                String[] t = line.split(" ");
                String cmd = t[0];

                if (inv.invalid) {
                    // We still consume lines; only PROFIT will print NA.
                    if ("PROFIT".equalsIgnoreCase(cmd)) {
                        System.out.println("Profit/Loss: NA");
                    }
                    continue;
                }

                switch (cmd) {
                    case "STOCK" -> handleStock(inv, t);
                    case "ORDER" -> handleOrder(inv, t);
                    case "EXPIRE" -> handleExpire(inv, t);
                    case "RETURN" -> handleReturn(inv, t);
                    case "DISCOUNT" -> handleDiscount(inv, t);
                    case "DISCOUNT_END" -> handleDiscountEnd(inv, t);
                    case "CHECK" -> handleCheck(inv);
                    case "PROFIT" -> handleProfit(inv);
                    default -> {
                        // Unknown commands are not specified; mark invalid conservatively.
                        inv.invalid = true;
                    }
                }
            }
        }
    }

    // ---------- handlers ----------

    private void handleStock(Inventory inv, String[] t) {
        if (t.length != 4) { inv.invalid = true; return; }
        String item = t[1];
        int qty;
        double cost;
        try {
            qty = parsePositiveInt(t[2]);      // allow 0 as no-op; negative -> invalid
            cost = parseDouble(t[3]);
        } catch (NumberFormatException e) { inv.invalid = true; return; }

        // Exceptions: cost <= 0 is invalid per spec
        if (cost <= 0.0) { inv.invalid = true; return; }
        if (qty == 0) return; // no-op

        ItemState s = inv.getItem(item);
        s.stockFIFO.addLast(new StockBatch(qty, cost));
        s.totalQtyInStock += qty;
    }

    private void handleOrder(Inventory inv, String[] t) {
        if (t.length != 4) { inv.invalid = true; return; }
        String item = t[1];
        int qty;
        double sell;
        try {
            qty = parsePositiveInt(t[2]);
            sell = parseDouble(t[3]);
        } catch (NumberFormatException e) { inv.invalid = true; return; }

        // Exceptions: negative sell price is invalid
        if (sell < 0.0) { inv.invalid = true; return; }
        if (qty == 0) return; // no-op

        ItemState s = inv.getItem(item);
        if (s.totalQtyInStock < qty) { inv.invalid = true; return; }

        int discount = s.discountStack.isEmpty() ? 0 : s.discountStack.peek();
        double effectiveSell = applyDiscount(sell, discount);
        String baseKey = priceKey(sell);

        SaleRecord sr = new SaleRecord(baseKey, effectiveSell, qty);

        int remaining = qty;
        while (remaining > 0) {
            StockBatch b = s.stockFIFO.peekFirst();
            int take = Math.min(remaining, b.qty);
            inv.profit += take * (effectiveSell - b.cost);

            sr.segmentsFIFO.addLast(new SaleSegment(take, b.cost));

            b.qty -= take;
            if (b.qty == 0) s.stockFIFO.removeFirst();
            remaining -= take;
            s.totalQtyInStock -= take;
        }

        s.salesByBaseSell.computeIfAbsent(baseKey, k -> new ArrayDeque<>()).push(sr);
    }

    private void handleExpire(Inventory inv, String[] t) {
        if (t.length != 3) { inv.invalid = true; return; }
        String item = t[1];
        int qty;
        try {
            qty = parsePositiveInt(t[2]);
        } catch (NumberFormatException e) { inv.invalid = true; return; }

        if (qty == 0) return; // no-op

        ItemState s = inv.getItem(item);
        if (s.totalQtyInStock < qty) { inv.invalid = true; return; }

        int remaining = qty;
        while (remaining > 0) {
            StockBatch b = s.stockFIFO.peekFirst();
            int take = Math.min(remaining, b.qty);
            inv.profit += -take * b.cost;

            b.qty -= take;
            if (b.qty == 0) s.stockFIFO.removeFirst();
            remaining -= take;
            s.totalQtyInStock -= take;
        }
    }

    private void handleReturn(Inventory inv, String[] t) {
        if (t.length != 4) { inv.invalid = true; return; }
        String item = t[1];
        int qty;
        double sell;
        try {
            qty = parsePositiveInt(t[2]);
            sell = parseDouble(t[3]);
        } catch (NumberFormatException e) { inv.invalid = true; return; }

        if (qty == 0) return; // no-op

        ItemState s = inv.getItem(item);
        String baseKey = priceKey(sell);
        Deque<SaleRecord> stack = s.salesByBaseSell.get(baseKey);
        if (stack == null || stack.isEmpty()) { inv.invalid = true; return; }

        int needed = qty;

        while (needed > 0) {
            if (stack.isEmpty()) { inv.invalid = true; return; }

            SaleRecord sr = stack.peek(); // most recent sale at this base price
            int canTake = Math.min(needed, sr.qtyRemaining);
            if (canTake <= 0) {
                // Defensive: remove empty sale records
                stack.pop();
                continue;
            }
            int remainingFromSale = canTake;

            while (remainingFromSale > 0) {
                if (sr.segmentsFIFO.isEmpty()) { inv.invalid = true; return; }
                SaleSegment seg = sr.segmentsFIFO.peekFirst();
                int segTake = Math.min(remainingFromSale, seg.qty);

                inv.profit += -segTake * (sr.effectiveSell - seg.cost);

                seg.qty -= segTake;
                if (seg.qty == 0) sr.segmentsFIFO.removeFirst();

                remainingFromSale -= segTake;
            }

            sr.qtyRemaining -= canTake;
            needed -= canTake;

            if (sr.qtyRemaining == 0 && sr.segmentsFIFO.isEmpty()) {
                stack.pop();
            }
        }
    }

    private void handleDiscount(Inventory inv, String[] t) {
        if (t.length != 3) { inv.invalid = true; return; }
        String item = t[1];
        int percent;
        try {
            percent = Integer.parseInt(t[2]);
        } catch (NumberFormatException e) { inv.invalid = true; return; }

        ItemState s = inv.getItem(item);
        s.discountStack.push(percent);
    }

    private void handleDiscountEnd(Inventory inv, String[] t) {
        if (t.length != 2) { inv.invalid = true; return; }
        String item = t[1];
        ItemState s = inv.getItem(item);
        if (!s.discountStack.isEmpty()) s.discountStack.pop();
        // If empty, silently ignore per spec (no exception required).
    }

    private void handleCheck(Inventory inv) {
        if (inv.invalid) return; // Spec: if NA, no need to output item quantities.
        for (Map.Entry<String, ItemState> e : inv.items.entrySet()) {
            System.out.println(e.getKey() + ": " + e.getValue().totalQtyInStock);
        }
    }

    private void handleProfit(Inventory inv) {
        if (inv.invalid) {
            System.out.println("Profit/Loss: NA");
        } else {
            System.out.println("Profit/Loss: $" + money(inv.profit));
        }
    }
}
