package com.theball.entity;

import com.theball.config.TheBallConfig;
import com.theball.power.Allies;
import com.theball.power.ExplosiveAbility;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bola de aluminio explosivo (habilidad de la Corona + cetro). Vuela recta, sin gravedad, y explota al chocar
 * o a los 3 s. Las explosiones son "a mano": dañan y empujan solo a enemigos, nunca rompen bloques (salvo que la
 * config lo pida) y no dañan al dueño, su equipo ni sus mascotas. La versión gigante es el remate final.
 */
public class ExplosiveShotEntity extends ThrowableProjectile {
	private static final EntityDataAccessor<Boolean> DATA_GIANT =
			SynchedEntityData.defineId(ExplosiveShotEntity.class, EntityDataSerializers.BOOLEAN);
	private static final int MAX_LIFE = 100;
	/** Velocidad de crucero (sin el rozamiento del aire de los proyectiles: la bola llega lejos). */
	private Vec3 cruise;
	/** Dano/radio propios (bola explosiva del cetro); -1 = los de la config del Aluminio explosivo. */
	private float customDamage = -1.0f;
	private double customRadius = -1.0;

	public ExplosiveShotEntity(EntityType<? extends ExplosiveShotEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_GIANT, false);
	}

	public void setCustomBlast(float damage, double radius) {
		this.customDamage = damage;
		this.customRadius = radius;
	}

	public boolean isGiant() {
		return this.entityData.get(DATA_GIANT);
	}

	public void setGiant(boolean giant) {
		this.entityData.set(DATA_GIANT, giant);
	}

	/** Entidad pequeña: sin esto el cliente dejaba de dibujarla a ~45 bloques (parecía que desaparecia). */
	@Override
	public boolean shouldRenderAtSqrDistance(double distanceSqr) {
		return distanceSqr < 256.0 * 256.0;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	@Override
	public void tick() {
		if (!level().isClientSide()) {
			if (cruise == null && this.tickCount >= 1 && getDeltaMovement().lengthSqr() > 1.0E-4) {
				cruise = getDeltaMovement();
			}
			if (cruise != null) {
				setDeltaMovement(cruise);
			}
		}
		super.tick();
		if (level().isClientSide()) {
			double s = isGiant() ? 1.2 : 0.2;
			level().addParticle(ParticleTypes.FIREWORK, getX(), getY() + s, getZ(), 0.0, 0.0, 0.0);
			if (isGiant()) {
				level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 2.5, getY() + s,
						getZ() + (random.nextDouble() - 0.5) * 2.5, 0.0, 0.0, 0.0);
			}
		} else if (this.tickCount > MAX_LIFE) {
			detonate();
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		Entity owner = getOwner();
		return super.canHitEntity(target) && !(target instanceof ExplosiveShotEntity)
				&& !(owner != null && Allies.areAllies(owner, target));
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!level().isClientSide()) {
			detonate();
		}
	}

	private void detonate() {
		if (isRemoved() || !(level() instanceof ServerLevel level)) {
			return;
		}
		TheBallConfig.Explosive cfg = TheBallConfig.get().explosive;
		Vec3 at = position().add(0.0, getBbHeight() / 2.0, 0.0);
		Entity owner = getOwner();
		if (isGiant()) {
			blast(level, this, owner, at, cfg.finaleRadius, cfg.finaleDamage, 2.2);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			// Onda expansiva: anillo de luz.
			for (int i = 0; i < 72; i++) {
				double a = i * Math.PI * 2.0 / 72.0;
				level.sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y, at.z, 0,
						Math.cos(a), 0.05, Math.sin(a), 0.9);
			}
			level.sendParticles(new DustParticleOptions(0xE8EEF4, 2.5f), true, false, at.x, at.y, at.z, 120,
					cfg.finaleRadius / 2.5, 1.0, cfg.finaleRadius / 2.5, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0f, 0.7f);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 6.0f, 1.2f);
			ExplosiveAbility.addEmbers(level, owner, at, cfg.finaleRadius);
		} else {
			double r = customRadius > 0 ? customRadius : cfg.shotRadius;
			blast(level, this, owner, at, r, customDamage >= 0 ? customDamage : cfg.shotDamage, 1.0);
			// Particulas "forzadas" (overrideLimiter): las normales solo las ve quien esta a menos de 32 bloques, y por eso
			// una explosion lejana parecia que la bola simplemente desaparecia.
			level.sendParticles(customRadius > 0 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, true, false,
					at.x, at.y, at.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.FIREWORK, true, false, at.x, at.y, at.z, 30, r / 4.0, r / 4.0, r / 4.0, 0.25);
			level.sendParticles(new DustParticleOptions(0xFFFFFF, 3.0f), true, false, at.x, at.y, at.z, 24, r / 3.0, r / 3.0, r / 3.0, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 1.2f);
		}
		if (cfg.breakBlocks) {
			level.explode(owner, at.x, at.y, at.z, (float) ((isGiant() ? cfg.finaleRadius : (customRadius > 0 ? customRadius : cfg.shotRadius)) / 2.0),
					Level.ExplosionInteraction.TNT);
		}
		discard();
	}

	/** Daño en radio (la mitad en el borde) y empuje, solo a enemigos. */
	public static void blast(ServerLevel level, Entity direct, Entity owner, Vec3 at, double radius, float damage, double knockback) {
		DamageSource source = level.damageSources().explosion(direct, owner);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
				e -> Allies.isValidTarget(owner, e))) {
			double d = e.position().add(0.0, e.getBbHeight() / 2.0, 0.0).distanceTo(at);
			if (d > radius) {
				continue;
			}
			float scaled = (float) (damage * (1.0 - 0.5 * d / radius));
			e.hurtServer(level, source, scaled);
			Vec3 away = e.position().subtract(at);
			if (away.lengthSqr() < 1.0E-4) {
				away = new Vec3(0, 1, 0);
			}
			Vec3 push = away.normalize().scale(knockback * (1.0 - d / radius) + 0.3);
			e.push(push.x, Math.max(0.3, push.y + 0.3), push.z);
			e.syncVelocity = true;
		}
	}
}
