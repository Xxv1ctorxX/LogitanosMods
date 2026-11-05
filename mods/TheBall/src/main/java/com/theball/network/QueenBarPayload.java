package com.theball.network;

import com.theball.TheBallMod;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Datos de la barra de la Reina para un jugador (servidor → cliente). {@code show = false} la oculta. El cliente la
 * dibuja él (ver QueenBarHud); el servidor solo decide cuándo se mira a la Reina y manda cifras.
 */
public record QueenBarPayload(boolean show, int sheets, int shareTenths, boolean crowned,
		List<String> topNames, List<Integer> topFoil) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<QueenBarPayload> TYPE = new CustomPacketPayload.Type<>(TheBallMod.id("queen_bar"));
	public static final StreamCodec<RegistryFriendlyByteBuf, QueenBarPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, QueenBarPayload::show,
			ByteBufCodecs.VAR_INT, QueenBarPayload::sheets,
			ByteBufCodecs.VAR_INT, QueenBarPayload::shareTenths,
			ByteBufCodecs.BOOL, QueenBarPayload::crowned,
				ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(3)), QueenBarPayload::topNames,
				ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(3)), QueenBarPayload::topFoil,
			QueenBarPayload::new);

	public static final QueenBarPayload HIDE = new QueenBarPayload(false, 0, 0, false, List.of(), List.of());

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
