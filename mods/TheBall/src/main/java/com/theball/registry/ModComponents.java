package com.theball.registry;

import com.theball.TheBallMod;
import com.theball.throne.Contributor;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;

public final class ModComponents {
	/** Número de hojas de papel de aluminio de una bola (item). */
	public static final DataComponentType<Integer> SHEETS = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			TheBallMod.id("sheets"),
			DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.POSITIVE_INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** Quién ha puesto cuánto papel en la bola (para repartir las recompensas del trono). */
	public static final DataComponentType<List<Contributor>> CONTRIBUTIONS = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			TheBallMod.id("contributions"),
			DataComponentType.<List<Contributor>>builder()
					.persistent(Contributor.LIST_CODEC)
					.networkSynchronized(Contributor.LIST_STREAM_CODEC)
					.build());

	/** Máscara de bits con los premios del trono ya entregados (250, 500, 1000, 1500, 2000 hojas). */
	public static final DataComponentType<Integer> REWARDS_GIVEN = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			TheBallMod.id("rewards_given"),
			DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.NON_NEGATIVE_INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** Medalla del tributo: papeles que aportó su dueño a la Reina. */
	public static final DataComponentType<Contributor> MEDAL = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			TheBallMod.id("medal"),
			DataComponentType.<Contributor>builder()
					.persistent(Contributor.CODEC)
					.networkSynchronized(Contributor.STREAM_CODEC)
					.build());

	private ModComponents() {
	}

	/** Llamarlo desde el initializer basta para que se cargue la clase y se registre el componente. */
	public static void register() {
	}
}
