package com.theslave.persistencia;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Compartido por compras y rituales; las lecturas desde red son seguras. */
public final class CerrojosJugador {
    private static final ConcurrentHashMap<UUID,UUID> OPERACIONES=new ConcurrentHashMap<>();
    private CerrojosJugador() {}
    public static void bloquear(UUID dueno,UUID op) {
        UUID previa=OPERACIONES.putIfAbsent(dueno,op);
        if (previa!=null && !previa.equals(op)) throw new IllegalStateException("Jugador con otra operacion en curso");
    }
    public static boolean ocupada(UUID dueno) { return dueno!=null && OPERACIONES.containsKey(dueno); }
    public static UUID operacion(UUID dueno) { return OPERACIONES.get(dueno); }
    public static void desbloquear(UUID dueno,UUID op) { OPERACIONES.remove(dueno,op); }
    public static void limpiar() { OPERACIONES.clear(); }
}
