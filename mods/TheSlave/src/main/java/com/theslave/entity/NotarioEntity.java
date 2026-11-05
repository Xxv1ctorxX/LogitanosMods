package com.theslave.entity;

import com.theslave.comercio.Comercio;
import com.theslave.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Notario de Ceniza. Está ligado a su enclave (no se mueve, no se monta, no se ata) y comerciar con él no exige
 * combate. Matarlo no da nada: no tiene botín.
 */
public class NotarioEntity extends PathfinderMob {
	public NotarioEntity(EntityType<? extends NotarioEntity> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder atributos() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// Se puede matar, pero solo con daño deliberado: nada de ahogarlo o aplastarlo en un enclave.
		if (source.getEntity() instanceof Player) {
			return super.hurtServer(level, source, amount);
		}
		return false;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			abrir(sp);
		}
		return InteractionResult.SUCCESS;
	}

	public void abrir(ServerPlayer sp) {
		ServerPlayNetworking.send(sp, Payloads.AbrirNotario.de(getId(), Comercio.situacion(sp), null));
	}
}
