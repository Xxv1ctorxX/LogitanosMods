package com.theslave.ritual;

import com.google.gson.*;
import com.theslave.Registro;
import com.theslave.TheSlaveMod;
import com.theslave.block.TumbaBlockEntity;
import com.theslave.entity.EsclavoEntity;
import com.theslave.persistencia.ArchivoDurable;
import com.theslave.persistencia.CustodiaJugador;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Confirmacion de rituales entre playerdata, Registro y entidades.
 * PREPARADA es una intencion durable de terminar, no una animacion reanudable.
 * El resultado permanece en custodia hasta comprobar escritura y sincronizacion real.
 */
public final class RitualDurable {
    public enum Estado { PREPARADA, CONFIRMADA, CANCELADA }
    public enum Tipo { INVOCACION, FUSION, SUPLICA, SUPLICA_FUSION }
    public static final class Operacion {
        public int esquema=1;
        public UUID id,dueno,resultado;
        public String dimension,snapshot;
        public int cx,cz;
        public List<UUID> originales=new ArrayList<>();
        public JsonArray inventarioAntes,inventarioDespues,cuentaAntes,cuentaDespues;
        public Estado estado=Estado.PREPARADA;
        public Tipo tipo;
        public boolean lechoEntregado;
        public Long tumba;
        public UUID difunto;
        public int muertes;
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    /** No retiene NBT, inventarios ni cuentas de operaciones ya confirmadas. */
    public record Huella(UUID id,UUID dueno,UUID resultado,UUID vinculo,Estado estado,Tipo tipo,
                         List<UUID> originales,List<UUID> vinculosAfectados,String dimension,Long tumba,UUID difunto,int muertes,boolean lechoEntregado) {}
    private record ChunkTumba(String dimension,long chunk) {}
    private static final Map<UUID,Operacion> OPERACIONES=new LinkedHashMap<>();
    private static final Map<UUID,Huella> RESULTADOS=new HashMap<>(), ORIGINALES=new HashMap<>();
    private static final Map<UUID,Map<UUID,Huella>> POR_DUENO=new HashMap<>();
    private static final Map<ChunkTumba,Map<UUID,Huella>> POR_TUMBA=new HashMap<>();
    private static final Set<String> LECHOS_ENTREGADOS=new HashSet<>();
    private static final Map<UUID,Long> ACTIVAS=new HashMap<>();
    private static long siguienteIntento;
    private static Path carpeta;
    private static int sesion;
    private RitualDurable() {}

    public static void registrar() {
        EntityDurability.alCargar(RitualDurable::alCargar);
        ServerLifecycleEvents.SERVER_STARTING.register(RitualDurable::cargar);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { sesion++;ACTIVAS.clear(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { OPERACIONES.clear();RESULTADOS.clear();ORIGINALES.clear();POR_DUENO.clear();POR_TUMBA.clear();LECHOS_ENTREGADOS.clear();carpeta=null; });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) -> {
            ServerPlayer p=handler.getPlayer();
            var propias=List.copyOf(POR_DUENO.getOrDefault(p.getUUID(),Map.of()).values());
            for(Huella op:propias) if(op.estado==Estado.CONFIRMADA&&CustodiaJugador.aplicado(p,op.id))
                liberarConfirmada(server,op,false);
            for(Huella h:propias) if(h.estado==Estado.PREPARADA) {
                Operacion op=OPERACIONES.get(h.id);
                CustodiaJugador.bloquear(p,op.id);
                continuar(server,p,op,true);
                break;
            }
        });
        ServerChunkEvents.CHUNK_LOAD.register((sl,chunk,nuevo) -> {
            var tumbas=POR_TUMBA.get(new ChunkTumba(sl.dimension().identifier().toString(),chunk.getPos().pack()));
            if(tumbas!=null)for(Huella op:tumbas.values())retirarTumba(sl,op);
        });
    }

    private static void cargar(MinecraftServer server) {
        sesion++;OPERACIONES.clear();RESULTADOS.clear();ORIGINALES.clear();POR_DUENO.clear();POR_TUMBA.clear();ACTIVAS.clear();LECHOS_ENTREGADOS.clear();
        carpeta=server.getWorldPath(LevelResource.ROOT).resolve("theslave_rituales");
        if(!Files.exists(carpeta))return;
        Set<UUID> leidas=new HashSet<>();
        try(var files=Files.list(carpeta)) {
            var iterator=files.filter(f -> f.getFileName().toString().endsWith(".json")).iterator();
            while(iterator.hasNext()) {
                Path file=iterator.next();
                Operacion op=GSON.fromJson(ArchivoDurable.leer(file),Operacion.class);
                validar(op);
                if(!file.getFileName().toString().equals(op.id+".json") || !leidas.add(op.id))
                    throw new IllegalStateException("Identidad WAL ritual duplicada");
                if(op.estado==Estado.PREPARADA)OPERACIONES.put(op.id,op);
                indexar(op);
            }
            validarPendientes(OPERACIONES.values());
            for(Operacion op:OPERACIONES.values())if(op.estado==Estado.PREPARADA)
                com.theslave.persistencia.CerrojosJugador.bloquear(op.dueno,op.id);
        } catch(IOException ex) { throw new IllegalStateException("No se puede leer la custodia de rituales",ex); }
    }
    public static void validarPendientes(Collection<Operacion> operaciones) {
        Set<UUID> duenos=new HashSet<>();
        for(Operacion op:operaciones)if(op.estado==Estado.PREPARADA&&!duenos.add(op.dueno))
            throw new IllegalStateException("Dos custodias rituales pendientes para el mismo jugador");
    }
    public static void validar(Operacion op) {
        if(op==null || op.esquema!=1 || op.id==null || op.dueno==null || op.resultado==null
                || op.dimension==null || op.snapshot==null || op.estado==null || op.originales==null
                || op.inventarioAntes==null || op.inventarioDespues==null || op.cuentaAntes==null || op.cuentaDespues==null
                || op.inventarioAntes.isEmpty() || op.inventarioAntes.size()!=op.inventarioDespues.size() || op.originales.contains(op.resultado)
                || new HashSet<>(op.originales).size()!=op.originales.size() || op.originales.contains(null)
                || (op.originales.size()!=0 && op.originales.size()!=2)
                || (op.tumba!=null&&op.difunto==null) || op.muertes<0
                || (op.tumba!=null&&!op.originales.isEmpty()))throw new IllegalStateException("WAL ritual incompleto");
        CompoundTag nbt=decodificar(op.snapshot);
        if(op.tipo==null)op.tipo=op.difunto!=null?(nbt.getIntOr("Variante2",-1)>=0?Tipo.SUPLICA_FUSION:Tipo.SUPLICA)
                :op.originales.isEmpty()?Tipo.INVOCACION:Tipo.FUSION;
        if((op.tipo==Tipo.FUSION&&op.originales.size()!=2)
                ||(op.tipo==Tipo.INVOCACION&&!op.originales.isEmpty())
                ||((op.tipo==Tipo.SUPLICA||op.tipo==Tipo.SUPLICA_FUSION)
                    &&(op.difunto==null||op.muertes<1||!op.originales.isEmpty()))
                ||((op.tipo==Tipo.INVOCACION||op.tipo==Tipo.FUSION)
                    &&(op.difunto!=null||op.tumba!=null||op.muertes!=0))
                ||(op.lechoEntregado&&op.tipo!=Tipo.INVOCACION))
            throw new IllegalStateException("Tipo de ritual sin identidad de alma");
        Identifier.parse(op.dimension);
        validarCuerpo(nbt,op);
    }
    private static void validarCuerpo(CompoundTag nbt,Operacion op) {
        if(!op.resultado.equals(nbt.read("UUID",UUIDUtil.CODEC).orElse(null))
                || !op.id.toString().equals(nbt.getStringOr("TheSlaveRitualOperacion",""))
                || !"theslave:esclavo".equals(nbt.getStringOr("id",""))
                || !op.dueno.toString().equals(nbt.getStringOr("Dueno",""))
                || !nbt.getBooleanOr("TheSlaveRitualCustodia",false)
                || nbt.getBooleanOr("TheSlaveRitualOriginal",true))
            throw new IllegalStateException("Snapshot ritual sin identidad estable");
        UUID vinculo;
        try { vinculo=UUID.fromString(nbt.getStringOr("Vinculo","")); }
        catch(IllegalArgumentException ex) {throw new IllegalStateException("Cuerpo sin vinculo valido",ex);}
        if((op.tipo==Tipo.SUPLICA||op.tipo==Tipo.SUPLICA_FUSION)&&!vinculo.equals(op.difunto))
            throw new IllegalStateException("El cuerpo no corresponde al alma presentada");
        boolean fusionado=nbt.getIntOr("Variante2",-1)>=0;
        if(fusionado!=(op.tipo==Tipo.FUSION||op.tipo==Tipo.SUPLICA_FUSION))
            throw new IllegalStateException("El aspecto guardado no corresponde al tipo del ritual");
        boolean asociado=false;
        for(JsonElement entry:op.cuentaDespues) {
            if(!entry.isJsonObject())continue;
            JsonObject v=entry.getAsJsonObject();
            if(v.has("id")&&vinculo.toString().equals(v.get("id").getAsString())
                    &&v.has("esclavo")&&op.resultado.toString().equals(v.get("esclavo").getAsString())
                    &&v.has("estado")&&"VIVO".equals(v.get("estado").getAsString()))asociado=true;
        }
        if(!asociado)throw new IllegalStateException("El cuerpo no corresponde a la cuenta resultante");
        ListTag pos=nbt.getListOrEmpty("Pos");
        if(pos.size()!=3)throw new IllegalStateException("Cuerpo sin posicion completa");
        double x=pos.getDoubleOr(0,Double.NaN),y=pos.getDoubleOr(1,Double.NaN),z=pos.getDoubleOr(2,Double.NaN);
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>30000000||Math.abs(z)>30000000
                || Math.floor(x/16)!=op.cx||Math.floor(z/16)!=op.cz)
            throw new IllegalStateException("Posicion del cuerpo fuera del chunk de custodia");
    }
    public static Huella resumir(Operacion op) {
        UUID vinculo=UUID.fromString(decodificar(op.snapshot).getStringOr("Vinculo",""));
        Set<UUID> afectados=new LinkedHashSet<>();afectados.add(vinculo);
        for(JsonElement entry:op.cuentaAntes)if(entry.isJsonObject()) {
            JsonObject ficha=entry.getAsJsonObject();
            if(ficha.has("id")&&ficha.has("esclavo")&&!ficha.get("esclavo").isJsonNull()
                    &&op.originales.contains(UUID.fromString(ficha.get("esclavo").getAsString())))
                afectados.add(UUID.fromString(ficha.get("id").getAsString()));
        }
        return new Huella(op.id,op.dueno,op.resultado,vinculo,op.estado,op.tipo,List.copyOf(op.originales),List.copyOf(afectados),op.dimension,op.tumba,op.difunto,op.muertes,op.lechoEntregado);
    }
    private static void indexar(Operacion completa) {
        Huella op=resumir(completa);
        if(op.estado==Estado.CANCELADA)return;
        registrarEntregaLecho(op);
        Huella anterior=RESULTADOS.get(op.resultado);
        if(anterior!=null&&!anterior.id.equals(op.id))throw new IllegalStateException("Resultado ritual duplicado");
        for(UUID original:op.originales) {
            Huella retirada=ORIGINALES.get(original);
            if(retirada!=null&&!retirada.id.equals(op.id))throw new IllegalStateException("Participante retirado por dos rituales");
        }
        RESULTADOS.put(op.resultado,op);for(UUID original:op.originales)ORIGINALES.put(original,op);
        POR_DUENO.computeIfAbsent(op.dueno,id->new LinkedHashMap<>()).put(op.id,op);
        if(op.estado==Estado.CONFIRMADA&&op.tumba!=null)
            POR_TUMBA.computeIfAbsent(new ChunkTumba(op.dimension,ChunkPos.containing(BlockPos.of(op.tumba)).pack()),key->new LinkedHashMap<>()).put(op.id,op);
    }
    public static boolean lechoEntregado(UUID dueno,UUID vinculo) {
        return LECHOS_ENTREGADOS.contains(dueno+":"+vinculo);
    }
    private static void registrarEntregaLecho(Huella op) {
        if(op.estado==Estado.CONFIRMADA&&op.lechoEntregado)
            LECHOS_ENTREGADOS.add(op.dueno+":"+op.vinculo);
    }
    private static void guardar(Operacion op) {
        guardarEstado(op,op.estado);
    }
    private static void guardarEstado(Operacion op,Estado estado) {
        JsonObject json=GSON.toJsonTree(op).getAsJsonObject();json.addProperty("estado",estado.name());
        ArchivoDurable.escribir(carpeta.resolve(op.id+".json"),json);
    }
    public static String codificar(CompoundTag nbt) {
        try(var bytes=new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(nbt,bytes);
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch(IOException ex) { throw new IllegalStateException("No se puede guardar el cuerpo ritual",ex); }
    }
    public static CompoundTag decodificar(String encoded) {
        try(var bytes=new ByteArrayInputStream(Base64.getDecoder().decode(encoded))) {
            return NbtIo.readCompressed(bytes,NbtAccounter.create(16L*1024*1024));
        } catch(IOException | IllegalArgumentException ex) { throw new IllegalStateException("Snapshot ritual ilegible",ex); }
    }

    /** El candidato aun no debe haberse incorporado al mundo. Los pagos son snapshots, no mutaciones. */
    public static boolean iniciar(ServerPlayer p,EsclavoEntity candidato,List<EsclavoEntity> originales,
                                  TumbaBlockEntity tumba,JsonArray cuentaDespues,JsonArray inventarioDespues) {
        Tipo tipo=tumba==null?(originales.isEmpty()?Tipo.INVOCACION:Tipo.FUSION)
                :candidato.fusionado()?Tipo.SUPLICA_FUSION:Tipo.SUPLICA;
        return iniciar(p,candidato,originales,tumba,tipo,tumba==null?null:tumba.vinculoId(),
                tumba==null?0:tumba.muertes,cuentaDespues,inventarioDespues);
    }
    public static boolean iniciar(ServerPlayer p,EsclavoEntity candidato,List<EsclavoEntity> originales,
                                  Tipo tipo,UUID alma,int generacion,JsonArray cuentaDespues,JsonArray inventarioDespues) {
        return iniciar(p,candidato,originales,null,tipo,alma,generacion,cuentaDespues,inventarioDespues);
    }
    private static boolean iniciar(ServerPlayer p,EsclavoEntity candidato,List<EsclavoEntity> originales,
                                   TumbaBlockEntity tumba,Tipo tipo,UUID alma,int generacion,
                                   JsonArray cuentaDespues,JsonArray inventarioDespues) {
        if(carpeta==null || CustodiaJugador.ocupada(p.getUUID()))return false;
        Operacion op=new Operacion();op.id=UUID.randomUUID();op.dueno=p.getUUID();op.resultado=candidato.getUUID();
        op.dimension=p.level().dimension().identifier().toString();op.tipo=tipo;op.difunto=alma;op.muertes=generacion;
        op.lechoEntregado=tipo==Tipo.INVOCACION;
        ChunkPos chunk=candidato.chunkPosition();op.cx=chunk.x();op.cz=chunk.z();
        op.inventarioAntes=CustodiaJugador.capturar(p);op.inventarioDespues=inventarioDespues.deepCopy();
        op.cuentaAntes=Registro.cuentaOperacion(op.dueno);op.cuentaDespues=cuentaDespues.deepCopy();
        originales.forEach(e -> op.originales.add(e.getUUID()));
        if(tumba!=null) { op.tumba=tumba.getBlockPos().asLong();op.difunto=tumba.vinculoId();op.muertes=tumba.muertes; }
        candidato.marcarOperacion(op.id,false);
        op.snapshot=codificar(EntityDurability.snapshot(candidato));
        try { validar(op);guardar(op); }
        catch(RuntimeException ex) { TheSlaveMod.LOGGER.error("No se pudo preparar el ritual; se conservan los ingredientes",ex);return false; }
        OPERACIONES.put(op.id,op);indexar(op);
        CustodiaJugador.bloquear(p,op.id);
        ServerLevel sl=(ServerLevel)p.level();
        if(!sl.addFreshEntity(candidato)) {
            try {
                guardarEstado(op,Estado.CANCELADA);op.estado=Estado.CANCELADA;
                RESULTADOS.remove(op.resultado);op.originales.forEach(ORIGINALES::remove);
                var propias=POR_DUENO.get(op.dueno);
                if(propias!=null) {propias.remove(op.id);if(propias.isEmpty())POR_DUENO.remove(op.dueno);}
                OPERACIONES.remove(op.id);
                CustodiaJugador.desbloquear(p.getUUID(),op.id);
                return false;
            } catch(RuntimeException ex) { fallo(p,op,ex);return true; }
        }
        originales.forEach(e -> e.marcarOperacion(op.id,true));
        if(tumba!=null)tumba.operacionRitual(op.id);
        continuar(sl.getServer(),p,op,false);
        return true;
    }

    /** Puertos del motor real: tests y produccion usan las mismas fronteras de confirmacion. */
    public interface Participantes {
        void comprobarSesion();
        CompletableFuture<Void> cargarRestaurar();
        void aplicarRegistro();
        void aplicarJugador();
        void guardarJugador();
        CompletableFuture<CompoundTag> guardarEntidad();
        void persistirEstado(Estado estado);
        void liberar();
    }
    public static boolean intentoVigente(int epoca,int sesionActual,long intento,Long activo,Object esperado,Object conectado) {
        return epoca==sesionActual&&Objects.equals(activo,intento)&&esperado!=null&&esperado==conectado;
    }
    public static CompletableFuture<Void> confirmar(Operacion op,Participantes participantes) {
        try {
            validar(op);participantes.comprobarSesion();
            if(op.estado==Estado.CANCELADA)throw new IllegalStateException("Un ritual cancelado no puede confirmarse");
            if(op.estado==Estado.CONFIRMADA) {participantes.liberar();return CompletableFuture.completedFuture(null);}
            return participantes.cargarRestaurar().thenCompose(v -> {
                participantes.comprobarSesion();
                participantes.aplicarRegistro();
                participantes.comprobarSesion();
                participantes.aplicarJugador();
                participantes.comprobarSesion();
                participantes.guardarJugador();
                participantes.comprobarSesion();
                return participantes.guardarEntidad();
            }).thenAccept(nbt -> {
                participantes.comprobarSesion();
                verificarResultado(nbt,op);
                participantes.comprobarSesion();
                participantes.persistirEstado(Estado.CONFIRMADA);
                op.estado=Estado.CONFIRMADA;
                participantes.comprobarSesion();
                participantes.liberar();
            });
        } catch(RuntimeException ex) {return CompletableFuture.failedFuture(ex);}
    }
    private static void continuar(MinecraftServer server,ServerPlayer p,Operacion op,boolean recuperando) {
        if(!recuperando&&ACTIVAS.containsKey(op.id))return;
        long intento=++siguienteIntento;ACTIVAS.put(op.id,intento);
        int epoca=sesion;
        ServerLevel sl=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(op.dimension)));
        if(sl==null) {fallo(p,op,new IllegalStateException("Dimension ritual ausente"));return;}
        confirmar(op,new Participantes() {
            public void comprobarSesion() {
                if(!intentoVigente(epoca,sesion,intento,ACTIVAS.get(op.id),p,server.getPlayerList().getPlayer(op.dueno)))
                    throw new IllegalStateException("El intento ritual ya no pertenece al jugador conectado");
            }
            public CompletableFuture<Void> cargarRestaurar() {
                return EntityDurability.cargarEntidades(sl,new ChunkPos(op.cx,op.cz)).thenRun(() -> {
                    comprobarSesion();
                    var existente=EntityDurability.buscar(sl,op.resultado);
                    EsclavoEntity resultado;
                    if(existente==null) {
                        resultado=EntityDurability.restaurar(sl,decodificar(op.snapshot));
                        if(!sl.addFreshEntity(resultado))throw new IllegalStateException("El cuerpo ritual no puede restaurarse");
                    } else {
                        if(!(existente instanceof EsclavoEntity esclavo))throw new IllegalStateException("UUID ritual ocupado por otra entidad");
                        resultado=esclavo;
                        if(!op.id.equals(resultado.operacionRitual()))throw new IllegalStateException("UUID ritual ocupado por otra entidad");
                    }
                    resultado.marcarOperacion(op.id,false);
                });
            }
            public void aplicarRegistro() {Registro.aplicarOperacion(op.id,op.dueno,op.cuentaAntes,op.cuentaDespues);}
            public void aplicarJugador() {
                if(recuperando)CustodiaJugador.recuperar(p,op.id,op.inventarioAntes,op.inventarioDespues);
                else CustodiaJugador.aplicar(p,op.id,op.inventarioAntes,op.inventarioDespues);
            }
            public void guardarJugador() {CustodiaJugador.guardarConfirmado(p,op.id);}
            public CompletableFuture<CompoundTag> guardarEntidad() {return EntityDurability.guardar(sl,new ChunkPos(op.cx,op.cz));}
            public void persistirEstado(Estado estado) {guardarEstado(op,estado);}
            public void liberar() {liberarConfirmada(server,op,true);}
        }).whenComplete((v,error) -> {
            if(error!=null&&intentoVigente(epoca,sesion,intento,ACTIVAS.get(op.id),p,server.getPlayerList().getPlayer(op.dueno)))fallo(p,op,error);
        });
    }
    private static void liberarConfirmada(MinecraftServer server,Operacion op,boolean nombrar) {
        indexar(op);OPERACIONES.remove(op.id);
        liberarConfirmada(server,RESULTADOS.get(op.resultado),nombrar);
    }
    private static void liberarConfirmada(MinecraftServer server,Huella op,boolean nombrar) {
        if(op.estado!=Estado.CONFIRMADA)throw new IllegalStateException("Liberacion sin COMMIT durable");
        registrarEntregaLecho(op);
        ServerLevel sl=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(op.dimension)));
        if(sl==null)throw new IllegalStateException("Dimension ritual ausente");
        for(UUID original:op.originales) {
            var anterior=EntityDurability.buscar(sl,original);if(anterior!=null)anterior.discard();
        }
        retirarTumba(sl,op);
        for(UUID afectado:op.vinculosAfectados) {
            var ficha=Registro.vinculo(op.dueno,afectado);
            if(ficha!=null)Hogares.confirmarRetorno(op.dueno,afectado,ficha.muertes);
        }
        var resultado=EntityDurability.buscar(sl,op.resultado);
        // Un resultado antiguo puede ser participante de un ritual posterior: su tombstone tiene prioridad.
        if(resultado instanceof EsclavoEntity esclavo)alCargar(esclavo);
        CustodiaJugador.desbloquear(op.dueno,op.id);ACTIVAS.remove(op.id);
        ServerPlayer online=server.getPlayerList().getPlayer(op.dueno);
        if(nombrar&&(op.tipo==Tipo.INVOCACION||op.tipo==Tipo.FUSION)&&online!=null&&resultado instanceof EsclavoEntity esclavo&&esclavo.isAlive()) {
            if(op.tipo==Tipo.INVOCACION)online.sendSystemMessage(Component.translatable("theslave.lecho.agradecimiento"));
            try {net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(online,new com.theslave.net.Payloads.AbrirNombre(esclavo.getId()));}
            catch(RuntimeException ex) {TheSlaveMod.LOGGER.warn("Ritual confirmado; no se pudo abrir el nombre",ex);}
        }
    }
    /** El ACK debe contener el UUID y la custodia del resultado, no solo una escritura cualquiera. */
    public static void verificarResultado(CompoundTag chunk,Operacion op) {
        int encontrados=0;
        CompoundTag esperado=decodificar(op.snapshot);
        for(CompoundTag nbt:chunk.getListOrEmpty("Entities").compoundStream().toList()) {
            if(op.resultado.equals(nbt.read("UUID",UUIDUtil.CODEC).orElse(null))) {
                validarCuerpo(nbt,op);encontrados++;
                for(String key:List.of("Mochila","Ref","equipment","EnergiaFundicion","Dueno","Vinculo","Variante","Variante2","CustomName","custom_name"))
                    if(!Objects.equals(esperado.get(key),nbt.get(key)))throw new IllegalStateException("El ACK omite o altera el campo del resultado: "+key);
            }
        }
        if(encontrados!=1)throw new IllegalStateException("La escritura no contiene un unico resultado ritual");
    }
    public enum DecisionCarga { NINGUNA, RETENER_ORIGINAL, RETENER_RESULTADO, DESCARTAR, LIBERAR }
    public static boolean custodiaPropia(UUID confirmada,UUID actual) {
        return confirmada!=null&&confirmada.equals(actual);
    }
    public static DecisionCarga decidirCarga(Estado estado,boolean original) {
        if(estado==Estado.CANCELADA)return DecisionCarga.NINGUNA;
        if(estado==Estado.CONFIRMADA)return original?DecisionCarga.DESCARTAR:DecisionCarga.LIBERAR;
        return original?DecisionCarga.RETENER_ORIGINAL:DecisionCarga.RETENER_RESULTADO;
    }
    private static void aplicarCarga(EsclavoEntity e,Huella op,boolean original) {
        switch(decidirCarga(op.estado,original)) {
            case DESCARTAR -> e.discard();
            case LIBERAR -> {if(custodiaPropia(op.id,e.operacionRitual())) {e.aplicarVinculo(Registro.vinculo(op.dueno,e.vinculo()));e.liberarCustodiaRitual();}}
            case RETENER_ORIGINAL -> e.marcarOperacion(op.id,true);
            case RETENER_RESULTADO -> e.marcarOperacion(op.id,false);
            case NINGUNA -> {}
        }
    }
    private static void alCargar(EsclavoEntity e) {
        Huella retirada=ORIGINALES.get(e.getUUID());
        if(retirada!=null) {
            aplicarCarga(e,retirada,true);
            return;
        }
        Huella resultado=RESULTADOS.get(e.getUUID());
        if(resultado!=null) {
            aplicarCarga(e,resultado,false);
        }
    }
    private static void retirarTumba(ServerLevel sl,Huella op) {
        if(op.tumba==null)return;
        BlockPos pos=BlockPos.of(op.tumba);
        if(!sl.hasChunkAt(pos))return;
        if(sl.getBlockEntity(pos) instanceof TumbaBlockEntity be && op.estado==Estado.CONFIRMADA&&op.dueno.equals(be.dueno)
                &&op.difunto.equals(be.vinculoId())&&op.muertes==be.muertes) {
            var flor=Blocks.POPPY.defaultBlockState();
            sl.setBlock(pos,flor.canSurvive(sl,pos)?flor:Blocks.AIR.defaultBlockState(),3);
        }
    }
    public static boolean coincideTumba(Operacion op,UUID dueno,UUID difunto,int muertes) {
        return op.estado==Estado.CONFIRMADA&&op.tumba!=null&&op.dueno.equals(dueno)&&op.difunto.equals(difunto)&&op.muertes==muertes;
    }
    private static void fallo(ServerPlayer p,Operacion op,Throwable error) {
        ACTIVAS.remove(op.id);
        TheSlaveMod.LOGGER.error("Ritual {} pendiente de recuperacion; no se repite el pago ni se libera la custodia",op.id,error);
        p.connection.disconnect(Component.literal("El ritual ha quedado bajo custodia. Vuelve a entrar para recuperar la operación; si persiste, avisa al administrador."));
    }
}
