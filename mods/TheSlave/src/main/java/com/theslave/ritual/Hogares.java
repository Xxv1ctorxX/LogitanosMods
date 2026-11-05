package com.theslave.ritual;

import com.google.gson.*;
import com.theslave.Registro;
import com.theslave.TheSlaveMod;
import com.theslave.block.LechoBlock;
import com.theslave.block.LechoBlockEntity;
import com.theslave.block.TumbaBlockEntity;
import com.theslave.persistencia.*;
import com.theslave.registry.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelResource;

/** Hogar canonico por vinculo; portabilidad y extraccion de almas son transacciones durables. */
public final class Hogares {
    public static final class Hogar {
        public UUID dueno,vinculo;
        public String dimension;
        public Long pie;
        public int frente;
        public boolean lechoEntregado;
        public UUID reciboLecho;
        public Map<Integer,UUID> extracciones=new HashMap<>();
    }
    public static final class Operacion {
        public int esquema=1;
        public UUID id,dueno;
        public boolean confirmada;
        public Hogar antes,despues;
        public JsonArray inventarioAntes,inventarioDespues,cuenta,cuentaDespues;
        public Integer generacionRenuncia;
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String,Hogar> HOGARES=new LinkedHashMap<>();
    private static final Map<UUID,Operacion> OPERACIONES=new LinkedHashMap<>();
    private record Chunk(String dimension,long posicion) {}
    private record Posicion(String dimension,long posicion) {}
    /** Indice real de las dos mitades; las consultas nunca recorren casas de otros chunks. */
    public static final class Indice {
        private final Map<Posicion,String> posiciones=new HashMap<>();
        private final Map<Chunk,Set<String>> chunks=new HashMap<>();
        public void poner(Hogar h) {
            if(h.pie==null)return;String id=clave(h.dueno,h.vinculo);
            for(BlockPos pos:List.of(BlockPos.of(h.pie),cabeza(h))) {
                String previo=posiciones.get(new Posicion(h.dimension,pos.asLong()));
                if(previo!=null && !previo.equals(id))throw new IllegalStateException("Dos hogares ocupan el mismo bloque");
            }
            for(BlockPos pos:List.of(BlockPos.of(h.pie),cabeza(h))) {
                posiciones.put(new Posicion(h.dimension,pos.asLong()),id);
                chunks.computeIfAbsent(new Chunk(h.dimension,ChunkPos.containing(pos).pack()),k -> new HashSet<>()).add(id);
            }
        }
        public void quitar(Hogar h) {
            if(h==null || h.pie==null)return;String id=clave(h.dueno,h.vinculo);
            for(BlockPos pos:List.of(BlockPos.of(h.pie),cabeza(h))) {
                posiciones.remove(new Posicion(h.dimension,pos.asLong()),id);
                var key=new Chunk(h.dimension,ChunkPos.containing(pos).pack());var ids=chunks.get(key);
                if(ids!=null) { ids.remove(id);if(ids.isEmpty())chunks.remove(key); }
            }
        }
        public String en(String dimension,BlockPos pos) { return posiciones.get(new Posicion(dimension,pos.asLong())); }
        public Set<String> enChunk(String dimension,ChunkPos chunk) { return Set.copyOf(chunks.getOrDefault(new Chunk(dimension,chunk.pack()),Set.of())); }
        public void limpiar() { posiciones.clear();chunks.clear(); }
    }
    private static final Indice INDICE=new Indice();
    private static final Map<String,Hogar> RETIROS=new HashMap<>();
    private static final Map<Chunk,Set<String>> RETIROS_CHUNK=new HashMap<>();
    private static final Map<String,Long> RENUNCIAS=new LinkedHashMap<>(128,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Long> e) { return size()>1024; }
    };
    private static final Map<UUID,Long> ULTIMA_ENTREGA=new HashMap<>();
    private static final Set<UUID> SIN_HUECO_LECHO=new HashSet<>();
    private static Path archivo,diario;
    private static MinecraftServer servidor;
    private Hogares() {}
    private static String clave(UUID dueno,UUID vinculo) { return dueno+":"+vinculo; }
    private static Hogar copia(Hogar h) { return GSON.fromJson(GSON.toJson(h),Hogar.class); }
    private static Hogar ficha(UUID dueno,UUID vinculo) {
        Hogar existente=HOGARES.get(clave(dueno,vinculo));
        if(existente!=null)return copia(existente);
        Hogar h=new Hogar();h.dueno=dueno;h.vinculo=vinculo;return h;
    }
    public static ItemStack crearLecho(UUID dueno,UUID vinculo) {
        ItemStack item=new ItemStack(ModBlocks.LECHO_CENIZA);
        item.set(ModComponents.PACTO,new ModComponents.Pacto(dueno,vinculo,0));return item;
    }
    /** null mientras el lecho sea portatil, o si la plaza ya fue liberada. */
    public static GlobalPos hogar(UUID dueno,UUID vinculo) {
        if(dueno==null || vinculo==null)return null;
        var registro=Registro.vinculo(dueno,vinculo);
        if(registro==null || registro.estado==Registro.Estado.LIBERADO)return null;
        Hogar h=HOGARES.get(clave(dueno,vinculo));
        return h==null || h.pie==null?null:GlobalPos.of(ResourceKey.create(Registries.DIMENSION,Identifier.parse(h.dimension)),BlockPos.of(h.pie));
    }
    public static UUID tokenMuerte(UUID dueno,UUID vinculo,int generacion) {
        UUID token=MuerteDurable.tokenMuerte(dueno,vinculo,generacion);
        return token!=null?token:UUID.nameUUIDFromBytes(("theslave:alma:"+dueno+":"+vinculo+":"+generacion).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static boolean almaEnTumba(UUID dueno,UUID vinculo,int generacion) {
        var v=Registro.vinculo(dueno,vinculo);
        var h=HOGARES.get(clave(dueno,vinculo));
        return v!=null && v.estado==Registro.Estado.DIFUNTO && v.muertes==generacion
            && (h==null || !h.extracciones.containsKey(generacion));
    }
    public static Registro.Vinculo almaValida(ServerPlayer jugador,ItemStack item) {
        if(!item.is(ModItems.ALMA_VINCULADA))return null;
        var alma=item.get(ModComponents.ALMA_VINCULADA);
        if(alma==null || !jugador.getUUID().equals(alma.dueno()))return null;
        var v=Registro.vinculo(alma.dueno(),alma.vinculo());
        var h=HOGARES.get(clave(alma.dueno(),alma.vinculo()));
        UUID entrega=h==null?null:h.extracciones.get(alma.generacion());
        boolean confirmada=entrega!=null && TransaccionJugador.confirmada(jugador.level().getServer(),alma.dueno(),entrega);
        return validarAlma(alma,jugador.getUUID(),v,tokenMuerte(alma.dueno(),alma.vinculo(),alma.generacion()),confirmada)?v:null;
    }
    public static boolean validarAlma(ModComponents.AlmaVinculada alma,UUID jugador,Registro.Vinculo v,UUID token,boolean entregaConfirmada) {
        return alma!=null && v!=null && alma.dueno().equals(jugador) && alma.vinculo().equals(v.id)
            && v.estado==Registro.Estado.DIFUNTO && v.muertes==alma.generacion() && v.fusionado==alma.fusionado()
            && alma.token().equals(token) && entregaConfirmada;
    }
    public static void confirmarRetorno(UUID dueno,UUID vinculo,int generacion) {
        Hogar h=HOGARES.get(clave(dueno,vinculo));if(h!=null && servidor!=null)reconciliar(h);
    }
    public static void muerte(UUID dueno,UUID vinculo,int generacion) { confirmarRetorno(dueno,vinculo,generacion); }

    public static void registrar() {
        ServerLifecycleEvents.SERVER_STARTING.register(Hogares::cargar);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { HOGARES.values().forEach(Hogares::reconciliar);RETIROS.values().forEach(Hogares::retirarFisico); });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { servidor=null;archivo=null;diario=null;HOGARES.clear();OPERACIONES.clear();RENUNCIAS.clear();ULTIMA_ENTREGA.clear();SIN_HUECO_LECHO.clear();INDICE.limpiar();RETIROS.clear();RETIROS_CHUNK.clear(); });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            for(Operacion op:OPERACIONES.values())if(!op.confirmada && op.dueno.equals(handler.player.getUUID())) {
                CerrojosJugador.bloquear(op.dueno,op.id);completar(handler.player,op);return;
            }
            entregarLechoLegado(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server) -> { ULTIMA_ENTREGA.remove(handler.player.getUUID());SIN_HUECO_LECHO.remove(handler.player.getUUID()); });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if(server.overworld().getGameTime()%200==0)for(var jugador:server.getPlayerList().getPlayers())entregarLechoLegado(jugador);
        });
        ServerChunkEvents.CHUNK_LOAD.register((nivel,chunk,nuevo) -> {
            String dim=nivel.dimension().identifier().toString();
            for(String id:INDICE.enChunk(dim,chunk.getPos()))reconciliar(HOGARES.get(id));
            for(String id:RETIROS_CHUNK.getOrDefault(new Chunk(dim,chunk.getPos().pack()),Set.of()))retirarFisico(RETIROS.get(id));
        });
    }
    private static void cargar(MinecraftServer server) {
        servidor=server;HOGARES.clear();OPERACIONES.clear();RENUNCIAS.clear();ULTIMA_ENTREGA.clear();SIN_HUECO_LECHO.clear();
        INDICE.limpiar();RETIROS.clear();RETIROS_CHUNK.clear();
        archivo=server.getWorldPath(LevelResource.ROOT).resolve("theslave_hogares.json");
        diario=server.getWorldPath(LevelResource.ROOT).resolve("theslave_hogares_operaciones");
        if(Files.exists(archivo)) {
            var root=ArchivoDurable.leer(archivo).getAsJsonObject();
            if(root.get("esquema").getAsInt()!=1)throw new IllegalStateException("Formato de hogares desconocido");
            for(var json:root.getAsJsonArray("hogares")) {
                Hogar h=GSON.fromJson(json,Hogar.class);validar(h);
                if(HOGARES.putIfAbsent(clave(h.dueno,h.vinculo),h)!=null)throw new IllegalStateException("Hogar duplicado");
            }
        }
        if(Files.exists(diario))try(var files=Files.list(diario)) {
            Set<UUID> pendientes=new HashSet<>();
            var iterator=files.filter(p -> p.getFileName().toString().endsWith(".json")).iterator();
            while(iterator.hasNext()) {
                Path path=iterator.next();
                Operacion op=GSON.fromJson(ArchivoDurable.leer(path),Operacion.class);validar(op);
                if(!path.getFileName().toString().equals(op.id+".json"))throw new IllegalStateException("Operacion de hogar duplicada");
                if(op.confirmada)indexarRetiro(op);
                else if(OPERACIONES.putIfAbsent(op.id,op)!=null)throw new IllegalStateException("Operacion de hogar duplicada");
                if(!op.confirmada && !pendientes.add(op.dueno))throw new IllegalStateException("Dos hogares pendientes del mismo jugador");
                if(!op.confirmada)CerrojosJugador.bloquear(op.dueno,op.id);
            }
        }catch(java.io.IOException ex) { throw new IllegalStateException("No se puede leer el diario de hogares",ex); }
        validarColeccion(new ArrayList<>(HOGARES.values()));
        HOGARES.values().forEach(INDICE::poner);
    }
    public static void validarColeccion(List<Hogar> hogares) {
        Set<String> identidades=new HashSet<>(),posiciones=new HashSet<>();
        for(Hogar h:hogares) {
            validar(h);
            if(!identidades.add(clave(h.dueno,h.vinculo)))throw new IllegalStateException("Hogar duplicado");
            if(h.pie!=null)for(BlockPos pos:List.of(BlockPos.of(h.pie),cabeza(h)))
                if(!posiciones.add(h.dimension+":"+pos.asLong()))throw new IllegalStateException("Dos hogares ocupan el mismo bloque");
        }
    }
    public static void validar(Hogar h) {
        if(h==null || h.dueno==null || h.vinculo==null || h.extracciones==null || h.frente<0 || h.frente>3
                || (h.pie!=null && h.dimension==null))throw new IllegalStateException("Hogar incompleto");
        if(h.dimension!=null)Identifier.parse(h.dimension);
        for(var recibo:h.extracciones.entrySet())if(recibo.getKey()==null || recibo.getKey()<1 || recibo.getValue()==null)throw new IllegalStateException("Recibo de alma invalido");
        if(h.reciboLecho!=null && !h.lechoEntregado)throw new IllegalStateException("Recibo de lecho sin entrega");
    }
    public static void validar(Operacion op) {
        if(op==null || op.esquema!=1 || op.id==null || op.dueno==null || op.inventarioAntes==null || op.inventarioDespues==null || op.cuenta==null)
            throw new IllegalStateException("Operacion de hogar incompleta");
        validar(op.antes);validar(op.despues);
        if(!op.dueno.equals(op.antes.dueno) || !op.dueno.equals(op.despues.dueno) || !op.antes.vinculo.equals(op.despues.vinculo))throw new IllegalStateException("Vinculo cambiado por una operacion de hogar");
        if(op.generacionRenuncia!=null && (op.generacionRenuncia<1 || op.cuentaDespues==null || op.despues.pie!=null))throw new IllegalStateException("Renuncia de hogar incompleta");
    }
    private static void guardar() {
        JsonObject root=new JsonObject();root.addProperty("esquema",1);root.add("hogares",GSON.toJsonTree(HOGARES.values()));ArchivoDurable.escribir(archivo,root);
    }
    private static void guardar(Operacion op) { ArchivoDurable.escribir(diario.resolve(op.id+".json"),GSON.toJsonTree(op)); }
    private static Direction frente(Hogar h) { return Direction.from2DDataValue(h.frente); }
    private static BlockPos cabeza(Hogar h) { return BlockPos.of(h.pie).relative(frente(h)); }
    private static ServerLevel nivel(Hogar h) { return servidor==null || h.dimension==null?null:servidor.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(h.dimension))); }
    private static boolean mismo(Hogar a,Hogar b) { return GSON.toJsonTree(a).equals(GSON.toJsonTree(b)); }

    public static boolean colocar(ServerPlayer p,ItemStack item,BlockPos pie,Direction facing) {
        if(CerrojosJugador.ocupada(p.getUUID()))return false;
        var pacto=item.get(ModComponents.PACTO);
        var vinculo=pacto==null?null:Registro.vinculo(p.getUUID(),pacto.referencia());
        if(pacto==null || !p.getUUID().equals(pacto.dueno()) || vinculo==null || vinculo.estado==Registro.Estado.LIBERADO) { aviso(p,"theslave.lecho.ajeno");return false; }
        Hogar antes=ficha(p.getUUID(),pacto.referencia());
        if(antes.pie!=null) { aviso(p,"theslave.lecho.ya_colocado");return false; }
        ServerLevel sl=(ServerLevel)p.level();BlockPos head=pie.relative(facing);
        if(!p.mayUseItemAt(pie,Direction.UP,item) || !p.mayUseItemAt(head,Direction.UP,item)
                || !sl.mayInteract(p,pie) || !sl.mayInteract(p,head))return false;
        if(!disponible(sl,pie) || !disponible(sl,head) || ocupado(sl,pie) || ocupado(sl,head)) { aviso(p,"theslave.lecho.sin_espacio");return false; }
        var despues=copia(antes);despues.dimension=sl.dimension().identifier().toString();despues.pie=pie.asLong();despues.frente=facing.get2DDataValue();
        ItemStack[] inventario=copiar(p);boolean consumido=false;
        for(int i=0;i<inventario.length;i++)if(p.getInventory().getItem(i)==item) { inventario[i].shrink(1);consumido=true;break; }
        if(!consumido)return false;
        return ejecutar(p,antes,despues,inventario,"theslave.lecho.colocado");
    }
    private static boolean disponible(ServerLevel sl,BlockPos pos) {
        return sl.hasChunkAt(pos) && sl.hasChunkAt(pos.below()) && sl.getBlockState(pos).canBeReplaced()
            && sl.getFluidState(pos).isEmpty() && sl.getBlockState(pos.below()).isFaceSturdy(sl,pos.below(),Direction.UP);
    }
    private static boolean ocupado(ServerLevel sl,BlockPos pos) {
        return INDICE.en(sl.dimension().identifier().toString(),pos)!=null;
    }
    public static void usar(ServerPlayer p,BlockPos pos) {
        if(CerrojosJugador.ocupada(p.getUUID()))return;
        String id=INDICE.en(p.level().dimension().identifier().toString(),pos);
        Hogar h=id==null?null:copia(HOGARES.get(id));
        if(h==null || !p.getUUID().equals(h.dueno)) { aviso(p,"theslave.lecho.ajeno");return; }
        var ficha=Registro.vinculo(h.dueno,h.vinculo);
        if(p.isShiftKeyDown() && ficha!=null && ficha.estado==Registro.Estado.DIFUNTO
                && (p.getMainHandItem().is(ItemTags.PICKAXES) || p.getOffhandItem().is(ItemTags.PICKAXES))) {
            if(!confirmarRenuncia(h.dueno,h.vinculo,p.level().getGameTime())) { aviso(p,"theslave.tumba.seguro");return; }
            Ceremonias.cancelarVinculo((ServerLevel)p.level(),h.dueno,h.vinculo);
            var despues=copia(h);despues.pie=null;despues.dimension=null;
            ejecutarRenuncia(p,h,despues,ficha.muertes);return;
        }
        RENUNCIAS.remove(clave(h.dueno,h.vinculo));
        if(p.isShiftKeyDown()) {
            var inventario=copiar(p);if(!MuerteDurable.insertar(inventario,crearLecho(h.dueno,h.vinculo)).isEmpty()) { aviso(p,"theslave.notario.sin_hueco");return; }
            var despues=copia(h);despues.pie=null;despues.dimension=null;
            ejecutar(p,h,despues,inventario,"theslave.lecho.recogido");return;
        }
        if(ficha==null || ficha.estado!=Registro.Estado.DIFUNTO) { aviso(p,"theslave.lecho.hogar");return; }
        extraer(p,h,ficha);
    }
    /** Migracion de lapidas anteriores al lecho: el recibo de extraccion sigue siendo unico. */
    public static void extraerAlma(ServerPlayer p,TumbaBlockEntity tumba) {
        if(CerrojosJugador.ocupada(p.getUUID()) || !p.getUUID().equals(tumba.dueno))return;
        var v=Registro.vinculo(p.getUUID(),tumba.vinculoId());
        if(v==null || v.estado!=Registro.Estado.DIFUNTO || v.muertes!=tumba.muertes) { aviso(p,"theslave.tumba.antigua");return; }
        extraer(p,ficha(p.getUUID(),v.id),v);
    }
    private static void extraer(ServerPlayer p,Hogar h,Registro.Vinculo ficha) {
        UUID token=tokenMuerte(h.dueno,h.vinculo,ficha.muertes);
        if(token==null || h.extracciones.containsKey(ficha.muertes)) { aviso(p,"theslave.lecho.sin_alma");return; }
        ItemStack alma=new ItemStack(ModItems.ALMA_VINCULADA);
        alma.set(ModComponents.ALMA_VINCULADA,new ModComponents.AlmaVinculada(h.dueno,h.vinculo,ficha.muertes,token,ficha.fusionado));
        if(ficha.fusionado)alma.set(DataComponents.ITEM_MODEL,TheSlaveMod.id("alma_vinculada_fusionada"));
        if(!ficha.nombre.isBlank())alma.set(DataComponents.CUSTOM_NAME,Component.literal("Alma de "+ficha.nombre));
        var inventario=copiar(p);if(!MuerteDurable.insertar(inventario,alma).isEmpty()) { aviso(p,"theslave.notario.sin_hueco");return; }
        var despues=copia(h);UUID id=UUID.randomUUID();despues.extracciones.put(ficha.muertes,id);
        ejecutar(p,h,despues,inventario,"theslave.lecho.alma_extraida",id);
    }
    private static ItemStack[] copiar(ServerPlayer p) {
        ItemStack[] inv=new ItemStack[p.getInventory().getContainerSize()];for(int i=0;i<inv.length;i++)inv[i]=p.getInventory().getItem(i).copy();return inv;
    }
    private static boolean ejecutar(ServerPlayer p,Hogar antes,Hogar despues,ItemStack[] inv,String mensaje) { return ejecutar(p,antes,despues,inv,mensaje,UUID.randomUUID()); }
    private static boolean ejecutar(ServerPlayer p,Hogar antes,Hogar despues,ItemStack[] inv,String mensaje,UUID id) {
        // Colocar/recoger demuestra que ese lecho ya existia. El mismo WAL deja su recibo
        // para que la migracion posterior no entregue otro por un campo legado ausente.
        if(antes.pie!=null || despues.pie!=null)despues.lechoEntregado=true;
        if(despues.lechoEntregado && despues.reciboLecho==null)despues.reciboLecho=id;
        Operacion op=new Operacion();op.id=id;op.dueno=p.getUUID();op.antes=copia(antes);op.despues=copia(despues);
        op.inventarioAntes=CustodiaJugador.capturar(p);op.inventarioDespues=CustodiaJugador.codificar(p,inv);op.cuenta=Registro.cuentaOperacion(op.dueno);
        try {
            validar(op);guardar(op);OPERACIONES.put(op.id,op);CerrojosJugador.bloquear(op.dueno,op.id);
            completar(p,op);if(op.confirmada)aviso(p,mensaje);return true;
        }catch(RuntimeException ex) { fallo(p,op,ex);return false; }
    }
    /** El contrato no distingue versiones: solo el marcador de invocacion nueva prueba la entrega. */
    public static boolean necesitaLechoLegado(Registro.Vinculo v,Hogar h,boolean entregadoAlInvocar) {
        return v!=null && (v.estado==Registro.Estado.VIVO || v.estado==Registro.Estado.DIFUNTO)
            && !entregadoAlInvocar && (h==null || (!h.lechoEntregado && h.reciboLecho==null));
    }
    public static boolean debeEmitirLechoLegado(Registro.Vinculo v,Hogar h,boolean entregadoAlInvocar) {
        return necesitaLechoLegado(v,h,entregadoAlInvocar) && (h==null || h.pie==null);
    }
    private static void entregarLechoLegado(ServerPlayer p) {
        if(servidor==null || CerrojosJugador.ocupada(p.getUUID()))return;
        long tick=servidor.overworld().getGameTime();
        if(Objects.equals(ULTIMA_ENTREGA.get(p.getUUID()),tick))return;
        for(var v:Registro.vinculos(p.getUUID())) {
            Hogar antes=ficha(p.getUUID(),v.id);
            boolean nueva=RitualDurable.lechoEntregado(p.getUUID(),v.id);
            if(!necesitaLechoLegado(v,antes,nueva))continue;
            ULTIMA_ENTREGA.put(p.getUUID(),tick);
            ItemStack[] inventario=copiar(p);
            boolean emitir=debeEmitirLechoLegado(v,antes,nueva) && !tieneLecho(p,v.id);
            if(emitir && !MuerteDurable.insertar(inventario,crearLecho(p.getUUID(),v.id)).isEmpty()) {
                if(SIN_HUECO_LECHO.add(p.getUUID()))aviso(p,"theslave.notario.sin_hueco");
                return;
            }
            Hogar despues=copia(antes);despues.lechoEntregado=true;
            UUID id=UUID.randomUUID();despues.reciboLecho=id;
            ejecutar(p,antes,despues,inventario,emitir?"theslave.lecho.entregado":null,id);
            Hogar actual=HOGARES.get(clave(p.getUUID(),v.id));
            if(actual!=null && actual.lechoEntregado)SIN_HUECO_LECHO.remove(p.getUUID());
            return; // Como mucho un vinculo por propietario y tick, incluso JOIN+tick juntos.
        }
    }
    private static boolean tieneLecho(ServerPlayer p,UUID vinculo) {
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            var item=p.getInventory().getItem(i);var pacto=item.get(ModComponents.PACTO);
            if(item.is(ModBlocks.LECHO_CENIZA.asItem()) && pacto!=null && p.getUUID().equals(pacto.dueno()) && vinculo.equals(pacto.referencia()))return true;
        }
        return false;
    }
    public static boolean validarRenuncia(Registro.Vinculo v,int generacion,boolean cuentaAplicada) {
        return v!=null && v.muertes==generacion && (cuentaAplicada?v.estado==Registro.Estado.LIBERADO:v.estado==Registro.Estado.DIFUNTO);
    }
    /** Primer clic solo conserva una confirmacion efimera de cien ticks. */
    public static boolean confirmarRenuncia(UUID dueno,UUID vinculo,long tick) {
        String key=clave(dueno,vinculo);Long anterior=RENUNCIAS.get(key);
        if(anterior==null || tick<anterior || tick-anterior>100) { RENUNCIAS.put(key,tick);return false; }
        RENUNCIAS.remove(key);return true;
    }
    private static void ejecutarRenuncia(ServerPlayer p,Hogar antes,Hogar despues,int generacion) {
        Operacion op=new Operacion();op.id=UUID.randomUUID();op.dueno=p.getUUID();op.antes=copia(antes);op.despues=copia(despues);
        op.inventarioAntes=CustodiaJugador.capturar(p);op.inventarioDespues=op.inventarioAntes.deepCopy();op.cuenta=Registro.cuentaOperacion(op.dueno);
        op.cuentaDespues=MuerteDurable.cuentaDespues(op.cuenta,antes.vinculo,MuerteDurable.Tipo.ENTERRAR,"",0);op.generacionRenuncia=generacion;
        try {
            validar(op);guardar(op);OPERACIONES.put(op.id,op);CerrojosJugador.bloquear(op.dueno,op.id);completar(p,op);
            if(op.confirmada)aviso(p,"theslave.tumba.enterrada");
        }catch(RuntimeException ex) { fallo(p,op,ex); }
    }
    private static void completar(ServerPlayer p,Operacion op) {
        try {
            if(op.generacionRenuncia!=null && !validarRenuncia(Registro.vinculo(op.dueno,op.antes.vinculo),op.generacionRenuncia,Registro.operacionAplicada(op.id)))
                throw new IllegalStateException("La renuncia no corresponde a la muerte actual");
            if(!TransaccionJugador.confirmada(p.level().getServer(),op.dueno,op.id))TransaccionJugador.ejecutarBajoCustodia(p,"HOGAR",op.id,op.inventarioAntes,op.inventarioDespues,op.cuenta,op.cuentaDespues==null?op.cuenta:op.cuentaDespues);
            Hogar actual=ficha(op.dueno,op.despues.vinculo);
            if(!mismo(actual,op.antes) && !mismo(actual,op.despues))throw new IllegalStateException("El hogar cambio fuera de su transaccion");
            INDICE.quitar(actual);HOGARES.put(clave(op.dueno,op.despues.vinculo),copia(op.despues));INDICE.poner(op.despues);guardar();
            op.confirmada=true;try { guardar(op); }catch(RuntimeException ex) { op.confirmada=false;throw ex; }
            limpiar(op);
        }catch(RuntimeException ex) { fallo(p,op,ex); }
    }
    private static void limpiar(Operacion op) {
        if(!op.confirmada)return;
        try {
            if(op.antes.pie!=null && !Objects.equals(op.antes.pie,op.despues.pie))retirarFisico(op.antes);
            Hogar actual=HOGARES.get(clave(op.dueno,op.despues.vinculo));
            if(actual!=null)reconciliar(actual); // Nunca recrear la ubicacion de un COMMIT historico.
        }catch(RuntimeException ex) { TheSlaveMod.LOGGER.error("Limpieza visual del hogar pendiente; COMMIT conservado",ex); }
        finally {
            try { indexarRetiro(op);OPERACIONES.remove(op.id); }
            finally { CerrojosJugador.desbloquear(op.dueno,op.id); }
        }
    }
    /** Solo queda la huella antigua, no inventarios/cuentas ni mapas de almas por cada COMMIT. */
    private static void indexarRetiro(Operacion op) {
        if(!op.confirmada || op.antes.pie==null || Objects.equals(op.antes.pie,op.despues.pie))return;
        Hogar h=new Hogar();h.dueno=op.antes.dueno;h.vinculo=op.antes.vinculo;h.dimension=op.antes.dimension;h.pie=op.antes.pie;h.frente=op.antes.frente;
        String id=clave(h.dueno,h.vinculo)+":"+h.dimension+":"+h.pie+":"+h.frente;
        if(RETIROS.putIfAbsent(id,h)!=null)return;
        for(BlockPos pos:List.of(BlockPos.of(h.pie),cabeza(h)))RETIROS_CHUNK.computeIfAbsent(new Chunk(h.dimension,ChunkPos.containing(pos).pack()),k -> new HashSet<>()).add(id);
    }
    private static void retirarFisico(Hogar h) {
        ServerLevel sl=nivel(h);if(sl==null)return;
        for(BlockPos pos:List.of(BlockPos.of(h.pie),cabeza(h)))if(sl.hasChunkAt(pos) && !ocupado(sl,pos)
                && sl.getBlockEntity(pos) instanceof LechoBlockEntity be && h.dueno.equals(be.dueno) && h.vinculo.equals(be.vinculo))sl.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
    }
    private static void reconciliar(Hogar h) {
        if(h.pie==null)return;ServerLevel sl=nivel(h);if(sl==null)return;
        var ficha=Registro.vinculo(h.dueno,h.vinculo);if(ficha==null)return;
        boolean tumba=ficha.estado==Registro.Estado.DIFUNTO;
        BlockPos pie=BlockPos.of(h.pie),head=cabeza(h);
        if(!sl.hasChunkAt(pie) || !sl.hasChunkAt(head))return;
        for(BlockPos pos:List.of(pie,head)) {
            var estado=sl.getBlockState(pos);
            if(!estado.is(ModBlocks.LECHO_CENIZA) && !estado.canBeReplaced())return;
            if(sl.getBlockEntity(pos) instanceof LechoBlockEntity be && be.dueno!=null
                    && (!h.dueno.equals(be.dueno) || !h.vinculo.equals(be.vinculo)))return;
        }
        for(BlockPos pos:List.of(pie,head)) {
            var estado=ModBlocks.LECHO_CENIZA.defaultBlockState().setValue(LechoBlock.FACING,frente(h))
                .setValue(LechoBlock.PART,pos.equals(pie)?BedPart.FOOT:BedPart.HEAD).setValue(LechoBlock.GRAVE,tumba).setValue(LechoBlock.FUSIONADO,ficha.fusionado);
            if(!sl.getBlockState(pos).equals(estado))sl.setBlock(pos,estado,3);
            if(sl.getBlockEntity(pos) instanceof LechoBlockEntity be)be.vincular(h.dueno,h.vinculo);
        }
    }
    private static void aviso(ServerPlayer p,String key) { if(key!=null)p.sendOverlayMessage(Component.translatable(key)); }
    private static void fallo(ServerPlayer p,Operacion op,RuntimeException error) {
        if(op.confirmada) { limpiar(op);return; }
        TheSlaveMod.LOGGER.error("Hogar/alma {} pendiente de recuperar",op.id,error);
        p.connection.disconnect(Component.literal("TheSlave: hogar o alma bajo custodia. Vuelve a entrar para recuperar la operacion."));
    }
}
