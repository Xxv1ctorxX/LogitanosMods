package com.theball.registry;

import com.theball.TheBallMod;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Logros del mod. Son JSON en {@code data/theball/advancement/} con el criterio {@code minecraft:impossible}
 * ("done"): no se consiguen solos, los otorga el código en el momento justo.
 */
public final class ModAdvancements {
	public static final String SEAT = "seat";
	public static final String PATRON = "patron";
	public static final String CORONATION = "coronation";
	public static final String ROYAL_GOAL = "royal_goal";
	public static final String EXPLOSIVE = "explosive";

	private ModAdvancements() {
	}

	public static void grant(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(TheBallMod.id(name));
		if (holder != null) {
			player.getAdvancements().award(holder, "done");
		}
	}
}
