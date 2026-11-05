package com.theball;

import com.theball.command.TheBallCommands;
import com.theball.config.TheBallConfig;
import com.theball.power.CrownPowers;
import com.theball.power.ExplosiveAbility;
import com.theball.power.ScepterPowers;
import com.theball.registry.ModAttachments;
import com.theball.registry.ModBlockEntities;
import com.theball.registry.ModBlocks;
import com.theball.registry.ModSounds;
import com.theball.registry.ModComponents;
import com.theball.registry.ModEntities;
import com.theball.registry.ModItems;
import com.theball.registry.ModWorldgen;
import com.theball.throne.XpPayout;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
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
		TheBallConfig.load();

		// El orden importa: el componente lo usan los items, los bloques los usan sus block entities y los items,
		// y los items los usa la entidad.
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
				com.theball.network.QueenBarPayload.TYPE, com.theball.network.QueenBarPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(
				com.theball.network.MusicPayload.TYPE, com.theball.network.MusicPayload.CODEC);
		ModSounds.register();
		ModComponents.register();
		ModBlocks.register();
		ModBlockEntities.register();
		ModItems.register();
		ModEntities.register();
		ModAttachments.register();

		ModWorldgen.register();

		registerEvents();

		LOGGER.info("The Ball cargado: ¡a rodar! (y larga vida a la Reina de Aluminio)");
	}

	private static void registerEvents() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				TheBallCommands.register(dispatcher));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			CrownPowers.tick(server);
			ExplosiveAbility.tick(server);
		});

		com.theball.world.OreRetrogen.register();
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ExplosiveAbility.clearAll();
			com.theball.world.OreRetrogen.clear();
			com.theball.throne.Coronation.clearAll();
		});

		// Sin daño de caída con la Corona; inmune a proyectiles en Modo Bola.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CrownPowers::allowDamage);

		// Interrupciones y limpieza de estados en memoria.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				ExplosiveAbility.interrupt(player);
				CrownPowers.forget(player);
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			ExplosiveAbility.interrupt(player);
			CrownPowers.forget(player);
		});
		// Tras un crash en pleno Modo Dios el jugador se guarda con NoGravity=true y se quedaba flotando:
		// al entrar no hay ningún modo activo (estado solo en memoria), así que se restaura.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			if (player.isNoGravity() && !player.isSpectator()) {
				player.setNoGravity(false);
				player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.SLOW_FALLING, 200, 0, false, false, true));
			}
			CrownPowers.forget(player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			ExplosiveAbility.interrupt(player);
			CrownPowers.forget(player);
			ScepterPowers.forget(player.getUUID());
			XpPayout.forget(player.getUUID());
		});
	}
}
