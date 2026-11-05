package com.theball.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theball.config.TheBallConfig;
import com.theball.entity.AluminumBallEntity;
import com.theball.power.CrownPowers;
import com.theball.power.ExplosiveAbility;
import com.theball.power.ScepterPowers;
import com.theball.registry.ModBlocks;
import com.theball.throne.Contributor;
import com.theball.throne.Coronation;
import com.theball.throne.Rewards;
import com.theball.throne.ThroneLogic;
import com.theball.throne.Tribute;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /theball} (solo operadores):
 * <pre>
 * /theball reload                          recarga config/theball.json
 * /theball reward &lt;jugadores&gt; &lt;hojas&gt;      entrega a mano el premio de 250/500/1000/1500/2000
 * /theball cooldown reset &lt;jugadores&gt;      reinicia las esperas de la Corona y el cetro
 * /theball retrogen                      rellena de aluminio (solo roca sin tocar) el chunk donde estás, para probar
 * /theball coronation skip                 (mirando a un trono) salta la cuenta atrás del Coronamiento
 * /theball coronation test                 (mirando a un trono) prueba el espectáculo contigo y 2 aldeanos, sin premios
 * /theball throne info                     (mirando a un trono) hojas, aportes y premios de la bola
 * /theball throne setsheets &lt;n&gt;            (mirando a un trono) fija las hojas de la bola sentada
 * </pre>
 */
public final class TheBallCommands {
	private TheBallCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("theball")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reload").executes(TheBallCommands::reload))
				.then(Commands.literal("reward")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("sheets", IntegerArgumentType.integer(250, 2000))
										.executes(TheBallCommands::reward))))
				.then(Commands.literal("cooldown").then(Commands.literal("reset")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(TheBallCommands::resetCooldowns))))
				.then(Commands.literal("retrogen").executes(TheBallCommands::retrogen)
						.then(Commands.literal("stats").executes(TheBallCommands::retrogenStats)))
				.then(Commands.literal("coronation")
						.then(Commands.literal("skip").executes(TheBallCommands::skipCoronation))
						.then(Commands.literal("test").executes(TheBallCommands::testCoronation)))
				.then(Commands.literal("throne")
						.then(Commands.literal("info").executes(TheBallCommands::throneInfo))
						.then(Commands.literal("setsheets")
								.then(Commands.argument("sheets", IntegerArgumentType.integer(1, AluminumBallEntity.MAX_SHEETS))
										.executes(TheBallCommands::setSheets)))));
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		String error = TheBallConfig.load();
		if (error == null) {
			ctx.getSource().sendSuccess(() -> Component.translatable("command.theball.reload.ok"), true);
			return 1;
		}
		ctx.getSource().sendFailure(Component.translatable("command.theball.reload.error", error));
		return 0;
	}

	private static int reward(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		int sheets = IntegerArgumentType.getInteger(ctx, "sheets");
		int bits = Rewards.bitsForThreshold(sheets);
		if (bits == 0) {
			ctx.getSource().sendFailure(Component.translatable("command.theball.reward.bad"));
			return 0;
		}
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer p : targets) {
			Rewards.deliverBits(p.level(), p, bits, null);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theball.reward.ok", sheets, targets.size()), true);
		return targets.size();
	}

	private static int resetCooldowns(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer p : targets) {
			CrownPowers.resetCooldowns(p);
			ExplosiveAbility.resetCooldown(p);
			ScepterPowers.resetCooldowns(p);
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theball.cooldown.ok", targets.size()), true);
		return targets.size();
	}

	/** La bola sentada en el trono al que mira el jugador (o la más cercana a 10 bloques). */
	private static AluminumBallEntity lookedBall(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.level();
		HitResult hit = player.pick(8.0, 0.0f, false);
		if (hit instanceof BlockHitResult bhr && level.getBlockState(bhr.getBlockPos()).is(ModBlocks.THRONE)) {
			AluminumBallEntity ball = ThroneLogic.seatedBall(level, bhr.getBlockPos());
			if (ball != null) {
				return ball;
			}
		}
		AluminumBallEntity best = null;
		double bestDist = 100.0;
		for (AluminumBallEntity ball : level.getEntitiesOfClass(AluminumBallEntity.class,
				player.getBoundingBox().inflate(10.0), AluminumBallEntity::isSeated)) {
			double d = ball.distanceToSqr(player);
			if (d < bestDist) {
				best = ball;
				bestDist = d;
			}
		}
		return best;
	}

	private static int throneInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		AluminumBallEntity ball = lookedBall(ctx.getSource());
		if (ball == null) {
			ctx.getSource().sendFailure(Component.translatable("command.theball.throne.none"));
			return 0;
		}
		Tribute t = ball.getTribute();
		BlockPos pos = ball.getAnchor();
		CommandSourceStack src = ctx.getSource();
		src.sendSuccess(() -> Component.translatable("command.theball.throne.header", pos.getX(), pos.getY(), pos.getZ(),
				ball.getSheets(), String.format("%.2f", ball.getDiameter())).withStyle(ChatFormatting.GOLD), false);
		StringBuilder given = new StringBuilder();
		for (int bit = 0; bit < Tribute.THRESHOLDS.length; bit++) {
			given.append(Tribute.THRESHOLDS[bit]).append(t.isGiven(bit) ? " ✔  " : " ✘  ");
		}
		src.sendSuccess(() -> Component.translatable("command.theball.throne.rewards", given.toString().trim()), false);
		src.sendSuccess(() -> Component.translatable("command.theball.throne.total", t.totalFoil(), Tribute.referenceFoil()), false);
		for (Contributor c : t.top(20)) {
			src.sendSuccess(() -> Component.literal("  " + c.name() + ": " + c.foil() + " ("
					+ String.format("%.1f", t.sharePercent(c.uuid())) + " %)").withStyle(ChatFormatting.YELLOW), false);
		}
		var throne = ThroneLogic.throneAt(src.getLevel(), pos);
		if (throne != null && !throne.pending().isEmpty()) {
			for (Map.Entry<UUID, Integer> e : throne.pending().entrySet()) {
				src.sendSuccess(() -> Component.translatable("command.theball.throne.pending", e.getKey().toString(),
						Integer.toBinaryString(e.getValue())).withStyle(ChatFormatting.GRAY), false);
			}
		}
		return 1;
	}

	private static int skipCoronation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		AluminumBallEntity ball = lookedBall(ctx.getSource());
		if (ball == null) {
			ctx.getSource().sendFailure(Component.translatable("command.theball.throne.none"));
			return 0;
		}
		if (!Coronation.skipCountdown(ball)) {
			ctx.getSource().sendFailure(Component.translatable("command.theball.coronation.none"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theball.coronation.skipped"), true);
		return 1;
	}

	private static int testCoronation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		AluminumBallEntity ball = lookedBall(ctx.getSource());
		if (ball == null) {
			ctx.getSource().sendFailure(Component.translatable("command.theball.throne.none"));
			return 0;
		}
		if (!Coronation.startTest(ctx.getSource().getLevel(), ball, ctx.getSource().getPlayerOrException())) {
			ctx.getSource().sendFailure(Component.translatable("command.theball.coronation.busy"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theball.coronation.test"), true);
		return 1;
	}

	/** Cuenta el aluminio de los chunks cargados a 8 chunks a la redonda (del jugador, o del spawn si es la consola). */
	private static int retrogenStats(CommandContext<CommandSourceStack> ctx) {
		ServerLevel level = ctx.getSource().getLevel();
		net.minecraft.core.BlockPos at = ctx.getSource().getEntity() != null ? ctx.getSource().getEntity().blockPosition() : level.getRespawnData().pos();
		var st = com.theball.world.OreRetrogen.stats(level, new net.minecraft.world.level.ChunkPos(at.getX() >> 4, at.getZ() >> 4), 8);
		ctx.getSource().sendSuccess(() -> Component.literal("Aluminio: " + st.chunks() + " chunks cargados, " + st.aluminum()
				+ " bloques, " + st.exposed() + " con aire/líquido pegado, Y " + st.minY() + ".." + st.maxY()), false);
		return (int) Math.min(Integer.MAX_VALUE, st.aluminum());
	}

	/** Fuerza el relleno de aluminio en el chunk donde estás (para probarlo; solo roca sin tocar). */
	private static int retrogen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		int placed = com.theball.world.OreRetrogen.run(player.level(), player.level().getChunkAt(player.blockPosition()));
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theball.retrogen.ok", placed), true);
		return placed;
	}

	private static int setSheets(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		AluminumBallEntity ball = lookedBall(ctx.getSource());
		if (ball == null) {
			ctx.getSource().sendFailure(Component.translatable("command.theball.throne.none"));
			return 0;
		}
		int sheets = IntegerArgumentType.getInteger(ctx, "sheets");
		ThroneLogic.setSheets(ctx.getSource().getLevel(), ball, sheets);
		ctx.getSource().sendSuccess(() -> Component.translatable("command.theball.throne.set", sheets), true);
		return 1;
	}
}
