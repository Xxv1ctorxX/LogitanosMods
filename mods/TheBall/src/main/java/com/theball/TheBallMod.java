package com.theball;

import com.theball.registry.ModBlocks;
import com.theball.registry.ModComponents;
import com.theball.registry.ModEntities;
import com.theball.registry.ModItems;
import com.theball.registry.ModWorldgen;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheBallMod implements ModInitializer {
	public static final String MOD_ID = "theball";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// El orden importa: el componente lo usan los items, y los items los usa la entidad.
		ModComponents.register();
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();

		ModWorldgen.register();

		LOGGER.info("The Ball cargado: ¡a rodar!");
	}
}
