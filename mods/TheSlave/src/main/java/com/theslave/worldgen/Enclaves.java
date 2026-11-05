package com.theslave.worldgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.theslave.TheSlaveMod;
import com.theslave.registry.ModBlocks;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.nio.file.StandardCopyOption;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Ubicación de los enclaves de los Notarios de Ceniza. Es determinista: el mundo se divide en regiones amplias y cada
 * una decide su enclave a partir de la semilla (sin depender de quién pase cerca ni de qué chunks existan). Cuando el
 * chunk del enclave se genera por primera vez se construye; lo descubierto se guarda para la brújula y para que no
 * vuelva a levantarse.
 */
public final class Enclaves {
	public enum Tipo {
		CUEVA, SUPERFICIE, CASA, REFUGIO
	}

	/** Entrada del enclave: celda local (2, 7) del chunk del enclave en todos los tipos. */
	public static final int ENTRADA_X = 2;
	public static final int ENTRADA_Z = 7;

	public record Sitio(Tipo tipo, int chunkX, int chunkZ) {
		public int x() {
			return chunkX * 16 + ENTRADA_X;
		}

		public int z() {
			return chunkZ * 16 + ENTRADA_Z;
		}
	}

	/** Lo ya construido: dimensión, chunk y altura real de la entrada. */
	public static final class Hallado {
		public String dim;
		public int cx;
		public int cz;
		public String tipo;
		public int y;
        /** Nunca se persiste: un indice JSON no demuestra que su chunk llegara a guardarse. */
        public transient Verificacion verificacion=Verificacion.PENDIENTE;
	}
    public enum Verificacion { PENDIENTE, VERIFICADO, HUERFANO }
    public static void confirmarVerificacion(Hallado hallado,boolean existeFisicamente) {
        hallado.verificacion=existeFisicamente?Verificacion.VERIFICADO:Verificacion.HUERFANO;
    }

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LISTA = new TypeToken<List<Hallado>>() {
	}.getType();
	private static final Map<String, List<Sitio>> CACHE = new java.util.LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<Sitio>> entry) { return size() > 128; }
    };
	private static final List<Hallado> HALLADOS = new ArrayList<>();
    private record ClaveHallado(String dimension,int x,int z) {}
    private static final Map<ClaveHallado,Hallado> POR_CHUNK=new HashMap<>();
    private static long revision;
    public static long versionIndice() { return revision; }
	private static Path archivo;
    private static int sesion;
    private static final Map<String, CupoBusqueda> BUSQUEDAS = new HashMap<>();
    private static final Map<String, Long> PROXIMO_RASTREO = new HashMap<>();
    private static final Map<String, CupoBusqueda> ZONAS = new java.util.LinkedHashMap<>(64,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,CupoBusqueda> e) { return size()>64; }
    };

    public enum Rastreo { BUSCANDO, ESPERA, SIN_RASTRO }

    /** Una region por uso y como mucho sus dos candidatos; nueve regiones por zona. */
    public static final class CupoBusqueda {
        private final int rx, rz;
        private int siguiente;
        private boolean activa;
        private long disponible;
        public CupoBusqueda(int rx,int rz) { this.rx=rx;this.rz=rz; }
        public boolean mismaZona(int x,int z) { return rx==x && rz==z; }
        public boolean activa() { return activa; }
        public boolean agotada() { return siguiente>=9; }
        public int reservar(long tick) {
            if(activa || tick<disponible || agotada())return -1;
            activa=true; disponible=tick+200;
            return siguiente++;
        }
        public void terminar() { activa=false; }
    }

    private static final int[][] REGIONES={{0,0},{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}};

	private Enclaves() {
	}

	public static void registrar() {
		ServerLifecycleEvents.SERVER_STARTING.register(Enclaves::cargar);
        // Invalida callbacks antes de que empiece a cerrarse el gestor de chunks.
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> sesion++);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			CACHE.clear();
            BUSQUEDAS.clear();
            PROXIMO_RASTREO.clear();
            ZONAS.clear();
            sesion++;
			HALLADOS.clear();
            POR_CHUNK.clear();revision++;
			archivo = null;
		});
		ServerChunkEvents.CHUNK_LOAD.register(Enclaves::alCargar);
	}

	// ---------------------------------------------------------------- persistencia

	private static void cargar(MinecraftServer server) {
        sesion++;
        BUSQUEDAS.clear();
        PROXIMO_RASTREO.clear();
        ZONAS.clear();
		CACHE.clear();
		HALLADOS.clear();
        POR_CHUNK.clear();revision++;
		archivo = server.getWorldPath(LevelResource.ROOT).resolve("theslave_enclaves.json");
		if (Files.exists(archivo)) {
			try (Reader r = Files.newBufferedReader(archivo)) {
				List<Hallado> l = GSON.fromJson(r, LISTA);
                if (l == null) throw new IllegalArgumentException("Indice de enclaves vacio o mal formado");
                java.util.Set<String> unicos=new java.util.HashSet<>();
                for(Hallado h:l) {
                    if(h==null || h.dim==null || h.tipo==null || h.y<Integer.MIN_VALUE+1)
                        throw new IllegalArgumentException("Entrada de enclave incompleta");
                    Tipo.valueOf(h.tipo);
                    h.verificacion=Verificacion.PENDIENTE;
                    if(!unicos.add(h.dim+":"+h.cx+":"+h.cz)) throw new IllegalArgumentException("Enclave duplicado");
                }
                HALLADOS.addAll(l);
                for(Hallado h:l)POR_CHUNK.put(new ClaveHallado(h.dim,h.cx,h.cz),h);
			} catch (IOException | RuntimeException e) {
				throw new IllegalStateException("theslave_enclaves.json ilegible; se conserva intacto y no se regenera sobre las tiendas", e);
			}
		}
	}

	private static void guardar() {
        if(archivo==null) return;
        Path tmp=archivo.resolveSibling(archivo.getFileName()+".tmp");
        try {
            try(Writer w=Files.newBufferedWriter(tmp)){GSON.toJson(HALLADOS,LISTA,w);}
            try { Files.move(tmp,archivo,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(java.nio.file.AtomicMoveNotSupportedException e){ Files.move(tmp,archivo,StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException e){throw new IllegalStateException("No se puede guardar el indice de enclaves",e);}
    }

	public static Hallado hallado(ServerLevel sl, Sitio s) {
        return POR_CHUNK.get(new ClaveHallado(sl.dimension().identifier().toString(),s.chunkX(),s.chunkZ()));
	}
    public static boolean coincideDestino(Hallado h,String dimension,BlockPos pos) {
        return h!=null && h.verificacion==Verificacion.VERIFICADO && h.dim.equals(dimension)
            && pos.getX()==h.cx*16+ENTRADA_X && pos.getZ()==h.cz*16+ENTRADA_Z && pos.getY()==h.y;
    }
    public static boolean esDestinoVerificado(ServerLevel sl,GlobalPos destino) {
        if(!destino.dimension().equals(sl.dimension()))return false;
        var pos=destino.pos();String dim=sl.dimension().identifier().toString();
        return coincideDestino(POR_CHUNK.get(new ClaveHallado(dim,pos.getX()>>4,pos.getZ()>>4)),dim,pos);
    }

	// ---------------------------------------------------------------- reparto por regiones

	private static boolean soportada(ServerLevel sl) {
		return sl.dimension() == Level.OVERWORLD || sl.dimension() == Level.NETHER;
	}

	private static int region(ServerLevel sl) {
		return sl.dimension() == Level.NETHER ? 62 : 140;
	}

	private static int margen(ServerLevel sl) {
		return sl.dimension() == Level.NETHER ? 15 : 35;
	}

	/** Enclaves de una región (0, 1 o 2: el corriente y, si hay un bosque de roble oscuro, la Casa bajo las Raíces). */
	public static List<Sitio> sitios(ServerLevel sl, int rx, int rz) {
		String clave = sl.dimension().identifier() + ":" + rx + ":" + rz;
		List<Sitio> hechos = CACHE.get(clave);
		if (hechos != null) {
			return hechos;
		}
		List<Sitio> res = new ArrayList<>();
		if (soportada(sl)) {
			int reg = region(sl);
			int mar = margen(sl);
			SplittableRandom r = new SplittableRandom(sl.getSeed() * 31L + rx * 341873128712L + rz * 132897987541L
					+ (sl.dimension() == Level.NETHER ? 7919L : 104729L));
			BlockPos spawn = sl.getRespawnData().pos();
			for (int intento = 0; intento < 6; intento++) {
				int cx = rx * reg + mar + r.nextInt(reg - 2 * mar);
				int cz = rz * reg + mar + r.nextInt(reg - 2 * mar);
				double dx = cx * 16.0 - spawn.getX();
				double dz = cz * 16.0 - spawn.getZ();
				boolean overworld = sl.dimension() == Level.OVERWORLD;
				if (overworld && dx * dx + dz * dz < 1500.0 * 1500.0) {
					continue;
				}
				Tipo t = !overworld ? Tipo.REFUGIO : r.nextInt(100) < 80 ? Tipo.CUEVA : Tipo.SUPERFICIE;
				res.add(new Sitio(t, cx, cz));
				break;
			}
			if (sl.dimension() == Level.OVERWORLD) {
				Sitio casa = buscarCasa(sl, rx, rz, reg, spawn);
				if (casa != null) {
					res.add(casa);
				}
			}
		}
		CACHE.put(clave, res);
		return res;
	}

	/** Un bosque de roble oscuro dentro de la región, fuera del spawn; si no hay ninguno, esta región no tiene Casa. */
	private static Sitio buscarCasa(ServerLevel sl, int rx, int rz, int reg, BlockPos spawn) {
		try {
            // 26.3: el resolver consulta el ruido de biomas sin pedir chunks ni crear
            // un cache de volumen. Sesenta y cuatro muestras sustituyen el barrido 3D.
            var resolver=sl.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(sl.getChunkSource().randomState());
            for(BlockPos p:muestrasCasa(rx,rz,reg)) {
                double dx=p.getX()-spawn.getX(),dz=p.getZ()-spawn.getZ();
                if(dx*dx+dz*dz<1500.0*1500.0)continue;
                if(resolver.getNoiseBiome(p.getX()>>2,16,p.getZ()>>2).is(Biomes.DARK_FOREST))return new Sitio(Tipo.CASA,p.getX()>>4,p.getZ()>>4);
            }
            return null;
		} catch (RuntimeException e) {
			TheSlaveMod.LOGGER.warn("No se pudo buscar un bosque de roble oscuro para la región {} {}", rx, rz, e);
			return null;
		}
	}
    /** Presupuesto fijo, determinista e independiente del tamaño/altura del mundo. */
    public static List<BlockPos> muestrasCasa(int rx,int rz,int reg) {
        if(reg<8)throw new IllegalArgumentException("Region demasiado pequena");
        List<BlockPos> muestras=new ArrayList<>(64);
        for(int x=0;x<8;x++)for(int z=0;z<8;z++)
            muestras.add(new BlockPos((rx*reg+(2*x+1)*reg/16)*16+ENTRADA_X,64,(rz*reg+(2*z+1)*reg/16)*16+ENTRADA_Z));
        double cx=(rx*reg+reg/2.0)*16,cz=(rz*reg+reg/2.0)*16;
        muestras.sort(java.util.Comparator.comparingDouble(p -> Math.pow(p.getX()-cx,2)+Math.pow(p.getZ()-cz,2)));
        return List.copyOf(muestras);
    }

	/** Solo entradas construidas y verificadas. Nunca dirige a una prediccion de terreno. */
    public static Sitio masCercano(ServerLevel sl, BlockPos desde) {
        if(!soportada(sl))return null;
        Hallado h=elegirHallado(HALLADOS,sl.dimension().identifier().toString(),desde);
        return h==null?null:new Sitio(Tipo.valueOf(h.tipo),h.cx,h.cz);
    }

    /** Seleccion pura: solo destinos reales de la dimension solicitada, sin consultar ni generar terreno. */
    public static Hallado elegirHallado(List<Hallado> candidatos, String dimension, BlockPos desde) {
        return elegir(candidatos,dimension,desde,Verificacion.VERIFICADO);
    }
    /** Un rastro del indice anterior solo merece prioridad si esta antes del destino ya comprobado. */
    public static Hallado elegirPendiente(List<Hallado> candidatos,String dimension,BlockPos desde) {
        Hallado pendiente=elegir(candidatos,dimension,desde,Verificacion.PENDIENTE);
        Hallado confirmado=elegirHallado(candidatos,dimension,desde);
        return pendiente!=null && (confirmado==null || distancia(pendiente,desde)<distancia(confirmado,desde))?pendiente:null;
    }
    public static boolean necesitaVerificar(ServerLevel sl,BlockPos desde) {
        return elegirPendiente(HALLADOS,sl.dimension().identifier().toString(),desde)!=null;
    }
    private static double distancia(Hallado h,BlockPos desde) {
        double dx=h.cx*16.0+ENTRADA_X-desde.getX(),dz=h.cz*16.0+ENTRADA_Z-desde.getZ();return dx*dx+dz*dz;
    }
    private static Hallado elegir(List<Hallado> candidatos,String dimension,BlockPos desde,Verificacion estado) {
        Hallado mejor=null; double distancia=Double.MAX_VALUE;
        for(Hallado h:candidatos) {
            if(h==null || !dimension.equals(h.dim) || h.tipo==null || h.verificacion!=estado)continue;
            try { Tipo.valueOf(h.tipo); } catch(IllegalArgumentException e){continue;}
            double d=distancia(h,desde);
            if(d<distancia) { mejor=h;distancia=d; }
        }
        return mejor;
    }

    /**
     * Descubre terreno solo al usar la brujula: una region y dos candidatos como maximo,
     * una tarea por dimension. La aguja lee exclusivamente estructuras ya construidas.
     */
    public static Rastreo buscar(ServerLevel sl,BlockPos origen,Consumer<Boolean> terminado) {
        if(!soportada(sl))return Rastreo.SIN_RASTRO;
        String dim=sl.dimension().identifier().toString();
        CupoBusqueda activa=BUSQUEDAS.get(dim);
        if(activa!=null && activa.activa())return Rastreo.BUSCANDO;
        long tick=sl.getGameTime();
        if(tick<PROXIMO_RASTREO.getOrDefault(dim,Long.MIN_VALUE))return Rastreo.ESPERA;
        int rx=Math.floorDiv(origen.getX()>>4,region(sl));
        int rz=Math.floorDiv(origen.getZ()>>4,region(sl));
        Hallado pendiente=elegirPendiente(HALLADOS,dim,origen);
        if(pendiente!=null) {
            CupoBusqueda verificacion=new CupoBusqueda(rx,rz);verificacion.reservar(tick);
            PROXIMO_RASTREO.put(dim,tick+200);BUSQUEDAS.put(dim,verificacion);
            verificar(sl,origen.immutable(),List.of(new Sitio(Tipo.valueOf(pendiente.tipo),pendiente.cx,pendiente.cz)),0,verificacion,sesion,terminado);
            return Rastreo.BUSCANDO;
        }
        CupoBusqueda cupo=ZONAS.computeIfAbsent(dim+":"+rx+":"+rz,k -> new CupoBusqueda(rx,rz));
        if(cupo.agotada())return Rastreo.SIN_RASTRO;
        int indice=cupo.reservar(tick);
        if(indice<0)return Rastreo.ESPERA;
        PROXIMO_RASTREO.put(dim,tick+200);
        BUSQUEDAS.put(dim,cupo);
        int[] offset=REGIONES[indice];
        List<Sitio> candidatos=List.copyOf(sitios(sl,rx+offset[0],rz+offset[1]));
        if(candidatos.isEmpty()) { cupo.terminar();return Rastreo.SIN_RASTRO; }
        verificar(sl,origen.immutable(),candidatos,0,cupo,sesion,terminado);
        return Rastreo.BUSCANDO;
    }

    private static void verificar(ServerLevel sl,BlockPos origen,List<Sitio> candidatos,int indice,
                                  CupoBusqueda cupo,int epoca,Consumer<Boolean> terminado) {
        if(epoca!=sesion)return;
        if(indice>=Math.min(2,candidatos.size()) || (indice>0 && masCercano(sl,origen)!=null)) {
            cupo.terminar();
            terminado.accept(masCercano(sl,origen)!=null);
            return;
        }
        Sitio s=candidatos.get(indice);
        var fuente=sl.getChunkSource();
        var servidor=sl.getServer();
        // Verificado en 26.3: la rama fuera del hilo principal encola la peticion;
        // la rama principal de getChunkFuture usa managedBlock y bloquearia el tick.
        // El ejecutor auxiliar no lee mundo, estructuras ni el indice.
        CompletableFuture.supplyAsync(() -> fuente.getChunkFuture(s.chunkX(),s.chunkZ(),ChunkStatus.FULL,true))
            .thenCompose(futuro -> futuro)
            .whenComplete((resultado,error) -> servidor.execute(() -> {
                if(epoca!=sesion)return;
                if(error!=null) {
                    cupo.terminar();
                    TheSlaveMod.LOGGER.warn("No se pudo verificar un rastro de ceniza en {} {}",s.chunkX(),s.chunkZ(),error);
                    terminado.accept(false);
                    return;
                }
                try {
                    // Una peticion FULL sobre un chunk ya cargado no vuelve a emitir CHUNK_LOAD.
                    // Se comprueba fisicamente tambien aqui, sin construir ni reparar terreno viejo.
                    var chunk=fuente.getChunkNow(s.chunkX(),s.chunkZ());
                    if(resultado!=null && resultado.isSuccess() && chunk!=null)validarIndice(sl,chunk);
                    verificar(sl,origen,candidatos,indice+1,cupo,epoca,terminado);
                }catch(RuntimeException ex) {
                    cupo.terminar();TheSlaveMod.LOGGER.error("No se pudo confirmar fisicamente el enclave",ex);terminado.accept(false);
                }
            }));
    }

	// ---------------------------------------------------------------- generación

	private static void alCargar(ServerLevel sl, LevelChunk chunk, boolean nuevo) {
		if (!soportada(sl)) {
			return;
		}
        validarIndice(sl,chunk);
        if(!nuevo)return;
		ChunkPos cp = chunk.getPos();
		int reg = region(sl);
		int rx = Math.floorDiv(cp.x(), reg);
		int rz = Math.floorDiv(cp.z(), reg);
		for (Sitio s : sitios(sl, rx, rz)) {
			if (s.chunkX() == cp.x() && s.chunkZ() == cp.z()) {
				construir(sl, chunk, s);
			}
		}
	}

    private static void validarIndice(ServerLevel sl,LevelChunk chunk) {
        String dim=sl.dimension().identifier().toString();ChunkPos pos=chunk.getPos();
        var key=new ClaveHallado(dim,pos.x(),pos.z());Hallado h=POR_CHUNK.get(key);
        if(h==null)return;
        var anterior=h.verificacion;confirmarVerificacion(h,existeFisicamente(sl,chunk,h));
        if(h.verificacion!=anterior)revision++;
        if(h.verificacion==Verificacion.HUERFANO) {
            HALLADOS.remove(h);POR_CHUNK.remove(key);
            TheSlaveMod.LOGGER.warn("Se descarta un rastro sin enclave fisico en {} {} {}",dim,h.cx,h.cz);
            guardar();
        }
    }
    private static boolean existeFisicamente(ServerLevel sl,LevelChunk chunk,Hallado h) {
        Tipo tipo=Tipo.valueOf(h.tipo);
        int suelo=(tipo==Tipo.SUPERFICIE || tipo==Tipo.CASA)?h.y-7:h.y-1;
        if(suelo<=sl.getMinY() || suelo+5>=sl.getMaxY())return false;
        int x=chunk.getPos().getMinBlockX(),z=chunk.getPos().getMinBlockZ();
        // Firma de la sala real, colocada por Estructuras.interior, mas su acceso.
        int[][] puntos={{9,suelo+1,6},{12,suelo+1,7},{14,suelo+2,8},{10,suelo+2,10}};
        String[] bloques={"piedra_registro","mesa_notario","archivo_condenas","raiz_ceniza"};
        for(int i=0;i<puntos.length;i++) {
            int[] p=puntos[i];
            if(!chunk.getBlockState(new BlockPos(x+p[0],p[1],z+p[2])).is(ModBlocks.EXTRA.get(bloques[i])))return false;
        }
        // Un jugador puede tapar temporalmente la puerta: eso no convierte una tienda real
        // en huerfana. Se contrasta el suelo propio de la entrada, no que siga vacia de bloques.
        if(tipo==Tipo.SUPERFICIE || tipo==Tipo.CASA)
            return chunk.getBlockState(new BlockPos(x+ENTRADA_X,h.y-1,z+ENTRADA_Z)).is(Blocks.STONE_BRICK_STAIRS);
        if(tipo==Tipo.REFUGIO)
            return chunk.getBlockState(new BlockPos(x+ENTRADA_X,h.y-1,z+ENTRADA_Z)).is(ModBlocks.EXTRA.get("netherrack_infectada"));
        return chunk.getBlockState(new BlockPos(x+3,suelo,z+ENTRADA_Z)).is(ModBlocks.EXTRA.get(suelo<0?"pizarra_infectada":"piedra_infectada"));
    }

	/** Construye el enclave en su chunk (también lo usa el comando de operador) y lo anota. */
	public static void construir(ServerLevel sl, LevelChunk chunk, Sitio s) {
		if (hallado(sl, s) != null) {
			return;
		}
        // La estructura solo se genera en un emplazamiento valido y separado del resto.
        if (sl.dimension() == Level.OVERWORLD) {
            BlockPos spawn=sl.getRespawnData().pos();
            double dx=s.x()-spawn.getX(),dz=s.z()-spawn.getZ();
            if(dx*dx+dz*dz<1500.0*1500.0)return;
        }
        String dimension=sl.dimension().identifier().toString();
        double minimo=sl.dimension()==Level.NETHER?1000:2000;
        for(Hallado previo:HALLADOS) {
            if(!dimension.equals(previo.dim))continue;
            double dx=(s.chunkX()-previo.cx)*16.0,dz=(s.chunkZ()-previo.cz)*16.0;
            if(dx*dx+dz*dz<minimo*minimo)return;
        }
        if(s.tipo()==Tipo.CASA && !sl.getBiome(new BlockPos(s.x(),64,s.z())).is(Biomes.DARK_FOREST)) return;
		int y = Estructuras.construir(sl, chunk, s.tipo());
        if(y==Integer.MIN_VALUE)return;
		Hallado h = new Hallado();
		h.dim = sl.dimension().identifier().toString();
		h.cx = s.chunkX();
		h.cz = s.chunkZ();
		h.tipo = s.tipo().name();
		h.y = y;
        confirmarVerificacion(h,true);
		HALLADOS.add(h);
        POR_CHUNK.put(new ClaveHallado(h.dim,h.cx,h.cz),h);revision++;
		guardar();
	}
}
