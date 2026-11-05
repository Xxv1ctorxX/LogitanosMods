package com.theball.power;

import com.theball.config.TheBallConfig;
import com.theball.entity.ExplosiveShotEntity;
import com.theball.entity.FoilShotEntity;
import com.theball.item.KingArmor;
import com.theball.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bola rebotona, bola explosiva y Embestida: los poderes de diario del cetro. */
public final class ScepterPowers {
	/** Espera de la Embestida (no se usa el cooldown del item para no bloquear la bola rebotona). */
	private static final Map<UUID, Long> DASH_READY = new HashMap<>();

	private ScepterPowers() {
	}

	// ------------------------------------------------------------------ Bola rebotona

	public static void bouncyBall(ServerPlayer player, ItemStack scepter) {
		TheBallConfig.BouncyBall cfg = TheBallConfig.get().scepter.bouncyBall;
		if (player.getCooldowns().isOnCooldown(scepter)) {
			return;
		}
		boolean free = cfg.freeWithFullSet && KingArmor.hasFullSet(player);
		if (!free && !FoilFuel.take(player, cfg.costFoil)) {
			return;
		}
		ServerLevel level = player.level();
		FoilShotEntity shot = new FoilShotEntity(ModEntities.FOIL_SHOT, level);
		shot.setOwner(player);
		Vec3 eye = player.getEyePosition();
		shot.setPos(eye.x, eye.y - 0.1, eye.z);
		shot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.6f, 0.5f);
		level.addFreshEntity(shot);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
				SoundSource.PLAYERS, 0.6f, 1.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 0.6f, 1.8f);
		player.getCooldowns().addCooldown(scepter, cfg.cooldownTicks);
	}

	// ------------------------------------------------------------------ Bola explosiva

	public static void explosiveBall(ServerPlayer player, ItemStack scepter, float power) {
		TheBallConfig.ExplosiveBall cfg = TheBallConfig.get().scepter.explosiveBall;
		if (player.getCooldowns().isOnCooldown(scepter)) {
			return;
		}
		if (!FoilFuel.take(player, cfg.costFoil)) {
			return;
		}
		ServerLevel level = player.level();
		ExplosiveShotEntity shot = new ExplosiveShotEntity(ModEntities.EXPLOSIVE_SHOT, level);
		shot.setOwner(player);
		shot.setCustomBlast((float) (cfg.damage * (1.0 + 0.5 * power)), cfg.radius * (1.0 + 0.4 * power));
		Vec3 eye = player.getEyePosition();
		shot.setPos(eye.x, eye.y - 0.2, eye.z);
		shot.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 2.2f + 1.0f * power, 0.2f);
		level.addFreshEntity(shot);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE,
				SoundSource.PLAYERS, 0.9f, 1.3f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 0.8f, 0.8f);
		player.getCooldowns().addCooldown(scepter, cfg.cooldownTicks);
	}

	// ------------------------------------------------------------------ Embestida

	/** Impulso hacia donde miras; aparta (y hiere un poco) a quien este en el camino. */
	public static void dash(ServerPlayer player, ItemStack scepter) {
		TheBallConfig.Dash cfg = TheBallConfig.get().scepter.dash;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long ready = DASH_READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.theball.cooldown",
					Component.translatable("power.theball.dash"), (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!FoilFuel.take(player, cfg.costFoil)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 dir = new Vec3(look.x, Math.max(look.y * 0.6, 0.12), look.z).normalize();
		Vec3 start = player.position();
		player.setDeltaMovement(dir.scale(cfg.speed));
		player.syncVelocity = true;
		player.fallDistance = 0.0;

		AABB path = player.getBoundingBox().expandTowards(dir.scale(4.0)).inflate(0.6);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, path, e -> e != player && Allies.isValidTarget(player, e))) {
			e.hurtServer(level, level.damageSources().playerAttack(player), cfg.damage);
			e.push(dir.x * 1.2, 0.35, dir.z * 1.2);
			e.syncVelocity = true;
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
		}
		for (int i = 0; i < 14; i++) {
			Vec3 p = start.add(dir.scale(i * 0.35)).add(0.0, 0.9, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.1, 0.2, 0.1, 0.01);
		}
		level.sendParticles(ParticleTypes.CLOUD, start.x, start.y + 0.1, start.z, 8, 0.3, 0.05, 0.3, 0.05);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.2f);
		DASH_READY.put(player.getUUID(), now + cfg.cooldownTicks);
		player.getCooldowns().addCooldown(scepter, 10);
	}

	public static void resetCooldowns(ServerPlayer player) {
		DASH_READY.remove(player.getUUID());
		player.getCooldowns().removeCooldown(
				net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(com.theball.registry.ModItems.ALUMINUM_SCEPTER));
	}

	public static void forget(UUID player) {
		DASH_READY.remove(player);
	}
}
