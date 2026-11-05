package com.theball.power;

import com.theball.TheBallMod;
import com.theball.config.TheBallConfig;
import com.theball.entity.PowerVisualEntity;
import com.theball.item.KingArmor;
import com.theball.registry.ModAttachments;
import com.theball.registry.ModEntities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.Vec3;

/**
 * Poderes de la Corona de aluminio (basta con llevarla puesta):
 * <ul>
 *   <li><b>Rebote</b> (pasivo): sin daño de caída; al caer desde alto rebotas (agachado no).</li>
 *   <li><b>Reflejo</b> (pasivo): un 30 % de los proyectiles vuelven a quien los lanzó.</li>
 *   <li><b>Modo Bola</b>: doble toque de Shift (quieto) → te haces bola y arrollas a los enemigos; otro doble toque para salir.</li>
 *   <li><b>Decreto real</b>: agachado + saltar → onda que empuja y marca.</li>
 * </ul>
 */
public final class CrownPowers {
	/** Ventana (ticks) del doble toque de Shift. */
	private static final int DOUBLE_TAP_TICKS = 8;
	private static final net.minecraft.resources.Identifier SCALE_ID = TheBallMod.id("ball_mode_scale");
	private static final net.minecraft.resources.Identifier SPEED_ID = TheBallMod.id("ball_mode_speed");
	private static final net.minecraft.resources.Identifier JUMP_ID = TheBallMod.id("ball_mode_jump");

	/** Estado por jugador que no hace falta guardar. */
	private static final class State {
		double maxFall;
		boolean wasOnGround = true;
		// Modo Bola
		int ballTicks;
		PowerVisualEntity shell;
		final Map<UUID, Long> rammed = new HashMap<>();
		boolean prevDecreeCombo;
		boolean prevShift;
		boolean prevJump;
		Vec3 lastPos;
		/** Velocidad horizontal (bloques/tick) del ultimo tick en el aire, para no perderla al rebotar. */
		Vec3 airVel = Vec3.ZERO;
		long lastShiftPress = -100;
		/** Hasta este tick de juego no hay rebote (p. ej. al aterrizar del Dios del aluminio). */
		long noBounceUntil;
		/** Rebotes seguidos en Modo Bola: cada uno empuja un multiplo mas (1x, 2x, 3x...). */
		int chain;
		int groundTicks;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private CrownPowers() {
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	// ------------------------------------------------------------------ cada tick

	/** Sin rebote durante {@code ticks} ticks (y se olvida la caida acumulada). */
	public static void suppressBounce(ServerPlayer player, int ticks) {
		State st = state(player);
		st.noBounceUntil = player.level().getGameTime() + ticks;
		st.maxFall = 0;
	}

	public static void tick(MinecraftServer server) {
		tickDecrees();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		State st = state(player);
		if (st.ballTicks > 0) {
			tickBallMode(player, st);
		}
		if (!KingArmor.wearsCrown(player) || player.isSpectator()) {
			st.maxFall = 0;
			st.wasOnGround = player.onGround();
			if (st.ballTicks > 0) {
				endBallMode(player, st);
			}
			return;
		}
		TheBallConfig.Crown cfg = TheBallConfig.get().crown;
		Input input = player.getLastClientInput();

		// --- velocidad horizontal en el aire (por la variacion de posicion, que es lo que el servidor sabe seguro)
		Vec3 pos = player.position();
		if (st.lastPos != null && !player.onGround()) {
			st.airVel = new Vec3(pos.x - st.lastPos.x, 0.0, pos.z - st.lastPos.z);
		}
		st.lastPos = pos;

		// --- Modo Bola: el salto te propulsa hacia donde vas
		if (st.ballTicks > 0 && input.jump() && !st.prevJump && player.onGround() && !player.isPassenger()) {
			Vec3 dir = inputDir(player, input);
			if (dir.lengthSqr() > 0.0) {
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(dir.x * 0.95, v.y, dir.z * 0.95);
				player.syncVelocity = true;
			}
		}
		st.prevJump = input.jump();

		// --- Rebote
		boolean onGround = player.onGround();
		st.groundTicks = onGround ? st.groundTicks + 1 : 0;
		if (st.groundTicks > 10) {
			st.chain = 0;
		}
		if (!onGround) {
			st.maxFall = Math.max(st.maxFall, player.fallDistance);
		} else if (!st.wasOnGround && st.ballTicks > 0) {
			// Modo Bola: rebota como una pelota (80 % de la caida, hasta 9 bloques).
			// En cadena, el rebote nunca se queda corto (si no, la cadena se cortaba al tercer bote y no se notaba).
			if ((st.maxFall >= 1.5 || st.chain > 0) && !player.isInWater() && !player.isPassenger()) {
				st.chain = Math.min(st.chain + 1, 3);
				Vec3 push = propulsion(player, input, st.airVel);
				double h = Math.min(9.0, st.maxFall * 0.8);
				if (st.chain > 1) {
					h = Math.max(h, 2.5 + 0.5 * st.chain);
				}
				bounce(player, h, push.scale(st.chain));
			} else {
				st.chain = 0;
			}
			st.maxFall = 0;
		} else if (!st.wasOnGround) {
			if (cfg.bounce.enabled && player.level().getGameTime() >= st.noBounceUntil && st.maxFall >= cfg.bounce.minFallBlocks && !player.isShiftKeyDown()
					&& !player.isPassenger() && !player.isInWater() && st.ballTicks <= 0) {
				bounce(player, Math.min(cfg.bounce.maxBounceBlocks, st.maxFall * 0.5), null);
			}
			st.maxFall = 0;
		}
		st.wasOnGround = onGround;

		// --- Modo Bola: doble toque de Shift (quieto) para entrar; otro doble toque para salir
		boolean shift = input.shift();
		if (cfg.ballMode.enabled && shift && !st.prevShift) {
			long now = player.level().getGameTime();
			if (now - st.lastShiftPress <= DOUBLE_TAP_TICKS) {
				st.lastShiftPress = -100;
				if (st.ballTicks > 0) {
					endBallMode(player, st);
				} else if (!input.forward() && !input.backward() && !input.left() && !input.right()
						&& !player.isPassenger() && ExplosiveAbility.state(player) == null) {
					startBallMode(player, st);
				}
			} else {
				st.lastShiftPress = now;
			}
		}
		st.prevShift = shift;

		// --- Decreto real: agachado + saltar
		boolean combo = input.shift() && input.jump() && st.ballTicks <= 0;
		if (cfg.decree.enabled && combo && !st.prevDecreeCombo) {
			decree(player);
		}
		st.prevDecreeCombo = combo;
	}

	// ------------------------------------------------------------------ Rebote

	/** ¿Se anula este daño? (caída con la Corona; proyectiles en Modo Bola). */
	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer protectedPlayer && com.theball.throne.Coronation.isProtected(protectedPlayer)) {
			return false;
		}
		if (!(entity instanceof ServerPlayer player) || !KingArmor.wearsCrown(player)) {
			return true;
		}
		if (TheBallConfig.get().crown.bounce.enabled && source.is(DamageTypeTags.IS_FALL)) {
			return false;
		}
		if (inBallMode(player) && source.is(DamageTypeTags.IS_PROJECTILE)) {
			return false;
		}
		return true;
	}

	/** Direccion horizontal (unitaria) que marcan las teclas de movimiento; cero si no hay ninguna. */
	private static Vec3 inputDir(ServerPlayer player, Input in) {
		double yaw = Math.toRadians(player.getYRot());
		double f = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
		double r = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
		double dx = -Math.sin(yaw) * f - Math.cos(yaw) * r;
		double dz = Math.cos(yaw) * f - Math.sin(yaw) * r;
		double len = Math.sqrt(dx * dx + dz * dz);
		return len > 0.0 ? new Vec3(dx / len, 0.0, dz / len) : Vec3.ZERO;
	}

	/** Velocidad horizontal tras un rebote en Modo Bola: sigue tu rumbo (teclas, o el que traias) y sale impulsado. */
	private static Vec3 propulsion(ServerPlayer player, Input in, Vec3 airVel) {
		double sp = airVel.length();
		Vec3 dir = inputDir(player, in);
		if (dir.lengthSqr() == 0.0 && sp > 0.02) {
			dir = airVel.normalize();
		}
		if (dir.lengthSqr() == 0.0) {
			return Vec3.ZERO;
		}
		double speed = Math.min(1.6, Math.max(sp * 1.3, 0.8));
		return dir.scale(speed);
	}

	private static void bounce(ServerPlayer player, double height, Vec3 keepHorizontal) {
		// v = sqrt(2 g h) con la gravedad del jugador (0,08) y un poco más por el rozamiento del aire.
		double vy = Math.sqrt(2.0 * 0.08 * height) * 1.12;
		Vec3 v = player.getDeltaMovement();
		// En Modo Bola se conserva la velocidad con la que caias: el rebote no te frena.
		if (keepHorizontal != null) {
			player.setDeltaMovement(keepHorizontal.x, vy, keepHorizontal.z);
		} else {
			player.setDeltaMovement(v.x, vy, v.z);
		}
		player.syncVelocity = true;
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_FALL, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 0.6f, 1.2f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.4, 0.05, 0.4, 0.02);
	}

	// ------------------------------------------------------------------ Reflejo (lo llama el mixin de Entity#deflection)

	/** Desvío para el jugador: null si no se desvía (se usa el de vanilla). */
	public static ProjectileDeflection deflectionFor(ServerPlayer player, Projectile projectile) {
		if (!KingArmor.wearsCrown(player)) {
			return null;
		}
		TheBallConfig.Reflect cfg = TheBallConfig.get().crown.reflect;
		boolean always = inBallMode(player);
		if (!always && (!cfg.enabled || player.getRandom().nextDouble() * 100.0 >= cfg.chancePercent)) {
			return null;
		}
		if (projectile.getOwner() == player) {
			return null;
		}
		return (proj, deflector, random, pos) -> reflect(player, proj);
	}

	/** Devuelve el proyectil a quien lo lanzó, con la misma velocidad, y pasa a ser del jugador. */
	private static void reflect(ServerPlayer player, Projectile proj) {
		Vec3 v = proj.getDeltaMovement();
		double speed = Math.max(0.6, v.length());
		Entity shooter = proj.getOwner();
		Vec3 dir;
		if (shooter != null && shooter.isAlive() && shooter.level() == proj.level()) {
			dir = shooter.position().add(0.0, shooter.getBbHeight() * 0.6, 0.0).subtract(proj.position()).normalize();
		} else {
			dir = v.lengthSqr() > 1.0E-6 ? v.normalize().scale(-1.0) : player.getLookAngle();
		}
		proj.setDeltaMovement(dir.scale(speed));
		proj.setOwner(player);
		proj.syncVelocity = true;
		ServerLevel level = player.level();
		Vec3 at = proj.position();
		level.sendParticles(new DustParticleOptions(0xF0F4FA, 1.4f), at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.4f);
	}

	// ------------------------------------------------------------------ Modo Bola

	public static boolean inBallMode(ServerPlayer player) {
		State st = STATES.get(player.getUUID());
		return st != null && st.ballTicks > 0;
	}

	private static void startBallMode(ServerPlayer player, State st) {
		st.ballTicks = 1; // activo hasta que se salga (doble toque de Shift, quitarse la Corona, morir...)
		st.rammed.clear();
		addModifier(player, Attributes.SCALE, SCALE_ID, -0.5);
		addModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 1.0);
		addModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, 0.9); // saltos grandes de pelota
		ServerLevel level = player.level();
		st.shell = PowerVisualEntity.create(ModEntities.POWER_VISUAL, level, player, PowerVisualEntity.KIND_BALL);
		level.addFreshEntity(st.shell);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0f, 1.4f);
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
		player.sendOverlayMessage(Component.translatable("power.theball.ball_mode").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
	}

	private static void tickBallMode(ServerPlayer player, State st) {
		if (player.isPassenger() || !player.isAlive()) {
			endBallMode(player, st);
			return;
		}
		// Atropellar: cada mob como mucho una vez por segundo.
		ServerLevel level = player.level();
		long now = level.getGameTime();
		float damage = TheBallConfig.get().crown.ballMode.damage;
		Vec3 motion = player.getDeltaMovement();
		Vec3 dir = new Vec3(motion.x, 0.0, motion.z);
		if (dir.lengthSqr() < 1.0E-4) {
			dir = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		}
		dir = dir.normalize();
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.35),
				e -> e != player && Allies.isValidTarget(player, e))) {
			Long last = st.rammed.get(target.getUUID());
			if (last != null && now - last < 20) {
				continue;
			}
			st.rammed.put(target.getUUID(), now);
			target.hurtServer(level, level.damageSources().playerAttack(player), damage);
			target.push(dir.x * 1.4, 0.55, dir.z * 1.4);
			target.syncVelocity = true;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 1.0f, 0.7f);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.METAL_HIT, SoundSource.PLAYERS, 0.8f, 0.9f);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
		}
		if (player.tickCount % 4 == 0 && motion.horizontalDistanceSqr() > 0.01) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.2, 0.0, 0.2, 0.05);
		}
	}

	private static void endBallMode(ServerPlayer player, State st) {
		st.ballTicks = 0;
		removeModifier(player, Attributes.SCALE, SCALE_ID);
		removeModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		removeModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
		if (st.shell != null) {
			st.shell.discard();
			st.shell = null;
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.5, player.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	private static void addModifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			net.minecraft.resources.Identifier id, double amount) {
		AttributeInstance inst = player.getAttribute(attr);
		if (inst != null && !inst.hasModifier(id)) {
			inst.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void removeModifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			net.minecraft.resources.Identifier id) {
		AttributeInstance inst = player.getAttribute(attr);
		if (inst != null) {
			inst.removeModifier(id);
		}
	}

	// ------------------------------------------------------------------ Decreto real

	private static void decree(ServerPlayer player) {
		TheBallConfig.Decree cfg = TheBallConfig.get().crown.decree;
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long ready = ModAttachments.power(player).decreeReady();
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.theball.cooldown",
					Component.translatable("power.theball.decree"), (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		ModAttachments.setPower(player, ModAttachments.power(player).withDecreeReady(now + cfg.cooldownTicks));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(cfg.radius),
				e -> e != player && Allies.isValidTarget(player, e) && e.distanceToSqr(player) <= cfg.radius * cfg.radius)) {
			Vec3 away = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 1.0E-4) {
				away = new Vec3(1, 0, 0);
			}
			away = away.normalize();
			e.push(away.x * 1.6, 0.6, away.z * 1.6);
			e.syncVelocity = true;
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0), player);
		}
		// Efecto: un único anillo de luz que se expande (ver tickDecrees)
		Vec3 at = player.position();
		DECREES.add(new ActiveDecree(level, at, cfg.radius));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 2.0f, 1.0f);
		player.sendOverlayMessage(Component.translatable("power.theball.decree").withStyle(ChatFormatting.GRAY));
	}

	private static final int DECREE_TICKS = 16;
	private static final java.util.List<ActiveDecree> DECREES = new java.util.ArrayList<>();

	private static final class ActiveDecree {
		final ServerLevel level;
		final Vec3 at;
		final double radius;
		int age;

		ActiveDecree(ServerLevel level, Vec3 at, double radius) {
			this.level = level;
			this.at = at;
			this.radius = radius;
		}
	}

	/** Anima los decretos en curso: un anillo de luz blanca que se expande desde el jugador. */
	private static void tickDecrees() {
		java.util.Iterator<ActiveDecree> it = DECREES.iterator();
		while (it.hasNext()) {
			ActiveDecree d = it.next();
			ServerLevel level = d.level;
			double p = (d.age + 1) / (double) DECREE_TICKS;
			double r = d.radius * p;
			int points = (int) (16 + 32 * p);
			for (int i = 0; i < points; i++) {
				double a = i * Math.PI * 2.0 / points;
				level.sendParticles(ParticleTypes.END_ROD, true, false, d.at.x + Math.cos(a) * r, d.at.y + 0.15,
						d.at.z + Math.sin(a) * r, 1, 0.0, 0.02, 0.0, 0.0);
			}
			if (++d.age >= DECREE_TICKS) {
				it.remove();
			}
		}
	}

	// ------------------------------------------------------------------ limpieza

	public static void forget(ServerPlayer player) {
		State st = STATES.remove(player.getUUID());
		if (st != null && st.ballTicks > 0) {
			endBallMode(player, st);
		}
	}

	public static void resetCooldowns(ServerPlayer player) {
		ModAttachments.setPower(player, ModAttachments.power(player).resetCooldowns());
	}
}
