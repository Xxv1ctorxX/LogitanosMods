package com.theball.item;

import com.theball.entity.AluminumBallEntity;
import com.theball.registry.ModComponents;
import com.theball.registry.ModEntities;
import com.theball.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Versión en item de la bola. Guarda su tamaño en el componente {@code theball:sheets}.
 * Mantener el click derecho carga el lanzamiento y al soltar sale la bola hacia donde mires
 * (con un toque rápido simplemente la sueltas delante de ti).
 */
public class AluminumBallItem extends Item {
	/** Tamaño inicial: muy pequeña (0.12 bloques, como un cubo de Rubik pequeño). */
	public static final int DEFAULT_SHEETS = 9;

	/** Ticks de carga para llegar a la potencia máxima. */
	private static final int MAX_CHARGE_TICKS = 20;

	public AluminumBallItem(Properties properties) {
		super(properties);
	}

	/** El nombre incluye el número de hojas, que va subiendo al agrandarla. */
	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable("item.theball.aluminum_ball.sized", getSheets(stack));
	}

	public static ItemStack create(int sheets) {
		ItemStack stack = new ItemStack(ModItems.ALUMINUM_BALL);
		stack.set(ModComponents.SHEETS, Math.max(1, sheets));
		return stack;
	}

	public static int getSheets(ItemStack stack) {
		return stack.getOrDefault(ModComponents.SHEETS, DEFAULT_SHEETS);
	}

	// ---------------------------------------------------------------- carga y lanzamiento

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BOW;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return true;
		}

		int charge = getUseDuration(stack, user) - remainingUseTicks;
		float power = Mth.clamp(charge / (float) MAX_CHARGE_TICKS, 0.0f, 1.0f);

		AluminumBallEntity ball = new AluminumBallEntity(ModEntities.ALUMINUM_BALL, level);
		ball.setSheets(getSheets(stack));

		// Sale justo delante de los ojos, fuera del jugador.
		Vec3 look = user.getLookAngle();
		double diameter = ball.getDiameter();
		double reach = 0.6 + diameter / 2.0;
		Vec3 center = user.getEyePosition().add(look.scale(reach));
		ball.setPos(center.x, center.y - diameter / 2.0, center.z);
		for (int i = 0; i < 6 && !level.noBlockCollision(ball, ball.getBoundingBox()); i++) {
			// Si queda dentro de un bloque la acercamos al jugador.
			center = center.subtract(look.scale(reach / 6.0));
			ball.setPos(center.x, center.y - diameter / 2.0, center.z);
		}
		// Las bolas grandes tienen la base muy por debajo de los ojos: si siguen dentro del suelo, las subimos.
		for (int i = 0; i < 100 && !level.noBlockCollision(ball, ball.getBoundingBox()); i++) {
			ball.setPos(ball.getX(), ball.getY() + 0.1, ball.getZ());
		}

		// Los toques rápidos la sueltan sin más; mantener da hasta ~1.5 bloques/tick (como un buen chut).
		// Cuanto más grande, más cuesta lanzarla.
		double mass = Math.sqrt(Math.max(1.0, diameter / 0.6));
		double speed = (power < 0.1f ? 0.05 : 0.25 + 1.25 * power) / mass;
		Vec3 userMotion = user.getDeltaMovement();
		ball.setDeltaMovement(look.scale(speed).add(userMotion.x, 0.0, userMotion.z));

		serverLevel.addFreshEntity(ball);
		level.playSound(null, ball.getX(), ball.getY(), ball.getZ(),
				SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 0.8f, 0.8f + power * 0.6f);

		if (user instanceof Player player) {
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
		} else {
			stack.shrink(1);
		}
		return true;
	}
}
