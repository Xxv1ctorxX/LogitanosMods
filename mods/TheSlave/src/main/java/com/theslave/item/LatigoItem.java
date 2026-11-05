package com.theslave.item;

import com.theslave.entity.EsclavoEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** El látigo: a la vez adopta, confirma, acelera y cobra (ver {@link EsclavoEntity#gesto}). Al aire, cancela. */
public class LatigoItem extends Item {
	public LatigoItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) {
			return InteractionResult.FAIL;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		// Al aire: cancela la orden en curso del esclavo propio más cercano (hasta 48 bloques).
		AABB box = player.getBoundingBox().inflate(48);
		EsclavoEntity mejor = null;
		double d = Double.MAX_VALUE;
		for (EsclavoEntity e : server.getEntitiesOfClass(EsclavoEntity.class, box)) {
			if (e.esDe(player) && e.puedeCancelar() && e.distanceToSqr(player) < d) {
				d = e.distanceToSqr(player);
				mejor = e;
			}
		}
		Latigazo.alAire(sp, hand);
		player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
		if (mejor != null) {
			mejor.cancelar(sp);
		}
		return InteractionResult.SUCCESS;
	}
}
