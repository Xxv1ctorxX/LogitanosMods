package com.theslave.ritual;

import com.theslave.entity.EsclavoEntity;
import com.theslave.mixin.PersistentEntityAccess;
import com.theslave.mixin.ServerLevelEntityAccess;
import com.theslave.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Barrera real de persistencia de entidades, separada del WAL de los rituales.
 * Todas las entradas y futuros completados pertenecen al hilo servidor.
 * Un ACK exige escritura terminada y synchronize(true), nunca saveAll(void).
 */
public final class EntityDurability {
    private record Clave(ServerLevel nivel,long chunk) {}
    private static final Map<Clave,CompletableFuture<CompoundTag>> ESCRITURAS=new HashMap<>();
    private static final Map<Clave,CompletableFuture<Void>> CARGAS=new HashMap<>();
    private static final Map<Clave,CompletableFuture<CompoundTag>> EN_CURSO=new HashMap<>();
    private static Consumer<EsclavoEntity> alCargar=e -> {};
    private static int sesion;
    private EntityDurability() {}

    public static void alCargar(Consumer<EsclavoEntity> filtro) { alCargar=filtro; }

    public static void registrar() {
        ServerEntityEvents.ENTITY_LOAD.register((entidad,nivel) -> {
            if(entidad instanceof EsclavoEntity e)alCargar.accept(e);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(CARGAS.isEmpty())return;
            for(var entrada:new ArrayList<>(CARGAS.entrySet())) {
                var clave=entrada.getKey();
                if(clave.nivel.areEntitiesLoaded(clave.chunk)) {
                    CARGAS.remove(clave);
                    entrada.getValue().complete(null);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            sesion++;
            var error=new IllegalStateException("El servidor cierra antes de confirmar entidades");
            new ArrayList<>(EN_CURSO.values()).forEach(f -> f.completeExceptionally(error));
            CARGAS.values().forEach(f -> f.completeExceptionally(error));
            ESCRITURAS.clear();CARGAS.clear();EN_CURSO.clear();
        });
    }

    public static CompoundTag snapshot(EsclavoEntity entidad) {
        return snapshot((Entity)entidad);
    }
    public static CompoundTag snapshot(Entity entidad) {
        var problemas=new ProblemReporter.Collector();
        var out=TagValueOutput.createWithContext(problemas,entidad.registryAccess());
        if(!entidad.save(out))throw new IllegalStateException("La entidad no permite crear snapshot ritual");
        if(!problemas.isEmpty())throw new IllegalStateException("Snapshot de entidad incompleto: "+problemas.getReport());
        return out.buildResult();
    }

    /** Crea la copia sin incorporarla al mundo: el orquestador verifica UUID y tombstones primero. */
    public static EsclavoEntity restaurar(ServerLevel nivel,CompoundTag snapshot) {
        var entidad=ModEntities.ESCLAVO.create(nivel,EntitySpawnReason.LOAD);
        if(entidad==null)throw new IllegalStateException("No se pudo restaurar el snapshot ritual");
        aplicarSnapshot(entidad,snapshot);
        return entidad;
    }
    public static void aplicarSnapshot(Entity entidad,CompoundTag snapshot) {
        var problemas=new ProblemReporter.Collector();
        entidad.load(TagValueInput.create(problemas,entidad.registryAccess(),snapshot.copy()));
        if(!problemas.isEmpty())throw new IllegalStateException("Restauracion de entidad incompleta: "+problemas.getReport());
    }

    /** FULL no implica que hayan terminado de cargarse las entidades del chunk. */
    public static CompletableFuture<Void> cargarEntidades(ServerLevel nivel,ChunkPos chunk) {
        if(nivel.areEntitiesLoaded(chunk.pack()))return CompletableFuture.completedFuture(null);
        var clave=new Clave(nivel,chunk.pack());
        var anterior=CARGAS.get(clave);
        if(anterior!=null)return anterior;
        var resultado=new CompletableFuture<Void>();CARGAS.put(clave,resultado);
        var gestor=((ServerLevelEntityAccess)nivel).theslave$entityManager();
        try { ((PersistentEntityAccess)gestor).theslave$ensureLoaded(chunk.pack()); }
        catch(RuntimeException ex) { CARGAS.remove(clave);resultado.completeExceptionally(ex); }
        return resultado;
    }

    /** Incluye secciones HIDDEN: getEntity(UUID) por si solo no prueba ausencia tras una carga. */
    public static Entity buscar(ServerLevel nivel,UUID uuid) {
        Entity visible=nivel.getEntity(uuid);
        if(visible!=null)return visible;
        var gestor=((ServerLevelEntityAccess)nivel).theslave$entityManager();
        var secciones=((PersistentEntityAccess)gestor).theslave$sections();
        for(long chunk:secciones.getAllChunksWithExistingSections()) {
            var entidad=secciones.getExistingSectionsInChunk(chunk).flatMap(s -> s.getEntities())
                .filter(e -> uuid.equals(e.getUUID())).findFirst();
            if(entidad.isPresent())return entidad.get();
        }
        return null;
    }

    /** Solicita solo este chunk; su resultado es el NBT efectivamente confirmado por el escritor vanilla. */
    public static CompletableFuture<CompoundTag> guardar(ServerLevel nivel,ChunkPos chunk) {
        if(!nivel.areEntitiesLoaded(chunk.pack()))return CompletableFuture.failedFuture(new IllegalStateException("Falta cargar las entidades antes de guardar"));
        var clave=new Clave(nivel,chunk.pack());
        CompletableFuture<CompoundTag> anterior=EN_CURSO.get(clave);
        int epocaSolicitud=sesion;
        if(anterior!=null&&!anterior.isDone())return despuesDe(anterior,()->epocaSolicitud==sesion?guardar(nivel,chunk)
            :CompletableFuture.failedFuture(new IllegalStateException("Sesion cerrada antes de repetir el guardado")));
        if(anterior!=null) {EN_CURSO.remove(clave,anterior);ESCRITURAS.remove(clave,anterior);}
        var resultado=new CompletableFuture<CompoundTag>();ESCRITURAS.put(clave,resultado);
        EN_CURSO.put(clave,resultado);
        resultado.whenComplete((v,error) -> { EN_CURSO.remove(clave,resultado);ESCRITURAS.remove(clave,resultado); });
        var gestor=((ServerLevelEntityAccess)nivel).theslave$entityManager();
        var acceso=(PersistentEntityAccess)gestor;
        boolean aceptada;
        try { aceptada=acceso.theslave$store(chunk.pack(),e -> {}); }
        catch(RuntimeException ex) { resultado.completeExceptionally(ex);return resultado; }
        if(!aceptada) {
            ESCRITURAS.remove(clave);
            resultado.completeExceptionally(new IllegalStateException("El gestor aun no acepta el guardado del chunk"));
        } else if(ESCRITURAS.remove(clave,resultado)) {
            // Vanilla omite una nueva escritura si ya sabe que el chunk esta vacio.
            // En ese caso verificamos el almacenamiento del escritor existente y lo sincronizamos.
            var storage=((EntityStorageAccess)acceso.theslave$storage()).theslave$regionStorage();
            int epoca=sesion;
            storage.synchronize(true).thenCompose(v -> storage.read(chunk))
                .thenCompose(tag -> storage.synchronize(true).thenApply(v -> tag.orElseGet(CompoundTag::new)))
                .whenComplete((tag,error) -> nivel.getServer().execute(() -> completar(epoca,resultado,tag,error)));
        }
        return resultado;
    }

    /** Hook llamado por el redirect del escritor vanilla; no sustituye ni duplica su almacenamiento. */
    public static CompletableFuture<Void> escritura(ServerLevel nivel,SimpleRegionStorage storage,ChunkPos chunk,
                                                   CompoundTag nbt,CompletableFuture<Void> escritura) {
        var pendiente=ESCRITURAS.remove(new Clave(nivel,chunk.pack()));
        if(pendiente==null)return escritura;
        var copia=nbt.copy();int epoca=sesion;
        var durable=barrera(escritura,() -> storage.synchronize(true));
        durable.whenComplete((v,error) -> nivel.getServer().execute(() -> completar(epoca,pendiente,copia,error)));
        return durable;
    }

    /** Separada para probar fallos y orden de ACK sin crear servidor ni mundo. */
    public static CompletableFuture<Void> barrera(CompletableFuture<Void> escritura,Supplier<CompletableFuture<Void>> sincronizar) {
        return escritura.thenCompose(v -> sincronizar.get());
    }
    /** Una peticion posterior necesita su propio NBT: no hereda el ACK capturado para otro estado del chunk. */
    public static <T> CompletableFuture<T> despuesDe(CompletableFuture<?> anterior,Supplier<CompletableFuture<T>> nuevaEscritura) {
        return anterior.handle((v,error)->null).thenCompose(v->nuevaEscritura.get());
    }

    private static void completar(int epoca,CompletableFuture<CompoundTag> resultado,CompoundTag nbt,Throwable error) {
        if(epoca!=sesion) { resultado.completeExceptionally(new IllegalStateException("Cambio de sesion durante el guardado"));return; }
        if(error==null)resultado.complete(nbt);else resultado.completeExceptionally(error);
    }
}
