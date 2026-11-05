package com.theslave.entity;

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

/**
 * El Portal del Umbral: una herida vertical sin marco, de unos 2x3 bloques, que solo existe mientras dura una ceremonia.
 * No se guarda en disco, no tiene colisión ni sirve para viajar. La apertura (0-100) la escribe el servidor.
 */
public class UmbralEntity extends Entity {
	private static final EntityDataAccessor<Integer> TIPO = SynchedEntityData.defineId(UmbralEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIEMPO = SynchedEntityData.defineId(UmbralEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<net.minecraft.core.BlockPos> TUMBA = SynchedEntityData.defineId(UmbralEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> APERTURA = SynchedEntityData.defineId(UmbralEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANTE = SynchedEntityData.defineId(UmbralEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANTE2 = SynchedEntityData.defineId(UmbralEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> NOMBRE = SynchedEntityData.defineId(UmbralEntity.class,EntityDataSerializers.STRING);
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack> CONTRATO = SynchedEntityData.defineId(UmbralEntity.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack> OFRENDA = SynchedEntityData.defineId(UmbralEntity.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack> ALMA = SynchedEntityData.defineId(UmbralEntity.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<org.joml.Vector3fc> MANO = SynchedEntityData.defineId(UmbralEntity.class,EntityDataSerializers.VECTOR3);

	public UmbralEntity(EntityType<? extends UmbralEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(APERTURA, 0);
        builder.define(TIPO, 0);
        builder.define(TIEMPO, 0);
        builder.define(TUMBA, net.minecraft.core.BlockPos.ZERO);
        builder.define(VARIANTE,0);builder.define(VARIANTE2,-1);builder.define(NOMBRE,"");
        builder.define(CONTRATO,net.minecraft.world.item.ItemStack.EMPTY);builder.define(OFRENDA,net.minecraft.world.item.ItemStack.EMPTY);
        builder.define(ALMA,net.minecraft.world.item.ItemStack.EMPTY);
        builder.define(MANO,new org.joml.Vector3f(0,1.35f,2));
	}

	public int apertura() {
		return entityData.get(APERTURA);
	}

	public void apertura(int v) {
		entityData.set(APERTURA, Math.max(0, Math.min(100, v)));
	}

    public int tipo() { return entityData.get(TIPO); }
    public void aparicion(int variante,int variante2,String nombre,net.minecraft.world.item.ItemStack contrato,net.minecraft.world.item.ItemStack ofrenda) {
        entityData.set(VARIANTE,variante);entityData.set(VARIANTE2,variante2);entityData.set(NOMBRE,nombre);
        entityData.set(CONTRATO,contrato.copy());entityData.set(OFRENDA,ofrenda.copy());
    }
    public int variante() {return entityData.get(VARIANTE);}
    public int variante2() {return entityData.get(VARIANTE2);}
    public String nombre() {return entityData.get(NOMBRE);}
    public net.minecraft.world.item.ItemStack contrato() {return entityData.get(CONTRATO);}
    public net.minecraft.world.item.ItemStack ofrenda() {return entityData.get(OFRENDA);}
    public void alma(net.minecraft.world.item.ItemStack alma) {entityData.set(ALMA,alma.copy());}
    public net.minecraft.world.item.ItemStack alma() {return entityData.get(ALMA);}
    public void mano(net.minecraft.world.phys.Vec3 relativa) {entityData.set(MANO,new org.joml.Vector3f((float)relativa.x,(float)relativa.y,(float)relativa.z));}
    public org.joml.Vector3f mano() {return new org.joml.Vector3f(entityData.get(MANO));}
    public int tiempo() { return entityData.get(TIEMPO); }
    public net.minecraft.core.BlockPos tumba() { return entityData.get(TUMBA); }
    public void guion(int tipo, int tick, net.minecraft.core.BlockPos tumba) {
        entityData.set(TIPO, Math.clamp(tipo, 0, 3));
        entityData.set(TIEMPO, Math.max(0, tick));
        entityData.set(TUMBA, tumba == null ? net.minecraft.core.BlockPos.ZERO : tumba);
    }

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

    @Override
    public boolean shouldBeSaved() { return false; }

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
	}
}
