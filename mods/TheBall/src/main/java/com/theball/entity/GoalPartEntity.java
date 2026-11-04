package com.theball.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pieza invisible y sólida de una portería (un poste, el larguero, un panel de red...). Su posición es el centro de
 * su caja. Existe solo mientras exista la portería (no se guarda: la portería la vuelve a crear al cargarse).
 */
public class GoalPartEntity extends Entity {
	private static final EntityDataAccessor<Float> DATA_HX = SynchedEntityData.defineId(GoalPartEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_HY = SynchedEntityData.defineId(GoalPartEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_HZ = SynchedEntityData.defineId(GoalPartEntity.class, EntityDataSerializers.FLOAT);

	/** Solo en el servidor. */
	private GoalEntity parent;

	public GoalPartEntity(EntityType<? extends GoalPartEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	public void setParent(GoalEntity parent) {
		this.parent = parent;
	}

	/** Medidas de la caja (mitades en cada eje). */
	public void setHalfSize(float hx, float hy, float hz) {
		this.entityData.set(DATA_HX, hx);
		this.entityData.set(DATA_HY, hy);
		this.entityData.set(DATA_HZ, hz);
		setPos(getX(), getY(), getZ());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_HX, 0.05f);
		builder.define(DATA_HY, 0.05f);
		builder.define(DATA_HZ, 0.05f);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_HX.equals(accessor) || DATA_HY.equals(accessor) || DATA_HZ.equals(accessor)) {
			setPos(getX(), getY(), getZ());
		}
	}

	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		if (this.entityData == null) {
			return new AABB(pos.x - 0.05, pos.y - 0.05, pos.z - 0.05, pos.x + 0.05, pos.y + 0.05, pos.z + 0.05);
		}
		double hx = this.entityData.get(DATA_HX);
		double hy = this.entityData.get(DATA_HY);
		double hz = this.entityData.get(DATA_HZ);
		return new AABB(pos.x - hx, pos.y - hy, pos.z - hz, pos.x + hx, pos.y + hy, pos.z + hz);
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return true;
	}

	/** No se puede apuntar a ella: los clics y golpes pasan a través (hacia la bola, otros jugadores...). */
	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide() && (this.parent == null || this.parent.isRemoved())) {
			discard();
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
