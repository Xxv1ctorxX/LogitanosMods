package com.theslave.client;

import com.theslave.Registro;
import com.theslave.registry.ModComponents.Almas;
import com.theslave.registry.ModComponents.Pacto;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

/** Regresiones sobre datos persistidos y recetas de almas, sin sustituir pruebas de caida del servidor. */
public final class LogicAuditTest {
    private static int checks;
    private static Method read;
    private static void require(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static Object parse(String text) throws Exception { return read.invoke(null, new StringReader(text)); }
    private static void reject(String text) throws Exception {
        try { parse(text); throw new AssertionError("Registro corrupto admitido: " + text); }
        catch (InvocationTargetException e) { require(e.getCause() instanceof RuntimeException, "Error de validacion inesperado"); }
    }
    private static void rollback(java.lang.reflect.Field data, java.lang.reflect.Field path,
            java.nio.file.Path target, String initial, Runnable action) throws Exception {
        data.set(null, parse(initial)); path.set(null, target);
        var gson = new com.google.gson.Gson();
        var before = gson.toJsonTree(data.get(null));
        java.nio.file.Files.writeString(target, initial);
        try { action.run(); throw new AssertionError("Mutacion sin persistir admitida"); }
        catch (IllegalStateException expected) { checks++; }
        require(before.equals(gson.toJsonTree(data.get(null))), "Fallo de escritura deja mutacion en memoria");
        require(initial.equals(java.nio.file.Files.readString(target)), "Fallo de escritura altera registro anterior");
    }

    /** Inyecta fallos reales del filesystem en las mutaciones publicas, sin arrancar Minecraft. */
    private static void persistence(java.lang.reflect.Field data, java.lang.reflect.Field path,
            UUID player) throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("theslave-persistence-test");
        var ledger = dir.resolve("ledger.json");
        var temporary = dir.resolve("ledger.json.tmp");
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), contract = UUID.randomUUID();
        String empty = "{\"esquema\":2,\"cuentas\":{}}";
        String living = "{\"esquema\":2,\"cuentas\":{\""+player+"\":[{\"id\":\""+a
                +"\",\"estado\":\"VIVO\",\"variante\":0},{\"id\":\""+b+"\",\"estado\":\"VIVO\",\"variante\":1}]}}";
        String reserved = "{\"esquema\":2,\"cuentas\":{\""+player+"\":[{\"id\":\""+a
                +"\",\"estado\":\"RESERVADO\",\"contrato\":\""+contract+"\"}]}}";
        try {
            // El temporal ocupado fuerza fallo ANTES de sustituir el registro valido.
            java.nio.file.Files.createDirectory(temporary);
            rollback(data,path,ledger,empty,()->Registro.reservar(player,contract,0));
            rollback(data,path,ledger,empty,()->Registro.adoptado(player,UUID.randomUUID(),0));
            rollback(data,path,ledger,living,()->Registro.nombrado(player,a,"Nuevo nombre"));
            rollback(data,path,ledger,living,()->Registro.muerto(player,a,"Difunto",0));
            rollback(data,path,ledger,living.replace("VIVO","DIFUNTO"),()->Registro.resucitado(player,a,UUID.randomUUID()));
            rollback(data,path,ledger,reserved,()->Registro.cancelarReserva(player,contract));
            rollback(data,path,ledger,living,()->Registro.meritos(player,a,net.minecraft.util.RandomSource.create(1)));
            rollback(data,path,ledger,living,()->Registro.fusionar(player,a,b,new float[5],"Fusion",UUID.randomUUID()));
            rollback(data,path,ledger,living,()->Registro.liberar(player,a));
            java.nio.file.Files.delete(temporary);
            data.set(null,parse(empty)); path.set(null,ledger);
            Registro.reservar(player,contract,0);
            require(parse(java.nio.file.Files.readString(ledger))!=null,"Guardado atomico no produce registro valido");
            require(Registro.reservaPendiente(player)!=null,"Reserva persistida perdida");
            require(!java.nio.file.Files.exists(temporary),"Temporal queda despues de confirmar");
            // Fallo DESPUES de escribir y sincronizar el temporal: destino no reemplazable.
            java.nio.file.Files.delete(ledger); java.nio.file.Files.createDirectory(ledger);
            var marker=ledger.resolve("preservar.txt"); java.nio.file.Files.writeString(marker,"intacto");
            try {
                try { Registro.cancelarReserva(player,contract); throw new AssertionError("Move fallido silenciado"); }
                catch (IllegalStateException expected) { checks++; }
                require(Registro.reservaPendiente(player)!=null,"Move fallido cancela reserva en memoria");
                require(java.nio.file.Files.readString(marker).equals("intacto"),"Move fallido destruye destino");
                require(java.nio.file.Files.exists(temporary),"Se perdio temporal diagnostico");
            } finally { java.nio.file.Files.delete(marker); }
        } finally {
            java.nio.file.Files.deleteIfExists(temporary);
            java.nio.file.Files.deleteIfExists(ledger);
            var load = Registro.class.getDeclaredMethod("cargarDesde", java.nio.file.Path.class);
            load.setAccessible(true);
            var corrupt = dir.resolve("theslave_vinculos.json");
            String corruptText = "{registro incompleto";
            java.nio.file.Files.writeString(corrupt,corruptText);
            try {
                try { load.invoke(null,dir); throw new AssertionError("Arranque corrupto admitido"); }
                catch (InvocationTargetException expected) { require(expected.getCause() instanceof IllegalStateException,"Fallo de carga inesperado"); }
                require(path.get(null)==null,"La carga fallida deja habilitado el guardado");
                Registro.guardar(); // Equivale al callback SERVER_STOPPING tras fallar el arranque.
                require(java.nio.file.Files.readString(corrupt).equals(corruptText),"El cierre sobrescribe el registro corrupto");
                require(!java.nio.file.Files.exists(dir.resolve("theslave_vinculos.json.tmp")),"El cierre crea un registro vacio");
            } finally { java.nio.file.Files.delete(corrupt); }
            java.nio.file.Files.delete(dir);
        }
    }
    private static void legacyGeneration(UUID owner) throws Exception {
        var load=Registro.class.getDeclaredMethod("cargarDesde",java.nio.file.Path.class);load.setAccessible(true);
        var dir=java.nio.file.Files.createTempDirectory("theslave-legacy-generation");
        var target=dir.resolve("theslave_vinculos.json");
        var old=dir.resolve("theslave_registro.json");
        var backup=dir.resolve("theslave_registro.json.pre-v2");
        UUID id=UUID.randomUUID();
        try {
            String v2="{\"esquema\":2,\"cuentas\":{\""+owner+"\":[{\"id\":\""+id+"\",\"estado\":\"DIFUNTO\",\"muertes\":0}]}}";
            java.nio.file.Files.writeString(target,v2);load.invoke(null,dir);
            require(Registro.vinculo(owner,id).muertes==1,"Difunto legado no obtiene generacion de alma valida");
            require(JsonParser.parseString(java.nio.file.Files.readString(target)).getAsJsonObject().getAsJsonObject("cuentas").getAsJsonArray(owner.toString()).get(0).getAsJsonObject().get("muertes").getAsInt()==1,"Normalizacion no se persiste");
            load.invoke(null,dir);require(Registro.vinculo(owner,id).muertes==1,"Recarga vuelve a incrementar muerte legacy");
            java.nio.file.Files.writeString(target,v2.replace("DIFUNTO","VIVO"));load.invoke(null,dir);
            require(Registro.vinculo(owner,id).muertes==0,"Normalizacion inventa muertes a vivos");
            java.nio.file.Files.delete(target);
            String ancient="{\""+owner+"\":{\"estado\":\"muerto\",\"variante\":1,\"muertes\":0}}";
            java.nio.file.Files.writeString(old,ancient);load.invoke(null,dir);
            require(Registro.vinculos(owner).getFirst().muertes==1,"Migracion vieja crea alma gen0");
            require(java.nio.file.Files.readString(backup).equals(ancient),"Migracion altera copia anterior");
        } finally {
            java.nio.file.Files.deleteIfExists(target);java.nio.file.Files.deleteIfExists(old);java.nio.file.Files.deleteIfExists(backup);
            java.nio.file.Files.delete(dir);
        }
    }
    public static void main(String[] args) throws Exception {
        read = Registro.class.getDeclaredMethod("leerArchivo", java.io.Reader.class); read.setAccessible(true);
        String owner = "11111111-1111-1111-1111-111111111111";
        String id = "22222222-2222-2222-2222-222222222222";
        String contract = "33333333-3333-3333-3333-333333333333";
        String link = "{\"id\":\"" + id + "\",\"estado\":\"DIFUNTO\",\"variante\":1}";
        String valid = "{\"esquema\":2,\"cuentas\":{\"" + owner + "\":[" + link + "]}}";
        require(parse(valid) != null, "Un difunto valido debe conservarse");
        require(parse("{\"esquema\":2,\"cuentas\":{}}") != null, "Mundo nuevo valido");
        reject("null"); reject("{}"); reject("{\"esquema\":3,\"cuentas\":{}}");
        reject("{\"esquema\":2,\"cuentas\":null}");
        reject(valid.replace(link, link + "," + link));
        reject(valid.replace("DIFUNTO", "ESTADO_DESCONOCIDO"));
        reject(valid.replace(link,"{\"estado\":\"VIVO\"}"));
        reject(valid.replace("\"variante\":1", "\"variante\":99"));
        reject(valid.replace("\"variante\":1", "\"variante\":1,\"merito\":[0,0]"));
        String reservation = "{\"id\":\""+id+"\",\"estado\":\"RESERVADO\",\"contrato\":\""+contract+"\"}";
        require(parse(valid.replace(link,reservation)) != null,"Reserva valida rechazada");
        reject(valid.replace(link,reservation.replace(",\"contrato\":\""+contract+"\"","")));
        reject(valid.replace(link,reservation+","+reservation.replace(id,"44444444-4444-4444-4444-444444444444")));
        for (int n : new int[]{2,8,14}) {
            Almas a = Almas.cada(n);
            require(a.total()==3*n,"Total incorrecto");
            require(Almas.CODEC.parse(JsonOps.INSTANCE,Almas.CODEC.encodeStart(JsonOps.INSTANCE,a).getOrThrow()).getOrThrow().equals(a),"Codec no conserva almas");
            require(a.menos(new Almas(0,1,0)).vigilante()==n-1,"Consumo de vigilante incorrecto");
        }
        require(Almas.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"hambrienta\":40,\"vigilante\":40}")).isError(),"Recipiente sobrecapacidad admitido");
        require(Almas.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"hambrienta\":-1}")).isError(),"Almas negativas admitidas");
        try { new Pacto(UUID.randomUUID(), UUID.randomUUID(), 3); throw new AssertionError("Grado invalido admitido"); }
        catch (IllegalArgumentException expected) { checks++; }
        // Registro real en memoria aislada: reservas y vivos cuentan aunque no haya entidades cargadas.
        var dataField = Registro.class.getDeclaredField("datos"); dataField.setAccessible(true);
        var pathField = Registro.class.getDeclaredField("archivo"); pathField.setAccessible(true);
        Object oldData = dataField.get(null), oldPath = pathField.get(null);
        UUID player = UUID.fromString(owner);
        try {
            dataField.set(null, parse("{\"esquema\":2,\"cuentas\":{}}")); pathField.set(null, null);
            for (int i = 0; i < 3; i++) {
                UUID contractId = UUID.randomUUID();
                Registro.reservar(player, contractId, i);
                require(Registro.ocupadas(player) == i + 1, "Reserva no ocupa plaza");
                try { Registro.reservar(player, UUID.randomUUID(), i + 1); throw new AssertionError("Doble reserva admitida"); }
                catch (IllegalStateException expected) { checks++; }
                UUID entityId = UUID.randomUUID();
                UUID linkId = Registro.adoptado(player, entityId, 0);
                require(Registro.vinculo(player,linkId).estado == Registro.Estado.VIVO, "Reserva no activada");
                if (i == 0) {
                    Registro.muerto(player, linkId, "Prueba", 0);
                    require(Registro.ocupadas(player)==1,"La tumba libera plaza indebidamente");
                }
            }
            require(Registro.libres(player)==0,"El maximo no es tres");
            try { Registro.reservar(player,UUID.randomUUID(),2); throw new AssertionError("Cuarta plaza admitida"); }
            catch (IllegalStateException expected) { checks++; }
            try { Registro.adoptado(player,UUID.randomUUID(),0); throw new AssertionError("Adopcion salta limite"); }
            catch (IllegalStateException expected) { checks++; }
            var temp = java.nio.file.Files.createTempDirectory("theslave-ledger-test");
            try {
                pathField.set(null,temp.resolve("missing-directory").resolve("vinculos.json"));
                try { Registro.guardar(); throw new AssertionError("Fallo de guardado silenciado"); }
                catch (IllegalStateException expected) { checks++; }
            } finally { java.nio.file.Files.delete(temp); }
            persistence(dataField,pathField,player);
            legacyGeneration(player);
        } finally { dataField.set(null, oldData); pathField.set(null, oldPath); }
        System.out.println("Auditoria logica: " + checks + " comprobaciones correctas.");
        PersistenceRecoveryTest.run();
        MixinContractTest.run();
        MantenimientoTest.run();
        SeguridadRedTest.run();
        EconomiaReplayTest.run();
        TransferenciasTest.run();
    }
}
