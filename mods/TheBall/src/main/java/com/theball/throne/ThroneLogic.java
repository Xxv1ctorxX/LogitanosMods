package com.theball.throne;

import com.theball.block.entity.ThroneBlockEntity;
import com.theball.config.TheBallConfig;
import com.theball.entity.AluminumBallEntity;
import com.theball.item.AluminumBallItem;
import com.theball.registry.ModAdvancements;
import com.theball.registry.ModBlocks;
import com.theball.registry.ModEntities;
import com.theball.network.QueenBarPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Todo lo que pasa en el trono: sentar y retirar la bola, alimentarla (XP, aportes, hitos), comprobar el espacio
 * libre, la barra de jefe de la Reina y el holograma «Ranking del tributo».
 */
public final class ThroneLogic {
	/** Etiqueta del holograma de aportes (para encontrarlo y no dejar huérfanos). */
	public static final String TRIBUTE_TAG = "theball_tribute";
	/** Tras dejar de mirar a la Reina, su barra sigue unos ticks (evita parpadeos en el borde de la bola). */
	private static final int BOSS_BAR_GRACE_TICKS = 15;

	private ThroneLogic() {
	}

	/** Estado del trono que vive en memoria (no se guarda): barra de jefe y actividad. */
	public static final class Runtime {
		/** Jugadores que ahora ven la barra (la dibuja su cliente; aquí solo se manda cuándo y con qué cifras). */
		final Set<UUID> shownTo = new HashSet<>();
		final Map<UUID, Long> lastSent = new HashMap<>();
		final Map<UUID, Integer> lastKey = new HashMap<>();
		final Map<UUID, Long> lastSeen = new HashMap<>();
		long lastActivity = Long.MIN_VALUE / 2;
		String lastHologram = "";
		String lastFeeder;
	}

	/** Nombre del último que alimentó la bola (quien la corona). */
	public static String lastFeeder(AluminumBallEntity ball) {
		return ball.throneRuntime == null ? null : ball.throneRuntime.lastFeeder;
	}

	private static Runtime runtime(AluminumBallEntity ball) {
		if (ball.throneRuntime == null) {
			ball.throneRuntime = new Runtime();
		}
		return ball.throneRuntime;
	}

	// ------------------------------------------------------------------ consultas

	public static ThroneBlockEntity throneAt(ServerLevel level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof ThroneBlockEntity throne ? throne : null;
	}

	public static AluminumBallEntity seatedBall(ServerLevel level, BlockPos pos) {
		ThroneBlockEntity throne = throneAt(level, pos);
		return throne == null ? null : throne.findBall(level);
	}

	/** Punto más alto de la bola (centro de la parte de arriba). */
	public static Vec3 top(AluminumBallEntity ball) {
		return ball.position().add(0.0, ball.getDiameter(), 0.0);
	}

	// ------------------------------------------------------------------ sentar / retirar

	public static void seat(ServerLevel level, BlockPos pos, Player player, InteractionHand hand) {
		ThroneBlockEntity throne = throneAt(level, pos);
		if (throne == null) {
			return;
		}
		if (throne.findBall(level) != null) {
			player.sendOverlayMessage(Component.translatable("message.theball.throne.occupied"));
			return;
		}
		ItemStack stack = player.getItemInHand(hand);
		int sheets = AluminumBallItem.getSheets(stack);
		if (TheBallConfig.get().throne.requireFreeSpace && !hasSpace(level, pos, AluminumBallEntity.diameterFor(sheets))) {
			noSpace(level, pos, player, AluminumBallEntity.diameterFor(sheets));
			return;
		}

		BlockState state = level.getBlockState(pos);
		Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING)
				? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.SOUTH;

		AluminumBallEntity ball = new AluminumBallEntity(ModEntities.ALUMINUM_BALL, level);
		ball.setSheets(sheets);
		ball.setTribute(Tribute.fromStack(stack));
		ball.seatOn(pos, facing);
		// Una bola que llega ya grande no cobra los hitos que dejo atras: solo se premia lo que se cruza sentada.
		for (int bit = Tribute.BIT_FOOD; bit <= Tribute.BIT_CHESTPLATE; bit++) {
			if (ball.getSheets() >= Tribute.THRESHOLDS[bit]) {
				ball.getTribute().markGiven(bit);
			}
		}
		if (ball.getSheets() >= AluminumBallEntity.MAX_SHEETS && ball.getTribute().isGiven(Tribute.BIT_CROWN)) {
			ball.setCrowned(true); // una Reina ya coronada vuelve a su trono con la corona de oro
		}
		level.addFreshEntity(ball);
		throne.setBallId(ball.getUUID());

		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}

		// Ceremonia: espiral de chispas que baja al asiento y campanada.
		Vec3 seat = ball.position();
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI / 6.0;
			double r = 0.6;
			level.sendParticles(ParticleTypes.END_ROD, seat.x + Math.cos(a) * r, seat.y + 1.6 - i * 0.06,
					seat.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		Fanfare.play(level, seat, SoundEvents.BELL_BLOCK, 0.7f, 1.4f);
		Fanfare.play(level, seat, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);

		if (player instanceof ServerPlayer sp) {
			ModAdvancements.grant(sp, ModAdvancements.SEAT);
			updateBossBar(level, ball);
		}
		// Solo queda por comprobar la coronación (bola de 2.000 hojas sin corona).
		Rewards.checkThresholds(level, ball);
		touch(ball);
	}

	/** Shift + click con la mano vacía: la bola vuelve al inventario con todos sus datos. */
	public static void unseat(AluminumBallEntity ball, Player player) {
		if (!(ball.level() instanceof ServerLevel level)) {
			return;
		}
		if (ball.coronationTick >= 0) {
			player.sendOverlayMessage(Component.translatable("message.theball.throne.coronating"));
			return;
		}
		ItemStack stack = ball.toItem();
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		} else if (!player.getInventory().add(stack)) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		Fanfare.play(level, ball.position(), SoundEvents.ITEM_PICKUP, 0.6f, 0.8f);
		ball.release();
		ball.discard();
	}

	/** Click con la mano vacía: estado de la bola y premios pendientes. */
	public static void statusFor(ServerLevel level, BlockPos pos, Player player) {
		if (!(player instanceof ServerPlayer sp)) {
			return;
		}
		ThroneBlockEntity throne = throneAt(level, pos);
		if (throne != null) {
			int pending = throne.takePending(sp.getUUID());
			if (pending != 0) {
				Rewards.deliverBits(level, sp, pending, seatedBall(level, pos));
			}
		}
		AluminumBallEntity ball = seatedBall(level, pos);
		if (ball == null) {
			sp.sendOverlayMessage(Component.translatable("message.theball.throne.empty"));
		} else {
			updateBossBar(level, ball);
		}
	}

	// ------------------------------------------------------------------ alimentar

	/** Papel sobre la bola sentada: +1 hoja, XP para quien la pone, hitos y premios. */
	public static void feed(AluminumBallEntity ball, Player player, InteractionHand hand) {
		if (!(ball.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
			return;
		}
		ItemStack held = player.getItemInHand(hand);
		int sheets = ball.getSheets();
		if (ball.coronationTick >= 0) {
			sp.sendOverlayMessage(Component.translatable("message.theball.throne.coronating"));
			return;
		}
		if (sheets >= AluminumBallEntity.MAX_SHEETS) {
			updateBossBar(level, ball);
			return;
		}
		int cost = AluminumBallEntity.foilCostFor(sheets);
		boolean free = player.hasInfiniteMaterials();
		if (!free && held.getCount() < cost) {
			sp.sendOverlayMessage(Component.translatable("message.theball.need_foil", cost));
			return;
		}
		BlockPos anchor = ball.getAnchor();
		double newDiameter = AluminumBallEntity.diameterFor(sheets + 1);
		if (TheBallConfig.get().throne.requireFreeSpace && !hasSpace(level, anchor, newDiameter)) {
			noSpace(level, anchor, sp, newDiameter);
			return;
		}

		int newSheets = sheets + 1;
		ball.setSheets(newSheets);
		runtime(ball).lastFeeder = sp.getGameProfile().name();
		if (!free) {
			held.shrink(cost);
			recordContribution(ball, player, cost);
		}
		ejectFromInside(level, ball);

		Vec3 top = top(ball);
		if (!free || TheBallConfig.get().xp.payInCreative) {
			XpPayout.pay(sp, newSheets, top);
		}
		Fanfare.play(level, top, SoundEvents.WOOL_PLACE, 0.7f, 1.3f + level.getRandom().nextFloat() * 0.5f);

		if (newSheets % 100 == 0) {
			int milestone = newSheets / 100;
			int bonus = !free || TheBallConfig.get().xp.payInCreative ? XpPayout.milestoneXp(milestone) : 0;
			XpPayout.payMilestone(sp, bonus, top);
			Fanfare.milestone(level, top, milestone, sp, bonus);
		}
		Rewards.checkThresholds(level, ball);
		touch(ball);
		updateBossBar(level, ball);
	}

	/** Anota el papel que ha gastado de verdad un jugador (en creativo no se llama). */
	public static void recordContribution(AluminumBallEntity ball, Player player, int foil) {
		// La hoja que se acaba de añadir es la número (hojas - 1); cuenta como tramo final si cae en las últimas N.
		boolean late = ball.getSheets() - 1 >= AluminumBallEntity.MAX_SHEETS - TheBallConfig.get().rewards.finalStretchSheets;
		ball.getTribute().add(player.getUUID(), player.getGameProfile().name(), foil, late ? foil : 0);
		if (player instanceof ServerPlayer sp && ball.getTribute().foilOf(sp.getUUID()) >= 1000) {
			ModAdvancements.grant(sp, ModAdvancements.PATRON);
		}
	}

	// ------------------------------------------------------------------ espacio libre

	/** Caja que ocupará una bola de ese diámetro sentada en el trono. */
	public static AABB ballBox(BlockPos throne, double diameter) {
		Vec3 base = AluminumBallEntity.seatPosition(throne);
		double r = diameter / 2.0;
		return new AABB(base.x - r, base.y, base.z - r, base.x + r, base.y + diameter, base.z + r);
	}

	/** ¿Cabe la bola? Cuenta cualquier bloque sólido salvo el propio trono. */
	public static boolean hasSpace(ServerLevel level, BlockPos throne, double diameter) {
		AABB box = ballBox(throne, diameter).deflate(1.0E-3);
		for (BlockPos pos : BlockPos.betweenClosed(
				BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			if (pos.equals(throne)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty()) {
				continue;
			}
			for (AABB part : shape.move(pos).toAabbs()) {
				if (part.intersects(box)) {
					return false;
				}
			}
		}
		return true;
	}

	/** Avisa y dibuja con partículas rojas el cubo que haría falta. */
	private static void noSpace(ServerLevel level, BlockPos throne, Player player, double diameter) {
		int size = (int) Math.ceil(diameter);
		player.sendOverlayMessage(Component.translatable("message.theball.throne.no_space", size, size, size)
				.withStyle(ChatFormatting.RED));
		AABB box = ballBox(throne, diameter);
		DustParticleOptions red = new DustParticleOptions(0xFF3030, 1.2f);
		double step = Math.max(0.25, diameter / 16.0);
		double[] xs = {box.minX, box.maxX};
		double[] ys = {box.minY, box.maxY};
		double[] zs = {box.minZ, box.maxZ};
		for (double y : ys) {
			for (double z : zs) {
				for (double x = box.minX; x <= box.maxX; x += step) {
					level.sendParticles(red, x, y, z, 1, 0, 0, 0, 0);
				}
			}
		}
		for (double x : xs) {
			for (double z : zs) {
				for (double y = box.minY; y <= box.maxY; y += step) {
					level.sendParticles(red, x, y, z, 1, 0, 0, 0, 0);
				}
			}
		}
		for (double x : xs) {
			for (double y : ys) {
				for (double z = box.minZ; z <= box.maxZ; z += step) {
					level.sendParticles(red, x, y, z, 1, 0, 0, 0, 0);
				}
			}
		}
		Fanfare.play(level, AluminumBallEntity.seatPosition(throne), SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.6f);
	}

	/** Al crecer, quien estuviera dentro de la bola acaba encima (así se puede subir a la Reina). */
	private static void ejectFromInside(ServerLevel level, AluminumBallEntity ball) {
		AABB box = ball.getBoundingBox();
		double topY = box.maxY + 0.01;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
			if (e.getBoundingBox().intersects(box.deflate(0.05))) {
				e.teleportTo(e.getX(), topY, e.getZ());
			}
		}
	}

	// ------------------------------------------------------------------ cada tick (bola sentada)

	public static void tickSeated(ServerLevel level, AluminumBallEntity ball) {
		BlockPos anchor = ball.getAnchor();
		if (ball.tickCount % 20 == 0 && !validateThrone(level, ball, anchor)) {
			ball.release();
			return;
		}
		if (ball.coronationTick >= 0) {
			Coronation.tick(level, ball);
		}
		if (ball.isCrowned() && ball.tickCount % 4 == 0) {
			crownedGlow(level, ball);
		}
		if (ball.tickCount % 4 == 0) {
			updateBossBar(level, ball);
		}
		if (ball.tickCount % 40 == 0) {
			updateHologram(level, ball);
		}
	}

	/** El trono sigue ahí y es el nuestro. */
	private static boolean validateThrone(ServerLevel level, AluminumBallEntity ball, BlockPos anchor) {
		if (!level.isLoaded(anchor)) {
			return true; // no podemos saberlo; ya se comprobará
		}
		if (!level.getBlockState(anchor).is(ModBlocks.THRONE)) {
			return false;
		}
		ThroneBlockEntity throne = throneAt(level, anchor);
		if (throne == null) {
			return false;
		}
		UUID id = throne.getBallId();
		if (id == null) {
			throne.setBallId(ball.getUUID());
			return true;
		}
		if (id.equals(ball.getUUID())) {
			return true;
		}
		// El trono tiene otra bola: si esa existe, sobramos.
		return !(level.getEntity(id) instanceof AluminumBallEntity other) || other.isRemoved();
	}

	/** La Reina coronada: haz de luz hacia el cielo y destellos dorados. */
	private static void crownedGlow(ServerLevel level, AluminumBallEntity ball) {
		Vec3 top = top(ball);
		double crownTop = top.y + ball.getDiameter() * 0.25;
		for (int i = 0; i < 10; i++) {
			level.sendParticles(ParticleTypes.END_ROD, true, false, top.x, crownTop + i * 2.5, top.z, 1, 0.05, 0.4, 0.05, 0.0);
		}
		level.sendParticles(new DustParticleOptions(0xFFD040, 1.5f), true, false, top.x, crownTop, top.z, 3,
				ball.getDiameter() * 0.2, 0.2, ball.getDiameter() * 0.2, 0.0);
	}

	// ------------------------------------------------------------------ barra de jefe

	private static void touch(AluminumBallEntity ball) {
		runtime(ball).lastActivity = ball.level().getGameTime();
	}

	/** ¿Está el jugador apuntando a la bola (dentro del alcance de la barra)? */
	private static boolean isLookingAt(ServerPlayer p, AluminumBallEntity ball, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		return ball.getBoundingBox().inflate(0.25).clip(eye, end).isPresent();
	}

	/**
	 * Barra de la Reina: el servidor solo decide quién la mira (con un pequeño margen para que no parpadee) y manda
	 * cifras a ese jugador cuando cambian y como latido cada medio segundo; el cliente la dibuja (QueenBarHud).
	 */
	private static void updateBossBar(ServerLevel level, AluminumBallEntity ball) {
		Runtime rt = runtime(ball);
		long now = level.getGameTime();
		int sheets = ball.getSheets();
		boolean crowned = ball.isCrowned() || sheets >= AluminumBallEntity.MAX_SHEETS;
		double range = TheBallConfig.get().throne.bossBarRange + ball.getDiameter();
		double range2 = range * range;
		boolean ceremony = Coronation.inProgress(ball); // durante el Coronamiento el HUD solo muestra el nombre
		Set<UUID> keep = new HashSet<>();
		List<String> topNames = new ArrayList<>();
		List<Integer> topFoil = new ArrayList<>();
		if (TheBallConfig.get().throne.tributeWall) {
			for (Contributor c : ball.getTribute().top(3)) {
				topNames.add(c.name());
				topFoil.add(c.foil());
			}
		}
		int topHash = topNames.hashCode() * 31 + topFoil.hashCode();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || p.distanceToSqr(ball) > range2) {
				continue;
			}
			UUID id = p.getUUID();
			if (isLookingAt(p, ball, range)) {
				rt.lastSeen.put(id, now);
			}
			Long seen = rt.lastSeen.get(id);
			if (seen == null || now - seen > BOSS_BAR_GRACE_TICKS) {
				continue;
			}
			keep.add(id);
			int share = (int) Math.round(ball.getTribute().sharePercent(id) * 10.0);
			int key = (((sheets * 31 + share) * 2 + (crowned ? 1 : 0)) * 31 + topHash) * 2 + (ceremony ? 1 : 0);
			Integer prev = rt.lastKey.get(id);
			Long sent = rt.lastSent.get(id);
			if (prev == null || prev != key || sent == null || now - sent >= 10) {
				ServerPlayNetworking.send(p, ceremony ? new QueenBarPayload(true, -1, 0, true, List.of(), List.of())
						: new QueenBarPayload(true, sheets, share, crowned, topNames, topFoil));
				rt.lastKey.put(id, key);
				rt.lastSent.put(id, now);
			}
		}
		// los que dejaron de mirar (o se alejaron): ocultar
		for (UUID id : new ArrayList<>(rt.shownTo)) {
			if (!keep.contains(id)) {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
				if (p != null) {
					ServerPlayNetworking.send(p, QueenBarPayload.HIDE);
				}
				rt.lastKey.remove(id);
				rt.lastSent.remove(id);
			}
		}
		rt.shownTo.clear();
		rt.shownTo.addAll(keep);
		rt.lastSeen.keySet().removeIf(id -> !keep.contains(id) && now - rt.lastSeen.get(id) > BOSS_BAR_GRACE_TICKS);
	}

	private static void clearBars(Runtime rt, ServerLevel level) {
		for (UUID id : rt.shownTo) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				ServerPlayNetworking.send(p, QueenBarPayload.HIDE);
			}
		}
		rt.shownTo.clear();
		rt.lastSent.clear();
		rt.lastKey.clear();
		rt.lastSeen.clear();
	}

	// ------------------------------------------------------------------ holograma «Ranking del tributo»

	/** El ranking ya no es un holograma en el mundo (se veía desde lejos): ahora va en el HUD. Aquí solo se limpian los antiguos. */
	private static void updateHologram(ServerLevel level, AluminumBallEntity ball) {
		Runtime rt = runtime(ball);
		if (!"clean".equals(rt.lastHologram)) {
			removeHolograms(level, ball);
			rt.lastHologram = "clean";
		}
	}

	private static void removeHolograms(ServerLevel level, AluminumBallEntity ball) {
		for (Display.TextDisplay d : level.getEntitiesOfClass(Display.TextDisplay.class,
				ball.getBoundingBox().inflate(16.0), d -> d.entityTags().contains(TRIBUTE_TAG + "_" + ball.getUUID()))) {
			d.discard();
		}
		runtime(ball).lastHologram = "";
	}

	// ------------------------------------------------------------------ limpieza

	/** La bola ha dejado el trono (retirada o trono roto). */
	public static void onLeftThrone(ServerLevel level, AluminumBallEntity ball, BlockPos thronePos) {
		Runtime rt = runtime(ball);
		clearBars(rt, level);
		removeHolograms(level, ball);
		Coronation.abort(level, ball);
		// El trono queda libre (si sigue ahí y era el nuestro).
		ThroneBlockEntity throne = throneAt(level, thronePos);
		if (throne != null && ball.getUUID().equals(throne.getBallId())) {
			throne.setBallId(null);
		}
	}

	public static void onRemoved(ServerLevel level, AluminumBallEntity ball, Entity.RemovalReason reason) {
		Runtime rt = ball.throneRuntime;
		if (rt != null) {
			clearBars(rt, level);
		}
		if (reason.shouldDestroy()) {
			removeHolograms(level, ball);
		}
	}

	/** Para comandos: fija las hojas de una bola sentada y dispara los umbrales que toquen. */
	public static void setSheets(ServerLevel level, AluminumBallEntity ball, int sheets) {
		ball.setSheets(sheets);
		ejectFromInside(level, ball);
		Rewards.checkThresholds(level, ball);
		touch(ball);
		updateBossBar(level, ball);
		updateHologram(level, ball);
	}

}
