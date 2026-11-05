package com.theslave.ritual;

import com.theslave.Aptitudes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.theslave.persistencia.CustodiaJugador;
import com.theslave.Registro;
import com.theslave.Registro.Estado;
import com.theslave.Registro.Vinculo;
import com.theslave.block.AltarBlock;
import com.theslave.block.TumbaBlockEntity;
import com.theslave.comercio.Comercio;
import com.theslave.entity.EsclavoEntity;
import com.theslave.entity.Inv;
import com.theslave.entity.LaMuerteEntity;
import com.theslave.entity.UmbralEntity;
import com.theslave.net.Payloads;
import com.theslave.registry.ModBlocks;
import com.theslave.registry.ModComponents;
import com.theslave.registry.ModComponents.Almas;
import com.theslave.registry.ModComponents.Pacto;
import com.theslave.registry.ModEntities;
import com.theslave.registry.ModItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Las cuatro ceremonias del Altar de Ceniza: invocación, súplica, fusión y súplica fusionada. Las sesiones viven en
 * memoria y el servidor decide todo. Nada se consume hasta el final de la ceremonia, y en ese instante se
 * vuelve a comprobar todo: si algo cambió o se interrumpe antes, el jugador conserva sus objetos y la plaza no cambia.
 */
public final class Ceremonias {
	public enum Tipo {
		INVOCAR(240), SUPLICAR(400), FUSION(360), SUPLICAR_FUSION(480);

		final int duracion;

		Tipo(int duracion) {
			this.duracion = duracion;
		}
	}

	private static final class Sesion {
		final BlockPos altar;
		final UUID dueno;
		final Tipo tipo;
		final Direction frente;
		int tick;
		UUID muerte;
		UUID umbral;
		UUID a;
		UUID b;
		UUID resultado;
		boolean sigueA;
		boolean sigueB;
		boolean hecho;
        UUID vistaVinculo;
        int vistaVariante,vistaVariante2=-1,vistaMuertes;
        String vistaNombre="";
        float[] vistaMeritos;
        ItemStack vistaContrato=ItemStack.EMPTY,vistaOfrenda=ItemStack.EMPTY;
        ItemStack alma=ItemStack.EMPTY;
        final java.util.Set<UUID> espectadores = new java.util.HashSet<>();

		Sesion(BlockPos altar, UUID dueno, Tipo tipo, Direction frente) {
			this.altar = altar;
			this.dueno = dueno;
			this.tipo = tipo;
			this.frente = frente;
		}
	}

	private static final Map<Long, Sesion> SESIONES = new HashMap<>();
    private static final Map<UUID,Sesion> VISTAS=new HashMap<>();
	private static final int MAX_SESIONES = 4;

	private Ceremonias() {
	}

	public static void registrar() {
		ServerTickEvents.END_SERVER_TICK.register(Ceremonias::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Sesion s : new ArrayList<>(SESIONES.values())) {
                interrumpir(server, server.overworld(), s, null, "theslave.altar.interrumpida");
            }
            SESIONES.clear();
            VISTAS.clear();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {SESIONES.clear();VISTAS.clear();});
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            UUID id=handler.player.getUUID();VISTAS.remove(id);for(Sesion sesion:SESIONES.values())sesion.espectadores.remove(id);
        });
	}

	public static boolean activa(BlockPos altar) {
		return SESIONES.containsKey(altar.asLong());
	}

	// ---------------------------------------------------------------- utilidades

	private static void aviso(ServerPlayer p, String clave) {
		p.sendSystemMessage(Component.translatable(clave), true);
	}

	private static boolean esNoche(ServerLevel sl) {
		long t = sl.getOverworldClockTime() % 24000L;
		return t >= 13000 && t <= 23000;
	}

	private static boolean libre(ServerLevel sl, BlockPos p) {
		return sl.hasChunkAt(p)&&sl.getBlockState(p).getCollisionShape(sl, p).isEmpty();
	}

	private static boolean suelo(ServerLevel sl, BlockPos p) {
		BlockPos b = p.below();
		return sl.hasChunkAt(b)&&sl.getBlockState(b).isFaceSturdy(sl, b, Direction.UP);
	}

	/** Espacio para el portal y el paso de La Muerte (detrás) y para los cuerpos (delante). null si todo está libre. */
	private static String espacio(ServerLevel sl, BlockPos altar, Direction frente) {
		Direction det = frente.getOpposite();
		Direction lat = frente.getClockWise();
		for (int k = 1; k <= 3; k++) {
			for (int l = -1; l <= 1; l++) {
				for (int h = 0; h <= 2; h++) {
					if (!libre(sl, altar.relative(det, k).relative(lat, l).above(h))) {
						return "theslave.altar.sin_espacio_detras";
					}
				}
			}
			if (!suelo(sl, altar.relative(det, k))) {
				return "theslave.altar.sin_suelo";
			}
		}
		for (int k = 1; k <= 2; k++) {
			for (int l = -1; l <= 1; l++) {
				for (int h = 0; h <= 2; h++) {
					if (!libre(sl, altar.relative(frente, k).relative(lat, l).above(h))) {
						return "theslave.altar.sin_espacio_delante";
					}
				}
			}
            for (int l = -1; l <= 1; l++) {
                if (!suelo(sl, altar.relative(frente, k).relative(lat, l))) return "theslave.altar.sin_suelo";
            }
		}
		return null;
	}

	private static Vec3 base(BlockPos altar) {
		return Vec3.atBottomCenterOf(altar);
	}

	private static Vec3 detras(Sesion s, double k) {
		Direction d = s.frente.getOpposite();
		return base(s.altar).add(d.getStepX() * k, 0, d.getStepZ() * k);
	}

	private static Vec3 delante(Sesion s, double k, double lado) {
		Direction l = s.frente.getClockWise();
		return base(s.altar).add(s.frente.getStepX() * k + l.getStepX() * lado, 0, s.frente.getStepZ() * k + l.getStepZ() * lado);
	}

	private static int hueco(ServerPlayer p, java.util.function.Predicate<ItemStack> f) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (f.test(inv.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	private static boolean esPactoDe(ItemStack s, UUID dueno) {
		Pacto pa = s.get(ModComponents.PACTO);
		return pa != null && dueno.equals(pa.dueno());
	}


    private static Sesion elegirEscena(ServerPlayer observador,Sesion excluir) {
        Sesion mejor=null;double distanciaMejor=Double.POSITIVE_INFINITY;boolean propiaMejor=false;long claveMejor=Long.MAX_VALUE;
        if(observador.level().dimension()!=Level.OVERWORLD)return null;
        for(Sesion candidata:SESIONES.values()) {
            if(candidata==excluir)continue;
            double distancia=observador.distanceToSqr(base(candidata.altar));boolean propia=candidata.dueno.equals(observador.getUUID());
            long clave=candidata.altar.asLong();
            if(PrioridadEscena.preferir(propia,distancia,clave,propiaMejor,distanciaMejor,claveMejor)) {
                mejor=candidata;distanciaMejor=distancia;propiaMejor=propia;claveMejor=clave;
            }
        }
        return mejor;
    }
    private static Payloads.Escena fotograma(Sesion s) {
        return new Payloads.Escena(1,s.tick,"",.3f+.3f*s.tick/s.tipo.duracion,false);
    }
    private static void actualizarEscena(ServerPlayer observador,Sesion nueva,Payloads.Escena payload) {
        UUID id=observador.getUUID();Sesion anterior=VISTAS.get(id);
        if(anterior!=nueva) {
            if(anterior!=null) {anterior.espectadores.remove(id);escena0(observador);}
            if(nueva==null) {VISTAS.remove(id);return;}
            VISTAS.put(id,nueva);
        }
        if(nueva!=null) {nueva.espectadores.add(id);ServerPlayNetworking.send(observador,payload);}
    }
    private static void escena(ServerPlayer p,int tick,String texto,float intensidad,boolean destello) {
        if(p==null||!(p.level() instanceof ServerLevel level))return;
        Sesion propia=null;for(Sesion candidata:SESIONES.values())if(candidata.dueno.equals(p.getUUID())){propia=candidata;break;}
        if(propia==null)return;
        Payloads.Escena payload=new Payloads.Escena(1,tick,texto,intensidad,false);
        for(ServerPlayer observador:level.players()) {
            Sesion elegida=elegirEscena(observador,null);
            if(elegida==propia)actualizarEscena(observador,propia,payload);
            else if(VISTAS.get(observador.getUUID())==propia)
                actualizarEscena(observador,elegida,elegida==null?null:fotograma(elegida));
        }
    }

    private static void sonido(ServerLevel sl, Vec3 at, SoundEvent ev, float vol, float pitch) {
		sl.playSound(null, at.x, at.y, at.z, ev, SoundSource.AMBIENT, vol, pitch);
	}

	// ---------------------------------------------------------------- entrada desde el bloque

	public static InteractionResult alUsar(ServerPlayer p, InteractionHand mano, ItemStack stack, BlockPos altar) {
		if (!(p.level() instanceof ServerLevel sl)) {
			return InteractionResult.SUCCESS;
		}
		if (stack.isEmpty()) {
			if (mano != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			if (SESIONES.containsKey(altar.asLong())) {
				aviso(p, "theslave.altar.ocupado");
			} else {
				aviso(p, "theslave.altar.vacio");
			}
			return InteractionResult.SUCCESS;
		}
		boolean ritual = stack.is(ModItems.CONTRATO) || stack.is(ModItems.OFRENDA) || stack.is(ModItems.TRIBUTO) || stack.is(ModItems.DOBLE)
				|| stack.is(ModItems.SELLO) || stack.has(ModComponents.ALMA_VINCULADA);
		if (!ritual) {
			return InteractionResult.PASS;
		}
		if (SESIONES.containsKey(altar.asLong())) {
			aviso(p, "theslave.altar.ocupado");
			return InteractionResult.SUCCESS;
		}
		String error = condicionesGenerales(sl, p, altar);
		if (error != null) {
			aviso(p, error);
			return InteractionResult.SUCCESS;
		}
		Direction frente = sl.getBlockState(altar).getValue(AltarBlock.FACING);
		if (stack.is(ModItems.OFRENDA) || stack.is(ModItems.CONTRATO)) {
			Piezas pz = piezasInvocacion(p);
			if (pz.error != null) {
				aviso(p, pz.error);
				return InteractionResult.SUCCESS;
			}
			empezar(sl, p, new Sesion(altar, p.getUUID(), Tipo.INVOCAR, frente));
        } else if (stack.is(ModItems.TRIBUTO) || stack.is(ModItems.DOBLE)
                || stack.has(ModComponents.ALMA_VINCULADA)) {
            Vinculo elegido=Hogares.almaValida(p,stack);
            boolean doble=elegido!=null?elegido.fusionado:stack.is(ModItems.DOBLE);
            UUID objetivo=elegido!=null?elegido.id:objetivoTributo(p,doble);
            int alma=indiceAlma(p,objetivo,doble);
            if(alma<0) {aviso(p,"theslave.altar.falta_alma");return InteractionResult.SUCCESS;}
            elegido=Hogares.almaValida(p,p.getInventory().getItem(alma));
            int tributo=indiceTributo(p,doble,elegido.id);
            if(tributo<0) {aviso(p,doble?"theslave.altar.tributo_doble_incompleto":"theslave.altar.tributo_incompleto");return InteractionResult.SUCCESS;}
            Sesion sesion=new Sesion(altar,p.getUUID(),doble?Tipo.SUPLICAR_FUSION:Tipo.SUPLICAR,frente);
            sesion.vistaVinculo=elegido.id;
            sesion.alma=p.getInventory().getItem(alma).copy();
            empezar(sl,p,sesion);
		} else {
			if (selloIndice(p) < 0) {
				aviso(p, "theslave.altar.sello_incompleto");
				return InteractionResult.SUCCESS;
			}
			List<EsclavoEntity> cand = candidatos(sl, altar, p);
			if (cand.size() < 2) {
				aviso(p, "theslave.altar.fusion_pocos");
				return InteractionResult.SUCCESS;
			}
			List<Payloads.Candidato> l = new ArrayList<>();
			for (EsclavoEntity e : cand) {
				l.add(new Payloads.Candidato(e.getId(), e.nombre(), e.variante(), e.meritos()));
			}
			ServerPlayNetworking.send(p, new Payloads.AbrirFusion(altar, l));
		}
		return InteractionResult.SUCCESS;
	}

	private static String condicionesGenerales(ServerLevel sl, ServerPlayer p, BlockPos altar) {
        if (CustodiaJugador.ocupada(p.getUUID())||!sl.hasChunkAt(altar)) return "theslave.altar.ocupado";
		if (sl.dimension() != Level.OVERWORLD) {
			return "theslave.altar.no_overworld";
		}
		if (!esNoche(sl)) {
			return "theslave.altar.no_noche";
		}
		if (SESIONES.size() >= MAX_SESIONES) {
			return "theslave.altar.ocupado";
		}
		for (Sesion o : SESIONES.values()) {
			if (o.dueno.equals(p.getUUID())) {
				return "theslave.altar.ya_ceremonia";
			}
		}
		if (p.distanceToSqr(base(altar)) > 64.0) {
			return "theslave.altar.lejos";
		}
		return espacio(sl, altar, sl.getBlockState(altar).getValue(AltarBlock.FACING));
	}


	// ---------------------------------------------------------------- validaciones de cada tipo

	private record Piezas(int contrato, int ofrenda, int latigo, String error) {
	}

	private static Piezas piezasInvocacion(ServerPlayer p) {
		UUID dueno = p.getUUID();
		Vinculo r = Registro.reservaPendiente(dueno);
		if (r == null || r.contrato == null) {
			return new Piezas(-1, -1, -1, "theslave.altar.sin_reserva");
		}
		Inventory inv = p.getInventory();
		int c = -1;
		int o = -1;
		int l = -1;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			Pacto pa = s.get(ModComponents.PACTO);
			if (pa == null || !dueno.equals(pa.dueno()) || !r.contrato.equals(pa.referencia())) {
				continue;
			}
			if (pa.grado() != r.grado) continue;
            if (s.is(ModItems.CONTRATO)) {
				c = i;
			} else if (s.is(ModItems.OFRENDA)) {
				o = i;
			} else if (s.is(ModItems.LATIGO) && s.getDamageValue() == 0) {
				l = i;
			}
		}
		if (o < 0) {
			return new Piezas(c, o, l, "theslave.altar.ofrenda_ajena");
		}
		if (c < 0) {
			return new Piezas(c, o, l, "theslave.altar.falta_contrato");
		}
		Almas req = Comercio.precioAdquisicion(r.grado).almas();
		if (!inv.getItem(o).getOrDefault(ModComponents.ALMAS, Almas.VACIO).equals(req)) {
			return new Piezas(c, o, l, "theslave.altar.ofrenda_incompleta");
		}
		return new Piezas(c, o, l, null);
	}

    private static int indiceTributo(ServerPlayer p, boolean doble) { return indiceTributo(p, doble, null); }

    private static int indiceTributo(ServerPlayer p, boolean doble, UUID objetivo) {
		Item item = doble ? ModItems.DOBLE : ModItems.TRIBUTO;
		Almas req = (doble ? Comercio.DOBLE : Comercio.TRIBUTO).almas();
		return hueco(p, s -> s.is(item) && esPactoDe(s, p.getUUID())
                && (objetivo == null || ModComponents.NINGUNO.equals(s.get(ModComponents.PACTO).referencia())
                    || objetivo.equals(s.get(ModComponents.PACTO).referencia()))
                && s.getOrDefault(ModComponents.ALMAS, Almas.VACIO).equals(req));
	}

    private static int indiceAlma(ServerPlayer p,UUID objetivo,boolean doble) {
        for(int i=0;i<p.getInventory().getContainerSize();i++) {
            Vinculo v=Hogares.almaValida(p,p.getInventory().getItem(i));
            if(v!=null&&v.fusionado==doble&&(objetivo==null||objetivo.equals(v.id)))return i;
        }
        return -1;
    }


	private static UUID objetivoTributo(ServerPlayer p, boolean doble) {
		int i = indiceTributo(p, doble);
		if (i < 0) {
			return null;
		}
		Pacto pa = p.getInventory().getItem(i).get(ModComponents.PACTO);
		return pa == null || ModComponents.NINGUNO.equals(pa.referencia()) ? null : pa.referencia();
	}

	private static int selloIndice(ServerPlayer p) {
		Almas req = Comercio.SELLO.almas();
		return hueco(p, s -> s.is(ModItems.SELLO) && esPactoDe(s, p.getUUID())
				&& s.getOrDefault(ModComponents.ALMAS, Almas.VACIO).equals(req));
	}


	private static boolean fusionable(EsclavoEntity e, ServerPlayer p) {
		if (!e.isAlive() || !e.esDe(p) || e.fusionado() || e.enRito() || e.enCustodiaRitual() || e.vinculo() == null
                || e.estado() != EsclavoEntity.Estado.OCIOSO) {
			return false;
		}
		Vinculo v = Registro.vinculo(p.getUUID(), e.vinculo());
		return v != null && v.estado == Estado.VIVO && !v.fusionado;
	}

	/** Esclavos propios, no fusionados, a menos de 6 bloques del altar y con camino hasta su puesto. Los 3 más cercanos. */
	private static List<EsclavoEntity> candidatos(ServerLevel sl, BlockPos altar, ServerPlayer p) {
		AABB caja = new AABB(altar).inflate(6, 4, 6);
		List<EsclavoEntity> l = new ArrayList<>(sl.getEntitiesOfClass(EsclavoEntity.class, caja, e -> fusionable(e, p)));
		l.removeIf(e -> {
            if (e.distanceToSqr(base(altar)) > 36.0) return true;
			var path = e.getNavigation().createPath(BlockPos.containing(delante(sentinel(altar, sl), 2, 0)), 1);
			return path == null || !path.canReach();
		});
		l.sort(Comparator.comparingDouble(e -> e.distanceToSqr(base(altar))));
		return l.size() > 3 ? new ArrayList<>(l.subList(0, 3)) : l;
	}

	private static Sesion sentinel(BlockPos altar, ServerLevel sl) {
		return new Sesion(altar, null, Tipo.FUSION, sl.getBlockState(altar).getValue(AltarBlock.FACING));
	}

	// ---------------------------------------------------------------- arranque de la fusión

	public static void iniciarFusion(ServerPlayer p, BlockPos altar, int ida, int idb) {
		if (!(p.level() instanceof ServerLevel sl) || ida == idb ||p.distanceToSqr(base(altar))>64||!sl.hasChunkAt(altar)||!sl.getBlockState(altar).is(ModBlocks.ALTAR)) {
			return;
		}
		if (SESIONES.containsKey(altar.asLong())) {
			aviso(p, "theslave.altar.ocupado");
			return;
		}
		String error = condicionesGenerales(sl, p, altar);
		if (error != null) {
			aviso(p, error);
			return;
		}
		if (selloIndice(p) < 0) {
			aviso(p, "theslave.altar.sello_incompleto");
			return;
		}
		List<EsclavoEntity> cand = candidatos(sl, altar, p);
		EsclavoEntity a = null;
		EsclavoEntity b = null;
		for (EsclavoEntity e : cand) {
			if (e.getId() == ida) {
				a = e;
			} else if (e.getId() == idb) {
				b = e;
			}
		}
		if (a == null || b == null) {
			aviso(p, "theslave.altar.fusion_pocos");
			return;
		}
		Sesion s = new Sesion(altar, p.getUUID(), Tipo.FUSION, sl.getBlockState(altar).getValue(AltarBlock.FACING));
		s.a = a.getUUID();
		s.b = b.getUUID();
		s.sigueA = a.sigue();
		s.sigueB = b.sigue();
		a.enRito(true);
		b.enRito(true);
		
		empezar(sl, p, s);
	}

	private static void empezar(ServerLevel sl, ServerPlayer p, Sesion s) {
        if(s.tipo==Tipo.INVOCAR) {
            Vinculo reserva=Registro.reservaPendiente(p.getUUID());Piezas piezas=piezasInvocacion(p);
            if(reserva==null||piezas.error!=null)return;
            s.vistaVinculo=reserva.id;s.vistaVariante=sl.getRandom().nextInt(EsclavoEntity.VARIANTES);
            s.vistaMeritos=reserva.merito==null?Aptitudes.generar(sl.getRandom()):reserva.merito.clone();
            s.vistaContrato=p.getInventory().getItem(piezas.contrato).copy();s.vistaOfrenda=p.getInventory().getItem(piezas.ofrenda).copy();
        } else if(s.tipo==Tipo.SUPLICAR||s.tipo==Tipo.SUPLICAR_FUSION) {
            Vinculo ficha=Hogares.almaValida(p,s.alma);
            if(ficha==null||!ficha.id.equals(s.vistaVinculo))return;
            s.vistaVinculo=ficha.id;s.vistaVariante=ficha.variante;s.vistaVariante2=ficha.variante2;
            s.vistaNombre=ficha.nombre;s.vistaMuertes=ficha.muertes;
            s.vistaMeritos=ficha.merito==null?Aptitudes.generar(sl.getRandom()):ficha.merito.clone();
            int tributo=indiceTributo(p,ficha.fusionado,ficha.id);if(tributo<0)return;
            s.vistaOfrenda=p.getInventory().getItem(tributo).copy();
        } else if(s.tipo==Tipo.FUSION) {
            int sello=selloIndice(p);if(sello<0)return;s.vistaOfrenda=p.getInventory().getItem(sello).copy();
        }
		SESIONES.put(s.altar.asLong(), s);
		sl.scheduleTick(s.altar, ModBlocks.ALTAR, 20);
		escena(p, 0, "", 0.2f, false);
	}

	// ---------------------------------------------------------------- bucle

	private static void tick(MinecraftServer server) {
		if (SESIONES.isEmpty()) {
			return;
		}
		for (Sesion s : new ArrayList<>(SESIONES.values())) {
			if (SESIONES.get(s.altar.asLong()) == s) {
				tickSesion(server, s);
			}
		}
	}

	private static void tickSesion(MinecraftServer server, Sesion s) {
		ServerLevel sl = server.overworld();
		ServerPlayer p = server.getPlayerList().getPlayer(s.dueno);
		if (p == null || !p.isAlive() || p.level() != sl || !sl.isLoaded(s.altar) || !sl.getBlockState(s.altar).is(ModBlocks.ALTAR)) {
			interrumpir(server, sl, s, null, "theslave.altar.interrumpida");
			return;
		}
		if (s.tick % 5 == 0) {
			String motivo = null;
			if (p.distanceToSqr(base(s.altar)) > 196.0) {
				motivo = "theslave.altar.lejos";
			} else if (!s.hecho && espacio(sl, s.altar, s.frente) != null) {
				motivo = "theslave.altar.sin_espacio_detras";
			}
			if (motivo != null) {
				interrumpir(server, sl, s, p, motivo);
				return;
			}
		}
		int d = s.tipo.duracion;
		int t = s.tick;
		velas(sl, s, t, d);
		portal(sl, s, t, d);
		muerte(sl, s, t, d);
		if (s.tipo == Tipo.FUSION) {
			if (!participantes(sl, s, p, t, d)) {
				return;
			}
		}
		if (!s.hecho && t >= d - 1) {
			if (!accion(server, sl, s, p)) {
				return;
			}
			s.hecho = true;
		}
        if (t % 40 == 0) escena(p, t, "", 0.3f + 0.3f * t / d, false);
		subtitulos(sl, s, p, t, d);
		s.tick++;
		if (s.tick >= d) {
			terminar(sl, s, p);
		}
	}


	// ---------------------------------------------------------------- velas, portal, La Muerte

	private static void velas(ServerLevel sl, Sesion s, int t, int d) {
		for (int i = 0; i < 4; i++) {
			int[] orden = switch (s.tipo) {
				case INVOCAR -> new int[]{0,1,3,2};
				case SUPLICAR -> new int[]{0,3,1,2};
				case FUSION -> new int[]{0,2,1,3};
				case SUPLICAR_FUSION -> new int[]{0,3,2,1};
			};
			if (t == i * 15) {
				BlockState st = sl.getBlockState(s.altar);
				if (st.is(ModBlocks.ALTAR)) {
					sl.setBlock(s.altar, st.setValue(AltarBlock.VELAS[orden[i]], true), 3);
					sonido(sl, base(s.altar), SoundEvents.FLINTANDSTEEL_USE, 0.6f, 0.7f + i * 0.1f);
				}
			}
		}
	}

	private static UmbralEntity umbral(ServerLevel sl, Sesion s) {
		return s.umbral != null && sl.getEntity(s.umbral) instanceof UmbralEntity u ? u : null;
	}

	private static LaMuerteEntity muerteDe(ServerLevel sl, Sesion s) {
		return s.muerte != null && sl.getEntity(s.muerte) instanceof LaMuerteEntity m ? m : null;
	}

	private static void portal(ServerLevel sl, Sesion s, int t, int d) {
        SecuenciaRitual guion = SecuenciaRitual.para(s.tipo.ordinal());
        if (s.umbral == null) {
            UmbralEntity u = ModEntities.UMBRAL.create(sl, EntitySpawnReason.TRIGGERED);
            if (u != null) {
                Vec3 at = detras(s, 3.0);
                u.setPos(at.x, at.y, at.z);
                u.setYRot(s.frente.toYRot());
                u.guion(s.tipo.ordinal(), t, null);
                u.aparicion(s.vistaVariante,s.vistaVariante2,s.vistaNombre,s.vistaContrato,s.vistaOfrenda);
                u.alma(s.alma.copy());
                if (sl.addFreshEntity(u)) s.umbral = u.getUUID();
            }
        }
        UmbralEntity u = umbral(sl, s);
        if (u != null) {
            u.apertura(guion.apertura(t));
            u.guion(s.tipo.ordinal(), t, null);
            LaMuerteEntity actor=muerteDe(sl,s);
            Vec3 mano=actor==null?detras(s,1).add(0,1.35,0):actor.position().add(0,1.35,0);
            u.mano(mano.subtract(u.position()));
        }
    }

	private static void orientar(LaMuerteEntity m, float yaw) {
		m.setYRot(yaw);
		m.setYHeadRot(yaw);
		m.setYBodyRot(yaw);
		m.yRotO = yaw;
	}

	private static void muerte(ServerLevel sl, Sesion s, int t, int d) {
        SecuenciaRitual guion = SecuenciaRitual.para(s.tipo.ordinal());
        int entra = guion.entra();
        int llega = guion.llega();
        int sale = guion.sale();
        int vuelta = guion.vuelve();
		if (t == entra && s.muerte == null) {
			LaMuerteEntity m = ModEntities.LA_MUERTE.create(sl, EntitySpawnReason.TRIGGERED);
			if (m != null) {
				Vec3 at = detras(s, 3.0);
				m.setPos(at.x, at.y, at.z);
				orientar(m, s.frente.toYRot());
                m.portal(s.altar.relative(s.frente.getOpposite(),3),s.frente);
					if (!sl.addFreshEntity(m)) return;
					s.muerte = m.getUUID();
				sonido(sl, at, SoundEvents.WITHER_SPAWN, 0.4f, 0.5f);
			}
		}
		LaMuerteEntity m = muerteDe(sl, s);
		if (m == null) {
			return;
		}
		Vec3 destino = detras(s,1);
        m.presencia(guion.presencia(t));
        m.guion(s.tipo.ordinal(),t);
		Vec3 portal = detras(s, 3.0);
		if (t > entra && t <= llega) {
			Vec3 at = portal.lerp(destino, (t - entra) / (double) Math.max(1, llega - entra));
			float rumbo = rumbo(m.position(),at,s.frente.toYRot());
			m.setPos(at.x, at.y, at.z);
			orientar(m, rumbo);
			if (t % 10 == 0) {
				sonido(sl, at, SoundEvents.SOUL_ESCAPE.value(), 0.5f, 0.6f);
			}
		} else if (t > llega && t < sale) {
			m.setPos(destino.x, destino.y, destino.z);
			orientar(m, (float) s.frente.getOpposite().getOpposite().toYRot());
		} else if (t >= sale && t <= vuelta) {
			Vec3 at = destino.lerp(portal, (t - sale) / (double) Math.max(1, vuelta - sale));
			float rumbo = rumbo(m.position(),at,s.frente.getOpposite().toYRot());
			m.setPos(at.x, at.y, at.z);
			orientar(m, rumbo);
		}
		if (t == vuelta) {
			m.discard();
			s.muerte = null;
		}
	}

	// ---------------------------------------------------------------- fusión: puestos, elevación y cadenas

    private static float rumbo(Vec3 desde,Vec3 hasta,float anterior) {
        Vec3 delta=hasta.subtract(desde);
        return delta.horizontalDistanceSqr()<1e-8?anterior:(float)Math.toDegrees(Math.atan2(delta.z,delta.x))-90;
    }

	private static EsclavoEntity esclavo(ServerLevel sl, UUID id) {
		return id != null && sl.getEntity(id) instanceof EsclavoEntity e && e.isAlive() ? e : null;
	}

	private static void liberar(EsclavoEntity e, double y) {
		if (e == null) {
			return;
		}
		e.setNoGravity(false);
		
		e.enRito(false);
		e.setPos(e.getX(), y, e.getZ());
		e.setDeltaMovement(Vec3.ZERO);
	}

	private static boolean participantes(ServerLevel sl, Sesion s, ServerPlayer p, int t, int d) {
		EsclavoEntity a = esclavo(sl, s.a);
		EsclavoEntity b = esclavo(sl, s.b);
		if (!s.hecho && (a == null || b == null || !a.esDe(p) || !b.esDe(p))) {
			interrumpir(p.level().getServer(), sl, s, p, "theslave.altar.fusion_cambio");
			return false;
		}
		if (a == null || b == null) {
			return true;
		}
		Vec3 pa = delante(s, 2.0, -1.0);
		Vec3 pb = delante(s, 2.0, 1.0);
        a.fusionVisual(1,t,s.frente);
        b.fusionVisual(2,t,s.frente);
		int llegada = (int) (d * 0.25);
		int sube = (int) (d * 0.30);
		int alto = (int) (d * 0.45);
		if (t < llegada) {
			if (t % 10 == 0) {
				a.getNavigation().moveTo(pa.x, pa.y, pa.z, 1.0);
				b.getNavigation().moveTo(pb.x, pb.y, pb.z, 1.0);
			}
			return true;
		}
		if (t == llegada) {
			if (a.position().distanceToSqr(pa) > 0.36 || b.position().distanceToSqr(pb) > 0.36) {
				interrumpir(p.level().getServer(), sl, s, p, "theslave.altar.fusion_sin_paso");
				return false;
			}
			a.getNavigation().stop();
			b.getNavigation().stop();
		}
		if (t >= llegada) {
			double y = base(s.altar).y;
			double alt = t < sube ? 0.0 : 0.5 * Math.min(1.0, (t - sube) / (double) Math.max(1, alto - sube));
			a.setNoGravity(true);
			b.setNoGravity(true);
			a.setPos(pa.x, y + alt, pa.z);
			b.setPos(pb.x, y + alt, pb.z);
			a.setDeltaMovement(Vec3.ZERO);
			b.setDeltaMovement(Vec3.ZERO);
			float ya = (float) (Math.atan2(base(s.altar).z - pa.z, base(s.altar).x - pa.x) * 180.0 / Math.PI) - 90.0f;
			a.setYRot(ya);
			a.setYHeadRot(ya);
			float yb = (float) (Math.atan2(base(s.altar).z - pb.z, base(s.altar).x - pb.x) * 180.0 / Math.PI) - 90.0f;
			b.setYRot(yb);
			b.setYHeadRot(yb);
			if (t >= sube && !s.hecho && t % 2 == 0) {
				a.cadenaRitual(s.altar);
				b.cadenaRitual(s.altar);
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- el momento central

	/** Instante de la acción. Vuelve a comprobarlo todo; si falla, no se consume nada y se interrumpe. */
	private static boolean accion(MinecraftServer server, ServerLevel sl, Sesion s, ServerPlayer p) {
		switch (s.tipo) {
			case INVOCAR -> {
				return accionInvocar(server, sl, s, p);
			}
			case SUPLICAR, SUPLICAR_FUSION -> {
				return accionSuplicar(server, sl, s, p);
			}
			default -> {
				return accionFusion(server, sl, s, p);
			}
		}
	}

	private static boolean accionInvocar(MinecraftServer server, ServerLevel sl, Sesion s, ServerPlayer p) {
		Piezas pz = piezasInvocacion(p);
		Vinculo r = Registro.reservaPendiente(p.getUUID());
		if (pz.error != null || r == null || !r.id.equals(s.vistaVinculo)) {
			interrumpir(server, sl, s, p, pz.error != null ? pz.error : "theslave.altar.fusion_cambio");
			return false;
		}
		EsclavoEntity e = ModEntities.ESCLAVO.create(sl, EntitySpawnReason.TRIGGERED);
		if (e == null) {
			interrumpir(server, sl, s, p, "theslave.altar.interrumpida");
			return false;
		}
		Vec3 at = delante(s, 2.0, 0.0);
		e.setPos(at.x, at.y, at.z);
		float yaw = s.frente.getOpposite().toYRot();
		e.setYRot(yaw);
		e.setYHeadRot(yaw);
        float[] aptitudes=s.vistaMeritos.clone();
        e.nacer(p.getUUID(),r.id,s.vistaVariante,aptitudes);
        JsonArray cuenta = Registro.cuentaOperacion(p.getUUID());
        JsonObject ficha = ficha(cuenta, r.id);
        ficha.addProperty("estado", "VIVO");
        ficha.addProperty("esclavo", e.getUUID().toString());
        ficha.addProperty("variante", e.variante());
        ficha.add("merito", new Gson().toJsonTree(aptitudes));
        ItemStack[] pago = copiaInventario(p);
        if (pz.latigo >= 0) pago[pz.latigo].remove(ModComponents.PACTO);
        pago[pz.contrato].shrink(1);
        pago[pz.ofrenda].shrink(1);
        if(!MuerteDurable.insertar(pago,Hogares.crearLecho(p.getUUID(),r.id)).isEmpty()) {
            interrumpir(server,sl,s,p,"theslave.notario.sin_hueco");return false;
        }
        if (!RitualDurable.iniciar(p, e, List.of(), null, cuenta, CustodiaJugador.codificar(p, pago))) {
            interrumpir(server, sl, s, p, "theslave.altar.interrumpida");
            return false;
        }
		s.resultado = e.getUUID();
		efectoNacimiento(sl, at);
		return true;
	}

    private static boolean accionSuplicar(MinecraftServer server,ServerLevel sl,Sesion s,ServerPlayer p) {
        boolean doble=s.tipo==Tipo.SUPLICAR_FUSION;
        int alma=indiceAlma(p,s.vistaVinculo,doble);
        int tributo=indiceTributo(p,doble,s.vistaVinculo);
        Vinculo v=alma<0?null:Hogares.almaValida(p,p.getInventory().getItem(alma));
        var marca=alma<0?null:p.getInventory().getItem(alma).get(ModComponents.ALMA_VINCULADA);
        var inicial=s.alma.get(ModComponents.ALMA_VINCULADA);
        if(v==null||tributo<0||marca==null||!marca.equals(inicial)||v.muertes!=s.vistaMuertes
                ||v.variante!=s.vistaVariante||!v.nombre.equals(s.vistaNombre)) {
            interrumpir(server,sl,s,p,"theslave.altar.fusion_cambio");return false;
        }
        EsclavoEntity resultado=ModEntities.ESCLAVO.create(sl,EntitySpawnReason.TRIGGERED);
        if(resultado==null) {interrumpir(server,sl,s,p,"theslave.altar.interrumpida");return false;}
        resultado.restaurar(p.getUUID(),v.id,v.nombre,v.variante);
        Vinculo preparada=new Vinculo();preparada.merito=s.vistaMeritos.clone();preparada.variante2=s.vistaVariante2;
        resultado.aplicarVinculo(preparada);
        Vec3 donde=delante(s,2,0);resultado.setPos(donde.x,donde.y,donde.z);
        resultado.setYRot(s.frente.getOpposite().toYRot());resultado.setYHeadRot(resultado.getYRot());
        JsonArray cuenta=Registro.cuentaOperacion(p.getUUID());JsonObject ficha=ficha(cuenta,v.id);
        ficha.addProperty("estado","VIVO");ficha.addProperty("esclavo",resultado.getUUID().toString());
        ficha.add("merito",new Gson().toJsonTree(s.vistaMeritos));
        ItemStack[] pago=copiaInventario(p);pago[alma].shrink(1);pago[tributo].shrink(1);
        if(!RitualDurable.iniciar(p,resultado,List.of(),doble?RitualDurable.Tipo.SUPLICA_FUSION:RitualDurable.Tipo.SUPLICA,
                v.id,v.muertes,cuenta,CustodiaJugador.codificar(p,pago))) {
            interrumpir(server,sl,s,p,"theslave.altar.interrumpida");return false;
        }
        s.resultado=resultado.getUUID();efectoNacimiento(sl,donde);return true;
    }

	private static boolean accionFusion(MinecraftServer server, ServerLevel sl, Sesion s, ServerPlayer p) {
		EsclavoEntity a = esclavo(sl, s.a);
		EsclavoEntity b = esclavo(sl, s.b);
		int sello = selloIndice(p);
		Vinculo va = a == null || a.vinculo() == null ? null : Registro.vinculo(p.getUUID(), a.vinculo());
		Vinculo vb = b == null || b.vinculo() == null ? null : Registro.vinculo(p.getUUID(), b.vinculo());
		if (sello < 0 || a == null || b == null || va == null || vb == null || va.estado != Estado.VIVO || vb.estado != Estado.VIVO
				|| va.fusionado || vb.fusionado || !a.esDe(p) || !b.esDe(p)||a.enCustodiaRitual()||b.enCustodiaRitual()) {
			interrumpir(server, sl, s, p, "theslave.altar.fusion_cambio");
			return false;
		}
		float[] m = Aptitudes.fusion(a.meritos(), b.meritos());
		String nombre = b.nombre();
		int varA = a.variante();
		int varB = b.variante();
		UUID idB = vb.id;
		Vec3 at = delante(s, 2.0, 0.0);
		EsclavoEntity f = ModEntities.ESCLAVO.create(sl, EntitySpawnReason.TRIGGERED);
		if (f == null) {
			interrumpir(server, sl, s, p, "theslave.altar.interrumpida");
			return false;
		}
		f.setPos(at.x, at.y, at.z);
		float yaw = s.frente.getOpposite().toYRot();
		f.setYRot(yaw);
		f.setYHeadRot(yaw);
		f.fusionar(p.getUUID(), idB, nombre, varB, varA, m);
        f.energiaFundicion(a.energiaFundicion()+b.energiaFundicion());
        // Simulacion completa antes de cambiar a los originales: no se pierde equipo ni se tira excedente.
        for (EsclavoEntity o : List.of(a, b)) {
            List<ItemStack> contenido = new ArrayList<>(o.inv().stacks());
            if (!o.ref().isEmpty()) contenido.add(o.ref());
            for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                ItemStack equipo = o.getItemBySlot(slot);
                if (!equipo.isEmpty()) contenido.add(equipo);
            }
            for (ItemStack st : contenido) {
                if (!f.inv().insertar(st, Inv.Fuente.DUENO).isEmpty()) {
                    p.sendSystemMessage(Component.literal("Las dos mochilas y equipos no caben en el fusionado. Retira objetos antes del ritual."), true);
                    interrumpir(server, sl, s, p, "theslave.altar.interrumpida");
                    return false;
                }
            }
        }

        JsonArray cuenta = Registro.cuentaOperacion(p.getUUID());
        JsonObject anterior = ficha(cuenta, va.id);
        anterior.addProperty("estado", "LIBERADO");
        anterior.remove("esclavo");
        JsonObject fusionado = ficha(cuenta, idB);
        fusionado.addProperty("estado", "VIVO");
        fusionado.addProperty("esclavo", f.getUUID().toString());
        fusionado.addProperty("fusionado", true);
        fusionado.addProperty("variante2", varA);
        fusionado.addProperty("nombre", nombre);
        fusionado.add("merito", new Gson().toJsonTree(m));
        ItemStack[] pago = copiaInventario(p);
        pago[sello].shrink(1);
        if (!RitualDurable.iniciar(p, f, List.of(a, b), null, cuenta, CustodiaJugador.codificar(p, pago))) {
            interrumpir(server, sl, s, p, "theslave.altar.interrumpida");
            return false;
        }
		s.resultado = f.getUUID();
		s.a = null;
		s.b = null;
		efectoNacimiento(sl, at);
		return true;
	}

    private static ItemStack[] copiaInventario(ServerPlayer p) {
        ItemStack[] copia = new ItemStack[p.getInventory().getContainerSize()];
        for (int i=0;i<copia.length;i++) copia[i]=p.getInventory().getItem(i).copy();
        return copia;
    }
    private static JsonObject ficha(JsonArray cuenta, UUID id) {
        for (var fila:cuenta) {
            JsonObject ficha=fila.getAsJsonObject();
            if (id.toString().equals(ficha.get("id").getAsString())) return ficha;
        }
        throw new IllegalStateException("El vinculo ha desaparecido antes de preparar el ritual");
    }

	private static void efectoNacimiento(ServerLevel sl, Vec3 at) {
		sl.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.6, at.z, 30, 0.35, 0.6, 0.35, 0.04);
		sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.2, at.z, 25, 0.4, 0.1, 0.4, 0.05);
		sonido(sl, at, SoundEvents.BELL_RESONATE, 1.4f, 0.5f);

	}

	// ---------------------------------------------------------------- guion

	private static void subtitulos(ServerLevel sl, Sesion s, ServerPlayer p, int t, int d) {
		int llega = SecuenciaRitual.para(s.tipo.ordinal()).llega();
		int accion = (int) (d * 0.62);
		float intensidad = 0.3f + 0.7f * t / d;
		switch (s.tipo) {
			case INVOCAR -> {
				if (t == accion) {
					escena(p, t, "«Queda escrito»", intensidad, false);
				}
			}
			case SUPLICAR -> {
				if (t == llega) {
					escena(p, t, "«Escucha a quien aún recuerda su nombre»", intensidad, false);
				} else if (t == accion) {
					escena(p, t, "«La deuda permanece»", intensidad, false);
				}
			}
			case FUSION -> {
				if (t == accion) {
					escena(p, t, "«Dos nombres. Un cuerpo»", intensidad, false);
				}
			}
			case SUPLICAR_FUSION -> {
				if (t == llega) {
					escena(p, t, "«Una vida recuerda su camino. Esta necesita recordar dos»", intensidad, false);
				} else if (t == accion) {
					escena(p, t, "«Todavía aquí»", intensidad, false);
				}
			}
		}
		if (t == accion + 40) {
			escena(p, t, "", 0.4f, false);
		}
	}

	private static void apagar(ServerLevel sl, Sesion s) {
		BlockState st = sl.getBlockState(s.altar);
		if (st.is(ModBlocks.ALTAR)) {
			for (int i = 0; i < 4; i++) {
				st = st.setValue(AltarBlock.VELAS[i], false);
			}
			sl.setBlock(s.altar, st, 3);
		}
		UmbralEntity u = umbral(sl, s);
		if (u != null) {
			u.discard();
		}
		LaMuerteEntity m = muerteDe(sl, s);
		if (m != null) {
			sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 1, m.getZ(), 30, 0.4, 0.9, 0.4, 0.03);
			m.discard();
		}
		SESIONES.remove(s.altar.asLong());
	}

	private static void terminar(ServerLevel sl, Sesion s, ServerPlayer p) {
		apagar(sl, s);
        cerrarEscena(sl, s);
        // El nombre se abre desde RitualDurable tras confirmar tambien el almacenamiento.
	}

    private static void cerrarEscena(ServerLevel sl, Sesion s) {
        for (UUID id : new ArrayList<>(s.espectadores)) {
            ServerPlayer o=sl.getServer().getPlayerList().getPlayer(id);
            if(VISTAS.get(id)!=s)continue;
            if(o==null) {VISTAS.remove(id);continue;}
            Sesion siguiente=elegirEscena(o,s);
            actualizarEscena(o,siguiente,siguiente==null?null:fotograma(siguiente));
        }
        s.espectadores.clear();
    }

	private static void escena0(ServerPlayer p) {
		if (p != null) {
			ServerPlayNetworking.send(p, new Payloads.Escena(0, 0, "", 0.0f, false));
		}
	}

	/** Interrupción: se apaga todo, los participantes recuperan su estado y no se ha consumido nada. */
	private static void interrumpir(MinecraftServer server, ServerLevel sl, Sesion s, ServerPlayer p, String clave) {
		if (s.tipo == Tipo.FUSION) {
			double y = base(s.altar).y;
			EsclavoEntity ea = esclavo(sl, s.a);
			EsclavoEntity eb = esclavo(sl, s.b);
			liberar(ea, y);
			liberar(eb, y);
			if (ea != null) {
				ea.sigue(s.sigueA);
			}
			if (eb != null) {
				eb.sigue(s.sigueB);
			}
		}
        cerrarEscena(sl, s);
		apagar(sl, s);
		if (p == null) {
			p = server.getPlayerList().getPlayer(s.dueno);
		}
		if (p != null) {
			aviso(p, clave);
		}
	}

	/** Un entierro o cualquier cambio en la tumba que usaba una ceremonia la corta sin consumir nada. */
    public static void cancelarEn(ServerLevel sl,BlockPos tumba) {
        if(sl.getBlockEntity(tumba) instanceof TumbaBlockEntity be && be.dueno!=null)
            cancelarVinculo(sl,be.dueno,be.vinculoId());
    }
    public static void cancelarVinculo(ServerLevel sl,UUID dueno,UUID vinculo) {
        for(Sesion sesion:new ArrayList<>(SESIONES.values()))if(sesion.dueno.equals(dueno)&&vinculo!=null&&vinculo.equals(sesion.vistaVinculo))
            interrumpir(sl.getServer(),sl.getServer().overworld(),sesion,null,"theslave.altar.fusion_cambio");
    }
}
