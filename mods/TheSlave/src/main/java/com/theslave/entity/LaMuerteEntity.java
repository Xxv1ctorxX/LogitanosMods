package com.theslave.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** La Muerte: solo existe durante el ritual. Inmóvil, inmortal, silenciosa. Un clic derecho abre el altar. */
public class LaMuerteEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> PRESENCIA = SynchedEntityData.defineId(LaMuerteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<net.minecraft.core.BlockPos> PORTAL = SynchedEntityData.defineId(LaMuerteEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> FRENTE = SynchedEntityData.defineId(LaMuerteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIPO = SynchedEntityData.defineId(LaMuerteEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIEMPO = SynchedEntityData.defineId(LaMuerteEntity.class, EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PRESENCIA, 0);
        builder.define(PORTAL,net.minecraft.core.BlockPos.ZERO); builder.define(FRENTE,0);
        builder.define(TIPO,0);builder.define(TIEMPO,0);
    }

    public int presencia() { return entityData.get(PRESENCIA); }
    public void guion(int tipo,int tiempo) {entityData.set(TIPO,tipo);entityData.set(TIEMPO,tiempo);}
    public int tipo() {return entityData.get(TIPO);}
    public int tiempo() {return entityData.get(TIEMPO);}
    public void presencia(int valor) { entityData.set(PRESENCIA, Math.clamp(valor, 0, 100)); }
    public void portal(net.minecraft.core.BlockPos pos,net.minecraft.core.Direction frente) {
        entityData.set(PORTAL,pos.immutable()); entityData.set(FRENTE,frente.get2DDataValue());
    }
    public net.minecraft.core.BlockPos portal() {return entityData.get(PORTAL);}
    public net.minecraft.core.Direction frentePortal() {return net.minecraft.core.Direction.from2DDataValue(entityData.get(FRENTE));}
	public LaMuerteEntity(EntityType<? extends LaMuerteEntity> type, Level level) {
		super(type, level);
		setNoAi(true);
		setNoGravity(true);
		setSilent(true);
		setPermanentlyInvulnerable(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder atributos() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.0);
	}

    /** La sesion es temporal: nunca guardar un actor sin su ceremonia. */
    @Override
    public boolean shouldBeSaved() { return false; }

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.SUCCESS;
	}
}
