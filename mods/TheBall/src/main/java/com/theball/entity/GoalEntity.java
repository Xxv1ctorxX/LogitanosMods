package com.theball.entity;

import com.theball.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Portería de fútbol 11 (7,32 m x 2,44 m, 2 m de fondo; 1 bloque = 1 m). La posición de la entidad es el centro de
 * su base. No es sólida (se puede atravesar); su "caja" para apuntar es solo el larguero, así no estorba al golpear
 * la bola que haya dentro. Cuando una bola entra por la boca y se pasa de la línea, ¡gol!
 */
public class GoalEntity extends Entity {
	public static final double WIDTH = 7.32;
	public static final double HEIGHT = 2.44;
	public static final double DEPTH = 2.0;
	/** Altura de los postes traseros (la portería es una caja: techo plano). */
	public static final double BACK_HEIGHT = HEIGHT;
	/** Grosor de los palos. */
	public static final double BAR = 0.12;

	private static final int GOAL_COOLDOWN_TICKS = 60;

	private static final EntityDataAccessor<Integer> DATA_FACING =
			SynchedEntityData.defineId(GoalEntity.class, EntityDataSerializers.INT);

	public GoalEntity(EntityType<? extends GoalEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_FACING, Direction.NORTH.get2DDataValue());
	}

	/** Hacia dónde mira la boca de la portería. */
	public Direction getFacing() {
		if (this.entityData == null) {
			return Direction.NORTH;
		}
		return Direction.from2DDataValue(this.entityData.get(DATA_FACING));
	}

	public void setFacing(Direction facing) {
		this.entityData.set(DATA_FACING, facing.get2DDataValue());
		setPos(getX(), getY(), getZ());
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_FACING.equals(accessor)) {
			setPos(getX(), getY(), getZ());
		}
	}

	/**
	 * Caja de apuntado: una pared fina en la boca de la portería (7,32 x 2,44). Lo que haya detrás (bola,
	 * jugadores) lo recuperamos en {@link #findBehind} para que la portería no estorbe al golpear.
	 */
	@Override
	protected AABB makeBoundingBox(Vec3 pos) {
		Direction f = getFacing();
		double mouthX = pos.x + f.getStepX() * DEPTH / 2.0;
		double mouthZ = pos.z + f.getStepZ() * DEPTH / 2.0;
		double hx;
		double hz;
		if (f.getAxis() == Direction.Axis.Z) {
			hx = WIDTH / 2.0;
			hz = 0.15;
		} else {
			hx = 0.15;
			hz = WIDTH / 2.0;
		}
		return new AABB(mouthX - hx, pos.y, mouthZ - hz, mouthX + hx, pos.y + HEIGHT, mouthZ + hz);
	}

	/** La entidad más cercana dentro de la portería a la que está apuntando el jugador (si la hay). */
	private Entity findBehind(Player player) {
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(8.0));
		Entity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Entity e : this.level().getEntities(this, volume().inflate(0.5),
				entity -> entity != player && entity.isPickable() && !(entity instanceof GoalEntity))) {
			var hit = e.getBoundingBox().inflate(e.getPickRadius()).clip(from, to);
			if (hit.isPresent()) {
				double d = from.distanceToSqr(hit.get());
				if (d < bestDistance) {
					bestDistance = d;
					best = e;
				}
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- colisión con la bola

	/**
	 * Cajas sólidas de la portería en coordenadas locales (x = a lo ancho, y = arriba, z = hacia el fondo; la boca está
	 * en z = -DEPTH/2): postes, larguero, postes traseros, raíles y los cuatro paneles de red.
	 */
	private static final double[][] BOXES;

	static {
		double hw = WIDTH / 2.0;
		double hd = DEPTH / 2.0;
		double h = HEIGHT;
		double b = BAR / 2.0;
		double n = 0.04; // mitad del grosor de la red
		BOXES = new double[][] {
				// postes delanteros y larguero
				{-hw - b, 0, -hd - b, -hw + b, h, -hd + b},
				{hw - b, 0, -hd - b, hw + b, h, -hd + b},
				{-hw - b, h - 2 * b, -hd - b, hw + b, h, -hd + b},
				// postes traseros y barra trasera
				{-hw - b, 0, hd - b, -hw + b, h, hd + b},
				{hw - b, 0, hd - b, hw + b, h, hd + b},
				{-hw - b, h - 2 * b, hd - b, hw + b, h, hd + b},
				// raíles del suelo
				{-hw - b, 0, -hd, -hw + b, 2 * b, hd},
				{hw - b, 0, -hd, hw + b, 2 * b, hd},
				// red: techo, fondo y laterales
				{-hw, h - 2 * n, -hd, hw, h, hd},
				{-hw, 0, hd - n, hw, h, hd + n},
				{-hw - n, 0, -hd, -hw + n, h, hd},
				{hw - n, 0, -hd, hw + n, h, hd}
		};
	}

	/** Volumen de la portería (suelo, techo y fondo), para detectar la bola. */
	private AABB volume() {
		Direction f = getFacing();
		double hx = f.getAxis() == Direction.Axis.Z ? WIDTH / 2.0 : DEPTH / 2.0;
		double hz = f.getAxis() == Direction.Axis.Z ? DEPTH / 2.0 : WIDTH / 2.0;
		return new AABB(getX() - hx, getY(), getZ() - hz, getX() + hx, getY() + HEIGHT, getZ() + hz);
	}

	// ---------------------------------------------------------------- piezas sólidas

	/** Piezas sólidas invisibles (postes, larguero, red): solo en el servidor. */
	private final java.util.List<GoalPartEntity> parts = new java.util.ArrayList<>();
	private Direction builtFacing;

	private void ensureParts(ServerLevel serverLevel) {
		boolean ok = builtFacing == getFacing() && parts.size() == BOXES.length;
		if (ok) {
			for (GoalPartEntity part : parts) {
				if (part.isRemoved()) {
					ok = false;
					break;
				}
			}
		}
		if (ok) {
			return;
		}
		removeParts();

		Direction f = getFacing();
		Vec3 fwd = new Vec3(f.getStepX(), 0.0, f.getStepZ());
		Vec3 side = new Vec3(-fwd.z, 0.0, fwd.x);
		boolean alongZ = f.getAxis() == Direction.Axis.Z;

		for (double[] box : BOXES) {
			double lx = (box[0] + box[3]) / 2.0;
			double ly = (box[1] + box[4]) / 2.0;
			double lz = (box[2] + box[5]) / 2.0;
			float hxLocal = (float) ((box[3] - box[0]) / 2.0);
			float hy = (float) ((box[4] - box[1]) / 2.0);
			float hzLocal = (float) ((box[5] - box[2]) / 2.0);

			Vec3 center = position().add(side.scale(lx)).add(0.0, ly, 0.0).add(fwd.scale(-lz));
			GoalPartEntity part = new GoalPartEntity(com.theball.registry.ModEntities.GOAL_PART, serverLevel);
			part.setParent(this);
			part.setPos(center.x, center.y, center.z);
			part.setHalfSize(alongZ ? hxLocal : hzLocal, hy, alongZ ? hzLocal : hxLocal);
			serverLevel.addFreshEntity(part);
			parts.add(part);
		}
		builtFacing = f;
	}

	private void removeParts() {
		for (GoalPartEntity part : parts) {
			part.discard();
		}
		parts.clear();
		builtFacing = null;
	}

	// ---------------------------------------------------------------- persistencia

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(DATA_FACING, input.getIntOr("Facing", Direction.NORTH.get2DDataValue()));
		setPos(getX(), getY(), getZ());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("Facing", this.entityData.get(DATA_FACING));
	}

	// ---------------------------------------------------------------- comportamiento

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return false;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(ModItems.GOAL);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		ensureParts(serverLevel);

		Direction f = getFacing();
		Vec3 forward = new Vec3(f.getStepX(), 0.0, f.getStepZ());
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);

		for (AluminumBallEntity ball : serverLevel.getEntitiesOfClass(AluminumBallEntity.class, volume())) {
			Vec3 rel = ball.position().subtract(position());
			double back = -rel.dot(forward); // positivo hacia el fondo; la boca está en -DEPTH/2
			double lateral = rel.dot(side);
			double centerY = rel.y + ball.getBbHeight() / 2.0;

			boolean pastLine = back > -DEPTH / 2.0 + 0.15;
			if (!pastLine || centerY > HEIGHT || Math.abs(lateral) > WIDTH / 2.0) {
				continue;
			}

			// La red frena la bola.
			Vec3 v = ball.getDeltaMovement();
			ball.setDeltaMovement(v.x * 0.92, v.y, v.z * 0.92);

			if (ball.tickCount - ball.lastGoalTick >= GOAL_COOLDOWN_TICKS) {
				ball.lastGoalTick = ball.tickCount;
				if (ball.isRoyalKick()) {
					celebrateRoyal(serverLevel, ball);
				} else {
					celebrate(serverLevel);
				}
			}
		}
	}

	private void celebrate(ServerLevel serverLevel) {
		Component message = Component.translatable("message.theball.goal")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (ServerPlayer player : serverLevel.players()) {
			if (player.distanceToSqr(this) < 64.0 * 64.0) {
				player.sendOverlayMessage(message);
			}
		}
		serverLevel.playSound(null, getX(), getY() + 1.0, getZ(),
				SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.NEUTRAL, 1.5f, 1.0f);
		serverLevel.playSound(null, getX(), getY() + 1.0, getZ(),
				SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0f, 1.2f);
		serverLevel.sendParticles(ParticleTypes.FIREWORK, getX(), getY() + 1.4, getZ(), 60, 1.2, 0.6, 1.2, 0.12);
	}

	/** Gol marcado con un chute del cetro (Golazo): celebración especial y logro para quien chutó. */
	private void celebrateRoyal(ServerLevel serverLevel, AluminumBallEntity ball) {
		ServerPlayer kicker = ball.getKickOwner() == null ? null
				: serverLevel.getServer().getPlayerList().getPlayer(ball.getKickOwner());
		Component message = Component.translatable("message.theball.royal_goal",
				kicker == null ? "?" : kicker.getGameProfile().name()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (ServerPlayer player : serverLevel.players()) {
			if (player.distanceToSqr(this) < 96.0 * 96.0) {
				player.sendOverlayMessage(message);
			}
		}
		Vec3 top = position().add(0.0, HEIGHT + 0.5, 0.0);
		com.theball.throne.Fanfare.rocket(serverLevel, top.add(-2.5, 0, 0), 1,
				net.minecraft.world.item.component.FireworkExplosion.Shape.STAR, com.theball.throne.Fanfare.GOLD);
		com.theball.throne.Fanfare.rocket(serverLevel, top.add(2.5, 0, 0), 1,
				net.minecraft.world.item.component.FireworkExplosion.Shape.STAR, com.theball.throne.Fanfare.GOLD);
		com.theball.throne.Fanfare.rocket(serverLevel, top, 2,
				net.minecraft.world.item.component.FireworkExplosion.Shape.LARGE_BALL, com.theball.throne.Fanfare.GOLD,
				com.theball.throne.Fanfare.WHITE);
		serverLevel.playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 2.0f, 1.0f);
		serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1.4, getZ(), 120, 2.0, 1.0, 2.0, 0.5);
		if (kicker != null) {
			com.theball.registry.ModAdvancements.grant(kicker, com.theball.registry.ModAdvancements.ROYAL_GOAL);
		}
	}

	/** Golpearla (el larguero) la recoge. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved()) {
			return false;
		}
		if (source.getEntity() instanceof Player player) {
			// Si hay algo detrás (la bola, otro jugador), el golpe es para eso, no para la portería.
			Entity behind = findBehind(player);
			if (behind != null) {
				return behind.hurtServer(level, source, damage);
			}
			if (!player.hasInfiniteMaterials()) {
				spawnAtLocation(level, new ItemStack(ModItems.GOAL));
			}
			playSound(SoundEvents.ITEM_PICKUP, 0.6f, 1.0f);
			removeParts();
			discard();
			return true;
		}
		return false;
	}

	/** Click derecho: solo sirve para llegar a la bola que haya dentro (la portería no se mueve; para girarla, se recoge y se vuelve a poner). */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSpectator()) {
			return InteractionResult.PASS;
		}
		Entity behind = findBehind(player);
		if (behind instanceof AluminumBallEntity ball) {
			return ball.onPlayerUse(player, hand);
		}
		return InteractionResult.PASS;
	}
}
