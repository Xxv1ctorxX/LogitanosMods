package com.theslave;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Vínculos de servidumbre de cada jugador (hasta {@link Config#maxPorJugador}). Un vínculo ocupa plaza mientras esté
 * reservado (contrato sin usar), vivo o difunto con tumba pendiente; no depende de que el chunk del esclavo esté cargado.
 *
 * <p>Se guarda en {@code theslave_vinculos.json} (esquema 2). Si solo existe el antiguo {@code theslave_registro.json}
 * (una ficha por dueño) se migra una vez, dejando una copia {@code theslave_registro.json.pre-v2}.
 */
public final class Registro {
	public static final int ESQUEMA = 2;

	public enum Estado {
		RESERVADO, VIVO, DIFUNTO, LIBERADO
	}

	public static final class Vinculo {
		public UUID id = UUID.randomUUID();
		public Estado estado = Estado.VIVO;
		public String nombre = "";
		public int variante;
		public int muertes;
		public UUID esclavo;
		/** Contrato de adquisición que lo reservó (null en vínculos migrados). */
		public UUID contrato;
		public boolean fusionado;
		/** Grado del contrato (0, 1, 2) con el que se reservó. */
		public int grado;
		/** Méritos de las cinco aptitudes (ver {@link Aptitudes}); null en vínculos anteriores, se generan al primer uso. */
		public float[] merito;
		/** Aspecto de la otra mitad en un fusionado, -1 si no lo es. */
		public int variante2 = -1;
	}

	private static final class Archivo {
		int esquema = ESQUEMA;
		Map<UUID, List<Vinculo>> cuentas = new HashMap<>();
        Map<UUID, String> operaciones = new HashMap<>();
        Map<UUID, UUID> recibosComerciales = new HashMap<>();
        transient boolean normalizado;
	}

	/** Formato antiguo: una ficha por dueño. */
	private static final class FichaAntigua {
		String estado = "vivo";
		String nombre = "";
		int variante;
		int muertes;
		UUID esclavo;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type TIPO_ANTIGUO = new TypeToken<Map<UUID, FichaAntigua>>() {
	}.getType();
	private static Archivo datos = new Archivo();
	private static Path archivo;

	private Registro() {
	}

	public static void cargar(MinecraftServer server) {
		cargarDesde(server.getWorldPath(LevelResource.ROOT));
	}

    private static void cargarDesde(Path raiz) {
        // Un arranque rechazado no debe habilitar el guardado de una cuenta vacia al detenerse.
        archivo = null;
        Path destino = raiz.resolve("theslave_vinculos.json");
		Path antiguo = raiz.resolve("theslave_registro.json");
		datos = new Archivo();
		if (Files.exists(destino)) {
			try (Reader r = Files.newBufferedReader(destino)) {
                datos = leerArchivo(r);
			} catch (IOException | RuntimeException e) {
                throw new IllegalStateException("No se puede leer theslave_vinculos.json. Se conserva intacto; restaura el registro antes de abrir el mundo.", e);
			}
		} else if (Files.exists(antiguo)) {
			archivo = destino;
            try { migrar(antiguo); }
            catch (RuntimeException fallo) { archivo = null; throw fallo; }
		}
        archivo = destino;
        if(datos.normalizado) {
            datos.normalizado=false;
            try { guardar(); }
            catch(RuntimeException fallo) { archivo=null;throw fallo; }
        }
	}

    /** Rechaza datos incompletos sin sustituirlos por cuentas vacias ni regenerar ids. */
    private static Archivo leerArchivo(Reader reader) {
        com.google.gson.JsonElement json = com.google.gson.JsonParser.parseReader(reader);
        if (!json.isJsonObject()) throw new IllegalArgumentException("Registro nulo o no es un objeto");
        var root = json.getAsJsonObject();
        if (!root.has("esquema") || root.get("esquema").getAsInt() != ESQUEMA
                || !root.has("cuentas") || !root.get("cuentas").isJsonObject())
            throw new IllegalArgumentException("Esquema de registro desconocido o incompleto");
        for (var account : root.getAsJsonObject("cuentas").entrySet()) {
            UUID.fromString(account.getKey());
            if (!account.getValue().isJsonArray()) throw new IllegalArgumentException("Cuenta sin lista de vinculos");
            for (var item : account.getValue().getAsJsonArray()) {
                if (!item.isJsonObject() || !item.getAsJsonObject().has("id") || !item.getAsJsonObject().has("estado"))
                    throw new IllegalArgumentException("Vinculo sin identidad o estado");
            }
        }
        Archivo result = GSON.fromJson(json, Archivo.class);
        if (result.operaciones == null) throw new IllegalArgumentException("Recibos de operaciones nulos");
        if(result.recibosComerciales==null)throw new IllegalArgumentException("Indice comercial nulo");
        for(var receipt:result.recibosComerciales.entrySet())
            if(receipt.getKey()==null || receipt.getValue()==null || !result.operaciones.containsKey(receipt.getValue()))
                throw new IllegalArgumentException("Indice comercial sin recibo");
        for (var receipt : result.operaciones.entrySet())
            if (receipt.getKey() == null || receipt.getValue() == null || receipt.getValue().isBlank())
                throw new IllegalArgumentException("Recibo de operacion invalido");
        java.util.Set<UUID> ids = new java.util.HashSet<>(), entities = new java.util.HashSet<>(), contracts = new java.util.HashSet<>();
        for (var account : result.cuentas.entrySet()) {
            int pending = 0;
            for (Vinculo v : account.getValue()) {
                if (v == null || v.id == null || v.estado == null || !ids.add(v.id)
                        || v.grado < 0 || v.grado > 2 || v.muertes < 0
                        || v.variante < 0 || v.variante >= 3 || v.variante2 < -1 || v.variante2 >= 3)
                    throw new IllegalArgumentException("Vinculo invalido o duplicado");
                if(v.estado==Estado.DIFUNTO && v.muertes==0) {v.muertes=1;result.normalizado=true;}
                if (v.estado == Estado.VIVO && v.esclavo != null && !entities.add(v.esclavo))
                    throw new IllegalArgumentException("Entidad asociada a varios vinculos");
                if (v.estado == Estado.RESERVADO) {
                    if (v.contrato == null || !contracts.add(v.contrato) || ++pending > 1)
                        throw new IllegalArgumentException("Reserva invalida o duplicada");
                }
                if (v.merito != null) {
                    if (v.merito.length != 5) throw new IllegalArgumentException("Aptitudes incompletas");
                    for (float value : v.merito) if (!Float.isFinite(value))
                        throw new IllegalArgumentException("Aptitud no finita");
                }
            }
        }
        return result;
    }

	private static void migrar(Path antiguo) {
		try (Reader r = Files.newBufferedReader(antiguo)) {
			Map<UUID, FichaAntigua> viejo = GSON.fromJson(r, TIPO_ANTIGUO);
            if (viejo == null) throw new IllegalArgumentException("Registro antiguo nulo");
			if (viejo != null) {
				for (Map.Entry<UUID, FichaAntigua> en : viejo.entrySet()) {
					FichaAntigua f = en.getValue();
                    if (en.getKey() == null || f == null || !("vivo".equals(f.estado) || "muerto".equals(f.estado))
                            || f.muertes<0 || f.variante<0 || f.variante>=3)
                        throw new IllegalArgumentException("Ficha antigua invalida");
					Vinculo v = new Vinculo();
					v.estado = "muerto".equals(f.estado) ? Estado.DIFUNTO : Estado.VIVO;
					v.nombre = f.nombre == null ? "" : f.nombre;
					v.variante = f.variante;
					v.muertes = v.estado==Estado.DIFUNTO?Math.max(1,f.muertes):f.muertes;
					v.esclavo = f.esclavo;
					datos.cuentas.computeIfAbsent(en.getKey(), k -> new ArrayList<>()).add(v);
				}
			}
			if (!Files.exists(antiguo.resolveSibling("theslave_registro.json.pre-v2")))
                Files.copy(antiguo, antiguo.resolveSibling("theslave_registro.json.pre-v2"));
			guardar();
			TheSlaveMod.LOGGER.info("Registro migrado al esquema {} ({} jugadores)", ESQUEMA, datos.cuentas.size());
		} catch (IOException | RuntimeException e) {
			TheSlaveMod.LOGGER.error("No se pudo migrar theslave_registro.json; se deja intacto", e);
			throw new IllegalStateException("La migracion del registro ha fallado; no se abre una cuenta vacia", e);
		}
	}

	public static void guardar() {
		if (archivo == null) {
			return;
		}
		Path tmp = archivo.resolveSibling(archivo.getFileName() + ".tmp");
		try (Writer w = Files.newBufferedWriter(tmp)) {
			GSON.toJson(datos, Archivo.class, w);
		} catch (IOException e) {
            throw new IllegalStateException("No se puede persistir el registro de vinculos", e);
		}
		try (FileChannel canal = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
            canal.force(true);
		} catch (IOException e) {
            throw new IllegalStateException("No se puede sincronizar el registro de vinculos", e);
        }
        try {
			Files.move(tmp, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
            // Un reemplazo no atomico puede perder el registro anterior durante una caida.
            throw new IllegalStateException("No se puede reemplazar atomicamente el registro de vinculos", e);
		}
	}

    /**
     * Revierte el registro en memoria si falla su escritura. No coordina playerdata ni chunks.
     * Tras una excepcion deben descartarse las referencias Vinculo anteriores y volver a consultarlas.
     */
    private static <T> T cambiar(java.util.function.Supplier<T> cambio) {
        Archivo anterior = GSON.fromJson(GSON.toJson(datos, Archivo.class), Archivo.class);
        try {
            T resultado = cambio.get();
            guardar();
            return resultado;
        } catch (RuntimeException fallo) {
            datos = anterior;
            throw fallo;
        }
    }

    /** Copia independiente de la cuenta para preparar un WAL antes de efectuar cambios. */
    public static com.google.gson.JsonArray cuentaOperacion(UUID dueno) {
        return GSON.toJsonTree(datos.cuentas.getOrDefault(dueno, List.of())).getAsJsonArray();
    }

    public static com.google.gson.JsonObject codificarVinculo(Vinculo vinculo) {
        return GSON.toJsonTree(vinculo).getAsJsonObject();
    }
    public static boolean operacionAplicada(UUID operacion) { return datos.operaciones.containsKey(operacion); }
    public static UUID reciboComercial(UUID dueno) { return datos.recibosComerciales.get(dueno); }

    /** Cuenta y recibo se reemplazan juntos en el mismo archivo; el reintento no repite efectos. */
    public static void aplicarOperacion(UUID operacion, UUID dueno, com.google.gson.JsonArray antes,
            com.google.gson.JsonArray despues) {
        aplicarOperacion(operacion,dueno,antes,despues,false,null);
    }
    public static void aplicarOperacionComercial(UUID operacion,UUID dueno,com.google.gson.JsonArray antes,
            com.google.gson.JsonArray despues,UUID anteriorConfirmado) {
        aplicarOperacion(operacion,dueno,antes,despues,true,anteriorConfirmado);
    }
    private static void aplicarOperacion(UUID operacion,UUID dueno,com.google.gson.JsonArray antes,
            com.google.gson.JsonArray despues,boolean comercial,UUID anteriorConfirmado) {
        java.util.Objects.requireNonNull(operacion); java.util.Objects.requireNonNull(dueno);
        String hash;
        try {
            byte[] encoded=(dueno+"|"+antes+"|"+despues).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(encoded));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        String previo=datos.operaciones.get(operacion);
        if (previo!=null) {
            if (!previo.equals(hash)) throw new IllegalStateException("Operacion de registro reutilizada con otros datos");
            return;
        }
        if (!cuentaOperacion(dueno).equals(antes)) throw new IllegalStateException("Cuenta cambiada durante operacion durable");
        UUID anterior=datos.recibosComerciales.get(dueno);
        if(comercial && anterior!=null && !anterior.equals(anteriorConfirmado))
            throw new IllegalStateException("No se puede sustituir un recibo comercial sin COMMIT comprobado");
        String receipt=hash;
        cambiar(() -> {
            List<Vinculo> nueva=GSON.fromJson(despues,new TypeToken<List<Vinculo>>() {}.getType());
            datos.cuentas.put(dueno,nueva);
            datos.operaciones.put(operacion,receipt);
            if(comercial) {
                if(anterior!=null)datos.operaciones.remove(anterior);
                datos.recibosComerciales.put(dueno,operacion);
            }
            datos=leerArchivo(new java.io.StringReader(GSON.toJson(datos,Archivo.class)));
            return null;
        });
    }

	private static List<Vinculo> cuenta(UUID dueno) {
		return datos.cuentas.computeIfAbsent(dueno, k -> new ArrayList<>());
	}

	public static List<Vinculo> vinculos(UUID dueno) {
		return List.copyOf(datos.cuentas.getOrDefault(dueno, List.of()));
	}

	public static Vinculo vinculo(UUID dueno, UUID id) {
		if (id == null) {
			return null;
		}
		for (Vinculo v : datos.cuentas.getOrDefault(dueno, List.of())) {
			if (v.id.equals(id)) {
				return v;
			}
		}
		return null;
	}

	/** Vínculo ligado a una entidad concreta (para esclavos anteriores a los ids de vínculo). */
	public static Vinculo porEsclavo(UUID dueno, UUID entidad) {
		for (Vinculo v : datos.cuentas.getOrDefault(dueno, List.of())) {
			if (entidad.equals(v.esclavo)) {
				return v;
			}
		}
		return null;
	}

	/** Plazas ocupadas: reservados, vivos (cargados o no) y difuntos con tumba pendiente. */
	public static int ocupadas(UUID dueno) {
		int n = 0;
		for (Vinculo v : datos.cuentas.getOrDefault(dueno, List.of())) {
			if (v.estado != Estado.LIBERADO) {
				n++;
			}
		}
		return n;
	}

	public static int libres(UUID dueno) {
		return Math.max(0, Config.get().maxPorJugador - ocupadas(dueno));
	}

	/** null si puede ocupar otra plaza; si no, la clave de lang del motivo. */
	public static String puedeTraerOtro(ServerPlayer p) {
		return libres(p.getUUID()) > 0 ? null : "theslave.contrato.limite";
	}

	/** Un esclavo acaba de ser adoptado: lo asocia a un vínculo (el reservado, el migrado o uno nuevo) y devuelve su id. */
	public static UUID adoptado(UUID dueno, UUID entidad, int variante) {
        permitirCambio(dueno);
		return cambiar(() -> {
		Vinculo v = porEsclavo(dueno, entidad);
		if (v == null) {
			for (Vinculo r : cuenta(dueno)) {
				if (r.estado == Estado.RESERVADO) {
					v = r;
					break;
				}
			}
		}
        if (v == null) {
            if (libres(dueno) <= 0) throw new IllegalStateException("No quedan plazas de servidumbre");
            v = new Vinculo();
            cuenta(dueno).add(v);
        }
		v.estado = Estado.VIVO;
		v.esclavo = entidad;
		v.variante = variante;
		return v.id;
		});
	}

	public static void nombrado(UUID dueno, UUID id, String nombre) {
        permitirCambio(dueno);
		Vinculo v = vinculo(dueno, id);
		if (v != null && !java.util.Objects.equals(v.nombre,nombre)) {
			cambiar(() -> {
			v.nombre = nombre;
			return null;
			});
		}
	}

	/** Registra la muerte una sola vez y devuelve el vínculo (null si no existía). */
	public static Vinculo muerto(UUID dueno, UUID id, String nombre, int variante) {
        permitirCambio(dueno);
		return cambiar(() -> {
		Vinculo v = vinculo(dueno, id);
		if (v == null) {
			v = new Vinculo();
			v.id = id != null ? id : v.id;
			cuenta(dueno).add(v);
		}
		if (v.estado == Estado.DIFUNTO) {
			return v;
		}
		v.estado = Estado.DIFUNTO;
		v.nombre = nombre;
		v.variante = variante;
		v.muertes++;
		v.esclavo = null;
		return v;
		});
	}

	public static void resucitado(UUID dueno, UUID id, UUID entidad) {
        permitirCambio(dueno);
		cambiar(() -> {
		Vinculo v = vinculo(dueno, id);
		if (v == null) {
			v = new Vinculo();
			v.id = id != null ? id : v.id;
			cuenta(dueno).add(v);
		}
		v.estado = Estado.VIVO;
		v.esclavo = entidad;
		return null;
		});
	}

	/** Contrato comprado y aún sin usar (reserva la plaza). */
	public static Vinculo reservaPendiente(UUID dueno) {
		for (Vinculo v : datos.cuentas.getOrDefault(dueno, List.of())) {
			if (v.estado == Estado.RESERVADO) {
				return v;
			}
		}
		return null;
	}

	/** Reserva una plaza para un contrato recién comprado. */
    public static Vinculo reservar(UUID dueno, UUID contrato, int grado) {
        permitirCambio(dueno);
        java.util.Objects.requireNonNull(dueno, "dueno");
        java.util.Objects.requireNonNull(contrato, "contrato");
        if (grado < 0 || grado > 2 || grado != ocupadas(dueno)
                || libres(dueno) <= 0 || reservaPendiente(dueno) != null)
            throw new IllegalStateException("Reserva no autorizada para este grado");
        for (var account : datos.cuentas.values()) for (var existing : account)
            if (contrato.equals(existing.contrato)) throw new IllegalStateException("Contrato ya emitido");
		return cambiar(() -> {
		Vinculo v = new Vinculo();
		v.estado = Estado.RESERVADO;
		v.contrato = contrato;
		v.grado = grado;
		cuenta(dueno).add(v);
		return v;
		});
	}

	/** Deshace una reserva (devolución al Notario): la plaza vuelve a quedar libre sin dejar rastro. */
	public static boolean cancelarReserva(UUID dueno, UUID contrato) {
        permitirCambio(dueno);
		List<Vinculo> l = datos.cuentas.get(dueno);
		if (l == null) {
			return false;
		}
		if (l.stream().noneMatch(v -> v.estado == Estado.RESERVADO && contrato.equals(v.contrato))) return false;
        return cambiar(() -> l.removeIf(v -> v.estado == Estado.RESERVADO && contrato.equals(v.contrato)));
	}

	/** Méritos del vínculo; si faltan (esclavos anteriores) se generan una sola vez y se guardan. */
	public static float[] meritos(UUID dueno, UUID id, net.minecraft.util.RandomSource r) {
		Vinculo v = vinculo(dueno, id);
		if (v == null) {
			return Aptitudes.generar(r);
		}
		if (v.merito == null || v.merito.length != 5) {
            permitirCambio(dueno);
			cambiar(() -> {
			v.merito = Aptitudes.generar(r);
			return null;
			});
		}
		return v.merito;
	}

	/** Une dos vivos: el segundo pasa a ser el fusionado y el primero libera su plaza. */
	public static Vinculo fusionar(UUID dueno, UUID a, UUID b, float[] resultado, String nombre, UUID esclavoNuevo) {
        permitirCambio(dueno);
		Vinculo va = vinculo(dueno, a);
		Vinculo vb = vinculo(dueno, b);
		if (va == null || vb == null) {
			return null;
		}
		return cambiar(() -> {
		vb.fusionado = true;
		vb.variante2 = va.variante;
		vb.merito = resultado;
		vb.nombre = nombre;
		vb.esclavo = esclavoNuevo;
		vb.estado = Estado.VIVO;
		va.estado = Estado.LIBERADO;
		va.esclavo = null;
		return vb;
		});
	}

	/** Libera la plaza (liberar con contrato, enterrar una tumba). El registro del individuo se conserva como historial. */
	public static void liberar(UUID dueno, UUID id) {
        permitirCambio(dueno);
		Vinculo v = vinculo(dueno, id);
		if (v != null) {
			cambiar(() -> {
			v.estado = Estado.LIBERADO;
			v.esclavo = null;
			return null;
			});
		}
	}

    private static void permitirCambio(UUID dueno) {
        if (com.theslave.persistencia.CerrojosJugador.ocupada(dueno))
            throw new IllegalStateException("Cuenta bajo custodia de una operacion durable");
    }
}
