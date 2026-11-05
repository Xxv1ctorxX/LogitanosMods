package com.theslave.client;

import com.google.gson.*;
import com.theslave.Registro;
import com.theslave.persistencia.*;
import java.nio.file.*;
import java.util.*;

/** Replays antiguos con recibos compactos y poda comercial; usa los participantes de produccion. */
public final class EconomiaReplayTest {
    private static int checks;
    private static void require(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static DiarioOperaciones.Entrada entry(UUID owner,String tipo,JsonArray before,JsonArray after) {
        JsonObject payload=new JsonObject();payload.add("cuentaAntes",before);payload.add("cuentaDespues",after);
        payload.addProperty("inventarioDePrueba","x".repeat(24000));
        return new DiarioOperaciones.Entrada(UUID.randomUUID(),owner,tipo,DiarioOperaciones.Estado.PREPARADA,payload);
    }
    private static final class Pago implements DiarioOperaciones.Participantes {
        final DiarioOperaciones store;int pagos;
        Pago(DiarioOperaciones store){this.store=store;}
        public void aplicarRegistro(DiarioOperaciones.Entrada e){TransaccionJugador.aplicarCuenta(store,e);}
        public void aplicarJugador(DiarioOperaciones.Entrada e){pagos++;}
        public void confirmarJugador(DiarioOperaciones.Entrada e){}
    }
    public static void run() throws Exception {
        var data=Registro.class.getDeclaredField("datos");data.setAccessible(true);
        var file=Registro.class.getDeclaredField("archivo");file.setAccessible(true);
        var load=Registro.class.getDeclaredMethod("cargarDesde",Path.class);load.setAccessible(true);
        Object oldData=data.get(null),oldFile=file.get(null);
        Path base=Files.createTempDirectory("theslave-economy-replay").toAbsolutePath().normalize();
        try {
            load.invoke(null,base);UUID owner=UUID.randomUUID();
            var store=new DiarioOperaciones(base.resolve("wal"));var pago=new Pago(store);
            var empty=new JsonArray();var withReservation=new JsonArray();
            var link=new Registro.Vinculo();link.estado=Registro.Estado.RESERVADO;link.contrato=UUID.randomUUID();
            withReservation.add(Registro.codificarVinculo(link));
            var acquisition=entry(owner,"ADQUISICION",empty,withReservation);store.preparar(acquisition);
            pago.aplicarRegistro(acquisition);
            require(Registro.operacionAplicada(acquisition.id()),"Falta recibo PREP aplicado");
            try{TransaccionJugador.aplicarCuenta(store,entry(owner,"DEVOLVER",withReservation,empty));throw new AssertionError("Se poda recibo antes de confirmar COMMIT");}
            catch(IllegalStateException expected){checks++;}
            store.completar(acquisition,pago);
            Path receipt=base.resolve("wal").resolve(owner.toString()).resolve(acquisition.id()+".json");
            require(Files.size(receipt)<1024,"COMMIT conserva los snapshots completos del inventario");
            require(store.leerOperacion(owner,acquisition.id()).orElseThrow().datos().isEmpty(),"Recibo conserva payload en RAM");
            UUID external=UUID.randomUUID();Registro.aplicarOperacion(external,owner,withReservation,withReservation);
            var refund=entry(owner,"DEVOLVER",withReservation,empty);store.preparar(refund);store.completar(refund,pago);
            require(!Registro.operacionAplicada(acquisition.id()),"Registro acumula recibos comerciales viejos");
            require(Registro.operacionAplicada(external),"Poda comercial elimina recibo externo");
            require(Registro.reciboComercial(owner).equals(refund.id()),"Indice comercial no apunta al ultimo recibo");
            int originalPayments=pago.pagos;
            store.completar(acquisition,pago);store.completar(refund,pago);
            require(pago.pagos==originalPayments&&Registro.cuentaOperacion(owner).isEmpty(),"Replay antiguo vuelve a cobrar/reservar despues de podar");
            DiarioOperaciones.limpiarIndices();load.invoke(null,base);store=new DiarioOperaciones(base.resolve("wal"));pago=new Pago(store);
            store.restaurarCerrojos();store.completar(acquisition,pago);store.completar(refund,pago);
            require(pago.pagos==0&&Registro.cuentaOperacion(owner).isEmpty(),"Reinicio permite replay de recibo podado");
            require(!CerrojosJugador.ocupada(owner),"COMMIT reconstruye una custodia fantasma");
            var noAccountChange=entry(owner,"RELICARIO",empty,empty);store.preparar(noAccountChange);store.completar(noAccountChange,pago);
            require(!Registro.operacionAplicada(noAccountChange.id()),"Compra sin cambio de cuenta hace crecer Registro");
            // Formato anterior: COMMIT con snapshots completos y sin campo huella.
            JsonObject legacy=new JsonObject();legacy.addProperty("esquema",1);JsonObject operation=new JsonObject();
            operation.addProperty("id",acquisition.id().toString());operation.addProperty("dueno",owner.toString());
            operation.addProperty("tipo",acquisition.tipo());operation.addProperty("estado","CONFIRMADA");operation.add("datos",acquisition.datos());legacy.add("operacion",operation);
            ArchivoDurable.escribir(receipt,legacy);store.restaurarCerrojos();
            require(Files.size(receipt)<1024&&store.confirmada(owner,acquisition.id()),"Migracion COMMIT antiguo pierde confirmacion");
            originalPayments=pago.pagos;store.completar(acquisition,pago);
            require(pago.pagos==originalPayments,"Migracion compacta reaplica el pago");
            // Consultar un op no relee los snapshots/archivos de todo el historial.
            Path unrelated=receipt.resolveSibling(UUID.randomUUID()+".json");Files.writeString(unrelated,"corrupto");
            require(store.confirmada(owner,refund.id()),"Consulta directa depende de todo el historial");
            store.olvidar(owner);
            require(store.pendientes(owner).isEmpty(),"Reconectar relee todo el historial confirmado");
            try{store.restaurarCerrojos();throw new AssertionError("Arranque no verifica historial");}
            catch(RuntimeException expected){checks++;}
            Files.delete(unrelated);
        }finally {
            data.set(null,oldData);file.set(null,oldFile);DiarioOperaciones.limpiarIndices();CerrojosJugador.limpiar();
            try(var paths=Files.walk(base)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) {
                if(!p.toAbsolutePath().normalize().startsWith(base))throw new IllegalStateException("Fuera del temporal");Files.delete(p);
            }}
        }
        System.out.println("Economia y replay compacto: "+checks+" comprobaciones correctas.");
    }
    public static void main(String[] args)throws Exception{run();}
}
