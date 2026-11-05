package com.livecrops;

/** Un acumulador por chunk. Nunca conserva una lista de intervalos. */
public final class DormantLedger {
    private double accumulated;
    private double anchor;

    public DormantLedger(double accumulated, double anchor) {
        if (!Double.isFinite(accumulated) || accumulated < 0 || !Double.isFinite(anchor) || anchor < 0) {
            throw new IllegalArgumentException("Reloj de chunk inválido");
        }
        this.accumulated = accumulated;
        this.anchor = anchor;
    }

    public double effective(double clock) {
        return accumulated + Math.max(0, clock - anchor);
    }

    public void attended(double before, double after) {
        accumulated = effective(before);
        anchor = after;
    }

    public void rebase(double clock) {
        anchor = clock;
    }

    public double accumulated() { return accumulated; }
    public double anchor() { return anchor; }
}
