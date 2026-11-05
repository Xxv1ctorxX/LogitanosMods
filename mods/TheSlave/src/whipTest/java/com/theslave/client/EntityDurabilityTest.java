package com.theslave.client;

import com.theslave.ritual.EntityDurability;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/** Futures simulados del escritor: ninguna instancia de juego, servidor o mundo. */
public final class EntityDurabilityTest {
    public static void run() {
        try {
            var manager=net.minecraft.world.level.entity.PersistentEntitySectionManager.class;
            manager.getDeclaredMethod("ensureChunkQueuedForLoad",long.class);
            manager.getDeclaredMethod("storeChunkSections",long.class,java.util.function.Consumer.class);
            manager.getDeclaredField("sectionStorage");manager.getDeclaredField("permanentStorage");
            net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");
            var writer=net.minecraft.world.level.chunk.storage.SimpleRegionStorage.class;
            writer.getDeclaredMethod("write",net.minecraft.world.level.ChunkPos.class,net.minecraft.nbt.CompoundTag.class);
            writer.getDeclaredMethod("write",net.minecraft.world.level.ChunkPos.class,java.util.function.Supplier.class);
            writer.getDeclaredMethod("synchronize",boolean.class);
        } catch(ReflectiveOperationException ex) { throw new AssertionError("API real incompatible con los hooks",ex); }
        var write=new CompletableFuture<Void>();var sync=new CompletableFuture<Void>();
        int[] llamadas={0};
        var ack=EntityDurability.barrera(write,() -> { llamadas[0]++;return sync; });
        if(ack.isDone() || llamadas[0]!=0)throw new AssertionError("Fsync anticipado a la escritura");
        write.complete(null);
        if(ack.isDone() || llamadas[0]!=1)throw new AssertionError("ACK anterior al fsync");
        sync.complete(null);
        if(!ack.isDone() || ack.isCompletedExceptionally())throw new AssertionError("No se confirma el fsync terminado");
        var writeError=EntityDurability.barrera(CompletableFuture.failedFuture(new IOException("fallo write")),() -> {
            throw new AssertionError("No debe ejecutar fsync si fallo write");
        });
        if(!writeError.isCompletedExceptionally())throw new AssertionError("Se oculta fallo de escritura");
        var syncError=EntityDurability.barrera(CompletableFuture.completedFuture(null),() -> CompletableFuture.failedFuture(new IOException("fallo fsync")));
        if(!syncError.isCompletedExceptionally())throw new AssertionError("Se confirma persistencia tras fallo fsync");
        var anterior=new CompletableFuture<String>();var nueva=new CompletableFuture<String>();int[] nuevas={0};
        var posterior=EntityDurability.despuesDe(anterior,()->{nuevas[0]++;return nueva;});
        if(posterior.isDone()||nuevas[0]!=0)throw new AssertionError("Se solapan escrituras del mismo chunk");
        anterior.complete("NBT anterior sin el resultado nuevo");
        if(posterior.isDone()||nuevas[0]!=1)throw new AssertionError("Se reutiliza un ACK de otro estado del chunk");
        nueva.complete("NBT nuevo con el resultado");
        if(!posterior.join().equals("NBT nuevo con el resultado"))throw new AssertionError("ACK no corresponde a la nueva escritura");
        var reintento=EntityDurability.despuesDe(CompletableFuture.failedFuture(new IOException("fallo anterior")),()->CompletableFuture.completedFuture("recuperada"));
        if(!reintento.join().equals("recuperada"))throw new AssertionError("Un error anterior bloquea un guardado nuevo valido");
        System.out.println("EntityDurability: orden write/fsync/ACK y propagacion de fallos correctos.");
    }
    public static void main(String[] args) { run(); }
}
