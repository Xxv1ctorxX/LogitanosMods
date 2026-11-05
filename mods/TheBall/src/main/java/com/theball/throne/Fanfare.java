package com.theball.throne;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

/** Celebraciones: cohetes reales, partículas y sonidos. */
public final class Fanfare {
	public static final int SILVER = 0xD8DEE6;
	public static final int WHITE = 0xFFFFFF;
	public static final int GOLD = 0xFFC830;
	public static final int SKY = 0x7FC8FF;

	private Fanfare() {
	}

	/** Lanza un cohete de verdad que explota a la altura indicada (aprox.). */
	public static void rocket(ServerLevel level, Vec3 pos, int flight, FireworkExplosion.Shape shape, int... colors) {
		ItemStack stack = new ItemStack(Items.FIREWORK_ROCKET);
		stack.set(DataComponents.FIREWORKS, new Fireworks(flight, List.of(
				new FireworkExplosion(shape, IntList.of(colors), IntList.of(WHITE), true, true))));
		FireworkRocketEntity rocket = new FireworkRocketEntity(level, pos.x, pos.y, pos.z, stack);
		level.addFreshEntity(rocket);
	}

	/** Hito de 100 hojas: chispas, campana y mensaje. */
	public static void milestone(ServerLevel level, Vec3 top, int milestone, ServerPlayer feeder, int xpOfMilestone) {
		level.sendParticles(ParticleTypes.FIREWORK, top.x, top.y + 0.3, top.z, 30, 0.4, 0.3, 0.4, 0.08);
		level.sendParticles(ParticleTypes.END_ROD, top.x, top.y + 0.2, top.z, 12, 0.3, 0.4, 0.3, 0.04);
		play(level, top, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.8f + milestone * 0.03f);
		play(level, top, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8f, 1.2f);
		if (feeder != null) {
			feeder.sendSystemMessage(Component.translatable("message.theball.throne.milestone", milestone, xpOfMilestone));
		}
	}

	/** Umbral de recompensa (250/500/1000/1500): cohetes, mensaje a los de cerca. */
	public static void threshold(ServerLevel level, Vec3 top, Component message) {
		rocket(level, top.add(0.0, 0.5, 0.0), 1, FireworkExplosion.Shape.LARGE_BALL, SILVER, SKY);
		rocket(level, top.add(1.5, 0.5, 0.0), 2, FireworkExplosion.Shape.STAR, SILVER, WHITE);
		rocket(level, top.add(-1.5, 0.5, 0.0), 2, FireworkExplosion.Shape.BURST, SKY, WHITE);
		play(level, top, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y + 0.5, top.z, 60, 0.8, 0.8, 0.8, 0.4);
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(top) < 64.0 * 64.0) {
				p.sendSystemMessage(message);
			}
		}
	}

	public static void play(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, volume, pitch);
	}
}
