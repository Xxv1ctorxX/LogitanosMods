package com.theslave.ritual;

/** Solo presentacion. No determina identidad, inventario, consumo ni resultado del ritual. */
public record CoreografiaFusion(float resto, float convergencia, float costura, float descenso) {
    public static CoreografiaFusion en(float tick) {
        return new CoreografiaFusion(1-suave((tick-216)/36), suave((tick-252)/78), suave((tick-326)/24),suave((tick-336)/22));
    }
    private static float suave(float t) { t=Math.clamp(t,0,1);return t*t*(3-2*t); }
    public boolean activa() { return resto<1; }
}
