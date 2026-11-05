package com.theslave.net;

import com.theslave.TheSlaveMod;
import com.theslave.comercio.Comercio;
import com.theslave.entity.EsclavoEntity;
import com.theslave.entity.NotarioEntity;
import com.theslave.ritual.Ceremonias;
import com.theslave.ritual.Ritual;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Todos los paquetes del mod. Servidor -> cliente abren pantallas; cliente -> servidor piden acciones. */
public final class Payloads {
    private static final LimitePeticiones LIMITE=new LimitePeticiones();
	private Payloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> tipo(String nombre) {
		return new CustomPacketPayload.Type<>(TheSlaveMod.id(nombre));
	}

	// ---------------- cliente -> servidor ----------------

	public record Nombre(int entidad, String nombre) implements CustomPacketPayload {
		public static final Type<Nombre> TYPE = tipo("nombre");
		public static final StreamCodec<RegistryFriendlyByteBuf, Nombre> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeVarInt(v.entidad);
					b.writeUtf(v.nombre, 32);
				},
				b -> new Nombre(b.readVarInt(), b.readUtf(32)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Acción sobre una tumba: 0 refrescar, 1 recoger el alma vinculada. */
	public record AccionTumba(BlockPos pos, int accion) implements CustomPacketPayload {
		public static final Type<AccionTumba> TYPE = tipo("accion_tumba");
		public static final StreamCodec<RegistryFriendlyByteBuf, AccionTumba> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeBlockPos(v.pos);
					b.writeVarInt(v.accion);
				},
				b -> new AccionTumba(b.readBlockPos(), b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Orden a un esclavo propio: 0 seguirme, 1 esperar aquí. */
	public record Orden(int entidad, int orden) implements CustomPacketPayload {
		public static final Type<Orden> TYPE = tipo("orden");
		public static final StreamCodec<RegistryFriendlyByteBuf, Orden> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeVarInt(v.entidad);
					b.writeVarInt(v.orden);
				}, b -> new Orden(b.readVarInt(), b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Compra a un Notario: índice de {@link Comercio.Oferta}. */
	public record Comprar(int entidad, int oferta) implements CustomPacketPayload {
		public static final Type<Comprar> TYPE = tipo("comprar");
		public static final StreamCodec<RegistryFriendlyByteBuf, Comprar> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeVarInt(v.entidad);
					b.writeVarInt(v.oferta);
				}, b -> new Comprar(b.readVarInt(), b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Pareja elegida para fundir ante un altar (ids de entidad). */
	public record FusionElegir(BlockPos altar, int a, int b) implements CustomPacketPayload {
		public static final Type<FusionElegir> TYPE = tipo("fusion_elegir");
		public static final StreamCodec<RegistryFriendlyByteBuf, FusionElegir> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeBlockPos(v.altar);
					b.writeVarInt(v.a);
					b.writeVarInt(v.b);
				}, b -> new FusionElegir(b.readBlockPos(), b.readVarInt(), b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ---------------- servidor -> cliente ----------------

	public record AbrirNombre(int entidad) implements CustomPacketPayload {
		public static final Type<AbrirNombre> TYPE = tipo("abrir_nombre");
		public static final StreamCodec<RegistryFriendlyByteBuf, AbrirNombre> CODEC = StreamCodec.of(
				(b, v) -> b.writeVarInt(v.entidad), b -> new AbrirNombre(b.readVarInt()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Menú de órdenes de un esclavo (clic con la mano vacía). */
	public record AbrirOrdenes(int entidad, String nombre, boolean fusionado, boolean sigue, int estado, float[] merito)
			implements CustomPacketPayload {
		public AbrirOrdenes {
            if (merito == null || merito.length != 5) throw new IllegalArgumentException("Ficha sin aptitudes");
            merito = merito.clone();
            for(float valor:merito)if(!Float.isFinite(valor))throw new IllegalArgumentException("Aptitud no finita");
        }
		public static final Type<AbrirOrdenes> TYPE = tipo("abrir_ordenes");
		public static final StreamCodec<RegistryFriendlyByteBuf, AbrirOrdenes> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeVarInt(v.entidad);
					b.writeUtf(v.nombre, 64);
					b.writeBoolean(v.fusionado);
					b.writeBoolean(v.sigue);
					b.writeVarInt(v.estado);
                    for(float valor:v.merito)b.writeFloat(valor);
				}, b -> {
                    int entidad=b.readVarInt();String nombre=b.readUtf(64);
                    boolean fusionado=b.readBoolean(),sigue=b.readBoolean();int estado=b.readVarInt();
                    float[] merito=new float[5];for(int i=0;i<5;i++)merito[i]=b.readFloat();
                    return new AbrirOrdenes(entidad,nombre,fusionado,sigue,estado,merito);
                });

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Pantalla del Notario: situación del jugador y, si procede, el resultado de la última operación (clave de lang). */
	public record AbrirNotario(int entidad, int ocupadas, int libres, boolean reserva, int hambrienta, int vigilante,
			int acechante, boolean difunto, boolean difuntoFusionado, boolean devolver, boolean fusionables, String mensaje)
			implements CustomPacketPayload {
		public static final Type<AbrirNotario> TYPE = tipo("abrir_notario");
		public static final StreamCodec<RegistryFriendlyByteBuf, AbrirNotario> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeVarInt(v.entidad);
					b.writeVarInt(v.ocupadas);
					b.writeVarInt(v.libres);
					b.writeBoolean(v.reserva);
					b.writeVarInt(v.hambrienta);
					b.writeVarInt(v.vigilante);
					b.writeVarInt(v.acechante);
					b.writeBoolean(v.difunto);
					b.writeBoolean(v.difuntoFusionado);
					b.writeBoolean(v.devolver);
					b.writeBoolean(v.fusionables);
					b.writeUtf(v.mensaje, 96);
				}, b -> new AbrirNotario(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readVarInt(),
						b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readBoolean(),
						b.readUtf(96)));

		public static AbrirNotario de(int entidad, Comercio.Situacion s, String mensaje) {
			return new AbrirNotario(entidad, s.ocupadas(), s.libres(), s.reservaPendiente(), s.almas().hambrienta(),
					s.almas().vigilante(), s.almas().acechante(), s.hayDifunto(), s.hayDifuntoFusionado(), s.puedeDevolver(),
					s.fusionables(), mensaje == null ? "" : mensaje);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Candidato(int entidad, String nombre, int variante, float[] merito) {
        public Candidato {
            if(entidad<0 || nombre==null || nombre.length()>64 || variante<0 || variante>2 || merito==null || merito.length!=5)
                throw new IllegalArgumentException("Candidato de fusion invalido");
            merito=merito.clone();
            for(float m:merito)if(!Float.isFinite(m))throw new IllegalArgumentException("Aptitud no finita");
        }
	}

	/** Elección de la pareja a fundir: los candidatos cercanos con sus méritos para previsualizar el resultado. */
	public record AbrirFusion(BlockPos altar, List<Candidato> candidatos) implements CustomPacketPayload {
        public AbrirFusion {
            longitudAcotada(candidatos.size(),8);candidatos=List.copyOf(candidatos);
        }
		public static final Type<AbrirFusion> TYPE = tipo("abrir_fusion");
		public static final StreamCodec<RegistryFriendlyByteBuf, AbrirFusion> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeBlockPos(v.altar);
					b.writeVarInt(v.candidatos.size());
					for (Candidato c : v.candidatos) {
						b.writeVarInt(c.entidad);
						b.writeUtf(c.nombre, 64);
						b.writeVarInt(c.variante);
						for (int i = 0; i < 5; i++) {
							b.writeFloat(c.merito[i]);
						}
					}
				}, b -> {
					BlockPos pos = b.readBlockPos();
					int n = longitudAcotada(b.readVarInt(),8);
					List<Candidato> l = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						int id = b.readVarInt();
						String nom = b.readUtf(64);
						int var = b.readVarInt();
						float[] m = new float[5];
						for (int k = 0; k < 5; k++) {
							m[k] = b.readFloat();
						}
						l.add(new Candidato(id, nom, var, m));
					}
					return new AbrirFusion(pos, l);
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Linea(String clave, boolean cumplida, String detalle) {
	}

	/** Contenido de la lápida (TumbaScreen). */
	public record TumbaInfo(BlockPos pos, String nombre, int dia, int muertes, List<Linea> lineas, boolean listo,
			boolean enCurso, String causa) implements CustomPacketPayload {
        public TumbaInfo {
            longitudAcotada(lineas.size(),16);lineas=List.copyOf(lineas);
        }
		public static final Type<TumbaInfo> TYPE = tipo("tumba_info");
		public static final StreamCodec<RegistryFriendlyByteBuf, TumbaInfo> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeBlockPos(v.pos);
					b.writeUtf(v.nombre, 64);
					b.writeVarInt(v.dia);
					b.writeVarInt(v.muertes);
					b.writeVarInt(v.lineas.size());
					for (Linea l : v.lineas) {
						b.writeUtf(l.clave, 64);
						b.writeBoolean(l.cumplida);
						b.writeUtf(l.detalle, 128);
					}
					b.writeBoolean(v.listo);
					b.writeBoolean(v.enCurso);
					b.writeUtf(v.causa, 64);
				},
				b -> {
					BlockPos pos = b.readBlockPos();
					String nombre = b.readUtf(64);
					int dia = b.readVarInt();
					int muertes = b.readVarInt();
					int n = longitudAcotada(b.readVarInt(),16);
					List<Linea> ls = new ArrayList<>(n);
					for (int i = 0; i < n; i++) {
						ls.add(new Linea(b.readUtf(64), b.readBoolean(), b.readUtf(128)));
					}
					return new TumbaInfo(pos, nombre, dia, muertes, ls, b.readBoolean(), b.readBoolean(), b.readUtf(64));
				});

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Guion de la escena: fase (0 fuera, 1 conjuro, 2 pide, 3 sellado, 4 final), subtítulo y ritmo. */
	public record Escena(int fase, int tick, String subtitulo, float intensidad, boolean destello)
			implements CustomPacketPayload {
		public static final Type<Escena> TYPE = tipo("escena");
		public static final StreamCodec<RegistryFriendlyByteBuf, Escena> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeVarInt(v.fase);
					b.writeVarInt(v.tick);
					b.writeUtf(v.subtitulo, 128);
					b.writeFloat(v.intensidad);
					b.writeBoolean(v.destello);
				},
				b -> new Escena(b.readVarInt(), b.readVarInt(), b.readUtf(128), b.readFloat(), b.readBoolean()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Inicio/cancelacion del modelo de latigo; la autoridad sigue en el servidor. */
	public record LatigoAnimado(int entidad, net.minecraft.world.InteractionHand mano,
			net.minecraft.world.phys.Vec3 destino, boolean cancelar) implements CustomPacketPayload {
		public static final Type<LatigoAnimado> TYPE = tipo("latigo_animado");
		public static final StreamCodec<RegistryFriendlyByteBuf, LatigoAnimado> CODEC = StreamCodec.of(
				(b, v) -> {
					b.writeVarInt(v.entidad);
					b.writeBoolean(v.mano == net.minecraft.world.InteractionHand.OFF_HAND);
					b.writeDouble(v.destino.x); b.writeDouble(v.destino.y); b.writeDouble(v.destino.z);
					b.writeBoolean(v.cancelar);
				}, b -> new LatigoAnimado(b.readVarInt(), b.readBoolean() ? net.minecraft.world.InteractionHand.OFF_HAND
						: net.minecraft.world.InteractionHand.MAIN_HAND,
						new net.minecraft.world.phys.Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readBoolean()));
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	public static void registrar() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->LIMITE.desconectar(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->LIMITE.limpiar());
		PayloadTypeRegistry.clientboundPlay().register(LatigoAnimado.TYPE, LatigoAnimado.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Nombre.TYPE, Nombre.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AccionTumba.TYPE, AccionTumba.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbrirNombre.TYPE, AbrirNombre.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TumbaInfo.TYPE, TumbaInfo.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Escena.TYPE, Escena.CODEC);

		PayloadTypeRegistry.serverboundPlay().register(Orden.TYPE, Orden.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Comprar.TYPE, Comprar.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FusionElegir.TYPE, FusionElegir.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbrirOrdenes.TYPE, AbrirOrdenes.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbrirNotario.TYPE, AbrirNotario.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AbrirFusion.TYPE, AbrirFusion.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(Orden.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
            if(!admitir(context.server(),p,LimitePeticiones.Accion.ORDEN))return;
			ejecutar(context.server(),p,() -> {
                if(!sesionValida(context.server(),p) || payload.orden()<0 || payload.orden()>1)return;
				Entity e = p.level().getEntity(payload.entidad());
				if (e instanceof EsclavoEntity esclavo && esclavo.isAlive() && esclavo.esDe(p) && esclavo.distanceToSqr(p) <= 64.0) {
					p.sendSystemMessage(net.minecraft.network.chat.Component.translatable(esclavo.ordenar(p, payload.orden())), true);
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(Comprar.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
            if(!admitir(context.server(),p,LimitePeticiones.Accion.COMPRA))return;
			ejecutar(context.server(),p,() -> {
                if(!sesionValida(context.server(),p))return;
				Entity e = p.level().getEntity(payload.entidad());
				int o = payload.oferta();
				if (e instanceof NotarioEntity n && n.isAlive() && n.distanceToSqr(p) <= 64.0 && p.hasLineOfSight(n)
                        && o >= 0 && o < Comercio.Oferta.values().length) {
					String r = Comercio.comprar(p, Comercio.Oferta.values()[o]);
					ServerPlayNetworking.send(p, AbrirNotario.de(n.getId(), Comercio.situacion(p), r));
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(FusionElegir.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
            if(!admitir(context.server(),p,LimitePeticiones.Accion.FUSION))return;
			ejecutar(context.server(),p,() -> {
                if(sesionValida(context.server(),p) && payload.a()!=payload.b() && cercaCargado(p,payload.altar(),64))
                    Ceremonias.iniciarFusion(p,payload.altar(),payload.a(),payload.b());
            });
		});

		ServerPlayNetworking.registerGlobalReceiver(Nombre.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
            if(!admitir(context.server(),p,LimitePeticiones.Accion.NOMBRE))return;
			ejecutar(context.server(),p,() -> {
                if(!sesionValida(context.server(),p))return;
				Entity e = p.level().getEntity(payload.entidad());
				if (e instanceof EsclavoEntity esclavo && esclavo.isAlive() && esclavo.esDe(p) && esclavo.distanceToSqr(p)<=64.0) {
					esclavo.bautizar(p, payload.nombre());
				}
			});
		});
		ServerPlayNetworking.registerGlobalReceiver(AccionTumba.TYPE, (payload, context) -> {
			ServerPlayer p = context.player();
            if(!admitir(context.server(),p,LimitePeticiones.Accion.TUMBA))return;
			ejecutar(context.server(),p,() -> {
                if(sesionValida(context.server(),p) && payload.accion()>=0 && payload.accion()<=1 && cercaCargado(p,payload.pos(),100))
                    Ritual.accion(p,payload.pos(),payload.accion());
            });
		});
	}
    public static int longitudAcotada(int n,int maximo) {
        if(n<0 || n>maximo)throw new io.netty.handler.codec.DecoderException("Lista de paquete fuera de limite");
        return n;
    }
    private static boolean sesionValida(net.minecraft.server.MinecraftServer server,ServerPlayer p) {
        return server.getPlayerList().getPlayer(p.getUUID())==p && p.connection.isAcceptingMessages() && p.isAlive()
                && !p.isSpectator() && !com.theslave.persistencia.CustodiaJugador.ocupada(p.getUUID());
    }
    private static boolean admitir(net.minecraft.server.MinecraftServer server,ServerPlayer p,LimitePeticiones.Accion accion) {
        return sesionValida(server,p)&&LIMITE.admitir(p.getUUID(),accion,System.nanoTime()/1_000_000L);
    }
    private static void ejecutar(net.minecraft.server.MinecraftServer server,ServerPlayer p,Runnable accion) {
        server.execute(()->{
            if(!sesionValida(server,p))return;
            try {accion.run();}
            catch(RuntimeException error) {
                // Los coordinadores ya conservan el WAL: no continuar ni enviar una pantalla de exito.
                if(p.connection.isAcceptingMessages()) {
                    TheSlaveMod.LOGGER.error("Peticion de TheSlave rechazada por error para {}",p.getUUID(),error);
                    p.connection.disconnect(net.minecraft.network.chat.Component.literal("TheSlave: no se pudo completar la peticion. Revisa el registro del servidor."));
                }
            }
        });
    }
    private static boolean cercaCargado(ServerPlayer p,BlockPos pos,double rango2) {
        return p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))<=rango2 && p.level().hasChunkAt(pos);
    }
}
