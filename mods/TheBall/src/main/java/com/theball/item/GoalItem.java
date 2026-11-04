package com.theball.item;

import com.theball.entity.GoalEntity;
import com.theball.registry.ModEntities;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Coloca una portería de fútbol 11 sobre el suelo, con la boca mirando hacia el jugador. */
public class GoalItem extends Item {
	public GoalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getClickedFace() != Direction.UP) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		Player player = context.getPlayer();
		Direction facing = player != null ? player.getDirection().getOpposite() : Direction.NORTH;
		Vec3 click = context.getClickLocation();

		GoalEntity goal = new GoalEntity(ModEntities.GOAL, level);
		goal.setFacing(facing);
		goal.setPos(click.x, click.y, click.z);
		serverLevel.addFreshEntity(goal);
		level.playSound(null, click.x, click.y, click.z, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6f, 1.4f);

		if (player != null) {
			context.getItemInHand().consume(1, player);
		} else {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
