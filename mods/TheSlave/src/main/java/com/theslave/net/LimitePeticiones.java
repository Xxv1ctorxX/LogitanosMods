package com.theslave.net;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Presupuesto acotado por conexion, previo al trabajo de inventario, disco o busqueda. */
public final class LimitePeticiones {
    public enum Accion {
        NOMBRE(1000), ORDEN(250), COMPRA(1000), TUMBA(250), FUSION(1000);
        final long intervaloMillis;
        Accion(long intervaloMillis) { this.intervaloMillis=intervaloMillis; }
    }
    private static final class Cupo {
        double disponibles=8;
        long actualizado;
        final long[] ultimo=new long[Accion.values().length];
        final boolean[] utilizado=new boolean[Accion.values().length];
        Cupo(long ahora) { actualizado=ahora; }
    }
    private final ConcurrentHashMap<UUID,Cupo> conexiones=new ConcurrentHashMap<>();
    public boolean admitir(UUID jugador,Accion accion,long ahoraMillis) {
        if(jugador==null || accion==null)return false;
        Cupo cupo=conexiones.computeIfAbsent(jugador,id->new Cupo(ahoraMillis));
        synchronized(cupo) {
            if(ahoraMillis<cupo.actualizado)return false;
            long transcurrido=ahoraMillis-cupo.actualizado;
            cupo.disponibles=Math.min(8,cupo.disponibles+transcurrido*0.004);
            cupo.actualizado=ahoraMillis;
            int i=accion.ordinal();
            if(cupo.utilizado[i] && ahoraMillis-cupo.ultimo[i]<accion.intervaloMillis)return false;
            if(cupo.disponibles<1)return false;
            cupo.disponibles--;cupo.ultimo[i]=ahoraMillis;cupo.utilizado[i]=true;
            return true;
        }
    }
    public void desconectar(UUID jugador) { conexiones.remove(jugador); }
    public void limpiar() { conexiones.clear(); }
    public int conexiones() { return conexiones.size(); }
}
