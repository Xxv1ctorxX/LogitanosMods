package com.theslave.persistencia;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

/** Un PREP completo y recibos COMMIT compactos. El historial nunca se carga para tramitar una accion. */
public final class DiarioOperaciones {
    public enum Estado { PREPARADA, CONFIRMADA }
    public record Entrada(UUID id, UUID dueno, String tipo, Estado estado, JsonObject datos, String huella) {
        public Entrada(UUID id,UUID dueno,String tipo,Estado estado,JsonObject datos) {
            this(id,dueno,tipo,estado,datos,null);
        }
        public Entrada {
            Objects.requireNonNull(id);Objects.requireNonNull(dueno);Objects.requireNonNull(estado);Objects.requireNonNull(datos);
            if(tipo==null || tipo.isBlank())throw new IllegalArgumentException("Tipo ausente");
            String calculada=hash(datos);
            if(huella==null)huella=calculada; // Compatibilidad con recibos completos anteriores.
            if(!huella.matches("[0-9a-f]{64}") || ((estado==Estado.PREPARADA || !datos.isEmpty()) && !huella.equals(calculada)))
                throw new IllegalArgumentException("Contenido WAL no coincide con su recibo");
            datos=estado==Estado.CONFIRMADA?new JsonObject():datos.deepCopy();
        }
        public Entrada confirmada() {return new Entrada(id,dueno,tipo,Estado.CONFIRMADA,new JsonObject(),huella);}
    }
    public interface Participantes {
        void aplicarRegistro(Entrada entrada);
        void aplicarJugador(Entrada entrada);
        void confirmarJugador(Entrada entrada);
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final class Indice {UUID pendiente;Indice(UUID pendiente){this.pendiente=pendiente;}}
    /** Solo directorios consultados por conexiones activas; se eliminan al desconectarse. */
    private static final Map<Path,Indice> INDICES=new HashMap<>();
    private static final Map<Path,UUID> PENDIENTES=new HashMap<>();
    private static final Set<Path> RAICES_VERIFICADAS=new HashSet<>();
    private final Path raiz;
    public DiarioOperaciones(Path raiz) {this.raiz=raiz.toAbsolutePath().normalize();}
    private Path directorio(UUID dueno) {return raiz.resolve(dueno.toString());}
    private Path fichero(UUID dueno,UUID op) {return directorio(dueno).resolve(op+".json");}
    private static String hash(JsonObject datos) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    private void escribir(Entrada e) {
        JsonObject json=new JsonObject();json.addProperty("esquema",1);json.add("operacion",GSON.toJsonTree(e));
        ArchivoDurable.escribir(fichero(e.dueno(),e.id()),json);
        if(e.estado()==Estado.PREPARADA)PENDIENTES.put(directorio(e.dueno()),e.id());
        else PENDIENTES.remove(directorio(e.dueno()));
    }
    private Entrada leerArchivo(UUID dueno,Path file) {
        var root=ArchivoDurable.leer(file).getAsJsonObject();
        if(!root.has("esquema") || !root.get("esquema").toString().equals("1"))throw new IllegalStateException("WAL desconocido");
        Entrada e=GSON.fromJson(root.get("operacion"),Entrada.class);
        if(e==null || !dueno.equals(e.dueno()) || !file.getFileName().toString().equals(e.id()+".json"))throw new IllegalStateException("Identidad WAL invalida");
        return e;
    }
    public Optional<Entrada> leerOperacion(UUID dueno,UUID op) {
        Path file=fichero(dueno,op);
        return Files.exists(file)?Optional.of(leerArchivo(dueno,file)):Optional.empty();
    }
    private void recorrer(UUID dueno,Consumer<Entrada> consumer) {
        Path directory=directorio(dueno);if(!Files.exists(directory))return;
        try(var files=Files.newDirectoryStream(directory,"*.json")) {
            for(Path file:files)consumer.accept(leerArchivo(dueno,file));
        }catch(IOException ex){throw new IllegalStateException("No se puede leer WAL del jugador",ex);}
    }
    private Indice escanear(UUID dueno,boolean compactar) {
        Indice result=new Indice(null);
        recorrer(dueno,e->{
            if(e.estado()==Estado.PREPARADA) {
                if(result.pendiente!=null)throw new IllegalStateException("Varias operaciones pendientes del mismo jugador");
                result.pendiente=e.id();
            }else if(compactar) {
                // Actualiza una vez los COMMIT antiguos que aun contienen snapshots completos.
                Path file=fichero(dueno,e.id());
                var raw=ArchivoDurable.leer(file).getAsJsonObject().getAsJsonObject("operacion");
                if(!raw.has("huella") || !raw.getAsJsonObject("datos").isEmpty())escribir(e);
            }
        });return result;
    }
    private Indice indice(UUID dueno) {
        return INDICES.computeIfAbsent(directorio(dueno),ignored->RAICES_VERIFICADAS.contains(raiz)
                ?new Indice(PENDIENTES.get(directorio(dueno))):escanear(dueno,false));
    }
    public List<Entrada> pendientes(UUID dueno) {
        UUID op=indice(dueno).pendiente;
        if(op==null)return List.of();
        Entrada e=leerOperacion(dueno,op).orElseThrow(()->new IllegalStateException("WAL pendiente desaparecido"));
        if(e.estado()!=Estado.PREPARADA)throw new IllegalStateException("Indice de custodia incoherente");
        return List.of(e);
    }
    public boolean confirmada(UUID dueno,UUID op) {return leerOperacion(dueno,op).map(e->e.estado()==Estado.CONFIRMADA).orElse(false);}
    private static boolean misma(Entrada a,Entrada b) {
        return a.id().equals(b.id())&&a.dueno().equals(b.dueno())&&a.tipo().equals(b.tipo())&&a.huella().equals(b.huella());
    }
    public void preparar(Entrada entrada) {
        if(entrada.estado()!=Estado.PREPARADA)throw new IllegalArgumentException("Una operacion nueva debe prepararse");
        Optional<Entrada> previa=leerOperacion(entrada.dueno(),entrada.id());
        if(previa.isPresent()) {
            if(!misma(previa.get(),entrada))throw new IllegalStateException("ID WAL reutilizado");
            return;
        }
        Indice indice=indice(entrada.dueno());
        if(indice.pendiente!=null)throw new IllegalStateException("Jugador con operacion pendiente");
        escribir(entrada);indice.pendiente=entrada.id();
    }
    public void restaurarCerrojos() {
        RAICES_VERIFICADAS.remove(raiz);
        PENDIENTES.keySet().removeIf(p->Objects.equals(p.getParent(),raiz));
        if(!Files.exists(raiz)){RAICES_VERIFICADAS.add(raiz);return;}
        try(var folders=Files.newDirectoryStream(raiz)) {
            for(Path folder:folders)if(Files.isDirectory(folder)) {
                UUID dueno=UUID.fromString(folder.getFileName().toString());
                Indice indice=escanear(dueno,true);INDICES.remove(directorio(dueno));
                if(indice.pendiente!=null) {
                    PENDIENTES.put(directorio(dueno),indice.pendiente);CerrojosJugador.bloquear(dueno,indice.pendiente);
                }
            }
            RAICES_VERIFICADAS.add(raiz);
        }catch(IOException ex){throw new IllegalStateException("No se puede restaurar custodia de compras",ex);}
    }
    public void completar(Entrada entrada,Participantes participantes) {
        Entrada actual=leerOperacion(entrada.dueno(),entrada.id()).orElseThrow(()->new IllegalStateException("Operacion no preparada"));
        if(!misma(actual,entrada))throw new IllegalStateException("Datos WAL cambiados");
        if(actual.estado()==Estado.CONFIRMADA)return;
        participantes.aplicarRegistro(actual);participantes.aplicarJugador(actual);participantes.confirmarJugador(actual);
        escribir(actual.confirmada());indice(actual.dueno()).pendiente=null;
    }
    public void olvidar(UUID dueno){INDICES.remove(directorio(dueno));}
    public static void limpiarIndices(){INDICES.clear();PENDIENTES.clear();RAICES_VERIFICADAS.clear();}
}
