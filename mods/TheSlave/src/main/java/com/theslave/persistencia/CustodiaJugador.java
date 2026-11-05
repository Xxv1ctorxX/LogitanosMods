package com.theslave.persistencia;

import com.google.gson.JsonArray;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.theslave.TheSlaveMod;
import com.theslave.mixin.PlayerListSaveInvoker;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;

/** Marcador y snapshot se guardan juntos en playerdata. El ACK comprueba el archivo, no el retorno void de vanilla. */
public final class CustodiaJugador {
    private static final String KEY=TheSlaveMod.id("operacion_jugador").toString();
    private static final AttachmentType<String> RECIBO=AttachmentRegistry.createPersistent(TheSlaveMod.id("operacion_jugador"),Codec.STRING);
    private CustodiaJugador() {}
    public static void registrar() { /* Inicializa el tipo antes de cargar cualquier jugador. */ }
    public static void bloquear(ServerPlayer p,UUID op) { CerrojosJugador.bloquear(p.getUUID(),op); }
    public static void desbloquear(UUID dueno,UUID op) { CerrojosJugador.desbloquear(dueno,op); }
    public static boolean ocupada(UUID dueno) { return CerrojosJugador.ocupada(dueno); }
    public static JsonArray capturar(ServerPlayer p) {
        ItemStack[] items=new ItemStack[p.getInventory().getContainerSize()];
        for (int i=0;i<items.length;i++) items[i]=p.getInventory().getItem(i).copy();
        return codificar(p,items);
    }
    public static JsonArray codificar(ServerPlayer p,ItemStack[] items) {
        var ops=RegistryOps.create(JsonOps.INSTANCE,p.registryAccess());
        JsonArray array=new JsonArray();
        for (ItemStack stack:items) array.add(ItemStack.OPTIONAL_CODEC.encodeStart(ops,stack).getOrThrow());
        return array;
    }
    public static boolean aplicado(ServerPlayer p,UUID op) {
        return op.toString().equals(((AttachmentTarget)p).getAttached(RECIBO));
    }
    public static void aplicar(ServerPlayer p,UUID op,JsonArray antes,JsonArray despues) {
        aplicar(p,op,antes,despues,false);
    }
    public static void recuperar(ServerPlayer p,UUID op,JsonArray antes,JsonArray despues) {
        aplicar(p,op,antes,despues,true);
    }
    private static void aplicar(ServerPlayer p,UUID op,JsonArray antes,JsonArray despues,boolean recuperando) {
        // El marcador impide reponer snapshots antiguos tras un ACK ya persistido.
        if (aplicado(p,op)) return;
        if (!recuperando && !capturar(p).equals(antes)) throw new IllegalStateException("Inventario cambiado fuera de la custodia durable");
        if (despues.size()!=p.getInventory().getContainerSize()) throw new IllegalArgumentException("Snapshot de inventario incompleto");
        var ops=RegistryOps.create(JsonOps.INSTANCE,p.registryAccess());
        List<ItemStack> decoded=new ArrayList<>();
        for (var element:despues) decoded.add(ItemStack.OPTIONAL_CODEC.parse(ops,element).getOrThrow());
        for (int i=0;i<decoded.size();i++) p.getInventory().setItem(i,decoded.get(i));
        p.getInventory().setChanged();
        ((AttachmentTarget)p).setAttached(RECIBO,op.toString());
    }
    public static void guardarConfirmado(ServerPlayer p,UUID op) {
        if (!aplicado(p,op)) throw new IllegalStateException("Jugador sin marcador de operacion");
        var problems=new ProblemReporter.Collector();
        var output=TagValueOutput.createWithContext(problems,p.registryAccess());
        p.saveWithoutId(output);
        if (!problems.isEmpty()) throw new IllegalStateException("Playerdata contiene errores de serializacion: "+problems.getReport());
        CompoundTag expected=output.buildResult();
        ((PlayerListSaveInvoker)p.level().getServer().getPlayerList()).theslave$guardar(p);
        Path file=p.level().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getStringUUID()+".dat");
        try {
            CompoundTag actual=NbtIo.readCompressed(file,NbtAccounter.create(16L*1024*1024));
            verificarGuardado(actual,expected,op);
            try (FileChannel channel=FileChannel.open(file,StandardOpenOption.WRITE)) { channel.force(true); }
        } catch (IOException ex) { throw new IllegalStateException("No se puede confirmar playerdata",ex); }
    }
    /** Separado para comprobar el ACK con NBT sintetico sin lanzar un servidor. */
    public static void verificarGuardado(CompoundTag actual,CompoundTag expected,UUID op) {
        EvidenciaJugador.verificar(actual,expected,op);
    }
}
