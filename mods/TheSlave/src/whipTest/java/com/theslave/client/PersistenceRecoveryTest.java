package com.theslave.client;

import com.google.gson.*;
import com.theslave.Registro;
import com.theslave.comercio.Comercio;
import com.theslave.persistencia.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Abortos simulados entre escrituras reales; utiliza el WAL y el registro de produccion. */
public final class PersistenceRecoveryTest {
    private static int checks;
    private static final class Caida extends Error {}
    private static void require(boolean ok,String text) { checks++; if (!ok) throw new AssertionError(text); }
    private static void rejectAck(net.minecraft.nbt.CompoundTag actual,net.minecraft.nbt.CompoundTag expected,UUID op) {
        try { EvidenciaJugador.verificar(actual,expected,op);throw new AssertionError("ACK incompleto admitido"); }
        catch(IllegalStateException expectedFailure) { checks++; }
    }
    private static JsonObject player(int iron,int gold,int diamonds,int souls,boolean contract) {
        var o=new JsonObject(); o.addProperty("iron",iron);o.addProperty("gold",gold);o.addProperty("diamonds",diamonds);
        o.addProperty("soulsEach",souls);o.addProperty("contract",contract);o.addProperty("marker","");return o;
    }
    private static final class Simulado implements DiarioOperaciones.Participantes {
        final Path playerFile; JsonObject memory; int crash; int writes; Path impedirCommit;
        Simulado(Path path,int crash) { playerFile=path;this.crash=crash;memory=ArchivoDurable.leer(path).getAsJsonObject(); }
        public void aplicarRegistro(DiarioOperaciones.Entrada e) {
            TransaccionJugador.aplicarCuenta(new DiarioOperaciones(playerFile.getParent().resolve("wal")),e);
            if (crash==1) throw new Caida();
        }
        public void aplicarJugador(DiarioOperaciones.Entrada e) {
            if (!memory.get("marker").getAsString().equals(e.id().toString())) {
                memory=e.datos().getAsJsonObject("despues").deepCopy();memory.addProperty("marker",e.id().toString());writes++;
            }
            if (crash==2) throw new Caida();
        }
        public void confirmarJugador(DiarioOperaciones.Entrada e) {
            ArchivoDurable.escribir(playerFile,memory);
            require(ArchivoDurable.leer(playerFile).equals(memory),"ACK de playerdata falso");
            if (impedirCommit!=null) {
                try { Files.createDirectory(impedirCommit); }
                catch(java.io.IOException ex) { throw new IllegalStateException(ex); }
            }
            if (crash==3) throw new Caida();
        }
    }
    public static void run() throws Exception {
        var data=Registro.class.getDeclaredField("datos"); data.setAccessible(true);
        var path=Registro.class.getDeclaredField("archivo"); path.setAccessible(true);
        var load=Registro.class.getDeclaredMethod("cargarDesde",Path.class); load.setAccessible(true);
        Object oldData=data.get(null),oldPath=path.get(null);
        Path base=Files.createTempDirectory("theslave-crash-recovery").toAbsolutePath().normalize();
        try {
            for (int grade=0;grade<3;grade++) for (boolean refund:new boolean[]{false,true}) for (int frontier=0;frontier<=4;frontier++) {
                Path world=Files.createDirectory(base.resolve("case-"+grade+"-"+refund+"-"+frontier));
                load.invoke(null,world);
                UUID owner=UUID.randomUUID(),op=UUID.randomUUID(),contract=UUID.randomUUID();
                JsonArray baseline=new JsonArray();
                for (int i=0;i<grade;i++) {
                    var live=new Registro.Vinculo();live.esclavo=UUID.randomUUID();baseline.add(Registro.codificarVinculo(live));
                }
                Registro.aplicarOperacion(UUID.randomUUID(),owner,new JsonArray(),baseline);
                JsonArray withReservation=baseline.deepCopy();
                var reserved=new Registro.Vinculo();reserved.estado=Registro.Estado.RESERVADO;reserved.grado=grade;reserved.contrato=contract;
                withReservation.add(Registro.codificarVinculo(reserved));
                if (refund) Registro.aplicarOperacion(UUID.randomUUID(),owner,baseline,withReservation);
                var price=Comercio.precioAdquisicion(grade);
                JsonObject before=player(100,100,100,60,refund);
                int direction=refund?1:-1;
                JsonObject after=player(100+direction*price.hierro(),100+direction*price.oro(),100+direction*price.diamante(),60+direction*price.almasPorTipo(),!refund);
                JsonObject payload=new JsonObject();payload.add("antes",before);payload.add("despues",after);
                payload.add("cuentaAntes",refund?withReservation:baseline);payload.add("cuentaDespues",refund?baseline:withReservation);
                var entry=new DiarioOperaciones.Entrada(op,owner,refund?"DEVOLVER":"ADQUISICION",DiarioOperaciones.Estado.PREPARADA,payload);
                var store=new DiarioOperaciones(world.resolve("wal"));
                Path playerPath=world.resolve("player.json");ArchivoDurable.escribir(playerPath,before);
                store.preparar(entry); // Frontera 0: PREPARADA ya durable, ningun efecto aplicado.
                store.restaurarCerrojos();
                require(op.equals(CerrojosJugador.operacion(owner)),"Startup no protege cuenta del dueno desconectado");
                Simulado first=new Simulado(playerPath,frontier);
                if (frontier>0) {
                    try { store.completar(entry,first); if (frontier==4) throw new Caida(); throw new AssertionError("No se inyecto caida"); }
                    catch (Caida expected) { checks++; }
                }
                // Reinicio: descarta memoria de registro, jugador y coordinador; solo quedan archivos.
                load.invoke(null,world);
                DiarioOperaciones.limpiarIndices();CerrojosJugador.limpiar();
                store=new DiarioOperaciones(world.resolve("wal"));
                store.restaurarCerrojos();
                var persisted=store.leerOperacion(owner,op).orElseThrow();
                Simulado recovered=new Simulado(playerPath,-1);
                store.completar(persisted,recovered);
                require(Registro.cuentaOperacion(owner).equals(refund?baseline:withReservation),"Plaza perdida/duplicada tras caida");
                JsonObject expected=after.deepCopy();expected.addProperty("marker",op.toString());
                require(ArchivoDurable.leer(playerPath).equals(expected),"Pago, almas u objetos aplicados mas/menos de una vez");
                require(store.confirmada(owner,op),"Operacion no confirmada");
                CerrojosJugador.desbloquear(owner,op);
                int previousWrites=recovered.writes;
                store.completar(persisted,recovered);
                require(recovered.writes==previousWrites,"Recuperacion repetida aplica inventario otra vez");
                recovered.memory.addProperty("iron",expected.get("iron").getAsInt()+7);
                ArchivoDurable.escribir(playerPath,recovered.memory);
                store.completar(persisted,recovered);
                require(ArchivoDurable.leer(playerPath).getAsJsonObject().get("iron").getAsInt()==expected.get("iron").getAsInt()+7,"COMMIT restaura snapshot antiguo y borra juego posterior");
                var changed=payload.deepCopy();changed.addProperty("alterado",true);
                try { store.preparar(new DiarioOperaciones.Entrada(op,owner,"otro",DiarioOperaciones.Estado.PREPARADA,changed)); throw new AssertionError("opId reutilizable"); }
                catch (IllegalStateException expectedError) { checks++; }
            }
            // Preparacion que falla: ningun participante puede ejecutarse.
            UUID owner=UUID.randomUUID(),op=UUID.randomUUID();
            var store=new DiarioOperaciones(base.resolve("failure-wal"));
            var directory=Files.createDirectories(base.resolve("failure-wal").resolve(owner.toString()));
            Files.createDirectory(directory.resolve(op+".json.tmp"));
            var entry=new DiarioOperaciones.Entrada(op,owner,"ADQUISICION",DiarioOperaciones.Estado.PREPARADA,new JsonObject());
            try { store.preparar(entry); throw new AssertionError("WAL sin persistir admitido"); }
            catch (IllegalStateException expected) { checks++; }
            require(store.pendientes(owner).isEmpty(),"Preparacion fallida aparece confirmada");
            // Mutex realmente compartido y con unlock por identidad.
            CerrojosJugador.bloquear(owner,op);
            try { CerrojosJugador.bloquear(owner,UUID.randomUUID());throw new AssertionError("Dos operaciones simultaneas"); }
            catch (IllegalStateException expected) { checks++; }
            CerrojosJugador.desbloquear(owner,UUID.randomUUID());require(CerrojosJugador.ocupada(owner),"Otro opId puede quitar custodia");
            CerrojosJugador.desbloquear(owner,op);require(!CerrojosJugador.ocupada(owner),"Custodia no liberada");
            var expectedNbt=new net.minecraft.nbt.CompoundTag();
            expectedNbt.put("Inventory",new net.minecraft.nbt.ListTag());
            var equipment=new net.minecraft.nbt.CompoundTag();equipment.putString("offhand","relicario");expectedNbt.put("equipment",equipment);
            var saved=expectedNbt.copy();var attachments=new net.minecraft.nbt.CompoundTag();attachments.putString("theslave:operacion_jugador",op.toString());saved.put("fabric:attachments",attachments);
            EvidenciaJugador.verificar(saved,expectedNbt,op);checks++;
            rejectAck(expectedNbt,expectedNbt,op); // save() que vuelve sin guardar el nuevo marcador.
            rejectAck(saved,expectedNbt,UUID.randomUUID());
            var wrongInventory=saved.copy();wrongInventory.remove("Inventory");rejectAck(wrongInventory,expectedNbt,op);
            var wrongEquipment=saved.copy();wrongEquipment.remove("equipment");rejectAck(wrongEquipment,expectedNbt,op);
            rejectAck(saved,new net.minecraft.nbt.CompoundTag(),op);
            // ACK del jugador conseguido pero escritura COMMIT falla: el marcador evita un segundo pago.
            Path commitWorld=Files.createDirectory(base.resolve("commit-failure"));load.invoke(null,commitWorld);
            UUID commitOwner=UUID.randomUUID(),commitOp=UUID.randomUUID();
            JsonObject commitBefore=player(100,100,100,50,false),commitAfter=player(84,96,100,48,true);
            JsonObject commitPayload=new JsonObject();commitPayload.add("antes",commitBefore);commitPayload.add("despues",commitAfter);
            commitPayload.add("cuentaAntes",new JsonArray());commitPayload.add("cuentaDespues",new JsonArray());
            var commitEntry=new DiarioOperaciones.Entrada(commitOp,commitOwner,"ADQUISICION",DiarioOperaciones.Estado.PREPARADA,commitPayload);
            var commitStore=new DiarioOperaciones(commitWorld.resolve("wal"));commitStore.preparar(commitEntry);
            Path commitPlayer=commitWorld.resolve("player.json");ArchivoDurable.escribir(commitPlayer,commitBefore);
            Simulado failure=new Simulado(commitPlayer,-1);
            failure.impedirCommit=commitWorld.resolve("wal").resolve(commitOwner.toString()).resolve(commitOp+".json.tmp");
            try { commitStore.completar(commitEntry,failure);throw new AssertionError("Commit IO fallido admitido"); }
            catch(IllegalStateException expectedFailure) { checks++; }
            require(commitStore.leerOperacion(commitOwner,commitOp).orElseThrow().estado()==DiarioOperaciones.Estado.PREPARADA,"Se pierde intencion al fallar COMMIT");
            Files.delete(failure.impedirCommit);load.invoke(null,commitWorld);
            Simulado retry=new Simulado(commitPlayer,-1);commitStore.completar(commitEntry,retry);
            require(retry.writes==0,"ACK previo se cobra otra vez despues de fallar COMMIT");
            require(commitStore.confirmada(commitOwner,commitOp),"Reintento COMMIT no finaliza");
            // Reserva cuenta bloqueada: operaciones ordinarias no divergen de un WAL preparado.
            UUID guardOp=UUID.randomUUID();CerrojosJugador.bloquear(commitOwner,guardOp);
            try { Registro.adoptado(commitOwner,UUID.randomUUID(),0);throw new AssertionError("Mutacion paralela bajo custodia"); }
            catch(IllegalStateException expectedFailure) { checks++; }
            CerrojosJugador.desbloquear(commitOwner,guardOp);
        } finally {
            data.set(null,oldData);path.set(null,oldPath);CerrojosJugador.limpiar();
            DiarioOperaciones.limpiarIndices();
            try (var paths=Files.walk(base)) {
                for (Path file:paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!file.toAbsolutePath().normalize().startsWith(base)) throw new IllegalStateException("Fuera del directorio temporal de pruebas");
                    Files.delete(file);
                }
            }
        }
        System.out.println("Recuperacion durable: "+checks+" comprobaciones correctas (30 escenarios de caida, sin Minecraft).");
    }
    public static void main(String[] args) throws Exception { run(); }
}
