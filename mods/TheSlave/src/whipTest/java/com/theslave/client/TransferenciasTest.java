package com.theslave.client;

import com.google.gson.*;
import com.theslave.persistencia.*;
import com.theslave.persistencia.TransferenciasDurables.*;
import com.theslave.ritual.RitualDurable;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.*;

/** Motor real de entrega: pruebas de fronteras, ACK, carga historica y retirada de recibos. */
public final class TransferenciasTest {
    private static int checks;
    private static void require(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static Operacion operation() {
        Operacion op=new Operacion();op.id=UUID.randomUUID();op.dueno=UUID.randomUUID();op.actor=UUID.randomUUID();op.vinculo=UUID.randomUUID();op.dimension="minecraft:overworld";
        op.antesJugador=new JsonArray();op.despuesJugador=new JsonArray();op.cuenta=new JsonArray();
        for(int i=0;i<43;i++){op.antesJugador.add(new JsonObject());op.despuesJugador.add(new JsonObject());}
        CompoundTag body=new CompoundTag();body.store("UUID",UUIDUtil.CODEC,op.actor);body.putString("id","theslave:esclavo");body.putBoolean("TieneDueno",true);
        body.putString("Dueno",op.dueno.toString());body.putString("Vinculo",op.vinculo.toString());
        body.putString("TheSlaveRitualOperacion",op.id.toString());body.putBoolean("TheSlaveRitualCustodia",true);body.putBoolean("TheSlaveRitualOriginal",false);
        body.putInt("EnergiaFundicion",1400);
        ListTag pos=new ListTag();pos.add(DoubleTag.valueOf(1.5));pos.add(DoubleTag.valueOf(64));pos.add(DoubleTag.valueOf(1.5));body.put("Pos",pos);
        CompoundTag item=new CompoundTag();item.putString("id","minecraft:diamond");item.putInt("count",10);
        ListTag bag=new ListTag();bag.add(item);body.put("Mochila",bag);body.put("Ref",item.copy());
        var equipment=new CompoundTag();equipment.put("mainhand",item.copy());body.put("equipment",equipment);
        op.cuerpo=RitualDurable.codificar(body);return op;
    }
    private static CompoundTag chunk(Operacion op){var result=new CompoundTag();var list=new ListTag();list.add(RitualDurable.decodificar(op.cuerpo));result.put("Entities",list);return result;}
    private static final class EstadoSimulado {boolean playerSaved,actorSaved,commit;int player=10;}
    private static final class Puertos implements Participantes {
        final Operacion op;final EstadoSimulado disk;final int crash;int player,actor,writes;boolean online=true;
        CompletableFuture<Void> carga;
        Puertos(Operacion op,EstadoSimulado disk,int crash){this.op=op;this.disk=disk;this.crash=crash;player=disk.player;actor=disk.actorSaved?20:10;}
        private void frontera(int n){if(crash==n)throw new IllegalStateException("Caida simulada "+n);}
        public void comprobar(){if(!online)throw new IllegalStateException("Sesion reemplazada");}
        public CompletableFuture<Void> cuerpo(){if(carga!=null)return carga;actor=20;frontera(1);return CompletableFuture.completedFuture(null);}
        public void jugador(){if(!disk.playerSaved){player=20;writes++;}frontera(2);}
        public void guardarJugador(){disk.player=player;disk.playerSaved=true;frontera(3);}
        public CompletableFuture<CompoundTag> guardarCuerpo(){disk.actorSaved=true;frontera(4);return CompletableFuture.completedFuture(chunk(op));}
        public void commit(){disk.commit=true;frontera(5);}
        public void liberar(){frontera(6);}
    }
    public static void run() throws Exception {
        for(int phase=0;phase<=6;phase++) {
            Operacion op=operation();TransferenciasDurables.validar(op);checks++;
            var disk=new EstadoSimulado();var first=new Puertos(op,disk,phase);
            if(phase>0)try{TransferenciasDurables.confirmar(op,first).join();throw new AssertionError("Frontera no interrumpida");}catch(CompletionException expected){checks++;}
            Operacion recovered=operation();recovered.id=op.id;recovered.actor=op.actor;recovered.dueno=op.dueno;recovered.vinculo=op.vinculo;recovered.cuerpo=op.cuerpo;recovered.confirmada=disk.commit;
            var retry=new Puertos(recovered,disk,-1);TransferenciasDurables.confirmar(recovered,retry).join();
            require(disk.commit&&disk.actorSaved&&disk.player==20,"Caida pierde entrega/ACK");
            require(retry.writes<=1,"Reintento duplica entrega de inventario");
            int writes=retry.writes;disk.player=27;
            TransferenciasDurables.confirmar(recovered,retry).join();
            require(retry.writes==writes&&disk.player==27,"COMMIT restaura inventario posterior legitimo");
        }
        var stale=operation();var disk=new EstadoSimulado();var ports=new Puertos(stale,disk,-1);ports.carga=new CompletableFuture<>();
        var future=TransferenciasDurables.confirmar(stale,ports);ports.online=false;ports.carga.complete(null);
        try{future.join();throw new AssertionError("Sesion antigua puede entregar");}catch(CompletionException expected){checks++;}
        require(!disk.playerSaved&&!disk.commit,"Callback de conexion vieja cobra inventario");
        Operacion valid=operation();TransferenciasDurables.verificarCuerpo(chunk(valid),valid);checks++;
        for(String key:List.of("Mochila","Ref","equipment","Dueno","Vinculo","TheSlaveRitualCustodia","EnergiaFundicion")) {
            var bad=chunk(valid);bad.getListOrEmpty("Entities").getCompoundOrEmpty(0).remove(key);
            try{TransferenciasDurables.verificarCuerpo(bad,valid);throw new AssertionError("ACK incompleto aceptado: "+key);}catch(IllegalStateException expected){checks++;}
        }
        require(TransferenciasDurables.decidirCarga(true,true,false,false)==DecisionCarga.PENDIENTE,"Ritual anterior oculta una entrega pendiente");
        require(TransferenciasDurables.decidirCarga(false,true,false,true)==DecisionCarga.OMITIR,"COMMIT antiguo pisa custodia de ritual nuevo");
        require(TransferenciasDurables.decidirCarga(false,false,true,false)==DecisionCarga.OMITIR,"Carga normal borra minerales posteriores");
        require(TransferenciasDurables.decidirCarga(false,false,false,false)==DecisionCarga.RESTAURAR,"Copia de chunk antigua vuelve a duplicar carga entregada");
        require(TransferenciasDurables.decidirCarga(false,false,true,true)==DecisionCarga.LIBERAR,"Crash tras COMMIT deja cuerpo congelado");
        prune();inventory();
        System.out.println("Transferencias durables: "+checks+" comprobaciones correctas, sin mundo ni servidor.");
    }
    private static void prune() throws Exception {
        var folder=TransferenciasDurables.class.getDeclaredField("carpeta");folder.setAccessible(true);Object previous=folder.get(null);
        var remove=TransferenciasDurables.class.getDeclaredMethod("retirarAnterior",Operacion.class);remove.setAccessible(true);
        Path base=Files.createTempDirectory("theslave-transfer-prune").toAbsolutePath().normalize();
        try {
            folder.set(null,base);var old=operation();old.confirmada=true;var next=operation();next.actor=old.actor;next.dueno=old.dueno;next.anterior=old.id;
            next.vinculo=old.vinculo;var nextBody=RitualDurable.decodificar(old.cuerpo);nextBody.putString("TheSlaveRitualOperacion",next.id.toString());next.cuerpo=RitualDurable.codificar(nextBody);
            var gson=new Gson();Path oldFile=base.resolve(old.id+".json");ArchivoDurable.escribir(oldFile,gson.toJsonTree(old));
            Path pointer=base.resolve("actores").resolve(old.actor+".json");var index=new JsonObject();index.addProperty("operacion",next.id.toString());ArchivoDurable.escribir(pointer,index);
            require(!TransferenciasDurables.puedeRetirarAnterior(old,next,next.id),"PREP autoriza borrar evidencia anterior");
            remove.invoke(null,next);require(Files.exists(oldFile),"Se borra COMMIT antes del COMMIT siguiente");
            next.confirmada=true;
            require(!TransferenciasDurables.puedeRetirarAnterior(old,next,old.id),"Poda sin indice nuevo durable");
            remove.invoke(null,next);require(Files.exists(oldFile),"Una bandera solo en memoria permite podar");
            ArchivoDurable.escribir(base.resolve(next.id+".json"),gson.toJsonTree(next));
            index.addProperty("operacion",old.id.toString());ArchivoDurable.escribir(pointer,index);
            remove.invoke(null,next);require(Files.exists(oldFile),"Se poda sin haber confirmado el indice nuevo");
            index.addProperty("operacion",next.id.toString());ArchivoDurable.escribir(pointer,index);
            remove.invoke(null,next);require(!Files.exists(oldFile),"Ultimo COMMIT no retira cuerpo historico");
            remove.invoke(null,next);require(!Files.exists(oldFile),"Reintento de limpieza recrea recibo");
        }finally {
            folder.set(null,previous);try(var paths=Files.walk(base)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList()){
                if(!p.toAbsolutePath().normalize().startsWith(base))throw new IllegalStateException("Fuera del temporal");Files.delete(p);}}
        }
    }
    private static void inventory() {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(Item item:List.of(Items.STONE,Items.DIAMOND))item.builtInRegistryHolder().bindComponents(DataComponentMap.builder().set(DataComponents.MAX_STACK_SIZE,64).build());
        ItemStack[] inv=new ItemStack[43];Arrays.fill(inv,ItemStack.EMPTY);for(int i=0;i<36;i++)inv[i]=new ItemStack(Items.STONE,64);
        inv[0]=new ItemStack(Items.DIAMOND,63);inv[40]=new ItemStack(Items.DIAMOND,63);var incoming=new ItemStack(Items.DIAMOND,4);
        var leftover=TransferenciasDurables.insertarJugador(inv,incoming);
        require(incoming.getCount()==4&&leftover.getCount()==2,"Insercion muta origen o pierde excedente");
        require(inv[0].getCount()==64&&inv[40].getCount()==64,"No fusiona mochila/mano secundaria");
        for(int i=36;i<40;i++)require(inv[i].isEmpty(),"Entrega ocupa armadura como mochila");
    }
    public static void main(String[] args)throws Exception{run();}
}
