package com.theslave.ritual;

/** Hitos del guion en ticks de servidor. No depende de FPS ni de sonidos. */
public record SecuenciaRitual(int duracion, int abre, int entra, int llega, int sale, int vuelve) {
    public static SecuenciaRitual para(int tipo) {
        return switch (tipo) {
            case 0 -> new SecuenciaRitual(240, 40, 56, 90, 200, 236);
            case 1 -> new SecuenciaRitual(400, 140, 160, 220, 340, 396);
            case 2 -> new SecuenciaRitual(360, 140, 152, 200, 310, 356);
            case 3 -> new SecuenciaRitual(480, 180, 200, 270, 420, 476);
            default -> throw new IllegalArgumentException("Tipo ritual desconocido");
        };
    }
    public int apertura(int tick) {
        if (tick < abre || tick >= duracion) return 0;
        if (tick < entra) return (int)(100.0 * (tick - abre) / (entra - abre));
        if (tick < vuelve) return 100;
        return (int)(100.0 * (duracion - tick) / (duracion - vuelve));
    }
    /** Materializacion gradual local; no pretende recortar la malla contra el portal. */
    public int presencia(int tick) {
        int aparece = Math.clamp((tick - entra) * 100 / 14, 0, 100);
        int desaparece = Math.clamp((vuelve - tick) * 100 / 12, 0, 100);
        return Math.min(aparece, desaparece);
    }
}
