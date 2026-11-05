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
import org.joml.Quaternionf;

/**
 * Efecto visual pegado a un jugador (no se guarda, no colisiona): la esfera de aluminio del Modo Bola o el halo
 * de la Ascensión de la Reina. El cliente lo dibuja en la posición interpolada de su dueño (ver el renderer),
 * así no "va con retraso" respecto al jugador.
 */
public class PowerVisualEntity extends Entity {
	public static final int KIND_BALL = 0;
	public static final int KIND_HALO = 1;

	private static final EntityDataAccessor<Integer> DATA_OWNER =
			SynchedEntityData.defineId(PowerVisualEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_KIND =
			SynchedEntityData.defineId(PowerVisualEntity.class, EntityDataSerializers.INT);

	/** Rotación de rodar (solo cliente). */
	public final Quaternionf roll = new Quaternionf();
	public final Quaternionf rollOld = new Quaternionf();
	private double lastOwnerX = Double.NaN;
	private double lastOwnerZ = Double.NaN;

	public PowerVisualEntity(EntityType<? extends PowerVisualEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	public static PowerVisualEntity create(EntityType<PowerVisualEntity> type, ServerLevel level, Entity owner, int kind) {
		PowerVisualEntity e = new PowerVisualEntity(type, level);
		e.entityData.set(DATA_OWNER, owner.getId());
		e.entityData.set(DATA_KIND, kind);
		e.setPos(owner.getX(), owner.getY(), owner.getZ());
		return e;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_OWNER, -1);
		builder.define(DATA_KIND, KIND_BALL);
	}

	public int getKind() {
		return this.entityData.get(DATA_KIND);
	}

	public Entity getOwnerEntity() {
		int id = this.entityData.get(DATA_OWNER);
		return id < 0 ? null : level().getEntity(id);
	}

	@Override
	public void tick() {
		super.tick();
		Entity owner = getOwnerEntity();
		if (owner == null || !owner.isAlive()) {
			if (!level().isClientSide()) {
				discard();
			}
			return;
		}
		setPos(owner.getX(), owner.getY(), owner.getZ());
		if (level().isClientSide() && getKind() == KIND_BALL) {
			this.rollOld.set(this.roll);
			if (!Double.isNaN(lastOwnerX)) {
				double dx = owner.getX() - lastOwnerX;
				double dz = owner.getZ() - lastOwnerZ;
				double dist = Math.sqrt(dx * dx + dz * dz);
				if (dist > 1.0E-4) {
					float angle = (float) (dist / 0.5);
					this.roll.premul(new Quaternionf().rotationAxis(angle, (float) (dz / dist), 0.0f, (float) (-dx / dist))).normalize();
				}
			}
			lastOwnerX = owner.getX();
			lastOwnerZ = owner.getZ();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
