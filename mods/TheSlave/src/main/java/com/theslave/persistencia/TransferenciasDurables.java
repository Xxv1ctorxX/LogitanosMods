package com.theslave.persistencia;

import com.google.gson.*;
import com.theslave.Registro;
import com.theslave.TheSlaveMod;
import com.theslave.entity.*;
import com.theslave.ritual.EntityDurability;
import com.theslave.ritual.RitualDurable;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

/** Custodia de entregas entre un jugador y el mismo cuerpo: nunca prepara mutando objetos del mundo. */
public final class TransferenciasDurables {
    public static final class Copias {
        public final ItemStack[] jugador;
        public final Inv mochila;
        public ItemStack referencia;
        public final EnumMap<EquipmentSlot,ItemStack> equipo=new EnumMap<>(EquipmentSlot.class);
        public final CompoundTag cuerpo;
        private final CompoundTag antesEntidad;
        private final JsonArray antesJugador,cuenta;
        private final UUID dueno,actor;
        private Copias(ServerPlayer p,EsclavoEntity e) {
            if(e.vinculo()==null)throw new IllegalStateException("El cuerpo no tiene un vinculo recuperable");
            dueno=p.getUUID();actor=e.getUUID();antesEntidad=EntityDurability.snapshot(e);cuerpo=antesEntidad.copy();
            antesJugador=CustodiaJugador.capturar(p);cuenta=Registro.cuentaOperacion(dueno);
            jugador=new ItemStack[p.getInventory().getContainerSize()];
            for(int i=0;i<jugador.length;i++)jugador[i]=p.getInventory().getItem(i).copy();
            mochila=e.inv().copiar();referencia=e.ref().copy();
            for(var slot:EquipmentSlot.values())equipo.put(slot,e.getItemBySlot(slot).copy());
        }
    }
    public static final class Operacion {
        public int esquema=1;public UUID id,dueno,actor,vinculo,anterior;
        public String dimension,cuerpo,huellaCuerpo;public int cx,cz;public boolean confirmada,seguir;
        public JsonArray antesJugador,despuesJugador,cuenta;
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID,Operacion> PENDIENTES=new HashMap<>();
    private static final Map<UUID,UUID> POR_ACTOR=new HashMap<>();
    private static final Map<UUID,Long> INTENTOS=new HashMap<>();
    private static Path carpeta;private static int epoca;private static long secuencia;
    private TransferenciasDurables() {}
    public static Copias copiar(ServerPlayer p,EsclavoEntity e) {return new Copias(p,e);}
    public static ItemStack insertarJugador(Copias plan,ItemStack entrada) {return insertarJugador(plan.jugador,entrada);}
    /** Solo mochila principal y fusiones en mano secundaria existente; no ocupa armadura. */
    public static ItemStack insertarJugador(ItemStack[] inventario,ItemStack entrada) {
        ItemStack resto=entrada.copy();int limite=Math.min(Inventory.INVENTORY_SIZE,inventario.length);
        for(int i=0;i<inventario.length && !resto.isEmpty();i++) {
            if(i>=limite && i!=Inventory.SLOT_OFFHAND)continue;
            ItemStack actual=inventario[i];
            if(actual.isEmpty() || !ItemStack.isSameItemSameComponents(actual,resto))continue;
            int n=Math.min(resto.getCount(),Math.max(0,actual.getMaxStackSize()-actual.getCount()));actual.grow(n);resto.shrink(n);
        }
        for(int i=0;i<limite && !resto.isEmpty();i++)if(inventario[i].isEmpty()) {
            int n=Math.min(resto.getCount(),resto.getMaxStackSize());inventario[i]=resto.copyWithCount(n);resto.shrink(n);
        }
        return resto;
    }
    private static Path file(UUID op){return carpeta.resolve(op+".json");}
    private static Path indice(UUID actor){return carpeta.resolve("actores").resolve(actor+".json");}
    private static void guardar(Operacion op){ArchivoDurable.escribir(file(op.id),GSON.toJsonTree(op));}
    private static Operacion leer(UUID id) {
        Path path=file(id);if(!Files.exists(path))return null;
        Operacion op=GSON.fromJson(ArchivoDurable.leer(path),Operacion.class);validar(op);
        if(!id.equals(op.id))throw new IllegalStateException("Identidad de transferencia incorrecta");return op;
    }
    public static void validar(Operacion op) {
        if(op==null || op.esquema!=1 || op.id==null || op.dueno==null || op.actor==null || op.vinculo==null
                || op.dimension==null || (op.cuerpo==null&&!op.confirmada) || op.id.equals(op.anterior)
                || (!op.confirmada&&(op.antesJugador==null||op.despuesJugador==null||op.cuenta==null||op.antesJugador.size()!=op.despuesJugador.size())))
            throw new IllegalStateException("Transferencia durable incompleta");
        Identifier.parse(op.dimension);
        if(op.cuerpo==null) {
            if(op.huellaCuerpo==null||!op.huellaCuerpo.matches("[0-9a-f]{64}"))throw new IllegalStateException("Recibo compacto sin huella");
            return;
        }
        if(op.huellaCuerpo!=null&&!op.huellaCuerpo.equals(huella(op.cuerpo)))throw new IllegalStateException("Cuerpo de entrega alterado");
        CompoundTag body=RitualDurable.decodificar(op.cuerpo);
        if(!op.actor.equals(body.read("UUID",UUIDUtil.CODEC).orElse(null)) || !op.dueno.toString().equals(body.getStringOr("Dueno",""))
                || !op.vinculo.toString().equals(body.getStringOr("Vinculo","")) || !op.id.toString().equals(body.getStringOr("TheSlaveRitualOperacion",""))
                || !"theslave:esclavo".equals(body.getStringOr("id","")) || !body.getBooleanOr("TieneDueno",false)
                || !body.getBooleanOr("TheSlaveRitualCustodia",false)||body.getBooleanOr("TheSlaveRitualOriginal",true))
            throw new IllegalStateException("Cuerpo de transferencia sin identidad estable");
        ListTag pos=body.getListOrEmpty("Pos");
        double x=pos.getDoubleOr(0,Double.NaN),y=pos.getDoubleOr(1,Double.NaN),z=pos.getDoubleOr(2,Double.NaN);
        if(pos.size()!=3||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000
                ||Math.floor(x/16)!=op.cx||Math.floor(z/16)!=op.cz)throw new IllegalStateException("Cuerpo fuera del chunk declarado");
    }
    private static String huella(String text) {
        try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static void registrar() {
        ServerLifecycleEvents.SERVER_STARTING.register(server->{
            epoca++;PENDIENTES.clear();POR_ACTOR.clear();INTENTOS.clear();
            carpeta=server.getWorldPath(LevelResource.ROOT).resolve("theslave_transferencias");
            if(!Files.exists(carpeta))return;Set<UUID> duenos=new HashSet<>();
            try(var files=Files.newDirectoryStream(carpeta,"*.json")) {
                for(Path path:files) {
                    Operacion op=GSON.fromJson(ArchivoDurable.leer(path),Operacion.class);validar(op);
                    if(!path.getFileName().toString().equals(op.id+".json"))throw new IllegalStateException("Identidad de archivo incorrecta");
                    if(!op.confirmada) {
                        if(!duenos.add(op.dueno)||POR_ACTOR.putIfAbsent(op.actor,op.id)!=null)throw new IllegalStateException("Transferencias pendientes incompatibles");
                        PENDIENTES.put(op.id,op);CerrojosJugador.bloquear(op.dueno,op.id);
                    } else if(Files.exists(indice(op.actor))) {
                        UUID actual=UUID.fromString(ArchivoDurable.leer(indice(op.actor)).getAsJsonObject().get("operacion").getAsString());
                        if(!actual.equals(op.id)) {
                            Operacion latest=leer(actual);
                            if(puedeRetirarAnterior(op,latest,actual))Files.deleteIfExists(path);
                        }
                    }
                }
            }catch(java.io.IOException ex){throw new IllegalStateException("No se puede leer custodia de transferencias",ex);}
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{epoca++;INTENTOS.clear();});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{carpeta=null;PENDIENTES.clear();POR_ACTOR.clear();INTENTOS.clear();});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
            ServerPlayer p=handler.getPlayer();
            for(Operacion op:new ArrayList<>(PENDIENTES.values()))if(op.dueno.equals(p.getUUID())) {continuar(p,op,null,true);break;}
        });
        // Debe registrarse despues de los filtros historicos de ritual/muerte.
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{if(entity instanceof EsclavoEntity e&&!e.isRemoved())alCargar(e);});
    }
    public static boolean ejecutar(ServerPlayer p,EsclavoEntity e,Copias plan,Runnable alConfirmar) {
        if(carpeta==null || e.level()!=p.level() || !e.esDe(p) || !e.isAlive() || e.enRito() || e.enCustodiaRitual() || CustodiaJugador.ocupada(p.getUUID()))return false;
        var vinculo=Registro.vinculo(p.getUUID(),e.vinculo());
        if(vinculo==null || vinculo.estado!=Registro.Estado.VIVO || !e.getUUID().equals(vinculo.esclavo))return false;
        if(!p.getUUID().equals(plan.dueno)||!e.getUUID().equals(plan.actor)
                ||!CustodiaJugador.capturar(p).equals(plan.antesJugador)||!EntityDurability.snapshot(e).equals(plan.antesEntidad))
            throw new IllegalStateException("Las copias dejaron de representar el estado actual");
        Operacion op=new Operacion();op.id=UUID.randomUUID();op.dueno=p.getUUID();op.actor=e.getUUID();op.vinculo=e.vinculo();
        op.dimension=p.level().dimension().identifier().toString();op.cx=e.chunkPosition().x();op.cz=e.chunkPosition().z();
        op.antesJugador=plan.antesJugador.deepCopy();op.despuesJugador=CustodiaJugador.codificar(p,plan.jugador);op.cuenta=plan.cuenta.deepCopy();
        CompoundTag body=plan.cuerpo.copy();var ops=RegistryOps.create(NbtOps.INSTANCE,p.registryAccess());
        if(!Objects.equals(body.get("Pos"),plan.antesEntidad.get("Pos")))throw new IllegalStateException("Una entrega no puede transportar el cuerpo");
        ListTag bag=new ListTag();for(ItemStack stack:plan.mochila.stacks())bag.add(ItemStack.CODEC.encodeStart(ops,stack).getOrThrow());body.put("Mochila",bag);
        if(plan.referencia.isEmpty())body.remove("Ref");else body.store("Ref",ItemStack.CODEC,ops,plan.referencia);
        EntityEquipment equipment=new EntityEquipment();plan.equipo.forEach((slot,stack)->equipment.set(slot,stack.copy()));
        if(equipment.isEmpty())body.remove("equipment");else body.store("equipment",EntityEquipment.CODEC,ops,equipment);
        body.putString("TheSlaveRitualOperacion",op.id.toString());body.putBoolean("TheSlaveRitualOriginal",false);body.putBoolean("TheSlaveRitualCustodia",true);
        body.putDouble("TheSlaveCustodiaX",e.getX());body.putDouble("TheSlaveCustodiaY",e.getY());body.putDouble("TheSlaveCustodiaZ",e.getZ());
        op.seguir=body.getBooleanOr("Siguiendo",false);op.cuerpo=RitualDurable.codificar(body);op.huellaCuerpo=huella(op.cuerpo);
        if(Files.exists(indice(op.actor)))op.anterior=UUID.fromString(ArchivoDurable.leer(indice(op.actor)).getAsJsonObject().get("operacion").getAsString());
        try {validar(op);guardar(op);}catch(RuntimeException ex){TheSlaveMod.LOGGER.error("No se pudo preparar la entrega; no se transfiere ningun objeto",ex);return false;}
        PENDIENTES.put(op.id,op);POR_ACTOR.put(op.actor,op.id);CustodiaJugador.bloquear(p,op.id);e.marcarOperacion(op.id,false);
        continuar(p,op,alConfirmar,false);return true;
    }
    private static ServerLevel nivel(MinecraftServer server,Operacion op) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(op.dimension)));
    }
    private static void alCargar(EsclavoEntity e) {
        UUID pendiente=POR_ACTOR.get(e.getUUID());
        if(pendiente!=null) {aplicarCuerpo(e,PENDIENTES.get(pendiente));return;}
        if(carpeta==null || e.dueno()==null || !Files.exists(indice(e.getUUID())))return;
        var pointer=ArchivoDurable.leer(indice(e.getUUID())).getAsJsonObject();UUID id=UUID.fromString(pointer.get("operacion").getAsString());
        UUID custodia=CerrojosJugador.operacion(e.dueno());
        DecisionCarga decision=decidirCarga(false,custodia!=null&&!custodia.equals(id),id.equals(e.operacionRitual()),e.enCustodiaRitual());
        if(decision==DecisionCarga.OMITIR)return;
        Operacion op=leer(id);if(op==null||!op.confirmada||!op.actor.equals(e.getUUID())||!op.dueno.equals(e.dueno()))throw new IllegalStateException("Indice de transferencia no confirmado");
        if(decision==DecisionCarga.RESTAURAR) {
            if(op.cuerpo==null){e.marcarOperacion(op.id,false);throw new IllegalStateException("El indice apunta a un recibo historico compacto");}
            aplicarCuerpo(e,op); // Copia de chunk anterior a la ultima entrega.
        }
        liberarCuerpo(e,op);
    }
    public enum DecisionCarga { PENDIENTE, OMITIR, RESTAURAR, LIBERAR }
    public static DecisionCarga decidirCarga(boolean pendiente,boolean custodiaAjena,boolean marcadorActual,boolean retenido) {
        if(pendiente)return DecisionCarga.PENDIENTE;
        if(custodiaAjena || (marcadorActual&&!retenido))return DecisionCarga.OMITIR;
        return marcadorActual?DecisionCarga.LIBERAR:DecisionCarga.RESTAURAR;
    }
    private static void aplicarCuerpo(EsclavoEntity e,Operacion op) {
        EntityDurability.aplicarSnapshot(e,RitualDurable.decodificar(op.cuerpo));e.marcarOperacion(op.id,false);
    }
    private static void liberarCuerpo(EsclavoEntity e,Operacion op){e.liberarCustodiaRitual();e.sigue(op.seguir);}
    public interface Participantes {
        void comprobar();CompletableFuture<Void> cuerpo();void jugador();void guardarJugador();
        CompletableFuture<CompoundTag> guardarCuerpo();void commit();void liberar();
    }
    /** El mismo orden se usa en produccion y en simulaciones de caida por frontera. */
    public static CompletableFuture<Void> confirmar(Operacion op,Participantes p) {
        try {
            p.comprobar();
            if(op.confirmada){p.liberar();return CompletableFuture.completedFuture(null);}
            return p.cuerpo().thenCompose(v->{p.comprobar();p.jugador();p.comprobar();p.guardarJugador();p.comprobar();return p.guardarCuerpo();})
                .thenAccept(nbt->{p.comprobar();verificarCuerpo(nbt,op);p.commit();op.confirmada=true;p.comprobar();p.liberar();});
        }catch(RuntimeException ex){return CompletableFuture.failedFuture(ex);}
    }
    public static void verificarCuerpo(CompoundTag chunk,Operacion op) {
        CompoundTag expected=RitualDurable.decodificar(op.cuerpo);int count=0;
        for(CompoundTag body:chunk.getListOrEmpty("Entities").compoundStream().toList())if(op.actor.equals(body.read("UUID",UUIDUtil.CODEC).orElse(null))) {
            count++;if(!op.id.toString().equals(body.getStringOr("TheSlaveRitualOperacion",""))||!body.getBooleanOr("TheSlaveRitualCustodia",false)
                    ||body.getBooleanOr("TheSlaveRitualOriginal",true))throw new IllegalStateException("ACK de cuerpo sin custodia");
            for(String key:List.of("Mochila","Ref","equipment","Dueno","Vinculo","id","TieneDueno","EnergiaFundicion"))if(!Objects.equals(expected.get(key),body.get(key)))throw new IllegalStateException("ACK de entrega incompleto: "+key);
        }
        if(count!=1)throw new IllegalStateException("ACK no contiene un unico cuerpo");
    }
    private static void continuar(ServerPlayer p,Operacion op,Runnable alConfirmar,boolean recuperando) {
        var server=p.level().getServer();int sesion=epoca;long intento=++secuencia;INTENTOS.put(op.id,intento);CustodiaJugador.bloquear(p,op.id);
        ServerLevel level=nivel(server,op);if(level==null){fallo(p,op,new IllegalStateException("Dimension ausente"));return;}
        confirmar(op,new Participantes(){
            public void comprobar(){if(!RitualDurable.intentoVigente(sesion,epoca,intento,INTENTOS.get(op.id),p,server.getPlayerList().getPlayer(op.dueno)))throw new IllegalStateException("Intento de entrega obsoleto");}
            public CompletableFuture<Void> cuerpo(){return EntityDurability.cargarEntidades(level,new ChunkPos(op.cx,op.cz)).thenRun(()->{
                comprobar();if(!Registro.cuentaOperacion(op.dueno).equals(op.cuenta))throw new IllegalStateException("Vinculo cambiado durante entrega");
                var actual=EntityDurability.buscar(level,op.actor);EsclavoEntity actor;
                if(actual==null){actor=EntityDurability.restaurar(level,RitualDurable.decodificar(op.cuerpo));if(!level.addFreshEntity(actor))throw new IllegalStateException("No se puede recuperar el cuerpo");}
                else if(actual instanceof EsclavoEntity a){actor=a;aplicarCuerpo(actor,op);}else throw new IllegalStateException("UUID ocupado por otra entidad");
            });}
            public void jugador(){if(recuperando)CustodiaJugador.recuperar(p,op.id,op.antesJugador,op.despuesJugador);else CustodiaJugador.aplicar(p,op.id,op.antesJugador,op.despuesJugador);}
            public void guardarJugador(){CustodiaJugador.guardarConfirmado(p,op.id);}
            public CompletableFuture<CompoundTag> guardarCuerpo(){return EntityDurability.guardar(level,new ChunkPos(op.cx,op.cz));}
            public void commit(){JsonObject pointer=new JsonObject();pointer.addProperty("operacion",op.id.toString());ArchivoDurable.escribir(indice(op.actor),pointer);
                JsonObject committed=GSON.toJsonTree(op).getAsJsonObject();committed.addProperty("confirmada",true);committed.remove("antesJugador");committed.remove("despuesJugador");committed.remove("cuenta");ArchivoDurable.escribir(file(op.id),committed);}
            public void liberar(){var actor=EntityDurability.buscar(level,op.actor);if(actor instanceof EsclavoEntity a)liberarCuerpo(a,op);
                retirarAnterior(op);
                PENDIENTES.remove(op.id);POR_ACTOR.remove(op.actor,op.id);INTENTOS.remove(op.id);CustodiaJugador.desbloquear(op.dueno,op.id);
                if(alConfirmar!=null)try{alConfirmar.run();}catch(RuntimeException ex){TheSlaveMod.LOGGER.warn("Entrega confirmada; fallo al mostrar aviso",ex);}}
        }).whenComplete((v,error)->{if(error!=null && RitualDurable.intentoVigente(sesion,epoca,intento,INTENTOS.get(op.id),p,server.getPlayerList().getPlayer(op.dueno)))fallo(p,op,error);});
    }
    public static boolean puedeRetirarAnterior(Operacion anterior,Operacion actual,UUID indiceActual) {
        return anterior!=null && actual!=null && anterior.confirmada && actual.confirmada
                && actual.cuerpo!=null
                && !anterior.id.equals(actual.id) && actual.id.equals(indiceActual)
                && anterior.actor.equals(actual.actor) && anterior.dueno.equals(actual.dueno);
    }
    private static void retirarAnterior(Operacion actual) {
        if(actual.anterior==null)return;
        try {
            Operacion old=leer(actual.anterior);
            if(old==null)return;
            Operacion committed=leer(actual.id);
            if(committed==null||!committed.confirmada||committed.cuerpo==null)throw new IllegalStateException("Falta el nuevo COMMIT durable con su cuerpo");
            UUID pointer=UUID.fromString(ArchivoDurable.leer(indice(actual.actor)).getAsJsonObject().get("operacion").getAsString());
            if(!puedeRetirarAnterior(old,committed,pointer))throw new IllegalStateException("Recibo previo aun necesario");
            Files.deleteIfExists(file(old.id));
        }catch(RuntimeException|java.io.IOException ex){TheSlaveMod.LOGGER.warn("Entrega confirmada; recibo anterior conservado",ex);}
    }
    private static void fallo(ServerPlayer p,Operacion op,Throwable error) {
        INTENTOS.remove(op.id);TheSlaveMod.LOGGER.error("Entrega {} bajo custodia pendiente de recuperar",op.id,error);
        p.connection.disconnect(Component.literal("TheSlave: entrega bajo custodia. Vuelve a entrar para recuperarla."));
    }
}
