package com.theball.item;

import com.theball.config.TheBallConfig;
import com.theball.power.ExplosiveAbility;
import com.theball.power.ScepterPowers;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Cetro del Rey de Aluminio. El poder depende de si vas agachado y de cuánto mantienes el click derecho:
 * <ul>
 *   <li>toque → Bola rebotona</li>
 *   <li>mantener → Bola explosiva (cuanto mas carga, mas rapida)</li>
 *   <li>agachado + toque → Embestida (impulso hacia delante)</li>
 *   <li>agachado + mantener 3 s con la Corona puesta → ¡Aluminio explosivo!</li>
 * </ul>
 */
public class ScepterItem extends Item {
	/** Por debajo de esto es un "toque". */
	public static final int TAP_TICKS = 6;

	public ScepterItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.SPEAR;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseTicks) {
		if (!(user instanceof ServerPlayer player)) {
			return;
		}
		int charge = getUseDuration(stack, user) - remainingUseTicks;
		if (ExplosiveAbility.inMode(player)) {
			// Modo Aluminio explosivo: mantener 1,5 s = salva de bolas (con efecto de carga en la punta)
			chargeEffect(player, charge);
		} else if (player.isShiftKeyDown()) {
			if (charge == TAP_TICKS) {
				ExplosiveAbility.tryBeginCharge(player);
			}
			// La carga del Aluminio explosivo la lleva ExplosiveAbility (efectos y final).
		} else if (charge >= TAP_TICKS) {
			// Mantener: efecto de carga en la punta a partir de 1,5 s
			chargeEffect(player, charge);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remainingUseTicks) {
		if (!(user instanceof ServerPlayer player)) {
			return true;
		}
		int charge = getUseDuration(stack, user) - remainingUseTicks;
		if (ExplosiveAbility.inMode(player)) {
			// click derecho = bola · mantener = bola GIGANTE · Shift + mantener = salva (el golpe al suelo es el click izquierdo)
			if (charge < TAP_TICKS) {
				ExplosiveAbility.modeFire(player);
			} else if (chargeComplete(charge)) {
				if (player.isShiftKeyDown()) {
					ExplosiveAbility.modeSalvo(player);
				} else {
					ExplosiveAbility.modeGiant(player);
				}
			}
			// si sueltas antes de completar la carga no se lanza nada
			return true;
		}
		if (ExplosiveAbility.isCharging(player)) {
			ExplosiveAbility.cancelCharge(player, true);
			return true;
		}
		if (player.isShiftKeyDown()) {
			if (charge < TAP_TICKS) {
				ScepterPowers.dash(player, stack);
			}
		} else if (charge < TAP_TICKS) {
			ScepterPowers.bouncyBall(player, stack);
		} else if (chargeComplete(charge)) {
			ScepterPowers.explosiveBall(player, stack, 1.0f);
		}
		// si sueltas antes de completar la carga no se lanza nada
		return true;
	}

	/** Ticks que hay que mantener antes de que empiece a cargarse (1,5 s). */
	public static final int CHARGE_START = TAP_TICKS;

	/** La bola explosiva solo se lanza con la carga completa. */
	public static boolean chargeComplete(int charge) {
		return chargePower(charge) >= 1.0f;
	}

	/** 0..1: cuanta carga llevas (el efecto empieza a los 1,5 s y se completa 1 s despues). */
	public static float chargePower(int charge) {
		return Mth.clamp((charge - CHARGE_START) / (float) TheBallConfig.get().scepter.explosiveBall.chargeTicks, 0.0f, 1.0f);
	}

	/** Efecto de carga en la bola del cetro: chispas que convergen, nucleo que crece y campanitas cada vez mas agudas. */
	private static void chargeEffect(ServerPlayer player, int charge) {
		if (charge < CHARGE_START) {
			return;
		}
		net.minecraft.server.level.ServerLevel level = player.level();
		int n = charge - CHARGE_START;
		float p = chargePower(charge);
		net.minecraft.world.phys.Vec3 look = player.getLookAngle();
		net.minecraft.world.phys.Vec3 right = look.cross(new net.minecraft.world.phys.Vec3(0, 1, 0)).normalize();
		if (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) {
			right = right.scale(-1.0);
		}
		net.minecraft.world.phys.Vec3 up = right.cross(look).normalize();
		net.minecraft.world.phys.Vec3 tip = player.getEyePosition().add(look.scale(1.6)).add(right.scale(0.5)).add(0.0, -0.02, 0.0);
		double radius = 1.0 * (1.0 - p) + 0.3;
		for (int k = 0; k < 6; k++) {
			double a = n * 0.5 + k * Math.PI / 3.0;
			net.minecraft.world.phys.Vec3 at = tip.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
			// Espiral de 6 brazos: chispas (brillan siempre, vida corta) + 2 destellos END_ROD que dejan
			// estela (vive ~3 s: con más de 2 por tick se acumulaba y cegaba)
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			if (k % 3 == 0) {
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 1 + (int) (2 * p), 0.1, 0.1, 0.1, 0.03);
		if (n == TheBallConfig.get().scepter.explosiveBall.chargeTicks) {
			// carga completa: destello blanco y campanada
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, tip.x, tip.y, tip.z, 6, 0.2, 0.2, 0.2, 0.15);
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 12, 0.3, 0.3, 0.3, 0.2);
			level.playSound(null, tip.x, tip.y, tip.z, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 1.6f);
		}
		if (n % 4 == 0 && p < 1.0f) {
			level.playSound(null, tip.x, tip.y, tip.z, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
					net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.7f + 1.3f * p);
		}
	}

	/** «Bola explosiva ▮▮▮▮▮▯▯▯▯▯». */
	public static Component chargeBar(Component title, float power) {
		int filled = Math.round(power * 10);
		StringBuilder bar = new StringBuilder(" ");
		for (int i = 0; i < 10; i++) {
			bar.append(i < filled ? '▮' : '▯');
		}
		return title.copy().withStyle(ChatFormatting.GOLD)
				.append(Component.literal(bar.toString()).withStyle(power >= 1.0f ? ChatFormatting.YELLOW : ChatFormatting.WHITE));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("tooltip.theball.scepter.tap").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.theball.scepter.hold").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.theball.scepter.sneak_tap").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("tooltip.theball.scepter.sneak_hold").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.accept(Component.translatable("tooltip.theball.scepter.sneak_hold2").withStyle(ChatFormatting.DARK_PURPLE));
	}
}
