package com.theslave.ai;

import java.util.*;

/** Turnos compartidos por todos los trabajadores, sin referencias a entidades ni chunks. */
public final class PresupuestoEscaneo {
    private final ArrayDeque<UUID> cola=new ArrayDeque<>();
    private final Map<UUID,Long> peticiones=new HashMap<>();
    private final Map<UUID,Integer> permisos=new HashMap<>();
    private long tick=Long.MIN_VALUE;
    public int tomar(long ahora,UUID id,int global,int individual) {
        if(ahora!=tick) {
            permisos.clear();tick=ahora;
            int restante=Math.max(1,Math.min(4096,global)),vueltas=cola.size();
            int lote=Math.max(1,Math.min(256,individual));
            while(restante>0&&vueltas-->0&&!cola.isEmpty()) {
                UUID siguiente=cola.removeFirst();Long ultimo=peticiones.get(siguiente);
                if(ultimo==null||ahora<ultimo||ahora-ultimo>40) {peticiones.remove(siguiente);continue;}
                int n=Math.min(lote,restante);permisos.put(siguiente,n);restante-=n;cola.addLast(siguiente);
            }
        }
        if(!peticiones.containsKey(id)) {
            if(peticiones.size()>=4096)return 0;
            cola.addLast(id);
        }
        peticiones.put(id,ahora);
        Integer n=permisos.remove(id);return n==null?0:n;
    }
    public int registrados(){return peticiones.size();}
}
