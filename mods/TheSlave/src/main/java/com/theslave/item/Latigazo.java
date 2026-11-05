package com.theslave.item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Sonidos y sincronizacion del latigazo; la cuerda la dibuja el modelo del cliente. */
public final class Latigazo {
	private static final int FRAMES = 9;
	private static final int RECUP = 9;
	private static final double ALCANCE = 4.5;
	private static final List<Activo> ACTIVOS = new ArrayList<>();

	private Latigazo() {
	}

	private static final class Activo {
		final ServerLevel level;
		final ServerPlayer jugador;
		final Vec3 destino;
		final net.minecraft.world.InteractionHand mano;
		final int ranura;
		int frame;

		Activo(ServerLevel level, ServerPlayer jugador, Vec3 destino, net.minecraft.world.InteractionHand mano) {
			this.level = level;
			this.jugador = jugador;
			this.destino = destino;
			this.mano = mano;
			ranura = jugador.getInventory().getSelectedSlot();
		}
	}

	/** Lanza el latigazo desde el jugador hasta un punto. */
	public static void lanzar(ServerPlayer p, Vec3 destino, net.minecraft.world.InteractionHand mano) {
		if (p.level() instanceof ServerLevel sl) {
			if (ACTIVOS.stream().anyMatch(a -> a.jugador == p)) return;
			sl.playSound(null, p.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8f, 0.5f);
			Activo a = new Activo(sl, p, destino, mano);
			ACTIVOS.add(a);
			sincronizar(a, false);
		}
	}

	private static void sincronizar(Activo a, boolean cancelar) {
		var paquete = new com.theslave.net.Payloads.LatigoAnimado(a.jugador.getId(), a.mano, a.destino, cancelar);
		for (ServerPlayer observador : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(a.jugador)) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(observador, paquete);
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(a.jugador, paquete);
	}

	public static void reiniciar() { ACTIVOS.clear(); }

	/** Latigazo al aire: unos cuatro bloques hacia donde mira. */
	public static void alAire(ServerPlayer p, net.minecraft.world.InteractionHand mano) {
		// desde los ojos, hasta 4,5 bloques; un bloque solido corta el recorrido (el latigo no atraviesa paredes)
		Vec3 ojos = p.getEyePosition();
		Vec3 fin = ojos.add(p.getLookAngle().scale(ALCANCE));
		net.minecraft.world.phys.BlockHitResult r = p.level().clip(new net.minecraft.world.level.ClipContext(
				ojos, fin, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		lanzar(p, r.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? fin : r.getLocation(), mano);
	}

	public static void tick() {
		Iterator<Activo> it = ACTIVOS.iterator();
		while (it.hasNext()) {
			Activo a = it.next();
			if (!a.jugador.isAlive() || a.jugador.isRemoved() || a.jugador.level() != a.level
					|| !a.jugador.getItemInHand(a.mano).is(com.theslave.registry.ModItems.LATIGO)
					|| (a.mano == net.minecraft.world.InteractionHand.MAIN_HAND && a.jugador.getInventory().getSelectedSlot() != a.ranura)) {
				sincronizar(a, true);
				it.remove();
				continue;
			}
			a.frame++;
			if (a.frame <= FRAMES) {
				// silbido que sube de tono segun la tira acelera
				if (a.frame >= 4 && a.frame % 2 == 0 && a.frame < FRAMES) {
					a.level.playSound(null, a.jugador.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.3f, 0.9f + 0.15f * a.frame);
				}
				if (a.frame == FRAMES) {
					chasquido(a);
				}
			} else {
				int r = a.frame - FRAMES;
				if (r == 1) {
					a.level.playSound(null, a.jugador.blockPosition(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.3f, 1.2f);
				}
				if (r >= RECUP) {
					it.remove();
				}
			}
		}
	}

	private static void chasquido(Activo a) {
		BlockPos pos = BlockPos.containing(a.destino);
		// impulso seco y agudo, con eco corto
		a.level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 2.0f);
		a.level.playSound(null, pos, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7f, 1.6f);
		a.level.playSound(null, pos, SoundEvents.FIREWORK_ROCKET_BLAST_FAR, SoundSource.PLAYERS, 0.3f, 1.5f);
	}
}
