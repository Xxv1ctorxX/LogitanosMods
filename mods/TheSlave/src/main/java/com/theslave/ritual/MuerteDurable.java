package com.theslave.ritual;

import com.google.gson.*;
import com.theslave.Registro;
import com.theslave.TheSlaveMod;
import com.theslave.block.TumbaBlockEntity;
import com.theslave.entity.EsclavoEntity;
import com.theslave.persistencia.*;
import com.theslave.registry.ModBlocks;
import com.theslave.registry.ModItems;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;

/** WAL de muerte y retiros: el botin se materializa con UUID fijo y queda inmovil hasta write+fsync. */
public final class MuerteDurable {
    public enum Tipo { MUERTE, LIBERAR, ENTERRAR, RECUPERAR_CUSTODIA }
    public static final class Operacion {
        public int esquema=1;
        public UUID id,dueno,vinculo,cuerpo;
        public Tipo tipo;
        public boolean confirmada;
        public String dimension,nombre,causa,cuerpoDespues,dimensionTumba,dimensionSegura;
        public int cx,cz,variante,muertes,dia;
        public Long tumba,sueloSeguro;
        public JsonArray cuentaAntes,cuentaDespues,inventarioAntes,inventarioDespues;
        public List<String> botin=new ArrayList<>();
        public transient List<UUID> idsBotin;
        public transient boolean resumida;
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID,Operacion> OPERACIONES=new LinkedHashMap<>(),CUERPOS=new HashMap<>(),BOTIN=new HashMap<>();
    private static final Set<UUID> ACTIVAS=new HashSet<>();
    private record Vinculo(UUID dueno,UUID vinculo) {}
    private record Chunk(String dimension,long posicion) {}
    private record Tumba(UUID dueno,UUID vinculo,String dimension,long posicion,int generacion) {}
    private static final Map<UUID,Operacion> PENDIENTES=new HashMap<>();
    private static final Map<Vinculo,Operacion> ULTIMAS_MUERTES=new HashMap<>();
    private static final Map<Chunk,Set<UUID>> TUMBAS_CHUNK=new HashMap<>();
    private static final Set<Tumba> CUSTODIAS_DEVUELTAS=new HashSet<>();
    private static Path carpeta;
    private static boolean registroListo;
    private static int sesion;
    private MuerteDurable() {}
    public static UUID tokenMuerte(UUID dueno,UUID vinculo,int generacion) {
        Operacion op=ULTIMAS_MUERTES.get(new Vinculo(dueno,vinculo));
        return op!=null && op.muertes==generacion?op.id:null;
    }

    public static void registrar() {
        ServerLifecycleEvents.SERVER_STARTING.register(MuerteDurable::cargar);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            registroListo=true;
            for(Operacion op:new ArrayList<>(PENDIENTES.values())) {
                ServerLevel sl=nivel(server,op);
                if(sl==null)throw new IllegalStateException("Dimension de una muerte ausente");
                reconciliarTumba(nivelTumba(server,op),op);
                if(!op.confirmada) {
                    CerrojosJugador.bloquear(op.dueno,op.id);
                    if(op.tipo==Tipo.MUERTE)continuar(server,null,op);
                }
            }
            Set<UUID> lapidas=new HashSet<>();TUMBAS_CHUNK.values().forEach(lapidas::addAll);
            for(UUID id:lapidas) { var op=OPERACIONES.get(id);if(op.confirmada)reconciliarTumba(nivelTumba(server,op),op); }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { sesion++;ACTIVAS.clear(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { OPERACIONES.clear();CUERPOS.clear();BOTIN.clear();PENDIENTES.clear();ULTIMAS_MUERTES.clear();TUMBAS_CHUNK.clear();CUSTODIAS_DEVUELTAS.clear();carpeta=null;registroListo=false; });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            Operacion op=PENDIENTES.get(handler.player.getUUID());
            if(op!=null) { CerrojosJugador.bloquear(op.dueno,op.id);continuar(server,handler.player,op); }
        });
        ServerChunkEvents.CHUNK_LOAD.register((sl,chunk,nuevo) -> {
            if(!registroListo)return;
            for(UUID id:TUMBAS_CHUNK.getOrDefault(new Chunk(sl.dimension().identifier().toString(),chunk.getPos().pack()),Set.of()))reconciliarTumba(sl,OPERACIONES.get(id));
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,sl) -> {
            if(entity instanceof EsclavoEntity e) {
                Operacion op=CUERPOS.get(e.getUUID());
                if(op==null)return;
                if(op.tipo==Tipo.MUERTE) { e.discard();return; }
                if(op.confirmada) {
                    if(custodiaAjena(e.enCustodiaRitual(),op.id,e.operacionRitual()))return;
                    if(e.tieneDueno() || !op.id.equals(e.operacionRitual()))EntityDurability.aplicarSnapshot(e,RitualDurable.decodificar(completa(op).cuerpoDespues));
                    if(RitualDurable.custodiaPropia(op.id,e.operacionRitual()))e.liberarCustodiaRitual();
                } else e.marcarOperacion(op.id,true);
            } else if(entity instanceof ItemEntity item) {
                Operacion op=BOTIN.get(item.getUUID());
                if(op==null)return;
                var custodia=(ItemCustody)item;
                if(op.confirmada) { if(custodia.theslave$custodiado())custodia.theslave$liberar(); }
                else custodia.theslave$custodiar(op.id);
            }
        });
    }

    private static ServerLevel nivel(MinecraftServer server,Operacion op) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(op.dimension)));
    }
    private static String dimensionTumba(Operacion op) { return op.dimensionTumba==null?op.dimension:op.dimensionTumba; }
    private static ServerLevel nivelTumba(MinecraftServer server,Operacion op) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(dimensionTumba(op))));
    }
    private static void cargar(MinecraftServer server) {
        sesion++;registroListo=false;OPERACIONES.clear();CUERPOS.clear();BOTIN.clear();ACTIVAS.clear();
        PENDIENTES.clear();ULTIMAS_MUERTES.clear();TUMBAS_CHUNK.clear();CUSTODIAS_DEVUELTAS.clear();
        carpeta=server.getWorldPath(LevelResource.ROOT).resolve("theslave_muertes");
        if(!Files.exists(carpeta))return;
        Set<UUID> pendientes=new HashSet<>();
        try(var files=Files.list(carpeta)) {
            var iterator=files.filter(p -> p.getFileName().toString().endsWith(".json")).iterator();
            while(iterator.hasNext()) {
                Path file=iterator.next();
                Operacion op=GSON.fromJson(ArchivoDurable.leer(file),Operacion.class);validar(op);
                if(!file.getFileName().toString().equals(op.id+".json") || OPERACIONES.putIfAbsent(op.id,op)!=null)
                    throw new IllegalStateException("Muerte WAL duplicada");
                if(!op.confirmada && !pendientes.add(op.dueno))throw new IllegalStateException("Varias muertes/retiros pendientes del mismo dueno");
                indexar(op);
                if(op.confirmada) { indexarConfirmada(op);compactar(op); }
            }
        } catch(java.io.IOException ex) { throw new IllegalStateException("No se puede cargar el WAL de muertes",ex); }
        // Todas las entradas se validan antes de reconstruir los cerrojos. Deben existir
        // antes de ENTITY_LOAD, aun cuando el propietario no vuelva a conectarse.
        for(Operacion op:OPERACIONES.values())if(!op.confirmada)CerrojosJugador.bloquear(op.dueno,op.id);
    }
    private static void indexar(Operacion op) {
        if(!op.confirmada)PENDIENTES.put(op.dueno,op);
        if(op.tipo==Tipo.MUERTE) {
            var key=new Vinculo(op.dueno,op.vinculo);var anterior=ULTIMAS_MUERTES.get(key);
            if(anterior!=null && anterior.muertes==op.muertes && !anterior.id.equals(op.id))throw new IllegalStateException("Dos muertes para una generacion");
            if(anterior==null || anterior.muertes<op.muertes)ULTIMAS_MUERTES.put(key,op);
        }
        if(op.tumba!=null)TUMBAS_CHUNK.computeIfAbsent(new Chunk(dimensionTumba(op),ChunkPos.containing(BlockPos.of(op.tumba)).pack()),k -> new HashSet<>()).add(op.id);
        if(op.cuerpo!=null && CUERPOS.putIfAbsent(op.cuerpo,op)!=null)throw new IllegalStateException("Cuerpo con dos retiros");
        List<UUID> ids=new ArrayList<>(op.botin.size());
        for(String encoded:op.botin) {
            UUID uuid=RitualDurable.decodificar(encoded).read("UUID",UUIDUtil.CODEC).orElseThrow();
            if(BOTIN.putIfAbsent(uuid,op)!=null)throw new IllegalStateException("Botin con UUID repetido");
            ids.add(uuid);
        }
        op.idsBotin=List.copyOf(ids);
    }
    private static void guardar(Operacion op) {
        if(op.resumida)throw new IllegalStateException("No se sobrescribe un WAL con un indice reducido");
        ArchivoDurable.escribir(carpeta.resolve(op.id+".json"),GSON.toJsonTree(op));
    }
    private static Operacion completa(Operacion op) {
        if(!op.resumida)return op;
        Operacion completa=GSON.fromJson(ArchivoDurable.leer(carpeta.resolve(op.id+".json")),Operacion.class);validar(completa);
        if(!completa.id.equals(op.id) || !completa.confirmada)throw new IllegalStateException("Recibo de muerte incoherente");
        return completa;
    }
    private static List<UUID> idsBotin(Operacion op) {
        if(op.idsBotin!=null)return op.idsBotin;
        return op.botin.stream().map(encoded -> RitualDurable.decodificar(encoded).read("UUID",UUIDUtil.CODEC).orElseThrow()).toList();
    }
    private static void indexarConfirmada(Operacion op) {
        if(op.tipo==Tipo.RECUPERAR_CUSTODIA && op.tumba!=null)CUSTODIAS_DEVUELTAS.add(new Tumba(op.dueno,op.vinculo,dimensionTumba(op),op.tumba,op.muertes));
    }
    /** Los WAL siguen completos en disco; solo las operaciones pendientes retienen snapshots en RAM. */
    public static void compactar(Operacion op) {
        if(!op.confirmada)return;
        op.idsBotin=idsBotin(op);op.botin=List.of();op.cuentaAntes=null;op.cuentaDespues=null;
        op.inventarioAntes=null;op.inventarioDespues=null;op.cuerpoDespues=null;op.resumida=true;
    }
    public static void validar(Operacion op) {
        if(op==null || op.esquema!=1 || op.id==null || op.dueno==null || op.vinculo==null || op.tipo==null
                || op.dimension==null || op.cuentaAntes==null || op.cuentaDespues==null || op.botin==null
                || op.muertes<0 || ((op.tipo==Tipo.MUERTE || op.tipo==Tipo.LIBERAR) && op.cuerpo==null)
                || (op.tipo!=Tipo.MUERTE && (op.inventarioAntes==null || op.inventarioDespues==null))
                || (op.tipo==Tipo.LIBERAR && op.cuerpoDespues==null))throw new IllegalStateException("WAL de muerte/retiro incompleto");
        Identifier.parse(op.dimension);
        if(op.dimensionTumba!=null)Identifier.parse(op.dimensionTumba);
        if(op.dimensionSegura!=null)Identifier.parse(op.dimensionSegura);
        Set<UUID> ids=new HashSet<>();
        for(String encoded:op.botin) {
            CompoundTag tag=RitualDurable.decodificar(encoded);
            UUID uuid=tag.read("UUID",UUIDUtil.CODEC).orElseThrow();
            if(!ids.add(uuid) || uuid.equals(op.cuerpo) || !op.id.toString().equals(tag.getStringOr("TheSlaveBotinOperacion",""))
                    || !tag.getBooleanOr("TheSlaveBotinCustodia",false) || tag.getCompound("Item").isEmpty())throw new IllegalStateException("Snapshot de botin sin custodia/identidad");
            var posicion=tag.getListOrEmpty("Pos");
            if(posicion.size()!=3)throw new IllegalStateException("Botin sin posicion completa");
            for(int i=0;i<3;i++)if(posicion.getDouble(i).isEmpty() || !Double.isFinite(posicion.getDoubleOr(i,Double.NaN)))throw new IllegalStateException("Posicion de botin invalida");
        }
        if(op.tipo==Tipo.LIBERAR) {
            var body=RitualDurable.decodificar(op.cuerpoDespues);
            if(!op.cuerpo.equals(body.read("UUID",UUIDUtil.CODEC).orElse(null)) || body.getBooleanOr("TieneDueno",true)
                    || !op.id.toString().equals(body.getStringOr("TheSlaveRitualOperacion","")))throw new IllegalStateException("Cuerpo liberado invalido");
        }
    }

    /** Transicion pura de la cuenta: prepara el despues sin llamar Registro.muerto/liberar. */
    public static JsonArray cuentaDespues(JsonArray antes,UUID vinculo,Tipo tipo,String nombre,int variante) {
        if(tipo==Tipo.RECUPERAR_CUSTODIA)return antes.deepCopy();
        JsonArray despues=antes.deepCopy();JsonObject objetivo=null;
        for(var elemento:despues)if(vinculo.toString().equals(elemento.getAsJsonObject().get("id").getAsString()))objetivo=elemento.getAsJsonObject();
        if(objetivo==null)throw new IllegalStateException("No existe el vinculo que se retira");
        String estado=objetivo.get("estado").getAsString();
        if(tipo==Tipo.MUERTE) {
            if(!"VIVO".equals(estado))throw new IllegalStateException("La muerte ya fue registrada o no corresponde a un vivo");
            objetivo.addProperty("estado","DIFUNTO");objetivo.addProperty("muertes",objetivo.get("muertes").getAsInt()+1);
            objetivo.addProperty("nombre",nombre);objetivo.addProperty("variante",variante);
        } else {
            if((tipo==Tipo.LIBERAR && !"VIVO".equals(estado)) || (tipo==Tipo.ENTERRAR && !"DIFUNTO".equals(estado)))throw new IllegalStateException("Estado incompatible con retiro");
            objetivo.addProperty("estado","LIBERADO");
        }
        objetivo.remove("esclavo");return despues;
    }
    private static Operacion base(ServerLevel sl,UUID dueno,UUID vinculo,Tipo tipo) {
        if(carpeta==null || CerrojosJugador.ocupada(dueno))throw new IllegalStateException("Cuenta bajo custodia");
        Operacion op=new Operacion();op.id=UUID.randomUUID();op.dueno=dueno;op.vinculo=vinculo;op.tipo=tipo;
        op.dimension=sl.dimension().identifier().toString();op.cuentaAntes=Registro.cuentaOperacion(dueno);
        return op;
    }
    private static void preparar(Operacion op) {
        validar(op);guardar(op);OPERACIONES.put(op.id,op);indexar(op);CerrojosJugador.bloquear(op.dueno,op.id);
    }
    private static void agregarBotin(ServerLevel sl,Operacion op,List<ItemStack> stacks,BlockPos donde) {
        for(ItemStack stack:stacks)if(!stack.isEmpty()) {
            var item=new ItemEntity(sl,donde.getX()+0.5,donde.getY()+1,donde.getZ()+0.5,stack.copy());
            ((ItemCustody)item).theslave$custodiar(op.id);
            op.botin.add(RitualDurable.codificar(EntityDurability.snapshot(item)));
        }
    }
    public static void morir(ServerLevel sl,EsclavoEntity e,BlockPos sitio,String causa) {
        Operacion op=base(sl,e.dueno(),e.vinculo(),Tipo.MUERTE);
        op.cuerpo=e.getUUID();op.cx=e.chunkPosition().x();op.cz=e.chunkPosition().z();
        op.nombre=e.nombre().isEmpty()?"Sin nombre":e.nombre();op.variante=e.variante();op.causa=causa;op.dia=(int)(sl.getGameTime()/24000L);
        var ficha=Registro.vinculo(op.dueno,op.vinculo);
        if(ficha==null || !e.getUUID().equals(ficha.esclavo))throw new IllegalStateException("El cuerpo no coincide con el vinculo vivo");
        op.muertes=ficha.muertes+1;
        op.tumba=null; // El hogar personal se convierte en tumba; nunca el lugar donde cae.
        op.cuentaDespues=cuentaDespues(op.cuentaAntes,op.vinculo,op.tipo,op.nombre,op.variante);
        List<ItemStack> stacks=new ArrayList<>();e.inv().stacks().forEach(s -> stacks.add(s.copy()));
        if(!e.ref().isEmpty())stacks.add(e.ref().copy());
        for(EquipmentSlot slot:EquipmentSlot.values())if(!e.getItemBySlot(slot).isEmpty())stacks.add(e.getItemBySlot(slot).copy());
        // El botin conserva el punto local de muerte; la sepultura puede recuperarse en otro suelo seguro.
        agregarBotin(sl,op,stacks,e.blockPosition());
        preparar(op);e.marcarOperacion(op.id,true);e.discard();
        continuar(sl.getServer(),null,op);
        var p=sl.getServer().getPlayerList().getPlayer(op.dueno);
        if(p!=null) { BlockPos donde=e.blockPosition();p.sendSystemMessage(Component.translatable("theslave.dice.murio",op.nombre,donde.getX(),donde.getY(),donde.getZ())); }
    }

    /** Solo se devuelve referencia/mochila; el equipo permanece en el cuerpo libre, igual que antes. */
    public static void liberar(ServerPlayer p,EsclavoEntity e,ItemStack contrato) {
        if(!e.esDe(p) || !contrato.is(ModItems.CONTRATO) || contrato.has(com.theslave.registry.ModComponents.PACTO))throw new IllegalArgumentException("Liberacion no autorizada");
        ServerLevel sl=(ServerLevel)p.level();Operacion op=base(sl,p.getUUID(),e.vinculo(),Tipo.LIBERAR);
        op.cuerpo=e.getUUID();op.cx=e.chunkPosition().x();op.cz=e.chunkPosition().z();
        var ficha=Registro.vinculo(op.dueno,op.vinculo);
        if(ficha==null || !e.getUUID().equals(ficha.esclavo))throw new IllegalStateException("El cuerpo no coincide con su vinculo");
        op.nombre=e.nombre();op.variante=e.variante();op.muertes=ficha.muertes;
        op.cuentaDespues=cuentaDespues(op.cuentaAntes,op.vinculo,op.tipo,op.nombre,op.variante);
        ItemStack[] inventario=copiarInventario(p);boolean consumido=false;
        for(int i=0;i<inventario.length;i++)if(p.getInventory().getItem(i)==contrato) { inventario[i].shrink(1);consumido=true;break; }
        if(!consumido)throw new IllegalStateException("El contrato ya no esta en el inventario");
        List<ItemStack> restos=new ArrayList<>();
        if(!e.ref().isEmpty()) { var resto=insertar(inventario,e.ref());if(!resto.isEmpty())restos.add(resto); }
        for(ItemStack stack:e.inv().stacks()) { var resto=insertar(inventario,stack);if(!resto.isEmpty())restos.add(resto); }
        op.inventarioAntes=CustodiaJugador.capturar(p);op.inventarioDespues=CustodiaJugador.codificar(p,inventario);
        CompoundTag cuerpo=EntityDurability.snapshot(e);cuerpo.remove("Dueno");cuerpo.remove("Vinculo");cuerpo.remove("Ref");
        cuerpo.putBoolean("TieneDueno",false);cuerpo.put("Mochila",new ListTag());cuerpo.putInt("Estado",0);cuerpo.putBoolean("Siguiendo",false);cuerpo.putBoolean("RitualSuspendido",false);
        cuerpo.putString("TheSlaveRitualOperacion",op.id.toString());cuerpo.putBoolean("TheSlaveRitualOriginal",false);cuerpo.putBoolean("TheSlaveRitualCustodia",true);
        cuerpo.putDouble("TheSlaveCustodiaX",e.getX());cuerpo.putDouble("TheSlaveCustodiaY",e.getY());cuerpo.putDouble("TheSlaveCustodiaZ",e.getZ());
        op.cuerpoDespues=RitualDurable.codificar(cuerpo);
        agregarBotin(sl,op,restos,e.blockPosition());preparar(op);e.marcarOperacion(op.id,true);continuar(sl.getServer(),p,op);
    }
    public static boolean enterrar(ServerPlayer p,TumbaBlockEntity be) {
        return retirarCustodia(p,be,Tipo.ENTERRAR);
    }
    /** Migracion idempotente de contratos/manzanas de tumbas antiguas, sin retirar su vinculo. */
    public static void recuperarCustodia(ServerPlayer p,TumbaBlockEntity be) {
        normalizarCustodia(be);
        if(be.custodia==0 || CerrojosJugador.ocupada(p.getUUID()))return;
        if(!p.getUUID().equals(be.dueno))throw new IllegalArgumentException("Custodia ajena");
        retirarCustodia(p,be,Tipo.RECUPERAR_CUSTODIA);
    }
    private static boolean retirarCustodia(ServerPlayer p,TumbaBlockEntity be,Tipo tipo) {
        normalizarCustodia(be);
        var ficha=Registro.vinculo(p.getUUID(),be.vinculoId());
        if(!p.getUUID().equals(be.dueno) || ficha==null || ficha.estado!=Registro.Estado.DIFUNTO || ficha.muertes!=be.muertes) {
            p.sendOverlayMessage(Component.translatable("theslave.tumba.antigua"));return false;
        }
        ServerLevel sl=(ServerLevel)p.level();Operacion op=base(sl,p.getUUID(),be.vinculoId(),tipo);
        op.tumba=be.getBlockPos().asLong();op.cx=be.getBlockPos().getX()>>4;op.cz=be.getBlockPos().getZ()>>4;
        op.nombre=be.nombre;op.variante=be.variante;op.muertes=be.muertes;
        op.cuentaDespues=cuentaDespues(op.cuentaAntes,op.vinculo,op.tipo,op.nombre,op.variante);
        ItemStack[] inv=copiarInventario(p);List<ItemStack> restos=new ArrayList<>();
        for(ItemStack stack:List.of((be.custodia&1)!=0?new ItemStack(ModItems.CONTRATO):ItemStack.EMPTY,(be.custodia&2)!=0?new ItemStack(Items.GOLDEN_APPLE):ItemStack.EMPTY)) {
            var resto=insertar(inv,stack);if(!resto.isEmpty())restos.add(resto);
        }
        op.inventarioAntes=CustodiaJugador.capturar(p);op.inventarioDespues=CustodiaJugador.codificar(p,inv);
        agregarBotin(sl,op,restos,be.getBlockPos());preparar(op);be.operacionRitual(op.id);continuar(sl.getServer(),p,op);
        return true;
    }
    private static void normalizarCustodia(TumbaBlockEntity be) {
        if(be.getLevel()!=null && CUSTODIAS_DEVUELTAS.contains(new Tumba(be.dueno,be.vinculoId(),be.getLevel().dimension().identifier().toString(),be.getBlockPos().asLong(),be.muertes))) {
            be.custodia=0;be.setChanged();return;
        }
    }
    private static ItemStack[] copiarInventario(ServerPlayer p) {
        ItemStack[] inv=new ItemStack[p.getInventory().getContainerSize()];
        for(int i=0;i<inv.length;i++)inv[i]=p.getInventory().getItem(i).copy();return inv;
    }
    /** Simula una devolucion vanilla en mochila principal, conservando excedente para drops durables. */
    public static ItemStack insertar(ItemStack[] inventario,ItemStack entrada) {
        ItemStack resto=entrada.copy();int limite=Math.min(Inventory.INVENTORY_SIZE,inventario.length);
        for(int i=0;i<limite && !resto.isEmpty();i++)if(!inventario[i].isEmpty() && ItemStack.isSameItemSameComponents(inventario[i],resto)) {
            int n=Math.min(resto.getCount(),Math.max(0,inventario[i].getMaxStackSize()-inventario[i].getCount()));inventario[i].grow(n);resto.shrink(n);
        }
        for(int i=0;i<limite && !resto.isEmpty();i++)if(inventario[i].isEmpty()) {
            int n=Math.min(resto.getCount(),resto.getMaxStackSize());inventario[i]=resto.copyWithCount(n);resto.shrink(n);
        }
        return resto;
    }

    private static void continuar(MinecraftServer server,ServerPlayer jugador,Operacion op) {
        if(op.confirmada) { limpiarConfirmada(server,op);return; }
        if(!ACTIVAS.add(op.id))return;int epoca=sesion;
        ServerLevel sl=nivel(server,op);
        try {
            if(sl==null)throw new IllegalStateException("Dimension ausente");
            CerrojosJugador.bloquear(op.dueno,op.id);
            if(op.tipo==Tipo.MUERTE) {
                Registro.aplicarOperacion(op.id,op.dueno,op.cuentaAntes,op.cuentaDespues);
                Hogares.muerte(op.dueno,op.vinculo,op.muertes);
            }
            else {
                if(jugador==null) { ACTIVAS.remove(op.id);return; }
                if(!TransaccionJugador.confirmada(server,op.dueno,op.id))TransaccionJugador.ejecutarBajoCustodia(jugador,op.tipo.name(),op.id,op.inventarioAntes,op.inventarioDespues,op.cuentaAntes,op.cuentaDespues);
            }
            Set<ChunkPos> chunks=new LinkedHashSet<>();chunks.add(new ChunkPos(op.cx,op.cz));
            for(String encoded:op.botin)chunks.add(chunkDe(RitualDurable.decodificar(encoded)));
            CompletableFuture.allOf(chunks.stream().map(c -> EntityDurability.cargarEntidades(sl,c)).toArray(CompletableFuture[]::new)).whenComplete((v,error) -> {
                if(epoca!=sesion)return;
                if(error!=null) { fallo(server,op,error);return; }
                try {
                    var padre=op.cuerpo==null?null:EntityDurability.buscar(sl,op.cuerpo);
                    if(op.tipo==Tipo.MUERTE) { if(padre!=null)padre.discard(); }
                    else if(op.tipo==Tipo.LIBERAR) {
                        EsclavoEntity libre;
                        if(padre==null) { libre=EntityDurability.restaurar(sl,RitualDurable.decodificar(op.cuerpoDespues));if(!sl.addFreshEntity(libre))throw new IllegalStateException("No se pudo recuperar el cuerpo libre"); }
                        else if(padre instanceof EsclavoEntity e) { libre=e;EntityDurability.aplicarSnapshot(e,RitualDurable.decodificar(op.cuerpoDespues)); }
                        else throw new IllegalStateException("UUID del cuerpo ocupado");
                        libre.marcarOperacion(op.id,false);
                    }
                    for(String encoded:op.botin) {
                        var tag=RitualDurable.decodificar(encoded);UUID id=tag.read("UUID",UUIDUtil.CODEC).orElseThrow();
                        var anterior=EntityDurability.buscar(sl,id);
                        ItemEntity item;
                        if(anterior==null) {
                            item=new ItemEntity(sl,0,0,0,ItemStack.EMPTY);
                            EntityDurability.aplicarSnapshot(item,tag);
                            if(!sl.addFreshEntity(item))throw new IllegalStateException("UUID del botin rechazado");
                        } else if(anterior instanceof ItemEntity i)item=i;else throw new IllegalStateException("UUID del botin ocupado");
                        if(!op.id.equals(((ItemCustody)item).theslave$operacion()))throw new IllegalStateException("Botin de otra operacion");
                        ((ItemCustody)item).theslave$custodiar(op.id);
                    }
                    reconciliarTumba(nivelTumba(server,op),op);
                    List<CompletableFuture<CompoundTag>> escrituras=chunks.stream().map(c -> EntityDurability.guardar(sl,c)).toList();
                    CompletableFuture.allOf(escrituras.toArray(CompletableFuture[]::new)).whenComplete((nada,ex) -> {
                        if(epoca!=sesion)return;
                        if(ex!=null) { fallo(server,op,ex);return; }
                        try {
                            verificarBotin(op,escrituras.stream().map(CompletableFuture::join).toList());
                            op.confirmada=true;try { guardar(op); }catch(RuntimeException e) { op.confirmada=false;throw e; }
                            limpiarConfirmada(server,op);
                        }catch(RuntimeException e) { fallo(server,op,e); }
                    });
                }catch(RuntimeException ex) { fallo(server,op,ex); }
            });
        }catch(RuntimeException ex) { fallo(server,op,ex); }
    }
    private static ChunkPos chunkDe(CompoundTag tag) {
        var pos=tag.getListOrEmpty("Pos");return ChunkPos.containing(BlockPos.containing(pos.getDoubleOr(0,0),pos.getDoubleOr(1,0),pos.getDoubleOr(2,0)));
    }
    public static void verificarBotin(Operacion op,List<CompoundTag> chunks) {
        Map<UUID,CompoundTag> entidades=new HashMap<>();
        for(var chunk:chunks)for(var tag:chunk.getListOrEmpty("Entities").compoundStream().toList())tag.read("UUID",UUIDUtil.CODEC).ifPresent(id -> {
            if(entidades.putIfAbsent(id,tag)!=null)throw new IllegalStateException("UUID repetido en ACK de entidades");
        });
        for(String encoded:op.botin) {
            var esperado=RitualDurable.decodificar(encoded);var actual=entidades.get(esperado.read("UUID",UUIDUtil.CODEC).orElseThrow());
            if(actual==null || !op.id.toString().equals(actual.getStringOr("TheSlaveBotinOperacion","")) || !actual.getBooleanOr("TheSlaveBotinCustodia",false)
                    || !Objects.equals(esperado.get("Item"),actual.get("Item")))throw new IllegalStateException("El ACK no contiene todo el botin en custodia");
        }
        if(op.tipo==Tipo.LIBERAR) {
            var actual=entidades.get(op.cuerpo);
            if(actual==null || actual.getBooleanOr("TieneDueno",true) || !op.id.toString().equals(actual.getStringOr("TheSlaveRitualOperacion","")))throw new IllegalStateException("El ACK no confirma la liberacion del cuerpo");
            var esperado=RitualDurable.decodificar(op.cuerpoDespues);
            for(String key:List.of("Mochila","Ref","equipment"))if(!Objects.equals(esperado.get(key),actual.get(key)))throw new IllegalStateException("Inventario del cuerpo liberado incompleto");
        }
    }
    private static void reconciliarTumba(ServerLevel sl,Operacion op) {
        if(sl==null || !registroListo || op.tumba==null || !dimensionTumba(op).equals(sl.dimension().identifier().toString()))return;
        BlockPos pos=BlockPos.of(op.tumba);if(!sl.hasChunkAt(pos))return;
        if(op.tipo==Tipo.RECUPERAR_CUSTODIA) {
            if(op.confirmada && sl.getBlockEntity(pos) instanceof TumbaBlockEntity be && op.dueno.equals(be.dueno)
                    && op.vinculo.equals(be.vinculoId()) && be.muertes==op.muertes) { be.custodia=0;be.setChanged(); }
            return;
        }
        var ficha=Registro.vinculo(op.dueno,op.vinculo);
        boolean misma=ficha!=null && ficha.muertes==op.muertes;
        boolean conservar=op.tipo==Tipo.MUERTE && misma && ficha.estado==Registro.Estado.DIFUNTO;
        if(conservar) {
            if(sl.getBlockEntity(pos) instanceof TumbaBlockEntity actual) {
                if(!op.dueno.equals(actual.dueno) || !op.vinculo.equals(actual.vinculoId()) || actual.muertes!=op.muertes)return;
            } else {
                var estado=sl.getBlockState(pos);
                if((!estado.isAir() && !estado.canBeReplaced()) || !estado.getFluidState().isEmpty()
                        || !sl.getBlockState(pos.below()).isFaceSturdy(sl,pos.below(),Direction.UP))return;
                sl.setBlock(pos,ModBlocks.TUMBA.defaultBlockState(),3);
            }
            if(sl.getBlockEntity(pos) instanceof TumbaBlockEntity be) { be.inicializar(op.dueno,op.vinculo,op.nombre,op.variante,op.dia,op.causa,op.muertes);be.operacionRitual(op.id); }
        } else if(sl.getBlockEntity(pos) instanceof TumbaBlockEntity be && op.dueno.equals(be.dueno)
                && op.vinculo.equals(be.vinculoId()) && be.muertes==op.muertes) {
            // Solo desaparece la generacion consumida: jamas la tumba de una muerte posterior.
            sl.removeBlock(pos,false);
        }
    }
    /** Un COMMIT nunca vuelve a pagar, crear cuerpos ni recrear botin ya recogido. */
    public static boolean custodiaAjena(boolean activa,UUID confirmada,UUID actual) {
        return activa&&!RitualDurable.custodiaPropia(confirmada,actual);
    }
    private static void limpiarConfirmada(MinecraftServer server,Operacion op) {
        if(!op.confirmada)return;
        try {
            ServerLevel sl=nivel(server,op);
            Hogares.confirmarRetorno(op.dueno,op.vinculo,op.muertes);
            reconciliarTumba(nivelTumba(server,op),op);
            if(sl!=null) {
                for(UUID id:idsBotin(op)) {
                    if(EntityDurability.buscar(sl,id) instanceof ItemEntity item && ((ItemCustody)item).theslave$custodiado())((ItemCustody)item).theslave$liberar();
                }
                if(op.tipo==Tipo.LIBERAR && EntityDurability.buscar(sl,op.cuerpo) instanceof EsclavoEntity e
                        &&RitualDurable.custodiaPropia(op.id,e.operacionRitual()))e.liberarCustodiaRitual();
            }
        }catch(RuntimeException ex) { TheSlaveMod.LOGGER.error("Limpieza de muerte/retiro {} pendiente; su COMMIT se conserva",op.id,ex); }
        finally {
            try { indexarConfirmada(op);compactar(op);PENDIENTES.remove(op.dueno,op); }
            finally { CerrojosJugador.desbloquear(op.dueno,op.id);ACTIVAS.remove(op.id); }
        }
    }
    private static void fallo(MinecraftServer server,Operacion op,Throwable error) {
        if(op.confirmada) { limpiarConfirmada(server,op);return; }
        ACTIVAS.remove(op.id);TheSlaveMod.LOGGER.error("Muerte/retiro {} pendiente; botin bajo custodia",op.id,error);
        var p=server.getPlayerList().getPlayer(op.dueno);
        if(p!=null)p.connection.disconnect(Component.literal("TheSlave: muerte o retiro bajo custodia. Vuelve a entrar para recuperar la operacion."));
    }
}
