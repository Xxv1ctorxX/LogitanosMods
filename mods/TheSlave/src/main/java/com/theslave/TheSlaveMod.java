package com.theslave;

import com.theslave.net.Payloads;
import com.theslave.registry.ModBlocks;
import com.theslave.registry.ModEntities;
import com.theslave.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TheSlaveMod implements ModInitializer {
	public static final String MOD_ID = "theslave";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Config.cargar();
        com.theslave.ai.Conocimiento.registrar();
		com.theslave.registry.ModComponents.register();
		com.theslave.registry.BrujulaRecipe.register();
		ModBlocks.register();
		ModItems.register();
		ModEntities.register();
		Payloads.registrar();
		Comandos.registrar();
		com.theslave.ritual.Ritual.registrar();
		com.theslave.ritual.Ceremonias.registrar();
		com.theslave.ritual.Captura.registrar();
		com.theslave.worldgen.Enclaves.registrar();
		com.theslave.persistencia.TransaccionJugador.registrar();
		com.theslave.ritual.EntityDurability.registrar();
		com.theslave.ritual.RitualDurable.registrar();
		com.theslave.ritual.MuerteDurable.registrar();
        com.theslave.ritual.Hogares.registrar();
        com.theslave.persistencia.TransferenciasDurables.registrar();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> com.theslave.item.Latigazo.reiniciar());
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> com.theslave.item.Latigazo.tick());
		LOGGER.info("The Slave cargado. Que trabaje.");
	}
}
