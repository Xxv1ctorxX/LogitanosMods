package com.theslave.ritual;

/** Prioridad estable para un observador que queda entre dos altares. */
public final class PrioridadEscena {
    private PrioridadEscena() {}
    public static boolean preferir(boolean propia,double distancia,long altar,boolean actualPropia,double actualDistancia,long actualAltar) {
        if(!Double.isFinite(distancia)||distancia>24*24)return false;
        if(propia!=actualPropia)return propia;
        return distancia<actualDistancia||(distancia==actualDistancia&&altar<actualAltar);
    }
}
