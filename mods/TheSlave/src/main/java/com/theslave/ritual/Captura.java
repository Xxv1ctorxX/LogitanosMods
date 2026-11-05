package com.theslave.ritual;

import com.theslave.comercio.Comercio;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;


/**
 * Captura automática de almas: si un jugador mata en cuerpo a cuerpo a un zombi, un esqueleto o una araña y lleva un
 * Relicario de Ánimas con hueco, el alma entra sola. Las muertes a distancia, por fuego o por terceros no cuentan.
 */
public final class Captura {
	private Captura() {
	}

	public static void registrar() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entidad, fuente) -> {
			if (!(entidad.level() instanceof ServerLevel sl) || !(fuente.getEntity() instanceof ServerPlayer p)
					|| fuente.getDirectEntity() != p) {
				return;
			}
			var clave = BuiltInRegistries.ENTITY_TYPE.getKey(entidad.getType());
			String n = "minecraft".equals(clave.getNamespace()) ? clave.getPath() : "";
			int tipo = n.equals("zombie") ? 0 : n.equals("skeleton") ? 1 : n.equals("spider") ? 2 : -1;
			if (tipo < 0 || !Comercio.capturar(p.getInventory(), tipo)) {
				return;
			}
			sl.sendParticles(ParticleTypes.SOUL, entidad.getX(), entidad.getY() + 0.8, entidad.getZ(), 6, 0.2, 0.4, 0.2, 0.03);
			sl.playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.6f, 0.8f + tipo * 0.15f);
			p.sendSystemMessage(Component.translatable("theslave.alma.capturada." + tipo), true);
		});
	}
}
