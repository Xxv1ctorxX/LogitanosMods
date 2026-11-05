package com.theball.power;

import com.theball.config.TheBallConfig;
import com.theball.entity.ExplosiveShotEntity;
import com.theball.entity.PowerVisualEntity;
import com.theball.item.KingArmor;
import com.theball.registry.ModAdvancements;
import com.theball.registry.ModAttachments;
import com.theball.registry.ModEntities;
import com.theball.registry.ModItems;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * ¡Aluminio explosivo! — el modo de la Reina. Requiere el cetro en la mano y la Corona puesta.
 * Agachado + mantener click derecho 3 s. Máquina de estados por jugador, avanzada en el tick del servidor:
 *
 * <pre>
 * CARGA (60 t)     columna de luz, círculo mágico, papeles en órbita, campana que sube. Se cancela si te alejas
 *                  3 bloques, sueltas el botón o recibes un golpe fuerte. Al completarse se gastan 50 papeles.
 * ASCENSIÓN (10 t) destello y subes 3 bloques; aparece el halo (la corona agrandada sobre tu cabeza).
 * MODO (20 s)      flotas rodeado de bolas que orbitan (cada una es una carga). Con el cetro:
 *                    click          → una bola explosiva desde la órbita hacia donde apuntas
 *                    mantener       → salva de 3 bolas en abanico
 *                    agachado+click → GOLPE AL SUELO: picado hasta el bloque que apuntas, impacto gigante y
 *                                     subida automática
 *                  Las bolas en órbita también golpean a quien se acerca. Acaba al agotar el tiempo o las cargas.
 * REMATE (20 t)    la energía converge delante de ti y sale una bola gigante.
 * DESCENSO         caída lenta, sin daño.
 * </pre>
 * La espera (3 min) cuenta desde la ascensión y se guarda en el jugador (sobrevive a reinicios).
 */
public final class ExplosiveAbility {
	public enum Phase { CHARGE, ASCEND, MODE }

	public static final class State {
		boolean prevSwinging;
		float prevSwingProgress;
		Phase phase = Phase.CHARGE;
		int ticks;
		/** 0 = flotando · 1 = en picado hacia el suelo · 2 = subiendo de nuevo. */
		int slam;
		int slamTicks;
		Vec3 slamTarget;
		double hoverY;
		Vec3 origin;
		float healthAtStart;
		ServerLevel level;
		PowerVisualEntity halo;
		/** La esfera de aluminio que te envuelve durante el descenso. */
		PowerVisualEntity shell;
		int particleBudget;
	}

	/** Brasas de aluminio tras el remate. */
	private record Embers(ServerLevel level, UUID owner, Vec3 center, double radius, int[] ticksLeft) {
	}

	private static final Map<UUID, State> ACTIVE = new HashMap<>();
	private static final List<Embers> EMBERS = new ArrayList<>();
	/** Golpes al suelo en curso: sus ondas y escombros se animan durante unos ticks. */
	private static final List<Impact> IMPACTS = new ArrayList<>();

	private static final class Impact {
		final ServerLevel level;
		final Vec3 at;
		final double radius;
		final net.minecraft.world.level.block.state.BlockState block;
		int age;

		Impact(ServerLevel level, Vec3 at, double radius, net.minecraft.world.level.block.state.BlockState block) {
			this.level = level;
			this.at = at;
			this.radius = radius;
			this.block = block;
		}
	}

	private ExplosiveAbility() {
	}

	/** Al parar el servidor: el estado en memoria no debe pasar a otro mundo. */
	public static void clearAll() {
		ACTIVE.clear();
		EMBERS.clear();
		IMPACTS.clear();
	}

	public static State state(ServerPlayer player) {
		return ACTIVE.get(player.getUUID());
	}

	public static boolean isCharging(ServerPlayer player) {
		State st = ACTIVE.get(player.getUUID());
		return st != null && st.phase == Phase.CHARGE;
	}

	// ------------------------------------------------------------------ inicio / cancelación

	public static void tryBeginCharge(ServerPlayer player) {
		TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
		if (!cfg.enabled || ACTIVE.containsKey(player.getUUID())) {
			return;
		}
		if (!KingArmor.wearsCrown(player)) {
			player.sendOverlayMessage(Component.translatable("message.theball.explosive.need_crown").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (CrownPowers.inBallMode(player)) {
			return;
		}
		long now = player.level().getGameTime();
		long ready = ModAttachments.power(player).explosiveReady();
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.theball.cooldown",
					Component.translatable("power.theball.explosive"), (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!FoilFuel.has(player, cfg.costFoil)) {
			player.sendOverlayMessage(Component.translatable("message.theball.scepter.no_foil", cfg.costFoil)
					.withStyle(ChatFormatting.RED));
			return;
		}
		State st = new State();
		st.origin = player.position();
		st.healthAtStart = player.getHealth();
		st.level = player.level();
		ACTIVE.put(player.getUUID(), st);
	}

	public static void cancelCharge(ServerPlayer player, boolean message) {
		State st = ACTIVE.get(player.getUUID());
		if (st != null && st.phase == Phase.CHARGE) {
			ACTIVE.remove(player.getUUID());
			if (message) {
				player.sendOverlayMessage(Component.translatable("message.theball.explosive.cancelled").withStyle(ChatFormatting.GRAY));
			}
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
					SoundSource.PLAYERS, 0.8f, 1.4f);
		}
	}

	/** Interrupción (muerte, cambio de dimensión, desconexión, montura): todo se limpia. */
	public static void interrupt(ServerPlayer player) {
		State st = ACTIVE.remove(player.getUUID());
		if (st == null) {
			return;
		}
		cleanup(player, st, st.phase != Phase.CHARGE);
	}

	private static void cleanup(ServerPlayer player, State st, boolean slowFall) {
		if (st.halo != null) {
			st.halo.discard();
			st.halo = null;
		}
		if (st.shell != null) {
			st.shell.discard();
			st.shell = null;
		}
		player.setNoGravity(false);
		if (slowFall && player.isAlive()) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
		}
	}

	// ------------------------------------------------------------------ tick

	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, State> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			State st = entry.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			if (!player.isAlive() || player.isPassenger() || player.level() != st.level || player.isSpectator()) {
				it.remove();
				cleanup(player, st, st.phase != Phase.CHARGE);
				continue;
			}
			st.particleBudget = TheBallConfig.get().explosive.maxParticlesPerTick;
			boolean keep = switch (st.phase) {
				case CHARGE -> tickCharge(player, st);
				case ASCEND -> tickAscend(player, st);
				case MODE -> tickMode(player, st);
			};
			if (!keep) {
				it.remove();
			}
		}
		tickEmbers();
		tickImpacts();
	}

	private static boolean tickCharge(ServerPlayer player, State st) {
		TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
		ServerLevel level = player.level();
		boolean holding = player.isUsingItem() && player.getUseItem().is(ModItems.ALUMINUM_SCEPTER) && player.isShiftKeyDown();
		boolean moved = player.position().distanceToSqr(st.origin) > 9.0;
		boolean hit = st.healthAtStart - player.getHealth() >= 4.0f;
		if (!holding || moved || hit || !KingArmor.wearsCrown(player)) {
			cancelChargeInternal(player);
			return false;
		}
		int t = st.ticks++;
		float progress = t / (float) cfg.chargeTicks;
		Vec3 p = player.position();

		// Columna de luz plateada desde el cielo.
		for (int i = 0; i < 4; i++) {
			particle(level, st, ParticleTypes.END_ROD, p.x, p.y + 2.0 + level.getRandom().nextDouble() * 10.0, p.z, 0.05, 0.0, 0.05, 0.0);
		}
		// Círculo mágico giratorio en el suelo (radio 2,5).
		for (int i = 0; i < 8; i++) {
			double a = t * 0.15 + i * Math.PI / 4.0;
			particle(level, st, ParticleTypes.ELECTRIC_SPARK, p.x + Math.cos(a) * 2.5, p.y + 0.05, p.z + Math.sin(a) * 2.5, 0, 0, 0, 0);
		}
		// Papeles de aluminio orbitando y cayendo hacia el jugador.
		ItemParticleOption foil = new ItemParticleOption(ParticleTypes.ITEM, ModItems.ALUMINUM_FOIL);
		double r = 2.2 * (1.0 - progress) + 0.4;
		for (int i = 0; i < 3; i++) {
			double a = -t * 0.25 + i * Math.PI * 2.0 / 3.0;
			particle(level, st, foil, p.x + Math.cos(a) * r, p.y + 1.0 + Math.sin(t * 0.2 + i) * 0.5, p.z + Math.sin(a) * r, 0, 0, 0, 0);
		}
		// Sonido de carga en capas: campana grave que sube, zumbido de baliza, arpegio de cristales y latido que se acelera
		if (t % 10 == 0) {
			level.playSound(null, p.x, p.y, p.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5f, 0.5f + progress * 1.2f);
		}
		if (t % 3 == 0) {
			float[] scale = {0.5f, 0.63f, 0.75f, 1.0f, 1.26f, 1.5f, 2.0f};
			float pitch = scale[(t / 3) % scale.length] * (0.8f + progress * 0.7f);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f + progress, Math.min(2.0f, pitch));
		}
		if (t % Math.max(4, (int) (14 - 10 * progress)) == 0) {
			level.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f + progress, 0.8f + progress * 0.4f);
		}
		if (t == cfg.chargeTicks - 6) {
			level.playSound(null, p.x, p.y, p.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0f, 1.3f);
		}
		player.sendOverlayMessage(com.theball.item.ScepterItem.chargeBar(
				Component.translatable("power.theball.explosive"), progress));

		if (st.ticks >= cfg.chargeTicks) {
			if (!FoilFuel.take(player, cfg.costFoil)) {
				ACTIVE.remove(player.getUUID());
				return false;
			}
			player.stopUsingItem();
			startAscension(player, st);
		}
		return true;
	}

	private static void cancelChargeInternal(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.theball.explosive.cancelled").withStyle(ChatFormatting.GRAY));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
				SoundSource.PLAYERS, 0.8f, 1.4f);
	}

	private static void startAscension(ServerPlayer player, State st) {
		TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
		ServerLevel level = player.level();
		st.phase = Phase.ASCEND;
		st.ticks = 0;
		long now = level.getGameTime();
		ModAttachments.setPower(player, ModAttachments.power(player).withExplosiveReady(now + cfg.cooldownTicks));
		int total = 10 + cfg.modeTicks + 20 + 40;
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, total, 2, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, total, 0, false, false, false));
		player.setNoGravity(true);
		player.setDeltaMovement(0.0, 0.32, 0.0);
		player.syncVelocity = true;
		st.halo = PowerVisualEntity.create(ModEntities.POWER_VISUAL, level, player, PowerVisualEntity.KIND_HALO);
		level.addFreshEntity(st.halo);

		Vec3 p = player.position();
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), true, false, p.x, p.y + 1.0, p.z, 1, 0, 0, 0, 0);
		level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 1.5f);
		// Mensaje solo para ti y en la barra de acciones (el titulo ocupaba toda la pantalla)
		player.sendOverlayMessage(Component.translatable("message.theball.explosive.title").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
	}

	private static boolean tickAscend(ServerPlayer player, State st) {
		int t = st.ticks++;
		aura(player, st);
		if (t >= 9) {
			TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
			ServerLevel level = player.level();
			st.phase = Phase.MODE;
			st.ticks = 0;
			st.hoverY = player.getY();
			player.setDeltaMovement(Vec3.ZERO);
			player.syncVelocity = true;
			// Las bolas aparecen: anillo de luz que se cierra en la órbita.
			Vec3 p = player.position();
			for (int i = 0; i < 48; i++) {
				double a = i * Math.PI * 2.0 / 48.0;
				level.sendParticles(ParticleTypes.END_ROD, true, false, p.x, p.y + 1.0, p.z, 0, Math.cos(a), 0.0, Math.sin(a), 0.5);
			}
			level.playSound(null, p.x, p.y, p.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0f, 1.0f);
			level.playSound(null, p.x, p.y, p.z, com.theball.registry.ModSounds.GOD_MODE, SoundSource.PLAYERS, 3.0f, 1.0f);
		}
		return true;
	}

	// ------------------------------------------------------------------ el modo

	/** Para otros efectos (el coronamiento): la misma órbita de bolas que en el Modo Dios. */
	public static Vec3 orbitPos(Vec3 center, int i, int n, int t) {
		return orbPos(center, i, n, t);
	}

	/** Posición de la órbita número {@code i} de {@code n}: anillo plano exterior y anillo inclinado interior. */
	private static Vec3 orbPos(Vec3 center, int i, int n, int t) {
		int idx = i / 2;
		if (i % 2 == 0) {
			int count = (n + 1) / 2;
			double a = t * 0.10 + idx * Math.PI * 2.0 / Math.max(1, count);
			return center.add(Math.cos(a) * 2.6, 1.0 + 0.35 * Math.sin(t * 0.07 + idx), Math.sin(a) * 2.6);
		}
		int count = Math.max(1, n / 2);
		double a = -t * 0.14 + idx * Math.PI * 2.0 / count;
		double tilt = Math.toRadians(55.0);
		double x = Math.cos(a) * 1.7;
		double y = Math.sin(a) * 1.7 * Math.sin(tilt);
		double z = Math.sin(a) * 1.7 * Math.cos(tilt);
		double spin = t * 0.03;
		double rx = x * Math.cos(spin) - z * Math.sin(spin);
		double rz = x * Math.sin(spin) + z * Math.cos(spin);
		return center.add(rx, 1.1 + y, rz);
	}

	private static boolean tickMode(ServerPlayer player, State st) {
		TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
		ServerLevel level = player.level();
		int t = st.ticks++;
		if (!KingArmor.wearsCrown(player)) {
			finish(player, st);
			return false;
		}
		// Click izquierdo (el brazo se balancea) con el cetro = golpe al suelo
		boolean swingingNow = player.isSwinging();
		float swingProgress = player.getSwingAnimation(0.0f);
		boolean swingStart = swingingNow && (!st.prevSwinging || swingProgress < st.prevSwingProgress);
		st.prevSwinging = swingingNow;
		st.prevSwingProgress = swingProgress;
		if (swingStart && st.slam == 0 && player.getMainHandItem().getItem() instanceof com.theball.item.ScepterItem) {
			modeSlam(player);
		}
		// (sin aura aquí: con tantas partículas pegadas al cuerpo no se veía nada)

		// --- movimiento: flotar / picado / subida automática
		Vec3 v = player.getDeltaMovement();
		if (st.slam == 0) {
			// Vuelo rapido: WASD mueve en horizontal a flySpeed, Espacio sube y Shift baja.
			net.minecraft.world.entity.player.Input in = player.getLastClientInput();
			double yaw = Math.toRadians(player.getYRot());
			double fx = -Math.sin(yaw), fz = Math.cos(yaw);
			double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
			double f = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
			double r = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
			double dx = fx * f + rx * r, dz = fz * f + rz * r;
			double len = Math.sqrt(dx * dx + dz * dz);
			double tx = len > 0 ? dx / len * cfg.flySpeed : 0.0;
			double tz = len > 0 ? dz / len * cfg.flySpeed : 0.0;
			if (in.jump()) {
				st.hoverY += 0.4;
			}
			if (in.shift()) {
				st.hoverY -= 0.4;
			}
			double vy = Math.max(-0.5, Math.min(0.5, (st.hoverY - player.getY()) * 0.25));
			player.setDeltaMovement(v.x + (tx - v.x) * 0.35, vy, v.z + (tz - v.z) * 0.35);
			player.syncVelocity = true;
		} else if (st.slam == 1) {
			tickSlamDive(player, st);
		} else {
			tickSlamRise(player, st);
		}

		// --- las bolas en órbita
		Vec3 center = player.position();
		for (int i = 0; i < cfg.orbs; i++) {
			Vec3 o = orbPos(center, i, cfg.orbs, t);
			boolean gold = i % 2 == 1;
			particle(level, st, new DustParticleOptions(0xFFFFFF, 1.5f), o.x, o.y, o.z, 0, 0, 0, 0);
			Vec3 prev = orbPos(center, i, cfg.orbs, t - 2);
			particle(level, st, ParticleTypes.ELECTRIC_SPARK, prev.x, prev.y, prev.z, 0.02, 0.02, 0.02, 0.0);
		}
		// las bolas golpean a quien se acerca
		if (t % 10 == 0 && st.slam == 0) {
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.0, 1.5, 3.0),
					e -> e != player && Allies.isValidTarget(player, e))) {
				target.hurtServer(level, level.damageSources().indirectMagic(player, player), cfg.orbitDamage);
				Vec3 away = target.position().subtract(center).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
				target.push(away.x * 0.8, 0.3, away.z * 0.8);
				target.syncVelocity = true;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.3, 0.4, 0.3, 0.2);
			}
		}
		// --- barra de tiempo restante (el modo dura un tiempo, no tiene cargas)
		if (t % 4 == 0) {
			int secs = Math.max(0, (cfg.modeTicks - st.ticks + 19) / 20);
			player.sendOverlayMessage(Component.translatable("message.theball.explosive.hud", secs)
					.withStyle(ChatFormatting.AQUA));
		}
		// --- fin del modo
		if (st.slam == 0 && st.ticks >= cfg.modeTicks) {
			// Fin del modo: sin bola final; bajas con caida lenta y sin rebote
			ModAdvancements.grant(player, ModAdvancements.EXPLOSIVE);
			finish(player, st);
			CrownPowers.suppressBounce(player, 80);
			return false;
		}
		return true;
	}

	private static Vec3 aimPoint(ServerPlayer player, double range) {
		net.minecraft.world.phys.HitResult hit = player.pick(range, 1.0f, false);
		return hit.getLocation();
	}

	/** Lanza una bola desde la órbita {@code slot} hacia el punto apuntado (con desvío de yaw opcional). */
	private static void launchFromOrb(ServerPlayer player, State st, int slot, double yawOffsetDeg) {
		launchFromOrb(player, st, slot, yawOffsetDeg, true);
	}

	private static void launchFromOrb(ServerPlayer player, State st, int slot, double yawOffsetDeg, boolean sound) {
		TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
		ServerLevel level = player.level();
		Vec3 from = orbPos(player.position(), slot, cfg.orbs, st.ticks);
		Vec3 aim = aimPoint(player, 64.0);
		Vec3 dir = aim.subtract(from);
		if (dir.lengthSqr() < 4.0) {
			dir = player.getLookAngle();
		}
		dir = dir.normalize();
		if (yawOffsetDeg != 0.0) {
			double r = Math.toRadians(yawOffsetDeg);
			dir = new Vec3(dir.x * Math.cos(r) - dir.z * Math.sin(r), dir.y, dir.x * Math.sin(r) + dir.z * Math.cos(r));
		}
		ExplosiveShotEntity shot = new ExplosiveShotEntity(ModEntities.EXPLOSIVE_SHOT, level);
		shot.setOwner(player);
		shot.setPos(from.x, from.y - 0.2, from.z);
		shot.setDeltaMovement(dir.scale(2.8));
		// Mismo daño y radio que la bola explosiva normal del cetro
		TheBallConfig.ExplosiveBall eb = TheBallConfig.get().scepter.explosiveBall;
		shot.setCustomBlast((float) eb.damage, eb.radius);
		level.addFreshEntity(shot);
		level.sendParticles(ParticleTypes.FIREWORK, from.x, from.y, from.z, 10, 0.1, 0.1, 0.1, 0.08);
		level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.1);
		if (sound) level.playSound(null, from.x, from.y, from.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0f, 1.4f + level.getRandom().nextFloat() * 0.4f);
	}

	private static State modeState(ServerPlayer player) {
		State st = ACTIVE.get(player.getUUID());
		return st != null && st.phase == Phase.MODE ? st : null;
	}

	public static boolean inMode(ServerPlayer player) {
		return modeState(player) != null;
	}

	/** Click: una bola explosiva. */
	public static void modeFire(ServerPlayer player) {
		State st = modeState(player);
		if (st == null || st.slam != 0) {
			return;
		}
		launchFromOrb(player, st, st.ticks % TheBallConfig.get().explosive.orbs, 0.0);
	}

	/** Mantener y soltar: bola gigante (gasta {@code giantCost} cargas). */
	public static void modeGiant(ServerPlayer player) {
		State st = modeState(player);
		if (st == null || st.slam != 0) {
			return;
		}
		fire(player, true, 1.6);
	}

	/** Mantener y soltar: salva en abanico. */
	public static void modeSalvo(ServerPlayer player) {
		State st = modeState(player);
		if (st == null || st.slam != 0) {
			return;
		}
		int n = TheBallConfig.get().explosive.salvoShots;
		int orbs = TheBallConfig.get().explosive.orbs;
		for (int k = 0; k < n; k++) {
			launchFromOrb(player, st, (st.ticks + k) % orbs, n == 1 ? 0.0 : (k - (n - 1) / 2.0) * 9.0, k == 0);
		}
	}

	/** Agachado + click: golpe al suelo en el bloque apuntado. */
	public static void modeSlam(ServerPlayer player) {
		State st = modeState(player);
		TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
		if (st == null || st.slam != 0) {
			return;
		}
		net.minecraft.world.phys.HitResult hit = player.pick(cfg.slamRange, 1.0f, false);
		if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
			player.sendOverlayMessage(Component.translatable("message.theball.explosive.no_target").withStyle(ChatFormatting.GRAY));
			return;
		}
		st.slam = 1;
		st.slamTicks = 0;
		st.slamTarget = hit.getLocation();
		ServerLevel level = player.level();
		Vec3 p = player.position();
		// los satélites se concentran en una lluvia de chispas sobre ti
		level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1.0, p.z, 40, 0.8, 0.8, 0.8, 0.3);
	}

	private static void tickSlamDive(ServerPlayer player, State st) {
		ServerLevel level = player.level();
		int t = st.slamTicks++;
		Vec3 to = st.slamTarget.subtract(player.position());
		double dist = to.length();
		boolean arrived = dist < 2.2 || t > 70 || (t > 4 && (player.onGround() || player.horizontalCollision));
		if (arrived) {
			slamImpact(player, st);
			st.slam = 2;
			st.hoverY = player.getY() + 4.0;
			return;
		}
		double speed = Math.min(2.4, 0.9 + 0.12 * t);
		player.setDeltaMovement(to.normalize().scale(speed));
		player.syncVelocity = true;
		player.fallDistance = 0.0;
		Vec3 p = player.position();
		slamBallEffect(level, st, p, t, to.normalize());
	}

	/** Te conviertes en una bola de luz: nucleo brillante, cascaron de meridianos que giran, anillos y una estela. Solo particulas. */
	private static void slamBallEffect(ServerLevel level, State st, Vec3 p, int t, Vec3 dir) {
		double cy = p.y + 1.0;
		double r = 1.4;
		for (int m = 0; m < 5; m++) {
			double a = t * 0.6 + m * Math.PI * 2.0 / 5.0;
			for (int k = 0; k < 8; k++) {
				double b = -Math.PI / 2.0 + (k + 0.5) * Math.PI / 8.0;
				particle(level, st, ParticleTypes.END_ROD, p.x + Math.cos(a) * Math.cos(b) * r, cy + Math.sin(b) * r,
						p.z + Math.sin(a) * Math.cos(b) * r, 0, 0, 0, 0);
			}
		}
		// ecuador brillante inclinado
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI / 8.0 - t * 0.4;
			particle(level, st, ParticleTypes.ELECTRIC_SPARK, p.x + Math.cos(a) * r * 1.05, cy + Math.sin(a) * r * 0.35,
					p.z + Math.sin(a) * r * 1.05, 0, 0, 0, 0);
		}
		// nucleo denso
		for (int i = 0; i < 3; i++) {
			particle(level, st, new DustParticleOptions(0xFFFFFF, 2.5f), p.x, cy, p.z, 0.45, 0.45, 0.45, 0.0);
		}
		// estela detras de la bola
		Vec3 back = dir.scale(-1.0);
		for (int k = 1; k <= 6; k++) {
			particle(level, st, ParticleTypes.FIREWORK, p.x + back.x * k * 0.8, cy + back.y * k * 0.8, p.z + back.z * k * 0.8, 0.15, 0.15, 0.15, 0.02);
		}
		particle(level, st, ParticleTypes.CLOUD, p.x + back.x * 2.0, cy + back.y * 2.0, p.z + back.z * 2.0, 0.3, 0.3, 0.3, 0.04);
		particle(level, st, ParticleTypes.FLAME, p.x + back.x * 3.0, cy + back.y * 3.0, p.z + back.z * 3.0, 0.25, 0.25, 0.25, 0.02);
		if (t == 0) {
			level.playSound(null, p.x, p.y, p.z, SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 2.0f, 0.7f);
		}
	}

	/** ¡GOLPAZO! Explosión enorme, onda expansiva, cráter de partículas y brasas. */
	private static void slamImpact(ServerPlayer player, State st) {
		TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
		ServerLevel level = player.level();
		Vec3 at = st.slamTarget;
		ExplosiveShotEntity.blast(level, player, player, at, cfg.slamRadius, cfg.slamDamage, 2.6);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), true, false, at.x, at.y + 1.0, at.z, 1, 0, 0, 0, 0);
		// cráter: trozos del bloque golpeado saliendo disparados
		net.minecraft.world.level.block.state.BlockState below =
				level.getBlockState(net.minecraft.core.BlockPos.containing(at.x, at.y - 0.3, at.z));
		if (!below.isAir()) {
			level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, below), true, false,
					at.x, at.y + 0.3, at.z, 120, cfg.slamRadius / 3.0, 0.3, cfg.slamRadius / 3.0, 0.4);
		}
		level.sendParticles(new DustParticleOptions(0xE8EEF4, 3.0f), true, false, at.x, at.y + 1.0, at.z, 80,
				cfg.slamRadius / 2.0, 0.8, cfg.slamRadius / 2.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 4.0f, 1.0f);
		level.playSound(null, at.x, at.y, at.z, com.theball.registry.ModSounds.SLAM, SoundSource.PLAYERS, 4.0f, 1.0f);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, true, false, at.x, at.y + 0.5, at.z, 25, cfg.slamRadius / 5.0, 0.3, cfg.slamRadius / 5.0, 0.1);
		IMPACTS.add(new Impact(level, at, cfg.slamRadius, below.isAir() ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : below));
		addEmbers(level, player, at, cfg.slamRadius * 0.7);
		player.setDeltaMovement(0.0, 0.6, 0.0);
		player.syncVelocity = true;
	}

	/** Dos aros que se expanden desde el golpe: uno pegado al suelo y otro un poco mas arriba. Sin sonidos propios. */
	private static void tickImpacts() {
		java.util.Iterator<Impact> it = IMPACTS.iterator();
		while (it.hasNext()) {
			Impact im = it.next();
			ServerLevel level = im.level;
			// Solo un aro cada pocos ticks (antes salía uno por tick y quedaban decenas)
			if (im.age % 5 != 4) {
				if (++im.age >= 10) {
					it.remove();
				}
				continue;
			}
			double p = (im.age + 1) / 10.0;
			double r = im.radius * 1.4 * p;
			int points = (int) (24 + 24 * p);
			for (int i = 0; i < points; i++) {
				double a = i * Math.PI * 2.0 / points;
				double cx = Math.cos(a), cz = Math.sin(a);
				// aro de abajo: a ras de suelo, polvo y trozos de bloque
				level.sendParticles(ParticleTypes.CLOUD, true, false, im.at.x + cx * r, im.at.y + 0.15, im.at.z + cz * r, 0, 0.0, 0.0, 0.0, 1.0);
				if (i % 3 == 0) {
					level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, im.block), true, false,
							im.at.x + cx * r, im.at.y + 0.2, im.at.z + cz * r, 1, 0.1, 0.05, 0.1, 0.1);
				}
				// aro de arriba: luz blanca, mas alto y algo mas pequeño
			}
			if (++im.age >= 10) {
				it.remove();
			}
		}
	}

	/** Subida automática tras el golpe: sin que hagas nada vuelves a la altura de vuelo. */
	private static void tickSlamRise(ServerPlayer player, State st) {
		ServerLevel level = player.level();
		double dy = st.hoverY - player.getY();
		if (dy <= 0.5) {
			st.slam = 0;
			return;
		}
		double vy = Math.max(0.35, Math.min(1.1, dy * 0.3));
		player.setDeltaMovement(0.0, vy, 0.0);
		player.syncVelocity = true;
		player.fallDistance = 0.0;
		Vec3 p = player.position();
		for (int i = 0; i < 6; i++) {
			double a = st.ticks * 0.6 + i * Math.PI / 3.0;
			particle(level, st, ParticleTypes.END_ROD, p.x + Math.cos(a) * 1.2, p.y + 0.2, p.z + Math.sin(a) * 1.2, 0, 0.05, 0, 0.0);
		}
		particle(level, st, ParticleTypes.CLOUD, p.x, p.y, p.z, 0.4, 0.1, 0.4, 0.02);
	}

	private static void finish(ServerPlayer player, State st) {
		cleanup(player, st, true);
	}

	/** Dispara una bola explosiva (o la gigante) desde los ojos hacia donde mira. */
	private static void fire(ServerPlayer player, boolean giant, double speed) {
		ServerLevel level = player.level();
		ExplosiveShotEntity shot = new ExplosiveShotEntity(ModEntities.EXPLOSIVE_SHOT, level);
		shot.setOwner(player);
		shot.setGiant(giant);
		Vec3 look = player.getLookAngle();
		Vec3 start = player.getEyePosition().add(look.scale(giant ? 3.0 : 1.0));
		shot.setPos(start.x, start.y - 0.2, start.z);
		shot.setDeltaMovement(look.scale(speed));
		level.addFreshEntity(shot);
		level.sendParticles(ParticleTypes.FIREWORK, start.x, start.y, start.z, 6, 0.1, 0.1, 0.1, 0.05);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS,
				giant ? 2.0f : 0.8f, giant ? 0.5f : 1.6f + level.getRandom().nextFloat() * 0.3f);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS,
				giant ? 2.0f : 0.6f, giant ? 0.6f : 1.2f);
	}

	/** Aura de la Ascensión alrededor del cuerpo. */
	private static void aura(ServerPlayer player, State st) {
		ServerLevel level = player.level();
		Vec3 p = player.position();
		for (int i = 0; i < 4; i++) {
			particle(level, st, ParticleTypes.END_ROD, p.x, p.y + 1.0, p.z, 0.4, 0.8, 0.4, 0.01);
		}
		particle(level, st, ParticleTypes.GLOW, p.x, p.y + 1.0, p.z, 0.5, 0.8, 0.5, 0.0);
	}

	private static void particle(ServerLevel level, State st, ParticleOptions type, double x, double y, double z,
			double dx, double dy, double dz, double speed) {
		if (st.particleBudget-- <= 0) {
			return;
		}
		level.sendParticles(type, x, y, z, 1, dx, dy, dz, speed);
	}

	// ------------------------------------------------------------------ brasas de aluminio (termita)

	public static void addEmbers(ServerLevel level, Entity owner, Vec3 center, double radius) {
		EMBERS.add(new Embers(level, owner == null ? null : owner.getUUID(), center, radius, new int[] {100}));
	}

	private static void tickEmbers() {
		Iterator<Embers> it = EMBERS.iterator();
		while (it.hasNext()) {
			Embers e = it.next();
			int left = --e.ticksLeft()[0];
			if (left <= 0) {
				it.remove();
				continue;
			}
			ServerLevel level = e.level();
			Entity owner = e.owner() == null ? null : level.getEntity(e.owner());
			if (left % 2 == 0) {
				for (int i = 0; i < 6; i++) {
					double a = level.getRandom().nextDouble() * Math.PI * 2.0;
					double r = Math.sqrt(level.getRandom().nextDouble()) * e.radius();
					double x = e.center().x + Math.cos(a) * r;
					double z = e.center().z + Math.sin(a) * r;
					level.sendParticles(ParticleTypes.WHITE_ASH, x, e.center().y - 0.5, z, 1, 0.1, 0.4, 0.1, 0.0);
					level.sendParticles(ParticleTypes.SMALL_FLAME, x, e.center().y - 0.8, z, 1, 0.1, 0.1, 0.1, 0.01);
				}
			}
			if (left % 10 == 0) {
				for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
						new net.minecraft.world.phys.AABB(e.center(), e.center()).inflate(e.radius(), 3.0, e.radius()),
						t -> Allies.isValidTarget(owner, t))) {
					target.igniteForSeconds(2.0f);
					target.hurtServer(level, level.damageSources().onFire(), 2.0f);
				}
			}
		}
	}

	public static void resetCooldown(ServerPlayer player) {
		ModAttachments.setPower(player, ModAttachments.power(player).withExplosiveReady(0L));
	}
}
