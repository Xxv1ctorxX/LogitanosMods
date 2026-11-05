package com.theball.throne;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Lo que ha aportado un jugador a una bola: papeles realmente gastados. Se guarda el nombre (el último conocido)
 * para poder mostrarlo sin buscar el perfil (holograma, medalla, /theball throne info).
 */
public record Contributor(UUID uuid, String name, int foil, int late) {
	/** Sin papel del tramo final (premios, medallas...). */
	public Contributor(UUID uuid, String name, int foil) {
		this(uuid, name, foil, 0);
	}

	public static final Codec<Contributor> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("uuid").forGetter(Contributor::uuid),
			Codec.STRING.fieldOf("name").forGetter(Contributor::name),
			Codec.INT.fieldOf("foil").forGetter(Contributor::foil),
			Codec.INT.optionalFieldOf("late", 0).forGetter(Contributor::late)
	).apply(i, Contributor::new));

	public static final Codec<List<Contributor>> LIST_CODEC = CODEC.listOf();

	public static final StreamCodec<ByteBuf, Contributor> STREAM_CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, Contributor::uuid,
			ByteBufCodecs.STRING_UTF8, Contributor::name,
			ByteBufCodecs.VAR_INT, Contributor::foil,
			ByteBufCodecs.VAR_INT, Contributor::late,
			Contributor::new);

	public static final StreamCodec<ByteBuf, List<Contributor>> LIST_STREAM_CODEC =
			STREAM_CODEC.apply(ByteBufCodecs.list());

	public Contributor plus(String newName, int moreFoil, int moreLate) {
		return new Contributor(uuid, newName != null ? newName : name, foil + moreFoil, late + moreLate);
	}
}
