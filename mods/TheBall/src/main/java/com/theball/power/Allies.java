package com.theball.power;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

/** Quién no debe recibir daño de los poderes de un jugador: él mismo, su equipo y sus mascotas. */
public final class Allies {
	private Allies() {
	}

	public static boolean areAllies(Entity user, Entity target) {
		if (user == null || target == null) {
			return false;
		}
		if (user == target || user.isAlliedTo(target)) {
			return true;
		}
		if (target instanceof OwnableEntity pet && pet.getOwner() == user) {
			return true;
		}
		// Un jugador en creativo/espectador nunca es objetivo.
		return target instanceof Player p && (p.isSpectator() || p.isCreative());
	}

	/** Objetivo válido de un poder: vivo, no aliado. */
	public static boolean isValidTarget(Entity user, Entity target) {
		return target.isAlive() && !target.isSpectator() && !areAllies(user, target);
	}
}
