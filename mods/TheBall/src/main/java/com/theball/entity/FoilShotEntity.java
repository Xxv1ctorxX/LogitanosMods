package com.theball.entity;

import com.theball.config.TheBallConfig;
import com.theball.power.Allies;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bola rebotona del cetro: una bolita brillante de papel de aluminio que rebota contra bloques y mobs (4 veces,
 * perdiendo un 15 % de velocidad en cada rebote) y hace daño a cada mob que golpea.
 */
public class FoilShotEntity extends ThrowableProjectile {
	public static final float SIZE = 0.25f;
	private static final int MAX_LIFE = 100;

	private int bounces;
	private UUID lastHit;

	public FoilShotEntity(EntityType<? extends FoilShotEntity> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	protected double getDefaultGravity() {
		return 0.02;
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) {
			level().addParticle(ParticleTypes.END_ROD, getX(), getY() + SIZE / 2, getZ(), 0.0, 0.0, 0.0);
		} else if (this.tickCount > MAX_LIFE) {
			discard();
		}
	}

	@Override
	protected boolean canHitEntity(Entity target) {
		if (!super.canHitEntity(target) || target.getUUID().equals(lastHit)) {
			return false;
		}
		Entity owner = getOwner();
		return !(owner != null && Allies.areAllies(owner, target));
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		Direction face = hit.getDirection();
		Vec3 v = getDeltaMovement();
		Vec3 reflected = switch (face.getAxis()) {
			case X -> new Vec3(-v.x, v.y, v.z);
			case Y -> new Vec3(v.x, -v.y, v.z);
			case Z -> new Vec3(v.x, v.y, -v.z);
		};
		Vec3 at = hit.getLocation().add(face.getStepX() * 0.15, face.getStepY() * 0.15, face.getStepZ() * 0.15);
		bounce(reflected, at);
		this.lastHit = null;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity target = hit.getEntity();
		if (level() instanceof ServerLevel serverLevel && target instanceof LivingEntity living) {
			Entity owner = getOwner();
			float damage = TheBallConfig.get().scepter.bouncyBall.damage;
			living.hurtServer(serverLevel, damageSources().thrown(this, owner), damage);
			Vec3 push = getDeltaMovement().normalize().scale(0.5);
			living.push(push.x, 0.25, push.z);
		} else if (target instanceof AluminumBallEntity ball && level() instanceof ServerLevel serverLevel) {
			ball.hurtServer(serverLevel, damageSources().thrown(this, getOwner()), 2.0f);
		}
		this.lastHit = target.getUUID();
		Vec3 v = getDeltaMovement();
		bounce(new Vec3(-v.x * 0.7, Math.abs(v.y) * 0.5 + 0.15, -v.z * 0.7), position());
	}

	private void bounce(Vec3 velocity, Vec3 at) {
		this.bounces++;
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 0.6f, 1.6f);
		serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.METAL_HIT, SoundSource.PLAYERS, 0.4f, 1.8f);
		serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.1);
		if (this.bounces > TheBallConfig.get().scepter.bouncyBall.maxBounces) {
			discard();
			return;
		}
		setPos(at.x, at.y, at.z);
		setDeltaMovement(velocity.scale(0.85));
		this.syncVelocity = true;
	}
}
