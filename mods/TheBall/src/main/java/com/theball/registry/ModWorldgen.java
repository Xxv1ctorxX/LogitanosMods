package com.theball.registry;

import com.theball.TheBallMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Añade el aluminio a todos los biomas del Overworld, con la misma frecuencia que el hierro. */
public final class ModWorldgen {
	private ModWorldgen() {
	}

	public static void register() {
		for (String name : new String[] {"ore_aluminum_upper", "ore_aluminum_middle", "ore_aluminum_small"}) {
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.UNDERGROUND_ORES,
					ResourceKey.create(Registries.PLACED_FEATURE, TheBallMod.id(name)));
		}
	}
}
