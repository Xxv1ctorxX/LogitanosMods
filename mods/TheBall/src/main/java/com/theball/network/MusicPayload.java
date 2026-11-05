package com.theball.network;

import com.theball.TheBallMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Música de fondo del Coronamiento (servidor → cliente). El cliente la reproduce él para poder difuminarla: START la
 * empieza, FADE la baja poco a poco durante {@code ticks} y STOP la corta.
 */
public record MusicPayload(int action, int ticks) implements CustomPacketPayload {
	public static final int START = 0;
	public static final int FADE = 1;
	public static final int STOP = 2;

	public static final CustomPacketPayload.Type<MusicPayload> TYPE = new CustomPacketPayload.Type<>(TheBallMod.id("coronation_music"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MusicPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MusicPayload::action,
			ByteBufCodecs.VAR_INT, MusicPayload::ticks,
			MusicPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
