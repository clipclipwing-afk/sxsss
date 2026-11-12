package dev.carryledger;

/** "earned / total" coins line for the HUD. */
public final class HudSum {

    private HudSum() {}

    public static String fmtCoins(double fallback) {
        Data d = CarryLedger.data;
        if (d == null || d.clients == null || d.clients.isEmpty()) return CarryLedger.fmtCoins(fallback);
        double earned = 0, total = 0;
        for (Data.Client c : d.clients) {
            double p = d.price(c.type(), c.tier);
            earned += p * c.done;
            total += p * c.total;
        }
        return CarryLedger.fmtCoins(earned) + " §7/ §6" + CarryLedger.fmtCoins(total);
    }
}
