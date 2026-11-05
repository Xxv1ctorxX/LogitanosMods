package com.theslave.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theslave.TheSlaveMod;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Datos que viajan en los objetos: almas del relicario/ofrendas y la ficha del pacto. */
public final class ModComponents {
	public static final UUID NINGUNO = new UUID(0L, 0L);

	/** Contadores de los tres tipos de alma. Inmutable. */
	public record Almas(int hambrienta, int vigilante, int acechante) {
        public Almas {
            if (hambrienta < 0 || vigilante < 0 || acechante < 0 || (long)hambrienta + vigilante + acechante > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Contadores de almas invalidos");
        }
		public static final Almas VACIO = new Almas(0, 0, 0);
		public static final int CAPACIDAD = 64;
		public static final Codec<Almas> CODEC = RecordCodecBuilder.<Almas>create(i -> i.group(
				Codec.intRange(0, 64).optionalFieldOf("hambrienta", 0).forGetter(Almas::hambrienta),
				Codec.intRange(0, 64).optionalFieldOf("vigilante", 0).forGetter(Almas::vigilante),
				Codec.intRange(0, 64).optionalFieldOf("acechante", 0).forGetter(Almas::acechante)).apply(i, Almas::new)).validate(a -> a.total() <= CAPACIDAD ? DataResult.success(a) : DataResult.error(() -> "El recipiente supera las 64 almas"));
		public static final StreamCodec<ByteBuf, Almas> STREAM = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Almas::hambrienta,
				ByteBufCodecs.VAR_INT, Almas::vigilante,
				ByteBufCodecs.VAR_INT, Almas::acechante, Almas::new);

		public int total() {
			return hambrienta + vigilante + acechante;
		}

		public Almas mas(Almas o) {
			return new Almas(hambrienta + o.hambrienta, vigilante + o.vigilante, acechante + o.acechante);
		}

		public Almas menos(Almas o) {
			return new Almas(hambrienta - o.hambrienta, vigilante - o.vigilante, acechante - o.acechante);
		}

		public boolean contiene(Almas o) {
			return hambrienta >= o.hambrienta && vigilante >= o.vigilante && acechante >= o.acechante;
		}

		public static Almas cada(int n) {
			return new Almas(n, n, n);
		}
	}

	/** Ficha de un pacto: dueño, contrato (o vínculo objetivo en los tributos) y grado (0, 1, 2). */
	public record Pacto(UUID dueno, UUID referencia, int grado) {
        public Pacto {
            java.util.Objects.requireNonNull(dueno, "dueno");
            java.util.Objects.requireNonNull(referencia, "referencia");
            if (grado < 0 || grado > 2) throw new IllegalArgumentException("Grado de pacto fuera de rango");
        }
		public static final Codec<Pacto> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("dueno").forGetter(Pacto::dueno),
				UUIDUtil.CODEC.fieldOf("referencia").forGetter(Pacto::referencia),
				Codec.intRange(0, 2).optionalFieldOf("grado", 0).forGetter(Pacto::grado)).apply(i, Pacto::new));
		public static final StreamCodec<ByteBuf, Pacto> STREAM = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, Pacto::dueno,
				UUIDUtil.STREAM_CODEC, Pacto::referencia,
				ByteBufCodecs.VAR_INT, Pacto::grado, Pacto::new);
	}

    /** Individuo difunto, distinto de los ecos de enemigos que se usan como precio. */
    public record AlmaVinculada(UUID dueno, UUID vinculo, int generacion, UUID token, boolean fusionado) {
        public AlmaVinculada {
            java.util.Objects.requireNonNull(dueno);java.util.Objects.requireNonNull(vinculo);java.util.Objects.requireNonNull(token);
            if(generacion<1)throw new IllegalArgumentException("Generacion de alma invalida");
        }
        public static final Codec<AlmaVinculada> CODEC=RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("dueno").forGetter(AlmaVinculada::dueno),
                UUIDUtil.CODEC.fieldOf("vinculo").forGetter(AlmaVinculada::vinculo),
                Codec.intRange(1,Integer.MAX_VALUE).fieldOf("generacion").forGetter(AlmaVinculada::generacion),
                UUIDUtil.CODEC.fieldOf("token").forGetter(AlmaVinculada::token),
                Codec.BOOL.fieldOf("fusionado").forGetter(AlmaVinculada::fusionado)).apply(i,AlmaVinculada::new));
        public static final StreamCodec<ByteBuf,AlmaVinculada> STREAM=StreamCodec.of((b,a) -> {
            UUIDUtil.STREAM_CODEC.encode(b,a.dueno);UUIDUtil.STREAM_CODEC.encode(b,a.vinculo);
            b.writeInt(a.generacion);UUIDUtil.STREAM_CODEC.encode(b,a.token);b.writeBoolean(a.fusionado);
        },b -> new AlmaVinculada(UUIDUtil.STREAM_CODEC.decode(b),UUIDUtil.STREAM_CODEC.decode(b),b.readInt(),UUIDUtil.STREAM_CODEC.decode(b),b.readBoolean()));
    }

	public static final DataComponentType<Almas> ALMAS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			TheSlaveMod.id("almas"),
			DataComponentType.<Almas>builder().persistent(Almas.CODEC).networkSynchronized(Almas.STREAM).build());

	public static final DataComponentType<Pacto> PACTO = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			TheSlaveMod.id("pacto"),
			DataComponentType.<Pacto>builder().persistent(Pacto.CODEC).networkSynchronized(Pacto.STREAM).build());

    public static final DataComponentType<AlmaVinculada> ALMA_VINCULADA=Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            TheSlaveMod.id("alma_vinculada"),DataComponentType.<AlmaVinculada>builder().persistent(AlmaVinculada.CODEC)
                    .networkSynchronized(AlmaVinculada.STREAM).build());

	private ModComponents() {
	}

	public static void register() {
	}
}
