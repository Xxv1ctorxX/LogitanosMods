package com.theball.throne;

import com.theball.block.entity.ThroneBlockEntity;
import com.theball.config.TheBallConfig;
import com.theball.entity.AluminumBallEntity;
import com.theball.item.TributeMedalItem;
import com.theball.registry.ModAttachments;
import com.theball.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Premios del trono. Al cruzar 250/500/1000/1500/2000 hojas (con la bola sentada) se entregan a quienes han
 * puesto al menos el {@code min_share_for_loot_percent} del papel de una Reina. Los que no están conectados los
 * reciben más tarde (se guardan en el trono como pendientes).
 */
public final class Rewards {
	/** Bit extra de los pendientes: medalla del tributo (no forma parte de rewards_given). */
	public static final int BIT_MEDAL = 5;

	private static final String[] REWARD_KEYS = {
			"reward.theball.food", "reward.theball.boots", "reward.theball.leggings",
			"reward.theball.chestplate", "reward.theball.crown"};

	private Rewards() {
	}

	/** Comprueba los umbrales que haya cruzado la bola sentada y entrega los que falten. */
	public static void checkThresholds(ServerLevel level, AluminumBallEntity ball) {
		if (!ball.isSeated()) {
			return; // fuera del trono no hay premios; se entregan al sentarla
		}
		Tribute tribute = ball.getTribute();
		int sheets = ball.getSheets();
		for (int bit = Tribute.BIT_FOOD; bit <= Tribute.BIT_CHESTPLATE; bit++) {
			if (sheets >= Tribute.THRESHOLDS[bit] && !tribute.isGiven(bit)) {
				tribute.markGiven(bit);
				grant(level, ball, bit);
			}
		}
		if (sheets >= AluminumBallEntity.MAX_SHEETS && !tribute.isGiven(Tribute.BIT_CROWN) && ball.coronationTick < 0) {
			Coronation.start(level, ball);
		}
	}

	/** Quienes tienen derecho a premio. Si nadie llega al umbral, el mayor aportador (un esfuerzo de todos tiene premio). */
	public static List<Contributor> eligible(Tribute tribute, double minPercent) {
		List<Contributor> out = new ArrayList<>();
		for (Contributor c : tribute.toList()) {
			if (tribute.sharePercent(c.uuid()) + 1.0E-9 >= minPercent) {
				out.add(c);
			}
		}
		if (out.isEmpty() && TheBallConfig.get().rewards.rewardTopIfNone && !tribute.isEmpty()) {
			out.add(tribute.top(1).get(0));
		}
		return out;
	}

	/** Entrega el premio de un umbral (0..3) a los que tienen derecho y lo anuncia. */
	private static void grant(ServerLevel level, AluminumBallEntity ball, int bit) {
		List<Contributor> winners = eligible(ball.getTribute(), TheBallConfig.get().rewards.minShareForLootPercent);
		for (Contributor c : winners) {
			give(level, ball, c.uuid(), 1 << bit);
		}
		MutableComponent msg = Component.translatable("message.theball.reward.threshold",
				Tribute.THRESHOLDS[bit], Component.translatable(REWARD_KEYS[bit])).withStyle(ChatFormatting.AQUA);
		msg.append(Component.literal(" "));
		msg.append(winners.isEmpty()
				? Component.translatable("message.theball.reward.nobody").withStyle(ChatFormatting.GRAY)
				: Component.literal(names(winners)).withStyle(ChatFormatting.YELLOW));
		Fanfare.threshold(level, ThroneLogic.top(ball), msg);
	}

	/** Premio final (coronación): corona y cetro solo al Rey; medalla a los del 2 % y a los 3 candidatos. */
	public static List<Contributor> grantFinal(ServerLevel level, AluminumBallEntity ball, Contributor king) {
		TheBallConfig.Rewards cfg = TheBallConfig.get().rewards;
		List<Contributor> winners = new ArrayList<>();
		if (king != null) {
			winners.add(king);
			give(level, ball, king.uuid(), 1 << Tribute.BIT_CROWN);
		}
		List<Contributor> top3 = ball.getTribute().top(3);
		for (Contributor c : ball.getTribute().toList()) {
			if (ball.getTribute().sharePercent(c.uuid()) + 1.0E-9 >= cfg.minShareForMedalPercent || winners.contains(c) || top3.contains(c)) {
				give(level, ball, c.uuid(), 1 << BIT_MEDAL);
			}
		}
		return winners;
	}

	/** Los candidatos a Rey: los 3 primeros del ranking de aportes (aunque hayan puesto poco). */
	public static List<Contributor> candidates(Tribute tribute) {
		return tribute.top(3);
	}

	/** ¿Ha puesto lo mínimo del tramo final (últimas {@code final_stretch_sheets} hojas)? Solo así puede ser Rey. */
	public static boolean meetsFinalStretch(Contributor c) {
		TheBallConfig.Rewards cfg = TheBallConfig.get().rewards;
		int min = (int) Math.ceil(cfg.finalStretchSheets * cfg.minFinalStretchPercent / 100.0);
		return c.late() >= Math.max(1, min);
	}

	/**
	 * Orden del juicio entre candidatos: el primero es el Rey, y se descarta del último al segundo. Primero van los que
	 * han aportado en el tramo final, por aporte total (el primero del ranking gana); luego los que no, por lo que
	 * pusieron en el tramo final y por aporte total. Así, quien puso muchísimo y luego dejó de aportar cede la corona.
	 */
	public static List<Contributor> judgmentOrder(List<Contributor> candidates) {
		List<Contributor> out = new ArrayList<>(candidates);
		out.sort((x, y) -> {
			boolean mx = meetsFinalStretch(x), my = meetsFinalStretch(y);
			if (mx != my) {
				return mx ? -1 : 1;
			}
			if (!mx && x.late() != y.late()) {
				return Integer.compare(y.late(), x.late());
			}
			return Integer.compare(y.foil(), x.foil());
		});
		return out;
	}

	/** El Rey por la regla (sin espectáculo): el primero del juicio entre los 3 primeros del ranking. */
	public static Contributor chooseKing(Tribute tribute) {
		List<Contributor> order = judgmentOrder(candidates(tribute));
		return order.isEmpty() ? null : order.get(0);
	}

	/** Al jugador si está conectado; si no, al trono como pendiente. */
	private static void give(ServerLevel level, AluminumBallEntity ball, UUID uuid, int bits) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
		if (player != null) {
			deliverBits(level, player, bits, ball);
		} else if (ball.getAnchor() != null) {
			ThroneBlockEntity throne = ThroneLogic.throneAt(level, ball.getAnchor());
			if (throne != null) {
				throne.addPending(uuid, bits);
			}
		}
	}

	/** Mete en el inventario los premios indicados por la máscara (si no caben, caen a sus pies). */
	public static void deliverBits(ServerLevel level, ServerPlayer player, int bits, AluminumBallEntity ball) {
		TheBallConfig.Rewards cfg = TheBallConfig.get().rewards;
		List<ItemStack> items = new ArrayList<>();
		if ((bits & (1 << Tribute.BIT_FOOD)) != 0) {
			items.add(new ItemStack(ModItems.BOCATA, cfg.foodBocata));
			items.add(new ItemStack(ModItems.DURUM, cfg.foodDurum));
		}
		if ((bits & (1 << Tribute.BIT_BOOTS)) != 0) {
			items.add(new ItemStack(ModItems.ALUMINUM_BOOTS));
		}
		if ((bits & (1 << Tribute.BIT_LEGGINGS)) != 0) {
			items.add(new ItemStack(ModItems.ALUMINUM_LEGGINGS));
		}
		if ((bits & (1 << Tribute.BIT_CHESTPLATE)) != 0) {
			items.add(new ItemStack(ModItems.ALUMINUM_CHESTPLATE));
		}
		if ((bits & (1 << Tribute.BIT_CROWN)) != 0) {
			boolean once = cfg.oncePerPlayer && ModAttachments.power(player).finalLootReceived();
			if (once) {
				player.sendSystemMessage(Component.translatable("message.theball.reward.already").withStyle(ChatFormatting.GRAY));
			} else {
				items.add(new ItemStack(ModItems.ALUMINUM_CROWN));
				items.add(new ItemStack(ModItems.ALUMINUM_SCEPTER));
				ModAttachments.setPower(player, ModAttachments.power(player).withFinalLootReceived(true));
			}
		}
		if ((bits & (1 << BIT_MEDAL)) != 0) {
			Contributor who = null;
			if (ball != null) {
				int foil = ball.getTribute().foilOf(player.getUUID());
				who = new Contributor(player.getUUID(), player.getGameProfile().name(), foil);
			}
			items.add(TributeMedalItem.create(who != null ? who
					: new Contributor(player.getUUID(), player.getGameProfile().name(), 0)));
		}
		for (ItemStack stack : items) {
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		if (!items.isEmpty()) {
			player.sendSystemMessage(Component.translatable("message.theball.reward.received").withStyle(ChatFormatting.GREEN));
			Fanfare.play(level, player.position(), net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
		}
	}

	/** Para el comando {@code /theball reward}: 250/500/1000/1500/2000 → bits. */
	public static int bitsForThreshold(int sheets) {
		for (int bit = 0; bit < Tribute.THRESHOLDS.length; bit++) {
			if (Tribute.THRESHOLDS[bit] == sheets) {
				return bit == Tribute.BIT_CROWN ? (1 << bit) | (1 << BIT_MEDAL) : 1 << bit;
			}
		}
		return 0;
	}

	private static String names(List<Contributor> list) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < list.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(list.get(i).name());
		}
		return sb.toString();
	}
}
