package com.theball.registry;

import com.theball.TheBallMod;
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

	private ModComponents() {
	}

	/** Llamarlo desde el initializer basta para que se cargue la clase y se registre el componente. */
	public static void register() {
	}
}
