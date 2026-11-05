package com.theball.throne;

import com.theball.config.TheBallConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Paga el XP de cada hoja añadida en el trono a quien pone el papel. Todas las hojas valen lo mismo
 * ({@code xp.per_sheet}); el plus grande es el bonus de cada hito de 100 hojas (tabla {@code xp.milestone_xp}), que
 * se paga aparte con {@link #payMilestone}. Como el XP por hoja puede no ser entero, los decimales se acumulan por jugador en
 * memoria y se entrega el entero (como mucho se pierde 1 XP si el jugador se desconecta).
 *
 * No guarda nada en disco: cada hoja se paga una sola vez, en el momento de añadirla, y las hojas solo suben.
 */
public final class XpPayout {
	private static final Map<UUID, Double> REMAINDER = new HashMap<>();

	private XpPayout() {
	}

	/** Paga la hoja número {@code sheet} (la que se acaba de añadir) y devuelve el XP entero entregado. */
	public static int pay(ServerPlayer player, int sheet, Vec3 from) {
		double xp = TheBallConfig.get().xpForSheet(sheet);
		double total = REMAINDER.getOrDefault(player.getUUID(), 0.0) + xp;
		int whole = (int) Math.floor(total);
		REMAINDER.put(player.getUUID(), total - whole);
		if (whole > 0) {
			player.giveExperiencePoints(whole);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.25f,
					0.9f + player.getRandom().nextFloat() * 0.4f);
			sparkTrail(player.level(), from, player.getEyePosition().subtract(0, 0.4, 0), Math.min(6, 1 + whole / 10));
		}
		return whole;
	}

	/** Bonus de hito: se entrega entero a quien pone la hoja que completa las 100. */
	public static void payMilestone(ServerPlayer player, int xp, Vec3 from) {
		if (xp <= 0) {
			return;
		}
		player.giveExperiencePoints(xp);
		sparkTrail(player.level(), from, player.getEyePosition().subtract(0, 0.4, 0), 6);
	}

	/** Estela de chispas verdes de la bola al jugador: el XP "vuela" hacia él. */
	private static void sparkTrail(ServerLevel level, Vec3 from, Vec3 to, int points) {
		Vec3 delta = to.subtract(from);
		for (int i = 1; i <= points; i++) {
			Vec3 p = from.add(delta.scale(i / (double) (points + 1)));
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	public static void forget(UUID player) {
		REMAINDER.remove(player);
	}

	/** XP total de un hito (para mensajes). */
	public static int milestoneXp(int milestone) {
		int[] table = TheBallConfig.get().xp.milestoneXp;
		return milestone >= 1 && milestone <= table.length ? table[milestone - 1] : 0;
	}
}
