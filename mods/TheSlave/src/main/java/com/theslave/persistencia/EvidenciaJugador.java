package com.theslave.persistencia;

import java.util.*;
import net.minecraft.nbt.CompoundTag;

/** Verificacion del archivo playerdata, separada de los hooks para poder probar fallos de ACK sin juego. */
public final class EvidenciaJugador {
    private EvidenciaJugador() {}
    public static void verificar(CompoundTag actual,CompoundTag expected,UUID op) {
        if (!actual.getCompoundOrEmpty("fabric:attachments").getStringOr("theslave:operacion_jugador","").equals(op.toString()))
            throw new IllegalStateException("El guardado del jugador no confirmo el marcador");
        if (!expected.contains("Inventory")) throw new IllegalStateException("Serializacion esperada sin inventario");
        for (String key:List.of("Inventory","equipment"))
            if (!Objects.equals(actual.get(key),expected.get(key))) throw new IllegalStateException("Playerdata no contiene el inventario confirmado: "+key);
    }
}
