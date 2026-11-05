package com.theslave.persistencia;

import com.google.gson.*;
import com.theslave.Registro;
import com.theslave.TheSlaveMod;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** Transaccion de compras y devoluciones: PREPARADA -> registro -> jugador+ACK -> CONFIRMADA. */
public final class TransaccionJugador {
    private static final Map<UUID,UUID> FALLOS=new HashMap<>();
    private static boolean registrado;
    private TransaccionJugador() {}
    private static DiarioOperaciones diario(MinecraftServer server) {
        return new DiarioOperaciones(server.getWorldPath(LevelResource.ROOT).resolve("theslave_operaciones_jugador"));
    }
    public static boolean bloqueado(UUID jugador) { return CerrojosJugador.ocupada(jugador); }
    public static void registrar() {
        if (registrado) return;
        registrado=true;
        CustodiaJugador.registrar();
        ServerLifecycleEvents.SERVER_STARTING.register(server->diario(server).restaurarCerrojos());
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->diario(server).olvidar(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{ CerrojosJugador.limpiar(); FALLOS.clear();DiarioOperaciones.limpiarIndices(); });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->recuperar(handler.player));
    }
    private static void fallar(ServerPlayer p,RuntimeException ex) {
        UUID op=CerrojosJugador.operacion(p.getUUID());
        if (op==null) { op=UUID.randomUUID(); CerrojosJugador.bloquear(p.getUUID(),op); }
        FALLOS.put(p.getUUID(),op);
        TheSlaveMod.LOGGER.error("Operacion durable pendiente de recuperar para {}. Se impide continuar jugando.",p.getUUID(),ex);
        p.connection.disconnect(Component.literal("TheSlave: no se pudo confirmar una operacion. Vuelve a entrar para recuperarla; si persiste, revisa el almacenamiento del mundo."));
    }
    public static void ejecutar(ServerPlayer p,String tipo,UUID op,JsonArray antesInventario,JsonArray despuesInventario,
            JsonArray antesCuenta,JsonArray despuesCuenta) {
        ejecutar(p,tipo,op,antesInventario,despuesInventario,antesCuenta,despuesCuenta,false);
    }
    public static void ejecutarBajoCustodia(ServerPlayer p,String tipo,UUID op,JsonArray antesInventario,JsonArray despuesInventario,
            JsonArray antesCuenta,JsonArray despuesCuenta) {
        ejecutar(p,tipo,op,antesInventario,despuesInventario,antesCuenta,despuesCuenta,true);
    }
    public static boolean confirmada(MinecraftServer server,UUID dueno,UUID op) {
        return diario(server).confirmada(dueno,op);
    }
    private static void ejecutar(ServerPlayer p,String tipo,UUID op,JsonArray antesInventario,JsonArray despuesInventario,
            JsonArray antesCuenta,JsonArray despuesCuenta,boolean conservarCustodia) {
        if (bloqueado(p.getUUID()) && !op.equals(CerrojosJugador.operacion(p.getUUID()))) throw new IllegalStateException("Jugador bloqueado por otra operacion pendiente");
        JsonObject payload=new JsonObject();
        payload.add("inventarioAntes",antesInventario); payload.add("inventarioDespues",despuesInventario);
        payload.add("cuentaAntes",antesCuenta); payload.add("cuentaDespues",despuesCuenta);
        var entry=new DiarioOperaciones.Entrada(op,p.getUUID(),tipo,DiarioOperaciones.Estado.PREPARADA,payload);
        CustodiaJugador.bloquear(p,op);
        try {
            var store=diario(p.level().getServer());
            store.preparar(entry);
            completar(p,store,entry,false);
            if (!conservarCustodia) CustodiaJugador.desbloquear(p.getUUID(),op);
        } catch (RuntimeException ex) { fallar(p,ex); throw ex; }
    }
    public static void recuperar(ServerPlayer p) {
        UUID falloAnterior=FALLOS.remove(p.getUUID());
        if (falloAnterior!=null) CerrojosJugador.desbloquear(p.getUUID(),falloAnterior);
        try {
            var store=diario(p.level().getServer());
            var pendientes=store.pendientes(p.getUUID());
            if (pendientes.size()>1) throw new IllegalStateException("Varias operaciones pendientes del mismo jugador");
            for (var entry:pendientes) {
                CustodiaJugador.bloquear(p,entry.id());
                completar(p,store,entry,true);
                CustodiaJugador.desbloquear(p.getUUID(),entry.id());
            }
        } catch (RuntimeException ex) { fallar(p,ex); }
    }
    private static void completar(ServerPlayer p,DiarioOperaciones store,DiarioOperaciones.Entrada entry,boolean recuperando) {
        if (!entry.dueno().equals(p.getUUID())) throw new IllegalStateException("Dueno de operacion incorrecto");
        store.completar(entry,new DiarioOperaciones.Participantes() {
            public void aplicarRegistro(DiarioOperaciones.Entrada e) {
                aplicarCuenta(store,e);
            }
            public void aplicarJugador(DiarioOperaciones.Entrada e) {
                if (recuperando) CustodiaJugador.recuperar(p,e.id(),e.datos().getAsJsonArray("inventarioAntes"),e.datos().getAsJsonArray("inventarioDespues"));
                else CustodiaJugador.aplicar(p,e.id(),e.datos().getAsJsonArray("inventarioAntes"),e.datos().getAsJsonArray("inventarioDespues"));
            }
            public void confirmarJugador(DiarioOperaciones.Entrada e) { CustodiaJugador.guardarConfirmado(p,e.id()); }
        });
    }
    /** Participante puro compartido por produccion y pruebas de replay comercial. */
    public static void aplicarCuenta(DiarioOperaciones store,DiarioOperaciones.Entrada e) {
        var antes=e.datos().getAsJsonArray("cuentaAntes");var despues=e.datos().getAsJsonArray("cuentaDespues");
        boolean comercial=esComercial(e.tipo());
        if(comercial && antes.equals(despues))return;
        if(comercial) {
            UUID anterior=Registro.reciboComercial(e.dueno());
            UUID confirmado=anterior!=null&&store.confirmada(e.dueno(),anterior)?anterior:null;
            Registro.aplicarOperacionComercial(e.id(),e.dueno(),antes,despues,confirmado);
        } else Registro.aplicarOperacion(e.id(),e.dueno(),antes,despues);
    }
    private static boolean esComercial(String tipo) {
        return switch(tipo) {case "ADQUISICION","DEVOLVER","TRIBUTO","SELLO","DOBLE","RELICARIO","ALTAR"->true;default->false;};
    }
}
